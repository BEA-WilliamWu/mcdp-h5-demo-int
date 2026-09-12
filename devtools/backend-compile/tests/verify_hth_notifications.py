"""Exercise production notification method bodies without OBDX bootstrap or live dispatch.

The harness replaces infrastructure types with in-memory doubles. It verifies routing and
ownership resolution, not Oracle/JTA or delivery. JAVA_HOME must support --release 8.
"""
from pathlib import Path
import os, re, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[3]
SERVICE = ROOT / 'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java'
s = SERVICE.read_text()
def method(name):
    m = re.search(r'  private [^\n]*\b' + name + r'\(', s)
    assert m, name
    a = s.index('{', m.start()); depth = 1; i = a + 1
    while depth:
        depth += (s[i] == '{') - (s[i] == '}'); i += 1
    return s[m.start():i]
names = ['notifySetupSuccess', 'notifyResetSuccess', 'notifyPasswordSuccess', 'addNotificationDestination', 'notifyEmailRecipients',
         'publishEmail', 'readNotificationUser', 'normalize', 'canonicalUser', 'isBlank']
methods = '\n'.join(method(n) for n in names)
methods = methods.replace('com.ofss.digx.domain.sms.entity.user.User', 'TestUser')
methods = methods.replace('com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData', 'Extension')
# These boundaries are security/transaction contracts around the exercised methods.
sms = next((ROOT/'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src').rglob('app/sms/service/user/UserExtensionData.java')).read_text()
assert 'String.class, String.class).invoke(service, dto.getHthApiPasswordCodeId().trim(),' in sms
assert 'sessionContext.getUserId(), dto.getCdcNo(), dto.getUserID()' in sms
assert 'catch (ClassNotFoundException' not in sms[sms.index('private void activateHthApiPasswordCode'):sms.index('private String generateHash')]
assert s.index('completeDatabase(identity.partyId') < s.index('notifySetupSuccess(sessionContext, identity)')
assert 'if (previous == null || !"SUCCESS".equals(previous.status))' in s
constants = '\n'.join(re.search(r'  private static final String ' + name + r' = [^;]+;', s).group(0)
                      for name in ['SETUP_SERVICE', 'RESET_SERVICE', 'SETUP_EVENT', 'RESET_EVENT'])
