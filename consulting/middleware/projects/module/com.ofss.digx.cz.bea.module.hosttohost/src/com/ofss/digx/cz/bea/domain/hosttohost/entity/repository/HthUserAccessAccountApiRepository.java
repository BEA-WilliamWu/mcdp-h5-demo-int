package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserAccessAccountApiRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Domain repository for effective account/API grants. */
public class HthUserAccessAccountApiRepository
    extends AbstractDomainObjectRepository<HthUserAccessAccountApi, HthUserAccessAccountApiKey> {
  public static HthUserAccessAccountApiRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public List<HthUserAccessAccountApi> listByAccountId(String accountId) throws Exception {
    return adapter().listByAccountId(accountId);
  }

  public HthUserAccessAccountApi findByAccountIdAndApiMasterId(String accountId,
      String apiMasterId) throws Exception {
    return adapter().findByAccountIdAndApiMasterId(accountId, apiMasterId);
  }

  @Override
  public HthUserAccessAccountApi read(HthUserAccessAccountApiKey key) throws Exception {
    return adapter().read(key);
  }

  @Override
  public void create(HthUserAccessAccountApi object) throws Exception {
    adapter().create(object);
  }

  @Override
  public void update(HthUserAccessAccountApi object) throws Exception {
    adapter().update(object);
  }

  @Override
  public void delete(HthUserAccessAccountApi object) throws Exception {
    adapter().delete(object);
  }

  private IHthUserAccessAccountApiRepositoryAdapter adapter() throws Exception {
    return (IHthUserAccessAccountApiRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(IHthUserAccessAccountApiRepositoryAdapter
            .HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthUserAccessAccountApiRepository INSTANCE =
        new HthUserAccessAccountApiRepository();
  }
}
