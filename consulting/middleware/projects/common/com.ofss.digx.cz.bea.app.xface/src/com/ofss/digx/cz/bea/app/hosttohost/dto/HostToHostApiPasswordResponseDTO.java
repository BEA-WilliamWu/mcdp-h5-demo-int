package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;

/** Status/policy response for HTH API password self service. */
public class HostToHostApiPasswordResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = 7968579792517074445L;

  /** NOT_APPLICABLE, REQUIRED, CODE_REQUIRED, ACTIVE, LOCKED or UNKNOWN. */
  private String setupState;
  /** True only when the active credential has a usable RESET Code. */
  private boolean resetAllowed;
  private HostToHostApiPasswordPolicyDTO passwordPolicy =
      new HostToHostApiPasswordPolicyDTO();

  /** Public transport material, included only when explicitly requested on status. */
  private java.util.Map<String, String> transportKey;

  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public java.util.Map<String, String> getTransportKey() { return transportKey; }
  public void setTransportKey(java.util.Map<String, String> transportKey) { this.transportKey = transportKey; }

  public String getSetupState() {
    return setupState;
  }
  public void setSetupState(String setupState) {
    this.setupState = setupState;
  }
  public boolean isResetAllowed() {
    return resetAllowed;
  }
  public void setResetAllowed(boolean resetAllowed) {
    this.resetAllowed = resetAllowed;
  }
  public HostToHostApiPasswordPolicyDTO getPasswordPolicy() {
    return passwordPolicy;
  }
  public void setPasswordPolicy(HostToHostApiPasswordPolicyDTO passwordPolicy) {
    this.passwordPolicy = passwordPolicy;
  }
}
