package com.ofss.digx.cz.bea.app.sms.service.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.crm.adapter.ICRMAsserterCallAdapter;
import com.ofss.digx.cz.bea.common.framework.crm.CRMConstants;
import com.ofss.digx.cz.bea.common.framework.crm.CRMInputData;
import com.ofss.digx.cz.bea.common.util.CZLocaleUtils;
import org.apache.commons.collections4.CollectionUtils;
import com.ofss.digx.cz.bea.domain.sms.entity.user.*;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ActivationLetter;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance;
import com.ofss.digx.cz.bea.domain.sms.entity.user.repository.adapter.LocalRevokedUserRepositoryAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.NoEntitlement;
import com.ofss.digx.annotations.Role;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupDTO;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupUserDTO;
import com.ofss.digx.app.dto.branchdates.BranchDatesDefinitionDTO;
import com.ofss.digx.app.exception.RunTimeException;
import com.ofss.digx.app.ext.ServiceExtensionFactory;
import com.ofss.digx.app.messages.Message.MessageType;
import com.ofss.digx.app.messages.Status.ResultType;
import com.ofss.digx.app.party.adapter.profile.IPartyPreferencesAdapter;
import com.ofss.digx.app.party.dto.profile.PartyPreferencesDTO;
import com.ofss.digx.app.party.dto.profile.PartyPreferencesResponse;
import com.ofss.digx.app.sms.adapter.user.IUserAdapter;
import com.ofss.digx.app.sms.adapter.user.IUserMeAdapter;
import com.ofss.digx.app.sms.adapter.user.password.policy.IPasswordPolicyAdapter;
import com.ofss.digx.app.sms.assembler.user.UserAssembler;
import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.digx.app.sms.dto.user.UserListResponseDTO;
import com.ofss.digx.app.sms.dto.user.UserPrincipalDTO;
import com.ofss.digx.app.sms.dto.user.UserPrincipalRequestDTO;
import com.ofss.digx.app.sms.dto.user.UserPrincipalResponseDTO;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.app.sms.dto.user.password.PasswordPolicyResponseDTO;
import com.ofss.digx.app.sms.service.user.UserPrincipal;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.common.constants.CommonConstants;
import com.ofss.digx.common.constants.UserManagementErrorConstants;
import com.ofss.digx.core.adapter.AdapterFactory;
import com.ofss.digx.core.adapter.alert.eventgen.IModuleToAlertAdapter;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.LoginUserDetailsDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.LoginUserDetailsResponseDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.SignerUserDetailsDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.SignerUserDetailsResponseDTO;
import com.ofss.digx.cz.bea.app.merchant.dto.MerchantUserMaintenanceDTO;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.security.dto.CheckLoginPinDTO4CDCRequestDto;
import com.ofss.digx.cz.bea.app.security.dto.CheckLoginPinDTO4CDCResponseDto;
import com.ofss.digx.cz.bea.app.security.dto.PublicKeyDTO4CDC;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IValidateUserDetailsAdapter;
import com.ofss.digx.cz.bea.app.sms.assembler.user.SelfResetSignerPinAssembler;
import com.ofss.digx.cz.bea.app.sms.assembler.user.UserExtensionDataAssembler;
import com.ofss.digx.cz.bea.app.sms.assembler.user.UserIdMaintenanceAssembler;
import com.ofss.digx.cz.bea.app.sms.dto.user.AuditLogMigRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.AuditLogMigResponse;
import com.ofss.digx.cz.bea.app.sms.dto.user.CZUserDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.MerchantUserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.MigrationStatusRequestDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.MigrationStatusResponseDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.PasswordExpiryDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetUserDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetUserListResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfResetSignerPinRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfResetSignerPinResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfServChangeSignerPinLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfServChangeSignerPinResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserAlertRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserDetailsUpdationDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionPartyDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserIdMaintenanceKeyDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserIdMaintenanceRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserListExtResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserManagementActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserPartyListDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserTokenDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ValidateUserResponseDTO;
import com.ofss.digx.cz.bea.app.sms.service.user.ext.IUserExtensionDataExtExecutor;
import com.ofss.digx.cz.bea.app.sms.user.dto.credentials.HostCredentialsRequestDTO;
import com.ofss.digx.cz.bea.app.sms.user.dto.credentials.HostCredentialsResponseDTO;
import com.ofss.digx.cz.bea.common.constants.CZCommonConstants;
import com.ofss.digx.cz.bea.common.constants.UserExtensionDataConstants;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.digx.cz.bea.common.util.RSAUtils;
import com.ofss.digx.cz.bea.common.util.UserManagementUtils;
import com.ofss.digx.cz.bea.domain.party.entity.profile.CZPartyPreferences;
import com.ofss.digx.cz.bea.domain.sms.entity.user.policy.ResetSignerPinBusinessPolicyDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.policy.SelfServChangeSignerPinBusinessPolicyDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicyDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataReadBusinessPolicyDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.repository.adapter.IUserExtensionDataRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.sms.entity.user.repository.adapter.IUserIdMaintenanceRepositoryAdapter;
import com.ofss.digx.cz.bea.extxface.fmo.adapter.IFMOHelperCallAdapter;
import com.ofss.digx.cz.bea.extxface.sms.adapter.user.credentials.IHostCredentialsAdapter;
import com.ofss.digx.datatype.complex.Party;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroup;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroupKey;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroupUser;
import com.ofss.digx.domain.party.entity.profile.PartyPreferencesKey;
import com.ofss.digx.domain.sms.entity.policy.UserBusinessPolicyDTO;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.RoleType;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.extxface.adapter.core.ICoreApplicationServiceAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.framework.determinant.DeterminantResolver;
import com.ofss.digx.framework.domain.business.policy.factory.BusinessPolicyFactory;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.ofss.fc.framework.domain.policy.AbstractBusinessPolicy;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.error.ErrorManager;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.infra.validation.error.ValidationError;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.utils.SerializationUtils;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import com.ofss.sms.dbAuthenticator.domain.Users;
import com.ofss.sms.dbAuthenticator.domain.UsersKey;

public class UserExtensionData extends AbstractApplication implements IUserExtensionData {
	private static final String THIS_COMPONENT_NAME = UserExtensionData.class.getName();

	private static final MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();

	private static final Logger logger = formatter.getLogger(THIS_COMPONENT_NAME);

	private transient IUserExtensionDataExtExecutor extensionExecutor = null;

	private static final String CDC_ID_PREFIX = "015";

	private static final String LOGIN_PIN_EXPIRY_EXTEND_DAYS = "LOGIN_PIN_EXPIRY_EXTEND_DAYS";

	private static final String SIGNER_PIN_EXPIRY_EXTEND_DAYS = "SIGNER_PIN_EXPIRY_EXTEND_DAYS";

	private static final String LOGIN = "LOGIN";

	private static final String SIGNER = "SIGNER";

	private static final String SIGNER_REMINDER = "SIGNER_REMINDER";

	private static final String LOGIN_REMINDER = "LOGIN_REMINDER";
	
	private static final String LOGIN_PIN_STATUS_BEING_RESET = "Being Reset";
	private static final String LOGIN_HOLD_REASON_REQ_BY_CUSTOMER = "Requested by Customer";
	private static final String LOGIN_PIN_STATUS_INACTIVE = "Inactive";
	private static final String LOGIN_PIN_STATUS_NORMAL = "Normal";
	private static final String LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED = "No Activity Allowed";
	
	public static final String HOSTUSER_DETAILS_ADAPTER_FACTORY = "HOSTUSER_DETAILS_ADAPTER_FACTORY";
	public static final String HOST_USERDETAILS_INVOCATION_ADAPTER = "HOST_USERDETAILS_INVOCATION_ADAPTER";
	public static final String SUSPEND_EMAIL_REJECTION_ON_PIN_RESET_MERCHANT = "SUSPEND_EMAIL_REJECTION_ON_PIN_RESET_MERCHANT";

	private static final int CONSTANT_FOUR = 4;
	private static final int CONSTANT_FIVE = 5;
	private static final int CONSTANT_SEVEN = 7;
	private static final int CONSTANT_EIGHT = 8;
	private static final int CONSTANT_NINETEEN = 19;
	private static final String ACCOUNT_START_STR = "015";

	private static final String RESET_REJECT_REMARK = "The transaction was rejected due to new application for resetting Signer PIN has been submitted by company's Authorised Person through BEA website/Branch.";

	/**
	 * {@link String} key for day one Configuration.
	 */
	public static final String DAY_ONE_CONFIG = "DayOneConfig";

	/**
	 * Preference contains day one configurations.
	 */
	private static Preferences dayOneConfigPref = ConfigurationFactory.getInstance().getConfigurations(DAY_ONE_CONFIG);

	public UserExtensionData() {
		extensionExecutor = (IUserExtensionDataExtExecutor) ServiceExtensionFactory
				.getServiceExtensionExecutor(THIS_COMPONENT_NAME);
	}
	
//	----------------------- MigratedUserResetPassword changes - STARTS ----------------------- 

	private static boolean isMigratedUserResetPasswordEnabled() {

		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);

		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);

		String migratedUserResetPasswordEnabled = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "MIGRATED_USER_RESET_PASSWORD_ENABLED",
				"false");

		BeaSystemOut.println("\n \n isMigratedUserResetPasswordEnabled : " + migratedUserResetPasswordEnabled + "\n \n");

		return Boolean.parseBoolean(migratedUserResetPasswordEnabled);
	}
//	----------------------- MigratedUserResetPassword changes - ENDS ----------------------- 
	
//	----------------------- MigrationUserOnboardingEdit changes - STARTS ----------------------- 

	private static boolean isMigrationUserOnboardingEditEnabled() {

		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);

		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);

		String MigrationUserOnboardingEditEnabled = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "MIGRATION_USER_ONBOARDING_EDIT_ENABLED",
				"false");

		BeaSystemOut.println(
				"\n \n isMigrationUserOnboardingEditEnabled : " + MigrationUserOnboardingEditEnabled + "\n \n");

		return Boolean.parseBoolean(MigrationUserOnboardingEditEnabled);
	}
//	----------------------- MigrationUserOnboardingEdit changes - ENDS ----------------------- 

	@Override
	@Entitlement(name = "Read UserExtensionData", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Read UserExtensionData", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Read UserExtensionData", action = ActionType.PERFORM, requiredResources = {})
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_RUS", parent = "MT", name = "Read UserExtensionData", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public UserExtensionDataResponseDTO read(SessionContext sessionContext, UserExtensionDataDTO requestDTO)
			throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into read() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read", sessionContext,
				requestDTO);
		UserExtensionDataResponseDTO response = new UserExtensionDataResponseDTO();
		response.setStatus(fetchStatus());
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserItokenInfo userItokenDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserItokenInfo();
		UserKey userKey = new UserKey();
		try {
			extensionExecutor.preRead(sessionContext, requestDTO);
			requestDTO.validate(sessionContext);

			if (sessionContext.getTransactingPartyCode() != null) {
				AbstractBusinessPolicy abstractBusinessPolicy = null;
				BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
				UserExtensionPartyDTO userExtensionPartyDTO = new UserExtensionPartyDTO();
				userExtensionPartyDTO.setCdcNo(requestDTO.getUserExtensionKey().split("@")[1]);
				userExtensionPartyDTO.setSessionPartyId(sessionContext.getTransactingPartyCode());
				UserExtensionDataReadBusinessPolicyDTO policyDTO = new UserExtensionDataReadBusinessPolicyDTO(
						userExtensionPartyDTO);
				abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
						"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.listUsersData", policyDTO);
				abstractBusinessPolicy.validate();
			}

			UserExtensionDataAssembler assembler = new UserExtensionDataAssembler();
			domain = assembler.toDomainObject(requestDTO);
			UserExtensionDataKey userExtensionDataKey = new UserExtensionDataKey();
			userExtensionDataKey.setUserExtensionKey(requestDTO.getUserExtensionKey());
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData extDomain = domain.read(userExtensionDataKey);
			requestDTO = assembler.fromDomainObject(extDomain);
			domain = domain.read(userExtensionDataKey);
			UserDTO channelTypeSearch = new UserDTO();
			channelTypeSearch.setPartyId(new Party(domain.getCdcNo()));
			channelTypeSearch.setUsername(domain.getUserID());
			List<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData> channelTypeData = domain
					.listUsers(channelTypeSearch);
			for (com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData item : channelTypeData) {
				if (item.getUserExtensionDataKey() != null && userExtensionDataKey.getUserExtensionKey()
						.equals(item.getUserExtensionDataKey().getUserExtensionKey())) {
					domain.setUserChannelType(item.getUserChannelType());
					break;
				}
			}
			requestDTO = assembler.fromDomainObject(domain);
			userKey.setUserId(requestDTO.getUserExtensionKey());
			userDomain = userDomain.read(userKey);

			//BCOCDC-7316  if the user is revoked, get email and mobile info from RevokedUser
			if(userDomain.getDeleteStatus()){
				BeaSystemOut.println("UserExtensionDataResponseDTO Read :revoked logic handle");
				LocalRevokedUserRepositoryAdapter localRevokedUserRepositoryAdapter = LocalRevokedUserRepositoryAdapter.getInstance();
				RevokedUserKey revokedUserKey = new RevokedUserKey();
				revokedUserKey.setKey(requestDTO.getUserExtensionKey());
				RevokedUser read = localRevokedUserRepositoryAdapter.read(revokedUserKey);
				BeaSystemOut.println("revoked logic handle revoked user info : " +SerializationUtils.toJsonString(read));
				requestDTO.setRevokedEmailId(read.getUmEmail());
				requestDTO.setRevokedMobileNo(read.getUmMobileNo());
				if(read.getUmEmail() != null && !userDomain.getEmailId().trim().equals("")) {
					requestDTO.setMaskRevokedEmailId(UserManagementUtils.getMaskedEmailID(read.getUmEmail()));
				}
				//phone number combine with that ,need confirm why
				if (read.getUmMobileNo() != null && !read.getCzMobileCode().trim().equals("")) {
					requestDTO.setMaskRevokedMobileNo(UserManagementUtils
							.getMaskedMobileNumber(read.getCzMobileCode() + "-" + read.getUmMobileNo()));
				}
				BeaSystemOut.println("revoked logic handle requestDTO : " + SerializationUtils.toJsonString(requestDTO));
				BeaSystemOut.println("UserExtensionDataResponseDTO Read :revoked logic handle end");
			}
			//BCOCDC-7316

			if(userDomain.getEmailId() != null && !userDomain.getEmailId().trim().equals("")) {
				requestDTO.setMaskedEmailId(UserManagementUtils.getMaskedEmailID(userDomain.getEmailId()));
			}

			if (userDomain.getMobileNumber() != null && requestDTO.getMobileCode() != null
					&& !userDomain.getMobileNumber().trim().equals("")) {
				requestDTO.setMaskedMobileNo(UserManagementUtils
						.getMaskedMobileNumber(requestDTO.getMobileCode() + "-" + userDomain.getMobileNumber()));
			}

			// add user Itoken info 2026-04-03 start
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" UserExtension get Itoken begin ");
			UserTokenDataDTO tokenDto = new UserTokenDataDTO();
			tokenDto = userItokenDomain.getUserItokenInfo(requestDTO.getUserExtensionKey(), extDomain);

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" UserExtension get Itoken status_ : " + tokenDto.getItokenStatus());
			response.setUserTokenDataDTO(tokenDto);
			// add user Itoken info 2026-04-03 end

			// BCM-2549: CDC6906 - View I-Token Status on BCO
			sendITokenViewCrm(sessionContext, requestDTO.getUserExtensionKey(), true);

			response.setUserExtensionDataDTO(requestDTO);
			extensionExecutor.postRead(sessionContext, requestDTO, response);
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from read() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from read() for requestDTO '%s' in class %s", requestDTO,
							THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Exiting from read() : response = %s", response));
		}
		return response;
	}

	private void sendITokenViewCrm(SessionContext sessionContext, String userName, boolean success) throws Exception {
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt add crm sendCrm CDC6906 begin ,"+ sessionContext.getUserId() + " : " + userName);
			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CRM_ADAPTER_FACTORY);
			ICRMAsserterCallAdapter crmAdapter = (ICRMAsserterCallAdapter) adapterFactory.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CRM_ADAPTER);

			// calling fetchCommonInfo to fill in most of the generic fields in CRMInputData, not passing serviceParameters and taskCode won't impact those generic fields
			Preferences crmConfiguration = ConfigurationFactory.getInstance().getConfigurations("CRMConfiguration");
			CRMInputData data = crmAdapter.fetchCommonInfo(null, "", crmConfiguration, null, sessionContext, userName);
			data.setEventActvTypeCode(CRMConstants.CRM_EVENT_VIEW_ITOKEN_STATUS);

			data.setEventStatusCode(success ? "A": "R");
            if (sessionContext.getUserId() != null){
                if (sessionContext.getUserId().contains("@")) {
                    String acctNbr = sessionContext.getUserId().split("@")[1];
                    if (acctNbr !=null){
                        data.setAcctNbr(acctNbr);
                        data.setUserId(sessionContext.getUserId().split("@")[0]);
                    }
                } else {
                    data.setUserId(sessionContext.getUserId());
                }

            }else{
                data.setAcctNbr(userName.split("@")[1]);
            }


			crmAdapter.crmInsertForRaq(data);
		} catch (java.lang.Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt CRMAsserter.crmInsertData( crm" + e);
		}
	}
	
