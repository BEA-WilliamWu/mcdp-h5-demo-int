package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.app.access.dto.AccountAccessListAccountsDTO;
import com.ofss.digx.app.access.dto.AccountAccessListAccountsResponseDTO;
import com.ofss.digx.app.access.dto.AccountFilterDTO;
import com.ofss.digx.app.access.dto.AccountsAccessListsDTO;
import com.ofss.digx.app.access.service.account.AccountAccess;
import com.ofss.digx.app.access.service.account.IAccountAccess;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.party.adapter.IPartyDetailsAdapter;
import com.ofss.digx.app.party.dto.PersonalInfoDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessAccountDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessApiDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessContextDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessSearchDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessSummaryDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessPendingRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessSummaryRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfile;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfileKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessAccountApiRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessAccountRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessRequestAccountRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessRequestApiRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessRequestRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserProfileRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestAccountRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestApiRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestRepositoryAdapter;
import com.ofss.digx.datatype.complex.Party;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.accounts.AccountType;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.service.response.TransactionStatus;
import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains account and API grants for Host-to-Host users.
 *
 * <p>Access is isolated by the tuple {@code (partyId, closeId, accessPartyId, linkageType)}. The
 * primary party owns the HTH user, while the access party owns the eligible Current and Savings
 * or Time Deposit accounts. RELATED means both parties are the same; ASSOCIATED requires a
 * current party relationship.
 *
 * <p>Write operations use the OBDX maker/checker workflow. Maker execution validates the request
 * and stores an immutable database snapshot. Checker approval reloads and revalidates that
 * snapshot before replacing effective grants. Rejection never calls this service's approval
 * re-entry path and therefore does not change effective grants.
 */
public class HostToHostUserAccess extends AbstractApplication implements IHostToHostUserAccess {
  private static final String THIS_COMPONENT_NAME = HostToHostUserAccess.class.getName();

  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

  private static final String SEARCH_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search";

  private static final String ACCOUNTS_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts";

  private static final String SUBMIT_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit";

  private static final String EDIT_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit";

  private static final String DELETE_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete";

  private static final String ENABLE = "ENABLE";

  private static final String DISABLE = "DISABLE";

  private static final String RELATED = "RELATED";

  private static final String ASSOCIATED = "ASSOCIATED";

  private static final String ACTIVE = "ACTIVE";

  private static final String NOT_SETUP = "NOT_SETUP";

  private static final String DISABLED = "DISABLED";

  private static final String CREATE = "CREATE";

  private static final String EDIT = "EDIT";

  private static final String DELETE = "DELETE";

  private static final String OBJECT_ACTIVE = "A";

  private static final String OBJECT_INACTIVE = "I";

  private static final String ACCOUNT_TYPE_CSA = "CSA";

  private static final String ACCOUNT_TYPE_TD = "TD";

  /**
   * Builds the effective and pending access summary for every valid company context of an HTH
   * user. Effective counts include active grants only; pending requests change presentation state
   * but do not count as granted access.
   */
  @Override
  @Entitlement(name = "Search Host To Host User Access", action = ActionType.VIEW, requiredResources = {})
  @EntitlementGroup(
      category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  public HostToHostUserAccessResponseDTO search(
      SessionContext sessionContext,
      HostToHostUserAccessSearchDTO requestDTO) throws Exception {
    super.checkAccessPolicy(SEARCH_SERVICE_ID, sessionContext);

    HostToHostUserAccessResponseDTO response = new HostToHostUserAccessResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    Interaction.begin(sessionContext);

    try {
      String partyId = normalize(requestDTO == null ? null : requestDTO.getPartyId());
      String closeId = normalize(requestDTO == null ? null : requestDTO.getCloseId());
      validateRequest(partyId, closeId);
      validateUserProfile(partyId, closeId);
      populateResponse(response, partyId, closeId);
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while searching HTH user access for party '%s'", safePartyId(requestDTO)), e);
      throw e;
    } catch (RuntimeException e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "RuntimeException while searching HTH user access for party '%s'", safePartyId(requestDTO)), e);
      throw new Exception(e);
    } finally {
      Interaction.close();
    }

