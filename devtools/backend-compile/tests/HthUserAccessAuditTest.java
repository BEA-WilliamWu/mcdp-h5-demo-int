package com.ofss.digx.cz.bea.app.hosttohost.service;
import com.ofss.digx.cz.bea.common.hth.HthOnboardingAudit;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.infra.audit.dto.AuditDetailsDTO;
import java.util.*;
public final class HthUserAccessAuditTest {
    @SuppressWarnings("unchecked") public static void main(String[] args) {
        Map<String,Map<String,Object>> before=new TreeMap<String,Map<String,Object>>(),after=new TreeMap<String,Map<String,Object>>();
        for (int i=0;i<500;i++) {
            Map<String,Object> fields=new LinkedHashMap<String,Object>();fields.put("accountType","CSA");fields.put("maskedAccountNumber","****0001");fields.put("apiMasterId","API"+i);
            before.put("old"+i,fields);after.put("new"+i,fields);
        }
        try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(new SessionContext(),"HostToHostUserAccess.edit","ACCESS_EDIT")) {
            HthUserAccessAudit.changes(audit,before,after);audit.result("SUCCESS");
        }
        Stack<AuditDetailsDTO> stack=(Stack<AuditDetailsDTO>)com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
        Map<String,Object> envelope=(Map<String,Object>)stack.peek().getRequest(),summary=(Map<String,Object>)envelope.get("hthOnboarding");
        List<Map<String,Object>> changes=(List<Map<String,Object>>)summary.get("accessChanges");
        if (changes.size()!=1000 || !"REMOVE".equals(changes.get(0).get("change")) || !"ADD".equals(changes.get(999).get("change"))) throw new AssertionError("Large/same-count service replacement must not be truncated");
        HthOnboardingAudit.clearRequestStack();
        for (String operation:Arrays.asList("ACCESS_CREATE","ACCESS_DELETE","ACCESS_NO_CHANGE")) {
            try (HthOnboardingAudit.Entry audit=HthOnboardingAudit.begin(new SessionContext(),"HostToHostUserAccess.edit",operation)) {
                HthUserAccessAudit.changes(audit,"ACCESS_CREATE".equals(operation) ? Collections.emptyMap() : before,
                    "ACCESS_DELETE".equals(operation) ? Collections.emptyMap() : before);
                audit.result("SUCCESS");
            }
            stack=(Stack<AuditDetailsDTO>)com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
            summary=(Map<String,Object>)((Map<String,Object>)stack.peek().getRequest()).get("hthOnboarding");
            changes=(List<Map<String,Object>>)summary.get("accessChanges");
            if (changes.size() != ("ACCESS_NO_CHANGE".equals(operation) ? 0 : 500)) throw new AssertionError(operation);
            if (Boolean.TRUE.equals(summary.get("effectiveChange")) == "ACCESS_NO_CHANGE".equals(operation)) throw new AssertionError("Effective change flag");
            HthOnboardingAudit.clearRequestStack();
        }
        if (!"********1234".equals(HthUserAccessAudit.maskAccountNumber("123456781234"))
                || !"****".equals(HthUserAccessAudit.maskAccountNumber("1234"))) throw new AssertionError("Account masking");
        System.out.println("PASS: production access audit detects equal-size service replacement and keeps all 1000 account/service differences");
    }
}
