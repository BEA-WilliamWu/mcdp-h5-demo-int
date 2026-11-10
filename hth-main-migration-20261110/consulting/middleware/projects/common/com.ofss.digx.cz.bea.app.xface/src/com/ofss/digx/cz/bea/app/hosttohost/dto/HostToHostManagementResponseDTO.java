package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;
import java.util.List;

public class HostToHostManagementResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = 3101428282296186713L;

  private String partyId;

  private String accountNumber;

  private String accountName;

  private String cinNumber;

  private Boolean hostToHostApiChannelEnabled;

  private String correlateUamClientId;

  private String apiApprovalFlowType;

  private String chargeAccount;

  private Boolean validUamClientId;

  private String errorMessage;

  private String referenceNumber;

  private String transactionReferenceNo;

  private String approvalComment;

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

  public Boolean getValidUamClientId() {
    return validUamClientId;
  }

  public void setValidUamClientId(Boolean validUamClientId) {
    this.validUamClientId = validUamClientId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }

  public String getTransactionReferenceNo() {
    return transactionReferenceNo;
  }

  public void setTransactionReferenceNo(String transactionReferenceNo) {
    this.transactionReferenceNo = transactionReferenceNo;
  }

  public String getApprovalComment() {
    return approvalComment;
  }

  public void setApprovalComment(String approvalComment) {
    this.approvalComment = approvalComment;
  }

  public List<HostToHostApiAuthorizationDTO> getApiAuthorizations() {
    return apiAuthorizations;
  }

  public void setApiAuthorizations(List<HostToHostApiAuthorizationDTO> apiAuthorizations) {
    this.apiAuthorizations = apiAuthorizations;
  }
}
