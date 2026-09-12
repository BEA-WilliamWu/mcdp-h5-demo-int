"""Checks the adapter's expiry predicate using SQLite; does not replace Oracle integration tests."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[3]
source = (root / 'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/domain/hosttohost/entity/repository/adapter/LocalHthApiPasswordCodeRepositoryAdapter.java').read_text()
method = source.split('public List findLatestExpiredCipher(', 1)[1].split('query.setParameter', 1)[0]
sql = ''.join(re.findall(r'"([^"\n]*)"', method)).replace('SYSTIMESTAMP', 'CURRENT_TIMESTAMP')
db = sqlite3.connect(':memory:')
db.execute("ATTACH DATABASE ':memory:' AS HTH_BEA")
db.execute('CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID, PARTY_ID, USER_NAME, PURPOSE, OBJECT_STATUS, STATUS, EXPIRY_TIME, ATTEMPT_COUNT, MAX_ATTEMPTS, CREATION_DATE, CODE_CIPHER)')
def row(status='ACTIVE', expiry='2000-01-01', attempts=0, user='USER', party='PARTY', purpose='SETUP', obj='A', created='2026-01-01'):
    db.execute('INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE VALUES (?,?,?,?,?,?,?,?,?,?,?)', ('id',party,user,purpose,obj,status,expiry,attempts,5,created,'encrypted-fixture'))
def expired(purpose='SETUP'):
    result = db.execute(sql, ('PARTY','USER','USER@PARTY',purpose)).fetchone()
    return bool(result and result[2] == 1)
assert not expired()
for status, time, attempts, expected in [('ACTIVE','2000-01-01',0,True),('EXPIRED','2000-01-01',0,True),('ACTIVE','2999-01-01',0,False),('USED','2000-01-01',0,False),('INVALID','2000-01-01',5,False),('ACTIVE','2000-01-01',5,False),('PENDING','2000-01-01',0,False),('IN_PROGRESS','2000-01-01',0,False)]:
    db.execute('DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE')
    row(status,time,attempts)
    assert expired() == expected, status
for kwargs in [dict(party='OTHER'),dict(user='OTHER'),dict(purpose='RESET'),dict(obj='I')]:
    db.execute('DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE');row(**kwargs);assert not expired()
db.execute('DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE');row(user='USER@PARTY',purpose='RESET');assert expired('RESET')
db.execute('DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE');row();row(status='USED',created='2026-02-01');assert not expired()
print('PASS: expiry, lifecycle status, attempts, owner/purpose isolation and latest-record precedence')
