"""Compile the 1216 HTH service/helper with actual repository dependencies (Java 8)."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[3]
projects=root/'consulting/middleware/projects'
cp=os.pathsep.join([str(root/'devtools/backend-compile/build/classes/java/main')]+[str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar')])
files=[]
for name in ('HostToHostUserAccess.java','HthUserAccessNotification.java','UserManagementActivityLogDTO.java',
             'HthOnboardingAudit.java','HthUserAccessAudit.java'):
    files.extend(p for p in projects.rglob(name) if '/appx/' not in str(p))
files += list((projects/'common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common/mtb').glob('*.java'))
with tempfile.TemporaryDirectory(prefix='hth1216-compile-') as output:
    result = subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-proc:none','--release','8','-cp',cp,'-d',output,*map(str,files)],check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS: 1216 production service/helper compile against repository Java 8 APIs')