//	public UserIdMaintenanceRequestDTO buildUserIdMaintenanceRequestDTO(UserExtensionDataDTO requestDTO) {
//
//		UserIdMaintenanceKeyDTO userIdMaintenanceKeyDTOKey = new UserIdMaintenanceKeyDTO();
//		UserIdMaintenanceRequestDTO userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();
//		userIdMaintenanceKeyDTOKey.setPartyId(requestDTO.getCdcNo());
//		userIdMaintenanceKeyDTOKey.setId(requestDTO.getLoginID());
//		userIdMaintenanceRequestDTO.setIdKey(userIdMaintenanceKeyDTOKey);
//
//		userIdMaintenanceRequestDTO.setIdToMap(requestDTO.getLoginID());
//		userIdMaintenanceRequestDTO.setPinRefToMap(requestDTO.getLoginPinReferenceNo());
//		userIdMaintenanceRequestDTO.setPinStatus(requestDTO.getLoginPinstatus());
//		userIdMaintenanceRequestDTO.setHoldStatus(requestDTO.getLoginHoldStatus());
//		userIdMaintenanceRequestDTO.setHoldReason(requestDTO.getLoginHoldReason());
//		userIdMaintenanceRequestDTO.setUserName(requestDTO.getUserID());
//		userIdMaintenanceRequestDTO.setIdType("02");
//		
//		
//		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("\n MigrationUserOnboardingEdit buildUserIdMaintenanceRequestDTO ::  "
//				+ "\n requestDTO.getCdcNo(): " + requestDTO.getCdcNo()
//				+ "\n requestDTO.getLoginID() : " + requestDTO.getLoginID()
//				+ "\n requestDTO.getLoginPinReferenceNo() : " + requestDTO.getLoginPinReferenceNo()
//				+ "\n requestDTO.getLoginPinstatus() :" + requestDTO.getLoginPinstatus()
//				+ "\n requestDTO.getLoginHoldReason() :" + requestDTO.getLoginHoldReason()
//				+ "\n requestDTO.getUserID() :" + requestDTO.getUserID()
//				+ "\n userIdMaintenanceRequestDTO.getIdType() :" + userIdMaintenanceRequestDTO.getIdType());
//
//		return userIdMaintenanceRequestDTO;
//	}
	
	public List<UserIdMaintenance> buildUserIdMaintenanceList(UserExtensionDataDTO requestDTO) {
		
		List<UserIdMaintenance> list = new ArrayList<UserIdMaintenance>();
		UserIdMaintenanceKey key = new UserIdMaintenanceKey();
		UserIdMaintenance loginID = new UserIdMaintenance();
		
		loginID.setPinRefNo(requestDTO.getLoginPinReferenceNo());
		loginID.setSubOption("02");
		key.setPartyId(requestDTO.getCdcNo());
		key.setId(requestDTO.getLoginID());
		loginID.setIdType("02");

		Party party = new Party();

		party.setValue(requestDTO.getCdcNo());
		loginID.setIdKey(key);
		loginID.setParty(party);

		list.add(loginID);

		BeaSystemOut.println("\n MigrationUserOnboardingEdit buildUserIdMaintenanceList ::  "
				+ "\n requestDTO.getLoginPinReferenceNo() : " + requestDTO.getLoginPinReferenceNo()
				+ "\n requestDTO.getLoginID() : " + requestDTO.getLoginID() + "\n requestDTO.getCdcNo() : "
				+ requestDTO.getCdcNo());

		return list;
	}
	
	//TODO: isDefaultUserUpdateMigrationStatus()
	
	public boolean isDefaultUserUpdateMigrationStatus(
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain, UserExtensionDataDTO requestDTO,
			String loginPinReferenceNoFromDomain) {

		boolean isDefaultUser = false;

		String loginPinstatus = requestDTO.getLoginPinstatus();
		String loginID = requestDTO.getLoginID();

		boolean newLoginPinReferenceNoEntered = requestDTO.getLoginPinReferenceNo() != null
				&& !(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()));

		BeaSystemOut.println("\n MigrationUserOnboardingEdit updateMigrationStatusForDefaultUser ::  "
				+ "\n loginPinReferenceNoFromDomain : " + loginPinReferenceNoFromDomain
				+ "\n requestDTO.getLoginPinReferenceNo(): " + requestDTO.getLoginPinReferenceNo()
				+ "\n newLoginPinReferenceNoEntered : " + newLoginPinReferenceNoEntered
				+ "\n domain.getMigtationStatus() : " + domain.getMigtationStatus() + "\n domain.getDefaultUser() : "
				+ domain.getDefaultUser() + "\n requestDTO.getLoginPinstatus() : " + loginPinstatus + "\n loginID : "
				+ loginID);

		if (isMigrationUserOnboardingEditEnabled() && domain.getMigtationStatus() != null
				&& !domain.getMigtationStatus().isEmpty() && domain.getMigtationStatus().equalsIgnoreCase("N")
				&& newLoginPinReferenceNoEntered && loginPinstatus != null) {

			// Default User check
			if (domain.getDefaultUser() != null && !domain.getDefaultUser().isEmpty()
					&& domain.getDefaultUser().equalsIgnoreCase("Y") 
					&& loginID != null && !loginID.isEmpty()
					&& (loginID.equalsIgnoreCase("U01") || loginID.equalsIgnoreCase("U02"))) {

				isDefaultUser = true;
			}

			// System Admin User check
			if (domain.getDefaultUser() != null && !domain.getDefaultUser().isEmpty()
					&& domain.getDefaultUser().equalsIgnoreCase("S") && loginID != null) {

				isDefaultUser = true;
			}

		}

		return isDefaultUser;
	}

	@Override
	@Entitlement(name = "Update UserExtensionData", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read" })
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_UUS", parent = "MT", name = "Update UserExtensionData", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public TransactionStatus update(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into update() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update", sessionContext,
				requestDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.app.sms.service.user.User user = new com.ofss.digx.app.sms.service.user.User();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintenanceDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();
		UserIdMaintenanceAssembler userIdMaintenanceAssembler = new UserIdMaintenanceAssembler();
		UserResponseDTO userResponseDTO = new UserResponseDTO();
		UserIdMaintenanceRequestDTO userIdMaintenanceRequestDTO = null;
		com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
		UserKey userKey = new UserKey();
		UserAssembler userAssembler = null;
		boolean isSignerDeleted = false;
		BeaSystemOut.println("Entering update UserExtensionData, update requestDTO:"+SerializationUtils.toJsonString(requestDTO));

		try {
			extensionExecutor.preUpdate(sessionContext, requestDTO);
			requestDTO.validate(sessionContext);
			UserExtensionDataAssembler assembler = new UserExtensionDataAssembler();
			// domain = assembler.toDomainObject(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = null;
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			UserExtensionDataBusinessPolicyDTO policyDTO = new UserExtensionDataBusinessPolicyDTO(requestDTO);
			Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");
			abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
					"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update", policyDTO);
			abstractBusinessPolicy.validate(
					com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicy.MT_USEREXTENSIONDATA_BUSINESS_POLICY_VIOLATION);

			/*
			 * Invoke base service business policy
			 */
			userAssembler = new UserAssembler();
			com.ofss.digx.domain.sms.entity.user.User userForPolicy = userAssembler
					.toDomainObject(requestDTO.getUserDTO());
			UserBusinessPolicyDTO businessPolicyDTO = new UserBusinessPolicyDTO();
			businessPolicyDTO.setUser(userForPolicy);
			AbstractBusinessPolicy abstractUserServiceBusinessPolicy = bpfact
					.getBusinesPolicyInstance("com.ofss.digx.app.sms.service.user.User.update", businessPolicyDTO);
			abstractUserServiceBusinessPolicy.validate(UserManagementErrorConstants.UM_VALIDATION_FAILED_MESSAGE);
			BeaSystemOut.println("Inside Extension update");
			if (requestDTO.getDocumentID() != null) {
				requestDTO.setDocumentID(requestDTO.getDocumentID().trim());
			}

			// All of the below details will be coming from UI
			String accountNoMigrationStatus = requestDTO.getCdcNoEncrypted();
			String userIdMigrationStatus = requestDTO.getUserIDEncrypted();
//			String keyIndicatorMigrationStatus = requestDTO.getKeyIndicator();
			
			// To invoke approval flow manually
			IUserExtensionDataRepositoryAdapter userExtensionDataRepositoryAdapter = (IUserExtensionDataRepositoryAdapter) RepositoryAdapterFactory
					.getInstance()
					.getRepositoryAdapter(IUserExtensionDataRepositoryAdapter.USEREXTENSIONDATA_REPOSITORY_ADAPTER);
			
			IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
			
			IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
					.getAdapter(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
			
			SignerUserDetailsResponseDTO signerUserDetailsResponseDTO = null;
			LoginUserDetailsResponseDTO loginUserDetailsResponseDTO = null;
			LoginUserDetailsDTO loginUserDetailsDTO = new LoginUserDetailsDTO();
			SignerUserDetailsDTO signerUserDetailsDTO = new SignerUserDetailsDTO();
			
			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserExtensionKey());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			String bypassFlag = requestDTO.getBypassFlag();
			String bypassCode = requestDTO.getBypassCode();
			if(StringUtils.isNotBlank(bypassFlag)) {
				if(bypassFlag.equalsIgnoreCase("Y") && domain.getSecurityQuestionsBypass().equalsIgnoreCase("N")) {
					domain.setSecurityQuestionsBypass(bypassFlag);
					domain.setBypassCode(generateHash(bypassCode));
					
					IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
					ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
							.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
					String expiryExtendDays = customConfigAdapter.getConfiguationDetails(
							com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "BYPASS_CODE_EXPIRY_EXTEND_DAYS",
							"1");
					domain.setBypassExpiryTime(new Date(new Date().plusDays(Integer.parseInt(expiryExtendDays)).toString("yyyy-MM-dd")+" 23:59:59","yyyy-MM-dd HH:mm:ss"));
					BeaSystemOut.println("update bypassFlag from disable to enable");
				} else if(bypassFlag.equalsIgnoreCase("N") && domain.getSecurityQuestionsBypass().equalsIgnoreCase("Y")) {
					domain.setSecurityQuestionsBypass(bypassFlag);
					domain.setBypassCode(null);
					domain.setBypassExpiryTime(null);
					BeaSystemOut.println("update bypassFlag from enable to disable");
				}
			}

            String preDocId = domain.getDocumentID();
            String preDocType = domain.getDocumentType();
            String preDocCountry = domain.getDocumentCountry();
            String signerId = domain.getSignerID();
            com.ofss.digx.infra.thread.ThreadAttribute.set("preDocId",preDocId);
            com.ofss.digx.infra.thread.ThreadAttribute.set("preDocType",preDocType);
            com.ofss.digx.infra.thread.ThreadAttribute.set("preDocCountry",preDocCountry);
            if (StringUtils.isEmpty(signerId)){
                com.ofss.digx.infra.thread.ThreadAttribute.set("signerIdIsEmpty",true);
                com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("signerId is empty");
            }else {
                com.ofss.digx.infra.thread.ThreadAttribute.set("signerIdIsEmpty",false);
            }
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before modification DocumentID: " + preDocId);
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before modification DocumentType: " + preDocType);
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before modification DocumentCountry: " + preDocCountry);

			String loginPinReferenceNoFromDomain = null;
			String loginHoldReasonFromDomain = null;
			String loginPinStatusFromDomain = null;
			String loginHoldStatusFromDomain = null;

			if (domain.getLoginPinReferenceNo() != null) {
				loginPinReferenceNoFromDomain = domain.getLoginPinReferenceNo();
			}

			if (domain.getLoginHoldReason() != null) {
				loginHoldReasonFromDomain = domain.getLoginHoldReason();
			}
			if (domain.getLoginPinstatus() != null) {
				loginPinStatusFromDomain = domain.getLoginPinstatus();
			}
			if (domain.getLoginHoldStatus() != null) {
				loginHoldStatusFromDomain = domain.getLoginHoldStatus();
			}
			
			boolean isSignerHostCallDone, isLoginHostCallDone;
			
			if (isAdmin && requestDTO.getDictionaryArray() != null) {
				for (Dictionary dictionaryArray : requestDTO.getDictionaryArray()) {
					for (NameValuePairDTO nameValuePairDTO : dictionaryArray.getNameValuePairDTOArray()) {
						BeaSystemOut.println("signerDeleteFlag==Name==" + nameValuePairDTO.getName());
						BeaSystemOut.println("signerDeleteFlag==value==" + nameValuePairDTO.getValue());
						BeaSystemOut.println("signerDeleteFlag==SignerID==" + domain.getSignerID());
						BeaSystemOut.println("signerDeleteFlag==CDC No==" + domain.getCdcNo());
						BeaSystemOut.println(
								"signerDeleteFlag==Boolean==" + Boolean.parseBoolean(nameValuePairDTO.getValue()));
						if (nameValuePairDTO.getName().equals("signerDeleteFlag")
								&& Boolean.parseBoolean(nameValuePairDTO.getValue())) {
							signerUserDetailsDTO.setSignerId(domain.getSignerID());
							signerUserDetailsDTO.setCdcId(domain.getCdcNo());
							hostuserDetailsAdapter.deleteSignerId(signerUserDetailsDTO);
							isSignerDeleted = true;
						}
					}
				}
			}

			if (requestDTO.getSignerID() != null && isAdmin) {

				signerUserDetailsDTO.setSignerId(requestDTO.getSignerID());
				signerUserDetailsDTO.setUserId(requestDTO.getUserID());
				signerUserDetailsDTO.setDocumentCountry(requestDTO.getDocumentCountry());
				signerUserDetailsDTO.setPinReferenceNo(requestDTO.getSignerPinReferenceNo());
				signerUserDetailsDTO.setPinStatus(requestDTO.getSignerPinstatus());
				signerUserDetailsDTO.setHkID(requestDTO.getDocumentID());
				signerUserDetailsDTO.setHkIDCheckDigit(requestDTO.getDocumentID());
				signerUserDetailsDTO.setCdcId(requestDTO.getCdcNo());
				signerUserDetailsDTO.setHoldReason(requestDTO.getSignerHoldReason());
				signerUserDetailsDTO.setHoldCode(requestDTO.getSignerHoldStatus());
				signerUserDetailsDTO.setDocTypeCode(requestDTO.getDocumentType());
				signerUserDetailsDTO.setAuthPersonFlag(requestDTO.getIsAuthorisedPerson() ? "Y" : "N");
				if (domain.getSignerHoldReason() != null
						&& domain.getSignerHoldReason().equals(requestDTO.getSignerHoldReason())
						&& domain.getSignerHoldStatus() != null
						&& domain.getSignerHoldStatus().equals(requestDTO.getSignerHoldStatus())
						&& domain.getSignerPinReferenceNo() != null
						&& domain.getSignerPinReferenceNo().equals(requestDTO.getSignerPinReferenceNo())
						&& domain.getSignerPinstatus() != null
						&& domain.getSignerPinstatus().equals(requestDTO.getSignerPinstatus())
						&& domain.getIsAuthorisedPerson() != null
						&& domain.getIsAuthorisedPerson().equals(requestDTO.getIsAuthorisedPerson())) {
					isSignerHostCallDone = true;
					BeaSystemOut.println("No need to call signer pin change request" + SerializationUtils.toJsonString(requestDTO));
				} else {
					//CDCODC-8137
					String signerPinReferenceNoRequest = requestDTO.getSignerPinReferenceNo();
					String signerPinReferenceNo = domain.getSignerPinReferenceNo();
					String signerPinStatus = domain.getSignerPinstatus();
					String signerPinStatusRequest = requestDTO.getSignerPinstatus();

					//BM  add new singer
					BeaSystemOut.println("signerPinReferenceNoRequest: " + signerPinReferenceNoRequest);
					BeaSystemOut.println("signerPinReferenceNo: " + signerPinReferenceNo);
					BeaSystemOut.println("signerPinStatus: " + signerPinStatus);
					BeaSystemOut.println("signerPinStatusRequest: " + signerPinStatusRequest);


					if (domain.getSignerPinReferenceNo() != null
							&& domain.getSignerPinReferenceNo().equals(signerUserDetailsDTO.getPinReferenceNo())) {
						signerUserDetailsDTO.setPinChangeFlag("N");
						//handle the BM change to Normal  and SignerPinReferenceNo no change
						if(signerPinStatus.equalsIgnoreCase("Being Reset") &&
								signerPinStatusRequest!= null &&
								signerPinStatusRequest.equalsIgnoreCase("Normal")){
							domain.setForceChangeSigner("Y");
						}
						// BM  active the signer pin
					} else {
						if(domain.getMigtationStatus() != null && domain.getMigtationStatus().equals("N")) {
							BeaSystemOut.println("** Migrated user and new signer pin ref no mapped");
							domain.setMigSignerUpdated("Y");
						}
						
						if(domain.getMigtationStatus() != null && domain.getMigtationStatus().equals("S")) {
							BeaSystemOut.println("** Migrated user and new signer pin ref no mapped--status was S");
							domain.setMigSignerUpdated("Y");
							domain.setMigtationStatus("Y");
						}

						signerUserDetailsDTO.setPinChangeFlag("Y");
						domain.setSignerPinType("N");
						//BM reset signer pin   normal ->being reset
						if(signerPinStatusRequest!= null && signerPinStatusRequest.equalsIgnoreCase("Being Reset")){
							domain.setForceChangeSigner("N");
						}else{
							domain.setForceChangeSigner("Y");
						}
						//CDCODC-8137
					}
					if (domain.getSignerID() != null) {
						BeaSystemOut.println("need to call EBK signer pin change request signerUserDetailsDTO:"
								+ SerializationUtils.toJsonString(signerUserDetailsDTO));
						signerUserDetailsResponseDTO = hostuserDetailsAdapter.updateSigner(signerUserDetailsDTO);
					} else {
						signerUserDetailsResponseDTO = hostuserDetailsAdapter.addSigner(signerUserDetailsDTO);
					}

					isSignerHostCallDone = signerUserDetailsResponseDTO.isVerified();
				}
			} else {
				isSignerHostCallDone = true;
			}

			boolean userMigrationOnboardingEditFlag = false;
			boolean defaultUserUpdateMigrationStatusFlag = false;

			BeaSystemOut.println("\n MigrationUserOnboardingEdit UserExtensionData ::  " + "\n requestDTO.getLoginID() : "
					+ requestDTO.getLoginID() + "\n isAdmin : " + isAdmin + "\n domain.getLoginHoldReason() : "
					+ domain.getLoginHoldReason() + "\n requestDTO.getLoginHoldReason() : "
					+ requestDTO.getLoginHoldReason() + "\n domain.getLoginHoldStatus() : "
					+ domain.getLoginHoldStatus() + "\n requestDTO.getLoginHoldStatus() :"
					+ requestDTO.getLoginHoldStatus() + "\n domain.getLoginPinReferenceNo() :"
					+ domain.getLoginPinReferenceNo() + "\n requestDTO.getLoginPinReferenceNo() : "
					+ requestDTO.getLoginPinReferenceNo() + "\n domain.getLoginPinstatus() : "
					+ domain.getLoginPinstatus() + "\n requestDTO.getLoginPinstatus() : "
					+ requestDTO.getLoginPinstatus());

			if (requestDTO.getLoginID() != null && isAdmin) {
				loginUserDetailsDTO.setLoginId(requestDTO.getLoginID());
				loginUserDetailsDTO.setPinReferenceNo(requestDTO.getLoginPinReferenceNo());
				loginUserDetailsDTO.setCdcId(requestDTO.getCdcNo());
				loginUserDetailsDTO.setPinStatus(requestDTO.getLoginPinstatus());
				loginUserDetailsDTO.setHoldCode(requestDTO.getLoginHoldStatus());
				loginUserDetailsDTO.setHoldReason(requestDTO.getLoginHoldReason());
				if (domain.getLoginHoldReason() != null
						&& domain.getLoginHoldReason().equals(requestDTO.getLoginHoldReason())
						&& domain.getLoginHoldStatus() != null
						&& domain.getLoginHoldStatus().equals(requestDTO.getLoginHoldStatus())
						&& domain.getLoginPinReferenceNo() != null
						&& domain.getLoginPinReferenceNo().equals(requestDTO.getLoginPinReferenceNo())
						&& domain.getLoginPinstatus() != null
						&& domain.getLoginPinstatus().equals(requestDTO.getLoginPinstatus())) {
					isLoginHostCallDone = true; // No need to call login pin change request

				} else {
					if (domain.getLoginPinReferenceNo().equals(loginUserDetailsDTO.getPinReferenceNo())) {
						loginUserDetailsDTO.setPinChangeFlag("N");

					} else {
						loginUserDetailsDTO.setPinChangeFlag("Y");
						domain.setLoginPinType("N"); // Change to Numeric pin again
						Users users = new Users();
						Users usersRead = null;
						UsersKey usersKey = new UsersKey();
						usersKey.setUserName(requestDTO.getUserID());
						users.setKey(usersKey);
						usersRead = users.read(users);
						usersRead.setForceChangePassword(true);
					}
//					----------------------- MigrationUserOnboardingEdit changes - STARTS --------------------- 

					BeaSystemOut.println("\n MigrationUserOnboardingEdit UserExtensionData ::  "
							+ "\n requestDTO.getLoginPinReferenceNo(): " + requestDTO.getLoginPinReferenceNo()
							+ "\n loginPinReferenceNoFromDomain : " + loginPinReferenceNoFromDomain
							+ "\n domain.getMigtationStatus() : " + domain.getMigtationStatus()
							+ "\n domain.getDefaultUser() :" + domain.getDefaultUser()
							+ "\n requestDTO.getLoginPinstatus() :" + requestDTO.getLoginPinstatus() 
							+ "\n accountNo: " + accountNoMigrationStatus 
							+ "\n userId: " + userIdMigrationStatus
							+ "\n domain.getLoginPinReferenceNo() : " + domain.getLoginPinReferenceNo()
							+ "\n loginHoldReasonFromDomain : " + loginHoldReasonFromDomain
							+ "\n loginPinStatusFromDomain : " + loginPinStatusFromDomain
							+ "\n loginHoldStatusFromDomain) : " + loginHoldStatusFromDomain);
					
					boolean isWHPUser = false;
					
					isWHPUser = !(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()))
							&& domain.getMigtationStatus() != null && domain.getMigtationStatus().equalsIgnoreCase("N")
							&& loginHoldReasonFromDomain != null
							&& loginHoldReasonFromDomain.equals(LOGIN_HOLD_REASON_REQ_BY_CUSTOMER)
							&& loginPinStatusFromDomain != null
							&& loginPinStatusFromDomain.equals(LOGIN_PIN_STATUS_INACTIVE)
							&& loginHoldStatusFromDomain != null
							&& loginHoldStatusFromDomain.equals(LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED) ;
							
					if (isMigrationUserOnboardingEditEnabled() 
							&& domain.getMigtationStatus() != null && domain.getMigtationStatus().equals("N")
							&& (domain.getDefaultUser() == null 
								|| (!(domain.getDefaultUser() != null && domain.getDefaultUser().equals("Y"))
										&& !(domain.getDefaultUser() != null && domain.getDefaultUser().equals("S"))))
							&& requestDTO.getLoginPinReferenceNo() != null && !(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()))
							&& requestDTO.getLoginPinstatus() != null && requestDTO.getLoginPinstatus().equals(LOGIN_PIN_STATUS_BEING_RESET)
							&& !isWHPUser) {
						
						isLoginHostCallDone = false;
						
						List<UserIdMaintenance> list = buildUserIdMaintenanceList(requestDTO);

						IUserIdMaintenanceRepositoryAdapter userIdMaintenancaRepositoryAdapter = (IUserIdMaintenanceRepositoryAdapter) RepositoryAdapterFactory
								.getInstance().getRepositoryAdapter(
										IUserIdMaintenanceRepositoryAdapter.USERIDMAINTENANCE_REMOTE_REPOSITORY_ADAPTER);
						userIdMaintenancaRepositoryAdapter.addUserId(list);
						BeaSystemOut.println("MigrationUserOnboardingEdit :: Sent to remote addUserId - ALU02 call");
						
						userMigrationOnboardingEditFlag = true;

						isLoginHostCallDone = true;

					} else {
						// Call CLU02
						loginUserDetailsResponseDTO = hostuserDetailsAdapter.updateLoginId(loginUserDetailsDTO);

						// TODO: isDefaultUserUpdateMigrationStatus

						defaultUserUpdateMigrationStatusFlag = isDefaultUserUpdateMigrationStatus(domain, requestDTO,
								loginPinReferenceNoFromDomain);

						isLoginHostCallDone = loginUserDetailsResponseDTO.isVerified();
					}
//					----------------------- MigrationUserOnboardingEdit changes - ENDS -----------------------

				}

			} else {
				isLoginHostCallDone = true;
			}

			if (domain != null && isSignerHostCallDone && isLoginHostCallDone) {

				UserAlertRequestDTO resultDto = new UserAlertRequestDTO();
				userKey.setUserId(requestDTO.getUserID());
				userDomain = userDomain.read(userKey);
				String oldMobNo = userDomain.getMobileNumber();
				BeaSystemOut.println("##########Old Mob No:- " + oldMobNo);
				BeaSystemOut.println("#############Old Email:- " + userDomain.getEmailId());
				resultDto = checkAlerts(requestDTO, userDomain);

				// UserExtensionDataKey key = new UserExtensionDataKey();
				// key.setUserExtensionKey(requestDTO.getUserExtensionKey());
				// domain.setUserExtensionDataKey(key);
				// domain = domain.read(key);
				domain = assembler.toUpdateDomainObject(requestDTO, domain);
				String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
				BeaSystemOut.println("local repo call for update" + taskId);
				SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
				IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance()
						.getAdapter(IFMOHelperCallAdapter.class, "callFMOHelper", DeterminantType.Enterprise);
				if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
						&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
					adapter.callFMOHelper(session, taskId, requestDTO, true);
					BeaSystemOut.println("local repo call after fmo");
				}
				
				if (isSignerDeleted) {
					BeaSystemOut.println("**Inside signer deleted condition="+domain.getUserID());
					domain.setForceChangeSigner(null);
					domain.setSignerID(null);
					domain.setMigSignerUpdated("Y");
					domain.setIsSignerPinReminder(false);
					domain.setSignPinExpiryDate(null);
					
					if(domain.getMigtationStatus() != null && domain.getMigtationStatus().equalsIgnoreCase("S")) {
						domain.setMigtationStatus("Y");
					}
				}
				
				domain.update(domain);
				BeaSystemOut.println("##############Executing domain update success end");

				// BCOH2H-787: a regeneration submitted through the original update flow
				// supersedes the previous ACTIVE code and activates the new one at approval.
				activateHthApiPasswordCode(sessionContext, requestDTO);
				/**
				 * Create group to for update
				 */
				requestDTO = createUserGroupDTOForUpdate(requestDTO);

				CZProductUsers czProductUsers = new CZProductUsers();
				transactionStatus = czProductUsers.update(sessionContext, requestDTO.getUserDTO());
				
				BeaSystemOut.println("Transaction Status: "+ transactionStatus);
				BeaSystemOut.println("Transaction Status Replytext + code : "+ transactionStatus.getReplyText() + "+" + transactionStatus.getReplyCode());
				BeaSystemOut.println("Transaction Status ErrorCOde + code : "+ transactionStatus.getErrorCode() + "+" + transactionStatus.getValidationErrors());
				if (transactionStatus!=null && transactionStatus.getErrorCode()==null) {
					// User Profile update alert
					BeaSystemOut.println("##############Executing alertUserProfileUpdate method");
					alertUserProfileUpdate(sessionContext, resultDto, requestDTO, userDomain, oldMobNo);
					BeaSystemOut.println("##############Executed alertUserProfileUpdate method");
				}
			}

			UserIdMaintenanceKey userIdMaintenanceKey = new UserIdMaintenanceKey();
			userIdMaintenanceKey.setId(requestDTO.getLoginID());
			userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
			userIdMaintenanceDomain = userIdMaintenanceDomain.read(userIdMaintenanceKey);
			BeaSystemOut.println("In Update of user extension");

			if (userIdMaintenanceDomain != null && isAdmin) {
				BeaSystemOut.println("details --> requestDTO.getLoginPinstatus()" + requestDTO.getLoginPinstatus()
						+ " requestDTO.getLoginHoldStatus()" + requestDTO.getLoginHoldStatus()
						+ " requestDTO.getLoginHoldReason()" + requestDTO.getLoginHoldReason());
				UserIdMaintenanceKeyDTO userIdMaintenanceKeyDTOKey = new UserIdMaintenanceKeyDTO();
				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();
				userIdMaintenanceKeyDTOKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKeyDTOKey.setId(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setIdKey(userIdMaintenanceKeyDTOKey);

				userIdMaintenanceRequestDTO.setIdToMap(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setPinRefToMap(requestDTO.getLoginPinReferenceNo());
				userIdMaintenanceRequestDTO.setPinStatus(requestDTO.getLoginPinstatus());
				userIdMaintenanceRequestDTO.setHoldStatus(requestDTO.getLoginHoldStatus());
				userIdMaintenanceRequestDTO.setHoldReason(requestDTO.getLoginHoldReason());
				BeaSystemOut.println("Setting Username update -->" + requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setUserName(requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setIdType("02");
				userIdMaintenanceDomain = userIdMaintenanceAssembler.toDomainObjectUpdate(userIdMaintenanceDomain,
						userIdMaintenanceRequestDTO);
				if (userIdMaintenanceDomain != null) {
					userIdMaintenanceDomain.update(userIdMaintenanceDomain);
				}
			}

			requestDTO = assembler.fromDomainObject(domain);
			
//			----------------------- MigratedUserResetPassword changes - STARTS --------------------- 
			if (isMigratedUserResetPasswordEnabled() && isAdmin) {

				MigrationStatusRequestDto migrationStatusRequestDto = new MigrationStatusRequestDto();
				PublicKeyDTO4CDC publicKeyDTO4CDC = new PublicKeyDTO4CDC();

				publicKeyDTO4CDC = hostuserDetailsAdapter.getPublickey4CDC(sessionContext, publicKeyDTO4CDC);

				BeaSystemOut.println("MigratedUserResetPassword getPublickey4CDC from service :: " + "\n PublicKey : "
						+ publicKeyDTO4CDC.getPublicKey() + "\n Key_indicator : "
						+ publicKeyDTO4CDC.getKey_indicator());

				String encryptedAccountNo = accountNoMigrationStatus;
				String encryptedUserId = userIdMigrationStatus;
				String keyIndicatorFromPublicKeyDTO4CDC = String.valueOf(publicKeyDTO4CDC.getKey_indicator());

				if (publicKeyDTO4CDC.getPublicKey() != null) {

					try {
						Boolean ccb_decommission_flag = hostuserDetailsAdapter.getCCBByPassFlag();
						encryptedUserId = RSAUtils.encrypt(userIdMigrationStatus, publicKeyDTO4CDC.getPublicKey(),ccb_decommission_flag);
						encryptedAccountNo = RSAUtils.encrypt(accountNoMigrationStatus, publicKeyDTO4CDC.getPublicKey(),ccb_decommission_flag);
					} catch (java.lang.Exception e) {
						BeaSystemOut.printErr(e);
						
					}
					
				}

				BeaSystemOut.println("\n MigratedUserResetPassword UserExtensionData Req Data from UI ::  "
						+ "\n encryptedAccountNo: " + encryptedAccountNo + "\n encryptedUserId: " + encryptedUserId
						+ "\n decryptedUserId: " + requestDTO.getUserExtensionKey()
						+ "\n requestDTO.getLoginPinReferenceNo(): " + requestDTO.getLoginPinReferenceNo()
						+ "\n domain.getLoginPinReferenceNo() : " + domain.getLoginPinReferenceNo()
						+ "\n loginPinReferenceNoFromDomain : " + loginPinReferenceNoFromDomain
						+ "\n loginHoldReasonFromDomain : " + loginHoldReasonFromDomain
						+ "\n domain.getMigtationStatus() : " + domain.getMigtationStatus()
						+ "\n loginPinStatusFromDomain : " + loginPinStatusFromDomain
						+ "\n loginHoldStatusFromDomain) : " + loginHoldStatusFromDomain
						+ "\n keyIndicatorFromPublicKeyDTO4CDC : " + keyIndicatorFromPublicKeyDTO4CDC);

				if (requestDTO.getLoginPinReferenceNo() != null) {
					if (!(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()))
							&& domain.getMigtationStatus() != null && domain.getMigtationStatus().equalsIgnoreCase("N")
							&& loginHoldReasonFromDomain != null
							&& loginHoldReasonFromDomain.equals(LOGIN_HOLD_REASON_REQ_BY_CUSTOMER)
							&& loginPinStatusFromDomain != null
							&& loginPinStatusFromDomain.equals(LOGIN_PIN_STATUS_INACTIVE)
							&& loginHoldStatusFromDomain != null
							&& loginHoldStatusFromDomain.equals(LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED)) {

						migrationStatusRequestDto.setAcctNo(encryptedAccountNo);
						migrationStatusRequestDto.setUsrId(encryptedUserId);
						migrationStatusRequestDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusRequestDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n MigratedUserResetPassword UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusRequestDto);
						BeaSystemOut.println(
								"\n MigratedUserResetPassword UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");

					} else if (userMigrationOnboardingEditFlag) {

						MigrationStatusRequestDto migrationStatusReqDto = new MigrationStatusRequestDto();

						migrationStatusReqDto.setAcctNo(encryptedAccountNo);
						migrationStatusReqDto.setUsrId(encryptedUserId);
						migrationStatusReqDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusReqDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n MigrationUserOnboardingEdit UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusReqDto);
						BeaSystemOut.println(
								"\n MigrationUserOnboardingEdit UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");
						
					} else if (defaultUserUpdateMigrationStatusFlag) {
						// TODO: isDefaultUserUpdateMigrationStatus
						MigrationStatusRequestDto migrationStatusReqDto = new MigrationStatusRequestDto();

						migrationStatusReqDto.setAcctNo(encryptedAccountNo);
						migrationStatusReqDto.setUsrId(encryptedUserId);
						migrationStatusReqDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusReqDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n defaultUserUpdateMigrationStatusFlag UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusReqDto);
						BeaSystemOut.println(
								"\n defaultUserUpdateMigrationStatusFlag UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");
					}
				}
			}
