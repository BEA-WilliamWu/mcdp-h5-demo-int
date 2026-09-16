package com.ofss.digx.cz.bea.app.hosttohost.service;

import fixture.Bank;
import java.util.*;
import java.lang.reflect.*;
import java.util.logging.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiAuthorizationDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.fc.framework.domain.entity.ep.dto.IDispatchData;
import com.ofss.fc.framework.domain.entity.ep.dto.ActivityData;
import com.ofss.fc.domain.ep.service.recipient.ExternalRecipientDerivationHelper;
import com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate;
import com.ofss.fc.domain.ep.entity.action.subscriber.RecipientMessageTemplateKey;
import com.ofss.fc.app.ep.dto.AlertPartyDetailsDTO;
import com.ofss.fc.enumeration.ep.*;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import com.ofss.digx.cz.bea.domain.service.dispatch.SMSDispatcher;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO;

public class Hth1288NotificationTest {
    static HostToHostManagementDTO request;
    static StringBuilder diagnostics = new StringBuilder();
    static void check(boolean b,String m){Bank.check(b,m);}
    static void reset(){
        Bank.writes=Bank.snapshots=Bank.legacy=Bank.registrations=Bank.countryReads=Bank.networkCalls=0;
        Bank.contactFailure=Bank.missingCompany=Bank.snapshotFailure=Bank.writeFailure=Bank.registerFailure=false;
        Bank.registerStatusFailure=Bank.registerNull=Bank.reject=false;
        Bank.email=" company@example.test ";Bank.phone="+86-13800138000";
        Bank.approval="APPROVED";Bank.status="ENABLE";
        Bank.skip="LEGACY_EVENT,HTH_API_SERVICE_DISABLE_SUCCESS,HTH_API_SERVICE_EDIT_SUCCESS";
        Bank.oldApis=new HashSet<>(Arrays.asList("A","B"));
        Bank.events.clear();Bank.activities.clear();Bank.logs.clear();diagnostics.setLength(0);
        Bank.context=new SessionContext();Bank.context.setTargetUnit("OBDX_BU");Bank.context.setUserLocale("en");
        Bank.context.setUserId("BM_APPROVER");Bank.context.setTransactingPartyCode("OPERATOR_PARTY");
        request=new HostToHostManagementDTO();request.setPartyId("COMPANY");request.setReferenceNumber("REF-1288");apis("A","C");
    }
    static void apis(String...codes){
        List<HostToHostApiAuthorizationDTO> values=new ArrayList<>();
        for(String code:codes){HostToHostApiAuthorizationDTO d=new HostToHostApiAuthorizationDTO();d.setApiCode(code);d.setSelected(true);values.add(d);}
        request.setApiAuthorizations(values);
    }
    static void save(String action)throws Exception{new HostToHostManagement().testSave(Bank.context,request,action);}
    static NotificationDetail detail(){return Bank.logs.get(0).getNotificationDetails()[0];}
    static void bean(Object root,String path,Object value)throws Exception{
        String[] parts=path.split("\\.");Object cursor=root;
        for(int i=0;i<parts.length-1;i++){
            Method get=cursor.getClass().getMethod("get"+parts[i]);Object child=get.invoke(cursor);
            if(child==null){child=get.getReturnType().getDeclaredConstructor().newInstance();cursor.getClass().getMethod("set"+parts[i],get.getReturnType()).invoke(cursor,child);}cursor=child;
        }
        cursor.getClass().getMethod("set"+parts[parts.length-1],value.getClass()).invoke(cursor,value);
    }
    static AlertPartyDetailsDTO recipient()throws Exception{
        UserProfUpdateActivityLogDTO log=(UserProfUpdateActivityLogDTO)Bank.logs.get(0);
        check("COMPANY".equals(log.getCustomerId()),"company id persists on ActivityLog");
        RecipientMessageTemplateKey key=new RecipientMessageTemplateKey();key.setSubscriberType(SubscriberType.EXTERNAL);
        key.setDestinationType(detail().getDestination());
        IRecipientMessageTemplate template=(IRecipientMessageTemplate)Proxy.newProxyInstance(IRecipientMessageTemplate.class.getClassLoader(),new Class[]{IRecipientMessageTemplate.class},(p,m,a)->m.getName().equals("getRecipientMessageTemplateKey")?key:null);
        List<AlertPartyDetailsDTO> recipients=new ExternalRecipientDerivationHelper().deriveRecipients(Bank.context,log,template,null);
        check(recipients.size()==1,"exactly one external company recipient");
        check(detail().getDispatchAddress().equals(recipients.get(0).getElectronicAddress()),"SDK preserves explicit company address");
        check("COMPANY".equals(recipients.get(0).getPartyId()),"SDK carries explicit company party id");
        return recipients.get(0);
    }
    static void deliverSms(String expected)throws Exception{
        AlertPartyDetailsDTO recipient=recipient();String event=Bank.events.get(0),activity=Bank.activities.get(0);
        AlertRequestDTO a=new AlertRequestDTO();a.setActivityLog(Bank.logs.get(0));a.setUserId("BM_APPROVER");a.setCodActDataId("TEST-ACTIVITY");
        bean(a,"EventAction.KeyDTO.ActivityId",activity);bean(a,"EventAction.KeyDTO.EventId",event);bean(a,"EventAction.KeyDTO.ActionId","A");
        bean(a,"RecipientMessageTemplate.KeyDTO.EventId",event);bean(a,"RecipientMessageTemplate.KeyDTO.MessageTemplateId","HTH1288_DISABLE_SMS_en");
        bean(a,"AlertContactPreference.DispatchAddress",recipient.getElectronicAddress());bean(a,"PreferredRecipient.ContactDetails.PartyId",recipient.getPartyId());
        IDispatchData dispatch=(IDispatchData)Proxy.newProxyInstance(IDispatchData.class.getClassLoader(),new Class[]{IDispatchData.class},(p,m,args)->m.getName().equals("getDispatchData")?new NameValuePair[0]:null);
        boolean ok=new SMSDispatcher().test(a,dispatch,"HTH service notification").getIsDispatchSuccessfull();
        check(ok!=Bank.reject,"MNG success and rejection propagated");
        MNGSmsAlertDTO sms=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);
        check(expected.equals(sms.getBody()[0].getSms().getDistNo()),"real dispatcher sends company country+number");
        check(Bank.countryReads==0,"never read BM user's 852 country");
    }
    public static void main(String[] args)throws Exception{
        Logger logger=Logger.getLogger("1288-test");logger.setUseParentHandlers(false);
        logger.addHandler(new Handler(){public void publish(LogRecord r){diagnostics.append(r.getMessage()).append('\n');}public void flush(){}public void close(){}});
        for(String action:new String[]{"EDIT","DISABLE"}){
            reset();save(action);
            check(Bank.events.equals(Collections.singletonList("HTH_API_SERVICE_"+action+"_SUCCESS")),"action-specific event");
            check(Bank.activities.get(0).endsWith("."+action.toLowerCase(Locale.ROOT)),"action-specific activity");
            check(Bank.legacy==0 && Bank.registrations==1,"new path does not duplicate legacy notification");
            check(detail().getDestination()==DestinationType.EMAIL && "company@example.test".equals(detail().getDispatchAddress()),"email priority");
            check("COMPANY".equals(detail().getRecipientId()),"verified company id despite adapter's null partyIdValue");recipient();
            check(diagnostics.toString().contains("outcome=Registered"),"registration success distinguished from delivery");
            for(String approval:new String[]{"PENDING_APPROVAL","REJECTED","CANCELLED",null}){
                reset();Bank.approval=approval;save(action);check(Bank.writes==0 && Bank.registrations==0,"only final approval");
            }
            reset();Bank.writeFailure=true;
            try{save(action);throw new AssertionError("write failure expected");}catch(IllegalStateException expected){}
            check(Bank.registrations==0,"failed save never registers notification");
            reset();Bank.context.setServiceCallContextType("VALIDATE");save(action);check(Bank.registrations==0,"validate does not notify");
            reset();Bank.context.setTargetUnit("OTHER");save(action);check(Bank.registrations==0,"not another entity");
        }
        reset();apis("B","A","A");save("EDIT");check(Bank.registrations==0,"same API set reordered/deduplicated does not notify");
        reset();apis("A");save("EDIT");check(Bank.registrations==1,"API removal notifies");
        reset();apis("A","B","C");save("EDIT");check(Bank.registrations==1,"API addition notifies");
        reset();save("ENABLE");check(Bank.legacy==1 && Bank.registrations==0,"597 retains old notification path");
        reset();Bank.status="DISABLE";save("DISABLE");check(Bank.registrations==0,"already disabled is not a transition");
        System.out.println("PASS: final approval, action/event routing, pre-save API comparison, 597 isolation, no success on save failure");

        reset();Bank.email="";save("DISABLE");check(detail().getDestination()==DestinationType.SMS,"SMS only without email");deliverSms("8613800138000");
        Bank.reject=true;deliverSms("8613800138000");
        reset();Bank.email=null;Bank.phone=" +852 61234567 ";save("EDIT");deliverSms("85261234567");
        for(String number:new String[]{null,"","61234567","+85261234567","+852-12","+852-12345678901234","+0-61234567","user~852"}){
            reset();Bank.email=null;Bank.phone=number;save("EDIT");check(Bank.registrations==0,"ambiguous/invalid company number not sent: "+number);
        }
        reset();Bank.email="invalid-address";save("EDIT");check(Bank.registrations==0,"populated invalid email does not fall back");
        reset();Bank.email="company@example.test,another@example.test";save("EDIT");check(Bank.registrations==0,"no multiple recipients in company email");
        reset();Bank.email=null;Bank.skip="LEGACY_EVENT";save("DISABLE");check(Bank.registrations==0 && diagnostics.toString().contains("MissingSmsCountryRouting"),"missing deployment configuration cannot misroute SMS");
        System.out.println("PASS: actual ActivityData/SDK recipient and SMSDispatcher-to-MNG routing, priority, missing/invalid contacts");

        for(int failure=0;failure<6;failure++){
            reset();
            if(failure==0)Bank.contactFailure=true;if(failure==1)Bank.registerFailure=true;
            if(failure==2)Bank.registerStatusFailure=true;if(failure==3)Bank.registerNull=true;
            if(failure==4)Bank.missingCompany=true;if(failure==5)Bank.snapshotFailure=true;
            save("EDIT");check(Bank.writes==1 && Bank.logs.isEmpty(),"recoverable notification failure does not bypass business save");
            String text=diagnostics.toString();check(!text.contains("outcome=Registered") && !text.contains("PRIVATE_") && !text.contains("example.test") && !text.contains("13800138000"),"logs have stage/type only, no false success or contact data");
        }
        System.out.println("PASS: registration error/null status, dependency failures and sanitized diagnostics");
    }
}
