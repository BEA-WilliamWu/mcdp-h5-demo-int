package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperation;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperationKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiPasswordOperationRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;

/** Repository facade for operation data and session-bound lifecycle operations. */
public class HthApiPasswordOperationRepository {
  public static HthApiPasswordOperationRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public HthApiPasswordOperation read(HthApiPasswordOperationKey key) throws Exception {
    return repositoryAdapter().read(key);
  }

  public List findResult(Session session, String requestId, String partyId, String userId, String operation) throws Exception {
    return repositoryAdapter().findResult(session, requestId, partyId, userId, operation);
  }

  public int reserve(Session session, String requestId, String partyId, String userId, String purpose, String codeId, String backend) throws Exception {
    return repositoryAdapter().reserve(session, requestId, partyId, userId, purpose, codeId, backend);
  }

  public int complete(Session session, String referenceNumber, String userId, String requestId) throws Exception {
    return repositoryAdapter().complete(session, referenceNumber, userId, requestId);
  }

  public int failReservation(Session session, String userId, String requestId, boolean uncertain) throws Exception {
    return repositoryAdapter().failReservation(session, userId, requestId, uncertain);
  }

  private IHthApiPasswordOperationRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthApiPasswordOperationRepositoryAdapter) RepositoryAdapterFactory.getInstance().getRepositoryAdapter(
        IHthApiPasswordOperationRepositoryAdapter.HTH_API_PASSWORD_OPERATION_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthApiPasswordOperationRepository INSTANCE = new HthApiPasswordOperationRepository();
  }
}
