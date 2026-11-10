package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiMasterRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;
import java.util.Set;

public class HthApiMaster extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 4132895560972211455L;

	private HthApiMasterKey key;

	private String apiCode;

	private String apiName;

	private Long displayOrder;

	private String objectStatus;

	public HthApiMasterKey getKey() {
		return key;
	}

	public void setKey(HthApiMasterKey key) {
		this.key = key;
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

	public Long getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(Long displayOrder) {
		this.displayOrder = displayOrder;
	}

	public String getObjectStatus() {
		return objectStatus;
	}

	public void setObjectStatus(String objectStatus) {
		this.objectStatus = objectStatus;
	}

	public HthApiMaster read(HthApiMasterKey apiMasterKey) throws Exception {
		HthApiMasterRepository repository = HthApiMasterRepository.getInstance();
		return repository.read(apiMasterKey);
	}

	public List<HthApiMaster> listActive() throws Exception {
		HthApiMasterRepository repository = HthApiMasterRepository.getInstance();
		return repository.listActive();
	}

	public List<HthApiMaster> listActiveByApiCodes(Set<String> apiCodes) throws Exception {
		HthApiMasterRepository repository = HthApiMasterRepository.getInstance();
		return repository.listActiveByApiCodes(apiCodes);
	}

	@Override
	protected void validate() {
	}
}
