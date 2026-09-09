package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordState;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordStateKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiPasswordStateRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;

/** Repository facade for state data and session-bound lifecycle operations. */
public class HthApiPasswordStateRepository {
  public static HthApiPasswordStateRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public HthApiPasswordState read(HthApiPasswordStateKey key) throws Exception {
    return repositoryAdapter().read(key);
  }

  public List findStatus(Session session, String partyId, String userId) throws Exception {
    return repositoryAdapter().findStatus(session, partyId, userId);
  }

  public int complete(Session session, String partyId, String userId, String operation, String requestId, String referenceNumber) throws Exception {
    return repositoryAdapter().complete(session, partyId, userId, operation, requestId, referenceNumber);
  }

  private IHthApiPasswordStateRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthApiPasswordStateRepositoryAdapter) RepositoryAdapterFactory.getInstance().getRepositoryAdapter(
        IHthApiPasswordStateRepositoryAdapter.HTH_API_PASSWORD_STATE_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthApiPasswordStateRepository INSTANCE = new HthApiPasswordStateRepository();
  }
}
