package com.ofss.digx.cz.bea.common.mtb;

import java.util.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.service.response.TransactionStatus;

/** A management-only scope. Does not add audit entries or change response objects. */
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
        if (failure instanceof com.ofss.digx.app.approval.exceptions.ApprovalRequiredException) {
            return result("PENDING_APPROVAL");
        }
        if (failure instanceof com.ofss.digx.infra.exceptions.Exception) {
            String code=((com.ofss.digx.infra.exceptions.Exception)failure).getErrorCode();
            if ("DIGX_APPROVAL_REQUIRED".equals(code)) result("PENDING_APPROVAL");
            else if(code!=null && code.matches("DIGX_[A-Z0-9_]{1,100}")) put("errorCode",code);
        }
        return this;
    }
    public HthMtbScope response(TransactionStatus status) {
        if(status==null)return this;
        if("DIGX_APPROVAL_REQUIRED".equals(status.getErrorCode())) result("PENDING_APPROVAL");
        else if(status.fetchLastKnownError()!=null) failure(status.fetchLastKnownError());
        else if(status.getReplyCode()!=0) result("FAILURE");
        return this;
    }
    public void close() {
        if(closed)return;closed=true;
        put("occurredAt",java.time.Instant.now().toString());
        HthMtbCollector.collect(service,values);
    }
}
