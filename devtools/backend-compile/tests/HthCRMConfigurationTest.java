import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.prefs.Preferences;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.config.ConfigurationManager;
import com.ofss.fc.infra.config.Configurations;
import com.ofss.digx.infra.config.impl.MultiEntityDBBasedPropProvider;

/** Real framework configuration loading; only the external SYSCONFIG connection is a fixture. */
public final class HthCRMConfigurationTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:crm-config;MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE DIGX_FW_CONFIG_ALL_O (PREFERENCE_NAME VARCHAR(100), PROP_ID VARCHAR(100), PROP_VALUE VARCHAR(500), DETERMINANT_VALUE VARCHAR(50))");
            statement.execute("CREATE TABLE DIGX_FW_CONFIG_ALL_B (CATEGORY_ID VARCHAR(100), PROP_ID VARCHAR(100), PROP_VALUE VARCHAR(500))");
            statement.execute("CREATE TABLE DIGX_FW_CONFIG_VAR_B (PROP_ID VARCHAR(100), PROP_VALUE VARCHAR(500), DETERMINANT_VALUE VARCHAR(50))");
            statement.execute("CREATE TABLE TEST_ENTITIES (ENTITY_ID VARCHAR(50))");
            statement.execute("INSERT INTO TEST_ENTITIES VALUES ('OBDX_BU'), ('OTHER_BU')");
            // Opposite entity values ensure an accidental unfiltered base query cannot pass.
            statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_O VALUES ('HTHCRMConfiguration','ENABLED','Y','N'), ('HTHCRMConfiguration','ENABLED','N','OTHER_BU')");
            statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_O VALUES ('HTHCRMConfiguration','RESERVED_TEST','base','N'), ('HTHCRMConfiguration','RESERVED_TEST','entity','OBDX_BU')");
            statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_O VALUES ('HTHMtbConfiguration','ENABLED','Y','N')");
            statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_O VALUES ('HTHCrmConfiguration','ENABLED','Y','N')");
            statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_O VALUES ('AdapterFactories','HTH_CRM_ADAPTER_FACTORY','wrong-legacy-row','N')");
            if (!"factory-o-only".equals(scenario)) {
                statement.execute("INSERT INTO DIGX_FW_CONFIG_ALL_B VALUES ('adapterfactoryconfig','HTH_CRM_ADAPTER_FACTORY','com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory')");
            }
        }
        ConfigurationFactory factory = ConfigurationFactory.getInstance();
        check(factory.getRootConfigurations() instanceof Configurations, "Must use the actual framework factory, not the 849 mock");
        Preferences crm = factory.getConfigurations("HTHCRMConfiguration");
        if ("missing-registration".equals(scenario)) {
            check("N".equals(crm.get("ENABLED", "N")), "Missing registration must reproduce the disabled fallback despite database Y");
            System.out.println("PASS: real Preferences.xml missing-registration regression (" + checks + " checks)");
            return;
        }
        check(ConfigurationManager.getInstance().getConfigurationProvider("HTHCRMConfiguration") instanceof MultiEntityDBBasedPropProvider,
                "The deployed node must use the real MultiEntityDBBasedPropProvider");
        check("Y".equals(crm.get("ENABLED", "N")), "Global ENABLED=Y must load from the new configuration group");
        check("base".equals(crm.get("RESERVED_TEST", "missing")), "Global base value");
        com.ofss.digx.infra.thread.ThreadAttribute.set("CURRENT_TARGET_UNIT", "OBDX_BU");
        check("Y".equals(crm.get("ENABLED", "N")), "An entity with no ENABLED override must inherit the global value");
        check("entity".equals(crm.get("RESERVED_TEST", "missing")), "Entity override must load independently");
        com.ofss.digx.infra.thread.ThreadAttribute.set("CURRENT_TARGET_UNIT", "OTHER_BU");
        check("N".equals(crm.get("ENABLED", "Y")), "Other entity explicit disable must override the global value");
        com.ofss.digx.infra.thread.ThreadAttribute.set("CURRENT_TARGET_UNIT", "UNKNOWN_BU");
        check("Y".equals(crm.get("ENABLED", "N")), "Unknown entity must not inherit OTHER_BU's disabled value");
        com.ofss.digx.infra.thread.ThreadAttribute.set("CURRENT_TARGET_UNIT", null);
        Preferences factories = factory.getConfigurations("AdapterFactories");
        if ("factory-o-only".equals(scenario)) {
            check("missing".equals(factories.get("HTH_CRM_ADAPTER_FACTORY", "missing")), "An _O/N row alone is not loaded by the BCO adapter provider");
        } else {
            check("com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory".equals(factories.get("HTH_CRM_ADAPTER_FACTORY", "missing")),
                    "The new factory must load from the adapterfactoryconfig base row");
        }
        check("N".equals(factory.getConfigurations("HTHMtbConfiguration").get("ENABLED", "N")), "Obsolete group must not enable CRM");
        check("N".equals(factory.getConfigurations("HTHCrmConfiguration").get("ENABLED", "N")), "Mixed-case legacy group must not enable CRM");
        System.out.println("PASS: real Preferences.xml and framework database providers " + scenario + " (" + checks + " checks)");
    }
}
