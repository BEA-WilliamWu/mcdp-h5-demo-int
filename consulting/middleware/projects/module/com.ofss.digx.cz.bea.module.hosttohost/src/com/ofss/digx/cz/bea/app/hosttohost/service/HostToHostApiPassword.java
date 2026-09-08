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

/** Implements BCOH2H-788, BCOH2H-790, and the BCOH2H-1204 success event. */
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
}
