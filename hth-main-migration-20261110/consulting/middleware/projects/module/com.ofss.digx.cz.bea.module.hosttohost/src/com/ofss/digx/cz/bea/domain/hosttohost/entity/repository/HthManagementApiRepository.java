package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthManagementApiRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;

public class HthManagementApiRepository
		extends AbstractDomainObjectRepository<HthManagementApi, HthManagementApiKey> {

	public static HthManagementApiRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthManagementApi read(HthManagementApiKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public List<HthManagementApi> listActiveByManagementId(String managementId) throws Exception {
		return repositoryAdapter().listActiveByManagementId(managementId);
	}

	public HthManagementApi findByManagementIdAndApiMasterId(String managementId, String apiMasterId)
			throws Exception {
		return repositoryAdapter().findByManagementIdAndApiMasterId(managementId, apiMasterId);
	}

	@Override
	public void create(HthManagementApi object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthManagementApi object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthManagementApi object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthManagementApiRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthManagementApiRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(
						IHthManagementApiRepositoryAdapter.HTH_MANAGEMENT_API_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthManagementApiRepository _INSTANCE = new HthManagementApiRepository();
	}
}
