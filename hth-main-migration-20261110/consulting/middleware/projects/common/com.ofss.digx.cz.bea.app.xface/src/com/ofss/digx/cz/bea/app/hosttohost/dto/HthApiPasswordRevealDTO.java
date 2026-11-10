package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;

/**
 * Request to reveal the plaintext of a previously generated HTH API Password Code.
 *
 * <p>Only the generating operator, or a checker re-entering in approval mode, may reveal a code.
 * Every reveal is written to the audit log by the reveal task.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HthApiPasswordRevealDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 3411258906712345890L;

  private String codeId;

  public String getCodeId() {
    return codeId;
  }

  public void setCodeId(String codeId) {
    this.codeId = codeId;
  }
}
