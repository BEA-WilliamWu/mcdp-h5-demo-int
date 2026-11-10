package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;

public interface IHthRequestRepositoryAdapter extends IRepositoryAdapter<HthRequest, HthRequestKey> {
	String HTH_REQUEST_REPOSITORY_ADAPTER = "HTH_REQUEST_REPOSITORY_ADAPTER";
	String HTH_REQUEST_LOCAL_REPOSITORY_ADAPTER = "HTH_REQUEST_LOCAL_REPOSITORY_ADAPTER";

	HthRequest read(HthRequestKey key) throws Exception;

	HthRequest findActiveByTransactionId(String transactionId) throws Exception;

	boolean existsPendingApproval(String partyId) throws Exception;

	boolean existsPendingApprovalByUamClientId(String uamClientId) throws Exception;

	void create(HthRequest object) throws Exception;

	void update(HthRequest object) throws Exception;

	void delete(HthRequest object) throws Exception;
}
