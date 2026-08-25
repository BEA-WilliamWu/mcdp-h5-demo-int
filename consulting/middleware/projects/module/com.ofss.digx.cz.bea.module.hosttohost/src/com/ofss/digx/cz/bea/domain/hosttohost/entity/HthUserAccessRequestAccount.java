package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Immutable account selection captured under a maker approval request. */
public class HthUserAccessRequestAccount extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = -8935485214142041263L;

  private HthUserAccessRequestAccountKey key;
  private String hthUserAccessRequestId;
  private String accountNumber;
  private String accountType;
  private String currency;
  private Long displayOrder;
  private String objectStatus;

  public HthUserAccessRequestAccountKey getKey() {
    return key;
  }

  public void setKey(HthUserAccessRequestAccountKey key) {
    this.key = key;
  }

  public String getHthUserAccessRequestId() {
    return hthUserAccessRequestId;
  }

  public void setHthUserAccessRequestId(String hthUserAccessRequestId) {
    this.hthUserAccessRequestId = hthUserAccessRequestId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getAccountType() {
    return accountType;
  }

  public void setAccountType(String accountType) {
    this.accountType = accountType;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
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
