package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Transactional persistence boundary for one-time password codes and operation state. */
final class HthApiPasswordStore {
  static final String STATE_ACTIVE = "ACTIVE";
  static final String STATE_NOT_SETUP = "NOT_SETUP";

  String findCredentialState(String partyId, String userId) throws Exception {
    SessionLease lease = open(false);
    try {
      Query query = lease.session.createSQLQuery(
          "SELECT CREDENTIAL_STATUS FROM HTH_BEA.HTH_API_PASSWORD_STATE "
              + "WHERE PARTY_ID = ? AND USER_ID = ? AND OBJECT_STATUS = 'A'");
      query.setParameter(1, partyId);
      query.setParameter(2, userId);
      query.setMaxResults(1);
      List rows = query.list();
      return rows == null || rows.isEmpty() ? STATE_NOT_SETUP : string(rows.get(0));
    } finally {
      lease.close(false);
    }
  }

  boolean hasUsableCode(String partyId, String userId, String purpose) throws Exception {
    SessionLease lease = open(false);
    try {
      Query query = lease.session.createSQLQuery(
          "SELECT ID FROM HTH_BEA.HTH_API_PASSWORD_CODE "
              + "WHERE PARTY_ID = ? AND USER_ID = ? AND PURPOSE = ? "
              + "AND STATUS = 'ACTIVE' AND OBJECT_STATUS = 'A' AND EXPIRES_AT > SYSDATE "
              + "AND FAILED_ATTEMPTS < MAX_ATTEMPTS ORDER BY CREATION_DATE DESC");
      query.setParameter(1, partyId);
      query.setParameter(2, userId);
      query.setParameter(3, purpose);
      query.setMaxResults(1);
      List rows = query.list();
      return rows != null && !rows.isEmpty();
    } finally {
      lease.close(false);
    }
  }

  OperationResult findSuccessfulOperation(String requestId, String partyId, String userId,
      String operation) throws Exception {
    SessionLease lease = open(false);
    try {
      Query query = lease.session.createSQLQuery(
          "SELECT STATUS, REFERENCE_NUMBER FROM HTH_BEA.HTH_API_PASSWORD_OPERATION "
              + "WHERE REQUEST_ID = ? AND PARTY_ID = ? AND USER_ID = ? AND OPERATION = ?");
      query.setParameter(1, requestId);
      query.setParameter(2, partyId);
      query.setParameter(3, userId);
      query.setParameter(4, operation);
      query.setMaxResults(1);
      List rows = query.list();
      if (rows == null || rows.isEmpty()) {
        return null;
      }
      Object[] row = (Object[]) rows.get(0);
      return new OperationResult(string(row[0]), string(row[1]));
    } finally {
      lease.close(false);
    }
  }

