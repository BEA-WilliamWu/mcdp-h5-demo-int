package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

public class HthUserProfileKey extends AbstractDomainObjectKey {
	private static final long serialVersionUID = -7497241192722819437L;

	private String partyId;

	private String closeId;

	public String getPartyId() {
		return partyId;
	}

	public void setPartyId(String partyId) {
		this.partyId = partyId;
	}

	public String getCloseId() {
		return closeId;
	}

	public void setCloseId(String closeId) {
		this.closeId = closeId;
	}

	@Override
	public String keyAsString() {
		return getPartyId() + "#" + getCloseId();
	}
}
