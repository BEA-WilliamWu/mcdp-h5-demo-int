package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import com.ofss.digx.app.approval.dto.transaction.TransactionActionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.app.messages.Status.ResultType;
import com.ofss.digx.enumeration.approval.ApprovalAction;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** HTH-only callback after the SDK has committed the user-maintenance rejection. */
class HthApiPasswordApprovalLifecycle {
    private static final Logger LOGGER = Logger.getLogger(HthApiPasswordApprovalLifecycle.class.getName());
    void afterAction(SessionContext context, TransactionActionDTO request,
            TransactionActionResponse response) throws Exception {
        if (context == null || request == null || request.getAction() != ApprovalAction.REJECT
                || request.getTransactionDTO() == null || response == null
                || (response.getStatus() != null && response.getStatus().getResult() == ResultType.FAILED)) {
            return;
        }
        TransactionDTO requested = request.getTransactionDTO();
        if (!isUserTask(requested.getTransactionName()) || requested.getTransactionId() == null) {
            return;
        }
        // The saved transaction establishes both the outcome and the Code owner. Do not trust
        // a client-provided snapshot, or invalidate the latest Code belonging to another request.
        try {
            Transaction stored = readTransaction(requested.getTransactionId());
            if (stored == null || !isUserTask(stored.getTransactionName())
                    || stored.getApprovalDetails() == null
                    || stored.getApprovalDetails().getAction() != ApprovalAction.REJECT
                    || stored.getApprovalDetails().getStatus() != ApprovalStatus.REJECTED
                    || !(stored.getTransactionSnapshot() instanceof UserExtensionDataDTO)) {
                return;
            }
            UserExtensionDataDTO user = (UserExtensionDataDTO) stored.getTransactionSnapshot();
            if (!"HTH".equalsIgnoreCase(user.getUserChannelType())
                    || user.getHthApiPasswordCodeId() == null || user.getHthApiPasswordCodeId().trim().isEmpty()) {
                return;
            }
            rejectCode(user, context.getUserId(), requested.getTransactionId());
        } catch (java.lang.Exception failure) {
            // Approval is already committed. Do not turn a completed rejection into a false
            // failure response. PENDING is unusable and excluded from ordinary display even
            // when cleanup fails; this conditional cleanup is safe to repeat.
            LOGGER.log(Level.WARNING,
                    "HTH_API_PASSWORD code: stage=REJECT_CLEANUP_FAILED, transactionId={0}, exceptionType={1}",
                    new Object[] {requested.getTransactionId(), failure.getClass().getName()});
        }
    }

    Transaction readTransaction(String transactionId) throws Exception {
        TransactionKey key = new TransactionKey();
        key.setId(transactionId);
        return new Transaction().read(key);
    }

    void rejectCode(UserExtensionDataDTO user, String operator, String transactionId) throws Exception {
        try {
            // Approval/sms are built before hosttohost. Match the existing approval activation bridge.
            Class<?> service = Class.forName("com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword");
            service.getMethod("rejectOnUserApproval", String.class, String.class, String.class,
                    String.class, String.class).invoke(service.getDeclaredConstructor().newInstance(),
                    user.getHthApiPasswordCodeId(), operator, user.getCdcNo(), user.getUserID(), transactionId);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception) {
                throw (Exception) failure.getCause();
            }
            throw new Exception(failure);
        } catch (java.lang.Exception failure) {
            throw new Exception(failure);
        }
    }

    private boolean isUserTask(String taskId) {
        return "MT_N_CUS".equals(taskId) || "MT_N_UUS".equals(taskId);
    }
}
