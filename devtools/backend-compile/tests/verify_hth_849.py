"""Compile production 849 paths and run behavioral contracts; Oracle/WebLogic remain UAT checks."""
from pathlib import Path
import os,subprocess,tempfile,re
root=Path(__file__).resolve().parents[3];projects=root/'consulting/middleware/projects'
cp=os.pathsep.join([str(root/'devtools/backend-compile/build/classes/java/main')]+[str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar')])
jdk=Path(os.environ['JAVA_HOME'])/'bin'
files=list(projects.rglob('HthMtb*.java'))+list(projects.rglob('LocalHthMtbRepositoryAdapter.java'))
for name in ['HthOnboardingAudit.java','HostToHostManagement.java','HostToHostApiPassword.java','EligibleAccountDTO.java',
             'HthApiPasswordTransport.java','HthApiPasswordStorage.java','HthApiCredentialWriteException.java',
             'HostToHostApiPasswordRequestDTO.java','HthApiPasswordOperationRepository.java','HthApiPasswordCodeRepository.java',
             'LocalHthApiPasswordCodeRepositoryAdapter.java','LocalHthApiPasswordOperationRepositoryAdapter.java',
             'IHthApiPasswordCodeRepositoryAdapter.java','IHthApiPasswordOperationRepositoryAdapter.java','CZApprovalWorker.java']:
    files += [p for p in projects.rglob(name) if '/appx/' not in str(p)]
files += [projects/'module/com.ofss.digx.cz.bea.module.approval/src/com/ofss/digx/cz/bea/app/approval/service/transaction/Transaction.java']
files += [Path(__file__).with_name('HthMtbApprovalTest.java'),Path(__file__).with_name('HthMtbJdbcTest.java'),Path(__file__).with_name('HthMtbTest.java'),Path(__file__).with_name('HthOnboardingAuditTest.java')]
with tempfile.TemporaryDirectory(prefix='hth849-test-') as out:
    config=Path(out)/'ConfigurationFactory.java'
    config.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
 public java.util.prefs.Preferences getRootConfigurations(){return java.util.prefs.Preferences.userRoot().node("hth849-tests");}
 public java.util.prefs.Preferences getConfigurations(String category){if("HTHMtbConfiguration".equals(category))System.setProperty("hth849.configReads",String.valueOf(Integer.parseInt(System.getProperty("hth849.configReads","0"))+1));return getRootConfigurations().node(category);}
}''')
    files.append(config)
    session=Path(out)/'SessionDataManager.java'
    session.write_text('package com.ofss.digx.framework.security.handlers;\npublic class SessionDataManager {\n public static SessionDataManager getManager(){return new SessionDataManager();}\n public String getId(){return "SYNTHETIC-SESSION";}\n}')
    files.append(session)
    subprocess.run([str(jdk/'javac'),'--release','8','-proc:none','-cp',cp,'-d',out,*map(str,dict.fromkeys(files))],check=False).check_returncode()
    for main in ('com.ofss.digx.cz.bea.common.mtb.HthMtbTest','HthOnboardingAuditTest','com.ofss.digx.cz.bea.app.approval.service.transaction.HthMtbApprovalTest'):
        subprocess.run([str(jdk/'java'),'-Djava.util.prefs.userRoot='+out+'/prefs','-cp',out+os.pathsep+cp,main],check=False).check_returncode()
    if os.environ.get('H2_JAR'):
        schema=(root/'consulting/db/branch_change_history/20260923_HTH_MTB_849/1_HTH_MTB_849.sql').read_text().split("q'~",1)[1].split("~'",1)[0]
        schema=re.sub(r'VARCHAR2\((\d+) CHAR\)',r'VARCHAR(\1)',schema)
        ddl=Path(out)/'schema.sql';ddl.write_text(schema)
        subprocess.run([str(jdk/'java'),'-cp',out+os.pathsep+cp+os.pathsep+os.environ['H2_JAR'],
            'com.ofss.digx.cz.bea.common.mtb.HthMtbJdbcTest',str(ddl)],check=False).check_returncode()
    else:
        print('SKIP real database tests: set H2_JAR (Oracle/WebLogic require UAT regardless).')
print('PASS: production compilation and 849 / existing 791 regression tests')
