"""Control-record contract and damaged-file checks; no Oracle connection required."""
import contextlib
import io
import pathlib
import tempfile
import unittest

from verify_hth_crm_csv import EXPECTED, verify


class VerifyHthCrmCsvTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = pathlib.Path(self.directory.name) / "hthtomtb_mvp1apis_D250106.csv"
        self.labels = ','.join('"%s"' % field for field in EXPECTED)
        values = ["50", "1"] + [""] * 59
        values[4] = "12:34:56"
        self.detail = ','.join('"%s"' % field for field in values)
        self.header = "0020250106HTHMTB" + " " * 84

    def content(self, count=2):
        trailer = "99%015d%015d%s" % (count, 20250106 + count, " " * 68)
        return '\n'.join([self.header, self.labels] + [self.detail] * count + [trailer]) + '\n'

    def check_content(self, content, expected_date="20250106"):
        self.path.write_bytes(content.encode("utf-8"))
        with contextlib.redirect_stdout(io.StringIO()):
            return verify(self.path, expected_date)

    def test_zero_one_many(self):
        for count in (0, 1, 2, 1000):
            with self.subTest(count=count):
                self.assertEqual(count, self.check_content(self.content(count)))

    def test_count_and_checksum(self):
        for old, new in (("000000000000002", "000000000000003"),
                         ("000000020250108", "000000020250109")):
            with self.subTest(field=old), self.assertRaises(ValueError):
                self.check_content(self.content().replace(old, new))

    def test_control_width_and_spaces(self):
        for content in (self.content().replace(" " * 84, " " * 83),
                        self.content().replace(" " * 68, "0" + " " * 67),
                        self.content().replace(" " * 84, "\t" + " " * 83)):
            with self.subTest(content=content[:20]), self.assertRaises(ValueError):
                self.check_content(content)

    def test_wrong_system_or_date(self):
        for content in (self.content().replace("HTHMTB", "CDCCRM"),
                        self.content().replace("20250106HTH", "20250230HTH")):
            with self.assertRaises(ValueError):
                self.check_content(content)
        with self.assertRaisesRegex(ValueError, "expected extraction date"):
            self.check_content(self.content(), "20250107")
        self.path = self.path.with_name("hthtomtb_mvp1apis_D250107.csv")
        with self.assertRaisesRegex(ValueError, "filename date"):
            self.check_content(self.content())

    def test_missing_repeated_or_misplaced_controls(self):
        lines = self.content().splitlines()
        for damaged in (lines[1:], lines[:-1], lines + [lines[-1]],
                        [lines[-1]] + lines[1:-1] + [lines[0]], lines[:1] + lines[2:]):
            with self.subTest(lines=len(damaged)), self.assertRaises(ValueError):
                self.check_content('\n'.join(damaged) + '\n')

    def test_newlines_and_encoding(self):
        for content in (self.content().replace('\n', '\r\n'), self.content()[:-1],
                        '\ufeff' + self.content(), self.content() + '\n'):
            with self.assertRaises(ValueError):
                self.check_content(content)

    def test_csv_field_count_labels_and_quoting(self):
        for content in (self.content().replace(self.labels, self.labels + ',"extra"'),
                        self.content().replace('"record_type"', '"wrong_label"'),
                        self.content().replace('"50","1"', '50,"1"'),
                        self.content().replace('"50","1"', '"99","1"'),
                        self.content().replace('"12:34:56"', '"123456"')):
            with self.assertRaises(ValueError):
                self.check_content(content)

    def test_utf8_and_escaped_values(self):
        content = self.content(1).replace('"12:34:56",""',
            '"12:34:56","中文,\\"quoted\\"\\nline\\\\path"')
        self.assertEqual(1, self.check_content(content))


if __name__ == "__main__":
    unittest.main()
