"""Check delivery files with the real DBeaver 26.2.1 Oracle script parser, offline.

Requires JAVA_HOME (JDK 21+) and DBEAVER_HOME (official extracted installation).
No network download, database connection, credentials or production changes.
Optional --output DIR exports exact parsed statements for separate JDBC testing.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not os.environ.get("JAVA_HOME") or not os.environ.get("DBEAVER_HOME"):
        parser.error("Set JAVA_HOME to JDK 21+ and DBEAVER_HOME to DBeaver 26.2.1")
    home = Path(os.environ["DBEAVER_HOME"])
    plugins = next((p for p in (home / "plugins", home / "Contents/Eclipse/plugins")
                    if p.is_dir()), None)
    if plugins is None or not list(plugins.glob("org.jkiss.dbeaver.core_26.2.1.*.jar")):
        parser.error("This regression is pinned to official DBeaver 26.2.1 plugin jars")
    java_bin = Path(os.environ["JAVA_HOME"]) / "bin"
    suffix = ".exe" if os.name == "nt" else ""
    root = Path(__file__).resolve().parents[3]
    sql_dir = root / "consulting/db/branch_change_history/20260923_HTH_MTB_849"
    helper = Path(__file__).with_name("DBeaverScriptParserRegression.java")
    plugin_classpath = str(plugins / "*")
    with tempfile.TemporaryDirectory(prefix="hth849-parser-") as build:
        subprocess.run([str(java_bin / ("javac" + suffix)), "-cp", plugin_classpath,
                        "-d", build, str(helper)], check=True)
        command = [str(java_bin / ("java" + suffix)), "-cp",
                   os.pathsep.join((plugin_classpath, build)), helper.stem,
                   str(sql_dir / "1_HTH_CRM_849_Schema.sql"),
                   str(sql_dir / "2_HTH_CRM_849_DIGX_Config.sql")]
        if args.output is not None:
            command.extend(("--output", str(args.output.resolve())))
        subprocess.run(command, check=True)


if __name__ == "__main__":
    main()
