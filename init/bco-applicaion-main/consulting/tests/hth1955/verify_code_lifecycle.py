"""Run production Code methods/SQL with JDBC/H2 and real AES/RSA.
Only OBDX bootstrap and session boundary are fixtures. No Oracle/WebLogic or remote DSP is used.
Requires JAVA_HOME and H2_JAR. Source method bodies are compiled unchanged.
"""
from pathlib import Path
import os, re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[3]
JAVA=Path(os.environ['JAVA_HOME'])/'bin'
H2=Path(os.environ['H2_JAR'])
BASE=ROOT/'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea'
service=(BASE/'app/hosttohost/service/HostToHostApiPassword.java').read_text()
adapter=(BASE/'domain/hosttohost/entity/repository/adapter/LocalHthApiPasswordCodeRepositoryAdapter.java').read_text()
def method(text,name):
    m=re.search(r'  (?:private|public) [^\n]+ '+name+r'\(.*?\n  }',text,re.S)
    assert m,name
    return m[0]
methods='\n'.join(method(service,n) for n in ['string','validateAndReserveCode','complete','fail','findCodeRequest'])
queries='\n'.join(method(adapter,n) for n in ['findUsableCipher','findLatestExpiredCipher','recordFailedAttempt','reserve','consume','failReservation','findRequest'])
state_source=(BASE/'domain/hosttohost/entity/repository/adapter/HthApiPasswordStateRepository.java').read_text()
state_source=state_source[state_source.index('public final class'):].replace('public final class','final class',1)
sql=(ROOT/'consulting/db/release/HTH_FIRST_DEPLOY.sql').read_text()
schema=re.search(r'CREATE TABLE HTH_BEA.HTH_API_PASSWORD_CODE \(.*?\);',sql,re.S)[0]
schema=re.sub(r'VARCHAR2\((\d+) BYTE\)',r'VARCHAR(\1)',schema)
profile_schema=re.search(r'CREATE TABLE HTH_BEA.HTH_USER_PROFILE \(.*?\);',sql,re.S)[0]
profile_schema=re.sub(r'VARCHAR2\((\d+) BYTE\)',r'VARCHAR(\1)',profile_schema)
JAVA_SOURCE=r"""
import java.sql.*;import java.util.*;import java.util.logging.*;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordTransport;
class Exception extends java.lang.Exception { Exception(String code){super(code);} Exception(java.lang.Exception e){super(e);} }
class Query {
 final Connection connection;final String sql;final Map<Integer,Object> parameters=new TreeMap<>();int max=0;
 Query(Connection c,String s){connection=c;sql=s;}
 void setParameter(int i,Object v){parameters.put(i,v);}void setMaxResults(int n){max=n;}
 PreparedStatement prepare() throws java.lang.Exception {PreparedStatement p=connection.prepareStatement(sql);for(var e:parameters.entrySet())p.setObject(e.getKey(),e.getValue());if(max>0)p.setMaxRows(max);return p;}
 List list() throws Exception {try(PreparedStatement p=prepare();ResultSet r=p.executeQuery()){List<Object> values=new ArrayList<>();int size=r.getMetaData().getColumnCount();while(r.next()){if(size==1)values.add(r.getObject(1));else{Object[] row=new Object[size];for(int i=0;i<size;i++)row[i]=r.getObject(i+1);values.add(row);}}return values;}catch(java.lang.Exception e){throw new Exception(e);}}
 int executeUpdate() throws Exception {try(PreparedStatement p=prepare()){return p.executeUpdate();}catch(java.lang.Exception e){throw new Exception(e);}}
}
class Session {final Connection connection;Session(Connection c){connection=c;}Query createSQLQuery(String sql){return new Query(connection,sql);}}
class Repository { QUERIES }
STATE_SOURCE
public class CodeLifecycleTest {
 static final String SETUP="SETUP";
 final HthApiPasswordStateRepository passwordStateRepository=new HthApiPasswordStateRepository();
 static final String URL="jdbc:h2:mem:codes;MODE=Oracle;DB_CLOSE_DELAY=-1";
 static final Logger LOGGER=Logger.getLogger("CodeTest");final Repository codeRepository=new Repository();
 static class Identity {String partyId="PARTY",userId="USER",uamClientId="client",profileUserId="USER@PARTY";}
 static class SessionLease {Session session;boolean closed;SessionLease() throws Exception {try{Connection c=DriverManager.getConnection(URL);c.setAutoCommit(false);session=new Session(c);}catch(java.lang.Exception e){throw new Exception(e);}}
 void close(boolean commit) throws Exception {if(closed)return;try{if(commit)session.connection.commit();else session.connection.rollback();session.connection.close();closed=true;}catch(java.lang.Exception e){throw new Exception(e);}}}
 SessionLease openIndependent() throws Exception{return new SessionLease();} SessionLease open(boolean write) throws Exception{return new SessionLease();}
 METHODS
 static Connection db;static CodeLifecycleTest service=new CodeLifecycleTest();
 interface Action{void run() throws java.lang.Exception;}
 static void fails(String code,Action a) throws java.lang.Exception {try{a.run();throw new AssertionError("Expected "+code);}catch(Exception e){if(!code.equals(e.getMessage()))throw e;}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static String scalar(String expression) throws java.lang.Exception {try(ResultSet r=db.createStatement().executeQuery(expression)){r.next();return r.getString(1);}}
 static void seed(String purpose,boolean expired) throws java.lang.Exception {
 db.createStatement().execute("DELETE FROM HTH_BEA.HTH_API_PASSWORD_CODE");
 db.createStatement().execute("DELETE FROM HTH_BEA.HTH_USER_PROFILE");
 db.createStatement().execute("INSERT INTO HTH_BEA.HTH_USER_PROFILE (PARTY_ID,CLOSE_ID,API_PASSWORD_STATE,API_PASSWORD_CLIENT_ID) VALUES ('PARTY','USER@PARTY','"+("SETUP".equals(purpose)?"NOT_SETUP":"ACTIVE")+"','client')");
 try(PreparedStatement p=db.prepareStatement("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CODE (ID,PARTY_ID,USER_NAME,CODE_CIPHER,STATUS,EXPIRY_TIME,CREATED_BY,LAST_UPDATED_BY,PURPOSE) VALUES ('CODE','PARTY','USER@PARTY',?,'ACTIVE',?,'AP','AP',?)")){
 p.setString(1,HthApiPasswordCrypto.encrypt("314159",HthApiPasswordCrypto.codeCipherKey()));p.setTimestamp(2,new Timestamp(System.currentTimeMillis()+(expired?-60000:60000)));p.setString(3,purpose);p.executeUpdate();}}
 static String reserve(String purpose,String code,String request) throws Exception{return service.validateAndReserveCode("PARTY","USER",purpose,code,request,"client");}
 static String status() throws java.lang.Exception{return scalar("SELECT STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE");}
 public static void main(String[] args) throws java.lang.Exception {
 db=DriverManager.getConnection(URL);db.createStatement().execute("CREATE SCHEMA HTH_BEA");db.createStatement().execute(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])));
 db.createStatement().execute(java.nio.file.Files.readString(java.nio.file.Path.of(args[1])));
 for(String purpose:new String[]{"SETUP","RESET"}){
 seed(purpose,false);String request=UUID.randomUUID().toString();
 fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve(purpose,"000000",request));
 check("1".equals(scalar("SELECT ATTEMPT_COUNT FROM HTH_BEA.HTH_API_PASSWORD_CODE")),"Failed attempt not committed");
 reserve(purpose,"314159",request);check("IN_PROGRESS".equals(status()),"Not reserved");
 service.complete(new Identity(),request,"CODE");check("USED".equals(status()),"Not consumed after success");
 check("ACTIVE".equals(scalar("SELECT API_PASSWORD_STATE FROM HTH_BEA.HTH_USER_PROFILE")),"State not updated with consume");
 check("USED".equals(service.findCodeRequest(request,new Identity(),purpose)),"Replay result missing");
 fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve(purpose,"314159",UUID.randomUUID().toString()));
 seed(purpose,false);reserve(purpose,"314159",request);service.fail(new Identity(),purpose,request,"CODE",false);check("ACTIVE".equals(status()),"Definite DSP rejection consumed Code");
 reserve(purpose,"314159",request);service.fail(new Identity(),purpose,request,"CODE",true);check("UNKNOWN".equals(status()),"Uncertain DSP write released Code");
 check("UNKNOWN".equals(scalar("SELECT API_PASSWORD_STATE FROM HTH_BEA.HTH_USER_PROFILE")),"Uncertain state incorrectly active");
 fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve(purpose,"314159",request));
 seed(purpose,true);fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve(purpose,"000000",request));fails("DIGX_CZ_HTH_API_PASSWORD_003",()->reserve(purpose,"314159",request));
 seed(purpose,false);for(int i=0;i<5;i++)fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve(purpose,"000000",request));check("INVALID".equals(status()),"No lockout");
 }
 seed("RESET",false);fails("DIGX_CZ_HTH_API_PASSWORD_002",()->reserve("SETUP","314159",UUID.randomUUID().toString()));
 // Two simultaneous submits must not both reserve the same user's password operation.
 seed("SETUP",false);
 var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
 var start=new java.util.concurrent.CountDownLatch(1);
 java.util.concurrent.Callable<Boolean> submit=()->{start.await();try{reserve("SETUP","314159",UUID.randomUUID().toString());return true;}catch(Exception rejected){return false;}};
 var first=pool.submit(submit);var second=pool.submit(submit);start.countDown();
 try{check((first.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(second.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)==1,"Concurrent submits both reserved");}
 finally{pool.shutdownNow();}
 // Failed local consume rolls back the state update in the same transaction.
 seed("SETUP",false);String req=UUID.randomUUID().toString();reserve("SETUP","314159",req);
 db.createStatement().execute("UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS='INVALID'");
 fails("DIGX_CZ_HTH_API_PASSWORD_007",()->service.complete(new Identity(),req,"CODE"));
 check("IN_PROGRESS".equals(scalar("SELECT API_PASSWORD_STATE FROM HTH_BEA.HTH_USER_PROFILE")),"State committed despite failed consume");
 seed("RESET",false);Session inspect=new Session(db);
 check("UNKNOWN".equals(service.passwordStateRepository.read(inspect,"PARTY","USER","different-client")),"Wrong client binding accepted");
 check("NOT_SETUP".equals(service.passwordStateRepository.readForCodeGeneration(inspect,"PARTY","NEWUSER","client")),"New-user pending Code unavailable");
 db.createStatement().execute("UPDATE HTH_BEA.HTH_USER_PROFILE SET API_PASSWORD_STATE='UNKNOWN'");
 check("UNKNOWN".equals(service.passwordStateRepository.readForCodeGeneration(inspect,"PARTY","USER","client")),"Legacy user guessed as unset");
 // Actual session RSA implementation: matching key works; cross-session and expired keys fail.
 var key=HthApiPasswordTransport.create("USER",System.currentTimeMillis());
 var pub=java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(new java.math.BigInteger(key.getModulus(),16),new java.math.BigInteger(key.getPublicExponent(),16)));
 var cipher=javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,pub);
 String ciphertext=Base64.getEncoder().encodeToString(cipher.doFinal("[test-code-envelope]".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 check("[test-code-envelope]".equals(HthApiPasswordTransport.decrypt(key,"USER",key.getKeyId(),ciphertext,System.currentTimeMillis())),"Session decrypt mismatch");
 try{HthApiPasswordTransport.decrypt(key,"OTHER",key.getKeyId(),ciphertext,System.currentTimeMillis());throw new AssertionError("Wrong owner accepted");}catch(java.security.GeneralSecurityException expected){}
 try{HthApiPasswordTransport.decrypt(key,"USER",key.getKeyId(),ciphertext,System.currentTimeMillis()+301000);throw new AssertionError("Expired key accepted");}catch(java.security.GeneralSecurityException expected){}
 System.out.println("PASS: real Code AES/RSA, production SQL and transaction bodies: SETUP/RESET reserve/consume/replay, release/unknown, mismatch-before-expiry, lockout, purpose and session isolation");db.close();
 }
}
""".replace('STATE_SOURCE',state_source).replace('QUERIES',queries).replace('METHODS',methods)
with tempfile.TemporaryDirectory(prefix='hth1955-code-') as directory:
 work=Path(directory);(work/'schema.sql').write_text(schema);(work/'profile.sql').write_text(profile_schema);(work/'CodeLifecycleTest.java').write_text(JAVA_SOURCE)
 (work/'ConfigurationFactory.java').write_text("""package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
 public java.util.prefs.Preferences getConfigurations(String category){return new java.util.prefs.AbstractPreferences(null,"") {
 protected String getSpi(String k){return "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";}
 protected void putSpi(String k,String v){} protected void removeSpi(String k){} protected void removeNodeSpi(){}
 protected String[] keysSpi(){return new String[0];}protected String[] childrenNamesSpi(){return new String[0];}
 protected java.util.prefs.AbstractPreferences childSpi(String n){throw new UnsupportedOperationException();}
 protected void syncSpi(){} protected void flushSpi(){}
 };}
}""")
 subprocess.run([str(JAVA/'javac'),'-proc:none','-cp',str(H2),'-d',directory,str(work/'CodeLifecycleTest.java'),str(work/'ConfigurationFactory.java'),str(BASE/'app/hosttohost/util/HthApiPasswordCrypto.java'),str(BASE/'app/hosttohost/util/HthApiPasswordTransport.java')],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',directory+os.pathsep+str(H2),'CodeLifecycleTest',str(work/'schema.sql'),str(work/'profile.sql')],check=True)
