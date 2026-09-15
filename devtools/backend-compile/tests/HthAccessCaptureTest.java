package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess;
import fixture.Bank;
import fixture.AccessBank;
import java.util.*;
import javax.persistence.*;

public final class HthAccessCaptureTest {
    static HthUserAccessAccount account(String id,String number,String party,String linkage) {
        HthUserAccessAccount a=new HthUserAccessAccount();HthUserAccessAccountKey key=new HthUserAccessAccountKey();key.setId(id);a.setKey(key);
        a.setPartyId("PARTY");a.setCloseId("API@PARTY");a.setAccessPartyId(party);a.setLinkageType(linkage);
        a.setAccountType("CSA");a.setAccountNumber(number);a.setObjectStatus("A");AccessBank.accounts.add(a);return a;
    }
    static void grant(String id,String api,String status) {
        HthUserAccessAccountApi a=new HthUserAccessAccountApi();a.setHthUserAccessAccountId(id);a.setApiMasterId(api);a.setObjectStatus(status);
        AccessBank.apis.computeIfAbsent(id,k->new ArrayList<>()).add(a);
    }
    public static void run() throws Exception {
        SessionContext context=new SessionContext();context.setTargetUnit("OBDX_BU");context.setUserLocale("en");context.setServiceCallContextType("EXECUTE");
        HostToHostUserAccessDTO request=new HostToHostUserAccessDTO();request.setPartyId("PARTY");request.setCloseId("API@PARTY");request.setAccessPartyId("PARTY");request.setLinkageType("RELATED");request.setUsername("BROWSER_UNTRUSTED");
        java.util.prefs.Preferences prefs=ConfigurationFactory.getInstance().getConfigurations(HthUserAccessNotificationPlan.CONFIG);
        prefs.putBoolean(HthUserAccessNotificationPlan.ENABLED,false);
        int approvals=Bank.approvalReads;
        Bank.check(HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1")==null,"default-off");
        Bank.check(AccessBank.reads==0 && Bank.approvalReads==approvals,"off does not add repositories");
        prefs.putBoolean(HthUserAccessNotificationPlan.ENABLED,true);
        context.setTargetUnit("OTHER_BU");Bank.check(HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1")==null,"other unit");context.setTargetUnit("OBDX_BU");
        context.setServiceCallContextType("VALIDATE");Bank.check(HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1")==null,"approval simulation");context.setServiceCallContextType("EXECUTE");
        Bank.check(HthUserAccessNotification.capture(context,request,"DELETE","ACCESS-1")==null,"delete is outside AC1/2");
        Transaction.snapshot=request;Transaction.service=HthUserAccessNotificationPlan.activity("LINK");Transaction.status="PENDING";
        Bank.check(HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1")==null,"maker/intermediate approval");
        Transaction.status="APPROVED";Transaction.service="BCO.service";
        Bank.check(HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1")==null,"unrelated service");Transaction.service=HthUserAccessNotificationPlan.activity("LINK");
        HostToHostUserAccessDTO wrong=new HostToHostUserAccessDTO();Transaction.snapshot=wrong;
        try{HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1");throw new AssertionError("approval context mismatch accepted");}catch(IllegalStateException expected){}Transaction.snapshot=request;
        AccessBank.hth=false;try{HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1");throw new AssertionError("non HTH accepted");}catch(IllegalStateException expected){}AccessBank.hth=true;
        Transaction.signers="FIRST~~FINAL~";
        HthUserAccessNotification.Snapshot snapshot=HthUserAccessNotification.capture(context,request,"CREATE","ACCESS-1");
        Bank.check(snapshot.before.isEmpty() && "API@PARTY".equals(snapshot.user) && "FINAL".equals(snapshot.approver),"trusted HTH identity + final signer");
        User user=new User();user.setEmailId("api-access@example.test");user.setMobileNumber("61234567");User.rows.put("API@PARTY",user);
        UserExtensionData ext=new UserExtensionData();ext.setMobileCode("852");UserExtensionData.rows.put("API@PARTY",ext);
        AccessBank.company.setOfficeEmailId("company-access@example.test");AccessBank.company.setOfficeTelNo("+852 6555-5555");AccessBank.company.setCompanyName("TEST COMPANY LIMITED");
        account("A1","111","PARTY","RELATED");grant("A1","BALANCE","A");
        account("OTHER","222","OTHER_PARTY","ASSOCIATED");grant("OTHER","TRANSFER","A");
        Bank.check(HthUserAccessNotification.effective(snapshot).size()==2,"only selected user/context active account and services");
        EntityManagerFactory factory=Persistence.createEntityManagerFactory("test");EntityManager em=factory.createEntityManager();DataAccessManager.current=TestOrmAccess.wrap(em);
        try {
            em.getTransaction().begin();HthUserAccessNotification.stage(snapshot);
            Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY")),"before commit invisible");
            em.getTransaction().rollback();Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY")),"rollback removes notices");
            em.getTransaction().begin();HthUserAccessNotification.stage(snapshot);HthUserAccessNotification.stage(snapshot);em.getTransaction().commit();
            Bank.check("6".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY")),"three roles two channels and reentry once");
            Bank.check("PARTY".equals(AccessBank.companyParty),"owner company contact");
            Bank.check("2".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE RECIPIENT_ROLE='AP' AND APPROVER_ID='FINAL'")),"final approver only");
            Transaction.service=HthUserAccessNotificationPlan.activity("UPDATE");
            HthUserAccessNotification.Snapshot same=HthUserAccessNotification.capture(context,request,"EDIT","ACCESS-NOOP");
            Collections.reverse(AccessBank.accounts);Collections.reverse(AccessBank.apis.get("A1"));grant("A1","OLD","I");
            int companyReads=AccessBank.companyReads;
            em.getTransaction().begin();HthUserAccessNotification.stage(same);em.getTransaction().commit();
            Bank.check(AccessBank.companyReads==companyReads && "6".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY")),"same values/reorder/inactive service not notified");
            grant("A1","TRANSFER","A");
            em.getTransaction().begin();HthUserAccessNotification.stage(same);em.getTransaction().commit();
            Bank.check("6".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE CHANGE_TYPE='UPDATE'")),"API-only change notified");
            HthUserAccessNotification.Snapshot remove=HthUserAccessNotification.capture(context,request,"EDIT","ACCESS-REMOVE");
            AccessBank.apis.get("A1").get(0).setObjectStatus("I");
            Bank.check(!remove.before.equals(HthUserAccessNotification.effective(remove)),"removing an API is a change");
            // Approved Associated changes notify the owner company, never the data-owning company.
            request.setAccessPartyId("OTHER_PARTY");request.setLinkageType("ASSOCIATED");
            HthUserAccessNotification.Snapshot associated=HthUserAccessNotification.capture(context,request,"EDIT","ACCESS-ASSOCIATED");
            grant("OTHER","HISTORY","A");
            // Shared destination receives identical content only once: API wins then AP then company.
            User.rows.get("FINAL").setEmailId(user.getEmailId());User.rows.get("FINAL").setMobileNumber(user.getMobileNumber());
            AccessBank.company.setOfficeEmailId(user.getEmailId());AccessBank.company.setOfficeTelNo("61234567~852");
            em.getTransaction().begin();HthUserAccessNotification.stage(associated);em.getTransaction().commit();
            Bank.check("2".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE APPROVAL_REF='ACCESS-ASSOCIATED'")),"three roles same addresses dedup to 2");
            Bank.check("PARTY".equals(AccessBank.companyParty),"associated uses owner contact");
            HthUserAccessNotification.Snapshot missing=HthUserAccessNotification.capture(context,request,"EDIT","ACCESS-MISSING");
            grant("OTHER","NEW","A");user.setEmailId(null);user.setMobileNumber(null);User.rows.get("FINAL").setEmailId(null);User.rows.get("FINAL").setMobileNumber(null);AccessBank.company.setOfficeEmailId(null);AccessBank.company.setOfficeTelNo("12345678");
            em.getTransaction().begin();HthUserAccessNotification.stage(missing);em.getTransaction().commit();
            Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE APPROVAL_REF='ACCESS-MISSING'")),"missing contacts skip no guessing");
            Bank.check("85265555555".equals(HthUserAccessNotification.companyMobile("+852 6555-5555")),"company international normalization");
            Bank.check("".equals(HthUserAccessNotification.companyMobile("65555555")),"company bare local number is not guessed");
        }finally{if(em.getTransaction().isActive())em.getTransaction().rollback();DataAccessManager.current=null;em.close();factory.close();}
        System.out.println("PASS: production 1216 approval/flag/context gates, Link/API-only Edit/noop, owner contacts, role dedup, actual ORM rollback/commit/reentry");
    }
}
