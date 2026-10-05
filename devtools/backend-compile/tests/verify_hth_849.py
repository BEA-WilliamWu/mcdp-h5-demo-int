"""Compile production 849 paths and exercise real EclipseLink/OBDX entity persistence.

Configuration, DataAccessManager bootstrap and the JTA boundary are fixtures. The
actual entity XML, EntityManager, Session, persister and transaction wrappers run
unchanged. H2 checks do not replace Oracle/WebLogic deployment verification.
"""
from pathlib import Path
import os,subprocess,tempfile,re,shutil,sys,xml.etree.ElementTree as ET
def run(command):
    result=subprocess.run(command,check=False)
    if result.returncode: raise SystemExit(result.returncode)

root=Path(__file__).resolve().parents[3];projects=root/'consulting/middleware/projects'
# A preferences-node mock accepts misspelled/unregistered groups. Pin the deployed
# registration here and exercise the real SDK loader/providers in a separate JVM.
registered=[node for node in ET.parse(root/'consulting/config/Preferences.xml').iter('Preference')
            if node.get('name')=='HTHCRMConfiguration']
assert len(registered)==1, 'Missing/duplicate deployed HTHCRMConfiguration registration'
assert registered[0].get('PreferencesProvider')=='com.ofss.digx.infra.config.impl.MultiEntityDBBasedPropProvider'
if os.environ.get('H2_JAR'):
    run([sys.executable,str(Path(__file__).with_name('verify_hth_crm_configuration.py'))])
# Architecture contract: audit has no MTB callback; common exposes data and interface only.
common=projects/'common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common'
assert sorted(p.name for p in (common/'hth').glob('*.java')) == ['HthCRMInputData.java','HthChannelSupport.java','HthOnboardingAudit.java','IHthCRMAdapter.java']
assert 'HthCRM' not in (common/'hth/HthOnboardingAudit.java').read_text()
for path in (common/'hth').glob('*.java'):
    if path.name == 'HthOnboardingAudit.java': continue
    assert all(token not in path.read_text() for token in ('weblogic.', 'das.orm', 'ConfigurationFactory', 'import com.ofss.digx.cz.bea.app.hosttohost'))
for name in ('HthCRMApproval.java','HthUserCRMScope.java'):
    assert 'app.hosttohost.crm' not in next(projects.rglob(name)).read_text()
lib=root/'consulting/middleware/lib'
eclipselink=lib/'OBDX_FW_LIB/eclipselink2.5.2.jar'
assert eclipselink.is_file(), 'The production EclipseLink 2.5.2 library is required'
# The current unchanged BeaParser uses SnakeYAML 2.0's LoaderOptions API.
yaml=lib/'thirdparty/snakeyaml-2.0.jar'
assert yaml.is_file()
cp=os.pathsep.join(map(str,[eclipselink,yaml,root/'devtools/backend-compile/build/classes/java/main',*sorted(lib.rglob('*.jar'))]))
jdk=Path(os.environ['JAVA_HOME'])/'bin'
files=list(projects.rglob('HthCRM*.java'))+list(projects.rglob('HthChannelSupport.java'))+list(projects.rglob('IHthCRMAdapter.java'))+list(projects.rglob('HthUserCRMScope.java'))+list(projects.rglob('LocalHthCRMRepositoryAdapter.java'))
for name in ('CRMConstants.java','CZCommonConstants.java','CZLocaleUtils.java','BeaParser.java','BeaParserClient.java','BeaBrowser.java','BeaBrowserParser.java','BeaOs.java','BeaOsParser.java'):
    files+=list(projects.rglob(name))
