package com.ofss.digx.cz.bea.app.hosttohost.service;

import java.util.*;
import fixture.Bank;
import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.*;

public class Hth876UserAccessTest {
  private static final Hth876Harness service = new Hth876Harness();
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private static void master(String id, String code, long order) {
    HthApiMaster row = new HthApiMaster();
    HthApiMasterKey key = new HthApiMasterKey(); key.setId(id); row.setKey(key);
    row.setApiCode(code); row.setApiName("Translated " + code); row.setDisplayOrder(order);
    row.setObjectStatus("A"); Bank.masters.put(id, row);
    HthManagementApi mapping = new HthManagementApi();
    mapping.setHthManagementId("COMPANY"); mapping.setApiMasterId(id); mapping.setObjectStatus("A");
    Bank.companyApis.add(mapping);
  }
  private static HostToHostUserAccessApiDTO api(String code) {
    HostToHostUserAccessApiDTO dto = new HostToHostUserAccessApiDTO();
    dto.setApiCode(code); dto.setSelected(true); dto.setApiMasterId("UNTRUSTED");
    return dto;
  }
  private static HostToHostUserAccessAccountDTO account(String number, String type, String... codes) {
    HostToHostUserAccessAccountDTO dto = new HostToHostUserAccessAccountDTO();
    dto.setAccountNumber(number); dto.setAccountNumberDisplay(number); dto.setAccountType(type);
    dto.setCurrency("HKD"); dto.setSelected(true);
    List<HostToHostUserAccessApiDTO> apis = new ArrayList<>();
    for (String code : codes) apis.add(api(code));
    dto.setApiServices(apis); return dto;
  }
  private static HostToHostUserAccessDTO request(String linkage, HostToHostUserAccessAccountDTO... accounts) {
    HostToHostUserAccessDTO dto = new HostToHostUserAccessDTO();
    dto.setPartyId("PARTY"); dto.setCloseId("API@PARTY"); dto.setUsername("API");
    dto.setAccessPartyId("RELATED".equals(linkage) ? "PARTY" : "ASSOCIATED-PARTY");
    dto.setLinkageType(linkage); dto.setAccounts(new ArrayList<>(Arrays.asList(accounts))); return dto;
  }
  private static HthUserAccessAccount stored(HostToHostUserAccessDTO context, String id, String number, String type, String status) {
    HthUserAccessAccount row = new HthUserAccessAccount();
    HthUserAccessAccountKey key = new HthUserAccessAccountKey(); key.setId(id); row.setKey(key);
    row.setPartyId(context.getPartyId()); row.setCloseId(context.getCloseId());
    row.setAccessPartyId(context.getAccessPartyId()); row.setLinkageType(context.getLinkageType());
    row.setAccountNumber(number); row.setAccountNumberFormatted(number); row.setAccountType(type);
    row.setObjectStatus(status); row.setCreatedBy("MAKER"); row.setLastUpdatedBy("ORIGINAL");
    Bank.accounts.add(row); return row;
  }
  private static HthUserAccessAccountApi grant(String account, String master, String status) {
    HthUserAccessAccountApi row = new HthUserAccessAccountApi();
    HthUserAccessAccountApiKey key = new HthUserAccessAccountApiKey();
    key.setId(account + ":" + master); row.setKey(key); row.setHthUserAccessAccountId(account);
    row.setApiMasterId(master); row.setObjectStatus(status);
    row.setCreatedBy("MAKER"); row.setLastUpdatedBy("ORIGINAL"); Bank.grants.add(row); return row;
  }
  private static void reset() {
    Bank.reset();
    HthManagement company = new HthManagement();
    HthManagementKey key = new HthManagementKey(); key.setId("COMPANY"); company.setKey(key);
    company.setHthStatus("ENABLE"); Bank.companies.put("PARTY", company);
    master("AUDIT", "AUDIT_LOG", 70); master("BAL", "BALANCE", 20); master("HIST", "HISTORY", 10);
    master("TD", "TD_BALANCE", 30);
    Bank.eligible.add(account("1001", "CSA")); Bank.eligible.add(account("2001", "TD"));
    Bank.eligible.add(account("1002", "CSA"));
  }
  private static void failure(HostToHostUserAccessDTO request, boolean checker, String expected) throws Exception {
    int writes = Bank.writes.size();
    try { service.validate(request, checker); throw new AssertionError("Expected " + expected); }
    catch (com.ofss.digx.infra.exceptions.Exception error) {
      check(expected.equals(error.getMessage()), "Expected " + expected + ", got " + error.getMessage());
    }
    check(Bank.writes.size() == writes, "Rejected request cannot modify storage");
  }
  public static void main(String[] args) throws Exception {
    reset();
    List<HostToHostUserAccessApiDTO> catalog = service.catalog();
    check(catalog.size() == 3 && "HISTORY".equals(catalog.get(0).getApiCode()), "Filter without changing business API order");
    Bank.masters.get("AUDIT").setApiCode(" audit_log ");
    check(service.catalog().size() == 3, "Stable code filtering independent of translated label/case");
    Bank.masters.get("HIST").setObjectStatus("I");
    check(service.catalog().size() == 2, "Inactive ordinary API remains unavailable");
    Bank.companies.get("PARTY").setHthStatus("DISABLE");
    failure(request("RELATED", account("1001", "CSA", "BALANCE")), false, "DIGX_CZ_HTH_UA_002");

    for (String linkage : new String[]{"RELATED", "ASSOCIATED"}) {
      for (boolean checker : new boolean[]{false, true}) {
        reset();
        HostToHostUserAccessAccountDTO row = account("1001", "CSA", "AUDIT_LOG", "BALANCE");
        row.getApiServices().get(1).setApiMasterId("AUDIT");
        HostToHostUserAccessAccountDTO unchecked = account("9999", "CSA", "AUDIT_LOG"); unchecked.setSelected(false);
        HostToHostUserAccessDTO input = request(linkage, row, unchecked);
        service.validate(input, checker);
        check(row.getApiServices().size() == 1 && "BAL".equals(row.getApiServices().get(0).getApiMasterId()),
            "Old snapshot is stripped; forged master ID is rebuilt from business code");
        check(unchecked.getApiServices().isEmpty(), "Unselected snapshot rows cannot carry hidden services");
        check(Bank.writes.isEmpty(), "Validation never changes effective grants");
        failure(request(linkage, account("1001", "CSA", "AUDIT_LOG")), checker, "DIGX_CZ_HTH_UA_009");
        failure(request(linkage, account("1001", "CSA", "UNKNOWN")), checker, "DIGX_CZ_HTH_UA_006");
        failure(request(linkage, account("1001", "CSA", "BALANCE", "BALANCE")), checker, "DIGX_CZ_HTH_UA_006");
        failure(request(linkage, account("9999", "CSA", "BALANCE")), checker,
            checker ? "DIGX_CZ_HTH_UA_012" : "DIGX_CZ_HTH_UA_005");
      }

      reset();
      HostToHostUserAccessDTO input = request(linkage, account("1001", "CSA", "HISTORY", "AUDIT_LOG"));
      HthUserAccessAccount kept = stored(input, "KEPT", "1001", "CSA", "A");
      HthUserAccessAccount removed = stored(input, "REMOVED", "2001", "TD", "A");
      HthUserAccessAccountApi hidden = grant("KEPT", "AUDIT", "A");
      HthUserAccessAccountApi oldBusiness = grant("KEPT", "BAL", "A");
      HthUserAccessAccountApi removedHidden = grant("REMOVED", "AUDIT", "A");
      HthUserAccessAccountApi removedBusiness = grant("REMOVED", "TD", "A");
      HostToHostUserAccessDTO otherUser = request(linkage); otherUser.setCloseId("OTHER@PARTY");
      stored(otherUser, "OTHER", "1001", "CSA", "A");
      HthUserAccessAccountApi otherHidden = grant("OTHER", "AUDIT", "A");
      HostToHostUserAccessDTO read = request(linkage, account("1001", "CSA"));
      service.readEffective(read);
      for (HostToHostUserAccessAccountDTO row : read.getAccounts()) {
        for (HostToHostUserAccessApiDTO api : row.getApiServices()) {
          check(!"AUDIT_LOG".equals(api.getApiCode()), "Effective view contains only maintainable services");
        }
      }
      service.save(input, "EDIT");
      check("A".equals(kept.getObjectStatus()), "Retained account stays active");
      check("A".equals(hidden.getObjectStatus()) && "ORIGINAL".equals(hidden.getLastUpdatedBy()), "Hidden valid grant preserved unchanged");
      check(!Bank.writes.contains("API:" + hidden.getKey().getId()), "No persistence update for hidden grant");
      check("I".equals(oldBusiness.getObjectStatus()), "Deselected ordinary service deactivated");
      check("I".equals(removed.getObjectStatus()) && "I".equals(removedHidden.getObjectStatus())
          && "I".equals(removedBusiness.getObjectStatus()), "Removed account loses all grants");
      check("A".equals(otherHidden.getObjectStatus()) && "ORIGINAL".equals(otherHidden.getLastUpdatedBy()), "Other user remains untouched");
      check("A".equals(HthUserAccessAccountApiRepository.getInstance().findByAccountIdAndApiMasterId("KEPT", "HIST").getObjectStatus()), "New business API saved");
      int size = Bank.grants.size();
      service.save(input, "EDIT");
      check(Bank.grants.size() == size, "Repeated save reuses existing grant identity");
      service.save(input, "DELETE");
      check("I".equals(kept.getObjectStatus()) && "I".equals(hidden.getObjectStatus()), "Context delete disables hidden grant too");

      reset();
      input = request(linkage, account("1001", "CSA", "BALANCE", "AUDIT_LOG"));
      service.save(input, "CREATE");
      check(Bank.grants.size() == 1 && "BAL".equals(Bank.grants.get(0).getApiMasterId()), "Create never materializes Audit Log entitlement");

      reset();
      input = request(linkage, account("1001", "CSA", "BALANCE"));
      stored(input, "KEPT", "1001", "CSA", "A"); hidden = grant("KEPT", "AUDIT", "I");
      service.save(input, "EDIT");
      check("I".equals(hidden.getObjectStatus()), "Inactive historical grant never reactivated");

      reset();
      input = request(linkage, account("1001", "CSA", "BALANCE"));
      stored(input, "OLD", "1001", "CSA", "I"); hidden = grant("OLD", "AUDIT", "A");
      service.save(input, "EDIT");
      check("I".equals(hidden.getObjectStatus()), "Reactivating old account cannot revive a stale hidden grant");

      reset();
      input = request(linkage, account("1001", "CSA", "BALANCE"));
      kept = stored(input, "ALIAS", "OLD1001", "CSA", "A"); kept.setAccountNumberFormatted("1001");
      hidden = grant("ALIAS", "AUDIT", "A");
      service.save(input, "EDIT");
      check(Bank.accounts.size() == 1 && "1001".equals(kept.getAccountNumber()), "Old formatted alias reuses account ID");
      check("A".equals(hidden.getObjectStatus()) && "ORIGINAL".equals(hidden.getLastUpdatedBy()), "Alias resolution also preserves hidden grant");
    }
    System.out.println("PASS: 876 actual catalogue/validation/effective-read/save: filtering, old/forged snapshots, minima, RELATED/ASSOCIATED, hidden grants, delete, new/inactive accounts and aliases");
  }
}
