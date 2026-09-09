package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Database primary key for the HTH API password state row. */
public class HthApiPasswordStateKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = 1L;

  private String partyId;
  private String userId;

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @Override
  public String keyAsString() {
    return partyId + "#" + userId;
  }
}
