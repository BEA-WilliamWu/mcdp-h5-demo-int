"""Check 851 config reruns and rollback without changing BCO event/template data.
Oracle expressions are adapted to SQLite; this is not an Oracle deployment run.
"""
from pathlib import Path
import ast
import re
import sqlite3
ROOT = Path(__file__).resolve().parents[3]
SQL_DIR = ROOT / 'consulting/db/branch_change_history/20260917_HTH_Profile_Contact_851'
SQL = (SQL_DIR / '2_HTH_Contact_Notification_Config.sql').read_text()
helpers = ast.parse(Path(__file__).with_name('verify_hth_deployment_sql.py').read_text())
namespace = {'re': re, 'LITERAL': r"'(?:''|[^'])*'"}
for node in helpers.body:
    if isinstance(node, ast.FunctionDef) and node.name in ('outside_literals', 'statements', 'portable', 'inserts', 'replay', 'snapshot'):
        exec(compile(ast.Module(body=[node], type_ignores=[]), '<sql-parser>', 'exec'), namespace)

def deployed():
    assert 'ROLLBACK TO HTH_CONTACT_CONFIG;' in SQL
    body = SQL.split('SAVEPOINT HTH_CONTACT_CONFIG;', 1)[1].split('EXCEPTION', 1)[0]
    tables = dict(namespace['inserts'](SQL))
    assert set(tables) == {'DIGX_FW_CONFIG_ALL_B'}, 'BCO templates and events must remain unchanged'
    db = sqlite3.connect(':memory:')
    db.executescript("CREATE TABLE DUAL(DUMMY); INSERT INTO DUAL VALUES ('X');")
    db.execute('CREATE TABLE DIGX_FW_CONFIG_ALL_B ('+tables['DIGX_FW_CONFIG_ALL_B']+', PRIMARY KEY(CATEGORY_ID, PROP_ID))')
    db.execute("INSERT INTO DIGX_FW_CONFIG_ALL_B(CATEGORY_ID,PROP_ID,PROP_VALUE) VALUES ('BCO','test','preserved')")
    db.commit()
    for _ in range(2):
        namespace['replay'](db, body)
        assert db.execute('SELECT COUNT(*) FROM DIGX_FW_CONFIG_ALL_B').fetchone()[0] == 3
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE CATEGORY_ID='BCO'").fetchone()[0] == 'preserved'
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'").fetchone()[0] == 'false'
    db.execute("UPDATE DIGX_FW_CONFIG_ALL_B SET PROP_VALUE='true' WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'")
    namespace['replay'](db, body)
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED'").fetchone()[0] == 'true'
    return db
if __name__ == '__main__':
    deployed()
    print('PASS: 851 config rerun/default-off/preserve enable switch; no BCO event/template changes')
