package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistry;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistryKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUamClientRegistryRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;

public class HthUamClientRegistryRepository
		extends AbstractDomainObjectRepository<HthUamClientRegistry, HthUamClientRegistryKey> {

	public static HthUamClientRegistryRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthUamClientRegistry read(HthUamClientRegistryKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public boolean existsRegisteredUamClientId(String uamClientId) throws Exception {
		return repositoryAdapter().existsRegisteredUamClientId(uamClientId);
	}

	public void registerUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		repositoryAdapter().registerUamClientId(id, partyId, uamClientId, transactionId, userId);
	}

	public void retireUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		repositoryAdapter().retireUamClientId(id, partyId, uamClientId, transactionId, userId);
	}

	@Override
	public void create(HthUamClientRegistry object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthUamClientRegistry object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthUamClientRegistry object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthUamClientRegistryRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthUamClientRegistryRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(
						IHthUamClientRegistryRepositoryAdapter.HTH_UAM_CLIENT_REGISTRY_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthUamClientRegistryRepository _INSTANCE = new HthUamClientRegistryRepository();
	}
}
