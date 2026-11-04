"""880 expiry/registration contract and exact reviewed DOCX/SQL template contract.

No Oracle or MNG connection: use the deployment verification SQL in UAT as well.
Uses only Python standard library; transaction execution is covered by verify_hth_password_transactions.py.
"""
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree as ET
import re
import sqlite3

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

# Execute the delta's real lookup/update SQL against both repository route baselines.
# SQLite covers relational selection and update scope, not Oracle PL/SQL execution.
activity = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
events = ['HTH_API_PASSWORD_CODE_APPROVED_' + role + '_EMAIL_EVENT' for role in ('USER', 'COMPANY')]
route_query = re.search(r'SELECT COUNT\(\*\), MIN\(COD_MSG_TMPL_ID\).*?;', sql, re.S).group(0)
template_query = re.search(r'SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_MSG_TMPL_B.*?;', sql, re.S).group(0)
shared_query = re.search(r'SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_EVT_REC_B\s+WHERE COD_MSG_TMPL_ID.*?;', sql, re.S).group(0)
template_update = re.search(r'UPDATE DIGX_EP_MSG_TMPL_B SET.*?;', sql, re.S).group(0)
route_update = re.search(r'UPDATE DIGX_EP_EVT_REC_B SET.*?;', sql, re.S).group(0)
assert 'COD_MSG_TMPL_ID=' not in route_update, 'Do not replace the installed route template ID'
assert "v_template := v_templates(role.name || '|' || lang.locale);" in sql
assert "'HTH API Password Code Approved_'" in sql and "WHEN 'COMPANY' THEN 'PARTY'" in sql

def executable(statement):
    statement = re.sub(r' INTO v_count(?:, v_template)?', '', statement)
    statement = statement.replace('lang.locale', ':locale').replace('NVL(', 'COALESCE(')
    for variable in ('c_activity', 'v_event', 'v_template', 'v_subject', 'v_body'):
        statement = re.sub(r'\b' + variable + r'\b', ':' + variable, statement)
    return re.sub(r'\b(USER|SYSDATE)\b(?!\')', lambda m: "'test'" if m.group() == 'USER' else "'2026-11-04'", statement)

for baseline_path in (
    '20260907_HTH_API_Password/6_HTH_API_Password_Notification.sql',
    '260908_BCOH2H-787_HTH_API_Password_Notification/1_HTH_API_Password_Notification_Template.sql',
):
    baseline = (ROOT / 'consulting/db/branch_change_history' / baseline_path).read_text()
    rows = []
    for match in re.finditer(r'INSERT\s+INTO\s+DIGX_EP_EVT_REC_B\s*\((.*?)\)\s*VALUES\s*\((.*?)\)\s*;', baseline, re.I | re.S):
        columns = [c.strip().upper() for c in match[1].split(',')]
        # The recipient INSERTs contain only scalar literals without embedded commas.
        values = [v.strip().strip("'") for v in match[2].split(',')]
        assert len(columns) == len(values)
        row = dict(zip(columns, values))
        if row['COD_ACT_ID'] == activity and row['COD_EVENT_ID'] in events:
            rows.append(row)
    assert len(rows) == 6, baseline_path
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE DIGX_EP_EVT_REC_B (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, LOCALE, TXT_DEST_TYP, SUBSCRIBER_TYPE, SUBSCRIBER_VALUE, RECIPIENT_TYPE, FLG_CONDITIONAL, COD_DEC_ID)')
    db.execute('CREATE TABLE DIGX_EP_MSG_TMPL_B (COD_TMPL_ID, DETERMINANT_VALUE, DESTINATION_TYPE, TXT_SUBJECT_TMPL, TXT_MSG_TMPL, OBJECT_STATUS, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER)')
    for row in rows:
        assert "'" + row['COD_MSG_TMPL_ID'] + "', 'EMAIL'" in baseline
        db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?,?,?,?,?)',
                   [row[k] for k in ('COD_ACT_ID', 'COD_EVENT_ID', 'COD_ACTION_ID', 'COD_MSG_TMPL_ID', 'LOCALE', 'TXT_DEST_TYP', 'SUBSCRIBER_TYPE', 'SUBSCRIBER_VALUE', 'RECIPIENT_TYPE', 'FLG_CONDITIONAL', 'COD_DEC_ID')])
        db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES (?, 'OBDX_BU', 'EMAIL', 'old', 'old', 'A', '', '', 1)", (row['COD_MSG_TMPL_ID'],))
    db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES ('BCO_UNRELATED', 'OBDX_BU', 'EMAIL', 'keep', 'keep', 'A', '', '', 1)")
    for run in range(2):
        for row in rows:
            params = dict(c_activity=activity, v_event=row['COD_EVENT_ID'], locale=row['LOCALE'],
                          v_subject=subject, v_body=''.join(chunks))
            count, template = db.execute(executable(route_query), params).fetchone()
            assert count == 1 and template == row['COD_MSG_TMPL_ID']
            params['v_template'] = template
            assert db.execute(executable(template_query), params).fetchone()[0] == 1
            assert db.execute(executable(shared_query), params).fetchone()[0] == 0
            assert db.execute(executable(template_update), params).rowcount == 1
            assert db.execute(executable(route_update), params).rowcount == 1
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_MSG_TMPL_B WHERE TXT_MSG_TMPL=?", (''.join(chunks),)).fetchone()[0] == 6
    assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID='BCO_UNRELATED'").fetchone()[0] == 'keep'
    assert db.execute('SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B').fetchone()[0] == 6
    # Missing, wrong-unit, duplicate and shared configuration remain visible to preflight.
    db.execute('UPDATE DIGX_EP_MSG_TMPL_B SET DETERMINANT_VALUE=? WHERE COD_TMPL_ID=?', ('OTHER_BU', template))
    assert db.execute(executable(template_query), params).fetchone()[0] == 0
    db.execute('DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=?', (template,))
    assert db.execute(executable(template_query), params).fetchone()[0] == 0
    db.execute('INSERT INTO DIGX_EP_EVT_REC_B SELECT * FROM DIGX_EP_EVT_REC_B WHERE COD_MSG_TMPL_ID=?', (template,))
    assert db.execute(executable(route_query), params).fetchone()[0] == 2
    db.execute("UPDATE DIGX_EP_EVT_REC_B SET COD_EVENT_ID='BCO_OTHER_EVENT' WHERE COD_MSG_TMPL_ID=?", (template,))
    assert db.execute(executable(shared_query), params).fetchone()[0] == 2
    db.close()
print('PASS: actual route SQL supports both installed ID families; repeat updates preserve IDs/row counts and unrelated BCO content; missing/duplicate/shared data detected')
print('NOTE: Oracle execution/re-execution and actual MNG delivery still require UAT verification')
