package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistry;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistryKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;

public interface IHthUamClientRegistryRepositoryAdapter
		extends IRepositoryAdapter<HthUamClientRegistry, HthUamClientRegistryKey> {
	String HTH_UAM_CLIENT_REGISTRY_REPOSITORY_ADAPTER = "HTH_UAM_CLIENT_REGISTRY_REPOSITORY_ADAPTER";
	String HTH_UAM_CLIENT_REGISTRY_LOCAL_REPOSITORY_ADAPTER = "HTH_UAM_CLIENT_REGISTRY_LOCAL_REPOSITORY_ADAPTER";

	HthUamClientRegistry read(HthUamClientRegistryKey key) throws Exception;

	boolean existsRegisteredUamClientId(String uamClientId) throws Exception;

	void registerUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception;

	void retireUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception;

	void create(HthUamClientRegistry object) throws Exception;

	void update(HthUamClientRegistry object) throws Exception;

	void delete(HthUamClientRegistry object) throws Exception;
}
