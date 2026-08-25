package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserAccessRequestAccountRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Domain repository for account selections stored in maker request snapshots. */
public class HthUserAccessRequestAccountRepository extends
    AbstractDomainObjectRepository<HthUserAccessRequestAccount, HthUserAccessRequestAccountKey> {
  public static HthUserAccessRequestAccountRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public List<HthUserAccessRequestAccount> listByRequestId(String requestId) throws Exception {
    return adapter().listByRequestId(requestId);
  }

  @Override
  public HthUserAccessRequestAccount read(HthUserAccessRequestAccountKey key) throws Exception {
    return adapter().read(key);
  }

  @Override
  public void create(HthUserAccessRequestAccount object) throws Exception {
    adapter().create(object);
  }

  @Override
  public void update(HthUserAccessRequestAccount object) throws Exception {
    adapter().update(object);
  }

  @Override
  public void delete(HthUserAccessRequestAccount object) throws Exception {
    adapter().delete(object);
  }

  private IHthUserAccessRequestAccountRepositoryAdapter adapter() throws Exception {
    return (IHthUserAccessRequestAccountRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(IHthUserAccessRequestAccountRepositoryAdapter
            .HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthUserAccessRequestAccountRepository INSTANCE =
        new HthUserAccessRequestAccountRepository();
  }
}
