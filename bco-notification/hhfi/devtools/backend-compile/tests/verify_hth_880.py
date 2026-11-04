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
assert not re.search(r'(?im)^\s*(CREATE|ALTER|DROP)\b', sql), 'Delta must not alter schema'
assert not re.search(r'(?im)^\s*INSERT\b', sql), 'Do not invent templates for dangling bindings'
assert 'HTH_API_PWD_CODE_' not in sql and 'HTH API Password Code Approved_' not in sql
assert not re.search(r'LOCALE\s*(=|IN|NOT IN)', sql), 'Use all installed locales'
assert not re.search(r'#[A-Za-z][A-Za-z0-9_]*#', ''.join(chunks))
print('PASS: exact v81 subject/raw HTML; event-based selection, no ID/locale assumptions or new templates')

# Execute the real relational SQL from the deployment block against representative data.
# The Python driver supplies PL/SQL loop/bind values; this is NOT an Oracle execution test.
activity = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
events = ['HTH_API_PASSWORD_CODE_APPROVED_' + role + '_EMAIL_EVENT' for role in ('USER', 'COMPANY')]
bound_query = re.search(r'CURSOR c_bound_templates IS\s*(.*?);', sql, re.S).group(1)
action_query = re.search(r'SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B.*?;', sql, re.S).group(0)
route_queries = re.findall(r'SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_EVT_REC_B.*?;', sql, re.S)
assert len(route_queries) == 3
route_query, blank_query, shared_query = route_queries
template_query = re.search(r'SELECT COUNT\(\*\), MIN\(DESTINATION_TYPE\).*?;', sql, re.S).group(0)
template_update = re.search(r'UPDATE DIGX_EP_MSG_TMPL_B SET.*?;', sql, re.S).group(0)
route_update = re.search(r'UPDATE DIGX_EP_EVT_REC_B SET.*?;', sql, re.S).group(0)
route_delete = re.search(r'DELETE FROM DIGX_EP_EVT_REC_B.*?;', sql, re.S).group(0)
assert 'COD_MSG_TMPL_ID=' not in route_update and 'UNSECURE_MSG_TMPL_ID=' not in route_update
assert sql.index('FOR bound IN c_bound_templates LOOP') < sql.index('UPDATE DIGX_EP_MSG_TMPL_B')

def executable(statement):
    statement = re.sub(r' INTO v_count(?:, v_destination)?', '', statement)
    statement = statement.replace('bound.template_id', ':template_id').replace('v_templates(i)', ':template_id')
    statement = statement.replace('NVL(', 'COALESCE(')
    for variable in ('c_activity', 'v_event', 'v_subject', 'v_body'):
        statement = re.sub(r'\b' + variable + r'\b', ':' + variable, statement)
    return re.sub(r'\b(USER|SYSDATE)\b(?!\')', lambda m: "'test'" if m.group() == 'USER' else "'2026-11-04'", statement)

class InvalidConfig(Exception):
    pass

def run_delta(db):
    params = dict(c_activity=activity, v_subject=subject, v_body=''.join(chunks))
    db.execute('SAVEPOINT HTH_880_CONFIG')
    try:
        for event in events:
            params['v_event'] = event
            if db.execute(executable(action_query), params).fetchone()[0] != 1:
                raise InvalidConfig('action')
            if db.execute(executable(route_query), params).fetchone()[0] == 0:
                raise InvalidConfig('missing route')
            if db.execute(executable(blank_query), params).fetchone()[0] != 0:
                raise InvalidConfig('blank template ID')
        ids = [r[0] for r in db.execute(executable(bound_query), params)]
        for template in ids:
            params['template_id'] = template
            count, channel = db.execute(executable(template_query), params).fetchone()
            if count != 1:
                raise InvalidConfig('template rows')
            if channel != 'EMAIL':
                raise InvalidConfig('wrong channel')
            if db.execute(executable(shared_query), params).fetchone()[0]:
                raise InvalidConfig('shared template')
        for template in ids:
            params['template_id'] = template
            if db.execute(executable(template_update), params).rowcount != 1:
                raise InvalidConfig('concurrent change')
        for event in events:
            params['v_event'] = event
            db.execute(executable(route_delete), params)
            db.execute(executable(route_update), params)
        db.execute('RELEASE HTH_880_CONFIG')
        return ids
    except (InvalidConfig, sqlite3.DatabaseError):
        db.execute('ROLLBACK TO HTH_880_CONFIG')
        db.execute('RELEASE HTH_880_CONFIG')
        raise

route_columns = ('COD_ACT_ID', 'COD_EVENT_ID', 'COD_ACTION_ID', 'COD_MSG_TMPL_ID', 'LOCALE',
                 'TXT_DEST_TYP', 'SUBSCRIBER_TYPE', 'SUBSCRIBER_VALUE', 'RECIPIENT_TYPE',
                 'FLG_CONDITIONAL', 'COD_DEC_ID', 'UNSECURE_MSG_TMPL_ID')

