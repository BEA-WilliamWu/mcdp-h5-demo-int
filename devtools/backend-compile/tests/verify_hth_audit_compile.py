"""Compile 791 production hooks against the repository's actual Java 8 APIs."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[3]
projects=root/'consulting/middleware/projects'
cp=os.pathsep.join([str(root/'devtools/backend-compile/build/classes/java/main')]+[str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar')])
files=[]
for name in ('HthOnboardingAudit.java','HthUserAccessAudit.java','HthUserAccessNotification.java','HostToHostUserAccess.java','HostToHostApiPassword.java','CZAsyncAuditHandler.java','CZUserExtensionDataExt.java'):
    files.extend(projects.rglob(name))
files += [p for p in projects.rglob('UserExtensionData.java') if '/app/sms/service/user/' in str(p)]
files += list(projects.rglob('HthApiCredentialWriteException.java')) + list(projects.rglob('IHthApiPassword*.java')) + list(projects.rglob('LocalHthApiPassword*.java'))
files += list(projects.rglob('HthApiPassword*.java')) + list(projects.rglob('HthUserAccessActivityLogDTO.java')) + list(projects.rglob('HostToHostApiPassword*DTO.java'))
files += list(projects.rglob('Hth*Notification*.java')) + list(projects.rglob('Hth*Contact*.java'))
with tempfile.TemporaryDirectory(prefix='hth-audit-compile-') as output:
    result = subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-proc:none','--release','8','-cp',cp,'-d',output,*map(str,dict.fromkeys(files))],check=False)
    if result.returncode: raise SystemExit(result.returncode)
print('PASS: 791 audit helper and all production/shared hooks compile (Java 8 target)')
