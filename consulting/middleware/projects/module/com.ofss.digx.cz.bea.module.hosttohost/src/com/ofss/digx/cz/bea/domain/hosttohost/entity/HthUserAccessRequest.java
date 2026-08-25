package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/**
 * Immutable maker request header used during checker approval re-entry.
 *
 * <p>The OBDX transaction ID connects this snapshot to the approval task. User and company names
 * are captured for historical display; authorization decisions continue to use IDs and current
 * server-side relationships.
 */
public class HthUserAccessRequest extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 6743255327516324938L;

  private HthUserAccessRequestKey key;

  private String transactionId;
  private String referenceNo;
  private String actionType;
  private String partyId;
  private String closeId;
  private String accessPartyId;
  private String linkageType;
  private String userName;
  private String fullName;
  private String accessPartyName;
  private String objectStatus;

  public HthUserAccessRequestKey getKey() {
    return key;
  }

  public void setKey(HthUserAccessRequestKey key) {
    this.key = key;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getReferenceNo() {
    return referenceNo;
  }

  public void setReferenceNo(String referenceNo) {
    this.referenceNo = referenceNo;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
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

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getAccessPartyName() {
    return accessPartyName;
  }

  public void setAccessPartyName(String accessPartyName) {
    this.accessPartyName = accessPartyName;
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
