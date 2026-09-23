package com.ofss.digx.cz.bea.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.digx.infra.audit.dto.AuditDetailsDTO;
import com.ofss.digx.enumeration.audit.Status;
import com.ofss.digx.enumeration.audit.Type;
import com.ofss.digx.datatype.complex.AuditMap;
import com.ofss.fc.app.context.SessionContext;
import java.util.*;

/** BCOH2H-791. Safe copies only; the business request and reveal response are never modified. */
public final class HthOnboardingAudit {
    public static final String MARKER = "HTH_ONBOARDING_791";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(HthOnboardingAudit.class.getName());
    private static final Set<String> TASKS = new HashSet<String>(Arrays.asList(
        "UAT_N_HAP_GEN", "UAT_N_HAP_REGEN", "UAT_N_HAP_RVL", "CM_N_HAP_SETUP", "CM_N_HAP_RESET",
        "UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL"));
    private static final Set<String> FIELDS = new HashSet<String>(Arrays.asList(
        "schemaVersion", "operation", "actorUserId", "targetUserId", "partyId", "targetUnit",
        "oldUserChannelType", "newUserChannelType", "userChannelType", "userID", "userId", "userName",
        "username", "closeId", "cdcNo", "referenceNumber", "approvalReference", "occurredAt",
        "businessOutcome", "errorCode", "codeId", "apiPasswordCodeId", "hthApiPasswordCodeId", "previousCodeId", "purpose",
        "codeStatus", "expiryTime", "usedTime", "setupState", "requestId", "idempotentReplay",
        "accessPartyId", "linkageType", "accessChanges", "accountType", "maskedAccountNumber",
        "apiMasterId", "change", "selected", "apiServices", "accounts", "effectiveChange",
        "auditStage", "processingStage"));
    private static final Set<String> CONTAINERS = new HashSet<String>(Arrays.asList(
        "userExtensionDataDTO", "userDTO", "access", "requestDTO", "request", "response", "hthOnboarding"));
    private HthOnboardingAudit() { }

