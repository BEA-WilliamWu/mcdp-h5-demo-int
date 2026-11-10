package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.common.dto.DomainObjectDTO;

/**
 * Identifies the HTH user whose access is being displayed or maintained.
 *
 * <p>{@code partyId} is the primary corporate party and {@code closeId} identifies the HTH user
 * within that party. This object does not identify the party that owns a selected account; that
 * value is carried separately as {@code accessPartyId} on maintenance requests.
 */
public class HostToHostUserAccessContextDTO extends DomainObjectDTO {
  private static final long serialVersionUID = -7909467728492060935L;

  private String partyId;

  private String closeId;

  private String username;

  private String fullName;

  private String userChannelType;

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

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getUserChannelType() {
    return userChannelType;
  }

  public void setUserChannelType(String userChannelType) {
    this.userChannelType = userChannelType;
  }
}
