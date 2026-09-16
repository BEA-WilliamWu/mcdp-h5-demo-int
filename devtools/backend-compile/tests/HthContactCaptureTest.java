package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.cz.bea.app.sms.dto.user.*;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.eclipselink.TestOrmAccess;
import fixture.Bank;
import java.util.*;
import javax.persistence.*;

public final class HthContactCaptureTest {
    public static void run() throws Exception {
        SessionContext context=new SessionContext();context.setTargetUnit("OBDX_BU");context.setUserLocale("en");
        context.setServiceCallContextType("EXECUTE");
        User old=new User();old.setEmailId("old@example.test");old.setMobileNumber("61234567");
        User next=new User();next.setEmailId("new@example.test");next.setMobileNumber("62345678");
        UserExtensionData ext=new UserExtensionData();ext.setCdcNo("PARTY");ext.setUserID("API@PARTY");ext.setMobileCode("852");
        UserExtensionDataDTO request=new UserExtensionDataDTO();request.setCdcNo("PARTY");request.setUserID("API@PARTY");request.setMobileCode("852");request.setUserDTO(next);
        User approver=new User();approver.setEmailId("final@example.test");approver.setMobileNumber("63456789");User.rows.put("FINAL",approver);
        UserExtensionData apExt=new UserExtensionData();apExt.setMobileCode("852");UserExtensionData.rows.put("FINAL",apExt);
        java.util.prefs.Preferences prefs=ConfigurationFactory.getInstance().getConfigurations("HthProfileContactNotification");
        prefs.putBoolean("HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED",false);
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext)==null,"flag off");
        Bank.check(Bank.profileReads==0 && Bank.approvalReads==0,"flag off has no profile/approval dependency");
        prefs.putBoolean("HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED",true);
        context.setTargetUnit("OTHER_BU");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext)==null,"unconfigured unit retains existing behavior");
        context.setTargetUnit("OBDX_BU");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext)==null,"BCO must retain legacy path");
        Bank.check(Bank.approvalReads==0,"BCO must not read approval");
        Bank.profiles.put(IHthUserProfileAdapter.userProfileKey("PARTY","API@PARTY"),"API@PARTY");
        context.setServiceCallContextType("VALIDATE");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext).recipients==null,"validation never stages notices");
        context.setServiceCallContextType("EXECUTE");
        Transaction.status="PENDING";
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext).recipients==null,"intermediate approval");
        Transaction.status="APPROVED";Transaction.service="some.other.service";
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext).recipients==null,"unrelated approval");
        Transaction.service=HthContactNotificationPlan.ACTIVITY;
        HthProfileContactNotification.Snapshot snapshot=HthProfileContactNotification.capture(context,request,old,ext);
        Bank.check("BOTH".equals(snapshot.change) && snapshot.recipients.size()==9,"both changes: four emails plus three user SMS/two final approver SMS");
        Bank.check("FINAL".equals(snapshot.approver),"last signer only");
        Bank.check(snapshot.recipients.stream().filter(r->r.hasRole("COMPANY") && r.channel.equals("EMAIL")).count()==1,"initial company email");
        Bank.check(snapshot.recipients.stream().filter(r->r.hasRole("API") && r.channel.equals("SMS")).count()==3,"both: new-number and email-change SMS are distinct");
        Bank.check(snapshot.recipients.stream().filter(r->r.hasRole("AP") && r.channel.equals("SMS")).count()==2,"both: final AP receives both change reminders");
        Bank.check(snapshot.recipients.stream().noneMatch(r->r.hasRole("AP") && HthContactNotificationPlan.NEW_MOBILE_SMS.equals(r.event)),"AP never gets the target's new-number welcome");

        Bank.check("FINAL".equals(HthProfileContactNotification.finalSigner(" FIRST ~~FINAL~ ")),"empty signer segments");
        Bank.check(HthProfileContactNotification.finalSigner("~MAKER~").isEmpty(),"maker is not a final approver");
        Bank.check(snapshot.recipients.stream().noneMatch(r->"FIRST".equals(r.user)),"earlier approver excluded");
        old.setEmailId("mutated@example.test");next.setEmailId("later@example.test");
        Bank.check(snapshot.recipients.stream().anyMatch(r->r.address.equals("old@example.test")),"old snapshot survives ORM mutation");
        Bank.check(snapshot.recipients.stream().anyMatch(r->r.address.equals("new@example.test")),"approved new snapshot survives mutation");
        next.setEmailId(old.getEmailId());next.setMobileNumber(old.getMobileNumber());
        Bank.check("NONE".equals(HthProfileContactNotification.capture(context,request,old,ext).change),"no change");
        request.setMobileCode("853");
        Bank.check("MOBILE".equals(HthProfileContactNotification.capture(context,request,old,ext).change),"country-code-only change");
        request.setMobileCode("852");next.setEmailId("new@example.test");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext).recipients.size()==6,"email-only retains one unchanged mobile");
        next.setEmailId(old.getEmailId());next.setMobileNumber("69999999");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext).recipients.size()==6,"mobile-only retains one unchanged email");
        Bank.profiles.clear();Bank.profiles.put(IHthUserProfileAdapter.userProfileKey("PARTY","API"),"API");
        Bank.check(HthProfileContactNotification.capture(context,request,old,ext)!=null,"short legacy CloseID mapping");
        ext.setCdcNo("OTHER");
        try{HthProfileContactNotification.capture(context,request,old,ext);throw new AssertionError("wrong owner accepted");}
        catch(IllegalStateException expected){}ext.setCdcNo("PARTY");
        List<HthContactNotificationPlan.Recipient> same=HthContactNotificationPlan.recipients("EMAIL","API","FINAL",
            " shared@EXAMPLE.test ","shared@example.test","85261234567","85261234567","shared@example.test","85261234567","shared@example.test");
        Bank.check(same.size()==2,"old/new and identical SMS dedup, cross-role identical BCO content deduplicated");
        Bank.check(HthContactNotificationPlan.recipients("NONE","a","b","a","b","c","d","e","f","g").isEmpty(),"no-change recipient plan");
        Bank.check("85261234567".equals(HthContactNotificationPlan.mobile("+852","6123-4567")),"number normalization");
        Bank.check("".equals(HthContactNotificationPlan.mobile(null,"61234567")),"never guess country");
        EntityManagerFactory factory=Persistence.createEntityManagerFactory("test");
        EntityManager em=factory.createEntityManager();
        DataAccessManager.current=TestOrmAccess.wrap(em);
        try {
            em.getTransaction().begin();
            HthProfileContactNotification.stage(snapshot);
            Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX")),"worker cannot see uncommitted contact update");
            em.getTransaction().rollback();
            Bank.check("0".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX")),"business rollback removes intents");
            em.getTransaction().begin();HthProfileContactNotification.stage(snapshot);HthProfileContactNotification.stage(snapshot);em.getTransaction().commit();
            Bank.check("9".equals(Bank.scalar("SELECT COUNT(*) FROM DIGX_CZ_HTH_CONTACT_OUTBOX")),"commit + repeated approved reference stage once");
        } finally {if(em.getTransaction().isActive())em.getTransaction().rollback();DataAccessManager.current=null;em.close();factory.close();}
        System.out.println("PASS: production 851 capture, BCO/flag/approval gates, contact scenarios, final signer, immutable snapshots, real ORM rollback/commit/reentry");
    }
}
