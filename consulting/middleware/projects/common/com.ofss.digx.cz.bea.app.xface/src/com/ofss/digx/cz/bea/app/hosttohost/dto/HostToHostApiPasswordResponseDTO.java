package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;

/** Status/policy response for HTH API password self service. */
public class HostToHostApiPasswordResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = 7968579792517074445L;

  private String setupState;
  private boolean resetAllowed;
  private HostToHostApiPasswordPolicyDTO passwordPolicy =
      new HostToHostApiPasswordPolicyDTO();

  public String getSetupState() { return setupState; }
  public void setSetupState(String setupState) { this.setupState = setupState; }
  public boolean isResetAllowed() { return resetAllowed; }
  public void setResetAllowed(boolean resetAllowed) { this.resetAllowed = resetAllowed; }
  public HostToHostApiPasswordPolicyDTO getPasswordPolicy() { return passwordPolicy; }
  public void setPasswordPolicy(HostToHostApiPasswordPolicyDTO passwordPolicy) {
    this.passwordPolicy = passwordPolicy;
  }
}
