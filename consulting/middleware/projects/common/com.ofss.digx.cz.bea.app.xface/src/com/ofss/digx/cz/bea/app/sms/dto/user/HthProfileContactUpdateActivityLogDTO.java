package com.ofss.digx.cz.bea.app.sms.dto.user;

import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;

/** One committed 851 recipient. Addresses remain in the restricted outbox, not template fields. */
public class HthProfileContactUpdateActivityLogDTO extends ActivityLog {
    private static final long serialVersionUID = 1L;
    private String hthContactNotificationId;
    private String hthContactUserName;
    private String hthContactApprovedAt;

    public String getHthContactNotificationId() { return hthContactNotificationId; }
    public void setHthContactNotificationId(String value) { hthContactNotificationId = value; }
    public String getHthContactUserName() { return hthContactUserName; }
    public void setHthContactUserName(String value) { hthContactUserName = value; }
    public String getHthContactApprovedAt() { return hthContactApprovedAt; }
    public void setHthContactApprovedAt(String value) { hthContactApprovedAt = value; }
}
