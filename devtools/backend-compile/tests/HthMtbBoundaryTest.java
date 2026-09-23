package com.ofss.digx.cz.bea.app.sms.service.user;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.cz.bea.common.audit.HthOnboardingAudit;
import com.ofss.digx.cz.bea.app.hosttohost.mtb.HthMtbScope;

public class HthMtbBoundaryTest {
 public static void main(String[] args) {
  AdapterFactoryConfigurator.lookups=0;
  try(HthUserMtbScope scope=new HthUserMtbScope(null,"UserExtensionData.update","UPDATE")) {
   scope.channel("BCO","BCO").result("SUCCESS");
  }
  check(AdapterFactoryConfigurator.lookups==0,"BCO must not resolve HTH Adapter");
  try(HthUserMtbScope scope=new HthUserMtbScope(null,"UserExtensionData.update","UPDATE")) {
   scope.channel("HTH",null).put("targetUserId","USER@PARTY").result("SUCCESS");
  }
  check(AdapterFactoryConfigurator.lookups==1,"stored HTH channel must work without request channel or audit scope");
  check("USER@PARTY".equals(AdapterFactoryConfigurator.last.getValues().get("targetUserId")),"snapshot target");
  int reads=Integer.parseInt(System.getProperty("hth849.configReads","0"));
  try(HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(null,"HostToHostApiPassword.setup","SETUP")) {
   audit.result("SUCCESS");
  }
  check(reads==Integer.parseInt(System.getProperty("hth849.configReads","0")),"audit close must not capture MTB");
  try(HthMtbScope scope=new HthMtbScope(null,"HostToHostApiPassword.setup","SETUP")) {scope.result("SUCCESS");}
  check(reads+1==Integer.parseInt(System.getProperty("hth849.configReads","0")),"host scope independent of audit");
  AdapterFactoryConfigurator.fail=true;
  try(HthUserMtbScope scope=new HthUserMtbScope(null,"UserExtensionData.create","CREATE")) {scope.channel(null,"HTH").result("SUCCESS");}
  AdapterFactoryConfigurator.fail=false;
  com.ofss.fc.service.response.TransactionStatus status=new com.ofss.fc.service.response.TransactionStatus();
  status.setErrorCode("DIGX_APPROVAL_REQUIRED");
  try(HthUserMtbScope scope=new HthUserMtbScope(null,"UserExtensionData.create","CREATE")) {
   scope.channel(null,"HTH").result("SUCCESS").response(status);
  }
  check("PENDING_APPROVAL".equals(AdapterFactoryConfigurator.last.getValues().get("businessOutcome")),"pending must not become success");
  System.out.println("PASS: module Adapter boundary, BCO bypass, stored-channel gate, audit independence and Adapter failure isolation");
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
