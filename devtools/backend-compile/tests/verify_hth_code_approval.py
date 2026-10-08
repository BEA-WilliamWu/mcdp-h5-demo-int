"""Run the production HTH rejection hook with actual SDK DTOs and synthetic transactions.

Server transaction reads and dispatch are fixtures. Post-commit cleanup and approval
rollback are tested by verify_hth_password_transactions.py; Oracle/WebLogic require UAT validation.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
projects = root / "consulting/middleware/projects"
jdk = Path(os.environ["JAVA_HOME"]) / "bin"
# Several dependency JARs bundle older Jackson classes. Pin the OBDX standalone
# compile API so existing shared approval code is checked against its declared API.
jackson = root / "consulting/middleware/lib/OBDX_FW_LIB"
cp = os.pathsep.join([str(jackson / ("jackson-" + kind + "-2.11.0.jar"))
                     for kind in ("annotations", "core", "databind")]
                     + [str(root / "devtools/backend-compile/build/classes/java/main")]
                     + [str(p) for p in (root / "consulting/middleware/lib").rglob("*.jar")])
# Recompile the current shared delegate and its updated dependencies instead of
# checking the new approval hook against stale cached BCO APIs.
sources = [next(projects.rglob("HthApiPasswordApprovalLifecycle.java")),
           next(projects.rglob("CZTransactionExt.java")),
           next(projects.rglob("CZTransactionExtFunc.java")),
           next(projects.rglob("CZAdhocBulkpaymentPayout.java")),
           next(projects.rglob("HttpUtils.java")),
           next(projects.rglob("UserExtensionDataDTO.java")),
           Path(__file__).with_name("HthApiPasswordApprovalLifecycleTest.java")]
# Guard the lifecycle assumption against the actual deployed SDK API: the post
# extension is called after commit, before the approval session is closed.
bytecode = subprocess.run([str(jdk / "javap"), "-p", "-c", "-classpath", cp,
                          "com.ofss.digx.app.approval.service.transaction.Transaction"],
                         capture_output=True, text=True, check=True).stdout
start = bytecode.index("TransactionActionResponse performAction(")
end = bytecode.find("\n  public ", start + 10)
perform = bytecode[start:end]
hook = perform.index("ITransactionExtExecutor.postPerformAction:")
assert "Transaction.commit:" in perform[:hook], "Revisit cleanup ownership if SDK moves the hook before commit"
assert "DataAccessManager.closeSession:" in perform[hook:], "Post hook is expected before session close"
with tempfile.TemporaryDirectory(prefix="hth-code-approval-") as directory:
    configuration = Path(directory) / "ConfigurationFactory.java"
    configuration.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
    private static final ConfigurationFactory INSTANCE = new ConfigurationFactory();
    public static ConfigurationFactory getInstance() { return INSTANCE; }
    public java.util.prefs.Preferences getRootConfigurations() {
        return java.util.prefs.Preferences.userRoot().node("test");
    }
    public java.util.prefs.Preferences getConfigurations(String category) { return getRootConfigurations(); }
}
''')
    subprocess.run([str(jdk / "javac"), "--release", "8", "-proc:none", "-cp", cp,
                    "-d", directory, str(configuration), *map(str, sources)], check=True)
    subprocess.run([str(jdk / "java"), "-Djava.util.prefs.userRoot=" + directory + "/prefs",
                    "-cp", directory + os.pathsep + cp,
                    "com.ofss.digx.cz.bea.app.approval.service.transaction.ext.HthApiPasswordApprovalLifecycleTest"],
                   check=True)
