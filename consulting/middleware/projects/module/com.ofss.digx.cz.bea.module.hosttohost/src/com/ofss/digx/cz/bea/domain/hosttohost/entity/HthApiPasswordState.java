package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Persistent HTH API password state data; lifecycle writes use conditional repository operations. */
public class HthApiPasswordState extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 1L;

  private HthApiPasswordStateKey key;
  private String credentialStatus;
  private Long credentialVersion;
  private com.ofss.fc.datatype.Date setupAt;
  private com.ofss.fc.datatype.Date lastResetAt;
  private String lastRequestId;
  private String lastReferenceNumber;
  private String createdBy;
  private com.ofss.fc.datatype.Date creationDate;
  private String lastUpdatedBy;
  private com.ofss.fc.datatype.Date lastUpdatedDate;
  private String objectStatus;
  private Long objectVersionNumber;

  public HthApiPasswordStateKey getKey() {
    return key;
  }

  public void setKey(HthApiPasswordStateKey key) {
    this.key = key;
  }

  public String getCredentialStatus() {
    return credentialStatus;
  }

  public void setCredentialStatus(String credentialStatus) {
    this.credentialStatus = credentialStatus;
  }

  public Long getCredentialVersion() {
    return credentialVersion;
  }

  public void setCredentialVersion(Long credentialVersion) {
    this.credentialVersion = credentialVersion;
  }

  public com.ofss.fc.datatype.Date getSetupAt() {
    return setupAt;
  }

  public void setSetupAt(com.ofss.fc.datatype.Date setupAt) {
    this.setupAt = setupAt;
  }

  public com.ofss.fc.datatype.Date getLastResetAt() {
    return lastResetAt;
  }

  public void setLastResetAt(com.ofss.fc.datatype.Date lastResetAt) {
    this.lastResetAt = lastResetAt;
  }

  public String getLastRequestId() {
    return lastRequestId;
  }

  public void setLastRequestId(String lastRequestId) {
    this.lastRequestId = lastRequestId;
  }

  public String getLastReferenceNumber() {
    return lastReferenceNumber;
  }

  public void setLastReferenceNumber(String lastReferenceNumber) {
    this.lastReferenceNumber = lastReferenceNumber;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public com.ofss.fc.datatype.Date getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(com.ofss.fc.datatype.Date creationDate) {
    this.creationDate = creationDate;
  }

  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  public com.ofss.fc.datatype.Date getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  public void setLastUpdatedDate(com.ofss.fc.datatype.Date lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
  }

  public String getObjectStatus() {
    return objectStatus;
  }

  public void setObjectStatus(String objectStatus) {
    this.objectStatus = objectStatus;
  }

  public Long getObjectVersionNumber() {
    return objectVersionNumber;
  }

  public void setObjectVersionNumber(Long objectVersionNumber) {
    this.objectVersionNumber = objectVersionNumber;
  }

  /** Required domain hook; business validation precedes conditional repository writes. */
  @Override
  protected void validate() {
  }
}
