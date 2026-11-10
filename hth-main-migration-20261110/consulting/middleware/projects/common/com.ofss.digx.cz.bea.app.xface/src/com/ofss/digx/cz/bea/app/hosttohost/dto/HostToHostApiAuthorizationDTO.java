package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.common.dto.DomainObjectDTO;

public class HostToHostApiAuthorizationDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 7167014487068180598L;

  private String apiCode;

  private String apiName;

  private Boolean selected;

  public String getApiCode() {
    return apiCode;
  }

  public void setApiCode(String apiCode) {
    this.apiCode = apiCode;
  }

  public String getApiName() {
    return apiName;
  }

  public void setApiName(String apiName) {
    this.apiName = apiName;
  }

  public Boolean getSelected() {
    return selected;
  }

  public void setSelected(Boolean selected) {
    this.selected = selected;
  }
}
