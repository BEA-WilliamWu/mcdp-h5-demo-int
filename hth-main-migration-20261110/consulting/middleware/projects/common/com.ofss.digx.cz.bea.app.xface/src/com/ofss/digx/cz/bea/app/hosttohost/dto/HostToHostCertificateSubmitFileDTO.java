package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DataTransferObject;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostCertificateSubmitFileDTO extends DataTransferObject {
  private static final long serialVersionUID = 1761548721566965740L;

  private String fileName;

  private String pemContent;

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
