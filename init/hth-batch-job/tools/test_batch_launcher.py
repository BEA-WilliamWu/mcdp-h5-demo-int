"""Portable launcher contract tests; the fake JVM records arguments without database access."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BatchLauncherTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="hth launcher ")
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        source = Path(__file__).resolve().parents[1]
        shutil.copytree(source / "bin", self.home / "bin")
        java_bin = self.home / "fake java" / "bin"
        java_bin.mkdir(parents=True)
        java = java_bin / "java"
        java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\nexit "${HTH_TEST_EXIT:-0}"\n')
        java.chmod(0o750)
        (self.home / "test.jar").touch()
        self.env = dict(os.environ, JAVA_HOME=str(java_bin.parent), BATCH_HOME=str(self.home),
                        CONFIG_DIR="config with spaces", LOG_DIR="relative logs",
                        BATCH_JAR="test.jar", JVM_OPTS="-Xmx64M", HTH_TEST_EXIT="0")

    def launch(self, *args):
        return subprocess.run(["sh", str(self.home / "bin" / "GenHthCrmExtract.sh"), *args],
                              cwd="/", env=self.env, text=True, capture_output=True, timeout=10)

    def test_date_arguments_paths_with_spaces_and_full_log_path(self):
        result = self.launch("20261007")
        self.assertEqual(0, result.returncode, result.stderr)
        log = next((self.home / "relative logs").glob("MTBBatch.*.log"))
        self.assertIn(str(log), result.stdout)
        arguments = log.read_text().splitlines()
        self.assertIn("-Dconfig.path=config with spaces", arguments)
        self.assertEqual(["-jar", "test.jar", "CRM_EXTRACT", "20261007"], arguments[-4:])
        self.assertNotIn("CDCBatch", log.read_text())

    def test_omitted_date_and_java_exit_code(self):
        self.env["HTH_TEST_EXIT"] = "7"
        result = self.launch()
        self.assertEqual(50, result.returncode)
        log = next((self.home / "relative logs").glob("*.log"))
        self.assertEqual("CRM_EXTRACT", log.read_text().splitlines()[-1])
        self.env["HTH_TEST_EXIT"] = "2"
        self.assertEqual(2, self.launch("20260230").returncode)

    def test_invalid_arguments_do_not_start_java(self):
        for args in (("x",), ("2026107",), ("202610071",), ("20261007", "extra")):
            with self.subTest(args=args):
                self.assertEqual(2, self.launch(*args).returncode)
        self.assertFalse((self.home / "relative logs").exists())

    def test_missing_jar_is_a_failure(self):
        (self.home / "test.jar").unlink()
        self.assertEqual(50, self.launch().returncode)


if __name__ == "__main__":
    unittest.main()
