package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Effective API grant attached to an HTH user account grant. */
public class HthUserAccessAccountApi extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 2043808701134254688L;

  private HthUserAccessAccountApiKey key;
  private String hthUserAccessAccountId;
  private String apiMasterId;
  private String objectStatus;

  public HthUserAccessAccountApiKey getKey() {
    return key;
  }

  public void setKey(HthUserAccessAccountApiKey key) {
    this.key = key;
  }

  public String getHthUserAccessAccountId() {
    return hthUserAccessAccountId;
  }

  public void setHthUserAccessAccountId(String hthUserAccessAccountId) {
    this.hthUserAccessAccountId = hthUserAccessAccountId;
  }

  public String getApiMasterId() {
    return apiMasterId;
  }

  public void setApiMasterId(String apiMasterId) {
    this.apiMasterId = apiMasterId;
  }

  public String getObjectStatus() {
    return objectStatus;
  }

  public void setObjectStatus(String objectStatus) {
    this.objectStatus = objectStatus;
  }

  @Override
  protected void validate() {
  }
}
