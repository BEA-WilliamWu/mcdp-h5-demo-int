package outboundbatchprocessor.hth;

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

/** Run directly with javac/java; no Oracle server or BCO batch execution needed. */
public final class HthCrmExtractJobTest {
    private static List<String[]> rows = Collections.emptyList();
    private static Timestamp from;
    private static Timestamp to;

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static final Driver DRIVER = new Driver() {
        public boolean acceptsURL(String url) { return "jdbc:hth-crm-test".equals(url); }
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

    public static void main(String[] args) throws Exception {
        DriverManager.registerDriver(DRIVER);
        Path dir = Files.createTempDirectory("hth-crm-test-");
        LocalDate day = LocalDate.of(2026, 9, 30);
        String name = HthCrmExtractJob.fileName(day, null);
        Path output = dir.resolve(name);
        check(HthCrmExtractJob.HEADERS.length == 61, "Expected 61 API mapping fields");
        check(HthCrmExtractJob.SQL.contains("FROM HTH_BEA.HTH_CRM_EVENT"), "Wrong source table");
        check(!HthCrmExtractJob.SQL.contains("DIGX_CZ_CRM"), "BCO source leaked into HTH query");
        rows = Collections.singletonList(row("id-1", "a,\"b\"\nline"));
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 1,
            "Wrong record count");
        String first = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        check(first.contains("\"a,\\\"b\\\"\\nline\""), "MTB CSV escaping failed");
        check(first.contains("\"12:34:56\""), "Event_Time was not formatted as HH:MM:SS");
        check(from.equals(Timestamp.valueOf("2026-09-30 00:00:00"))
            && to.equals(Timestamp.valueOf("2026-10-01 00:00:00")), "Wrong day bounds");
        String dataName = HthCrmExtractJob.fileName(day.minusDays(1), null);
        HthCrmExtractJob.run(day.minusDays(1), dir, dataName,
            "jdbc:hth-crm-test", "user", "pass");
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 1,
            "Rerun failed");
        check(first.equals(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)),
            "Rerun changed output");
        rows = Collections.emptyList();
        check(HthCrmExtractJob.run(day, dir, name, "jdbc:hth-crm-test", "user", "pass") == 0,
            "Empty day failed");
        String empty = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        check(empty.split("\n", -1).length == 2, "Empty file is not header-only");
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
        System.out.println("DATA_CSV=" + dir.resolve(dataName));
        System.out.println("CSV=" + output);
        System.out.println("HTH CRM extract tests passed");
    }
}
