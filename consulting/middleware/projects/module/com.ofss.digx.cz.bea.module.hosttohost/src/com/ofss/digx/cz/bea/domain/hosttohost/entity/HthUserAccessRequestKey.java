package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Surrogate UUID key for an HTH access approval request snapshot. */
public class HthUserAccessRequestKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = 2605114359659288558L;

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
