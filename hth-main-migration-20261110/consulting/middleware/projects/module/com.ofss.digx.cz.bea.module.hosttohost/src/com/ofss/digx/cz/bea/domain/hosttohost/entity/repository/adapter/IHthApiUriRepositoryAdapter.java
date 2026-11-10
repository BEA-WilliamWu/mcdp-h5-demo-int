package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUri;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUriKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;

public interface IHthApiUriRepositoryAdapter extends IRepositoryAdapter<HthApiUri, HthApiUriKey> {
	String HTH_API_URI_REPOSITORY_ADAPTER = "HTH_API_URI_REPOSITORY_ADAPTER";
	String HTH_API_URI_LOCAL_REPOSITORY_ADAPTER = "HTH_API_URI_LOCAL_REPOSITORY_ADAPTER";

	HthApiUri read(HthApiUriKey key) throws Exception;

	List<HthApiUri> listActiveByApiMasterId(String apiMasterId) throws Exception;

	void create(HthApiUri object) throws Exception;

	void update(HthApiUri object) throws Exception;

	void delete(HthApiUri object) throws Exception;
}
