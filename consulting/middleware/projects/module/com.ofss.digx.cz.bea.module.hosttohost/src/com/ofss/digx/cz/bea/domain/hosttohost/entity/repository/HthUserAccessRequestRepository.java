package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessPendingRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserAccessRequestRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Repository facade for immutable request headers and pending workflow queries. */
public class HthUserAccessRequestRepository {
  public static HthUserAccessRequestRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public List<HthUserAccessPendingRecord> listPendingSummary(String partyId, String closeId)
      throws Exception {
    return repositoryAdapter().listPendingSummary(partyId, closeId);
  }

  public HthUserAccessRequest findActiveByTransactionId(String transactionId) throws Exception {
    return repositoryAdapter().findActiveByTransactionId(transactionId);
  }

  public HthUserAccessRequest findPendingByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception {
    return repositoryAdapter().findPendingByContext(partyId, closeId, accessPartyId, linkageType);
  }

  private IHthUserAccessRequestRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthUserAccessRequestRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(
            IHthUserAccessRequestRepositoryAdapter.HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthUserAccessRequestRepository INSTANCE =
        new HthUserAccessRequestRepository();
  }
}
