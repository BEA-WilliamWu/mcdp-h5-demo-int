"""2110 recipient/lifecycle regressions using production helpers and real SDK DTOs.

Only contact reads, registration, clock and server configuration are fixtures.
Includes Java 8 target compile of both changed shared hooks, SDK post-commit ordering,
BCO notification-method preservation and exact raw v81 template text checks.
Oracle rerun, WebLogic/OMB execution and MNG delivery still require SIT/UAT.
"""
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
CP = os.pathsep.join([str(ROOT / 'consulting/middleware/lib/OBDX_FW_LIB' / ('jackson-' + k + '-2.11.0.jar'))
                     for k in ('annotations', 'core', 'databind')]
                    + [str(ROOT / 'devtools/backend-compile/build/classes/java/main')]
                    + [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])

def run(args, **options):
    result = subprocess.run(args, check=False, **options)
    if result.returncode:
        raise SystemExit(result.returncode)
    return result

sources = [next(PROJECTS.rglob(name)) for name in (
    'HthContactUpdateNotification.java', 'HthContactUpdateApprovalLifecycle.java',
    'CZTransactionExt.java', 'CZTransactionExtFunc.java', 'CZAdhocBulkpaymentPayout.java',
    'HttpUtils.java', 'UserExtensionDataDTO.java', 'HthApiPasswordApprovalLifecycle.java',
    'HthProfileApproverNotification.java', 'HthOnboardingAudit.java', 'UserProfUpdateActivityLogDTO.java',
    'UserManagementActivityLogDTO.java', 'CZUserExtensionDataExt.java', 'CZCommonUtils.java')]
user = next(p for p in PROJECTS.rglob('UserExtensionData.java') if '/app/sms/service/user/' in str(p))
sources.append(user)
sources += [Path(__file__).with_name(name) for name in ('Hth2110NotificationTest.java', 'Hth2110ApprovalLifecycleTest.java')]
bytecode = run([str(JDK / 'javap'), '-p', '-c', '-classpath', CP,
               'com.ofss.digx.app.approval.service.transaction.Transaction'], capture_output=True, text=True).stdout
start = bytecode.index('TransactionActionResponse performAction(')
end = bytecode.find('\n  public ', start + 10)
perform = bytecode[start:end]
hook = perform.index('ITransactionExtExecutor.postPerformAction:')
assert perform.index('ServiceInvocationHelper.invokeService:') < perform.rindex('Transaction.commit:', 0, hook)
assert 'DataAccessManager.closeSession:' in perform[hook:]
# The method holding all BCO/single-field route behavior must be byte-for-byte unchanged.
baseline = run(['git', 'show', '4efb66ed:' + str(user.relative_to(ROOT))], cwd=ROOT,
               capture_output=True, text=True).stdout
current = user.read_text()
def legacy_method(source):
    return source[source.index('\tpublic void alertUserProfileUpdate('):source.index('\tpublic void userCreateWelcomeAlert(')]
assert legacy_method(baseline) == legacy_method(current), 'BCO/single-field notification flow changed'
assert current.index('HthContactUpdateNotification.capture(') < current.index('domain.update(domain);', current.index('HthContactUpdateNotification.capture('))
assert 'if (hthBothContacts)' in current and 'HthContactUpdateNotification.defer(hthContactSnapshot)' in current
with tempfile.TemporaryDirectory(prefix='hth-2110-') as directory:
    config = Path(directory) / 'ConfigurationFactory.java'
    config.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 private static final ConfigurationFactory INSTANCE = new ConfigurationFactory();
 public static ConfigurationFactory getInstance(){return INSTANCE;}
 public java.util.prefs.Preferences getRootConfigurations(){return java.util.prefs.Preferences.userRoot().node("test");}
 public java.util.prefs.Preferences getConfigurations(String category){return getRootConfigurations();}
}''')
    # SDK ActivityLog's constructor boots a bank adapter even before any notification.
    # Replace only that constructor layer; retain the real framework fields/NotificationDetail.
    log_base = Path(directory) / 'ActivityLog.java'
    log_base.write_text('''package com.ofss.digx.app.alerts.dto.eventgen;
