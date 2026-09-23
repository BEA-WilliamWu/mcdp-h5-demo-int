package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import java.util.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.service.response.TransactionStatus;

/** Independent HTH operation scope. Does not add audit entries or change response objects. */
public final class HthMtbScope implements AutoCloseable {
    private final Map<String,Object> values=new LinkedHashMap<String,Object>();
    private final String service;
    private boolean closed;
    public HthMtbScope(SessionContext context,String service,String activity) {
        this.service=service; put("operation",activity); put("businessOutcome","FAILURE");
        put("actorUserId",context==null?null:context.getUserId());
    }
    public HthMtbScope put(String key,Object value) { if(value!=null)values.put(key,value);return this; }
    public HthMtbScope result(String result) { return put("businessOutcome",result); }
    public HthMtbScope failure(Throwable failure) {
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
    public HthMtbScope response(Object response) {
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
    public static String fullUser(String user, String party) {
        return user == null || user.contains("@") || party == null ? user : user + "@" + party;
    }
    public void close() {
        if(closed)return;closed=true;
        put("occurredAt",java.time.Instant.now().toString());
        HthMtbCollector.collect(service,values);
    }
}
