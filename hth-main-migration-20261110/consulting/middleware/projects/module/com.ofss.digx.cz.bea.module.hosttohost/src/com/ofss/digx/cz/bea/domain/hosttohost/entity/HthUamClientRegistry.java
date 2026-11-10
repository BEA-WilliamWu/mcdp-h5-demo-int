package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUamClientRegistryRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;

public class HthUamClientRegistry extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = -6781150588155718740L;

	private HthUamClientRegistryKey key;

	private String uamClientId;

	private String partyId;

	private String status;

	private String enableTransactionId;

	private String disableTransactionId;

	private String objectStatus;

	public HthUamClientRegistryKey getKey() {
		return key;
	}

	public void setKey(HthUamClientRegistryKey key) {
		this.key = key;
	}

	public String getUamClientId() {
		return uamClientId;
	}

	public void setUamClientId(String uamClientId) {
		this.uamClientId = uamClientId;
	}

	public String getPartyId() {
		return partyId;
	}

	public void setPartyId(String partyId) {
		this.partyId = partyId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getEnableTransactionId() {
		return enableTransactionId;
	}

	public void setEnableTransactionId(String enableTransactionId) {
		this.enableTransactionId = enableTransactionId;
	}

	public String getDisableTransactionId() {
		return disableTransactionId;
	}

	public void setDisableTransactionId(String disableTransactionId) {
		this.disableTransactionId = disableTransactionId;
	}

	public String getObjectStatus() {
		return objectStatus;
	}

	public void setObjectStatus(String objectStatus) {
		this.objectStatus = objectStatus;
	}

	public HthUamClientRegistry read(HthUamClientRegistryKey registryKey) throws Exception {
		HthUamClientRegistryRepository repository = HthUamClientRegistryRepository.getInstance();
		return repository.read(registryKey);
	}

	public boolean existsRegisteredUamClientId(String uamClientId) throws Exception {
		HthUamClientRegistryRepository repository = HthUamClientRegistryRepository.getInstance();
		return repository.existsRegisteredUamClientId(uamClientId);
	}

	public void registerUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		HthUamClientRegistryRepository repository = HthUamClientRegistryRepository.getInstance();
		repository.registerUamClientId(id, partyId, uamClientId, transactionId, userId);
	}

	public void retireUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		HthUamClientRegistryRepository repository = HthUamClientRegistryRepository.getInstance();
		repository.retireUamClientId(id, partyId, uamClientId, transactionId, userId);
	}

	@Override
	protected void validate() {
	}
}
