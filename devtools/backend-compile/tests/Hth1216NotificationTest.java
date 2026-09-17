package com.ofss.digx.cz.bea.app.hosttohost.service;

import fixture.Bank;
import java.util.*;
import java.lang.reflect.*;
import java.util.logging.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserManagementActivityLogDTO;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.fc.framework.domain.entity.ep.dto.IDispatchData;
import com.ofss.fc.framework.domain.entity.ep.dto.ActivityData;
import com.ofss.fc.domain.ep.service.recipient.ExternalRecipientDerivationHelper;
import com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate;
import com.ofss.fc.domain.ep.entity.action.subscriber.RecipientMessageTemplateKey;
import com.ofss.fc.app.ep.dto.AlertPartyDetailsDTO;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.digx.cz.bea.domain.service.dispatch.SMSDispatcher;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO;

public final class Hth1216NotificationTest {
    static final String ACTIVITY=HostToHostUserAccess.class.getName()+".submit";
    static final String TARGET="TARGET@PARTY";
    static HostToHostUserAccessDTO request;
    static User target, approver;
    static List<UserManagementActivityLogDTO> prepare(){return new HthUserAccessNotification().prepare(Bank.context,request,Transaction.service,"REF");}
    static void check(boolean ok,String name){Bank.check(ok,name);}
    static User user(String email,String mobile){User u=new User();u.setEmailId(email);u.setMobileNumber(mobile);return u;}
    static void reset(){
        Bank.context=new SessionContext();Bank.context.setUserId("WORKER@PARTY");Bank.context.setTargetUnit("OBDX_BU");
        Bank.context.setTransactingPartyCode("PARTY");Bank.context.setServiceCallContextType("EXECUTE");
        Bank.events.clear();Bank.logs.clear();Bank.committed.clear();Bank.approvalReads=0;Bank.userReads=0;
        Bank.approvalFailure=false;Bank.extensionFailure=false;Bank.writeFailure=false;Bank.registerFailure=false;
        Bank.registerStatusFailure=false;Bank.approval=true;Bank.rollback=false;Bank.writes=0;Bank.registrations=0;
        Bank.company="company@example.test";Bank.includeSmsEvents=false;Bank.reject=false;Bank.country="852";
        Transaction.status="APPROVED";Transaction.signers="MAKER~EARLIER~FINAL~";Transaction.service=ACTIVITY;
        User.rows.clear();target=user("target@example.test","61234567");approver=user("final@example.test","63456789");
        User.rows.put(TARGET,target);User.rows.put("FINAL",approver);User.rows.put("WORKER@PARTY",user("worker@example.test","69999999"));
        com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.clear();
        for(String id:new String[]{TARGET,"FINAL"}){
            com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData e=new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
            e.setMobileCode(id.equals("FINAL")?"+853":"852");com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.put(id,e);
        }
        request=new HostToHostUserAccessDTO();request.setPartyId("PARTY");request.setCloseId(TARGET);
        request.setAccessPartyId("OTHER_COMPANY");request.setUsername("UNTRUSTED_DISPLAY_NAME");
    }
    static long count(List<UserManagementActivityLogDTO> logs,DestinationType type){return logs.stream().filter(l->l.getNotificationDetails()[0].getDestination()==type).count();}
    static void bean(Object root,String path,Object value)throws Exception{
        String[] parts=path.split("\\.");Object cursor=root;
        for(int i=0;i<parts.length-1;i++){
            Method get=cursor.getClass().getMethod("get"+parts[i]);Object child=get.invoke(cursor);
            if(child==null){child=get.getReturnType().getDeclaredConstructor().newInstance();cursor.getClass().getMethod("set"+parts[i],get.getReturnType()).invoke(cursor,child);}cursor=child;
        }
        cursor.getClass().getMethod("set"+parts[parts.length-1],value.getClass()).invoke(cursor,value);
    }
    static AlertPartyDetailsDTO sdkRecipient(UserManagementActivityLogDTO log)throws Exception{
        ActivityData data=new ActivityData();data.setActivityLog(log);
        UserManagementActivityLogDTO stored=(UserManagementActivityLogDTO)data.getActivityLog();
        check(stored.getUserNameId().equals("TARGET"),"serialized body target remains TARGET");
        RecipientMessageTemplateKey key=new RecipientMessageTemplateKey();key.setSubscriberType(SubscriberType.EXTERNAL);
        key.setDestinationType(log.getNotificationDetails()[0].getDestination());
        IRecipientMessageTemplate template=(IRecipientMessageTemplate)Proxy.newProxyInstance(IRecipientMessageTemplate.class.getClassLoader(),new Class[]{IRecipientMessageTemplate.class},(p,m,a)->m.getName().equals("getRecipientMessageTemplateKey")?key:null);
        List<AlertPartyDetailsDTO> recipients=new ExternalRecipientDerivationHelper().deriveRecipients(Bank.context,stored,template,null);
        check(recipients.size()==1,"SDK produces exactly one explicit recipient");return recipients.get(0);
    }
    static void testDelivery(UserManagementActivityLogDTO log)throws Exception{
        AlertPartyDetailsDTO recipient=sdkRecipient(log);
        check("PARTY".equals(recipient.getPartyId()),"recipient company is target owner, not associated account owner");
        check(log.getNotificationDetails()[0].getDispatchAddress().equals(recipient.getElectronicAddress()),"SDK preserves explicit address");
        if(log.getNotificationDetails()[0].getDestination()!=DestinationType.SMS)return;
        check(log.getUserId().equals(recipient.getUserId()),"SDK recipientUserId survives persistence");
        AlertRequestDTO a=new AlertRequestDTO();a.setActivityLog(log);a.setUserId(recipient.getUserId());a.setCodActDataId("TEST-ACTIVITY");
        bean(a,"EventAction.KeyDTO.ActivityId",ACTIVITY);bean(a,"EventAction.KeyDTO.EventId",HthUserAccessNotification.EVENT);bean(a,"EventAction.KeyDTO.ActionId","A");
        bean(a,"RecipientMessageTemplate.KeyDTO.EventId",HthUserAccessNotification.EVENT);bean(a,"RecipientMessageTemplate.KeyDTO.MessageTemplateId","HTH1216_SMS_en");
        bean(a,"AlertContactPreference.DispatchAddress",recipient.getElectronicAddress());bean(a,"PreferredRecipient.ContactDetails.PartyId",recipient.getPartyId());
        NameValuePair pair=new NameValuePair();pair.setName("UserId");pair.setGenericName("UserId");pair.setValue(log.getUserId());
        IDispatchData dispatch=(IDispatchData)Proxy.newProxyInstance(IDispatchData.class.getClassLoader(),new Class[]{IDispatchData.class},(p,m,args)->m.getName().equals("getDispatchData")?new NameValuePair[]{pair}:null);
        boolean ok=new SMSDispatcher().test(a,dispatch,"BCO body for TARGET").getIsDispatchSuccessfull();
        check(ok!=Bank.reject,"actual SMS dispatcher reports MNG response");
        MNGSmsAlertDTO sms=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);
        String expected=(log.getUserId().equals("FINAL")?"853":"852")+recipient.getElectronicAddress();
        check(expected.equals(sms.getBody()[0].getSms().getDistNo()),"MNG uses the correct recipient country and number");
    }
    public static void main(String[] args)throws Exception{
        reset();List<UserManagementActivityLogDTO> messages=prepare();
        check(messages.size()==5 && count(messages,DestinationType.EMAIL)==3 && count(messages,DestinationType.SMS)==2,"three emails and two SMS");
        for(UserManagementActivityLogDTO log:messages){check("TARGET".equals(log.getUserNameId()),"template uses target, never AP");testDelivery(log);}
        Bank.reject=true;testDelivery(messages.get(1));
        System.out.println("PASS: actual DTO serialization, SDK recipient resolution and original SMS dispatcher/MNG routing");

        reset();Bank.company=" TARGET@EXAMPLE.TEST ";approver.setEmailId("target@example.test");
        approver.setMobileNumber("6123-4567");com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.get("FINAL").setMobileCode("+852");
        check(prepare().size()==2,"dedup all three roles across email and SMS");
        com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.get("FINAL").setMobileCode("853");
        check(prepare().size()==3,"same national number in different countries remains distinct");
        reset();Transaction.signers="MAKER~EARLIER~ FINAL ~~";check(prepare().size()==5,"last nonempty signer only");
        Transaction.signers="MAKER~";check(prepare().size()==3,"maker alone is never final approver");
        reset();Bank.extensionFailure=true;messages=prepare();check(messages.size()==3 && count(messages,DestinationType.SMS)==0,"missing countries preserve email");
        reset();User.rows.remove(TARGET);check(prepare().size()==3,"missing target does not discard final AP/company");
        reset();Bank.company="";check(prepare().size()==4,"missing company email never uses office phone");
        reset();request.setCloseId("TARGET");check(prepare().size()==5,"short closeId resolved within verified company");
        System.out.println("PASS: three-role dedup, final signer, missing contacts and target company selection");

        for(String op:new String[]{"submit","edit"}){
            reset();Transaction.service=HostToHostUserAccess.class.getName()+"."+op;
            new HostToHostUserAccess().testSave(Bank.context,request,op);
            check(Bank.writes==1 && Bank.events.size()==5 && Bank.committed.size()==5,"real "+op+" save method registers one notification group after storage");
            check(Collections.frequency(Bank.events,HthUserAccessNotification.EVENT)==4,"target and final approver use BCO user templates");
            check(Collections.frequency(Bank.events,HthUserAccessNotification.COMPANY_EVENT)==1,"company uses its own BCO template once");
            UserManagementActivityLogDTO companyLog=(UserManagementActivityLogDTO)Bank.logs.get(4);
            check(companyLog.getNotificationDetails()[0].getDestination()==DestinationType.EMAIL
                    && "company@example.test".equals(companyLog.getNotificationDetails()[0].getDispatchAddress()),
                    "company event contains company email only");
            reset();Transaction.service=HostToHostUserAccess.class.getName()+"."+op;Bank.writeFailure=true;
            new HostToHostUserAccess().testSave(Bank.context,request,op);
            check(Bank.events.isEmpty() && Bank.committed.isEmpty(),"storage failure prevents notifications");
            reset();Transaction.service=HostToHostUserAccess.class.getName()+"."+op;Bank.approval=false;
            new HostToHostUserAccess().testSave(Bank.context,request,op);check(Bank.writes==0 && Bank.events.isEmpty(),"maker/intermediate execution not notified");
        }
        reset();new HostToHostUserAccess().testSave(Bank.context,request,"delete");check(Bank.events.isEmpty() && Bank.approvalReads==0,"delete creates no new dependencies/notifications");
        for(String status:new String[]{"PENDING_APPROVAL","REJECTED"}){reset();Transaction.status=status;check(prepare().isEmpty(),"nonfinal transaction rejected");}
        reset();Transaction.service="wrong";check(new HthUserAccessNotification().prepare(Bank.context,request,ACTIVITY,"REF").isEmpty(),"wrong approval activity rejected");
        reset();Bank.context.setServiceCallContextType("VALIDATE");check(prepare().isEmpty() && Bank.approvalReads==0,"validate phase excluded");
        reset();Bank.context.setTargetUnit("OTHER");check(prepare().isEmpty() && Bank.approvalReads==0,"other unit excluded");
        reset();check(new HthUserAccessNotification().prepare(Bank.context,request,ACTIVITY,"").isEmpty(),"missing transaction reference excluded");
        System.out.println("PASS: actual create/edit hook ordering, negative approval gates and delete isolation");

        reset();Bank.company=" TARGET@EXAMPLE.TEST ";
        new HthUserAccessNotification().notifyApproved(Bank.context,request,ACTIVITY,"REF");
        check(Bank.events.size()==4 && !Bank.events.contains(HthUserAccessNotification.COMPANY_EVENT),
                "shared target/company email is sent once with the user template");
        reset();Bank.company=" FINAL@EXAMPLE.TEST ";
        new HthUserAccessNotification().notifyApproved(Bank.context,request,ACTIVITY,"REF");
        check(Bank.events.size()==4 && !Bank.events.contains(HthUserAccessNotification.COMPANY_EVENT),
                "shared approver/company email is sent once with the user template");
        reset();User.rows.remove(TARGET);User.rows.remove("FINAL");
        new HthUserAccessNotification().notifyApproved(Bank.context,request,ACTIVITY,"REF");
        check(Bank.events.equals(Collections.singletonList(HthUserAccessNotification.COMPANY_EVENT)),
                "company notification remains separate when user contacts are unavailable");
        System.out.println("PASS: user/company event routing and dedup across template variants");

        reset();final List<String> diagnostics=new ArrayList<>();Logger logger=Logger.getLogger(HthUserAccessNotification.class.getName());
        Handler handler=new Handler(){public void publish(LogRecord r){diagnostics.add(r.getMessage()+Arrays.toString(r.getParameters()));}public void flush(){}public void close(){}};logger.addHandler(handler);
        Bank.registerFailure=true;new HthUserAccessNotification().notifyApproved(Bank.context,request,ACTIVITY,"REF");
        check(Bank.registrations==5 && Bank.events.isEmpty(),"registration errors contained per recipient");
        Bank.registerFailure=false;Bank.registerStatusFailure=true;new HthUserAccessNotification().notifyApproved(Bank.context,request,ACTIVITY,"REF");
        check(diagnostics.stream().anyMatch(s->s.contains("UnsuccessfulStatus")),"returned registration failures diagnosed");
        check(diagnostics.stream().noneMatch(s->s.contains("example.test") || s.contains("61234567") || s.contains("DO_NOT_LOG_CONTACT")),"no contacts or exception contents in diagnostic logs");
        logger.removeHandler(handler);
        System.out.println("PASS: registration failures and privacy-safe diagnostics");
    }
}
