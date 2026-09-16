"""Compile the 851 production classes/shared hooks against current repository dependencies."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
projects = root / 'consulting/middleware/projects'
java = Path(os.environ['JAVA_HOME']) / 'bin/javac'
cp = os.pathsep.join([str(root / 'devtools/backend-compile/build/classes/java/main')] +
                    [str(p) for p in (root / 'consulting/middleware/lib').rglob('*.jar')])
files = list(projects.rglob('HthProfileApprover*.java')) + list(projects.rglob('UserProfUpdateActivityLogDTO.java'))
for name in ('UserExtensionData.java',):
    files.extend(p for p in (projects / 'module').rglob(name)
                 if name != 'UserExtensionData.java' or '/app/sms/service/user/' in str(p))
with tempfile.TemporaryDirectory(prefix='hth-contact-compile-') as output:
    code = subprocess.run([str(java), '-proc:none', '--release', '8', '-cp', cp, '-d', output,
                           *map(str, files)], check=False).returncode
    if code:
        raise SystemExit(code)
print('PASS: narrow 851 module-local helper and UserExtensionData compile against real dependencies (Java 8 target)')
