"""Check reviewed wording and execute the delta's actual SQL predicates in SQLite.

This does NOT compile/execute the PL/SQL block in Oracle or send notifications.
No bank data is used. Source extraction checks the reviewed template cells, not demos.
"""
from pathlib import Path
import re
import sqlite3
from xml.etree import ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
SQL = (ROOT / 'consulting/db/branch_change_history/20261109_BCOH2H-1332_1333/1_Update_Password_Success_Templates.sql').read_text()
DOC = ROOT / 'story/notification/2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx'
BASE = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.'
NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
with ZipFile(DOC) as z:
    document = ET.fromstring(z.read('word/document.xml'))


def cell_text(cell):
    paragraphs = []
    for p in cell.findall('w:p', NS):
        pieces = []
        for e in p.iter():
            if e.tag == '{%s}t' % NS['w']:
                pieces.append(e.text or '')
            elif e.tag in ('{%s}br' % NS['w'], '{%s}cr' % NS['w']):
                pieces.append('\n')
            elif e.tag == '{%s}tab' % NS['w']:
                pieces.append('\t')
        paragraphs.append(''.join(pieces))
    return '\n'.join(paragraphs)


tables = [[[cell_text(c) for c in r.findall('w:tc', NS)]
           for r in t.findall('w:tr', NS)] for t in document.findall('.//w:tbl', NS)]
values = dict(re.findall(r"\b(c_\w+) CONSTANT (?:VARCHAR2\(1000\)|CLOB) := (?:TO_CLOB\()?q'~(.*?)~'", SQL, re.S))
for number, operation in [('7', 'setup'), ('8', 'reset')]:
    row = next(r for r in tables[1] if r[0] == number)
    assert values['c_'+operation+'_subject'] == row[3].strip()
    assert values['c_'+operation+'_body'] == row[4].strip()
    row = next(r for r in tables[2] if r[0] == number)
    for locale, col in [('en', 4), ('tc', 5), ('sc', 6)]:
        assert values['c_sms_'+locale] == row[col].split('<Current Situation>', 1)[0].strip()
assert all('<Current Situation>' not in value for value in values.values())
assert all(not re.search(r'#[A-Za-z]\w*#', value) for value in values.values())
assert all(-20999 <= int(n) <= -20000 for n in re.findall(r'RAISE_APPLICATION_ERROR\((-\d+)', SQL))
assert 'ROLLBACK TO HTH_PASSWORD_SUCCESS_TMPL;' in SQL
assert not re.search(r'(?im)^\s*(INSERT|DELETE|COMMIT)\b', SQL)
assert re.findall(r'(?im)^\s*UPDATE\s+(\w+)', SQL) == ['DIGX_EP_MSG_TMPL_B']
print('PASS: exact v81 Features 7/8 raw email and EN/TC/SC SMS; no demo text/placeholders; template-only DML')
# Keep a single outer execution block: a local procedure in DECLARE was split at
# its END by DBeaver's Oracle script parser, leaving the main BEGIN in a second query.
code = re.sub(r"q'~.*?~'", "''", SQL, flags=re.S)
code = re.sub(r"'(?:''|[^'])*'", "''", code)
code = re.sub(r'--[^\n]*', '', code)
assert not re.search(r'\b(?:PROCEDURE|FUNCTION)\b', code, re.I)
assert len(re.findall(r'(?m)^BEGIN\s*$', code)) == 1
assert len(re.findall(r'(?m)^END;\s*$', code)) == 1
assert code.rstrip().endswith('END;')
route_matrix = re.search(r'CURSOR c_routes IS\s*(SELECT .*?);', SQL, re.S).group(1)
with sqlite3.connect(':memory:') as matrix_db:
    matrix_db.executescript("CREATE TABLE DUAL(dummy); INSERT INTO DUAL VALUES ('X');")
    actual = matrix_db.execute(route_matrix).fetchall()
expected = {(op, event, channel, locale)
    for op, event in [('setup', 'HTH_API_PASSWORD_SETUP_SUCCESS'), ('reset', 'HTH_API_PASSWORD_RESET_SUCCESS')]
    for channel in ['EMAIL', 'SMS'] for locale in ['en', 'zh-Hant', 'zh-Hans-CN']}
assert len(actual) == 12 and set(actual) == expected
assert SQL.index('FOR route IN c_routes LOOP') < SQL.index('UPDATE DIGX_EP_MSG_TMPL_B')
print('PASS: one outer anonymous block; actual route cursor covers all 12 success/channel/locale combinations')


# Run the actual binding selection, external-reference guard and UPDATE predicate.
bindings = re.search(r'CURSOR c_bindings IS\s*(SELECT .*?);', SQL, re.S).group(1)
external = re.search(r'SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_EVT_REC_B\s+WHERE \(COD_MSG_TMPL_ID=r.template_id.*?;', SQL, re.S).group(0)
external = external.replace(' INTO v_count', '').rstrip(';')
update = re.search(r'    UPDATE DIGX_EP_MSG_TMPL_B\s+SET .*?;', SQL, re.S).group(0).strip().rstrip(';')


