package outboundbatchprocessor.hth;

import java.io.OutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import util.AESUtil;

/** Run directly with javac/java; no Oracle server or BCO batch execution needed. */
public final class HthCrmExtractJobTest {
    private static List<String[]> rows = Collections.emptyList();
    private static Timestamp from;
    private static Timestamp to;

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static final Driver DRIVER = new Driver() {
        public boolean acceptsURL(String url) {
            return "jdbc:hth-crm-test".equals(url)
                || "jdbc:oracle:thin:@//hth-db:1521/hth-service".equals(url);
        }
        public Connection connect(String url, Properties props) {
            if (!acceptsURL(url)) return null;
            return (Connection) proxy(Connection.class, new InvocationHandler() {
                public Object invoke(Object self, Method method, Object[] args) {
                    if ("prepareStatement".equals(method.getName())) {
                        check(HthCrmExtractJob.SQL.equals(args[0]), "SQL changed");
                        return proxy(PreparedStatement.class, new InvocationHandler() {
                            public Object invoke(Object statement, Method call, Object[] values) {
                                if ("setTimestamp".equals(call.getName())) {
                                    if (((Integer) values[0]) == 1) from = (Timestamp) values[1];
                                    else to = (Timestamp) values[1];
                                }
                                if ("executeQuery".equals(call.getName())) {
                                    return proxy(ResultSet.class, new InvocationHandler() {
                                        int current = -1;
                                        public Object invoke(Object result, Method action, Object[] input) {
                                            if ("next".equals(action.getName())) return ++current < rows.size();
                                            if ("getString".equals(action.getName())) {
                                                return rows.get(current)[((Integer) input[0]) - 1];
                                            }
                                            return null;
                                        }
                                    });
                                }
                                return null;
                            }
                        });
                    }
                    return null;
                }
            });
        }
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    };

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String[] row(String id, String eventRemark) {
        String[] values = new String[HthCrmExtractJob.HEADERS.length + 1];
        values[0] = id;
        values[1] = "50";
        values[2] = "1";
        values[5] = "123456";
        for (int i = 0; i < HthCrmExtractJob.HEADERS.length; i++) {
            if ("Event_Rem".equals(HthCrmExtractJob.HEADERS[i])) values[i + 1] = eventRemark;
        }
        return values;
    }

    private static void checkFile(String content, String date, long count) {
        String[] lines = content.split("\n", -1);
        check(lines.length == count + 4 && lines[lines.length - 1].isEmpty(),
            "Expected header, column names, data rows, trailer and final LF");
        String header = lines[0];
        String trailer = lines[lines.length - 2];
        check(header.length() == 100 && header.equals("00" + date + "HTHMTB"
            + String.join("", Collections.nCopies(84, " "))), "Invalid header layout");
        check(trailer.length() == 100 && trailer.startsWith("99"), "Invalid trailer length/type");
        check(trailer.substring(2, 17).equals(String.format(java.util.Locale.ROOT, "%015d", count)),
            "Trailer counts controls or column names");
        check(trailer.substring(17, 32).equals(String.format(java.util.Locale.ROOT,
            "%015d", Long.parseLong(date) + count)), "Trailer checksum uses the wrong date/count");
        check(trailer.substring(32).equals(String.join("", Collections.nCopies(68, " "))),
            "Trailer file size and filler must be spaces");
        check(lines[1].startsWith("\"record_type\",\"Filler_01\",\"Event_Id\","),
            "Existing column names were not retained");
        check(!content.contains("\r"), "Output must use LF");
    }

