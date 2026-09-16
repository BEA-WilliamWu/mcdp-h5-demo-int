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

public final class HthContactRuntimeTest {
    static final String ACTIVITY="com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    static User oldUser, finalUser;
    static UserExtensionDataDTO request;
    static com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData stored, finalExtension;
    static void check(boolean ok,String name){Bank.check(ok,name);}
    static User user(String mail,String phone){User u=new User();u.setEmailId(mail);u.setMobileNumber(phone);return u;}
    static UserAlertRequestDTO changes(boolean email,boolean mobile){UserAlertRequestDTO c=new UserAlertRequestDTO();c.setEmailId(!email);c.setMobNo(!mobile);return c;}
    static HthProfileApproverNotification.Approver resolve(UserAlertRequestDTO c){return HthProfileApproverNotification.resolve(Bank.context,request,c,stored);}
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
        AlertRequestDTO a=new AlertRequestDTO();a.setActivityLog(log);a.setUserId("TARGET@PARTY");a.setCodActDataId("TEST-ACTIVITY");
        bean(a,"EventAction.KeyDTO.ActivityId",activity);bean(a,"EventAction.KeyDTO.EventId",event);bean(a,"EventAction.KeyDTO.ActionId","A");
        bean(a,"RecipientMessageTemplate.KeyDTO.EventId",event);bean(a,"RecipientMessageTemplate.KeyDTO.MessageTemplateId","BCO-TEMPLATE");
        bean(a,"AlertContactPreference.DispatchAddress","63456789");bean(a,"PreferredRecipient.ContactDetails.PartyId","PARTY");
        NameValuePair pair=new NameValuePair();pair.setName("UserId");pair.setGenericName("UserId");pair.setValue("TARGET@PARTY");
        IDispatchData data=(IDispatchData)Proxy.newProxyInstance(IDispatchData.class.getClassLoader(),new Class[]{IDispatchData.class},(p,m,args)->m.getName().equals("getDispatchData")?new NameValuePair[]{pair}:null);
        return new SMSDispatcher().test(a,data,"BCO reminder for TARGET").getIsDispatchSuccessfull();
    }
    public static void main(String[] args)throws Exception{
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
        System.out.println("PASS: persisted-channel gate, no BCO extra reads, final approval and failure isolation");

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
            List<HthProfileApproverActivityLogDTO> sms=new ArrayList<>();
            for(ActivityLog l:Bank.logs)if(l instanceof HthProfileApproverActivityLogDTO)sms.add((HthProfileApproverActivityLogDTO)l);
            check(sms.size()==(flags[0]?1:0)+(flags[1]?1:0),"AP receives only corresponding BCO reminders");
            for(HthProfileApproverActivityLogDTO l:sms){
                check("TARGET".equals(l.getProfileUser()) && "TARGET@PARTY".equals(l.getUserId()),"body identifies changed user");
                check("FINAL".equals(l.getApproverId()),"serialized marker carries final recipient");
                for(boolean configured:new boolean[]{false,true}){
                    Bank.includeSmsEvents=configured;check(send(l,l.getApproverEventId(),ACTIVITY),"SMS MNG success");
                    MNGSmsAlertDTO sent=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);
                    check("85363456789".equals(sent.getBody()[0].getSms().getDistNo()),"AP country/number survive legacy target lookup with event configured="+configured);
                    check("FINAL".equals(com.ofss.digx.cz.bea.domain.emailmng.EmailMNG.last.getCustomerId()),"MNG records AP recipient");
                }
                int count=Bank.networkCalls;send(l,l.getApproverEventId(),"other.activity");check(count==Bank.networkCalls,"marker cannot route unrelated activity");
                Bank.reject=true;check(!send(l,l.getApproverEventId(),ACTIVITY),"MNG rejection retained");Bank.reject=false;
            }
        }
        System.out.println("PASS: real BCO notification method matches baseline for AC1/AC2/AC3/no-op; final AP email/SMS, templates, MNG routing and failure handling");
        reset();HthProfileApproverNotification.Approver ap=resolve(both);
        List<String> existing=new ArrayList<>(Arrays.asList(" FINAL@EXAMPLE.TEST ~TARGET@PARTY"));HthProfileApproverNotification.addEmail(existing,ap);check(existing.size()==1,"AP email same as target dedup");
        existing=new ArrayList<>(Arrays.asList("final@example.test"));HthProfileApproverNotification.addEmail(existing,ap);check(existing.size()==1,"AP email same as company dedup");
        ap.country="852";ap.mobile="62345678";check(HthProfileApproverNotification.sms(ap,changes(true,false),request,"61234567").isEmpty(),"same email-reminder target SMS dedup");
        ap.mobile="61234567";check(HthProfileApproverNotification.sms(ap,changes(false,true),request,"6123-4567").isEmpty(),"same mobile-reminder target SMS dedup");
        ap.mobile="62345678";check(HthProfileApproverNotification.sms(ap,changes(false,true),request,"61234567").size()==1,"new-number welcome is distinct from AP reminder");
        UserProfUpdateActivityLogDTO normal=new UserProfUpdateActivityLogDTO();normal.setUserId("TARGET@PARTY");Bank.includeSmsEvents=true;
        check(send(normal,HthProfileApproverActivityLogDTO.EMAIL_EVENT,ACTIVITY),"ordinary BCO SMS success");
        MNGSmsAlertDTO bco=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);check("85261234567".equals(bco.getBody()[0].getSms().getDistNo()),"BCO legacy lookup/country retained");
        System.out.println("PASS: same-content recipient dedup, ordinary BCO SMS routing unchanged");
    }
}
