package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;

/**
 * Maker request to generate (or regenerate) a one-time HTH API Password Code.
 *
 * <p>The code is created as a PENDING snapshot under the HTH maker/checker flow and becomes
 * ACTIVE, with its expiry anchored, when the original user-maintenance transaction is approved. The username may belong
 * to a user that is created by a parallel user-creation request, so no OBDX user lookup is
 * performed at generate time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HthApiPasswordGenerateDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 7823164509213476521L;

  private String purpose = "SETUP";

  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }

  private String partyId;
  private String userName;
  private String referenceNumber;

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }
}
