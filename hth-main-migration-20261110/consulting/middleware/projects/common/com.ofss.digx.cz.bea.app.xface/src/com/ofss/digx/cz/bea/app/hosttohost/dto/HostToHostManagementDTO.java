package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostManagementDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 9132822819001304904L;

  private String partyId;

  private String accountNumber;

  private String accountName;

  private String cinNumber;

  private String hostCode;

  private String status;

  private Boolean hostToHostApiChannelEnabled;

  private String correlateUamClientId;

  private String apiApprovalFlowType;

  private String chargeAccount;

  private String referenceNumber;

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }

  private List<HostToHostApiAuthorizationDTO> apiAuthorizations;

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getCinNumber() {
    return cinNumber;
  }

  public void setCinNumber(String cinNumber) {
    this.cinNumber = cinNumber;
  }

  public String getHostCode() {
    return hostCode;
  }

  public void setHostCode(String hostCode) {
    this.hostCode = hostCode;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Boolean getHostToHostApiChannelEnabled() {
    return hostToHostApiChannelEnabled;
  }

  public void setHostToHostApiChannelEnabled(Boolean hostToHostApiChannelEnabled) {
    this.hostToHostApiChannelEnabled = hostToHostApiChannelEnabled;
  }

  public String getCorrelateUamClientId() {
    return correlateUamClientId;
  }

  public void setCorrelateUamClientId(String correlateUamClientId) {
    this.correlateUamClientId = correlateUamClientId;
  }

  public String getApiApprovalFlowType() {
    return apiApprovalFlowType;
  }

  public void setApiApprovalFlowType(String apiApprovalFlowType) {
    this.apiApprovalFlowType = apiApprovalFlowType;
  }

  public String getChargeAccount() {
    return chargeAccount;
  }

  public void setChargeAccount(String chargeAccount) {
    this.chargeAccount = chargeAccount;
  }

  public List<HostToHostApiAuthorizationDTO> getApiAuthorizations() {
    return apiAuthorizations;
  }

  public void setApiAuthorizations(List<HostToHostApiAuthorizationDTO> apiAuthorizations) {
    this.apiAuthorizations = apiAuthorizations;
  }
}
