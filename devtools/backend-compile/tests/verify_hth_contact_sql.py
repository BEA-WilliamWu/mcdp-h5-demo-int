"""Replay the actual 851 deployment DML with relational constraints (SQLite).

Oracle date/user expressions alone are adapted. This is not an Oracle PL/SQL run.
Also exports metadata/template fixtures for the Java runtime verification.
"""
from pathlib import Path
import ast
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[3]
SQL_DIR = ROOT / 'consulting/db/branch_change_history/20260915_HTH_Profile_Contact_851'
SQL = (SQL_DIR / '2_HTH_Contact_Notification_Config.sql').read_text()
ACTIVITY = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update'

# Reuse parser helpers without executing the unrelated password SQL test suite.
helpers = ast.parse(Path(__file__).with_name('verify_hth_deployment_sql.py').read_text())
namespace = {'re': re, 'LITERAL': r"'(?:''|[^'])*'"}
for node in helpers.body:
    if isinstance(node, ast.FunctionDef) and node.name in (
            'outside_literals', 'statements', 'portable', 'inserts', 'replay', 'snapshot'):
        exec(compile(ast.Module(body=[node], type_ignores=[]), '<sql-parser>', 'exec'), namespace)
statements, portable, inserts, replay, snapshot = (
    namespace[n] for n in ('statements', 'portable', 'inserts', 'replay', 'snapshot'))


def database():
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    db.executescript("CREATE TABLE DUAL(DUMMY); INSERT INTO DUAL VALUES ('X');")
    keys = {
        'DIGX_FW_CONFIG_ALL_B': 'CATEGORY_ID,PROP_ID',
        'DIGX_EP_ACT_B': 'COD_ACT_ID', 'DIGX_PM_EVENT_ALL_B': 'EVENT_CODE',
        'DIGX_EP_ACT_EVT_B': 'COD_ACT_ID,COD_EVENT_ID',
        'DIGX_EP_ACT_EVT_ACN_B': 'COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID',
        'DIGX_EP_EVT_REC_B': 'COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,TXT_DEST_TYP,LOCALE',
        'DIGX_EP_MSG_TMPL_B': 'COD_TMPL_ID,DETERMINANT_VALUE',
        'DIGX_MD_GEN_ATTR_LEGACY_B': 'COD_CONSTRAINT_ATTR_ID',
        'DIGX_MD_SERVICE_ATTR': 'COD_SERVICE_ATTR_ID',
        'DIGX_EP_MSG_ATTR_B': 'COD_MESS_TMPL_ID,COD_ATTR_ID,DETERMINANT_VALUE',
        'DIGX_EP_MSG_SRC_B': 'COD_MESS_TMPL_ID,COD_ATTR_ID,COD_ACT_ID,DETERMINANT_VALUE',
    }
    references = {
        'DIGX_EP_ACT_EVT_B': 'FOREIGN KEY(COD_ACT_ID) REFERENCES DIGX_EP_ACT_B(COD_ACT_ID)',
        'DIGX_EP_ACT_EVT_ACN_B': 'FOREIGN KEY(COD_ACT_ID,COD_EVENT_ID) REFERENCES DIGX_EP_ACT_EVT_B(COD_ACT_ID,COD_EVENT_ID)',
        'DIGX_EP_EVT_REC_B': 'FOREIGN KEY(COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID) REFERENCES DIGX_EP_ACT_EVT_ACN_B(COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID)',
        'DIGX_MD_SERVICE_ATTR': 'FOREIGN KEY(COD_ATTR_ID) REFERENCES DIGX_MD_GEN_ATTR_LEGACY_B(COD_CONSTRAINT_ATTR_ID)',
        'DIGX_EP_MSG_ATTR_B': 'FOREIGN KEY(COD_MESS_TMPL_ID,DETERMINANT_VALUE) REFERENCES DIGX_EP_MSG_TMPL_B(COD_TMPL_ID,DETERMINANT_VALUE), FOREIGN KEY(COD_ATTR_ID) REFERENCES DIGX_MD_GEN_ATTR_LEGACY_B(COD_CONSTRAINT_ATTR_ID)',
        'DIGX_EP_MSG_SRC_B': 'FOREIGN KEY(COD_MESS_TMPL_ID,COD_ATTR_ID,DETERMINANT_VALUE) REFERENCES DIGX_EP_MSG_ATTR_B(COD_MESS_TMPL_ID,COD_ATTR_ID,DETERMINANT_VALUE), FOREIGN KEY(COD_SERVICE_ATTR_ID) REFERENCES DIGX_MD_SERVICE_ATTR(COD_SERVICE_ATTR_ID)',
    }
    definitions = dict(inserts(SQL))
    for table, columns in definitions.items():
        fields = columns + ', PRIMARY KEY(' + keys[table] + ')'
        if table in references:
            fields += ',' + references[table]
        db.execute('CREATE TABLE ' + table + '(' + fields + ')')
    return db


