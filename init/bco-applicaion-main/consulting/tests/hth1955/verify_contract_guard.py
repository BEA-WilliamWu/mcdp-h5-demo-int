"""Compile the production change() body and actual DSP adapter; fixture only OBDX boundaries.
Proves unsupported setup/reset cannot reserve a Code, issue a password write or report replay success.
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
class Base {void checkAccessPolicy(Object... args){} void checkResponsePolicy(Object... args){} }
public class ContractGuardTest extends Base {
 static final String SETUP="SETUP",RESET="RESET";
 int lookups,reserves,completes,releases,successes,rejections,notifications;
 String phase;
 static class SessionContext {String getUserId(){return "USER@PARTY";}}
 static class HostToHostApiPasswordRequestDTO {
  String encrypted="synthetic-code-envelope",password="synthetic-dsp-cipher";
  String getRequestId(){return "request";}String getDspEncryptedPassword(){return password;}
  void setEncryptedCredentials(String value){encrypted=value;}void setDspEncryptedPassword(String value){password=value;}
 }
 static class Status {void setReferenceNumber(String value){}void setExternalReferenceNumber(String value){} }
 static class HostToHostApiPasswordResponseDTO {
  Status getStatus(){return new Status();}void setSetupState(String value){}void setResetAllowed(boolean value){}
 }
 static class HthOnboardingAudit {static class Entry {Entry put(String key,Object value){return this;} }}
 static class Identity {String userId="USER",partyId="PARTY",uamClientId="client",profileUserId="USER@PARTY";}
 static class Interaction {static int begins,closes;static void begin(SessionContext c){begins++;}static void close(){closes++;}}
 static class ThreadAttribute {static Object get(String key){return null;}}
 static class HthApiPasswordTransport {static String THREAD_ATTRIBUTE="fixture";}
 static class KeyMaterial {String getRandomNumber(){return "314159";}}
 boolean isFeatureEnabled(){return true;}void validateRequest(HostToHostApiPasswordRequestDTO r){}
 Identity identity(SessionContext c,boolean required){return new Identity();}
 HostToHostApiPasswordResponseDTO response(){return new HostToHostApiPasswordResponseDTO();}
 IHthApiCredentialAdapter dspAdapter(){return new HthDspApiCredentialAdapter((DspApiPasswordClient)null);}
 String findCodeRequest(String id,Identity identity,String operation){lookups++;return "USED";}
 String credentialState(Identity i,boolean unknown){return "NOT_SETUP";}
 String decryptCode(HostToHostApiPasswordRequestDTO r,String user,String operation,Identity i){return "314159";}
 String validateAndReserveCode(String party,String user,String operation,String code,String request,String client){reserves++;return "CODE";}
 void fail(String user,String request,String code,boolean unknown) throws Exception{releases++;}
 void complete(String user,String request,String code) throws Exception{completes++;}
 void recordCRM(SessionContext c,String operation,boolean success,Identity i){if(success)successes++;else rejections++;}
 void notifySetupSuccess(SessionContext c,Identity i){notifications++;}void notifyResetSuccess(SessionContext c,Identity i){notifications++;}
 void logPhaseFailure(String stage,java.lang.Exception e){phase=stage;}
 BODY
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) throws java.lang.Exception {
  for(String operation:new String[]{SETUP,RESET}){
   ContractGuardTest service=new ContractGuardTest();HostToHostApiPasswordRequestDTO request=new HostToHostApiPasswordRequestDTO();
   try{service.change(new SessionContext(),request,operation,"service",new HthOnboardingAudit.Entry());throw new AssertionError("Unsupported flow returned success");}
   catch(Exception e){check("DIGX_CZ_HTH_API_PASSWORD_009".equals(e.getMessage()),"Wrong unavailable error");}
   check("DSP_CONTRACT".equals(service.phase),"Guard did not reject before Code handling");
   check(service.lookups==0 && service.reserves==0 && service.completes==0 && service.releases==0,"Code lifecycle touched");
   check(service.successes==0 && service.notifications==0 && service.rejections==1,"Wrong success/audit behaviour");
   check(request.encrypted==null && request.password==null,"Request secrets not cleared");
  }
  check(Interaction.begins==2 && Interaction.closes==2,"Interaction not closed");
  System.out.println("PASS: production setup/reset guard rejects before USED replay, Code lookup/reserve/consume and success notification; request buffers cleared");
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
  str(SOURCE/'DspApiPasswordClient.java'),str(SOURCE/'HthDspApiCredentialAdapter.java'),str(harness)],check=True)
 subprocess.run([str(JAVA/'java'),'-cp',directory+os.pathsep+CP,
  'com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.ContractGuardTest'],check=True)
