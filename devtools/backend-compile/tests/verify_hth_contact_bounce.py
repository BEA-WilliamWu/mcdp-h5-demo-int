"""Execute the production 851 office-failure batch method against H2.
Only the existing BCO webmail writer is replaced by a transactional insert/failure fixture.
"""
from pathlib import Path
import os, re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[3]
H2=os.environ.get('HTH_TEST_H2_JAR','/tmp/hth-h2-1.4.200.jar')
JDK=Path(os.environ['JAVA_HOME'])/'bin'
def method(text,signature):
    start=text.index(signature); end=text.index('{',start)+1; depth=1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}'); end+=1
    return text[start:end]
methods=[]
for variant in ('','UAT','PRD'):
    text=(ROOT/'consulting/middleware/batchJobs'/variant/'inboundbatchprocessor/ValidateAndSendBounceNotify.java').read_text()
    methods.append(method(text,'void processHthContactOfficeFailures'))
    assert "M.REFNUMBER = 'H851' || H.ID" in text
    assert 'if (legacyAutoCommit) conn.setAutoCommit(true);' in text
assert len(set(methods))==1, 'all deployment variants use the same scoped handling'
source='''import java.sql.*;import java.util.*;
public class Bounce851Test {
 boolean fail=true; int logs;
 void writeLog(String f,String m,boolean append){logs++;}
 void sendSysAdminWebMail(String office,String party,Connection c,String user,String file,boolean legacy)throws SQLException{
  if(c.getAutoCommit()||legacy)throw new AssertionError("must share outbox followup transaction");
  try(PreparedStatement p=c.prepareStatement("INSERT INTO WM VALUES (?,?)")){p.setString(1,user);p.setString(2,office);p.executeUpdate();}
  if(fail)throw new SQLException("inject webmail failure");
 }
'''+methods[0]+'''
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static int count(Connection c,String sql)throws SQLException{try(ResultSet r=c.createStatement().executeQuery(sql)){r.next();return r.getInt(1);}}
 public static void main(String[] args)throws Exception{
  try(Connection c=DriverManager.getConnection("jdbc:h2:mem:bounce;MODE=Oracle")){
   Statement s=c.createStatement();
   s.execute("CREATE TABLE DIGX_CZ_HTH_CONTACT_OUTBOX(ID VARCHAR2(32),PARTY_ID VARCHAR2(50),ADDRESS VARCHAR2(320),STATE VARCHAR2(30),TARGET_UNIT VARCHAR2(50),UPDATED_AT TIMESTAMP)");
   s.execute("CREATE TABLE DIGX_UM_USER_PRINCIPAL(USERNAME VARCHAR2(50),PRINCIPAL VARCHAR2(50))");
   s.execute("CREATE TABLE DIGX_UM_USERPARTY_RELATION(USER_ID VARCHAR2(50),PARTY_ID VARCHAR2(50))");
   s.execute("CREATE TABLE DIGX_CZ_UM_EXTENSIONDATA(USER_ID VARCHAR2(50),BOUNCE_BACK_REMINDER VARCHAR2(10))");
   s.execute("CREATE TABLE WM(USER_ID VARCHAR2(50),ADDRESS VARCHAR2(320))");
   s.execute("INSERT INTO DIGX_CZ_HTH_CONTACT_OUTBOX VALUES ('a','P','original-office@example.test','OFFICE_FOLLOWUP','OBDX_BU',CURRENT_TIMESTAMP), ('other','P','other@example.test','OFFICE_FOLLOWUP','OTHER_BU',CURRENT_TIMESTAMP)");
   s.execute("INSERT INTO DIGX_UM_USER_PRINCIPAL VALUES ('SYS1','SYSADM'),('SYS2','SYSADM'),('VIEWER','VIEWER')");
   s.execute("INSERT INTO DIGX_UM_USERPARTY_RELATION VALUES ('SYS1','P'),('SYS2','P'),('VIEWER','P')");
   s.execute("INSERT INTO DIGX_CZ_UM_EXTENSIONDATA VALUES ('SYS1',NULL),('SYS2',NULL),('VIEWER',NULL)");
   Bounce851Test job=new Bounce851Test();job.processHthContactOfficeFailures(c,"test");
   check(job.logs==1 && count(c,"SELECT COUNT(*) FROM WM")==0);
   check(count(c,"SELECT COUNT(*) FROM DIGX_CZ_UM_EXTENSIONDATA WHERE BOUNCE_BACK_REMINDER='AP'")==0);
   check(count(c,"SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE STATE='OFFICE_NOTIFIED'")==0);
   job.fail=false;job.processHthContactOfficeFailures(c,"test");job.processHthContactOfficeFailures(c,"test");
   check(count(c,"SELECT COUNT(*) FROM WM WHERE ADDRESS='original-office@example.test'")==2);
   check(count(c,"SELECT COUNT(*) FROM DIGX_CZ_UM_EXTENSIONDATA WHERE BOUNCE_BACK_REMINDER='AP'")==2);
   check(count(c,"SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE STATE='OFFICE_NOTIFIED'")==1);
   check(c.getAutoCommit());
   s.execute("INSERT INTO DIGX_CZ_HTH_CONTACT_OUTBOX VALUES ('no-recipient','EMPTY','office@example.test','OFFICE_FOLLOWUP','OBDX_BU',CURRENT_TIMESTAMP)");
   job.processHthContactOfficeFailures(c,"test");
   check(count(c,"SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX WHERE ID='no-recipient' AND STATE='OFFICE_FOLLOWUP'")==1);

  }
  System.out.println("PASS: root/UAT/PRD office-failure adapter, original address, BCO followup recipients, atomic rollback, retry, once-only, unit isolation");
 }
}'''
with tempfile.TemporaryDirectory(prefix='hth851-bounce-') as tmp:
    p=Path(tmp)/'Bounce851Test.java';p.write_text(source)
    subprocess.run([str(JDK/'javac'),'--release','8','-cp',H2,'-d',tmp,str(p)],check=True)
    subprocess.run([str(JDK/'java'),'-cp',tmp+os.pathsep+H2,'Bounce851Test'],check=True)
