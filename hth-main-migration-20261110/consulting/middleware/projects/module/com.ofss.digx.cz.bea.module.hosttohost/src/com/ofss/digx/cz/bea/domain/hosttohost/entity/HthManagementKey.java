package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;
import java.lang.Override;
import java.lang.String;

public class HthManagementKey extends AbstractDomainObjectKey {
	private static final long serialVersionUID = 4992983229458487379L;

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
