package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Persistent HTH API password credential data; lifecycle writes use conditional repository operations. */
public class HthApiPasswordCredential extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 1L;

  private HthApiPasswordCredentialKey key;
  private String passwordHash;
  private String credentialStatus;
  private Long credentialVersion;
  private String lastRequestId;
  private java.sql.Timestamp createdAt;
  private java.sql.Timestamp updatedAt;
  private String lastUpdatedBy;

  public HthApiPasswordCredentialKey getKey() {
    return key;
  }

  public void setKey(HthApiPasswordCredentialKey key) {
    this.key = key;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
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

  public String getLastRequestId() {
    return lastRequestId;
  }

  public void setLastRequestId(String lastRequestId) {
    this.lastRequestId = lastRequestId;
  }

  public java.sql.Timestamp getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.sql.Timestamp createdAt) {
    this.createdAt = createdAt;
  }

  public java.sql.Timestamp getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.sql.Timestamp updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  /** Excludes the credential hash from diagnostic output. */
  @Override
  public String toString() {
    return "HthApiPasswordCredential{passwordHash=[PROTECTED]}";
  }
  /** Required domain hook; business validation precedes conditional repository writes. */
  @Override
  protected void validate() {
  }
}
