package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.assembler;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainKey;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** BCO-style operation-level mapping, not one row per account or API grant. */
public final class HthCRMRequestAssembler {
    private HthCRMRequestAssembler() { }
    public static HthCRMEvent3DomainDTO assemble(String service, Map<String,Object> source, String externalCode) {
        String operation = text(source, "operation");
        String activity = activity(service, operation);
        if (activity == null || "NO_CHANGE".equals(text(source,"businessOutcome"))
                || Boolean.TRUE.equals(source.get("idempotentReplay"))) return null;
        if (activity.startsWith("USER_") && !hth(text(source,"newUserChannelType"))
                && !hth(text(source,"oldUserChannelType"))) return null;
        String outcome = text(source,"businessOutcome");
        boolean pending = "PENDING_APPROVAL".equals(outcome);
        String phase = text(source,"crmPhase");
        if (phase == null) phase = pending ? "SUBMIT" :
            (activity.startsWith("PASSWORD_") ? "EXECUTE" :
            ("CODE_GENERATE".equals(activity) ? "GENERATE" :
            (Boolean.TRUE.equals(source.get("effectiveChange")) ||
                (activity.startsWith("ACCESS_") && "SUCCESS".equals(outcome)) ? "APPLY" : "EXECUTE")));
        ZonedDateTime time = Instant.parse(text(source,"occurredAt")).atZone(ZoneId.of("Asia/Hong_Kong"));
        HthCRMEvent3DomainDTO event = new HthCRMEvent3DomainDTO();
        HthCRMEvent3DomainKey key = new HthCRMEvent3DomainKey();
        key.setEventId(UUID.randomUUID().toString());
        event.setKey(key);
        event.setRecordType(text(source,"crmRecordType"));
        event.setFiller01(source.get("crmFiller01") instanceof String ? (String)source.get("crmFiller01") : null);
        event.setEventDte(time.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        event.setEventTime(time.format(DateTimeFormatter.ofPattern("HHmmss")));
        event.setChnlId(text(source,"crmChnlId"));
        event.setChnlTypeCode(text(source,"crmChnlTypeCode"));
        event.setFeeChrgCode(text(source,"crmFeeChrgCode"));
        event.setSelfSrvInd(text(source,"crmSelfSrvInd"));
        if (activity.startsWith("USER_") || activity.startsWith("ACCESS_"))
            event.setOmbFlag(text(source,"crmOmbFlag"));
        event.setEventCountryCode(text(source,"crmEventCountryCode"));
        event.setSourceSystem("HTH");
        event.setChannelType(activity.startsWith("COMPANY_") ? "BM" : "CM");
        event.setTargetUserChannel(activity.startsWith("COMPANY_") ? null : "HTH");
        event.setActivityKey(activity);
        event.setEventActvTypeCode(externalCode);
        event.setEventStatusCode("SUCCESS".equals(outcome) || pending ? "A" : "R");
        event.setPhase(phase);
        event.setFinInd("N");
        event.setUserId(text(source,"actorUserId"));
        event.setTargetUserId(text(source,"targetUserId"));
        event.setAcctNbr(text(source,"partyId"));
        event.setRelationshipType(text(source,"linkageType"));
        event.setServiceId(service);
        event.setTaskCode(text(source,"crmTask"));
        String reference = text(source,"approvalReference");
        if (reference == null) reference = text(source,"referenceNumber");
        event.setSourceTrxRefNbr(reference);
        String eventReference = reference == null ? text(source,"crmReference") : reference;
        // BCO's assembler limits EVENT_REM to 40; the full business reference remains above.
        event.setEventRem(eventReference == null || eventReference.length() <= 40
                ? eventReference : eventReference.substring(0,40));
        event.setSourceActionId(text(source,"crmActionId"));
        event.setRequestId(text(source,"requestId"));
        event.setIpAddress(text(source,"crmIp"));
        event.setErrorCode(text(source,"errorCode"));
        event.setMobileBrand(text(source,"crmMobileBrand"));
        event.setDeviceModel(text(source,"crmDeviceModel"));
        event.setDeviceOsVersion(text(source,"crmDeviceOsVersion"));
        event.setUserRole(text(source,"crmUserRole"));
        event.setBioType(text(source,"crmBioType"));
        event.setRegMethod(text(source,"crmRegMethod"));
        event.setAuthMethod(text(source,"crmAuthMethod"));
        event.setLang(text(source,"crmLang"));
        event.setAppVersion(text(source,"crmAppVersion"));
        event.setErrCode(text(source,"crmErrCode"));
        event.settTAutoRoute("N");
        // Financial/FPS/merchant fields have no value for this onboarding scope.
        // TOKEN_ID and AR_TOKEN are retained in the model only, never populated.
        String stable = text(source,"crmActionId");
        if (stable == null && activity.startsWith("PASSWORD_") && "SUCCESS".equals(outcome))
            stable = text(source,"requestId");
        // A transaction can have multiple approval actions; transactionId alone is never a key.
        if (stable != null && "SUCCESS".equals(outcome)) {
            event.setDedupKey(digest(Arrays.asList(activity, phase, reference, stable,
                text(source,"partyId"), text(source,"targetUserId")).toString()));
        }
        return event;
    }
    /** Copy the approved projection so a rollback callback never mutates its input event. */
    public static HthCRMEvent3DomainDTO rolledBack(HthCRMEvent3DomainDTO source) {
        HthCRMEvent3DomainDTO copy = source.copy();
        copy.setEventStatusCode("R");
        copy.setErrorCode("BUSINESS_ROLLBACK");
        copy.setDedupKey(null);
        return copy;
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
    private static boolean hth(String value) { return com.ofss.digx.cz.bea.common.hth.HthChannelSupport.isHthChannel(value); }
    public static String text(Map<String,Object> data, String key) {
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
