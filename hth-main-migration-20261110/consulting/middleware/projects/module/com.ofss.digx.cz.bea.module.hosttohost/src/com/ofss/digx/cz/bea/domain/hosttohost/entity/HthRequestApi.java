package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthRequestApiRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class HthRequestApi extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 6644479914492525034L;

	private HthRequestApiKey key;

	private String hthRequestId;

	private String apiMasterId;

	private String apiCode;

	private String apiName;

	private String objectStatus;

	public HthRequestApiKey getKey() {
		return key;
	}

	public void setKey(HthRequestApiKey key) {
		this.key = key;
	}

	public String getHthRequestId() {
		return hthRequestId;
	}

	public void setHthRequestId(String hthRequestId) {
		this.hthRequestId = hthRequestId;
	}

	public String getApiMasterId() {
		return apiMasterId;
	}

	public void setApiMasterId(String apiMasterId) {
		this.apiMasterId = apiMasterId;
	}

	public String getApiCode() {
		return apiCode;
	}

	public void setApiCode(String apiCode) {
		this.apiCode = apiCode;
	}

	public String getApiName() {
		return apiName;
	}

	public void setApiName(String apiName) {
		this.apiName = apiName;
	}

	public String getObjectStatus() {
		return objectStatus;
	}

	public void setObjectStatus(String objectStatus) {
		this.objectStatus = objectStatus;
	}

	public List<HthRequestApi> listByRequestId(String requestId) throws Exception {
		HthRequestApiRepository repository = HthRequestApiRepository.getInstance();
		return repository.listByRequestId(requestId);
	}

	public void create(HthRequestApi requestApi) throws Exception {
		HthRequestApiRepository repository = HthRequestApiRepository.getInstance();
		repository.create(requestApi);
	}

	@Override
	protected void validate() {
	}
}
