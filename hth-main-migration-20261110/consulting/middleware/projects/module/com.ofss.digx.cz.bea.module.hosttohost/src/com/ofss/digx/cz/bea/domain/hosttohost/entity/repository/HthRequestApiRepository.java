package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthRequestApiRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;

public class HthRequestApiRepository
		extends AbstractDomainObjectRepository<HthRequestApi, HthRequestApiKey> {

	public static HthRequestApiRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	public List<HthRequestApi> listByRequestId(String requestId) throws Exception {
		return repositoryAdapter().listByRequestId(requestId);
	}

	@Override
	public void create(HthRequestApi object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public HthRequestApi read(HthRequestApiKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	@Override
	public void update(HthRequestApi object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthRequestApi object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthRequestApiRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthRequestApiRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(
						IHthRequestApiRepositoryAdapter.HTH_REQUEST_API_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthRequestApiRepository _INSTANCE = new HthRequestApiRepository();
	}
}
