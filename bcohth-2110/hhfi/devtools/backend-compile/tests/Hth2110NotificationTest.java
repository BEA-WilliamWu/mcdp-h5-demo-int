package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;
import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserAlertRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserManagementActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.*;
import static com.ofss.digx.cz.bea.app.sms.service.user.HthContactUpdateNotification.*;

/** Real production helper/SDK DTOs; only contacts, clock, config and delivery are fixtures. */
public class Hth2110NotificationTest {
    static final class Probe extends HthContactUpdateNotification {
        Map<String,User> users = new HashMap<>();
        Map<String,String> countries = new HashMap<>();
        CZPartyPreferenceDTO company = new CZPartyPreferenceDTO();
        List<Notice> delivered = new ArrayList<>();
        boolean companyFails, publishFails;
        @Override User readUser(String id) { return users.get(id); }
        @Override String country(String id) { return countries.getOrDefault(id, ""); }
        @Override CZPartyPreferenceDTO readCompany(String id) {
            check("PARTY".equals(id), "Company must be resolved by target party");
            if (companyFails) throw new IllegalStateException("CONTACT_SENTINEL");
            return company;
        }
        @Override String formattedDate() { return "2026/11/03 15:00"; }
        @Override String config(String key) { return key.endsWith("MAILSUBJ") ? "電郵及流動電話更新" : "電郵及流動電話資料。"; }
        @Override TransactionStatus publish(SessionContext context, Notice notice) {
            delivered.add(notice);
            if (publishFails) throw new IllegalStateException("INPUT_SENTINEL");
            TransactionStatus status = new TransactionStatus(); status.setReplyCode(0); return status;
        }
    }
    static String[] snapshot() { return new String[] {"TX","PARTY","TARGET@PARTY","old@example.test","new@example.test","11112222","33334444","852","86"}; }
    static User user(String email,String phone) { User u=new User();u.setEmailId(email);u.setMobileNumber(phone);return u; }
    static Probe probe() {
        Probe p=new Probe();p.users.put("FINAL@PARTY",user("approver@example.test","55556666"));p.countries.put("FINAL@PARTY","853");
        p.users.put("TARGET@PARTY",user("new@example.test","33334444"));p.countries.put("TARGET@PARTY","86");
        p.company.setOfficeEmailId("company@example.test");p.company.setCompanyName("Example Company");return p;
    }
    public static void main(String[] args) throws java.lang.Exception {
        Probe p=probe();String[] s=snapshot();List<Notice> all=p.prepare(s,"FINAL@PARTY");
        check(all.size()==10,"Four email A, two email B, four SMS");
        Map<String,Set<String>> expected=new LinkedHashMap<>();
        expected.put(CONTACT+"/EMAIL",set("old@example.test","new@example.test","approver@example.test","company@example.test"));
        expected.put(EDIT+"/EMAIL",set("approver@example.test","company@example.test"));
        expected.put(WELCOME+"/SMS",set("33334444~86"));expected.put(EMAIL+"/SMS",set("33334444~86"));
        expected.put(EDIT+"/SMS",set("55556666~853"));expected.put(OLD_MOBILE+"/SMS",set("11112222~852"));
        Map<String,Set<String>> actual=new LinkedHashMap<>();Set<ActivityLog> identities=Collections.newSetFromMap(new IdentityHashMap<ActivityLog,Boolean>());
        for(Notice n:all){
            check(identities.add(n.log),"Each async notice has an independent immutable-after-publish log");
            NotificationDetail d=n.log.getNotificationDetails()[0];
            check("EXTERNAL".equals(d.getRecipientType())&&"PARTY".equals(d.getRecipientId()),"Explicit external destination/party");
            verifyFrameworkRecipient(n.log,d);
            actual.computeIfAbsent(n.event+"/"+d.getDestination(),k->new LinkedHashSet<>()).add(d.getDispatchAddress());
            if(n.log instanceof UserProfUpdateActivityLogDTO){
                UserProfUpdateActivityLogDTO log=(UserProfUpdateActivityLogDTO)n.log;
                if(d.getDestination()==DestinationType.SMS){
                    String masked=CZCommonUtils.maskName(CZCommonUtils.sepChar("TARGET"," "),1);
                    check(masked.equals(log.getProfileUser())&&!"TARGET".equals(log.getProfileUser()),"Existing BCO name mask applied before SMS registration");
                    check(!log.getEmailId().equals(s[4]),"SMS email placeholder retains BCO masking");
                }else check("TARGET".equals(log.getProfileUser()),"Email describes the target, not the approver");
            }else{
                UserManagementActivityLogDTO log=(UserManagementActivityLogDTO)n.log;
                check("TARGET".equals(log.getUserNameId()),"User-edit subject identifies target");
                check(!"TARGET".equals(log.getCompName())&&!log.getCompName().isEmpty(),"Company placeholder comes from company preference");
            }
        }
        check(expected.equals(actual),"All six routes map exactly to reviewed recipients");
        p=probe();p.company.setOfficeEmailId(" APPROVER@EXAMPLE.TEST ");
        List<Notice> dedup=p.prepare(s,"FINAL@PARTY");
        check(dedup.size()==8,"Approver/company same mailbox once per event, both distinct email events retained");
        s[3]=" NEW@EXAMPLE.TEST ";dedup=p.prepare(s,"FINAL@PARTY");
        check(dedup.size()==7,"Old/new/approver/company normalized within each email event");
        s=snapshot();p.users.put("FINAL@PARTY",user(s[4],s[6]));p.countries.put("FINAL@PARTY",s[8]);
        check(p.prepare(s,"FINAL@PARTY").stream().filter(n->n.log.getNotificationDetails()[0].getDestination()==DestinationType.SMS).count()==4,
                "Different SMS events remain even when final approver and target share a number");
        p=probe();p.companyFails=true;p.countries.remove("FINAL@PARTY");
        check(p.prepare(snapshot(),"FINAL@PARTY").size()==7,"Missing company and AP country do not drop other valid recipients");
        p=probe();SessionContext context=new SessionContext();context.setTargetUnit("OBDX_BU");context.setUserId("BACKGROUND");
        p.notifyAfterCommit(context,snapshot(),"FINAL@PARTY");check(p.delivered.size()==10,"Committed contacts generate notices independent of executing session identity");
        p.delivered.clear();p.users.get("TARGET@PARTY").setEmailId("not-committed@example.test");
        p.notifyAfterCommit(context,snapshot(),"FINAL@PARTY");check(p.delivered.isEmpty(),"Uncommitted/mismatched target contact cannot notify");
        p=probe();p.countries.put("TARGET@PARTY","852");p.notifyAfterCommit(context,snapshot(),"FINAL@PARTY");check(p.delivered.isEmpty(),"Wrong committed country is rejected");
        p=probe();p.publishFails=true;p.notifyAfterCommit(context,snapshot(),"FINAL@PARTY");check(p.delivered.size()==10,"Failure of one destination does not abort other routes or throw into committed approval");
        gatesAndSnapshot(context);
        System.out.println("PASS 2110: six recipient/template routes, ten notices, event-scoped dedup, masking, country snapshots, failures and BCO/single-field exclusions");
    }
    static void gatesAndSnapshot(SessionContext context) {
        UserAlertRequestDTO changes=new UserAlertRequestDTO();
        com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData stored=new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
        stored.setUserChannelType("HTH");stored.setCdcNo("PARTY");stored.setUserID("TARGET@PARTY");stored.setMobileCode("+852");
        check(HthContactUpdateNotification.applies(context,changes,stored),"HTH both changed selected");
        changes.setEmailId(true);check(!HthContactUpdateNotification.applies(context,changes,stored),"Mobile-only stays legacy");
        changes.setEmailId(false);changes.setMobNo(true);check(!HthContactUpdateNotification.applies(context,changes,stored),"Email-only stays legacy");
        changes.setMobNo(false);stored.setUserChannelType("BCO");check(!HthContactUpdateNotification.applies(context,changes,stored),"Ordinary BCO stays legacy");
        stored.setUserChannelType("HTH");context.setTargetUnit("BM");check(!HthContactUpdateNotification.applies(context,changes,stored),"BM excluded");context.setTargetUnit("OBDX_BU");
        User old=user("old@example.test","11112222");UserExtensionDataDTO request=new UserExtensionDataDTO();request.setCdcNo("PARTY");request.setUserID("TARGET@PARTY");request.setMobileCode("86");
        UserDTO newUser=new UserDTO();newUser.setEmailId("new@example.test");newUser.setMobileNumber("33334444");request.setUserDTO(newUser);
        com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO,"TX");
        String[] captured=HthContactUpdateNotification.capture(context,request,old,stored);
        old.setEmailId("new@example.test");old.setMobileNumber("33334444");stored.setMobileCode("86");
        check("old@example.test".equals(captured[3])&&"11112222".equals(captured[5])&&"852".equals(captured[7]),"Managed entity mutation cannot change captured old contacts");
        HthContactUpdateNotification.defer(captured);captured[3]="MUTATED";
        String[] marked=(String[])com.ofss.fc.infra.thread.ThreadAttribute.clear(HthContactUpdateNotification.MARKER);
        check("old@example.test".equals(marked[3]),"Deferred marker owns its own snapshot");
    }
    static void verifyFrameworkRecipient(ActivityLog log,NotificationDetail detail) throws java.lang.Exception {
        com.ofss.fc.domain.ep.entity.action.subscriber.RecipientMessageTemplateKey key=new com.ofss.fc.domain.ep.entity.action.subscriber.RecipientMessageTemplateKey();
        key.setSubscriberType(com.ofss.fc.enumeration.ep.SubscriberType.EXTERNAL);key.setDestinationType(detail.getDestination());
        com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate template=
            (com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate)java.lang.reflect.Proxy.newProxyInstance(
                com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate.class.getClassLoader(),
                new Class[]{com.ofss.fc.domain.ep.entity.action.subscriber.IRecipientMessageTemplate.class},
                (p,m,args)->m.getName().equals("getRecipientMessageTemplateKey")?key:null);
        com.ofss.fc.app.context.SessionContext context=new com.ofss.fc.app.context.SessionContext();context.setUserId("BACKGROUND");
        java.util.List<com.ofss.fc.app.ep.dto.AlertPartyDetailsDTO> recipients=
            new com.ofss.fc.domain.ep.service.recipient.ExternalRecipientDerivationHelper().deriveRecipients(context,log,template,null);
        check(recipients.size()==1&&detail.getDispatchAddress().equals(recipients.get(0).getElectronicAddress()),
            "Real SDK selects exactly the explicit channel/address");
        if(log.getInputfacts()!=null)check(log.getInputfacts()[0].getValue().equals(recipients.get(0).getUserId()),"Real SDK preserves delivery identity input fact");
    }
    static Set<String> set(String... values){return new LinkedHashSet<>(Arrays.asList(values));}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
