package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.datatype.Date;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/**
 * One-time HTH API Password Code lifecycle row (BCOH2H-787).
 *
 * <p>A maker generate call persists the row as PENDING inside the HTH maker/checker flow; checker
 * approval of the original user-maintenance transaction activates it and anchors {@code expiryTime}. First-time setup
 * consumes it (USED); regeneration supersedes it (INVALID); passing {@code expiryTime} renders it
 * EXPIRED at read time. The plaintext code never leaves the application: {@code codeCipher} holds
 * the reversible AES-256-GCM ciphertext used for authorized reveal and verification.
 */
public class HthApiPasswordCode extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 4519082374651287345L;

  private HthApiPasswordCodeKey key;

  private String purpose = "SETUP";

  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }

  private String partyId;
  private String userName;
  private String transactionId;
  private String codeCipher;
  private String status;
  private Integer attemptCount;
  private Date expiryTime;
  private Date usedTime;
  private String objectStatus;

  public HthApiPasswordCodeKey getKey() {
    return key;
  }

  public void setKey(HthApiPasswordCodeKey key) {
    this.key = key;
  }

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getCodeCipher() {
    return codeCipher;
  }

  public void setCodeCipher(String codeCipher) {
    this.codeCipher = codeCipher;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer attemptCount) {
    this.attemptCount = attemptCount;
  }

  public Date getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(Date expiryTime) {
    this.expiryTime = expiryTime;
  }

  public Date getUsedTime() {
    return usedTime;
  }

  public void setUsedTime(Date usedTime) {
    this.usedTime = usedTime;
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
