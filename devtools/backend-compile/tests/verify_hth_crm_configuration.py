"""Load deployed Preferences.xml through the actual OBDX configuration framework.

Only the SYSCONFIG connection is redirected to isolated H2. ConfigurationFactory,
ConfigurationManager, XML loader and both DB providers are the production JARs.
This catches missing XML registration and the _O/N adapter-factory trap which
ordinary preferences-node fixtures cannot detect. No UAT/real database access.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

def run(command):
    result = subprocess.run(command, check=False, timeout=60)
    if result.returncode:
        raise SystemExit(result.returncode)

root = Path(__file__).resolve().parents[3]
lib = root / "consulting/middleware/lib"
jdk = Path(os.environ["JAVA_HOME"]) / "bin"
h2 = Path(os.environ["H2_JAR"])
assert h2.is_file(), "H2_JAR must be set to an existing isolated-test driver"
preferences = root / "consulting/config/Preferences.xml"
nodes = ET.parse(preferences).getroot().find("Nodes")
registered = [node for node in nodes if node.get("name") == "HTHCrmConfiguration"]
assert len(registered) == 1, "Production Preferences.xml must register HTHCrmConfiguration exactly once"
assert registered[0].get("parent") == "jdbcpreference"
assert registered[0].get("PreferencesProvider") == "com.ofss.digx.infra.config.impl.MultiEntityDBBasedPropProvider"
assert not any(node.get("name") == "HTHMtbConfiguration" for node in nodes), "Obsolete configuration must not remain registered"
cp = os.pathsep.join(map(str, [h2, *sorted(lib.rglob("*.jar"))]))
with tempfile.TemporaryDirectory(prefix="hthcrm-real-config-") as directory:
    out = Path(directory)
    fixture = out / "ConnectionUtil.java"
    fixture.write_text('''package com.ofss.fc.infra.jdbc;
public final class ConnectionUtil {
 public static java.sql.Connection getConnection(String name, String ignored) throws java.sql.SQLException {
  if (!"SYSCONFIG".equals(name)) throw new AssertionError("Unexpected external datasource: " + name);
  return java.sql.DriverManager.getConnection("jdbc:h2:mem:crm-config;MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
 }
}''')
    run([str(jdk / "javac"), "--release", "8", "-proc:none", "-cp", cp, "-d", str(out),
                    str(fixture), str(Path(__file__).with_name("HthCRMConfigurationTest.java"))])
    # Keep the entire production XML unchanged, including root/jdbc/variable/factory nodes.
    # External deployment settings are synthetic and contain no real endpoints or secrets.
    (out / "LocalConfig.properties").write_text("DEFAULT_ENTITY=OBDX_BU\ndefault.enterprise.code=01\nLIST_ENTITIES=SELECT ENTITY_ID FROM TEST_ENTITIES\n")
    (out / "jdbc.properties").write_text("# JDBC boundary is isolated in this regression.\n")
    for resource in ("InfoMessages_en.properties", "ErrorMessages_en.properties"):
        target = out / "resources" / resource
        target.parent.mkdir(exist_ok=True)
        shutil.copyfile(root / "consulting/config_core/resources" / resource, target)
    for scenario in ("registered", "factory-o-only", "missing-registration"):
        if scenario == "missing-registration":
            broken = ET.parse(preferences)
            broken_nodes = broken.getroot().find("Nodes")
            for node in list(broken_nodes):
                if node.get("name") == "HTHCrmConfiguration":
                    broken_nodes.remove(node)
            broken.write(out / "Preferences.xml", encoding="UTF-8", xml_declaration=True)
        else:
            shutil.copyfile(preferences, out / "Preferences.xml")
        run([str(jdk / "java"), "-cp", str(out) + os.pathsep + cp,
                        "HthCRMConfigurationTest", scenario])
