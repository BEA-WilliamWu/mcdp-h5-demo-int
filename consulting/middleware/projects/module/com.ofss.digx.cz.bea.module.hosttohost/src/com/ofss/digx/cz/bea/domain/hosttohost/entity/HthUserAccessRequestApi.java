package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/**
 * Immutable API selection captured under a request account.
 *
 * <p>Code and name are copied from the catalogue so historical approval display does not change
 * after a catalogue rename; {@code apiMasterId} remains the value used for approval validation.
 */
public class HthUserAccessRequestApi extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 2071946315651332549L;

  private HthUserAccessRequestApiKey key;
  private String hthUserAccessRequestAccountId;
  private String apiMasterId;
  private String apiCode;
  private String apiName;
  private Long displayOrder;
  private String objectStatus;

  public HthUserAccessRequestApiKey getKey() {
    return key;
  }

  public void setKey(HthUserAccessRequestApiKey key) {
    this.key = key;
  }

  public String getHthUserAccessRequestAccountId() {
    return hthUserAccessRequestAccountId;
  }

  public void setHthUserAccessRequestAccountId(String hthUserAccessRequestAccountId) {
    this.hthUserAccessRequestAccountId = hthUserAccessRequestAccountId;
  }

  public String getApiMasterId() {
    return apiMasterId;
  }

  public void setApiMasterId(String apiMasterId) {
    this.apiMasterId = apiMasterId;
  }

  public String getApiCode() {
    return apiCode;
  }

  public void setApiCode(String apiCode) {
    this.apiCode = apiCode;
  }

  public String getApiName() {
    return apiName;
  }

  public void setApiName(String apiName) {
    this.apiName = apiName;
  }

  public Long getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(Long displayOrder) {
    this.displayOrder = displayOrder;
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
