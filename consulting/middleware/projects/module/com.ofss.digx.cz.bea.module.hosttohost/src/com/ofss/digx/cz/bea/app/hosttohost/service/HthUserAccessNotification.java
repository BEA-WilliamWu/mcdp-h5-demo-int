package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserManagementActivityLogDTO;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
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

/** BCOH2H-1216: explicit HTH recipients, using the existing BCO template DTO and Alert pipeline. */
final class HthUserAccessNotification extends AbstractApplication {
    static final String EVENT = "HTH_USER_ACCOUNT_ACCESS_UPDATE";
    private static final String SERVICE = HostToHostUserAccess.class.getName();
    private static final Logger LOG = Logger.getLogger(HthUserAccessNotification.class.getName());

    void notifyApproved(SessionContext context, HostToHostUserAccessDTO request,
            String activity, String reference) {
        for (UserManagementActivityLogDTO log : prepare(context, request, activity, reference)) {
            try {
                TransactionStatus status = super.registerActivityAndGenerateEvent(
                        context, activity, EVENT, new Date(), log);
                if (status == null || status.getReplyCode() != 0) {
                    diagnostic(reference, "REGISTER", "RECIPIENT", "UnsuccessfulStatus");
                }
            } catch (java.lang.Exception e) {
                diagnostic(reference, "REGISTER", "RECIPIENT", e.getClass().getSimpleName());
            }
        }
    }

    List<UserManagementActivityLogDTO> prepare(SessionContext context,
            HostToHostUserAccessDTO request, String activity, String reference) {
        List<UserManagementActivityLogDTO> messages = new ArrayList<>();
        if (context == null || request == null || !"OBDX_BU".equals(context.getTargetUnit())
                || "VALIDATE".equals(String.valueOf(context.getServiceCallContextType()))
                || !(SERVICE.concat(".submit").equals(activity) || SERVICE.concat(".edit").equals(activity))) {
            return messages;
        }
        String party = text(request.getPartyId());
        String targetId = text(request.getCloseId());
        if (party.isEmpty() || targetId.isEmpty() || text(reference).isEmpty()) {
            diagnostic(reference, "CONTEXT", "ALL", "MissingContext");
            return messages;
        }
        // closeId is validated by HostToHostUserAccess before this helper is called.
        if (!targetId.contains("@")) targetId += "@" + party;
        String finalApprover;
        try {
            TransactionKey key = new TransactionKey();
            key.setId(reference);
            Transaction transaction = new Transaction().read(key);
            if (transaction == null || transaction.getApprovalDetails() == null
                    || !"APPROVED".equals(String.valueOf(transaction.getApprovalDetails().getStatus()))
                    || !activity.equals(transaction.getServiceId())) {
                diagnostic(reference, "APPROVAL", "ALL", "UnconfirmedApproval");
                return messages;
            }
            finalApprover = finalSigner(transaction.getApprovalDetails().getSignedBy());
        } catch (java.lang.Exception e) {
            diagnostic(reference, "APPROVAL", "ALL", e.getClass().getSimpleName());
            return messages;
        }

        CZPartyPreferenceDTO company = null;
        try {
            IUserExtensionAdapter adapter = (IUserExtensionAdapter) AdapterFactoryConfigurator.getInstance()
                    .getAdapterFactory(CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY)
                    .getAdapter(CommonAdapterConstants.USER_EXTENSION_ADAPTER);
            company = adapter.getPartyPreferences(party);
        } catch (java.lang.Exception e) {
            diagnostic(reference, "CONTACT", "COMPANY", e.getClass().getSimpleName());
        }
        try {
            String date = CZCommonUtils.getFormatDate(new Date());
            String companyName = company == null ? "" : CZCommonUtils.maskName(
                    CZCommonUtils.sepChar(text(company.getCompanyName()), " "), 1);
            Set<String> addresses = new HashSet<>();
            appendUser(messages, addresses, targetId, "TARGET", party, targetId, companyName, date, reference);
            if (finalApprover.isEmpty()) {
                diagnostic(reference, "APPROVAL", "FINAL_APPROVER", "MissingFinalSigner");
            } else {
                appendUser(messages, addresses, finalApprover, "FINAL_APPROVER", party,
                        targetId, companyName, date, reference);
            }
            String email = company == null ? "" : text(company.getOfficeEmailId());
            if (!email.isEmpty()) {
                append(messages, addresses, DestinationType.EMAIL, email, "", "", party,
                        targetId, companyName, date);
            } else {
                diagnostic(reference, "EMAIL", "COMPANY", "MissingContact");
            }
        } catch (java.lang.Exception e) {
            diagnostic(reference, "PREPARE", "ALL", e.getClass().getSimpleName());
        }
        return messages;
    }

