package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;
import java.util.Set;

public interface IHthApiMasterRepositoryAdapter extends IRepositoryAdapter<HthApiMaster, HthApiMasterKey> {
	String HTH_API_MASTER_REPOSITORY_ADAPTER = "HTH_API_MASTER_REPOSITORY_ADAPTER";
	String HTH_API_MASTER_LOCAL_REPOSITORY_ADAPTER = "HTH_API_MASTER_LOCAL_REPOSITORY_ADAPTER";

	HthApiMaster read(HthApiMasterKey key) throws Exception;

	List<HthApiMaster> listActive() throws Exception;

	List<HthApiMaster> listActiveByApiCodes(Set<String> apiCodes) throws Exception;

	void create(HthApiMaster object) throws Exception;

	void update(HthApiMaster object) throws Exception;

	void delete(HthApiMaster object) throws Exception;
}
