package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessNotificationPlan;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.*;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/** 1216: capture immutable effective grants; stage only after approved replacement succeeds. */
final class HthUserAccessNotification {
    private static final Logger LOG = Logger.getLogger(HthUserAccessNotification.class.getName());
    private HthUserAccessNotification() { }
    static Snapshot capture(SessionContext context, HostToHostUserAccessDTO request,
            String action, String reference) throws Exception {
        if (!"OBDX_BU".equals(context.getTargetUnit()) || "VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))
                || !("CREATE".equals(action) || "EDIT".equals(action))
                || !ConfigurationFactory.getInstance().getConfigurations(HthUserAccessNotificationPlan.CONFIG)
                    .getBoolean(HthUserAccessNotificationPlan.ENABLED, false)) return null;
        Snapshot s = new Snapshot();
        s.change = "CREATE".equals(action) ? "LINK" : "UPDATE";
        s.reference = text(reference);
        if (s.reference.isEmpty()) throw new IllegalStateException("HTH access approval reference missing");
        TransactionKey key = new TransactionKey(); key.setId(s.reference);
        Transaction transaction = new Transaction().read(key);
        if (transaction == null || transaction.getApprovalDetails() == null
                || !"APPROVED".equals(String.valueOf(transaction.getApprovalDetails().getStatus()))
                || !HthUserAccessNotificationPlan.activity(s.change).equals(transaction.getServiceId())) return null;
        if (!(transaction.getTransactionSnapshot() instanceof HostToHostUserAccessDTO)
                || !sameContext(request, (HostToHostUserAccessDTO) transaction.getTransactionSnapshot()))
            throw new IllegalStateException("HTH access approval context mismatch");
        s.party=text(request.getPartyId()); s.closeId=text(request.getCloseId());
        s.accessParty=text(request.getAccessPartyId()); s.linkage=text(request.getLinkageType());
        s.unit=context.getTargetUnit(); s.locale=context.getUserLocale();
        HthUserProfileKey profileKey = new HthUserProfileKey();
        profileKey.setPartyId(s.party); profileKey.setCloseId(s.closeId);
        if (HthUserProfileRepository.getInstance().read(profileKey) == null)
            throw new IllegalStateException("HTH access user profile missing");
        s.user = s.closeId.contains("@") ? s.closeId : s.closeId + "@" + s.party;
        if (!s.user.endsWith("@" + s.party)) throw new IllegalStateException("HTH access user owner mismatch");
        s.approver = finalSigner(transaction.getApprovalDetails().getSignedBy());
        if (s.approver.isEmpty()) throw new IllegalStateException("HTH access final approver missing");
        s.before=effective(s);
        return s;
    }
    private static boolean sameContext(HostToHostUserAccessDTO a, HostToHostUserAccessDTO b) {
        return text(a.getPartyId()).equals(text(b.getPartyId())) && text(a.getCloseId()).equals(text(b.getCloseId()))
                && text(a.getAccessPartyId()).equals(text(b.getAccessPartyId()))
                && text(a.getLinkageType()).equals(text(b.getLinkageType()));
    }
    static Set<String> effective(Snapshot s) throws Exception {
        Set<String> result = new TreeSet<String>();
        List<HthUserAccessAccount> accounts = HthUserAccessAccountRepository.getInstance()
                .listByContext(s.party,s.closeId,s.accessParty,s.linkage);
        if (accounts != null) for (HthUserAccessAccount account : accounts) {
            if (account == null || !"A".equals(account.getObjectStatus())) continue;
            String identity=HthUserAccessNotificationPlan.id(text(account.getAccountType()).toUpperCase(Locale.ROOT),
                    account.getAccountNumber());
            result.add(identity);
            List<HthUserAccessAccountApi> apis=HthUserAccessAccountApiRepository.getInstance()
                    .listByAccountId(account.getKey().getId());
            if (apis != null) for (HthUserAccessAccountApi api : apis)
                if (api != null && "A".equals(api.getObjectStatus()))
                    result.add(identity+":"+text(api.getApiMasterId()));
        }
        return result;
    }
    static void stage(Snapshot s) throws Exception {
        if (s == null || s.before.equals(effective(s))) return;
        Session session=DataAccessManager.getManager().fetchCurrentSession();
        if (session == null) throw new IllegalStateException("HTH access business transaction missing");
        User user=readUser(s.user), approver=readUser(s.approver);
        IAdapterFactory factory=AdapterFactoryConfigurator.getInstance().getAdapterFactory(
                com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
        IUserExtensionAdapter adapter=(IUserExtensionAdapter) factory.getAdapter(
                com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
        CZPartyPreferenceDTO company=adapter.getPartyPreferences(s.party);
        if (company == null) throw new IllegalStateException("HTH access owner company profile missing");
        // Align BCO UserAccountAccessExt's company-name masking; never use accessParty's contact.
        String companyName=text(company.getCompanyName());
        s.companyName=companyName.isEmpty() ? "" : CZCommonUtils.maskName(CZCommonUtils.sepChar(companyName," "),1);
        SimpleDateFormat format=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong")); s.approvedAt=format.format(new java.util.Date());
        LinkedHashMap<String,Recipient> recipients=new LinkedHashMap<String,Recipient>();
        add(recipients,"API","EMAIL",HthContactNotificationPlan.email(user.getEmailId()),s.user);
        add(recipients,"API","SMS",mobile(s.user,user),s.user);
        add(recipients,"AP","EMAIL",HthContactNotificationPlan.email(approver.getEmailId()),s.approver);
        add(recipients,"AP","SMS",mobile(s.approver,approver),s.approver);
        add(recipients,"COMPANY","EMAIL",HthContactNotificationPlan.email(company.getOfficeEmailId()),s.user);
        add(recipients,"COMPANY","SMS",companyMobile(company.getOfficeTelNo()),s.user);
        for (Recipient recipient : recipients.values()) {
            String id=HthUserAccessNotificationPlan.id(s.unit,s.reference,s.party,s.closeId,s.accessParty,s.linkage,
                    s.change,recipient.channel,recipient.address);
            Query q=session.createSQLQuery("INSERT INTO DIGX_CZ_HTH_ACCESS_NOTIFY "
                + "(ID,TARGET_UNIT,APPROVAL_REF,PARTY_ID,TARGET_USER_ID,APPROVER_ID,CHANGE_TYPE,"
                + "RECIPIENT_ROLE,CHANNEL,ADDRESS,RECIPIENT_USER_ID,USER_LOCALE,APPROVED_AT,STATE,"
                + "CLOSE_ID,ACCESS_PARTY_ID,LINKAGE_TYPE,COMPANY_NAME,CREATED_AT,UPDATED_AT) "
                + "SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,'READY',?,?,?,?,SYSTIMESTAMP,SYSTIMESTAMP FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM DIGX_CZ_HTH_ACCESS_NOTIFY WHERE ID=?)");
            Object[] values={id,s.unit,s.reference,s.party,s.user,s.approver,s.change,recipient.role,
                    recipient.channel,recipient.address,recipient.user,s.locale,s.approvedAt,
                    s.closeId,s.accessParty,s.linkage,s.companyName,id};
            for(int i=0;i<values.length;i++) q.setParameter(i+1,values[i]);
            q.executeUpdate();
        }
    }
    private static User readUser(String id) throws Exception {
        UserKey key=new UserKey(); key.setUserId(id); User user=new User().read(key);
        if(user==null) throw new IllegalStateException("HTH access notification user missing"); return user;
    }
    private static String mobile(String id,User user) throws Exception {
        UserExtensionDataKey key=new UserExtensionDataKey(); key.setUserExtensionKey(id);
        UserExtensionData extension=new UserExtensionData().read(key);
        return extension==null ? "" : HthContactNotificationPlan.mobile(extension.getMobileCode(),user.getMobileNumber());
    }
    static String companyMobile(String value) {
        String v=text(value);
        // BCO contact preferences also support local-number~country-code. Never guess the country.
        String[] parts=v.split("~",-1);
        if(parts.length==2) return HthContactNotificationPlan.mobile(parts[1],parts[0]);
        v=v.replaceAll("[\\s()-]", "").replaceFirst("^\\+", "");
        return v.matches("[1-9][0-9]{9,14}") ? v : "";
    }
    static String finalSigner(String signers) {
        String last=""; for(String signer:text(signers).split("~")) if(!signer.trim().isEmpty()) last=signer.trim();
        return last;
    }
    private static void add(Map<String,Recipient> rows,String role,String channel,String address,String user) {
        if(text(address).isEmpty()) {
            LOG.info("HTH_ACCESS stage=RECIPIENT role="+role+" channel="+channel+" reason=MISSING_OR_INVALID_CONTACT"); return;
        }
        // Same approved message for all three roles: API, then final approver, then company.
        String key=channel+":"+address;
        if(!rows.containsKey(key)) rows.put(key,new Recipient(role,channel,address,user));
    }
    private static String text(String value) { return HthContactNotificationPlan.text(value); }
    static final class Snapshot {
        String unit,reference,party,user,approver,change,locale,approvedAt,closeId,accessParty,linkage,companyName;
        Set<String> before;
    }
    private static final class Recipient {
        final String role,channel,address,user;
        Recipient(String role,String channel,String address,String user) {
            this.role=role;this.channel=channel;this.address=address;this.user=user;
        }
    }
}