def portable(query):
    query = re.sub(r'v_changes\(i\)\.(template_id|destination|subject|body)', r':\1', query)
    query = query.replace('r.template_id', ':template_id')
    for name in ['c_base', 'p_operation', 'p_event', 'p_destination', 'p_locale']:
        query = re.sub(r'\b'+name+r'\b', ':'+name, query)
    query = query.replace('DBMS_LOB.COMPARE', 'lob_compare').replace('NVL(', 'ifnull(')
    return re.sub(r'\bSYSDATE\b', "'FIXED_TIME'", re.sub(r'\bUSER\b', "'TEST'", query))


db = sqlite3.connect(':memory:')
db.create_function('lob_compare', 2, lambda a, b: None if a is None or b is None else (0 if a == b else 1))
db.executescript('''
CREATE TABLE DIGX_EP_EVT_REC_B(COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,TXT_DEST_TYP,LOCALE,COD_MSG_TMPL_ID,UNSECURE_MSG_TMPL_ID);
CREATE TABLE DIGX_EP_MSG_TMPL_B(COD_TMPL_ID,DETERMINANT_VALUE,DESTINATION_TYPE,TXT_SUBJECT_TMPL,TXT_MSG_TMPL,LAST_UPDATED_BY,LAST_UPDATED_DATE,OBJECT_VERSION_NUMBER);
''')
for unit in ['OBDX_BU', 'OTHER_BU']:
    db.execute('INSERT INTO DIGX_EP_MSG_TMPL_B VALUES (?,?,?,?,?,?,?,?)',
               ('renamed-template', unit, 'EMAIL', 'old-subject', 'old-body', 'original', 'original', 1))
db.executemany('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?)', [
    (BASE+'setup', 'HTH_API_PASSWORD_SETUP_SUCCESS', 'A', 'EMAIL', 'en', 'renamed-template', 'alternate-template'),
    (BASE+'setup', 'HTH_API_PASSWORD_SETUP_SUCCESS', 'A', 'EMAIL', 'zh-Hant', 'renamed-template', None),
    ('BCO.service', 'BCO_EVENT', 'A', 'EMAIL', 'en', 'BCO-template', None)])
params = dict(c_base=BASE, p_operation='setup', p_event='HTH_API_PASSWORD_SETUP_SUCCESS',
              p_destination='EMAIL', p_locale='en', template_id='renamed-template',
              destination='EMAIL', subject=values['c_setup_subject'], body=values['c_setup_body'])
assert {r[0] for r in db.execute(portable(bindings), params)} == {'renamed-template', 'alternate-template'}
assert not list(db.execute(portable(bindings), dict(params, p_locale='zh-Hans-CN')))
assert db.execute(portable(external), params).fetchone()[0] == 0
for bad in [
    ('BCO.service','BCO_EVENT','A','EMAIL','en','renamed-template',None),
    ('BCO.service','BCO_EVENT','A','EMAIL','en','other','renamed-template'),
    (BASE+'setup','HTH_API_PASSWORD_SETUP_SUCCESS','A','SMS','en','renamed-template',None),
    (BASE+'setup','HTH_API_PASSWORD_SETUP_SUCCESS','A','EMAIL','unknown','renamed-template',None),
    (BASE+'setup','HTH_API_PASSWORD_SETUP_SUCCESS',None,'EMAIL','en','renamed-template',None),
    (BASE+'reset','OTHER_EVENT','A','EMAIL','en','renamed-template',None)]:
    db.execute('SAVEPOINT bad_reference')
    db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?)', bad)
    assert db.execute(portable(external), params).fetchone()[0] == 1, bad
    db.execute('ROLLBACK TO bad_reference')
    db.execute('RELEASE bad_reference')
routes = list(db.execute('SELECT * FROM DIGX_EP_EVT_REC_B'))
assert db.execute(portable(update), params).rowcount == 1
snapshot = list(db.execute('SELECT * FROM DIGX_EP_MSG_TMPL_B'))
assert db.execute(portable(update), params).rowcount == 0
assert snapshot == list(db.execute('SELECT * FROM DIGX_EP_MSG_TMPL_B'))
assert routes == list(db.execute('SELECT * FROM DIGX_EP_EVT_REC_B'))
assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE DETERMINANT_VALUE='OTHER_BU'").fetchone()[0] == 'old-body'
db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES ('SMS-template','OBDX_BU','SMS','keep-sms-subject','old',NULL,NULL,1)")
assert db.execute(portable(update), dict(params, template_id='SMS-template', destination='SMS', subject=None, body=values['c_sms_tc'])).rowcount == 1
assert db.execute("SELECT TXT_SUBJECT_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID='SMS-template'").fetchone()[0] == 'keep-sms-subject'
print('PASS: actual SQL selects renamed/alternate IDs; detects foreign references; content update is rerunnable; other unit, routes and SMS subject unchanged')
print('NOTE: Oracle PL/SQL execution, server cache and actual email/SMS delivery require UAT verification')
