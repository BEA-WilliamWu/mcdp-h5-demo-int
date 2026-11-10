package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiUriRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class HthApiUri extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 7115905330568373666L;

	private HthApiUriKey key;

	private String apiMasterId;

	private String apiUri;

	private Long displayOrder;

	private String objectStatus;

	public HthApiUriKey getKey() {
		return key;
	}

	public void setKey(HthApiUriKey key) {
		this.key = key;
	}

	public String getApiMasterId() {
		return apiMasterId;
	}

	public void setApiMasterId(String apiMasterId) {
		this.apiMasterId = apiMasterId;
	}

	public String getApiUri() {
		return apiUri;
	}

	public void setApiUri(String apiUri) {
		this.apiUri = apiUri;
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

	public HthApiUri read(HthApiUriKey apiUriKey) throws Exception {
		HthApiUriRepository repository = HthApiUriRepository.getInstance();
		return repository.read(apiUriKey);
	}

	public List<HthApiUri> listActiveByApiMasterId(String apiMasterId) throws Exception {
		HthApiUriRepository repository = HthApiUriRepository.getInstance();
		return repository.listActiveByApiMasterId(apiMasterId);
	}

	@Override
	protected void validate() {
	}
}
