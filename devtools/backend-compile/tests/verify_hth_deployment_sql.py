"""Replay deployment DML with relational constraints and rollback in SQLite.

The production SQL is used directly after adapting Oracle date/user expressions.
This covers notification/error reruns, parent registration, rollback, adapter DML
and verification queries. It does not compile PL/SQL or replace an Oracle UAT run.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[3]
SQL_DIR = ROOT / 'consulting/db/branch_change_history/20260907_HTH_API_Password'
SQL = {i: next(SQL_DIR.glob(str(i) + '_*.sql')).read_text() for i in range(1, 8)}
LITERAL = r"'(?:''|[^'])*'"


def outside_literals(sql, transform):
    return ''.join(part if i % 2 else transform(part)
                   for i, part in enumerate(re.split('(' + LITERAL + ')', sql)))


def statements(sql):
    sql = outside_literals(sql, lambda p: re.sub(r'--[^\n]*', '', p))
    masked = outside_literals(sql, lambda p: p.replace(';', '\x00'))
    return [s.strip() for s in masked.split('\x00') if s.strip()]


def portable(sql):
    def adapt(part):
        part = re.sub(r'\b(?:SYSDATE|SYSTIMESTAMP)\b', 'CURRENT_TIMESTAMP', part)
        part = re.sub(r'\bUSER\b', "'deployer'", part)
        return re.sub(r'\bDATE\s+(?=$)', '', part)
    return outside_literals(sql, adapt)


def body(number):
    sql = SQL[number]
    assert sql.count('COMMIT;') == 1
    assert 'ROLLBACK TO HTH_API_PASSWORD_CONFIG;' in sql
    assert re.search(r'EXCEPTION\s+WHEN OTHERS THEN\s+ROLLBACK TO HTH_API_PASSWORD_CONFIG;\s+RAISE;', sql)
    return sql.split('SAVEPOINT HTH_API_PASSWORD_CONFIG;', 1)[1].split('COMMIT;', 1)[0]


def inserts(sql):
    # Column lists contain identifiers only; balanced expressions stay in the DML.
    return re.findall(r'(?:INSERT\s+)?INTO\s+(\w+)\s*\(([^)]+)\)', sql, re.I)


def connect():
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    db.create_function('NVL', 2, lambda a, b: b if a is None else a)
    db.executescript('CREATE TABLE DUAL(DUMMY TEXT); INSERT INTO DUAL VALUES (\'X\');')
    definitions = {}
    for table, cols in inserts(SQL[6] + SQL[5]):
        definitions[table] = [c.strip() for c in cols.split(',')]
    keys = {
        'DIGX_EP_ACT_B': 'COD_ACT_ID',
        'DIGX_PM_EVENT_ALL_B': 'EVENT_CODE',
        'DIGX_EP_ACT_EVT_B': 'COD_ACT_ID,COD_EVENT_ID',
        'DIGX_EP_ACT_EVT_ACN_B': 'COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID',
        'DIGX_EP_MSG_TMPL_B': 'COD_TMPL_ID,DETERMINANT_VALUE',
        'DIGX_EP_EVT_REC_B': 'COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,TXT_DEST_TYP,LOCALE',
        'DIGX_FW_ERROR_MESSAGES': 'ERROR_CODE,USER_LOCALE',
    }
    refs = {
        'DIGX_EP_ACT_EVT_B': 'FOREIGN KEY(COD_ACT_ID) REFERENCES DIGX_EP_ACT_B(COD_ACT_ID)',
        'DIGX_EP_ACT_EVT_ACN_B': 'FOREIGN KEY(COD_ACT_ID,COD_EVENT_ID) REFERENCES DIGX_EP_ACT_EVT_B(COD_ACT_ID,COD_EVENT_ID)',
        'DIGX_EP_EVT_REC_B': 'FOREIGN KEY(COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID) REFERENCES DIGX_EP_ACT_EVT_ACN_B(COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID)',
    }
    for table, cols in definitions.items():
        fields = ','.join(cols) + ',PRIMARY KEY(' + keys[table] + ')'
        if table in refs:
            fields += ',' + refs[table]
        db.execute('CREATE TABLE ' + table + '(' + fields + ')')
    db.execute("INSERT INTO DIGX_EP_ACT_B(COD_ACT_ID,OBJECT_STATUS) VALUES ('BCO_EXISTING','A')")
    db.execute("INSERT INTO DIGX_FW_ERROR_MESSAGES(ERROR_CODE,USER_LOCALE,ERROR_MESSAGE) VALUES ('BCO_EXISTING','en','unchanged')")
    db.commit()
    return db


def replay(db, sql):
    db.execute('SAVEPOINT deployment')
    try:
        for statement in statements(sql):
            if statement.startswith('INSERT ALL'):
                chunks = re.split(r'\bINTO\s+(?=DIGX_)', statement)[1:]
                for chunk in chunks:
                    chunk = re.sub(r'SELECT 1 FROM DUAL\s*$', '', chunk).strip()
                    db.execute(portable('INSERT INTO ' + chunk))
            else:
                db.execute(portable(statement))
    except Exception:
        db.execute('ROLLBACK TO deployment')
        db.execute('RELEASE deployment')
        raise
    db.execute('RELEASE deployment')


def snapshot(db):
    return {name: sorted(db.execute('SELECT * FROM ' + name).fetchall(), key=repr)
            for name, in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}


def notification_check(db):
    query = SQL[7].split('-- Expected: four rows, CONFIG_STATUS=OK;', 1)[1]
    query = query[query.index('WITH expected AS'):].split(';', 1)[0]
    rows = db.execute(portable(query)).fetchall()
    assert len(rows) == 4 and all(row[-1] == 'OK' for row in rows), rows


db = connect()
for iteration in range(2):
    replay(db, body(6))
    replay(db, body(5))
    notification_check(db)
    for table, count in [('DIGX_EP_ACT_B', 4), ('DIGX_PM_EVENT_ALL_B', 4),
                         ('DIGX_EP_ACT_EVT_B', 4), ('DIGX_EP_ACT_EVT_ACN_B', 4),
                         ('DIGX_EP_EVT_REC_B', 18), ('DIGX_EP_MSG_TMPL_B', 18),
                         ('DIGX_FW_ERROR_MESSAGES', 31)]:
        assert db.execute('SELECT COUNT(*) FROM ' + table).fetchone()[0] == count, table
    assert db.execute("SELECT OBJECT_STATUS FROM DIGX_EP_ACT_B WHERE COD_ACT_ID='BCO_EXISTING'").fetchone()[0] == 'A'
    assert db.execute("SELECT ERROR_MESSAGE FROM DIGX_FW_ERROR_MESSAGES WHERE ERROR_CODE='BCO_EXISTING'").fetchone()[0] == 'unchanged'
    assert db.execute("SELECT COUNT(*) FROM DIGX_FW_ERROR_MESSAGES WHERE ERROR_CODE LIKE 'DIGX_CZ_HTH_API_PASSWORD_%' GROUP BY USER_LOCALE").fetchall() == [(10,), (10,), (10,)]
print('PASS: two deployments retain BCO rows, 3 HTH Activities, 4 events/actions, 18 recipients/templates, 30 error messages')

# A stored activity reference and another business unit's template must survive reruns.
activity = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
db.execute('CREATE TABLE EXISTING_ACTIVITY_REFERENCE(ID PRIMARY KEY, ACTIVITY_ID REFERENCES DIGX_EP_ACT_B(COD_ACT_ID))')
db.execute('INSERT INTO EXISTING_ACTIVITY_REFERENCE VALUES (1,?)', (activity,))
db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B(COD_TMPL_ID,DETERMINANT_VALUE,TXT_MSG_TMPL) VALUES ('HTH_API_PWD_RESET_USER_EMAIL_en','OTHER_BU','preserve me')")
replay(db, body(6))
assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE DETERMINANT_VALUE='OTHER_BU'").fetchone()[0] == 'preserve me'
notification_check(db)

# Reproduce the observed missing Activity, then verify the same script repairs it.
broken = connect()
without_parents = '\n'.join(s + ';' for s in statements(body(6))
                           if not re.match(r'(?:UPDATE|INSERT INTO) DIGX_EP_ACT_B\b', s))
try:
    replay(broken, without_parents)
    raise AssertionError('Missing parent Activity was accepted')
except sqlite3.IntegrityError:
    pass
assert broken.execute('SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_B').fetchone()[0] == 0
replay(broken, body(6))
notification_check(broken)

# Force a late failure after earlier deletes/inserts and confirm complete restoration.
for number, table, condition in [
        (6, 'DIGX_EP_MSG_TMPL_B', "NEW.COD_TMPL_ID='HTH_API_PWD_CODE_COMPANY_EMAIL_zh-Hant'"),
        (5, 'DIGX_FW_ERROR_MESSAGES', "NEW.ERROR_CODE='DIGX_CZ_HTH_API_PASSWORD_010' AND NEW.USER_LOCALE='zh-hant'")]:
    before = snapshot(db)
    db.execute(f"CREATE TRIGGER force_failure BEFORE INSERT ON {table} WHEN {condition} BEGIN SELECT RAISE(ABORT,'injected late failure'); END")
    try:
        replay(db, body(number))
        raise AssertionError('Injected failure was not reached')
    except sqlite3.IntegrityError:
        pass
    assert snapshot(db) == before, number
    db.execute('DROP TRIGGER force_failure')
print('PASS: missing Activity reproduces failure; parent repair, external references, BU isolation and late-failure rollback')

# The actual verification query must flag broken parents and language/channel wiring.
for mutation in [
        "UPDATE DIGX_EP_ACT_B SET DOMAIN_OBJECT_EXTN='OTHER' WHERE COD_ACT_ID=?",
        "UPDATE DIGX_EP_ACT_B SET OBJECT_STATUS='I' WHERE COD_ACT_ID=?",
        "UPDATE DIGX_EP_EVT_REC_B SET LOCALE='xx' WHERE COD_ACT_ID=? AND TXT_DEST_TYP='SMS' AND LOCALE='en'",
        "DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID=? AND TXT_DEST_TYP='SMS' AND LOCALE='en'"]:
    db.execute('SAVEPOINT corrupt')
    db.execute(mutation, (activity,))
    try:
        notification_check(db)
        raise RuntimeError('Verification accepted a broken configuration')
    except AssertionError:
        pass
    db.execute('ROLLBACK TO corrupt')
    db.execute('RELEASE corrupt')
print('PASS: deployed verification detects inactive/wrong-domain parents and missing/wrong-locale recipients')

# Keep the Oracle verification's expected error rows, adapting only its LEVEL generator.
errors = SQL[7].split('-- Expected: ten rows,', 1)[1]
errors = errors[errors.index('WITH expected AS'):].split(';', 1)[0]
db.create_function('TO_CHAR', 2, lambda value, fmt: f'{value:03d}')
levels = ' UNION ALL '.join(f'SELECT {i} AS LEVEL' for i in range(1, 11))
errors = errors.replace('FROM DUAL CONNECT BY LEVEL <= 10', 'FROM (' + levels + ')')
rows = db.execute(errors).fetchall()
assert len(rows) == 10 and all(row[-1] == 'OK' for row in rows)
db.execute('SAVEPOINT missing_error')
db.execute("DELETE FROM DIGX_FW_ERROR_MESSAGES WHERE ERROR_CODE='DIGX_CZ_HTH_API_PASSWORD_010' AND USER_LOCALE='zh-hant'")
rows = db.execute(errors).fetchall()
assert len(rows) == 10 and rows[-1][-1] == 'MISSING_OR_INVALID'
db.execute('ROLLBACK TO missing_error')
db.execute('RELEASE missing_error')
print('PASS: error verification includes 010 and detects a missing translation')

# Replay the property helper's DML using binds; do not emulate the whole PL/SQL program.
adapter = SQL[4]
helper = adapter.split('PROCEDURE PUT_PROPERTY(', 1)[1].split('\n  END;', 1)[0]
update = re.search(r'UPDATE DIGX_FW_CONFIG_ADAPTER_PROP_B.*?;', helper, re.S).group()
insert = re.search(r'INSERT INTO DIGX_FW_CONFIG_ADAPTER_PROP_B.*?;', helper, re.S).group()
cols = inserts(insert)[0][1]
db.execute('CREATE TABLE DIGX_FW_CONFIG_ADAPTER_PROP_B(' + cols + ',PRIMARY KEY(HOST_ID,TRANSACTION_TYPE,PROP_ID))')


def put(prop, value, replace=False):
    for sql in ([update] if replace else []) + [insert]:
        sql = outside_literals(sql, lambda p: re.sub(r'\bP_(ID|VALUE)\b', r':P_\1', p))
        db.execute(portable(sql), {'P_ID': prop, 'P_VALUE': value})


preserved = {'CODE_CIPHER_KEY': 'test-key-marker', 'STORAGE_BACKEND': 'UAM', 'ENABLED': 'false',
             'SERVICE_URL': 'https://example.test', 'POLICY_MIN_LENGTH': '10', 'READ_TIMEOUT_MS': '30000'}
for prop, value in preserved.items():
    put(prop, value)
for prop, value in [('STORAGE_BACKEND', 'DATABASE'), ('ENABLED', 'true'),
                    ('POLICY_MIN_LENGTH', '8'), ('READ_TIMEOUT_MS', '15000')]:
    put(prop, value)
assert dict(db.execute('SELECT PROP_ID,PROP_VALUE FROM DIGX_FW_CONFIG_ADAPTER_PROP_B')) == preserved
put('READ_TIMEOUT_MS', '25000', True)
assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ADAPTER_PROP_B WHERE PROP_ID='READ_TIMEOUT_MS'").fetchone()[0] == '25000'
assert re.search(r'V_STORAGE_BACKEND VARCHAR2\(8\) := NULL;', adapter)
assert re.search(r'V_ENABLED VARCHAR2\(5\) := NULL;', adapter)
assert "EXISTING_PROPERTY('STORAGE_BACKEND', 'DATABASE')" in adapter
assert "EXISTING_PROPERTY('ENABLED', 'true')" in adapter
assert 'DELETE FROM DIGX_FW_CONFIG_ADAPTER_PROP_B' not in adapter
assert "Existing Code key differs; refusing to overwrite." in adapter
assert all("DETERMINANT_VALUE = 'OBDX_BU'" in stmt for stmt in statements(adapter)
           if stmt.startswith('DELETE FROM DIGX_FW_CONFIG_ALL_O'))
print('PASS: property helper preserves key/UAM/disabled/policy/timeout values and inserts no duplicates')

for number, sql in SQL.items():
    assert not re.search(r'(?im)^\s*(?:DEFINE|UNDEFINE|WHENEVER|PROMPT|SPOOL)\b|^\s*/\s*$', sql), number
    assert not re.search(r'(?i)(?:INSERT INTO|UPDATE|DELETE FROM|MERGE INTO)\s+DIGX_\w+_V\b', sql), number
    assert not re.search(r'(?i)(?:DELETE FROM|UPDATE|TRUNCATE TABLE|DROP TABLE)\s+HTH_BEA\.HTH_API_PASSWORD', sql), number
assert ':PARTY_ID' not in SQL[7] and ':USER_ID' not in SQL[7]
baseline = (ROOT / 'consulting/db/branch_change_history/20260830_BCOH2H-787_HTH_API_Password/1_HTH_API_Password_Schema.sql').read_text()
for index in ['IX_HTH_API_PWD_CODE_OWN', 'IX_HTH_API_PWD_CODE_TXN']:
    assert index in baseline and index in SQL[7]
assert "'DIGX_CZ_HTH_API_PASSWORD_010'" in SQL[5] and 'LEVEL <= 10' in SQL[7]
assert 'DELETE FROM DIGX_CM_TASK\n' not in SQL[2]
print('PASS: seven scripts contain no SQL*Plus directives, view DML or credential/Code data reset; verification matches the baseline')
