"""Exercise production Code activation methods and SQL with H2 (Oracle mode).

JAVA_HOME must point to a JDK; H2_JAR must point to an H2 2.x jar.
SessionContext/enums use the repository SDK. ORM/session and event registration
are fixtures; this checks the activation/notification boundary, not SMTP delivery
or the complete WebLogic approval workflow. The JDBC fixture translates Oracle's
NUMTODSINTERVAL to the equivalent H2 interval expression; predicates are unchanged.
No bank database/API is accessed.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
H2 = Path(os.environ['H2_JAR'])
BASE = ROOT / 'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea'
service = (BASE / 'app/hosttohost/service/HostToHostApiPassword.java').read_text()
adapter = (BASE / 'domain/hosttohost/entity/repository/adapter/LocalHthApiPasswordCodeRepositoryAdapter.java').read_text()


def method(source, name):
    start = re.search(r'  (?:private|public) [^\n]*\b' + name + r'\(', source)
    assert start, name
    end = source.index('\n  }', start.start()) + len('\n  }')
    return source[start.start():end].replace('  @Override\n', '')


source = r'''
import java.sql.*;
import java.util.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.enumeration.ServiceCallContextType;
class Exception extends java.lang.Exception {
  Exception(String message){super(message);} Exception(java.lang.Exception cause){super(cause);}
}
class Query {
  final Connection connection; final String sql; final Map<Integer,Object> parameters=new TreeMap<>();
  Query(Connection connection,String sql){this.connection=connection;this.sql=sql.replace("NUMTODSINTERVAL(?, 'HOUR')","(CAST(? AS INTEGER) * INTERVAL '1' HOUR)");}
  void setParameter(int index,Object value){parameters.put(index,value);}
  PreparedStatement prepare() throws SQLException {
    PreparedStatement statement=connection.prepareStatement(sql);
    for(Map.Entry<Integer,Object> parameter:parameters.entrySet())statement.setObject(parameter.getKey(),parameter.getValue());
    return statement;
  }
  List list() throws Exception {
    try(PreparedStatement statement=prepare();ResultSet result=statement.executeQuery()){
      List<Object[]> rows=new ArrayList<>();
      while(result.next())rows.add(new Object[]{result.getString(1),result.getString(2)});
      return rows;
    }catch(SQLException error){throw new Exception(error);}
  }
  int executeUpdate() throws Exception {
    try(PreparedStatement statement=prepare()){return statement.executeUpdate();}
    catch(SQLException error){throw new Exception(error);}
  }
}
class Session {
  Query createSQLQuery(String sql){return new Query(CodeApprovalTest.db,sql);}
  void refresh(HthApiPasswordCode row) throws Exception {row.status=CodeApprovalTest.value(row.id,"STATUS");}
}
class Repository { QUERIES }
class HthApiPasswordCode {String id,status; HthApiPasswordCode(String id){this.id=id;}}
class LocalHthApiPasswordCodeRepositoryAdapter {
  static LocalHthApiPasswordCodeRepositoryAdapter getInstance(){return new LocalHthApiPasswordCodeRepositoryAdapter();}
  HthApiPasswordCode read(String id){return new HthApiPasswordCode(id);}
}
class BeaSystemOut {static void println(String value){System.out.println(value);}}
public class CodeApprovalTest {
  static Connection db; static SessionContext context=new SessionContext();
  static final List<String> notifications=new ArrayList<>(); static int opened;
  final Repository codeRepository=new Repository();
  static class SessionLease {
    final Session session=new Session();
    void close(boolean success) throws Exception {
      try{if(success)db.commit();else db.rollback();}catch(SQLException error){throw new Exception(error);}
    }
  }
  SessionLease open(boolean write){opened++;return new SessionLease();}
  String key(String id){return id;}
  // Match the SDK event-registration boundary: VALIDATE does not register an event.
  void notifyHthApiPasswordApproved(SessionContext ignored,HthApiPasswordCode row){
    check("ACTIVE".equals(row.status),"notification must see refreshed ACTIVE Code");
    if(context.getServiceCallContextType()!=ServiceCallContextType.VALIDATE)notifications.add(row.id);
  }
  METHODS
  static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
  static String value(String id,String column) throws Exception {
    try(PreparedStatement statement=db.prepareStatement("SELECT "+column+" FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID=?")){
      statement.setString(1,id);try(ResultSet result=statement.executeQuery()){check(result.next(),"Code missing");return result.getString(1);}
    }catch(SQLException error){throw new Exception(error);}
  }
  static void seed() throws java.lang.Exception {
    db.createStatement().execute("DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE");
    db.createStatement().execute("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE (ID,PARTY_ID,USER_NAME,PURPOSE,STATUS,OBJECT_STATUS) VALUES ('new','PARTY','USER@PARTY','SETUP','PENDING','A'),('old','PARTY','USER','SETUP','ACTIVE','A')");
    db.commit();notifications.clear();opened=0;
  }
  static boolean activate(CodeApprovalTest test,String operator) throws Exception {
    return test.activateApprovedCode("new","PARTY","USER@PARTY","SETUP",operator,"TX",24);
  }
  public static void main(String[] args) throws java.lang.Exception {
    db=DriverManager.getConnection("jdbc:h2:mem:approval;MODE=Oracle");db.setAutoCommit(false);
    db.createStatement().execute("CREATE SCHEMA HTH_BEA");
    db.createStatement().execute("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID VARCHAR(30) PRIMARY KEY, PARTY_ID VARCHAR(30), USER_NAME VARCHAR(60), PURPOSE VARCHAR(20), STATUS VARCHAR(20), OBJECT_STATUS VARCHAR(1), TRANSACTION_ID VARCHAR(30), EXPIRY_TIME TIMESTAMP, LAST_UPDATED_BY VARCHAR(30), LAST_UPDATE_DATE TIMESTAMP)");
    com.ofss.fc.infra.thread.ThreadAttribute.set("CTX",context);
    CodeApprovalTest test=new CodeApprovalTest();
    for(String operator:new String[]{"AUTO_APPROVED_AP","CHECKER"}){
      seed();context.setServiceCallContextType(ServiceCallContextType.VALIDATE);
      check(!activate(test,operator),"validation must not activate Code");
      check(opened==0,"validation must not open a write session");
      check("PENDING".equals(value("new","STATUS")),"validation changed pending Code");
      check("ACTIVE".equals(value("old","STATUS")),"validation retired existing Code");
      check(value("new","EXPIRY_TIME")==null,"validation started expiry");
      check(notifications.isEmpty(),"validation sent notification");
      context.setServiceCallContextType(ServiceCallContextType.NORMAL);
      check(activate(test,operator),"actual execution did not activate");
      check("ACTIVE".equals(value("new","STATUS")),"actual execution did not persist activation");
      check("INVALID".equals(value("old","STATUS")),"previous Code was not retired");
      check("TX".equals(value("new","TRANSACTION_ID")),"transaction binding missing");
      String expiry=value("new","EXPIRY_TIME");check(expiry!=null,"expiry missing");
      check(notifications.equals(Collections.singletonList("new")),"actual execution lost/duplicated notification");
      check(!activate(test,operator),"replay activated again");
      check(expiry.equals(value("new","EXPIRY_TIME")),"replay extended expiry");
      check(notifications.size()==1,"replay resent notification");
    }
    seed();context.setServiceCallContextType(ServiceCallContextType.NORMAL);
    check(activate(test,"AUTO_APPROVED_AP"),"direct execution without validation did not activate");
    check(notifications.size()==1,"direct execution lost notification");
    // A rejected/missing Code cannot become active or emit the approval notification.
    seed();db.createStatement().execute("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET OBJECT_STATUS='D' WHERE ID='new'");db.commit();
    try{activate(test,"CHECKER");throw new AssertionError("deleted Code accepted");}
    catch(Exception expected){check("DIGX_CZ_HTH_PW_008".equals(expected.getMessage()),"wrong rejection");}
    check("PENDING".equals(value("new","STATUS"))&&notifications.isEmpty(),"rejected Code changed/notified");
    System.out.println("PASS: production activation + SQL: VALIDATE has no writes, normal auto/checker execution notifies once, direct execution, replay, rejection");
    db.close();
  }
}
'''
source = source.replace('QUERIES', '\n'.join(method(adapter, name) for name in ('lockForApproval', 'retireActive', 'activate')))
source = source.replace('METHODS', '\n'.join(method(service, name) for name in ('activateApprovedCode', 'string')))
classpath = os.pathsep.join(map(str, [H2] + sorted((ROOT / 'consulting/middleware/lib').rglob('*.jar'))))
with tempfile.TemporaryDirectory(prefix='hth-code-approval-') as directory:
    work = Path(directory)
    (work / 'CodeApprovalTest.java').write_text(source)
    thread = work / 'com/ofss/fc/infra/thread/ThreadAttribute.java'
    thread.parent.mkdir(parents=True)
    thread.write_text('''package com.ofss.fc.infra.thread;
public class ThreadAttribute {
  private static final java.util.Map<String,Object> data=new java.util.HashMap<>();
  public static Object get(String key){return data.get(key);}
  public static void set(String key,Object value){data.put(key,value);}
}''')
    config = work / 'com/ofss/fc/infra/config/ConfigurationFactory.java'
    config.parent.mkdir(parents=True)
    config.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
  private static final java.util.prefs.Preferences preferences=new java.util.prefs.AbstractPreferences(null, "") {
    protected String getSpi(String key){return null;} protected void putSpi(String key,String value){}
    protected void removeSpi(String key){} protected void removeNodeSpi(){}
    protected String[] keysSpi(){return new String[0];} protected String[] childrenNamesSpi(){return new String[0];}
    protected java.util.prefs.AbstractPreferences childSpi(String name){return this;}
    protected void syncSpi(){} protected void flushSpi(){}
  };
  public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
  public java.util.prefs.Preferences getConfigurations(String name){return preferences;}
  public java.util.prefs.Preferences getRootConfigurations(){return preferences;}
}''')
    for command in (
        [str(JAVA / 'javac'), '-proc:none', '-cp', classpath, '-d', directory,
         str(thread), str(config), str(work / 'CodeApprovalTest.java')],
        [str(JAVA / 'java'), '-cp', directory + os.pathsep + classpath, 'CodeApprovalTest'],
    ):
        result = subprocess.run(command, capture_output=True, text=True, timeout=60)
        print(result.stdout + result.stderr)
        if result.returncode:
            raise SystemExit(result.returncode)