public class ActivityLog extends com.ofss.fc.xface.ep.dto.ActivityLog {}''')
    production = [p for p in sources if p.name not in ('Hth2110NotificationTest.java', 'Hth2110ApprovalLifecycleTest.java')]
    run([str(JDK / 'javac'), '--release', '8', '-proc:none', '-cp', CP, '-d', directory, *map(str, production)])
    tests = [p for p in sources if p not in production]
    run([str(JDK / 'javac'), '--release', '8', '-proc:none', '-cp', directory + os.pathsep + CP,
         '-d', directory, str(config), str(log_base), *map(str, tests)])
    for main in ('com.ofss.digx.cz.bea.app.sms.service.user.Hth2110NotificationTest',
                 'com.ofss.digx.cz.bea.app.approval.service.transaction.ext.Hth2110ApprovalLifecycleTest'):
        run([str(JDK / 'java'), '-Djava.util.prefs.userRoot=' + directory + '/prefs',
             '-cp', directory + os.pathsep + CP, main])
print('PASS: complete changed hooks compile; real SDK commits before callback; existing BCO/single-field notification method unchanged')

source = ROOT / 'story/notification/2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx'
with ZipFile(source) as archive:
    document = ET.fromstring(archive.read('word/document.xml'))
ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
tables = document.find('w:body', ns).findall('w:tbl', ns)
def cell(table, column):
    element = tables[table].findall('w:tr', ns)[9].findall('w:tc', ns)[column]
    lines = []
    for paragraph in element.findall('w:p', ns):
        content = []
        for node in paragraph.iter():
            local = node.tag.rsplit('}', 1)[-1]
            if local == 't': content.append(node.text or '')
            elif local in ('br', 'cr'): content.append('\n')
            elif local == 'tab': content.append('\t')
        lines.append(''.join(content))
    return '\n'.join(lines).strip()
def parts(text, labels):
    result = {}
    for i, label in enumerate(labels):
        start = text.index(label + '\n') + 2
        end = text.index('\n' + labels[i + 1] + '\n', start) if i + 1 < len(labels) else len(text)
        result[label] = text[start:end].strip()
    return result
sql = (ROOT / 'consulting/db/branch_change_history/20261103_BCOH2H-2110/1_HTH_Contact_Update_Notification.sql').read_text()
subjects = parts(cell(1,3), ['A','B'])
bodies = parts(cell(1,4), ['A','B'])
for letter in ('A','B'):
    block = sql.split("r.route_id = 'EMAIL_" + letter + "' THEN\n",1)[1].split('      ELSIF',1)[0]
    subject = re.search(r"v_subject := q'~(.*?)~';",block,re.S).group(1)
    body = ''.join(re.findall(r"TO_CLOB\(q'~(.*?)~'\)",block,re.S))
    assert subject == subjects[letter], 'Reviewed subject mismatch ' + letter
    assert body == bodies[letter], 'Reviewed raw HTML mismatch ' + letter
for letter in ('A','B','C','D'):
    block = sql.split("r.route_id = 'SMS_" + letter + "' THEN\n",1)[1].split('        END IF;',1)[0]
    for locale,col in (('en',4),('zh-hant',5),('zh-hans-cn',6)):
        value = re.search("l.locale = '"+locale+r"' THEN\s+v_body := TO_CLOB\(q'~(.*?)~'\);",block,re.S).group(1)
        assert value == parts(cell(2,col),['A','B','C','D'])[letter], 'Reviewed SMS mismatch ' + letter + '/' + locale
assert all(len(x.encode())<=4000 for x in re.findall(r"q'~(.*?)~'",sql,re.S)), 'Oracle literal byte limit'
assert 'ROLLBACK TO HTH_2110_CONFIG;' in sql and sql.count('  COMMIT;') == 1
assert "v_skip := v_skip || ',' || e.event_id" in sql
assert 'IF v_count <> 18' in sql
assert "'EXTERNAL',NULL,'M','CZ',l.locale,NULL,'NA',0,NULL,'USER'" in sql
print('PASS: two exact v81 email subjects/bodies; twelve exact EN/TC/SC SMS bodies; template mapping and guarded rerunnable DML contract')
print('NOTE: no live Oracle, approval server or MNG contacted; use deployment verification SQL and UAT cases')
