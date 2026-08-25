package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Combined response for HTH access summary and account-maintenance operations.
 *
 * <p>Summary calls populate the user, related, and associated sections. Account-maintenance calls
 * additionally populate effective access, eligible accounts/APIs, and pending-approval metadata.
 */
public class HostToHostUserAccessResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = -515303453464517209L;

  private HostToHostUserAccessContextDTO user;

  private String enterpriseHthStatus;

  private HostToHostUserAccessSummaryDTO related;

  private List<HostToHostUserAccessSummaryDTO> associated =
      new ArrayList<HostToHostUserAccessSummaryDTO>();

  private HostToHostUserAccessDTO access;

  private List<HostToHostUserAccessAccountDTO> eligibleAccounts =
      new ArrayList<HostToHostUserAccessAccountDTO>();

  private List<HostToHostUserAccessApiDTO> eligibleApis =
      new ArrayList<HostToHostUserAccessApiDTO>();

  private Boolean pendingRequest;

  private String pendingAction;

  private String pendingReferenceNumber;

  public HostToHostUserAccessContextDTO getUser() {
    return user;
  }

  public void setUser(HostToHostUserAccessContextDTO user) {
    this.user = user;
  }

  public String getEnterpriseHthStatus() {
    return enterpriseHthStatus;
  }

  public void setEnterpriseHthStatus(String enterpriseHthStatus) {
    this.enterpriseHthStatus = enterpriseHthStatus;
  }

  public HostToHostUserAccessSummaryDTO getRelated() {
    return related;
  }

  public void setRelated(HostToHostUserAccessSummaryDTO related) {
    this.related = related;
  }

  public List<HostToHostUserAccessSummaryDTO> getAssociated() {
    return associated;
  }

  public void setAssociated(List<HostToHostUserAccessSummaryDTO> associated) {
    this.associated = associated;
  }

  public HostToHostUserAccessDTO getAccess() {
    return access;
  }

  public void setAccess(HostToHostUserAccessDTO access) {
    this.access = access;
  }

  public List<HostToHostUserAccessAccountDTO> getEligibleAccounts() {
    return eligibleAccounts;
  }

  public void setEligibleAccounts(List<HostToHostUserAccessAccountDTO> eligibleAccounts) {
    this.eligibleAccounts = eligibleAccounts;
  }

  public List<HostToHostUserAccessApiDTO> getEligibleApis() {
    return eligibleApis;
  }

  public void setEligibleApis(List<HostToHostUserAccessApiDTO> eligibleApis) {
    this.eligibleApis = eligibleApis;
  }

  public Boolean getPendingRequest() {
    return pendingRequest;
  }

  public void setPendingRequest(Boolean pendingRequest) {
    this.pendingRequest = pendingRequest;
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
