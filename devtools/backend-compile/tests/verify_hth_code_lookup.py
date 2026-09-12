"""Exercise the production latest-Code query across historical username formats.

SQLite supplies synthetic rows; Oracle ORM entity materialization is covered by deployment.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[3]
source = (ROOT / "consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/domain/hosttohost/entity/repository/adapter/LocalHthApiPasswordCodeRepositoryAdapter.java").read_text()
method = source.split("public HthApiPasswordCode findLatestByOwner(", 1)[1].split("  private static", 1)[0]
query = method.split("session.createSQLQuery(", 1)[1].split("(String) null", 1)[0]
sql = "".join(re.findall(r'"([^"\n]*)"', query))
db = sqlite3.connect(":memory:")
db.execute("ATTACH DATABASE ':memory:' AS HTH_BEA")
db.execute("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID, PARTY_ID, USER_NAME, OBJECT_STATUS, CREATION_DATE)")

def seed(id, user, date, party="PARTY", status="A"):
    db.execute("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE VALUES (?,?,?,?,?)", (id, party, user, status, date))

def latest():
    row = db.execute(sql, ("PARTY", "USER", "USER@PARTY", "A")).fetchone()
    return row[0] if row else None

assert latest() is None
seed("old-short", "USER", "2026-01-01")
seed("new-full", "USER@PARTY", "2026-02-01")
assert latest() == "new-full", "An older short-name Code must not hide the current full-name Code"
seed("newest-short", "USER", "2026-03-01")
assert latest() == "newest-short", "Both owner formats participate in the same ordering"
seed("foreign-party", "USER", "2027-01-01", party="OTHER")
seed("foreign-user", "OTHER", "2027-01-01")
seed("inactive", "USER", "2027-01-01", status="I")
assert latest() == "newest-short", "Newer unrelated or inactive rows must not be revealed"
db.close()
print("PASS: latest Code across short/full usernames, both ordering directions, party/user/object-status isolation")
