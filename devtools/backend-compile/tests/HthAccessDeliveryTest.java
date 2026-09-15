package com.ofss.digx.cz.bea.domain.service.dispatch;

import fixture.Bank;
import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
import com.ofss.digx.cz.bea.app.email.dto.alerts.*;
import com.ofss.fc.app.ep.dto.action.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class HthAccessDeliveryTest {
    private static final HthContactNotificationRepository repo=new HthContactNotificationRepository(Bank.source,true);
    static String seed(String label,String role,String channel,String address,String state,String parent) throws Exception {
        String id=HthUserAccessNotificationPlan.id(label);
        try(Connection c=Bank.source.getConnection()){
            HthContactNotificationRepository.update(c,"INSERT INTO DIGX_CZ_HTH_ACCESS_NOTIFY (ID,TARGET_UNIT,APPROVAL_REF,PARTY_ID,TARGET_USER_ID,APPROVER_ID,CHANGE_TYPE,RECIPIENT_ROLE,CHANNEL,ADDRESS,RECIPIENT_USER_ID,USER_LOCALE,APPROVED_AT,STATE,PARENT_ID,CLOSE_ID,ACCESS_PARTY_ID,LINKAGE_TYPE,COMPANY_NAME) VALUES (?,'OBDX_BU',?,'PARTY','API@PARTY','FINAL','UPDATE',?,?,?,'API@PARTY','en','16/09/2026 12:00:00',?,?,'API@PARTY','PARTY','RELATED','Company')",id,label,role,channel,address,state,parent);
        }return id;
    }
    static String state(String id)throws Exception{return Bank.scalar("SELECT STATE FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE ID='"+id+"'");}
    static AlertRequestDTO request(String id)throws Exception {
        HthContactNotificationRepository.Row row;
        try(Connection c=Bank.source.getConnection()){row=repo.read(c,id);}
        AlertRequestDTO req=new AlertRequestDTO();ActivityEventActionDTO event=new ActivityEventActionDTO();
        // Public bean setters only: key class varies between bundled SDK releases.
        Class<?> type=event.getClass().getMethod("getKeyDTO").getReturnType();Object key=type.getDeclaredConstructor().newInstance();
        type.getMethod("setEventId",String.class).invoke(key,row.event());
        event.getClass().getMethod("setKeyDTO",type).invoke(event,key);req.setEventAction(event);
        HthUserAccessActivityLogDTO log=new HthUserAccessActivityLogDTO();log.setHthAccessNotificationId(id);
        req.setActivityLog(log);req.setCodActDataId("ACTIVITY-"+id);return req;
    }
    public static void run() throws Exception {
        Bank.publications=0; Bank.networkCalls=0; Bank.outcome="SUCCESS";
        SessionContext context=new SessionContext();context.setTargetUnit("OBDX_BU");context.setUserLocale("original");context.setTransactingPartyCode("ORIGINAL");
        new HthUserAccessNotificationService().process(context);
        Bank.check(Bank.publications==14,"committed staged recipients published by existing batch hook");
        Bank.check("original".equals(context.getUserLocale()) && "ORIGINAL".equals(context.getTransactingPartyCode()),"shared scheduler context restored");
        new HthUserAccessNotificationService().process(context);Bank.check(Bank.publications==14,"fresh publications not republished");
        String id=seed("success","API","EMAIL","snapshot@example.test","PUBLISHED",null);
        Bank.outcome="SUCCESS";
        Bank.check(HthContactNotificationDispatch.dispatch(request(id),"EMAIL","rendered message","subject").getIsDispatchSuccessfull(),"email MNG submission");
        MNGEmailAlertsDTO email=(MNGEmailAlertsDTO)((List<?>)Bank.lastRequest).get(0);
        Bank.check("snapshot@example.test".equals(email.getBody()[0].getEmail().getDistAdds()),"snapshot email used");
        Bank.check(email.getHeader().getAppID()==89L && "o".equals(email.getHeader().getMessageMode()) && email.getBody()[0].getMessageType()==6,"BCO email request protocol");
        int sent=Bank.networkCalls;HthContactNotificationDispatch.dispatch(request(id),"EMAIL","rendered message","subject");
        Bank.check(Bank.networkCalls==sent && "SUBMITTED".equals(state(id)),"framework duplicate never resends");
        Bank.check("Success".equals(Bank.scalar("SELECT RESPONSE_STATUS FROM DIGX_CZ_EMAIL_MNG WHERE REFNUMBER='HA"+id+"'")),"ack linked to MNG");
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
        Bank.sql("INSERT INTO DIGX_CZ_EMAIL_MNG(REFNUMBER) VALUES ('HA"+broken+"')");sent=Bank.networkCalls;
        HthContactNotificationDispatch.dispatch(request(broken),"EMAIL","message","subject");
        Bank.check(Bank.networkCalls==sent && "PUBLISHED".equals(state(broken)),"MNG insert failure rolls claim back and prevents IO");
        String malformed=seed("unresolved","API","EMAIL","example@example.test","PUBLISHED",null);
        HthContactNotificationDispatch.dispatch(request(malformed),"EMAIL","#userNameId#","subject");
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
            for(int i=0;i<2;i++)sends.add(pool.submit(()->repo.claimDispatch(race,"EMAIL",HthUserAccessNotificationPlan.event("UPDATE"),"a","s","hash")!=null));
            wins=0;for(Future<Boolean> f:sends)if(f.get())wins++;
            Bank.check(wins==1,"concurrent dispatch claim");
        }finally{pool.shutdownNow();}
        Bank.sql("UPDATE DIGX_CZ_HTH_ACCESS_NOTIFY SET UPDATED_AT=CURRENT_TIMESTAMP-INTERVAL '16' MINUTE WHERE ID='"+race+"'");
        repo.reconcile("OBDX_BU");Bank.check("UNKNOWN".equals(state(race)),"crashed sending becomes unknown, not resendable");
        Bank.sql("UPDATE DIGX_PI_PARTY_PREFERENCES SET OFFICE_EMAIL='company@example.test' WHERE PARTYID='PARTY'");
        Bank.outcome="REJECT";String rejected=seed("rejected","API","EMAIL","old@example.test","PUBLISHED",null);
        HthContactNotificationDispatch.dispatch(request(rejected),"EMAIL","message","subject");
        Bank.check("REJECTED".equals(state(rejected)),"explicit MNG rejection");repo.reconcile("OBDX_BU");repo.reconcile("OBDX_BU");
        String child=Bank.scalar("SELECT ID FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE PARENT_ID='"+rejected+"'");
        Bank.check(child!=null && "1".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE PARENT_ID='"+rejected+"'")),"repeated failure produces one fallback");
        Bank.check("company@example.test".equals(Bank.scalar("SELECT ADDRESS FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE ID='"+child+"'")),"current company officeEmail");
        repo.claimPublication(child);HthContactNotificationDispatch.dispatch(request(child),"EMAIL","fallback message","subject");repo.reconcile("OBDX_BU");
        Bank.check("FAILED_FINAL".equals(state(child)),"fallback failure ends without cascading");
        String bounce=seed("bounce","API","EMAIL","bounce@example.test","PUBLISHED",null);Bank.outcome="SUCCESS";
        HthContactNotificationDispatch.dispatch(request(bounce),"EMAIL","message","subject");
        Bank.sql("INSERT INTO DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL(SRC_SYS_REF_NUM) VALUES ('HA"+bounce+"')");
        repo.reconcile("OBDX_BU");Bank.check("FALLBACK_QUEUED".equals(state(bounce)),"real negative receipt triggers API fallback");
        for(String[] test:new String[][]{{"companyFail","COMPANY","EMAIL","owner@example.test"},{"apFail","AP","EMAIL","ap@example.test"},{"smsFail","API","SMS","85261234567"},{"sameCompany","API","EMAIL","company@example.test"}}){
            String failed=seed(test[0],test[1],test[2],test[3],"REJECTED",null);repo.fallback(failed);
            Bank.check("FAILED_FINAL".equals(state(failed)),"no fallback cascade: "+test[0]);
        }
        Bank.check(repo.pending("OTHER_BU").isEmpty(),"delivery unit isolation");
        String held=seed("held","API","EMAIL","held@example.test","PUBLISHED",null);
        ConfigurationFactory.getInstance().getConfigurations(HthUserAccessNotificationPlan.CONFIG).putBoolean(HthUserAccessNotificationPlan.ENABLED,false);
        sent=Bank.networkCalls;HthContactNotificationDispatch.dispatch(request(held),"EMAIL","message","subject");
        Bank.check(Bank.networkCalls==sent && "PUBLISHED".equals(state(held)),"1216 off blocks only its own dispatcher");
        ConfigurationFactory.getInstance().getConfigurations(HthUserAccessNotificationPlan.CONFIG).putBoolean(HthUserAccessNotificationPlan.ENABLED,true);
        // Company was already an original recipient: confirmed API failure must not duplicate it.
        String original=seed("dedup-original","API","EMAIL","api@example.test","REJECTED",null);
        String company=seed("dedup-company","COMPANY","EMAIL","company@example.test","SUBMITTED",null);
        Bank.sql("UPDATE DIGX_CZ_HTH_ACCESS_NOTIFY SET APPROVAL_REF='dedup-original' WHERE ID='"+company+"'");
        repo.fallback(original);
        Bank.check("FAILED_FINAL".equals(state(original)) && "0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE PARENT_ID='"+original+"'")),"company already notified: no duplicate fallback");
        System.out.println("PASS: actual 1216 dispatcher + BCO MNG builders, committed MNG-before-IO, duplicate/concurrent claims, timeout/crash handling, mock/placeholder guards, receipt fallback and terminal cases");
    }
}
