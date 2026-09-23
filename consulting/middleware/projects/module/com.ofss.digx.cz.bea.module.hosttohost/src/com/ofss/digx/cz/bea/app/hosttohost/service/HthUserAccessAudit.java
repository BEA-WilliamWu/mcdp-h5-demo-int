package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.common.hth.HthOnboardingAudit;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessAccountRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessAccountApiRepository;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.*;

/** Audit effective account/service differences, including deletion, independently of notification flags. */
final class HthUserAccessAudit {
    private HthUserAccessAudit() { }
    static Map<String,Map<String,Object>> effective(HostToHostUserAccessDTO request) throws Exception {
        Map<String,Map<String,Object>> result=new TreeMap<String,Map<String,Object>>();
        List<HthUserAccessAccount> accounts=HthUserAccessAccountRepository.getInstance().listByContext(
            request.getPartyId(),request.getCloseId(),request.getAccessPartyId(),request.getLinkageType());
        if (accounts != null) for (HthUserAccessAccount account:accounts) {
            if (account == null || !"A".equals(account.getObjectStatus())) continue;
            String identity=account.getAccountType()+":"+account.getAccountNumber();
            Map<String,Object> fields=new LinkedHashMap<String,Object>();
            fields.put("accountType",account.getAccountType());
            fields.put("maskedAccountNumber",maskAccountNumber(account.getAccountNumber()));
            result.put(identity,fields);
            List<HthUserAccessAccountApi> services=HthUserAccessAccountApiRepository.getInstance().listByAccountId(account.getKey().getId());
            if (services != null) for (HthUserAccessAccountApi service:services) if (service != null && "A".equals(service.getObjectStatus())) {
                Map<String,Object> api=new LinkedHashMap<String,Object>(fields);
                api.put("apiMasterId",service.getApiMasterId()); result.put(identity+":"+service.getApiMasterId(),api);
            }
        }
        return result;
    }
    static String maskAccountNumber(String number) {
        if (number == null || number.trim().isEmpty()) return null;
        number=number.trim();
        if (number.length() <= 4) return "****";
        StringBuilder masked=new StringBuilder();
        for (int i=0;i<number.length()-4;i++) masked.append('*');
        return masked.append(number.substring(number.length()-4)).toString();
    }
    static void changes(HthOnboardingAudit.Entry audit, Map<String,Map<String,Object>> before,
            Map<String,Map<String,Object>> after) {
        List<Map<String,Object>> changes=new ArrayList<Map<String,Object>>();
        append(changes,before,after,"REMOVE"); append(changes,after,before,"ADD");
        audit.put("accessChanges",changes).put("effectiveChange",Boolean.valueOf(!changes.isEmpty()));
    }
    private static void append(List<Map<String,Object>> changes, Map<String,Map<String,Object>> first,
            Map<String,Map<String,Object>> second, String action) {
        for (Map.Entry<String,Map<String,Object>> row:first.entrySet()) if (!second.containsKey(row.getKey())) {
            Map<String,Object> change=new LinkedHashMap<String,Object>(row.getValue()); change.put("change",action); changes.add(change);
        }
    }
}
