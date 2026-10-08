"""Exercise production latest and ordinary-display Code queries across lifecycle states.

SQLite supplies synthetic rows; Oracle ORM entity materialization is covered by deployment.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[3]
source = (ROOT / "consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/domain/hosttohost/entity/repository/adapter/LocalHthApiPasswordCodeRepositoryAdapter.java").read_text()
def query_for(name):
    method = source.split("public HthApiPasswordCode " + name + "(", 1)[1]
    query = method.split("session.createSQLQuery(", 1)[1].split("(String) null", 1)[0]
    return "".join(re.findall(r'"([^"\n]*)"', query))
sql = query_for("findLatestByOwner")
current_sql = query_for("findCurrentByOwner")
db = sqlite3.connect(":memory:")
db.execute("ATTACH DATABASE ':memory:' AS HTH_BEA")
db.execute("CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE (ID, PARTY_ID, USER_NAME, OBJECT_STATUS, CREATION_DATE, STATUS)")

def seed(id, user, date, party="PARTY", status="A", code_status="ACTIVE"):
    db.execute("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE VALUES (?,?,?,?,?,?)", (id, party, user, status, date, code_status))

def latest(query=sql):
    row = db.execute(query, ("PARTY", "USER", "USER@PARTY", "A")).fetchone()
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
assert latest(current_sql) == "newest-short"
seed("pending", "USER@PARTY", "2028-01-01", code_status="PENDING")
assert latest() == "pending", "Audit lookup still detects regeneration drafts"
assert latest(current_sql) == "newest-short", "A newer PENDING draft must not hide the old approved Code"
db.execute("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS='INVALID', OBJECT_STATUS='I' WHERE ID='pending'")
assert latest(current_sql) == "newest-short", "Rejected draft must not replace the ordinary page Code"
db.execute("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS='USED' WHERE ID='newest-short'")
assert latest(current_sql) == "newest-short", "The latest consumed Code remains visible as USED, never an earlier ACTIVE one"
db.execute("DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE")
seed("first-draft", "USER", "2028-01-01", code_status="PENDING")
assert latest(current_sql) is None, "No previously approved Code means no ordinary page Code"
db.close()
print("PASS: Code display preserves the approved/history row while pending and after rejection; username aliases, owner isolation, USED history and first draft")
