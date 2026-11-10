package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Surrogate UUID key for an effective HTH account grant. */
public class HthUserAccessAccountKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = -6304709080962735935L;
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