preamble = r'''
import java.util.*;
import java.util.logging.*;
class Base {
  static List<String> events = new ArrayList<String>();
  static List<List<String>> destinations = new ArrayList<List<String>>();
  static String failEvent;
  void registerActivityAndGenerateEvent(SessionContext c, String a, String e, Date d,
      HthApiPasswordActivityLogDTO log) throws Exception {
    if(e.equals(failEvent)) throw new Exception("dispatch failure");
    events.add(e); List<String> ds=new ArrayList<String>();
    for(NotificationDetail n:log.details) ds.add(n.destination+":"+n.address);
    destinations.add(ds);
  }
}
public class HthNotificationHarness extends Base {
  static final Logger LOGGER=Logger.getLogger("test");
  static final String THIS_COMPONENT_NAME="com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword";
  static List<Extension> owners=new ArrayList<Extension>();
  static Map<String,TestUser> users=new HashMap<String,TestUser>();
  static String readKey, companyEmail;
  static boolean companyFailure;
  static int closes;
  static class SessionContext {}
  static class Identity { String partyId="P", userId="ALICE"; }
  static enum DestinationType { EMAIL, SMS }
  static enum SubscriberType { EXTERNAL }
  static class NotificationDetail {
    DestinationType destination; String address;
    void setDestination(DestinationType d){destination=d;}
    void setDispatchAddress(String a){address=a;}
    void setRecipientId(String p){}
    void setRecipientType(String p){}
  }
  static class HthApiPasswordActivityLogDTO {
    NotificationDetail[] details;
    void setNotificationDetails(NotificationDetail[] d){details=d;}
    void setCustomerId(String p){}
    void setHthApiPasswordPartyId(String p){}
    void setHthApiPasswordUserName(String p){}
  }
  static class UserKey { String id; void setUserId(String id){this.id=id;} }
  static class TestUser {
    String email,mobile;
    TestUser(){}
    TestUser(String e,String m){email=e;mobile=m;}
    String getEmailId(){return email;} String getMobileNumber(){return mobile;}
    TestUser read(UserKey k){readKey=k.id;return users.get(k.id);}
  }
  static class ExtensionKey { String id; ExtensionKey(String s){id=s;} String getUserExtensionKey(){return id;} }
  static class Extension {
    String party; ExtensionKey key;
    Extension(){} Extension(String p,String id){party=p;key=new ExtensionKey(id);}
    void setCdcNo(String p){party=p;} String getCdcNo(){return party;}
    ExtensionKey getUserExtensionDataKey(){return key;}
    List<Extension> listUsersByParty(Extension f){return owners;}
  }
  static class SessionLease { void close(boolean c){closes++;} }
  SessionLease open(boolean w){return new SessionLease();}
  static class CZPartyPreferenceDTO { String getOfficeEmailId(){return companyEmail;} }
  static class IUserExtensionAdapter {
    CZPartyPreferenceDTO getPartyPreferences(String p) throws Exception {
      if(companyFailure)throw new Exception("party lookup failure");return new CZPartyPreferenceDTO();
    }
  }
  static class IAdapterFactory { Object getAdapter(String s){return new IUserExtensionAdapter();} }
  static class AdapterFactoryConfigurator {
    static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
    IAdapterFactory getAdapterFactory(String s){return new IAdapterFactory();}
  }
  static class CommonAdapterFactoryConstants { static String USER_EXTENSION_ADAPTER_FACTORY="factory"; }
  static class CommonAdapterConstants { static String USER_EXTENSION_ADAPTER="adapter"; }
'''
# Base refers to nested types explicitly, just as the production superclass accepts framework DTOs.
preamble = preamble.replace('void registerActivityAndGenerateEvent(SessionContext c', 'void registerActivityAndGenerateEvent(HthNotificationHarness.SessionContext c').replace('      HthApiPasswordActivityLogDTO log)', '      HthNotificationHarness.HthApiPasswordActivityLogDTO log)').replace('for(NotificationDetail n:', 'for(HthNotificationHarness.NotificationDetail n:')
tests=r'''
  static void reset(){events.clear();destinations.clear();owners.clear();users.clear();readKey=null;
    companyEmail="office@example.test";companyFailure=false;failEvent=null;closes=0;}
  static void owner(String id,String email,String mobile){owners.add(new Extension("P",id));users.put(id,new TestUser(email,mobile));}
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  void code(){notifyEmailRecipients(null,"approve","USER","COMPANY","P","ALICE",new HthApiPasswordActivityLogDTO());}
  void success(){notifyPasswordSuccess(null,new Identity(),"reset","RESET");}
  public static void main(String[] args) throws Exception {
    LOGGER.setLevel(Level.OFF);HthNotificationHarness h=new HthNotificationHarness();
    reset();owner("ALICE@P"," alice@example.test ","123");h.code();
    check("ALICE@P".equals(readKey),"legacy key retained");check(events.size()==2,"two distinct emails");
    check(destinations.get(0).equals(Arrays.asList("EMAIL:alice@example.test")),"email only for Code");check(closes==1,"session lease closed");
    reset();owner("ALICE","Office@Example.test", "123");h.code();check(events.size()==1,"case-insensitive dedup");
    reset();owner("ALICE",null,"123");h.code();check(events.equals(Arrays.asList("COMPANY")),"no SMS fallback");
    reset();owner("ALICE","u@example.test","123");companyFailure=true;h.code();check(events.equals(Arrays.asList("USER")),"company lookup failure isolated");
    reset();owner("ALICE","u@example.test","123");failEvent="USER";h.code();check(events.equals(Arrays.asList("COMPANY")),"user publication failure isolated");
    reset();owners.add(new Extension("OTHER","ALICE"));users.put("ALICE",new TestUser("wrong@example.test","1"));h.code();check(readKey==null,"other party rejected");
    reset();owner("ALICE","a@example.test","1");owner("ALICE@P","b@example.test","2");h.code();check(readKey==null,"ambiguous owners rejected");check(events.equals(Arrays.asList("COMPANY")),"company independent of user lookup");
    reset();owner("ALICE@P","u@example.test","123");h.success();check(events.equals(Arrays.asList("RESET")),"one success event");check(destinations.get(0).equals(Arrays.asList("EMAIL:u@example.test","SMS:123")),"success uses both channels");
    reset();owner("ALICE",null,"123");h.success();check(destinations.get(0).equals(Arrays.asList("SMS:123")),"SMS without email");
    reset();owner("ALICE","u@example.test",null);h.success();check(destinations.get(0).equals(Arrays.asList("EMAIL:u@example.test")),"email without SMS");
    reset();owner("ALICE"," ",null);h.success();check(events.isEmpty(),"missing contacts skipped");
    reset();owner("ALICE","u@example.test","123");failEvent="RESET";h.success();check(events.isEmpty(),"failure does not escape");
    for (boolean setup : new boolean[]{true, false}) {
      String event = setup ? SETUP_EVENT : RESET_EVENT;
      reset();owner("ALICE@P","u@example.test","123");
      if (setup) h.notifySetupSuccess(null,new Identity()); else h.notifyResetSuccess(null,new Identity());
      check(events.equals(Arrays.asList(event)),"actual setup/reset event");
      check(destinations.get(0).equals(Arrays.asList("EMAIL:u@example.test","SMS:123")),"both setup/reset channels");
      reset();owner("ALICE@P","u@example.test","123");failEvent=event;
      if (setup) h.notifySetupSuccess(null,new Identity()); else h.notifyResetSuccess(null,new Identity());
      check(events.isEmpty(),"setup/reset publication failure contained");
    }
    System.out.println("PASS: notification routing, email dedup, owner keys, party isolation, missing contacts and failure containment");
  }
}
'''
with tempfile.TemporaryDirectory(prefix='hth-notification-') as temp:
    p=Path(temp)/'HthNotificationHarness.java';p.write_text(preamble+constants+methods+tests)
    java=Path(os.environ['JAVA_HOME'])/'bin'
    subprocess.run([str(java/'javac'),'--release','8','-d',temp,str(p)],check=True)
    subprocess.run([str(java/'java'),'-cp',temp,'HthNotificationHarness'],check=True)
