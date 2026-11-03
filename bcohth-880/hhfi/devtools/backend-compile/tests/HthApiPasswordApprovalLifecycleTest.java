package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import com.ofss.digx.app.approval.dto.transaction.TransactionActionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.app.messages.Status.ResultType;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.enumeration.approval.ApprovalAction;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionApprovalDetails;
import com.ofss.fc.app.context.SessionContext;

/** Production decision logic with SDK DTOs; transaction read and service dispatch are fixtures. */
public final class HthApiPasswordApprovalLifecycleTest {
    private static final class Probe extends HthApiPasswordApprovalLifecycle {
        Transaction saved;
        int reads, rejects, notifications;
        UserExtensionDataDTO rejected;
        String operator, transactionId;
        boolean failCleanup;
        @Override void notifyCode(SessionContext context, UserExtensionDataDTO user, String id) {
            notifications++; rejected = user; transactionId = id;
        }
        @Override Transaction readTransaction(String id) { reads++; return saved; }
        @Override void rejectCode(UserExtensionDataDTO user, String by, String id) {
            if (failCleanup) throw new IllegalStateException("INPUT_SENTINEL");
            rejects++; rejected = user; operator = by; transactionId = id;
        }
    }

    public static void main(String[] args) throws java.lang.Exception {
        SessionContext context = new SessionContext();
        context.setUserId("APPROVER@PARTY");
        TransactionActionDTO request = new TransactionActionDTO();
        request.setAction(ApprovalAction.REJECT);
        TransactionDTO requested = new TransactionDTO();
        requested.setTransactionName("MT_N_UUS"); requested.setTransactionId("TX-REJECT");
        UserExtensionDataDTO spoofed = user("HTH", "CLIENT-SUPPLIED-ID");
        requested.setTransactionSnapshot(spoofed);
        request.setTransactionDTO(requested);
        TransactionActionResponse response = new TransactionActionResponse();
        Status status = new Status(); status.setResult(ResultType.SUCCESSFUL); response.setStatus(status);
        Probe hook = new Probe();
        hook.saved = saved("MT_N_UUS", ApprovalAction.REJECT, user("HTH", "SAVED-DRAFT-ID"));
        hook.afterAction(context, request, response);
        check(hook.rejects == 1 && "SAVED-DRAFT-ID".equals(hook.rejected.getHthApiPasswordCodeId()),
                "Rejection must use the server snapshot, not client-supplied Code ID");
        check("APPROVER@PARTY".equals(hook.operator) && "TX-REJECT".equals(hook.transactionId),
                "Cleanup is bound to the authenticated operator and rejected transaction");
        hook.saved = saved("MT_N_CUS", ApprovalAction.REJECT, user("HTH", "CREATE-DRAFT"));
        requested.setTransactionName("MT_N_CUS");
        hook.afterAction(context, request, response);
        check(hook.rejects == 2, "HTH user create rejection also cleans its own draft");
        for (String channel : new String[] {"BCO", "INT", null}) {
            hook.saved = saved("MT_N_CUS", ApprovalAction.REJECT, user(channel, "BCO-ID"));
            hook.afterAction(context, request, response);
        }
        hook.saved = saved("MT_N_CUS", ApprovalAction.REJECT, user("HTH", null));
        hook.afterAction(context, request, response);
        hook.saved = saved("MT_N_CUS", ApprovalAction.APPROVE, user("HTH", "DRAFT"));
        hook.afterAction(context, request, response);
        hook.saved = saved("UNRELATED", ApprovalAction.REJECT, user("HTH", "DRAFT"));
        hook.afterAction(context, request, response);
        hook.saved = null; hook.afterAction(context, request, response);
        check(hook.rejects == 2, "BCO, edits without regeneration, unrelated or unconfirmed rejection do not clean Code");
        int reads = hook.reads;
        hook.saved = saved("MT_N_CUS", ApprovalAction.REJECT, user("HTH", "DRAFT"));
        status.setResult(ResultType.FAILED); hook.afterAction(context, request, response);
        status.setResult(ResultType.SUCCESSFUL);
        request.setAction(ApprovalAction.APPROVE); hook.afterAction(context, request, response);
        request.setAction(ApprovalAction.REJECT);
        requested.setTransactionName("UNRELATED"); hook.afterAction(context, request, response);
        hook.afterAction(null, request, response);
        hook.afterAction(context, null, response);
        hook.afterAction(context, request, null);
        check(hook.reads == reads && hook.rejects == 2, "Failed action, approval and unrelated tasks are gated before repository work");
        requested.setTransactionName("MT_N_CUS");
        hook.saved.getApprovalDetails().setStatus(ApprovalStatus.PENDING_APPROVAL);
        hook.afterAction(context, request, response);
        check(hook.rejects == 2, "An uncommitted or partial rejection cannot clean Code");
        hook.saved.getApprovalDetails().setStatus(ApprovalStatus.REJECTED);
        hook.failCleanup = true;
        final StringBuilder log = new StringBuilder();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HthApiPasswordApprovalLifecycle.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) {
                check(record.getThrown() == null, "No input-bearing exceptions attached to diagnostics");
                log.append(java.text.MessageFormat.format(record.getMessage(), record.getParameters()));
            }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try { hook.afterAction(context, request, response); } finally { logger.removeHandler(handler); }
        check(status.getResult() == ResultType.SUCCESSFUL, "Cleanup failure must not alter an already committed rejection result");
        check(log.toString().contains("REJECT_CLEANUP_FAILED") && !log.toString().contains("INPUT_SENTINEL"),
                "Cleanup diagnostic records only phase, transaction and exception type");
        approvalCases();
        System.out.println("PASS: real approval SDK DTOs; HTH create/edit rejection gates; server snapshot ownership; authenticated approver; ordinary BCO unchanged");
    }

    private static final String MARKER = "HTH_880_CODE_ACTIVATED";
    private static void mark(String code, String tx) {
        com.ofss.fc.infra.thread.ThreadAttribute.set(MARKER, new String[] {code, tx});
    }
    private static void approvalCases() throws java.lang.Exception {
        SessionContext context = new SessionContext();
        context.setUserId("FINAL-APPROVER@PARTY"); context.setTargetUnit("OBDX_BU");
        TransactionActionDTO request = new TransactionActionDTO(); request.setAction(ApprovalAction.APPROVE);
        TransactionDTO requested = new TransactionDTO(); requested.setTransactionId("TX-APPROVED");
        requested.setTransactionSnapshot(user("HTH", "UNTRUSTED-CLIENT-CODE")); request.setTransactionDTO(requested);
        TransactionActionResponse response = new TransactionActionResponse();
        Status status = new Status(); status.setResult(ResultType.SUCCESSFUL); response.setStatus(status);
        Probe hook = new Probe();
        for (String task : new String[] {"MT_N_CUS", "MT_N_UUS"}) {
            requested.setTransactionName(task);
            hook.saved = saved(task, ApprovalAction.APPROVE, user("HTH", "COMMITTED-CODE"));
            mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
            check("COMMITTED-CODE".equals(hook.rejected.getHthApiPasswordCodeId()), "Notification uses server snapshot");
            check("TX-APPROVED".equals(hook.transactionId), "Notification bound to original approval transaction");
        }
        check(hook.notifications == 2, "Create and edit both notify after final approval");
        hook.afterAction(context, request, response);
        check(hook.notifications == 2, "Consumed marker prevents duplicate callback");
        // Approval can be APPROVED while business execution is FAIL or still PROCESSING.
        for (com.ofss.digx.enumeration.approval.ProcessingStatus state :
                new com.ofss.digx.enumeration.approval.ProcessingStatus[] {
                    com.ofss.digx.enumeration.approval.ProcessingStatus.FAIL,
                    com.ofss.digx.enumeration.approval.ProcessingStatus.PROCESSING}) {
            hook.saved.getProcessingDetails().setStatus(state);
            mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        }
        hook.saved.getProcessingDetails().setStatus(com.ofss.digx.enumeration.approval.ProcessingStatus.SUCCESS);
        hook.saved.getApprovalDetails().setStatus(ApprovalStatus.PENDING_APPROVAL);
        mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        hook.saved.getApprovalDetails().setStatus(ApprovalStatus.APPROVED);
        status.setResult(ResultType.FAILED);
        mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        status.setResult(ResultType.SUCCESSFUL);
        com.ofss.digx.app.messages.Message error = new com.ofss.digx.app.messages.Message();
        error.setType(com.ofss.digx.app.messages.Message.MessageType.ERROR); status.setMessage(error);
        mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        status.setMessage(null);
        mark("OTHER-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        mark("COMMITTED-CODE", "OTHER-TX"); hook.afterAction(context, request, response);
        for (String channel : new String[] {"BCO", "INT", null}) {
            hook.saved.setTransactionSnapshot(user(channel, "COMMITTED-CODE"));
            mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        }
        hook.saved.setTransactionSnapshot(user("HTH", "COMMITTED-CODE"));
        context.setTargetUnit("BM");
        mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, response);
        context.setTargetUnit("OBDX_BU");
        mark("COMMITTED-CODE", "TX-APPROVED"); hook.afterAction(context, request, null);
        // Even when all other fields match, a rolled-back activation/no marker cannot notify.
        hook.afterAction(context, request, response);
        check(hook.notifications == 2, "No success email on failure, pending, wrong owner/transaction, BCO, rollback or replay");
        check(com.ofss.fc.infra.thread.ThreadAttribute.get(MARKER) == null, "All exit paths consume marker");
        System.out.println("PASS: 880 create/edit final approval, execution SUCCESS, no Code emails on failure/partial/replay, server ownership and BCO exclusion");
    }

    private static UserExtensionDataDTO user(String channel, String codeId) {
        UserExtensionDataDTO user = new UserExtensionDataDTO();
        user.setUserChannelType(channel); user.setHthApiPasswordCodeId(codeId);
        user.setCdcNo("PARTY"); user.setUserID("USER@PARTY");
        return user;
    }
    private static Transaction saved(String task, ApprovalAction action, UserExtensionDataDTO user) {
        Transaction stored = new Transaction(); stored.setTransactionName(task);
        TransactionApprovalDetails approval = new TransactionApprovalDetails(); approval.setAction(action);
        approval.setStatus(action == ApprovalAction.REJECT ? ApprovalStatus.REJECTED : ApprovalStatus.APPROVED);
        stored.setApprovalDetails(approval); stored.setTransactionSnapshot(user);
        com.ofss.digx.framework.domain.transaction.ProcessingDetails processing =
                new com.ofss.digx.framework.domain.transaction.ProcessingDetails();
        processing.setStatus(com.ofss.digx.enumeration.approval.ProcessingStatus.SUCCESS);
        stored.setProcessingDetails(processing);
        return stored;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
