package com.ofss.digx.cz.bea.domain.service.dispatch;

import fixture.Bank;
import com.ofss.digx.cz.bea.app.sms.dto.user.*;
import com.ofss.digx.cz.bea.app.email.dto.alerts.*;
import com.ofss.fc.app.ep.dto.action.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class HthContactDeliveryTest {
    private static final HthContactNotificationRepository repo=new HthContactNotificationRepository(Bank.source);
    static String seed(String label,String role,String channel,String address,String state,String parent) throws Exception {
        String id=HthContactNotificationPlan.id(label);
        try(Connection c=Bank.source.getConnection()){
            HthContactNotificationRepository.update(c,"INSERT INTO DIGX_CZ_HTH_CONTACT_OUTBOX (ID,TARGET_UNIT,APPROVAL_REF,PARTY_ID,TARGET_USER_ID,APPROVER_ID,CHANGE_TYPE,RECIPIENT_ROLE,CHANNEL,ADDRESS,RECIPIENT_USER_ID,USER_LOCALE,APPROVED_AT,EVENT_ID,NEW_EMAIL,STATE,PARENT_ID) VALUES (?,'OBDX_BU',?,'PARTY','API@PARTY','FINAL','EMAIL',?,?,?,'API@PARTY','en','15/09/2026 12:00:00',?, 'new@example.test',?,?)",id,label,role,channel,address,"EMAIL".equals(channel)?HthContactNotificationPlan.EMAIL:HthContactNotificationPlan.EMAIL_SMS,state,parent);
        }return id;
    }
    static String state(String id)throws Exception{return Bank.scalar("SELECT STATE FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE ID='"+id+"'");}
    static AlertRequestDTO request(String id)throws Exception {
        HthContactNotificationRepository.Row row;
        try(Connection c=Bank.source.getConnection()){row=repo.read(c,id);}
        AlertRequestDTO req=new AlertRequestDTO();ActivityEventActionDTO event=new ActivityEventActionDTO();
        // Public bean setters only: key class varies between bundled SDK releases.
        Class<?> type=event.getClass().getMethod("getKeyDTO").getReturnType();Object key=type.getDeclaredConstructor().newInstance();
        type.getMethod("setEventId",String.class).invoke(key,row.event());
        event.getClass().getMethod("setKeyDTO",type).invoke(event,key);req.setEventAction(event);
        HthProfileContactUpdateActivityLogDTO log=new HthProfileContactUpdateActivityLogDTO();log.setHthContactNotificationId(id);
        req.setActivityLog(log);req.setCodActDataId("ACTIVITY-"+id);return req;
    }
    public static void run() throws Exception {
        SessionContext context=new SessionContext();context.setTargetUnit("OBDX_BU");context.setUserLocale("original");context.setTransactingPartyCode("ORIGINAL");
        new HthContactNotificationService().process(context);
        Bank.check(Bank.publications==9,"committed staged recipients published by existing batch hook");
        Bank.check("original".equals(context.getUserLocale()) && "ORIGINAL".equals(context.getTransactingPartyCode()),"shared scheduler context restored");
        new HthContactNotificationService().process(context);Bank.check(Bank.publications==9,"fresh publications not republished");
        String id=seed("success","API","EMAIL","snapshot@example.test","PUBLISHED",null);
        Bank.outcome="SUCCESS";
        Bank.check(HthContactNotificationDispatch.dispatch(request(id),"EMAIL","rendered message","subject").getIsDispatchSuccessfull(),"email MNG submission");
        MNGEmailAlertsDTO email=(MNGEmailAlertsDTO)((List<?>)Bank.lastRequest).get(0);
        Bank.check("snapshot@example.test".equals(email.getBody()[0].getEmail().getDistAdds()),"snapshot email used");
        Bank.check(email.getHeader().getAppID()==89L && "o".equals(email.getHeader().getMessageMode()) && email.getBody()[0].getMessageType()==6,"BCO email request protocol");
        int sent=Bank.networkCalls;HthContactNotificationDispatch.dispatch(request(id),"EMAIL","rendered message","subject");
        Bank.check(Bank.networkCalls==sent && "SUBMITTED".equals(state(id)),"framework duplicate never resends");
        Bank.check("Success".equals(Bank.scalar("SELECT RESPONSE_STATUS FROM DIGX_CZ_EMAIL_MNG WHERE REFNUMBER='H851"+id+"'")),"ack linked to MNG");
        String smsId=seed("sms","AP","SMS","85361234567","PUBLISHED",null);
        HthContactNotificationDispatch.dispatch(request(smsId),"SMS","sms message","");
        MNGSmsAlertDTO sms=(MNGSmsAlertDTO)((List<?>)Bank.lastRequest).get(0);
        Bank.check("85361234567".equals(sms.getBody()[0].getSms().getDistNo()),"old country prefix not replaced or doubled");
        Bank.check(sms.getHeader().getAppID()==89L && sms.getBody()[0].getMessageType()==1,"BCO SMS protocol");
        for(String outcome:new String[]{"TIMEOUT","NULL"}){
            String unknown=seed(outcome,"API","EMAIL","unknown@example.test","PUBLISHED",null);Bank.outcome=outcome;
            HthContactNotificationDispatch.dispatch(request(unknown),"EMAIL","message","subject");
            Bank.check("UNKNOWN".equals(state(unknown)),"uncertain MNG result must be UNKNOWN");
            sent=Bank.networkCalls;repo.reconcile("OBDX_BU");HthContactNotificationDispatch.dispatch(request(unknown),"EMAIL","message","subject");
            Bank.check(Bank.networkCalls==sent && "UNKNOWN".equals(state(unknown)),"timeout does not resend or trigger fallback");
        }
        String broken=seed("mng-insert-failure","API","EMAIL","broken@example.test","PUBLISHED",null);
        Bank.sql("INSERT INTO DIGX_CZ_EMAIL_MNG(REFNUMBER) VALUES ('H851"+broken+"')");sent=Bank.networkCalls;
        HthContactNotificationDispatch.dispatch(request(broken),"EMAIL","message","subject");
        Bank.check(Bank.networkCalls==sent && "PUBLISHED".equals(state(broken)),"MNG insert failure rolls claim back and prevents IO");
        String malformed=seed("unresolved","API","EMAIL","example@example.test","PUBLISHED",null);
        HthContactNotificationDispatch.dispatch(request(malformed),"EMAIL","#hthContactUserName#","subject");
        Bank.check("PUBLISHED".equals(state(malformed)) && Bank.networkCalls==sent,"unresolved placeholder never sent");
        ConfigurationFactory.getInstance().getConfigurations("DispatchDetails").put("isDispatchMocked","true");
        HthContactNotificationDispatch.dispatch(request(malformed),"EMAIL","message","subject");
        Bank.check("PUBLISHED".equals(state(malformed)) && Bank.networkCalls==sent,"mock switch holds real delivery");
        ConfigurationFactory.getInstance().getConfigurations("DispatchDetails").put("isDispatchMocked","false");
        String race=seed("concurrent","API","EMAIL","race@example.test","READY",null);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Boolean>> claims=new ArrayList<>();
            for(int i=0;i<2;i++)claims.add(pool.submit(()->{start.await();return repo.claimPublication(race);}));
            start.countDown();int wins=0;for(Future<Boolean> f:claims)if(f.get())wins++;
            Bank.check(wins==1,"concurrent publication claim");
            List<Future<Boolean>> sends=new ArrayList<>();
            for(int i=0;i<2;i++)sends.add(pool.submit(()->repo.claimDispatch(race,"EMAIL",HthContactNotificationPlan.EMAIL,"a","s","hash")!=null));
            wins=0;for(Future<Boolean> f:sends)if(f.get())wins++;
            Bank.check(wins==1,"concurrent dispatch claim");
        }finally{pool.shutdownNow();}
        Bank.sql("UPDATE DIGX_CZ_HTH_CONTACT_OUTBOX SET UPDATED_AT=CURRENT_TIMESTAMP-INTERVAL '16' MINUTE WHERE ID='"+race+"'");
        repo.reconcile("OBDX_BU");Bank.check("UNKNOWN".equals(state(race)),"crashed sending becomes unknown, not resendable");
        Bank.sql("INSERT INTO DIGX_PI_PARTY_PREFERENCES(PARTYID,OFFICE_EMAIL) VALUES ('PARTY','company@example.test')");
        Bank.outcome="REJECT";String rejected=seed("rejected","API","EMAIL","old@example.test","PUBLISHED",null);
        HthContactNotificationDispatch.dispatch(request(rejected),"EMAIL","message","subject");
        Bank.check("REJECTED".equals(state(rejected)),"explicit MNG rejection");repo.reconcile("OBDX_BU");repo.reconcile("OBDX_BU");
        String child=Bank.scalar("SELECT ID FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE PARENT_ID='"+rejected+"'");
        Bank.check(child!=null && "1".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE PARENT_ID='"+rejected+"'")),"repeated failure produces one fallback");
        Bank.check("company@example.test".equals(Bank.scalar("SELECT ADDRESS FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE ID='"+child+"'")),"current company officeEmail");
        repo.claimPublication(child);HthContactNotificationDispatch.dispatch(request(child),"EMAIL","fallback message","subject");repo.reconcile("OBDX_BU");
        Bank.check("OFFICE_FOLLOWUP".equals(state(child)),"company fallback failure uses BCO follow-up without another email");
        String bounce=seed("bounce","API","EMAIL","bounce@example.test","PUBLISHED",null);Bank.outcome="SUCCESS";
        HthContactNotificationDispatch.dispatch(request(bounce),"EMAIL","message","subject");
        Bank.sql("INSERT INTO DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL(SRC_SYS_REF_NUM) VALUES ('H851"+bounce+"')");
        repo.reconcile("OBDX_BU");Bank.check("FALLBACK_QUEUED".equals(state(bounce)),"real negative receipt triggers API fallback");
        for(String[] test:new String[][]{{"apFail","AP","EMAIL","ap@example.test"},{"smsFail","API","SMS","85261234567"},{"sameCompany","API","EMAIL","company@example.test"}}){
            String failed=seed(test[0],test[1],test[2],test[3],"REJECTED",null);repo.fallback(failed);
            Bank.check(("sameCompany".equals(test[0])?"OFFICE_FOLLOWUP":"FAILED_FINAL").equals(state(failed)),"no fallback cascade: "+test[0]);
        }
        // Ordinary BCO uses the same event names but MUST NOT enter the HTH dispatcher.
        AlertRequestDTO bco=request(id);bco.setActivityLog(new UserProfUpdateActivityLogDTO());
        Bank.check(!HthContactNotificationDispatch.matches(bco),"same BCO event without subtype stays on BCO path");
        // Initial company row is reused even if still READY or delivery outcome is UNKNOWN.
        for(String existingState:new String[]{"READY","SUBMITTED","UNKNOWN"}){
            String failed=seed("company-existing-"+existingState,"API","EMAIL","prior@example.test","REJECTED",null);
            String company=seed("company-copy-"+existingState,"COMPANY","EMAIL","company@example.test",existingState,null);
            String ref="company-existing-"+existingState;
            String key=HthContactNotificationPlan.deliveryId("OBDX_BU",ref,"API@PARTY","EMAIL",HthContactNotificationPlan.EMAIL,"EMAIL","company@example.test");
            Bank.sql("UPDATE DIGX_CZ_HTH_CONTACT_OUTBOX SET ID='"+key+"',APPROVAL_REF='"+ref+"' WHERE ID='"+company+"'");
            repo.fallback(failed);
            Bank.check("FALLBACK_LINKED".equals(state(failed)),"initial company notification linked: "+existingState);
            Bank.check(key.equals(Bank.scalar("SELECT FALLBACK_ID FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE ID='"+failed+"'")),"existing company reference retained");
            Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE PARENT_ID='"+failed+"'")),"no duplicate company email");
        }
        // Two old/new target failures for one approval produce only one company plan under concurrency.
        String first=seed("two-failures","API","EMAIL","old1@example.test","REJECTED",null);
        String second=seed("second-failure","API","EMAIL","new1@example.test","REJECTED",null);
        Bank.sql("UPDATE DIGX_CZ_HTH_CONTACT_OUTBOX SET APPROVAL_REF='two-failures' WHERE ID='"+second+"'");
        ExecutorService failures=Executors.newFixedThreadPool(2);
        try {
            Future<?> f1=failures.submit(()->{try{repo.fallback(first);}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> f2=failures.submit(()->{try{repo.fallback(second);}catch(Exception e){throw new RuntimeException(e);}});
            f1.get();f2.get();
            Bank.check("1".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE APPROVAL_REF='two-failures' AND RECIPIENT_ROLE='COMPANY'")),"concurrent old/new failures share one company plan");
        } finally { failures.shutdownNow(); }
        Bank.check(repo.pending("OTHER_BU").isEmpty(),"delivery unit isolation");
        System.out.println("PASS: actual 851 dispatcher + BCO MNG builders, committed MNG-before-IO, duplicate/concurrent claims, timeout/crash handling, mock/placeholder guards, receipt fallback and terminal cases");
    }
}
