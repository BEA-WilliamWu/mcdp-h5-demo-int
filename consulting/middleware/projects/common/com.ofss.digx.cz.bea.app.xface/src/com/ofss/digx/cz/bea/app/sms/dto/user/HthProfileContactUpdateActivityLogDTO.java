package com.ofss.digx.cz.bea.app.sms.dto.user;

/** Keeps all BCO template getters; the marker scopes snapshot delivery to 851 only. */
public class HthProfileContactUpdateActivityLogDTO extends UserProfUpdateActivityLogDTO {
    private static final long serialVersionUID = 1L;
    private String hthContactNotificationId;
    public String getHthContactNotificationId() { return hthContactNotificationId; }
    public void setHthContactNotificationId(String value) { hthContactNotificationId = value; }
    @Override public String toString() { return "HthProfileContactUpdateActivityLogDTO"; }
}
