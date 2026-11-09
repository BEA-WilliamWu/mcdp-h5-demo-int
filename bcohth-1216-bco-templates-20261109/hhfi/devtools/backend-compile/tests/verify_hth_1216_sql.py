"""Run the deployment SQL's source selectors against BCO recipient variants.

SQLite executes the extracted SELECT/DELETE statements with bound PL/SQL variables.
This covers the UAT ambiguity regression and cleanup scope, not Oracle block execution.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[3]
SQL = (ROOT / 'consulting/db/branch_change_history/20260917_BCOH2H-1216/'
       '1216_HTH_User_Access_Notification.sql').read_text()
CONSTANTS = dict(re.findall(r"(c_\w+) CONSTANT VARCHAR2\(\d+\) := '([^']+)';", SQL))


def extract(pattern):
    match = re.search(pattern, SQL, re.S)
    assert match, pattern
    return match.group(1)


def execute(statement, **values):
    statement = statement.replace(' INTO v_count', '').replace('NVL(', 'COALESCE(')
    statement = re.sub(r'\broute\.(\w+)', r':route_\1', statement)
    statement = re.sub(r'\b[cp]_\w+\b', lambda m: ':' + m[0], statement)
    return db.execute(statement, {**CONSTANTS, **values})


db = sqlite3.connect(':memory:')
db.row_factory = sqlite3.Row
db.executescript('''
CREATE TABLE DUAL (dummy TEXT);
INSERT INTO DUAL VALUES ('X');
CREATE TABLE DIGX_EP_EVT_REC_B (
 COD_ACT_ID TEXT, COD_EVENT_ID TEXT, COD_ACTION_ID TEXT, SUBSCRIBER_TYPE TEXT,
 SUBSCRIBER_VALUE TEXT, TXT_DEST_TYP TEXT, LOCALE TEXT, COD_MSG_TMPL_ID TEXT,
 FLG_CONDITIONAL TEXT, UNSECURE_MSG_TMPL_ID TEXT);
CREATE TABLE DIGX_EP_MSG_TMPL_B (
 COD_TMPL_ID TEXT, DETERMINANT_VALUE TEXT, OBJECT_STATUS TEXT, DESTINATION_TYPE TEXT);
''')
for locale in ('en', 'zh-Hant', 'zh-Hans-CN'):
    for subscriber, value in (('CORPORATE', 'USER'), ('PARTY', 'CUSTOMER')):
        for dest in ('EMAIL', 'SMS'):
            template = f'USER_ACCOUNT_ACCESS_UPDATE_{subscriber}_{value}_{dest}_{locale}'
            row = (CONSTANTS['c_source'], CONSTANTS['c_bco_event'], 'A',
                   subscriber, value, dest, locale, template, 'N', None)
            db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?,?,?,?)', row)
            db.execute('INSERT INTO DIGX_EP_MSG_TMPL_B VALUES (?,?,?,?)',
                       (template, 'OBDX_BU', 'A', dest))

routes = list(execute(extract(r'CURSOR c_routes IS\s*(.*?);')))
assert {(r['event_id'], r['subscriber_type'], r['subscriber_value'], r['dest']) for r in routes} == {
    (CONSTANTS['c_event'], 'CORPORATE', 'USER', 'EMAIL'),
    (CONSTANTS['c_event'], 'CORPORATE', 'USER', 'SMS'),
    (CONSTANTS['c_company_event'], 'PARTY', 'CUSTOMER', 'EMAIL'),
}
templates = extract(r'CURSOR c_templates\(.*?\) IS\s*(.*?);')
ambiguity = extract(r'FOR r IN \(\s*(SELECT LOCALE, COUNT\(DISTINCT COD_MSG_TMPL_ID\).*?)\s*\) LOOP')
usable_english = extract(r'(SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_EVT_REC_B r JOIN DIGX_EP_MSG_TMPL_B t.*?);')
conditional = extract(r'(SELECT COUNT\(\*\) INTO v_count FROM DIGX_EP_EVT_REC_B\s+WHERE.*?);')


def bindings(route):
    return {'route_' + key: route[key] for key in route.keys()}


def validate(route):
    args = bindings(route)
    assert execute(conditional, **args).fetchone()[0] == 0, 'conditional source'
    assert execute(usable_english, **args).fetchone()[0] > 0, 'missing active English source'
    for row in execute(ambiguity, **args):
        assert (row['n'] == 1 and row['n_alt'] == 1 and row['total_rows'] == row['primary_rows']
                and row['LOCALE']), 'ambiguous within recipient group'


for route in routes:
    validate(route)
    chosen = list(execute(templates, p_type=route['subscriber_type'],
                          p_value=route['subscriber_value'], p_dest=route['dest']))
    assert len(chosen) == 3
    for row in chosen:
        assert row['COD_MSG_TMPL_ID'] == ('USER_ACCOUNT_ACCESS_UPDATE_'
                f"{route['subscriber_type']}_{route['subscriber_value']}_{route['dest']}_{row['LOCALE']}")
print('PASS: separate BCO user/company templates in all three languages; no company SMS route')

# The actual UAT screenshot has these two IDs for SMS/en. Same ID duplicates are harmless.
assert db.execute("SELECT COUNT(DISTINCT COD_MSG_TMPL_ID) FROM DIGX_EP_EVT_REC_B "
                  "WHERE TXT_DEST_TYP='SMS' AND LOCALE='en'").fetchone()[0] == 2
db.execute("INSERT INTO DIGX_EP_EVT_REC_B SELECT * FROM DIGX_EP_EVT_REC_B "
           "WHERE TXT_DEST_TYP='SMS' AND LOCALE='en' AND SUBSCRIBER_TYPE='CORPORATE'")
validate(routes[1])
assert len(list(execute(templates, p_type='CORPORATE', p_value='USER', p_dest='SMS'))) == 3


def rejected(mutation, route, reason):
    db.execute('SAVEPOINT negative_case')
    db.execute(mutation)
    try:
        validate(route)
    except AssertionError as error:
        assert str(error) == reason, str(error)
    else:
        raise AssertionError('invalid source accepted: ' + reason)
    finally:
        db.execute('ROLLBACK TO negative_case')
        db.execute('RELEASE negative_case')


rejected("INSERT INTO DIGX_EP_EVT_REC_B SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, "
         "SUBSCRIBER_TYPE, SUBSCRIBER_VALUE, TXT_DEST_TYP, LOCALE, 'CONFLICT', 'N', NULL "
         "FROM DIGX_EP_EVT_REC_B WHERE SUBSCRIBER_TYPE='CORPORATE' AND TXT_DEST_TYP='SMS' AND LOCALE='en'",
         routes[1], 'ambiguous within recipient group')
rejected("DELETE FROM DIGX_EP_EVT_REC_B WHERE SUBSCRIBER_TYPE='PARTY' AND TXT_DEST_TYP='EMAIL'",
         routes[2], 'missing active English source')
rejected("UPDATE DIGX_EP_MSG_TMPL_B SET OBJECT_STATUS='I' WHERE "
         "COD_TMPL_ID='USER_ACCOUNT_ACCESS_UPDATE_CORPORATE_USER_SMS_en'",
         routes[1], 'missing active English source')
rejected("UPDATE DIGX_EP_EVT_REC_B SET FLG_CONDITIONAL='Y' WHERE "
         "SUBSCRIBER_TYPE='CORPORATE' AND TXT_DEST_TYP='SMS'",
         routes[1], 'conditional source')
print('PASS: duplicate mappings dedup; genuine same-role ambiguity, missing/inactive and conditional sources rejected')

# Execute the deployment's actual scoped cleanup twice. BCO and other HTH events survive.
original = [tuple(r) for r in db.execute('SELECT * FROM DIGX_EP_EVT_REC_B')]
db.execute("INSERT INTO DIGX_EP_EVT_REC_B (COD_ACT_ID,COD_EVENT_ID) VALUES ('other','OTHER_HTH_EVENT')")
for event in (CONSTANTS['c_event'], CONSTANTS['c_company_event']):
    for operation in ('submit', 'edit'):
        db.execute("INSERT INTO DIGX_EP_EVT_REC_B (COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID) VALUES (?,?,'A')",
                   (CONSTANTS['c_base'] + operation, event))
cleanup = extract(r'(DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID IN .*?);')
for _ in range(2):
    execute(cleanup)
    remaining = [tuple(r) for r in db.execute('SELECT * FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID != ?',
                                            ('OTHER_HTH_EVENT',))]
    assert remaining == original
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID='OTHER_HTH_EVENT'").fetchone()[0] == 1
print('PASS: repeated cleanup targets both 1216 events only; BCO rows preserved')

# Direct sharing must not write template/attribute rows or delete the old copies.
assert not re.search(r'(?:INSERT INTO|UPDATE|DELETE FROM)\s+DIGX_EP_MSG_(?:TMPL|ATTR)_B\b', SQL)
assert 'DELETE FROM DIGX_MD_SERVICE_ATTR' not in SQL
assert 'DELETE FROM DIGX_EP_ACT_EVT_ACN_B' not in SQL
assert 'DELETE FROM DIGX_PM_EVENT_ALL_B' not in SQL
assert "'.1216.bco.'" in SQL
assert 'ROLLBACK TO HTH_1216_CONFIG' in SQL
assert not re.search(r'^\s*COMMIT;', SQL, re.M)

# Add a configured alternate and test ambiguous alternate/null primary safeguards.
db.execute("UPDATE DIGX_EP_EVT_REC_B SET UNSECURE_MSG_TMPL_ID='BCO-alternate' "
           "WHERE SUBSCRIBER_TYPE='CORPORATE' AND TXT_DEST_TYP='EMAIL' AND LOCALE='en'")
db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES ('BCO-alternate','OBDX_BU','A','EMAIL')")
validate(routes[0])
assert any(r['UNSECURE_MSG_TMPL_ID'] == 'BCO-alternate' for r in execute(
    templates, p_type='CORPORATE', p_value='USER', p_dest='EMAIL'))
rejected("INSERT INTO DIGX_EP_EVT_REC_B SELECT COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,"
         "SUBSCRIBER_TYPE,SUBSCRIBER_VALUE,TXT_DEST_TYP,LOCALE,COD_MSG_TMPL_ID,FLG_CONDITIONAL,'OTHER_ALT' "
         "FROM DIGX_EP_EVT_REC_B WHERE SUBSCRIBER_TYPE='CORPORATE' AND TXT_DEST_TYP='EMAIL' AND LOCALE='en'",
         routes[0], 'ambiguous within recipient group')
rejected("INSERT INTO DIGX_EP_EVT_REC_B SELECT COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,"
         "SUBSCRIBER_TYPE,SUBSCRIBER_VALUE,TXT_DEST_TYP,LOCALE,NULL,FLG_CONDITIONAL,UNSECURE_MSG_TMPL_ID "
         "FROM DIGX_EP_EVT_REC_B WHERE SUBSCRIBER_TYPE='CORPORATE' AND TXT_DEST_TYP='EMAIL' AND LOCALE='en'",
         routes[0], 'ambiguous within recipient group')

# Relational migration fixture. Actual SQL selectors/deletes/recipient INSERT and
# verification queries execute; PL/SQL row-record copies are represented by fixtures.
# This is deliberately not presented as an Oracle block execution.
for col in ('COD_DEC_ID','RECIPIENT_TYPE','ALERTTYPE','DOMAIN_OBJECT_EXTN','GROUPE_NAME','BANKER_TYPE','AMOUNT'):
    db.execute('ALTER TABLE DIGX_EP_EVT_REC_B ADD COLUMN '+col)
db.execute('ALTER TABLE DIGX_EP_MSG_TMPL_B ADD COLUMN TXT_MSG_TMPL')
db.execute('ALTER TABLE DIGX_EP_MSG_TMPL_B ADD COLUMN TXT_SUBJECT_TMPL')
db.executescript('''
CREATE TABLE DIGX_EP_MSG_ATTR_B(COD_MESS_TMPL_ID,COD_ATTR_ID,DETERMINANT_VALUE,
 PRIMARY KEY(COD_MESS_TMPL_ID,COD_ATTR_ID,DETERMINANT_VALUE));
CREATE TABLE DIGX_MD_SERVICE_ATTR(COD_SERVICE_ATTR_ID PRIMARY KEY,COD_SERVICE_ID,REF_FIELD_DEFN_ID,TYP_DATA_SRC,OBJECT_STATUS);
CREATE TABLE DIGX_EP_MSG_SRC_B(COD_MESS_TMPL_ID,COD_ATTR_ID,COD_ACT_ID,DETERMINANT_VALUE,COD_SERVICE_ATTR_ID,
 PRIMARY KEY(COD_MESS_TMPL_ID,COD_ATTR_ID,COD_ACT_ID,DETERMINANT_VALUE));
''')
for row in list(db.execute('SELECT COD_TMPL_ID FROM DIGX_EP_MSG_TMPL_B')):
    tid = row[0]
    db.execute('UPDATE DIGX_EP_MSG_TMPL_B SET TXT_MSG_TMPL=?,TXT_SUBJECT_TMPL=? WHERE COD_TMPL_ID=?',
               ('#userNameId# / #compName# / #userSysDate#', '#compName#', tid))
    for attr in ('userNameId','compName','userSysDate'):
        mid = tid+'.'+attr
        db.execute('INSERT INTO DIGX_EP_MSG_ATTR_B VALUES (?,?,?)', (tid,attr,'OBDX_BU'))
        db.execute('INSERT INTO DIGX_MD_SERVICE_ATTR VALUES (?,?,?,?,?)', (mid,CONSTANTS['c_source'],attr,'DTO','A'))
        db.execute('INSERT INTO DIGX_EP_MSG_SRC_B VALUES (?,?,?,?,?)', (tid,attr,CONSTANTS['c_source'],'OBDX_BU',mid))

# A queued old private template and an unrelated business unit must remain usable.
db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES ('HTH1216_EMAIL_en','OBDX_BU','A','EMAIL','old #userNameId#','old')")
db.execute("INSERT INTO DIGX_EP_MSG_TMPL_B VALUES ('BCO-alternate','OTHER_BU','A','EMAIL','other','other')")
db.execute("INSERT INTO DIGX_EP_MSG_ATTR_B VALUES ('HTH1216_EMAIL_en','userNameId','OBDX_BU')")
db.execute('INSERT INTO DIGX_MD_SERVICE_ATTR VALUES (?,?,?,?,?)',
           ('legacy1216',CONSTANTS['c_base']+'submit','userNameId','DTO','A'))
db.execute('INSERT INTO DIGX_EP_MSG_SRC_B VALUES (?,?,?,?,?)',
           ('HTH1216_EMAIL_en','userNameId',CONSTANTS['c_base']+'submit','OBDX_BU','legacy1216'))
for operation in ('submit','edit'):
    db.execute('INSERT INTO DIGX_EP_EVT_REC_B (COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,TXT_DEST_TYP,LOCALE,COD_MSG_TMPL_ID) VALUES (?,?,?,?,?,?)',
               (CONSTANTS['c_base']+operation,CONSTANTS['c_event'],'A','EMAIL','en','HTH1216_EMAIL_en'))
    # Non-A action survives the action-A migration.
    db.execute('INSERT INTO DIGX_EP_EVT_REC_B (COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID) VALUES (?,?,?)',
               (CONSTANTS['c_base']+operation,CONSTANTS['c_event'],'OTHER'))


def protected():
    return {
        'templates': [tuple(r) for r in db.execute('SELECT * FROM DIGX_EP_MSG_TMPL_B ORDER BY 1,2')],
        'attrs': [tuple(r) for r in db.execute('SELECT * FROM DIGX_EP_MSG_ATTR_B ORDER BY 1,2')],
        'sources': [tuple(r) for r in db.execute("SELECT * FROM DIGX_EP_MSG_SRC_B WHERE COD_ACT_ID=? OR COD_MESS_TMPL_ID='HTH1216_EMAIL_en' ORDER BY 1,2,3", (CONSTANTS['c_source'],))],
        'metadata': [tuple(r) for r in db.execute("SELECT * FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ID=? OR COD_SERVICE_ATTR_ID='legacy1216' ORDER BY 1", (CONSTANTS['c_source'],))],
        'routes': [tuple(r) for r in db.execute("SELECT * FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID=? OR COD_EVENT_ID='OTHER_HTH_EVENT' OR COD_ACTION_ID='OTHER' ORDER BY 1,2,3", (CONSTANTS['c_source'],))]
    }


before = protected()
insert_route = extract(r'(INSERT INTO DIGX_EP_EVT_REC_B\s+\(.*?\)\s+VALUES\s*\(.*?\));')
source_delete = extract(r'(DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID=p_template.*?);')
for run in range(2):
    execute(cleanup)
    for route in routes:
        chosen = list(execute(templates,p_type=route['subscriber_type'],p_value=route['subscriber_value'],p_dest=route['dest']))
        for r in chosen:
            for kind in ('PRIMARY','ALTERNATE'):
                tid = r['COD_MSG_TMPL_ID'] if kind=='PRIMARY' else r['UNSECURE_MSG_TMPL_ID']
                if not tid or (kind=='ALTERNATE' and tid==r['COD_MSG_TMPL_ID']):
                    continue
                for attrrow in db.execute('SELECT COD_ATTR_ID FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID=? AND DETERMINANT_VALUE=?', (tid,'OBDX_BU')).fetchall():
                    attr = attrrow[0]
                    for operation in ('submit','edit'):
                        act = CONSTANTS['c_base']+operation
                        mid = act+'.1216.bco.'+(route['template_prefix'] or '')+r['TXT_DEST_TYP']+'.'+r['LOCALE']+'.'+kind+'.'+attr
                        db.execute('INSERT OR REPLACE INTO DIGX_MD_SERVICE_ATTR VALUES (?,?,?,?,?)', (mid,act,attr,'DTO','A'))
                        execute(source_delete.replace('m.COD_ATTR_ID',':attr').replace('v_activity',':activity'),p_template=tid,attr=attr,activity=act)
                        db.execute('INSERT INTO DIGX_EP_MSG_SRC_B VALUES (?,?,?,?,?)',(tid,attr,act,'OBDX_BU',mid))
            for operation in ('submit','edit'):
                statement = re.sub(r'\br\.(\w+)',r':r_\1',insert_route).replace('a.operation',':operation')
                execute(statement,**bindings(route),**{'r_'+k:r[k] for k in r.keys()},operation=operation)
    assert protected() == before, 'BCO/shared/legacy rows changed'
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID IN (?,?) AND COD_ACTION_ID='A'", (CONSTANTS['c_event'],CONSTANTS['c_company_event'])).fetchone()[0] == 18
    counts = [db.execute('SELECT COUNT(*) FROM '+t).fetchone()[0] for t in ('DIGX_EP_EVT_REC_B','DIGX_EP_MSG_SRC_B','DIGX_MD_SERVICE_ATTR')]
    if run == 0:
        first_counts = counts
    else:
        assert counts == first_counts, 'rerun added rows'

verify = (ROOT/'consulting/db/branch_change_history/20260917_BCOH2H-1216/1216_Verify_BCO_Template_Reuse.sql').read_text()
queries = [q.strip().replace('NVL(', 'COALESCE(') for q in re.sub(r'--[^\n]*','',verify).split(';') if q.strip()]
assert len(queries) == 5
assert db.execute(queries[0]).fetchall(), 'inventory includes actual bindings/raw content'
assert not db.execute(queries[1]).fetchall(), 'HTH routes should match correct BCO role/language/channel'
assert not db.execute(queries[2]).fetchall(), 'shared-template HTH fields must match BCO sources'
for query in queries[3:]:
    assert db.execute(query).fetchall()
db.execute('SAVEPOINT missing_mapping')
db.execute('DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID=? AND COD_ACT_ID=?',
           ('BCO-alternate',CONSTANTS['c_base']+'submit'))
assert db.execute(queries[2]).fetchall(), 'verification catches missing alternate placeholder sources'
db.execute('ROLLBACK TO missing_mapping')
db.execute('RELEASE missing_mapping')
print('PASS: direct BCO IDs and alternate IDs; HTH placeholder sources; 18 routes; no duplicate rows on rerun; all BCO/shared/old-template rows preserved')
print('PASS: delivered verification SQL executes against fixtures and detects missing alternate mappings')
print('NOTE: Oracle PL/SQL execution and real template rendering/delivery still require UAT.')
