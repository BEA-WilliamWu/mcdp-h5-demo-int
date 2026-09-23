"""Exercise production transaction methods and SQL with EclipseLink/OBDX ORM and H2.

WebLogic transaction suspension, configuration and service bootstrap are fixtures.
EntityManager, ORM Session/Query/Transaction wrappers, change() orchestration and Code crypto are real.
For change() tests, session decryption, DSP and notification delivery are fixtures;
the separate transport tests exercise real RSA and HTTPS.
The STATE MERGE source parameters receive explicit VARCHAR casts for H2 type inference,
and Oracle's approval expiry arithmetic uses H2 DATEADD. Service method bodies are unchanged.
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

    # Apply the production user foreign keys to the test schema.
    schema = (ROOT / "consulting/db/branch_change_history/20260907_HTH_API_Password/1_HTH_API_Password_Schema.sql").read_text()
    constraints = []
    for table, constraint in (("STATE", "STATE"), ("OPERATION", "OP"), ("CREDENTIAL", "CRED")):
        match = re.search(r"CONSTRAINT FK_HTH_API_PWD_" + constraint
                          + r"_USER FOREIGN KEY \(PARTY_ID, USER_ID\)\s+REFERENCES HTH_BEA.HTH_USER_PROFILE \(PARTY_ID, CLOSE_ID\)", schema)
        assert match, table
        constraints.append("ALTER TABLE HTH_BEA.HTH_API_PASSWORD_" + table + " ADD "
                           + " ".join(match.group().split()))
    write("user-foreign-keys.sql", "\n".join(constraints))

    # Copy complete service method bodies, including transaction order and close logic.
    methods = []
    for name in ("string", "validateAndReserveCode", "completeInternal", "completeDatabase", "openIndependent", "open", "logPhaseFailure", "identity", "canonicalUser", "normalize", "findDatabaseCredentialState", "findDspCredentialState", "findSuccessfulOperation", "resolveCodePurpose", "credentialState", "storageBackend", "activateApprovedCode", "change", "auditedChange", "validateRequest", "validatePassword", "passwordPolicy", "complete", "fail"):
        match = re.search(r"  private [^\n]+ " + name + r"\(.*?\n  }", source, re.S)
        assert match, name
        methods.append(match.group())
    lease = source[source.index("  private static final class OperationResult {"):source.rindex("\n}")]
    identity_class = re.search(r"  private static final class Identity \{.*?\n  }", source, re.S).group()
    # Compile the production call sites as well as the methods they invoke.
    reserve_call = re.search(r"codeId = (validateAndReserveCode\(identity\..*?\));", source, re.S).group(1)
    complete_call = re.search(r"completeDatabase\(identity\..*?\);", source, re.S).group()
    lookup_call = re.search(r"OperationResult previous = (findSuccessfulOperation\(.*?\));", source, re.S).group(1)
    status_call = re.search(r"return (findDatabaseCredentialState\(identity\..*?\));", source, re.S).group(1)
    def call(expression):
        return expression.replace("request.getRequestId()", "requestId").replace("storage.name()", '"DATABASE"')

    policy_constants = re.findall(r'  private static final String POLICY_.*?;', source, re.S)
    fields = []
    for kind in ("Code", "Credential", "State", "Operation"):
        name = "LocalHthApiPassword" + kind + "RepositoryAdapter"
        adapter = (BASE / ("domain/hosttohost/entity/repository/adapter/" + name + ".java")).read_text()
        bodies = re.findall(r"  public [^\n]+\(Session session[^\n]+ \{\n.*?\n  \}", adapter, re.S)
        assert bodies, name
        if kind == "Code":
            bodies = [body.replace("SYSTIMESTAMP + NUMTODSINTERVAL(?, 'HOUR')",
                                   "DATEADD('HOUR', ?, SYSTIMESTAMP)") for body in bodies]
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
import com.ofss.digx.cz.bea.common.hth.HthOnboardingAudit;
import com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMScope;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.fc.infra.config.ConfigurationFactory;
import javax.transaction.TransactionManager;
import weblogic.transaction.TransactionHelper;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import java.util.logging.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordPolicyDTO;
public class PasswordTransactionHarness extends PolicyFixture {
  private static final Logger LOGGER = Logger.getLogger("transaction-test");
  private static final String SETUP = "SETUP", RESET = "RESET";
  private static final String ADAPTER_CATEGORY = "HthApiCredentialAdapterConfig";
  private static final FormatterFixture FORMATTER = new FormatterFixture();
  private IHthApiCredentialAdapter adapter() { return RemoteCredentialFixture.INSTANCE; }
  private IHthApiCredentialAdapter dspAdapter() { return DspCredentialFixture.INSTANCE; }
  static int setupNotifications, resetNotifications;
  private boolean isFeatureEnabled() { return true; }
  private HostToHostApiPasswordResponseDTO response() { return new HostToHostApiPasswordResponseDTO(); }
  private List<String> decryptCredentials(HostToHostApiPasswordRequestDTO request, String user, String operation) throws Exception {
    if ("BAD_TRANSPORT".equals(request.encrypted)) throw new Exception("DIGX_CZ_HTH_API_PASSWORD_010");
    return java.util.Arrays.asList(request.password, request.code);
  }
  private void notifySetupSuccess(SessionContext session, Identity identity) { setupNotifications++; }
  private void notifyResetSuccess(SessionContext session, Identity identity) { resetNotifications++; }
  public String changeDsp(String operation, String password, String code, String requestId) throws Exception {
    HostToHostApiPasswordRequestDTO request = new HostToHostApiPasswordRequestDTO();
    request.requestId = requestId; request.password = password; request.code = code;
    request.encrypted = "BAD_TRANSPORT".equals(password) ? password : "synthetic-encrypted-envelope";
    return auditedChange(new SessionContext(), request, operation, "test-service").state;
  }
  public String dspState() throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DSP);
    return credentialState(identity, true, HthApiPasswordStorage.DSP);
  }
  public String codePurpose(String partyId, String userName, String requested) throws Exception {
    return resolveCodePurpose(partyId, userName, requested);
  }
  public boolean approve(String codeId, String purpose) throws Exception {
    return activateApprovedCode(codeId, "PARTY", "USER", purpose, "MAKER", "TX-APPROVED", 24);
  }
  public void logTestFailure(Throwable failure) { logPhaseFailure("TEST_FAILURE", failure); }
  private static final String STATE_NOT_SETUP = "NOT_SETUP";
  public String[] resolvedIdentity() throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DATABASE);
    return new String[] { identity.userId, identity.profileUserId };
  }
  public String reserve(String operation, String code, String requestId) throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DATABASE);
    return RESERVE_CALL;
  }
  public void complete(String operation, String requestId, String codeId, String passwordHash) throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DATABASE);
    String reference = "REFERENCE";
    COMPLETE_CALL
  }
  public String credentialStatus() throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DATABASE);
    return STATUS_CALL;
  }
  public String previousStatus(String operation, String requestId) throws Exception {
    Identity identity = identity(new SessionContext(), true, HthApiPasswordStorage.DATABASE);
    OperationResult previous = LOOKUP_CALL;
    return previous == null ? null : previous.status;
  }
""".replace("RESERVE_CALL", call(reserve_call)).replace("COMPLETE_CALL", call(complete_call))
       .replace("STATUS_CALL", status_call).replace("LOOKUP_CALL", call(lookup_call))
       + "\n".join(policy_constants + fields + methods) + identity_class + lease + "\n}")
    write("IdentityFixtures.java", """
import java.sql.*;
import com.ofss.digx.infra.exceptions.Exception;
class SessionContext extends com.ofss.fc.app.context.SessionContext {
  public String getUserId() { return "USER@PARTY"; }
  public String getTransactingPartyCode() { return "PARTY"; }
}
class HthUserProfileKey {
  private String partyId, closeId;
  public void setPartyId(String value) { partyId = value; }
  public void setCloseId(String value) { closeId = value; }
  public String getPartyId() { return partyId; }
  public String getCloseId() { return closeId; }
}
class HthUserProfile {
  private HthUserProfileKey key;
  HthUserProfile(HthUserProfileKey value) { key = value; }
  public HthUserProfileKey getKey() { return key; }
}
class HthUserProfileRepository {
  static HthUserProfileRepository getInstance() { return new HthUserProfileRepository(); }
  HthUserProfile read(HthUserProfileKey key) throws Exception {
    try (Connection db = DriverManager.getConnection("jdbc:h2:mem:hth;MODE=Oracle");
         PreparedStatement query = db.prepareStatement(
             "SELECT CLOSE_ID FROM HTH_BEA.HTH_USER_PROFILE WHERE PARTY_ID=? AND CLOSE_ID=?")) {
      query.setString(1, key.getPartyId()); query.setString(2, key.getCloseId());
      try (ResultSet rows = query.executeQuery()) {
        return rows.next() ? new HthUserProfile(key) : null;
      }
    } catch (SQLException failure) { throw new Exception(failure); }
  }
}
class HthManagement {
  HthManagement findActiveByPartyId(String partyId) { return this; }
  String getHthStatus() { return "ENABLE"; }
  static String clientId = "CLIENT";
  String getUamClientId() { return clientId; }
}
class PolicyFixture {
  protected void checkAccessPolicy(String id, SessionContext session, Object request) { }
  protected void checkResponsePolicy(SessionContext session, Object response) { }
}
class Interaction {
  static void begin(SessionContext session) { }
  static void close() { }
}
class HostToHostApiPasswordRequestDTO {
  String requestId, password, code, encrypted;
  String getRequestId() { return requestId; }
  String getEncryptedCredentials() { return encrypted; }
  void setEncryptedCredentials(String value) { encrypted = value; }
}
class HostToHostApiPasswordResponseDTO {
  String state;
  final StatusFixture status = new StatusFixture();
  StatusFixture getStatus() { return status; }
  void setSetupState(String value) { state = value; }
  void setResetAllowed(boolean value) { }
}
class StatusFixture {
  private String reference;
  String getReferenceNumber() { return reference; }
  void setReferenceNumber(String value) { reference=value; }
  void setExternalReferenceNumber(String value) { }
}
class DspCredentialFixture implements com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter {
  static final DspCredentialFixture INSTANCE = new DspCredentialFixture();
  static int calls;
  static String failure, lastPassword, lastUser, lastClient;
  static Runnable afterPersist;
  public String getStatus(String p, String u, String c) { throw new AssertionError("No DSP status API"); }
  public String setup(String p, String u, String c, String v, String r) throws Exception { return persist(p,u,c,v,r); }
  public String reset(String p, String u, String c, String v, String r) throws Exception { return persist(p,u,c,v,r); }
  private String persist(String p, String u, String c, String v, String r) throws Exception {
    calls++; lastUser = u; lastClient = c; lastPassword = v;
    if (failure != null) throw new com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException(!"BEFORE_POST".equals(failure));
    if (afterPersist != null) afterPersist.run();
    return r;
  }
}
class FormatterFixture {
  String formatMessage(String text, Object... args) { return String.format(text, args); }
}
class RemoteCredentialFixture implements com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter {
  static final RemoteCredentialFixture INSTANCE = new RemoteCredentialFixture();
  static String state = "NOT_SETUP", lastParty, lastUser;
  static int calls;
  public String getStatus(String partyId, String userId, String clientId) throws Exception {
    calls++; lastParty = partyId; lastUser = userId;
    if ("FAIL".equals(state)) throw new Exception("REMOTE_UNAVAILABLE");
    return state;
  }
  public String setup(String p, String u, String c, String v, String r) { throw new AssertionError(); }
  public String reset(String p, String u, String c, String v, String r) { throw new AssertionError(); }
}
""")
    storage = (BASE / "app/hosttohost/service/HthApiPasswordStorage.java").read_text()
    write("HthApiPasswordStorage.java", re.sub(r"^package .*?;", "", storage))
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
  public String getErrorCode() { return getMessage(); }
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
    if ("suspend".equals(m.getName())) { Transaction saved = current; current = null; if (saved != null) suspends++; return saved; }
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
  public boolean isSessionOpen() { return outerSession != null; }
  public Session openSession(String unit) {
    opened++;
    return TestOrmAccess.wrap(factory.createEntityManager());
  }
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
    sources += [str(p) for p in (BASE / "app/hosttohost/crm").glob("*.java")
                if p.name not in ("HthCRMApprovalAsserter.java", "HthCRMAdapter.java", "HthCRMAdapterFactory.java")]
    sources += [str(p) for p in (ROOT / "consulting/middleware/projects/common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common/hth").glob("*.java")]
    common = ROOT / "consulting/middleware/projects/common"
    sources += [str(next((ROOT / "consulting/middleware/projects").rglob("HthOnboardingAudit.java")))]
    sources += [str(common / "com.ofss.digx.cz.bea.app.xface/src/com/ofss/digx/cz/bea/app/hosttohost/dto/HostToHostApiPasswordPolicyDTO.java"),
                str(common / "com.ofss.digx.cz.bea.extxface/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/HthApiCredentialWriteException.java"), str(BASE / "app/hosttohost/util/HthApiPasswordCrypto.java"),
                str(BASE / "app/hosttohost/util/HthApiPasswordHash.java"),
                str(common / "com.ofss.digx.cz.bea.app.xface/src/com/ofss/digx/cz/bea/app/hosttohost/dto/HthApiPasswordGenerateDTO.java"),
                str(common / "com.ofss.digx.cz.bea.extxface/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/IHthApiCredentialAdapter.java"),
                str(Path(__file__).with_name("HthApiPasswordTransactionTest.java"))]
    compile_result = subprocess.run([str(JDK / "javac"), "--release", "8", "-proc:none", "-cp", CP,
                    "-d", str(work), *sources], check=False)
    if compile_result.returncode:
        raise SystemExit(compile_result.returncode)
    run_result = subprocess.run([str(JDK / "java"), "-Djava.util.prefs.userRoot=" + str(work / "prefs"),
                    "-cp", str(work) + os.pathsep + CP, "HthApiPasswordTransactionTest"], check=False)
    raise SystemExit(run_result.returncode)
