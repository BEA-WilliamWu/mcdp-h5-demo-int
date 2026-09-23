package com.ofss.digx.cz.bea.app.sms.service.user;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.cz.bea.common.hth.HthOnboardingAudit;
import com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMScope;

public class HthCRMBoundaryTest {
 public static void main(String[] args) {
  AdapterFactoryConfigurator.lookups=0;
  try(HthUserCRMScope scope=new HthUserCRMScope(null,"UserExtensionData.update","UPDATE")) {
   scope.channel("BCO","BCO").result("SUCCESS");
  }
  check(AdapterFactoryConfigurator.lookups==0,"BCO must not resolve HTH Adapter");
  try(HthUserCRMScope scope=new HthUserCRMScope(null,"UserExtensionData.update","UPDATE")) {
   scope.channel("HTH",null).put("targetUserId","USER@PARTY").result("SUCCESS");
  }
  check(AdapterFactoryConfigurator.lookups==1,"stored HTH channel must work without request channel or audit scope");
  check("USER@PARTY".equals(AdapterFactoryConfigurator.last.getValues().get("targetUserId")),"snapshot target");
  int reads=Integer.parseInt(System.getProperty("hth849.configReads","0"));
  try(HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(null,"HostToHostApiPassword.setup","SETUP")) {
   audit.result("SUCCESS");
  }
  check(reads==Integer.parseInt(System.getProperty("hth849.configReads","0")),"audit close must not capture MTB");
  try(HthCRMScope scope=new HthCRMScope(null,"HostToHostApiPassword.setup","SETUP")) {scope.result("SUCCESS");}
  check(reads+1==Integer.parseInt(System.getProperty("hth849.configReads","0")),"host scope independent of audit");
  AdapterFactoryConfigurator.fail=true;
  try(HthUserCRMScope scope=new HthUserCRMScope(null,"UserExtensionData.create","CREATE")) {scope.channel(null,"HTH").result("SUCCESS");}
  AdapterFactoryConfigurator.fail=false;
  AdapterFactoryConfigurator.missingImplementation=true;
  try(HthUserCRMScope scope=new HthUserCRMScope(null,"UserExtensionData.create","CREATE")) {scope.channel(null,"HTH").result("SUCCESS");}
  AdapterFactoryConfigurator.missingImplementation=false;
  check(com.ofss.digx.cz.bea.common.hth.HthChannelSupport.isHthChange("h2h",null),"H2H alias");
  check(!com.ofss.digx.cz.bea.common.hth.HthChannelSupport.isHthChange(null,"BCO"),"non HTH channel");
  com.ofss.fc.service.response.TransactionStatus status=new com.ofss.fc.service.response.TransactionStatus();
  status.setErrorCode("DIGX_APPROVAL_REQUIRED");
  try(HthUserCRMScope scope=new HthUserCRMScope(null,"UserExtensionData.create","CREATE")) {
   scope.channel(null,"HTH").result("SUCCESS").response(status);
  }
  check("PENDING_APPROVAL".equals(AdapterFactoryConfigurator.last.getValues().get("businessOutcome")),"pending must not become success");
  System.out.println("PASS: module Adapter boundary, BCO bypass, stored-channel gate, audit independence and Adapter failure isolation");
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
