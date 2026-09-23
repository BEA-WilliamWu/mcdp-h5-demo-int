"""Run the production 791 projector and diff with real audit DTOs/JMS serialization."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[3];projects=root/'consulting/middleware/projects'
cp=os.pathsep.join([str(root/'devtools/backend-compile/build/classes/java/main')]+[str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar')])
sources=[next(projects.rglob('HthOnboardingAudit.java')),next(projects.rglob('HthUserAccessAudit.java')),Path(__file__).with_name('HthOnboardingAuditTest.java'),Path(__file__).with_name('HthUserAccessAuditTest.java')]
sources += list((projects/'common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common/mtb').glob('*.java'))
jdk=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory(prefix='hth-audit-test-') as work:
    config=Path(work)/'ConfigurationFactory.java'
    config.write_text("""package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 private static final ConfigurationFactory INSTANCE=new ConfigurationFactory();
 public static ConfigurationFactory getInstance(){return INSTANCE;}
 public java.util.prefs.Preferences getRootConfigurations(){return java.util.prefs.Preferences.userRoot().node("test");}
 public java.util.prefs.Preferences getConfigurations(String category){return getRootConfigurations();}
}""")
    sources.append(config)
    session=Path(work)/'SessionDataManager.java'
    session.write_text("""package com.ofss.digx.framework.security.handlers;
public class SessionDataManager {
 private static final SessionDataManager INSTANCE=new SessionDataManager();
 public static SessionDataManager getManager(){return INSTANCE;}
 public String getId(){return "SYNTHETIC-SESSION";}
}""")
    sources.append(session)
    subprocess.run([str(jdk/'javac'),'--release','8','-proc:none','-cp',cp,'-d',work,*map(str,sources)],check=False).check_returncode()
    for name in ('HthOnboardingAuditTest','com.ofss.digx.cz.bea.app.hosttohost.service.HthUserAccessAuditTest'):
        subprocess.run([str(jdk/'java'),'-Djava.util.prefs.userRoot='+work+'/prefs','-cp',work+os.pathsep+cp,name],check=False).check_returncode()
