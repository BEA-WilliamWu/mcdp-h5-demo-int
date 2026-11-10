package com.ofss.digx.cz.bea.domain.hosttohost.entity;

/**
 * Read-only pending approval projection used by the HTH user access summary.
 *
 * <p>It contains workflow state only and never contributes to effective account counts.
 */
public class HthUserAccessPendingRecord {
  private String accessPartyId;

  private String linkageType;

  private String actionType;

  private String referenceNumber;

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

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }
}
