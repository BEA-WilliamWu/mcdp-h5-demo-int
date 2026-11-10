package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;

/**
 * Activity log DTO for HTH API Password Code approved notification.
 *
 * <p>Carries the template parameters required by the notification engine when the
 * HTH API Password Code transitions from PENDING to ACTIVE (BCOH2H-787). Fields map
 * to the {@code #hthApiPasswordXxx#} placeholders defined in the notification
 * template configuration.
 *
 * @see com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword#notifyHthApiPasswordApproved
 */
public class HthApiPasswordActivityLogDTO extends ActivityLog {

  private static final long serialVersionUID = 2874510963248157903L;

  private String hthApiPasswordPartyId;
  private String hthApiPasswordUserName;
  private String hthApiPasswordExpiryDateTime;
  private String hthApiPasswordExpiryYear;
  private String hthApiPasswordExpiryMonth;
  private String hthApiPasswordExpiryDay;
  private String hthApiPasswordExpiryHour;
  private String hthApiPasswordExpiryMinute;
  private String hthApiPasswordExpirySecond;

  public String getHthApiPasswordPartyId() {
    return hthApiPasswordPartyId;
  }

  public void setHthApiPasswordPartyId(String hthApiPasswordPartyId) {
    this.hthApiPasswordPartyId = hthApiPasswordPartyId;
  }

  public String getHthApiPasswordUserName() {
    return hthApiPasswordUserName;
  }

  public void setHthApiPasswordUserName(String hthApiPasswordUserName) {
    this.hthApiPasswordUserName = hthApiPasswordUserName;
  }

  public String getHthApiPasswordExpiryDateTime() {
    return hthApiPasswordExpiryDateTime;
  }

  public void setHthApiPasswordExpiryDateTime(String hthApiPasswordExpiryDateTime) {
    this.hthApiPasswordExpiryDateTime = hthApiPasswordExpiryDateTime;
  }

  public String getHthApiPasswordExpiryYear() {
    return hthApiPasswordExpiryYear;
  }

  public void setHthApiPasswordExpiryYear(String hthApiPasswordExpiryYear) {
    this.hthApiPasswordExpiryYear = hthApiPasswordExpiryYear;
  }

  public String getHthApiPasswordExpiryMonth() {
    return hthApiPasswordExpiryMonth;
  }

  public void setHthApiPasswordExpiryMonth(String hthApiPasswordExpiryMonth) {
    this.hthApiPasswordExpiryMonth = hthApiPasswordExpiryMonth;
  }

  public String getHthApiPasswordExpiryDay() {
    return hthApiPasswordExpiryDay;
  }

  public void setHthApiPasswordExpiryDay(String hthApiPasswordExpiryDay) {
    this.hthApiPasswordExpiryDay = hthApiPasswordExpiryDay;
  }

  public String getHthApiPasswordExpiryHour() {
    return hthApiPasswordExpiryHour;
  }

  public void setHthApiPasswordExpiryHour(String hthApiPasswordExpiryHour) {
    this.hthApiPasswordExpiryHour = hthApiPasswordExpiryHour;
  }

  public String getHthApiPasswordExpiryMinute() {
    return hthApiPasswordExpiryMinute;
  }

  public void setHthApiPasswordExpiryMinute(String hthApiPasswordExpiryMinute) {
    this.hthApiPasswordExpiryMinute = hthApiPasswordExpiryMinute;
  }

  public String getHthApiPasswordExpirySecond() {
    return hthApiPasswordExpirySecond;
  }

  public void setHthApiPasswordExpirySecond(String hthApiPasswordExpirySecond) {
    this.hthApiPasswordExpirySecond = hthApiPasswordExpirySecond;
  }

  @Override
  public String toString() {
    return "HthApiPasswordActivityLogDTO [hthApiPasswordPartyId=" + hthApiPasswordPartyId
        + ", hthApiPasswordUserName=" + hthApiPasswordUserName
        + ", hthApiPasswordExpiryDateTime=" + hthApiPasswordExpiryDateTime
        + ", hthApiPasswordExpiryYear=" + hthApiPasswordExpiryYear
        + ", hthApiPasswordExpiryMonth=" + hthApiPasswordExpiryMonth
        + ", hthApiPasswordExpiryDay=" + hthApiPasswordExpiryDay
        + ", hthApiPasswordExpiryHour=" + hthApiPasswordExpiryHour
        + ", hthApiPasswordExpiryMinute=" + hthApiPasswordExpiryMinute
        + ", hthApiPasswordExpirySecond=" + hthApiPasswordExpirySecond + "]";
  }
}
