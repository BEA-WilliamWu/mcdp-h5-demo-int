package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredential;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredentialKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiPasswordCredentialRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;

/** Repository facade for credential data and session-bound lifecycle operations. */
public class HthApiPasswordCredentialRepository {
  public static HthApiPasswordCredentialRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public HthApiPasswordCredential read(HthApiPasswordCredentialKey key) throws Exception {
    return repositoryAdapter().read(key);
  }

  public List findStatus(Session session, String partyId, String userId) throws Exception {
    return repositoryAdapter().findStatus(session, partyId, userId);
  }

  public void write(Session session, String partyId, String userId, String operation, String requestId, String passwordHash) throws Exception {
    repositoryAdapter().write(session, partyId, userId, operation, requestId, passwordHash);
  }

  private IHthApiPasswordCredentialRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthApiPasswordCredentialRepositoryAdapter) RepositoryAdapterFactory.getInstance().getRepositoryAdapter(
        IHthApiPasswordCredentialRepositoryAdapter.HTH_API_PASSWORD_CREDENTIAL_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthApiPasswordCredentialRepository INSTANCE = new HthApiPasswordCredentialRepository();
  }
}