def seed_template(db, template, unit='OBDX_BU', channel='EMAIL'):
    db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES (?, ?, ?, 'old subject', 'old body', 'A', 'before', 'before', 1)",
               (template, unit, channel))

def seed_route(db, template, event=events[0], locale='en', alternate=None, channel='EMAIL'):
    db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
               (activity, event, 'A', template, locale, channel, 'EXTERNAL', 'CUSTOMER', None, 'N', '0', alternate))

def fixture(rows):
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE DIGX_EP_ACT_EVT_ACN_B (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, OBJECT_STATUS, FLG_TRANSACTIONAL)')
    for event in events:
        db.execute('INSERT INTO DIGX_EP_ACT_EVT_ACN_B VALUES (?,?,?,?,?)', (activity, event, 'A', 'A', 'N'))
    db.execute('CREATE TABLE DIGX_EP_EVT_REC_B (' + ','.join(route_columns) + ')')
    # No key in this fixture so the production duplicate-row guard can also be exercised.
    db.execute('CREATE TABLE DIGX_EP_MSG_TMPL_B (COD_TMPL_ID, DETERMINANT_VALUE, DESTINATION_TYPE, TXT_SUBJECT_TMPL, TXT_MSG_TMPL, OBJECT_STATUS, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER)')
    for row in rows:
        db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', [row[k] for k in route_columns])
        seed_template(db, row['COD_MSG_TMPL_ID'])
    seed_template(db, 'BCO_UNRELATED')
    seed_route(db, 'BCO_UNRELATED', event='BCO_OTHER_EVENT')
    return db

def snapshot(db):
    return [db.execute('SELECT * FROM ' + table + ' ORDER BY rowid').fetchall()
            for table in ('DIGX_EP_MSG_TMPL_B', 'DIGX_EP_EVT_REC_B', 'DIGX_EP_ACT_EVT_ACN_B')]

def reject_without_changes(db, reason):
    before = snapshot(db)
    try:
        run_delta(db)
        raise AssertionError('Expected rejection: ' + reason)
    except (InvalidConfig, sqlite3.DatabaseError) as error:
        assert reason in str(error), str(error)
    assert snapshot(db) == before, 'Failure must leave all templates/routes unchanged'
    db.close()

