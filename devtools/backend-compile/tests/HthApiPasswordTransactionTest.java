import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
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
      schema();
      failedAttemptsSurviveOuterRollback();
      setupAndResetCommitTogether();
      failedCompletionRollsBackAllWrites();
      expiredCodeDoesNotReserve();
      openFailureRestoresOuterTransaction();
      check(DataAccessManager.opened == DataAccessManager.closed, "Every independent ORM session closed");
      check(TransactionHelper.suspends == TransactionHelper.resumes, "Every suspended transaction restored");
      System.out.println("PASS: actual OBDX/EclipseLink ORM + H2: failed-attempt commit/lockout, SETUP/RESET writes, atomic rollback, expiry, outer restoration and session cleanup");
    } finally {
      if (outerEntityManager != null && outerEntityManager.isOpen()) outerEntityManager.close();
      db.close();
      DataAccessManager.factory.close();
    }
  }

  private static void schema() throws SQLException {
    sql("CREATE SCHEMA HTH_BEA");
    sql("CREATE TABLE OUTER_WORK (ID INT)");
    sql("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID VARCHAR(40) PRIMARY KEY, PARTY_ID VARCHAR(40), "
        + "USER_NAME VARCHAR(80), PURPOSE VARCHAR(10), CODE_CIPHER VARCHAR(200), STATUS VARCHAR(20), "
        + "OBJECT_STATUS CHAR(1), EXPIRY_TIME TIMESTAMP, ATTEMPT_COUNT INT, MAX_ATTEMPTS INT, "
        + "CREATION_DATE TIMESTAMP, LAST_UPDATE_DATE TIMESTAMP, REQUEST_ID VARCHAR(40), "
        + "USED_TIME TIMESTAMP, LAST_UPDATED_BY VARCHAR(80))");
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
      seed(id, operation);
      beginOuter();
      check(id.equals(SERVICE.reserve(operation, CODE, id)), "Correct code reserved");
      rollbackOuter();
      check("IN_PROGRESS".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='" + id + "'")), "Reservation committed");
      beginOuter();
      SERVICE.complete(operation, id, id, operation + "-HASH-FIXTURE");
      rollbackOuter();
      check("USED".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID='" + id + "'")), "Code consumed");
      check("SUCCESS".equals(value("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_OPERATION WHERE REQUEST_ID='" + id + "'")), "Operation completed");
      check((operation + "-HASH-FIXTURE").equals(value("SELECT PASSWORD_HASH FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL")), "Credential committed");
      check(number("SELECT CREDENTIAL_VERSION FROM HTH_BEA.HTH_API_PASSWORD_STATE") == ++version, "State version committed");
    }
  }

  private static void failedCompletionRollsBackAllWrites() throws java.lang.Exception {
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
    check("RESET-HASH-FIXTURE".equals(value("SELECT PASSWORD_HASH FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL")), "Password write rolled back");
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
