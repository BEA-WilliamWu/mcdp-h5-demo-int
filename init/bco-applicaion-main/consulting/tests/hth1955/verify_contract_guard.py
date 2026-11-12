"""Compile the production change() body and actual DSP adapter; fixture only OBDX boundaries.
Exercises setup/reset success and definite/uncertain failures using the unchanged production change() body.
DSP HTTP and Code/State SQL have separate integration tests; those boundaries are fixtures here.
"""
from pathlib import Path
import os, re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[3]
JAVA=Path(os.environ['JAVA_HOME'])/'bin'
LIB=ROOT/'consulting/middleware/lib'
preferred=[LIB/'OBDX_FW_LIB/jakarta.ws.rs-api-2.1.6.jar'] + [LIB/'thirdparty'/('jackson-'+name+'-2.19.4.jar') for name in ('core','annotations','databind')]
CP=os.pathsep.join(str(p) for p in preferred+sorted(set(LIB.rglob('*.jar'))-set(preferred)))
SOURCE=ROOT/'consulting/middleware/projects/ext-xface/com.ofss.digx.cz.bea.extxface.app.impl/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/impl'
COMMON=ROOT/'consulting/middleware/projects/common/com.ofss.digx.cz.bea.extxface/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter'
service=(ROOT/'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java').read_text()
body=re.search(r'  private HostToHostApiPasswordResponseDTO change\(.*?\n  }',service,re.S)[0]
HARNESS=r'''
package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
class Base {void checkAccessPolicy(Object... args){} void checkResponsePolicy(Object... args){} }
public class ContractGuardTest extends Base {
 static final String SETUP="SETUP",RESET="RESET";
 int reserves,completes,releases,successes,rejections,notifications,writes;
 boolean replay,badCode,commitFailure,writeFailure,uncertain;
 String phase,state,codeState="ACTIVE",initialState;
 static class SessionContext {String getUserId(){return "USER@PARTY";}}
 static class HostToHostApiPasswordRequestDTO {
  String encrypted="synthetic-code-envelope",password="synthetic-dsp-cipher";
  String getRequestId(){return "request";}String getDspEncryptedPassword(){return password;}
  void setEncryptedCredentials(String value){encrypted=value;}void setDspEncryptedPassword(String value){password=value;}
 }
 static class Status {void setReferenceNumber(String value){}void setExternalReferenceNumber(String value){} }
 static class HostToHostApiPasswordResponseDTO {
  String state;Status getStatus(){return new Status();}void setSetupState(String value){state=value;}void setResetAllowed(boolean value){}
 }
 static class HthOnboardingAudit {static class Entry {Entry put(String key,Object value){return this;} }}
 static class Identity {String userId="USER",partyId="PARTY",uamClientId="client",profileUserId="USER@PARTY";}
 static class Interaction {static int begins,closes;static void begin(SessionContext c){begins++;}static void close(){closes++;}}
 static class ThreadAttribute {static Object get(String key){return new KeyMaterial();}}
 static class HthApiPasswordTransport {static String THREAD_ATTRIBUTE="fixture";}
 static class KeyMaterial {String getRandomNumber(){return "314159";}}
 boolean isFeatureEnabled(){return true;}void validateRequest(HostToHostApiPasswordRequestDTO r){}
 Identity identity(SessionContext c,boolean required){return new Identity();}
 HostToHostApiPasswordResponseDTO response(){return new HostToHostApiPasswordResponseDTO();}
 IHthApiCredentialAdapter dspAdapter(){return new IHthApiCredentialAdapter(){
  public String getStatus(String client,String user){throw new AssertionError("DSP has no status API");}
  public void requireWriteSupported(String operation){}
  public String publicKey(String h){return "fixture";}
  public void modify(String client,String close,String req,String operation,String hash,String nonce) throws Exception {
   check("IN_PROGRESS".equals(codeState),"DSP called before Code reservation");writes++;
   if(writeFailure)throw new HthApiCredentialWriteException(uncertain);
  }
 };}
 String findCodeRequest(String id,Identity identity,String operation){return replay?"USED":null;}
 String credentialState(Identity i,boolean unknown){return state;}
 String decryptCode(HostToHostApiPasswordRequestDTO r,String user,String operation,Identity i){return "314159";}
 String validateAndReserveCode(String party,String user,String operation,String code,String request,String client) throws Exception {
  if(badCode)throw new Exception("DIGX_CZ_HTH_API_PASSWORD_002");reserves++;codeState="IN_PROGRESS";state="IN_PROGRESS";return "CODE";
 }
 void fail(Identity i,String operation,String request,String code,boolean unknown) throws Exception {
  releases++;codeState=unknown?"UNKNOWN":"ACTIVE";state=unknown?"UNKNOWN":initialState;
 }
 void complete(Identity i,String request,String code) throws Exception {
  if(commitFailure)throw new Exception("commit failure");completes++;codeState="USED";state="ACTIVE";
 }
 void recordCRM(SessionContext c,String operation,boolean success,Identity i){if(success)successes++;else rejections++;}
 void notifySetupSuccess(SessionContext c,Identity i){notifications++;}void notifyResetSuccess(SessionContext c,Identity i){notifications++;}
 void logPhaseFailure(String stage,java.lang.Exception e){phase=stage;}
 BODY
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static ContractGuardTest create(String operation){ContractGuardTest s=new ContractGuardTest();s.initialState=SETUP.equals(operation)?"NOT_SETUP":"ACTIVE";s.state=s.initialState;return s;}
 void invoke(String operation,String expectedError) throws java.lang.Exception {
  HostToHostApiPasswordRequestDTO r=new HostToHostApiPasswordRequestDTO();
  try{HostToHostApiPasswordResponseDTO result=change(new SessionContext(),r,operation,"service",new HthOnboardingAudit.Entry());
   check(expectedError==null,"Expected failure "+expectedError);check("ACTIVE".equals(result.state),"Wrong success state");
  }catch(Exception e){check(expectedError!=null && expectedError.equals(e.getMessage()),"Unexpected result: "+e.getMessage());}
  check(r.encrypted==null && r.password==null,"Request buffers not cleared");
 }
 public static void main(String[] args) throws java.lang.Exception {
  for(String op:new String[]{SETUP,RESET}){
   ContractGuardTest s=create(op);s.invoke(op,null);check(s.writes==1&&s.completes==1&&s.notifications==1&&s.successes==1,"Success incomplete");
   s=create(op);s.badCode=true;s.invoke(op,"DIGX_CZ_HTH_API_PASSWORD_002");check(s.writes==0&&s.reserves==0&&s.notifications==0,"Wrong Code reached DSP");
   for(boolean unknown:new boolean[]{false,true}){s=create(op);s.writeFailure=true;s.uncertain=unknown;s.invoke(op,"DIGX_CZ_HTH_API_PASSWORD_009");
    check(s.completes==0&&s.notifications==0&&s.releases==1,"DSP failure reported success");check((unknown?"UNKNOWN":s.initialState).equals(s.state),"Wrong failure state");}
   s=create(op);s.commitFailure=true;s.invoke(op,"DIGX_CZ_HTH_API_PASSWORD_009");check("UNKNOWN".equals(s.state)&&s.notifications==0,"Lost local commit allowed retry");
   s=create(op);s.replay=true;s.state="ACTIVE";s.invoke(op,null);check(s.writes==0&&s.reserves==0&&s.notifications==0,"Replay wrote or notified again");
   s=create(op);s.replay=true;s.state="UNKNOWN";s.invoke(op,"DIGX_CZ_HTH_API_PASSWORD_007");check(s.writes==0,"Stale replay overwrote ambiguous state");
   s=create(op);s.state="UNKNOWN";s.invoke(op,SETUP.equals(op)?"DIGX_CZ_HTH_API_PASSWORD_005":"DIGX_CZ_HTH_API_PASSWORD_006");check(s.writes==0,"Unknown state written");
  }
  check(Interaction.begins==Interaction.closes,"Interaction leaked");
  System.out.println("PASS: production setup/reset flow: success, invalid Code, known rejection, timeout, local completion failure, replay, unknown state, notifications and buffer clearing");
 }
}
'''.replace('BODY',body)
with tempfile.TemporaryDirectory(prefix='hth1955-guard-') as directory:
 tmp=Path(directory)
 exception=tmp/'Exception.java'
 exception.write_text('package com.ofss.digx.infra.exceptions; public class Exception extends java.lang.Exception { public Exception(String code) { super(code); } }')
 harness=tmp/'ContractGuardTest.java';harness.write_text(HARNESS)
 subprocess.run([str(JAVA/'javac'),'--release','8','-proc:none','-encoding','UTF-8','-cp',CP,'-d',directory,
  str(exception),str(COMMON/'IHthApiCredentialAdapter.java'),str(COMMON/'HthApiCredentialWriteException.java'),
  *[str(path) for path in (SOURCE.parents[2]/'microservice/api/dsp').rglob('*.java')],str(SOURCE/'HthDspApiCredentialAdapter.java'),str(harness)],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',directory+os.pathsep+CP,
  'com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.ContractGuardTest'],check=True)
