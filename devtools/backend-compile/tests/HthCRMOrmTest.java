package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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
    static final int COLUMN_COUNT=113;
    static final Map<String,Field> FIELD_BY_COLUMN=mappingFields();
    static final Map<String,Integer> WIDTH_BY_COLUMN=new HashMap<String,Integer>();
    static Map<String,Field> mappingFields() {
        Map<String,Field> fields=new LinkedHashMap<String,Field>();
        try {
            DocumentBuilderFactory builder=DocumentBuilderFactory.newInstance();builder.setNamespaceAware(true);
            Element entity=(Element)builder.newDocumentBuilder().parse(HthCRMOrmTest.class.getClassLoader().getResourceAsStream(
                "orm/eclipselink/mappings/cz/hosttohost/crm/HthCRMEvent3DomainDTO.orm.xml")).getElementsByTagNameNS("*","entity").item(0);
            NodeList basics=entity.getElementsByTagNameNS("*","basic");
            for(int index=0;index<basics.getLength();index++) {
                Element basic=(Element)basics.item(index);
                String column=((Element)basic.getElementsByTagNameNS("*","column").item(0)).getAttribute("name").toUpperCase(Locale.ROOT);
                Field field=HthCRMEvent3DomainDTO.class.getDeclaredField(basic.getAttribute("name"));field.setAccessible(true);
                if(fields.put(column,field)!=null)throw new AssertionError("duplicate mapped column "+column);
            }
            if(fields.size()!=COLUMN_COUNT-1)throw new AssertionError("Expected 112 basic mappings");
            return fields;
        } catch(Exception failure) {throw new ExceptionInInitializerError(failure);}
    }
    static void fillRichFields(HthCRMEvent3DomainDTO event) {
        int index=0;
        try {
            for(Map.Entry<String,Field> entry:FIELD_BY_COLUMN.entrySet()) {
                Field field=entry.getValue();index++;
                if(field.get(event)!=null)continue;
                if(field.getType()==String.class) {
                    String value="X"+Integer.toString(index,36).toUpperCase(Locale.ROOT);
                    int width=WIDTH_BY_COLUMN.get(entry.getKey());
                    field.set(event,value.substring(Math.max(0,value.length()-width)));
                } else if(field.getType()==BigDecimal.class) field.set(event,new BigDecimal(index+".1234567890"));
                else throw new AssertionError("Fixture does not support "+field.getType());
            }
        } catch(IllegalAccessException failure) {throw new AssertionError(failure);}
    }
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
        fillRichFields(event);
        return event;
    }
    static Map<String,Object> columns(HthCRMEvent3DomainDTO event) {
        Map<String,Object> expected=new LinkedHashMap<String,Object>();
        expected.put("EVENT_ID",event.getKey().getEventId());
        try {
            for(Map.Entry<String,Field> entry:FIELD_BY_COLUMN.entrySet())
                expected.put(entry.getKey(),entry.getValue().get(event));
        } catch(IllegalAccessException failure) {throw new AssertionError(failure);}
        return expected;
    }
    static void verifyRow(Connection observer,HthCRMEvent3DomainDTO event)throws Exception {
        verifyRow(observer,event,true);
    }
    static void verifyRow(Connection observer,HthCRMEvent3DomainDTO event,boolean requireRich)throws Exception {
        Map<String,Object> expected=columns(event);
        try(PreparedStatement statement=observer.prepareStatement("SELECT * FROM "+TABLE+" WHERE EVENT_ID=?")) {
            statement.setString(1,event.getKey().getEventId());
            try(ResultSet rows=statement.executeQuery()) {
                check(rows.next(),"mapped event committed");
                check(rows.getMetaData().getColumnCount()==COLUMN_COUNT && expected.size()==COLUMN_COUNT,"113 mapped columns");
                for(Map.Entry<String,Object> entry:expected.entrySet()) {
                    if(requireRich)check(entry.getValue()!=null,"rich mapping fixture covers "+entry.getKey());
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
        } else if(expected instanceof BigDecimal && actual instanceof Number)
            check(((BigDecimal)expected).compareTo(new BigDecimal(actual.toString()))==0,"numeric mapping "+column);
        else check(Objects.equals(expected,actual),"mapping "+column+": expected="+expected+", actual="+actual);
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
        check(immutableColumns==COLUMN_COUNT-1,"all 112 non-key HTH columns reject updates");
        int baselineColumns=0;
        for(org.eclipse.persistence.internal.helper.DatabaseField field:bco.getFields()) {
            String column=field.getName().toUpperCase(Locale.ROOT);baselineColumns++;
            check(hth.getFields().stream().anyMatch(candidate->column.equals(candidate.getName().toUpperCase(Locale.ROOT))),
                "BCO baseline column retained in separate HTH model: "+column);
        }
        check(baselineColumns==99,"test uses all 99 actual BCO ORM columns");
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
            try(ResultSet metadata=observer.getMetaData().getColumns(null,"HTH_BEA","HTH_CRM_EVENT_DETAILS",null)) {
                while(metadata.next())WIDTH_BY_COLUMN.put(metadata.getString("COLUMN_NAME"),metadata.getInt("COLUMN_SIZE"));
            }
            check(WIDTH_BY_COLUMN.size()==COLUMN_COUNT,"actual expanded table has 113 columns");
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
                rollbackCopyRetainsExpandedFields();
                nullableReservedFields(observer);
                productionContextCollection(observer);
                check(legacy.equals(legacyRows(observer)),"old table unchanged after successful/duplicate writes");
                check(DataAccessManager.currentLookups==0,"normal repository path never consults caller thread's session");
                missingTableDoesNotFallback(observer,legacy);
                check(DataAccessManager.current()==null,"caller session marker restored by test only");
            } finally {factory.close();}
        }
        System.out.println("PASS: "+checks+" real EclipseLink 2.5.2 / OBDX ORM checks: 113 fields, embedded PK/timestamp, append-only merge/race, unbound and caller-bound sessions, duplicate rollback, legacy isolation and no fallback");
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
        for(Field field:FIELD_BY_COLUMN.values()) {
            if(field.getType()==String.class)field.set(changed,"Z");
            else if(field.getType()==BigDecimal.class)field.set(changed,new BigDecimal("-987.654321"));
            else if(field.getType()==Timestamp.class)field.set(changed,Timestamp.valueOf("2000-01-01 00:00:00.000001"));
            else throw new AssertionError("Unexpected persisted type "+field.getType());
            altered++;
        }
        check(altered==112,"merge attempt modifies every non-key field, including numeric values");
        EntityManager mergeManager=factory.createEntityManager();Session merge=TestOrmAccess.wrap(mergeManager);
        try {merge.beginTransaction();merge.saveOrUpdate(changed);merge.fetchCurrentTransaction().commit();}
        finally {if(merge.fetchCurrentTransaction().isActive())merge.fetchCurrentTransaction().rollback();merge.close();}
        check(count(observer,TABLE)==1,"direct merge did not add a row");
        verifyRow(observer,first); // Checks JDBC plus all 113 values through a fresh EntityManager.
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
    static void rollbackCopyRetainsExpandedFields() {
        HthCRMEvent3DomainDTO original=event("expanded-rollback");
        Map<String,Object> before=columns(original);
        HthCRMEvent3DomainDTO rejected=HthCRMRequestAssembler.rolledBack(original);
        check(rejected!=original,"rollback uses an independent event object");
        check(before.equals(columns(original)),"rollback did not mutate any of 113 original fields");
        Map<String,Object> expected=new LinkedHashMap<String,Object>(before);
        expected.put("EVENT_STATUS_CODE","R");expected.put("ERROR_CODE","BUSINESS_ROLLBACK");expected.put("DEDUP_KEY",null);
        check(expected.equals(columns(rejected)),"rollback retains all new BCO baseline data and HTH metadata");
    }
    static void nullableReservedFields(Connection observer)throws Exception {
        HthCRMEvent3DomainDTO sparse=event("sparse-reserved-fields");int nullable=0;
        try(ResultSet metadata=observer.getMetaData().getColumns(null,"HTH_BEA","HTH_CRM_EVENT_DETAILS",null)) {
            while(metadata.next()) {
                String column=metadata.getString("COLUMN_NAME");
                if(metadata.getInt("NULLABLE")==DatabaseMetaData.columnNullable) {
                    FIELD_BY_COLUMN.get(column).set(sparse,null);nullable++;
                }
            }
        }
        check(nullable>89,"expanded reserved and optional metadata fields permit null");
        int before=count(observer,TABLE);DbResources resources=new DbResources();
        HthCRMWriter.write(sparse,resources);resources.verifyClosed();
        check(count(observer,TABLE)==before+1,"event with all nullable fields absent persists");
        verifyRow(observer,sparse,false);
    }
    static void productionContextCollection(Connection observer)throws Exception {
        java.util.prefs.Preferences hth=com.ofss.fc.infra.config.ConfigurationFactory.getInstance().getConfigurations("HTHCRMConfiguration");
        java.util.prefs.Preferences crm=com.ofss.fc.infra.config.ConfigurationFactory.getInstance().getConfigurations("CRMConfiguration");
        String oldEnabled=hth.get("ENABLED",null),oldActivity=hth.get("ACTIVITY_PASSWORD_RESET",null);
        String oldChannel=crm.get("CRM_CHNL-ID_INTERNET",null),oldType=crm.get("CRM_RECORD_TYPE",null);
        String oldFiller=crm.get("CRM_FILLER_01",null);
        Object oldTask=com.ofss.fc.infra.thread.ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.CURRENT_TASK);
        Object oldIp=com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS");
        Object oldToken=com.ofss.digx.infra.thread.ThreadAttribute.get("TFA_ITOKEN_ID");
        Object oldError=com.ofss.digx.infra.thread.ThreadAttribute.get("TFA_ERR_CODE");
        final javax.transaction.Synchronization[] callback={null};
        javax.transaction.Transaction transaction=(javax.transaction.Transaction)Proxy.newProxyInstance(
            HthCRMOrmTest.class.getClassLoader(),new Class[]{javax.transaction.Transaction.class},(p,m,a)->{
                if("getStatus".equals(m.getName()))return Status.STATUS_ACTIVE;
                if("registerSynchronization".equals(m.getName())){callback[0]=(javax.transaction.Synchronization)a[0];return null;}
                throw new AssertionError("Unexpected business transaction mutation: "+m.getName());
            });
        weblogic.transaction.ClientTransactionManager manager=(weblogic.transaction.ClientTransactionManager)Proxy.newProxyInstance(
            HthCRMOrmTest.class.getClassLoader(),new Class[]{weblogic.transaction.ClientTransactionManager.class},(p,m,a)->{
                if("getStatus".equals(m.getName()))return Status.STATUS_NO_TRANSACTION;
                if("getTransaction".equals(m.getName()))return transaction;
                throw new AssertionError("Unexpected JTA mutation: "+m.getName());
            });
        weblogic.transaction.TransactionHelper.pushTransactionHelper(new weblogic.transaction.TransactionHelper(){
            public UserTransaction getUserTransaction(){throw new AssertionError("Unexpected business transaction access");}
            public weblogic.transaction.ClientTransactionManager getTransactionManager(){return manager;}
        });
        try {
            hth.put("ENABLED","Y");hth.put("ACTIVITY_PASSWORD_RESET","HTH_RESET_TEST");
            crm.put("CRM_CHNL-ID_INTERNET","ELE-CONTEXT");crm.put("CRM_RECORD_TYPE","3");crm.put("CRM_FILLER_01","   ");
            com.ofss.fc.infra.thread.ThreadAttribute.set(com.ofss.fc.infra.thread.ThreadAttribute.CURRENT_TASK,"BEFORE_COMMIT");
            com.ofss.digx.infra.thread.ThreadAttribute.set("FMO_IP_ADDRESS","192.0.2.20");
            com.ofss.digx.infra.thread.ThreadAttribute.set("TFA_ITOKEN_ID","DO_NOT_PERSIST_TOKEN");
            Map<String,Object> source=HthCRMTest.data("RESET");source.put("requestId","context-chain");
            source.put("password","DO_NOT_PERSIST_PASSWORD");
            int before=count(observer,TABLE);
            HthCRMAsserter.collect("x.HostToHostApiPassword.reset",source);
            check(callback[0]!=null && count(observer,TABLE)==before,"real collector waits for approval transaction completion");
            com.ofss.fc.infra.thread.ThreadAttribute.set(com.ofss.fc.infra.thread.ThreadAttribute.CURRENT_TASK,"AFTER_COMMIT");
            com.ofss.digx.infra.thread.ThreadAttribute.set("FMO_IP_ADDRESS","192.0.2.21");
            callback[0].afterCompletion(Status.STATUS_COMMITTED);
            check(count(observer,TABLE)==before+1,"collector/context/assembler/writer/actual ORM chain persists after commit");
            try(PreparedStatement query=observer.prepareStatement("SELECT * FROM "+TABLE+" WHERE REQUEST_ID=?")) {
                query.setString(1,"context-chain");
                try(ResultSet rows=query.executeQuery()) {
                    check(rows.next(),"collected event stored");
                    check("BEFORE_COMMIT".equals(rows.getString("TASK_CODE")) && "192.0.2.20".equals(rows.getString("IP_ADDRESS")),"request metadata captured before transaction callback loses context");
                    check("ELE-CONTEXT".equals(rows.getString("CHNL_ID")) && "3".equals(rows.getString("RECORD_TYPE")),"BCO common configuration reaches HTH database");
                    check("   ".equals(rows.getString("FILLER_01")),"intentional filler spaces survive complete collection and ORM persistence");
                    check("HTH_RESET_TEST".equals(rows.getString("EVENT_ACTV_TYPE_CODE")),"HTH activity mapping remains independent");
                    check(rows.getObject("TOKEN_ID")==null && rows.getObject("AR_TOKEN")==null
                        && rows.getObject("EVENT_AMT")==null,"production collection leaves secrets and financial fields null");
                    for(int index=1;index<=rows.getMetaData().getColumnCount();index++) {
                        Object value=rows.getObject(index);
                        check(value==null || !value.toString().contains("DO_NOT_PERSIST"),"production row contains only allowed event metadata");
                    }
                    check(!rows.next(),"one business operation emits one CRM event");
                }
            }
            com.ofss.digx.infra.thread.ThreadAttribute.set("TFA_ERR_CODE","TFA_FALLBACK");
            com.ofss.digx.framework.domain.transaction.ProcessingError safe=new com.ofss.digx.framework.domain.transaction.ProcessingError(
                "PREFIX_ABCDEFGHIJKLMNOPQRST","DO_NOT_PERSIST_ERROR_MESSAGE");
            com.ofss.digx.framework.domain.transaction.ProcessingError second=new com.ofss.digx.framework.domain.transaction.ProcessingError(
                "SECOND_ERROR_MUST_NOT_WIN","DO_NOT_PERSIST_SECOND_MESSAGE");
            approvalErrorCase(observer,callback,Arrays.asList(safe,second),"ABCDEFGHIJKLMNOPQRST","approval-safe");
            com.ofss.digx.framework.domain.transaction.ProcessingError invalid=new com.ofss.digx.framework.domain.transaction.ProcessingError(
                "DO_NOT_PERSIST invalid code","DO_NOT_PERSIST_INVALID_MESSAGE");
            approvalErrorCase(observer,callback,Arrays.asList(invalid,second),"TFA_FALLBACK","approval-invalid");
            approvalErrorCase(observer,callback,Collections.singletonList(null),"TFA_FALLBACK","approval-null-first");
            approvalErrorCase(observer,callback,Collections.emptyList(),"TFA_FALLBACK","approval-empty");
            approvalErrorCase(observer,callback,null,"TFA_FALLBACK","approval-null-errors");
        } finally {
            weblogic.transaction.TransactionHelper.popTransactionHelper();
            com.ofss.digx.infra.thread.ThreadAttribute.set("TFA_ERR_CODE",oldError);
            com.ofss.fc.infra.thread.ThreadAttribute.set(com.ofss.fc.infra.thread.ThreadAttribute.CURRENT_TASK,oldTask);
            com.ofss.digx.infra.thread.ThreadAttribute.set("FMO_IP_ADDRESS",oldIp);
            com.ofss.digx.infra.thread.ThreadAttribute.set("TFA_ITOKEN_ID",oldToken);
            restorePreference(hth,"ENABLED",oldEnabled);restorePreference(hth,"ACTIVITY_PASSWORD_RESET",oldActivity);
            restorePreference(crm,"CRM_CHNL-ID_INTERNET",oldChannel);restorePreference(crm,"CRM_RECORD_TYPE",oldType);
            restorePreference(crm,"CRM_FILLER_01",oldFiller);
        }
    }
    static void approvalErrorCase(Connection observer,javax.transaction.Synchronization[] callback,
            List<com.ofss.digx.framework.domain.transaction.ProcessingError> errors,String expectedCode,String reference)throws Exception {
        com.ofss.digx.framework.domain.transaction.Transaction approval=new com.ofss.digx.framework.domain.transaction.Transaction();
        com.ofss.digx.framework.domain.transaction.TransactionKey key=new com.ofss.digx.framework.domain.transaction.TransactionKey();
        key.setId(reference);approval.setKey(key);
        approval.setServiceId("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update");
        com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO user=new com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO();
        user.setUserChannelType("HTH");user.setUserID("TARGET@PARTY");user.setCdcNo("001");
        approval.setTransactionSnapshot(user);approval.setErrors(errors);
        com.ofss.fc.app.context.SessionContext context=new com.ofss.fc.app.context.SessionContext();context.setUserId("APPROVER@PARTY");
        int before=count(observer,TABLE);callback[0]=null;
        HthCRMApprovalAsserter.committed(context,approval,"REJECT");
        check(callback[0]!=null && count(observer,TABLE)==before,"actual domain approval capture waits for completion");
        callback[0].afterCompletion(Status.STATUS_COMMITTED);
        check(count(observer,TABLE)==before+1,"actual domain approval error reaches ORM row");
        try(PreparedStatement query=observer.prepareStatement("SELECT * FROM "+TABLE+" WHERE SOURCE_TRX_REF_NBR=?")) {
            query.setString(1,reference);
            try(ResultSet rows=query.executeQuery()) {
                check(rows.next(),"approval event stored");
                check(expectedCode.equals(rows.getString("ERR_CODE")),"first public domain ProcessingError is safely prioritized: "+reference);
                check("APPROVAL_REJECT".equals(rows.getString("PHASE")) && "APPROVER@PARTY".equals(rows.getString("USER_ID")),"approval actor/phase preserved with CRM error metadata");
                check(rows.getObject("ERROR_CODE")==null,"approval CRM error does not invent HTH business failure");
                for(int index=1;index<=rows.getMetaData().getColumnCount();index++) {
                    Object value=rows.getObject(index);
                    check(value==null || !value.toString().contains("DO_NOT_PERSIST"),"approval row excludes error messages/unsafe codes");
                }
                check(!rows.next(),"one row per approval action");
            }
        }
        user.setUserChannelType("BCO");callback[0]=null;
        HthCRMApprovalAsserter.committed(context,approval,"REJECT");
        check(callback[0]==null && count(observer,TABLE)==before+1,"ordinary BCO approval with errors still bypasses HTH collector");
    }
    static void restorePreference(java.util.prefs.Preferences preferences,String key,String value) {
        if(value==null)preferences.remove(key);else preferences.put(key,value);
    }
    static class DbResources implements HthCRMWriter.Resources {
        EntityManager entityManager;int opens,closes;boolean activeAtClose;
        public int transactionStatus(){return Status.STATUS_NO_TRANSACTION;}
        public Session open(){opens++;entityManager=factory.createEntityManager();return TestOrmAccess.wrap(entityManager);}
        public void close(Session session){closes++;activeAtClose=entityManager.getTransaction().isActive();session.close();}
        void verifyClosed(){check(opens==1 && closes==1 && !activeAtClose && !entityManager.isOpen(),"independent transaction ended and real session closed once");}
    }
}