    private void appendUser(List<UserManagementActivityLogDTO> messages, Set<String> addresses,
            String userId, String role, String party, String targetId, String companyName,
            String date, String reference) {
        User user;
        try {
            UserKey key = new UserKey();
            key.setUserId(userId);
            user = new User().read(key);
            if (user == null) throw new IllegalStateException();
        } catch (java.lang.Exception e) {
            diagnostic(reference, "CONTACT", role, e.getClass().getSimpleName());
            return;
        }
        String email = text(user.getEmailId());
        if (!email.isEmpty()) {
            append(messages, addresses, DestinationType.EMAIL, email, "", userId, party,
                    targetId, companyName, date);
        } else {
            diagnostic(reference, "EMAIL", role, "MissingContact");
        }
        try {
            UserExtensionDataKey key = new UserExtensionDataKey();
            key.setUserExtensionKey(userId);
            UserExtensionData extension = new UserExtensionData().read(key);
            String country = extension == null ? "" : digits(extension.getMobileCode());
            String mobile = digits(user.getMobileNumber());
            if (!country.matches("[0-9]{1,4}") || !mobile.matches("[0-9]{4,14}")) {
                diagnostic(reference, "SMS", role, "MissingOrInvalidContact");
                return;
            }
            append(messages, addresses, DestinationType.SMS, mobile, country, userId, party,
                    targetId, companyName, date);
        } catch (java.lang.Exception e) {
            // An absent mobile/country must not discard this recipient's valid email.
            diagnostic(reference, "SMS", role, e.getClass().getSimpleName());
        }
    }

    private void append(List<UserManagementActivityLogDTO> messages, Set<String> addresses,
            DestinationType destination, String address, String country, String recipientUser,
            String party, String targetId, String companyName, String date) {
        String key = destination == DestinationType.EMAIL
                ? "EMAIL:" + address.toLowerCase(Locale.ROOT) : "SMS:" + country + ":" + address;
        if (!addresses.add(key)) return;
        UserManagementActivityLogDTO log = new UserManagementActivityLogDTO();
        log.setCustomerId(party);
        log.setUserNameId(targetId.split("@", 2)[0]);
        log.setCompName(companyName);
        log.setUserSysDate(date);
        // UserNameId remains the subject of the BCO message; UserId is the delivery identity.
        log.setUserId(recipientUser);
        if (!recipientUser.isEmpty()) {
            NameValuePairDTO recipient = new NameValuePairDTO();
            recipient.setName("recipientUserId");
            recipient.setValue(recipientUser);
            log.setInputfacts(new NameValuePairDTO[] {recipient});
        }
        NotificationDetail detail = new NotificationDetail();
        detail.setRecipientId(party);
        detail.setRecipientType(SubscriberType.EXTERNAL.toString());
        detail.setDestination(destination);
        detail.setDispatchAddress(address);
        log.setNotificationDetails(new NotificationDetail[] {detail});
        messages.add(log);
    }

    private static String finalSigner(String signedBy) {
        String last = "";
        int count = 0;
        for (String signer : text(signedBy).split("~")) {
            if (!signer.trim().isEmpty()) { last = signer.trim(); count++; }
        }
        return count > 1 ? last : ""; // First entry is the maker, as in the BCO approval record.
    }

    private static void diagnostic(String reference, String stage, String role, String exception) {
        LOG.log(Level.WARNING, "HTH_1216 reference={0} stage={1} role={2} exception={3}",
                new Object[] {text(reference).replaceAll("[\\r\\n]", ""), stage, role, exception});
    }

    private static String digits(String value) { return text(value).replaceAll("[+\\s()-]", ""); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
