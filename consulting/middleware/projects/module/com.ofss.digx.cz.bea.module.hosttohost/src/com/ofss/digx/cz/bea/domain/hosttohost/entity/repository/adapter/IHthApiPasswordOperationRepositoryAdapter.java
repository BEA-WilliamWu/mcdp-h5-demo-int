package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperation;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperationKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;

/** Session-bound persistence contract for operation data. */
public interface IHthApiPasswordOperationRepositoryAdapter extends IRepositoryAdapter<HthApiPasswordOperation, HthApiPasswordOperationKey> {
  String HTH_API_PASSWORD_OPERATION_LOCAL_REPOSITORY_ADAPTER = "HTH_API_PASSWORD_OPERATION_LOCAL_REPOSITORY_ADAPTER";
  /** Reads the idempotent operation result and its storage backend for the owner. Uses the supplied session. */
  List findResult(Session session, String requestId, String partyId, String userId, String operation) throws Exception;

  /** Inserts an IN_PROGRESS operation bound to its owner, Code and selected backend. Uses the supplied session. */
  int reserve(Session session, String requestId, String partyId, String userId, String purpose, String codeId, String backend) throws Exception;

  /** Marks only an IN_PROGRESS operation SUCCESS; returns the affected row count. Uses the supplied session. */
  int complete(Session session, String referenceNumber, String userId, String requestId) throws Exception;

  /** Marks an uncertain request UNKNOWN, or releases a confirmed failed reservation. Uses the supplied session. */
  int failReservation(Session session, String userId, String requestId, boolean uncertain) throws Exception;
}
