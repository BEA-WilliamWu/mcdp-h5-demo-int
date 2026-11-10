package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthManagementRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;

public class HthManagement extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 6522737563975366098L;

	private HthManagementKey key;

	private String partyId;

	private String uamClientId;

	private String hthStatus;

	private String approvalFlowType;

	private String objectStatus;

	private String chargeAccount;

	public HthManagementKey getKey() {
		return key;
	}

	public void setKey(HthManagementKey key) {
		this.key = key;
	}

	public String getPartyId() {
		return partyId;
	}

	public void setPartyId(String partyId) {
		this.partyId = partyId;
	}

	public String getUamClientId() {
		return uamClientId;
	}

	public void setUamClientId(String uamClientId) {
		this.uamClientId = uamClientId;
	}

	public String getHthStatus() {
		return hthStatus;
	}

	public void setHthStatus(String hthStatus) {
		this.hthStatus = hthStatus;
	}

	public String getApprovalFlowType() {
		return approvalFlowType;
	}

	public void setApprovalFlowType(String approvalFlowType) {
		this.approvalFlowType = approvalFlowType;
	}

	public String getObjectStatus() {
		return objectStatus;
	}

	public void setObjectStatus(String objectStatus) {
		this.objectStatus = objectStatus;
	}

	public String getChargeAccount() {
		return chargeAccount;
	}

	public void setChargeAccount(String chargeAccount) {
		this.chargeAccount = chargeAccount;
	}

	public HthManagement read(HthManagementKey managementKey) throws Exception {
		HthManagementRepository repository = HthManagementRepository.getInstance();
		return repository.read(managementKey);
	}

	public HthManagement findActiveByPartyId(String partyId) throws Exception {
		HthManagementRepository repository = HthManagementRepository.getInstance();
		return repository.findActiveByPartyId(partyId);
	}

	public HthManagement findActiveByUamClientId(String uamClientId) throws Exception {
		HthManagementRepository repository = HthManagementRepository.getInstance();
		return repository.findActiveByUamClientId(uamClientId);
	}

	public void create(HthManagement management) throws Exception {
		HthManagementRepository repository = HthManagementRepository.getInstance();
		repository.create(management);
	}

	public void update(HthManagement management) throws Exception {
		HthManagementRepository repository = HthManagementRepository.getInstance();
		repository.update(management);
	}

	@Override
	protected void validate() {
	}
}
