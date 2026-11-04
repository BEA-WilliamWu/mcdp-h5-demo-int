"""Compile complete HTH production sources, including both approval notification call sites.

Use --service-source to check a delivery/build copy instead of the repository service.
Compiles actual source without extracted methods or test replacements; existing compiled
classes and SDK JARs supply dependencies outside the HTH module. No server is contacted.
"""
from pathlib import Path
import argparse
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
projects = root / 'consulting/middleware/projects'
module = projects / 'module/com.ofss.digx.cz.bea.module.hosttohost/src'
service = module / 'com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--service-source', type=Path, default=service)
args = parser.parse_args()
if not args.service_source.is_file():
    parser.error('Service source does not exist: ' + str(args.service_source))

files = sorted(module.rglob('*.java'))
files += sorted((projects / 'common/com.ofss.digx.cz.bea.app.xface/src'
                 / 'com/ofss/digx/cz/bea/app/hosttohost/dto').glob('*.java'))
for name in ('HthOnboardingAudit.java', 'HthApiCredentialWriteException.java',
             'UserManagementActivityLogDTO.java'):
    files += sorted(projects.rglob(name))
files = [args.service_source.resolve() if p == service else p for p in files]
classpath = os.pathsep.join(
    [str(root / 'devtools/backend-compile/build/classes/java/main')]
    + [str(p) for p in sorted((root / 'consulting/middleware/lib').rglob('*.jar'))])

with tempfile.TemporaryDirectory(prefix='hth880-full-compile-') as output:
    result = subprocess.run([
        str(Path(os.environ['JAVA_HOME']) / 'bin/javac'), '-proc:none', '--release', '8',
        '-encoding', 'UTF-8', '-sourcepath', output, '-cp', classpath, '-d', output,
        *map(str, files)], check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
print('PASS: {} complete production sources compile for Java 8; no method extraction or test replacements'.format(len(files)))
print('NOTE: Uses existing compiled dependencies/SDK JARs; not a clean full backend build or UAT delivery test')
