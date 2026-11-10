package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;
import java.util.List;

public class HostToHostCertificateResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = 4311173774215107687L;

  private String partyId;

  private String accountNumber;

  private String accountName;

  private String referenceNumber;

  private List<HostToHostCertificateFileDTO> certificates;

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

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }

  public List<HostToHostCertificateFileDTO> getCertificates() {
    return certificates;
  }

  public void setCertificates(List<HostToHostCertificateFileDTO> certificates) {
    this.certificates = certificates;
  }
}
