package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;
import java.lang.Override;
import java.lang.String;

public class HthApiUriKey extends AbstractDomainObjectKey {
	private static final long serialVersionUID = -2143531675114899700L;

	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String keyAsString() {
		return getId();
	}
}
