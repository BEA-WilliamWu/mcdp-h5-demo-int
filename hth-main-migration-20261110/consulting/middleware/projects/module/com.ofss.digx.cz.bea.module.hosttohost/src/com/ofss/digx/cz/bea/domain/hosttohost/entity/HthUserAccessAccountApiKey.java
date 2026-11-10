package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Surrogate UUID key for an effective account/API grant. */
public class HthUserAccessAccountApiKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = 1992413492992481654L;
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String keyAsString() {
    return id;
  }
}
