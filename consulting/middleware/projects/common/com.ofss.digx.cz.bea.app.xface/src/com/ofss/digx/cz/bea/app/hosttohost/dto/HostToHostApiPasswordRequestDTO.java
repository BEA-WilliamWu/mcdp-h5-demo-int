package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;

/** Sensitive request used by HTH API password setup and reset. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostApiPasswordRequestDTO extends DomainObjectDTO {
  private static final long serialVersionUID = -4677655960161447844L;

  /** Shared customer-pin-encrypt envelope containing password followed by one-time Code. */
  private String encryptedCredentials;
  /** Client UUID retained across retries of the same operation. */
  private String requestId;

  public String getEncryptedCredentials() {
    return encryptedCredentials;
  }

  public void setEncryptedCredentials(String encryptedCredentials) {
    this.encryptedCredentials = encryptedCredentials;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  @JsonIgnore
  @Override
  public String toString() {
    return "HostToHostApiPasswordRequestDTO{requestId='" + requestId
        + "', encryptedCredentials='[PROTECTED]'}";
  }
}
