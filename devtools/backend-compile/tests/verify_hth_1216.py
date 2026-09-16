"""1216 runtime tests: actual helper, save methods, DTO serialization, SDK recipients and SMS dispatcher.
Bank repositories, transaction boundaries, clock and MNG network use fixtures; no messages are sent.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
CP = os.pathsep.join([str(ROOT / 'devtools/backend-compile/build/classes/java/main')] +
                    [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])
SERVICE = next(p for p in PROJECTS.rglob('HostToHostUserAccess.java') if '/app/hosttohost/service/' in str(p))
SMS = next(PROJECTS.rglob('SMSDispatcher.java'))


def method(source, signature):
    start = source.index(signature)
    end = source.index('\n\t}', start) + len('\n\t}')
    assert end > start
    return source[start:end]


with tempfile.TemporaryDirectory(prefix='hth1216-notification-') as tmp:
    work = Path(tmp)
    fixtures = []

    def write(name, value):
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        if name.endswith('.java'):
            fixtures.append(str(path))

    def bean(pkg, cls, fields, extra=''):
        body = 'package ' + pkg + '; public class ' + cls + ' {'
        for field, typ in fields.items():
            body += f'private {typ} {field}; public {typ} get{field}(){{return {field};}} public void set{field}({typ} v){{{field}=v;}}'
        write(pkg.replace('.', '/') + '/' + cls + '.java', body + extra + '}')

    write('fixture/Bank.java', '''package fixture;
import java.util.*; import java.lang.reflect.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;
public class Bank {
 public static SessionContext context;
 public static int approvalReads, userReads, networkCalls;
 public static boolean approvalFailure, extensionFailure, includeSmsEvents=false, reject, writeFailure, registerFailure, registerStatusFailure, approval=true, rollback; public static int writes, registrations; public static final List<ActivityLog> committed=new ArrayList<>();
 public static Object lastRequest;
 public static String country="852", company="company@example.test";
 public static final List<String> events=new ArrayList<>();
 public static final List<ActivityLog> logs=new ArrayList<>();
 public static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static class Format {public String formatMessage(String value,Object...args){return value;}}
 public static Object adapter(){
  Class<?>[] types={com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter.class,
   com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter.class,com.ofss.digx.app.sms.adapter.user.IUserMeAdapter.class};
  return Proxy.newProxyInstance(types[0].getClassLoader(),types,(p,m,a)->{
   if(m.getName().equals("getPartyPreferences")){com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO d=new com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO();d.setOfficeEmailId(company);d.setPartyName("Company");return d;}
   if(m.getName().equals("getConfiguationDetails"))return "fixture";
   if(m.getName().equals("readUser")){com.ofss.digx.app.sms.dto.user.UserResponseDTO r=new com.ofss.digx.app.sms.dto.user.UserResponseDTO();com.ofss.digx.app.sms.dto.user.UserDTO u=new com.ofss.digx.app.sms.dto.user.UserDTO();com.ofss.digx.domain.sms.entity.user.User row=com.ofss.digx.domain.sms.entity.user.User.rows.get((String)a[1]);u.setMobileNumber(row==null?"61234567":row.getMobileNumber());u.setEmailId(row==null?"target@example.test":row.getEmailId());r.setUserDTO(u);return r;}
   throw new AssertionError(m.getName());});
 }
 public static <T>T network(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
  check(m.getName().equals("createAlert"),"MNG operation");networkCalls++;lastRequest=a[0];
  com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO response=new com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO();
  if(reject)response.setErrorCode("TEST_FAILURE");return Collections.singletonList(response);
 }));}
}'''.replace('d.setPartyName("Company")','d.setCompanyName("Company")'))
    bean('com.ofss.fc.app.context', 'SessionContext',
         {k:'String' for k in ('TargetUnit','UserLocale','TransactingPartyCode','ServiceCallContextType','UserId','ServiceCode','ExternalReferenceNo','InternalReferenceNo')})
    bean('com.ofss.digx.domain.sms.entity.user', 'UserKey', {'UserId':'String'})
    bean('com.ofss.digx.domain.sms.entity.user', 'User', {'EmailId':'String','MobileNumber':'String'},
         'public static final java.util.Map<String,User> rows=new java.util.HashMap<>(); public User read(UserKey k) throws com.ofss.digx.infra.exceptions.Exception {fixture.Bank.userReads++;return rows.get(k.getUserId());}')
    bean('com.ofss.digx.cz.bea.domain.sms.entity.user', 'UserExtensionDataKey', {'UserExtensionKey':'String'})
    bean('com.ofss.digx.cz.bea.domain.sms.entity.user', 'UserExtensionData',
         {k:'String' for k in ('UserChannelType','CdcNo','UserID','MobileCode')},
         'public static final java.util.Map<String,UserExtensionData> rows=new java.util.HashMap<>();public UserExtensionData read(UserExtensionDataKey k) throws com.ofss.digx.infra.exceptions.Exception {if(fixture.Bank.extensionFailure)throw new IllegalStateException();return rows.get(k.getUserExtensionKey());}')
    bean('com.ofss.digx.framework.domain.transaction', 'TransactionKey', {'Id':'String'})
    write('com/ofss/digx/framework/domain/transaction/Transaction.java', '''package com.ofss.digx.framework.domain.transaction;
public class Transaction {
 public static String status="APPROVED",signers="MAKER~EARLIER~FINAL~",service="com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit";
 public Transaction read(TransactionKey key){fixture.Bank.approvalReads++;if(fixture.Bank.approvalFailure)throw new IllegalStateException();return this;}
 public String getServiceId(){return service;} public Approval getApprovalDetails(){return new Approval();}
 public static class Approval {public String getStatus(){return status;}public String getSignedBy(){return signers;}}
}''')
    write('com/ofss/digx/infra/thread/ThreadAttribute.java', '''package com.ofss.digx.infra.thread;
public class ThreadAttribute {public static final String TRANSACTION_REFERENCE_NO="reference";public static Object reference="APPROVAL-1216";public static Object get(String key){return reference;}}
''')
    write('com/ofss/fc/infra/thread/ThreadAttribute.java', '''package com.ofss.fc.infra.thread;
public class ThreadAttribute {public static final String CURRENT_TASK="task";public static Object get(String key){return null;}}
''')
    write('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java', '''package com.ofss.digx.app.adapter;
public class AdapterFactoryConfigurator {
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public IAdapterFactory getAdapterFactory(String name){return (IAdapterFactory)java.lang.reflect.Proxy.newProxyInstance(IAdapterFactory.class.getClassLoader(),new Class[]{IAdapterFactory.class},(p,m,a)->fixture.Bank.adapter());}
}''')
    write('com/ofss/digx/app/AbstractApplication.java', '''package com.ofss.digx.app;
public class AbstractApplication {
 protected com.ofss.fc.service.response.TransactionStatus registerActivityAndGenerateEvent(com.ofss.fc.app.context.SessionContext c,String activity,String event,com.ofss.fc.datatype.Date date,com.ofss.digx.app.alerts.dto.eventgen.ActivityLog log) throws com.ofss.digx.infra.exceptions.Exception {
  com.ofss.fc.framework.domain.entity.ep.dto.ActivityData data=new com.ofss.fc.framework.domain.entity.ep.dto.ActivityData();data.setActivityLog(log);
  fixture.Bank.registrations++; if(fixture.Bank.registerFailure)throw new IllegalStateException("DO_NOT_LOG_CONTACT"); if(fixture.Bank.registerStatusFailure){com.ofss.fc.service.response.TransactionStatus s=new com.ofss.fc.service.response.TransactionStatus();s.setReplyCode(99);return s;} fixture.Bank.events.add(event);fixture.Bank.logs.add((com.ofss.digx.app.alerts.dto.eventgen.ActivityLog)data.getActivityLog());return new com.ofss.fc.service.response.TransactionStatus();}
}''')
    write('com/ofss/digx/app/alerts/dto/eventgen/ActivityLog.java','package com.ofss.digx.app.alerts.dto.eventgen;public class ActivityLog extends com.ofss.fc.xface.ep.dto.ActivityLog {}')
    write('com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java', 'package com.ofss.digx.cz.bea.app.logger;public class BeaSystemOut {public static void println(Object o){} public static void println(String o){} public static void printErr(Object o){}}')
    write('com/ofss/digx/cz/bea/app/customconfig/util/CustomConfigUtil.java', '''package com.ofss.digx.cz.bea.app.customconfig.util;
public class CustomConfigUtil {public static String readConfigValue(String key,String fallback){
 if(key.equals("SMS_DISPATCHER_ALERT_EVENTID_LIST"))return fixture.Bank.includeSmsEvents?"USER_EMAIL_ADDRESS_UPDATE,USER_MOBILE_NUMBER_UPDATED_REMINDER":"";
 return fallback;}}
''')
    write('com/ofss/digx/extxface/extxface/ExtxfaceAdapterFactory.java','''package com.ofss.digx.extxface.extxface;
public class ExtxfaceAdapterFactory {public static ExtxfaceAdapterFactory getInstance(){return new ExtxfaceAdapterFactory();}public <T>T getAdapter(Class<T> type,String op,com.ofss.fc.enumeration.DeterminantType determinant){return fixture.Bank.network(type);}}
''')
    bean('com.ofss.digx.cz.bea.domain.emailmng', 'CountryCodeKey', {'Id':'String'})
    write('com/ofss/digx/cz/bea/domain/emailmng/CountryCode.java','''package com.ofss.digx.cz.bea.domain.emailmng;
public class CountryCode {private String country;public String getMobile_code(){return country;}public CountryCode read(CountryCodeKey key) throws com.ofss.digx.infra.exceptions.Exception{country="FINAL".equals(key.getId())?"853":fixture.Bank.country;return this;}}
''')
    bean('com.ofss.digx.cz.bea.domain.emailmng', 'EmailMNG',
         {**{k:'String' for k in ('RecipientId','MessageBody','Subject','CustomerId','PartyId','ActivityId','ActionId','EventId','CodActDataId','TxnType','OrgTxnRefNO','Alert_type','ResponseStatus')},'LastUpdatedDate':'com.ofss.fc.datatype.Date','Key':'EmailMNGKey'},
         'public static EmailMNG last;public void create(EmailMNG m) throws com.ofss.digx.infra.exceptions.Exception{last=m;}public void update(EmailMNG m) throws com.ofss.digx.infra.exceptions.Exception{last=m;}')
    write('com/ofss/digx/cz/bea/app/common/util/CZCommonUtils.java','''package com.ofss.digx.cz.bea.app.common.util;
public class CZCommonUtils {public static String getFormatDate(com.ofss.fc.datatype.Date d){return "fixed";}
public static java.util.List<String> sepChar(String s,String d){return java.util.Collections.singletonList(s);}
public static String maskName(java.util.List<String> names,int i){return "Masked Company";}}''')
    service=SERVICE.read_text()
    imports='\n'.join(re.findall(r'^import .*;', service, re.M))
    # Execute both real save methods. Bank validation/storage/transaction scope are fixtures.
    start=service.index('  private HostToHostUserAccessResponseDTO saveResponse(')
    end=service.index('  private void validateContext(', start)
    write('HostToHostUserAccess.java','package com.ofss.digx.cz.bea.app.hosttohost.service;\n'+imports+'\npublic class HostToHostUserAccess extends AbstractApplication {\n'+'''
 private static final Logger LOGGER=Logger.getLogger("fixture");
 private static final fixture.Bank.Format FORMATTER=new fixture.Bank.Format();
 private boolean isApprovedExecution(){return fixture.Bank.approval;}
 private String normalize(String s){return s;} private String readTransactionId(){return "REF";}
 private void setExternalReferenceNumber(String s){} private String readUserId(SessionContext c){return c.getUserId();}
 private void validateWriteRequest(SessionContext c,HostToHostUserAccessDTO r,String a,boolean approved) throws Exception {}
 private void applyApprovedAccess(HostToHostUserAccessDTO r,String a,String u){if(fixture.Bank.writeFailure)throw new IllegalStateException("test storage failure");fixture.Bank.writes++;}
 private com.ofss.digx.app.messages.Status fetchStatus(){return new com.ofss.digx.app.messages.Status();}
 private TransactionStatus fetchTransactionStatus(){return new TransactionStatus();}
 private com.ofss.digx.app.messages.Status buildStatus(TransactionStatus s){return new com.ofss.digx.app.messages.Status();}
 private void fillTransactionStatus(TransactionStatus s,Throwable t){fixture.Bank.rollback=true;}
 public void testSave(SessionContext c,HostToHostUserAccessDTO r,String op) throws Exception {
  String service=HostToHostUserAccess.class.getName()+"."+op;
  if(op.equals("submit"))saveResponse(c,r,service,"CREATE");else saveStatus(c,r,service,op.equals("edit")?"EDIT":"DELETE");
 }
'''+service[start:end]+'}')
    # Replace server-only access policy and Interaction boundaries, retaining call order.
    abstract=work/'com/ofss/digx/app/AbstractApplication.java'
    s=abstract.read_text().replace('public class AbstractApplication {','''public class AbstractApplication {
 protected void checkAccessPolicy(String s,com.ofss.fc.app.context.SessionContext c,Object r){}
 protected void checkResponsePolicy(com.ofss.fc.app.context.SessionContext c,Object r){}
''')
    abstract.write_text(s)
    write('com/ofss/digx/app/Interaction.java','''package com.ofss.digx.app;
public class Interaction {public static void begin(com.ofss.fc.app.context.SessionContext c){} public static void close(){if(!fixture.Bank.rollback)fixture.Bank.committed.addAll(fixture.Bank.logs);else fixture.Bank.logs.clear();}}''')
    # The bank Date constructor boots server XML configuration. Stub only this clock
    # value; recipient routing, MNG construction and success/failure handling stay real.
    source=SMS.read_text().replace('new Date(new java.util.Date())', 'null')
    imports='\n'.join(re.findall(r'^import .*;',source,re.M))
    signatures=['private DispatchResultDTO dispatchMNGSms(', 'public String getCurrentDateAndTime(', 'public static String ShortString(',
                'private List<MNGSmsAlertDTO> buildMNGrequest(', 'private EmailMNG populateDomainObject(',
                'private DispatchResultDTO convertToDispatchResult(', 'private String generateHash(', 'private String bytesToHex(']
    write('SMSDispatcher.java','package com.ofss.digx.cz.bea.domain.service.dispatch;\n'+imports+'\npublic class SMSDispatcher {\n'+'''
 private final java.util.logging.Logger logger=java.util.logging.Logger.getLogger("test");
 private final fixture.Bank.Format formatter=new fixture.Bank.Format();
 public SessionContext getSessionContext() throws FatalException{return fixture.Bank.context;}
 public DispatchResultDTO test(AlertRequestDTO request,IDispatchData data,String body){return dispatchMNGSms(request,data,body);}
'''+ '\n'.join(method(source,sig) for sig in signatures)+'}')
    sources=list(PROJECTS.rglob('HthUserAccessNotification.java'))
    for name in ('UserManagementActivityLogDTO.java','HostToHostUserAccessDTO.java','HostToHostUserAccessResponseDTO.java'):
        sources+=list(PROJECTS.rglob(name))
    # Only the server Date construction is replaced. The real DTO serialization and recipient helper run.
    helper=sources[0]
    path=work/helper.name
    path.write_text(helper.read_text().replace('new Date()', 'null'))
    sources[0]=path
    result_compile=subprocess.run([str(JDK/'javac'),'--release','8','-proc:none','-cp',CP,'-d',tmp,*fixtures,*map(str,sources),
                    str(Path(__file__).with_name('Hth1216NotificationTest.java'))],check=False)
    if result_compile.returncode: raise SystemExit(result_compile.returncode)
    result=subprocess.run([str(JDK/'java'),'-cp',tmp+os.pathsep+CP,'com.ofss.digx.cz.bea.app.hosttohost.service.Hth1216NotificationTest'])
    raise SystemExit(result.returncode)
