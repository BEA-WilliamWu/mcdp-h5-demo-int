package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;

/**
 * Read criteria for HTH user access.
 *
 * <p>Summary search requires the primary {@code partyId} and HTH {@code closeId}. Account detail
 * search also requires the account-owning {@code accessPartyId} and a {@code linkageType} of
 * RELATED or ASSOCIATED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostUserAccessSearchDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 3264021208149132842L;

  private String partyId;

  private String closeId;

  private String accessPartyId;

  private String linkageType;

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getCloseId() {
    return closeId;
  }

  public void setCloseId(String closeId) {
    this.closeId = closeId;
  }

  public String getAccessPartyId() {
    return accessPartyId;
  }

  public void setAccessPartyId(String accessPartyId) {
    this.accessPartyId = accessPartyId;
  }

  public String getLinkageType() {
    return linkageType;
  }

  public void setLinkageType(String linkageType) {
    this.linkageType = linkageType;
  }
}
