package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import java.util.List;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordCodeRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordCredentialRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordStateRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordOperationRepository;

import com.ofss.fc.infra.jdbc.ConnectionUtil;
import javax.transaction.TransactionManager;
import weblogic.transaction.TransactionHelper;

/**
 * Coordinates Code verification and repository operations within explicit transaction boundaries.
 * Repositories share the supplied session for conditional updates and approval row locks.
 * Independent transactions preserve failed attempts and reservations across API rollback;
 * database completion commits the hash, consumed Code and success state together.
 */
final class HthApiPasswordStore {
  private final HthApiPasswordCodeRepository codeRepository =
      HthApiPasswordCodeRepository.getInstance();
  private final HthApiPasswordCredentialRepository credentialRepository =
      HthApiPasswordCredentialRepository.getInstance();
  private final HthApiPasswordStateRepository stateRepository =
      HthApiPasswordStateRepository.getInstance();
  private final HthApiPasswordOperationRepository operationRepository =
      HthApiPasswordOperationRepository.getInstance();

  static final String STATE_ACTIVE = "ACTIVE";
  static final String STATE_NOT_SETUP = "NOT_SETUP";

  String findCredentialState(String partyId, String userId) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = stateRepository.findStatus(lease.session, partyId, userId);
      return rows == null || rows.isEmpty() ? STATE_NOT_SETUP : string(rows.get(0));
    } finally {
      lease.close(false);
    }
  }

  /** Reads the authoritative credential state for DATABASE mode. */
  String findDatabaseCredentialState(String partyId, String userId) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = credentialRepository.findStatus(lease.session, partyId, userId);
      return rows == null || rows.isEmpty() ? STATE_NOT_SETUP : string(rows.get(0));
    } finally {
      lease.close(false);
    }
  }

  boolean hasUsableCode(String partyId, String userId, String purpose) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = codeRepository.findUsable(lease.session, partyId, userId, purpose);
      return rows != null && !rows.isEmpty();
    } finally {
      lease.close(false);
    }
  }

  OperationResult findSuccessfulOperation(String requestId, String partyId, String userId,
      String operation, String backend) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = operationRepository.findResult(lease.session, requestId, partyId, userId, operation);
      if (rows == null || rows.isEmpty()) {
        return null;
      }
      Object[] row = (Object[]) rows.get(0);
      if (!backend.equals(string(row[2]))) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      return new OperationResult(string(row[0]), string(row[1]));
    } finally {
      lease.close(false);
    }
  }

  /** Verifies the Code and reserves it, persisting failed verification attempts independently. */
  String validateAndReserveCode(String partyId, String userId, String purpose, String code,
      String requestId, String backend) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      List rows = codeRepository.findUsableCipher(lease.session, partyId, userId, purpose);
      if (rows == null || rows.isEmpty()) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_003");
      }
      Object[] row = (Object[]) rows.get(0);
      String codeId = string(row[0]);
      if (!HthApiPasswordCrypto.constantTimeEquals(code,
          HthApiPasswordCrypto.decrypt(string(row[1]), HthApiPasswordCrypto.codeCipherKey()))) {
        codeRepository.recordFailedAttempt(lease.session, codeId);
        success = true;
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_002");
      }

      if (codeRepository.reserve(lease.session, requestId, codeId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      operationRepository.reserve(lease.session, requestId, partyId, userId, purpose, codeId, backend);
      success = true;
      return codeId;
    } catch (Exception e) {
      if (success) {
        lease.close(true);
        lease.closed = true;
      }
      throw e;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (!lease.closed) {
        lease.close(success);
      }
    }
  }

  /** Records a confirmed UAM result and consumes its reserved Code. */
  void complete(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber) throws Exception {
    completeInternal(partyId, userId, operation, requestId, codeId, referenceNumber, null);
  }

  /** Commits the password hash, Code consumption and operation result in one transaction. */
  void completeDatabase(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber, String passwordHash) throws Exception {
    if (passwordHash == null) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
    completeInternal(partyId, userId, operation, requestId, codeId, referenceNumber, passwordHash);
  }

  private void completeInternal(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber, String passwordHash) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      if (codeRepository.consume(lease.session, userId, codeId, requestId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      if (passwordHash != null) {
        credentialRepository.write(lease.session, partyId, userId, operation, requestId, passwordHash);
      }

      stateRepository.complete(lease.session, partyId, userId, operation, requestId, referenceNumber);

      if (operationRepository.complete(lease.session, referenceNumber, userId, requestId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      success = true;
    } catch (Exception e) {
      throw e;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }


  /** Retains UNKNOWN reservations for reconciliation when the write outcome is uncertain. */
  void fail(String userId, String requestId, String codeId, boolean uncertain) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      codeRepository.failReservation(lease.session, userId, requestId, codeId, uncertain);

      operationRepository.failReservation(lease.session, userId, requestId, uncertain);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  /** Regeneration must not inactivate a code that became ACTIVE after the maker read it. */
  void retirePendingCodes(String partyId, String userName, String purpose, String operator)
      throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      codeRepository.retirePending(lease.session, partyId, userName, purpose, operator);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  /** Approval and consume share the same row locks and status constraints. */
  boolean activateApprovedCode(String codeId, String partyId, String userName, String purpose,
      String operator, String transactionId, int expiryHours) throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      List rows = codeRepository.lockForApproval(lease.session, codeId);
      if (rows == null || rows.isEmpty()) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      Object[] row = (Object[]) rows.get(0);
      if (!"A".equals(string(row[1]))) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      String status = string(row[0]);
      if ("ACTIVE".equals(status) || "USED".equals(status)
          || "IN_PROGRESS".equals(status) || "UNKNOWN".equals(status)) {
        success = true;
        return false; // Approval replay does not reactivate the Code or resend its notification.
      }
      if (!"PENDING".equals(status)) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      codeRepository.retireActive(lease.session, operator, partyId, userName, purpose);
      // The live-code unique index rejects activation while another code is IN_PROGRESS/UNKNOWN.
      if (codeRepository.activate(lease.session, transactionId, expiryHours, operator, codeId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      success = true;
      return true;
    } catch (java.lang.Exception e) {
      if (e instanceof Exception) {
        throw (Exception) e;
      }
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  private String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** Persist failed attempts/reservations even when the outer API transaction is rolled back. */
  private SessionLease openIndependent() throws Exception {
    TransactionManager manager = null;
    javax.transaction.Transaction suspended = null;
    Session session = null;
    try {
      if (ConnectionUtil.isConnPooled("DIGX")) {
        manager = TransactionHelper.getTransactionHelper().getTransactionManager();
        suspended = manager.suspend();
      }
      // openNewSession does not replace the outer thread-bound ORM session.
      session = DataAccessManager.getManager().openNewSession("DIGX");
      session.beginTransaction();
      return new SessionLease(session, manager, suspended);
    } catch (java.lang.Exception e) {
      try {
        if (session != null) {
          try {
            if (session.fetchCurrentTransaction() != null && session.fetchCurrentTransaction().isActive()) {
              session.fetchCurrentTransaction().rollback();
            }
          } finally {
            DataAccessManager.getManager().closeSession(session);
          }
        }
      } finally {
        if (manager != null && suspended != null) {
          try { manager.resume(suspended); } catch (java.lang.Exception resumeFailure) {
            e.addSuppressed(resumeFailure);
          }
        }
      }
      throw new Exception(e);
    }
  }

  private SessionLease open(boolean write) throws Exception {
    if (DataAccessManager.getManager().isSessionOpen()) {
      return new SessionLease(DataAccessManager.getManager().fetchCurrentSession(), false, write);
    }
    Session session = DataAccessManager.getManager().openSession("DIGX");
    if (write) {
      session.beginTransaction();
    }
    return new SessionLease(session, true, write);
  }

  static final class OperationResult {
    final String status;
    final String referenceNumber;

    OperationResult(String status, String referenceNumber) {
      this.status = status;
      this.referenceNumber = referenceNumber;
    }
  }

  private static final class SessionLease {
    private final Session session;
    private final boolean owned;
    private final boolean write;
    private boolean closed;
    private TransactionManager manager;
    private javax.transaction.Transaction suspended;

    private SessionLease(Session session, TransactionManager manager,
        javax.transaction.Transaction suspended) {
      this(session, true, true);
      this.manager = manager;
      this.suspended = suspended;
    }

    private SessionLease(Session session, boolean owned, boolean write) {
      this.session = session;
      this.owned = owned;
      this.write = write;
    }

    private void close(boolean commit) throws Exception {
      if (closed || !owned) {
        return;
      }
      try {
        if (write && commit) {
          session.fetchCurrentTransaction().commit();
        } else if (write && session.fetchCurrentTransaction() != null) {
          session.fetchCurrentTransaction().rollback();
        }
      } catch (java.lang.Exception failure) {
        try {
          if (write && session.fetchCurrentTransaction() != null
              && session.fetchCurrentTransaction().isActive()) {
            session.fetchCurrentTransaction().rollback();
          }
        } catch (java.lang.Exception rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw new Exception(failure);
      } finally {
        closed = true;
        try {
          DataAccessManager.getManager().closeSession(session);
        } finally {
          if (manager != null && suspended != null) {
            try { manager.resume(suspended); } catch (java.lang.Exception e) {
              throw new Exception(e);
            }
          }
        }
      }
    }
  }
}