//			----------------------- MigratedUserResetPassword changes - ENDS -----------------------
	
			extensionExecutor.postUpdate(sessionContext, requestDTO, transactionStatus);

						
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from update() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from update() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		}finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from update() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	@Override
	@Entitlement(name = "validatePinStatus UserExtensionData", action = ActionType.PERFORM, requiredResources = {})
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_VPS", parent = "UM", name = "validatePinStatus UserExtensionData", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public UserExtensionDataResponseDTO validatePinStatus(SessionContext sessionContext,
			UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Entered into validatePinStatus() : requestDTO = %s in class %s ",
							requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.validatePinStatus",
				sessionContext, requestDTO);
		UserExtensionDataResponseDTO response = new UserExtensionDataResponseDTO();
		UserExtensionDataDTO userExtensionDataDTO = new UserExtensionDataDTO();
		response.setStatus(fetchStatus());
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintenanceDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();
		String currrentPinConfigDays = getCurrrentPinConfigDays();
		String centreCode = getCentreCode();
		Boolean suspsendEmailConditionFlag = false;

		com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSession hostUserSessiondomain = new com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSession();
		com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSessionKey hostUserSessionkey = new com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSessionKey();
		com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSession userDomain = new com.ofss.digx.cz.bea.domain.sms.entity.session.HostUserSession();
		com.ofss.digx.cz.bea.domain.sms.entity.user.ResetPassword resetPasswordDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.ResetPassword();
		ResetPasswordKey resetPasswordKey = new ResetPasswordKey();
		ResetPasswordRequestDTO resetPasswordRequestDTO = new ResetPasswordRequestDTO();
		ValidateUserResponseDTO validateUserResponseDTO = new ValidateUserResponseDTO();

		Boolean isChannelOrNoActivityAllowed = false, isCentreCodeGAOD = false, isRequestResetPinRecord = false;
		try {
			requestDTO.validate(sessionContext);
			Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");

			BeaSystemOut.println("#############centreCode :- " + centreCode);
			BeaSystemOut.println("#############currrentPinConfigDays :- " + currrentPinConfigDays);

			// CZPartyPreferences retrieval
			CZPartyPreferences partyPref = new CZPartyPreferences();
			PartyPreferencesKey partyPreferencesKey = new PartyPreferencesKey();
			partyPreferencesKey.setPartyId(requestDTO.getCdcNo());
			partyPref.setKey(partyPreferencesKey);
			partyPref = (CZPartyPreferences) partyPref.read(partyPref);
			if (partyPref != null) {
				BeaSystemOut.println("#############PartPreference IsEnabled :- " + partyPref.getIsEnabled());
			}

			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserExtensionKey());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			BeaSystemOut.println(
					"#############LoginPinMapDate for UserExtensionData domain :-" + domain.getLoginPinMapDate());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut
					.println("#############SignPinMaDate for UserExtensionData domain:-" + domain.getSignPinMapDate());
			BeaSystemOut.println(
					"#############SignerHoldStatus for UserExtensionData domain:-" + requestDTO.getSignerHoldStatus());
			BeaSystemOut.println(
					"#############SignerHoldReason for UserExtensionData domain:-" + requestDTO.getSignerHoldReason());

			if ((partyPref != null && !partyPref.getIsEnabled())
					|| (CZCommonConstants.NO_ACTIVITY_ALLOWED.equals(requestDTO.getLoginHoldStatus())
							&& !CZCommonConstants.RETRY_COUNT_EXCEEDED.equals(requestDTO.getLoginHoldReason()))) {
				isChannelOrNoActivityAllowed = true;
			}

			if ((partyPref != null && !partyPref.getIsEnabled())
					|| (CZCommonConstants.NO_ACTIVITY_ALLOWED.equals(requestDTO.getSignerHoldStatus())
							&& !CZCommonConstants.RETRY_COUNT_EXCEEDED.equals(requestDTO.getSignerHoldReason()))) {
				isChannelOrNoActivityAllowed = true;
			}

			// Department Code retrieval
			hostUserSessionkey.setUserID((String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));
			hostUserSessiondomain.setKey(hostUserSessionkey);
			userDomain = hostUserSessiondomain.readSession(hostUserSessionkey.getUserID());
			if (userDomain.getDepartmentCode() != null && centreCode.equals(userDomain.getDepartmentCode())) {
				BeaSystemOut.println("#############DepartmentCode :- " + userDomain.getDepartmentCode());
				isCentreCodeGAOD = true;
			}

			// Request Reset Pin Record
			if (requestDTO.getLoginID() != null && requestDTO.getLoginPinReferenceNo() != null) {
				List<ResetPasswordUserRecord> resetPasswordUserRecordList = resetPasswordDomain
						.getResetPasswordRecordsByUsername(requestDTO.getUserID());
				BeaSystemOut.println("#############LGN UserStatus :- " + resetPasswordUserRecordList.size());

				if (resetPasswordUserRecordList.size() > 0) {
					for (ResetPasswordUserRecord record : resetPasswordUserRecordList) {
						BeaSystemOut.println("==validate--com Status==" + record.getCompletionStatus());
						if ("N".equalsIgnoreCase(record.getCompletionStatus())
								|| record.getCompletionStatus() == null) {
							isRequestResetPinRecord = true;
						}
					}
				}

			}

			if (requestDTO.getSignerID() != null && requestDTO.getSignerPinReferenceNo() != null) {
				List<ResetPasswordUserRecord> resetPasswordUserRecordList = resetPasswordDomain
						.getResetPasswordRecordsByUsername(requestDTO.getUserID());
				BeaSystemOut.println("#############SGN UserStatus :- " + resetPasswordUserRecordList.size());

				if (resetPasswordUserRecordList.size() > 0) {
					for (ResetPasswordUserRecord record : resetPasswordUserRecordList) {
						BeaSystemOut.println("==validate--com Status==" + record.getCompletionStatus());
						if ("N".equalsIgnoreCase(record.getCompletionStatus())
								|| record.getCompletionStatus() == null) {
							isRequestResetPinRecord = true;
						}
					}
				}
			}

			BeaSystemOut.println("==isChannelOrNoActivityAllowed=" + isChannelOrNoActivityAllowed);
			BeaSystemOut.println("==isCentreCodeGAOD=" + isCentreCodeGAOD);
			BeaSystemOut.println("==isRequestResetPinRecord=" + isRequestResetPinRecord);
			if (isChannelOrNoActivityAllowed && isCentreCodeGAOD && isRequestResetPinRecord) {
				userExtensionDataDTO.setSuspsendEmailCondition(suspsendEmailConditionFlag);
				response.setUserExtensionDataDTO(userExtensionDataDTO);
				setWarning(response.getStatus(), "DIGX_CZ_SUSPEND_NOTIFICATION");
				return response;
			}

			// login Pin reset Warning
			BeaSystemOut.println("#############LGN Login ID :- " + domain.getLoginID());
			BeaSystemOut.println("#############LGN getLoginPinMapDate :- " + domain.getLoginPinMapDate());
			BeaSystemOut.println("#############LGN currrentPinConfigDays :- " + currrentPinConfigDays);
			BeaSystemOut.println("#############LGN comparision :- " + domain.getLoginPinMapDate()
					.plusDays(Integer.parseInt(currrentPinConfigDays)).isBefore(new Date()));
			if (domain.getLoginID() != null && domain.getLoginPinMapDate()
					.plusDays(Integer.parseInt(currrentPinConfigDays)).isBefore(new Date())) {
				setWarning(response.getStatus(), "DIGX_CZ_PIN_RESET_WARNING", currrentPinConfigDays);
				return response;
			}

			// Sign Pin reset Warning
			if (domain.getSignerID() != null && domain.getSignPinMapDate()
					.plusDays(Integer.parseInt(currrentPinConfigDays)).isBefore(new Date())) {
				setWarning(response.getStatus(), "DIGX_CZ_PIN_RESET_WARNING", currrentPinConfigDays);
				return response;
			}
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE,
					formatter.formatMessage("Exception from validatePinStatus() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from validatePinStatus() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Exiting from validatePinStatus() : transactionStatus = %s",
					transactionStatus));
		}
		return response;
	}

	@Override
	@Entitlement(name = "Create UserExtensionData", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Create UserExtensionData", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Create UserExtensionData", action = ActionType.PERFORM, requiredResources = {})
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_CUS", parent = "MT", name = "Create UserExtensionData", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public UserExtensionDataResponseDTO create(SessionContext sessionContext, UserExtensionDataDTO requestDTO)
			throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into create() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create", sessionContext,
				requestDTO);
		UserExtensionDataResponseDTO response = new UserExtensionDataResponseDTO();
		response.setStatus(fetchStatus());
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.app.sms.service.user.User user = new com.ofss.digx.app.sms.service.user.User();
		UserAssembler userAssembler = new UserAssembler();
		try {
			extensionExecutor.preCreate(sessionContext, requestDTO);
			requestDTO.validate(sessionContext);
			UserExtensionDataAssembler assembler = new UserExtensionDataAssembler();
			domain = assembler.toDomainObject(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = null;
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintenanceDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenanceKey userIdMaintenanceDomainKey = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenanceKey();
			UserIdMaintenanceAssembler userIdMaintenanceAssembler = new UserIdMaintenanceAssembler();
			UserResponseDTO userResponseDTO = new UserResponseDTO();
			UserIdMaintenanceRequestDTO userIdMaintenanceRequestDTO = null;
			com.ofss.digx.domain.sms.entity.user.User userForPolicy = userAssembler
					.toDomainObjectCreate(requestDTO.getUserDTO());

			if (requestDTO.getDocumentID() != null) {
				requestDTO.setDocumentID(requestDTO.getDocumentID().trim());
			}

			if(requestDTO.getUserDTO() != null && requestDTO.getUserDTO().getUsername() != null) {
				BeaSystemOut.println("**USERNAME_TO_BE_CREATED = "+requestDTO.getUserDTO().getUsername());
				com.ofss.digx.infra.thread.ThreadAttribute.set("USERNAME_TO_BE_CREATED", requestDTO.getUserDTO().getUsername());
			}
			UserExtensionDataBusinessPolicyDTO policyDTO = new UserExtensionDataBusinessPolicyDTO(requestDTO);
			abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
					"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create", policyDTO);
			abstractBusinessPolicy.validate(
					com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicy.MT_USEREXTENSIONDATA_BUSINESS_POLICY_VIOLATION);

			/*
			 * Invoke base service business policy
			 */
			UserBusinessPolicyDTO businessPolicyDTO = new UserBusinessPolicyDTO();
			businessPolicyDTO.setUser(userForPolicy);
			AbstractBusinessPolicy abstractUsersBusinessPolicy = bpfact
					.getBusinesPolicyInstance("com.ofss.digx.app.sms.service.user.User.createUser", businessPolicyDTO);
			abstractUsersBusinessPolicy.validate(UserManagementErrorConstants.UM_VALIDATION_FAILED_MESSAGE);

			// To invoke approval flow manually
			IUserExtensionDataRepositoryAdapter userExtensionDataRepositoryAdapter = (IUserExtensionDataRepositoryAdapter) RepositoryAdapterFactory
					.getInstance()
					.getRepositoryAdapter(IUserExtensionDataRepositoryAdapter.USEREXTENSIONDATA_REPOSITORY_ADAPTER);

			boolean signerVerifiedFlag = true;
			BeaSystemOut.println("In Extension Create");

			if (requestDTO.getLoginID() != null && requestDTO.getLoginPinReferenceNo() != null && isAdmin) {

				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();

				List<UserIdMaintenance> list = new ArrayList<UserIdMaintenance>();
				UserIdMaintenanceKey key = new UserIdMaintenanceKey();
				UserIdMaintenance loginID = new UserIdMaintenance();
				loginID.setPinRefNo(requestDTO.getLoginPinReferenceNo());
				loginID.setSubOption("02");
				key.setPartyId(requestDTO.getCdcNo());
				key.setId(requestDTO.getLoginID());
				loginID.setIdType("02");
				Party party = new Party();
				party.setValue(requestDTO.getCdcNo());
				loginID.setIdKey(key);
				loginID.setParty(party);
				list.add(loginID);
				IUserIdMaintenanceRepositoryAdapter userIdMaintenancaRepositoryAdapter = (IUserIdMaintenanceRepositoryAdapter) RepositoryAdapterFactory
						.getInstance().getRepositoryAdapter(
								IUserIdMaintenanceRepositoryAdapter.USERIDMAINTENANCE_REMOTE_REPOSITORY_ADAPTER);
				userIdMaintenancaRepositoryAdapter.addUserId(list);
				BeaSystemOut.println("Sent to remote addUserId");
				// TODO: ALU02 call
				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();
				userIdMaintenanceRequestDTO.setIdToMap(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setPinRefToMap(requestDTO.getLoginPinReferenceNo());
				UserIdMaintenanceKeyDTO userIdMaintenanceKey = new UserIdMaintenanceKeyDTO();
				userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKey.setId(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setIdKey(userIdMaintenanceKey);
				userIdMaintenanceRequestDTO.setHoldReason("NIL");
				userIdMaintenanceRequestDTO.setHoldStatus("Unhold");
				userIdMaintenanceRequestDTO.setPinStatus("Inactive");
				userIdMaintenanceRequestDTO.setIdType("02");
				BeaSystemOut.println("Got the suer name" + requestDTO.getUserID());
				BeaSystemOut.println("Setting username-->" + requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setUserName(requestDTO.getUserID());
				userIdMaintenanceDomainKey.setId(requestDTO.getLoginID());
				userIdMaintenanceDomainKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceDomain = userIdMaintenanceDomain.read(userIdMaintenanceDomainKey);
				if (userIdMaintenanceDomain == null) {
					userIdMaintenanceDomain = userIdMaintenanceAssembler.toDomainObject(userIdMaintenanceRequestDTO);
					BeaSystemOut.println("In Domain .create and domain key is" + requestDTO.getLoginID() + " and"
							+ requestDTO.getCdcNo());
					BeaSystemOut.println("Username in domain" + userIdMaintenanceDomain.getUserName());
					userIdMaintenanceDomain.create(userIdMaintenanceDomain);
				} else {
					userIdMaintenanceDomain.setPinRefNo(requestDTO.getLoginPinReferenceNo());
					userIdMaintenanceDomain.setUserName(requestDTO.getUserID());
					BeaSystemOut.println("Inside Else ==" + userIdMaintenanceDomain.getIdKey().getId() + "---"
							+ userIdMaintenanceDomain.getIdKey().getPartyId());
					userIdMaintenanceDomain.setLoginPinMapDate(new com.ofss.fc.datatype.Date());
					userIdMaintenanceDomain.update(userIdMaintenanceDomain);
				}

			}

			if (requestDTO.getSignerID() != null && requestDTO.getSignerPinReferenceNo() != null && isAdmin) {
				IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(
								com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
				IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
						.getAdapter(
								com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
				SignerUserDetailsDTO SignerUserDetailsDTO = new SignerUserDetailsDTO();
				SignerUserDetailsDTO.setSignerId(requestDTO.getSignerID());
				SignerUserDetailsDTO.setUserId(requestDTO.getUserID());
				SignerUserDetailsDTO.setDocumentCountry(requestDTO.getDocumentCountry());
				SignerUserDetailsDTO.setPinReferenceNo(requestDTO.getSignerPinReferenceNo());
				SignerUserDetailsDTO.setPinStatus(requestDTO.getSignerPinstatus());
				SignerUserDetailsDTO.setHkID(requestDTO.getDocumentID());
				SignerUserDetailsDTO.setCdcId(requestDTO.getCdcNo());
				SignerUserDetailsDTO.setHoldCode(requestDTO.getSignerHoldStatus());
				SignerUserDetailsDTO.setHoldReason(requestDTO.getSignerHoldReason());
				SignerUserDetailsDTO.setHkIDCheckDigit(requestDTO.getDocumentID());
				SignerUserDetailsDTO.setDocTypeCode(requestDTO.getDocumentType());
				SignerUserDetailsDTO.setAuthPersonFlag(requestDTO.getIsAuthorisedPerson() ? "Y" : "N");

				SignerUserDetailsResponseDTO signerUserDetailsResponseDTO = hostuserDetailsAdapter
						.addSigner(SignerUserDetailsDTO);

				if (!signerUserDetailsResponseDTO.isVerified()) {
					signerVerifiedFlag = false;
				}
			}

			if (requestDTO.getLoginID() != null && isAdmin != true) {
				com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();

				UserIdMaintenanceKey userIdMaintenanceKey = new UserIdMaintenanceKey();
				userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKey.setId(requestDTO.getLoginID());
				userIdMaintDomain = userIdMaintDomain.read(userIdMaintenanceKey);
				BeaSystemOut.println("Setting username in corp-->" + requestDTO.getUserID());
				userIdMaintDomain.setUserName(requestDTO.getUserID());
				if (userIdMaintDomain != null) {

					BeaSystemOut.println("Not null check for userIdMaintDomain");
					userIdMaintDomain.update(userIdMaintDomain);
				}
			}

			if (domain != null && signerVerifiedFlag) {
				if (domain.getLoginPinReferenceNo() != null) {
					domain.setLoginPinType("N");// during create login pin is numeric
				}
				if (domain.getSignerPinReferenceNo() != null) {
					domain.setSignerPinType("N");

					//CDCODC-8137
					//create default value is Y
					domain.setForceChangeSigner("Y");// during create signer pin is numeric & must be changed
					//CDCODC-8137
				}

				/**
				 * Create group to for create
				 */
				requestDTO = createUserGroupDTOForCreate(requestDTO);

				userResponseDTO = user.createUser(sessionContext, requestDTO.getUserDTO());

				if (userResponseDTO.getStatus().getLastKnownError() != null) {
					BeaSystemOut.println("inside ##getLastKnownError");
					throw (Exception) transactionStatus.fetchLastKnownError();
				}

				if (userResponseDTO.getStatus().getResult() != ResultType.FAILED) {
					BeaSystemOut.println("inside ##getStatus FAILED");
					domain.setSecurityQuestionsBypass("N");
					domain.create(domain);

					// calling alert method
					userCreateWelcomeAlert(sessionContext, requestDTO);

					// BCOH2H-787: activate the one-time HTH API Password Code now that the
					// original user creation flow has reached approval execution. BCO requests
					// carry no code id and are unaffected.
					activateHthApiPasswordCode(sessionContext, requestDTO);
				}
			}

			requestDTO = assembler.fromDomainObject(domain);
			response.setUserExtensionDataDTO(requestDTO);
			response.setUserResponseDTO(userResponseDTO);
			extensionExecutor.postCreate(sessionContext, requestDTO, response);
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from create() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from create() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Exiting from create() : response = %s", response));
		}
		return response;
	}

	@Override
	@NoEntitlement()
	public UserListExtResponseDTO listUsers(SessionContext sessionContext, UserDTO userDTO) throws Exception {
		// TODO Auto-generated method stub

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Entering list method of class:%s, SessionContext:%s ,UserSearchRequestDTO:%s",
							THIS_COMPONENT_NAME, sessionContext, userDTO));
		}
		super.canonicalizeInput(userDTO);
		super.checkAccessPolicy("com.ofss.digx.app.sms.service.user.User.list", sessionContext, userDTO);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		UserListExtResponseDTO userListResponseDTO = new UserListExtResponseDTO();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		try {

			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(CommonAdapterFactoryConstants.USER_ADAPTER_FACTORY);
			IUserAdapter adapter = (IUserAdapter) adapterFactory.getAdapter(CommonAdapterConstants.USER_ADAPTER);
			UserListResponseDTO response = new UserListResponseDTO();

			response = adapter.search(userDTO);

			List<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData> userExtensionData = new ArrayList<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData>();
			List<CZUserDTO> responseData = new ArrayList<CZUserDTO>();
			userExtensionData = domain.listUsers(userDTO);
			for (UserDTO userData : response.getUserDTOList()) {
				for (com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData user : userExtensionData) {

					if (user.getCdcNo().equals(userData.getPartyId().getValue())
							&& user.getUserID().equals(userData.getUsername())) {
						CZUserDTO data = new CZUserDTO();
						data.setUserData(userData);
						data.setUserChannelType(user.getUserChannelType());
						data.setCloseId(user.getCloseId());
						if (user.getIsAuthorisedPerson())
							data.setUserExtendedType("AuthorisedPerson");
						else if (user.getSignerID() != null)
							data.setUserExtendedType("Signer");
						else
							data.setUserExtendedType("");
						responseData.add(data);
					}
				}

			}

			userListResponseDTO.setUserDTOList(responseData);
			userListResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the list method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"RuntimeException from read method of class %s for UserSearchRequestDTO '%s'",
							THIS_COMPONENT_NAME, userDTO),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, userListResponseDTO);
		super.encodeOutput(userListResponseDTO);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting list method of class : %s, UserSearchResponseDTO:%s",
							THIS_COMPONENT_NAME, userListResponseDTO));
		}
		return userListResponseDTO;
	}

	public ResetUserListResponseDTO listResetPinUsers(SessionContext sessionContext, UserDTO userDTO) throws Exception {
		// TODO Auto-generated method stub

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Entering list method of class:%s, SessionContext:%s ,UserSearchRequestDTO:%s",
							THIS_COMPONENT_NAME, sessionContext));
		}
		super.checkAccessPolicy("com.ofss.digx.app.sms.service.user.User.list", sessionContext, userDTO);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		ResetUserListResponseDTO resetUserListResponseDTO = new ResetUserListResponseDTO();
		List<ResetUserDataDTO> responseData = new ArrayList<ResetUserDataDTO>();

		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		try {
			// Gets ResetUserDataList
			List<ResetUserDataDTO> tempData = new ArrayList<ResetUserDataDTO>();
			tempData = domain.listResetPinUsers(userDTO);

			com.ofss.digx.cz.bea.domain.sms.entity.user.ActivationLetter activationLetterdomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.ActivationLetter();
			List<ActivationLetter> activationLetters = activationLetterdomain
					.listByCdcId(userDTO.getPartyId().getValue());
			for (ResetUserDataDTO userData : tempData) {
				Boolean foundFlag = false;
				for (ActivationLetter letter : activationLetters) {
					for (ActivationUserRecord userRecord : letter.getActivationUserRecord()) {
						if (!Objects.isNull(userData.getPinReferenceNumber())
								&& !Objects.isNull(userRecord.getPinReferenceNumber())
								&& userData.getPinReferenceNumber().equals(userRecord.getPinReferenceNumber())) {
							foundFlag = true;
							break;
						}
					}
					if (foundFlag) {
						break;
					}
				}
				if (!foundFlag) {
					responseData.add(userData);
				}

			}

			resetUserListResponseDTO.setUserDTOList(responseData);
			resetUserListResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the list method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"RuntimeException from read method of class %s for UserSearchRequestDTO '%s'",
							THIS_COMPONENT_NAME, userDTO),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, resetUserListResponseDTO);
		super.encodeOutput(resetUserListResponseDTO);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting list method of class : %s, UserSearchResponseDTO:%s",
							THIS_COMPONENT_NAME, resetUserListResponseDTO));
		}
		return resetUserListResponseDTO;
	}

	@Override
	public UserExtensionDataResponseDTO fetchCustInfo(SessionContext sessionContext, UserExtensionDataDTO requestDTO)
			throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Entered into fetchFullName() : requestDTO = %s in class %s ", requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read", sessionContext,
				requestDTO);
		UserExtensionDataResponseDTO response = new UserExtensionDataResponseDTO();
		response.setStatus(fetchStatus());
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		try {
			requestDTO.validate(sessionContext);

			AbstractBusinessPolicy abstractBusinessPolicy = null;
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			UserExtensionDataBusinessPolicyDTO policyDTO = new UserExtensionDataBusinessPolicyDTO(requestDTO);
			abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
					"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create", policyDTO);
			abstractBusinessPolicy.validate(
					com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicy.MT_USEREXTENSIONDATA_BUSINESS_POLICY_VIOLATION);

			domain.setDocumentCountry(requestDTO.getDocumentCountry());
			domain.setDocumentID(requestDTO.getDocumentID());
			domain.setDocumentType(requestDTO.getDocumentType());
			response = domain.fetchCustInfo(domain);
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE,
					formatter.formatMessage("Exception from fetchFullName() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from fetchFullName() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Exiting from fetchFullName() : response = %s", response));
		}
		return response;
	}

	/**
	 * Alert method for suspend email on rejection of pin activation alert
	 * 
	 * @param sessionContext
	 * @param requestDTO
	 * @throws Exception
	 */
	@Override
	public TransactionStatus sendSuspendEmailAlert(SessionContext sessionContext, UserExtensionDataDTO requestDTO)
			throws Exception {

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Entering sendSuspendEmailAlert method of class:%s, SessionContext:%s ,UserExtensionDataDTO:%s",
					THIS_COMPONENT_NAME, sessionContext, requestDTO));
		}
		super.canonicalizeInput(requestDTO);
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read", sessionContext,
				requestDTO);
		Interaction.begin(sessionContext);
		UserExtensionDataActivityLogDTO activityLog = new UserExtensionDataActivityLogDTO();
		TransactionStatus transactionStatus = fetchTransactionStatus();
		UserExtensionDataResponseDTO response = new UserExtensionDataResponseDTO();
		NotificationDetail[] details = new NotificationDetail[1];
		NotificationDetail emailNotificationDetail = new NotificationDetail();
		String officeEmail = "";
		String fullUserId = "";

		try {
			
			requestDTO.validate(sessionContext);
			response.setStatus(fetchStatus());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut
					.println("###########Setting Activity Log for Suspend Email - Rejection on PIN Reset Application");
			
			IAdapterFactory validateUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.VALIDATE_USER_DETAILS_ADAPTER_FACTORY);
			IValidateUserDetailsAdapter validateUserDetailsAdapter = (IValidateUserDetailsAdapter) validateUserDetailsAdapterFactory
					.getAdapter(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.VALIDATE_USER_DETAILS_ADAPTER);

			String cdcNO = requestDTO.getCdcNo();
			
			IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
			ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
					.getAdapter("CUSTOM_CONFIG_ADAPTER");
			String cdcId = customConfigAdapter.getConfiguationDetails("DayOneConfig", "MERCHANT_ACCOUNT_ID",
					"01551468009999");
			BeaSystemOut.println("###########requestDTO.getCdcNo():" + requestDTO.getCdcNo());
			BeaSystemOut.println("###########cdcId:" + cdcId);
			
			if(requestDTO.getUserID() != null) {
				if(requestDTO.getUserID().contains("@")) {
					fullUserId = requestDTO.getUserID();
				}else {
					fullUserId = requestDTO.getUserID() + "@" + cdcNO;
				 
				}
			}
			

			if (cdcNO != null && cdcNO.length() > 13) {
				
				if (cdcNO.length() == 15) {
					cdcNO = maskActNo(cdcNO, cdcNO.length() - 7, cdcNO.length() - 4, '*');
					BeaSystemOut.println("######################Masked 15 digit CdcNO:- " + cdcNO);
					cdcNO = cdcNO.substring(0, 3) + "-" + cdcNO.substring(3, 6) + "-" + cdcNO.substring(6, 8) + "-"
							+ cdcNO.substring(8, 11) + cdcNO.substring(11, 14)+ "-" + cdcNO.substring(14, 15);
					activityLog.setPartyId(cdcNO);
					BeaSystemOut.println("######################Hiphen 15 digit CdcNO:- " + cdcNO);
				} else {
					cdcNO = maskActNo(cdcNO, cdcNO.length() - 6, cdcNO.length() - 3, '*');
					BeaSystemOut.println("######################Masked CdcNO:- " + cdcNO);
					cdcNO = cdcNO.substring(0, 3) + "-" + cdcNO.substring(3, 6) + "-" + cdcNO.substring(6, 8) + "-"
							+ cdcNO.substring(8, 11) + cdcNO.substring(11, 14);
					activityLog.setPartyId(cdcNO);
					BeaSystemOut.println("######################Hiphen CdcNO:- " + cdcNO);
				}
			} else {
				activityLog.setPartyId(requestDTO.getCdcNo());
			}
			activityLog.setCustomerId(requestDTO.getCdcNo());

			BeaSystemOut.println("###################PartyId: - " + activityLog.getCustomerId()
					+ "#############FmtCdcNo:- " + activityLog.getPartyId());
			if (activityLog.getCustomerId().equals(cdcId)) {
				
				BeaSystemOut.println("######################Full Merchant UserID:- " + fullUserId);
				
				try {
					officeEmail = validateUserDetailsAdapter.getMerchantRecipient(fullUserId,requestDTO.getCdcNo(),true);
				}catch(Exception e) {
					fillTransactionStatus(transactionStatus, e);
				}
				catch(java.lang.Exception e) {
					fillTransactionStatus(transactionStatus, e);
				}
				

				emailNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				emailNotificationDetail.setDestination(DestinationType.EMAIL);
				emailNotificationDetail.setDispatchAddress(officeEmail);
				emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				BeaSystemOut.println("####################Office EmailId:- " + emailNotificationDetail.getDispatchAddress());
				
				details[0] = emailNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setUserId(fullUserId);
				
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".sendSuspendEmailAlert",
						SUSPEND_EMAIL_REJECTION_ON_PIN_RESET_MERCHANT, new Date(), activityLog);
			} else {
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".sendSuspendEmailAlert",
						UserExtensionDataConstants.SUSPEND_EMAIL_REJECTION_ON_PIN_RESET, new Date(), activityLog);
			}
			
			response.setStatus(buildStatus(transactionStatus));
			BeaSystemOut.println(
					"#####################Executed the Suspend Email - Rejection on PIN Reset Application alert");
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE,
					formatter.formatMessage("Exception from sendSuspendEmailAlert() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"RuntimeException from sendSuspendEmailAlert() for requestDTO '%s' in class %s", requestDTO,
							THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter
					.formatMessage("Exiting from sendSuspendEmailAlert() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	//BCOCDC-3371 amended to avoid null pointer exception as CCB migrated account has no email and mobile no. start
	public UserAlertRequestDTO checkAlerts(UserExtensionDataDTO requestDTO,
			com.ofss.digx.domain.sms.entity.user.User userDomain) {

		UserAlertRequestDTO resultDTO = new UserAlertRequestDTO();
		
		String requestEmail = safeTrim(requestDTO.getUserDTO().getEmailId()); 
		String domainEmail = safeTrim(userDomain.getEmailId());

		if (Objects.equals(requestEmail, domainEmail)) {
			BeaSystemOut.println("requestEmail: " + requestEmail  + " is the same as " + "domainEmail: " + domainEmail);
			resultDTO.setEmailId(true);
		}
		
		String requestMobNo = safeTrim(requestDTO.getUserDTO().getMobileNumber());
		String domainMobNo = safeTrim(userDomain.getMobileNumber());
		
		if (Objects.equals(requestMobNo, domainMobNo)) {
			BeaSystemOut.println("requestMobNo: " + requestMobNo  + " is the same as " + "domainMobNo: " + domainMobNo);
			resultDTO.setMobNo(true);
		}
		return resultDTO;

	}
	
	public String safeTrim(String str) {
		return str == null ? "" : str.trim();
	}
	//BCOCDC-3371 amended to avoid null pointer exception as CCB migrated account has no email and mobile no. end
	
	/**
	 * @param sessionContext
	 * @param resultDTO
	 * @param requestDTO
	 * @param userDomain
	 * @param oldMobNo
	 */
	public void alertUserProfileUpdate(SessionContext sessionContext, UserAlertRequestDTO resultDTO,
			UserExtensionDataDTO requestDTO, com.ofss.digx.domain.sms.entity.user.User userDomain, String oldMobNo) {

		UserProfUpdateActivityLogDTO activityLog = new UserProfUpdateActivityLogDTO();
		UserManagementActivityLogDTO usermgmtActivityLog = new UserManagementActivityLogDTO();

		NotificationDetail[] details = new NotificationDetail[1];
		NotificationDetail emailNotificationDetail = new NotificationDetail();
		NotificationDetail messageNotificationDetail = new NotificationDetail();

		try {
			BeaSystemOut.println("##############Entered the Try Catch block of User Profile Update#######");
			BeaSystemOut.println("###########Request Email Flag: - " + resultDTO.getEmailId());
			BeaSystemOut.println("###########Request Email: - " + requestDTO.getUserDTO().getEmailId());
			BeaSystemOut.println("###########Domain Email: - " + userDomain.getEmailId());
			BeaSystemOut.println("###########Request Mob Flag: - " + resultDTO.getMobNo());
			BeaSystemOut.println("###########Request Mob: - " + requestDTO.getUserDTO().getMobileNumber());
			BeaSystemOut.println("###########Domain Mob: - " + userDomain.getMobileNumber());
			BeaSystemOut.println("################Party Id: " + sessionContext.getTransactingPartyCode());
			BeaSystemOut.println("################CDCNO: " + requestDTO.getCdcNo());

			IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
			ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
					.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
			String userId = "";

			com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
					.getInstance().getAdapterFactory(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
			IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
					.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);

			CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(requestDTO.getCdcNo());
			BeaSystemOut.println("###################Office Email: " + partyDetails.getOfficeEmailId());
			BeaSystemOut.println("###################Company Name: " + partyDetails.getCompanyName());

			if (requestDTO.getUserID() != null && !requestDTO.getUserID().equalsIgnoreCase("")) {
				String[] userDtls = requestDTO.getUserID().split("@");
				userId = userDtls[0];
				BeaSystemOut.println("################User: " + userId);
			}

			String recipient = "";
			String mngUser = "";

			// Executing User Management Edit Alert
			// ---------------------------------------------------------------------------------------------------------------
			if (sessionContext.getUserId().contains("@")) {
				BeaSystemOut.println("######################Executing User Management Edit Alert");

				BeaSystemOut.println("################User: " + userId);
				BeaSystemOut.println("###########Comp Name: " + partyDetails.getCompanyName());

				usermgmtActivityLog.setCustomerId(requestDTO.getCdcNo());
				usermgmtActivityLog.setUserSysDate(getFormatDate(new Date()));
				usermgmtActivityLog.setUserNameId(userId);
				usermgmtActivityLog.setCompName(maskName(sepChar(partyDetails.getCompanyName(), " "), 1));
				usermgmtActivityLog.setUserId(sessionContext.getUserId());

				BeaSystemOut.println("#########################usermgmtActivityLog: - " + usermgmtActivityLog.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
						UserExtensionDataConstants.USER_MANAGEMENT_EDIT, new Date(), usermgmtActivityLog);
				BeaSystemOut.println("############### Executed User Management Edit Alert");
			}

			// Executing other User management update alerts
			// ---------------------------------------------------------------------------------------------------------------

			if (!resultDTO.getEmailId() && resultDTO.getMobNo()) {

				BeaSystemOut.println("#############Entered If loop of USER_EMAIL_ADDRESS_UPDATE");

				BeaSystemOut.println("##########email: " + requestDTO.getUserDTO().getEmailId());
				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(requestDTO.getUserDTO().getMobileNumber());
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				// details[0] = messageNotificationDetail;
				details[0] = messageNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);
				activityLog.setEmailId(requestDTO.getUserDTO().getEmailId().replaceAll("(?<=.....).", "*"));
				activityLog.setCustomerId(requestDTO.getCdcNo());
				BeaSystemOut.println(
						"###############UserProfUpdateActivityLogDTO SMS with Party Id: - " + activityLog.toString());
				BeaSystemOut.println("################Session Context: " + sessionContext.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
						UserExtensionDataConstants.USER_EMAIL_ADDRESS_UPDATE, new Date(), activityLog);

				BeaSystemOut.println("######################Executed the USER_EMAIL_ADDRESS_UPDATE SMS Alert##########");

				BeaSystemOut.println("######################Executing the Email Update Email Alert##########");

				List<String> userEmailList = new ArrayList<String>();

				com.ofss.digx.domain.sms.entity.user.User signerUser = new com.ofss.digx.domain.sms.entity.user.User();
				UserKey userKey = new UserKey();

				userKey.setUserId(sessionContext.getUserId());
				signerUser = signerUser.read(userKey);

				if (partyDetails != null && partyDetails.getOfficeEmailId() != null) {
					userEmailList.add(partyDetails.getOfficeEmailId());
				}

				userEmailList.add(userDomain.getEmailId() + "~" + requestDTO.getUserID());
				userEmailList.add(requestDTO.getUserDTO().getEmailId() + "~" + requestDTO.getUserID());
				userEmailList.add(signerUser.getEmailId());

				for (String userEmail : userEmailList) {
					BeaSystemOut.println("###############Entered Email IDs list: " + userEmail);

					mngUser = "";
					// userId = requestDTO.getUserID();
					BeaSystemOut.println("###User: " + userId);

					if (userEmail.contains("~")) {

						recipient = userEmail.split("~")[0];
						mngUser = userEmail.split("~")[1];
					} else {
						recipient = userEmail;
					}

					BeaSystemOut.println("###User recipient: " + recipient + " : mngUser - " + mngUser);

					emailNotificationDetail.setRecipientId(requestDTO.getCdcNo());
					emailNotificationDetail.setDestination(DestinationType.EMAIL);
					emailNotificationDetail.setDispatchAddress(recipient);
					emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

					details[0] = emailNotificationDetail;
					activityLog.setNotificationDetails(details);
					// activityLog.setUserId(sessionContext.getUserId());
					activityLog.setProfileUser(userId);
					// activityLog.setUserName(userFromDb.getFirstName());
					activityLog.setCustomerId(requestDTO.getCdcNo());
					if (mngUser != null && !mngUser.equalsIgnoreCase("")) {
						activityLog.setEngMailSubj("Email Address Update" + "~" + mngUser + "~");
					} else {
						activityLog.setEngMailSubj("Email Address Update");
					}
					activityLog.setZhMailSubj(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
							"INFOUPDATE_EMAIL_UPDATE_MAILSUBJ", ""));
					activityLog.setEngMailContent("email address.");
					activityLog.setZhMailContent(customConfigAdapter.getConfiguationDetails(
							CommonConstants.DAY_ONE_CONFIG, "INFOUPDATE_EMAIL_UPDATE_MAILCONTENT", ""));
					BeaSystemOut.println(
							"###############UserProfUpdateActivityLogDTO for email update INFO_UPDATE_BY_CORP_ADMIN - "
									+ activityLog.toString());
					BeaSystemOut.println("################Session Context: " + sessionContext.toString());
					super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
							UserExtensionDataConstants.INFO_UPDATE_BY_CORP_ADMIN, new Date(), activityLog);

					BeaSystemOut.println(
							"######################Executed the email INFO_UPDATE_BY_CORP_ADMIN Alert##########");
				}

			}
			if (!resultDTO.getMobNo() && resultDTO.getEmailId()) {

				BeaSystemOut.println("### Mobile update to new nob number Alert");
				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(requestDTO.getUserDTO().getMobileNumber());
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				BeaSystemOut.println("#### User New mobile no: " + requestDTO.getUserDTO().getMobileNumber());

				details[0] = messageNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setCustomerId(requestDTO.getCdcNo());
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);

				BeaSystemOut.println("################# userCreateWelcomeAlert Party Id: " + requestDTO.getCdcNo());
				BeaSystemOut.println("################ userCreateWelcomeAlert activityLog: " + activityLog.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".create",
						UserExtensionDataConstants.CORPORATEPLUS_WELCOME_MAIL, new Date(), activityLog);
				BeaSystemOut.println("############### Executed Mobile update to new nob number Alert");

				// -------------------------------------------------------------------------------------------------
				BeaSystemOut.println("########################USER_MOBILE_NUMBER_UPDATED_REMINDER Alert begin");
				BeaSystemOut.println("################Old Mob:- " + oldMobNo);
				BeaSystemOut.println("################New Mob:- " + requestDTO.getUserDTO().getMobileNumber());

				// String user = requestDTO.getUserID();
				BeaSystemOut.println("################User: " + userId);
				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(oldMobNo);
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				details[0] = messageNotificationDetail;
				// details[1] = messageNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);
				activityLog.setCustomerId(requestDTO.getCdcNo());
				BeaSystemOut.println(
						"###############UserProfUpdateActivityLogDTO SMS with Party Id: - " + activityLog.toString());
				BeaSystemOut.println("################Session Context: " + sessionContext.toString());

				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
						UserExtensionDataConstants.USER_MOBILE_NUMBER_UPDATED_REMINDER, new Date(), activityLog);

				BeaSystemOut.println(
						"######################Executed the USER_MOBILE_NUMBER_UPDATED_REMINDER SMS Alert##########");
				BeaSystemOut.println("######################Executing the Mob Update Email Alert##########");

				List<String> userEmailList = new ArrayList<String>();
				// com.ofss.digx.domain.sms.entity.user.User userFromDb = new
				// com.ofss.digx.domain.sms.entity.user.User();
				com.ofss.digx.domain.sms.entity.user.User signerUser = new com.ofss.digx.domain.sms.entity.user.User();
				UserKey userKey = new UserKey();
				/*
				 * userKey.setUserId(requestDTO.getUserID()); userFromDb =
				 * userFromDb.read(userKey);
				 */

				userKey.setUserId(sessionContext.getUserId());
				signerUser = signerUser.read(userKey);

				if (partyDetails != null && partyDetails.getOfficeEmailId() != null) {
					userEmailList.add(partyDetails.getOfficeEmailId());
				}

				// userEmailList.add(userDomain.getEmailId());
				userEmailList.add(requestDTO.getUserDTO().getEmailId() + "~" + requestDTO.getUserID());
				userEmailList.add(signerUser.getEmailId());

				for (String userEmail : userEmailList) {
					BeaSystemOut.println("###############Entered Email IDs list for MobNo Update: " + userEmail);

					mngUser = "";
					// userId = requestDTO.getUserID();
					BeaSystemOut.println("###User: " + userId);

					if (userEmail.contains("~")) {

						recipient = userEmail.split("~")[0];
						mngUser = userEmail.split("~")[1];
					} else {
						recipient = userEmail;
					}

					BeaSystemOut.println("###User recipient: " + recipient + " : mngUser - " + mngUser);

					emailNotificationDetail.setRecipientId(requestDTO.getCdcNo());
					emailNotificationDetail.setDestination(DestinationType.EMAIL);
					emailNotificationDetail.setDispatchAddress(recipient);
					emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

					details[0] = emailNotificationDetail;
					activityLog.setNotificationDetails(details);
					// activityLog.setUserId(sessionContext.getUserId());
					activityLog.setProfileUser(userId);
					// activityLog.setUserName(userFromDb.getFirstName());
					activityLog.setCustomerId(requestDTO.getCdcNo());
					if (mngUser != null && !mngUser.equalsIgnoreCase("")) {
						activityLog.setEngMailSubj("Mobile No. Update" + "~" + mngUser + "~");
					} else {
						activityLog.setEngMailSubj("Mobile No. Update");
					}

					activityLog.setZhMailSubj(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
							"INFOUPDATE_MOB_UPDATE_MAILSUBJ", ""));
					activityLog.setEngMailContent("mobile no.");
					activityLog.setZhMailContent(customConfigAdapter.getConfiguationDetails(
							CommonConstants.DAY_ONE_CONFIG, "INFOUPDATE_MOB_UPDATE_MAILCONTENT", ""));
					BeaSystemOut.println(
							"###############UserProfUpdateActivityLogDTO for MobNo INFO_UPDATE_BY_CORP_ADMIN - "
									+ activityLog.toString());
					BeaSystemOut.println("################Session Context: " + sessionContext.toString());
					super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
							UserExtensionDataConstants.INFO_UPDATE_BY_CORP_ADMIN, new Date(), activityLog);

					BeaSystemOut.println(
							"######################Executed the MobNo INFO_UPDATE_BY_CORP_ADMIN Alert##########");

				}
			}

			if (!resultDTO.getMobNo() && !resultDTO.getEmailId()) {
				BeaSystemOut.println("######################Executing the Email & Mob Update Email Alert##########");

				List<String> userEmailList = new ArrayList<String>();

				com.ofss.digx.domain.sms.entity.user.User signerUser = new com.ofss.digx.domain.sms.entity.user.User();
				UserKey userKey = new UserKey();

				userKey.setUserId(sessionContext.getUserId());
				signerUser = signerUser.read(userKey);

				if (partyDetails != null && partyDetails.getOfficeEmailId() != null) {
					userEmailList.add(partyDetails.getOfficeEmailId());
				}

				userEmailList.add(userDomain.getEmailId() + "~" + requestDTO.getUserID());
				userEmailList.add(requestDTO.getUserDTO().getEmailId() + "~" + requestDTO.getUserID());
				userEmailList.add(signerUser.getEmailId());

				for (String userEmail : userEmailList) {
					BeaSystemOut.println("###############Entered Email IDs list for Email & MobNo Update: " + userEmail);
					mngUser = "";
					// userId = requestDTO.getUserID();
					BeaSystemOut.println("##User: " + userId);

					if (userEmail.contains("~")) {

						recipient = userEmail.split("~")[0];
						mngUser = userEmail.split("~")[1];
					} else {
						recipient = userEmail;
					}

					BeaSystemOut.println("###User recipient: " + recipient + " : mngUser - " + mngUser);

					emailNotificationDetail.setRecipientId(requestDTO.getCdcNo());
					emailNotificationDetail.setDestination(DestinationType.EMAIL);
					emailNotificationDetail.setDispatchAddress(recipient);
					emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

					details[0] = emailNotificationDetail;
					activityLog.setNotificationDetails(details);
					// activityLog.setUserId(sessionContext.getUserId());
					activityLog.setProfileUser(userId);
					// activityLog.setUserName(userFromDb.getFirstName());
					activityLog.setCustomerId(requestDTO.getCdcNo());
					if (mngUser != null && !mngUser.equalsIgnoreCase("")) {
						activityLog.setEngMailSubj("Email Address and Mobile No. Update" + "~" + mngUser + "~");
					} else {
						activityLog.setEngMailSubj("Email Address and Mobile No. Update");
					}

					activityLog.setZhMailSubj(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
							"INFOUPDATE_EMAILMOB_UPDATE_MAILSUBJ", ""));
					activityLog.setEngMailContent("email address and mobile no.");
					activityLog.setZhMailContent(customConfigAdapter.getConfiguationDetails(
							CommonConstants.DAY_ONE_CONFIG, "INFOUPDATE_EMAILMOB_UPDATE_MAILCONTENT", ""));
					BeaSystemOut.println(
							"###############UserProfUpdateActivityLogDTO for Email & MobNo INFO_UPDATE_BY_CORP_ADMIN - "
									+ activityLog.toString());
					BeaSystemOut.println("################Session Context: " + sessionContext.toString());
					super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
							UserExtensionDataConstants.INFO_UPDATE_BY_CORP_ADMIN, new Date(), activityLog);

					BeaSystemOut.println(
							"######################Executed the Email & MobNo INFO_UPDATE_BY_CORP_ADMIN Alert##########");

				}

				// ------------------------------------------------------------
				BeaSystemOut.println("### Mobile update to new nob number Alert");
				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(requestDTO.getUserDTO().getMobileNumber());
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				BeaSystemOut.println("#### User New mobile no: " + requestDTO.getUserDTO().getMobileNumber());

				details[0] = messageNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setCustomerId(requestDTO.getCdcNo());
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);

				BeaSystemOut.println("################# userCreateWelcomeAlert Party Id: " + requestDTO.getCdcNo());
				BeaSystemOut.println("################ userCreateWelcomeAlert activityLog: " + activityLog.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".create",
						UserExtensionDataConstants.CORPORATEPLUS_WELCOME_MAIL, new Date(), activityLog);
				BeaSystemOut.println("############### Executed Mobile update to new nob number Alert");

				// Executing USER_MOBILE_NUMBER_UPDATED_REMINDER Alert
				// --------------------------------------------------------------------------------------------------------------
				BeaSystemOut.println("########################USER_MOBILE_NUMBER_UPDATED_REMINDER Alert begin");
				BeaSystemOut.println("################Old Mob:- " + oldMobNo);
				BeaSystemOut.println("################New Mob:- " + requestDTO.getUserDTO().getMobileNumber());

				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(oldMobNo);
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

				BeaSystemOut.println("#################USER_MOBILE_NUMBER_UPDATED_REMINDER DispatchAddress:- "
						+ messageNotificationDetail.getDispatchAddress());

				details[0] = messageNotificationDetail;

				// userId = requestDTO.getUserID();
				BeaSystemOut.println("################User: " + userId);

				activityLog.setNotificationDetails(details);
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);
				activityLog.setCustomerId(requestDTO.getCdcNo());
				BeaSystemOut.println(
						"###############UserProfUpdateActivityLogDTO SMS with Party Id: - " + activityLog.toString());
				BeaSystemOut.println("################Session Context: " + sessionContext.toString());

				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
						UserExtensionDataConstants.USER_MOBILE_NUMBER_UPDATED_REMINDER, new Date(), activityLog);

				BeaSystemOut.println(
						"######################Executed the USER_MOBILE_NUMBER_UPDATED_REMINDER SMS Alert##########");

				// Executing USER_EMAIL_ADDRESS_UPDATE Alert
				// --------------------------------------------------------------------------------------------------------------

				BeaSystemOut.println("#############Entered If loop of USER_EMAIL_ADDRESS_UPDATE");

				// String user = requestDTO.getUserID();
				BeaSystemOut.println("###############user:- " + userId);
				BeaSystemOut.println("##########email: " + requestDTO.getUserDTO().getEmailId());
				messageNotificationDetail.setRecipientId(requestDTO.getCdcNo());
				messageNotificationDetail.setDestination(DestinationType.SMS);
				messageNotificationDetail.setDispatchAddress(requestDTO.getUserDTO().getMobileNumber());
				messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
				BeaSystemOut.println("#################USER_EMAIL_ADDRESS_UPDATE DispatchAddress:- "
						+ messageNotificationDetail.getDispatchAddress());

				// details[0] = messageNotificationDetail;
				details[0] = messageNotificationDetail;
				activityLog.setNotificationDetails(details);
				activityLog.setUserId(requestDTO.getUserID());
				activityLog.setProfileUser(userId);
				activityLog.setEmailId(requestDTO.getUserDTO().getEmailId().replaceAll("(?<=.....).", "*"));
				activityLog.setCustomerId(requestDTO.getCdcNo());
				BeaSystemOut.println(
						"###############UserProfUpdateActivityLogDTO SMS with Party Id: - " + activityLog.toString());
				BeaSystemOut.println("################Session Context: " + sessionContext.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".update",
						UserExtensionDataConstants.USER_EMAIL_ADDRESS_UPDATE, new Date(), activityLog);

				BeaSystemOut.println("######################Executed the USER_EMAIL_ADDRESS_UPDATE SMS Alert##########");

			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the alertUserProfileUpdate method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);
			// fillTransactionStatus(transactionStatus, e);

		} catch (java.lang.Exception e) {
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"Java.lang Exception encountered while communicating the alert. Exception details are %s"),
					e);
			// fillTransactionStatus(transactionStatus, e);

		}
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Exit from alertUserProfileUpdate method of UserExtensionData class %s", THIS_COMPONENT_NAME));
		}
	}

	public void userCreateWelcomeAlert(SessionContext sessionContext, UserExtensionDataDTO requestDTO) {

		UserExtensionDataActivityLogDTO activityLog = new UserExtensionDataActivityLogDTO();

		UserManagementActivityLogDTO usermgmtActivityLog = new UserManagementActivityLogDTO();

		NotificationDetail[] details = new NotificationDetail[1];

		// NotificationDetail emailNotificationDetail = new NotificationDetail();
		NotificationDetail smsNotificationDetail = new NotificationDetail();

		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);

		try {

			com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
					.getInstance().getAdapterFactory(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
			IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
					.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);

			CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(requestDTO.getCdcNo());

			UUID uuid = UUID.randomUUID();
			String userId = "";
			BeaSystemOut.println("###################Entering the Welcome Email Alert");
			BeaSystemOut.println("################ Welcome User RequestDTO: " + requestDTO.toString());
			BeaSystemOut.println("##################### Session User: " + sessionContext.getUserId());

			BeaSystemOut.println("###################Office Email: " + partyDetails.getOfficeEmailId());
			BeaSystemOut.println("###################Company Name: " + partyDetails.getCompanyName());

			/*
			 * com.ofss.digx.domain.sms.entity.user.User newUser = new
			 * com.ofss.digx.domain.sms.entity.user.User(); UserKey userKey = new UserKey();
			 * userKey.setUserId(requestDTO.getUserID()); newUser = newUser.read(userKey);
			 */

			if (requestDTO.getUserID() != null && !requestDTO.getUserID().equalsIgnoreCase("")) {
				String[] userDtls = requestDTO.getUserID().split("@");
				userId = userDtls[0];
				BeaSystemOut.println("################User: " + userId);
			}

			Random rnd = SecureRandom.getInstanceStrong();
			int number = rnd.nextInt(999999);

			String messageID = String.format("%06d", number);
			messageID = "WW" + messageID;
			BeaSystemOut.println("################Welcome Email MessageId:- " + messageID);
			Date currentDate = new Date();

			MailBoxMailerUser mailboxmaileruser = new MailBoxMailerUser();
			MailBoxMailerUserKey mailboxmaileruserkey = new MailBoxMailerUserKey();

			MailBoxMailer mailboxmailer = new MailBoxMailer();
			MailBoxMailerKey mailboxmailerkey = new MailBoxMailerKey();

			MailBoxMessage mailboxmessage = new MailBoxMessage();
			MailBoxMessageKey mailboxmessagekey = new MailBoxMessageKey();

			// setting domain for DIGX_CO_MAILBOX_MAILER_USER
			mailboxmaileruserkey.setId(uuid.toString().replace("-", ""));
			mailboxmaileruser.setKey(mailboxmaileruserkey);
			BeaSystemOut.println("###################ID: " + mailboxmaileruserkey.getId());

			mailboxmaileruser.setMessageId(messageID);
			mailboxmaileruser.setUserName(null);
			mailboxmaileruser.setUserId(requestDTO.getUserID());
			mailboxmaileruser.setSubject(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERUSER_MESSAGE_SUBJECT", ""));
			mailboxmaileruser.setPriority(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERUSER_MESSAGE_PRIORITY", ""));
			mailboxmaileruser.setMsgStatus(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERUSER_MESSAGE_STATUS", ""));
			mailboxmaileruser.setReceivedDate(currentDate);
			mailboxmaileruser.setDismissed("N");
			mailboxmaileruser.setCreatedBy(requestDTO.getCreatedBy());
			mailboxmaileruser.setCreationDate(currentDate);
			mailboxmaileruser.setLastUpdatedBy(requestDTO.getLastUpdatedBy());
			mailboxmaileruser.setLastUpdatedDate(currentDate);
			mailboxmaileruser.setVersionMailBox(1);
			mailboxmaileruser.setDeterminantValue("OBDX_BU");

			// setting domain for DIGX_CO_MAILBOX_MAILER
			mailboxmailerkey.setMessageId(messageID);
			mailboxmailerkey.setDeterminantValue("OBDX_BU");
			mailboxmailer.setKey(mailboxmailerkey);
			mailboxmailer.setDescription(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILER_MESSAGE_DESCRIPTION", ""));
			mailboxmailer.setCode(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILER_MESSAGE_CODE", ""));
			mailboxmailer.setPriority(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERUSER_MESSAGE_PRIORITY", ""));
			mailboxmailer.setTriggerType(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILER_MESSAGE_TRIGGERTYPE", ""));
			mailboxmailer.setActivationDate(currentDate);
			mailboxmailer.setBannerBody(null);

			// setting domain for DIGX_CO_MAILBOX_MESSAGE
			mailboxmessagekey.setMessageId(messageID);
			mailboxmessagekey.setDeterminantValue("OBDX_BU");
			mailboxmessage.setKey(mailboxmessagekey);

			mailboxmessage.setCreationDate(currentDate);
			mailboxmessage.setExpiryDate(new Date(15, 11, 2199));
			mailboxmessage.setMessageType(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERMESSAGE_MESSAGE_TYPE", ""));
			mailboxmessage.setSubject(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERUSER_MESSAGE_SUBJECT", ""));
			mailboxmessage.setMessageBody(customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
					"MAILBOXMAILERMESSAGE_MESSAGE_BODY_FIRST", "")
					+ customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
							"MAILBOXMAILERMESSAGE_MESSAGE_BODY_SECOND", ""));
			mailboxmessage.setSenderName(null);
			mailboxmessage.setCreatedBy(requestDTO.getCreatedBy());
			mailboxmessage.setLastUpdatedBy(requestDTO.getLastUpdatedBy());
			mailboxmessage.setLastUpdatedDate(currentDate);
			mailboxmessage.setEntityStatus(null);
			mailboxmessage.setVersion(1);

			BeaSystemOut.println("#####################mailboxmaileruser domain: " + mailboxmaileruser.toString());
			BeaSystemOut.println("#####################mailboxmailer domain: " + mailboxmailer.toString());
			BeaSystemOut.println("#####################mailboxmessage domain: " + mailboxmessage.toString());

			if (requestDTO.getIsAuthorisedPerson()) {
				BeaSystemOut.println("########################User is AP");
				// mailboxmailer.create(mailboxmailer);
				// mailboxmessage.create(mailboxmessage);
				// mailboxmaileruser.create(mailboxmaileruser);

			}

			// Executing User Management Create Alert
			// -------------------------------------------------------------------------------------------------------------

			if (sessionContext.getUserId().contains("@")) {
				BeaSystemOut.println("######################Executing User Management Create Alert");

				BeaSystemOut.println("################Company Name: " + partyDetails.getCompanyName());
				usermgmtActivityLog.setCustomerId(requestDTO.getCdcNo());
				usermgmtActivityLog.setUserSysDate(getFormatDate(new Date()));
				usermgmtActivityLog.setUserNameId(userId);
				usermgmtActivityLog.setUserId(sessionContext.getUserId());
				usermgmtActivityLog.setCompName(maskName(sepChar(partyDetails.getCompanyName(), " "), 1));

				BeaSystemOut.println("#########################usermgmtActivityLog: - " + usermgmtActivityLog.toString());
				super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".create",
						UserExtensionDataConstants.USER_MANAGEMENT_CREATE, new Date(), usermgmtActivityLog);
				BeaSystemOut.println("############### Executed User Management Create Alert");
			}

			// Executing Welcome User New mobile Register Alert
			// -------------------------------------------------------------------------------------------------------------------

			BeaSystemOut.println("######################Welcome User New mobile Register Alert");
			smsNotificationDetail.setRecipientId(requestDTO.getCdcNo());
			smsNotificationDetail.setDestination(DestinationType.SMS);
			smsNotificationDetail.setDispatchAddress(requestDTO.getMobileNo());
			smsNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
			BeaSystemOut.println("###################### Welcome User New mobile no: " + requestDTO.getMobileNo());

			details[0] = smsNotificationDetail;
			activityLog.setNotificationDetails(details);
			activityLog.setCustomerId(requestDTO.getCdcNo());
			activityLog.setUserId(requestDTO.getUserID());
			activityLog.setProfileUser(userId);

			BeaSystemOut.println("################# userCreateWelcomeAlert Party Id: " + requestDTO.getCdcNo());
			BeaSystemOut.println("################ userCreateWelcomeAlert activityLog: " + activityLog.toString());
			super.registerActivityAndGenerateEvent(sessionContext, THIS_COMPONENT_NAME + ".create",
					UserExtensionDataConstants.CORPORATEPLUS_WELCOME_MAIL, new Date(), activityLog);
			BeaSystemOut.println("############### Executed Welcome User Alert");

		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the userCreateWelcomeAlert method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);

		} catch (java.lang.Exception e) {
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"Java.lang Exception encountered while communicating the alert. Exception details are %s"),
					e);

		}
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Exit from userCreateWelcomeAlert method of UserExtensionData class %s", THIS_COMPONENT_NAME));
		}
	}

	public static String getCurrrentPinConfigDays() {
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String currrentPinConfigDays = customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
				CZCommonConstants.CURRENT_PIN_CONFIG_DAYS, "5");
		return currrentPinConfigDays;
	}

	/**
	 * Sets given warning code in the response.
	 *
	 * @param status
	 * @param warningCode
	 */
	private void setWarning(com.ofss.digx.app.messages.Status status, String warningCode, String... msgParams) {
		if (warningCode != null && warningCode.length() > 0) {
			status.getMessage().setCode(warningCode);
			status.getMessage().setType(MessageType.INFO);
			status.getMessage().setDetail(ErrorManager.buildErrorMessage(warningCode, msgParams, null));
		}
	}

	public static String getCentreCode() {
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String currrentPinConfigDays = customConfigAdapter.getConfiguationDetails(CommonConstants.DAY_ONE_CONFIG,
				CZCommonConstants.CENTRE_CODE, "0101247");
		return currrentPinConfigDays;
	}

	public UserListExtResponseDTO listUsersData(SessionContext sessionContext, UserPartyListDTO partyList)
			throws Exception {
		// TODO Auto-generated method stub
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Entering list method of class:%s, SessionContext:%s ,UserSearchRequestDTO:%s",
							THIS_COMPONENT_NAME, sessionContext, partyList));
		}
		super.canonicalizeInput(partyList);
		super.checkAccessPolicy("com.ofss.digx.app.sms.service.user.User.list", sessionContext, partyList);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		UserListExtResponseDTO userListResponseDTO = new UserListExtResponseDTO();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		try {

			Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");
			if (sessionContext.getTransactingPartyCode() != null) {
				AbstractBusinessPolicy abstractBusinessPolicy = null;
				BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
				UserExtensionPartyDTO userExtensionPartyDTO = new UserExtensionPartyDTO();
				userExtensionPartyDTO.setCdcNo(partyList.getParty().getValue());
				userExtensionPartyDTO.setSessionPartyId(sessionContext.getTransactingPartyCode());
				UserExtensionDataReadBusinessPolicyDTO policyDTO = new UserExtensionDataReadBusinessPolicyDTO(
						userExtensionPartyDTO);
				abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
						"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.listUsersData", policyDTO);
				abstractBusinessPolicy.validate();
			}

			List<CZUserDTO> responseData = new ArrayList<CZUserDTO>();
			List<CZUserDTO> userData = new ArrayList<CZUserDTO>();
			responseData = domain.listUsersData(partyList);

			if (!isAdmin) {
				/**
				 * If user is revoked, delete if from corporate admin list
				 */
				for (int i = 0; i < responseData.size(); i++) {
					if (responseData.get(i).getUserData().isDeleteStatus() == false) {
						userData.add(responseData.get(i));
					}
				}

				for (CZUserDTO item : userData) {
					UserDTO userDTO = item.getUserData();

					List<String> appRoles = fetchUserPrincipal(sessionContext, userDTO);
					userDTO.setUpdatable(
							checkDiscrepancyInRoles(sessionContext, appRoles, userDTO.getPartyId().getValue()));
					item.setIsDeleteAllowed(userDTO.isUpdatable());

					/**
					 * If user is AP, revoke is allowed to Bank admin only
					 */
					if (item.getUserExtendedType().equals("AuthorisedPerson")) {
						item.setIsDeleteAllowed(false);
						userDTO.setUpdatable(true);
					}

					/**
					 * lock status and channel access updated allowed for every user except logged
					 * in user
					 */
					if (userDTO.getUsername().equals(ThreadAttribute.get(ThreadAttribute.SUBJECTNAME))) {
						item.setIsDeleteAllowed(false);
						userDTO.setUpdatable(false);
					}

				}
				userListResponseDTO.setUserDTOList(userData);
			} else {
				for (CZUserDTO item : responseData) {
					UserDTO userDTO = item.getUserData();
					userDTO.setUpdatable(true);
					if (userDTO.isDeleteStatus()) {
						item.setIsDeleteAllowed(false);
					} else {
						item.setIsDeleteAllowed(true);
					}
				}
				userListResponseDTO.setUserDTOList(responseData);
			}

			userListResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the list method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage(
							"RuntimeException from read method of class %s for UserSearchRequestDTO '%s'",
							THIS_COMPONENT_NAME, partyList),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, userListResponseDTO);
		super.encodeOutput(userListResponseDTO);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting list method of class : %s, UserSearchResponseDTO:%s",
							THIS_COMPONENT_NAME, userListResponseDTO));
		}
		return userListResponseDTO;

	}

	private List<String> fetchUserPrincipal(SessionContext sessionContext, UserDTO userDTO) throws Exception {
		UserPrincipal userPrincipal = new UserPrincipal();
		UserPrincipalDTO userPrincipalDTO = new UserPrincipalDTO();
		UserPrincipalRequestDTO requestDTO = new UserPrincipalRequestDTO();
		List<UserPrincipalDTO> userPrincipalDTOs = new ArrayList<>();
		List<String> principal = new ArrayList<>();
		UserPrincipalResponseDTO response = new UserPrincipalResponseDTO();
		String username = userDTO.getUsername();
		userPrincipalDTO.setUsername(username);
		userPrincipalDTOs.add(userPrincipalDTO);
		requestDTO.setUserPrincipalDTOs(userPrincipalDTOs);
		response = userPrincipal.search(sessionContext, requestDTO);
		if (response.getUserPrincipalDTOs() != null) {
			for (UserPrincipalDTO dto : response.getUserPrincipalDTOs()) {
				principal.add(dto.getPrincipal());
			}
		}
		return principal;
	}

	/**
	 * This method returns the party preferences maintained for the party It is used
	 * in maintenance service to check isCorpAdminEnabled or not for the specified
	 * party
	 * 
	 * @param sessionContext
	 * @param partyId
	 *
	 * 
	 * @return partyPreferences maintained for the party in the form of
	 *         {@link com.ofss.digx.app.party.dto.profile.PartyPreferencesResponse}}
	 */
	protected PartyPreferencesResponse readPartyPreferences(SessionContext sessionContext, String partyId)
			throws Exception {

		/* Read party preference for the logged in user */
		/* PartyPreference adapter call */
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(CommonAdapterFactoryConstants.PARTY_PREFERENCES_ADAPTER_FACTORY);
		IPartyPreferencesAdapter partyPreferenceAdapter = (IPartyPreferencesAdapter) adapterFactory
				.getAdapter(CommonAdapterConstants.PARTY_PREFERENCES_ADAPTER);
		PartyPreferencesResponse partyPreferenceResponse;

		PartyPreferencesDTO partyPreferencesDTO = new PartyPreferencesDTO();
		partyPreferencesDTO.setParty(new Party(partyId));

		partyPreferenceResponse = partyPreferenceAdapter.read(sessionContext, partyPreferencesDTO);
		return partyPreferenceResponse;
	}

	private boolean checkDiscrepancyInRoles(SessionContext sessionContext, List<String> appRoles, String partyId)
			throws Exception {

		boolean updatable = true;
		PartyPreferencesResponse partyPreferences = readPartyPreferences(sessionContext, partyId);
		List<String> allowedRoles = null;
		if (partyPreferences != null && partyPreferences.getPartyPreferencesDTOs() != null) {
			allowedRoles = partyPreferences.getPartyPreferencesDTOs().getAllowedRoles();
			if (allowedRoles != null && !allowedRoles.isEmpty()) {
				for (String userRole : appRoles) {
					if (!allowedRoles.contains(userRole)) {
						updatable = false;
						break;
					}

				}
			}

		}
		return updatable;
	}

	@Override
	public TransactionStatus updatePasswordExpiryDate(SessionContext sessionContext,
			PasswordExpiryDTO passwordExpiryDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Entering updatePasswordExpiryDate method of class:%s, SessionContext:%s ,PasswordExpiryDTO:%s",
					THIS_COMPONENT_NAME, sessionContext, passwordExpiryDTO));
		}
		super.canonicalizeInput(passwordExpiryDTO);
		super.checkAccessPolicy("com.ofss.digx.app.user.service.User.updateProfile", sessionContext, passwordExpiryDTO);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		UserResponseDTO userResponseDTO = new UserResponseDTO();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");
		try {
			IAdapterFactory passwordPolicyAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(CommonAdapterFactoryConstants.PASSWORD_POLICY_ADAPTER_FACTORY);
			IPasswordPolicyAdapter passwordPolicyAdapter = (IPasswordPolicyAdapter) passwordPolicyAdapterFactory
					.getAdapter(CommonAdapterConstants.PASSWORD_POLICY_ADAPTER);
			PasswordPolicyResponseDTO passwordPolicyResponse = passwordPolicyAdapter
					.fetchPasswordPolicy(ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID).toString());

			if (passwordExpiryDTO.getUserType().equals(LOGIN)) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.USER_ME_ADAPTER_FACTORY);
				IUserMeAdapter adapter = (IUserMeAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.USER_ME_ADAPTER);
				userResponseDTO = adapter.readUser(sessionContext,
						(String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));

				String loginPinExtendDays = dayOneConfigPref.get(LOGIN_PIN_EXPIRY_EXTEND_DAYS, "10");

				Date todayDate = new Date();
				if (userResponseDTO.getUserDTO().getPwdExpiryDate().compareTo(todayDate) == -1) {
					passwordExpiryDTO.setPwdExpiryDate(
							todayDate.plusDays(passwordPolicyResponse.getPasswordPolicyDTO().getPwdMaxExpiryDays()));
				} else {
					passwordExpiryDTO.setPwdExpiryDate(userResponseDTO.getUserDTO().getPwdExpiryDate()
							.plusDays(Integer.parseInt(loginPinExtendDays)));
				}

				passwordExpiryDTO.setUserId((String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));
				domain.updatePasswordExpiryDate(passwordExpiryDTO);
				userResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
			} else if (passwordExpiryDTO.getUserType().equals(SIGNER) && !isAdmin) {
				UserExtensionDataKey key = new UserExtensionDataKey();
				key.setUserExtensionKey(sessionContext.getUserId());
				domain.setUserExtensionDataKey(key);
				domain = domain.read(key);
				if (domain != null && domain.getSignerID() != null) {
					String loginPinExtendDays = dayOneConfigPref.get(SIGNER_PIN_EXPIRY_EXTEND_DAYS, "10");

					Date todayDate = new Date();
					if (domain.getSignPinExpiryDate().compareTo(todayDate) == -1) {
						passwordExpiryDTO.setPwdExpiryDate(todayDate
								.plusDays(passwordPolicyResponse.getPasswordPolicyDTO().getPwdMaxExpiryDays()));
					} else {
						passwordExpiryDTO.setPwdExpiryDate(
								domain.getSignPinExpiryDate().plusDays(Integer.parseInt(loginPinExtendDays)));
					}

					passwordExpiryDTO.setUserId((String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));
					domain.updatePasswordExpiryDate(passwordExpiryDTO);
					userResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
				}
			} else if (passwordExpiryDTO.getUserType().equals(LOGIN_REMINDER) && !isAdmin
					&& passwordExpiryDTO.getFlag() != null) {
				UserExtensionDataKey key = new UserExtensionDataKey();
				key.setUserExtensionKey(sessionContext.getUserId());
				domain.setUserExtensionDataKey(key);
				domain = domain.read(key);
				domain.setIsLoginPinReminder(passwordExpiryDTO.getFlag());
				domain.update(domain);
				userResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
			} else if (passwordExpiryDTO.getUserType().equals(SIGNER_REMINDER) && !isAdmin
					&& passwordExpiryDTO.getFlag() != null) {
				UserExtensionDataKey key = new UserExtensionDataKey();
				key.setUserExtensionKey(sessionContext.getUserId());
				domain.setUserExtensionDataKey(key);
				domain = domain.read(key);
				domain.setIsSignerPinReminder(passwordExpiryDTO.getFlag());
				domain.update(domain);
				userResponseDTO.setStatus(buildStatus(fetchTransactionStatus()));
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					" FatalException has occurred while getting response object of inside the updatePasswordExpiryDate method of %s. Exception details are %s",
					THIS_COMPONENT_NAME), e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE, formatter.formatMessage(
					"RuntimeException from updatePasswordExpiryDate method of class %s for PasswordExpiryDTO '%s'",
					THIS_COMPONENT_NAME, userResponseDTO), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, userResponseDTO);
		super.encodeOutput(userResponseDTO);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Exiting updatePasswordExpiryDate method of class : %s, UserSearchResponseDTO:%s",
							THIS_COMPONENT_NAME, userResponseDTO));
		}
		return transactionStatus;

	}

	/**
	 * Utility to provide the FC Date in format yyyy/MM/dd HH:mm
	 * 
	 * @param date
	 * @return
	 */
	public String getFormatDate(Date date) {
		LocalDateTime currentDateTime = LocalDateTime.now();

		LocalDateTime newDateTime = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(),
				currentDateTime.getHour(), currentDateTime.getMinute());
		String text = newDateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
		return text;
	}

	/**
	 * Utility to mask the UserId for User Management Create/Edit alerts
	 * 
	 * @param strText
	 * @param start
	 * @param end
	 * @param maskChar
	 * @return
	 * @throws Exception
	 */
	public String maskActNo(String strText, int start, int end, char maskChar) throws Exception {

		if (strText == null || strText.equals(""))
			return "";

		if (start < 0)
			start = 0;

		if (end > strText.length())
			end = strText.length();

		if (start > end)
			throw new Exception("End index cannot be greater than start index");

		int maskLength = end - start;

		if (maskLength == 0)
			return strText;

		StringBuilder sbMaskString = new StringBuilder(maskLength);

		for (int i = 0; i < maskLength; i++) {
			sbMaskString.append(maskChar);
		}
		BeaSystemOut.println("#######strText length: " + strText.length());
		BeaSystemOut.println("#######start: " + start);
		BeaSystemOut.println("#######end: " + end);
		BeaSystemOut.println("sbMaskString:- " + sbMaskString.toString());
		BeaSystemOut.println("##" + strText.substring(0, start));
		BeaSystemOut.println("####" + strText.substring(start + maskLength));

		return strText.substring(0, start) + sbMaskString.toString() + strText.substring(start + maskLength);
	}

	/**
	 * This method is to mask the provided string (Name) with first character of
	 * each words being printed and remaining characters are masked with masking
	 * character Eg: REVAMP1********* T****** A****** N******
	 * 
	 * @param entryList
	 * @param maskStartElemIndex
	 * @return
	 */
	public static String maskName(List<String> entryList, int maskStartElemIndex) {
		String str = "";
		if (entryList == null) {
			return "";
		}
		if (entryList.size() > maskStartElemIndex) {
			for (int j = 0; j < maskStartElemIndex; j++) {
				str = str + " " + entryList.get(j);
			} // com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("str=" + str);// take out part that does not require
				// masking
			for (int j = 0; j < entryList.size() - maskStartElemIndex; j++) {
				str = str + " " + repString(entryList.get(j + maskStartElemIndex));
			}
		} else if (entryList.size() == maskStartElemIndex) {// added for single word name
			for (int j = 0; j < entryList.size(); j++) {
				str = str + " " + repString(entryList.get(j));
			}
		} else {
			for (int j = 0; j < entryList.size(); j++) {
				str = str + " " + entryList.get(j);
			}
		}
		return str.trim();
	}

	public static String repString(String s) {
		BeaSystemOut.println("#####rep String:- " + s);
		if (s == null || s.length() < 1) {
			return "";
		}
		int len = s.length();
		BeaSystemOut.println("######s length:- " + len);
		if (len > 1) {
			s = s.substring(0, 1);
			BeaSystemOut.println("#######s:- " + s);
			for (int i = 0; i < len - 1; i++) {
				s = s + "*";
			}
			BeaSystemOut.println("######masked:- " + s);
			return s;
		} else {
			BeaSystemOut.println("######unmaksed:- " + s);
			return s;
		}
	}

	/**
	 * To split the string with a delimiter and them to a List
	 * 
	 * @param ch
	 * @param delimiter
	 * @return
	 */
	public static List<String> sepChar(String ch, String delimiter) {
		if (ch == null || ch.length() < 1) {
			return null;
		}
		String[] chArray = ch.split(delimiter);
		List<String> chList = new ArrayList<String>();
		if (chArray.length > 0) {
			for (String name : chArray) {
				chList.add(name);
			}
		}
		BeaSystemOut.println("Size: " + chList.size());
		return chList;
	}

	@Override
	@Entitlement(name = "Update UserExtensionData", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read" })
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_UUS", parent = "MT", name = "Update UserExtensionData", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public TransactionStatus updateUserExtensionData(SessionContext sessionContext, UserExtensionDataDTO requestDTO,
			Boolean isLoginPinChange) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Entered into updateUserExtensionData() : requestDTO = %s in class %s ",
							requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update", sessionContext,
				requestDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();

		try {
			extensionExecutor.preUpdate(sessionContext, requestDTO);
			requestDTO.validate(sessionContext);

			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserExtensionKey());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			if (domain != null) {
				BeaSystemOut.println("Domain Found");
				if (isLoginPinChange) {
					BeaSystemOut.println("Updating for login pin");
					domain.setLoginHoldReason(requestDTO.getLoginHoldReason());
					domain.setLoginHoldStatus(requestDTO.getLoginHoldStatus());
					domain.setLoginPinstatus(requestDTO.getLoginPinstatus());
				} else {
					BeaSystemOut.println("Updating for signer pin");
					domain.setSignerAttempts(requestDTO.getSignerAttempts());
					domain.setSignerHoldReason(requestDTO.getSignerHoldReason());
					domain.setSignerHoldStatus(requestDTO.getSignerHoldStatus());
					domain.setSignerPinstatus(requestDTO.getSignerPinstatus());
				}
				domain.updateUserExtensionData(domain);
				BeaSystemOut.println("Domain Updated");
			}
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from update() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from update() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from update() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	public TransactionStatus updateBounceBackReminder(SessionContext sessionContext, String flag) throws Exception {

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage(
					"Entered into updateBounceBackReminder() : requestDTO = %s in class %s ", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.app.user.service.User.updateProfile", sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();

		try {

			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(sessionContext.getUserId());
			BeaSystemOut.println("User id is " + sessionContext.getUserId());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			if (domain != null) {
				BeaSystemOut.println("Updating for updateBounceBackReminder");
				domain.setBounceBackReminder(flag);
				domain.update(domain);
				BeaSystemOut.println("Domain Updated");
			}
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from update() for requestDTO '%s' in class %s",
					THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE, formatter.formatMessage(
					"RuntimeException from update() for requestDTO '%s' in class %s", THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from update() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;

	}

	private UserExtensionDataDTO createUserGroupDTOForCreate(UserExtensionDataDTO requestDTO) throws Exception {

		BeaSystemOut.println("#GROUP group size 1 ==" + requestDTO.getUserDTO().getUserGroupDTOs().size());

		/**
		 * Remove existing users from the list
		 */
		for (UserGroupDTO userGroupDTO : requestDTO.getUserDTO().getUserGroupDTOs()) {
			userGroupDTO.getUsers().clear();
		}

		/**
		 * Traverse through group dto of request
		 */
		for (UserGroupDTO userGroupDTO : requestDTO.getUserDTO().getUserGroupDTOs()) {

			/**
			 * Read each group domain
			 */
			List<UserGroupUserDTO> users = new ArrayList<UserGroupUserDTO>();
			UserGroup userGroupDomain = new UserGroup();
			UserGroupKey userGroupKey = new UserGroupKey();
			userGroupKey.setId(userGroupDTO.getId());
			userGroupDomain = userGroupDomain.read(userGroupKey);

			/**
			 * Traverse domain and add current group users from domain to DTO
			 */
			for (UserGroupUser userGroupUser : userGroupDomain.getUserGroupUserList()) {
				UserGroupUserDTO groupDTO = new UserGroupUserDTO();
				groupDTO.setUserId(userGroupUser.getEntity().getValue());
				groupDTO.setUserGroupId(userGroupDTO.getId());
				users.add(groupDTO);
			}

			userGroupDTO.setUsers(users);
		}
		BeaSystemOut.println("#GROUP -- Final");
		return requestDTO;
	}

	private UserExtensionDataDTO createUserGroupDTOForUpdate(UserExtensionDataDTO requestDTO) throws Exception {

		BeaSystemOut.println("#GROUP group size 1 ==" + requestDTO.getUserDTO().getUserGroupDTOs().size());

		List<String> groupsToBeAdded = new ArrayList<String>();
		List<String> groupsToBeRemoved = new ArrayList<String>();
		List<UserGroupDTO> userGroupDTOList = new ArrayList<UserGroupDTO>();
		UserGroup userGroupDomain = new UserGroup();
		UserGroupKey userGroupKey = new UserGroupKey();

		/**
		 * Identify and generate groups to be added or removed
		 */
		for (UserGroupDTO userGroupDTO : requestDTO.getUserDTO().getUserGroupDTOs()) {
			boolean isUserPresentInGroup = false;
			for (UserGroupUserDTO userGroupUserDTO : userGroupDTO.getUsers()) {
				if (userGroupUserDTO.getUserId().equals(requestDTO.getUserID())) {
					isUserPresentInGroup = true;
				}
			}

			if (isUserPresentInGroup) {
				groupsToBeAdded.add(userGroupDTO.getId());
			} else {
				groupsToBeRemoved.add(userGroupDTO.getId());
			}
		}

		BeaSystemOut.println("#GROUP groups to be added=" + groupsToBeAdded);
		BeaSystemOut.println("#GROUP groups to be removed=" + groupsToBeRemoved);

		/**
		 * Remove existing groups from request
		 */
		requestDTO.getUserDTO().getUserGroupDTOs().clear();

		BeaSystemOut.println("#GROUP -- After Clear");
		BeaSystemOut.println("#GROUP group list size=" + requestDTO.getUserDTO().getUserGroupDTOs().size());

		/**
		 * Add users in the group
		 */
		for (String groupID : groupsToBeAdded) {

			/**
			 * Read each group domain
			 */
			userGroupKey.setId(groupID);
			userGroupDomain = userGroupDomain.read(userGroupKey);

			UserGroupDTO userGroupDTO = new UserGroupDTO();
			List<UserGroupUserDTO> userGroupUserDTOList = new ArrayList<UserGroupUserDTO>();

			/**
			 * Add users from domain to user group dto
			 */
			for (UserGroupUser userGroupUser : userGroupDomain.getUserGroupUserList()) {
				UserGroupUserDTO userGroupUserDTO = new UserGroupUserDTO();
				userGroupUserDTO.setUserId(userGroupUser.getEntity().getValue());
				userGroupUserDTO.setUserGroupId(groupID);
				userGroupUserDTOList.add(userGroupUserDTO);
			}

			/**
			 * If not present, add current user to group
			 */
			boolean isUserAlreadyInGroup = false;
			for (UserGroupUserDTO userGroupUserDTO : userGroupUserDTOList) {
				if (userGroupUserDTO.getUserId().equals(requestDTO.getUserID())) {
					isUserAlreadyInGroup = true;
				}
			}

			if (!isUserAlreadyInGroup) {
				BeaSystemOut.println("#GROUP user is already not present");
				UserGroupUserDTO newUserGroupUserDTO = new UserGroupUserDTO();
				newUserGroupUserDTO.setUserId(requestDTO.getUserID());
				newUserGroupUserDTO.setUserGroupId(groupID);
				userGroupUserDTOList.add(newUserGroupUserDTO);
			}

			/**
			 * Set group data
			 */
			userGroupDTO.setCreatedBy(userGroupDomain.getCreatedBy());
			userGroupDTO.setVersion(userGroupDomain.getVersion());
			userGroupDTO.setAuditSequence(userGroupDomain.getAuditSequence());
			userGroupDTO.setId(userGroupDomain.getKey().getId());
			userGroupDTO.setName(userGroupDomain.getName());
			userGroupDTO.setDescription(userGroupDomain.getDescription());
			userGroupDTO.setPartyId(new Party(userGroupDomain.getPartyId()));
			userGroupDTO.setUnary(userGroupDomain.isUnary());
			userGroupDTO.setCreationDate(userGroupDomain.getCreationDate());
			userGroupDTO.setUsers(userGroupUserDTOList);
			userGroupDTOList.add(userGroupDTO);
		}

		/**
		 * Remove users from group
		 */
		for (String groupID : groupsToBeRemoved) {
			/**
			 * Read each group domain
			 */
			userGroupKey.setId(groupID);
			userGroupDomain = userGroupDomain.read(userGroupKey);

			UserGroupDTO userGroupDTO = new UserGroupDTO();
			List<UserGroupUserDTO> userGroupUserDTOList = new ArrayList<UserGroupUserDTO>();

			/**
			 * Add users in user group dto
			 */
			for (UserGroupUser userGroupUser : userGroupDomain.getUserGroupUserList()) {
				/**
				 * Skip current user which is updating
				 */
				if (!userGroupUser.getEntity().getValue().equals(requestDTO.getUserID())) {
					UserGroupUserDTO userGroupUserDTO = new UserGroupUserDTO();
					userGroupUserDTO.setUserId(userGroupUser.getEntity().getValue());
					userGroupUserDTO.setUserGroupId(groupID);
					userGroupUserDTOList.add(userGroupUserDTO);
				}
			}

			/**
			 * Set group data
			 */
			userGroupDTO.setCreatedBy(userGroupDomain.getCreatedBy());
			userGroupDTO.setVersion(userGroupDomain.getVersion());
			userGroupDTO.setAuditSequence(userGroupDomain.getAuditSequence());
			userGroupDTO.setId(userGroupDomain.getKey().getId());
			userGroupDTO.setName(userGroupDomain.getName());
			userGroupDTO.setDescription(userGroupDomain.getDescription());
			userGroupDTO.setPartyId(new Party(userGroupDomain.getPartyId()));
			userGroupDTO.setUnary(userGroupDomain.isUnary());
			userGroupDTO.setCreationDate(userGroupDomain.getCreationDate());
			userGroupDTO.setUsers(userGroupUserDTOList);
			userGroupDTOList.add(userGroupDTO);
		}

		requestDTO.getUserDTO().setUserGroupDTOs(userGroupDTOList);

		BeaSystemOut.println("#GROUP -- Done");

		return requestDTO;
	}

	public TransactionStatus updateDefaultUser(SessionContext sessionContext,
			UserDetailsUpdationDTO userDetailsUpdationDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Entered into updateDefaultUser() : requestDTO = %s in class %s ",
							userDetailsUpdationDTO, THIS_COMPONENT_NAME));
		}
		BeaSystemOut.println("Entered updateDefaultUser service");
		TransactionStatus transactionStatus = fetchTransactionStatus();
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateDefaultUser",
				sessionContext, userDetailsUpdationDTO);

		Interaction.begin(sessionContext);
		UserExtensionDataKey key = new UserExtensionDataKey();
		key.setUserExtensionKey(sessionContext.getUserId());
