package com.ofss.digx.cz.bea.app.sms.service.user;

import java.util.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.service.response.TransactionStatus;

/** SMS-local HTH capture context. BCO returns before Adapter lookup. Does not add audit entries or change response objects. */
public final class HthUserCRMScope implements AutoCloseable {
    private final Map<String,Object> values=new LinkedHashMap<String,Object>();
    private final String service;
    private boolean closed;
    public HthUserCRMScope(SessionContext context,String service,String activity) {
        this.service=service; put("operation",activity); put("businessOutcome","FAILURE");
        put("actorUserId",context==null?null:context.getUserId());
    }
    public HthUserCRMScope put(String key,Object value) { if(value!=null)values.put(key,value);return this; }
    public HthUserCRMScope result(String result) { return put("businessOutcome",result); }
    public HthUserCRMScope failure(Throwable failure) {
        result("FAILURE");
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof com.ofss.digx.app.approval.exceptions.ApprovalRequiredException)
                return result("PENDING_APPROVAL");
            if (failure instanceof com.ofss.digx.infra.exceptions.Exception) {
                String code = ((com.ofss.digx.infra.exceptions.Exception) failure).getErrorCode();
                if ("DIGX_APPROVAL_REQUIRED".equals(code)) return result("PENDING_APPROVAL");
                if (code != null && code.matches("DIGX_[A-Z0-9_]{1,100}")) put("errorCode", code);
            }
        }
        return this;
    }
    public HthUserCRMScope response(Object response) {
        if (response instanceof TransactionStatus) {
            TransactionStatus status = (TransactionStatus) response;
            put("referenceNumber", status.getExternalReferenceNo() == null
                ? status.getInternalReferenceNumber() : status.getExternalReferenceNo());
            if (status.fetchLastKnownError() != null) failure(status.fetchLastKnownError());
            else if ("DIGX_APPROVAL_REQUIRED".equals(status.getErrorCode())) result("PENDING_APPROVAL");
            else if (status.getReplyCode() != 0) result("FAILURE");
        } else if (response instanceof com.ofss.digx.service.response.BaseResponseObject) {
            com.ofss.digx.app.messages.Status status =
                ((com.ofss.digx.service.response.BaseResponseObject) response).getStatus();
            if (status == null) return this;
            put("referenceNumber", status.getReferenceNumber());
            String code = status.getMessage() == null ? null : status.getMessage().getCode();
            if (status.getLastKnownError() != null) failure(status.getLastKnownError());
            else if ("DIGX_APPROVAL_REQUIRED".equals(code) || "ACCEPTED".equals(String.valueOf(status.getResult())))
                result("PENDING_APPROVAL");
            else if ("FAILED".equals(String.valueOf(status.getResult())) || "FAILURE".equals(String.valueOf(status.getResult()))
                    || (status.getMessage() != null && "ERROR".equals(String.valueOf(status.getMessage().getType())))) {
                result("FAILURE");
                if (code != null && code.matches("DIGX_[A-Z0-9_]{1,100}")) put("errorCode", code);
            }
        }
        return this;
    }
    public HthUserCRMScope channel(String oldChannel, String newChannel) {
        return put("oldUserChannelType", oldChannel).put("newUserChannelType", newChannel);
    }
    public static String fullUser(String user, String party) {
        return user == null || user.contains("@") || party == null ? user : user + "@" + party;
    }
    public void close() {
        if(closed)return;closed=true;
        put("occurredAt",java.time.Instant.now().toString());
        if (!com.ofss.digx.cz.bea.common.hth.HthChannelSupport.isHthChange(
                values.get("oldUserChannelType"), values.get("newUserChannelType"))) return;
        try {
            com.ofss.digx.app.adapter.IAdapterFactory factory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
                .getInstance().getAdapterFactory(com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter.FACTORY);
            com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter adapter =
                (com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter) factory.getAdapter(com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter.ADAPTER);
            adapter.collect(new com.ofss.digx.cz.bea.common.hth.HthCRMInputData(service, values));
        } catch (java.lang.Exception | LinkageError failure) {
            java.util.logging.Logger.getLogger(HthUserCRMScope.class.getName()).log(java.util.logging.Level.WARNING,
                "HTH_CRM stage=USER_CAPTURE_FAILED, exceptionType={0}", failure.getClass().getName());
        }
    }
}
