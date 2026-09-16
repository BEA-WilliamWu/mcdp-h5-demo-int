import com.ofss.digx.cz.bea.common.audit.HthOnboardingAudit;
import com.ofss.digx.infra.audit.dto.*;
import com.ofss.digx.enumeration.audit.*;
import com.ofss.digx.datatype.complex.AuditMap;
import com.ofss.fc.app.context.SessionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.io.*;

/** Synthetic secrets only. Exercises the production projection with real framework DTOs. */
public final class HthOnboardingAuditTest {
    static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> out=new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) out.put((String)pairs[i],pairs[i+1]); return out;
    }
    static AuditDTO dto(Type type,Object request,Object response) {
        AuditDetailsDTO detail=new AuditDetailsDTO(); detail.setAuditType(type); detail.setRequest(request);detail.setResponse(response);
        AuditDTO dto=new AuditDTO(); dto.setTaskCode("CM_N_HAP_SETUP"); dto.setStatus(Status.SUCCESS);
        dto.setAuditDetailsDTOList(new ArrayList<AuditDetailsDTO>(Arrays.asList(detail))); return dto;
    }
    static AuditMap header(String name,String value) { AuditMap h=new AuditMap();h.setName(name);h.setValue(value);return h; }
    static String json(Object value) throws java.lang.Exception { return new ObjectMapper().writeValueAsString(value); }
    @SuppressWarnings("unchecked") static Stack<AuditDetailsDTO> stack() {
        return (Stack<AuditDetailsDTO>)com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
    }
    public static void main(String[] args) throws java.lang.Exception {
        SessionContext context=new SessionContext(); context.setUserId("MAKER@PARTY"); context.setTargetUnit("OBDX_BU");
        String secret="SYNTHETIC-SECRET-791";
        try (HthOnboardingAudit.Entry scope=HthOnboardingAudit.begin(context,"HostToHostApiPassword.setup","SETUP")) {
            AuditDTO nested=dto(Type.HOST,map("password","NESTED-SECRET"),null); nested.setTaskCode(null);
            check(HthOnboardingAudit.prepare(nested),"Live HTH scope protects nested adapters before the business method returns");
            check(!json(nested).contains("NESTED-SECRET"),"Nested pre-return HOST payload is safe");
            scope.result("SUCCESS");
        }
        check(!json(stack()).contains("_SCOPE_"),"Temporary scope marker removed on close");
        HthOnboardingAudit.clearRequestStack();
        Map<String,Object> request=map("requestId","REQ1","encryptedCredentials",secret,"transportKeyId",secret,
            "requestDTO",map("password",secret,"code",secret,"closeId","TARGET@PARTY","passwordHash",secret));
        Map<String,Object> reveal=map("code",secret,"codeId","CODE-ROW","purpose","RESET","codeCipher",secret);
        for (Type type:Arrays.asList(Type.HOST,Type.SERVICE,Type.REST)) {
            AuditDTO audit=dto(type,request,reveal); audit.setRequestHeaders(Arrays.asList(header("token",secret),header("Authorization",secret),header("Cookie",secret),header("x-nonce",secret),header("User-Agent","TEST")));
            audit.setResponseHeaders(Arrays.asList(header("Set-Cookie",secret)));
            audit.setQueryParamList(Arrays.asList(header("code",secret)));
            audit.setRequestUrl("/hostToHostApiPassword/setup?token="+secret);
            audit.setResolvedRequestUrl("/hostToHostApiPassword/setup?token="+secret);
            audit.setUri("/hostToHostApiPassword/setup?token="+secret);
            audit.setSessionID(secret); audit.setAccessKey(secret);
            audit.setThreadAttributeMap(map("PASSWORD",secret,"LEGAL_ENTITY_CODE","TEST"));
            HthOnboardingAudit.prepare(audit); HthOnboardingAudit.finish(audit);
            check(!json(audit).contains(secret),type+" omits every synthetic secret");
            check(json(audit).contains("CODE-ROW"),"Code record metadata retained");
            check(secret.equals(reveal.get("code")),"Authorized reveal value must remain intact");
            check(secret.equals(request.get("encryptedCredentials")),"Original business request unchanged");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();new ObjectOutputStream(bytes).writeObject(audit);
            AuditDTO roundtrip=(AuditDTO)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
            check(!json(roundtrip).contains(secret),"JMS serialization contains safe copy");
        }
        javax.servlet.http.HttpServletRequest http=(javax.servlet.http.HttpServletRequest)java.lang.reflect.Proxy.newProxyInstance(
            HthOnboardingAuditTest.class.getClassLoader(),new Class<?>[]{javax.servlet.http.HttpServletRequest.class},
            (proxy,method,arguments) -> null);
        javax.servlet.http.HttpServletResponse httpResponse=(javax.servlet.http.HttpServletResponse)java.lang.reflect.Proxy.newProxyInstance(
            HthOnboardingAuditTest.class.getClassLoader(),new Class<?>[]{javax.servlet.http.HttpServletResponse.class},
            (proxy,method,arguments) -> null);
        com.ofss.digx.app.context.ChannelContext channel=new com.ofss.digx.app.context.ChannelContext(http,httpResponse,"/setup",false);
        channel.setSessionContext(context); channel.setSessionId(secret); channel.setNonce(secret);
        channel.setResponseNonce(Arrays.asList(secret));
        AuditDTO contextual=dto(Type.REST,request,reveal);
        contextual.setThreadAttributeMap(map("CHANNEL_CONTEXT",channel,"LEGAL_ENTITY_CODE","TEST"));
        HthOnboardingAudit.finish(contextual);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();new ObjectOutputStream(bytes).writeObject(contextual);
        check(!new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1).contains(secret),"JMS context excludes incoming/outgoing nonces and session token");
        check(secret.equals(channel.getNonce()) && secret.equals(channel.getSessionId()),"Original HTTP context remains intact");
        check(((com.ofss.digx.app.context.ChannelContext)contextual.getThreadAttributeMap().get("CHANNEL_CONTEXT")).getSessionContext().getUserId().equals(context.getUserId()),"JMS actor/tenant context preserved");
        AuditDTO forged=dto(Type.REST,map("hthOnboarding",map("schemaVersion","1","operation","FORGED")),null);
        HthOnboardingAudit.prepare(forged);
        check(!json(forged).contains("FORGED"),"Client payload cannot impersonate a trusted business summary");
        AuditDTO error=dto(Type.REST,map(),map("result","SUCCESSFUL","message",map("type","ERROR","code","DIGX_CZ_HTH_API_PASSWORD_002")));
        HthOnboardingAudit.prepare(error);
        check(error.getStatus()==Status.FAILURE,"HTTP successful with ERROR must be audited as failure");
        check(json(error).contains("DIGX_CZ_HTH_API_PASSWORD_002"),"Safe error code retained");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"HostToHostUserAccess.edit","ACCESS_EDIT")) {
            audit.put("accessChanges",Arrays.asList(map("change","ADD","apiMasterId","API1"))).result("SUCCESS");
        }
        AuditDTO merged=dto(Type.REST,map("password",secret),map("result","SUCCESSFUL"));
        merged.getAuditDetailsDTOList().addAll(stack());
        HthOnboardingAudit.finish(merged);
        String nativeView=json(merged.getAuditDetailsDTOList().get(0).getRequest());
        check(nativeView.contains("hthOnboarding") && nativeView.contains("API1") && !nativeView.contains(secret),"Trusted service summary is visible through the existing REST JSON detail");
        HthOnboardingAudit.clearRequestStack();
        com.ofss.fc.service.response.TransactionStatus transaction=new com.ofss.fc.service.response.TransactionStatus();
        transaction.setReplyCode(99); transaction.setErrorCode("DIGX_CZ_HTH_API_PASSWORD_002");
        transaction.setExternalReferenceNo("APPROVAL-REF");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"UserExtensionData.update","UPDATE")) {
            audit.put("effectiveChange",true).result("SUCCESS").response(transaction);
        }
        String transactionSummary=json(stack());
        check(transactionSummary.contains("FAILURE") && transactionSummary.contains("DIGX_CZ_HTH_API_PASSWORD_002")
            && transactionSummary.contains("APPROVAL-REF") && transactionSummary.contains("\"effectiveChange\":false"),"Real TransactionStatus failure/reference captured; failed write is not an effective change");
        HthOnboardingAudit.clearRequestStack();
        com.ofss.digx.app.messages.Status accepted=new com.ofss.digx.app.messages.Status();
        accepted.setResult(com.ofss.digx.app.messages.Status.ResultType.ACCEPTED);
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"UserExtensionData.create","CREATE")) {
            audit.result("SUCCESS").put("effectiveChange",true).response(accepted);
        }
        check(json(stack()).contains("PENDING_APPROVAL") && json(stack()).contains("\"effectiveChange\":false"),"Real REST ACCEPTED state is not effective user creation");
        HthOnboardingAudit.clearRequestStack();
        transaction.setErrorCode("DIGX_APPROVAL_REQUIRED");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"UserExtensionData.update","UPDATE")) {
            audit.result("COMPLETED").response(transaction);
        }
        check(json(stack()).contains("PENDING_APPROVAL") && !json(stack()).contains("FAILURE"),"Real TransactionStatus approval response is pending");
        HthOnboardingAudit.clearRequestStack();
        AuditDTO bco=dto(Type.REST,map("bcoField","existing"),map("result","SUCCESSFUL"));bco.setTaskCode("CM_N_CC");
        Object original=bco.getAuditDetailsDTOList();
        check(!HthOnboardingAudit.prepare(bco) && original==bco.getAuditDetailsDTOList(),"BCO DTO identity and payload unchanged");
        bco.setResolvedRequestUrl("/users?next=/hostToHostApiPassword/setup");
        check(!HthOnboardingAudit.prepare(bco),"HTH text in an unrelated query does not change BCO audit handling");
        AuditDTO unprojectable=dto(Type.REST,new Object() { public String getValue() {throw new IllegalStateException(secret);} },null);
        HthOnboardingAudit.prepare(unprojectable);
        check(json(unprojectable).contains("PAYLOAD_UNAVAILABLE") && !json(unprojectable).contains(secret),"Projection failure records only a safe stage, never the source or exception text");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.user(context,"UPDATE","TARGET","PARTY","BCO",null)) {
            audit.channel("HTH","BCO").result("COMPLETED");
        }
        String transition=json(stack());
        check(transition.contains("HTH") && transition.contains("BCO") && transition.contains("TARGET@PARTY") && transition.contains("MAKER@PARTY"),"HTH to BCO and distinct actor/target captured");
        HthOnboardingAudit.clearRequestStack();
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.user(context,"UPDATE","BCO","PARTY","BCO",null)) { audit.result("COMPLETED"); }
        check(stack()==null,"Ordinary BCO update adds no audit summary");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"HostToHostApiPassword.reset","RESET")) {
            audit.put("requestId","REQ2").put("idempotentReplay",false).failure(new java.lang.Exception("DIGX_CZ_HTH_API_PASSWORD_001")).result("SUCCESS");
        }
        check(json(stack()).contains("FAILURE") && !json(stack()).contains("SUCCESS"),"Failure cannot be overwritten by later success");
        check(json(stack()).contains("false"),"False is retained for replay and no-change evidence");
        HthOnboardingAudit.clearRequestStack();
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"HostToHostUserAccess.edit","ACCESS_EDIT")) { audit.failure(new java.lang.Exception("DIGX_APPROVAL_REQUIRED")); }
        check(json(stack()).contains("PENDING_APPROVAL"),"Approval challenge is not effective success or business failure");
        HthOnboardingAudit.clearRequestStack();
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"HostToHostUserAccess.edit","ACCESS_EDIT")) {
            audit.response(map("status",map("message",map("type","ERROR","code","DIGX_APPROVAL_REQUIRED"))));
        }
        check(json(stack()).contains("PENDING_APPROVAL") && !json(stack()).contains("FAILURE"),"Approval response is pending");
        HthOnboardingAudit.clearRequestStack();
        com.ofss.digx.infra.exceptions.Exception frameworkFailure=new com.ofss.digx.infra.exceptions.Exception();
        frameworkFailure.setErrorCode("DIGX_CZ_HTH_API_PASSWORD_010");
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(context,"HostToHostApiPassword.setup","SETUP")) {
            audit.failure(frameworkFailure).failure(new java.lang.Exception("DIGX_APPROVAL_REQUIRED"));
        }
        check(json(stack()).contains("DIGX_CZ_HTH_API_PASSWORD_010") && json(stack()).contains("FAILURE"),"Framework error getter retained and later approval cannot overwrite failure");
        HthOnboardingAudit.clearRequestStack();
        check(!HthOnboardingAudit.prepare(bco),"Subsequent request does not inherit HTH state");
        System.out.println("PASS: production HOST/SERVICE/REST allowlist, JMS copies, real ERROR outcome, reveal unchanged, channel transitions, actor/target, pending approval, no thread carry-over and BCO isolation");
    }
}
