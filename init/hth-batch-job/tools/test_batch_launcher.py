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
        java.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-version" ]; then echo \'openjdk version "21.0.13"\' >&2; exit 0; fi\n'
            'if [ -n "${HTH_TEST_EXPECTED_KEY:-}" ] && '
            '[ "${HTH_BATCH_BCO_WRAPPING_KEY:-}" != "$HTH_TEST_EXPECTED_KEY" ]; then exit 50; fi\n'
            'printf "%s\\n" "$@"\nexit "${HTH_TEST_EXIT:-0}"\n'
        )
        java.chmod(0o750)
        (self.home / "test.jar").touch()
        self.env = dict(os.environ, JAVA_HOME=str(java_bin.parent), HTH_JAVA_HOME=str(java_bin.parent),
                        HTH_BATCH_COMMON_ENV="", HTH_BATCH_BCO_WRAPPING_KEY="", HTH_TEST_EXPECTED_KEY="",
                        BATCH_HOME=str(self.home), CONFIG_DIR="config with spaces", LOG_DIR="relative logs",
                        BATCH_JAR="test.jar", JVM_OPTS="-Xmx64M", HTH_TEST_EXIT="0")

    def launch(self, *args, launcher="GenHthCrmExtract.sh"):
        return subprocess.run(["sh", str(self.home / "bin" / launcher), *args],
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

    def test_scheduler_key_takes_precedence_over_shared_file(self):
        conf = self.home / "conf"
        conf.mkdir()
        (conf / "bco-db-key.env").write_text("return 50\n")
        self.env["HTH_BATCH_BCO_WRAPPING_KEY"] = "scheduler-test-key"
        self.env["HTH_TEST_EXPECTED_KEY"] = "scheduler-test-key"
        self.assertEqual(0, self.launch("20261116").returncode)

    def test_shared_key_file_without_key_fails_before_java(self):
        conf = self.home / "custom conf"
        conf.mkdir()
        common = conf / "HTHBatchEnv.sh"
        common.touch()
        (conf / "bco-db-key.env").touch()
        self.env["HTH_BATCH_COMMON_ENV"] = str(common)
        result = self.launch("20261116")
        self.assertEqual(50, result.returncode)
        self.assertIn("missing HTH_BATCH_BCO_WRAPPING_KEY", result.stderr)
        self.assertFalse((self.home / "relative logs").exists())

    def test_shared_launcher_defaults_in_source_and_installed_layouts(self):
        modules = (
            ("hth-mtb", "GenHthCrmExtract.sh", "hth-batch-job-mtb-1.0.0.jar", ("20261116",)),
            ("hth-eAdvice", "EAdviceBatch.sh", "hth-eAdvice-1.0.0.jar", ("3", "20261116")),
        )
        env = {key: value for key, value in self.env.items()
               if key not in ("BATCH_HOME", "CONFIG_DIR", "LOG_DIR", "BATCH_JAR", "HTH_BATCH_COMMON_ENV",
                              "HTH_JAVA_HOME", "JAVA_HOME")}
        conf = self.home / "conf"
        conf.mkdir()
        java = Path(self.env["JAVA_HOME"])
        (conf / "HTHBatchEnv.sh").write_text(f'export HTH_JAVA_HOME="{java}"\n')
        key_file = conf / "bco-db-key.env"
        key_file.write_text("HTH_BATCH_BCO_WRAPPING_KEY=local-test-key\n")
        key_file.chmod(0o600)
        env["HTH_TEST_EXPECTED_KEY"] = "local-test-key"

        for installed in (False, True):
            for module, launcher, jar_name, args in modules:
                with self.subTest(installed=installed, module=module):
                    module_home = self.home / module
                    jar_dir = module_home / ("lib" if installed else "target")
                    jar_dir.mkdir(parents=True)
                    (jar_dir / jar_name).touch()
                    batch_home = module_home if installed else self.home
                    if installed:
                        shutil.copytree(self.home / "bin", module_home / "bin")
                    result = subprocess.run(
                        ["sh", str(batch_home / "bin" / launcher), *args],
                        cwd="/", env=env, text=True, capture_output=True, timeout=10,
                    )
                    self.assertEqual(0, result.returncode, result.stderr)
                    log_path = Path(result.stdout.splitlines()[0].split("log=", 1)[1])
                    self.assertEqual(batch_home / "logs", log_path.parent)
                    arguments = log_path.read_text().splitlines()
                    self.assertIn(f"-Dconfig.path={module_home / 'config'}", arguments)
                    self.assertIn(str(jar_dir / jar_name), arguments)
                    self.assertEqual(list(args), arguments[-len(args):])


    def test_eadvice_entry_points_select_jobs_and_forward_optional_date(self):
        entry_points = (
            ("genEAdvice2EBK1.sh", "1"),
            ("genEAdvice2EBK2.sh", "2"),
            ("genEAdvice2EBK3.sh", "3"),
            ("genPaperAdvice2EBK.sh", "P"),
        )
        for launcher, job in entry_points:
            for args in ((), ("20261116",)):
                with self.subTest(launcher=launcher, args=args):
                    result = self.launch(*args, launcher=launcher)
                    self.assertEqual(0, result.returncode, result.stderr)
                    log = Path(result.stdout.splitlines()[0].split("log=", 1)[1])
                    self.assertTrue(log.name.startswith(f"EAdviceBatch.{job}."))
                    java_arguments = log.read_text().splitlines()
                    expected = ["-jar", "test.jar", job, *args]
                    self.assertEqual(expected, java_arguments[-len(expected):])

    def test_eadvice_entry_point_preserves_launcher_exit_codes(self):
        launcher = "genEAdvice2EBK3.sh"
        for args in (("bad",), ("20261116", "extra")):
            with self.subTest(args=args):
                self.assertEqual(2, self.launch(*args, launcher=launcher).returncode)
        self.assertFalse((self.home / "relative logs").exists())
        self.env["HTH_TEST_EXIT"] = "7"
        self.assertEqual(50, self.launch("20261116", launcher=launcher).returncode)


if __name__ == "__main__":
    unittest.main()
