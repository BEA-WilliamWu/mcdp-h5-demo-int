import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordHash;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordGenerateDTO;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import javax.persistence.*;
import javax.transaction.Transaction;
import weblogic.transaction.TransactionHelper;

/** Real ORM/database transactions with synthetic data; server bootstrap is isolated by the runner. */
public final class HthApiPasswordTransactionTest {
  private static Connection db;
  private static final PasswordTransactionHarness SERVICE = new PasswordTransactionHarness();
  private static final String CODE = "123456";
  private static Transaction outer;
  private static EntityManager outerEntityManager;

  public static void main(String[] args) throws java.lang.Exception {
    DataAccessManager.factory = Persistence.createEntityManagerFactory("NONXA");
    db = DriverManager.getConnection("jdbc:h2:mem:hth;MODE=Oracle;DB_CLOSE_DELAY=-1");
    try {
      byte[] key = new byte[32];
      new SecureRandom().nextBytes(key);
      ConfigurationFactory.getInstance().getRootConfigurations().put(
          "HTH_API_PASSWORD.CODE_CIPHER_KEY", Base64.getEncoder().encodeToString(key));
      diagnosticsOmitSensitiveInput();
      HthApiPasswordGenerateDTO request = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
          "{\"partyId\":\"PARTY\",\"userName\":\"USER\"}", HthApiPasswordGenerateDTO.class);
      check(request.getPurpose() == null, "Existing clients omit purpose and must allow automatic selection");
      schema();
      for (String closeId : new String[] {"USER@PARTY", "USER"}) {
        resetData(closeId);
        check("USER".equals(SERVICE.resolvedIdentity()[0]), "Code username stays canonical");
        check(closeId.equals(SERVICE.resolvedIdentity()[1]), "Persistence uses the actual profile key");
        check("SETUP".equals(SERVICE.codePurpose("PARTY", "USER@PARTY", null)), "Unset target selects SETUP");
        reject("DIGX_CZ_HTH_PW_009", () -> SERVICE.codePurpose("PARTY", "USER", "RESET"));
        failedAttemptsSurviveOuterRollback();
        setupAndResetCommitTogether();
        failedCompletionRollsBackAllWrites();
        expiredCodeDoesNotReserve();
        codeFailuresRemainDistinct();
        changingPurposeDoesNotChangeCipher();
        targetStateControlsPurpose();
        openFailureRestoresOuterTransaction();
        if ("USER@PARTY".equals(closeId)) {
          sql("INSERT INTO HTH_BEA.HTH_USER_PROFILE VALUES ('PARTY', 'USER')");
          check(closeId.equals(SERVICE.resolvedIdentity()[1]), "Exact login profile takes precedence");
          check("ACTIVE".equals(SERVICE.credentialStatus()), "Status reads the selected profile credential");
        }
      }
      check(DataAccessManager.opened == DataAccessManager.closed, "Every independent ORM session closed");
      check(TransactionHelper.suspends == TransactionHelper.resumes, "Every suspended transaction restored");
      System.out.println("PASS: actual OBDX/EclipseLink ORM + H2: automatic SETUP/RESET by target/store, PBKDF2 password changes, failed-attempt commit/lockout, Code aliases/purpose/expiry/consumption/decryption, profile foreign keys, atomic rollback, status/retry reads and session cleanup");
    } finally {
      if (outerEntityManager != null && outerEntityManager.isOpen()) outerEntityManager.close();
      db.close();
      DataAccessManager.factory.close();
    }
  }

  private static void diagnosticsOmitSensitiveInput() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("transaction-test");
    final StringBuilder captured = new StringBuilder();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      public void publish(java.util.logging.LogRecord record) {
        check(record.getThrown() == null, "Diagnostic must not attach the throwable or its messages");
        captured.append(java.text.MessageFormat.format(record.getMessage(), record.getParameters()));
      }
      public void flush() { }
      public void close() { }
    };
    logger.addHandler(handler);
    try {
      RuntimeException failure = new RuntimeException("PASSWORD_SENTINEL",
          new IllegalStateException("CODE_SENTINEL"));
      failure.addSuppressed(new RuntimeException("CIPHERTEXT_SENTINEL"));
      SERVICE.logTestFailure(failure);
    } finally { logger.removeHandler(handler); }
    String diagnostic = captured.toString();
    check(diagnostic.contains("TEST_FAILURE") && diagnostic.contains("java.lang.IllegalStateException"),
        "Diagnostic identifies phase and nested exception type");
    check(!diagnostic.contains("SENTINEL"), "Diagnostic must omit input-bearing exception messages");
  }

  private static void schema() throws SQLException {
    sql("CREATE SCHEMA HTH_BEA");
    sql("CREATE TABLE OUTER_WORK (ID INT)");
    sql("CREATE TABLE HTH_BEA.HTH_USER_PROFILE (PARTY_ID VARCHAR(40), CLOSE_ID VARCHAR(80), "
        + "PRIMARY KEY (PARTY_ID, CLOSE_ID))");
    sql("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID VARCHAR(40) PRIMARY KEY, PARTY_ID VARCHAR(40), "
        + "USER_NAME VARCHAR(80), PURPOSE VARCHAR(10), CODE_CIPHER VARCHAR(200), STATUS VARCHAR(20), "
        + "OBJECT_STATUS CHAR(1), EXPIRY_TIME TIMESTAMP, ATTEMPT_COUNT INT, MAX_ATTEMPTS INT, "
        + "CREATION_DATE TIMESTAMP, LAST_UPDATE_DATE TIMESTAMP, REQUEST_ID VARCHAR(40), "
        + "USED_TIME TIMESTAMP, LAST_UPDATED_BY VARCHAR(80), TRANSACTION_ID VARCHAR(40))");
    sql("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_OPERATION (REQUEST_ID VARCHAR(40) PRIMARY KEY, PARTY_ID VARCHAR(40), "
        + "USER_ID VARCHAR(80), OPERATION VARCHAR(10), CODE_ID VARCHAR(40), STATUS VARCHAR(20), CREATED_BY VARCHAR(80), "
        + "CREATION_DATE TIMESTAMP, LAST_UPDATED_BY VARCHAR(80), LAST_UPDATE_DATE TIMESTAMP, OBJECT_STATUS CHAR(1), "
        + "OBJECT_VERSION_NUMBER INT, STORAGE_BACKEND VARCHAR(20), REFERENCE_NUMBER VARCHAR(40))");
    sql("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CREDENTIAL (PARTY_ID VARCHAR(40), USER_ID VARCHAR(80), "
        + "PASSWORD_HASH VARCHAR(200), LAST_REQUEST_ID VARCHAR(40), LAST_UPDATED_BY VARCHAR(80), "
        + "CREDENTIAL_STATUS VARCHAR(20), CREDENTIAL_VERSION INT, CREATED_AT TIMESTAMP, UPDATED_AT TIMESTAMP, "
        + "PRIMARY KEY (PARTY_ID, USER_ID))");
    sql("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_STATE (PARTY_ID VARCHAR(40), USER_ID VARCHAR(80), "
        + "CREDENTIAL_STATUS VARCHAR(20), CREDENTIAL_VERSION INT, LAST_RESET_AT TIMESTAMP, SETUP_AT TIMESTAMP, "
        + "LAST_REQUEST_ID VARCHAR(40), LAST_REFERENCE_NUMBER VARCHAR(40), LAST_UPDATED_BY VARCHAR(80), "
        + "LAST_UPDATE_DATE TIMESTAMP, OBJECT_STATUS CHAR(1), CREATED_BY VARCHAR(80), CREATION_DATE TIMESTAMP, "
        + "OBJECT_VERSION_NUMBER INT, PRIMARY KEY (PARTY_ID, USER_ID))");
    try (java.util.Scanner constraints = new java.util.Scanner(
        HthApiPasswordTransactionTest.class.getResourceAsStream("/user-foreign-keys.sql"), "UTF-8")) {
      while (constraints.hasNextLine()) sql(constraints.nextLine());
    }
  }

  private static void resetData(String closeId) throws SQLException {
    for (String table : new String[] {"OPERATION", "CREDENTIAL", "STATE", "CODE"}) {
      sql("DELETE FROM HTH_BEA.HTH_API_PASSWORD_" + table);
    }
    sql("DELETE FROM HTH_BEA.HTH_USER_PROFILE");
    try (PreparedStatement insert = db.prepareStatement("INSERT INTO HTH_BEA.HTH_USER_PROFILE VALUES ('PARTY', ?)")) {
      insert.setString(1, closeId); insert.executeUpdate();
    }
  }

  private static void beginOuter() {
    outer = (Transaction) Proxy.newProxyInstance(Transaction.class.getClassLoader(), new Class<?>[] {Transaction.class},
        (p, m, a) -> { throw new AssertionError("Outer JTA transaction was completed by independent work"); });
    TransactionHelper.current = outer;
    outerEntityManager = DataAccessManager.factory.createEntityManager();
    DataAccessManager.outerSession = com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess.wrap(outerEntityManager);
    outerEntityManager.getTransaction().begin();
    outerEntityManager.createNativeQuery("INSERT INTO OUTER_WORK VALUES (1)").executeUpdate();
  }

  private static void rollbackOuter() throws SQLException {
    check(TransactionHelper.current == outer, "Original transaction restored");
    check(outerEntityManager.getTransaction().isActive(), "Outer ORM transaction remains active");
    outerEntityManager.getTransaction().rollback();
    outerEntityManager.close();
    check(number("SELECT COUNT(*) FROM OUTER_WORK") == 0, "Outer work rolled back");
    TransactionHelper.current = null;
    DataAccessManager.outerSession = null;
  }

  private static void failedAttemptsSurviveOuterRollback() throws java.lang.Exception {
    seed("wrong", "SETUP");
    for (int i = 1; i <= 5; i++) {
      beginOuter();
      reject("DIGX_CZ_HTH_API_PASSWORD_002", () -> SERVICE.reserve("SETUP", "654321", "wrong-request"));
      rollbackOuter();
      check(number("SELECT ATTEMPT_COUNT FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='wrong'") == i, "Attempt committed independently");
    }
    check("INVALID".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='wrong'")), "Lockout after five attempts");
    check(number("SELECT COUNT(*) FROM HTH_BEA.HTH_API_PASSWORD_OPERATION") == 0, "Mismatch creates no reservation");
  }

  private static void setupAndResetCommitTogether() throws java.lang.Exception {
    int version = 0;
    for (String operation : new String[] {"SETUP", "RESET"}) {
      String id = operation.toLowerCase();
      String purpose = SERVICE.codePurpose("PARTY", "USER", null);
      check(operation.equals(purpose), "Generation follows target state before/after setup");
      seed(id, purpose);
      sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS='PENDING', EXPIRY_TIME=NULL WHERE ID='" + id + "'");
      beginOuter();
      reject("DIGX_CZ_HTH_API_PASSWORD_002", () -> SERVICE.reserve(operation, CODE, id + "-pending"));
      rollbackOuter();
      if ("RESET".equals(operation)) {
        sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET USER_NAME='USER@PARTY' WHERE ID='reset'");
      }
      check(SERVICE.approve(id, purpose), "Original user-maintenance approval activates new Code");
      check(!SERVICE.approve(id, purpose), "Approval replay is idempotent");
      beginOuter();
      check(id.equals(SERVICE.reserve(operation, CODE, id)), "Correct code reserved");
      rollbackOuter();
      check("IN_PROGRESS".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='" + id + "'")), "Reservation committed");
      beginOuter();
      String password = "SETUP".equals(operation) ? "Example123" : "Example456";
      SERVICE.complete(operation, id, id, HthApiPasswordHash.hash(password));
      rollbackOuter();
      check("USED".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='" + id + "'")), "Code consumed");
      check(!SERVICE.approve(id, purpose), "Approval replay must not reactivate a consumed Code");
      check("SUCCESS".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_OPERATION WHERE REQUEST_ID='" + id + "'")), "Operation completed");
      String savedHash = (String) value("SELECT PASSWORD_HASH FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL");
      check(HthApiPasswordHash.verify(password, savedHash), "New password verifies against persisted hash");
      if ("RESET".equals(operation)) {
        check(!HthApiPasswordHash.verify("Example123", savedHash), "Old password stops matching after reset");
      }
      check(number("SELECT CREDENTIAL_VERSION FROM HTH_BEA.HTH_API_PASSWORD_STATE") == ++version, "State version committed");
      String closeId = SERVICE.resolvedIdentity()[1];
      for (String table : new String[] {"OPERATION", "CREDENTIAL", "STATE"}) {
        check(closeId.equals(value("SELECT USER_ID FROM HTH_BEA.HTH_API_PASSWORD_" + table)),
            table + " references the resolved profile key");
      }
      check("ACTIVE".equals(SERVICE.credentialStatus()), "Status finds the saved credential");
      check("SUCCESS".equals(SERVICE.previousStatus(operation, id)), "Retry finds the completed operation");
    }
  }

  private static void failedCompletionRollsBackAllWrites() throws java.lang.Exception {
    Object previousHash = value("SELECT PASSWORD_HASH FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL");
    seed("rollback", "RESET");
    beginOuter();
    SERVICE.reserve("RESET", CODE, "rollback");
    rollbackOuter();
    // Force the final conditional update to fail after Code, credential and state writes.
    sql("UPDATE HTH_BEA.HTH_API_PASSWORD_OPERATION SET STATUS='FAILED' WHERE REQUEST_ID='rollback'");
    beginOuter();
    reject("DIGX_CZ_HTH_API_PASSWORD_007", () -> SERVICE.complete("RESET", "rollback", "rollback", "UNCOMMITTED-HASH"));
    rollbackOuter();
    check("IN_PROGRESS".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='rollback'")), "Code consumption rolled back");
    check(previousHash.equals(value("SELECT PASSWORD_HASH FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL")), "Password write rolled back");
    check(number("SELECT CREDENTIAL_VERSION FROM HTH_BEA.HTH_API_PASSWORD_STATE") == 2, "State write rolled back");
  }

  private static void expiredCodeDoesNotReserve() throws java.lang.Exception {
    seed("expired", "SETUP");
    sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET EXPIRY_TIME=TIMESTAMP '2000-01-01 00:00:00' WHERE ID='expired'");
    beginOuter();
    reject("DIGX_CZ_HTH_API_PASSWORD_003", () -> SERVICE.reserve("SETUP", CODE, "expired"));
    rollbackOuter();
    check(number("SELECT ATTEMPT_COUNT FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='expired'") == 0, "Expiry does not consume an attempt");
  }

  private static void openFailureRestoresOuterTransaction() throws java.lang.Exception {
    beginOuter();
    DataAccessManager.failOpen = true;
    try {
      try { SERVICE.reserve("SETUP", CODE, "open-failure"); throw new AssertionError("Expected open failure"); }
      catch (com.ofss.digx.infra.exceptions.Exception expected) {
        check(expected.getCause() instanceof IllegalStateException, "Original open failure preserved");
      }
    } finally { DataAccessManager.failOpen = false; rollbackOuter(); }
  }

  private static void targetStateControlsPurpose() throws java.lang.Exception {
    check("RESET".equals(SERVICE.codePurpose("PARTY", "USER@PARTY", "RESET")), "Active target selects RESET");
    check("SETUP".equals(SERVICE.codePurpose("PARTY", "NEW_USER", null)), "Creating a new user needs no saved profile");
    sql("INSERT INTO HTH_BEA.HTH_USER_PROFILE VALUES ('OTHER_PARTY', 'USER@OTHER_PARTY')");
    check("SETUP".equals(SERVICE.codePurpose("OTHER_PARTY", "USER", null)), "Maker session credential is not the target credential");
    reject("DIGX_CZ_HTH_PW_009", () -> SERVICE.codePurpose("PARTY", "USER", "SETUP"));
    sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CREDENTIAL SET CREDENTIAL_STATUS='LOCKED'");
    reject("DIGX_CZ_HTH_API_PASSWORD_006", () -> SERVICE.codePurpose("PARTY", "USER", null));
    sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CREDENTIAL SET CREDENTIAL_STATUS='ACTIVE'");
    check(RemoteCredentialFixture.calls == 0, "Default DATABASE generation does not depend on UAM");
    java.util.prefs.Preferences config = ConfigurationFactory.getInstance().getRootConfigurations();
    config.put("HTH_API_PASSWORD.STORAGE_BACKEND", "UAM");
    try {
      RemoteCredentialFixture.state = "NOT_SETUP";
      check("SETUP".equals(SERVICE.codePurpose("PARTY", "USER", null)), "UAM mode uses remote state despite local ACTIVE credential");
      RemoteCredentialFixture.state = "ACTIVE";
      check("RESET".equals(SERVICE.codePurpose("OTHER_PARTY", "USER@OTHER_PARTY", null)), "UAM target ACTIVE selects RESET");
      check("OTHER_PARTY".equals(RemoteCredentialFixture.lastParty) && "USER".equals(RemoteCredentialFixture.lastUser), "UAM receives target identity");
      RemoteCredentialFixture.state = "UNKNOWN";
      reject("DIGX_CZ_HTH_API_PASSWORD_009", () -> SERVICE.codePurpose("PARTY", "USER", null));
      RemoteCredentialFixture.state = "FAIL";
      reject("REMOTE_UNAVAILABLE", () -> SERVICE.codePurpose("PARTY", "USER", null));
    } finally {
      config.remove("HTH_API_PASSWORD.STORAGE_BACKEND");
      RemoteCredentialFixture.calls = 0;
    }
  }

  private static void codeFailuresRemainDistinct() throws java.lang.Exception {
    seed("bad-cipher", "SETUP");
    String encrypted = (String) value("SELECT CODE_CIPHER FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='bad-cipher'");
    java.util.prefs.Preferences config = ConfigurationFactory.getInstance().getRootConfigurations();
    String originalKey = config.get("HTH_API_PASSWORD.CODE_CIPHER_KEY", null);
    byte[] differentKey = new byte[32];
    new SecureRandom().nextBytes(differentKey);
    config.put("HTH_API_PASSWORD.CODE_CIPHER_KEY", Base64.getEncoder().encodeToString(differentKey));
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("transaction-test");
    final StringBuilder captured = new StringBuilder();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      public void publish(java.util.logging.LogRecord record) {
        check(record.getThrown() == null, "Code decryption diagnostic must not attach exception details");
        captured.append(java.text.MessageFormat.format(record.getMessage(), record.getParameters()));
      }
      public void flush() { }
      public void close() { }
    };
    logger.addHandler(handler);
    beginOuter();
    try { reject("DIGX_CZ_HTH_API_PASSWORD_009", () -> SERVICE.reserve("SETUP", CODE, "bad-cipher")); }
    finally { config.put("HTH_API_PASSWORD.CODE_CIPHER_KEY", originalKey); logger.removeHandler(handler); rollbackOuter(); }
    check(captured.toString().contains("CODE_DECRYPT"), "Stored ciphertext failure has a separate stage");
    check(!captured.toString().contains(CODE) && !captured.toString().contains(encrypted)
        && !captured.toString().contains(originalKey), "No Code, ciphertext or key in diagnostics");
    check(number("SELECT ATTEMPT_COUNT FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='bad-cipher'") == 0, "Configuration failure does not count as user mismatch");
    sql("DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='bad-cipher'");
  }

  private static void changingPurposeDoesNotChangeCipher() throws java.lang.Exception {
    seed("purpose", "SETUP");
    Object originalCipher = value("SELECT CODE_CIPHER FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='purpose'");
    beginOuter();
    reject("DIGX_CZ_HTH_API_PASSWORD_002", () -> SERVICE.reserve("RESET", CODE, "purpose-wrong"));
    rollbackOuter();
    sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET PURPOSE='RESET' WHERE ID='purpose'");
    beginOuter();
    check("purpose".equals(SERVICE.reserve("RESET", CODE, "purpose")), "Changing an unused Code purpose preserves decryption");
    rollbackOuter();
    check(originalCipher.equals(value("SELECT CODE_CIPHER FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='purpose'")), "Purpose is not part of stored ciphertext");
    sql("DELETE FROM HTH_BEA.HTH_API_PASSWORD_OPERATION WHERE REQUEST_ID='purpose'");
    for (String status : new String[] {"USED", "PENDING", "INVALID", "IN_PROGRESS", "UNKNOWN"}) {
      sql("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS='" + status + "' WHERE ID='purpose'");
      beginOuter();
      reject("DIGX_CZ_HTH_API_PASSWORD_002", () -> SERVICE.reserve("RESET", CODE, "purpose-reuse"));
      rollbackOuter();
    }
    sql("DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='purpose'");
  }

  private static void seed(String id, String purpose) throws SQLException {
    try (PreparedStatement insert = db.prepareStatement("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE "
        + "(ID,PARTY_ID,USER_NAME,PURPOSE,CODE_CIPHER,STATUS,OBJECT_STATUS,EXPIRY_TIME,ATTEMPT_COUNT,MAX_ATTEMPTS,CREATION_DATE) "
        + "VALUES (?,'PARTY','USER',?,?,'ACTIVE','A',?,0,5,CURRENT_TIMESTAMP)")) {
      insert.setString(1, id); insert.setString(2, purpose);
      insert.setString(3, HthApiPasswordCrypto.encrypt(CODE, HthApiPasswordCrypto.codeCipherKey()));
      insert.setTimestamp(4, new Timestamp(System.currentTimeMillis() + 3600000));
      insert.executeUpdate();
    }
  }
  private interface Checked { void run() throws java.lang.Exception; }
  private static void reject(String code, Checked action) throws java.lang.Exception {
    try { action.run(); throw new AssertionError("Expected " + code); }
    catch (com.ofss.digx.infra.exceptions.Exception expected) { check(code.equals(expected.getMessage()), "Business error preserved: " + expected); }
  }
  private static void sql(String sql) throws SQLException { try (Statement s = db.createStatement()) { s.execute(sql); } }
  private static Object value(String sql) throws SQLException {
    try (Statement s = db.createStatement(); ResultSet rows = s.executeQuery(sql)) { check(rows.next(), "Expected row"); return rows.getObject(1); }
  }
  private static int number(String sql) throws SQLException { return ((Number) value(sql)).intValue(); }
  private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
