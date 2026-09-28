package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.prefs.Preferences;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.digx.cz.bea.common.framework.crm.CRMConstants;
import com.ofss.digx.cz.bea.common.constants.CZCommonConstants;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.assembler.HthCRMRequestAssembler;

/** Real request-thread metadata and unchanged BCO config/parser/locale utilities. */
public class HthCRMContextTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final String[] ATTRIBUTES={ThreadAttribute.CURRENT_TASK,ThreadAttribute.USER_LOCALE,
        ThreadAttribute.SESSION_CONTEXT,"FMO_IP_ADDRESS","FMO_USER_ID","TRANS_REF","Login-Channel",
        "isAdmin","FMO_USER_AGENT","User-Role","Bio-Type","Reg-Method","Auth-Method","App-Version",
        "TFA_ERR_CODE","TFA_ITOKEN_ID","AR_TOKEN","Authorization","password","code","encryptedCredentials"};
    static boolean fcAttribute(String name) {
        return ThreadAttribute.CURRENT_TASK.equals(name) || ThreadAttribute.USER_LOCALE.equals(name)
            || ThreadAttribute.SESSION_CONTEXT.equals(name) || "TRANS_REF".equals(name);
    }
    static Object attribute(String name) {
        return fcAttribute(name)?ThreadAttribute.get(name):com.ofss.digx.infra.thread.ThreadAttribute.get(name);
    }
    static void attribute(String name,Object value) {
        if(fcAttribute(name))ThreadAttribute.set(name,value);
        else com.ofss.digx.infra.thread.ThreadAttribute.set(name,value);
    }
    static HthCRMEvent3DomainDTO assemble(Map<String,Object> source) {
        HthCRMContext.capture(source);
        return HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",source,"HTH_ONLY_ACTIVITY");
    }
    static HthCRMEvent3DomainDTO scopeEvent(String service,String operation) {
        Map<String,Object> source=HthCRMTest.data(operation);source.put("newUserChannelType","HTH");
        HthCRMContext.capture(source);
        HthCRMEvent3DomainDTO event=HthCRMRequestAssembler.assemble(service,source,"HTH_SCOPE_TEST");
        check(event!=null,"recognized scope fixture "+service+"/"+operation);
        return event;
    }
    static void oneManBankContext() {
        String key=CZCommonConstants.IS_OMB_ENABLED;
        Object oldFc=ThreadAttribute.get(key),oldDigx=com.ofss.digx.infra.thread.ThreadAttribute.get(key);
        Object oldAdmin=attribute("isAdmin");
        try {
            attribute("isAdmin",Boolean.FALSE);ThreadAttribute.set(key,Boolean.TRUE);
            com.ofss.digx.infra.thread.ThreadAttribute.set(key,Boolean.FALSE);
            String[][] supported={{"UserExtensionData.create","CREATE"},{"UserExtensionData.update","UPDATE"},
                {"x.HostToHostUserAccess.submit","ACCESS_CREATE"},{"x.HostToHostUserAccess.edit","ACCESS_EDIT"},
                {"x.HostToHostUserAccess.delete","ACCESS_DELETE"}};
            for(String[] operation:supported)
                check("Y".equals(scopeEvent(operation[0],operation[1]).getOmbFlag()),"existing positive FC One Man Bank decision retained for "+operation[1]);
            String[][] excluded={{"x.HostToHostApiPassword.setup","SETUP"},{"x.HostToHostApiPassword.reset","RESET"},
                {"x.HostToHostApiPassword.generate","GENERATE"},{"x.HostToHostApiPassword.generate","REGENERATE"},
                {"x.HostToHostApiPassword.generate","CODE_ACTIVATE"},{"x.HostToHostManagement.submit","COMPANY_ENABLE"},
                {"x.HostToHostManagement.edit","COMPANY_EDIT"},{"x.HostToHostManagement.disable","COMPANY_DISABLE"}};
            for(String[] operation:excluded)
                check(scopeEvent(operation[0],operation[1]).getOmbFlag()==null,"One Man Bank flag excluded from "+operation[1]);
            ThreadAttribute.set(key,null);com.ofss.digx.infra.thread.ThreadAttribute.set(key,Boolean.TRUE);
            check("Y".equals(scopeEvent("UserExtensionData.update","UPDATE").getOmbFlag()),"positive DIGX decision used if FC decision absent");
            ThreadAttribute.set(key,Boolean.FALSE);
            check(scopeEvent("UserExtensionData.update","UPDATE").getOmbFlag()==null,"FC false takes precedence over DIGX true but cannot prove evaluated negative");
            ThreadAttribute.set(key,null);com.ofss.digx.infra.thread.ThreadAttribute.set(key,Boolean.FALSE);
            check(scopeEvent("x.HostToHostUserAccess.edit","ACCESS_EDIT").getOmbFlag()==null,"platform false default remains unknown/null");
            com.ofss.digx.infra.thread.ThreadAttribute.set(key,null);
            check(scopeEvent("UserExtensionData.create","CREATE").getOmbFlag()==null,"missing One Man Bank decision stays null");
            ThreadAttribute.set(key,"true");com.ofss.digx.infra.thread.ThreadAttribute.set(key,"Y");
            check(scopeEvent("UserExtensionData.update","UPDATE").getOmbFlag()==null,"untyped/incomplete approval decision stays null");
            ThreadAttribute.set(key,Boolean.TRUE);
            for(Object admin:Arrays.asList(Boolean.TRUE,null,"false")) {
                attribute("isAdmin",admin);
                check(scopeEvent("UserExtensionData.update","UPDATE").getOmbFlag()==null,"bank or unknown admin context cannot acquire corporate OMB flag");
            }
        } finally {
            attribute("isAdmin",oldAdmin);ThreadAttribute.set(key,oldFc);
            com.ofss.digx.infra.thread.ThreadAttribute.set(key,oldDigx);
        }
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> previous=new HashMap<String,Object>();
        Object previousDigxTask=com.ofss.digx.infra.thread.ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
        for(String name:ATTRIBUTES){previous.put(name,attribute(name));attribute(name,null);}
        Preferences config=ConfigurationFactory.getInstance().getConfigurations(CRMConstants.CRM_CONFIGURATION);
        Map<String,String> settings=new LinkedHashMap<String,String>();
        settings.put(CRMConstants.CRM_RECORD_TYPE,"3");settings.put(CRMConstants.CRM_FILLER_01,"BCO-FILLER");
        settings.put(CRMConstants.CRM_CHNL_ID_INTERNET,"ELE-TEST");settings.put(CRMConstants.CRM_CHNL_TYPE_CODE_INTERNET,"I");
        settings.put(CRMConstants.CRM_FEE_CHARGE_CODE,"00");settings.put(CRMConstants.CRM_EVENT_COUNTRY_CODE,"HK");
        Map<String,String> oldConfig=new HashMap<String,String>();
        for(Map.Entry<String,String> entry:settings.entrySet()){
            oldConfig.put(entry.getKey(),config.get(entry.getKey(),null));config.put(entry.getKey(),entry.getValue());
        }
        try {
            attribute(ThreadAttribute.CURRENT_TASK,"SYNTHETIC_TASK");
            com.ofss.digx.infra.thread.ThreadAttribute.set(ThreadAttribute.CURRENT_TASK,"SECONDARY_TASK");attribute("FMO_IP_ADDRESS","192.0.2.10");
            attribute("FMO_USER_ID","THREAD_ACTOR@THREAD_PARTY");attribute("TRANS_REF","THREAD_REFERENCE");
            attribute("isAdmin",false);attribute("User-Role","AP");attribute("Bio-Type","FINGERPRINT");
            attribute("Reg-Method","MOBILE");attribute("Auth-Method","TOKEN");attribute("App-Version","9.1.2");
            attribute("TFA_ERR_CODE","ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            attribute("FMO_USER_AGENT","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.6099.129 Safari/537.36");
            attribute(ThreadAttribute.USER_LOCALE,CZCommonConstants.LOCALE_TRAD_CHI);
            for(String secret:Arrays.asList("TFA_ITOKEN_ID","AR_TOKEN","Authorization","password","code","encryptedCredentials"))
                attribute(secret,"DO_NOT_CAPTURE_"+secret);
            SessionContext session=new SessionContext();session.setUserId("SESSION_ACTOR@SESSION_PARTY");
            session.setTransactingPartyCode("SESSION_PARTY");session.setUserLocale(CZCommonConstants.LOCALE_SIMPLI_CHI);
            attribute(ThreadAttribute.SESSION_CONTEXT,session);
            Map<String,Object> source=HthCRMTest.data("RESET");
            source.put("approvalReference","12345678901234567890123456789012345678901234567890");
            source.put("errorCode","HTH_BUSINESS_FAILURE");
            HthCRMEvent3DomainDTO event=assemble(source);
            check("3".equals(event.getRecordType()) && "BCO-FILLER".equals(event.getFiller01()),"record/filler from BCO configuration");
            config.put(CRMConstants.CRM_FILLER_01,"   ");
            check("   ".equals(assemble(HthCRMTest.data("RESET")).getFiller01()),"intentional BCO filler spaces survive context capture and assembly");
            config.put(CRMConstants.CRM_FILLER_01,settings.get(CRMConstants.CRM_FILLER_01));
            check("ELE-TEST".equals(event.getChnlId()) && "I".equals(event.getChnlTypeCode()),"BCO transport channel metadata");
            check("CM".equals(event.getChannelType()) && "HTH".equals(event.getSourceSystem()),"HTH scope remains separate from BCO channel IDs");
            check("00".equals(event.getFeeChrgCode()) && "HK".equals(event.getEventCountryCode()),"fee/country BCO defaults");
            check(CRMConstants.CRM_CORPORATE_INDICATOR.equals(event.getSelfSrvInd()),"corporate indicator follows BCO");
            check("AP@PARTY".equals(event.getUserId()) && "001".equals(event.getAcctNbr()),"event's explicit actor/company retained over context");
            check("SYNTHETIC_TASK".equals(event.getTaskCode()) && "192.0.2.10".equals(event.getIpAddress()),"task/IP from actual thread attributes");
            attribute(ThreadAttribute.CURRENT_TASK,null);
            check("SECONDARY_TASK".equals(assemble(HthCRMTest.data("RESET")).getTaskCode()),"task falls back to DIGX only if FC task absent");
            attribute(ThreadAttribute.CURRENT_TASK,"LATER_TASK");
            check("SYNTHETIC_TASK".equals(event.getTaskCode()),"captured metadata remains stable after request thread changes");
            check("AP".equals(event.getUserRole()) && "FINGERPRINT".equals(event.getBioType())
                && "MOBILE".equals(event.getRegMethod()) && "TOKEN".equals(event.getAuthMethod())
                && "9.1.2".equals(event.getAppVersion()),"BCO role/authentication metadata");
            check("Windows".equals(event.getMobileBrand()) && "Chrome".equals(event.getDeviceModel())
                && "120.0.6099".equals(event.getDeviceOsVersion()),"unchanged BCO user-agent parser semantics");
            check("tc".equals(event.getLang()),"thread locale takes precedence over session locale");
            check("GHIJKLMNOPQRSTUVWXYZ".equals(event.getErrCode()) && "HTH_BUSINESS_FAILURE".equals(event.getErrorCode()),"BCO short error and HTH business error remain distinct");
            Map<String,Object> approvalCode=HthCRMTest.data("RESET");
            approvalCode.put("crmApprovalErrorCode","PREFIX_ABCDEFGHIJKLMNOPQRST");
            check("ABCDEFGHIJKLMNOPQRST".equals(assemble(approvalCode).getErrCode()),"safe first approval error takes precedence over TFA and retains last 20 characters");
            for(Object invalid:Arrays.asList(null,"","error message with spaces",new Object(),String.join("",Collections.nCopies(101,"A")))) {
                approvalCode=HthCRMTest.data("RESET");approvalCode.put("crmApprovalErrorCode",invalid);
                check("GHIJKLMNOPQRSTUVWXYZ".equals(assemble(approvalCode).getErrCode()),"missing or invalid approval error falls back to TFA");
            }
            attribute("TFA_ERR_CODE",null);
            approvalCode=HthCRMTest.data("RESET");approvalCode.put("crmApprovalErrorCode","not a safe code");
            check(assemble(approvalCode).getErrCode()==null,"no approval or TFA code yields null, never error message text");
            attribute("TFA_ERR_CODE","ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            check("1234567890123456789012345678901234567890".equals(event.getEventRem())
                && event.getSourceTrxRefNbr().length()==50,"BCO remark length with full HTH reference retained");
            check("N".equals(event.gettTAutoRoute()) && "N".equals(event.getFinInd()),"nonfinancial onboarding defaults");
            check(event.getEventAmt()==null && event.getAcctAmt()==null && event.getFeeChrgAmt()==null
                && event.getFpsId()==null && event.getPhoneNbr()==null && event.getElectAdd()==null,"nonapplicable financial/FPS fields remain null");
            check(event.getTokenId()==null && event.getAr_Token()==null,"no authentication tokens stored");
            for(Method getter:HthCRMEvent3DomainDTO.class.getDeclaredMethods()) {
                if(Modifier.isPublic(getter.getModifiers()) && getter.getName().startsWith("get")
                    && getter.getParameterTypes().length==0) {
                    Object value=getter.invoke(event);
                    check(value==null || !value.toString().contains("DO_NOT_CAPTURE"),"no secret leaked through "+getter.getName());
                }
            }
            check(!source.keySet().contains("TFA_ITOKEN_ID") && !source.keySet().contains("Authorization"),"capture does not copy arbitrary headers");
            Map<String,Object> fallback=HthCRMTest.data("RESET");fallback.remove("actorUserId");fallback.remove("partyId");
            attribute("isAdmin",true);attribute("Login-Channel","BCM");attribute(ThreadAttribute.USER_LOCALE,null);
            HthCRMEvent3DomainDTO bank=assemble(fallback);
            check(CRMConstants.CRM_CHNL_ID_BCM.equals(bank.getChnlId()) && CRMConstants.CRM_BANK_INDICATOR.equals(bank.getSelfSrvInd()),"BM channel/admin use existing BCO constants");
            check("THREAD_ACTOR@THREAD_PARTY".equals(bank.getUserId()) && "SESSION_PARTY".equals(bank.getAcctNbr()),"missing explicit identity falls back to thread/session");
            check("THREAD_REFERENCE".equals(bank.getEventRem()) && bank.getSourceTrxRefNbr()==null,"common reference does not fabricate an approval reference");
            check("sc".equals(bank.getLang()),"session locale used if thread locale absent");
            attribute("FMO_USER_ID",null);fallback=HthCRMTest.data("RESET");fallback.remove("actorUserId");
            check("SESSION_ACTOR@SESSION_PARTY".equals(assemble(fallback).getUserId()),"session actor final fallback");
            oneManBankContext();
            for(String name:ATTRIBUTES)attribute(name,null);
            for(String name:settings.keySet())config.remove(name);
            HthCRMEvent3DomainDTO absent=assemble(HthCRMTest.data("RESET"));
            check(absent!=null && "en".equals(absent.getLang()),"missing optional context preserves valid event and locale default");
            check(absent.getRecordType()==null && absent.getChnlId()==null && absent.getSelfSrvInd()==null
                && absent.getDeviceModel()==null && absent.getTokenId()==null,"missing configuration/context stays null, not invented values");
            for(Map.Entry<String,String> entry:settings.entrySet())config.put(entry.getKey(),entry.getValue());
            attribute("isAdmin","invalid");attribute("FMO_IP_ADDRESS",new Object());attribute("FMO_USER_AGENT",123);
            HthCRMEvent3DomainDTO malformed=assemble(HthCRMTest.data("RESET"));
            check(malformed.getSelfSrvInd()==null && malformed.getIpAddress()==null && "3".equals(malformed.getRecordType()),"malformed optional context cannot suppress configured metadata");
            for(Map.Entry<String,String> entry:settings.entrySet())check(entry.getValue().equals(config.get(entry.getKey(),null)),"capture never mutates BCO configuration");
            HthCRMTest.CapturedLogs logs=new HthCRMTest.CapturedLogs();logs.start();
            try {
                System.setProperty("hth849.failConfigCategory",CRMConstants.CRM_CONFIGURATION);
                HthCRMEvent3DomainDTO configFailed=assemble(HthCRMTest.data("RESET"));
                check(configFailed!=null && "PASSWORD_RESET".equals(configFailed.getActivityKey())
                    && configFailed.getRecordType()==null && "en".equals(configFailed.getLang()),"optional config failure preserves independent metadata and event");
                check(logs.has("CONTEXT_CONFIG_FAILED",null),"optional config failure records stage diagnostic");
                logs.records.clear();System.setProperty("hth849.failConfigLinkage","true");
                HthCRMEvent3DomainDTO dependencyFailed=assemble(HthCRMTest.data("RESET"));
                check(dependencyFailed!=null && "en".equals(dependencyFailed.getLang()) && dependencyFailed.getRecordType()==null,
                    "optional linkage error does not discard valid onboarding event");
                check(logs.has("CONTEXT_CONFIG_FAILED",null) && logs.text().contains("NoClassDefFoundError")
                    && !logs.text().contains("injected dependency failure"),"linkage diagnostic records exception type without details");
                System.clearProperty("hth849.failConfigLinkage");
                System.clearProperty("hth849.failConfigCategory");
                attribute(ThreadAttribute.USER_LOCALE,Integer.valueOf(42));
                HthCRMEvent3DomainDTO localeFailed=assemble(HthCRMTest.data("RESET"));
                check(localeFailed!=null && "3".equals(localeFailed.getRecordType()) && localeFailed.getLang()==null,
                    "malformed locale cannot suppress valid configuration/event");
                check(logs.has("CONTEXT_LOCALE_FAILED",null) && !logs.text().contains("DO_NOT_CAPTURE"),"optional metadata failures log stage/type only");
            } finally {System.clearProperty("hth849.failConfigCategory");System.clearProperty("hth849.failConfigLinkage");logs.stop();}
        } finally {
            com.ofss.digx.infra.thread.ThreadAttribute.set(ThreadAttribute.CURRENT_TASK,previousDigxTask);
            for(Map.Entry<String,Object> entry:previous.entrySet())attribute(entry.getKey(),entry.getValue());
            for(Map.Entry<String,String> entry:oldConfig.entrySet())if(entry.getValue()==null)config.remove(entry.getKey());else config.put(entry.getKey(),entry.getValue());
        }
        System.out.println("PASS: "+checks+" HTH CRM request-context / BCO metadata / secret exclusion checks");
    }
}
