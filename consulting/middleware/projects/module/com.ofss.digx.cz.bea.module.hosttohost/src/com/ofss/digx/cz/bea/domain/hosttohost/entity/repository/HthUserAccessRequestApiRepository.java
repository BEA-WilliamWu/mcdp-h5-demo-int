package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserAccessRequestApiRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Domain repository for API selections stored in maker request snapshots. */
public class HthUserAccessRequestApiRepository
    extends AbstractDomainObjectRepository<HthUserAccessRequestApi, HthUserAccessRequestApiKey> {
  public static HthUserAccessRequestApiRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public List<HthUserAccessRequestApi> listByRequestAccountId(String requestAccountId)
      throws Exception {
    return adapter().listByRequestAccountId(requestAccountId);
  }

  public List<HthUserAccessRequestApi> listByRequestId(String requestId) throws Exception {
    return adapter().listByRequestId(requestId);
  }

  @Override
  public HthUserAccessRequestApi read(HthUserAccessRequestApiKey key) throws Exception {
    return adapter().read(key);
  }

  @Override
  public void create(HthUserAccessRequestApi object) throws Exception {
    adapter().create(object);
  }

  @Override
  public void update(HthUserAccessRequestApi object) throws Exception {
    adapter().update(object);
  }

  @Override
  public void delete(HthUserAccessRequestApi object) throws Exception {
    adapter().delete(object);
  }

  private IHthUserAccessRequestApiRepositoryAdapter adapter() throws Exception {
    return (IHthUserAccessRequestApiRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(IHthUserAccessRequestApiRepositoryAdapter
            .HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthUserAccessRequestApiRepository INSTANCE =
        new HthUserAccessRequestApiRepository();
  }
}
