package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.cz.bea.app.sms.dto.user.UserAlertRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.app.context.SessionContext;
import java.util.List;
import java.util.Map;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Preserves old contacts and resolves the final approver for BCO contact-update email. */
final class HthProfileApproverNotification {
    private static final Logger LOG = Logger.getLogger(HthProfileApproverNotification.class.getName());
    private static final String ACTIVITY = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    private HthProfileApproverNotification() { }

    static Approver resolve(SessionContext context, UserExtensionDataDTO request,
            UserAlertRequestDTO changes, UserExtensionData stored, User oldUser) {
        // update resolves the transient channel before entering this notification path.
        if (!isHth(context, stored) || (changes.getEmailId() && changes.getMobNo())) return null;
        Approver result = new Approver();
        // Copy before persistence; a managed User may subsequently contain the new address.
        result.oldTargetEmail = oldUser.getEmailId();
        if ("VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))) return result;
        try {
            if (!text(request.getUserID()).equals(stored.getUserID())
                    || !text(request.getCdcNo()).equals(stored.getCdcNo()))
                throw new IllegalStateException("HTH profile owner mismatch");
            String reference = (String) ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO);
            if (text(reference).isEmpty()) throw new IllegalStateException("Approval reference missing");
            TransactionKey key = new TransactionKey(); key.setId(reference);
            Transaction transaction = new Transaction().read(key);
            if (transaction == null || transaction.getApprovalDetails() == null
                    || !"APPROVED".equals(String.valueOf(transaction.getApprovalDetails().getStatus()))
                    || !ACTIVITY.equals(transaction.getServiceId())) return result;
            String signer = finalSigner(transaction.getApprovalDetails().getSignedBy());
            if (signer.isEmpty()) throw new IllegalStateException("Final approver missing");
            UserKey userKey = new UserKey(); userKey.setUserId(signer);
            User user = new User().read(userKey);
            if (user == null) throw new IllegalStateException("Final approver profile missing");
            result.id = signer;
            result.email = text(user.getEmailId());
        } catch (java.lang.Exception e) {
            // Never replace an unresolved final approver with the maker or background execution user.
            // Keep existing target/company notifications on their original path.
            LOG.log(Level.WARNING, "HTH_851 stage=APPROVER_RESOLUTION exception={0}", e.getClass().getSimpleName());
        }
        return result;
    }

    static void loadStoredChannel(SessionContext context, UserExtensionData stored)
            throws com.ofss.digx.infra.exceptions.Exception {
        if (stored == null || !"OBDX_BU".equals(context.getTargetUnit())) return;
        // userChannelType is transient. read(key) does not perform listUsers' HTH enrichment.
        // Resolve from persisted ownership, never from the client-supplied channel or an ORM cache.
        try {
            if (text(stored.getCdcNo()).isEmpty() || text(stored.getUserID()).isEmpty())
                throw new IllegalStateException("Stored profile owner missing");
            IHthUserProfileAdapter adapter = (IHthUserProfileAdapter) RepositoryAdapterFactory
                    .getInstance().getRepositoryAdapter(
                            IHthUserProfileAdapter.HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER);
            Map<String, String> profiles = adapter.listCloseIdsByUserKey(stored.getCdcNo());
            if (profiles == null) throw new IllegalStateException("HTH profile lookup returned no result");
            boolean hth = profiles.containsKey(IHthUserProfileAdapter.userProfileKey(
                    stored.getCdcNo(), stored.getUserID()));
            stored.setUserChannelType(hth ? "HTH" : "BCO");
            LOG.log(Level.INFO, "HTH_851 stage=CHANNEL_RESOLUTION source=HTH_USER_PROFILE hth={0}", hth);
        } catch (com.ofss.digx.infra.exceptions.Exception | RuntimeException e) {
            LOG.log(Level.WARNING, "HTH_851 stage=CHANNEL_RESOLUTION exception={0}", e.getClass().getSimpleName());
            // Do not silently execute the BCO PIN notification branch after a failed lookup.
            throw e;
        }
    }

    static boolean profileUpdateSucceeded(com.ofss.fc.service.response.TransactionStatus status,
            Boolean hthPinResetChanged) {
        // Preserve main's BCO condition; only HTH uses the extended SDK success check below.
        if (hthPinResetChanged == null) {
            return status != null && status.getErrorCode() == null;
        }
        boolean success = status != null && status.getReplyCode() == 0
                && (status.getErrorCode() == null || "0".equals(status.getErrorCode()))
                && (status.getValidationErrors() == null || status.getValidationErrors().length == 0);
        LOG.log(Level.INFO, "HTH_851 stage=PROFILE_RESULT success={0}", success);
        return success;
    }

    static Boolean loginPinResetChanged(SessionContext context, UserExtensionData stored,
            UserExtensionDataDTO request) {
        if (!isHth(context, stored)) return null; // Preserve the BCO hook's original behavior.
        if ("VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))) return false;
        String before = stored.getSecurityQuestionsBypass();
        String after = request.getBypassFlag();
        // Match the two actual state transitions in UserExtensionData.update.
        return ("N".equalsIgnoreCase(before) && "Y".equalsIgnoreCase(after))
                || ("Y".equalsIgnoreCase(before) && "N".equalsIgnoreCase(after));
    }

    private static boolean isHth(SessionContext context, UserExtensionData stored) {
        return stored != null && "OBDX_BU".equals(context.getTargetUnit())
                && ("HTH".equalsIgnoreCase(text(stored.getUserChannelType()))
                    || "H2H".equalsIgnoreCase(text(stored.getUserChannelType())));
    }

    static String finalSigner(String signedBy) {
        String last = "";
        int count = 0;
        for (String signer : text(signedBy).split("~")) {
            if (!signer.trim().isEmpty()) { last = signer.trim(); count++; }
        }
        // The first entry is the maker; only the final approved signer is a recipient.
        return count > 1 ? last : "";
    }

    static void addEmail(List<String> existing, Approver approver) {
        if (approver == null || text(approver.email).isEmpty()) return;
        for (String address : existing) {
            if (text(address).split("~", 2)[0].trim().equalsIgnoreCase(approver.email)) return;
        }
        // Existing BCO convention: suffix identifies the recipient in MNG without changing the body.
        existing.add(approver.email + "~" + approver.id);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    static final class Approver {
        String id, email, oldTargetEmail;
    }
}
