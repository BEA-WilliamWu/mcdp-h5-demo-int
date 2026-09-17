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
 FLG_CONDITIONAL TEXT);
CREATE TABLE DIGX_EP_MSG_TMPL_B (
 COD_TMPL_ID TEXT, DETERMINANT_VALUE TEXT, OBJECT_STATUS TEXT, DESTINATION_TYPE TEXT);
''')
for locale in ('en', 'zh-Hant', 'zh-Hans-CN'):
    for subscriber, value in (('CORPORATE', 'USER'), ('PARTY', 'CUSTOMER')):
        for dest in ('EMAIL', 'SMS'):
            template = f'USER_ACCOUNT_ACCESS_UPDATE_{subscriber}_{value}_{dest}_{locale}'
            row = (CONSTANTS['c_source'], CONSTANTS['c_bco_event'], 'A',
                   subscriber, value, dest, locale, template, 'N')
            db.execute('INSERT INTO DIGX_EP_EVT_REC_B VALUES (?,?,?,?,?,?,?,?,?)', row)
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
        assert row['n'] == 1 and row['LOCALE'], 'ambiguous within recipient group'


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
         "SUBSCRIBER_TYPE, SUBSCRIBER_VALUE, TXT_DEST_TYP, LOCALE, 'CONFLICT', 'N' "
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
        db.execute('INSERT INTO DIGX_EP_EVT_REC_B (COD_ACT_ID,COD_EVENT_ID) VALUES (?,?)',
                   (CONSTANTS['c_base'] + operation, event))
cleanup = extract(r'(DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID IN .*?);')
for _ in range(2):
    execute(cleanup)
    remaining = [tuple(r) for r in db.execute('SELECT * FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID != ?',
                                            ('OTHER_HTH_EVENT',))]
    assert remaining == original
    assert db.execute("SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID='OTHER_HTH_EVENT'").fetchone()[0] == 1
print('PASS: repeated cleanup targets both 1216 events only; BCO rows preserved')
print('NOTE: Oracle PL/SQL execution and real template rendering/delivery still require UAT.')
