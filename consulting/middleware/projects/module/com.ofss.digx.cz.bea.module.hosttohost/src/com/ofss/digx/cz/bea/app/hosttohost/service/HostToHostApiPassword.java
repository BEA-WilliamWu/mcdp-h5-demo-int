package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordPolicyDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfile;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfileKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserProfileRepository;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.infra.crypto.service.AsymmetricCryptographyProviderFactory;
import com.ofss.digx.infra.crypto.spi.IAsymmetricCryptographyProvider;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordActivityLogDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordCodeResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordGenerateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordRevealDTO;
import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordCrypto;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.common.constants.UserExtensionDataConstants;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCodeKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordCodeRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthApiPasswordCodeRepositoryAdapter;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.Calendar;
import org.apache.commons.lang3.ObjectUtils;

/** Shared BCOH2H-787 code lifecycle and 788/790/1204 password self service. */
public class HostToHostApiPassword extends AbstractApplication
    implements IHostToHostApiPassword {
  private static final String THIS_COMPONENT_NAME = HostToHostApiPassword.class.getName();
  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();
  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);
  private static final String STATUS_SERVICE = THIS_COMPONENT_NAME + ".status";
  private static final String SETUP_SERVICE = THIS_COMPONENT_NAME + ".setup";
  private static final String RESET_SERVICE = THIS_COMPONENT_NAME + ".reset";
  private static final String SETUP = "SETUP";
  private static final String RESET = "RESET";
  private static final String SETUP_EVENT = "HTH_API_PASSWORD_SETUP_SUCCESS";
  private static final String ADAPTER_CATEGORY = "HthApiCredentialAdapterConfig";
  private static final String FEATURE_ENABLED = "HTH_API_PASSWORD.ENABLED";
  private static final String ADAPTER_CLASS = "HTH_API_PASSWORD.ADAPTER_CLASS";
  private static final String POLICY_MIN_LENGTH = "HTH_API_PASSWORD.POLICY_MIN_LENGTH";
  private static final String POLICY_MAX_LENGTH = "HTH_API_PASSWORD.POLICY_MAX_LENGTH";
  private static final String POLICY_NUMERIC_REQUIRED =
      "HTH_API_PASSWORD.POLICY_NUMERIC_REQUIRED";
  private static final String POLICY_ALPHABET_REQUIRED =
      "HTH_API_PASSWORD.POLICY_ALPHABET_REQUIRED";
  private static final String POLICY_SPECIAL_ALLOWED =
      "HTH_API_PASSWORD.POLICY_SPECIAL_ALLOWED";
  private static final String POLICY_SPACES_ALLOWED =
      "HTH_API_PASSWORD.POLICY_SPACES_ALLOWED";
  private static final String DEFAULT_ADAPTER =
      "com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.HthApiCredentialAdapter";

  private static final String GENERATE_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate";

  private static final String MASKED_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked";

  private static final String REVEAL_SERVICE_ID =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal";

  private static final String STATUS_PENDING = "PENDING";

  private static final String STATUS_ACTIVE = "ACTIVE";

  private static final String STATUS_EXPIRED = "EXPIRED";

  private static final String OBJECT_ACTIVE = "A";

  private static final String MASKED_CODE = "******";

  private static final int EXPIRY_HOURS = 24;

  private static final int CODE_LENGTH = 6;


  private static final long MILLIS_PER_HOUR = 3600000L;

  private static final String ACTIVITY_HTH_API_PASSWORD_APPROVED =
      "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval";

  private static final String DATE_FORMAT_PATTERN = "yyyy/MM/dd HH:mm";


  private final HthApiPasswordStore store = new HthApiPasswordStore();

  @Override
  @Entitlement(name = "View HTH API Password Status", action = ActionType.VIEW,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  public HostToHostApiPasswordResponseDTO status(SessionContext sessionContext) throws Exception {
    super.checkAccessPolicy(STATUS_SERVICE, sessionContext);
    Interaction.begin(sessionContext);
    HostToHostApiPasswordResponseDTO response = response();
    try {
      if (!isFeatureEnabled()) {
        response.setSetupState("NOT_APPLICABLE");
      } else {
        Identity identity = identity(sessionContext, false);
        if (identity == null) {
          response.setSetupState("NOT_APPLICABLE");
        } else {
          String state = credentialState(identity, true);
          if (IHthApiCredentialAdapter.STATUS_ACTIVE.equals(state)) {
            response.setSetupState("ACTIVE");
            response.setResetAllowed(store.hasUsableCode(identity.partyId, identity.userId, RESET));
          } else if (IHthApiCredentialAdapter.STATUS_LOCKED.equals(state)) {
            response.setSetupState("LOCKED");
          } else {
            response.setSetupState(store.hasUsableCode(identity.partyId, identity.userId, SETUP)
                ? "REQUIRED" : "CODE_REQUIRED");
          }
        }
      }
    } finally {
      Interaction.close();
    }
    super.checkResponsePolicy(sessionContext, response);
    return response;
  }

  @Override
  @Entitlement(name = "Set Up HTH API Password", action = ActionType.PERFORM,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "CM_N_HAP_SETUP", parent = "CM", name = "Set Up HTH API Password",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.SMS,
      aspects = {TaskAspect.AUDIT}, type = TaskType.NONFINANCIAL_TRANSACTION)
  public HostToHostApiPasswordResponseDTO setup(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception {
    return change(sessionContext, request, SETUP, SETUP_SERVICE);
  }

  @Override
  @Entitlement(name = "Reset HTH API Password", action = ActionType.PERFORM,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "CM_N_HAP_RESET", parent = "CM", name = "Reset HTH API Password",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.SMS,
      aspects = {TaskAspect.AUDIT}, type = TaskType.NONFINANCIAL_TRANSACTION)
  public HostToHostApiPasswordResponseDTO reset(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception {
    return change(sessionContext, request, RESET, RESET_SERVICE);
  }

  private HostToHostApiPasswordResponseDTO change(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request, String operation, String serviceId)
      throws Exception {
    super.checkAccessPolicy(serviceId, sessionContext, request);
    if (!isFeatureEnabled()) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
    }
    validateRequest(request);
    Interaction.begin(sessionContext);
    HostToHostApiPasswordResponseDTO response = response();
    String password = null;
    String code = null;
    String codeId = null;
    try {
      Identity identity = identity(sessionContext, true);
      HthApiPasswordStore.OperationResult previous = store.findSuccessfulOperation(
          request.getRequestId(), identity.partyId, identity.userId, operation);
      if (previous != null) {
        if ("SUCCESS".equals(previous.status)) {
          response.setSetupState("ACTIVE");
          response.getStatus().setReferenceNumber(previous.referenceNumber);
          response.getStatus().setExternalReferenceNumber(previous.referenceNumber);
        } else if ("IN_PROGRESS".equals(previous.status) || "UNKNOWN".equals(previous.status)) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
        }
      }
      if (previous == null || !"SUCCESS".equals(previous.status)) {
        // UAM is authoritative for write preconditions. Never use a stale local projection here.
        String state = credentialState(identity, false);
        if (SETUP.equals(operation) && IHthApiCredentialAdapter.STATUS_ACTIVE.equals(state)) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_005");
        }
        if (RESET.equals(operation) && !IHthApiCredentialAdapter.STATUS_ACTIVE.equals(state)) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_006");
        }

        List<String> credentials = decryptCredentials(request.getEncryptedCredentials());
        if (credentials == null || credentials.size() < 2) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
        }
        password = credentials.get(0);
        code = credentials.get(1);
        validatePassword(password);
        codeId = store.validateAndReserveCode(identity.partyId, identity.userId, operation,
            code, request.getRequestId());

        String reference;
        try {
          reference = SETUP.equals(operation)
              ? adapter().setup(identity.partyId, identity.userId, identity.uamClientId, password,
                  request.getRequestId())
              : adapter().reset(identity.partyId, identity.userId, identity.uamClientId, password,
                  request.getRequestId());
        } catch (Exception e) {
          store.fail(identity.userId, request.getRequestId(), codeId, true);
          throw e;
        }
        if (reference == null || reference.trim().length() == 0) {
          reference = request.getRequestId();
        }
        try {
          store.complete(identity.partyId, identity.userId, operation, request.getRequestId(),
              codeId, reference);
        } catch (Exception e) {
          // UAM has already accepted the change. Keep the request non-retryable until the
          // external result is reconciled, otherwise a retry could rotate the password twice.
          try {
            store.fail(identity.userId, request.getRequestId(), codeId, true);
          } catch (Exception reconciliationFailure) {
            LOGGER.log(Level.WARNING,
                "Unable to mark an HTH API password operation for reconciliation",
                reconciliationFailure);
          }
          throw e;
        }
        response.setSetupState("ACTIVE");
        response.setResetAllowed(false);
        response.getStatus().setReferenceNumber(reference);
        response.getStatus().setExternalReferenceNumber(reference);
        if (SETUP.equals(operation)) {
          notifySetupSuccess(sessionContext, identity);
        }
      }
    } finally {
      password = null;
      code = null;
      if (request != null) {
        request.setEncryptedCredentials(null);
      }
      Interaction.close();
    }
    super.checkResponsePolicy(sessionContext, response);
    return response;
  }

  private Identity identity(SessionContext sessionContext, boolean required) throws Exception {
    String partyId = normalize(sessionContext == null ? null
        : sessionContext.getTransactingPartyCode());
    String loginUser = normalize(sessionContext == null ? null : sessionContext.getUserId());
    if (partyId == null || loginUser == null) {
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    String userId = canonicalUser(loginUser, partyId);
    HthUserProfileKey key = new HthUserProfileKey();
    key.setPartyId(partyId);
    key.setCloseId(userId);
    HthUserProfile profile = HthUserProfileRepository.getInstance().read(key);
    if (profile == null) {
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    HthManagement management = new HthManagement().findActiveByPartyId(partyId);
    if (management == null || !"ENABLE".equalsIgnoreCase(management.getHthStatus())
        || normalize(management.getUamClientId()) == null) {
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    return new Identity(partyId, userId, management.getUamClientId());
  }

  private String credentialState(Identity identity, boolean allowLocalFallback) throws Exception {
    try {
      String remote = adapter().getStatus(identity.partyId, identity.userId,
          identity.uamClientId);
      if (IHthApiCredentialAdapter.STATUS_ACTIVE.equals(remote)
          || IHthApiCredentialAdapter.STATUS_NOT_SETUP.equals(remote)
          || IHthApiCredentialAdapter.STATUS_LOCKED.equals(remote)) {
        return remote;
      }
    } catch (Exception e) {
      if (!allowLocalFallback) {
        throw e;
      }
      LOGGER.log(Level.WARNING, FORMATTER.formatMessage(
          "Unable to reconcile HTH API password status for party '%s'; using local projection",
          identity.partyId), e);
    }
    if (!allowLocalFallback) { throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009"); }
    return store.findCredentialState(identity.partyId, identity.userId);
  }

  private boolean isFeatureEnabled() {
    Preferences config = ConfigurationFactory.getInstance().getConfigurations(ADAPTER_CATEGORY);
    return config.getBoolean(FEATURE_ENABLED, false);
  }

  @SuppressWarnings("unchecked")
  private List<String> decryptCredentials(String encryptedCredentials) throws Exception {
    if (normalize(encryptedCredentials) == null) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
    try {
      IAsymmetricCryptographyProvider provider = AsymmetricCryptographyProviderFactory
          .getInstance().getLatestProvider();
      String decrypted = provider.decrypt(encryptedCredentials);
      Class<?> validatorClass = Class.forName(
          "com.ofss.digx.app.sms.service.user.credentials.SaltValidator");
      Object validator = validatorClass.newInstance();
      Method method = validatorClass.getDeclaredMethod("getSeperatePassword", String.class);
      if (!method.isAccessible()) {
        method.setAccessible(true);
      }
      return (List<String>) method.invoke(validator, decrypted);
    } catch (java.lang.Exception e) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
  }

  private void validateRequest(HostToHostApiPasswordRequestDTO request) throws Exception {
    if (request == null || normalize(request.getEncryptedCredentials()) == null
        || normalize(request.getRequestId()) == null) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
    try {
      UUID.fromString(request.getRequestId());
    } catch (IllegalArgumentException e) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
  }

  private void validatePassword(String password) throws Exception {
    HostToHostApiPasswordPolicyDTO policy = passwordPolicy();
    if (password == null || password.length() < policy.getMinLength()
        || password.length() > policy.getMaxLength()) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_004");
    }
    int alphabetCount = 0;
    int numericCount = 0;
    for (int i = 0; i < password.length(); i++) {
      char value = password.charAt(i);
      boolean alphabet = (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
      boolean numeric = value >= '0' && value <= '9';
      if (alphabet) {
        alphabetCount++;
      } else if (numeric) {
        numericCount++;
      } else if (Character.isWhitespace(value)) {
        if (!policy.isSpacesAllowed()) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_004");
        }
      } else if (!policy.isSpecialCharsAllowed()) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_004");
      }
    }
    if (alphabetCount < policy.getAlphabetRequired()
        || numericCount < policy.getNumericRequired()) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_004");
    }
  }

  private IHthApiCredentialAdapter adapter() throws Exception {
    Preferences config = ConfigurationFactory.getInstance().getConfigurations(ADAPTER_CATEGORY);
    String className = config.get(ADAPTER_CLASS, DEFAULT_ADAPTER);
    try {
      return (IHthApiCredentialAdapter) Class.forName(className).newInstance();
    } catch (java.lang.Exception e) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009");
    }
  }

  private void notifySetupSuccess(SessionContext sessionContext, Identity identity) {
    try {
      UserProfUpdateActivityLogDTO log = new UserProfUpdateActivityLogDTO();
      log.setCustomerId(identity.partyId);
      log.setUserId(identity.userId);
      log.setProfileUser(identity.userId);
      super.registerActivityAndGenerateEvent(sessionContext, SETUP_SERVICE, SETUP_EVENT,
          new Date(), log);
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "HTH API password setup succeeded but notification publication failed for party '%s'",
          identity.partyId), e);
    }
  }

  private HostToHostApiPasswordResponseDTO response() {
    HostToHostApiPasswordResponseDTO response = new HostToHostApiPasswordResponseDTO();
    response.setStatus(fetchStatus());
    response.setPasswordPolicy(passwordPolicy());
    return response;
  }

  private HostToHostApiPasswordPolicyDTO passwordPolicy() {
    Preferences config = ConfigurationFactory.getInstance().getConfigurations(ADAPTER_CATEGORY);
    HostToHostApiPasswordPolicyDTO policy = new HostToHostApiPasswordPolicyDTO();
    policy.setMinLength(config.getInt(POLICY_MIN_LENGTH, 8));
    policy.setMaxLength(config.getInt(POLICY_MAX_LENGTH, 16));
    policy.setNumericRequired(config.getInt(POLICY_NUMERIC_REQUIRED, 2));
    policy.setAlphabetRequired(config.getInt(POLICY_ALPHABET_REQUIRED, 1));
    policy.setSpecialCharsAllowed(config.getBoolean(POLICY_SPECIAL_ALLOWED, false));
    policy.setSpacesAllowed(config.getBoolean(POLICY_SPACES_ALLOWED, false));
    return policy;
  }

  private String canonicalUser(String userId, String partyId) {
    String suffix = "@" + partyId;
    return userId.toUpperCase().endsWith(suffix.toUpperCase())
        ? userId.substring(0, userId.length() - suffix.length()) : userId;
  }

  private String normalize(String value) {
    return value == null || value.trim().length() == 0 ? null : value.trim();
  }

  private static final class Identity {
    private final String partyId;
    private final String userId;
    private final String uamClientId;

    private Identity(String partyId, String userId, String uamClientId) {
      this.partyId = partyId;
      this.userId = userId;
      this.uamClientId = uamClientId;
    }
  }
  /**
   * Generates a one-time setup code while the maker fills the original user-maintenance form.
   *
   * <p>Prior PENDING rows for the same user are superseded (Re-Generate semantics). The new row
   * stays PENDING — and therefore unusable — until the original user-maintenance transaction is
   * approved, at which point {@link #activateOnUserApproval(String, String)} anchors the expiry.
   * The plaintext is returned exactly once for the reminder dialog.
   */
  @Override
  @Entitlement(name = "Generate Host To Host API Password Code", action = ActionType.PERFORM,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "UAT_N_HAP_GEN", parent = "UAT", name = "HTH API Password - Generate",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
      aspects = {TaskAspect.AUDIT, TaskAspect.BLACKOUT},
      type = TaskType.ADMINISTRATION)
  public HthApiPasswordCodeResponseDTO generate(SessionContext sessionContext,
      HthApiPasswordGenerateDTO requestDTO) throws Exception {
    super.checkAccessPolicy(GENERATE_SERVICE_ID, sessionContext, requestDTO);
    HthApiPasswordCodeResponseDTO response = new HthApiPasswordCodeResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    Interaction.begin(sessionContext);
    try {
      String partyId = normalize(requestDTO == null ? null : requestDTO.getPartyId());
      String userName = normalize(requestDTO == null ? null : requestDTO.getUserName());
      if (partyId == null || userName == null) {
        throw new Exception("DIGX_CZ_HTH_PW_009");
      }
      requireCodeOwner(sessionContext, partyId);
      userName = canonicalUser(userName, partyId);
      String purpose = normalize(requestDTO.getPurpose());
      purpose = purpose == null ? SETUP : purpose;
      if (!SETUP.equals(purpose) && !RESET.equals(purpose)) {
        throw new Exception("DIGX_CZ_HTH_PW_009");
      }
      // Validate key configuration before superseding an earlier pending code.
      HthApiPasswordCrypto.codeCipherKey();
      String operator = readUserId(sessionContext);
      LocalHthApiPasswordCodeRepositoryAdapter adapter =
          LocalHthApiPasswordCodeRepositoryAdapter.getInstance();
      store.retirePendingCodes(partyId, userName, purpose, operator);
      String plaintext = HthApiPasswordCrypto.randomDigits(CODE_LENGTH);
      HthApiPasswordCode row = new HthApiPasswordCode();
      row.setKey(key(UUID.randomUUID().toString()));
      row.setPartyId(partyId);
      row.setUserName(userName);
      row.setPurpose(purpose);
      row.setCodeCipher(HthApiPasswordCrypto.encrypt(plaintext, HthApiPasswordCrypto.codeCipherKey()));
      row.setStatus(STATUS_PENDING);
      row.setAttemptCount(Integer.valueOf(0));
      row.setObjectStatus(OBJECT_ACTIVE);
      row.setCreatedBy(operator);
      row.setLastUpdatedBy(operator);
      adapter.create(row);

      response.setCodeId(row.getKey().getId());
      response.setCode(plaintext);
      fillMaskedResponse(response, row, false);
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while generating HTH API password code for user '%s'",
          requestDTO == null ? null : requestDTO.getUserName()), e);
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

  /** Masked lifecycle view; never exposes the plaintext code. */
  @Override
  @Entitlement(name = "Read Host To Host API Password Code", action = ActionType.VIEW,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  public HthApiPasswordCodeResponseDTO masked(SessionContext sessionContext, String partyId,
      String userName) throws Exception {
    super.checkAccessPolicy(MASKED_SERVICE_ID, sessionContext);
    HthApiPasswordCodeResponseDTO response = new HthApiPasswordCodeResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    Interaction.begin(sessionContext);
    try {
      requireCodeOwner(sessionContext, normalize(partyId));
      if (normalize(userName) == null) { throw new Exception("DIGX_CZ_HTH_PW_009"); }
      userName = canonicalUser(normalize(userName), normalize(partyId));
      HthApiPasswordCode latest = HthApiPasswordCodeRepository.getInstance()
          .findLatestByOwner(normalize(partyId), normalize(userName));
      if (latest != null) {
        fillMaskedResponse(response, latest, true);
      }
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while reading masked HTH API password code for user '%s'", userName), e);
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
   * Authorized plaintext reveal. Access is controlled by the reveal task entitlement; every call
   * is recorded by the audit aspect of {@code UAT_N_HAP_RVL}.
   */
  @Override
  @Entitlement(name = "Reveal Host To Host API Password Code", action = ActionType.PERFORM,
      requiredResources = {})
  @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE,
      subCategory = EntitlementSubCategory.Party_Preference)
  @Task(id = "UAT_N_HAP_RVL", parent = "UAT", name = "HTH API Password - Reveal",
      supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE,
      aspects = {TaskAspect.AUDIT, TaskAspect.BLACKOUT},
      type = TaskType.ADMINISTRATION)
  public HthApiPasswordCodeResponseDTO reveal(SessionContext sessionContext,
      HthApiPasswordRevealDTO requestDTO) throws Exception {
    super.checkAccessPolicy(REVEAL_SERVICE_ID, sessionContext, requestDTO);
    HthApiPasswordCodeResponseDTO response = new HthApiPasswordCodeResponseDTO();
    response.setStatus(fetchStatus());
    TransactionStatus transactionStatus = fetchTransactionStatus();
    Interaction.begin(sessionContext);
    try {
      String codeId = normalize(requestDTO == null ? null : requestDTO.getCodeId());
      HthApiPasswordCode row = codeId == null ? null
          : LocalHthApiPasswordCodeRepositoryAdapter.getInstance().read(key(codeId));
      if (row == null || !OBJECT_ACTIVE.equals(row.getObjectStatus())) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      requireCodeOwner(sessionContext, row.getPartyId());
      fillMaskedResponse(response, row, true);
      response.setCode(HthApiPasswordCrypto.decrypt(row.getCodeCipher(), HthApiPasswordCrypto.codeCipherKey()));
      response.setStatus(buildStatus(transactionStatus));
    } catch (Exception e) {
      fillTransactionStatus(transactionStatus, e);
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception while revealing HTH API password code '%s' by user '%s'",
          requestDTO == null ? null : requestDTO.getCodeId(),
          sessionContext == null ? null : sessionContext.getUserId()), e);
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
   * Activates the code referenced by a user-maintenance snapshot when the original flow reaches
   * approval; called from {@code UserExtensionData.create}/{@code update}. Idempotent for
   * approval re-entry. A regeneration supersedes any earlier ACTIVE code of the same user.
   */
  @Override
  public void activateOnUserApproval(String codeId, String operator) throws Exception {
    HthApiPasswordCode row = LocalHthApiPasswordCodeRepositoryAdapter.getInstance().read(key(codeId));
    if (row == null) { throw new Exception("DIGX_CZ_HTH_PW_008"); }
    activateOnUserApproval(codeId, operator, row.getPartyId(), row.getUserName());
  }

  /** Bind the submitted code to the actual user being approved, not just a client-supplied id. */
  public void activateOnUserApproval(String codeId, String operator, String partyId,
      String userName) throws Exception {
    HthApiPasswordCode row = LocalHthApiPasswordCodeRepositoryAdapter.getInstance().read(key(codeId));
    if (row == null || normalize(partyId) == null || normalize(userName) == null
        || !partyId.equals(row.getPartyId())
        || !canonicalUser(userName, partyId).equals(canonicalUser(row.getUserName(), partyId))) {
      throw new Exception("DIGX_CZ_HTH_PW_008");
    }
    String transactionId = readTransactionId();
    if (store.activateApprovedCode(codeId, partyId, row.getUserName(), row.getPurpose(),
        operator, transactionId, EXPIRY_HOURS)) {
      row.setStatus(STATUS_ACTIVE);
      row.setExpiryTime(new Date(new java.util.Date(
          System.currentTimeMillis() + EXPIRY_HOURS * MILLIS_PER_HOUR)));
      row.setTransactionId(transactionId);
      notifyHthApiPasswordApproved(row);
    }
  }

  private void requireCodeOwner(SessionContext sessionContext, String partyId) throws Exception {
    if (partyId == null || sessionContext == null
        || !partyId.equals(normalize(sessionContext.getTransactingPartyCode()))) {
      throw new Exception("DIGX_CZ_HTH_PW_008");
    }
  }

  /**
   * Sends notification to the HTH API User and their company when the API Password Code has been
   * approved (status transitioned from PENDING to ACTIVE). Follows the same dispatch mechanism as
   * {@code HostToHostManagement.notifyHostToHostManagement}.
   *
   * <p>Notification publication is best-effort. Code activation itself remains part of the
   * original user-maintenance transaction and is rolled back if that approval execution fails.
   */
  private void notifyHthApiPasswordApproved(HthApiPasswordCode row) {
    try {
      LOGGER.log(Level.FINE, FORMATTER.formatMessage(
          "[HTH-PWD-NOTIFY] Start notification for codeId=%s, partyId=%s, userName=%s",
          row.getKey().getId(), row.getPartyId(), row.getUserName()));

      IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
          .getAdapterFactory(CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
      IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
          .getAdapter(CommonAdapterConstants.USER_EXTENSION_ADAPTER);
      CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(row.getPartyId());

      NotificationDetail detail = buildHthApiPasswordNotification(partyDetails);
      if (ObjectUtils.isEmpty(detail.getDestination())) {
        LOGGER.log(Level.WARNING, FORMATTER.formatMessage(
            "[HTH-PWD-NOTIFY] No dispatch destination for partyId=%s; skipping notification",
            row.getPartyId()));
        return;
      }

      NotificationDetail[] details = new NotificationDetail[1];
      details[0] = detail;

      HthApiPasswordActivityLogDTO activityLog = new HthApiPasswordActivityLogDTO();
      activityLog.setCustomerId(row.getPartyId());
      activityLog.setNotificationDetails(details);

      Calendar expiryCal = Calendar.getInstance();
      if (row.getExpiryTime() != null) {
        expiryCal.setTimeInMillis(row.getExpiryTime().getMillis());
      }

      activityLog.setHthApiPasswordPartyId(row.getPartyId());
      activityLog.setHthApiPasswordUserName(row.getUserName());
      activityLog.setHthApiPasswordExpiryDateTime(new java.text.SimpleDateFormat(DATE_FORMAT_PATTERN).format(expiryCal.getTime()));
      activityLog.setHthApiPasswordExpiryYear(String.valueOf(expiryCal.get(Calendar.YEAR)));
      activityLog.setHthApiPasswordExpiryMonth(
          String.valueOf(expiryCal.get(Calendar.MONTH) + 1));
      activityLog.setHthApiPasswordExpiryDay(
          String.valueOf(expiryCal.get(Calendar.DAY_OF_MONTH)));
      activityLog.setHthApiPasswordExpiryHour(
          String.valueOf(expiryCal.get(Calendar.HOUR_OF_DAY)));
      activityLog.setHthApiPasswordExpiryMinute(
          String.valueOf(expiryCal.get(Calendar.MINUTE)));
      activityLog.setHthApiPasswordExpirySecond(
          String.valueOf(expiryCal.get(Calendar.SECOND)));

      String activityId = ACTIVITY_HTH_API_PASSWORD_APPROVED;
      String eventId = UserExtensionDataConstants.HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT;

      LOGGER.log(Level.FINE, FORMATTER.formatMessage(
          "[HTH-PWD-NOTIFY] Registering activity: activityId=%s, eventId=%s, partyId=%s",
          activityId, eventId, row.getPartyId()));

      super.registerActivityAndGenerateEvent(null, activityId, eventId,
          new Date(), activityLog);

      LOGGER.log(Level.FINE, FORMATTER.formatMessage(
          "[HTH-PWD-NOTIFY] Notification submitted for codeId=%s, partyId=%s",
          row.getKey().getId(), row.getPartyId()));
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "[HTH-PWD-NOTIFY] Failed to send notification for codeId=%s, partyId=%s",
          row.getKey().getId(), row.getPartyId()), e);
    } catch (RuntimeException rte) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "[HTH-PWD-NOTIFY] RuntimeException while sending notification for codeId=%s",
          row.getKey().getId()), rte);
    }
  }

  /**
   * Builds the {@link NotificationDetail} for the HTH API Password Code approved notification.
   * Prefers email over SMS, mirroring {@code HostToHostManagement.buildNotification}.
   */
  private NotificationDetail buildHthApiPasswordNotification(CZPartyPreferenceDTO partyDetails) {
    NotificationDetail detail = new NotificationDetail();

    if (!isBlank(partyDetails.getOfficeEmailId())) {
      detail.setDestination(DestinationType.EMAIL);
      detail.setDispatchAddress(partyDetails.getOfficeEmailId());
    } else if (!isBlank(partyDetails.getOfficeTelNo())) {
      detail.setDestination(DestinationType.SMS);
      detail.setDispatchAddress(partyDetails.getOfficeTelNo());
    }
    detail.setRecipientType(SubscriberType.EXTERNAL.toString());
    detail.setRecipientId(partyDetails.getPartyIdValue());

    return detail;
  }

  private boolean isBlank(String value) {
    if (value == null) {
      return true;
    }
    return value.trim().isEmpty();
  }

  private void fillMaskedResponse(HthApiPasswordCodeResponseDTO response, HthApiPasswordCode row,
      boolean applyExpiry) {
    response.setCodeId(row.getKey().getId());
    response.setPurpose(row.getPurpose());
    response.setMaskedCode(MASKED_CODE);
    response.setCodeStatus(applyExpiry && isExpired(row) ? STATUS_EXPIRED : row.getStatus());
    if (row.getExpiryTime() != null) {
      response.setExpiryTime(new java.util.Date(row.getExpiryTime().getMillis()));
    }
    response.setExpiryHours(Integer.valueOf(EXPIRY_HOURS));
    response.setCanReveal(Boolean.valueOf(true));
  }

  private boolean isExpired(HthApiPasswordCode row) {
    return STATUS_ACTIVE.equals(row.getStatus()) && row.getExpiryTime() != null
        && row.getExpiryTime().getMillis() < System.currentTimeMillis();
  }

  private HthApiPasswordCodeKey key(String id) {
    HthApiPasswordCodeKey key = new HthApiPasswordCodeKey();
    key.setId(id);
    return key;
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

  private String readUserId(SessionContext sessionContext) {
    return sessionContext == null || normalize(sessionContext.getUserId()) == null
        ? "system" : sessionContext.getUserId();
  }

}
