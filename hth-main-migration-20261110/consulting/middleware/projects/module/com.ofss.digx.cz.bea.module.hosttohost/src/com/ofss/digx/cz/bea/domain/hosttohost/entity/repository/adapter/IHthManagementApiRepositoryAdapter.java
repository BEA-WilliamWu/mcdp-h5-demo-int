package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApiKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;

public interface IHthManagementApiRepositoryAdapter
		extends IRepositoryAdapter<HthManagementApi, HthManagementApiKey> {
	String HTH_MANAGEMENT_API_REPOSITORY_ADAPTER = "HTH_MANAGEMENT_API_REPOSITORY_ADAPTER";
	String HTH_MANAGEMENT_API_LOCAL_REPOSITORY_ADAPTER = "HTH_MANAGEMENT_API_LOCAL_REPOSITORY_ADAPTER";

	HthManagementApi read(HthManagementApiKey key) throws Exception;

	List<HthManagementApi> listActiveByManagementId(String managementId) throws Exception;

	HthManagementApi findByManagementIdAndApiMasterId(String managementId, String apiMasterId)
			throws Exception;

	void create(HthManagementApi object) throws Exception;

	void update(HthManagementApi object) throws Exception;

	void delete(HthManagementApi object) throws Exception;
}
