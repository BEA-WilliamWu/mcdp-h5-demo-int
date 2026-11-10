package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthManagementRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;

public class HthManagementRepository extends AbstractDomainObjectRepository<HthManagement, HthManagementKey> {

	public static HthManagementRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthManagement read(HthManagementKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public HthManagement findActiveByPartyId(String partyId) throws Exception {
		return repositoryAdapter().findActiveByPartyId(partyId);
	}

	public HthManagement findActiveByUamClientId(String uamClientId) throws Exception {
		return repositoryAdapter().findActiveByUamClientId(uamClientId);
	}

	@Override
	public void create(HthManagement object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthManagement object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthManagement object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthManagementRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthManagementRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(IHthManagementRepositoryAdapter.HTH_MANAGEMENT_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthManagementRepository _INSTANCE = new HthManagementRepository();
	}
}
