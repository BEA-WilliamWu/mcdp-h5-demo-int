package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/**
 * Effective Current and Savings or Time Deposit account grant for one HTH user and company
 * relationship context.
 *
 * <p>Rows use A/I status for soft replacement and deletion; inactive rows are retained for audit
 * history and may be reactivated when the same business key is approved again.
 */
public class HthUserAccessAccount extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = -3561898781880535440L;

  private HthUserAccessAccountKey key;
  private String partyId;
  private String closeId;
  private String accessPartyId;
  private String linkageType;
  private String accountNumber;
  private String accountNumberFormatted;
  private String productCode;
  private String accountType;
  private String currency;
  private String objectStatus;

  public HthUserAccessAccountKey getKey() {
    return key;
  }

  public void setKey(HthUserAccessAccountKey key) {
    this.key = key;
  }

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getCloseId() {
    return closeId;
  }

  public void setCloseId(String closeId) {
    this.closeId = closeId;
  }

  public String getAccessPartyId() {
    return accessPartyId;
  }

  public void setAccessPartyId(String accessPartyId) {
    this.accessPartyId = accessPartyId;
  }

  public String getLinkageType() {
    return linkageType;
  }

  public void setLinkageType(String linkageType) {
    this.linkageType = linkageType;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getAccountNumberFormatted() {
    return accountNumberFormatted;
  }

  public void setAccountNumberFormatted(String accountNumberFormatted) {
    this.accountNumberFormatted = accountNumberFormatted;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
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