//		
		UsersKey userProfileKey = new UsersKey();
		userProfileKey.setUserName(sessionContext.getUserId());
//		Subject subject = SubjectUtil.getCurrentSubject();
//		String userName = SubjectUtil.getUserName(subject);
		String partyID = sessionContext.getTransactingPartyCode();
		String userName = sessionContext.getUserId();
		boolean mobNumUpdated = true;
		boolean emailIdUpdated = true;
		try {
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
			domain = domain.read(key);
			
			com.ofss.sms.dbAuthenticator.domain.UserProfile userProfileDomain = new com.ofss.sms.dbAuthenticator.domain.UserProfile();
			
			userProfileDomain.setKey(userProfileKey);
			userProfileDomain = userProfileDomain.read(userProfileDomain);
			
			if (domain.getDefaultUser()!= null && domain.getDefaultUser().equals("Y") ) {
				userDetailsUpdationDTO.setIsDefaultUser(true);
			} else {
				userDetailsUpdationDTO.setIsDefaultUser(false);
			}
			
			if (domain.getDocumentType()!= null) {
				userDetailsUpdationDTO.setDocumentType(domain.getDocumentType());
			}
			if (domain.getDocumentCountry()!= null) {
				userDetailsUpdationDTO.setDocumentCountry(domain.getDocumentCountry());
			}
			if (domain.getDocumentID()!= null) {
				userDetailsUpdationDTO.setDocumentID(domain.getDocumentID());
			}
			if (userProfileDomain.getMobileNumber()!= null && !userProfileDomain.getMobileNumber().trim().equals("")) {
				userDetailsUpdationDTO.setMobileNo(userProfileDomain.getMobileNumber());
				mobNumUpdated=false;
			}
			if (domain.getMobileCode()!= null) {
				userDetailsUpdationDTO.setMobileNoCode(domain.getMobileCode());
			}
			if (userProfileDomain.getEmailId() !=null && !userProfileDomain.getEmailId().trim().equals("")) {
				userDetailsUpdationDTO.setEmail(userProfileDomain.getEmailId());
				emailIdUpdated=false;
			}
			
			IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
			ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
					.getAdapter("CUSTOM_CONFIG_ADAPTER");
			String cdcId = customConfigAdapter.getConfiguationDetails("DayOneConfig", "MERCHANT_ACCOUNT_ID",
					"01551468009999");
			
			if(cdcId.equals(partyID)) {
				
				BeaSystemOut.println("##Updating MerchantUserMaintenance userId: " + userDetailsUpdationDTO.getUserId() + "@" + cdcId);
				IAdapterFactory validateUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(
								com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.VALIDATE_USER_DETAILS_ADAPTER_FACTORY);
				IValidateUserDetailsAdapter validateUserDetailsAdapter = (IValidateUserDetailsAdapter) validateUserDetailsAdapterFactory
						.getAdapter(
								com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.VALIDATE_USER_DETAILS_ADAPTER);
				try {
					validateUserDetailsAdapter.updateMerchantEmailMobile(userDetailsUpdationDTO.getEmail(), userDetailsUpdationDTO.getMobileNo(),userDetailsUpdationDTO.getUserId() + "@" + cdcId,cdcId);
				}catch(Exception e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception from updateDefaultUser() for updating MerchantUserMaintenance",
							THIS_COMPONENT_NAME), e);
				}
				catch(java.lang.Exception e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception from updateDefaultUser() for updating MerchantUserMaintenance",
							THIS_COMPONENT_NAME), e);
				}
				
				BeaSystemOut.println("##Update MerchantUserMaintenance userId: " + userDetailsUpdationDTO.getUserId() + "@" + cdcId + " Finished");
				
			}
			
			userDetailsUpdationDTO.setUserId(userDetailsUpdationDTO.getUserId() + "@" + partyID);
			domain.updateDefaultUser(userDetailsUpdationDTO, userName);
			BeaSystemOut.println("After updateDefaultUser Domain call");
			if(mobNumUpdated && emailIdUpdated) {
				BeaSystemOut.println("Both email and mob no updated");
				triggerAlert(sessionContext, userDetailsUpdationDTO, "UPDATE_DEFAULT_USER_EMAIL_AND_MOBILE");
				triggerSecondarySMSAlert(sessionContext, userDetailsUpdationDTO, "UPDATE_DEFAULT_USER_EMAIL");
			}
			else if(mobNumUpdated) {
				BeaSystemOut.println("Mob no updated");
				triggerAlert(sessionContext, userDetailsUpdationDTO, "UPDATE_DEFAULT_USER_MOBILE");
			}
			else if(emailIdUpdated) {
				BeaSystemOut.println("Email updated");
				triggerAlert(sessionContext, userDetailsUpdationDTO, "UPDATE_DEFAULT_USER_EMAIL");
			}
			
			BeaSystemOut.println("** User to be logged out="+userName);
			com.ofss.digx.framework.security.handlers.ISessionManager sessionManager = new com.ofss.digx.framework.security.handlers.SessionManager();
			sessionManager.handleTransactionBasedSessionInvalidation(userName);

		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from updateDefaultUser() for requestDTO '%s' in class %s",
					THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from updateDefaultUser() for requestDTO '%s' in class %s",
							THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		BeaSystemOut.println("Exited updateDefaultUser service");
		return transactionStatus;

	}
	
	private void triggerAlert(SessionContext sessionContext, UserDetailsUpdationDTO userDetailsUpdationDTO, String eventID) throws Exception {
		BeaSystemOut.println("Entered TriggerAlert of UpdateDefaultUser Service");
		String activityID = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateDefaultUser";
		UpdateDefaultUserActivityLog activityLog = new UpdateDefaultUserActivityLog();
		activityLog.setUsername(userDetailsUpdationDTO.getUserId().split("@")[0]);
		activityLog.setEmailId(getMaskedEmailAddress( userDetailsUpdationDTO.getEmail()));
		
		BeaSystemOut.println("Username : " + activityLog.getUsername());
		BeaSystemOut.println("EmailId : " + activityLog.getEmailId());
		
		NotificationDetail[] details = new NotificationDetail[3];
		NotificationDetail messageNotificationDetail = new NotificationDetail();
		NotificationDetail userEmailNotificationDetail = new NotificationDetail();
		NotificationDetail companyEmailNotificationDetail = new NotificationDetail();

		

		messageNotificationDetail.setRecipientId(sessionContext.getTransactingPartyCode());
		messageNotificationDetail.setDestination(DestinationType.SMS);
		messageNotificationDetail.setDispatchAddress(userDetailsUpdationDTO.getMobileNo());
		messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
		
		details[0] = messageNotificationDetail;
		
		
		userEmailNotificationDetail.setRecipientId(sessionContext.getTransactingPartyCode());
		userEmailNotificationDetail.setDestination(DestinationType.EMAIL);
		userEmailNotificationDetail.setDispatchAddress(userDetailsUpdationDTO.getEmail());
		userEmailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
		details[1] = userEmailNotificationDetail;
		
		com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
				.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
		IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
		CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(sessionContext.getTransactingPartyCode());
		
		
		companyEmailNotificationDetail.setRecipientId(sessionContext.getTransactingPartyCode());
		companyEmailNotificationDetail.setDestination(DestinationType.EMAIL);
		companyEmailNotificationDetail.setDispatchAddress(partyDetails.getOfficeEmailId());
		companyEmailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
		details[2] = companyEmailNotificationDetail;
		
		activityLog.setNotificationDetails(details);
		BeaSystemOut.println("ActivityLog: " + activityLog.toString());
		
		try {
			registerActivityAndGenerateEvent(sessionContext, activityID, eventID, new Date(), activityLog);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			BeaSystemOut.printErr(e);
		}
		BeaSystemOut.println("Exiting TriggerAlert of UpdateDefaultUser Service");
		
	}
	
	
	private void triggerSecondarySMSAlert(SessionContext sessionContext, UserDetailsUpdationDTO userDetailsUpdationDTO, String eventID) throws Exception {
		BeaSystemOut.println("Entered triggerSecondarySMSAlert of UpdateDefaultUser Service");
		String activityID = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateDefaultUser";
		UpdateDefaultUserActivityLog activityLog = new UpdateDefaultUserActivityLog();
		activityLog.setUsername(userDetailsUpdationDTO.getUserId().split("@")[0]);
		activityLog.setEmailId(getMaskedEmailAddress( userDetailsUpdationDTO.getEmail()));
		BeaSystemOut.println("Username in triggerSecondarySMSAlert: " + activityLog.getUsername());
		BeaSystemOut.println("EmailId in triggerSecondarySMSAlert: " + activityLog.getEmailId());
		NotificationDetail[] details = new NotificationDetail[1];
		NotificationDetail messageNotificationDetail = new NotificationDetail();

		messageNotificationDetail.setRecipientId(sessionContext.getTransactingPartyCode());
		messageNotificationDetail.setDestination(DestinationType.SMS);
		messageNotificationDetail.setDispatchAddress(userDetailsUpdationDTO.getMobileNo());
		messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
		
		details[0] = messageNotificationDetail;
		
		activityLog.setNotificationDetails(details);
		BeaSystemOut.println("ActivityLog: " + activityLog.toString());
		
		try {
			registerActivityAndGenerateEvent(sessionContext, activityID, eventID, new Date(), activityLog);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			BeaSystemOut.printErr(e);
		}
		BeaSystemOut.println("Exiting TriggerAlert of UpdateDefaultUser Service");
		
	}
	
	public static String getMaskedEmailAddress(String emailAddress)
	{
		StringBuffer maskedEmailAddress = new StringBuffer();
		if(emailAddress != null && !emailAddress.trim().equals(""))
		{		
			int addressLength = emailAddress.split("@")[1].length();
			maskedEmailAddress.append(emailAddress.split("@")[0]);
			for(int i =0; i < addressLength+1; i++) {
				maskedEmailAddress.append("*");
			}
		}
		return maskedEmailAddress.toString();
	}
	
	private void registerActivityAndGenerateEvent(SessionContext sessionContext, String activityId, String eventId,
			Date date, UpdateDefaultUserActivityLog activityLog) throws Exception {
		
		BeaSystemOut.println("Entered registerActivityAndGenerateEvent of UpdateDefaultUser Service");
		IModuleToAlertAdapter adapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
		adapter.registerActivityAndGenerateEvent(activityId, eventId, date, activityLog);
		BeaSystemOut.println("###########Event called:- " + eventId);
		BeaSystemOut.println("Exiting registerActivityAndGenerateEvent of UpdateDefaultUser Service");
	}

	@Override
	public TransactionStatus updateMigrationStatus(SessionContext sessionContext,
			MigrationStatusRequestDto migrationStatusRequestDto) throws Exception {
		BeaSystemOut.println("UserExtensionData.updateMigrationStatus() Service starts here");

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Entering updateMigrationStatus of %s, sessionContext=%s, migrationStatusRequestDto=%s",
							THIS_COMPONENT_NAME, sessionContext, migrationStatusRequestDto));
		}
		super.canonicalizeInput(migrationStatusRequestDto);
		BeaSystemOut.println("UserExtensionData.updateMigrationStatus() before checkAccessPolicy");
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateMigrationStatus",
				sessionContext, migrationStatusRequestDto);
		BeaSystemOut.println("UserExtensionData.updateMigrationStatus() after checkAccessPolicy");
		Interaction.begin(sessionContext);
		MigrationStatusResponseDto migrationStatusResponseDto = new MigrationStatusResponseDto();
		TransactionStatus transactionStatus = fetchTransactionStatus();
		try {

			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
			BeaSystemOut.println("UserExtensionData.updateMigrationStatus()  before calling domain");
			//migrationStatusRequestDto.setUsrId(sessionContext.getUserId());
			migrationStatusResponseDto = domain.updateHostMigrationStatus(sessionContext, migrationStatusRequestDto);
			if (migrationStatusResponseDto != null && migrationStatusResponseDto.getResult() != null
					&& !migrationStatusResponseDto.getResult().isEmpty()
					&& migrationStatusResponseDto.getResult().equalsIgnoreCase("TRUE")) {
				BeaSystemOut.println("UserExtensionData.updateMigrationStatus() if result is success");
				domain.updateMigrationStatus(sessionContext, migrationStatusRequestDto);
			}
			BeaSystemOut.println("UserExtensionData.updateMigrationStatus()  After calling domain");
			BeaSystemOut.println(
					"UserExtensionData.updateMigrationStatus() user id from service::" + sessionContext.getUserId());
			// migrationStatusResponseDto.setStatus(buildStatus(transactionStatus));
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RunTimeException from   migrationStatusResponseDto '%s' in class %s",
							migrationStatusResponseDto, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		super.encodeOutput(migrationStatusResponseDto);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting list, migrationStatusResponseDto=%s", migrationStatusResponseDto));
		}
		BeaSystemOut.println("UserExtensionData.updateMigrationStatus()  Service ends here");
		return transactionStatus;
	}

	/**
	 * @param sessionContext
	 * @return
	 * @throws Exception
	 */
	public AuditLogMigResponse downloadCcbAuditLog(SessionContext sessionContext, String auditType) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entering fetch(): sessionContext= %s in class. %s",
					sessionContext, THIS_COMPONENT_NAME));
		}
		
		String auditMethodName = auditType != null && auditType.equalsIgnoreCase("TRANSACTION") ? ".downloadCcbAuditLog" : ".downloadCcbSystemAuditLog";
		
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData" + auditMethodName,
				sessionContext);
		Interaction.begin(sessionContext);
		TransactionStatus status = fetchTransactionStatus();
		AuditLogMigResponse response = null;
		try {

			String reportFormat = "zip";

			String partyId = sessionContext.getTransactingPartyCode();
			BeaSystemOut.println("Download CCB Audit Log : PartyCode :" + partyId);

			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();

			AuditLogMigRequestDTO requestDTO = domain.downloadCcbAuditLog(partyId, auditType);

			if (requestDTO != null) {
				BeaSystemOut.println("Download CCB Audit Log : Got Response for partyId :" + partyId);
			response = new AuditLogMigResponse();
			response.setReportContents(requestDTO.getFileContent());
				response.setFileName(requestDTO.getFileName());
			response.setFileFormat(reportFormat);
			} else {
				if(auditType!=null && auditType.equalsIgnoreCase("TRANSACTION")) {
					BeaSystemOut.println("Download CCB Audit Log : DID NOT GOT Response for partyId :" + partyId);
					Exception exception = new Exception();
					ValidationError[] validationErrors = new ValidationError[1];
					validationErrors[0] = new ValidationError("MigratedAuditLogs", "download", null, "DIGX_CZ_MAL_01", "");
					exception.setValidationErrors(validationErrors);
					throw exception;
				}
				else if (auditType!=null && auditType.equalsIgnoreCase("SYSTEM")) {
					BeaSystemOut.println("Download CCB Audit Log : DID NOT GOT Response for partyId :" + partyId);
					Exception exception = new Exception();
					ValidationError[] validationErrors = new ValidationError[1];
					validationErrors[0] = new ValidationError("MigratedAuditLogs", "download", null, "DIGX_CZ_MAL_02", "");
					exception.setValidationErrors(validationErrors);
					throw exception;
				}
				
			}

			response.setStatus(buildStatus(status));
		} catch (RuntimeException rte) {
			fillTransactionStatus(status, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RunTimeException from   AuditLogMigResponse '%s' in class %s", response,
							THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		super.encodeOutput(response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Exiting downloadReport(), SessionContext: %s, TransactionStatus: %s in class '%s' ",
							sessionContext, status, THIS_COMPONENT_NAME));
		}
		return response;
	}
	
	
	
	
	@Override
	@Entitlement(name = "Create Merchant User", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Create Merchant User", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Create Merchant User", action = ActionType.PERFORM, requiredResources = {})
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_CMUS", parent = "MT", name = "Create Merchant User", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public MerchantUserExtensionDataResponseDTO createMerchantUser(SessionContext sessionContext, UserExtensionDataDTO requestDTO, MerchantUserMaintenanceDTO merchantUserMaintenanceDTO)
			throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into create() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.createMerchantUser", sessionContext,
				requestDTO);
		MerchantUserExtensionDataResponseDTO response = new MerchantUserExtensionDataResponseDTO();
		 
		response.setStatus(fetchStatus());
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.app.sms.service.user.User user = new com.ofss.digx.app.sms.service.user.User();
		UserAssembler userAssembler = new UserAssembler();
		try {
			
			requestDTO.validate(sessionContext);
			UserExtensionDataAssembler assembler = new UserExtensionDataAssembler();
			domain = assembler.toDomainObject(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = null;
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintenanceDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenanceKey userIdMaintenanceDomainKey = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenanceKey();
			UserIdMaintenanceAssembler userIdMaintenanceAssembler = new UserIdMaintenanceAssembler();
			UserResponseDTO userResponseDTO = new UserResponseDTO();
			UserIdMaintenanceRequestDTO userIdMaintenanceRequestDTO = null;
			com.ofss.digx.domain.sms.entity.user.User userForPolicy = userAssembler
					.toDomainObjectCreate(requestDTO.getUserDTO());

			if (requestDTO.getDocumentID() != null) {
				requestDTO.setDocumentID(requestDTO.getDocumentID().trim());
			}

			UserExtensionDataBusinessPolicyDTO policyDTO = new UserExtensionDataBusinessPolicyDTO(requestDTO);
			abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
					"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.createMerchantUser", policyDTO);
			abstractBusinessPolicy.validate(
					com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicy.MT_USEREXTENSIONDATA_BUSINESS_POLICY_VIOLATION);

			/*
			 * Invoke base service business policy
			 */
			UserBusinessPolicyDTO businessPolicyDTO = new UserBusinessPolicyDTO();
			businessPolicyDTO.setUser(userForPolicy);
			AbstractBusinessPolicy abstractUsersBusinessPolicy = bpfact
					.getBusinesPolicyInstance("com.ofss.digx.app.sms.service.user.User.createUser", businessPolicyDTO);
			abstractUsersBusinessPolicy.validate(UserManagementErrorConstants.UM_VALIDATION_FAILED_MESSAGE);

			// To invoke approval flow manually
			IUserExtensionDataRepositoryAdapter userExtensionDataRepositoryAdapter = (IUserExtensionDataRepositoryAdapter) RepositoryAdapterFactory
					.getInstance()
					.getRepositoryAdapter(IUserExtensionDataRepositoryAdapter.USEREXTENSIONDATA_REPOSITORY_ADAPTER);

			BeaSystemOut.println("In Extension Create");

			if (requestDTO.getLoginID() != null && requestDTO.getLoginPinReferenceNo() != null) {

				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();

				List<UserIdMaintenance> list = new ArrayList<UserIdMaintenance>();
				UserIdMaintenanceKey key = new UserIdMaintenanceKey();
				UserIdMaintenance loginID = new UserIdMaintenance();
				loginID.setPinRefNo(requestDTO.getLoginPinReferenceNo());
				loginID.setSubOption("02");
				key.setPartyId(requestDTO.getCdcNo());
				key.setId(requestDTO.getLoginID());
				loginID.setIdType("02");
				Party party = new Party();
				party.setValue(requestDTO.getCdcNo());
				loginID.setIdKey(key);
				loginID.setParty(party);
				list.add(loginID);
				IUserIdMaintenanceRepositoryAdapter userIdMaintenancaRepositoryAdapter = (IUserIdMaintenanceRepositoryAdapter) RepositoryAdapterFactory
						.getInstance().getRepositoryAdapter(
								IUserIdMaintenanceRepositoryAdapter.USERIDMAINTENANCE_REMOTE_REPOSITORY_ADAPTER);
				userIdMaintenancaRepositoryAdapter.addUserId(list);
				BeaSystemOut.println("Sent to remote addUserId");
				// TODO: ALU02 call
				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();
				userIdMaintenanceRequestDTO.setIdToMap(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setPinRefToMap(requestDTO.getLoginPinReferenceNo());
				UserIdMaintenanceKeyDTO userIdMaintenanceKey = new UserIdMaintenanceKeyDTO();
				userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKey.setId(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setIdKey(userIdMaintenanceKey);
				userIdMaintenanceRequestDTO.setHoldReason("NIL");
				userIdMaintenanceRequestDTO.setHoldStatus("Unhold");
				userIdMaintenanceRequestDTO.setPinStatus("Inactive");
				userIdMaintenanceRequestDTO.setIdType("02");
				BeaSystemOut.println("Got the suer name" + requestDTO.getUserID());
				BeaSystemOut.println("Setting username-->" + requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setUserName(requestDTO.getUserID());
				userIdMaintenanceDomainKey.setId(requestDTO.getLoginID());
				userIdMaintenanceDomainKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceDomain = userIdMaintenanceDomain.read(userIdMaintenanceDomainKey);
				if (userIdMaintenanceDomain == null) {
					userIdMaintenanceDomain = userIdMaintenanceAssembler.toDomainObject(userIdMaintenanceRequestDTO);
					BeaSystemOut.println("In Domain .create and domain key is" + requestDTO.getLoginID() + " and"
							+ requestDTO.getCdcNo());
					BeaSystemOut.println("Username in domain" + userIdMaintenanceDomain.getUserName());
					userIdMaintenanceDomain.create(userIdMaintenanceDomain);
				} else {
					userIdMaintenanceDomain.setPinRefNo(requestDTO.getLoginPinReferenceNo());
					userIdMaintenanceDomain.setUserName(requestDTO.getUserID());
					BeaSystemOut.println("Inside Else ==" + userIdMaintenanceDomain.getIdKey().getId() + "---"
							+ userIdMaintenanceDomain.getIdKey().getPartyId());
					userIdMaintenanceDomain.setLoginPinMapDate(new com.ofss.fc.datatype.Date());
					userIdMaintenanceDomain.update(userIdMaintenanceDomain);
				}

			}



			if (requestDTO.getLoginID() != null) {
				com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();

				UserIdMaintenanceKey userIdMaintenanceKey = new UserIdMaintenanceKey();
				userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKey.setId(requestDTO.getLoginID());
				userIdMaintDomain = userIdMaintDomain.read(userIdMaintenanceKey);
				BeaSystemOut.println("Setting username in corp-->" + requestDTO.getUserID());
				userIdMaintDomain.setUserName(requestDTO.getUserID());
				if (userIdMaintDomain != null) {

					BeaSystemOut.println("Not null check for userIdMaintDomain");
					userIdMaintDomain.update(userIdMaintDomain);
				}
			}

			if (domain != null) {
				if (domain.getLoginPinReferenceNo() != null) {
					domain.setLoginPinType("N");// during create login pin is numeric
				}
				
			
				userResponseDTO = user.createUser(sessionContext, requestDTO.getUserDTO());

				if (userResponseDTO.getStatus().getLastKnownError() != null) {
					BeaSystemOut.println("inside ##getLastKnownError");
					throw (Exception) transactionStatus.fetchLastKnownError();
				}

				if (userResponseDTO.getStatus().getResult() != ResultType.FAILED) {
					BeaSystemOut.println("inside ##getStatus FAILED");
					domain.setSecurityQuestionsBypass("N");
					domain.create(domain);

					// calling alert method
					userCreateWelcomeAlert(sessionContext, requestDTO);
				}
			}
			
			//calling merchant user creation service 
			//MerchantUserMaintenanceResponse merchantUserMaintenanceResponseDTO = new MerchantUserMaintenanceResponse();
			//MerchantUserMaintenance merchantUserMaintenance = new MerchantUserMaintenance();
			//merchantUserMaintenanceResponseDTO = merchantUserMaintenance.create(sessionContext, 
				//	merchantUserMaintenanceDTO);

			requestDTO = assembler.fromDomainObject(domain);
			response.setUserExtensionDataDTO(requestDTO);
			response.setUserResponseDTO(userResponseDTO);
			//response.setMerchantUserMaintenanceResponseDTO(merchantUserMaintenanceResponseDTO);
			
		//}
		//catch (FatalException e) {
			//fillTransactionStatus(transactionStatus, e);
			//logger.log(Level.SEVERE, formatter.formatMessage("Exception from create() for requestDTO '%s' in class %s",
			//		requestDTO, THIS_COMPONENT_NAME), e);
		}catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from create() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from create() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Exiting from create() : response = %s", response));
		}
		return response;
	}
	
	
	
	
	@Override
	@Entitlement(name = "Update MerchantUser", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Update MerchantUser", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Update MerchantUser", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read" })
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_UMUS", parent = "MT", name = "Update MerchantUser", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public TransactionStatus updateMerchantUser(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into update() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateMerchantUser", sessionContext,
				requestDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		com.ofss.digx.app.sms.service.user.User user = new com.ofss.digx.app.sms.service.user.User();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance userIdMaintenanceDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserIdMaintenance();
		UserIdMaintenanceAssembler userIdMaintenanceAssembler = new UserIdMaintenanceAssembler();
		UserResponseDTO userResponseDTO = new UserResponseDTO();
		UserIdMaintenanceRequestDTO userIdMaintenanceRequestDTO = null;
		com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
		UserKey userKey = new UserKey();
		UserAssembler userAssembler = null;

		try {
			
			requestDTO.validate(sessionContext);
			UserExtensionDataAssembler assembler = new UserExtensionDataAssembler();
			// domain = assembler.toDomainObject(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = null;
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			UserExtensionDataBusinessPolicyDTO policyDTO = new UserExtensionDataBusinessPolicyDTO(requestDTO);
			
			abstractBusinessPolicy = bpfact.getBusinesPolicyInstance(
					"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.updateMerchantUser", policyDTO);
			abstractBusinessPolicy.validate(
					com.ofss.digx.cz.bea.domain.sms.entity.user.policy.UserExtensionDataBusinessPolicy.MT_USEREXTENSIONDATA_BUSINESS_POLICY_VIOLATION);

			/*
			 * Invoke base service business policy
			 */
			userAssembler = new UserAssembler();
			com.ofss.digx.domain.sms.entity.user.User userForPolicy = userAssembler
					.toDomainObject(requestDTO.getUserDTO());
			UserBusinessPolicyDTO businessPolicyDTO = new UserBusinessPolicyDTO();
			businessPolicyDTO.setUser(userForPolicy);
			AbstractBusinessPolicy abstractUserServiceBusinessPolicy = bpfact
					.getBusinesPolicyInstance("com.ofss.digx.app.sms.service.user.User.update", businessPolicyDTO);
			abstractUserServiceBusinessPolicy.validate(UserManagementErrorConstants.UM_VALIDATION_FAILED_MESSAGE);
			BeaSystemOut.println("Inside Extension update");
			if (requestDTO.getDocumentID() != null) {
				requestDTO.setDocumentID(requestDTO.getDocumentID().trim());
			}

			// All of the below details will be coming from UI
			String accountNoMigrationStatus = requestDTO.getCdcNoEncrypted();
			String userIdMigrationStatus = requestDTO.getUserIDEncrypted();
//			String keyIndicatorMigrationStatus = requestDTO.getKeyIndicator();
			
			// To invoke approval flow manually
			IUserExtensionDataRepositoryAdapter userExtensionDataRepositoryAdapter = (IUserExtensionDataRepositoryAdapter) RepositoryAdapterFactory
					.getInstance()
					.getRepositoryAdapter(IUserExtensionDataRepositoryAdapter.USEREXTENSIONDATA_REPOSITORY_ADAPTER);
			
			IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
			
			IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
					.getAdapter(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
			
			SignerUserDetailsResponseDTO signerUserDetailsResponseDTO = null;
			LoginUserDetailsResponseDTO loginUserDetailsResponseDTO = null;
			LoginUserDetailsDTO loginUserDetailsDTO = new LoginUserDetailsDTO();
			SignerUserDetailsDTO signerUserDetailsDTO = new SignerUserDetailsDTO();
			
			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserExtensionKey());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			
			String loginPinReferenceNoFromDomain = null;
			String loginHoldReasonFromDomain = null;
			String loginPinStatusFromDomain = null;
			String loginHoldStatusFromDomain = null;

			if (domain.getLoginPinReferenceNo() != null) {
				loginPinReferenceNoFromDomain = domain.getLoginPinReferenceNo();
			}

			if (domain.getLoginHoldReason() != null) {
				loginHoldReasonFromDomain = domain.getLoginHoldReason();
			}
			if (domain.getLoginPinstatus() != null) {
				loginPinStatusFromDomain = domain.getLoginPinstatus();
			}
			if (domain.getLoginHoldStatus() != null) {
				loginHoldStatusFromDomain = domain.getLoginHoldStatus();
			}
			
			boolean isSignerHostCallDone, isLoginHostCallDone;
			
			


			boolean userMigrationOnboardingEditFlag = false;
			boolean defaultUserUpdateMigrationStatusFlag = false;

			BeaSystemOut.println("\n MigrationUserOnboardingEdit UserExtensionData ::  " + "\n requestDTO.getLoginID() : "
					+ requestDTO.getLoginID() +  domain.getLoginHoldReason()+ 
					 domain.getLoginHoldReason() + "\n requestDTO.getLoginHoldReason() : "
					+ requestDTO.getLoginHoldReason() + "\n domain.getLoginHoldStatus() : "
					+ domain.getLoginHoldStatus() + "\n requestDTO.getLoginHoldStatus() :"
					+ requestDTO.getLoginHoldStatus() + "\n domain.getLoginPinReferenceNo() :"
					+ domain.getLoginPinReferenceNo() + "\n requestDTO.getLoginPinReferenceNo() : "
					+ requestDTO.getLoginPinReferenceNo() + "\n domain.getLoginPinstatus() : "
					+ domain.getLoginPinstatus() + "\n requestDTO.getLoginPinstatus() : "
					+ requestDTO.getLoginPinstatus());

			if (requestDTO.getLoginID() != null) {
				loginUserDetailsDTO.setLoginId(requestDTO.getLoginID());
				loginUserDetailsDTO.setPinReferenceNo(requestDTO.getLoginPinReferenceNo());
				loginUserDetailsDTO.setCdcId(requestDTO.getCdcNo());
				loginUserDetailsDTO.setPinStatus(requestDTO.getLoginPinstatus());
				loginUserDetailsDTO.setHoldCode(requestDTO.getLoginHoldStatus());
				loginUserDetailsDTO.setHoldReason(requestDTO.getLoginHoldReason());
				if (domain.getLoginHoldReason() != null
						&& domain.getLoginHoldReason().equals(requestDTO.getLoginHoldReason())
						&& domain.getLoginHoldStatus() != null
						&& domain.getLoginHoldStatus().equals(requestDTO.getLoginHoldStatus())
						&& domain.getLoginPinReferenceNo() != null
						&& domain.getLoginPinReferenceNo().equals(requestDTO.getLoginPinReferenceNo())
						&& domain.getLoginPinstatus() != null
						&& domain.getLoginPinstatus().equals(requestDTO.getLoginPinstatus())) {
					isLoginHostCallDone = true; // No need to call login pin change request

				} else {
					if (domain.getLoginPinReferenceNo().equals(loginUserDetailsDTO.getPinReferenceNo())) {
						loginUserDetailsDTO.setPinChangeFlag("N");

					} else {
						loginUserDetailsDTO.setPinChangeFlag("Y");
						domain.setLoginPinType("N"); // Change to Numeric pin again
						Users users = new Users();
						Users usersRead = null;
						UsersKey usersKey = new UsersKey();
						usersKey.setUserName(requestDTO.getUserID());
						users.setKey(usersKey);
						usersRead = users.read(users);
						usersRead.setForceChangePassword(true);
					}
//					----------------------- MigrationUserOnboardingEdit changes - STARTS --------------------- 

					BeaSystemOut.println("\n MigrationUserOnboardingEdit UserExtensionData ::  "
							+ "\n requestDTO.getLoginPinReferenceNo(): " + requestDTO.getLoginPinReferenceNo()
							+ "\n loginPinReferenceNoFromDomain : " + loginPinReferenceNoFromDomain
							+ "\n domain.getMigtationStatus() : " + domain.getMigtationStatus()
							+ "\n domain.getDefaultUser() :" + domain.getDefaultUser()
							+ "\n requestDTO.getLoginPinstatus() :" + requestDTO.getLoginPinstatus() + "\n accountNo: "
							+ accountNoMigrationStatus + "\n userId: " + userIdMigrationStatus);

					if (isMigrationUserOnboardingEditEnabled() && domain.getMigtationStatus() != null
							&& domain.getMigtationStatus().equals("N")
							&& requestDTO.getLoginPinReferenceNo() != null
							&& !(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()))
							&& requestDTO.getLoginPinstatus() != null
							&& requestDTO.getLoginPinstatus().equals(LOGIN_PIN_STATUS_BEING_RESET)) {

						isLoginHostCallDone = false;
						
						List<UserIdMaintenance> list = buildUserIdMaintenanceList(requestDTO);

						IUserIdMaintenanceRepositoryAdapter userIdMaintenancaRepositoryAdapter = (IUserIdMaintenanceRepositoryAdapter) RepositoryAdapterFactory
								.getInstance().getRepositoryAdapter(
										IUserIdMaintenanceRepositoryAdapter.USERIDMAINTENANCE_REMOTE_REPOSITORY_ADAPTER);
						userIdMaintenancaRepositoryAdapter.addUserId(list);
						BeaSystemOut.println("MigrationUserOnboardingEdit :: Sent to remote addUserId - ALU02 call");
						
						userMigrationOnboardingEditFlag = true;

						isLoginHostCallDone = true;

					} else {
						// Call CLU02
						loginUserDetailsResponseDTO = hostuserDetailsAdapter.updateLoginId(loginUserDetailsDTO);

						// TODO: isDefaultUserUpdateMigrationStatus

						defaultUserUpdateMigrationStatusFlag = isDefaultUserUpdateMigrationStatus(domain, requestDTO,
								loginPinReferenceNoFromDomain);

						isLoginHostCallDone = loginUserDetailsResponseDTO.isVerified();
					}
//					----------------------- MigrationUserOnboardingEdit changes - ENDS -----------------------

				}

			} else {
				isLoginHostCallDone = true;
			}

			if (domain != null && isLoginHostCallDone) {

				UserAlertRequestDTO resultDto = new UserAlertRequestDTO();
				userKey.setUserId(requestDTO.getUserID());
				userDomain = userDomain.read(userKey);
				String oldMobNo = userDomain.getMobileNumber();
				BeaSystemOut.println("##########Old Mob No:- " + oldMobNo);
				BeaSystemOut.println("#############Old Email:- " + userDomain.getEmailId());
				resultDto = checkAlerts(requestDTO, userDomain);

				// UserExtensionDataKey key = new UserExtensionDataKey();
				// key.setUserExtensionKey(requestDTO.getUserExtensionKey());
				// domain.setUserExtensionDataKey(key);
				// domain = domain.read(key);
				domain = assembler.toUpdateDomainObject(requestDTO, domain);
				String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
				BeaSystemOut.println("local repo call for update" + taskId);
				SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
				IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance()
						.getAdapter(IFMOHelperCallAdapter.class, "callFMOHelper", DeterminantType.Enterprise);
				if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
						&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
					adapter.callFMOHelper(session, taskId, requestDTO, true);
					BeaSystemOut.println("local repo call after fmo");
				}
				
				domain.update(domain);


				transactionStatus = user.update(sessionContext, requestDTO.getUserDTO());
				BeaSystemOut.println("Transaction Status: "+ transactionStatus);
				BeaSystemOut.println("Transaction Status Replytext + code  : "+ transactionStatus.getReplyText() + "+" + transactionStatus.getReplyCode());
				BeaSystemOut.println("Transaction Status ErrorCOde + code  : "+ transactionStatus.getErrorCode() + "+" + transactionStatus.getValidationErrors());
				if (transactionStatus!=null && transactionStatus.getErrorCode()==null) {
					// User Profile update alert
					BeaSystemOut.println("##############Executing alertUserProfileUpdate method");
					alertUserProfileUpdate(sessionContext, resultDto, requestDTO, userDomain, oldMobNo);
					BeaSystemOut.println("##############Executed alertUserProfileUpdate method");
				}
			}

			UserIdMaintenanceKey userIdMaintenanceKey = new UserIdMaintenanceKey();
			userIdMaintenanceKey.setId(requestDTO.getLoginID());
			userIdMaintenanceKey.setPartyId(requestDTO.getCdcNo());
			userIdMaintenanceDomain = userIdMaintenanceDomain.read(userIdMaintenanceKey);
			BeaSystemOut.println("In Update of user extension");

			if (userIdMaintenanceDomain != null) {
				BeaSystemOut.println("details --> requestDTO.getLoginPinstatus()" + requestDTO.getLoginPinstatus()
						+ " requestDTO.getLoginHoldStatus()" + requestDTO.getLoginHoldStatus()
						+ " requestDTO.getLoginHoldReason()" + requestDTO.getLoginHoldReason());
				UserIdMaintenanceKeyDTO userIdMaintenanceKeyDTOKey = new UserIdMaintenanceKeyDTO();
				userIdMaintenanceRequestDTO = new UserIdMaintenanceRequestDTO();
				userIdMaintenanceKeyDTOKey.setPartyId(requestDTO.getCdcNo());
				userIdMaintenanceKeyDTOKey.setId(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setIdKey(userIdMaintenanceKeyDTOKey);

				userIdMaintenanceRequestDTO.setIdToMap(requestDTO.getLoginID());
				userIdMaintenanceRequestDTO.setPinRefToMap(requestDTO.getLoginPinReferenceNo());
				userIdMaintenanceRequestDTO.setPinStatus(requestDTO.getLoginPinstatus());
				userIdMaintenanceRequestDTO.setHoldStatus(requestDTO.getLoginHoldStatus());
				userIdMaintenanceRequestDTO.setHoldReason(requestDTO.getLoginHoldReason());
				BeaSystemOut.println("Setting Username update -->" + requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setUserName(requestDTO.getUserID());
				userIdMaintenanceRequestDTO.setIdType("02");
				userIdMaintenanceDomain = userIdMaintenanceAssembler.toDomainObjectUpdate(userIdMaintenanceDomain,
						userIdMaintenanceRequestDTO);
				if (userIdMaintenanceDomain != null) {
					userIdMaintenanceDomain.update(userIdMaintenanceDomain);
				}
			}

			requestDTO = assembler.fromDomainObject(domain);
			
//			----------------------- MigratedUserResetPassword changes - STARTS --------------------- 
			if (isMigratedUserResetPasswordEnabled()) {

				MigrationStatusRequestDto migrationStatusRequestDto = new MigrationStatusRequestDto();
				PublicKeyDTO4CDC publicKeyDTO4CDC = new PublicKeyDTO4CDC();

				publicKeyDTO4CDC = hostuserDetailsAdapter.getPublickey4CDC(sessionContext, publicKeyDTO4CDC);

				BeaSystemOut.println("MigratedUserResetPassword getPublickey4CDC from service :: " + "\n PublicKey : "
						+ publicKeyDTO4CDC.getPublicKey() + "\n Key_indicator : "
						+ publicKeyDTO4CDC.getKey_indicator());

				String encryptedAccountNo = accountNoMigrationStatus;
				String encryptedUserId = userIdMigrationStatus;
				String keyIndicatorFromPublicKeyDTO4CDC = String.valueOf(publicKeyDTO4CDC.getKey_indicator());

				if (publicKeyDTO4CDC.getPublicKey() != null) {

					try {        
						Boolean ccb_decommission_flag = hostuserDetailsAdapter.getCCBByPassFlag();
						encryptedUserId = RSAUtils.encrypt(userIdMigrationStatus, publicKeyDTO4CDC.getPublicKey(),ccb_decommission_flag);
						encryptedAccountNo = RSAUtils.encrypt(accountNoMigrationStatus, publicKeyDTO4CDC.getPublicKey(),ccb_decommission_flag);
					} catch (java.lang.Exception e) {
						BeaSystemOut.printErr(e);
						
					}
					
				}

				BeaSystemOut.println("\n MigratedUserResetPassword UserExtensionData Req Data from UI ::  "
						+ "\n encryptedAccountNo: " + encryptedAccountNo + "\n encryptedUserId: " + encryptedUserId
						+ "\n decryptedUserId: " + requestDTO.getUserExtensionKey()
						+ "\n requestDTO.getLoginPinReferenceNo(): " + requestDTO.getLoginPinReferenceNo()
						+ "\n domain.getLoginPinReferenceNo() : " + domain.getLoginPinReferenceNo()
						+ "\n loginPinReferenceNoFromDomain : " + loginPinReferenceNoFromDomain
						+ "\n loginHoldReasonFromDomain : " + loginHoldReasonFromDomain
						+ "\n domain.getMigtationStatus() : " + domain.getMigtationStatus()
						+ "\n loginPinStatusFromDomain : " + loginPinStatusFromDomain
						+ "\n loginHoldStatusFromDomain) : " + loginHoldStatusFromDomain
						+ "\n keyIndicatorFromPublicKeyDTO4CDC : " + keyIndicatorFromPublicKeyDTO4CDC);

				if (requestDTO.getLoginPinReferenceNo() != null) {
					if (!(loginPinReferenceNoFromDomain.equals(requestDTO.getLoginPinReferenceNo()))
							&& domain.getMigtationStatus() != null && domain.getMigtationStatus().equalsIgnoreCase("N")
							&& loginHoldReasonFromDomain != null
							&& loginHoldReasonFromDomain.equals(LOGIN_HOLD_REASON_REQ_BY_CUSTOMER)
							&& loginPinStatusFromDomain != null
							&& loginPinStatusFromDomain.equals(LOGIN_PIN_STATUS_INACTIVE)
							&& loginHoldStatusFromDomain != null
							&& loginHoldStatusFromDomain.equals(LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED)) {

						migrationStatusRequestDto.setAcctNo(encryptedAccountNo);
						migrationStatusRequestDto.setUsrId(encryptedUserId);
						migrationStatusRequestDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusRequestDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n MigratedUserResetPassword UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusRequestDto);
						BeaSystemOut.println(
								"\n MigratedUserResetPassword UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");

					} else if (userMigrationOnboardingEditFlag) {

						MigrationStatusRequestDto migrationStatusReqDto = new MigrationStatusRequestDto();

						migrationStatusReqDto.setAcctNo(encryptedAccountNo);
						migrationStatusReqDto.setUsrId(encryptedUserId);
						migrationStatusReqDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusReqDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n MigrationUserOnboardingEdit UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusReqDto);
						BeaSystemOut.println(
								"\n MigrationUserOnboardingEdit UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");
						
					} else if (defaultUserUpdateMigrationStatusFlag) {
						// TODO: isDefaultUserUpdateMigrationStatus
						MigrationStatusRequestDto migrationStatusReqDto = new MigrationStatusRequestDto();

						migrationStatusReqDto.setAcctNo(encryptedAccountNo);
						migrationStatusReqDto.setUsrId(encryptedUserId);
						migrationStatusReqDto.setKeyIndicator(keyIndicatorFromPublicKeyDTO4CDC);
						migrationStatusReqDto.setDecryptedUserId(requestDTO.getUserExtensionKey());

						BeaSystemOut.println(
								"\n defaultUserUpdateMigrationStatusFlag UserExtensionData update() ::  Calling updateMigrationStatus : STARTS");
						updateMigrationStatus(sessionContext, migrationStatusReqDto);
						BeaSystemOut.println(
								"\n defaultUserUpdateMigrationStatusFlag UserExtensionData update() ::  Calling updateMigrationStatus : ENDS");
					}
				}
			}
//			----------------------- MigratedUserResetPassword changes - ENDS -----------------------
	
			

						
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from update() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from update() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
		}finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from update() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	@Override
	public UserExtensionDataResponseDTO createMerchantUser(SessionContext sessionContext,
			UserExtensionDataDTO requestDTO) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
	
	private void activateHthApiPasswordCode(SessionContext sessionContext, UserExtensionDataDTO dto)
			throws Exception {
		if (dto == null || !"HTH".equals(com.ofss.digx.cz.bea.app.hosttohost.adapter
				.IHthUserProfileAdapter.normalizeUserChannelType(dto.getUserChannelType()))
				|| dto.getHthApiPasswordCodeId() == null || dto.getHthApiPasswordCodeId().trim().isEmpty()) {
			return;
		}
		try {
			// sms is built before hosttohost; keep the module dependency runtime-only.
			Class<?> serviceClass = Class.forName(
					"com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword");
			Object service = serviceClass.getDeclaredConstructor().newInstance();
			serviceClass.getMethod("activateOnUserApproval", String.class, String.class,
					String.class, String.class).invoke(service, dto.getHthApiPasswordCodeId().trim(),
					sessionContext.getUserId(), dto.getCdcNo(), dto.getUserID());
		} catch (java.lang.reflect.InvocationTargetException e) {
			if (e.getCause() instanceof Exception) { throw (Exception) e.getCause(); }
			throw new Exception(e);
		} catch (java.lang.Exception e) {
			// A submitted code must not silently remain PENDING after user approval.
			throw new Exception(e);
		}
	}

	private String generateHash(String answer) {
		byte[] encodedhash = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			encodedhash = digest.digest(answer.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			logger.log(Level.SEVERE, "Failed to generate hash", e);
		}

		return bytesToHex(encodedhash);
	}

	private String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder(2 * hash.length);
		for (int i = 0; i < hash.length; i++) {
			String hex = Integer.toHexString(0xff & hash[i]);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	@Override
	@Entitlement(name = "Update UserExtensionData selfServChangeSignerPin", action = ActionType.APPROVE, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData selfServChangeSignerPin", action = ActionType.VIEW, requiredResources = {})
	@Entitlement(name = "Update UserExtensionData selfServChangeSignerPin", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin" })
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_UUS_SPR", parent = "MT", name = "Update UserExtensionData selfServChangeSignerPin", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.ADMIN_MAINTENANCE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.APPROVALS,
			TaskAspect.AUDIT }, type = TaskType.NONFINANCIAL_TRANSACTION)
	public TransactionStatus selfServChangeSignerPin(SessionContext sessionContext,	UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into selfServChangeSignerPin() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin", sessionContext, requestDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();

		BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin requestDTO:"+SerializationUtils.toJsonString(requestDTO));

		try {
			extensionExecutor.preUpdate(sessionContext, requestDTO);
//			requestDTO.validate(sessionContext);
			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserExtensionKey());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin domain:"+SerializationUtils.toJsonString(domain));

			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			SelfServChangeSignerPinBusinessPolicyDTO policyDTO = new SelfServChangeSignerPinBusinessPolicyDTO(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = bpfact.getBusinesPolicyInstance("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin", policyDTO);
			abstractBusinessPolicy.validate();
			BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin validate: true");
			BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin requestDTO: SelfChangeSignerPin:"+requestDTO.getSelfChangeSignerPin());

			if (domain != null && domain.getCdcNo() != null) {
				domain.setSignerPinstatus(LOGIN_PIN_STATUS_INACTIVE);
				domain.setSignerHoldStatus(LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED);
				domain.setSignerHoldReason("Others");
				domain.setForceChangeSigner("N");
				domain.setLastUpdatedDate(new Date());
				domain.update(domain);

				String transactionId = (String) com.ofss.fc.infra.thread.ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
				ResetUserPinRecord record = new ResetUserPinRecord();
				ResetUserPinRecordKey recordKey = new ResetUserPinRecordKey();
				recordKey.setKey(transactionId);
				record = record.read(recordKey);
				if(record != null && record.getUserId() != null) {
					record.setWorkflowStatus("APPROVED");
					record.setResetStatus("PENDING_MODIFICATION");
					record.setForceChangePin("Y");
					record.setLastUpdatedBy(requestDTO.getUserExtensionKey());
					record.setLastUpdatedDate(new Date());
					record.setApproveTime(new Date());
					record.update(record);
					BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin ResetUserPinRecord:"+SerializationUtils.toJsonString(record));
				}

				IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
	            		com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
	            IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
	            		.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
            	List<Object[]> transactionList = hostuserDetailsAdapter.listChangeSignerPinPendingTransactions(domain.getCdcNo());
				//check other pending transactions
				String partyId = domain.getCdcNo();
				String userId = domain.getUserID();
				List<ResetUserPinRecord> resetUserPinRecords = record.listPendingResetUserPinRecordList(partyId, userId, Arrays.asList("PENDING_APPROVAL","APPROVED"));
				Optional.ofNullable(resetUserPinRecords).orElse(Collections.emptyList()).stream()
				.forEach(it -> rejectOtherPendingRecord(sessionContext, partyId, userId, hostuserDetailsAdapter, transactionList, it, transactionId));

				//email
                com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
                UserKey userKey = new UserKey();
                userKey.setUserId(requestDTO.getUserExtensionKey());
                userDomain = userDomain.read(userKey);
                requestDTO.setMaskedEmailId(userDomain.getEmailId());
                BeaSystemOut.println("Entering UserExtensionData selfServChangeSignerPin EmailId: "+userDomain.getEmailId());

				IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
						"callFMOHelper", DeterminantType.Enterprise);
				BeaSystemOut.println("Entering UserExtensionData FMO for selfServForgotSignerPin ");
				BeaSystemOut.println("Entering UserExtensionData FMO  selfServForgotSignerPin requestDTO " + requestDTO);
				adapter.callFMOHelper(sessionContext, "MT_N_UUS_SPR", requestDTO, true);
			}

			extensionExecutor.postUpdate(sessionContext, requestDTO, transactionStatus);
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from selfServChangeSignerPin() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from selfServChangeSignerPin() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME), rte);
		}finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from selfServChangeSignerPin() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	private void rejectOtherPendingRecord(SessionContext sessionContext, String partyId, String currentUserId, IHostUserDetailsInvocationAdapter hostuserDetailsAdapter,
			List<Object[]> transactionList, ResetUserPinRecord record, String transactionId){
		if (transactionList != null && !transactionList.isEmpty()) {
        	for(Object[] item : transactionList) {
        		if(item != null && item.length==3) {
    				String transactionUserID = String.valueOf(item[0]);
    				String pendingTransactionId = String.valueOf(item[1]);
    				String approveStatus = String.valueOf(item[2]);
    				BeaSystemOut.println("UserExtensionData selfServChangeSignerPin : transactionUserID=" + transactionUserID
    						+", pendingTransactionId="+pendingTransactionId+", approveStatus="+approveStatus);
	        		if (currentUserId.equals(transactionUserID)
	        				&& pendingTransactionId.equals(record.getResetUserPinRecordKey().getKey()) && !transactionId.equals(pendingTransactionId)
	        				&& (ApprovalStatus.PENDING_APPROVAL==ApprovalStatus.fromValue(approveStatus)
	        				|| (ApprovalStatus.APPROVED==ApprovalStatus.fromValue(approveStatus) && Arrays.asList("PENDING_MODIFICATION", "MODIFIED", "EFFECTIVE").contains(record.getResetStatus())))) {
						try {
							BeaSystemOut.println("UserExtensionData rejectOtherSignerPinPendingTransaction userDataDTO.UserID: " + transactionUserID);
							if(ApprovalStatus.PENDING_APPROVAL==ApprovalStatus.fromValue(approveStatus)) {
								hostuserDetailsAdapter.rejectOtherPendingTransaction(pendingTransactionId, RESET_REJECT_REMARK);
							}
    						ResetUserPinRecordKey resetUserPinRecordKey = new ResetUserPinRecordKey();
    						resetUserPinRecordKey.setKey(pendingTransactionId);
    						record.setResetUserPinRecordKey(resetUserPinRecordKey);
    						record.setWorkflowStatus("REJECTED");
    						record.setStatus("N");
							record.setEffectiveTime(null);
							record.setLastUpdatedBy(record.getLastUpdatedBy() + "~" + sessionContext.getUserId());
							record.setLastUpdatedDate(new Date());
							record.setApproveTime(new Date());
							record.update(record);

							//TODO send reject notification BCOCDC-6741/6742
							if (ApprovalStatus.APPROVED==ApprovalStatus.fromValue(approveStatus) && Arrays.asList("MODIFIED","EFFECTIVE").contains(record.getResetStatus())) {
                                //BCOCDC-6742/6741
                                sendExceptionHandlingNotifications(partyId,currentUserId,UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_EMAIL_EVENT,UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_EVENT);
							}
							BeaSystemOut.println("UserExtensionData selfServChangeSignerPin You have an other pending transaction in process! send notification to user rejected");
						} catch (Exception e) {
							BeaSystemOut.printErr(e);
						}
					}
	        	}
			}
		}
	}

	@Override
	public SelfServChangeSignerPinResponseDTO getSelfServChangeSignerPinPending(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into getSelfServChangeSignerPinPending() : requestDTO = %s in class %s ",
					"", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read", sessionContext, requestDTO);

		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);

		BeaSystemOut.println("Entering UserExtensionData getSelfServChangeSignerPinPending init.");
		SelfServChangeSignerPinResponseDTO responseDTO = new SelfServChangeSignerPinResponseDTO();
		try {
			extensionExecutor.preUpdate(sessionContext, requestDTO);

			String userId = sessionContext.getUserId();
			BeaSystemOut.println("UserExtensionData getSelfServChangeSignerPinPending, userId=" + userId);
			if(Objects.isNull(userId) || userId.indexOf("@") == -1) {
				BeaSystemOut.println("DIGX_CMN_0069 UserExtensionData getSelfServChangeSignerPinPending get userId session timeout.");
				Exception exception = new Exception("DIGX_CMN_0069");
				ValidationError[] validationErrors = new ValidationError[1];
				validationErrors[0] = new ValidationError(THIS_COMPONENT_NAME, null, null, "DIGX_CMN_0069", "");
				exception.setValidationErrors(validationErrors);
				throw exception;
			}
			String[] userDtls = userId.split("@");
			String currentUserId = userDtls[0];
			String partyId = userDtls[1];
			BeaSystemOut.println("UserExtensionData getSelfServChangeSignerPinPending, partyId=" + partyId + ", currentUserId=" + currentUserId);

			ResetUserPinRecord resetUserPinRecord = new ResetUserPinRecord();
			Predicate<ResetUserPinRecord> rejectFilter = u -> ("REJECTED".equals(u.getWorkflowStatus()));
			Predicate<ResetUserPinRecord> notActiveFilter = u -> !Arrays.asList("EFFECTIVE","MODIFIED").contains(u.getResetStatus());
			List<ResetUserPinRecord> resetUserPinRecords = resetUserPinRecord.listPendingResetUserPinRecordList(partyId, userId, Arrays.asList("PENDING_APPROVAL","APPROVED","REJECTED"));
			Optional.ofNullable(resetUserPinRecords).orElse(Collections.emptyList()).stream()
				.findFirst()
				.filter(rejectFilter.or(notActiveFilter))
				.filter(record -> "Y".equals(record.getStatus()))
				.ifPresent(record -> {
					responseDTO.setTransactionId(record.getResetUserPinRecordKey().getKey());
					responseDTO.setApprovalStatus(record.getWorkflowStatus());
				});

		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from getSelfServChangeSignerPinPending() for requestDTO '%s' in class %s",
					"", THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from getSelfServChangeSignerPinPending() for requestDTO '%s' in class %s",
							"", THIS_COMPONENT_NAME), rte);
		}finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from getSelfServChangeSignerPinPending() : transactionStatus = %s", transactionStatus));
		}
		return responseDTO;
	}

	@Override
	public TransactionStatus validateSelfServChangeSignerPin(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered into validateSelfServChangeSignerPin() : requestDTO = %s in class %s ",
					requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.read", sessionContext, requestDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);

		try {
			extensionExecutor.preRead(sessionContext, requestDTO);

			requestDTO.setSelfChangeSignerPin("PENDING_APPROVAL");
			BeaSystemOut.println("Entering UserExtensionData validateSelfServChangeSignerPin requestDTO:"+SerializationUtils.toJsonString(requestDTO));
			BusinessPolicyFactory bpfact = BusinessPolicyFactory.getInstance();
			SelfServChangeSignerPinBusinessPolicyDTO policyDTO = new SelfServChangeSignerPinBusinessPolicyDTO(requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = bpfact.getBusinesPolicyInstance("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin", policyDTO);
			abstractBusinessPolicy.validate();
			BeaSystemOut.println("Entering UserExtensionData validateSelfServChangeSignerPin validate: true");

		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from validateSelfServChangeSignerPin() for requestDTO '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from validateSelfServChangeSignerPin() for requestDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME), rte);
		}finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("Exiting from validateSelfServChangeSignerPin() : transactionStatus = %s", transactionStatus));
		}
		return transactionStatus;
	}

	@Override
	@Entitlement(name = "Check TFA of Update UserExtensionData resetSignerPinByUser", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.resetSignerPinByUser" })
	@EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.User_Management)
	@Task(id = "MT_N_UUS_RSP", parent = "MT", name = "Check TFA of Update UserExtensionData resetSignerPinByUser", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.BACK_OFFICE, aspects = {
			TaskAspect.TWO_FACTOR_AUTHENTICATION, TaskAspect.AUDIT }, type = TaskType.ADMINISTRATION)
	@Role(role = RoleType.AUTHADMIN)
	public SelfResetSignerPinResponseDTO resetSignerPinByUser(SessionContext sessionContext, SelfResetSignerPinRequestDTO requestDTO) throws Exception {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Entered into method resetSignerPinByUser() in Service  Input: SelfResetSignerPinRequestDTO: %s in class %s",
							requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.resetSignerPinByUser", sessionContext, requestDTO);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		SelfResetSignerPinResponseDTO response = new SelfResetSignerPinResponseDTO();
		try {
			requestDTO.validate(sessionContext);
			requestDTO.setSigner(true);
			requestDTO.setOldPassword(null);
			response.setStatus(fetchStatus());

			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(requestDTO.getUserId());
			domain.setUserExtensionDataKey(key);
			domain = domain.read(key);
			BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser domain:"+SerializationUtils.toJsonString(domain));

			//validate resetSingerPin
			BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser.validateResetSignerPinByUser requestDTO:"+SerializationUtils.toJsonString(requestDTO));
			BusinessPolicyFactory bpfactReset = BusinessPolicyFactory.getInstance();
			ResetSignerPinBusinessPolicyDTO resetSignerPinBusinessPolicyDTO = new ResetSignerPinBusinessPolicyDTO(requestDTO);
			AbstractBusinessPolicy resetSignerPinBusinessPolicy = bpfactReset.getBusinesPolicyInstance("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.resetSignerPinByUser", resetSignerPinBusinessPolicyDTO);
			resetSignerPinBusinessPolicy.validate();
			BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser.validateResetSignerPinByUser validate: true");

			CheckLoginPinDTO4CDCRequestDto checkLoginPinDTO4CDCRequestDto = new CheckLoginPinDTO4CDCRequestDto();
			checkLoginPinDTO4CDCRequestDto.setAcctNo(requestDTO.getAcctNo4CheckLoginPin());
			checkLoginPinDTO4CDCRequestDto.setActPwd(requestDTO.getActPwd4CheckLoginPin());
			checkLoginPinDTO4CDCRequestDto.setUsrId(requestDTO.getUsrId4CheckLoginPin());
			checkLoginPinDTO4CDCRequestDto.setKeyIndicator(requestDTO.getKeyIndicator4CheckLoginPin());
			checkLoginPinDTO4CDCRequestDto.setDecryptedUsername(requestDTO.getDecryptedUsername4CheckLoginPin());
			boolean isCCBSignerPinValidated = false;
			if(domain.getSignerID()!=null && (domain.getMigtationStatus()!= null && domain.getMigtationStatus().equals("S")) && getOffshoreDevFlag()) {
				BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser Migrated user case for resetSignerPinByUser");
				checkLoginPinDTO4CDCRequestDto.setIsSignerPinCheckRequired(true);

				IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
				IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
						.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);

				CheckLoginPinDTO4CDCResponseDto checkLoginPinDTO4CDC = hostuserDetailsAdapter.checkLoginPin4CDC(sessionContext, checkLoginPinDTO4CDCRequestDto);
				if (checkLoginPinDTO4CDC.getResult().equals("1")) {
					BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser Result = 1 for Migrated user case for resetSignerPinByUser");
					isCCBSignerPinValidated=true;
				}
			} else {
				BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser Non-Migrated user case for resetSignerPinByUser");
				isCCBSignerPinValidated=true;
			}
			if(isCCBSignerPinValidated) {

				SelfResetSignerPinAssembler resetSignerPinAssembler = new SelfResetSignerPinAssembler();
				HostCredentialsRequestDTO hostCredentialsRequestDTO = resetSignerPinAssembler.fromDomainObjectRequestDTO(requestDTO);
				IHostCredentialsAdapter hostAdapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IHostCredentialsAdapter.class,
						"changeCredentials", DeterminantType.Enterprise);
				HostCredentialsResponseDTO credentialResponse = hostAdapter.changeCredentials(hostCredentialsRequestDTO);

				BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser credentialResponse:"+SerializationUtils.toJsonString(credentialResponse));
				if (credentialResponse != null && credentialResponse.getPasswordValidationRepsonseDTO() != null
						&& credentialResponse.getPasswordValidationRepsonseDTO().isVerificationStatus()) {
					UserDTO userDTO = new UserDTO();
					userDTO.setUsername(hostCredentialsRequestDTO.getUserId());
					userDTO = new com.ofss.digx.app.sms.service.user.User().read(sessionContext, userDTO).getUserDTO();

					String role = null;
					if (userDTO != null) {
						role = userDTO.getUserGroups().get(0);
					}

					com.ofss.digx.app.sms.service.user.password.policy.PasswordPolicy policyService = new com.ofss.digx.app.sms.service.user.password.policy.PasswordPolicy();
					com.ofss.digx.app.sms.dto.user.password.PasswordPolicyResponseDTO passwordPolicyResponse = policyService.fetchPasswordPolicy(sessionContext, role);
					BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser passwordPolicyResponse:"+SerializationUtils.toJsonString(passwordPolicyResponse));
					if (passwordPolicyResponse != null && passwordPolicyResponse.getPasswordPolicyDTO() != null
							&& passwordPolicyResponse.getPasswordPolicyDTO().getPwdMaxExpiryDays() != null) {
						Integer pwdMaxExpiryDays = passwordPolicyResponse.getPasswordPolicyDTO().getPwdMaxExpiryDays();
						domain.setSignPinExpiryDate(new Date().plusDays(pwdMaxExpiryDays));
						domain.setIsSignerPinReminder(false);
						domain.setSignerPinType("AN");
						domain.setForceChangeSigner("N");
						domain.setSignerPinstatus(LOGIN_PIN_STATUS_NORMAL);
						domain.setSignerHoldStatus(LOGIN_HOLD_STATUS_NO_ACTIVITY_ALLOWED);
						domain.setSignerHoldReason("Others");
						domain.update(domain);

						ResetUserPinRecord record = new ResetUserPinRecord();
						record = record.listApprovedResetUserPinRecord(domain.getCdcNo(), domain.getUserID());
						if(record != null && record.getResetUserPinRecordKey().getKey() != null) {
							record.setResetStatus("MODIFIED");
							record.setForceChangePin("N");
							record.setLastUpdatedBy(requestDTO.getUserId());
							record.setLastUpdatedDate(new Date());
							record.setEffectiveTime(new Date(new Date().plusDays(1).toString("yyyy-MM-dd")+" 07:00:00","yyyy-MM-dd HH:mm:ss"));
							record.update(record);
							BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser ResetUserPinRecord:"+SerializationUtils.toJsonString(record));

							String txnId = record.getResetUserPinRecordKey().getKey();
							List<ResetUserPinRecord> otherApprovalPendingRecords = record.listPendingResetUserPinRecordList(domain.getCdcNo(), domain.getUserID(), Arrays.asList("PENDING_APPROVAL","APPROVED"));
							Optional.ofNullable(otherApprovalPendingRecords).orElse(Collections.emptyList()).stream()
								.filter(it -> !txnId.equals(it.getResetUserPinRecordKey().getKey()))
								.forEach(it -> {
									ResetUserPinRecord updateRecord = new ResetUserPinRecord();
									ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
									updateRecordKey.setKey(it.getResetUserPinRecordKey().getKey());
									updateRecord.setResetUserPinRecordKey(updateRecordKey);
									try {
										updateRecord = updateRecord.read(updateRecordKey);
										updateRecord.setStatus("N");
										updateRecord.setLastUpdatedBy(requestDTO.getUserId());
										updateRecord.setLastUpdatedDate(new Date());
										updateRecord.update(updateRecord);
									} catch (Exception e) {
										BeaSystemOut.printErr(e);
									}
								});
						}

						//send notification
						Map<String,Object> parameterMap = new HashMap<String, Object>();
						parameterMap.put("userId", record.getUserId());
						parameterMap.put("userPartyId", domain.getCdcNo());
						parameterMap.put("approveTime", record.getApproveTime());
						//send email and SMS
						sendSuccessResetSignerPinNotifications(parameterMap);

						IFMOHelperCallAdapter fmoAdapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
								"callFMOHelper", DeterminantType.Enterprise);
						BeaSystemOut.println("Entering UserExtensionData FMO for resetSignerPinByUser ");
						BeaSystemOut.println("Entering UserExtensionData FMO resetSignerPinByUser requestDTO " + requestDTO);
						fmoAdapter.callFMOHelper(sessionContext, "MT_N_UUS_RSP", requestDTO, true);
					}
				} else {
					Exception infraException = new Exception();
					infraException.setErrorCode("DIGX_DB_015");
					infraException.setDisplayMessage("Self Reset Failed to update Signer Pin");
					infraException.setLocalizedMessage("Self Reset Failed to update Signer Pin");
					BeaSystemOut.println("Entering UserExtensionData resetSignerPinByUser Error In Credentials line 680- Invalid");
					throw infraException;
				}
			}
			response.setStatus(buildStatus(transactionStatus));
		} catch (Exception e) {
			logger.log(Level.SEVERE, formatter.formatMessage(
					"RuntimeException from method resetSignerPinByUser() in Service  Input: SelfResetSignerPinRequestDTO: '%s' in class %s",
					SelfResetSignerPinResponseDTO.class.getName(), THIS_COMPONENT_NAME, requestDTO, e), e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RunTimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE, formatter.formatMessage(
					"RuntimeException from method resetSignerPinByUser() in Service  Input: SelfResetSignerPinRequestDTO: '%s' in class %s",
					requestDTO, THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.encodeOutput(response);
		super.checkResponsePolicy(sessionContext, response);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage(
							"Exiting from method resetSignerPinByUser() in Service Output: SelfResetSignerPinResponseDTO : %s in class %s",
							response, THIS_COMPONENT_NAME));
		}

		return response;
	}

	private boolean getOffshoreDevFlag() {
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter("CUSTOM_CONFIG_ADAPTER");
		String flag = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "SIGNER_OFFSHORE_DEV", "false");
		return Boolean.parseBoolean(flag);
	}

	//BCOCDC-6825 start
	private void sendSuccessResetSignerPinNotifications(Map<String,Object> parameterMap) throws Exception {
		try {
			BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications parameters= "
					+ SerializationUtils.toJsonString(parameterMap));
	        Set<String> sendEmailSet = new HashSet<>();
			if (!parameterMap.containsKey("userId") || !parameterMap.containsKey("userPartyId")|| !parameterMap.containsKey("approveTime")) {
				BeaSystemOut.println(
						"UserExtensionData sendSuccessResetSignerPinNotifications miss the important parameter");
				return;
			}
			String userId = (String)parameterMap.get("userId");
			String userPartyId = (String)parameterMap.get("userPartyId");
			Date approveTime = (Date)parameterMap.get("approveTime");

			//get user email and phone number
			if (StringUtils.isBlank(userId)) {
				BeaSystemOut.println(
						"UserExtensionData sendSuccessResetSignerPinNotifications miss userId or userId is null");
				return;
			}
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications userId= " + userId);

			com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
			UserKey userKey = new UserKey();
			userKey.setUserId(userId);
			userDomain = userDomain.read(userKey);
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications userDomain= " + SerializationUtils.toJsonString(userDomain));
			String userEmail = userDomain.getEmailId();
			if (StringUtils.isNotBlank(userEmail)) {
				sendEmailSet.add(userEmail);
			}
			String userMobileNumber = userDomain.getMobileNumber();
			BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications userMobileNumber= " + userMobileNumber);


            // get companyEmail
			if (StringUtils.isBlank(userPartyId)) {
				BeaSystemOut.println(
						"UserExtensionData sendSuccessResetSignerPinNotifications miss userPartyId or userPartyId is null");
				return;
			}
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications userPartyId= " + userPartyId);

            String resetSignerPinAccountNumber = getFormatAccountNumber(CZAccountHelper.formatExtAccountNumber(userPartyId));
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications resetSignerPinAccountNumber= " + resetSignerPinAccountNumber);
            String companyEmail = getCompanyEmail(userPartyId);
            if (StringUtils.isNotBlank(companyEmail)) {
                sendEmailSet.add(companyEmail);
			}

			String userName = getUsernameFromUserId(userId);
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications userName= " + userName);

			//send Email
            BeaSystemOut.println("UserExtensionData sendSuccessResetSignerPinNotifications sendEmailSet= " + SerializationUtils.toJsonString(sendEmailSet));
			if (CollectionUtils.isNotEmpty(sendEmailSet) ) {
                sendEmailEvent(userPartyId,userName,UserExtensionDataConstants.USER_SUCCESS_RESET_SIGNER_PIN_EMAIL_EVENT,sendEmailSet,resetSignerPinAccountNumber);
			}

        	//send user SMS
			if (StringUtils.isNotBlank(userMobileNumber)) {
                sendMobileNumberEvent(userId,userPartyId,userMobileNumber,UserExtensionDataConstants.USER_SUCCESS_RESET_SIGNER_PIN_SMS_EVENT,approveTime);
			}
		} catch (Exception e) {
			BeaSystemOut.printErr(e);
		}
	}


    private String getFormatAccountNumber(String accountNumber) throws Exception {
    	BeaSystemOut.println("UserExtensionData getFormatAccountNumber start accountNumber= " + accountNumber);
        String formatAccountNumber = accountNumber;
        if (formatAccountNumber.startsWith(ACCOUNT_START_STR)) {
            if (formatAccountNumber.length() == CONSTANT_NINETEEN) {
                BeaSystemOut.println("UserExtensionData getFormatAccountNumber Account is standalone so masking will be different : " + formatAccountNumber);
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - CONSTANT_EIGHT, formatAccountNumber.length() - CONSTANT_FIVE, '*');
            } else {
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - CONSTANT_SEVEN, formatAccountNumber.length() - CONSTANT_FOUR, '*');
            }
        } else {
            formatAccountNumber = formatAccountNumber.replaceAll(".(?=.{5})", "*");
        }
        BeaSystemOut.println("UserExtensionData getFormatAccountNumber end accountNumber= " + accountNumber);
    	return formatAccountNumber;
    }

    private String getCompanyEmail(String userPartyId) throws Exception{
        String companyEmail = null;
        if (StringUtils.isNotBlank(userPartyId)) {
            com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
                    .getInstance().getAdapterFactory(
                            com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
            IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
                    .getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
            CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(userPartyId);
            if (partyDetails != null && StringUtils.isNotBlank(partyDetails.getOfficeEmailId())) {
                companyEmail = partyDetails.getOfficeEmailId();
            }
        }
        BeaSystemOut.println(
                "UserExtensionData sendSubmittedNotifications companyEmail= " + companyEmail);
        return companyEmail;
    }

	private String getUsernameFromUserId(String userId) {
		if (StringUtils.isBlank(userId)) {
			return "";
		}
	    int atIndex = userId.indexOf('@');
	    if (atIndex != -1) {
	        return userId.substring(0, atIndex);
	    } else {
	        return userId;
	    }
	}

	private void sendEmailEvent(String userPartyId,String userName,String emailEventCode,Set<String> sendEmailSet,String resetSignerPinAccountNumber) throws Exception{
		BeaSystemOut.println("UserExtensionData sendEmailEvent parameter userPartyId= "+userPartyId+",userName="+userName+",emailEventCode="+emailEventCode+",sendEmailSet="+SerializationUtils.toJsonString(sendEmailSet)+",resetSignerPinAccountNumber="+resetSignerPinAccountNumber);
		IModuleToAlertAdapter alertAdapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
		SelfServChangeSignerPinLogDTO emailActivityLog = new SelfServChangeSignerPinLogDTO();
		emailActivityLog.setResetSignerPinUserName(userName);
		emailActivityLog.setResetSignerPinAccountNumber(resetSignerPinAccountNumber);
        String codActId = null;
        if (emailEventCode.equals(UserExtensionDataConstants.USER_SUCCESS_RESET_SIGNER_PIN_EMAIL_EVENT)){
            codActId = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.resetSignerPinByUser";
        }else if (emailEventCode.equals(UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_EMAIL_EVENT)){
            codActId = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin";
        }else {
            BeaSystemOut.println("UserExtensionData sendEmailEvent not this type smsEventCode");
            return;
        }
        for (String signerEmail : sendEmailSet) {
            BeaSystemOut.println("UserExtensionData sendEmailEvent:ready to send email : "
                    + signerEmail);
    		NotificationDetail emailNotificationDetail = new NotificationDetail();
    		NotificationDetail[] details = new NotificationDetail[1];
			emailNotificationDetail.setRecipientId(userPartyId);
			emailNotificationDetail.setDestination(DestinationType.EMAIL);
			emailNotificationDetail.setDispatchAddress(signerEmail);
			emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

			details[0] = emailNotificationDetail;
			emailActivityLog.setNotificationDetails(details);
			alertAdapter.registerActivityAndGenerateEvent(codActId,
					emailEventCode, new Date(),
					emailActivityLog);
            BeaSystemOut.println("UserExtensionData sendEmailEvent:send email ok ");
        }
	}

	private void sendMobileNumberEvent(String userId,String userPartyId,String mobileNumber,String smsEventCode,Date approveTime) throws Exception{
		BeaSystemOut.println("UserExtensionData sendMobileNumberEvent:ready to send SMS : parameters userPartyId="+userPartyId +",mobileNumber="+mobileNumber+",smsEventCode="+smsEventCode);

		IModuleToAlertAdapter alertAdapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
		SelfServChangeSignerPinLogDTO smsActivityLog = new SelfServChangeSignerPinLogDTO();
		NotificationDetail[] details = new NotificationDetail[1];
		NotificationDetail messageNotificationDetail = new NotificationDetail();
		messageNotificationDetail.setRecipientId(userPartyId);
		messageNotificationDetail.setDestination(DestinationType.SMS);
		messageNotificationDetail.setDispatchAddress(mobileNumber);
		messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

		details[0] = messageNotificationDetail;
		smsActivityLog.setNotificationDetails(details);
		smsActivityLog.setCustomerId(userPartyId);
        String codActId = null;
        if (smsEventCode.equals(UserExtensionDataConstants.USER_SUCCESS_RESET_SIGNER_PIN_SMS_EVENT)){
            List<String> formatDateArray = getSignerPinFormatDate(approveTime);
            smsActivityLog.setResetSignerPinSuccessDateTime(formatDateArray.get(0));
            smsActivityLog.setResetSignerPinSuccessYear(formatDateArray.get(1));
            smsActivityLog.setResetSignerPinSuccessMonth(formatDateArray.get(2));
            smsActivityLog.setResetSignerPinSuccessDay(formatDateArray.get(3));
            smsActivityLog.setResetSignerPinSuccessHour(formatDateArray.get(4));
            smsActivityLog.setResetSignerPinSuccessMinute(formatDateArray.get(5));
            codActId = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.resetSignerPinByUser";
        }else if (smsEventCode.equals(UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_EVENT)){
            smsActivityLog.setUserId(userId);
            codActId = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin";
        }else {
            BeaSystemOut.println("UserExtensionData sendMobileNumberEvent not this type smsEventCode and will not send SMS");
            return;
        }
        BeaSystemOut.println("UserExtensionData sendMobileNumberEvent codActId="+codActId+"/smsEventCode="+smsEventCode);
        BeaSystemOut.println("UserExtensionData sendMobileNumberEvent smsActivityLog="+ SerializationUtils.toJsonString(smsActivityLog));
        alertAdapter.registerActivityAndGenerateEvent(codActId,
                smsEventCode, new Date(),
                smsActivityLog);
        BeaSystemOut.println("UserExtensionData sendMobileNumberEvent:send SMS ok ,mobileNumber="+mobileNumber);
	}

	private List<String> getSignerPinFormatDate(Date approveTime) {
		LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDateTime newDateTime = null;
		if (approveTime == null || approveTime.getYear() == 1970) {
			newDateTime = currentDateTime;
		} else {
			newDateTime = LocalDateTime.of(approveTime.getYear(), approveTime.getMonth(), approveTime.getDayOfMonth(),
					currentDateTime.getHour(), currentDateTime.getMinute());
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

		List<String> result = new ArrayList<>();
		result.add(newDateTime.format(formatter));
		result.add(String.valueOf(newDateTime.getYear()));
		result.add(String.valueOf(newDateTime.getMonthValue()));
		result.add(String.valueOf(newDateTime.getDayOfMonth()));
        result.add(String.format("%02d", newDateTime.getHour()));
		result.add(String.format("%02d", newDateTime.getMinute()));
		BeaSystemOut.println("UserExtensionData getSignerPinFormatDate result=" + SerializationUtils.toJsonString(result));
		return result;
	}
	//BCOCDC-6825 end

	//BCOCDC-6403 add for daily effective user reset signer pin  batch job start
	public void effectiveUserResetPin(SessionContext sessionContext) throws Exception {
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		try {

			final String HOLD = "No Activity Allowed";
			final String NORMAL = "Normal";
			final String UNHOLD = "Unhold";
			final String NIL = "NIL";
			ResetUserPinRecord record = new ResetUserPinRecord();
			List<ResetUserPinRecord> recordList = record.listPendingResetUserPinRecordListAll();
			BeaSystemOut.println("UserExtensionData effectiveUserResetPin resetUserPinRecord=" + SerializationUtils.toJsonString(recordList));

			Predicate<ResetUserPinRecord> pendingWorkflowFilter = u -> ("PENDING_APPROVAL".equals(u.getWorkflowStatus()));
			Predicate<ResetUserPinRecord> pendingResetFilter = u -> (u.getResetStatus() == null || "PENDING_MODIFICATION".equals(u.getResetStatus()));
			List<ResetUserPinRecord> pendingRecordList =
					Optional.ofNullable(recordList).orElse(Collections.emptyList()).stream()
						.filter(pendingWorkflowFilter.or(pendingResetFilter))
						.filter(u -> ("Y".equals(u.getStatus())))
						.collect(Collectors.toList());

			Predicate<ResetUserPinRecord> rejectWorkflowFilter = u -> ("REJECTED".equals(u.getWorkflowStatus()));
			List<ResetUserPinRecord> rejectRecordList =
					Optional.ofNullable(recordList).orElse(Collections.emptyList()).stream()
						.filter(rejectWorkflowFilter)
						.filter(u -> ("Y".equals(u.getStatus())))
						.collect(Collectors.toList());

			//modified APPROVED & MODIFIED
			Predicate<ResetUserPinRecord> modifiedWorkflowFilter = u -> ("APPROVED".equals(u.getWorkflowStatus()));
			Predicate<ResetUserPinRecord> modifiedResetFilter = u -> ("MODIFIED".equals(u.getResetStatus()));
			List<String> modifiedUserIds = new ArrayList<>();
			Optional.ofNullable(recordList).orElse(Collections.emptyList()).stream()
					.filter(modifiedWorkflowFilter.and(modifiedResetFilter))
					.filter(u -> checkPendingAfterEffectiveTime(u, pendingRecordList))
					.filter(u -> checkEffectiveTime(u.getEffectiveTime()))
					.map(u -> u.getResetUserPinRecordKey().getKey())
					.forEach(key -> {
						ResetUserPinRecord updateRecord = new ResetUserPinRecord();
						ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
						updateRecordKey.setKey(key);
						updateRecord.setResetUserPinRecordKey(updateRecordKey);
						try {
							updateRecord = updateRecord.read(updateRecordKey);
							updateRecord.setResetStatus("EFFECTIVE");
							updateRecord.setStatus("N");
							updateRecord.setLastUpdatedDate(new Date());
							updateRecord.update(updateRecord);
							BeaSystemOut.println("UserExtensionData effectiveUserResetPin updateRecord=" + SerializationUtils.toJsonString(updateRecord));
							modifiedUserIds.add(updateRecord.getUserId());
						} catch (Exception e) {
							BeaSystemOut.printErr(e);
						}
					});
			//update user signer hold status
			Optional.ofNullable(modifiedUserIds).orElse(Collections.emptyList()).stream()
				.forEach(userId -> {
					com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData userExtensionData = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
					try {
						UserExtensionDataKey userExtensionDataKey = new UserExtensionDataKey();
						userExtensionDataKey.setUserExtensionKey(userId);
						userExtensionData = userExtensionData.read(userExtensionDataKey);
						if(userExtensionData != null && userExtensionData.getCdcNo() != null
								&& NORMAL.equals(userExtensionData.getSignerPinstatus())
								&& HOLD.equals(userExtensionData.getSignerHoldStatus())) {
							userExtensionData.setSignerPinstatus(NORMAL);
							userExtensionData.setSignerHoldStatus(UNHOLD);
							userExtensionData.setSignerHoldReason(NIL);
							userExtensionData.setLastUpdatedDate(new Date());
							userExtensionData.update(userExtensionData);
							BeaSystemOut.println("UserExtensionData effectiveUserResetPin userExtensionData=" + SerializationUtils.toJsonString(userExtensionData));
						}
					} catch (Exception e) {
						BeaSystemOut.printErr(e);
					}
				});


			IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
            		com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
            IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
            		.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
            Optional.ofNullable(pendingRecordList).orElse(Collections.emptyList()).stream()
            	.map(u -> u.getResetUserPinRecordKey().getKey())
            	.forEach(key -> {
            		ResetUserPinRecord updateRecord = new ResetUserPinRecord();
					ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
					updateRecordKey.setKey(key);
					updateRecord.setResetUserPinRecordKey(updateRecordKey);
					try {
						updateRecord = updateRecord.read(updateRecordKey);
						final String recordUserId = updateRecord.getUserId();
						final String recordTransactionId = updateRecord.getResetUserPinRecordKey().getKey();

						boolean rejectFlag = false;
						List<Object[]> transactionList = hostuserDetailsAdapter.listChangeSignerPinPendingTransactions(updateRecord.getPartyId());
						if (transactionList != null && !transactionList.isEmpty()) {
				        	for(Object[] item : transactionList) {
				        		if(item != null && item.length==3) {
				        			String transactionUserID = String.valueOf(item[0]);
				        			String transactionId = String.valueOf(item[1]);
				        			String approveStatus = String.valueOf(item[2]);
				        			if(transactionId.equals(recordTransactionId) && (transactionUserID.equals(recordUserId))
				        					&& ApprovalStatus.REJECTED==ApprovalStatus.fromValue(approveStatus)) {
				        				rejectFlag = true;
				        				break;
				        			}
				        		}
				        	}
						}
						if(rejectFlag) {
							updateRecord.setStatus("N");
							updateRecord.setWorkflowStatus("REJECTED");
							updateRecord.setLastUpdatedDate(new Date());
							updateRecord.update(updateRecord);
						}
						BeaSystemOut.println("UserExtensionData effectiveUserResetPin invalid WorkflowStatus=REJECTED for others record=" + SerializationUtils.toJsonString(updateRecord));
					} catch (Exception e) {
						BeaSystemOut.printErr(e);
					}
            	});


			//set invalid for others
			Optional.ofNullable(rejectRecordList).orElse(Collections.emptyList()).stream()
				.map(u -> u.getResetUserPinRecordKey().getKey())
				.forEach(key -> {
					ResetUserPinRecord updateRecord = new ResetUserPinRecord();
					ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
					updateRecordKey.setKey(key);
					updateRecord.setResetUserPinRecordKey(updateRecordKey);
					try {
						updateRecord = updateRecord.read(updateRecordKey);
						updateRecord.setStatus("N");
						updateRecord.setLastUpdatedDate(new Date());
						updateRecord.update(updateRecord);
						BeaSystemOut.println("UserExtensionData effectiveUserResetPin invalid Status=N for others record=" + SerializationUtils.toJsonString(updateRecord));
					} catch (Exception e) {
						BeaSystemOut.printErr(e);
					}
				});

			//set 7 days reject for pending record
			Optional.ofNullable(pendingRecordList).orElse(Collections.emptyList()).stream()
				.filter(u -> checkCreateTimeExpired(u.getCreationDate()))
				.map(u -> u.getResetUserPinRecordKey().getKey())
				.forEach(key -> {
					ResetUserPinRecord updateRecord = new ResetUserPinRecord();
					ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
					updateRecordKey.setKey(key);
					updateRecord.setResetUserPinRecordKey(updateRecordKey);
					try {
						updateRecord = updateRecord.read(updateRecordKey);
						updateRecord.setWorkflowStatus("REJECTED");
						updateRecord.setLastUpdatedDate(new Date());
						updateRecord.update(updateRecord);
						BeaSystemOut.println("UserExtensionData effectiveUserResetPin 7 days reject for pending record=" + SerializationUtils.toJsonString(updateRecord));
					} catch (Exception e) {
						BeaSystemOut.printErr(e);
					}
				});

		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			logger.log(Level.SEVERE, formatter.formatMessage("Exception from UserExtensionData effectiveUserResetPin() for requestDTO '%s' in class %s",
					"", THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			logger.log(Level.SEVERE,
					formatter.formatMessage("RuntimeException from UserExtensionData effectiveUserResetPin() for requestDTO '%s' in class %s",
							"", THIS_COMPONENT_NAME), rte);
		}finally {
			Interaction.close();
		}
	}

	private boolean checkCreateTimeExpired(Date creationDate) {
		Date currentDate =  new Date();
		boolean expired = creationDate != null && currentDate.isAfterOrEqual(creationDate.plusDays(7));
		BeaSystemOut.println("UserExtensionData effectiveUserResetPin time expired="+expired);
		return expired;
	}

	private boolean checkPendingAfterEffectiveTime(ResetUserPinRecord effectiveRecord, List<ResetUserPinRecord> pendingRecordList) {
		Predicate<ResetUserPinRecord> pendingFilter = record ->
			(record.getUserId().equals(effectiveRecord.getUserId())
			&& record.getCreationDate().compareTo(effectiveRecord.getCreationDate()) > 0);
			BeaSystemOut.println("UserExtensionData effectiveUserResetPin checkPendingAfterEffectiveTime="
			+ Optional.ofNullable(pendingRecordList).orElse(Collections.emptyList()).stream().noneMatch(pendingFilter));
		return Optional.ofNullable(pendingRecordList).orElse(Collections.emptyList()).stream().noneMatch(pendingFilter);
	}

	private boolean checkEffectiveTime(Date effectiveTime) {
		try {
			ICoreApplicationServiceAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(
					ICoreApplicationServiceAdapter.class, "fetchBranchDates",
					DeterminantResolver.getInstance().getDeterminantTypeForObject(BranchDatesDefinitionDTO.class.getName()));
			BeaSystemOut.println("BranchDateHelper : Adapter calling to get CurrentDate is : " + adapter);
			BranchDatesDefinitionDTO responseDTO = adapter.fetchBranchDates("BEA", "");
			BeaSystemOut.println("UserExtensionData effectiveUserResetPin checkEffectiveTime response year="+responseDTO.getCurrentDate().getYear()+",response month="+responseDTO.getCurrentDate().getMonth()+",response day="+responseDTO.getCurrentDate().getDayOfMonth());
			Date hostDate =  new Date();
			BeaSystemOut.println("UserExtensionData effectiveUserResetPin checkEffectiveTime host year="+hostDate.getYear()+",host month="+hostDate.getMonth()+",host day="+hostDate.getDayOfMonth());
			BeaSystemOut.println("UserExtensionData effectiveUserResetPin checkEffectiveTime effe year="+effectiveTime.getYear()+",effe month="+effectiveTime.getMonth()+",effe day="+effectiveTime.getDayOfMonth());

			return effectiveTime != null
					&& hostDate.getYear() == effectiveTime.getYear()
					&& hostDate.getMonth() == effectiveTime.getMonth()
					&& hostDate.getDayOfMonth() == effectiveTime.getDayOfMonth();
		} catch (Exception e) {
			BeaSystemOut.printErr(e);
		}
		return false;
	}
	//BCOCDC-6403 end


	//BCOCDC-6742 start
    private void sendExceptionHandlingNotifications(String partyId,String userId,String emailEventCode,String smsEventCode) throws Exception {
        BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications parameters  partyId= "
                + partyId +"//////userId= "+userId + "////emailEventCode="+emailEventCode+"///smsEventCode="+smsEventCode);
        try {
            List<String> userList = new ArrayList<String>();
            Set<String> sendEmailSet = new HashSet<>();
            String userMobileNumber = null;
            if (StringUtils.isBlank(userId) ||StringUtils.isBlank(partyId)||StringUtils.isBlank(emailEventCode)||StringUtils.isBlank(smsEventCode)) {
                BeaSystemOut.println(
                        "UserExtensionData sendExceptionHandlingNotifications parameters value are null");
                return;
            }
            userList.add(userId);
            String resetSignerPinAccountNumber = getFormatAccountNumber(CZAccountHelper.formatExtAccountNumber(partyId));
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications resetSignerPinAccountNumber= " + resetSignerPinAccountNumber);

            // All AP's Email Address and Users' Email Address
            com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
            UserExtensionDataKey key = new UserExtensionDataKey();
            key.setUserExtensionKey(userId);
            domain.setUserExtensionDataKey(key);
            domain = domain.read(key);
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications domain= " + SerializationUtils.toJsonString(domain));
            List<String> apUsers = listAPUsersByParty(domain);
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications apUsers= " + SerializationUtils.toJsonString(apUsers));
            if(CollectionUtils.isNotEmpty(apUsers)){
                userList.addAll(apUsers);
            }
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userList= " +SerializationUtils.toJsonString(userList));

            // get companyEmail Address
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userPartyId= " + partyId);
            String companyEmail = getCompanyEmail(partyId);
            if (StringUtils.isNotBlank(companyEmail)) {
                sendEmailSet.add(companyEmail);
            }
            if (CollectionUtils.isNotEmpty(userList)) {
                userList = userList.stream().distinct().collect(Collectors.toList());
                for (String signerId : userList) {
                    com.ofss.digx.domain.sms.entity.user.User userDomain = new com.ofss.digx.domain.sms.entity.user.User();
                    UserKey userKey = new UserKey();
                    userKey.setUserId(signerId);
                    userDomain = userDomain.read(userKey);
                    BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userDomain= " + SerializationUtils.toJsonString(userDomain)+"/////////signerId="+signerId);
                    String signerEmail = userDomain.getEmailId();
                    if (StringUtils.isNotBlank(signerEmail)) {
                        sendEmailSet.add(signerEmail);
                    }
                    if (userId.equals(signerId)) {
                        userMobileNumber = userDomain.getMobileNumber();
                        BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userMobileNumber=" + userMobileNumber);
                    }
                }
            }
            String userName = getUsernameFromUserId(userId);
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userName= " + userName);

            //send Email
            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications sendEmailSet= " + SerializationUtils.toJsonString(sendEmailSet));
            if (CollectionUtils.isNotEmpty(sendEmailSet)) {
                sendEmailEvent(partyId,userName,emailEventCode,sendEmailSet,resetSignerPinAccountNumber);
            }

            BeaSystemOut.println("UserExtensionData sendExceptionHandlingNotifications userMobileNumber= " + userMobileNumber);
            //send user SMS
            if (StringUtils.isNotBlank(userMobileNumber)) {
                sendMobileNumberEvent(userId,partyId,userMobileNumber,smsEventCode,null);
            }
        } catch (Exception e) {
            BeaSystemOut.printErr(e);
        }
    }


	private List<String> listAPUsersByParty(com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain) throws Exception {
		if(domain != null && domain.getCdcNo() != null) {
			List<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData> userExtensionDataList = domain.listUsersByParty(domain);
			//ap userId
			List<String> apUserList =
					Optional.ofNullable(userExtensionDataList).orElse(Collections.emptyList())
					.stream().filter(com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData::getIsAuthorisedPerson)
					.map(user -> user.getUserExtensionDataKey().getUserExtensionKey()).collect(Collectors.toList());
			BeaSystemOut.println("UserExtensionData listAPUsersByParty : apUserList=" + SerializationUtils.toJsonString(apUserList));
			return apUserList;
		}
		return null;
	}
	//BCOCDC-6742 end
}
