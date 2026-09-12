package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCodeKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;
import com.ofss.fc.infra.das.orm.Session;

/** Read/write contract for one-time HTH API Password Code lifecycle rows. */
public interface IHthApiPasswordCodeRepositoryAdapter
    extends IRepositoryAdapter<HthApiPasswordCode, HthApiPasswordCodeKey> {
  String HTH_API_PASSWORD_CODE_REPOSITORY_ADAPTER = "HTH_API_PASSWORD_CODE_REPOSITORY_ADAPTER";

  String HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER =
      "HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER";

  /** Finds the active snapshot associated with approval-framework re-entry. */
  HthApiPasswordCode findActiveByTransactionId(String transactionId) throws Exception;

  /** Latest logical row for a user, used by masked display regardless of lifecycle status. */
  HthApiPasswordCode findLatestByOwner(String partyId, String userName) throws Exception;

  /** PENDING rows for a user; superseded (OBJECT_STATUS='I') when a newer code is generated. */
  List<HthApiPasswordCode> listPendingByOwner(String partyId, String userName) throws Exception;

  /** ACTIVE row for a user, consumed by first-time setup. */
  HthApiPasswordCode findActiveByOwner(String partyId, String userName) throws Exception;

  /** ACTIVE rows for a user; superseded (INVALID) when a regenerated code is activated. */
  List<HthApiPasswordCode> listActiveByOwner(String partyId, String userName) throws Exception;
  /** Finds an unexpired ACTIVE Code whose attempt limit is not exhausted. Uses the supplied session. */
  List findUsable(Session session, String partyId, String userId, String purpose) throws Exception;

  /** Loads the usable Code identifier and ciphertext for verification. Uses the supplied session. */
  List findUsableCipher(Session session, String partyId, String userId, String purpose) throws Exception;

  /** Loads the latest expired Code for input comparison, excluding used or attempt-exhausted Codes. */
  List findLatestExpiredCipher(Session session, String partyId, String userId, String purpose) throws Exception;

  /** Increments the failed attempt count and invalidates an exhausted ACTIVE or EXPIRED Code. Uses the supplied session. */
  int recordFailedAttempt(Session session, String codeId) throws Exception;

  /** Reserves an ACTIVE Code conditionally; returns the affected row count. Uses the supplied session. */
  int reserve(Session session, String requestId, String codeId) throws Exception;

  /** Consumes only the IN_PROGRESS Code held by the specified request. Uses the supplied session. */
  int consume(Session session, String userId, String codeId, String requestId) throws Exception;

  /** Marks an uncertain request UNKNOWN, or releases a confirmed failed reservation. Uses the supplied session. */
  int failReservation(Session session, String userId, String requestId, String codeId, boolean uncertain) throws Exception;

  /** Supersedes only PENDING Codes belonging to this owner and purpose. Uses the supplied session. */
  int retirePending(Session session, String partyId, String userName, String purpose, String operator) throws Exception;

  /** Locks the Code row and reads lifecycle status for serialized approval. Uses the supplied session. */
  List lockForApproval(Session session, String codeId) throws Exception;

  /** Invalidates the owner's ACTIVE Codes of the same purpose. Uses the supplied session. */
  int retireActive(Session session, String operator, String partyId, String userName, String purpose) throws Exception;

  /** Activates a PENDING Code and anchors its expiry at approval time. Uses the supplied session. */
  int activate(Session session, String transactionId, int expiryHours, String operator, String codeId) throws Exception;
}
