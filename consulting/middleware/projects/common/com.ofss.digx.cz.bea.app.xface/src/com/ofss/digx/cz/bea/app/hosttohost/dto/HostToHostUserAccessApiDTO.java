package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.common.dto.DomainObjectDTO;

/**
 * HTH API catalogue entry or account-level API selection.
 *
 * <p>The server resolves {@code apiMasterId}, name, and display order from the active enterprise
 * API catalogue. Client-supplied descriptive values are not treated as authoritative.
 */
public class HostToHostUserAccessApiDTO extends DomainObjectDTO {
  private static final long serialVersionUID = -8523723282916165487L;

  private String apiMasterId;
  private String apiCode;
  private String apiName;
  private Boolean selected;
  private Long displayOrder;

  public String getApiMasterId() {
    return apiMasterId;
  }

  public void setApiMasterId(String apiMasterId) {
    this.apiMasterId = apiMasterId;
  }

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

  public Long getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(Long displayOrder) {
    this.displayOrder = displayOrder;
  }
}
