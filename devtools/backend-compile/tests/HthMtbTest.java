package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import java.util.*;
import java.lang.reflect.*;
import javax.transaction.*;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.das.orm.Query;

/** Runtime contract tests. ORM/JTA proxies model failures; not an Oracle/WebLogic integration test. */
public class HthMtbTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    static Map<String,Object> data(String operation) {
        Map<String,Object> value=new HashMap<String,Object>();
        value.put("operation",operation);value.put("businessOutcome","SUCCESS");
        value.put("occurredAt","2026-09-22T16:01:02Z");value.put("actorUserId","AP@PARTY");
        value.put("partyId","001");value.put("targetUserId","USER@PARTY");return value;
    }
    static HthCRMEvent3DomainDTO event(String op) {
        return HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.setup",data(op),null);
    }
    public static void main(String[] args) throws Exception {
        Map<String,Object> user=data("CREATE");user.put("newUserChannelType","BCO");
        check(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null)==null);
        user.put("newUserChannelType","HTH");
        HthCRMEvent3DomainDTO created=HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null);
        check("USER_CREATE".equals(created.get("ACTIVITY_KEY")));
        check("20260923".equals(created.get("EVENT_DTE")) && "000102".equals(created.get("EVENT_TIME")));
        check("AP@PARTY".equals(created.get("USER_ID")) && "USER@PARTY".equals(created.get("TARGET_USER_ID")));
        user.put("businessOutcome","PENDING_APPROVAL");
        check("SUBMIT".equals(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null).get("PHASE")));
        user.put("businessOutcome","SUCCESS");user.put("effectiveChange",true);
        check("APPLY".equals(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null).get("PHASE")));
        check(!event("SETUP").get("ACTIVITY_KEY").equals(event("RESET").get("ACTIVITY_KEY")));
        check("CODE_GENERATE".equals(event("GENERATE").get("ACTIVITY_KEY")));
        check(event("REVEAL")==null);
        Map<String,Object> secret=data("RESET");secret.put("password","DO_NOT_STORE");secret.put("code","123456");
        secret.put("encryptedCredentials","SECRET");secret.put("requestId","request");
        HthCRMEvent3DomainDTO reset=HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",secret,null);
        check(!reset.fields().toString().contains("DO_NOT_STORE") && !reset.fields().toString().contains("123456"));
        check(reset.get("DEDUP_KEY")!=null);
        secret.put("idempotentReplay",true);
        check(HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",secret,null)==null);
        Map<String,Object> access=data("ACCESS_EDIT");access.put("linkageType","ASSOCIATED");
        HthCRMEvent3DomainDTO grant=HthCRMRequestAssembler.assemble("x.HostToHostUserAccess.edit",access,null);
        check("ACCESS_EDIT".equals(grant.get("ACTIVITY_KEY")) && "ASSOCIATED".equals(grant.get("RELATIONSHIP_TYPE")));
        check(grant.get("DEDUP_KEY")==null); // no accidental collapse of different approval actions
        for(String action:Arrays.asList("ENABLE","EDIT","DISABLE")) {
            String method="ENABLE".equals(action)?"submit":action.toLowerCase(Locale.ROOT);
            HthCRMEvent3DomainDTO company=HthCRMRequestAssembler.assemble("x.HostToHostManagement."+method,data("COMPANY_"+action),null);
            check("BM".equals(company.get("CHANNEL_TYPE")));
        }
        List<HthCRMEvent3DomainDTO> saved=new ArrayList<HthCRMEvent3DomainDTO>();
        final Synchronization[] callback={null};
        Transaction tx=(Transaction)Proxy.newProxyInstance(Transaction.class.getClassLoader(),new Class[]{Transaction.class},(p,m,a)->{
            if(m.getName().equals("getStatus"))return Status.STATUS_ACTIVE;
            if(m.getName().equals("registerSynchronization")){callback[0]=(Synchronization)a[0];return null;}
            throw new AssertionError("Unexpected caller transaction operation: "+m.getName());
        });
        HthCRMAsserter.schedule(reset,tx,true,saved::add);check(saved.isEmpty());
        callback[0].afterCompletion(Status.STATUS_COMMITTED);check(saved.size()==1 && saved.get(0)==reset);
        saved.clear();HthCRMAsserter.schedule(reset,tx,true,saved::add);
        callback[0].afterCompletion(Status.STATUS_ROLLEDBACK);
        check(saved.size()==1 && "R".equals(saved.get(0).get("EVENT_STATUS_CODE")) && saved.get(0).get("DEDUP_KEY")==null);
        saved.clear();HthCRMAsserter.schedule(reset,null,true,saved::add);check(saved.isEmpty());
        HthCRMAsserter.schedule(reset,null,false,saved::add);check(saved.size()==1);
        saved.clear();HthCRMAsserter.schedule(reset,tx,true,saved::add);callback[0].afterCompletion(Status.STATUS_UNKNOWN);check(saved.isEmpty());
        for(String failure:Arrays.asList("NONE","INSERT","COMMIT","CLOSE","ROLLBACK")) {
            Resources resources=new Resources(failure);
            HthMtbWriter.write(reset,resources);
            check(resources.opens==1 && resources.closes==1);
            check(resources.inserts==1); // no retry even if commit outcome is uncertain
            check(resources.bindings.size()==LocalHthCRMRepositoryAdapter.COLUMNS.length);
            check(!resources.sql.contains("DIGX_CZ_CRM_EVENT3_DETAILS") && !resources.sql.contains("USER@PARTY"));
            if("NONE".equals(failure)||"CLOSE".equals(failure))check(resources.commits==1 && resources.rollbacks==0);
            else check(resources.rollbacks==1);
        }
        Resources active=new Resources("NONE");active.status=Status.STATUS_ACTIVE;
        HthMtbWriter.write(reset,active);check(active.opens==0 && active.closes==0);
        System.out.println("PASS HTH MTB: "+checks+" mapping, secret exclusion, commit/rollback, isolation and SQL binding checks");
    }
    static class Resources implements HthMtbWriter.Resources {
        int status=Status.STATUS_NO_TRANSACTION,opens,closes,inserts,commits,rollbacks;
        boolean active;String failure,sql;Map<Integer,Object>bindings=new HashMap<Integer,Object>();
        Resources(String failure){this.failure=failure;}
        public int transactionStatus(){return status;}
        public Session open(){opens++;
            com.ofss.fc.infra.das.orm.Transaction transaction=(com.ofss.fc.infra.das.orm.Transaction)Proxy.newProxyInstance(
                Session.class.getClassLoader(),new Class[]{com.ofss.fc.infra.das.orm.Transaction.class},(p,m,a)->{
                if(m.getName().equals("isActive"))return active;
                if(m.getName().equals("commit")){commits++;if(failure.equals("COMMIT"))throw new IllegalStateException();active=false;return null;}
                if(m.getName().equals("rollback")){rollbacks++;active=false;if(failure.equals("ROLLBACK"))throw new IllegalStateException();return null;}
                return null;
            });
            Query query=(Query)Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Query.class},(p,m,a)->{
                if(m.getName().equals("setParameter")){bindings.put((Integer)a[0],a[1]);return p;}
                if(m.getName().equals("setTimeout"))return p;
                if(m.getName().equals("executeUpdate")){inserts++;if(failure.equals("INSERT")||failure.equals("ROLLBACK"))throw new IllegalStateException();return 1;}
                throw new AssertionError(m.getName());
            });
            return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Session.class},(p,m,a)->{
                if(m.getName().equals("beginTransaction")){active=true;return transaction;}
                if(m.getName().equals("fetchCurrentTransaction"))return transaction;
                if(m.getName().equals("createSQLQuery")){sql=(String)a[0];return query;}
                throw new AssertionError(m.getName());
            });
        }
        public void close(Session session){closes++;if(failure.equals("CLOSE"))throw new IllegalStateException();}
    }
}
