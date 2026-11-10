package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

public class HthUserProfile extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = 4045532367268799811L;

	private HthUserProfileKey key;

	public HthUserProfileKey getKey() {
		return key;
	}

	public void setKey(HthUserProfileKey key) {
		this.key = key;
	}

	@Override
	protected void validate() {
	}
}
