package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Database primary key for the HTH API password operation row. */
public class HthApiPasswordOperationKey extends AbstractDomainObjectKey {
  private static final long serialVersionUID = 1L;

  private String requestId;

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  @Override
  public String keyAsString() {
    return requestId;
  }
}
