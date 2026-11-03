package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import com.ofss.digx.app.approval.dto.transaction.TransactionActionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionResponse;
import com.ofss.digx.app.messages.Status.ResultType;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.enumeration.approval.ApprovalAction;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.approval.ProcessingStatus;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.fc.app.context.SessionContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/** BCOH2H-2110 bridge. The SDK calls postPerformAction after business execution commits. */
class HthContactUpdateApprovalLifecycle {
    private static final String MARKER = "HTH_2110_CONTACT_UPDATED";
    private static final String SERVICE = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    private static final Logger LOG = Logger.getLogger(HthContactUpdateApprovalLifecycle.class.getName());

    void afterAction(SessionContext context, TransactionActionDTO request, TransactionActionResponse response) {
        Object pending = com.ofss.fc.infra.thread.ThreadAttribute.clear(MARKER);
        if (!(pending instanceof String[]) || context == null || !"OBDX_BU".equals(context.getTargetUnit())
                || request == null || request.getAction() != ApprovalAction.APPROVE
                || request.getTransactionDTO() == null || response == null || response.getStatus() == null
                || response.getStatus().getResult() != ResultType.SUCCESSFUL
                || response.getStatus().getLastKnownError() != null
                || (response.getStatus().getMessage() != null
                    && "ERROR".equals(String.valueOf(response.getStatus().getMessage().getType())))) return;
        String[] s = (String[]) pending;
        if (s.length != 9 || !"MT_N_UUS".equals(request.getTransactionDTO().getTransactionName())
                || !s[0].equals(request.getTransactionDTO().getTransactionId())) return;
        try {
            Transaction stored = readTransaction(s[0]);
            if (stored == null || !SERVICE.equals(stored.getServiceId())
                    || !"MT_N_UUS".equals(stored.getTransactionName()) || stored.getApprovalDetails() == null
                    || stored.getApprovalDetails().getAction() != ApprovalAction.APPROVE
                    || stored.getApprovalDetails().getStatus() != ApprovalStatus.APPROVED
                    || stored.getProcessingDetails() == null
                    || stored.getProcessingDetails().getStatus() != ProcessingStatus.SUCCESS
                    || !(stored.getTransactionSnapshot() instanceof UserExtensionDataDTO)) return;
            UserExtensionDataDTO user = (UserExtensionDataDTO) stored.getTransactionSnapshot();
            // The trusted marker was made from the persisted HTH profile. Bind it to the server snapshot.
            if (!s[1].equals(user.getCdcNo()) || !s[2].equals(user.getUserID())) return;
            String approver = finalSigner(stored.getApprovalDetails().getSignedBy());
            if (approver.isEmpty()) throw new IllegalStateException("Final approver unavailable");
            notifyContacts(context, s, approver);
        } catch (java.lang.Exception failure) {
            LOG.log(Level.WARNING, "HTH_2110 stage=APPROVAL_CALLBACK_FAILED exceptionType={0}",
                    failure.getClass().getName());
        }
    }

    Transaction readTransaction(String id) throws com.ofss.digx.infra.exceptions.Exception {
        TransactionKey key = new TransactionKey(); key.setId(id); return new Transaction().read(key);
    }

    void notifyContacts(SessionContext context, String[] snapshot, String approver) throws java.lang.Exception {
        // Keep module build order unchanged: approval is built before the SMS implementation module.
        Class<?> helper = Class.forName("com.ofss.digx.cz.bea.app.sms.service.user.HthContactUpdateNotification");
        helper.getMethod("notifyAfterCommit", SessionContext.class, String[].class, String.class)
                .invoke(helper.getDeclaredConstructor().newInstance(), context, snapshot, approver);
    }

    private static String finalSigner(String signedBy) {
        String last = ""; int count = 0;
        if (signedBy != null) for (String signer : signedBy.split("~")) {
            if (!signer.trim().isEmpty()) { last = signer.trim(); count++; }
        }
        return count > 1 ? last : ""; // First signer is the maker.
    }
}
