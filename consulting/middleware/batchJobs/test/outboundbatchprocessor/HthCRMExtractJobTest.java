package outboundbatchprocessor;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;

/** Exercises extraction/publishing with an isolated JDBC stand-in; no bank DB required. */
public final class HthCRMExtractJobTest {
    private HthCRMExtractJobTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("hth-crm-1293-test-");
        try {
            LocalDate day = LocalDate.of(2026, 9, 11);
            HthCRMExtractJob.Configuration config = new HthCRMExtractJob.Configuration(
                    directory, "hth_crm_{date}.csv", Arrays.asList("EVENT_ID", "ACTIVITY_KEY"),
                    true, true);
            JdbcStandIn db = new JdbcStandIn(new Object[][] {
                    { "id-1", "USER,EDIT" }, { "id-2", "A\"B\nC" }
            });
            run(db, config, day);
            Path output = directory.resolve("hth_crm_20260911.csv");
            require(Files.exists(output), "daily file missing");
            require("EVENT_ID,ACTIVITY_KEY\nid-1,\"USER,EDIT\"\nid-2,\"A\"\"B\nC\"\n"
                    .equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
                    "CSV escaping or row completeness failed");
            require(db.query.contains("FROM HTH_BEA.HTH_CRM_EVENT_DETAILS"), "wrong source table");
            require(!db.query.contains("DIGX_CZ"), "BCO source leaked into HTH job");
            require(Timestamp.valueOf("2026-09-11 00:00:00").equals(db.start), "wrong start bound");
            require(Timestamp.valueOf("2026-09-12 00:00:00").equals(db.end), "wrong end bound");
            require(db.lastCount == 2 && "SUCCESS".equals(db.lastStatus), "wrong run result");

            db.rows = new Object[][] { { "id-3", "RESET" } };
            run(db, config, day);
            require("EVENT_ID,ACTIVITY_KEY\nid-3,RESET\n"
                    .equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
                    "dated rerun did not replace the same-day file");

            db.failSelect = true;
            boolean failed = false;
            try {
                run(db, config, day);
            } catch (SQLException expected) {
                failed = true;
            }
            require(failed && "FAILED".equals(db.lastStatus), "failure was not recorded");
            require("EVENT_ID,ACTIVITY_KEY\nid-3,RESET\n"
                    .equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
                    "failed rerun damaged the last good file");
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                require(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                        "failed rerun left a partial file");
            }
            db.failSelect = false;

            db.rows = new Object[0][0];
            run(db, config, day);
            require("EVENT_ID,ACTIVITY_KEY\n"
                    .equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
                    "header-only empty policy failed");
            require(db.lastCount == 0 && "SUCCESS".equals(db.lastStatus), "empty run must succeed");

            HthCRMExtractJob.Configuration noFile = new HthCRMExtractJob.Configuration(
                    directory, "hth_crm_{date}.csv", Arrays.asList("EVENT_ID", "ACTIVITY_KEY"),
                    true, false);
            run(db, noFile, day);
            require(!Files.exists(output), "NO_FILE policy left a stale file");
            require(db.lastFileName == null, "NO_FILE run has a filename");

            Path configuration = directory.resolve("configuration.properties");
            Files.write(configuration, ("output.directory=" + directory + "\n"
                    + "file.name.pattern=hth_{date}.csv\n"
                    + "csv.columns=EVENT_ID,AR_TOKEN\n").getBytes(StandardCharsets.UTF_8));
            boolean rejected = false;
            try {
                HthCRMExtractJob.Configuration.load(configuration);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "secret-bearing source column was accepted");
            System.out.println("HthCRMExtractJobTest PASS");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort test cleanup only.
                    }
                });
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void run(JdbcStandIn db, HthCRMExtractJob.Configuration config, LocalDate date)
            throws Exception {
        HthCRMExtractJob.Options options = new HthCRMExtractJob.Options(
                null, date, "MANUAL");
        new HthCRMExtractJob.HthCRMExtractJobRunner(db.connection(), config, options).run();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class JdbcStandIn {
        Object[][] rows;
        boolean failSelect;
        String query;
        Timestamp start;
        Timestamp end;
        String lastStatus;
        long lastCount;
        String lastFileName;

        JdbcStandIn(Object[][] rows) {
            this.rows = rows;
        }

        Connection connection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return statement((String) args[0]);
                }
                if ("close".equals(method.getName()) || "setAutoCommit".equals(method.getName())) {
                    return null;
                }
                throw new AssertionError("Unexpected Connection." + method.getName());
            });
        }

        PreparedStatement statement(String sql) {
            return proxy(PreparedStatement.class, new InvocationHandler() {
                Object[] binds = new Object[10];

                public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length == 2
                            && args[0] instanceof Integer) {
                        binds[((Integer) args[0]) - 1] = args[1];
                        return null;
                    }
                    if ("setFetchSize".equals(name) || "close".equals(name)) {
                        return null;
                    }
                    if ("executeQuery".equals(name)) {
                        if (failSelect) {
                            throw new SQLException("Synthetic query failure");
                        }
                        query = sql;
                        start = (Timestamp) binds[0];
                        end = (Timestamp) binds[1];
                        return resultSet(rows);
                    }
                    if ("executeUpdate".equals(name)) {
                        if (sql.startsWith("UPDATE")) {
                            lastStatus = (String) binds[1];
                            lastCount = ((Number) binds[2]).longValue();
                            lastFileName = (String) binds[3];
                        }
                        return 1;
                    }
                    throw new AssertionError("Unexpected PreparedStatement." + name);
                }
            });
        }

        ResultSet resultSet(Object[][] values) {
            return proxy(ResultSet.class, new InvocationHandler() {
                int row = -1;

                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        row++;
                        return row < values.length;
                    }
                    if ("getObject".equals(name)) {
                        return values[row][((Integer) args[0]) - 1];
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    throw new AssertionError("Unexpected ResultSet." + name);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }
}
