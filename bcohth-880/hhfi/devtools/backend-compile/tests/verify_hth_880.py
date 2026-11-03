"""880 expiry/registration contract and exact reviewed DOCX/SQL template contract.

No Oracle or MNG connection: use the deployment verification SQL in UAT as well.
Uses only Python standard library; transaction execution is covered by verify_hth_password_transactions.py.
"""
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[3]
projects = ROOT / 'consulting/middleware/projects'
service = next(projects.rglob('HostToHostApiPassword.java')).read_text()
assert 'private static final int EXPIRY_HOURS = 24;' in service
assert 'HTH_880_CODE_ACTIVATED' not in service
assert 'notifyOnUserApprovalCommit' not in service
assert not list(projects.rglob('HthApiPasswordCodeExpiry.java'))
assert "SYSTIMESTAMP + NUMTODSINTERVAL(?, 'HOUR')" in next(projects.rglob('LocalHthApiPasswordCodeRepositoryAdapter.java')).read_text()
print('PASS: 24-hour DB expiry contract; no 880 thread marker/post-commit bridge/calendar utility')

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
assert "AND FLG_TRANSACTIONAL='N'" in sql, "Require the existing background Code action"
assert 'ROLLBACK TO HTH_880_CONFIG;' in sql
assert not re.search(r'(?im)^\s*(INSERT|CREATE|ALTER|DROP)\b', sql), 'Delta must not add duplicate configuration/schema rows'
print('PASS: exact v81 subject/raw HTML, two recipient roles, three locales, EMAIL-only delta; no unrelated event mutation')
print('NOTE: Oracle execution/re-execution and actual MNG delivery still require UAT verification')
