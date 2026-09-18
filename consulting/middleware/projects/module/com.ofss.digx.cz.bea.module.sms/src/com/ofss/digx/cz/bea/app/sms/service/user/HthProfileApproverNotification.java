package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserAlertRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Adds the final HTH approver to the existing BCO contact notification flow. */
final class HthProfileApproverNotification {
    private static final Logger LOG = Logger.getLogger(HthProfileApproverNotification.class.getName());
    private static final String ACTIVITY = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    static final String EMAIL_EVENT = "USER_EMAIL_ADDRESS_UPDATE";
    static final String MOBILE_EVENT = "USER_MOBILE_NUMBER_UPDATED_REMINDER";
    private HthProfileApproverNotification() { }

    static Approver resolve(SessionContext context, UserExtensionDataDTO request,
            UserAlertRequestDTO changes, UserExtensionData stored, User oldUser) {
        // Use the profile already loaded by update: ordinary BCO has no additional repository dependency.
        if (!isHth(context, stored) || (changes.getEmailId() && changes.getMobNo())) return null;
        Approver result = new Approver();
        result.oldTargetCountry = stored.getMobileCode();
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
            result.mobile = digits(user.getMobileNumber());
            UserExtensionDataKey extKey = new UserExtensionDataKey(); extKey.setUserExtensionKey(signer);
            UserExtensionData extension = new UserExtensionData().read(extKey);
            result.country = extension == null ? "" : digits(extension.getMobileCode());
        } catch (java.lang.Exception e) {
            // Never replace an unresolved final approver with the maker or background execution user.
            // Keep existing target/company notifications on their original path.
            LOG.log(Level.WARNING, "HTH_851 stage=APPROVER_RESOLUTION exception={0}", e.getClass().getSimpleName());
        }
        return result;
    }

    static boolean profileUpdateSucceeded(com.ofss.fc.service.response.TransactionStatus status,
            Boolean hthPinResetChanged) {
        // Keep ordinary BCO's existing decision; HTH must also accept SDK success code "0".
        if (hthPinResetChanged == null) return status != null && status.getErrorCode() == null;
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

    static List<Notification> sms(Approver approver, UserAlertRequestDTO changes,
            UserExtensionDataDTO request, String oldMobile) {
        List<Notification> messages = new ArrayList<>();
        if (approver == null || text(approver.id).isEmpty()
                || !text(approver.country).matches("[0-9]{1,4}")
                || !text(approver.mobile).matches("[0-9]{4,14}")) return messages;
        if (!changes.getMobNo() && !sameMobile(approver, approver.oldTargetCountry, oldMobile))
            messages.add(message(approver, request, MOBILE_EVENT));
        if (!changes.getEmailId() && !sameMobile(approver, request.getMobileCode(), request.getUserDTO().getMobileNumber()))
            messages.add(message(approver, request, EMAIL_EVENT));
        return messages;
    }

    private static Notification message(Approver approver,
            UserExtensionDataDTO request, String event) {
        UserProfUpdateActivityLogDTO log = new UserProfUpdateActivityLogDTO();
        // UserId routes SMS, while ProfileUser is the user described by the BCO template.
        log.setUserId(approver.id);
        // Existing SDK ExternalRecipientDerivationHelper forwards this to AlertRequest.userId.
        // This also selects the AP country code when the event is not in the dispatcher lookup list.
        NameValuePairDTO recipient = new NameValuePairDTO();
        recipient.setName("recipientUserId");
        recipient.setValue(approver.id);
        log.setInputfacts(new NameValuePairDTO[] {recipient});
        log.setProfileUser(text(request.getUserID()).split("@", 2)[0]);
        log.setCustomerId(request.getCdcNo());
        log.setEmailId(text(request.getUserDTO().getEmailId()).replaceAll("(?<=.....).", "*"));
        NotificationDetail detail = new NotificationDetail();
        detail.setRecipientId(request.getCdcNo());
        detail.setRecipientType(SubscriberType.EXTERNAL.toString());
        detail.setDestination(DestinationType.SMS);
        detail.setDispatchAddress(approver.mobile);
        log.setNotificationDetails(new NotificationDetail[] {detail});
        return new Notification(event, log);
    }

    private static boolean sameMobile(Approver approver, String country, String number) {
        return approver.country.equals(digits(country)) && approver.mobile.equals(digits(number));
    }
    private static String digits(String value) { return text(value).replaceAll("[+\\s()-]", ""); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    static final class Notification {
        final String eventId;
        final UserProfUpdateActivityLogDTO log;
        Notification(String eventId, UserProfUpdateActivityLogDTO log) {
            this.eventId = eventId;
            this.log = log;
        }
    }
    static final class Approver {
        String id, email, mobile, country, oldTargetCountry, oldTargetEmail;
    }
}
