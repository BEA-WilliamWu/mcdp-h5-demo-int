package com.ofss.digx.cz.bea.common.mtb;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** BCO-style operation-level mapping, not one row per account or API grant. */
public final class HthMtbEventAssembler {
    private HthMtbEventAssembler() { }
    public static HthMtbEvent assemble(String service, Map<String,Object> source, String externalCode) {
        String operation = text(source, "operation");
        String activity = activity(service, operation);
        if (activity == null || "NO_CHANGE".equals(text(source,"businessOutcome"))
                || Boolean.TRUE.equals(source.get("idempotentReplay"))) return null;
        if (activity.startsWith("USER_") && !hth(text(source,"newUserChannelType"))
                && !hth(text(source,"oldUserChannelType"))) return null;
        String outcome = text(source,"businessOutcome");
        boolean pending = "PENDING_APPROVAL".equals(outcome);
        String phase = text(source,"mtbPhase");
        if (phase == null) phase = pending ? "SUBMIT" :
            (activity.startsWith("PASSWORD_") ? "EXECUTE" :
            ("CODE_GENERATE".equals(activity) ? "GENERATE" :
            (Boolean.TRUE.equals(source.get("effectiveChange")) ||
                (activity.startsWith("ACCESS_") && "SUCCESS".equals(outcome)) ? "APPLY" : "EXECUTE")));
        ZonedDateTime time = Instant.parse(text(source,"occurredAt")).atZone(ZoneId.of("Asia/Hong_Kong"));
        Map<String,String> fields = new LinkedHashMap<String,String>();
        fields.put("EVENT_ID", UUID.randomUUID().toString());
        fields.put("EVENT_DTE", time.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        fields.put("EVENT_TIME", time.format(DateTimeFormatter.ofPattern("HHmmss")));
        fields.put("SOURCE_SYSTEM", "HTH");
        fields.put("CHANNEL_TYPE", activity.startsWith("COMPANY_") ? "BM" : "CM");
        fields.put("TARGET_USER_CHANNEL", activity.startsWith("COMPANY_") ? null : "HTH");
        fields.put("ACTIVITY_KEY", activity);
        fields.put("EVENT_ACTV_TYPE_CODE", externalCode);
        fields.put("EVENT_STATUS_CODE", "SUCCESS".equals(outcome) || pending ? "A" : "R");
        fields.put("PHASE", phase);
        fields.put("FIN_IND", "N");
        fields.put("USER_ID", text(source,"actorUserId"));
        fields.put("TARGET_USER_ID", text(source,"targetUserId"));
        fields.put("ACCT_NBR", text(source,"partyId"));
        fields.put("RELATIONSHIP_TYPE", text(source,"linkageType"));
        fields.put("SERVICE_ID", service);
        fields.put("TASK_CODE", text(source,"mtbTask"));
        String reference = text(source,"approvalReference");
        if (reference == null) reference = text(source,"referenceNumber");
        fields.put("SOURCE_TRX_REF_NBR", reference);
        fields.put("SOURCE_ACTION_ID", text(source,"mtbActionId"));
        fields.put("REQUEST_ID", text(source,"requestId"));
        fields.put("IP_ADDRESS", text(source,"mtbIp"));
        fields.put("ERROR_CODE", text(source,"errorCode"));
        String stable = text(source,"mtbActionId");
        if (stable == null && activity.startsWith("PASSWORD_") && "SUCCESS".equals(outcome))
            stable = text(source,"requestId");
        // A transaction can have multiple approval actions; transactionId alone is never a key.
        if (stable != null && "SUCCESS".equals(outcome)) {
            fields.put("DEDUP_KEY", digest(Arrays.asList(activity, phase, reference, stable,
                text(source,"partyId"), text(source,"targetUserId")).toString()));
        }
        return new HthMtbEvent(fields);
    }
    public static String activity(String service, String operation) {
        if (service == null || operation == null) return null;
        if (service.startsWith("UserExtensionData.")) {
            if ("CREATE".equals(operation)) return "USER_CREATE";
            if ("UPDATE".equals(operation)) return "USER_EDIT";
        }
        if (service.endsWith("HostToHostManagement.submit") && "COMPANY_ENABLE".equals(operation)) return operation;
        if (service.endsWith("HostToHostManagement.edit") && "COMPANY_EDIT".equals(operation)) return operation;
        if (service.endsWith("HostToHostManagement.disable") && "COMPANY_DISABLE".equals(operation)) return operation;
        if (service.contains("HostToHostUserAccess.")) {
            if ("ACCESS_SUBMIT".equals(operation) || "ACCESS_CREATE".equals(operation)) return "ACCESS_CREATE";
            if ("ACCESS_EDIT".equals(operation)) return "ACCESS_EDIT";
            if ("ACCESS_DELETE".equals(operation)) return "ACCESS_DELETE";
        }
        if (service.contains("HostToHostApiPassword.")) {
            if ("SETUP".equals(operation)) return "PASSWORD_SETUP";
            if ("RESET".equals(operation)) return "PASSWORD_RESET";
            if ("GENERATE".equals(operation) || "REGENERATE".equals(operation) || "CODE_ACTIVATE".equals(operation)) return "CODE_GENERATE";
        }
        return null; // Inquiries/reveal/notifications keep existing BCO rules; no new HTH event.
    }
    private static boolean hth(String value) { return "HTH".equalsIgnoreCase(value) || "H2H".equalsIgnoreCase(value); }
    static String text(Map<String,Object> data, String key) {
        Object value=data.get(key);
        return value instanceof String && !((String)value).trim().isEmpty() ? (String)value : null;
    }
    private static String digest(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for (byte b:bytes) result.append(String.format(Locale.ROOT,"%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
