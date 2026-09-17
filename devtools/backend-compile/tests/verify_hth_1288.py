"""597/1288 runtime tests. Real service methods, DTO serialization and SDK recipient resolution.
Bank storage/approval attributes, clock, Alert registration and network are fixtures.
The real SMSDispatcher method is executed against a fake MNG endpoint; no messages are sent.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import json
from verify_hth_management_templates import templates

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
CP = os.pathsep.join([str(ROOT / 'devtools/backend-compile/build/classes/java/main')] +
                    [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])
SERVICE = next(p for p in PROJECTS.rglob('HostToHostManagement.java') if '/app/hosttohost/service/' in str(p))


def method(source, name, indent='    '):
    start = re.search(r'^' + indent + r'(?:private|public) [^\n]*\b' + name + r'\(', source, re.M).start()
    end = source.index('\n' + indent + '}', start) + len('\n' + indent + '}')
    return source[start:end]


with tempfile.TemporaryDirectory(prefix='hth1288-test-') as tmp:
    work = Path(tmp)
    fixtures = []

    def write(name, value):
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        if name.endswith('.java'):
            fixtures.append(str(path))

    write('fixture/Templates.java', 'package fixture; public class Templates { public static String sms(String action) {' + ''.join('if(action.equals(' + json.dumps(action + '/' + lang) + '))return ' + json.dumps(value) + ';' for action,item in templates().items() for lang,value in item['sms_locales'].items()) + 'throw new AssertionError(action);}}')

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
 public static boolean contactFailure, missingCompany, missingManagement, snapshotFailure, writeFailure, registerFailure, registerStatusFailure, registerNull, reject;
 public static int writes, snapshots, registrations, countryReads, networkCalls;
 public static String email="company@example.test",phone="+86-13800138000",approval="APPROVED",status="ENABLE";
 public static String skip="HTH_API_SERVICE_SUBMIT_SUCCESS,HTH_API_SERVICE_DISABLE_SUCCESS,HTH_API_SERVICE_EDIT_SUCCESS";
 public static Set<String> oldApis=new HashSet<>(Arrays.asList("A","B"));
 public static List<String> events=new ArrayList<>(),activities=new ArrayList<>();
 public static List<ActivityLog> logs=new ArrayList<>(); public static Object lastRequest;
 public static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static class Format {public String formatMessage(String value,Object...args){return String.format(value,args);}}
 public static Object adapter(){
  Class<?>[] types={com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter.class,
    com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter.class};
  return Proxy.newProxyInstance(types[0].getClassLoader(),types,(p,m,a)->{
   if(m.getName().equals("getPartyPreferences")){
    if(contactFailure)throw new IllegalStateException("PRIVATE_CONTACT");if(missingCompany)return null;
    com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO d=new com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO();
    d.setOfficeEmailId(email);d.setOfficeTelNo(phone);return d;}
   if(m.getName().equals("getConfiguationDetails"))return "fixture";
   throw new AssertionError(m.getName());});
 }
 public static <T>T network(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
  check(m.getName().equals("createAlert"),"MNG operation");networkCalls++;lastRequest=a[0];
  com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO r=new com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO();
  if(reject)r.setErrorCode("TEST_FAILURE");return Collections.singletonList(r);
 }));}
}''')
    bean('com.ofss.fc.app.context', 'SessionContext',
         {k:'String' for k in ('TargetUnit','UserLocale','TransactingPartyCode','ServiceCallContextType','UserId','ServiceCode','ExternalReferenceNo','InternalReferenceNo')})
    write('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java', '''package com.ofss.digx.app.adapter;
public class AdapterFactoryConfigurator {
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public IAdapterFactory getAdapterFactory(String name){return (IAdapterFactory)java.lang.reflect.Proxy.newProxyInstance(IAdapterFactory.class.getClassLoader(),new Class[]{IAdapterFactory.class},(p,m,a)->fixture.Bank.adapter());}
}''')
    write('com/ofss/digx/app/AbstractApplication.java', '''package com.ofss.digx.app;
public class AbstractApplication {
 protected com.ofss.fc.service.response.TransactionStatus registerActivityAndGenerateEvent(com.ofss.fc.app.context.SessionContext c,String activity,String event,com.ofss.fc.datatype.Date date,com.ofss.digx.app.alerts.dto.eventgen.ActivityLog log) throws com.ofss.digx.infra.exceptions.Exception {
  fixture.Bank.check(fixture.Bank.writes==1,"notification after business save");fixture.Bank.registrations++;
  if(fixture.Bank.registerFailure)throw new IllegalStateException("PRIVATE_REGISTER");
  if(fixture.Bank.registerNull)return null;
  com.ofss.fc.service.response.TransactionStatus s=new com.ofss.fc.service.response.TransactionStatus();
  if(fixture.Bank.registerStatusFailure){s.setReplyCode(99);return s;}
  com.ofss.fc.framework.domain.entity.ep.dto.ActivityData data=new com.ofss.fc.framework.domain.entity.ep.dto.ActivityData();data.setActivityLog(log);
  fixture.Bank.events.add(event);fixture.Bank.activities.add(activity);fixture.Bank.logs.add((com.ofss.digx.app.alerts.dto.eventgen.ActivityLog)data.getActivityLog());return s;
 }
}''')
    write('com/ofss/digx/app/alerts/dto/eventgen/ActivityLog.java', 'package com.ofss.digx.app.alerts.dto.eventgen; public class ActivityLog extends com.ofss.fc.xface.ep.dto.ActivityLog {}')
    bean('com.ofss.digx.cz.bea.domain.hosttohost.entity', 'HthManagement', {'Key':'HthManagementKey','HthStatus':'String'},
         '''public HthManagement findActiveByPartyId(String party){if(fixture.Bank.snapshotFailure)throw new IllegalStateException("PRIVATE_SNAPSHOT");
         if(fixture.Bank.missingManagement)return null;
         HthManagement m=new HthManagement();m.setHthStatus(fixture.Bank.status);HthManagementKey k=new HthManagementKey();k.setId("M");m.setKey(k);return m;}''')
    for pkg in ('com.ofss.fc.infra.thread', 'com.ofss.digx.infra.thread'):
        write(pkg.replace('.', '/') + '/ThreadAttribute.java', 'package ' + pkg + ''';
public class ThreadAttribute {public static final String APPROVAL_STATUS="APPROVAL",CURRENT_TASK="task";
 public static Object get(String key){return APPROVAL_STATUS.equals(key)?fixture.Bank.approval:null;}}
''')
    write('com/ofss/digx/cz/bea/app/customconfig/util/CustomConfigUtil.java', '''package com.ofss.digx.cz.bea.app.customconfig.util;
public class CustomConfigUtil {public static String readConfigValue(String key,String fallback){
 if(key.equals("SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST"))return fixture.Bank.skip;
 if(key.equals("SMS_DISPATCHER_ALERT_EVENTID_LIST"))return "";return fallback;}}
''')
    s = SERVICE.read_text()
    # Enable now shares the company-only path; there must be no second legacy sender.
    assert 'notifyHostToHostManagement(' not in s
    imports = '\n'.join(re.findall(r'^import .*;', s, re.M))
    constants = '\n'.join(re.findall(r'^    private static final String [^;]+;', s, re.M))
    names = ('processSave','shouldNotifyCompanyChange','notifyCompanyChange','companySmsAddress','logCompanyNotification',
             'extractSelectedApiCodes','isEnableAction','isEditAction','isDisableAction','getEventId','getActivityId','normalize','isBlank','isApprovedExecution')
    body = '\n'.join(method(s, name) for name in names).replace('new Date()', 'null')
    write('HostToHostManagement.java','package com.ofss.digx.cz.bea.app.hosttohost.service;\n'+imports+'''
public class HostToHostManagement extends AbstractApplication {
 private static final Logger logger=Logger.getLogger("1288-test");
 private static final fixture.Bank.Format formatter=new fixture.Bank.Format();
 private Set<String> fetchSelectedApiCodes(String id){return new HashSet<>(fixture.Bank.oldApis);}
 private void validateRequestForSave(SessionContext c,HostToHostManagementDTO r,String a,boolean approved){}
 private void logSaveDecision(HostToHostManagementDTO r,String a,boolean approved,String ref){}
 private String executeApprovedSave(SessionContext c,HostToHostManagementDTO r,String a){
  if(fixture.Bank.writeFailure)throw new IllegalStateException("TEST_SAVE_FAILURE");fixture.Bank.writes++;
  fixture.Bank.oldApis=extractSelectedApiCodes(r);fixture.Bank.status="DISABLE".equals(a)?"DISABLE":"ENABLE";return "REF-1288";}
 private void createRequestSnapshotForCurrentTransaction(HostToHostManagementDTO r,String a,String ref,SessionContext c){fixture.Bank.snapshots++;}
 private void populateSaveResponse(HostToHostManagementResponseDTO response,HostToHostManagementDTO r,String ref){}
 public void testSave(SessionContext c,HostToHostManagementDTO r,String action) throws Exception {
  processSave(c,r,new HostToHostManagementResponseDTO(),action,isApprovedExecution(),"REF-1288");}
'''+constants+'\n'+body+'}')
    write('com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java', 'package com.ofss.digx.cz.bea.app.logger;public class BeaSystemOut {public static void println(Object o){} public static void println(String o){} public static void printErr(Object o){}}')
    write('com/ofss/digx/extxface/extxface/ExtxfaceAdapterFactory.java','''package com.ofss.digx.extxface.extxface;
public class ExtxfaceAdapterFactory {public static ExtxfaceAdapterFactory getInstance(){return new ExtxfaceAdapterFactory();}public <T>T getAdapter(Class<T> type,String op,com.ofss.fc.enumeration.DeterminantType determinant){return fixture.Bank.network(type);}}
''')
    bean('com.ofss.digx.cz.bea.domain.emailmng', 'CountryCodeKey', {'Id':'String'})
    write('com/ofss/digx/cz/bea/domain/emailmng/CountryCode.java','''package com.ofss.digx.cz.bea.domain.emailmng;
public class CountryCode {private String country;public String getMobile_code(){return country;}public CountryCode read(CountryCodeKey key) throws com.ofss.digx.infra.exceptions.Exception{fixture.Bank.countryReads++;country="852";return this;}}
''')
    bean('com.ofss.digx.cz.bea.domain.emailmng', 'EmailMNG',
         {**{k:'String' for k in ('RecipientId','MessageBody','Subject','CustomerId','PartyId','ActivityId','ActionId','EventId','CodActDataId','TxnType','OrgTxnRefNO','Alert_type','ResponseStatus')},'LastUpdatedDate':'com.ofss.fc.datatype.Date','Key':'EmailMNGKey'},
         'public static EmailMNG last;public void create(EmailMNG m) throws com.ofss.digx.infra.exceptions.Exception{last=m;}public void update(EmailMNG m) throws com.ofss.digx.infra.exceptions.Exception{last=m;}')
    write('com/ofss/digx/cz/bea/app/common/util/CZCommonUtils.java','''package com.ofss.digx.cz.bea.app.common.util;
public class CZCommonUtils {public static String getFormatDate(com.ofss.fc.datatype.Date d){return "fixed";}
public static java.util.List<String> sepChar(String s,String d){return java.util.Collections.singletonList(s);}
public static String maskName(java.util.List<String> names,int i){return "Masked Company";}}''')
    source = next(PROJECTS.rglob('SMSDispatcher.java')).read_text().replace('new Date(new java.util.Date())', 'null')
    imports = '\n'.join(re.findall(r'^import .*;', source, re.M))
    names = ('dispatchMNGSms','getCurrentDateAndTime','ShortString','buildMNGrequest','populateDomainObject',
             'convertToDispatchResult','generateHash','bytesToHex')
    write('SMSDispatcher.java', 'package com.ofss.digx.cz.bea.domain.service.dispatch;\n' + imports + '''
public class SMSDispatcher {
 private final java.util.logging.Logger logger=java.util.logging.Logger.getLogger("test");
 private final fixture.Bank.Format formatter=new fixture.Bank.Format();
 public SessionContext getSessionContext() throws FatalException{return fixture.Bank.context;}
 public DispatchResultDTO test(AlertRequestDTO r,IDispatchData d,String body){return dispatchMNGSms(r,d,body);}
''' + '\n'.join(method(source, n, '\t') for n in names) + '}')
    sources = [p for name in ('UserProfUpdateActivityLogDTO.java','HostToHostManagementDTO.java',
                               'HostToHostManagementResponseDTO.java','HostToHostApiAuthorizationDTO.java')
               for p in PROJECTS.rglob(name)]
    result = subprocess.run([str(JDK/'javac'), '--release', '8', '-proc:none', '-cp', CP, '-d', tmp,
                             *fixtures, *map(str,sources), str(Path(__file__).with_name('Hth1288NotificationTest.java'))])
    if result.returncode: raise SystemExit(result.returncode)
    result = subprocess.run([str(JDK/'java'), '-cp', tmp + os.pathsep + CP,
                             'com.ofss.digx.cz.bea.app.hosttohost.service.Hth1288NotificationTest'])
    raise SystemExit(result.returncode)
