"""Run session-bound SQL contracts in isolation from the OBDX application bootstrap.

The SDK repository superclass requires deployed configuration. This runner compiles
only the unchanged Session methods into temporary harness classes; production
inheritance, factory resolution and Oracle/JTA integration require environment tests.
Set JAVA_HOME to a JDK capable of compiling with --release 8.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
ADAPTERS = ROOT / ('consulting/middleware/projects/module/'
    'com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/'
    'domain/hosttohost/entity/repository/adapter')
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
JARS = os.pathsep.join(str(p) for p in sorted(
    (ROOT / 'consulting/middleware/lib').rglob('*.jar')))
with tempfile.TemporaryDirectory(prefix='hth-repository-contract-') as temporary:
    work = Path(temporary)
    sources = []
    for kind in ('Code', 'Credential', 'State', 'Operation'):
        name = 'LocalHthApiPassword' + kind + 'RepositoryAdapter'
        source = (ADAPTERS / (name + '.java')).read_text()
        methods = re.findall(
            r'  public [^\n]+\(Session session[^\n]+ \{\n.*?\n  \}',
            source, re.S)
        assert methods, name
        target = work / (name + '.java')
        target.write_text(
            'package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;\n'
            'import com.ofss.digx.infra.exceptions.Exception;\n'
            'import com.ofss.fc.infra.das.orm.Session;\n'
            'import com.ofss.fc.infra.das.orm.Query;\n'
            'import java.util.List;\n'
            'public class ' + name + ' {\n'
            '  public static ' + name + ' getInstance() { return new ' + name + '(); }\n'
            + '\n'.join(methods) + '\n}\n')
        sources.append(str(target))
    sources.append(str(Path(__file__).with_name('HthApiPasswordRepositoryTest.java')))
    subprocess.run([str(JDK / 'javac'), '-proc:none', '--release', '8',
                    '-cp', JARS, '-d', str(work), *sources], check=True)
    subprocess.run([str(JDK / 'java'), '-cp', str(work) + os.pathsep + JARS,
                    'HthApiPasswordRepositoryTest'], check=True)
