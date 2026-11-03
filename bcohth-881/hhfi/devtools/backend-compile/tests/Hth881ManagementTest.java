package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiAuthorizationDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApi;
import fixture.Bank;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class Hth881ManagementTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void master(String id, String code, String name) {
        HthApiMaster item = new HthApiMaster();
        HthApiMasterKey key = new HthApiMasterKey();
        key.setId(id);
        item.setKey(key);
        item.setApiCode(code);
        item.setApiName(name);
        Bank.masters.put(id, item);
    }

    private static HthManagementApi grant(String company, String master, String status) {
        HthManagementApi item = new HthManagementApi();
        item.setHthManagementId(company);
        item.setApiMasterId(master);
        item.setObjectStatus(status);
        Bank.links.add(item);
        return item;
    }

    private static HostToHostManagementDTO request(String... codes) {
        HostToHostManagementDTO result = new HostToHostManagementDTO();
        java.util.ArrayList<HostToHostApiAuthorizationDTO> apis = new java.util.ArrayList<>();
        for (String code : codes) {
            HostToHostApiAuthorizationDTO item = new HostToHostApiAuthorizationDTO();
            item.setApiCode(code);
            item.setSelected(Boolean.TRUE);
            apis.add(item);
        }
        result.setApiAuthorizations(apis);
        return result;
    }

    public static void main(String[] args) throws Exception {
        Hth881Harness service = new Hth881Harness();
        master("audit", "AUDIT_LOG", "Translated / renamed audit service");
        master("balance", "BALANCE_INQUIRY", "Balance");
        master("activity", "ACCOUNT_ACTIVITY", "Activity");
        List<HostToHostApiAuthorizationDTO> catalog = service.catalog(new HashSet<>(Arrays.asList("AUDIT_LOG", "BALANCE_INQUIRY")));
        check(catalog.size() == 2, "Hide Audit Log using code, independent of display name");
        check("BALANCE_INQUIRY".equals(catalog.get(0).getApiCode()) && catalog.get(0).getSelected(), "Keep ordinary selected API");
        check(!catalog.get(1).getSelected(), "Keep ordinary unselected API");
        check(service.catalog(null).size() == 2, "New company catalogue also hides Audit Log");
        check(!service.hasSelection(request("AUDIT_LOG", " audit_log ")), "Hidden API cannot satisfy business selection minimum");
        check(!service.hasSelection(null), "Missing DTO has no selection");
        check(service.requested(request("AUDIT_LOG", "BALANCE_INQUIRY")).equals(new HashSet<>(Arrays.asList("BALANCE_INQUIRY"))), "Ignore injected and legacy Audit Log selections");

        HthManagementApi audit = grant("COMPANY", "audit", "A");
        HthManagementApi balance = grant("COMPANY", "balance", "A");
        HthManagementApi otherCompanyAudit = grant("OTHER", "audit", "A");
        check(service.effective("COMPANY").equals(service.requested(request("BALANCE_INQUIRY"))), "No false edit/notification change caused by hidden grant");
        service.save(request("ACCOUNT_ACTIVITY", "AUDIT_LOG"), "EDIT");
        check("A".equals(audit.getObjectStatus()) && audit.getLastUpdatedBy() == null, "Edit preserves legacy Audit Log grant without writing it");
        check("I".equals(balance.getObjectStatus()), "Ordinary deselected API is disabled");
        check(service.effective("COMPANY").equals(new HashSet<>(Arrays.asList("ACCOUNT_ACTIVITY"))), "Only requested ordinary API is effective in BM");
        check("A".equals(otherCompanyAudit.getObjectStatus()), "Other company is untouched");

        // Audit Log cannot be assigned through a crafted request for a company without that grant.
        Bank.links.clear();
        service.save(request("AUDIT_LOG", "BALANCE_INQUIRY"), "ENABLE");
        check(Bank.links.size() == 1 && "balance".equals(Bank.links.get(0).getApiMasterId()), "Never add an Audit Log mapping");
        HthManagementApi inactiveAudit = grant("COMPANY", "audit", "I");
        service.save(request("AUDIT_LOG", "ACCOUNT_ACTIVITY"), "EDIT");
        check("I".equals(inactiveAudit.getObjectStatus()), "Never reactivate an old inactive Audit Log grant");

        audit = grant("COMPANY", "audit", "A");
        service.save(request("AUDIT_LOG", "ACCOUNT_ACTIVITY"), "DISABLE");
        check("I".equals(audit.getObjectStatus()), "Company Disable must still deactivate Audit Log");
        check(service.effective("COMPANY").isEmpty(), "Company Disable deactivates all API grants");
        int writes = Bank.writes;
        try {
            service.save(request("NOT_CONFIGURED"), "EDIT");
            throw new AssertionError("Unknown ordinary API must still fail validation");
        } catch (IllegalArgumentException expected) {
            check(Bank.writes == writes, "Unknown API fails before repository writes");
        }
        System.out.println("PASS: 881 catalogues, forged/legacy requests, edit preservation, enable, disable and company isolation");
    }
}
