package com.ofss.digx.cz.bea.app.sms.service.user;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserAlertRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserManagementActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Feature 9 only: both contacts changed for a persisted HTH user. BCO routes remain untouched. */
public class HthContactUpdateNotification extends AbstractApplication {
    public static final String MARKER = "HTH_2110_CONTACT_UPDATED";
    public static final String ACTIVITY = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    static final String CONTACT = "HTH_2110_CONTACT_UPDATE";
    static final String EDIT = "HTH_2110_USER_EDIT";
    static final String WELCOME = "HTH_2110_MOBILE_WELCOME";
    static final String EMAIL = "HTH_2110_EMAIL_UPDATED";
    static final String OLD_MOBILE = "HTH_2110_OLD_MOBILE";
    private static final Logger LOG = Logger.getLogger(HthContactUpdateNotification.class.getName());

    static boolean applies(SessionContext context, UserAlertRequestDTO changes,
            com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData stored) {
        // checkAlerts flags mean unchanged, not changed.
        return context != null && stored != null && "OBDX_BU".equals(context.getTargetUnit())
                && ("HTH".equalsIgnoreCase(stored.getUserChannelType())
                    || "H2H".equalsIgnoreCase(stored.getUserChannelType()))
                && changes != null && !changes.getEmailId() && !changes.getMobNo();
    }