def deployed():
    assert re.search(r'EXCEPTION\s+WHEN OTHERS THEN\s+ROLLBACK TO HTH_CONTACT_CONFIG;\s+RAISE;', SQL)
    body = SQL.split('SAVEPOINT HTH_CONTACT_CONFIG;', 1)[1].split('EXCEPTION', 1)[0]
    assert not re.search(r'\bCOMMIT\b', body)
    db = database()
    # A shared BCO activity must keep its original class/extension/status.
    db.execute('INSERT INTO DIGX_EP_ACT_B(COD_ACT_ID,TXT_LOG_CLASS,DOMAIN_OBJECT_EXTN,OBJECT_STATUS) VALUES (?,?,?,?)',
               (ACTIVITY, 'BCO_LOG_CLASS', 'BCO_EXTENSION', 'A'))
    db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B(COD_TMPL_ID,DETERMINANT_VALUE,TXT_MSG_TMPL) VALUES ('HTH_851_EMAIL_API_EMAIL_en','OTHER_BU','preserved')")
    db.commit()
    expected = {
        'DIGX_FW_CONFIG_ALL_B': 2, 'DIGX_EP_ACT_B': 1, 'DIGX_PM_EVENT_ALL_B': 6,
        'DIGX_EP_ACT_EVT_B': 6, 'DIGX_EP_ACT_EVT_ACN_B': 6,
        'DIGX_EP_EVT_REC_B': 36, 'DIGX_EP_MSG_TMPL_B': 37,
        'DIGX_MD_GEN_ATTR_LEGACY_B': 2, 'DIGX_MD_SERVICE_ATTR': 2,
        'DIGX_EP_MSG_ATTR_B': 36, 'DIGX_EP_MSG_SRC_B': 36,
    }
    for _ in range(2):
        replay(db, body)
        for table, count in expected.items():
            assert db.execute('SELECT COUNT(*) FROM ' + table).fetchone()[0] == count, table
        assert db.execute('SELECT TXT_LOG_CLASS,DOMAIN_OBJECT_EXTN FROM DIGX_EP_ACT_B').fetchone() == ('BCO_LOG_CLASS', 'BCO_EXTENSION')
        assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE DETERMINANT_VALUE='OTHER_BU'").fetchone()[0] == 'preserved'
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'").fetchone()[0] == 'false'
    db.execute("UPDATE DIGX_FW_CONFIG_ALL_B SET PROP_VALUE='true' WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'")
    replay(db, body)
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'").fetchone()[0] == 'true'
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_ACN_B WHERE FLG_RETRY_ALLOWED='N' AND NUM_RETRY_CNT=0").fetchone()[0] == 6

    before = snapshot(db)
    db.execute("CREATE TRIGGER inject_failure BEFORE INSERT ON DIGX_EP_MSG_TMPL_B WHEN NEW.COD_TMPL_ID='HTH_851_MOBILE_AP_SMS_zh-Hans-CN' BEGIN SELECT RAISE(ABORT,'late deployment failure'); END")
    try:
        replay(db, body)
        raise AssertionError('Failure injection did not execute')
    except sqlite3.IntegrityError:
        pass
    assert snapshot(db) == before
    db.execute('DROP TRIGGER inject_failure')

    for template, body_text, subject in db.execute("SELECT COD_TMPL_ID,TXT_MSG_TMPL,TXT_SUBJECT_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE DETERMINANT_VALUE='OBDX_BU'"):
        fields = set(re.findall(r'#(hthContact\w+)#', (body_text or '') + (subject or '')))
        rows = db.execute('''SELECT A.COD_ATTR_ID,S.REF_FIELD_DEFN_ID,S.TYP_DATA_SRC
             FROM DIGX_EP_MSG_ATTR_B A JOIN DIGX_EP_MSG_SRC_B M
             ON M.COD_MESS_TMPL_ID=A.COD_MESS_TMPL_ID AND M.COD_ATTR_ID=A.COD_ATTR_ID
             AND M.DETERMINANT_VALUE=A.DETERMINANT_VALUE
             JOIN DIGX_MD_SERVICE_ATTR S ON S.COD_SERVICE_ATTR_ID=M.COD_SERVICE_ATTR_ID
             WHERE A.COD_MESS_TMPL_ID=? AND A.DETERMINANT_VALUE='OBDX_BU' ''', (template,)).fetchall()
        assert {row[0] for row in rows} == fields, template
        assert all(row[2] == 'DTO' for row in rows), rows
    # Identical API/AP SMS text is the basis for cross-role SMS deduplication.
    for kind in ('CONTACT', 'EMAIL', 'MOBILE'):
        for locale in ('en', 'zh-Hant', 'zh-Hans-CN'):
            sms = [db.execute('SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID=?',
                  (f'HTH_851_{kind}_{role}_SMS_{locale}',)).fetchone()[0] for role in ('API', 'AP')]
            assert sms[0] == sms[1]
            assert len(sms[0]) <= (160 if locale == "en" else 70), "matrix SMS fits one message"
    queries = statements((SQL_DIR / '3_Verify_HTH_Contact_Notification.sql').read_text())
    event_query = next(q for q in queries if q.startswith('WITH expected AS'))
    metadata_query = next(q for q in queries if q.startswith('WITH expected_fields AS'))
    checked = db.execute(event_query).fetchall()
    assert len(checked) == 6 and all(row[1:] == (1, 1, 1, 6) for row in checked), checked
    checked = db.execute(metadata_query).fetchall()
    assert len(checked) == 36 and all(row[2] == row[3] for row in checked), checked
    db.execute('DELETE FROM DIGX_EP_MSG_SRC_B')
    assert any(row[2] != row[3] for row in db.execute(metadata_query))
    replay(db, body)
    assert all(row[2] == row[3] for row in db.execute(metadata_query))
    return db


if __name__ == '__main__':
    deployed()
    print('PASS: 851 SQL replay/rerun/rollback, BCO and other-BU preservation, 36 templates, 36 metadata bindings, default-off flag')
