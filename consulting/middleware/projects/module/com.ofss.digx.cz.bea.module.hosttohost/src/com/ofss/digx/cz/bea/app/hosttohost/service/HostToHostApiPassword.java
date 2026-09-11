package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordPolicyDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordResponseDTO;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.Calendar;

import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordCredentialRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordStateRepository;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthApiPasswordOperationRepository;
import com.ofss.fc.infra.jdbc.ConnectionUtil;
import javax.transaction.TransactionManager;
import weblogic.transaction.TransactionHelper;

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
  private static final String RESET_EVENT = "HTH_API_PASSWORD_RESET_SUCCESS";
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


  private final HthApiPasswordCodeRepository codeRepository =
      HthApiPasswordCodeRepository.getInstance();
  private final HthApiPasswordCredentialRepository credentialRepository =
      HthApiPasswordCredentialRepository.getInstance();
  private final HthApiPasswordStateRepository stateRepository =
      HthApiPasswordStateRepository.getInstance();
  private final HthApiPasswordOperationRepository operationRepository =
      HthApiPasswordOperationRepository.getInstance();

  private static final String STATE_ACTIVE = "ACTIVE";
  private static final String STATE_NOT_SETUP = "NOT_SETUP";

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
        HthApiPasswordStorage storage = storageBackend();
        Identity identity = identity(sessionContext, false, storage);
        if (identity == null) {
          response.setSetupState("NOT_APPLICABLE");
        } else {
          String state = credentialState(identity, true, storage);
          if (IHthApiCredentialAdapter.STATUS_ACTIVE.equals(state)) {
            response.setSetupState("ACTIVE");
            response.setResetAllowed(hasUsableCode(identity.partyId, identity.userId, RESET));
          } else if (IHthApiCredentialAdapter.STATUS_LOCKED.equals(state)) {
            response.setSetupState("LOCKED");
          } else if (IHthApiCredentialAdapter.STATUS_UNKNOWN.equals(state)) {
            response.setSetupState("UNKNOWN");
          } else {
            response.setSetupState(hasUsableCode(identity.partyId, identity.userId, SETUP)
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
      HthApiPasswordStorage storage = storageBackend();
      Identity identity = identity(sessionContext, true, storage);
      OperationResult previous = findSuccessfulOperation(
          request.getRequestId(), identity.partyId, identity.userId, operation, storage.name());
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
        // Read the selected authoritative store, never another backend on failure.
        String state = credentialState(identity, false, storage);
        if (SETUP.equals(operation) && !IHthApiCredentialAdapter.STATUS_NOT_SETUP.equals(state)) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_005");
        }
        if (RESET.equals(operation) && !IHthApiCredentialAdapter.STATUS_ACTIVE.equals(state)) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_006");
        }

        List<String> credentials = decryptCredentials(request.getEncryptedCredentials(), request.getRequestId(), operation);
        if (credentials == null || credentials.size() < 2) {
          throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
        }
        password = credentials.get(0);
        code = credentials.get(1);
        validatePassword(password);
        String passwordHash = storage == HthApiPasswordStorage.DATABASE
            ? com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordHash.hash(password) : null;
        codeId = validateAndReserveCode(identity.partyId, identity.userId, operation,
            code, request.getRequestId(), storage.name());

        String reference = request.getRequestId();
        if (storage == HthApiPasswordStorage.DATABASE) {
          try {
            completeDatabase(identity.partyId, identity.userId, operation,
                request.getRequestId(), codeId, reference, passwordHash);
          } catch (Exception e) {
            // A connection/commit failure can be indeterminate. Never release a possibly used code.
            try { fail(identity.userId, request.getRequestId(), codeId, true); }
            catch (Exception failure) { e.addSuppressed(failure); }
            throw e;
          }
        } else {
          try {
            reference = SETUP.equals(operation)
                ? adapter().setup(identity.partyId, identity.userId, identity.uamClientId, password,
                    request.getRequestId())
                : adapter().reset(identity.partyId, identity.userId, identity.uamClientId, password,
                    request.getRequestId());
          } catch (Exception e) {
            fail(identity.userId, request.getRequestId(), codeId, true);
            throw e;
          }
          if (reference == null || reference.trim().length() == 0) {
            reference = request.getRequestId();
          }
          try {
            complete(identity.partyId, identity.userId, operation, request.getRequestId(),
                codeId, reference);
          } catch (Exception e) {
            // UAM has already accepted the change. Keep the request non-retryable until the
            // external result is reconciled, otherwise a retry could rotate the password twice.
            try {
              fail(identity.userId, request.getRequestId(), codeId, true);
            } catch (Exception reconciliationFailure) {
              LOGGER.log(Level.WARNING,
                  "Unable to mark an HTH API password operation for reconciliation",
                  reconciliationFailure);
            }
            throw e;
          }
        }
        response.setSetupState("ACTIVE");
        response.setResetAllowed(false);
        response.getStatus().setReferenceNumber(reference);
        response.getStatus().setExternalReferenceNumber(reference);
        if (SETUP.equals(operation)) {
          notifySetupSuccess(sessionContext, identity);
        } else if (RESET.equals(operation)) {
          notifyResetSuccess(sessionContext, identity);
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

  private Identity identity(SessionContext sessionContext, boolean required,
      HthApiPasswordStorage storage) throws Exception {
    String partyId = normalize(sessionContext == null ? null
        : sessionContext.getTransactingPartyCode());
    String loginUser = normalize(sessionContext == null ? null : sessionContext.getUserId());
    if (partyId == null || loginUser == null) {
      LOGGER.log(Level.WARNING,
          "HTH_API_PASSWORD eligibility: reason=MISSING_SESSION_IDENTITY, partyId={0}, loginUser={1}, storage={2}",
          new Object[] {partyId, loginUser, storage});
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    String userId = canonicalUser(loginUser, partyId);
    HthUserProfileKey key = new HthUserProfileKey();
    key.setPartyId(partyId);
    // User profiles retain the OBDX login key; password and Code records use the canonical user ID.
    key.setCloseId(loginUser);
    HthUserProfile profile = HthUserProfileRepository.getInstance().read(key);
    if (profile == null && !loginUser.equals(userId)) {
      HthUserProfileKey canonicalKey = new HthUserProfileKey();
      canonicalKey.setPartyId(partyId);
      canonicalKey.setCloseId(userId);
      profile = HthUserProfileRepository.getInstance().read(canonicalKey);
    }
    if (profile == null) {
      LOGGER.log(Level.WARNING,
          "HTH_API_PASSWORD eligibility: reason=USER_PROFILE_NOT_FOUND, partyId={0}, loginUser={1}, canonicalUserId={2}, storage={3}",
          new Object[] {partyId, loginUser, userId, storage});
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    HthManagement management = new HthManagement().findActiveByPartyId(partyId);
    if (management == null || !"ENABLE".equalsIgnoreCase(management.getHthStatus())
        || (storage == HthApiPasswordStorage.UAM && normalize(management.getUamClientId()) == null)) {
      String reason = management == null ? "ACTIVE_MANAGEMENT_NOT_FOUND"
          : !"ENABLE".equalsIgnoreCase(management.getHthStatus()) ? "HTH_NOT_ENABLED"
          : "UAM_CLIENT_NOT_CONFIGURED";
      LOGGER.log(Level.WARNING,
          "HTH_API_PASSWORD eligibility: reason={0}, partyId={1}, loginUser={2}, canonicalUserId={3}, storage={4}, hthStatus={5}",
          new Object[] {reason, partyId, loginUser, userId, storage,
              management == null ? null : management.getHthStatus()});
      if (required) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_008");
      }
      return null;
    }
    return new Identity(partyId, userId, management.getUamClientId());
  }

  private String credentialState(Identity identity, boolean allowLocalFallback,
      HthApiPasswordStorage storage) throws Exception {
    if (storage == HthApiPasswordStorage.DATABASE) {
      return findDatabaseCredentialState(identity.partyId, identity.userId);
    }
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
          "Unable to reconcile HTH API password status for party '%s'; returning UNKNOWN",
          identity.partyId), e);
    }
    if (!allowLocalFallback) { throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009"); }
    return IHthApiCredentialAdapter.STATUS_UNKNOWN;
  }

  private HthApiPasswordStorage storageBackend() throws Exception {
    try {
      return HthApiPasswordStorage.parse(ConfigurationFactory.getInstance()
          .getConfigurations(ADAPTER_CATEGORY).get("HTH_API_PASSWORD.STORAGE_BACKEND", "DATABASE"));
    } catch (IllegalArgumentException e) { throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009"); }
  }

  private boolean isFeatureEnabled() {
    Preferences config = ConfigurationFactory.getInstance().getConfigurations(ADAPTER_CATEGORY);
    boolean enabled = config.getBoolean(FEATURE_ENABLED, false);
    if (!enabled) {
      LOGGER.log(Level.WARNING,
          "HTH_API_PASSWORD eligibility: reason=FEATURE_DISABLED, category={0}, property={1}, configuredValue={2}",
          new Object[] {ADAPTER_CATEGORY, FEATURE_ENABLED, config.get(FEATURE_ENABLED, "<missing>")});
    }
    return enabled;
  }

  private List<String> decryptCredentials(String encryptedCredentials, String requestId,
      String operation) throws Exception {
    String stage = "DECRYPT";
    try {
      if (encryptedCredentials == null || encryptedCredentials.length() > 8192
          || !encryptedCredentials.matches("[A-Za-z0-9+/]+={0,2}")) {
        throw new IllegalArgumentException();
      }
      IAsymmetricCryptographyProvider provider = AsymmetricCryptographyProviderFactory
          .getInstance().getLatestProvider();
      String decrypted = provider.decrypt(encryptedCredentials);
      stage = "PARSE";
      if (decrypted == null || decrypted.length() > 1024) {
        throw new IllegalArgumentException();
      }
      JsonNode envelope = new ObjectMapper().readTree(decrypted);
      if (envelope == null || !envelope.isArray() || envelope.size() != 5) {
        throw new IllegalArgumentException();
      }
      for (JsonNode item : envelope) {
        if (!item.isTextual()) { throw new IllegalArgumentException(); }
      }
      if (!"HTH1".equals(envelope.get(0).asText())
          || !operation.equals(envelope.get(1).asText())
          || !requestId.equals(envelope.get(2).asText())) {
        throw new IllegalArgumentException();
      }
      return java.util.Arrays.asList(envelope.get(3).asText(), envelope.get(4).asText());
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.WARNING,
          "HTH_API_PASSWORD input: stage={0}, exceptionType={1}",
          new Object[] {stage, e.getClass().getSimpleName()});
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_010");
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
    notifyPasswordSuccess(sessionContext, identity, SETUP_SERVICE, SETUP_EVENT);
  }

  private void notifyResetSuccess(SessionContext sessionContext, Identity identity) {
    notifyPasswordSuccess(sessionContext, identity, RESET_SERVICE, RESET_EVENT);
  }

  /** Uses the BCO successful Login PIN reset channels after credential finalization. */
  private void notifyPasswordSuccess(SessionContext sessionContext, Identity identity,
      String activityId, String eventId) {
    try {
      com.ofss.digx.domain.sms.entity.user.User user =
          readNotificationUser(identity.partyId, identity.userId);
      if (user == null) {
        LOGGER.log(Level.WARNING, "No user recipient for HTH event {0}", eventId);
        return;
      }
      java.util.List<NotificationDetail> details = new java.util.ArrayList<NotificationDetail>();
      addNotificationDestination(details, identity.partyId, user.getEmailId(), DestinationType.EMAIL);
      addNotificationDestination(details, identity.partyId, user.getMobileNumber(), DestinationType.SMS);
      if (details.isEmpty()) {
        LOGGER.log(Level.WARNING, "No registered contacts for HTH event {0}", eventId);
        return;
      }
      HthApiPasswordActivityLogDTO log = new HthApiPasswordActivityLogDTO();
      log.setCustomerId(identity.partyId);
      log.setHthApiPasswordPartyId(identity.partyId);
      log.setHthApiPasswordUserName(identity.userId);
      log.setNotificationDetails(details.toArray(new NotificationDetail[details.size()]));
      super.registerActivityAndGenerateEvent(sessionContext, activityId, eventId, new Date(), log);
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.SEVERE, "Unable to publish HTH notification event " + eventId, e);
    }
  }

  private void addNotificationDestination(java.util.List<NotificationDetail> details,
      String partyId, String address, DestinationType destination) {
    String normalized = normalize(address);
    if (normalized != null) {
      NotificationDetail detail = new NotificationDetail();
      detail.setDestination(destination);
      detail.setDispatchAddress(normalized);
      detail.setRecipientId(partyId);
      detail.setRecipientType(SubscriberType.EXTERNAL.toString());
      details.add(detail);
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
      retirePendingCodes(partyId, userName, purpose, operator);
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
      String normalizedName = normalize(userName);
      String canonicalName = canonicalUser(normalizedName, normalize(partyId));
      // Try canonical form first (QTES2 format, e.g. "WILLIAM5"), then fall back to
      // the original form that may include the "@partyId" suffix (legacy format).
      HthApiPasswordCode latest = HthApiPasswordCodeRepository.getInstance()
          .findLatestByOwner(normalize(partyId), canonicalName);
      if (latest == null && !canonicalName.equals(normalizedName)) {
        latest = HthApiPasswordCodeRepository.getInstance()
            .findLatestByOwner(normalize(partyId), normalizedName);
      }
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
    if (activateApprovedCode(codeId, partyId, row.getUserName(), row.getPurpose(),
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

  /** Publishes the Code-approved user/company emails through the BCO event framework. */
  private void notifyHthApiPasswordApproved(HthApiPasswordCode row) {
    HthApiPasswordActivityLogDTO log = new HthApiPasswordActivityLogDTO();
    log.setCustomerId(row.getPartyId());
    log.setHthApiPasswordPartyId(row.getPartyId());
    log.setHthApiPasswordUserName(row.getUserName());
    Calendar expiry = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Hong_Kong"));
    if (row.getExpiryTime() != null) {
      expiry.setTimeInMillis(row.getExpiryTime().getMillis());
    }
    java.text.SimpleDateFormat expiryFormat = new java.text.SimpleDateFormat(DATE_FORMAT_PATTERN);
    expiryFormat.setTimeZone(expiry.getTimeZone());
    log.setHthApiPasswordExpiryDateTime(expiryFormat.format(expiry.getTime()));
    log.setHthApiPasswordExpiryYear(String.valueOf(expiry.get(Calendar.YEAR)));
    log.setHthApiPasswordExpiryMonth(String.valueOf(expiry.get(Calendar.MONTH) + 1));
    log.setHthApiPasswordExpiryDay(String.valueOf(expiry.get(Calendar.DAY_OF_MONTH)));
    log.setHthApiPasswordExpiryHour(String.valueOf(expiry.get(Calendar.HOUR_OF_DAY)));
    log.setHthApiPasswordExpiryMinute(String.valueOf(expiry.get(Calendar.MINUTE)));
    log.setHthApiPasswordExpirySecond(String.valueOf(expiry.get(Calendar.SECOND)));
    notifyEmailRecipients(null, ACTIVITY_HTH_API_PASSWORD_APPROVED,
        UserExtensionDataConstants.HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT,
        UserExtensionDataConstants.HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT,
        row.getPartyId(), row.getUserName(), log);
  }

  /**
   * Like BCO Logon PIN Code notifications, addresses the user and company by email,
   * without sending a second message to the same mailbox. Contact or publication failures
   * are contained so notification delivery never changes the completed business result.
   */
  private void notifyEmailRecipients(SessionContext context, String activityId,
      String userEvent, String companyEvent, String partyId, String userName,
      HthApiPasswordActivityLogDTO log) {
    String userEmail = null;
    try {
      com.ofss.digx.domain.sms.entity.user.User user = readNotificationUser(partyId, userName);
      if (user != null) {
        userEmail = normalize(user.getEmailId());
      }
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.WARNING, "Unable to resolve HTH notification user contacts", e);
    }
    publishEmail(context, activityId, userEvent, partyId, userEmail, log);

    try {
      IAdapterFactory factory = AdapterFactoryConfigurator.getInstance()
          .getAdapterFactory(CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
      IUserExtensionAdapter adapter = (IUserExtensionAdapter) factory
          .getAdapter(CommonAdapterConstants.USER_EXTENSION_ADAPTER);
      CZPartyPreferenceDTO party = adapter.getPartyPreferences(partyId);
      String companyEmail = party == null ? null : normalize(party.getOfficeEmailId());
      if (companyEmail != null && !companyEmail.equalsIgnoreCase(userEmail)) {
        publishEmail(context, activityId, companyEvent, partyId, companyEmail, log);
      }
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.WARNING, "Unable to resolve HTH notification company contacts", e);
    }
  }

  private void publishEmail(SessionContext context, String activityId, String eventId,
      String partyId, String address, HthApiPasswordActivityLogDTO log) {
    if (isBlank(address)) {
      LOGGER.log(Level.WARNING, "No email recipient for HTH event {0}", eventId);
      return;
    }
    try {
      NotificationDetail detail = new NotificationDetail();
      detail.setDestination(DestinationType.EMAIL);
      detail.setDispatchAddress(address);
      detail.setRecipientId(partyId);
      detail.setRecipientType(SubscriberType.EXTERNAL.toString());
      log.setNotificationDetails(new NotificationDetail[] { detail });
      super.registerActivityAndGenerateEvent(context, activityId, eventId, new Date(), log);
    } catch (java.lang.Exception e) {
      LOGGER.log(Level.SEVERE, "Unable to publish HTH notification event " + eventId, e);
    }
  }

  /** Resolves the real OBDX user key within the owning party, including legacy @party IDs. */
  private com.ofss.digx.domain.sms.entity.user.User readNotificationUser(
      String partyId, String userName) throws Exception {
    if (isBlank(partyId) || isBlank(userName)) {
      return null;
    }
    SessionLease lease = open(false);
    try {
      com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData filter =
          new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
      filter.setCdcNo(partyId);
      List<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData> users =
          filter.listUsersByParty(filter);
      String owner = canonicalUser(userName, partyId);
      String resolvedId = null;
      if (users != null) {
        for (com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData user : users) {
          if (user == null || !partyId.equals(user.getCdcNo())
              || user.getUserExtensionDataKey() == null) {
            continue;
          }
          String id = normalize(user.getUserExtensionDataKey().getUserExtensionKey());
          if (id != null && owner.equalsIgnoreCase(canonicalUser(id, partyId))) {
            if (resolvedId != null && !resolvedId.equals(id)) {
              throw new Exception("DIGX_CZ_HTH_PW_008");
            }
            resolvedId = id;
          }
        }
      }
      if (resolvedId == null) {
        return null;
      }
      UserKey key = new UserKey();
      key.setUserId(resolvedId);
      return new com.ofss.digx.domain.sms.entity.user.User().read(key);
    } finally {
      lease.close(false);
    }
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

  /**
   * Repository operations share a session so credential completion is atomic.
   * Failed Code attempts and reservations use independent transactions to survive API rollback.
   */
  private String findCredentialState(String partyId, String userId) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = stateRepository.findStatus(lease.session, partyId, userId);
      return rows == null || rows.isEmpty() ? STATE_NOT_SETUP : string(rows.get(0));
    } finally {
      lease.close(false);
    }
  }

  /** Reads the authoritative credential state for DATABASE mode. */
  private String findDatabaseCredentialState(String partyId, String userId) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = credentialRepository.findStatus(lease.session, partyId, userId);
      return rows == null || rows.isEmpty() ? STATE_NOT_SETUP : string(rows.get(0));
    } finally {
      lease.close(false);
    }
  }

  private boolean hasUsableCode(String partyId, String userId, String purpose) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = codeRepository.findUsable(lease.session, partyId, userId, purpose);
      return rows != null && !rows.isEmpty();
    } finally {
      lease.close(false);
    }
  }

  private OperationResult findSuccessfulOperation(String requestId, String partyId, String userId,
      String operation, String backend) throws Exception {
    SessionLease lease = open(false);
    try {
      List rows = operationRepository.findResult(lease.session, requestId, partyId, userId, operation);
      if (rows == null || rows.isEmpty()) {
        return null;
      }
      Object[] row = (Object[]) rows.get(0);
      if (!backend.equals(string(row[2]))) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      return new OperationResult(string(row[0]), string(row[1]));
    } finally {
      lease.close(false);
    }
  }

  /** Verifies the Code and reserves it, persisting failed verification attempts independently. */
  private String validateAndReserveCode(String partyId, String userId, String purpose, String code,
      String requestId, String backend) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      List rows = codeRepository.findUsableCipher(lease.session, partyId, userId, purpose);
      if (rows == null || rows.isEmpty()) {
        boolean expired = codeRepository.isLatestCodeExpired(lease.session, partyId, userId, purpose);
        throw new Exception(expired ? "DIGX_CZ_HTH_API_PASSWORD_003" : "DIGX_CZ_HTH_API_PASSWORD_002");
      }
      Object[] row = (Object[]) rows.get(0);
      String codeId = string(row[0]);
      if (!HthApiPasswordCrypto.constantTimeEquals(code,
          HthApiPasswordCrypto.decrypt(string(row[1]), HthApiPasswordCrypto.codeCipherKey()))) {
        LOGGER.log(Level.WARNING, "HTH_API_PASSWORD input: stage=CODE_COMPARE, result=MISMATCH");
        codeRepository.recordFailedAttempt(lease.session, codeId);
        success = true;
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_002");
      }

      if (codeRepository.reserve(lease.session, requestId, codeId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      operationRepository.reserve(lease.session, requestId, partyId, userId, purpose, codeId, backend);
      success = true;
      return codeId;
    } catch (Exception e) {
      if (success) {
        lease.close(true);
        lease.closed = true;
      }
      throw e;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (!lease.closed) {
        lease.close(success);
      }
    }
  }

  /** Records a confirmed UAM result and consumes its reserved Code. */
  private void complete(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber) throws Exception {
    completeInternal(partyId, userId, operation, requestId, codeId, referenceNumber, null);
  }

  /** Commits the password hash, Code consumption and operation result in one transaction. */
  private void completeDatabase(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber, String passwordHash) throws Exception {
    if (passwordHash == null) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_001");
    }
    completeInternal(partyId, userId, operation, requestId, codeId, referenceNumber, passwordHash);
  }

  private void completeInternal(String partyId, String userId, String operation, String requestId,
      String codeId, String referenceNumber, String passwordHash) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      if (codeRepository.consume(lease.session, userId, codeId, requestId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }

      if (passwordHash != null) {
        credentialRepository.write(lease.session, partyId, userId, operation, requestId, passwordHash);
      }

      stateRepository.complete(lease.session, partyId, userId, operation, requestId, referenceNumber);

      if (operationRepository.complete(lease.session, referenceNumber, userId, requestId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      success = true;
    } catch (Exception e) {
      throw e;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }


  /** Retains UNKNOWN reservations for reconciliation when the write outcome is uncertain. */
  private void fail(String userId, String requestId, String codeId, boolean uncertain) throws Exception {
    SessionLease lease = openIndependent();
    boolean success = false;
    try {
      codeRepository.failReservation(lease.session, userId, requestId, codeId, uncertain);

      operationRepository.failReservation(lease.session, userId, requestId, uncertain);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  /** Regeneration must not inactivate a code that became ACTIVE after the maker read it. */
  private void retirePendingCodes(String partyId, String userName, String purpose, String operator)
      throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      codeRepository.retirePending(lease.session, partyId, userName, purpose, operator);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  /** Approval and consume share the same row locks and status constraints. */
  private boolean activateApprovedCode(String codeId, String partyId, String userName, String purpose,
      String operator, String transactionId, int expiryHours) throws Exception {
    SessionLease lease = open(true);
    boolean success = false;
    try {
      List rows = codeRepository.lockForApproval(lease.session, codeId);
      if (rows == null || rows.isEmpty()) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      Object[] row = (Object[]) rows.get(0);
      if (!"A".equals(string(row[1]))) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      String status = string(row[0]);
      if ("ACTIVE".equals(status) || "USED".equals(status)
          || "IN_PROGRESS".equals(status) || "UNKNOWN".equals(status)) {
        success = true;
        return false; // Approval replay does not reactivate the Code or resend its notification.
      }
      if (!"PENDING".equals(status)) {
        throw new Exception("DIGX_CZ_HTH_PW_008");
      }
      codeRepository.retireActive(lease.session, operator, partyId, userName, purpose);
      // The live-code unique index rejects activation while another code is IN_PROGRESS/UNKNOWN.
      if (codeRepository.activate(lease.session, transactionId, expiryHours, operator, codeId) != 1) {
        throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
      }
      success = true;
      return true;
    } catch (java.lang.Exception e) {
      if (e instanceof Exception) {
        throw (Exception) e;
      }
      throw new Exception(e);
    } finally {
      lease.close(success);
    }
  }

  private String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** Persist failed attempts/reservations even when the outer API transaction is rolled back. */
  private SessionLease openIndependent() throws Exception {
    TransactionManager manager = null;
    javax.transaction.Transaction suspended = null;
    Session session = null;
    try {
      if (ConnectionUtil.isConnPooled("DIGX")) {
        manager = TransactionHelper.getTransactionHelper().getTransactionManager();
        suspended = manager.suspend();
      }
      // openNewSession does not replace the outer thread-bound ORM session.
      session = DataAccessManager.getManager().openNewSession("DIGX");
      session.beginTransaction();
      return new SessionLease(session, manager, suspended);
    } catch (java.lang.Exception e) {
      try {
        if (session != null) {
          try {
            if (session.fetchCurrentTransaction() != null && session.fetchCurrentTransaction().isActive()) {
              session.fetchCurrentTransaction().rollback();
            }
          } finally {
            DataAccessManager.getManager().closeSession(session);
          }
        }
      } finally {
        if (manager != null && suspended != null) {
          try { manager.resume(suspended); } catch (java.lang.Exception resumeFailure) {
            e.addSuppressed(resumeFailure);
          }
        }
      }
      throw new Exception(e);
    }
  }

  private SessionLease open(boolean write) throws Exception {
    if (DataAccessManager.getManager().isSessionOpen()) {
      return new SessionLease(DataAccessManager.getManager().fetchCurrentSession(), false, write);
    }
    Session session = DataAccessManager.getManager().openSession("DIGX");
    if (write) {
      session.beginTransaction();
    }
    return new SessionLease(session, true, write);
  }

  private static final class OperationResult {
    final String status;
    final String referenceNumber;

    OperationResult(String status, String referenceNumber) {
      this.status = status;
      this.referenceNumber = referenceNumber;
    }
  }

  private static final class SessionLease {
    private final Session session;
    private final boolean owned;
    private final boolean write;
    private boolean closed;
    private TransactionManager manager;
    private javax.transaction.Transaction suspended;

    private SessionLease(Session session, TransactionManager manager,
        javax.transaction.Transaction suspended) {
      this(session, true, true);
      this.manager = manager;
      this.suspended = suspended;
    }

    private SessionLease(Session session, boolean owned, boolean write) {
      this.session = session;
      this.owned = owned;
      this.write = write;
    }

    private void close(boolean commit) throws Exception {
      if (closed || !owned) {
        return;
      }
      try {
        if (write && commit) {
          session.fetchCurrentTransaction().commit();
        } else if (write && session.fetchCurrentTransaction() != null) {
          session.fetchCurrentTransaction().rollback();
        }
      } catch (java.lang.Exception failure) {
        try {
          if (write && session.fetchCurrentTransaction() != null
              && session.fetchCurrentTransaction().isActive()) {
            session.fetchCurrentTransaction().rollback();
          }
        } catch (java.lang.Exception rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw new Exception(failure);
      } finally {
        closed = true;
        try {
          DataAccessManager.getManager().closeSession(session);
        } finally {
          if (manager != null && suspended != null) {
            try { manager.resume(suspended); } catch (java.lang.Exception e) {
              throw new Exception(e);
            }
          }
        }
      }
    }
  }

}
