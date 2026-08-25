package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.common.dto.DomainObjectDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * Account-level selection used by HTH user access maintenance and approval snapshots.
 *
 * <p>The account number is the canonical value used for server-side ownership validation;
 * {@code maskedAccountNumber} and {@code displayName} are presentation-only values. Only CSA
 * accounts are supported. The nested API list records the services selected for this account.
 */
public class HostToHostUserAccessAccountDTO extends DomainObjectDTO {
  private static final long serialVersionUID = 1032528897282091210L;

  private String accountNumber;
  private String maskedAccountNumber;
  private String displayName;
  private String accountType;
  private String currency;
  private Boolean selected;
  private Long displayOrder;
  private List<HostToHostUserAccessApiDTO> apiServices =
      new ArrayList<HostToHostUserAccessApiDTO>();

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getMaskedAccountNumber() {
    return maskedAccountNumber;
  }

  public void setMaskedAccountNumber(String maskedAccountNumber) {
    this.maskedAccountNumber = maskedAccountNumber;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getAccountType() {
    return accountType;
  }

  public void setAccountType(String accountType) {
    this.accountType = accountType;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
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

  public List<HostToHostUserAccessApiDTO> getApiServices() {
    return apiServices;
  }

  public void setApiServices(List<HostToHostUserAccessApiDTO> apiServices) {
    this.apiServices = apiServices;
  }
}
