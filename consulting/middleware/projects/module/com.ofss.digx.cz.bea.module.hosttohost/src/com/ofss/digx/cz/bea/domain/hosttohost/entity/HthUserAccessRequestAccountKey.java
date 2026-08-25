package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Surrogate UUID key for a request account snapshot. */
public class HthUserAccessRequestAccountKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = -4503015777975515608L;
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
