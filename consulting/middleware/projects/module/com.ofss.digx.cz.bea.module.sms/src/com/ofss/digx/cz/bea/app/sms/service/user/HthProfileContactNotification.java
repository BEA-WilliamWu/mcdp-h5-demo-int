package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan.Recipient;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Captures contacts BEFORE ORM mutation; stages notifications in the existing profile transaction. */
final class HthProfileContactNotification {
    private HthProfileContactNotification() { }
    static Snapshot capture(SessionContext context, UserExtensionDataDTO request, User oldUser,
            UserExtensionData oldExtension) throws Exception {
        if (!"OBDX_BU".equals(context.getTargetUnit())) return null;
        if (!ConfigurationFactory.getInstance().getConfigurations("HthProfileContactNotification")
                .getBoolean("HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED", false)) return null;
        String party = HthContactNotificationPlan.text(request.getCdcNo());
        String user = HthContactNotificationPlan.text(request.getUserID());
        if (party.isEmpty() || !user.endsWith("@" + party)) return null;
        if (!party.equals(oldExtension.getCdcNo()) || !user.equals(oldExtension.getUserID()))
            throw new IllegalStateException("HTH contact profile owner mismatch");
        // Profile existence is the server-side HTH discriminator. No account-access/status dependency.
        IHthUserProfileAdapter profiles = (IHthUserProfileAdapter) RepositoryAdapterFactory.getInstance()
            .getRepositoryAdapter(IHthUserProfileAdapter.HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER);
        Map<String, String> closeIds = profiles.listCloseIdsByUserKey(party);
        String shortUser = user.substring(0, user.length() - party.length() - 1);
        if (!closeIds.containsKey(IHthUserProfileAdapter.userProfileKey(party, user))
                && !closeIds.containsKey(IHthUserProfileAdapter.userProfileKey(party, shortUser))) return null;
        Snapshot snapshot = new Snapshot();
        snapshot.party = party; snapshot.user = user; snapshot.unit = context.getTargetUnit();
        snapshot.locale = context.getUserLocale();
        String oldMobile = HthContactNotificationPlan.mobile(oldExtension.getMobileCode(), oldUser.getMobileNumber());
        String newMobile = HthContactNotificationPlan.mobile(request.getMobileCode(), request.getUserDTO().getMobileNumber());
        snapshot.change = HthContactNotificationPlan.change(oldUser.getEmailId(), request.getUserDTO().getEmailId(),
                contactKey(oldExtension.getMobileCode(), oldUser.getMobileNumber()),
                contactKey(request.getMobileCode(), request.getUserDTO().getMobileNumber()));
        if ("NONE".equals(snapshot.change)) return snapshot;
        // VALIDATE is a maker/approval simulation, never a committed contact change.
        if ("VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))) return snapshot;
        snapshot.reference = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(
                com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
        if (HthContactNotificationPlan.text(snapshot.reference).isEmpty())
            throw new IllegalStateException("HTH contact approval reference missing");
        TransactionKey key = new TransactionKey(); key.setId(snapshot.reference);
        Transaction transaction = new Transaction().read(key);
        if (transaction == null || transaction.getApprovalDetails() == null
                || !"APPROVED".equals(String.valueOf(transaction.getApprovalDetails().getStatus()))
                || !HthContactNotificationPlan.ACTIVITY.equals(transaction.getServiceId()))
            return snapshot;
        // The approval service commits APPROVED before invoking this update. EXECUTION is only
        // set on its in-memory transaction until after the business invocation; do not require
        // that not-yet-persisted processing step on a separate repository read.
        snapshot.approver = finalSigner(transaction.getApprovalDetails().getSignedBy());
        if (snapshot.approver.isEmpty()) throw new IllegalStateException("HTH contact final approver missing");
        UserKey approverKey = new UserKey(); approverKey.setUserId(snapshot.approver);
        User approver = new User().read(approverKey);
        if (approver == null) throw new IllegalStateException("HTH contact final approver profile missing");
        UserExtensionDataKey extensionKey = new UserExtensionDataKey();
        extensionKey.setUserExtensionKey(snapshot.approver);
        UserExtensionData extension = new UserExtensionData().read(extensionKey);
        String approverMobile = extension == null ? "" :
                HthContactNotificationPlan.mobile(extension.getMobileCode(), approver.getMobileNumber());
        snapshot.recipients = HthContactNotificationPlan.recipients(snapshot.change, user, snapshot.approver,
                oldUser.getEmailId(), request.getUserDTO().getEmailId(), oldMobile, newMobile,
                approver.getEmailId(), approverMobile);
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
        snapshot.approvedAt = format.format(new java.util.Date());
        return snapshot;
    }
    private static String contactKey(String country, String number) {
        return HthContactNotificationPlan.text(country).replace("+", "") + ":"
                + HthContactNotificationPlan.text(number).replaceAll("[\\s()-]", "");
    }
    static String finalSigner(String signers) {
        String last = "";
        for (String signer : HthContactNotificationPlan.text(signers).split("~"))
            if (!signer.trim().isEmpty()) last = signer.trim();
        return last;
    }
    static void stage(Snapshot snapshot) throws Exception {
        if (snapshot == null || snapshot.recipients == null) return;
        Session session = DataAccessManager.getManager().fetchCurrentSession();
        if (session == null) throw new IllegalStateException("HTH contact business transaction missing");
        for (Recipient recipient : snapshot.recipients) {
            String id = HthContactNotificationPlan.id(snapshot.unit, snapshot.reference, snapshot.user,
                    snapshot.change, recipient.role, recipient.channel, recipient.address);
            Query query = session.createSQLQuery("INSERT INTO DIGX_CZ_HTH_CONTACT_NOTIFY "
                + "(ID, TARGET_UNIT, APPROVAL_REF, PARTY_ID, TARGET_USER_ID, APPROVER_ID, CHANGE_TYPE, "
                + "RECIPIENT_ROLE, CHANNEL, ADDRESS, RECIPIENT_USER_ID, USER_LOCALE, APPROVED_AT, STATE, CREATED_AT, UPDATED_AT) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', SYSTIMESTAMP, SYSTIMESTAMP FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM DIGX_CZ_HTH_CONTACT_NOTIFY WHERE ID = ?)");
            Object[] values = {id, snapshot.unit, snapshot.reference, snapshot.party, snapshot.user,
                snapshot.approver, snapshot.change, recipient.role, recipient.channel, recipient.address,
                recipient.user, snapshot.locale, snapshot.approvedAt, id};
            for (int i = 0; i < values.length; i++) query.setParameter(i + 1, values[i]);
            query.executeUpdate();
        }
    }
    static final class Snapshot {
        String party, user, unit, locale, change, reference, approver, approvedAt;
        List<Recipient> recipients;
    }
}
