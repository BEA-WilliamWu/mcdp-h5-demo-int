package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import javax.persistence.*;
import javax.transaction.Status;
import javax.transaction.UserTransaction;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.HthCRMLocalRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.assembler.HthCRMRequestAssembler;
import com.ofss.digx.cz.bea.domain.common.entity.crm.CRMEvent3DomainDTO;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.jpa.JpaEntityManagerFactory;

/** Actual EclipseLink mappings and OBDX Session/Transaction wrappers; JDBC is fixture/observation only. */
public class HthCRMOrmTest {
    static final String URL="jdbc:h2:mem:crm;MODE=Oracle;DB_CLOSE_DELAY=-1";
    static final String TABLE="HTH_BEA.HTH_CRM_EVENT_DETAILS";
    static int checks;
    static EntityManagerFactory factory;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static int count(Connection connection,String table)throws Exception {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
            rows.next();return rows.getInt(1);
        }
    }
    static List<List<Object>> legacyRows(Connection connection)throws Exception {
        List<List<Object>> rows=new ArrayList<List<Object>>();
        try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(
                "SELECT * FROM HTH_BEA.HTH_MTB_EVENT_DETAILS ORDER BY EVENT_ID")) {
            while(result.next()) {
                List<Object> row=new ArrayList<Object>();
                for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.add(result.getObject(i));
                rows.add(row);
            }
        }
        return rows;
    }
    static HthCRMEvent3DomainDTO event(String requestId) {
        Map<String,Object> source=HthCRMTest.data("RESET");
        source.put("requestId",requestId);source.put("crmTask","SYNTHETIC_TASK");
        source.put("approvalReference","SYNTHETIC_REFERENCE");source.put("crmActionId",requestId);
        source.put("linkageType","ASSOCIATED");source.put("crmIp","192.0.2.1");
        source.put("errorCode","SYNTHETIC_CODE");
        HthCRMEvent3DomainDTO event=HthCRMRequestAssembler.assemble("x.HostToHostApiPassword.reset",source,"SYNTHETIC_ACTIVITY");
        // Direct repository tests supply the timestamp that the production writer normally sets.
        event.setCreatedAt(Timestamp.valueOf("2026-09-28 11:22:33.123456"));
        return event;
    }
    static Map<String,Object> columns(HthCRMEvent3DomainDTO event) {
        Map<String,Object> expected=new LinkedHashMap<String,Object>();
        expected.put("EVENT_ID",event.getKey().getEventId());
        expected.put("EVENT_DTE",event.getEventDte());expected.put("EVENT_TIME",event.getEventTime());
        expected.put("SOURCE_SYSTEM",event.getSourceSystem());expected.put("CHANNEL_TYPE",event.getChannelType());
        expected.put("TARGET_USER_CHANNEL",event.getTargetUserChannel());expected.put("ACTIVITY_KEY",event.getActivityKey());
        expected.put("EVENT_ACTV_TYPE_CODE",event.getEventActvTypeCode());expected.put("EVENT_STATUS_CODE",event.getEventStatusCode());
        expected.put("PHASE",event.getPhase());expected.put("FIN_IND",event.getFinInd());
        expected.put("USER_ID",event.getUserId());expected.put("TARGET_USER_ID",event.getTargetUserId());
        expected.put("ACCT_NBR",event.getAcctNbr());expected.put("RELATIONSHIP_TYPE",event.getRelationshipType());
        expected.put("SERVICE_ID",event.getServiceId());expected.put("TASK_CODE",event.getTaskCode());
        expected.put("SOURCE_TRX_REF_NBR",event.getSourceTrxRefNbr());expected.put("SOURCE_ACTION_ID",event.getSourceActionId());
        expected.put("REQUEST_ID",event.getRequestId());expected.put("IP_ADDRESS",event.getIpAddress());
        expected.put("ERROR_CODE",event.getErrorCode());expected.put("DEDUP_KEY",event.getDedupKey());
        expected.put("CREATED_AT",event.getCreatedAt());
        return expected;
    }
    static void verifyRow(Connection observer,HthCRMEvent3DomainDTO event)throws Exception {
        Map<String,Object> expected=columns(event);
        try(PreparedStatement statement=observer.prepareStatement("SELECT * FROM "+TABLE+" WHERE EVENT_ID=?")) {
            statement.setString(1,event.getKey().getEventId());
            try(ResultSet rows=statement.executeQuery()) {
                check(rows.next(),"mapped event committed");
                check(rows.getMetaData().getColumnCount()==24 && expected.size()==24,"24 mapped columns");
                for(Map.Entry<String,Object> entry:expected.entrySet()) {
                    check(entry.getValue()!=null,"rich mapping fixture covers "+entry.getKey());
                    verifyColumn(entry.getKey(),entry.getValue(),rows.getObject(entry.getKey()));
                }
                check(!rows.next(),"unique event primary key");
            }
        }
        EntityManager reader=factory.createEntityManager();
        try {
            HthCRMEvent3DomainKey key=new HthCRMEvent3DomainKey();key.setEventId(event.getKey().getEventId());
            HthCRMEvent3DomainDTO loaded=reader.find(HthCRMEvent3DomainDTO.class,key);
            check(loaded!=null && key.equals(loaded.getKey()),"embedded primary key round trip");
            Map<String,Object> actual=columns(loaded);
            for(Map.Entry<String,Object> entry:expected.entrySet())
                verifyColumn(entry.getKey(),entry.getValue(),actual.get(entry.getKey()));
        } finally {reader.close();}
    }
    static void verifyColumn(String column,Object expected,Object actual) {
        if(expected instanceof Timestamp && actual instanceof Timestamp) {
            Timestamp timestamp=(Timestamp)actual;
            long difference=Math.abs(java.time.temporal.ChronoUnit.NANOS.between(
                ((Timestamp)expected).toLocalDateTime(),timestamp.toLocalDateTime()));
            check(timestamp.getNanos()%1000==0 && difference<=1000,"TIMESTAMP(6) write/read precision");
        } else check(Objects.equals(expected,actual),"mapping "+column);
    }
    static void verifyMappings() {
        org.eclipse.persistence.sessions.server.ServerSession session=((JpaEntityManagerFactory)factory).getServerSession();
        ClassDescriptor hth=session.getDescriptor(HthCRMEvent3DomainDTO.class);
        ClassDescriptor bco=session.getDescriptor(CRMEvent3DomainDTO.class);
        check(hth!=null && TABLE.equals(hth.getTables().get(0).getQualifiedName()),"actual HTH mapping table/schema");
        check(hth.getPrimaryKeyFieldNames().size()==1 && hth.getPrimaryKeyFieldNames().get(0).toString().endsWith(".EVENT_ID"),"actual HTH embedded PK mapping");
        check(bco!=null && "DIGX_CZ_CRM_EVENT3_DETAILS".equals(bco.getTableName()),"BCO mapping remains separately loaded");
        check(hth.getQueryManager().getQueryTimeout()==5,"HTH mapping retains five-second ORM statement timeout");
        check(bco.getQueryManager().getQueryTimeout()==0,"HTH customizer leaves BCO descriptor timeout unchanged");
        check(hth.isIsolated() && bco.isSharedIsolation(),"only HTH event cache is isolated");
        int immutableColumns=0;
        for(org.eclipse.persistence.internal.helper.DatabaseField field:hth.getFields()) {
            if("EVENT_ID".equals(field.getName()))continue;
            check(!field.isUpdatable(),"HTH append-only mapping "+field.getName());immutableColumns++;
        }
        check(immutableColumns==23,"all non-key HTH columns reject updates");
        check(bco.getMappingForAttributeName("userId").getField().isUpdatable(),"BCO update mapping unchanged");
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");
        try(Connection observer=DriverManager.getConnection(URL)) {
            try(Statement statement=observer.createStatement()) {
                statement.execute("CREATE SCHEMA HTH_BEA");
                statement.execute(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
                statement.execute("CREATE UNIQUE INDEX UK_HTH_CRM_EVENT_DEDUP ON "+TABLE+"(DEDUP_KEY)");
                statement.execute("CREATE TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS AS SELECT * FROM "+TABLE+" WHERE 1=0");
                statement.execute("ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS ADD LEGACY_ORIGIN VARCHAR(80)");
                statement.executeUpdate("INSERT INTO HTH_BEA.HTH_MTB_EVENT_DETAILS "
                    + "(EVENT_ID,EVENT_DTE,EVENT_TIME,SOURCE_SYSTEM,CHANNEL_TYPE,ACTIVITY_KEY,EVENT_STATUS_CODE,PHASE,FIN_IND,SERVICE_ID,CREATED_AT,LEGACY_ORIGIN) "
                    + "VALUES ('legacy-marker','20000101','000000','LEGACY','CM','LEGACY','A','LEGACY','N','legacy.service',CURRENT_TIMESTAMP,'existing-table-marker')");
                statement.execute("CREATE TABLE BCO_BUSINESS(ID INT PRIMARY KEY)");
            }
            List<List<Object>> legacy=legacyRows(observer);
            check(legacy.size()==1 && legacy.get(0).contains("existing-table-marker"),"legacy marker fixture");
            factory=Persistence.createEntityManagerFactory("NONXA");
            DataAccessManager.factory=factory;
            try {
                verifyMappings();
                noCurrentSession(observer);
                defaultWriterResources(observer);
                callerIsolationAndDuplicateRollback(observer);
                samePrimaryKeyRaceCannotOverwrite(observer);
                check(legacy.equals(legacyRows(observer)),"old table unchanged after successful/duplicate writes");
                check(DataAccessManager.currentLookups==0,"normal repository path never consults caller thread's session");
                missingTableDoesNotFallback(observer,legacy);
                check(DataAccessManager.current()==null,"caller session marker restored by test only");
            } finally {factory.close();}
        }
        System.out.println("PASS: "+checks+" real EclipseLink 2.5.2 / OBDX ORM checks: 24 fields, embedded PK/timestamp, append-only merge/race, unbound and caller-bound sessions, duplicate rollback, legacy isolation and no fallback");
    }
    static void noCurrentSession(Connection observer)throws Exception {
        check(DataAccessManager.current()==null,"no caller session before independent write");
        LocalDateTime before=LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1);
        HthCRMEvent3DomainDTO first=event("first");
        DbResources resources=new DbResources();HthCRMWriter.write(first,resources);
        LocalDateTime after=LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusSeconds(1);
        check(count(observer,TABLE)==1,"no-current-session ORM write succeeds");
        check(DataAccessManager.current()==null,"independent write never binds a caller session");
        check(!first.getCreatedAt().toLocalDateTime().isBefore(before) && !first.getCreatedAt().toLocalDateTime().isAfter(after),"createdAt is current Hong Kong wall time, not business occurredAt");
        resources.verifyClosed();verifyRow(observer,first);
        HthCRMEvent3DomainDTO duplicateId=event("different-action-same-pk");duplicateId.setKey(first.getKey());
        duplicateId.setUserId("MUST_NOT_REPLACE_EXISTING_ACTOR");
        HthCRMTest.CapturedLogs logs=new HthCRMTest.CapturedLogs();logs.start();
        try {HthCRMWriter.write(duplicateId,new DbResources());check(logs.has("WRITE_FAILED",null),"duplicate primary key rejected");}
        finally {logs.stop();}
        check(count(observer,TABLE)==1,"duplicate PK did not create row");verifyRow(observer,first);
        // Exercise the actual merge branch even when the repository's early guard is bypassed.
        HthCRMEvent3DomainDTO changed=event("direct-merge");changed.setKey(first.getKey());
        int altered=0;
        for(java.lang.reflect.Method method:HthCRMEvent3DomainDTO.class.getDeclaredMethods()) {
            if(method.getName().startsWith("set") && method.getParameterTypes().length==1
                    && method.getParameterTypes()[0]==String.class) {
                method.invoke(changed,"Z");altered++;
            }
        }
        changed.setCreatedAt(Timestamp.valueOf("2000-01-01 00:00:00.000001"));
        check(altered==22,"merge attempt modifies all non-key string fields and timestamp");
        EntityManager mergeManager=factory.createEntityManager();Session merge=TestOrmAccess.wrap(mergeManager);
        try {merge.beginTransaction();merge.saveOrUpdate(changed);merge.fetchCurrentTransaction().commit();}
        finally {if(merge.fetchCurrentTransaction().isActive())merge.fetchCurrentTransaction().rollback();merge.close();}
        check(count(observer,TABLE)==1,"direct merge did not add a row");
        verifyRow(observer,first); // Checks JDBC plus all 24 values through a fresh EntityManager.
    }
    static void defaultWriterResources(Connection observer)throws Exception {
        final int[] statusCalls={0};
        weblogic.transaction.ClientTransactionManager manager=(weblogic.transaction.ClientTransactionManager)Proxy.newProxyInstance(
            HthCRMOrmTest.class.getClassLoader(),new Class[]{weblogic.transaction.ClientTransactionManager.class},(p,m,a)->{
                if("getStatus".equals(m.getName())){statusCalls[0]++;return Status.STATUS_NO_TRANSACTION;}
                throw new AssertionError("Unexpected JTA mutation: "+m.getName());
            });
        weblogic.transaction.TransactionHelper.pushTransactionHelper(new weblogic.transaction.TransactionHelper(){
            public UserTransaction getUserTransaction(){throw new AssertionError("Unexpected JTA transaction");}
            public weblogic.transaction.ClientTransactionManager getTransactionManager(){return manager;}
        });
        int opens=DataAccessManager.opens,closes=DataAccessManager.closes;
        try {HthCRMWriter.write(event("default-resources"));}
        finally {weblogic.transaction.TransactionHelper.popTransactionHelper();}
        check(statusCalls[0]==1,"default writer checks JTA boundary");
        check(DataAccessManager.opens==opens+1 && DataAccessManager.closes==closes+1,"default writer opens/closes real independent NONXA wrapper");
        check(count(observer,TABLE)==2 && DataAccessManager.current()==null,"default resources persisted without binding session");
    }
    static void callerIsolationAndDuplicateRollback(Connection observer)throws Exception {
        EntityManager business=factory.createEntityManager();Session caller=TestOrmAccess.wrap(business);
        DataAccessManager.bind(caller);
        try {
            business.getTransaction().begin();business.createNativeQuery("INSERT INTO BCO_BUSINESS VALUES(1)").executeUpdate();
            HthCRMEvent3DomainDTO event=event("caller-bound");DbResources resources=new DbResources();
            HthCRMWriter.write(event,resources);
            check(DataAccessManager.current()==caller && business.isOpen() && business.getTransaction().isActive(),"caller session and active transaction preserved");
            check(count(observer,"BCO_BUSINESS")==0 && count(observer,TABLE)==3,"HTH commit did not commit caller");
            resources.verifyClosed();verifyRow(observer,event);
            business.getTransaction().rollback();
            check(count(observer,"BCO_BUSINESS")==0 && count(observer,TABLE)==3,"caller rollback did not roll back HTH event");

            // Both inserts remain pending until the real OBDX Transaction wrapper commits.
            EntityManager pending=factory.createEntityManager();Session independent=TestOrmAccess.wrap(pending);
            try {
                independent.beginTransaction();HthCRMLocalRepository repository=new HthCRMLocalRepository();
                repository.create(independent,event("must-roll-back"));
                repository.create(independent,event("caller-bound"));
                check(count(observer,TABLE)==3 && pending.getTransaction().isActive(),"saveOrUpdate buffers inserts until commit");
                boolean failed=false;
                try {independent.fetchCurrentTransaction().commit();}catch(java.lang.Exception expected){failed=true;}
                check(failed,"database dedup violation raised by real commit");
                if(independent.fetchCurrentTransaction().isActive())independent.fetchCurrentTransaction().rollback();
                check(!pending.getTransaction().isActive() && count(observer,TABLE)==3,"failed commit rolled back entire HTH transaction");
            } finally {independent.close();}
            business.getTransaction().begin();business.createNativeQuery("INSERT INTO BCO_BUSINESS VALUES(2)").executeUpdate();
            DbResources duplicate=new DbResources();HthCRMTest.CapturedLogs logs=new HthCRMTest.CapturedLogs();logs.start();
            try {HthCRMWriter.write(event("caller-bound"),duplicate);check(logs.has("WRITE_FAILED",null),"writer isolates duplicate commit failure");}
            finally {logs.stop();}
            duplicate.verifyClosed();
            check(business.getTransaction().isActive() && count(observer,"BCO_BUSINESS")==0,"failed HTH commit left caller active and uncommitted");
            business.getTransaction().commit();
            check(count(observer,"BCO_BUSINESS")==1 && count(observer,TABLE)==3,"caller commits independently after HTH failure");
        } finally {DataAccessManager.bind(null);if(business.getTransaction().isActive())business.getTransaction().rollback();business.close();}
    }
    static void missingTableDoesNotFallback(Connection observer,List<List<Object>> legacy)throws Exception {
        EntityManager business=factory.createEntityManager();Session caller=TestOrmAccess.wrap(business);DataAccessManager.bind(caller);
        try {
            business.getTransaction().begin();business.createNativeQuery("INSERT INTO BCO_BUSINESS VALUES(3)").executeUpdate();
            observer.createStatement().execute("DROP TABLE "+TABLE);
            DbResources resources=new DbResources();HthCRMTest.CapturedLogs logs=new HthCRMTest.CapturedLogs();logs.start();
            try {HthCRMWriter.write(event("missing-table"),resources);check(logs.has("WRITE_FAILED",null),"missing new table is a write failure");}
            finally {logs.stop();}
            resources.verifyClosed();check(legacy.equals(legacyRows(observer)),"no fallback into writable old table");
            check(business.getTransaction().isActive() && count(observer,"BCO_BUSINESS")==1,"missing table did not commit caller");
            business.getTransaction().commit();
            check(count(observer,"BCO_BUSINESS")==2 && legacy.equals(legacyRows(observer)),"caller still commits and legacy marker remains unchanged");
        } finally {DataAccessManager.bind(null);if(business.getTransaction().isActive())business.getTransaction().rollback();business.close();}
    }
    static void samePrimaryKeyRaceCannotOverwrite(Connection observer)throws Exception {
        final HthCRMEvent3DomainDTO contender=event("race-contender");
        final HthCRMEvent3DomainDTO winner=event("race-winner");winner.setKey(contender.getKey());
        winner.setUserId("ORIGINAL_RACE_ACTOR");contender.setUserId("MUST_NOT_OVERWRITE_RACE_ACTOR");
        final int[] gapCommits={0},mergeCalls={0};
        EntityManager business=factory.createEntityManager();Session caller=TestOrmAccess.wrap(business);
        DataAccessManager.bind(caller);
        DbResources resources=new DbResources(){
            @Override public Session open(){
                final Session actual=super.open();
                return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Session.class},(proxy,method,args)->{
                    Object result;
                    try {result=method.invoke(actual,args);}
                    catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
                    if("get".equals(method.getName())) {
                        check(result==null && gapCommits[0]==0,"real repository guard observed absent primary key");
                        // Commit a competing row precisely after the real guard read and before the real saveOrUpdate.
                        EntityManager competingManager=factory.createEntityManager();Session competing=TestOrmAccess.wrap(competingManager);
                        try {
                            competing.beginTransaction();new HthCRMLocalRepository().create(competing,winner);
                            competing.fetchCurrentTransaction().commit();gapCommits[0]++;
                        } finally {
                            if(competing.fetchCurrentTransaction().isActive())competing.fetchCurrentTransaction().rollback();
                            competing.close();
                        }
                    }
                    if("saveOrUpdate".equals(method.getName()))mergeCalls[0]++;
                    return result;
                });
            }
        };
        HthCRMTest.CapturedLogs logs=new HthCRMTest.CapturedLogs();logs.start();
        try {
            business.getTransaction().begin();business.createNativeQuery("INSERT INTO BCO_BUSINESS VALUES(4)").executeUpdate();
            HthCRMWriter.write(contender,resources);
            check(gapCommits[0]==1 && mergeCalls[0]==1 && !logs.has("WRITE_FAILED",null),"same-PK race reaches and commits the real merge branch");
            resources.verifyClosed();
            check(count(observer,TABLE)==4,"race commits exactly one winning event");
            verifyRow(observer,winner);
            check(DataAccessManager.current()==caller && business.isOpen() && business.getTransaction().isActive(),"race preserves caller session and active transaction");
            check(count(observer,"BCO_BUSINESS")==1,"race does not commit caller business data");
            business.getTransaction().rollback();
            check(count(observer,"BCO_BUSINESS")==1 && count(observer,TABLE)==4,"caller rollback leaves winning CRM row intact");
        } finally {
            logs.stop();DataAccessManager.bind(null);
            if(business.getTransaction().isActive())business.getTransaction().rollback();business.close();
        }
    }
    static class DbResources implements HthCRMWriter.Resources {
        EntityManager entityManager;int opens,closes;boolean activeAtClose;
        public int transactionStatus(){return Status.STATUS_NO_TRANSACTION;}
        public Session open(){opens++;entityManager=factory.createEntityManager();return TestOrmAccess.wrap(entityManager);}
        public void close(Session session){closes++;activeAtClose=entityManager.getTransaction().isActive();session.close();}
        void verifyClosed(){check(opens==1 && closes==1 && !activeAtClose && !entityManager.isOpen(),"independent transaction ended and real session closed once");}
    }
}
