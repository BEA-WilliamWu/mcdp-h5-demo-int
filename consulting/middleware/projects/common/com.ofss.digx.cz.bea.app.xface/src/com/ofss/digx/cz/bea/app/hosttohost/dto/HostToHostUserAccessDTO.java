package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ofss.digx.app.common.dto.DomainObjectDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * Create, edit, or delete request for one HTH user-access context.
 *
 * <p>The context is the tuple {@code (partyId, closeId, accessPartyId, linkageType)}. The platform
 * approval framework stores the validated maker payload as {@code transactionSnapshot} and
 * supplies that server-side snapshot during checker execution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostToHostUserAccessDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 4121053725598913894L;

  private String partyId;
  private String closeId;
  private String accessPartyId;
  private String linkageType;
  private String username;
  private String fullName;
  private String accessPartyName;
  private String referenceNumber;
  private List<HostToHostUserAccessAccountDTO> accounts =
      new ArrayList<HostToHostUserAccessAccountDTO>();

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

  public String getAccessPartyName() {
    return accessPartyName;
  }

  public void setAccessPartyName(String accessPartyName) {
    this.accessPartyName = accessPartyName;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }

  public List<HostToHostUserAccessAccountDTO> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<HostToHostUserAccessAccountDTO> accounts) {
    this.accounts = accounts;
  }
}
