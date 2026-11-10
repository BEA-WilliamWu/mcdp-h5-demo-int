package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApiKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;

public interface IHthRequestApiRepositoryAdapter
		extends IRepositoryAdapter<HthRequestApi, HthRequestApiKey> {
	String HTH_REQUEST_API_REPOSITORY_ADAPTER = "HTH_REQUEST_API_REPOSITORY_ADAPTER";
	String HTH_REQUEST_API_LOCAL_REPOSITORY_ADAPTER = "HTH_REQUEST_API_LOCAL_REPOSITORY_ADAPTER";

	HthRequestApi read(HthRequestApiKey key) throws Exception;

	List<HthRequestApi> listByRequestId(String requestId) throws Exception;

	void create(HthRequestApi object) throws Exception;

	void update(HthRequestApi object) throws Exception;

	void delete(HthRequestApi object) throws Exception;
}
