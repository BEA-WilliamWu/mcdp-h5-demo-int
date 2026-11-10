package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostCertificateDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 7406641805165317302L;

  private String partyId;

  private String referenceNumber;

  private List<HostToHostCertificateFileDTO> certificates;

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
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
