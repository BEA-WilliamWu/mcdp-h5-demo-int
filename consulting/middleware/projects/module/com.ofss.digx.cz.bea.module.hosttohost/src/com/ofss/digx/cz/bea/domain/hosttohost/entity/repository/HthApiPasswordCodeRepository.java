package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiPasswordCodeRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Repository facade for one-time HTH API Password Code lifecycle rows. */
public class HthApiPasswordCodeRepository {
  public static HthApiPasswordCodeRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public HthApiPasswordCode findActiveByTransactionId(String transactionId) throws Exception {
    return repositoryAdapter().findActiveByTransactionId(transactionId);
  }

  public HthApiPasswordCode findLatestByOwner(String partyId, String userName) throws Exception {
    return repositoryAdapter().findLatestByOwner(partyId, userName);
  }

  public List<HthApiPasswordCode> listPendingByOwner(String partyId, String userName)
      throws Exception {
    return repositoryAdapter().listPendingByOwner(partyId, userName);
  }

  public HthApiPasswordCode findActiveByOwner(String partyId, String userName) throws Exception {
    return repositoryAdapter().findActiveByOwner(partyId, userName);
  }

  private IHthApiPasswordCodeRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthApiPasswordCodeRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(
            IHthApiPasswordCodeRepositoryAdapter.HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthApiPasswordCodeRepository INSTANCE =
        new HthApiPasswordCodeRepository();
  }
}
