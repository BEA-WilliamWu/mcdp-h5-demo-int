package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Persistent HTH API password operation data; lifecycle writes use conditional repository operations. */
public class HthApiPasswordOperation extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 1L;

  private HthApiPasswordOperationKey key;
  private String partyId;
  private String userId;
  private String operation;
  private String storageBackend;
  private String codeId;
  private String status;
  private String referenceNumber;
  private String createdBy;
  private com.ofss.fc.datatype.Date creationDate;
  private String lastUpdatedBy;
  private com.ofss.fc.datatype.Date lastUpdatedDate;
  private String objectStatus;
  private Long objectVersionNumber;

  public HthApiPasswordOperationKey getKey() {
    return key;
  }

  public void setKey(HthApiPasswordOperationKey key) {
    this.key = key;
  }

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

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public String getStorageBackend() {
    return storageBackend;
  }

  public void setStorageBackend(String storageBackend) {
    this.storageBackend = storageBackend;
  }

  public String getCodeId() {
    return codeId;
  }

  public void setCodeId(String codeId) {
    this.codeId = codeId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
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
