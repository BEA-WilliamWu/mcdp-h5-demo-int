package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessPendingRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Read/write contract for immutable maker request headers and pending workflow projections. */
public interface IHthUserAccessRequestRepositoryAdapter
    extends IRepositoryAdapter<HthUserAccessRequest, HthUserAccessRequestKey> {
  String HTH_USER_ACCESS_REQUEST_REPOSITORY_ADAPTER = "HTH_USER_ACCESS_REQUEST_REPOSITORY_ADAPTER";

  String HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER =
      "HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER";

  /** Lists pending workflow requests for all valid company contexts of an HTH user. */
  List<HthUserAccessPendingRecord> listPendingSummary(String partyId, String closeId) throws Exception;

  /** Finds the active snapshot associated with approval-framework re-entry. */
  HthUserAccessRequest findActiveByTransactionId(String transactionId) throws Exception;

  /** Prevents a second maker request for the same logical context while one is pending. */
  HthUserAccessRequest findPendingByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception;
}