files+=list(projects.rglob('CRMEvent3DomainDTO.java'))+list(projects.rglob('CRMEvent3DomainKey.java'))
for name in ['HthUserAccessAudit.java','HthUserAccessNotification.java','UserManagementActivityLogDTO.java','HthOnboardingAudit.java','HostToHostUserAccess.java','HostToHostManagement.java','HostToHostApiPassword.java','EligibleAccountDTO.java',
             'HthApiPasswordTransport.java','HthApiPasswordStorage.java','HthApiCredentialWriteException.java',
             'HostToHostApiPasswordRequestDTO.java','HthApiPasswordOperationRepository.java','HthApiPasswordCodeRepository.java',
             'LocalHthApiPasswordCodeRepositoryAdapter.java','LocalHthApiPasswordOperationRepositoryAdapter.java',
             'IHthApiPasswordCodeRepositoryAdapter.java','IHthApiPasswordOperationRepositoryAdapter.java','CZApprovalWorker.java']:
    files += [p for p in projects.rglob(name) if '/appx/' not in str(p)]
files += [projects/'module/com.ofss.digx.cz.bea.module.approval/src/com/ofss/digx/cz/bea/app/approval/service/transaction/Transaction.java']
files += [Path(__file__).with_name('HthCRMContextTest.java'),Path(__file__).with_name('HthCRMApprovalTest.java'),Path(__file__).with_name('HthCRMOrmTest.java'),Path(__file__).with_name('HthCRMTest.java'),Path(__file__).with_name('HthOnboardingAuditTest.java')]
with tempfile.TemporaryDirectory(prefix='hth849-test-') as out:
    config_root=root/'consulting/config'
    cfg=config_root/'orm/eclipselink/cfg'
    module_cfg=(cfg/'module-cfg.properties').read_text().splitlines()
    assert 'cz-hosttohost.cfg.xml' in module_cfg and 'cz-crm-mapping.cfg.xml' in module_cfg
    hth_mappings=[element.attrib['resource'] for element in ET.parse(cfg/'cz-hosttohost.cfg.xml').iter('mapping')]
    bco_mappings=[element.attrib['resource'] for element in ET.parse(cfg/'cz-crm-mapping.cfg.xml').iter('mapping')]
    hth_mapping='orm/eclipselink/mappings/cz/hosttohost/crm/HthCRMEvent3DomainDTO.orm.xml'
    bco_mapping='orm/eclipselink/mappings/cz/crm/CustomerRelationshipManagement_Event3.orm.xml'
    assert hth_mappings.count(hth_mapping)==1 and bco_mappings.count(bco_mapping)==1
    # Use the unchanged BCO production mapping as the baseline, independently of
    # the HTH schema/field manifest. This catches dropped, renamed or mistyped fields.
    namespace={'orm':'http://www.eclipse.org/eclipselink/xsds/persistence/orm'}
    def columns(resource):
        entity=ET.parse(config_root/resource).getroot().find('orm:entity',namespace)
        mapped={}
        for basic in entity.findall('orm:attributes/orm:basic',namespace):
            column=basic.find('orm:column',namespace).attrib['name'].upper()
            assert column not in mapped, 'Duplicate mapping: '+column
            mapped[column]=(basic.attrib['name'],basic.attrib['attribute-type'])
        mapped['EVENT_ID']=('key.eventId','java.lang.String')
        return mapped
    bco_columns=columns(bco_mapping);hth_columns=columns(hth_mapping)
    assert len(bco_columns)==99 and len(hth_columns)==113
    for column,definition in bco_columns.items():
        assert column in hth_columns, 'Missing BCO baseline column: '+column
        assert hth_columns[column]==definition, 'BCO field/type differs: '+column
    assert set(hth_columns)-set(bco_columns)=={
        'SOURCE_SYSTEM','CHANNEL_TYPE','TARGET_USER_CHANNEL','ACTIVITY_KEY','PHASE',
        'TARGET_USER_ID','RELATIONSHIP_TYPE','SERVICE_ID','TASK_CODE','SOURCE_ACTION_ID',
        'REQUEST_ID','ERROR_CODE','DEDUP_KEY','CREATED_AT'}
    assert sum(kind=='java.math.BigDecimal' for _,kind in hth_columns.values())==5
    # Load the exact deployed mappings together, including the unchanged BCO entity.
    for resource in (hth_mapping,bco_mapping):
        target=Path(out)/resource;target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(config_root/resource,target)
    # The real SDK exception translator formats database errors using these bundles.
    for resource in ('InfoMessages_en.properties','ErrorMessages_en.properties'):
        target=Path(out)/'resources'/resource;target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(root/'consulting/config_core/resources'/resource,target)
    persistence=Path(out)/'META-INF/persistence.xml';persistence.parent.mkdir(parents=True)
    persistence.write_text('''<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="http://java.sun.com/xml/ns/persistence" version="2.0">
 <persistence-unit name="NONXA" transaction-type="RESOURCE_LOCAL">
  <provider>org.eclipse.persistence.jpa.PersistenceProvider</provider>
  <mapping-file>'''+hth_mapping+'''</mapping-file>
  <mapping-file>'''+bco_mapping+'''</mapping-file>
  <exclude-unlisted-classes>true</exclude-unlisted-classes>
  <properties>
   <property name="javax.persistence.jdbc.driver" value="org.h2.Driver"/>
   <property name="javax.persistence.jdbc.url" value="jdbc:h2:mem:crm;MODE=Oracle;DB_CLOSE_DELAY=-1"/>
   <property name="eclipselink.weaving" value="false"/>
   <property name="eclipselink.logging.level" value="OFF"/>
  </properties>
 </persistence-unit>
</persistence>''')
    bridge=Path(out)/'TestOrmAccess.java'
    bridge.write_text('''package com.ofss.fc.infra.das.orm.eclipselink;
import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.das.orm.SessionPersister;
public final class TestOrmAccess {
 public static Session wrap(EntityManager entityManager) {
  entityManager.setFlushMode(FlushModeType.COMMIT);
  return new EclipseLinkEntityManagerWrapper(entityManager);
 }
 public static SessionPersister persister(){return EclipseLinkSessionPersister.getInstance();}
}''')
    manager=Path(out)/'DataAccessManager.java'
    manager.write_text('''package com.ofss.fc.infra.das.orm;
import javax.persistence.EntityManagerFactory;
import com.ofss.fc.infra.das.exception.PersistenceException;
import com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess;
/** Bootstrap only. Mirrors openNewSession not binding a thread session; uses the actual SDK persister. */
public class DataAccessManager {
 private static final DataAccessManager INSTANCE=new DataAccessManager();
 private static final ThreadLocal<Session> CURRENT=new ThreadLocal<Session>();
 public static EntityManagerFactory factory;
 public static int opens,closes,currentLookups;
 public static DataAccessManager getManager(){return INSTANCE;}
 public static DataAccessManager getManager(String name){return INSTANCE;}
 public static void bind(Session session){if(session==null)CURRENT.remove();else CURRENT.set(session);}
 public static Session current(){return CURRENT.get();}
 public boolean isSessionOpen(){return CURRENT.get()!=null;}
 public Session openSession(){return openSession("NONXA");}
 public Session openSession(String name) {
  if(CURRENT.get()!=null)throw new AssertionError("Session already found in thread local!");
  Session session=openNewSession(name);bind(session);return session;
 }
 public SessionPersister getSessionPersister(){return TestOrmAccess.persister();}
 public Session fetchCurrentSession() throws PersistenceException {
  currentLookups++;
  if(CURRENT.get()==null)throw new PersistenceException("2130",new String[]{"Failed to obtain current session"});
  return CURRENT.get();
 }
 public Session openNewSession(String name) {
  if(!"NONXA".equals(name))throw new AssertionError("Expected independent NONXA unit");
  opens++;return TestOrmAccess.wrap(factory.createEntityManager());
 }
 public Session openNewSession(){return openNewSession("NONXA");}
 public void closeSession(Session session){closes++;try{session.close();}catch(PersistenceException error){throw new IllegalStateException(error);}}
}''')
    files += [bridge,manager]
    config=Path(out)/'ConfigurationFactory.java'
    config.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
 public java.util.prefs.Preferences getRootConfigurations(){return java.util.prefs.Preferences.userRoot().node("hth849-tests");}
 public java.util.prefs.Preferences getConfigurations(String category){if(category.equals(System.getProperty("hth849.failConfigCategory"))){if(Boolean.getBoolean("hth849.failConfigLinkage"))throw new NoClassDefFoundError("injected dependency failure");throw new IllegalStateException("injected configuration failure");}if("HTHCRMConfiguration".equals(category))System.setProperty("hth849.configReads",String.valueOf(Integer.parseInt(System.getProperty("hth849.configReads","0"))+1));return getRootConfigurations().node(category);}
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
 public static com.ofss.digx.cz.bea.common.hth.HthCRMInputData last;
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public IAdapterFactory getAdapterFactory(String key){
  lookups++;
  if(missingImplementation)throw new NoClassDefFoundError("injected missing HTH implementation");
  if(fail)throw new IllegalStateException("injected adapter failure");
  if(!com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter.FACTORY.equals(key))throw new AssertionError(key);
  return new com.ofss.digx.app.adapter.AdapterFactory(){
   public Object getAdapter(String name,com.ofss.digx.datatype.NameValuePair[] values){return getAdapter(name);}
   public Object getAdapter(String name){
    final com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter delegate=(com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter)
     com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory.getInstance().getAdapter(name);
    if(delegate==null)throw new AssertionError(name);
    return new com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter(){
     public void collectApproval(com.ofss.fc.app.context.SessionContext context,
       com.ofss.digx.framework.domain.transaction.Transaction transaction,String action){delegate.collectApproval(context,transaction,action);}
     public void collect(com.ofss.digx.cz.bea.common.hth.HthCRMInputData value){last=value;delegate.collect(value);}
    };
   }
  };
 }
}''')
    files += [factory, Path(__file__).with_name('HthCRMBoundaryTest.java')]
    run([str(jdk/'javac'),'--release','8','-proc:none','-cp',cp,'-d',out,*map(str,dict.fromkeys(files))])
    for main in ('com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMContextTest','com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMTest','HthOnboardingAuditTest','com.ofss.digx.cz.bea.app.sms.service.user.HthCRMBoundaryTest','com.ofss.digx.cz.bea.app.approval.service.transaction.HthCRMApprovalTest'):
        run([str(jdk/'java'),'-Djava.util.prefs.userRoot='+out+'/prefs','-cp',out+os.pathsep+cp,main])
    if os.environ.get('H2_JAR'):
        schema=(root/'consulting/db/branch_change_history/20260923_HTH_MTB_849/1_HTH_CRM_849_Schema.sql').read_text().split("q'~",1)[1].split("~'",1)[0]
        schema=re.sub(r'VARCHAR2\((\d+) CHAR\)',r'VARCHAR(\1)',schema)
        # H2 1.4's bare NUMBER defaults to scale zero. Oracle's unconstrained
        # NUMBER preserves fractions; use an explicit H2 decimal for ORM checks.
        # The separate Oracle upgrade tests exercise the unmodified production DDL.
        schema=re.sub(r'\bNUMBER\b(?!\s*\()',r'DECIMAL(38,10)',schema)
        ddl=Path(out)/'schema.sql';ddl.write_text(schema)
        h2=Path(os.environ['H2_JAR']);assert h2.is_file(), 'H2_JAR must reference a local H2 driver (tested with 1.4.200)'
        run([str(jdk/'java'),'-Duser.timezone=UTC','-Djava.util.prefs.userRoot='+out+'/prefs','-cp',out+os.pathsep+str(h2)+os.pathsep+cp,
            'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMOrmTest',str(ddl)])
    else:
        print('SKIP real database tests: set H2_JAR (Oracle/WebLogic require UAT regardless).')
print('PASS: production compilation and 849 / existing 791 regression tests')