    static String[] capture(SessionContext context, UserExtensionDataDTO request, User oldUser,
            com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData stored) {
        if ("VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))) return null;
        String reference = text((String) com.ofss.digx.infra.thread.ThreadAttribute.get(
                com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO));
        if (reference.isEmpty() || !text(request.getUserID()).equals(stored.getUserID())
                || !text(request.getCdcNo()).equals(stored.getCdcNo())) {
            diagnostic("CAPTURE_SKIPPED", "ALL", "MissingOrMismatchedApprovalContext");
            return null;
        }
        // Strings copied before the managed User and extension are updated.
        return new String[] {reference, stored.getCdcNo(), stored.getUserID(), text(oldUser.getEmailId()),
                text(request.getUserDTO().getEmailId()), digits(oldUser.getMobileNumber()),
                digits(request.getUserDTO().getMobileNumber()), digits(stored.getMobileCode()),
                digits(request.getMobileCode())};
    }

    static void defer(String[] snapshot) {
        if (snapshot != null) {
            com.ofss.fc.infra.thread.ThreadAttribute.set(MARKER, snapshot.clone());
            diagnostic("DEFERRED", "ALL", "AwaitingApprovalCommit");
        }
    }

    /** Internal approval bridge, not a REST endpoint. Called after the SDK's business commit. */
    public void notifyAfterCommit(SessionContext context, String[] snapshot, String finalApprover) {
        try {
            if (snapshot == null || snapshot.length != 9 || context == null
                    || !"OBDX_BU".equals(context.getTargetUnit()) || text(finalApprover).isEmpty()) return;
            User current = readUser(snapshot[2]);
            // Verify the saved contacts. Never send success based only on the submitted request.
            if (current == null || !snapshot[4].equals(text(current.getEmailId()))
                    || !snapshot[6].equals(digits(current.getMobileNumber()))
                    || !snapshot[8].equals(country(snapshot[2]))) {
                diagnostic("COMMITTED_CONTACT_MISMATCH", "ALL", "NoNotification");
                return;
            }
            for (Notice notice : prepare(snapshot, finalApprover)) {
                try {
                    TransactionStatus status = publish(context, notice);
                    if (status == null || status.getReplyCode() != 0
                            || (status.getErrorCode() != null && !"0".equals(status.getErrorCode()))) {
                        diagnostic("REGISTER_FAILED", notice.event, "UnsuccessfulStatus");
                    } else {
                        diagnostic("REGISTERED", notice.event, "Success");
                    }
                } catch (java.lang.Exception failure) {
                    diagnostic("REGISTER_FAILED", notice.event, failure.getClass().getSimpleName());
                }
            }
        } catch (java.lang.Exception failure) {
            // The business commit has completed; notification failure must not falsify its result.
            diagnostic("POST_COMMIT_FAILED", "ALL", failure.getClass().getSimpleName());
        }
    }

    List<Notice> prepare(String[] s, String approverId) throws java.lang.Exception {
        List<Notice> notices = new ArrayList<>();
        Set<String> recipients = new HashSet<>();
        User approver = null;
        CZPartyPreferenceDTO company = null;
        String approverCountry = "";
        try { approver = readUser(approverId); }
        catch (java.lang.Exception failure) { diagnostic("CONTACT_UNAVAILABLE", "APPROVER", failure.getClass().getSimpleName()); }
        try { approverCountry = country(approverId); }
        catch (java.lang.Exception failure) { diagnostic("CONTACT_UNAVAILABLE", "APPROVER_SMS", failure.getClass().getSimpleName()); }
        try { company = readCompany(s[1]); }
        catch (java.lang.Exception failure) { diagnostic("CONTACT_UNAVAILABLE", "COMPANY", failure.getClass().getSimpleName()); }
        String companyEmail = company == null ? "" : text(company.getOfficeEmailId());
        String approverEmail = approver == null ? "" : text(approver.getEmailId());
        String approverMobile = approver == null ? "" : digits(approver.getMobileNumber());
        String companyName = company == null ? "" : CZCommonUtils.maskName(
                CZCommonUtils.sepChar(text(company.getCompanyName()), " "), 1);
        String date = formattedDate();
        String zhSubject = config("INFOUPDATE_EMAILMOB_UPDATE_MAILSUBJ");
        String zhContent = config("INFOUPDATE_EMAILMOB_UPDATE_MAILCONTENT");
        // Each Notice owns a fresh log/detail: asynchronous processing cannot see later mutations.
        add(notices, recipients, CONTACT, DestinationType.EMAIL, s[3], "", s[2], s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, CONTACT, DestinationType.EMAIL, s[4], "", s[2], s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, CONTACT, DestinationType.EMAIL, approverEmail, "", approverId, s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, CONTACT, DestinationType.EMAIL, companyEmail, "", "", s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, EDIT, DestinationType.EMAIL, approverEmail, "", approverId, s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, EDIT, DestinationType.EMAIL, companyEmail, "", "", s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, WELCOME, DestinationType.SMS, s[6], s[8], s[2], s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, EMAIL, DestinationType.SMS, s[6], s[8], s[2], s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, EDIT, DestinationType.SMS, approverMobile, approverCountry, approverId, s, companyName, date, zhSubject, zhContent);
        add(notices, recipients, OLD_MOBILE, DestinationType.SMS, s[5], s[7], s[2], s, companyName, date, zhSubject, zhContent);
        return notices;
    }

    private void add(List<Notice> notices, Set<String> recipients, String event, DestinationType destination,
            String address, String country, String recipientUser, String[] s, String company, String date,
            String zhSubject, String zhContent) {
        address = text(address);
        if (address.isEmpty() || (destination == DestinationType.SMS
                && (!country.matches("[0-9]{1,4}") || !address.matches("[0-9]{4,14}")))) {
            diagnostic("CONTACT_SKIPPED", event + "/" + destination, "MissingOrInvalidContact");
            return;
        }
        String identity = destination == DestinationType.EMAIL ? address.toLowerCase(Locale.ROOT) : country + ":" + address;
        // Only within this event/channel; A and B email and the two new-mobile SMS are distinct notices.
        if (!recipients.add(event + ":" + destination + ":" + identity)) return;
        ActivityLog log;
        String target = s[2].split("@", 2)[0];
        if (EDIT.equals(event)) {
            UserManagementActivityLogDTO edit = new UserManagementActivityLogDTO();
            edit.setUserId(recipientUser); edit.setUserNameId(target);
            edit.setCompName(company); edit.setUserSysDate(date);
            log = edit;
        } else {
            UserProfUpdateActivityLogDTO profile = new UserProfUpdateActivityLogDTO();
            profile.setUserId(recipientUser);
            profile.setProfileUser(destination == DestinationType.SMS
                    ? CZCommonUtils.maskName(CZCommonUtils.sepChar(target, " "), 1) : target);
            profile.setEmailId(s[4].replaceAll("(?<=.....).", "*"));
            profile.setEngMailSubj("Email Address and Mobile No. Update");
            profile.setZhMailSubj(zhSubject); profile.setEngMailContent("email address and mobile no.");
            profile.setZhMailContent(zhContent);
            log = profile;
        }
        log.setCustomerId(s[1]);
        if (!text(recipientUser).isEmpty()) {
            NameValuePairDTO recipient = new NameValuePairDTO();
            recipient.setName("recipientUserId"); recipient.setValue(recipientUser);
            log.setInputfacts(new NameValuePairDTO[] {recipient});
        }
        NotificationDetail detail = new NotificationDetail();
        detail.setRecipientId(s[1]); detail.setRecipientType(SubscriberType.EXTERNAL.toString());
        detail.setDestination(destination);
        // Existing SMSDispatcher supports number~country when the event is in its country skip-list.
        detail.setDispatchAddress(destination == DestinationType.SMS ? address + "~" + country : address);
        log.setNotificationDetails(new NotificationDetail[] {detail});
        notices.add(new Notice(event, log));
    }

    TransactionStatus publish(SessionContext context, Notice notice) throws com.ofss.digx.infra.exceptions.Exception {
        return super.registerActivityAndGenerateEvent(context, ACTIVITY, notice.event, new Date(), notice.log);
    }
    User readUser(String id) throws com.ofss.digx.infra.exceptions.Exception {
        UserKey key = new UserKey(); key.setUserId(id); return new User().read(key);
    }
    String country(String id) throws com.ofss.digx.infra.exceptions.Exception {
        UserExtensionDataKey key = new UserExtensionDataKey(); key.setUserExtensionKey(id);
        com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData row =
                new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData().read(key);
        return row == null ? "" : digits(row.getMobileCode());
    }
    CZPartyPreferenceDTO readCompany(String party) throws com.ofss.digx.infra.exceptions.Exception {
        IUserExtensionAdapter adapter = (IUserExtensionAdapter) AdapterFactoryConfigurator.getInstance()
                .getAdapterFactory(CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY)
                .getAdapter(CommonAdapterConstants.USER_EXTENSION_ADAPTER);
        return adapter.getPartyPreferences(party);
    }
    String config(String key) throws com.ofss.digx.infra.exceptions.Exception {
        ICustomConfigAdapter adapter = (ICustomConfigAdapter) AdapterFactoryConfigurator.getInstance()
                .getAdapterFactory(CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY)
                .getAdapter(CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
        return adapter.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, key, "");
    }
    String formattedDate() { return CZCommonUtils.getFormatDate(new Date()); }
    static final class Notice {
        final String event; final ActivityLog log;
        Notice(String event, ActivityLog log) { this.event = event; this.log = log; }
    }
    private static void diagnostic(String stage, String route, String result) {
        LOG.log(Level.INFO, "HTH_2110 stage={0} route={1} result={2}", new Object[] {stage, route, result});
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String digits(String value) { return text(value).replaceAll("[+\\s()-]", ""); }
}
