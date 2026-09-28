package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.util.*;
import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.logging.*;
import java.util.prefs.Preferences;
import javax.transaction.*;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.assembler.HthCRMRequestAssembler;

/** Runtime contract tests. ORM/JTA proxies model failures; not an Oracle/WebLogic integration test. */
public class HthCRMTest {
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
        disabledGateDiagnostics();
        Map<String,Object> user=data("CREATE");user.put("newUserChannelType","BCO");
        check(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null)==null);
        user.put("newUserChannelType","HTH");
        HthCRMEvent3DomainDTO created=HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null);
        check("USER_CREATE".equals(created.getActivityKey()));
        check("20260923".equals(created.getEventDte()) && "000102".equals(created.getEventTime()));
        check("AP@PARTY".equals(created.getUserId()) && "USER@PARTY".equals(created.getTargetUserId()));
        user.put("businessOutcome","PENDING_APPROVAL");
        check("SUBMIT".equals(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null).getPhase()));
        user.put("businessOutcome","SUCCESS");user.put("effectiveChange",true);
        check("APPLY".equals(HthCRMRequestAssembler.assemble("UserExtensionData.create",user,null).getPhase()));
        check(!event("SETUP").getActivityKey().equals(event("RESET").getActivityKey()));
        check("CODE_GENERATE".equals(event("GENERATE").getActivityKey()));
        check(event("REVEAL")==null);
        Map<String,Object> secret=data("RESET");secret.put("password","DO_NOT_STORE");secret.put("code","123456");
        secret.put("encryptedCredentials","SECRET");secret.put("requestId","request");
        HthCRMEvent3DomainDTO reset=HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",secret,null);
        String persistedValues=beanProperties(reset).toString();
        check(persistedValues.contains("AP@PARTY") && persistedValues.contains("request")
            && !persistedValues.contains("DO_NOT_STORE") && !persistedValues.contains("123456")
            && !persistedValues.contains("SECRET"));
        check(reset.getDedupKey()!=null);
        secret.put("idempotentReplay",true);
        check(HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",secret,null)==null);
        Map<String,Object> access=data("ACCESS_EDIT");access.put("linkageType","ASSOCIATED");
        HthCRMEvent3DomainDTO grant=HthCRMRequestAssembler.assemble("x.HostToHostUserAccess.edit",access,null);
        check("ACCESS_EDIT".equals(grant.getActivityKey()) && "ASSOCIATED".equals(grant.getRelationshipType()));
        check(grant.getDedupKey()==null); // no accidental collapse of different approval actions
        for(String action:Arrays.asList("ENABLE","EDIT","DISABLE")) {
            String method="ENABLE".equals(action)?"submit":action.toLowerCase(Locale.ROOT);
            HthCRMEvent3DomainDTO company=HthCRMRequestAssembler.assemble("x.HostToHostManagement."+method,data("COMPANY_"+action),null);
            check("BM".equals(company.getChannelType()));
        }
        List<HthCRMEvent3DomainDTO> saved=new ArrayList<HthCRMEvent3DomainDTO>();
        final Synchronization[] callback={null};
        Transaction tx=(Transaction)Proxy.newProxyInstance(Transaction.class.getClassLoader(),new Class[]{Transaction.class},(p,m,a)->{
            if(m.getName().equals("getStatus"))return Status.STATUS_ACTIVE;
            if(m.getName().equals("registerSynchronization")){callback[0]=(Synchronization)a[0];return null;}
            throw new AssertionError("Unexpected caller transaction operation: "+m.getName());
        });
        CapturedLogs schedulingLogs=new CapturedLogs();
        schedulingLogs.start();
        HthCRMAsserter.schedule(reset,tx,true,saved::add);check(saved.isEmpty());
        check(schedulingLogs.has("WAITING_FOR_JTA", "PASSWORD_RESET"));
        callback[0].afterCompletion(Status.STATUS_COMMITTED);check(saved.size()==1 && saved.get(0)==reset);
        saved.clear();HthCRMAsserter.schedule(reset,tx,true,saved::add);
        callback[0].afterCompletion(Status.STATUS_ROLLEDBACK);
        check(saved.size()==1 && "R".equals(saved.get(0).getEventStatusCode()) && saved.get(0).getDedupKey()==null);
        check(saved.get(0)!=reset && "A".equals(reset.getEventStatusCode()) && reset.getDedupKey()!=null);
        check("BUSINESS_ROLLBACK".equals(saved.get(0).getErrorCode())
            && reset.getKey().getEventId().equals(saved.get(0).getKey().getEventId())
            && reset.getRequestId().equals(saved.get(0).getRequestId()));
        saved.clear();HthCRMAsserter.schedule(reset,null,true,saved::add);check(saved.isEmpty());
        HthCRMAsserter.schedule(reset,null,false,saved::add);check(saved.size()==1);
        saved.clear();HthCRMAsserter.schedule(reset,tx,true,saved::add);callback[0].afterCompletion(Status.STATUS_UNKNOWN);check(saved.isEmpty());
        check(schedulingLogs.has("LOCAL_TX_PENDING", "PASSWORD_RESET"));
        check(schedulingLogs.has("TX_OUTCOME_UNKNOWN", "PASSWORD_RESET"));
        schedulingLogs.stop();
        for(String failure:Arrays.asList("NONE","SAVE","COMMIT","CLOSE","ROLLBACK")) {
            Resources resources=new Resources(failure);
            HthCRMWriter.write(reset,resources);
            check(resources.opens==1 && resources.closes==1);
            check(resources.lookups==1 && resources.lookupKey==reset.getKey());
            check(resources.saves==1); // no retry even if commit outcome is uncertain
            check(resources.saved==reset);
            if("NONE".equals(failure)||"CLOSE".equals(failure))check(resources.commits==1 && resources.rollbacks==0);
            else check(resources.rollbacks==1);
        }
        Resources active=new Resources("NONE");active.status=Status.STATUS_ACTIVE;
        HthCRMWriter.write(reset,active);check(active.opens==0 && active.closes==0);
        System.out.println("PASS HTH MTB: "+checks+" mapping, secret exclusion, commit/rollback, isolation and ORM save checks");
    }
    /** Inspect actual bean values, including the key, without relying on redacted DTO logging. */
    private static Map<String,Object> beanProperties(Object bean) throws ReflectiveOperationException {
        Map<String,Object> values=new TreeMap<String,Object>();
        for(Method getter:bean.getClass().getDeclaredMethods()) {
            if(Modifier.isPublic(getter.getModifiers()) && getter.getName().startsWith("get")
                    && getter.getParameterTypes().length==0 && getter.getReturnType()!=Void.TYPE) {
                Object value=getter.invoke(bean);
                values.put(getter.getName(),value instanceof HthCRMEvent3DomainKey?beanProperties(value):value);
            }
        }
        return values;
    }
    private static void disabledGateDiagnostics() {
        Preferences config=com.ofss.fc.infra.config.ConfigurationFactory.getInstance().getConfigurations("HTHMtbConfiguration");
        String previous=config.get("ENABLED",null);
        final int[] transactionLookups={0};
        weblogic.transaction.TransactionHelper.pushTransactionHelper(new weblogic.transaction.TransactionHelper() {
            public UserTransaction getUserTransaction() { throw new AssertionError("Disabled CRM requested user transaction"); }
            public weblogic.transaction.ClientTransactionManager getTransactionManager() {
                transactionLookups[0]++;
                throw new AssertionError("Disabled CRM reached transaction/database path");
            }
        });
        CapturedLogs logs=new CapturedLogs();
        logs.start();
        try {
            Map<String,Object> access=data("ACCESS_CREATE");
            access.put("password","DO_NOT_LOG_PASSWORD");
            access.put("crmActionId","DO_NOT_LOG_ACTION");
            for (String flag:Arrays.asList(null,"N","invalid")) {
                if(flag==null)config.remove("ENABLED");else config.put("ENABLED",flag);
                logs.records.clear();
                HthCRMAsserter.collect("com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit",access);
                check(logs.records.size()==1 && logs.records.get(0).getLevel()==Level.INFO);
                check(logs.has("DISABLED","ACCESS_CREATE"));
                check(!logs.text().contains("DO_NOT_LOG") && !logs.text().contains("USER@PARTY")
                    && !logs.text().contains("AP@PARTY") && !logs.text().contains("partyId"));
                check(!logs.has("COLLECT",null) && !logs.has("WRITE",null));
            }
            logs.records.clear();
            int configReads=Integer.parseInt(System.getProperty("hth849.configReads","0"));
            Map<String,Object> bco=data("UPDATE");bco.put("newUserChannelType","BCO");
            HthCRMAsserter.collect("UserExtensionData.update",bco);
            HthCRMAsserter.collect("AccountAccess.update",access);
            check(logs.records.isEmpty());
            check(configReads==Integer.parseInt(System.getProperty("hth849.configReads","0")));
            check(transactionLookups[0]==0);
        } finally {
            logs.stop();
            weblogic.transaction.TransactionHelper.popTransactionHelper();
            if(previous==null)config.remove("ENABLED");else config.put("ENABLED",previous);
        }
    }
    static class CapturedLogs extends Handler {
        final List<LogRecord> records=new ArrayList<LogRecord>();
        final Logger logger=Logger.getLogger(HthCRMAsserter.class.getName());
        Level previous;
        void start(){previous=logger.getLevel();logger.setLevel(Level.ALL);logger.addHandler(this);}
        void stop(){logger.removeHandler(this);logger.setLevel(previous);}
        public void publish(LogRecord record){records.add(record);}
        public void flush(){}
        public void close(){}
        String text(){StringBuilder result=new StringBuilder();for(LogRecord record:records)
            result.append(MessageFormat.format(record.getMessage(),record.getParameters())).append('\n');return result.toString();}
        boolean has(String stage,String activity){for(LogRecord record:records){
            String message=MessageFormat.format(record.getMessage(),record.getParameters());
            if(message.contains("stage="+stage) && (activity==null || message.contains("activity="+activity)))return true;
        }return false;}
    }
    static class Resources implements HthCRMWriter.Resources {
        int status=Status.STATUS_NO_TRANSACTION,opens,closes,lookups,saves,commits,rollbacks;
        boolean active;String failure;HthCRMEvent3DomainDTO saved;HthCRMEvent3DomainKey lookupKey;
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
            return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Session.class},(p,m,a)->{
                if(m.getName().equals("beginTransaction")){active=true;return transaction;}
                if(m.getName().equals("fetchCurrentTransaction"))return transaction;
                if(m.getName().equals("get")){
                    if(a.length!=2 || !"HthCRMEvent3DomainDTO".equals(a[0]) || !(a[1] instanceof HthCRMEvent3DomainKey))
                        throw new AssertionError("Expected an HTH CRM entity alias/key lookup");
                    lookups++;lookupKey=(HthCRMEvent3DomainKey)a[1];
                    return null;
                }
                if(m.getName().equals("saveOrUpdate")){
                    if(a.length!=1 || !(a[0] instanceof HthCRMEvent3DomainDTO))
                        throw new AssertionError("Expected Session.saveOrUpdate with the HTH CRM entity");
                    saves++;saved=(HthCRMEvent3DomainDTO)a[0];
                    if(failure.equals("SAVE")||failure.equals("ROLLBACK"))throw new IllegalStateException();
                    return null;
                }
                if(m.getName().equals("save"))throw new AssertionError("Independent CRM writes must avoid Session.save current-session lookup");
                if(m.getName().equals("createSQLQuery"))throw new AssertionError("CRM must use mapped entity persistence, not native SQL");
                throw new AssertionError(m.getName());
            });
        }
        public void close(Session session){closes++;if(failure.equals("CLOSE"))throw new IllegalStateException();}
    }
}
