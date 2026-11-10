package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthRequestRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;

public class HthRequest extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 4147699185734348871L;

	private HthRequestKey key;

	private String transactionId;

	private String referenceNo;

	private String partyId;

	private String actionType;

	private String uamClientId;

	private String hthStatus;

	private String approvalFlowType;

	private String objectStatus;

	private String chargeAccount;

	public HthRequestKey getKey() {
		return key;
	}

	public void setKey(HthRequestKey key) {
		this.key = key;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getReferenceNo() {
		return referenceNo;
	}

	public void setReferenceNo(String referenceNo) {
		this.referenceNo = referenceNo;
	}

	public String getPartyId() {
		return partyId;
	}

	public void setPartyId(String partyId) {
		this.partyId = partyId;
	}

	public String getActionType() {
		return actionType;
	}

	public void setActionType(String actionType) {
		this.actionType = actionType;
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

	public HthRequest read(HthRequestKey requestKey) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		return repository.read(requestKey);
	}

	public HthRequest findActiveByTransactionId(String transactionId) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		return repository.findActiveByTransactionId(transactionId);
	}

	public boolean existsPendingApproval(String partyId) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		return repository.existsPendingApproval(partyId);
	}

	public boolean existsPendingApprovalByUamClientId(String uamClientId) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		return repository.existsPendingApprovalByUamClientId(uamClientId);
	}

	public void create(HthRequest request) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		repository.create(request);
	}

	public void update(HthRequest request) throws Exception {
		HthRequestRepository repository = HthRequestRepository.getInstance();
		repository.update(request);
	}

	@Override
	protected void validate() {
	}
}