for baseline_path in (
    '20260907_HTH_API_Password/6_HTH_API_Password_Notification.sql',
    '260908_BCOH2H-787_HTH_API_Password_Notification/1_HTH_API_Password_Notification_Template.sql',
):
    baseline = (ROOT / 'consulting/db/branch_change_history' / baseline_path).read_text()
    rows = []
    for match in re.finditer(r'INSERT\s+INTO\s+DIGX_EP_EVT_REC_B\s*\((.*?)\)\s*VALUES\s*\((.*?)\)\s*;', baseline, re.I | re.S):
        columns = [c.strip().upper() for c in match[1].split(',')]
        values = [v.strip() for v in match[2].split(',')]
        assert len(columns) == len(values)
        row = dict(zip(columns, [None if v.upper() == 'NULL' else v.strip("'") for v in values]))
        if row['COD_ACT_ID'] == activity and row['COD_EVENT_ID'] in events:
            rows.append(row)
    assert len(rows) == 6, baseline_path
    first_id = rows[0]['COD_MSG_TMPL_ID']
    db = fixture(rows)
    # Arbitrary ID and an additional locale; multiple routes per locale and shared IDs
    # are legitimate and must not be mistaken for ambiguous template rows.
    seed_template(db, 'Custom reviewed mail 42')
    seed_template(db, 'Alternate reviewed mail 43')
    seed_route(db, 'Custom reviewed mail 42', locale='en-GB', alternate='Alternate reviewed mail 43')
    seed_route(db, 'Custom reviewed mail 42', event=events[1], locale='en')
    seed_route(db, first_id, locale='fr')
    seed_template(db, first_id, unit='OTHER_BU')
    seed_template(db, 'UNRELATED_ORPHAN')
    seed_template(db, 'CODE_SMS', channel='SMS')
    seed_route(db, 'CODE_SMS', channel='SMS')
    count_before = db.execute('SELECT COUNT(*) FROM DIGX_EP_MSG_TMPL_B').fetchone()[0]
    bound_before = db.execute("SELECT COD_MSG_TMPL_ID, UNSECURE_MSG_TMPL_ID, LOCALE FROM DIGX_EP_EVT_REC_B WHERE TXT_DEST_TYP='EMAIL' ORDER BY rowid").fetchall()
    for run in (1, 2):
        ids = run_delta(db)
        assert len(ids) == 8 and len(set(ids)) == 8
        assert db.execute('SELECT COUNT(*) FROM DIGX_EP_MSG_TMPL_B').fetchone()[0] == count_before
        for template in ids:
            assert db.execute("SELECT TXT_SUBJECT_TMPL, TXT_MSG_TMPL, OBJECT_VERSION_NUMBER FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=? AND DETERMINANT_VALUE='OBDX_BU'", (template,)).fetchone() == (subject, ''.join(chunks), run + 1)
        for template in ('BCO_UNRELATED', 'UNRELATED_ORPHAN', 'CODE_SMS'):
            assert db.execute('SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=?', (template,)).fetchone()[0] == 'old body'
        assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=? AND DETERMINANT_VALUE='OTHER_BU'", (first_id,)).fetchone()[0] == 'old body'
        assert db.execute("SELECT COD_MSG_TMPL_ID, UNSECURE_MSG_TMPL_ID, LOCALE FROM DIGX_EP_EVT_REC_B WHERE TXT_DEST_TYP='EMAIL' ORDER BY rowid").fetchall() == bound_before
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B WHERE TXT_DEST_TYP='SMS'").fetchone()[0] == 0
    db.close()

    for channel in (None, 'SMS'):
        db = fixture(rows)
        db.execute('UPDATE DIGX_EP_MSG_TMPL_B SET DESTINATION_TYPE=? WHERE COD_TMPL_ID=?', (channel, first_id))
        reject_without_changes(db, 'wrong channel')
    for mode in ('missing', 'other_unit', 'duplicate', 'only_legacy_zh_hant'):
        db = fixture(rows)
        if mode == 'missing':
            db.execute('DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=?', (first_id,))
        elif mode == 'other_unit':
            db.execute("UPDATE DIGX_EP_MSG_TMPL_B SET DETERMINANT_VALUE='OTHER_BU' WHERE COD_TMPL_ID=?", (first_id,))
        elif mode == 'duplicate':
            seed_template(db, first_id)
        else:
            db.execute("DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID <> 'BCO_UNRELATED'")
            seed_template(db, 'HTH API Password Code Approved_USER_EMAIL_zh-Hant')
        reject_without_changes(db, 'template rows')
    for kind in ('primary', 'alternate', 'other_activity', 'other_channel'):
        db = fixture(rows)
        if kind == 'primary':
            seed_route(db, first_id, event='BCO_OTHER_EVENT')
        elif kind == 'alternate':
            seed_route(db, 'BCO_UNRELATED', event='BCO_OTHER_EVENT', alternate=first_id)
        elif kind == 'other_activity':
            seed_route(db, first_id, locale='zz')
            db.execute("UPDATE DIGX_EP_EVT_REC_B SET COD_ACT_ID='BCO_OTHER_ACTIVITY' WHERE LOCALE='zz'")
        else:
            seed_route(db, first_id, channel='SMS')
        reject_without_changes(db, 'shared template')
    db = fixture(rows)
    db.execute('UPDATE DIGX_EP_EVT_REC_B SET COD_MSG_TMPL_ID=NULL WHERE COD_MSG_TMPL_ID=?', (first_id,))
    reject_without_changes(db, 'blank template ID')
    db = fixture(rows)
    db.execute('DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID=?', (events[1],))
    reject_without_changes(db, 'missing route')
    db = fixture(rows)
    db.execute("UPDATE DIGX_EP_ACT_EVT_ACN_B SET FLG_TRANSACTIONAL='Y' WHERE COD_EVENT_ID=?", (events[0],))
    reject_without_changes(db, 'action')
    db = fixture(rows)
    # Simulate a later write error: earlier template writes must roll back as well.
    blocked_id = max(r['COD_MSG_TMPL_ID'] for r in rows)
    db.execute("CREATE TRIGGER fail_update BEFORE UPDATE ON DIGX_EP_MSG_TMPL_B WHEN NEW.COD_TMPL_ID='" + blocked_id + "' BEGIN SELECT RAISE(ABORT, 'write failure'); END;")
    reject_without_changes(db, 'write failure')

print('PASS: both baselines; arbitrary IDs/locales/multiple bindings/alternate templates; shared target templates updated once; reruns add no rows; unrelated BCO/other units preserved')
print('PASS: missing/blank/duplicate/wrong-channel/shared templates, missing routes/actions and later write failures reject with no partial changes')
verification = (ROOT / 'consulting/db/branch_change_history/20261103_BCOH2H-880/2_HTH_Code_Approval_Verification.sql').read_text()
binding_report = re.search(r'WITH routes AS .*?;', verification, re.S).group(0)
shared_report = re.search(r'WITH bound_ids AS .*?;', verification, re.S).group(0)
db = fixture(rows)
def report_checks():
    cursor = db.execute(binding_report)
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, result))['TEMPLATE_CHECK'] for result in cursor]
assert report_checks() == ['OK'] * 6
assert db.execute(shared_report).fetchall() == []
db.execute('DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=?', (first_id,))
assert report_checks().count('MISSING_TEMPLATE') == 1
seed_template(db, first_id)
seed_template(db, first_id)
assert report_checks().count('DUPLICATE_TEMPLATE') == 2
seed_route(db, 'BCO_UNRELATED', event='BCO_OTHER_EVENT', alternate=first_id)
assert db.execute(shared_report).fetchone()[0] == first_id
db.close()
print('PASS: verification queries report complete/missing/duplicate templates and outside-event alternate references')
print('NOTE: relational SQL tested with SQLite; Oracle PL/SQL execution and actual MNG delivery still require UAT verification')