    private static String runMain(Path configDirectory, Path outputDirectory, boolean fail)
            throws Exception {
        ProcessBuilder command = new ProcessBuilder(
            java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), HthCrmExtractJobTest.class.getName(),
            fail ? "--run-job-failure" : "--run-job");
        command.environment().put("batchConfigPath", configDirectory.toString());
        command.environment().put("HTH_CRM_OUTPUT_DIR", outputDirectory.toString());
        command.environment().put("HTH_CRM_FILE_PATTERN", "hthtomtb_mvp1apis_D{date}.csv");
        command.redirectErrorStream(true);
        Process process = command.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        }
        String log = new String(output.toByteArray(), StandardCharsets.UTF_8);
        check(process.waitFor() == (fail ? 1 : 0), "Unexpected main exit status: " + log);
        check(!log.contains("test-password"), "Password leaked in main log");
        return log;
    }

    public static void main(String[] args) throws Exception {
        DriverManager.registerDriver(DRIVER);
        if (args.length == 1 && args[0].startsWith("--run-job")) {
            rows = Collections.singletonList(row("--run-job-failure".equals(args[0]) ? null : "id-1",
                "中文,\\path\r\n\"quoted\""));
            HthCrmExtractJob.main(new String[] {"--date", "20260930"});
            return;
        }
        Path dir = Files.createTempDirectory("hth-crm-test-");
        Path configDir = Files.createTempDirectory("hth-crm-config-test-");
        String passphrase = "hth-crm-test-passphrase";
        Properties encrypted = new Properties();
        encrypted.setProperty("encr_passphrase", AESUtil.encrypt(passphrase, AESUtil.DEFAULT_KEY));
        encrypted.setProperty("db.hostname", AESUtil.encrypt("hth-db", passphrase));
        encrypted.setProperty("db.port", AESUtil.encrypt("1521", passphrase));
        encrypted.setProperty("db.servicename", AESUtil.encrypt("hth-service", passphrase));
        encrypted.setProperty("db.username", AESUtil.encrypt("hth-user", passphrase));
        encrypted.setProperty("db.password", AESUtil.encrypt("test-password", passphrase));
        Path configFile = configDir.resolve("hth_crm_batch_config.properties");
        try (OutputStream output = Files.newOutputStream(configFile)) {
            encrypted.store(output, "HTH test configuration");
        }
        HthCrmExtractJob.DatabaseConfig database = HthCrmExtractJob.loadDatabaseConfig(configDir);
        check("jdbc:oracle:thin:@//hth-db:1521/hth-service".equals(database.url),
            "HTH database URL did not use the encrypted HTH configuration");
        check("hth-user".equals(database.user) && "test-password".equals(database.password),
            "HTH database credentials did not decrypt");
        String successLog = runMain(configDir, dir, false);
        Path mainOutput = dir.resolve("hthtomtb_mvp1apis_D260930.csv");
        byte[] published = Files.readAllBytes(mainOutput);
        checkFile(new String(published, StandardCharsets.UTF_8), "20260930", 1);
        check(successLog.contains("stage=END status=SUCCESS")
            && successLog.contains(" count=1 size=" + published.length + " file=" + mainOutput.getFileName()),
            "Latest count/byte-size/filename logging changed: " + successLog);
        String failureLog = runMain(configDir, dir, true);
        check(failureLog.contains("stage=END status=FAILED") && failureLog.contains(" size=-1 "),
            "Failure log lost the latest size field: " + failureLog);
        check(Arrays.equals(published, Files.readAllBytes(mainOutput)),
            "Main failure replaced a published complete file");
        encrypted.remove("db.password");
        try (OutputStream output = Files.newOutputStream(configFile)) {
            encrypted.store(output, "HTH test configuration");
        }
        boolean missingCredentialRejected = false;
        try {
            HthCrmExtractJob.loadDatabaseConfig(configDir);
        } catch (IllegalArgumentException expected) {
            missingCredentialRejected = expected.getMessage().contains("db.password");
        }
        check(missingCredentialRejected, "Missing HTH database credential was accepted");
        LocalDate day = LocalDate.of(2026, 9, 30);
        String name = HthCrmExtractJob.fileName(day, null);
        check("hthtomtb_mvp1apis_D260930.csv".equals(name), "Wrong v16 filename");
        check("HTH_CRM_260930.csv".equals(HthCrmExtractJob.fileName(day, "HTH_CRM_{date}.csv")),
            "Latest filename override must retain its six-digit date");
        check("hthtomtb_mvp1apis_D260930.csv".equals(
            HthCrmExtractJob.fileName(day, "hthtomtb_mvp1apis_D{date}.csv")),
            "Latest deployed filename pattern changed");
        check(day.equals(HthCrmExtractJob.date(new String[] {"--date", "20260930"})),
            "Explicit extraction date changed");
        Path output = dir.resolve(name);
        check(HthCrmExtractJob.HEADERS.length == 61, "Expected 61 API mapping fields");
        check(HthCrmExtractJob.SQL.contains("FROM HTH_BEA.HTH_CRM_EVENT"), "Wrong source table");
        check(!HthCrmExtractJob.SQL.contains("DIGX_CZ_CRM"), "BCO source leaked into HTH query");
        rows = Collections.singletonList(row("id-1", "a,\"b\"\nline"));
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 1,
            "Wrong record count");
        String first = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        checkFile(first, "20260930", 1);
        check(first.contains("\"a,\\\"b\\\"\\nline\""), "MTB CSV escaping failed");
        check(first.contains("\"12:34:56\""), "Event_Time was not formatted as HH:MM:SS");
        check(from.equals(Timestamp.valueOf("2026-09-30 00:00:00"))
            && to.equals(Timestamp.valueOf("2026-10-01 00:00:00")), "Wrong day bounds");
        String dataName = HthCrmExtractJob.fileName(day.minusDays(1), null);
        HthCrmExtractJob.run(day.minusDays(1), dir, dataName,
            "jdbc:hth-crm-test", "user", "pass");
        checkFile(new String(Files.readAllBytes(dir.resolve(dataName)), StandardCharsets.UTF_8),
            "20260929", 1);
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 1,
            "Rerun failed");
        check(first.equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
            "Rerun changed output");
        rows = Collections.emptyList();
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 0,
            "Empty day failed");
        String empty = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        checkFile(empty, "20260930", 0);
        rows = Arrays.asList(row("id-1", "中文,\\path\r\n\"quoted\""), row("id-2", null));
        String multiName = HthCrmExtractJob.fileName(day.minusDays(2), null);
        check(HthCrmExtractJob.run(day.minusDays(2), dir, multiName,
            "jdbc:hth-crm-test", "user", "pass") == 2, "Multiple rows not counted");
        checkFile(new String(Files.readAllBytes(dir.resolve(multiName)), StandardCharsets.UTF_8),
            "20260928", 2);
        rows = Arrays.asList(row("id-1", null), row(null, null));
        boolean failed = false;
        try {
            HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass");
        } catch (IllegalStateException expected) {
            failed = true;
        }
        check(failed, "Missing table ID was accepted");
        check(empty.equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
            "Failure changed published output");
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            check(!files.anyMatch(file -> file.toString().endsWith(".tmp")), "Failed output left a temp file");
        }
        StringWriter sample = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(sample)) {
            HthCrmExtractJob.writeTrailer(writer, LocalDate.of(2025, 1, 6), 2);
        }
        check(sample.toString().startsWith("99000000000000002000000020250108"),
            "Trailer does not match the v16 date + count example");
        // Counts may exceed int capacity; numeric fields must remain exactly 15 digits.
        StringWriter large = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(large)) {
            HthCrmExtractJob.writeTrailer(writer, day, 2147483648L);
        }
        check(large.toString().substring(2, 17).equals("000002147483648"), "Large count overflow");
        for (long invalid : new long[] {-1, 999999999999999L, Long.MAX_VALUE}) {
            boolean rejected = false;
            try (BufferedWriter writer = new BufferedWriter(new StringWriter())) {
                HthCrmExtractJob.writeTrailer(writer, day, invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid/overflowing trailer field accepted");
        }
        System.out.println("DATA_CSV=" + dir.resolve(dataName));
        System.out.println("MULTI_CSV=" + dir.resolve(multiName));
        System.out.println("CSV=" + output);
        System.out.println("HTH CRM extract tests passed");
    }
}
