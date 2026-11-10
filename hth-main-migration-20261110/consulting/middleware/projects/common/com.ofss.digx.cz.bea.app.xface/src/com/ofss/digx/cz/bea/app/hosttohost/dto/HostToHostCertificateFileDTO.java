package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostCertificateFileDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 5009249878647540509L;

  private String certificateId;

  private String fileName;

  private String pemContent;

  public String getCertificateId() {
    return certificateId;
  }

  public void setCertificateId(String certificateId) {
    this.certificateId = certificateId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getPemContent() {
    return pemContent;
  }

  public void setPemContent(String pemContent) {
    this.pemContent = pemContent;
  }
}
