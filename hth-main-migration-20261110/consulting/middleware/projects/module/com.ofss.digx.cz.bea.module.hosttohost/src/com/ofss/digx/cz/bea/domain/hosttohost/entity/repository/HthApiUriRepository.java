package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUri;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUriKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiUriRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;

public class HthApiUriRepository extends AbstractDomainObjectRepository<HthApiUri, HthApiUriKey> {

	public static HthApiUriRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthApiUri read(HthApiUriKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public List<HthApiUri> listActiveByApiMasterId(String apiMasterId) throws Exception {
		return repositoryAdapter().listActiveByApiMasterId(apiMasterId);
	}

	@Override
	public void create(HthApiUri object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthApiUri object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthApiUri object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthApiUriRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthApiUriRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(IHthApiUriRepositoryAdapter.HTH_API_URI_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthApiUriRepository _INSTANCE = new HthApiUriRepository();
	}
}
