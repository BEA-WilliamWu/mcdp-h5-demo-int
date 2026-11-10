package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiMasterRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;
import java.util.Set;

public class HthApiMasterRepository extends AbstractDomainObjectRepository<HthApiMaster, HthApiMasterKey> {

	public static HthApiMasterRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthApiMaster read(HthApiMasterKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public List<HthApiMaster> listActive() throws Exception {
		return repositoryAdapter().listActive();
	}

	public List<HthApiMaster> listActiveByApiCodes(Set<String> apiCodes) throws Exception {
		return repositoryAdapter().listActiveByApiCodes(apiCodes);
	}

	@Override
	public void create(HthApiMaster object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthApiMaster object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthApiMaster object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthApiMasterRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthApiMasterRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(IHthApiMasterRepositoryAdapter.HTH_API_MASTER_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthApiMasterRepository _INSTANCE = new HthApiMasterRepository();
	}
}
