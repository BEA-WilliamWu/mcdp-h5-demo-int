"""851 production policy, capture/staging, JDBC ledger, dispatcher and metadata tests.

Uses H2 + the real EclipseLink/OBDX ORM wrapper; bank repositories, JNDI, config,
Event registration and MNG network boundaries are fixtures. No real notification
is sent. Target compilation separately checks integration with all real classes.
"""
from pathlib import Path
import os
import base64
import re
import subprocess
import tempfile
from verify_hth_contact_sql import SQL_DIR

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
H2 = Path(os.environ.get('HTH_TEST_H2_JAR', '/tmp/hth-h2-1.4.200.jar'))
if not H2.is_file():
    raise SystemExit('Set HTH_TEST_H2_JAR to the existing H2 1.4.200 test dependency')
CP = os.pathsep.join([str(H2), str(ROOT / 'devtools/backend-compile/build/classes/java/main')] +
                     [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])
SOURCES = list(PROJECTS.rglob('Hth*Contact*.java')) + list(PROJECTS.rglob('UserProfUpdateActivityLogDTO.java'))
DISPATCH = next(PROJECTS.rglob('HthContactNotificationDispatch.java')).parent
HELPER = next(PROJECTS.rglob('HthProfileContactNotification.java'))


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


with tempfile.TemporaryDirectory(prefix='hth-contact-851-') as tmp:
    work = Path(tmp)
    fixture_sources = []

    def write(name, text):
        file = work / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)
        if name.endswith('.java'):
            fixture_sources.append(str(file))

    def bean(pkg, cls, fields, extra=''):
        code = 'package ' + pkg + '; public class ' + cls + ' {'
        for field, typ in fields.items():
            code += f'private {typ} {field}; public {typ} get{field}(){{return {field};}} public void set{field}({typ} v){{{field}=v;}}'
        write(pkg.replace('.', '/') + '/' + cls + '.java', code + extra + '}')

    write('fixture/Bank.java', '''package fixture;
import java.lang.reflect.*; import java.util.*; import java.sql.*;
import org.h2.jdbcx.JdbcDataSource;
import com.ofss.digx.cz.bea.app.email.dto.alerts.*;
public final class Bank {
 public static final JdbcDataSource source=new JdbcDataSource();
 public static int profileReads, approvalReads, networkCalls, publications;
 public static String outcome="SUCCESS"; public static Object lastRequest;
 public static final Map<String,String> profiles=new HashMap<>();
 public static final List<com.ofss.digx.app.alerts.dto.eventgen.ActivityLog> logs=new ArrayList<>();
 public static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static String scalar(String sql) throws Exception {try(Connection c=source.getConnection();Statement s=c.createStatement();ResultSet rs=s.executeQuery(sql)){return rs.next()?rs.getString(1):null;}}
 public static void sql(String sql) throws Exception {try(Connection c=source.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
 public static <T>T adapter(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
   if(!"createAlert".equals(m.getName()))throw new AssertionError(m.getName());
   networkCalls++;lastRequest=a[0];String ref;
   Object dto=((List<?>)a[0]).get(0);
   if(dto instanceof MNGEmailAlertsDTO)ref=((MNGEmailAlertsDTO)dto).getBody()[0].getSourceSysRefNumber();
   else ref=((MNGSmsAlertDTO)dto).getBody()[0].getSourceSysRefNumber();
   check("Pending".equals(scalar("SELECT RESPONSE_STATUS FROM DIGX_CZ_EMAIL_MNG WHERE REFNUMBER='"+ref+"'")),"MNG row must be committed BEFORE external IO");
   if("TIMEOUT".equals(outcome))throw new RuntimeException("simulated network timeout");
   if("NULL".equals(outcome))return null;
   if(dto instanceof MNGEmailAlertsDTO){MNGEmailAlertsDTO response=new MNGEmailAlertsDTO();if("REJECT".equals(outcome))response.setErrorCode("TEST_REJECT");return Collections.singletonList(response);}
   MNGSmsAlertDTO response=new MNGSmsAlertDTO();if("REJECT".equals(outcome))response.setErrorCode("TEST_REJECT");return Collections.singletonList(response);
 }));}
}''')
    write('fixture/Naming.java', '''package fixture;
import java.util.*;import javax.naming.*;import javax.naming.spi.*;import java.lang.reflect.*;
public class Naming implements InitialContextFactory {
 public Context getInitialContext(Hashtable<?,?> env){return (Context)Proxy.newProxyInstance(Context.class.getClassLoader(),new Class[]{Context.class},(p,m,a)->{
  if(m.getName().equals("lookup")){Bank.check("NONXA".equals(a[0]),"only configured delivery datasource");return Bank.source;}
  if(m.getName().equals("close"))return null;throw new NamingException(m.getName());});}
}''')
    write('com/ofss/fc/infra/config/ConfigurationFactory.java', '''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 private static final ConfigurationFactory INSTANCE=new ConfigurationFactory();
 public static ConfigurationFactory getInstance(){return INSTANCE;}
 public java.util.prefs.Preferences getConfigurations(String category){return java.util.prefs.Preferences.userRoot().node(category);}
 public java.util.prefs.Preferences getRootConfigurations(){return getConfigurations("root");}
}''')
    bean('com.ofss.fc.app.context', 'SessionContext',
         {'TargetUnit': 'String', 'UserLocale': 'String', 'TransactingPartyCode': 'String', 'ServiceCallContextType': 'String'})
    write('com/ofss/digx/app/Interaction.java', """package com.ofss.digx.app;
public class Interaction { public static boolean open;
 public static void begin(com.ofss.fc.app.context.SessionContext c){fixture.Bank.check(!open,"balanced begin");open=true;}
 public static void close(){fixture.Bank.check(open,"balanced close");open=false;} }
""")
    write('com/ofss/digx/app/AbstractApplication.java', '''package com.ofss.digx.app;
public class AbstractApplication {
 protected com.ofss.fc.service.response.TransactionStatus registerActivityAndGenerateEvent(com.ofss.fc.app.context.SessionContext context,String activity,String event,
  com.ofss.fc.datatype.Date date,com.ofss.digx.app.alerts.dto.eventgen.ActivityLog log){
  fixture.Bank.check(com.ofss.digx.app.Interaction.open,"publisher owns OBDX interaction"); fixture.Bank.publications++;fixture.Bank.logs.add(log);return new com.ofss.fc.service.response.TransactionStatus();}
}''')
    write('com/ofss/digx/app/alerts/dto/eventgen/ActivityLog.java',
          'package com.ofss.digx.app.alerts.dto.eventgen; public class ActivityLog extends com.ofss.fc.xface.ep.dto.ActivityLog {}')
    write('com/ofss/digx/extxface/extxface/ExtxfaceAdapterFactory.java', '''package com.ofss.digx.extxface.extxface;
public class ExtxfaceAdapterFactory {
 public static ExtxfaceAdapterFactory getInstance(){return new ExtxfaceAdapterFactory();}
 public <T>T getAdapter(Class<T> type,String operation,com.ofss.fc.enumeration.DeterminantType determinant){return fixture.Bank.adapter(type);}
}''')
    # Real BCO request builders, unchanged method bodies, without unrelated dispatcher bootstrap.
    for cls in ('EmailDispatcher', 'SMSDispatcher'):
        source = (DISPATCH / (cls + '.java')).read_text()
        builder = method(source, 'List<MNGEmailAlertsDTO> buildMNGrequest' if cls == 'EmailDispatcher' else 'List<MNGSmsAlertDTO> buildMNGrequest')
        imports = '\n'.join(re.findall(r'^import .*;', source, re.M))
        write(cls + '.java', 'package com.ofss.digx.cz.bea.domain.service.dispatch;\n' + imports + '\npublic class ' + cls + ' {\n' + builder + '\n}')
    write('com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java', '''package com.ofss.digx.cz.bea.app.logger;
public class BeaSystemOut {public static void println(Object o){} public static void println(String o){} public static void printErr(Object o){throw new AssertionError(o);}}
''')
    bean('com.ofss.digx.cz.bea.app.sms.dto.user', 'UserExtensionDataDTO',
         {'CdcNo': 'String', 'UserID': 'String', 'MobileCode': 'String', 'UserDTO': 'com.ofss.digx.domain.sms.entity.user.User'})
    bean('com.ofss.digx.domain.sms.entity.user', 'UserKey', {'UserId': 'String'})
    bean('com.ofss.digx.domain.sms.entity.user', 'User', {'EmailId': 'String', 'MobileNumber': 'String'},
         'public static final java.util.Map<String,User> rows=new java.util.HashMap<>(); public User read(UserKey k){return rows.get(k.getUserId());}')
    bean('com.ofss.digx.cz.bea.domain.sms.entity.user', 'UserExtensionDataKey', {'UserExtensionKey': 'String'})
    bean('com.ofss.digx.cz.bea.domain.sms.entity.user', 'UserExtensionData', {'CdcNo': 'String', 'UserID': 'String', 'MobileCode': 'String'},
         'public static final java.util.Map<String,UserExtensionData> rows=new java.util.HashMap<>(); public UserExtensionData read(UserExtensionDataKey k){return rows.get(k.getUserExtensionKey());}')
    write('com/ofss/digx/cz/bea/app/hosttohost/adapter/IHthUserProfileAdapter.java', '''package com.ofss.digx.cz.bea.app.hosttohost.adapter;
public interface IHthUserProfileAdapter {
 String HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER="HTH";
 java.util.Map<String,String> listCloseIdsByUserKey(String party);
 static String userProfileKey(String p,String u){return p.length()+":"+p+u;}
}''')
    write('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java', """package com.ofss.digx.app.adapter;
public class AdapterFactoryConfigurator {
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public Factory getAdapterFactory(String name){return new Factory();}
 public static class Factory {public Object getAdapter(String name){
  return java.lang.reflect.Proxy.newProxyInstance(com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter.class.getClassLoader(),
   new Class[]{com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter.class},(p,m,a)->{
    if(!m.getName().equals("getPartyPreferences"))throw new AssertionError(m.getName());
    com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO dto=new com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO();
    dto.setOfficeEmailId("company@example.test");return dto;});}}
}""")
    write('com/ofss/digx/cz/bea/app/customconfig/util/CustomConfigUtil.java', """package com.ofss.digx.cz.bea.app.customconfig.util;
public class CustomConfigUtil { public static String readConfigValue(String key,String fallback){return "BCO_"+key;} }
""")
    write('com/ofss/digx/framework/domain/repository/RepositoryAdapterFactory.java', '''package com.ofss.digx.framework.domain.repository;
public class RepositoryAdapterFactory {
 public static RepositoryAdapterFactory getInstance(){return new RepositoryAdapterFactory();}
 public Object getRepositoryAdapter(String name){return (com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter)p->{fixture.Bank.profileReads++;return fixture.Bank.profiles;};}
}''')
    bean('com.ofss.digx.framework.domain.transaction', 'TransactionKey', {'Id': 'String'})
    write('com/ofss/digx/framework/domain/transaction/Transaction.java', '''package com.ofss.digx.framework.domain.transaction;
public class Transaction {
 public static String status="APPROVED",signers="FIRST~FINAL~",service=com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan.ACTIVITY;
 public Transaction read(TransactionKey key){fixture.Bank.approvalReads++;return this;}
 public String getServiceId(){return service;}
 public static Object snapshot; public Object getTransactionSnapshot(){return snapshot;}
 public Approval getApprovalDetails(){return new Approval();}
 public static class Approval {public String getStatus(){return status;}public String getSignedBy(){return signers;}}
}''')
    write('com/ofss/digx/infra/thread/ThreadAttribute.java', '''package com.ofss.digx.infra.thread;
public class ThreadAttribute {public static final String TRANSACTION_REFERENCE_NO="reference";
 public static Object reference="APPROVAL-851"; public static Object get(String key){return reference;}}
''')
    write('com/ofss/fc/infra/das/orm/eclipselink/TestOrmAccess.java', '''package com.ofss.fc.infra.das.orm.eclipselink;
public class TestOrmAccess {public static com.ofss.fc.infra.das.orm.Session wrap(javax.persistence.EntityManager em){return new EclipseLinkEntityManagerWrapper(em);}}
''')
    write('com/ofss/fc/infra/das/orm/DataAccessManager.java', '''package com.ofss.fc.infra.das.orm;
public class DataAccessManager {public static Session current;public static DataAccessManager getManager(){return new DataAccessManager();}public Session fetchCurrentSession(){return current;}}
''')
    # Uses the actual DDL, only extracting/unquoting the EXECUTE IMMEDIATE string.
    ddl = (SQL_DIR / '1_HTH_Contact_Notification_Outbox.sql').read_text()
    ddl = re.search(r"EXECUTE IMMEDIATE '(CREATE TABLE .*?)';", ddl, re.S).group(1).replace("''", "'")
    write('ledger.sql', ddl)
    write('META-INF/persistence.xml', '''<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence" version="2.1"><persistence-unit name="test" transaction-type="RESOURCE_LOCAL">
<provider>org.eclipse.persistence.jpa.PersistenceProvider</provider><exclude-unlisted-classes>true</exclude-unlisted-classes><properties>
<property name="javax.persistence.jdbc.driver" value="org.h2.Driver"/><property name="javax.persistence.jdbc.url" value="jdbc:h2:mem:hth851;MODE=Oracle;DB_CLOSE_DELAY=-1"/>
<property name="javax.persistence.jdbc.user" value="sa"/><property name="javax.persistence.jdbc.password" value=""/>
<property name="eclipselink.weaving" value="false"/><property name="eclipselink.logging.level" value="WARNING"/>
</properties></persistence-unit></persistence>''')
    tests = [str(Path(__file__).with_name(f)) for f in
             ('HthContactCaptureTest.java', 'HthContactDeliveryTest.java', 'HthContactRuntimeTest.java')]
    compiled = subprocess.run([str(JDK / 'javac'), '--release', '8', '-proc:none', '-cp', CP, '-d', tmp,
                    *fixture_sources, *map(str, SOURCES), *tests], check=False)
    if compiled.returncode:
        raise SystemExit(compiled.returncode)
    executed = subprocess.run([str(JDK / 'java'), '-Djava.util.prefs.userRoot=' + str(work / 'prefs'),
                    '-Djava.naming.factory.initial=fixture.Naming', '-cp', tmp + os.pathsep + CP,
                    'HthContactRuntimeTest', str(work)], check=False)
    raise SystemExit(executed.returncode)
