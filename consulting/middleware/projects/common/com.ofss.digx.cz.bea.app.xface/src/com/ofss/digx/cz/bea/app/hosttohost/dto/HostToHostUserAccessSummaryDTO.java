package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.common.dto.DomainObjectDTO;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summary of effective and pending HTH access for one account-owning party.
 *
 * <p>RELATED represents the primary corporate party itself; ASSOCIATED represents a currently
 * linked party. Account counts include active effective grants only. A pending action overrides
 * the displayed setup status but does not change effective access until checker approval.
 */
public class HostToHostUserAccessSummaryDTO extends DomainObjectDTO {
  private static final long serialVersionUID = -653267465608194677L;

  private String linkageType;

  private String accessPartyId;

  private String accessPartyName;

  private String setupStatus;

  private Map<String, Integer> accountCountByType = new LinkedHashMap<String, Integer>();

  private String pendingAction;

  private String pendingReferenceNumber;

  public String getLinkageType() {
    return linkageType;
  }

  public void setLinkageType(String linkageType) {
    this.linkageType = linkageType;
  }

  public String getAccessPartyId() {
    return accessPartyId;
  }

  public void setAccessPartyId(String accessPartyId) {
    this.accessPartyId = accessPartyId;
  }

  public String getAccessPartyName() {
    return accessPartyName;
  }

  public void setAccessPartyName(String accessPartyName) {
    this.accessPartyName = accessPartyName;
  }

  public String getSetupStatus() {
    return setupStatus;
  }

  public void setSetupStatus(String setupStatus) {
    this.setupStatus = setupStatus;
  }

  public Map<String, Integer> getAccountCountByType() {
    return accountCountByType;
  }

  public void setAccountCountByType(Map<String, Integer> accountCountByType) {
    this.accountCountByType = accountCountByType;
  }

  public String getPendingAction() {
    return pendingAction;
  }

  public void setPendingAction(String pendingAction) {
    this.pendingAction = pendingAction;
  }

  public String getPendingReferenceNumber() {
    return pendingReferenceNumber;
  }

  public void setPendingReferenceNumber(String pendingReferenceNumber) {
    this.pendingReferenceNumber = pendingReferenceNumber;
  }
}
