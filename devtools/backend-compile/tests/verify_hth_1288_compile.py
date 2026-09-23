"""Compile the changed 1288 production class with current DTO source and repository dependencies."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
projects = root / 'consulting/middleware/projects'
cp = os.pathsep.join([str(root / 'devtools/backend-compile/build/classes/java/main')] +
                     [str(p) for p in (root / 'consulting/middleware/lib').rglob('*.jar')])
files = [p for name in ('HostToHostManagement.java', 'EligibleAccountDTO.java')
         for p in projects.rglob(name) if '/appx/' not in str(p)]
files += list((projects/'common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common/mtb').glob('*.java'))
files += list((projects/'module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/mtb').glob('*.java')) + list(projects.rglob('LocalHthMtbRepositoryAdapter.java')) + list(projects.rglob('HthUserMtbScope.java'))
with tempfile.TemporaryDirectory(prefix='hth1288-compile-') as output:
    result = subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/javac'),
                             '-proc:none', '--release', '8', '-cp', cp, '-d', output, *map(str, files)])
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS: 1288 production service compiles against repository Java 8 APIs')
