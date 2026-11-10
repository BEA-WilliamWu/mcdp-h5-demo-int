package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthManagementApiRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class HthManagementApi extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 4272589761003508718L;

	private HthManagementApiKey key;

	private String hthManagementId;

	private String apiMasterId;

	private String objectStatus;

	public HthManagementApiKey getKey() {
		return key;
	}

	public void setKey(HthManagementApiKey key) {
		this.key = key;
	}

	public String getHthManagementId() {
		return hthManagementId;
	}

	public void setHthManagementId(String hthManagementId) {
		this.hthManagementId = hthManagementId;
	}

	public String getApiMasterId() {
		return apiMasterId;
	}

	public void setApiMasterId(String apiMasterId) {
		this.apiMasterId = apiMasterId;
	}

	public String getObjectStatus() {
		return objectStatus;
	}

	public void setObjectStatus(String objectStatus) {
		this.objectStatus = objectStatus;
	}

	public HthManagementApi read(HthManagementApiKey managementApiKey) throws Exception {
		HthManagementApiRepository repository = HthManagementApiRepository.getInstance();
		return repository.read(managementApiKey);
	}

	public List<HthManagementApi> listActiveByManagementId(String managementId) throws Exception {
		HthManagementApiRepository repository = HthManagementApiRepository.getInstance();
		return repository.listActiveByManagementId(managementId);
	}

	public HthManagementApi findByManagementIdAndApiMasterId(String managementId, String apiMasterId)
			throws Exception {
		HthManagementApiRepository repository = HthManagementApiRepository.getInstance();
		return repository.findByManagementIdAndApiMasterId(managementId, apiMasterId);
	}

	public void create(HthManagementApi managementApi) throws Exception {
		HthManagementApiRepository repository = HthManagementApiRepository.getInstance();
		repository.create(managementApi);
	}

	public void update(HthManagementApi managementApi) throws Exception {
		HthManagementApiRepository repository = HthManagementApiRepository.getInstance();
		repository.update(managementApi);
	}

	@Override
	protected void validate() {
	}
}
