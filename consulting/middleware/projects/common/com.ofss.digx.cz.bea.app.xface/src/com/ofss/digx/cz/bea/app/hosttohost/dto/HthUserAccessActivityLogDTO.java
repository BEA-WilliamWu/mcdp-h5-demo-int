package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;

/** Approved notification projection; no access request payload or account/service identifiers. */
public class HthUserAccessActivityLogDTO extends ActivityLog {
    private static final long serialVersionUID = 121601L;
    private String hthAccessNotificationId, userNameId, userSysDate, compName;
    public String getHthAccessNotificationId() { return hthAccessNotificationId; }
    public void setHthAccessNotificationId(String value) { hthAccessNotificationId = value; }
    public String getUserNameId() { return userNameId; }
    public void setUserNameId(String value) { userNameId = value; }
    public String getUserSysDate() { return userSysDate; }
    public void setUserSysDate(String value) { userSysDate = value; }
    public String getCompName() { return compName; }
    public void setCompName(String value) { compName = value; }
}
