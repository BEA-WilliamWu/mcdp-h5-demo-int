"""Exercise production transaction methods and SQL with EclipseLink/OBDX ORM and H2.

WebLogic transaction suspension, configuration and service bootstrap are fixtures.
EntityManager, ORM Session/Query/Transaction wrappers and Code crypto are real.
The STATE MERGE source parameters receive explicit VARCHAR casts for H2 type inference;
all other SQL and all service method bodies are unchanged.
This does not replace Oracle/WebLogic deployment tests. Requires JAVA_HOME and H2_JAR.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea"
LIB = ROOT / "consulting/middleware/lib"
JDK = Path(os.environ["JAVA_HOME"]) / "bin"
H2 = Path(os.environ["H2_JAR"])
assert H2.is_file(), "H2_JAR must reference a local H2 driver (tested with 1.4.200)"
ECLIPSELINK = Path(os.environ.get("ECLIPSELINK_JAR", str(LIB / "OBDX_FW_LIB/eclipselink2.5.2.jar")))
CP = os.pathsep.join(map(str, [ECLIPSELINK, H2, *sorted(LIB.rglob("*.jar"))]))
source = (BASE / "app/hosttohost/service/HostToHostApiPassword.java").read_text()

with tempfile.TemporaryDirectory(prefix="hth-orm-transactions-") as temporary:
    work = Path(temporary)
    sources = []

    def write(name, text):
        target = work / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        if name.endswith(".java"):
            sources.append(str(target))

    # Copy complete service method bodies, including transaction order and close logic.
    methods = []
    for name in ("string", "validateAndReserveCode", "completeInternal", "openIndependent", "logPhaseFailure"):
        match = re.search(r"  private [^\n]+ " + name + r"\(.*?\n  }", source, re.S)
        assert match, name
        methods.append(match.group())
    lease = source[source.index("  private static final class SessionLease {"):source.rindex("\n}")]
    fields = []
    for kind in ("Code", "Credential", "State", "Operation"):
        name = "LocalHthApiPassword" + kind + "RepositoryAdapter"
        adapter = (BASE / ("domain/hosttohost/entity/repository/adapter/" + name + ".java")).read_text()
        bodies = re.findall(r"  public [^\n]+\(Session session[^\n]+ \{\n.*?\n  \}", adapter, re.S)
        assert bodies, name
        if kind == "State":
            # H2 requires types for bind variables in a MERGE-derived table; Oracle infers them.
            bodies = [body.replace("SELECT ? PARTY_ID, ? USER_ID FROM DUAL",
                                   "SELECT CAST(? AS VARCHAR(50)) PARTY_ID, CAST(? AS VARCHAR(80)) USER_ID FROM DUAL")
                      for body in bodies]
        write(name + ".java", "import com.ofss.digx.infra.exceptions.Exception;\n"
              "import com.ofss.fc.infra.das.orm.*; import java.util.List;\n"
              "public class " + name + " {\n" + "\n".join(bodies) + "\n}")
        fields.append("private final " + name + " " + kind.lower() + "Repository = new " + name + "();")
    write("PasswordTransactionHarness.java", """
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.*;
import com.ofss.fc.infra.jdbc.ConnectionUtil;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import javax.transaction.TransactionManager;
import weblogic.transaction.TransactionHelper;
import java.util.List;
import java.util.logging.*;
public class PasswordTransactionHarness {
  private static final Logger LOGGER = Logger.getLogger("transaction-test");
  public void logTestFailure(Throwable failure) { logPhaseFailure("TEST_FAILURE", failure); }
  public String reserve(String purpose, String code, String requestId) throws Exception {
    return validateAndReserveCode("PARTY", "USER", purpose, code, requestId, "DATABASE");
  }
  public void complete(String purpose, String requestId, String codeId, String hash) throws Exception {
    completeInternal("PARTY", "USER", purpose, requestId, codeId, "REFERENCE", hash);
  }
""" + "\n".join(fields + methods) + lease + "\n}")
    write("ConfigurationFactory.java", """
package com.ofss.fc.infra.config;
public class ConfigurationFactory {
  private static final ConfigurationFactory INSTANCE = new ConfigurationFactory();
  public static ConfigurationFactory getInstance() { return INSTANCE; }
  public java.util.prefs.Preferences getRootConfigurations() {
    return java.util.prefs.Preferences.userRoot().node("test");
  }
  public java.util.prefs.Preferences getConfigurations(String category) { return getRootConfigurations(); }
}
""")
    write("Exception.java", """
package com.ofss.digx.infra.exceptions;
public class Exception extends java.lang.Exception {
  public Exception(String code) { super(code); }
  public Exception(java.lang.Exception cause) { super(cause); }
}
""")
    write("ConnectionUtil.java", """
package com.ofss.fc.infra.jdbc;
public class ConnectionUtil { public static boolean isConnPooled(String app) { return true; } }
""")
    write("TransactionHelper.java", """
package weblogic.transaction;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.lang.reflect.Proxy;
public class TransactionHelper {
  public static Transaction current;
  public static int suspends, resumes;
  private static final TransactionHelper INSTANCE = new TransactionHelper();
  private static final TransactionManager MANAGER = (TransactionManager) Proxy.newProxyInstance(
      TransactionManager.class.getClassLoader(), new Class<?>[] {TransactionManager.class}, (p, m, a) -> {
    if ("suspend".equals(m.getName())) { Transaction saved = current; current = null; suspends++; return saved; }
    if ("resume".equals(m.getName())) { if (current != null) throw new AssertionError(); current = (Transaction) a[0]; resumes++; return null; }
    throw new AssertionError("Independent ORM work must not complete the outer JTA transaction: " + m.getName());
  });
  public static TransactionHelper getTransactionHelper() { return INSTANCE; }
  public TransactionManager getTransactionManager() { return MANAGER; }
}
""")
    write("TestOrmAccess.java", """
package com.ofss.fc.infra.das.orm.eclipselink;
import javax.persistence.*;
import com.ofss.fc.infra.das.orm.Session;
/** Package-level fixture access to the unchanged SDK wrapper; no reflection. */
public class TestOrmAccess {
  public static Session wrap(EntityManager em) { return new EclipseLinkEntityManagerWrapper(em); }
}
""")
    write("DataAccessManager.java", """
package com.ofss.fc.infra.das.orm;
import javax.persistence.*;
import com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess;
public class DataAccessManager {
  public static EntityManagerFactory factory;
  public static int opened, closed;
  public static Session outerSession;
  public int fetchCurrentUsageCount() { return outerSession == null ? 0 : 1; }
  public Session fetchCurrentSession() { return outerSession; }
  public static boolean failOpen;
  private static final DataAccessManager INSTANCE = new DataAccessManager();
  public static DataAccessManager getManager() { return INSTANCE; }
  public Session openNewSession(String unit) {
    if (weblogic.transaction.TransactionHelper.current != null) throw new AssertionError("Outer transaction not suspended");
    if (failOpen) throw new IllegalStateException("Injected session-open failure");
    if (!"NONXA".equals(unit)) throw new IllegalArgumentException("Only the NONXA resource-local test unit is configured: " + unit);
    opened++;
    return TestOrmAccess.wrap(factory.createEntityManager());
  }
  public void closeSession(Session session) { closed++; session.close(); }
}
""")
    write("META-INF/persistence.xml", """<persistence xmlns="http://java.sun.com/xml/ns/persistence" version="2.0">
<persistence-unit name="NONXA" transaction-type="RESOURCE_LOCAL">
<provider>org.eclipse.persistence.jpa.PersistenceProvider</provider><exclude-unlisted-classes>true</exclude-unlisted-classes>
<properties>
<property name="javax.persistence.jdbc.driver" value="org.h2.Driver"/>
<property name="javax.persistence.jdbc.url" value="jdbc:h2:mem:hth;MODE=Oracle;DB_CLOSE_DELAY=-1"/>
<property name="eclipselink.weaving" value="false"/><property name="eclipselink.logging.level" value="OFF"/>
</properties></persistence-unit></persistence>""")
    sources += [str(BASE / "app/hosttohost/util/HthApiPasswordCrypto.java"),
                str(Path(__file__).with_name("HthApiPasswordTransactionTest.java"))]
    compile_result = subprocess.run([str(JDK / "javac"), "--release", "8", "-proc:none", "-cp", CP,
                    "-d", str(work), *sources], check=False)
    if compile_result.returncode:
        raise SystemExit(compile_result.returncode)
    run_result = subprocess.run([str(JDK / "java"), "-Djava.util.prefs.userRoot=" + str(work / "prefs"),
                    "-cp", str(work) + os.pathsep + CP, "HthApiPasswordTransactionTest"], check=False)
    raise SystemExit(run_result.returncode)
