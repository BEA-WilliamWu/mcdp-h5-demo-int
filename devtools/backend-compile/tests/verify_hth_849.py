"""Compile production 849 paths and run behavioral contracts; Oracle/WebLogic remain UAT checks."""
from pathlib import Path
import os,subprocess,tempfile,re
root=Path(__file__).resolve().parents[3];projects=root/'consulting/middleware/projects'
# Architecture contract: audit has no MTB callback; common exposes data and interface only.
common=projects/'common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common'
assert sorted(p.name for p in (common/'mtb').glob('*.java')) == ['HthCRMInputData.java','HthChannelSupport.java','IHthMtbAdapter.java']
assert 'HthMtb' not in (common/'audit/HthOnboardingAudit.java').read_text()
for path in (common/'mtb').glob('*.java'):
    assert all(token not in path.read_text() for token in ('weblogic.', 'das.orm', 'ConfigurationFactory', 'import com.ofss.digx.cz.bea.app.hosttohost'))
for name in ('HthMtbApproval.java','HthUserMtbScope.java'):
    assert 'app.hosttohost.mtb' not in next(projects.rglob(name)).read_text()
cp=os.pathsep.join([str(root/'devtools/backend-compile/build/classes/java/main')]+[str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar')])
jdk=Path(os.environ['JAVA_HOME'])/'bin'
files=list(projects.rglob('HthCRM*.java'))+list(projects.rglob('HthChannelSupport.java'))+list(projects.rglob('IHthMtbAdapter.java'))+list(projects.rglob('HthUserMtbScope.java'))+list(projects.rglob('HthMtb*.java'))+list(projects.rglob('LocalHthCRMRepositoryAdapter.java'))
for name in ['HthUserAccessAudit.java','HthUserAccessNotification.java','UserManagementActivityLogDTO.java','HthOnboardingAudit.java','HostToHostUserAccess.java','HostToHostManagement.java','HostToHostApiPassword.java','EligibleAccountDTO.java',
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
    factory=Path(out)/'AdapterFactoryConfigurator.java'
    factory.write_text('''package com.ofss.digx.app.adapter;
public class AdapterFactoryConfigurator {
 public static int lookups;
 public static boolean fail;
 public static boolean missingImplementation;
 public static com.ofss.digx.cz.bea.common.mtb.HthCRMInputData last;
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public IAdapterFactory getAdapterFactory(String key){
  lookups++;
  if(missingImplementation)throw new NoClassDefFoundError("injected missing HTH implementation");
  if(fail)throw new IllegalStateException("injected adapter failure");
  if(!com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter.FACTORY.equals(key))throw new AssertionError(key);
  return new com.ofss.digx.app.adapter.AdapterFactory(){
   public Object getAdapter(String name,com.ofss.digx.datatype.NameValuePair[] values){return getAdapter(name);}
   public Object getAdapter(String name){
    final com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter delegate=(com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter)
     com.ofss.digx.cz.bea.app.hosttohost.mtb.HthMtbAdapterFactory.getInstance().getAdapter(name);
    if(delegate==null)throw new AssertionError(name);
    return new com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter(){
     public void collectApproval(com.ofss.fc.app.context.SessionContext context,
       com.ofss.digx.framework.domain.transaction.Transaction transaction,String action){delegate.collectApproval(context,transaction,action);}
     public void collect(com.ofss.digx.cz.bea.common.mtb.HthCRMInputData value){last=value;delegate.collect(value);}
    };
   }
  };
 }
}''')
    files += [factory, Path(__file__).with_name('HthMtbBoundaryTest.java')]
    subprocess.run([str(jdk/'javac'),'--release','8','-proc:none','-cp',cp,'-d',out,*map(str,dict.fromkeys(files))],check=False).check_returncode()
    for main in ('com.ofss.digx.cz.bea.app.hosttohost.mtb.HthMtbTest','HthOnboardingAuditTest','com.ofss.digx.cz.bea.app.sms.service.user.HthMtbBoundaryTest','com.ofss.digx.cz.bea.app.approval.service.transaction.HthMtbApprovalTest'):
        subprocess.run([str(jdk/'java'),'-Djava.util.prefs.userRoot='+out+'/prefs','-cp',out+os.pathsep+cp,main],check=False).check_returncode()
    if os.environ.get('H2_JAR'):
        schema=(root/'consulting/db/branch_change_history/20260923_HTH_MTB_849/1_HTH_MTB_849.sql').read_text().split("q'~",1)[1].split("~'",1)[0]
        schema=re.sub(r'VARCHAR2\((\d+) CHAR\)',r'VARCHAR(\1)',schema)
        ddl=Path(out)/'schema.sql';ddl.write_text(schema)
        subprocess.run([str(jdk/'java'),'-cp',out+os.pathsep+cp+os.pathsep+os.environ['H2_JAR'],
            'com.ofss.digx.cz.bea.app.hosttohost.mtb.HthMtbJdbcTest',str(ddl)],check=False).check_returncode()
    else:
        print('SKIP real database tests: set H2_JAR (Oracle/WebLogic require UAT regardless).')
print('PASS: production compilation and 849 / existing 791 regression tests')