    super.checkResponsePolicy(sessionContext, response);
    return response;
  }

  /**
   * Loads eligible Current and Savings/Time Deposit accounts and enterprise-enabled APIs for a
   * single company context. Existing effective selections are merged into the eligible catalogue
   * for view/edit screens.
   */
  @Override
  @Entitlement(name = "Read Host To Host User Accounts", action = ActionType.VIEW,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  public HostToHostUserAccessResponseDTO accounts(SessionContext sessionContext,
      HostToHostUserAccessSearchDTO requestDTO) throws Exception {
    super.checkAccessPolicy(ACCOUNTS_SERVICE_ID, sessionContext);
    HostToHostUserAccessResponseDTO response = new HostToHostUserAccessResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    Interaction.begin(sessionContext);

    try {
      String partyId = normalize(requestDTO == null ? null : requestDTO.getPartyId());
      String closeId = normalize(requestDTO == null ? null : requestDTO.getCloseId());
      String accessPartyId = normalize(
          requestDTO == null ? null : requestDTO.getAccessPartyId());
      String linkageType = normalize(
          requestDTO == null ? null : requestDTO.getLinkageType());
      validateContext(partyId, closeId, accessPartyId, linkageType, true);
      populateAccountsResponse(response, sessionContext, partyId, closeId,
          accessPartyId, linkageType);
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while reading HTH user access accounts for party '%s'",
          safePartyId(requestDTO)), e);
      throw e;
    } catch (RuntimeException e) {
      fillTransactionStatus(transactionStatus, e);
      throw new Exception(e);
    } finally {
      Interaction.close();
    }

    super.checkResponsePolicy(sessionContext, response);
    return response;
  }

  /** Submits creation of a new access context for approval. */
  @Override
  @Entitlement(name = "Create Host To Host User Access", action = ActionType.PERFORM,
      requiredResources = {})
  @Entitlement(name = "Create Host To Host User Access", action = ActionType.APPROVE,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "UAT_N_HUA_NEW", parent = "UAT", name = "HTH User Access - Create",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
      aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
      type = TaskType.ADMINISTRATION)
  public HostToHostUserAccessResponseDTO submit(SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception {
    return save(sessionContext, requestDTO, SUBMIT_SERVICE_ID, CREATE);
  }

  /** Submits full replacement of an existing access context for approval. */
  @Override
  @Entitlement(name = "Edit Host To Host User Access", action = ActionType.PERFORM,
      requiredResources = {})
  @Entitlement(name = "Edit Host To Host User Access", action = ActionType.APPROVE,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "UAT_N_HUA_EDT", parent = "UAT", name = "HTH User Access - Edit",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
      aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
      type = TaskType.ADMINISTRATION)
  public HostToHostUserAccessResponseDTO edit(SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception {
    return save(sessionContext, requestDTO, EDIT_SERVICE_ID, EDIT);
  }

  /** Submits soft deletion of an existing access context for approval. */
  @Override
  @Entitlement(name = "Delete Host To Host User Access", action = ActionType.PERFORM,
      requiredResources = {})
  @Entitlement(name = "Delete Host To Host User Access", action = ActionType.APPROVE,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "UAT_N_HUA_DEL", parent = "UAT", name = "HTH User Access - Delete",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
      aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
      type = TaskType.ADMINISTRATION)
  public HostToHostUserAccessResponseDTO delete(SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception {
    return save(sessionContext, requestDTO, DELETE_SERVICE_ID, DELETE);
  }

  /**
   * Executes the maker or checker branch for all write actions.
   *
   * <p>Maker execution receives a browser DTO, validates it against current ownership and API
   * configuration, then persists an immutable approval snapshot. Approved checker re-entry does
   * not trust the re-entry DTO: it resolves the approval transaction ID, reloads the database
   * snapshot, validates it again against current data, and only then changes effective grants.
   * This prevents request tampering between submission and approval.
   */
  private HostToHostUserAccessResponseDTO save(SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO, String serviceId, String actionType) throws Exception {
    super.checkAccessPolicy(serviceId, sessionContext, requestDTO);
    HostToHostUserAccessResponseDTO response = new HostToHostUserAccessResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    boolean approvedExecution = isApprovedExecution();
    String referenceNumber = approvedExecution ? null : generateReferenceNumber(actionType);
    if (requestDTO != null && referenceNumber != null) {
      requestDTO.setReferenceNumber(referenceNumber);
      setExternalReferenceNumber(referenceNumber);
      response.getStatus().setExternalReferenceNumber(referenceNumber);
    }

    Interaction.begin(sessionContext);
    try {
      HthUserAccessRequest approvalSnapshot = null;
      if (approvedExecution) {
        approvalSnapshot = HthUserAccessRequestRepository.getInstance()
            .findActiveByTransactionId(readTransactionId());
        if (approvalSnapshot == null || !actionType.equals(approvalSnapshot.getActionType())) {
          throw new Exception("DIGX_CZ_HTH_UA_011");
        }
        requestDTO = loadRequestSnapshot(approvalSnapshot);
        referenceNumber = approvalSnapshot.getReferenceNo();
      }
      validateWriteRequest(sessionContext, requestDTO, actionType, approvedExecution);
      if (approvedExecution) {
        applyApprovedAccess(requestDTO, actionType, readUserId(sessionContext));
      } else {
        persistRequestSnapshot(requestDTO, actionType, referenceNumber,
            requireTransactionId(), readUserId(sessionContext));
      }
      requestDTO.setReferenceNumber(referenceNumber);
      response.setAccess(requestDTO);
      if (referenceNumber != null) {
        response.getStatus().setExternalReferenceNumber(referenceNumber);
        setExternalReferenceNumber(referenceNumber);
      }
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while processing HTH user access action '%s' for party '%s'",
          actionType, requestDTO == null ? null : requestDTO.getPartyId()), e);
      throw e;
    } catch (RuntimeException e) {
      fillTransactionStatus(transactionStatus, e);
      throw new Exception(e);
    } finally {
      Interaction.close();
    }

    super.checkResponsePolicy(sessionContext, response);
    return response;
  }

  /**
   * Reconstructs the approved request exclusively from the persisted header/account/API snapshot.
   * Display order is retained so the checker sees the same logical selection submitted by maker.
   */
  private HostToHostUserAccessDTO loadRequestSnapshot(HthUserAccessRequest snapshot)
      throws Exception {
    HostToHostUserAccessDTO request = new HostToHostUserAccessDTO();
    request.setPartyId(snapshot.getPartyId());
    request.setCloseId(snapshot.getCloseId());
    request.setAccessPartyId(snapshot.getAccessPartyId());
    request.setLinkageType(snapshot.getLinkageType());
    request.setUsername(snapshot.getUserName());
    request.setFullName(snapshot.getFullName());
    request.setAccessPartyName(snapshot.getAccessPartyName());
    request.setReferenceNumber(snapshot.getReferenceNo());

    List<HthUserAccessRequestAccount> accountRows =
        HthUserAccessRequestAccountRepository.getInstance()
            .listByRequestId(snapshot.getKey().getId());
    Map<String, List<HthUserAccessRequestApi>> apiRowsByAccount =
        groupRequestApisByAccount(HthUserAccessRequestApiRepository.getInstance()
            .listByRequestId(snapshot.getKey().getId()));
    List<HostToHostUserAccessAccountDTO> accounts =
        new ArrayList<HostToHostUserAccessAccountDTO>();
    if (accountRows != null) {
      Collections.sort(accountRows, new Comparator<HthUserAccessRequestAccount>() {
        @Override
        public int compare(HthUserAccessRequestAccount left,
            HthUserAccessRequestAccount right) {
          return compareOrder(left.getDisplayOrder(), right.getDisplayOrder());
        }
      });
      for (HthUserAccessRequestAccount accountRow : accountRows) {
        if (!OBJECT_ACTIVE.equals(accountRow.getObjectStatus())) {
          continue;
        }
        HostToHostUserAccessAccountDTO account = new HostToHostUserAccessAccountDTO();
        account.setAccountNumber(accountRow.getAccountNumber());
        account.setMaskedAccountNumber(maskAccountNumber(accountRow.getAccountNumber()));
        account.setAccountType(accountRow.getAccountType());
        account.setCurrency(accountRow.getCurrency());
        account.setDisplayOrder(accountRow.getDisplayOrder());
        account.setSelected(Boolean.TRUE);

        List<HthUserAccessRequestApi> apiRows =
            apiRowsByAccount.get(accountRow.getKey().getId());
        List<HostToHostUserAccessApiDTO> apis =
            new ArrayList<HostToHostUserAccessApiDTO>();
        if (apiRows != null) {
          Collections.sort(apiRows, new Comparator<HthUserAccessRequestApi>() {
            @Override
            public int compare(HthUserAccessRequestApi left,
                HthUserAccessRequestApi right) {
              return compareOrder(left.getDisplayOrder(), right.getDisplayOrder());
            }
          });
          for (HthUserAccessRequestApi apiRow : apiRows) {
            if (!OBJECT_ACTIVE.equals(apiRow.getObjectStatus())) {
              continue;
            }
            HostToHostUserAccessApiDTO api = new HostToHostUserAccessApiDTO();
            api.setApiMasterId(apiRow.getApiMasterId());
            api.setApiCode(apiRow.getApiCode());
            api.setApiName(apiRow.getApiName());
            api.setDisplayOrder(apiRow.getDisplayOrder());
            api.setSelected(Boolean.TRUE);
            apis.add(api);
          }
        }
        account.setApiServices(apis);
        accounts.add(account);
      }
    }
    request.setAccounts(accounts);
    return request;
  }

  private Map<String, List<HthUserAccessRequestApi>> groupRequestApisByAccount(
      List<HthUserAccessRequestApi> apiRows) {
    Map<String, List<HthUserAccessRequestApi>> result =
        new HashMap<String, List<HthUserAccessRequestApi>>();
    if (apiRows == null) {
      return result;
    }
    for (HthUserAccessRequestApi apiRow : apiRows) {
      if (apiRow == null) {
        continue;
      }
      String accountId = apiRow.getHthUserAccessRequestAccountId();
      List<HthUserAccessRequestApi> accountApis = result.get(accountId);
      if (accountApis == null) {
        accountApis = new ArrayList<HthUserAccessRequestApi>();
        result.put(accountId, accountApis);
      }
      accountApis.add(apiRow);
    }
    return result;
  }

  private int compareOrder(Long left, Long right) {
    long leftValue = left == null ? Long.MAX_VALUE : left.longValue();
    long rightValue = right == null ? Long.MAX_VALUE : right.longValue();
    return leftValue == rightValue ? 0 : (leftValue < rightValue ? -1 : 1);
  }

  private void validateContext(String partyId, String closeId, String accessPartyId,
      String linkageType, boolean requireEnterpriseEnabled) throws Exception {
    validateRequest(partyId, closeId);
    validateUserProfile(partyId, closeId);
    if (requireEnterpriseEnabled && !ENABLE.equals(enterpriseStatus(partyId))) {
      throw new Exception("DIGX_CZ_HTH_UA_002");
    }
    if (RELATED.equals(linkageType)) {
      if (!partyId.equals(accessPartyId)) {
        throw new Exception("DIGX_CZ_HTH_UA_003");
      }
      return;
    }
    if (!ASSOCIATED.equals(linkageType)
        || !listAssociatedPartyIds(partyId).contains(accessPartyId)) {
      throw new Exception("DIGX_CZ_HTH_UA_003");
    }
  }

  private void populateAccountsResponse(HostToHostUserAccessResponseDTO response,
      SessionContext sessionContext, String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception {
    List<HostToHostUserAccessApiDTO> eligibleApis = listEnterpriseApis(partyId);
    List<HostToHostUserAccessAccountDTO> eligibleAccounts =
        listEligibleAccounts(sessionContext, partyId, accessPartyId, linkageType,
            eligibleApis);
    List<HthUserAccessAccount> effectiveAccounts = HthUserAccessAccountRepository
        .getInstance().listByContext(partyId, closeId, accessPartyId, linkageType);

    HostToHostUserAccessDTO access = new HostToHostUserAccessDTO();
    access.setPartyId(partyId);
    access.setCloseId(closeId);
    access.setAccessPartyId(accessPartyId);
    access.setLinkageType(linkageType);
    access.setUsername(closeId);
    access.setAccessPartyName(fetchPartyName(accessPartyId));
    access.setAccounts(eligibleAccounts);

    if (hasActiveAccounts(effectiveAccounts)) {
      mergeEffectiveGrants(access, effectiveAccounts, eligibleApis);
    }

    HthUserAccessRequest pending = HthUserAccessRequestRepository.getInstance()
        .findPendingByContext(partyId, closeId, accessPartyId, linkageType);
    response.setEnterpriseHthStatus(ENABLE);
    response.setEligibleApis(eligibleApis);
    response.setEligibleAccounts(access.getAccounts());
    response.setAccess(access);
    response.setPendingRequest(Boolean.valueOf(pending != null));
    if (pending != null) {
      response.setPendingAction(pending.getActionType());
      response.setPendingReferenceNumber(pending.getReferenceNo());
    }
  }

  private List<HostToHostUserAccessApiDTO> listEnterpriseApis(String partyId)
      throws Exception {
    HthManagement management = new HthManagement().findActiveByPartyId(partyId);
    if (management == null || management.getKey() == null
        || !ENABLE.equalsIgnoreCase(normalize(management.getHthStatus()))) {
      throw new Exception("DIGX_CZ_HTH_UA_002");
    }

    List<HthManagementApi> mappings =
        new HthManagementApi().listActiveByManagementId(management.getKey().getId());
    List<HostToHostUserAccessApiDTO> result =
        new ArrayList<HostToHostUserAccessApiDTO>();
    HthApiMaster masterDomain = new HthApiMaster();
    if (mappings != null) {
      for (HthManagementApi mapping : mappings) {
        if (mapping == null || normalize(mapping.getApiMasterId()) == null) {
          continue;
        }
        HthApiMasterKey key = new HthApiMasterKey();
        key.setId(mapping.getApiMasterId());
        HthApiMaster master = masterDomain.read(key);
        if (master == null || !OBJECT_ACTIVE.equals(master.getObjectStatus())) {
          continue;
        }
        HostToHostUserAccessApiDTO dto = new HostToHostUserAccessApiDTO();
        dto.setApiMasterId(master.getKey().getId());
        dto.setApiCode(master.getApiCode());
        dto.setApiName(master.getApiName());
        dto.setDisplayOrder(master.getDisplayOrder());
        dto.setSelected(Boolean.FALSE);
        result.add(dto);
      }
    }
    Collections.sort(result, new Comparator<HostToHostUserAccessApiDTO>() {
      @Override
      public int compare(HostToHostUserAccessApiDTO left,
          HostToHostUserAccessApiDTO right) {
        long leftOrder = left.getDisplayOrder() == null ? Long.MAX_VALUE
            : left.getDisplayOrder().longValue();
        long rightOrder = right.getDisplayOrder() == null ? Long.MAX_VALUE
            : right.getDisplayOrder().longValue();
        return leftOrder == rightOrder ? safe(left.getApiCode()).compareTo(safe(right.getApiCode()))
            : (leftOrder < rightOrder ? -1 : 1);
      }
    });
    return result;
  }

  private List<HostToHostUserAccessAccountDTO> listEligibleAccounts(
      SessionContext sessionContext, String partyId, String accessPartyId,
      String linkageType,
      List<HostToHostUserAccessApiDTO> eligibleApis) throws Exception {
    // Reuse the same AccountAccess service and request model as the BCO account-linkage screen.
    // The primary party is the company being maintained. For an ASSOCIATED context the selected
    // company is supplied as a linked party; for RELATED it is the primary party and the linked
    // party list stays empty. Calling PartyToAccountRelationship directly would bypass BCO's
    // corporate-admin remote lookup and eligibility adapter, producing a different or stale list.
    List<AccountType> accountTypes = new ArrayList<AccountType>();
    accountTypes.add(AccountType.DEMAND_DEPOSIT);
    accountTypes.add(AccountType.TERM_DEPOSIT);

    AccountAccessListAccountsDTO request = new AccountAccessListAccountsDTO();
    Party party = new Party();
    party.setValue(partyId);
    request.setParty(party);
    request.setAccountTypes(accountTypes);
    List<Party> linkedParties = new ArrayList<Party>();
    if (ASSOCIATED.equals(linkageType)) {
      Party linkedParty = new Party();
      linkedParty.setValue(accessPartyId);
      linkedParties.add(linkedParty);
    }
    request.setLinkedPartyList(linkedParties);

    IAccountAccess accountAccess = new AccountAccess();
    AccountAccessListAccountsResponseDTO accountAccessResponse =
        accountAccess.listAccounts(sessionContext, request);
    List<HostToHostUserAccessAccountDTO> result =
        new ArrayList<HostToHostUserAccessAccountDTO>();
    // BCO can legitimately expose one account number in both CSA and TD; only an exact
    // Account Type + Account Number duplicate is removed.
    Set<String> seen = new HashSet<String>();
    if (accountAccessResponse == null || accountAccessResponse.getAccounts() == null) {
      return result;
    }

    long order = 0L;
    for (AccountsAccessListsDTO partyAccounts : accountAccessResponse.getAccounts()) {
      if (partyAccounts == null || partyAccounts.getParty() == null
          || !accessPartyId.equals(normalize(partyAccounts.getParty().getValue()))
          || partyAccounts.getAccountsList() == null) {
        continue;
      }
      for (AccountFilterDTO account : partyAccounts.getAccountsList()) {
        String accountNumber = account == null || account.getAccountNumber() == null
            ? null : normalize(account.getAccountNumber().getValue());
        String accountType = toUserAccessAccountType(
            account == null ? null : account.getAccountType());
        if (accountNumber == null || accountType == null
            || !seen.add(accountKey(accountType, accountNumber))) {
          continue;
        }
        HostToHostUserAccessAccountDTO dto = new HostToHostUserAccessAccountDTO();
        dto.setAccountNumber(accountNumber);
        dto.setMaskedAccountNumber(maskAccountNumber(accountNumber));
        dto.setDisplayName(firstNonBlank(account.getDisplayName(), null,
            dto.getMaskedAccountNumber()));
        dto.setAccountType(accountType);
        dto.setCurrency(account.getCurrencyCode());
        dto.setSelected(Boolean.FALSE);
        dto.setDisplayOrder(Long.valueOf(order++));
        dto.setApiServices(copyApis(eligibleApis));
        result.add(dto);
      }
    }
    return result;
  }

  private void mergeEffectiveGrants(HostToHostUserAccessDTO access,
      List<HthUserAccessAccount> effectiveAccounts,
      List<HostToHostUserAccessApiDTO> eligibleApis)
      throws Exception {
    Map<String, HostToHostUserAccessAccountDTO> dtoByAccount =
        new LinkedHashMap<String, HostToHostUserAccessAccountDTO>();
    for (HostToHostUserAccessAccountDTO dto : access.getAccounts()) {
      dtoByAccount.put(accountKey(dto.getAccountType(), dto.getAccountNumber()), dto);
    }
    if (effectiveAccounts == null) {
      return;
    }
    Map<String, Set<String>> selectedMasterIdsByAccount =
        new HashMap<String, Set<String>>();
    for (HthUserAccessAccount account : effectiveAccounts) {
      selectedMasterIdsByAccount.putAll(groupActiveGrantIds(
          HthUserAccessAccountApiRepository.getInstance()
              .listByAccountId(account.getKey().getId())));
    }
    for (HthUserAccessAccount account : effectiveAccounts) {
      if (!OBJECT_ACTIVE.equals(account.getObjectStatus())) {
        continue;
      }
      String effectiveAccountKey = accountKey(account.getAccountType(),
          account.getAccountNumber());
      HostToHostUserAccessAccountDTO dto = dtoByAccount.get(effectiveAccountKey);
      if (dto == null) {
        dto = new HostToHostUserAccessAccountDTO();
        dto.setAccountNumber(account.getAccountNumber());
        dto.setMaskedAccountNumber(maskAccountNumber(account.getAccountNumber()));
        dto.setDisplayName(dto.getMaskedAccountNumber());
        dto.setAccountType(account.getAccountType());
        dto.setCurrency(account.getCurrency());
        dto.setApiServices(copyApis(eligibleApis));
        access.getAccounts().add(dto);
        dtoByAccount.put(effectiveAccountKey, dto);
      }
      dto.setSelected(Boolean.TRUE);
      Set<String> selectedMasterIds = selectedMasterIdsByAccount.get(account.getKey().getId());
      if (selectedMasterIds == null) {
        selectedMasterIds = Collections.emptySet();
      }
      for (HostToHostUserAccessApiDTO api : dto.getApiServices()) {
        api.setSelected(Boolean.valueOf(selectedMasterIds.contains(api.getApiMasterId())));
      }
    }
  }

  private boolean hasActiveAccounts(List<HthUserAccessAccount> accounts) {
    if (accounts != null) {
      for (HthUserAccessAccount account : accounts) {
        if (account != null && OBJECT_ACTIVE.equals(account.getObjectStatus())) {
          return true;
        }
      }
    }
    return false;
  }

  private Map<String, Set<String>> groupActiveGrantIds(
      List<HthUserAccessAccountApi> grants) {
    Map<String, Set<String>> result = new HashMap<String, Set<String>>();
    if (grants == null) {
      return result;
    }
    for (HthUserAccessAccountApi grant : grants) {
      if (grant == null || !OBJECT_ACTIVE.equals(grant.getObjectStatus())) {
        continue;
      }
      Set<String> masterIds = result.get(grant.getHthUserAccessAccountId());
      if (masterIds == null) {
        masterIds = new HashSet<String>();
        result.put(grant.getHthUserAccessAccountId(), masterIds);
      }
      masterIds.add(grant.getApiMasterId());
    }
    return result;
  }

  private List<HostToHostUserAccessApiDTO> copyApis(
      List<HostToHostUserAccessApiDTO> source) {
    List<HostToHostUserAccessApiDTO> copies =
        new ArrayList<HostToHostUserAccessApiDTO>();
    for (HostToHostUserAccessApiDTO api : source) {
      HostToHostUserAccessApiDTO copy = new HostToHostUserAccessApiDTO();
      copy.setApiMasterId(api.getApiMasterId());
      copy.setApiCode(api.getApiCode());
      copy.setApiName(api.getApiName());
      copy.setDisplayOrder(api.getDisplayOrder());
      copy.setSelected(Boolean.FALSE);
      copies.add(copy);
    }
    return copies;
  }

  private void validateWriteRequest(SessionContext sessionContext,
      HostToHostUserAccessDTO request, String actionType, boolean approvedExecution)
      throws Exception {
    if (request == null) {
      throw new Exception("DIGX_CZ_HTH_UA_010");
    }
    String partyId = normalize(request.getPartyId());
    String closeId = normalize(request.getCloseId());
    String accessPartyId = normalize(request.getAccessPartyId());
    String linkageType = normalize(request.getLinkageType());
    validateContext(partyId, closeId, accessPartyId, linkageType, true);
    request.setPartyId(partyId);
    request.setCloseId(closeId);
    request.setAccessPartyId(accessPartyId);
    request.setLinkageType(linkageType);

    if (!approvedExecution && HthUserAccessRequestRepository.getInstance()
        .findPendingByContext(partyId, closeId, accessPartyId, linkageType) != null) {
      throw new Exception("DIGX_CZ_HTH_UA_008");
    }

    boolean active = HthUserAccessAccountRepository.getInstance()
        .hasActiveByContext(partyId, closeId, accessPartyId, linkageType);
    if (CREATE.equals(actionType) && active) {
      throw new Exception("DIGX_CZ_HTH_UA_010");
    }
    if (!CREATE.equals(actionType) && !active) {
      throw new Exception("DIGX_CZ_HTH_UA_010");
    }
    if (!DELETE.equals(actionType)) {
      validateSelectedAccess(sessionContext, request, approvedExecution);
    }
  }

  private void validateSelectedAccess(SessionContext sessionContext,
      HostToHostUserAccessDTO request, boolean approvedExecution) throws Exception {
    // Rebuild both catalogues from current server-side data. Approval must fail if an account or
    // API became ineligible after maker submission; client snapshot metadata is never authoritative.
    List<HostToHostUserAccessApiDTO> eligibleApis = listEnterpriseApis(request.getPartyId());
    Map<String, HostToHostUserAccessApiDTO> eligibleApiByCode =
        new HashMap<String, HostToHostUserAccessApiDTO>();
    for (HostToHostUserAccessApiDTO api : eligibleApis) {
      eligibleApiByCode.put(api.getApiCode(), api);
    }
    List<HostToHostUserAccessAccountDTO> eligibleAccounts = listEligibleAccounts(
        sessionContext, request.getPartyId(), request.getAccessPartyId(),
        request.getLinkageType(), eligibleApis);
    Map<String, HostToHostUserAccessAccountDTO> eligibleAccountByKey =
        new HashMap<String, HostToHostUserAccessAccountDTO>();
    for (HostToHostUserAccessAccountDTO account : eligibleAccounts) {
      eligibleAccountByKey.put(accountKey(account.getAccountType(), account.getAccountNumber()),
          account);
    }

    int selectedAccountCount = 0;
    Set<String> selectedAccountKeys = new HashSet<String>();
    if (request.getAccounts() != null) {
      for (HostToHostUserAccessAccountDTO account : request.getAccounts()) {
        if (account == null || !Boolean.TRUE.equals(account.getSelected())) {
          continue;
        }
        selectedAccountCount++;
        String accountNumber = normalize(account.getAccountNumber());
        String accountType = normalizeAccountType(account.getAccountType());
        if (!isSupportedAccountType(accountType)) {
          throw new Exception("DIGX_CZ_HTH_UA_004");
        }
        if (accountNumber == null
            || !selectedAccountKeys.add(accountKey(accountType, accountNumber))) {
          throw new Exception("DIGX_CZ_HTH_UA_005");
        }
        HostToHostUserAccessAccountDTO eligibleAccount =
            eligibleAccountByKey.get(accountKey(accountType, accountNumber));
        if (eligibleAccount == null) {
          throw new Exception(approvedExecution ? "DIGX_CZ_HTH_UA_012"
              : "DIGX_CZ_HTH_UA_005");
        }
        account.setAccountNumber(accountNumber);
        account.setAccountType(accountType);
        account.setMaskedAccountNumber(eligibleAccount.getMaskedAccountNumber());
        account.setDisplayName(eligibleAccount.getDisplayName());
        account.setCurrency(eligibleAccount.getCurrency());
        account.setDisplayOrder(eligibleAccount.getDisplayOrder());
        int selectedApiCount = 0;
        Set<String> selectedCodes = new HashSet<String>();
        if (account.getApiServices() != null) {
          for (HostToHostUserAccessApiDTO api : account.getApiServices()) {
            if (api == null || !Boolean.TRUE.equals(api.getSelected())) {
              continue;
            }
            selectedApiCount++;
            String apiCode = normalize(api.getApiCode());
            HostToHostUserAccessApiDTO eligibleApi = eligibleApiByCode.get(apiCode);
            if (apiCode == null || !selectedCodes.add(apiCode) || eligibleApi == null) {
              throw new Exception("DIGX_CZ_HTH_UA_006");
            }
            api.setApiCode(apiCode);
            api.setApiMasterId(eligibleApi.getApiMasterId());
            api.setApiName(eligibleApi.getApiName());
            api.setDisplayOrder(eligibleApi.getDisplayOrder());
          }
        }
        if (selectedApiCount == 0) {
          throw new Exception("DIGX_CZ_HTH_UA_009");
        }
      }
    }
    if (selectedAccountCount == 0) {
      throw new Exception("DIGX_CZ_HTH_UA_009");
    }
  }

  /**
   * Persists one immutable maker snapshot in a dedicated NONXA transaction.
   *
   * <p>The approval framework may invoke the maker operation more than once for the same
   * transaction. The transaction ID unique key and the initial lookup make the snapshot write
   * idempotent. Header, account, and API rows are committed together; any failure rolls back the
   * complete snapshot before the session is closed.
   */
  private void persistRequestSnapshot(HostToHostUserAccessDTO request,
      String actionType, String referenceNumber, String transactionId, String userId)
      throws Exception {
    Session session = null;
    boolean committed = false;
    try {
      session = DataAccessManager.getManager().openNewSession("NONXA");
      session.beginTransaction();
      LocalHthUserAccessRequestRepositoryAdapter requestAdapter =
          LocalHthUserAccessRequestRepositoryAdapter.getInstance();
      if (requestAdapter.findActiveByTransactionId(transactionId) != null) {
        session.fetchCurrentTransaction().commit();
        committed = true;
        return;
      }

      String requestId = generateId();
      HthUserAccessRequest snapshot = buildRequestSnapshot(request, actionType,
          referenceNumber, transactionId, userId, requestId);
      requestAdapter.create(snapshot);
      session.flush();
      if (!DELETE.equals(actionType)) {
        persistRequestAccountSnapshots(request, requestId, userId);
      }
      session.flush();
      session.fetchCurrentTransaction().commit();
      committed = true;
    } catch (java.lang.Exception e) {
      throw e instanceof Exception ? (Exception) e : new Exception(e);
    } finally {
      if (session != null) {
        try {
          if (!committed) {
            session.fetchCurrentTransaction().rollback();
          }
        } finally {
          DataAccessManager.getManager().closeSession(session);
        }
      }
    }
  }

  private HthUserAccessRequest buildRequestSnapshot(HostToHostUserAccessDTO request,
      String actionType, String referenceNumber, String transactionId, String userId,
      String requestId) {
    HthUserAccessRequest row = new HthUserAccessRequest();
    HthUserAccessRequestKey key = new HthUserAccessRequestKey();
    key.setId(requestId);
    row.setKey(key);
    row.setTransactionId(transactionId);
    row.setReferenceNo(referenceNumber);
    row.setActionType(actionType);
    row.setPartyId(request.getPartyId());
    row.setCloseId(request.getCloseId());
    row.setAccessPartyId(request.getAccessPartyId());
    row.setLinkageType(request.getLinkageType());
    row.setUserName(request.getUsername());
    row.setFullName(request.getFullName());
    row.setAccessPartyName(request.getAccessPartyName());
    row.setObjectStatus(OBJECT_ACTIVE);
    row.setCreatedBy(userId);
    row.setLastUpdatedBy(userId);
    return row;
  }

  private void persistRequestAccountSnapshots(HostToHostUserAccessDTO request,
      String requestId, String userId) throws Exception {
    Map<String, HostToHostUserAccessApiDTO> enterpriseApiByCode =
        new HashMap<String, HostToHostUserAccessApiDTO>();
    for (HostToHostUserAccessApiDTO api : listEnterpriseApis(request.getPartyId())) {
      enterpriseApiByCode.put(api.getApiCode(), api);
    }
    long accountOrder = 0L;
    for (HostToHostUserAccessAccountDTO account : selectedAccounts(request)) {
      HthUserAccessRequestAccount accountRow = new HthUserAccessRequestAccount();
      HthUserAccessRequestAccountKey accountKey = new HthUserAccessRequestAccountKey();
      accountKey.setId(generateId());
      accountRow.setKey(accountKey);
      accountRow.setHthUserAccessRequestId(requestId);
      accountRow.setAccountNumber(account.getAccountNumber());
      accountRow.setAccountType(account.getAccountType());
      accountRow.setCurrency(account.getCurrency());
      accountRow.setDisplayOrder(account.getDisplayOrder() == null
          ? Long.valueOf(accountOrder) : account.getDisplayOrder());
      accountRow.setObjectStatus(OBJECT_ACTIVE);
      accountRow.setCreatedBy(userId);
      accountRow.setLastUpdatedBy(userId);
      LocalHthUserAccessRequestAccountRepositoryAdapter.getInstance().create(accountRow);

      long apiOrder = 0L;
      for (HostToHostUserAccessApiDTO api : selectedApis(account)) {
        HostToHostUserAccessApiDTO catalogApi = enterpriseApiByCode.get(api.getApiCode());
        HthUserAccessRequestApi apiRow = new HthUserAccessRequestApi();
        HthUserAccessRequestApiKey apiKey = new HthUserAccessRequestApiKey();
        apiKey.setId(generateId());
        apiRow.setKey(apiKey);
        apiRow.setHthUserAccessRequestAccountId(accountKey.getId());
        apiRow.setApiMasterId(catalogApi.getApiMasterId());
        apiRow.setApiCode(catalogApi.getApiCode());
        apiRow.setApiName(catalogApi.getApiName());
        apiRow.setDisplayOrder(api.getDisplayOrder() == null
            ? Long.valueOf(apiOrder) : api.getDisplayOrder());
        apiRow.setObjectStatus(OBJECT_ACTIVE);
        apiRow.setCreatedBy(userId);
        apiRow.setLastUpdatedBy(userId);
        LocalHthUserAccessRequestApiRepositoryAdapter.getInstance().create(apiRow);
        apiOrder++;
      }
      accountOrder++;
    }
  }

  /**
   * Applies a checker-approved request. Delete deactivates the context; create/edit replace the
   * complete effective selection using soft status changes so audit history is retained.
   */
  private void applyApprovedAccess(HostToHostUserAccessDTO request,
      String actionType, String userId) throws Exception {
    if (DELETE.equals(actionType)) {
      deactivateAccounts(request, userId);
      return;
    }
    replaceEffectiveAccounts(request, userId);
  }

  private void replaceEffectiveAccounts(HostToHostUserAccessDTO request, String userId)
      throws Exception {
    // Replacement is intentionally implemented as deactivate-then-reactivate. Rows are reused
    // where unique business keys match, preserving creation audit fields and historical identity.
    deactivateAccounts(request, userId);
    Map<String, HostToHostUserAccessApiDTO> enterpriseApiByCode =
        new HashMap<String, HostToHostUserAccessApiDTO>();
    for (HostToHostUserAccessApiDTO api : listEnterpriseApis(request.getPartyId())) {
      enterpriseApiByCode.put(api.getApiCode(), api);
    }
    Map<String, HthUserAccessAccount> existingAccountByKey =
        new HashMap<String, HthUserAccessAccount>();
    List<HthUserAccessAccount> existingAccounts = HthUserAccessAccountRepository.getInstance()
        .listByContext(request.getPartyId(), request.getCloseId(),
            request.getAccessPartyId(), request.getLinkageType());
    if (existingAccounts != null) {
      for (HthUserAccessAccount existingAccount : existingAccounts) {
        existingAccountByKey.put(accountKey(existingAccount.getAccountType(),
            existingAccount.getAccountNumber()), existingAccount);
      }
    }
    for (HostToHostUserAccessAccountDTO accountDTO : selectedAccounts(request)) {
      HthUserAccessAccount account = existingAccountByKey.get(
          accountKey(accountDTO.getAccountType(), accountDTO.getAccountNumber()));
      if (account == null) {
        account = new HthUserAccessAccount();
        HthUserAccessAccountKey key = new HthUserAccessAccountKey();
        key.setId(generateId());
        account.setKey(key);
        account.setPartyId(request.getPartyId());
        account.setCloseId(request.getCloseId());
        account.setAccessPartyId(request.getAccessPartyId());
        account.setLinkageType(request.getLinkageType());
        account.setAccountNumber(accountDTO.getAccountNumber());
        account.setAccountType(accountDTO.getAccountType());
        account.setCurrency(accountDTO.getCurrency());
        account.setObjectStatus(OBJECT_ACTIVE);
        account.setCreatedBy(userId);
        account.setLastUpdatedBy(userId);
        HthUserAccessAccountRepository.getInstance().create(account);
      } else {
        account.setAccountType(accountDTO.getAccountType());
        account.setCurrency(accountDTO.getCurrency());
        account.setObjectStatus(OBJECT_ACTIVE);
        account.setLastUpdatedBy(userId);
        HthUserAccessAccountRepository.getInstance().update(account);
      }
      for (HostToHostUserAccessApiDTO apiDTO : selectedApis(accountDTO)) {
        HostToHostUserAccessApiDTO catalogApi = enterpriseApiByCode.get(apiDTO.getApiCode());
        HthUserAccessAccountApi grant = HthUserAccessAccountApiRepository.getInstance()
            .findByAccountIdAndApiMasterId(account.getKey().getId(),
                catalogApi.getApiMasterId());
        if (grant == null) {
          grant = new HthUserAccessAccountApi();
          HthUserAccessAccountApiKey key = new HthUserAccessAccountApiKey();
          key.setId(generateId());
          grant.setKey(key);
          grant.setHthUserAccessAccountId(account.getKey().getId());
          grant.setApiMasterId(catalogApi.getApiMasterId());
          grant.setObjectStatus(OBJECT_ACTIVE);
          grant.setCreatedBy(userId);
          grant.setLastUpdatedBy(userId);
          HthUserAccessAccountApiRepository.getInstance().create(grant);
        } else {
          grant.setObjectStatus(OBJECT_ACTIVE);
          grant.setLastUpdatedBy(userId);
          HthUserAccessAccountApiRepository.getInstance().update(grant);
        }
      }
    }
  }

  /** Deactivates API grants before their parent account grants to preserve relationship history. */
  private void deactivateAccounts(HostToHostUserAccessDTO request, String userId)
      throws Exception {
    List<HthUserAccessAccount> accounts = HthUserAccessAccountRepository.getInstance()
        .listByContext(request.getPartyId(), request.getCloseId(),
            request.getAccessPartyId(), request.getLinkageType());
    if (accounts == null) {
      return;
    }
    for (HthUserAccessAccount account : accounts) {
      List<HthUserAccessAccountApi> grants = HthUserAccessAccountApiRepository.getInstance()
          .listByAccountId(account.getKey().getId());
      if (grants != null) {
        for (HthUserAccessAccountApi grant : grants) {
          if (!OBJECT_INACTIVE.equals(grant.getObjectStatus())) {
            grant.setObjectStatus(OBJECT_INACTIVE);
            grant.setLastUpdatedBy(userId);
            HthUserAccessAccountApiRepository.getInstance().update(grant);
          }
        }
      }
      if (!OBJECT_INACTIVE.equals(account.getObjectStatus())) {
        account.setObjectStatus(OBJECT_INACTIVE);
        account.setLastUpdatedBy(userId);
        HthUserAccessAccountRepository.getInstance().update(account);
      }
    }
  }

  private List<HostToHostUserAccessAccountDTO> selectedAccounts(
      HostToHostUserAccessDTO request) {
    List<HostToHostUserAccessAccountDTO> result =
        new ArrayList<HostToHostUserAccessAccountDTO>();
    if (request.getAccounts() != null) {
      for (HostToHostUserAccessAccountDTO account : request.getAccounts()) {
        if (account != null && Boolean.TRUE.equals(account.getSelected())) {
          result.add(account);
        }
      }
    }
    return result;
  }

  private List<HostToHostUserAccessApiDTO> selectedApis(
      HostToHostUserAccessAccountDTO account) {
    List<HostToHostUserAccessApiDTO> result =
        new ArrayList<HostToHostUserAccessApiDTO>();
    if (account.getApiServices() != null) {
      for (HostToHostUserAccessApiDTO api : account.getApiServices()) {
        if (api != null && Boolean.TRUE.equals(api.getSelected())) {
          result.add(api);
        }
      }
    }
    return result;
  }

  /** Maps platform portfolio account types to the stable values stored by this feature. */
  private String toUserAccessAccountType(AccountType accountType) {
    if (AccountType.DEMAND_DEPOSIT.equals(accountType)) {
      return ACCOUNT_TYPE_CSA;
    }
    if (AccountType.TERM_DEPOSIT.equals(accountType)) {
      return ACCOUNT_TYPE_TD;
    }
    return null;
  }

  private String normalizeAccountType(String accountType) {
    String normalized = normalize(accountType);
    return normalized == null ? null : normalized.toUpperCase();
  }

  private boolean isSupportedAccountType(String accountType) {
    return ACCOUNT_TYPE_CSA.equals(accountType) || ACCOUNT_TYPE_TD.equals(accountType);
  }

  private String accountKey(String accountType, String accountNumber) {
    return safe(normalizeAccountType(accountType)) + "#" + safe(normalize(accountNumber));
  }

  private void validateRequest(String partyId, String closeId) throws Exception {
    if (partyId == null || closeId == null) {
      throw new Exception("DIGX_CZ_HTH_UA_001");
    }
  }

  private void validateUserProfile(String partyId, String closeId) throws Exception {
    HthUserProfileKey key = new HthUserProfileKey();
    key.setPartyId(partyId);
    key.setCloseId(closeId);
    HthUserProfile profile = HthUserProfileRepository.getInstance().read(key);
    if (profile == null) {
      throw new Exception("DIGX_CZ_HTH_UA_001");
    }
  }

  private void populateResponse(
      HostToHostUserAccessResponseDTO response,
      String partyId,
      String closeId) throws Exception {
    // Enterprise HTH status is a prerequisite for maintenance, but access setup is derived from
    // effective user grants. An enabled enterprise with no active grant is therefore NOT_SETUP.
    String enterpriseStatus = enterpriseStatus(partyId);
    response.setEnterpriseHthStatus(enterpriseStatus);

    HostToHostUserAccessContextDTO user = new HostToHostUserAccessContextDTO();
    user.setPartyId(partyId);
    user.setCloseId(closeId);
    user.setUsername(closeId);
    user.setUserChannelType("HTH");
    response.setUser(user);

    Map<String, HostToHostUserAccessSummaryDTO> summaries =
        initializeSummaries(partyId, enterpriseStatus);
    applyEffectiveAccess(summaries, partyId, closeId, enterpriseStatus);
    applyPendingRequests(summaries, partyId, closeId, enterpriseStatus);

    response.setRelated(summaries.get(contextKey(RELATED, partyId)));
    List<HostToHostUserAccessSummaryDTO> associated =
        new ArrayList<HostToHostUserAccessSummaryDTO>();
    for (HostToHostUserAccessSummaryDTO summary : summaries.values()) {
      if (ASSOCIATED.equals(summary.getLinkageType())) {
        associated.add(summary);
      }
    }
    response.setAssociated(associated);
  }

  private Map<String, HostToHostUserAccessSummaryDTO> initializeSummaries(
      String partyId,
      String enterpriseStatus) throws Exception {
    // The primary party is always represented as RELATED. ASSOCIATED contexts are created only
    // from current party relationships, preventing orphaned historical grants from being exposed.
    Map<String, HostToHostUserAccessSummaryDTO> summaries =
        new LinkedHashMap<String, HostToHostUserAccessSummaryDTO>();
    summaries.put(contextKey(RELATED, partyId),
        newSummary(RELATED, partyId, fetchPartyName(partyId), enterpriseStatus));

    for (String associatedPartyId : listAssociatedPartyIds(partyId)) {
      summaries.put(contextKey(ASSOCIATED, associatedPartyId),
          newSummary(ASSOCIATED, associatedPartyId, fetchPartyName(associatedPartyId), enterpriseStatus));
    }
    return summaries;
  }

  private HostToHostUserAccessSummaryDTO newSummary(
      String linkageType,
      String accessPartyId,
      String accessPartyName,
      String enterpriseStatus) {
    HostToHostUserAccessSummaryDTO summary = new HostToHostUserAccessSummaryDTO();
    summary.setLinkageType(linkageType);
    summary.setAccessPartyId(accessPartyId);
    summary.setAccessPartyName(accessPartyName);
    summary.setSetupStatus(ENABLE.equals(enterpriseStatus) ? NOT_SETUP : DISABLED);
    summary.getAccountCountByType().put(ACCOUNT_TYPE_CSA, Integer.valueOf(0));
    summary.getAccountCountByType().put(ACCOUNT_TYPE_TD, Integer.valueOf(0));
    return summary;
  }

  private void applyEffectiveAccess(
      Map<String, HostToHostUserAccessSummaryDTO> summaries,
      String partyId,
      String closeId,
      String enterpriseStatus) throws Exception {
    List<HthUserAccessSummaryRecord> records =
        HthUserAccessAccountRepository.getInstance().listActiveSummary(partyId, closeId);
    if (records == null) {
      return;
    }

    for (HthUserAccessSummaryRecord record : records) {
      HostToHostUserAccessSummaryDTO summary =
          summaries.get(contextKey(record.getLinkageType(), record.getAccessPartyId()));
      if (summary == null) {
        LOGGER.log(Level.WARNING, FORMATTER.formatMessage(
            "Ignoring HTH user access with invalid party relationship for party '%s'", partyId));
        continue;
      }
      summary.setAccountCountByType(
          new LinkedHashMap<String, Integer>(record.getAccountCountByType()));
      if (!summary.getAccountCountByType().containsKey(ACCOUNT_TYPE_CSA)) {
        summary.getAccountCountByType().put(ACCOUNT_TYPE_CSA, Integer.valueOf(0));
      }
      if (!summary.getAccountCountByType().containsKey(ACCOUNT_TYPE_TD)) {
        summary.getAccountCountByType().put(ACCOUNT_TYPE_TD, Integer.valueOf(0));
      }
      if (ENABLE.equals(enterpriseStatus)) {
        summary.setSetupStatus(ACTIVE);
      }
    }
  }

  private void applyPendingRequests(
      Map<String, HostToHostUserAccessSummaryDTO> summaries,
      String partyId,
      String closeId,
      String enterpriseStatus) throws Exception {
    List<HthUserAccessPendingRecord> records =
        HthUserAccessRequestRepository.getInstance().listPendingSummary(partyId, closeId);
    if (records == null) {
      return;
    }

    for (HthUserAccessPendingRecord record : records) {
      HostToHostUserAccessSummaryDTO summary =
          summaries.get(contextKey(record.getLinkageType(), record.getAccessPartyId()));
      if (summary == null) {
        LOGGER.log(Level.WARNING, FORMATTER.formatMessage(
            "Ignoring pending HTH user access with invalid party relationship for party '%s'", partyId));
        continue;
      }
      summary.setPendingAction(record.getActionType());
      summary.setPendingReferenceNumber(record.getReferenceNumber());
      if (ENABLE.equals(enterpriseStatus)) {
        summary.setSetupStatus("PENDING_" + record.getActionType());
      }
    }
  }

  private Set<String> listAssociatedPartyIds(String partyId) throws Exception {
    Set<String> associatedPartyIds = new LinkedHashSet<String>();
    com.ofss.digx.domain.party.entity.relation.PartyToPartyRelationship relationshipDomain =
        new com.ofss.digx.domain.party.entity.relation.PartyToPartyRelationship();
    List<com.ofss.digx.domain.party.entity.relation.PartyToPartyRelationship> relationships =
        relationshipDomain.listPartyToPartyRelationship(partyId, null);
    if (relationships == null) {
      return associatedPartyIds;
    }

    for (com.ofss.digx.domain.party.entity.relation.PartyToPartyRelationship relationship : relationships) {
      if (relationship != null && relationship.getKey() != null) {
        String relatedPartyId = normalize(relationship.getKey().getRelatedPartyId());
        if (relatedPartyId != null && !partyId.equals(relatedPartyId)) {
          associatedPartyIds.add(relatedPartyId);
        }
      }
    }
    return associatedPartyIds;
  }

  private String fetchPartyName(String partyId) {
    try {
      IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
          com.ofss.digx.common.constants.CommonAdapterFactoryConstants.PARTY_DETAILS_ADAPTER_FACTORY);
      IPartyDetailsAdapter partyDetailsAdapter = (IPartyDetailsAdapter) adapterFactory.getAdapter(
          com.ofss.digx.common.constants.CommonAdapterConstants.PARTY_DETAILS_ADAPTER);
      PersonalInfoDTO personalInfo = partyDetailsAdapter.fetchPersonalInformation(partyId);
      return personalInfo == null || normalize(personalInfo.getFullName()) == null
          ? partyId : personalInfo.getFullName();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, FORMATTER.formatMessage(
          "Unable to resolve company name for HTH user access party '%s'", partyId), e);
      return partyId;
    }
  }

  private String enterpriseStatus(String partyId) throws Exception {
    HthManagement management = new HthManagement().findActiveByPartyId(partyId);
    return management != null && ENABLE.equalsIgnoreCase(normalize(management.getHthStatus()))
        ? ENABLE : DISABLE;
  }

  private String contextKey(String linkageType, String accessPartyId) {
    return linkageType + "#" + accessPartyId;
  }

  private boolean isApprovedExecution() {
    // Approval status is read from both framework ThreadAttribute variants for release
    // compatibility. Only an explicit APPROVED value enters the effective-data mutation path.
    Object approvalStatus = com.ofss.fc.infra.thread.ThreadAttribute
        .get(com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
    if (approvalStatus == null) {
      approvalStatus = ThreadAttribute.get(
          com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
    }
    return ApprovalStatus.APPROVED.toString().equals(String.valueOf(approvalStatus));
  }

  private String generateReferenceNumber(String actionType) {
    return "HUA" + actionType.substring(0, 3) + UUID.randomUUID().toString();
  }

  private String generateId() {
    return UUID.randomUUID().toString();
  }

  private String requireTransactionId() {
    String transactionId = normalize(readTransactionId());
    if (transactionId == null) {
      throw new IllegalStateException(
          "Transaction reference number is not available from approval framework.");
    }
    return transactionId;
  }

  private String readTransactionId() {
    Object transactionId = ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO);
    if (transactionId == null) {
      transactionId = ThreadAttribute.get(
          com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
    }
    if (transactionId == null) {
      transactionId = com.ofss.fc.infra.thread.ThreadAttribute.get(
          com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
    }
    if (transactionId == null) {
      transactionId = com.ofss.fc.infra.thread.ThreadAttribute.get(
          com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
    }
    return transactionId == null ? null : String.valueOf(transactionId);
  }

  private void setExternalReferenceNumber(String referenceNumber) {
    ThreadAttribute.set(ThreadAttribute.EXTERNAL_REFERENCE_NUMBER, referenceNumber);
    com.ofss.fc.infra.thread.ThreadAttribute.set(
        com.ofss.fc.infra.thread.ThreadAttribute.EXTERNAL_REFERENCE_NUMBER,
        referenceNumber);
  }

  private String readUserId(SessionContext sessionContext) {
    return sessionContext == null || normalize(sessionContext.getUserId()) == null
        ? "system" : sessionContext.getUserId();
  }

  private String maskAccountNumber(String accountNumber) {
    String normalized = normalize(accountNumber);
    if (normalized == null) {
      return null;
    }
    if (normalized.length() <= 4) {
      return normalized;
    }
    StringBuilder masked = new StringBuilder();
    for (int index = 0; index < normalized.length() - 4; index++) {
      masked.append('*');
    }
    return masked.append(normalized.substring(normalized.length() - 4)).toString();
  }

  private String firstNonBlank(String first, String second, String fallback) {
    if (normalize(first) != null) {
      return first;
    }
    if (normalize(second) != null) {
      return second;
    }
    return fallback;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String safePartyId(HostToHostUserAccessSearchDTO requestDTO) {
    return requestDTO == null ? null : normalize(requestDTO.getPartyId());
  }
}
