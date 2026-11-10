package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DataTransferObject;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostCertificateSubmitDTO extends DataTransferObject {
  private static final long serialVersionUID = 6082836462845259882L;

  private List<HostToHostCertificateSubmitFileDTO> certificates;

  public List<HostToHostCertificateSubmitFileDTO> getCertificates() {
    return certificates;
  }

  public void setCertificates(List<HostToHostCertificateSubmitFileDTO> certificates) {
    this.certificates = certificates;
  }
}