# Validate event/template wiring in the final SQL without connecting to Oracle.
sql=(ROOT/'consulting/db/branch_change_history/20260907_HTH_API_Password/6_HTH_API_Password_Notification.sql').read_text()
assert sql.count('INSERT INTO DIGX_EP_MSG_TMPL_B')==18
assert sql.count('INSERT INTO DIGX_EP_EVT_REC_B')==18
for event in ['HTH_API_PASSWORD_SETUP_SUCCESS','HTH_API_PASSWORD_RESET_SUCCESS',
              'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT','HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT']:
    assert event in sql
placeholders=set(re.findall(r'#(\w+)#',sql))
dto=next((ROOT/'consulting/middleware/projects/common/com.ofss.digx.cz.bea.app.xface/src').rglob('HthApiPasswordActivityLogDTO.java')).read_text()
for field in placeholders:assert 'get'+field[0].upper()+field[1:]+'()' in dto,field
assert not any(re.search(r'(?im)^\s*'+cmd+r'\b',sql) for cmd in ['DEFINE','UNDEFINE','WHENEVER'])
print('PASS: 4 events, 18 recipient/template configurations; template placeholders resolve; no SQL*Plus directives')

# Each recipient must select an existing template with the same destination, and each
# event must have exactly its intended language/channel matrix.
values = re.findall(r"INSERT INTO (DIGX_EP_\w+_B) \([^;]+?\)\s*VALUES \((.*?)\);", sql, re.S)
def literals(text):
    return [value.replace("''", "'") for value in re.findall(r"'((?:''|[^'])*)'", text)]
templates = {}
recipients = []
for table, body in values:
    strings = literals(body)
    if table == 'DIGX_EP_MSG_TMPL_B':
        assert strings[0] not in templates
        templates[strings[0]] = strings[1]
        assert len(strings[4].encode('utf-8')) <= 4000
    elif table == 'DIGX_EP_EVT_REC_B':
        recipients.append(strings)
assert len(templates) == len(recipients) == 18
for row in recipients:
    assert templates[row[3]] == row[4]
for event, count in [('HTH_API_PASSWORD_SETUP_SUCCESS', 6), ('HTH_API_PASSWORD_RESET_SUCCESS', 6),
                     ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT', 3),
                     ('HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT', 3)]:
    rows = [row for row in recipients if row[1] == event]
    assert len(rows) == count
    channels = ['EMAIL', 'SMS'] if count == 6 else ['EMAIL']
    for channel in channels:
        for locale in ['en', 'zh-Hans-CN', 'zh-Hant']:
            assert sum(row[4] == channel and locale in row for row in rows) == 1
print('PASS: every event has exact language/channel coverage; no orphan or mismatched templates')