  String validateAndReserveCode(String partyId, String userId, String purpose, String code,
      String requestId) throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      Query query = lease.session.createSQLQuery(
          "SELECT ID, CODE_HASH, CODE_SALT FROM HTH_BEA.HTH_API_PASSWORD_CODE "
              + "WHERE PARTY_ID = ? AND USER_ID = ? AND PURPOSE = ? "
              + "AND STATUS = 'ACTIVE' AND OBJECT_STATUS = 'A' AND EXPIRES_AT > SYSDATE "
              + "AND FAILED_ATTEMPTS < MAX_ATTEMPTS ORDER BY CREATION_DATE DESC");
      query.setParameter(1, partyId);
      query.setParameter(2, userId);
      query.setParameter(3, purpose);
      query.setMaxResults(1);
      List rows = query.list();
      if (rows == null || rows.isEmpty()) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_003");
      }
      Object[] row = (Object[]) rows.get(0);
      String codeId = string(row[0]);
      if (!constantTimeEquals(string(row[1]), digest(string(row[2]), code))) {
        Query failed = lease.session.createSQLQuery(
            "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET FAILED_ATTEMPTS = FAILED_ATTEMPTS + 1, "
                + "STATUS = CASE WHEN FAILED_ATTEMPTS + 1 >= MAX_ATTEMPTS THEN 'LOCKED' "
                + "ELSE STATUS END, LAST_UPDATE_DATE = SYSDATE "
                + "WHERE ID = ? AND STATUS = 'ACTIVE'");
        failed.setParameter(1, codeId);
        failed.executeUpdate();
        success = true;
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_002");
      }

      Query reserve = lease.session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'IN_PROGRESS', REQUEST_ID = ?, "
              + "LAST_UPDATE_DATE = SYSDATE WHERE ID = ? AND STATUS = 'ACTIVE' "
              + "AND EXPIRES_AT > SYSDATE");
      reserve.setParameter(1, requestId);
      reserve.setParameter(2, codeId);
      if (reserve.executeUpdate() != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      Query operation = lease.session.createSQLQuery(
          "INSERT INTO HTH_BEA.HTH_API_PASSWORD_OPERATION "
              + "(REQUEST_ID, PARTY_ID, USER_ID, OPERATION, CODE_ID, STATUS, CREATED_BY, "
              + "CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, OBJECT_STATUS, "
              + "OBJECT_VERSION_NUMBER) VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, SYSDATE, ?, "
              + "SYSDATE, 'A', 1)");
      operation.setParameter(1, requestId);
      operation.setParameter(2, partyId);
      operation.setParameter(3, userId);
      operation.setParameter(4, purpose);
      operation.setParameter(5, codeId);
      operation.setParameter(6, userId);
      operation.setParameter(7, userId);
      operation.executeUpdate();
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

  void complete(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber) throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      Query code = lease.session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'USED', USED_AT = SYSDATE, "
              + "LAST_UPDATED_BY = ?, LAST_UPDATE_DATE = SYSDATE WHERE ID = ? "
              + "AND STATUS = 'IN_PROGRESS' AND REQUEST_ID = ?");
      code.setParameter(1, userId);
      code.setParameter(2, codeId);
      code.setParameter(3, requestId);
      if (code.executeUpdate() != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      Query state = lease.session.createSQLQuery(
          "MERGE INTO HTH_BEA.HTH_API_PASSWORD_STATE T USING "
              + "(SELECT ? PARTY_ID, ? USER_ID FROM DUAL) S "
              + "ON (T.PARTY_ID = S.PARTY_ID AND T.USER_ID = S.USER_ID) "
              + "WHEN MATCHED THEN UPDATE SET T.CREDENTIAL_STATUS = 'ACTIVE', "
              + "T.CREDENTIAL_VERSION = NVL(T.CREDENTIAL_VERSION, 0) + 1, "
              + "T.LAST_RESET_AT = CASE WHEN ? = 'RESET' THEN SYSDATE ELSE T.LAST_RESET_AT END, "
              + "T.SETUP_AT = CASE WHEN T.SETUP_AT IS NULL THEN SYSDATE ELSE T.SETUP_AT END, "
              + "T.LAST_REQUEST_ID = ?, T.LAST_REFERENCE_NUMBER = ?, T.LAST_UPDATED_BY = ?, "
              + "T.LAST_UPDATE_DATE = SYSDATE, T.OBJECT_STATUS = 'A' "
              + "WHEN NOT MATCHED THEN INSERT (PARTY_ID, USER_ID, CREDENTIAL_STATUS, "
              + "CREDENTIAL_VERSION, SETUP_AT, LAST_RESET_AT, LAST_REQUEST_ID, "
              + "LAST_REFERENCE_NUMBER, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, "
              + "LAST_UPDATE_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER) "
              + "VALUES (?, ?, 'ACTIVE', 1, SYSDATE, CASE WHEN ? = 'RESET' THEN SYSDATE END, "
              + "?, ?, ?, SYSDATE, ?, SYSDATE, 'A', 1)");
      int i = 1;
      state.setParameter(i++, partyId);
      state.setParameter(i++, userId);
      state.setParameter(i++, operation);
      state.setParameter(i++, requestId);
      state.setParameter(i++, referenceNumber);
      state.setParameter(i++, userId);
      state.setParameter(i++, partyId);
      state.setParameter(i++, userId);
      state.setParameter(i++, operation);
      state.setParameter(i++, requestId);
      state.setParameter(i++, referenceNumber);
      state.setParameter(i++, userId);
      state.setParameter(i++, userId);
      state.executeUpdate();

      Query op = lease.session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_OPERATION SET STATUS = 'SUCCESS', "
              + "REFERENCE_NUMBER = ?, LAST_UPDATED_BY = ?, LAST_UPDATE_DATE = SYSDATE "
              + "WHERE REQUEST_ID = ? AND STATUS = 'IN_PROGRESS'");
      op.setParameter(1, referenceNumber);
      op.setParameter(2, userId);
      op.setParameter(3, requestId);
      op.executeUpdate();
      success = true;
    } catch (Exception e) {
      throw e;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  void fail(String userId, String requestId, String codeId, boolean uncertain) throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      Query code = lease.session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = ?, LAST_UPDATED_BY = ?, "
              + "LAST_UPDATE_DATE = SYSDATE WHERE ID = ? AND STATUS = 'IN_PROGRESS' "
              + "AND REQUEST_ID = ?");
      code.setParameter(1, uncertain ? "UNKNOWN" : "ACTIVE");
      code.setParameter(2, userId);
      code.setParameter(3, codeId);
      code.setParameter(4, requestId);
      code.executeUpdate();

      Query op = lease.session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_OPERATION SET STATUS = ?, LAST_UPDATED_BY = ?, "
              + "LAST_UPDATE_DATE = SYSDATE WHERE REQUEST_ID = ? AND STATUS = 'IN_PROGRESS'");
      op.setParameter(1, uncertain ? "UNKNOWN" : "FAILED");
      op.setParameter(2, userId);
      op.setParameter(3, requestId);
      op.executeUpdate();
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  private String digest(String salt, String code) throws java.lang.Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] value = digest.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(value.length * 2);
    for (byte item : value) {
      hex.append(String.format("%02x", item & 0xff));
    }
    return hex.toString();
  }

  private boolean constantTimeEquals(String left, String right) {
    return left != null && right != null && MessageDigest.isEqual(
        left.toLowerCase().getBytes(StandardCharsets.US_ASCII),
        right.toLowerCase().getBytes(StandardCharsets.US_ASCII));
  }

  private String string(Object value) {
    return value == null ? null : String.valueOf(value);
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
          DataAccessManager.getManager().rollbackTransaction();
        }
      } finally {
        closed = true;
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }
}
