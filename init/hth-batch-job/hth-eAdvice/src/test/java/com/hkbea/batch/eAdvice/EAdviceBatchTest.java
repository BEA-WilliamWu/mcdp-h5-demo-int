package com.hkbea.batch.eAdvice;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EAdviceBatchTest {
    private static final String DAY = "20261126";
    private static final String PASSWORD = "local-h2-fixture";

    @TempDir Path directory;
    private Path output;
    private Path config;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        output = Files.createDirectory(directory.resolve("output"));
        config = Files.createDirectory(directory.resolve("config"));
        jdbcUrl = "jdbc:h2:file:" + directory.resolve("eadvice") + ";MODE=Oracle";
        Files.writeString(config.resolve("db.ibank.batch.properties"), """
                jdbc.driver.class=org.h2.Driver
                jdbc.conn.string=%s
                jdbc.conn.username=sa
                jdbc.conn.password=%s
                jdbc.conn.autocommit=false
                """.formatted(jdbcUrl, PASSWORD));
        Files.writeString(config.resolve("system_batch.properties"), "batch.output.dir=./output\n");

        executeSql("CREATE SCHEMA HTH_BEA");
        executeSql("""
                CREATE TABLE HTH_BEA.HTH_PAYMENT_PAYOUT (
                    PAYMENT_ID VARCHAR(40) PRIMARY KEY, PAYMENT_TYPE VARCHAR(12), IPE_STATUS VARCHAR(15),
                    BATCH_STATUS_FLAG VARCHAR(15), STATUS_FLAG VARCHAR(20), VALUEDATE DATE, CREATION_DATE DATE,
                    PARTY_ID VARCHAR(20), DEBIT_INTERNAL_ACCOUNT VARCHAR(20), PRODUCT_CODE VARCHAR(4),
                    DEBIT_ACCOUNT_CUR VARCHAR(3), INSTD_AMOUNT DECIMAL(15,2), BENEF_EMAIL VARCHAR(80),
                    BENEF_MOBILE VARCHAR(20), BENEF_FPSID VARCHAR(20), PAYEE_ID VARCHAR(20),
                    DEBIT_ACCOUNT_ID VARCHAR(20), CREDIT_ACCOUNT_NAME VARCHAR(140), CREDIT_ADDRESS_LINE1 VARCHAR(70),
                    CREDIT_ADDRESS_LINE2 VARCHAR(70), REMARKS VARCHAR(140), FPS_TXN_REF VARCHAR(9)
                )
                """);
        executeSql("CREATE TABLE DIGX_PI_PARTY_ACCOUNTS (ACCOUNT_NUMBER VARCHAR(40), MASTER_ACCOUNT_NUMBER VARCHAR(40))");
        executeSql("INSERT INTO DIGX_PI_PARTY_ACCOUNTS VALUES ('001501238912123456~6805~SAV', 'master')");
        executeSql("CREATE TABLE DIGX_CZ_AC_EACCTPREF (ACCOUNT_NUMBER VARCHAR(40), NORMAL_ADVICE_SETTING VARCHAR(4))");
        executeSql("INSERT INTO DIGX_CZ_AC_EACCTPREF VALUES ('master', 'PEAD')");
        insertPayment("p1");
    }

    @ParameterizedTest
    @CsvSource({"1,A,1", "2,B,2", "3,C,3", "P,P,P"})
    void writesExpectedFileAndProtectsItOnSameDayRerun(String task, String letter, String flag)
            throws Exception {
        runJob(task, 0);
        Path file = outputFile(letter);
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.length() == 500));
        assertTrue(lines.get(2).startsWith("99000000010020261127"));
        assertPaymentFlags("p1", flag);
        byte[] original = Files.readAllBytes(file);

        assertTrue(runJob(task, 0).contains("retained file="));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertPaymentFlags("p1", flag);

        insertPayment("late");
        assertTrue(runJob(task, 50).contains("Output already exists and was retained"));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertPaymentFlags("p1", flag);
        assertPaymentFlags("late", null);
        assertNoTemporaryFiles();
    }

    @Test
    void failedMarkLeavesNoNewDeliveryFileAndCanBeRetried() throws Exception {
        executeSql("""
                ALTER TABLE HTH_BEA.HTH_PAYMENT_PAYOUT ADD CONSTRAINT reject_flag_3
                CHECK (BATCH_STATUS_FLAG <> '3')
                """);
        runJob("3", 50);
        assertFalse(Files.exists(outputFile("C")));
        assertPaymentFlags("p1", null);
        assertNoTemporaryFiles();

        executeSql("ALTER TABLE HTH_BEA.HTH_PAYMENT_PAYOUT DROP CONSTRAINT reject_flag_3");
        runJob("3", 0);
        assertTrue(Files.isRegularFile(outputFile("C")));
        assertPaymentFlags("p1", "3");
    }

    @Test
    void concurrentRunLeavesExistingFileAndPaymentsUnchanged() throws Exception {
        Path file = outputFile("P");
        Files.writeString(file, "original delivery\n");
        Path lock = file.resolveSibling(file.getFileName() + ".lock");
        try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            assertTrue(runJob("P", 50).contains("eAdvice already running"));
        }
        assertEquals("original delivery\n", Files.readString(file));
        assertPaymentFlags("p1", null);
        assertNoTemporaryFiles();
    }

    private Path outputFile(String letter) {
        return output.resolve("EBKP.HOSTPC.HTHTOEBK.EBFPADV." + letter + ".D" + DAY + ".TXT");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", PASSWORD);
    }

    private void executeSql(String sql) throws SQLException {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void insertPayment(String id) throws SQLException {
        try (var connection = connect(); var statement = connection.prepareStatement("""
                INSERT INTO HTH_BEA.HTH_PAYMENT_PAYOUT
                (PAYMENT_ID, PAYMENT_TYPE, IPE_STATUS, STATUS_FLAG, VALUEDATE, CREATION_DATE, PARTY_ID,
                 DEBIT_INTERNAL_ACCOUNT, PRODUCT_CODE, DEBIT_ACCOUNT_CUR, INSTD_AMOUNT, DEBIT_ACCOUNT_ID, CREDIT_ACCOUNT_NAME)
                VALUES (?, 'FPS', 'SUCCESS', 'ORIGINAL', TIMESTAMP '2026-11-26 15:00:00', TIMESTAMP '2026-11-26 15:00:00',
                        '01512389123456', '001501238912123456', '6805', 'HKD', 100.00, '01512389123456', 'TEST PAYEE')
                """)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private void assertPaymentFlags(String id, String batchFlag) throws SQLException {
        try (var connection = connect(); var statement = connection.prepareStatement("""
                SELECT BATCH_STATUS_FLAG, STATUS_FLAG FROM HTH_BEA.HTH_PAYMENT_PAYOUT WHERE PAYMENT_ID = ?
                """)) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(batchFlag, rows.getString(1));
                assertEquals("ORIGINAL", rows.getString(2));
            }
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(output)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }

    private String runJob(String task, int expectedExitCode) throws Exception {
        Path log = directory.resolve("job.log");
        Process job = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dconfig.path=" + config,
                "-Dbatch.log.dir=" + directory.resolve("logs"),
                "-cp", System.getProperty("java.class.path"),
                EAdviceBatchMain.class.getName(), task, DAY)
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!job.waitFor(20, TimeUnit.SECONDS)) {
            job.destroyForcibly();
            fail("eAdvice job did not exit");
        }
        String messages = Files.readString(log);
        assertEquals(expectedExitCode, job.exitValue(), messages);
        return messages;
    }
}
