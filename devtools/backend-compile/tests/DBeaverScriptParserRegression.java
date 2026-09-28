/*
 * Offline regression using unmodified DBeaver Community 26.2.1 parser and Oracle dialect jars.
 * Application services and JDBC metadata are stubs; the data source uses the constructor that
 * does not initialize connections. No database connection or SQL execution is performed.
 * Usage: DBeaverScriptParserRegression <schema.sql> <config.sql> [--output <directory>]
 */
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.eclipse.core.internal.runtime.AdapterManager;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ModelPreferences.SQLScriptStatementDelimiterMode;
import org.jkiss.dbeaver.ext.oracle.model.OracleSQLDialect;
import org.jkiss.dbeaver.ext.oracle.sql.OracleDialectAdapterFactory;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.app.DBPApplicationWorkbench;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.preferences.SimplePreferenceStore;
import org.jkiss.dbeaver.model.sql.SQLScriptElement;
import org.jkiss.dbeaver.model.sql.parser.SQLScriptParser;
import org.jkiss.dbeaver.model.sql.registry.SQLCommandsRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.ui.DBPPlatformUI;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.sql.SQLDialect;

public class DBeaverScriptParserRegression {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && !(args.length == 4 && args[2].equals("--output"))) {
            throw new IllegalArgumentException("Supply schema and config SQL paths, optionally --output directory");
        }
        var prefs = new SimplePreferenceStore() { public void save() {} };
        prefs.setValue(ModelPreferences.SCRIPT_STATEMENT_DELIMITER, ";");
        prefs.setValue(ModelPreferences.SCRIPT_IGNORE_NATIVE_DELIMITER, false);
        prefs.setValue(ModelPreferences.QUERY_REMOVE_TRAILING_DELIMITER, true);
        // OSGi normally installs these application services. No client @commands occur in these inputs.
        var registryConstructor = SQLCommandsRegistry.class.getDeclaredConstructor();
        registryConstructor.setAccessible(true);
        Field registryInstance = SQLCommandsRegistry.class.getDeclaredField("instance");
        registryInstance.setAccessible(true);
        registryInstance.set(null, registryConstructor.newInstance());
        DBPPlatform platform = (DBPPlatform) Proxy.newProxyInstance(
            DBeaverScriptParserRegression.class.getClassLoader(), new Class<?>[]{DBPPlatform.class},
            (proxy, method, values) -> {
                if (method.getName().equals("getPreferenceStore")) return prefs;
                if (method.getReturnType() == boolean.class) return false;
                return null;
            });
        DBPApplicationWorkbench workbench = new DBPApplicationWorkbench() {
            public DBPPlatform getPlatform() { return platform; }
            public DBPPlatformUI getPlatformUI() { return null; }
        };
        Field app = DBWorkbench.class.getDeclaredField("applicationWorkbench");
        app.setAccessible(true);
        app.set(null, workbench);

        DBPDriver driver = stub(DBPDriver.class, Map.of());
        DBPDataSourceContainer container = stub(DBPDataSourceContainer.class, Map.of(
            "getPreferenceStore", prefs, "getDriver", driver,
            "getActualConnectionConfiguration", new DBPConnectionConfiguration()));
        JDBCSession session = stub(JDBCSession.class, Map.of());
        JDBCDatabaseMetaData metadata = stub(JDBCDatabaseMetaData.class, Map.of("getIdentifierQuoteString", "\""));
        AdapterManager.getDefault().registerAdapters(new OracleDialectAdapterFactory(), OracleSQLDialect.class);
        var dialect = new OracleSQLDialect();
        JDBCDataSource dataSource = new OfflineDataSource(container, dialect);
        dialect.initDriverSettings(session, dataSource, metadata);

        Path output = args.length == 4 ? Path.of(args[3]) : null;
        if (output != null) Files.createDirectories(output);
        Map<Path, String> extractedPayloads = new HashMap<>();
        String brokenHelper = """
            DECLARE
              PROCEDURE helper IS
                v_change BOOLEAN := FALSE;
              BEGIN
                NULL;
              END;
            BEGIN
              helper;
            END;
            """;
        for (boolean ignoreNative : new boolean[]{false, true}) {
            prefs.setValue(ModelPreferences.SCRIPT_IGNORE_NATIVE_DELIMITER, ignoreNative);
            for (SQLScriptStatementDelimiterMode mode : SQLScriptStatementDelimiterMode.values()) {
                prefs.setValue(ModelPreferences.SCRIPT_STATEMENT_DELIMITER_BLANK, mode.name());
                String setting = mode + " ignoreNative=" + ignoreNative;
                List<SQLScriptElement> broken = SQLScriptParser.parseScript(dataSource, dialect, prefs, brokenHelper);
                require(broken.size() == 2 && broken.getFirst().getText().lines().count() == 6,
                    setting + ": nested helper fixture no longer reproduces premature split");
                List<SQLScriptElement> withSlash = SQLScriptParser.parseScript(dataSource, dialect, prefs, brokenHelper + "/\n");
                require(withSlash.size() == 3 && withSlash.getLast().getText().strip().equals("/"),
                    setting + ": fixture plus slash should remain split, with an extra slash query");
                for (int i = 0; i < 2; i++) {
                    Path source = Path.of(args[i]);
                    String current = Files.readString(source);
                    List<SQLScriptElement> result = SQLScriptParser.parseScript(dataSource, dialect, prefs, current);
                    require(result.size() == 1, setting + ": " + source.getFileName()
                        + " split into " + result.size() + " statements; expected one");
                    String expected = current.substring(0, current.lastIndexOf("END;") + 4).strip();
                    require(result.getFirst().getText().strip().equals(expected),
                        setting + ": parser did not preserve complete " + source.getFileName() + " through END;");
                    if (output != null) {
                        Path payload = output.resolve(source.getFileName().toString() + ".dbeaver.sql");
                        String exact = result.getFirst().getText();
                        String previousPayload = extractedPayloads.putIfAbsent(payload, exact);
                        if (previousPayload != null) require(previousPayload.equals(exact),
                            setting + ": settings produced different payload bytes for " + source.getFileName());
                        Files.writeString(payload, exact); // SQLQuery.getText() bytes: no trim, wrap or delimiter changes.
                    }
                }
                Path sqlDir = Path.of(args[0]).getParent();
                for (String name : List.of("3_HTH_CRM_849_HTH_Verify.sql", "4_HTH_CRM_849_DIGX_Verify.sql", "5_HTH_CRM_849_Comments.sql")) {
                    String related = Files.readString(sqlDir.resolve(name));
                    List<SQLScriptElement> queries = SQLScriptParser.parseScript(dataSource, dialect, prefs, related);
                    List<String> commentLines = related.lines().filter(line -> line.startsWith("COMMENT ON "))
                        .map(line -> line.substring(0, line.length() - 1)).toList();
                    int expectedCount = name.startsWith("3") ? 13 : name.startsWith("4") ? 6 : 117;
                    require(queries.size() == expectedCount, setting + ": " + name + " split into "
                        + queries.size() + " queries; expected " + expectedCount);
                    if (name.startsWith("5")) require(commentLines.size() == 114, "Expected one table and 113 column comments");
                    Path folder = output == null ? null : output.resolve(name + "_" + mode + "_native" + ignoreNative);
                    if (folder != null) Files.createDirectories(folder);
                    for (int j = 0; j < queries.size(); j++) {
                        String exact = queries.get(j).getText();
                        String query = exact.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "").strip();
                        require(queries.get(j).getClass().getSimpleName().equals("SQLQuery"), setting + ": unexpected script command");
                        if (j < commentLines.size()) require(query.equals(commentLines.get(j)), setting + ": incomplete COMMENT " + j);
                        else require((query.startsWith("SELECT ") || query.startsWith("WITH ")) && !query.endsWith(";"),
                            setting + ": incomplete read-only query " + name + " #" + j);
                        if (folder != null) Files.writeString(folder.resolve(String.format("%03d.sql", j)), exact);
                    }
                }
                System.out.println("PASS " + setting + ": both delivery files are single complete statements; "
                    + "prior helper fixture reproduces the failure, and slash does not repair it");
            }
        }
        System.out.println("All schema/configuration/verification/comment scripts checked. No database connections or SQL execution performed.");
    }
    private static <T> T stub(Class<T> api, Map<String,Object> values) {
        return api.cast(Proxy.newProxyInstance(DBeaverScriptParserRegression.class.getClassLoader(),
            new Class<?>[]{api}, (proxy, method, args) -> {
                if (values.containsKey(method.getName())) return values.get(method.getName());
                Class<?> r = method.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == long.class) return 0L;
                return null;
            }));
    }
    private static class OfflineDataSource extends JDBCDataSource {
        OfflineDataSource(DBPDataSourceContainer container, SQLDialect dialect) { super(container, dialect); }
        @Override public org.jkiss.dbeaver.model.struct.DBSDataType getLocalDataType(String name) { return null; }
        @Override public java.util.Collection<? extends org.jkiss.dbeaver.model.struct.DBSDataType> getLocalDataTypes() { return List.of(); }
        @Override public boolean isServerVersionAtLeast(int major, int minor) { return true; }
        @Override protected org.jkiss.dbeaver.model.exec.jdbc.JDBCFactory createJdbcFactory() { return null; }
        @Override public java.util.Collection<? extends DBSObject> getChildren(DBRProgressMonitor monitor) { return List.of(); }
        @Override public DBSObject getChild(DBRProgressMonitor monitor, String name) { return null; }
        @Override public Class<? extends DBSObject> getPrimaryChildType(DBRProgressMonitor monitor) { return DBSObject.class; }
        @Override public void cacheStructure(DBRProgressMonitor monitor, int scope) {}
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
