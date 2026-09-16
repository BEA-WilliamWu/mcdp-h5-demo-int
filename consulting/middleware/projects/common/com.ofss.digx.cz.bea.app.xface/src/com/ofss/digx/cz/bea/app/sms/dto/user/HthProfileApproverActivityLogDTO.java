package com.ofss.digx.cz.bea.app.sms.dto.user;

/** BCO template fields describe the changed user; these fields identify only the SMS recipient. */
public class HthProfileApproverActivityLogDTO extends UserProfUpdateActivityLogDTO {
    private static final long serialVersionUID = 1L;
    public static final String EMAIL_EVENT = "USER_EMAIL_ADDRESS_UPDATE";
    public static final String MOBILE_EVENT = "USER_MOBILE_NUMBER_UPDATED_REMINDER";
    private String approverId, approverMobile, approverCountryCode, approverEventId;
    public String getApproverId() { return approverId; }
    public void setApproverId(String value) { approverId = value; }
    public String getApproverMobile() { return approverMobile; }
    public void setApproverMobile(String value) { approverMobile = value; }
    public String getApproverCountryCode() { return approverCountryCode; }
    public void setApproverCountryCode(String value) { approverCountryCode = value; }
    public String getApproverEventId() { return approverEventId; }
    public void setApproverEventId(String value) { approverEventId = value; }
    public boolean isApproverSms(String activity, String event) {
        return "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update".equals(activity)
                && (EMAIL_EVENT.equals(event) || MOBILE_EVENT.equals(event)) && event.equals(approverEventId)
                && approverId != null && !approverId.trim().isEmpty()
                && approverMobile != null && approverMobile.matches("[0-9]{4,14}")
                && approverCountryCode != null && approverCountryCode.matches("[0-9]{1,4}");
    }
    @Override public String toString() { return "HthProfileApproverActivityLogDTO"; }
}
