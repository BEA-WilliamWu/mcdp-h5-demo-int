package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;

public interface IHthManagementRepositoryAdapter extends IRepositoryAdapter<HthManagement, HthManagementKey> {
	String HTH_MANAGEMENT_REPOSITORY_ADAPTER = "HTH_MANAGEMENT_REPOSITORY_ADAPTER";
	String HTH_MANAGEMENT_LOCAL_REPOSITORY_ADAPTER = "HTH_MANAGEMENT_LOCAL_REPOSITORY_ADAPTER";

	HthManagement read(HthManagementKey key) throws Exception;

	HthManagement findActiveByPartyId(String partyId) throws Exception;

	HthManagement findActiveByUamClientId(String uamClientId) throws Exception;

	void create(HthManagement object) throws Exception;

	void update(HthManagement object) throws Exception;

	void delete(HthManagement object) throws Exception;
}
