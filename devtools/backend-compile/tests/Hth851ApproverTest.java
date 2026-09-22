package com.ofss.digx.cz.bea.app.sms.service.user;

import fixture.Bank;
import java.util.*;
import java.lang.reflect.*;
import com.ofss.digx.cz.bea.app.sms.dto.user.*;
import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.fc.framework.domain.entity.ep.dto.IDispatchData;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO;
import com.ofss.digx.cz.bea.domain.service.dispatch.SMSDispatcher;
import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;
import com.ofss.fc.domain.ep.service.recipient.ExternalRecipientDerivationHelper;
import com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate;
import com.ofss.fc.domain.ep.entity.action.subscriber.RecipientMessageTemplateKey;
import com.ofss.fc.app.ep.dto.AlertPartyDetailsDTO;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.digx.cz.bea.app.sms.service.user.ext.CZUserExtensionDataExt;
import com.ofss.digx.cz.bea.app.sms.service.user.ext.IUserExtensionDataExtExecutor;
import com.ofss.fc.service.response.TransactionStatus;

public final class Hth851ApproverTest {
    static final String ACTIVITY="com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    static User oldUser, finalUser;
    static UserExtensionDataDTO request;
    static com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData stored, finalExtension;
    static void check(boolean ok,String name){Bank.check(ok,name);}
    static User user(String mail,String phone){User u=new User();u.setEmailId(mail);u.setMobileNumber(phone);return u;}
    static UserAlertRequestDTO changes(boolean email,boolean mobile){UserAlertRequestDTO c=new UserAlertRequestDTO();c.setEmailId(!email);c.setMobNo(!mobile);return c;}
    static HthProfileApproverNotification.Approver resolve(UserAlertRequestDTO c){return HthProfileApproverNotification.resolve(Bank.context,request,c,stored,oldUser);}
    static void reset(){
        Bank.context=new SessionContext();Bank.context.setUserId("WORKER@PARTY");Bank.context.setTargetUnit("OBDX_BU");
        Bank.context.setServiceCallContextType("EXECUTE");Bank.context.setTransactingPartyCode("PARTY");
        Bank.events.clear();Bank.logs.clear();Bank.approvalReads=0;Bank.userReads=0;Bank.approvalFailure=false;Bank.extensionFailure=false;
        Bank.company="company@example.test";Bank.reject=false;
        Transaction.status="APPROVED";Transaction.signers="MAKER~EARLIER~FINAL~";Transaction.service=ACTIVITY;
        com.ofss.digx.infra.thread.ThreadAttribute.reference="APPROVAL-851";
        oldUser=user("old@example.test","61234567");finalUser=user("final@example.test","63456789");
        User.rows.clear();User.rows.put("FINAL",finalUser);User.rows.put("WORKER@PARTY",user("worker@example.test","65555555"));
        stored=new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();stored.setCdcNo("PARTY");stored.setUserID("TARGET@PARTY");stored.setMobileCode("852");stored.setUserChannelType("HTH");
        finalExtension=new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();finalExtension.setMobileCode("+853");
        com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.clear();com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData.rows.put("FINAL",finalExtension);
        request=new UserExtensionDataDTO();request.setCdcNo("PARTY");request.setUserID("TARGET@PARTY");request.setUserChannelType("HTH");request.setMobileCode("852");
        UserDTO next=new UserDTO();next.setEmailId("new@example.test");next.setMobileNumber("62345678");request.setUserDTO(next);
    }
    static void invoke(UserAlertRequestDTO c,HthProfileApproverNotification.Approver ap)throws Exception{
        Bank.events.clear();Bank.logs.clear();
        Method m=UserExtensionData.class.getDeclaredMethod("alertUserProfileUpdate",SessionContext.class,UserAlertRequestDTO.class,UserExtensionDataDTO.class,User.class,String.class,HthProfileApproverNotification.Approver.class);
        m.setAccessible(true);m.invoke(new UserExtensionData(),Bank.context,c,request,oldUser,oldUser.getMobileNumber(),ap);
    }
    static List<String> summaries(){
        List<String> result=new ArrayList<>();
        for(int i=0;i<Bank.events.size();i++){
            ActivityLog log=Bank.logs.get(i);String row=Bank.events.get(i);
            if(log instanceof UserProfUpdateActivityLogDTO){UserProfUpdateActivityLogDTO p=(UserProfUpdateActivityLogDTO)log;
                NotificationDetail d=p.getNotificationDetails()[0];row+="|"+d.getDestination()+"|"+d.getDispatchAddress()+"|"+p.getProfileUser()+"|"+p.getEngMailSubj()+"|"+p.getUserId()+"|"+p.getEmailId();}
            result.add(row);
        }
        return result;
    }
    static long emails(String destination){return Bank.logs.stream().filter(l->l instanceof UserProfUpdateActivityLogDTO).map(l->(UserProfUpdateActivityLogDTO)l)
            .filter(l->"EMAIL".equals(String.valueOf(l.getNotificationDetails()[0].getDestination())) && destination.equals(l.getNotificationDetails()[0].getDispatchAddress())).count();}
    static void bean(Object root,String path,Object value)throws Exception{
        String[] parts=path.split("\\.");Object cursor=root;
        for(int i=0;i<parts.length-1;i++){
            Method getter=cursor.getClass().getMethod("get"+parts[i]);Object child=getter.invoke(cursor);
            if(child==null){child=getter.getReturnType().getDeclaredConstructor().newInstance();cursor.getClass().getMethod("set"+parts[i],getter.getReturnType()).invoke(cursor,child);}cursor=child;
        }
        cursor.getClass().getMethod("set"+parts[parts.length-1],value.getClass()).invoke(cursor,value);
    }
    static boolean send(UserProfUpdateActivityLogDTO log,String event,String activity)throws Exception{
        RecipientMessageTemplateKey key=new RecipientMessageTemplateKey();
        key.setSubscriberType(SubscriberType.EXTERNAL);key.setDestinationType(DestinationType.SMS);
        IRecipientMessageTemplate template=(IRecipientMessageTemplate)Proxy.newProxyInstance(IRecipientMessageTemplate.class.getClassLoader(),new Class[]{IRecipientMessageTemplate.class},
                (p,m,args)->m.getName().equals("getRecipientMessageTemplateKey")?key:null);
        List<AlertPartyDetailsDTO> recipients=new ExternalRecipientDerivationHelper().deriveRecipients(Bank.context,log,template,null);
        check(recipients.size()==1,"real SDK resolves external recipient");
        AlertPartyDetailsDTO recipient=recipients.get(0);
        if(log.getInputfacts()!=null) check("FINAL".equals(recipient.getUserId()),"real SDK respects recipientUserId input fact");
        AlertRequestDTO a=new AlertRequestDTO();a.setActivityLog(log);a.setUserId(recipient.getUserId());a.setCodActDataId("TEST-ACTIVITY");
        bean(a,"EventAction.KeyDTO.ActivityId",activity);bean(a,"EventAction.KeyDTO.EventId",event);bean(a,"EventAction.KeyDTO.ActionId","A");
        bean(a,"RecipientMessageTemplate.KeyDTO.EventId",event);bean(a,"RecipientMessageTemplate.KeyDTO.MessageTemplateId","BCO-TEMPLATE");
        bean(a,"AlertContactPreference.DispatchAddress",recipient.getElectronicAddress());bean(a,"PreferredRecipient.ContactDetails.PartyId",recipient.getPartyId());
        NameValuePair pair=new NameValuePair();pair.setName("UserId");pair.setGenericName("UserId");pair.setValue(log.getUserId());
        IDispatchData data=(IDispatchData)Proxy.newProxyInstance(IDispatchData.class.getClassLoader(),new Class[]{IDispatchData.class},(p,m,args)->m.getName().equals("getDispatchData")?new NameValuePair[]{pair}:null);
        return new SMSDispatcher().test(a,data,"BCO reminder for TARGET").getIsDispatchSuccessfull();
    }
    static void hook(Boolean notify)throws Exception{
        UserExtensionData service=new UserExtensionData();
        Field executor=UserExtensionData.class.getDeclaredField("extensionExecutor");executor.setAccessible(true);
        executor.set(service,Proxy.newProxyInstance(IUserExtensionDataExtExecutor.class.getClassLoader(),
                new Class[]{IUserExtensionDataExtExecutor.class},(p,m,a)->{
                    check("postUpdate".equals(m.getName()),"only existing postUpdate is invoked");
                    new CZUserExtensionDataExt().postUpdate((SessionContext)a[0],(UserExtensionDataDTO)a[1],(TransactionStatus)a[2]);
                    return null;
                }));
        Method post=UserExtensionData.class.getDeclaredMethod("postUpdateWithHthPinNotification",
                SessionContext.class,UserExtensionDataDTO.class,TransactionStatus.class,Boolean.class);
        post.setAccessible(true);post.invoke(service,Bank.context,request,new TransactionStatus(),notify);
    }
    static void storedChannelTests()throws Exception{
        reset();
        // Match the actual ORM read: the transient channel is absent even for an HTH user.
        stored.setUserChannelType(null);stored.setSecurityQuestionsBypass("N");request.setBypassFlag("N");
        com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory.profiles.clear();
        String key=com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter.userProfileKey("PARTY","TARGET@PARTY");
        com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory.profiles.put(key,"TARGET@PARTY");
        HthProfileApproverNotification.loadStoredChannel(Bank.context,stored);
        Boolean pin=HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request);
        check(Boolean.FALSE.equals(pin),"unhydrated HTH read resolves contact-only PIN suppression");
        TransactionStatus status=new TransactionStatus();status.setReplyCode(0);status.setErrorCode("0");
        check(HthProfileApproverNotification.profileUpdateSucceeded(status,pin),"resolved HTH accepts actual UAT success");
        HthProfileApproverNotification.Approver ap=resolve(changes(true,true));
        oldUser.setEmailId(request.getUserDTO().getEmailId());
        invoke(changes(true,true),ap);
        check(emails("old@example.test")==1 && emails("new@example.test")==1,"resolved channel preserves old/new email");
        check(emails("final@example.test")==1,"resolved channel includes final approver");
        com.ofss.digx.infra.thread.ThreadAttribute.set("isAdmin",false);
        CZUserExtensionDataExt.pinCalls=0;hook(pin);
        check(CZUserExtensionDataExt.pinCalls==0,"real postUpdate sends no wrong PIN reminder");
        // A stale HTH cache field / HTH request cannot turn a persisted BCO user into HTH.
        com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory.profiles.clear();
        HthProfileApproverNotification.loadStoredChannel(Bank.context,stored);
        check("BCO".equals(stored.getUserChannelType()) && HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request)==null,"BCO verified from persistence");
        com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory.fail=true;
        try {HthProfileApproverNotification.loadStoredChannel(Bank.context,stored);throw new AssertionError("lookup failure must propagate");}
        catch(IllegalStateException expected){}finally{com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory.fail=false;}
        System.out.println("PASS: transient ORM channel resolved from persisted HTH ownership; old/new email, final AP and actual PIN hook verified; lookup failure never falls back to BCO");
    }
    static void pinNotificationTests()throws Exception{
        reset();String key=CZUserExtensionDataExt.HTH_LOGIN_PIN_RESET_NOTIFICATION;
        com.ofss.digx.infra.thread.ThreadAttribute.set("isAdmin",false);
        // Contact-only saves do not change PIN reset state; real toggles still send.
        for(String[] state:new String[][]{{"N","N"},{"Y","Y"},{"N",null},{"Y",null},{"N","Y"},{"Y","N"}}){
            stored.setSecurityQuestionsBypass(state[0]);request.setBypassFlag(state[1]);
            Boolean notify=HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request);
            CZUserExtensionDataExt.pinCalls=0;CZUserExtensionDataExt.itokenCalls=0;
            hook(notify);
            boolean changed=state[1]!=null&&!state[0].equals(state[1]);
            check(CZUserExtensionDataExt.pinCalls==(changed?1:0),"PIN reminder only for actual HTH transition "+Arrays.toString(state));
            check(CZUserExtensionDataExt.itokenCalls==1,"unrelated iToken hook still runs");
            check(com.ofss.digx.infra.thread.ThreadAttribute.get(key)==null,"request marker cleaned");
        }
        stored.setSecurityQuestionsBypass("N");request.setBypassFlag("Y");
        Bank.context.setServiceCallContextType("VALIDATE");
        check(Boolean.FALSE.equals(HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request)),"validate phase has no PIN notification");
        Bank.context.setServiceCallContextType("EXECUTE");
        CZUserExtensionDataExt.pinCalls=0;hook(false);check(CZUserExtensionDataExt.pinCalls==0,"unsaved/failed update gate");
        for(String channel:new String[]{"BCO",null,""}){
            stored.setUserChannelType(channel);request.setBypassFlag("N");
            Boolean notify=HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request);
            check(notify==null,"BCO receives no HTH override");
            CZUserExtensionDataExt.pinCalls=0;hook(notify);check(CZUserExtensionDataExt.pinCalls==1,"BCO original PIN hook preserved");
        }
        stored.setUserChannelType("H2H");request.setBypassFlag("Y");
        check(Boolean.TRUE.equals(HthProfileApproverNotification.loginPinResetChanged(Bank.context,stored,request)),"H2H alias covered");
        com.ofss.digx.infra.thread.ThreadAttribute.set(key,true);
        CZUserExtensionDataExt.failHook=true;
        try {hook(false);throw new AssertionError("expected hook exception");} catch(InvocationTargetException expected){}
        finally {CZUserExtensionDataExt.failHook=false;}
        check(Boolean.TRUE.equals(com.ofss.digx.infra.thread.ThreadAttribute.get(key)),"nested marker restored on hook exception");
        com.ofss.digx.infra.thread.ThreadAttribute.set(key,null);
        System.out.println("PASS: real postUpdate hook suppresses HTH contact-only PIN mail; actual PIN changes, iToken and BCO path retained; marker cleanup");
    }
    static void profileStatusTests() {
        TransactionStatus status = new TransactionStatus();
        status.setReplyCode(0);status.setErrorCode("0");
        check(HthProfileApproverNotification.profileUpdateSucceeded(status, false), "UAT reply=0/error=0 is HTH success");
        check(HthProfileApproverNotification.profileUpdateSucceeded(status, null), "BCO error=0 is success");
        status.setErrorCode(null);
        check(HthProfileApproverNotification.profileUpdateSucceeded(status, false), "null error remains success");
        check(HthProfileApproverNotification.profileUpdateSucceeded(status, null), "BCO null error legacy decision unchanged");
        status.setReplyCode(99);
        check(!HthProfileApproverNotification.profileUpdateSucceeded(status, false), "nonzero reply cannot send success alert");
        status.setReplyCode(0);status.setErrorCode("FAILURE");
        check(!HthProfileApproverNotification.profileUpdateSucceeded(status, false), "business error cannot send success alert");
        check(!HthProfileApproverNotification.profileUpdateSucceeded(status, null), "BCO business error cannot send success alert");
        check(!HthProfileApproverNotification.profileUpdateSucceeded(null, null), "BCO null status cannot send success alert");
        check(!HthProfileApproverNotification.profileUpdateSucceeded(null, false), "null status cannot send success alert");
        System.out.println("PASS: null and zero error codes accepted for BCO and HTH; failure gates retained");
    }
    public static void main(String[] args)throws Exception{
        profileStatusTests();
        reset();UserAlertRequestDTO both=changes(true,true);
        for(String channel:new String[]{"BCO",null,""}){
            stored.setUserChannelType(channel);check(resolve(both)==null,"non-HTH legacy path");
            check(Bank.approvalReads==0 && Bank.userReads==0,"BCO performs no new approval/profile reads");
        }
        stored.setUserChannelType("HTH");Bank.context.setTargetUnit("OTHER");check(resolve(both)==null,"other unit");Bank.context.setTargetUnit("OBDX_BU");
        check(resolve(changes(false,false))==null && Bank.approvalReads==0,"no-op no new reads");
        Bank.context.setServiceCallContextType("VALIDATE");check(resolve(both).id==null && Bank.approvalReads==0,"maker validation adds no AP");Bank.context.setServiceCallContextType("EXECUTE");
        for(String status:new String[]{"PENDING","REJECTED"}){Transaction.status=status;check(resolve(both).id==null,"only final approval");}
        Transaction.status="APPROVED";Transaction.service="other";check(resolve(both).id==null,"unrelated service excluded");Transaction.service=ACTIVITY;
        Transaction.signers="MAKER~";check(resolve(both).id==null,"maker alone excluded");Transaction.signers="MAKER~~EARLIER~ FINAL ~";
        check("FINAL".equals(resolve(both).id),"final signer, never session worker or earlier approver");
        Bank.approvalFailure=true;check(resolve(both).id==null,"resolution failure contained");Bank.approvalFailure=false;
        com.ofss.digx.infra.thread.ThreadAttribute.reference=null;check(resolve(both).id==null,"missing approval no worker fallback");com.ofss.digx.infra.thread.ThreadAttribute.reference="APPROVAL";
        Bank.extensionFailure=true;HthProfileApproverNotification.Approver partial=resolve(both);
        List<String> mail=new ArrayList<>();HthProfileApproverNotification.addEmail(mail,partial);check(mail.size()==1,"email survives missing AP mobile extension");
        check(HthProfileApproverNotification.sms(partial,both,request,oldUser.getMobileNumber()).isEmpty(),"missing country never guessed");Bank.extensionFailure=false;
        System.out.println("PASS: channel gate, no BCO approver reads, final approval and failure isolation");

        for(boolean[] flags:new boolean[][]{{true,false},{false,true},{true,true},{false,false}}){
            reset();UserAlertRequestDTO c=changes(flags[0],flags[1]);stored.setUserChannelType("BCO");
            new UserExtensionDataBaseline().alertUserProfileUpdate(Bank.context,c,request,oldUser,oldUser.getMobileNumber());List<String> before=summaries();
            invoke(c,resolve(c));check(before.equals(summaries()),"BCO event sequence, recipients and template payload unchanged "+Arrays.toString(flags));
            stored.setUserChannelType("HTH");HthProfileApproverNotification.Approver ap=resolve(c);invoke(c,ap);
            if(!flags[0]&&!flags[1]){check(before.equals(summaries()),"HTH no-op");continue;}
            check(emails("final@example.test")==1 && emails("worker@example.test")==0,"replace worker email with final AP once");
            check(emails("company@example.test")==1,"company email preserved");
            check(emails("new@example.test")==1 && emails("old@example.test")== (flags[0]?1:0),"target mail preserved");
            check(Bank.events.contains("USER_MANAGEMENT_EDIT"),"independent user-management notification retained");
            List<UserProfUpdateActivityLogDTO> sms=new ArrayList<>();
            List<String> smsEvents=new ArrayList<>();
            for(int i=0;i<Bank.logs.size();i++){
                ActivityLog l=Bank.logs.get(i);
                if(l instanceof UserProfUpdateActivityLogDTO && l.getInputfacts()!=null){sms.add((UserProfUpdateActivityLogDTO)l);smsEvents.add(Bank.events.get(i));}
            }
            check(sms.size()==(flags[0]?1:0)+(flags[1]?1:0),"AP receives only corresponding BCO reminders");
            for(int i=0;i<sms.size();i++){
                UserProfUpdateActivityLogDTO l=sms.get(i);String event=smsEvents.get(i);
                check("TARGET".equals(l.getProfileUser()),"body identifies changed user");
                check("FINAL".equals(l.getUserId()),"UserId routes to final recipient");
                check("recipientUserId".equals(l.getInputfacts()[0].getName()) && "FINAL".equals(l.getInputfacts()[0].getValue()),"serialized SDK recipient identity preserved");
                for(boolean configured:new boolean[]{false,true}){
                    Bank.includeSmsEvents=configured;check(send(l,event,ACTIVITY),"SMS MNG success");
                    MNGSmsAlertDTO sent=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);
                    check("85363456789".equals(sent.getBody()[0].getSms().getDistNo()),"AP country/number survive legacy target lookup with event configured="+configured);
                    check("FINAL".equals(com.ofss.digx.cz.bea.domain.emailmng.EmailMNG.last.getCustomerId()),"MNG records AP recipient");
                }
                Bank.reject=true;check(!send(l,event,ACTIVITY),"MNG rejection retained");Bank.reject=false;
            }
        }
        System.out.println("PASS: real BCO notification method matches baseline for AC1/AC2/AC3/no-op; final AP email/SMS, templates, MNG routing and failure handling");
        reset();HthProfileApproverNotification.Approver ap=resolve(both);
        List<String> existing=new ArrayList<>(Arrays.asList(" FINAL@EXAMPLE.TEST ~TARGET@PARTY"));HthProfileApproverNotification.addEmail(existing,ap);check(existing.size()==1,"AP email same as target dedup");
        existing=new ArrayList<>(Arrays.asList("final@example.test"));HthProfileApproverNotification.addEmail(existing,ap);check(existing.size()==1,"AP email same as company dedup");
        ap.country="852";ap.mobile="62345678";check(HthProfileApproverNotification.sms(ap,changes(true,false),request,"61234567").isEmpty(),"same email-reminder target SMS dedup");
        ap.mobile="61234567";check(HthProfileApproverNotification.sms(ap,changes(false,true),request,"6123-4567").isEmpty(),"same mobile-reminder target SMS dedup");
        ap.mobile="62345678";check(HthProfileApproverNotification.sms(ap,changes(false,true),request,"61234567").size()==1,"new-number welcome is distinct from AP reminder");
        UserProfUpdateActivityLogDTO normal=new UserProfUpdateActivityLogDTO();normal.setUserId("TARGET@PARTY");normal.setCustomerId("PARTY");
        NotificationDetail detail=new NotificationDetail();detail.setRecipientId("PARTY");detail.setDestination(DestinationType.SMS);detail.setRecipientType(SubscriberType.EXTERNAL.toString());detail.setDispatchAddress("61234567");normal.setNotificationDetails(new NotificationDetail[]{detail});Bank.includeSmsEvents=true;
        check(send(normal,HthProfileApproverNotification.EMAIL_EVENT,ACTIVITY),"ordinary BCO SMS success");
        MNGSmsAlertDTO bco=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);check("85261234567".equals(bco.getBody()[0].getSms().getDistNo()),"BCO legacy lookup/country retained");
        // A managed user object may be updated in place before the notification is built.
        for(boolean mobile:new boolean[]{false,true}) {
            reset();UserAlertRequestDTO c=changes(true,mobile);
            HthProfileApproverNotification.Approver snapshot=resolve(c);
            oldUser.setEmailId(request.getUserDTO().getEmailId());
            invoke(c,snapshot);
            check(emails("old@example.test")==1 && emails("new@example.test")==1,
                    "pre-save old email survives in-place user update");
        }
        System.out.println("PASS: same-content recipient dedup, ordinary BCO SMS routing unchanged");
        storedChannelTests();
        pinNotificationTests();
    }
}
