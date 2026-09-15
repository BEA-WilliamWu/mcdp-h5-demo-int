"""Compile the 851/1216 production classes/shared hooks against current repository dependencies."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
projects = root / 'consulting/middleware/projects'
java = Path(os.environ['JAVA_HOME']) / 'bin/javac'
cp = os.pathsep.join([str(root / 'devtools/backend-compile/build/classes/java/main')] +
                    [str(p) for p in (root / 'consulting/middleware/lib').rglob('*.jar')])
files = list(projects.rglob('Hth*Contact*.java')) + list(projects.rglob('HthUserAccess*Notification*.java')) + list(projects.rglob('HthUserAccessActivityLogDTO.java')) + list(projects.rglob('HostToHostUserAccess.java'))
for name in ('UserExtensionData.java', 'EmailDispatcher.java', 'SMSDispatcher.java', 'BatchExecutionScheduler.java'):
    files.extend(p for p in (projects / 'module').rglob(name)
                 if name != 'UserExtensionData.java' or '/app/sms/service/user/' in str(p))
with tempfile.TemporaryDirectory(prefix='hth-contact-compile-') as output:
    code = subprocess.run([str(java), '-proc:none', '--release', '8', '-cp', cp, '-d', output,
                           *map(str, files)], check=False).returncode
    if code:
        raise SystemExit(code)
for variant in ('', 'UAT', 'PRD'):
    source = root / 'consulting/middleware/batchJobs' / variant
    with tempfile.TemporaryDirectory(prefix='hth-contact-batch-') as output:
        code = subprocess.run([str(java), '-proc:none', '--release', '8', '-cp', cp,
                               '-sourcepath', str(source), '-d', output,
                               str(source / 'inboundbatchprocessor/ValidateAndSendBounceNotify.java')],
                              check=False).returncode
        if code:
            raise SystemExit(code)
print('PASS: root/UAT/PRD bounce batch sources compile')
print('PASS: 851/1216 production classes and shared hooks compile against real repository dependencies (Java 8 target)')
