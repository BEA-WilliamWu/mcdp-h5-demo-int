"""1216 SQL rerun/rollback and coexistence with the actual 851 deployment DML.
SQLite validates DML/constraints, not Oracle PL/SQL; Java tests use the actual ledger DDL.
"""
from pathlib import Path
import re
import sqlite3
from verify_hth_contact_sql import deployed as contact_deployed, replay, snapshot, statements
ROOT = Path(__file__).resolve().parents[3]
SQL_DIR = ROOT / 'consulting/db/branch_change_history/20260916_HTH_User_Access_1216'
SQL = (SQL_DIR / '2_HTH_Access_Notification_Config.sql').read_text()
ACTIVITY = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.'

def deployed():
    db = contact_deployed()
    base = {table: db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0] for table in
            ['DIGX_EP_ACT_B','DIGX_PM_EVENT_ALL_B','DIGX_EP_ACT_EVT_B','DIGX_EP_ACT_EVT_ACN_B',
             'DIGX_EP_EVT_REC_B','DIGX_EP_MSG_TMPL_B','DIGX_MD_SERVICE_ATTR','DIGX_EP_MSG_ATTR_B','DIGX_EP_MSG_SRC_B']}
    # Existing BCO globals must not be overwritten, even when values differ from our new DTO metadata.
    for field in ['userNameId','userSysDate','compName']:
        db.execute('INSERT INTO DIGX_MD_GEN_ATTR_LEGACY_B(COD_CONSTRAINT_ATTR_ID,TXT_CONSTRAINT_ATTR_NAME,DATA_TYPE,OBJECT_STATUS) VALUES (?,?,?,?)',(field,field,'java.lang.String','A'))
    db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B(COD_TMPL_ID,DETERMINANT_VALUE,TXT_MSG_TMPL) VALUES ('HTH_1216_LINK_EMAIL_en','OTHER_BU','preserved')")
    db.commit()
    assert re.search(r'EXCEPTION WHEN OTHERS THEN\s+ROLLBACK TO HTH_ACCESS_CONFIG;\s+RAISE;',SQL)
    body=SQL.split('SAVEPOINT HTH_ACCESS_CONFIG;',1)[1].split('EXCEPTION',1)[0]
    deltas=[2,2,2,2,12,13,6,18,18]
    for _ in range(2):
        replay(db,body)
        for (table,count),delta in zip(base.items(),deltas):
            assert db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==count+delta,table
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_USER_ACCESS_NOTIFICATION_ENABLED'").fetchone()[0]=='false'
    db.execute("UPDATE DIGX_FW_CONFIG_ALL_B SET PROP_VALUE='true' WHERE PROP_ID='HTH_USER_ACCESS_NOTIFICATION_ENABLED'")
    replay(db,body)
    assert db.execute("SELECT PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B WHERE PROP_ID='HTH_USER_ACCESS_NOTIFICATION_ENABLED'").fetchone()[0]=='true'
    assert db.execute("SELECT TXT_MSG_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID='HTH_1216_LINK_EMAIL_en' AND DETERMINANT_VALUE='OTHER_BU'").fetchone()[0]=='preserved'
    before=snapshot(db)
    db.execute("CREATE TRIGGER fail_1216 BEFORE INSERT ON DIGX_EP_MSG_TMPL_B WHEN NEW.COD_TMPL_ID='HTH_1216_UPDATE_SMS_zh-Hans-CN' BEGIN SELECT RAISE(ABORT,'injected late failure'); END")
    try:
        replay(db,body);raise AssertionError('failure injection missing')
    except sqlite3.IntegrityError: pass
    assert snapshot(db)==before
    db.execute('DROP TRIGGER fail_1216')
    queries=statements((SQL_DIR/'3_Verify_HTH_Access_Notification.sql').read_text())
    checked=db.execute(next(q for q in queries if q.startswith('WITH expected AS'))).fetchall()
    assert len(checked)==2 and all(row[1:]==(1,1,1,6) for row in checked),checked
    metadata=next(q for q in queries if q.startswith('WITH expected_fields AS'))
    checked=db.execute(metadata).fetchall()
    assert len(checked)==12 and all(row[2]==row[3] for row in checked),checked
    for tmpl,text,subject in db.execute("SELECT COD_TMPL_ID,TXT_MSG_TMPL,TXT_SUBJECT_TMPL FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID LIKE 'HTH_1216_%' AND DETERMINANT_VALUE='OBDX_BU'"):
        fields=set(re.findall(r'#(\w+)#',(text or '')+(subject or '')))
        attrs={row[0] for row in db.execute('SELECT COD_ATTR_ID FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID=? AND DETERMINANT_VALUE=?',(tmpl,'OBDX_BU'))}
        assert fields==attrs,(tmpl,fields,attrs)
        if '_SMS_' in tmpl: assert len(text)<=(160 if tmpl.endswith('_en') else 70)
    return db
if __name__=='__main__':
    deployed()
    print('PASS: 1216 DML rerun/rollback/default-off, preserves 851/BCO/other BU, 12 templates and 18 metadata bindings')