    /** A local scope, published into the platform's existing per-request audit stack on exit. */
    public static final class Entry implements AutoCloseable {
        private final Map<String,Object> values = new LinkedHashMap<String,Object>();
        private final String service;
        private boolean enabled;
        private boolean closed;
        private boolean failed;
        private String scopeName;
        private Entry(SessionContext context, String service, String operation, boolean enabled) {
            this.service=service; this.enabled=enabled;
            put("schemaVersion", "1"); put("operation", operation);
            put("actorUserId", context == null ? null : context.getUserId());
            put("targetUnit", context == null ? null : context.getTargetUnit());
            put("partyId", context == null ? null : context.getTransactingPartyCode());
            put("businessOutcome", "FAILURE");
            start();
        }
        private void start() {
            if (!enabled || scopeName != null) return;
            scopeName=MARKER+"_SCOPE_"+UUID.randomUUID().toString();
            AuditDetailsDTO guard=new AuditDetailsDTO(); guard.setAuditType(Type.SERVICE);
            guard.setServiceName(service); guard.setOperationName(scopeName);
            stack(true).push(guard);
        }
        public Entry put(String key, Object value) {
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("Unsupported audit field");
            if (value != null) values.put(key, value);
            return this;
        }
        public Entry channel(String before, String after) {
            if (after == null) after = before;
            enabled = enabled || hth(before) || hth(after);
            start();
            return put("oldUserChannelType", before).put("newUserChannelType", after);
        }
        public Entry result(String outcome) {
            if (failed) return this;
            if ("PENDING_APPROVAL".equals(outcome) && values.containsKey("effectiveChange")) put("effectiveChange",false);
            return put("businessOutcome", outcome);
        }
        public Entry failure(Throwable failure) {
            boolean previousFailure=failed;
            failed=true; put("businessOutcome", "FAILURE");
            if (values.containsKey("effectiveChange")) put("effectiveChange",false);
            for (int i=0; failure != null && i<8; i++, failure=failure.getCause()) {
                String code=failure instanceof com.ofss.digx.infra.exceptions.Exception
                    ? ((com.ofss.digx.infra.exceptions.Exception)failure).getErrorCode() : null;
                if (code == null) code=failure.getMessage();
                if (failure instanceof com.ofss.digx.app.approval.exceptions.ApprovalRequiredException
                        || "DIGX_APPROVAL_REQUIRED".equals(code)) {
                    if (!previousFailure) { failed=false; result("PENDING_APPROVAL"); }
                    break;
                }
                if (code != null && code.matches("DIGX_[A-Z0-9_]{1,100}")) { put("errorCode",code); break; }
            }
            return this;
        }
        public Entry response(Object response) {
            if (!enabled) return this;
            // User maintenance/edit returns TransactionStatus, which has no REST result/message.
            if (response instanceof com.ofss.fc.service.response.TransactionStatus) {
                com.ofss.fc.service.response.TransactionStatus status=(com.ofss.fc.service.response.TransactionStatus)response;
                String reference=status.getExternalReferenceNo();
                put("referenceNumber",reference == null ? status.getInternalReferenceNumber() : reference);
                Throwable error=status.fetchLastKnownError();
                if (error != null) failure(error);
                else if ("DIGX_APPROVAL_REQUIRED".equals(status.getErrorCode())) result("PENDING_APPROVAL");
                else if (status.getReplyCode() != 0) failure(new IllegalStateException("TRANSACTION_FAILED"));
                String code=status.getErrorCode();
                if (failed && code != null && code.matches("DIGX_[A-Z0-9_]{1,100}")) put("errorCode",code);
                return this;
            }
            Map<String,Object> status=object(response);
            if (status.get("status") instanceof Map) status=cast(status.get("status"));
            Object reference=status.get("referenceNumber");
            if (reference == null) reference=status.get("externalReferenceNo");
            if (reference == null) reference=status.get("externalReferenceNumber");
            if (reference instanceof String) put("referenceNumber",reference);
            String error=errorCode(status);
            if (failed(status)) { failure(new IllegalStateException("RESPONSE_FAILED")); if (error != null) put("errorCode", error); }
            else if ("DIGX_APPROVAL_REQUIRED".equals(error) || "ACCEPTED".equals(status.get("result"))
                    || "PENDING".equals(status.get("status"))) result("PENDING_APPROVAL");
            return this;
        }
        @Override public void close() {
            if (!enabled || closed) return;
            closed=true;
            Stack<AuditDetailsDTO> pending=stack(true);
            for (Iterator<AuditDetailsDTO> it=pending.iterator();it.hasNext();) {
                AuditDetailsDTO existing=it.next();
                if (existing != null && scopeName.equals(existing.getOperationName())) it.remove();
            }
            values.put("occurredAt", java.time.Instant.now().toString());
            AuditDetailsDTO detail=new AuditDetailsDTO();
            detail.setAuditType(Type.SERVICE); detail.setServiceName(service); detail.setOperationName(MARKER);
            Map<String,Object> envelope=new LinkedHashMap<String,Object>();
            envelope.put("hthOnboarding", safe(values)); detail.setRequest(envelope);
            stack(true).push(detail);
        }
    }
    public static Entry begin(SessionContext context, String service, String operation) {
        return new Entry(context,service,operation,true);
    }
    public static Entry user(SessionContext context, String operation, String user, String party, String channel, String codeId) {
        return new Entry(context,"UserExtensionData."+operation.toLowerCase(Locale.ROOT),operation,hth(channel))
            .put("targetUserId",fullUser(user,party)).put("partyId",party)
            .put("newUserChannelType",channel).put("codeId",codeId);
    }
    public static String fullUser(String user, String party) {
        return user == null || user.contains("@") || party == null ? user : user+"@"+party;
    }
    private static boolean hth(Object value) { return "HTH".equalsIgnoreCase(String.valueOf(value)) || "H2H".equalsIgnoreCase(String.valueOf(value)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> cast(Object value) { return (Map<String,Object>)value; }
    private static Map<String,Object> object(Object value) {
        try {
            if (value instanceof Map) return cast(value);
            if (value instanceof String) return JSON.readValue((String)value,Map.class);
            return value == null ? Collections.<String,Object>emptyMap() : JSON.convertValue(value,Map.class);
        } catch (java.lang.Exception failure) {
            diagnostic("PROJECT_PAYLOAD",failure);
            return Collections.singletonMap("auditStage",(Object)"PAYLOAD_UNAVAILABLE");
        }
    }
    private static String errorCode(Map<String,Object> value) {
        Object code=value.get("errorCode");
        if (value.get("message") instanceof Map) code=cast(value.get("message")).get("code");
        return code instanceof String && ((String)code).matches("DIGX_[A-Z0-9_]{1,100}") ? (String)code : null;
    }
    private static boolean failed(Map<String,Object> value) {
        Object result=value.get("result"), state=value.get("status");
        if ("FAILURE".equals(value.get("businessOutcome"))) return true;
        if (value.get("hthOnboarding") instanceof Map && failed(cast(value.get("hthOnboarding")))) return true;
        if ("DIGX_APPROVAL_REQUIRED".equals(errorCode(value)) || "ACCEPTED".equals(result)) return false;
        if ("FAILED".equals(result) || "FAILURE".equals(result) || "FAILURE".equals(state)
                || errorCode(value) != null || value.get("lastKnownError") != null) return true;
        if (value.get("message") instanceof Map && "ERROR".equals(cast(value.get("message")).get("type"))) return true;
        return value.get("status") instanceof Map && failed(cast(value.get("status")));
    }
    /** Allowlisted structural projection. Unknown objects/fields never fall back to raw serialization. */
    public static Object safe(Object value) { return safe(value,false); }
    private static Object safe(Object value, boolean trustedSummary) {
        if (value == null) return null;
        if (value instanceof Iterable) {
            List<Object> items=new ArrayList<Object>();
            for (Object item:(Iterable<?>)value) items.add(safe(item,trustedSummary));
            return items;
        }
        if (value instanceof Object[]) return safe(Arrays.asList((Object[])value),trustedSummary);
        Map<String,Object> source=object(value), copy=new LinkedHashMap<String,Object>();
        for (Map.Entry<String,Object> field:source.entrySet()) {
            String key=field.getKey(); Object item=field.getValue();
            if ("hthOnboarding".equals(key) && !trustedSummary) continue;
            if (FIELDS.contains(key) && (item instanceof String || item instanceof Number || item instanceof Boolean)) copy.put(key,item);
            else if (FIELDS.contains(key) || CONTAINERS.contains(key)) copy.put(key,safe(item,trustedSummary));
        }
        if (failed(source)) copy.put("businessOutcome","FAILURE");
        String error=errorCode(source); if (error != null) copy.put("errorCode",error);
        if (source.get("status") instanceof Map) {
            Map<String,Object> status=cast(source.get("status"));
            if (failed(status)) copy.put("businessOutcome","FAILURE");
            error=errorCode(status); if (error != null) copy.put("errorCode",error);
        }
        return copy;
    }
    @SuppressWarnings("unchecked") private static Stack<AuditDetailsDTO> stack(boolean create) {
        Object current=com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
        if (current instanceof Stack) return (Stack<AuditDetailsDTO>)current;
        if (!create) return null;
        Stack<AuditDetailsDTO> result=new Stack<AuditDetailsDTO>();
        com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK,result);
        return result;
    }
    private static boolean related(Object value) {
        if (value == null) return false;
        String type=value.getClass().getSimpleName();
        if (type.startsWith("HthApiPassword") || type.startsWith("HostToHostApiPassword") || type.startsWith("HostToHostUserAccess")) return true;
        if (value instanceof Object[]) { for (Object v:(Object[])value) if (related(v)) return true; return false; }
        if (value instanceof Iterable) { for (Object v:(Iterable<?>)value) if (related(v)) return true; return false; }
        if (!(value instanceof Map) && !(value instanceof String)
                && !"UserExtensionDataDTO".equals(type) && !"UserExtensionDataResponseDTO".equals(type)) return false;
        if (value instanceof String && !((String)value).contains("userChannelType")) return false;
        Map<String,Object> map=object(value);
        if (hth(map.get("userChannelType"))) return true;
        for (String key:CONTAINERS) if (!"hthOnboarding".equals(key) && map.containsKey(key) && related(map.get(key))) return true;
        return false;
    }
    public static boolean applies(AuditDTO dto) {
        if (dto == null) return false;
        String task=dto.getTaskCode();
        if (TASKS.contains(task)) return true;
        String url=dto.getResolvedRequestUrl();
        if (url != null && url.split("\\?",2)[0].matches(".*/(?:hostToHostApiPassword|hostToHostUserAccess)(?:/.*)?")) return true;
        if (relatedDetails(dto.getAuditDetailsDTOList())) return true;
        if (("MT_N_CUS".equals(task) || "MT_N_UUS".equals(task)) && dto.getAuditDetailsDTOList() != null)
            for (AuditDetailsDTO detail:dto.getAuditDetailsDTOList())
                if (detail != null && (related(detail.getRequest()) || related(detail.getResponse()))) return true;
        return false;
    }
    private static boolean relatedDetails(List<AuditDetailsDTO> details) {
        if (details != null) for (AuditDetailsDTO detail:details)
            if (detail != null && (MARKER.equals(detail.getOperationName()) || (detail.getOperationName() != null && detail.getOperationName().startsWith(MARKER+"_SCOPE_"))
                    || (detail.getServiceName() != null && (detail.getServiceName().contains("HostToHostApiPassword") || detail.getServiceName().contains("HostToHostUserAccess"))))) return true;
        return false;
    }
    public static boolean prepare(AuditDTO dto) {
        boolean active=applies(dto) || relatedDetails(stack(false));
        if (!active) return false;
        project(dto);
        Stack<AuditDetailsDTO> pending=stack(false);
        if (pending != null) {
            List<AuditDetailsDTO> copies=projectDetails(pending);
            pending.clear(); pending.addAll(copies);
        }
        return true;
    }
    /** Work only on the audit copy; business request/response objects are never mutated. */
    public static void project(AuditDTO dto) {
        if (dto == null) return;
        boolean failure=dto.getStatus()==Status.FAILURE;
        if (dto.getAuditDetailsDTOList()!=null) for (AuditDetailsDTO detail:dto.getAuditDetailsDTOList())
            if (detail != null) failure |= (detail.getAuditType()==Type.REST && failed(object(detail.getResponse())))
                || (MARKER.equals(detail.getOperationName()) && failed(object(detail.getRequest())));
        dto.setAuditDetailsDTOList(projectDetails(dto.getAuditDetailsDTOList()));
        dto.setRequestHeaders(headers(dto.getRequestHeaders())); dto.setResponseHeaders(headers(dto.getResponseHeaders()));
        dto.setQueryParamList(headers(dto.getQueryParamList()));
        dto.setException(null); dto.setAccessKey(null);
        if (failure) dto.setStatus(Status.FAILURE);
        if (dto.getResolvedRequestUrl()!=null) dto.setResolvedRequestUrl(dto.getResolvedRequestUrl().split("\\?",2)[0]);
        if (dto.getRequestUrl()!=null) dto.setRequestUrl(dto.getRequestUrl().split("\\?",2)[0]);
        if (dto.getUri()!=null) dto.setUri(dto.getUri().split("\\?",2)[0]);
    }
    /** Drop credentials from the JMS copy, preserving the context required by the stock listener. */
    public static void finish(AuditDTO dto) {
        // Only change the completed audit DTO, never CURRENT_TASK or the business resource mapping.
        classifyCodeGeneration(dto);
        project(dto);
        dto.setSessionID(null);
        Map<String,Object> context=new HashMap<String,Object>();
        Map<String,Object> source=dto.getThreadAttributeMap();
        if (source != null) {
            for (String key:Arrays.asList("LEGAL_ENTITY_CODE","MARKET_ENTITY_CODE","TRANSACTION_BRANCH","BUSINESS_UNIT_CODE","REGULATORY_REGION"))
                if (source.containsKey(key)) context.put(key,source.get(key));
            Object channel=source.get("CHANNEL_CONTEXT");
            if (channel instanceof com.ofss.digx.app.context.ChannelContext) {
                try {
                    com.ofss.digx.app.context.ChannelContext original=(com.ofss.digx.app.context.ChannelContext)channel;
                    // Public API, empty request headers and a fresh context: no responseNonce/private fields copied.
                    javax.servlet.http.HttpServletRequest headersOnly=new javax.servlet.http.HttpServletRequestWrapper(original.getHttpRequest()) {
                        @Override public String getHeader(String name) { return null; }
                    };
                    com.ofss.digx.app.context.ChannelContext copy=new com.ofss.digx.app.context.ChannelContext(
                        headersOnly,null,dto.getResolvedRequestUrl(),Boolean.FALSE);
                    copy.setSessionContext(original.getSessionContext());
                    copy.setNonce(null); copy.setSessionId(null); copy.setRequestedNonceCount(0);
                    context.put("CHANNEL_CONTEXT",copy);
                } catch (java.lang.Exception failure) {
                    diagnostic("PROJECT_CONTEXT",failure);
                }
            }
        }
        dto.setThreadAttributeMap(context);
        // The BCO detail view renders REST request/response. Reuse it rather than a second UI/export.
        List<Object> summaries=new ArrayList<Object>();
        if (dto.getAuditDetailsDTOList()!=null) {
            for (AuditDetailsDTO detail:dto.getAuditDetailsDTOList())
                if (MARKER.equals(detail.getOperationName())) {
                    Object summary=object(detail.getRequest()).get("hthOnboarding");
                    if (summary != null) summaries.add(summary);
                }
            for (AuditDetailsDTO detail:dto.getAuditDetailsDTOList()) if (detail.getAuditType()==Type.REST && !summaries.isEmpty()) {
                Map<String,Object> request=new LinkedHashMap<String,Object>(object(detail.getRequest()));
                request.put("hthOnboarding",summaries.size()==1 ? summaries.get(0) : summaries);
                detail.setRequest(request);
            }
        }
    }
    private static void classifyCodeGeneration(AuditDTO dto) {
        if (dto == null || !"UAT_N_HAP_GEN".equals(dto.getTaskCode())
                || dto.getAuditDetailsDTOList() == null) return;
        for (AuditDetailsDTO detail : dto.getAuditDetailsDTOList()) {
            // Trust only the service-created summary, not an operation supplied in the REST body.
            if (detail == null || detail.getAuditType() != Type.SERVICE
                    || !MARKER.equals(detail.getOperationName())
                    || !("HostToHostApiPassword.generate".equals(detail.getServiceName())
                        || "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate"
                            .equals(detail.getServiceName()))) continue;
            Object value = object(detail.getRequest()).get("hthOnboarding");
            if (!(value instanceof Map)) continue;
            Map<String,Object> summary = cast(value);
            if ("1".equals(summary.get("schemaVersion"))
                    && "REGENERATE".equals(summary.get("operation"))
                    && summary.get("previousCodeId") instanceof String
                    && !((String) summary.get("previousCodeId")).trim().isEmpty()) {
                dto.setTaskCode("UAT_N_HAP_REGEN");
                return;
            }
        }
    }

    public static void clearRequestStack() {
        com.ofss.digx.infra.thread.ThreadAttribute.clear(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
    }
    private static List<AuditDetailsDTO> projectDetails(List<AuditDetailsDTO> source) {
        if (source == null) return null;
        List<AuditDetailsDTO> result=new ArrayList<AuditDetailsDTO>();
        for (AuditDetailsDTO old:source) {
            if (old == null) continue;
            AuditDetailsDTO copy=new AuditDetailsDTO();
            copy.setAuditType(old.getAuditType()); copy.setServiceName(old.getServiceName()); copy.setOperationName(old.getOperationName());
            copy.setRequest(safe(old.getRequest(),MARKER.equals(old.getOperationName()))); copy.setResponse(safe(old.getResponse())); result.add(copy);
        }
        return result;
    }
    private static List<AuditMap> headers(List<AuditMap> source) {
        if (source == null) return null;
        Set<String> allowed=new HashSet<String>(Arrays.asList("user-agent","accept-language","content-type","x-target-unit",
            "highriskindicator","issuspiciousindicator","iprange","logintime","x-batch-id","x-validate-only",
            "x-device-id","x-device-type","x-device-platform","x-device-version","x-forwarded-for","countryname",
            "x-device-model","x-device-os-version","x-device-brand","x-device-os"));
        List<AuditMap> result=new ArrayList<AuditMap>();
        for (AuditMap old:source) if (old!=null && old.getName()!=null && allowed.contains(old.getName().toLowerCase(Locale.ROOT))) {
            AuditMap copy=new AuditMap(); copy.setName(old.getName()); copy.setValue(old.getValue()); result.add(copy);
        }
        return result;
    }
    private static void diagnostic(String stage, Throwable failure) {
        LOGGER.log(java.util.logging.Level.WARNING,"HTH_AUDIT stage={0}, exceptionType={1}",
            new Object[]{stage,failure.getClass().getName()});
    }
}
