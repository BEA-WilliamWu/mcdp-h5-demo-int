package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.service.response.BaseResponseObject;
import java.util.Date;

/**
 * Response for HTH API Password Code generate, masked search, and reveal operations.
 *
 * <p>{@code code} is populated only for the operator-facing generate response (shown once in the
 * reminder dialog) and for authorized reveal calls. All other reads return {@code maskedCode}
 * with the lifecycle {@code codeStatus} so pages can render masked rows and expiry hints without
 * touching the plaintext.
 */
public class HthApiPasswordCodeResponseDTO extends BaseResponseObject {
  private static final long serialVersionUID = -6234517890234512345L;

  private String purpose;
  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }

  private String codeId;
  private String code;
  private String maskedCode;
  private String codeStatus;
  private Date expiryTime;
  private Integer expiryHours;
  private Boolean canReveal;
  private String referenceNumber;

  public String getCodeId() {
    return codeId;
  }

  public void setCodeId(String codeId) {
    this.codeId = codeId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMaskedCode() {
    return maskedCode;
  }

  public void setMaskedCode(String maskedCode) {
    this.maskedCode = maskedCode;
  }

  public String getCodeStatus() {
    return codeStatus;
  }

  public void setCodeStatus(String codeStatus) {
    this.codeStatus = codeStatus;
  }

  public Date getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(Date expiryTime) {
    this.expiryTime = expiryTime;
  }

  public Integer getExpiryHours() {
    return expiryHours;
  }

  public void setExpiryHours(Integer expiryHours) {
    this.expiryHours = expiryHours;
  }

  public Boolean getCanReveal() {
    return canReveal;
  }

  public void setCanReveal(Boolean canReveal) {
    this.canReveal = canReveal;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }
}
