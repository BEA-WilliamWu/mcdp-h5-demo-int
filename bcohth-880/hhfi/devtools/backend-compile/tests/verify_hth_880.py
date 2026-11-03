"""880 expiry calendar contract and exact reviewed DOCX/SQL template contract.

No Oracle or MNG connection: use the deployment verification SQL in UAT as well.
JAVA_HOME must support --release 8. Uses only Python standard library.
"""
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JDK = Path(os.environ['JAVA_HOME']) / 'bin'
UTILITY = next((ROOT / 'consulting/middleware/projects').rglob('HthApiPasswordCodeExpiry.java'))
with tempfile.TemporaryDirectory(prefix='hth-880-expiry-') as directory:
    java = Path(directory) / 'ExpiryTest.java'
    java.write_text('''
import java.time.*;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCodeExpiry;
public class ExpiryTest {
  public static void main(String[] args) {
    String[][] cases = {
      {"2026-11-02T16:00:00Z", "2026-11-04T23:59:59+08:00"},
      {"2026-11-03T15:59:59Z", "2026-11-04T23:59:59+08:00"},
      {"2026-11-03T16:00:00Z", "2026-11-05T23:59:59+08:00"},
      {"2026-12-31T09:00:00Z", "2027-01-01T23:59:59+08:00"},
      {"2028-02-28T09:00:00Z", "2028-02-29T23:59:59+08:00"},
      {"2028-02-29T09:00:00Z", "2028-03-01T23:59:59+08:00"},
      {"2026-02-28T09:00:00Z", "2026-03-01T23:59:59+08:00"}
    };
    for (String zone : new String[] {"UTC", "Asia/Hong_Kong", "America/New_York"}) {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
      for (String[] c : cases) {
        long actual = HthApiPasswordCodeExpiry.nextDayEnd(Instant.parse(c[0]).toEpochMilli());
        long expected = OffsetDateTime.parse(c[1]).toInstant().toEpochMilli();
        if (actual != expected) throw new AssertionError(zone + ": " + c[0]);
      }
    }
    System.out.println("PASS: 21 expiry cases; Hong Kong next-day end independent of JVM timezone");
  }
}''')
    subprocess.run([str(JDK / 'javac'), '--release', '8', '-d', directory, str(UTILITY), str(java)], check=True)
    subprocess.run([str(JDK / 'java'), '-cp', directory, 'ExpiryTest'], check=True)

source = ROOT / 'story/notification/2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx'
with ZipFile(source) as docx:
    document = ET.fromstring(docx.read('word/document.xml'))
ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
row = document.find('w:body', ns).findall('w:tbl', ns)[1].findall('w:tr', ns)[6]
cells = row.findall('w:tc', ns)
def cell_text(cell):
    lines = []
    for paragraph in cell.findall('w:p', ns):
        content = []
        for node in paragraph.iter():
            local = node.tag.rsplit('}', 1)[-1]
            if local == 't': content.append(node.text or '')
            elif local in ('br', 'cr'): content.append('\n')
            elif local == 'tab': content.append('\t')
        lines.append(''.join(content))
    return '\n'.join(lines).strip()

sql = (ROOT / 'consulting/db/branch_change_history/20261103_BCOH2H-880/1_HTH_Code_Approval_Notification.sql').read_text()
subject = re.search(r"v_subject .*? := q'~(.*?)~';", sql, re.S).group(1)
body_expr = re.search(r'v_body CLOB := (.*?);\nBEGIN', sql, re.S).group(1)
chunks = re.findall(r"TO_CLOB\(q'~(.*?)~'\)", body_expr, re.S)
assert subject == cell_text(cells[3]), 'Subject differs from reviewed v81 Feature 6'
assert ''.join(chunks) == cell_text(cells[4]), 'Raw HTML differs from reviewed v81 Feature 6'
assert all(len(x.encode('utf-8')) <= 4000 for x in chunks), 'Oracle string literal limit'
assert not re.search(r'HTH_API_PASSWORD_(SETUP|RESET)_SUCCESS', sql), '880 must not alter password-success templates'
assert "'USER' name FROM DUAL UNION ALL SELECT 'COMPANY'" in sql
assert "SUBSCRIBER_TYPE='EXTERNAL', SUBSCRIBER_VALUE='USER'" in sql
assert "TXT_DEST_TYP <> 'EMAIL'" in sql
assert 'ROLLBACK TO HTH_880_CONFIG;' in sql
assert not re.search(r'(?im)^\s*(INSERT|CREATE|ALTER|DROP)\b', sql), 'Delta must not add duplicate configuration/schema rows'
print('PASS: exact v81 subject/raw HTML, two recipient roles, three locales, EMAIL-only delta; no unrelated event mutation')
print('NOTE: Oracle execution/re-execution and actual MNG delivery still require UAT verification')
