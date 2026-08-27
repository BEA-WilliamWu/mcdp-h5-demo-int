/*******************************************************************************
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *******************************************************************************/
package com.ofss.digx.cz.bea.app.sms.service.user.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.Locale;
import java.util.Map;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.sms.adapter.user.IUserMeAdapter;
import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.digx.app.sms.dto.user.UserListResponseDTO;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.app.sms.dto.user.UserUpdateCredentialsRequestDTO;
import com.ofss.digx.app.sms.dto.user.password.PasswordPolicyResponseDTO;
import com.ofss.digx.app.sms.service.user.User;
import com.ofss.digx.app.sms.service.user.ext.VoidUserExt;
import com.ofss.digx.app.sms.service.user.password.policy.PasswordPolicy;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.crm.adapter.ICRMAsserterCallAdapter;
import com.ofss.digx.cz.bea.app.gaas.dto.access.GaasAccessListDTO;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.LoginUserDetailsDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.LoginUserDetailsResponseDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.SignerUserDetailsDTO;
import com.ofss.digx.cz.bea.app.hostuserdetails.dto.SignerUserDetailsResponseDTO;
import com.ofss.digx.cz.bea.app.itoken.dto.ITokenCancelResponseDTO;
import com.ofss.digx.cz.bea.app.itoken.dto.ITokenDTO;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.PasswordExpiryDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserTokenDataDTO;
import com.ofss.digx.cz.bea.common.constants.CZCommonErrorConstants;
import com.ofss.digx.cz.bea.domain.sms.entity.user.RevokedUser;
import com.ofss.digx.cz.bea.domain.sms.entity.user.RevokedUserKey;
import com.ofss.digx.cz.bea.common.constants.UserItokenConstants;
import com.ofss.digx.cz.bea.common.framework.crm.CRMConstants;
import com.ofss.digx.cz.bea.common.framework.crm.CRMInputData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.cz.bea.extxface.fmo.adapter.IFMOHelperCallAdapter;
import com.ofss.digx.cz.bea.extxface.gaas.adapter.IGaasAccessAdapter;
import com.ofss.digx.cz.bea.common.constants.UserItokenConstants;
import com.ofss.digx.cz.bea.extxface.itoken.adapter.IITokenAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.framework.determinant.DeterminantResolver;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.sms.dbAuthenticator.domain.UserProfile;

public class CZUserExt extends VoidUserExt implements com.ofss.digx.app.sms.service.user.ext.IUserExt {

	private static final String LOGINID = "loginId";


	private static final String SIGNERID = "signerId";

	private static final String USER_CHANNEL_TYPE = "userChannelType";

	private static final String CLOSE_ID = "closeId";

	private static final String HTH_ACCESS_SETUP_DONE = "hthAccessSetupDone";
	
	private static final String HOSTUSER_DETAILS_ADAPTER_FACTORY = "HOSTUSER_DETAILS_ADAPTER_FACTORY";
	
	private static final String HOST_USERDETAILS_INVOCATION_ADAPTER = "HOST_USERDETAILS_INVOCATION_ADAPTER";

	/**
	 * This is the extension point for {@code User#softDelete(SessionContext, userDetails)}. Execution of business
	 * logic, which necessarily has to happen before going ahead with normal service execution can be added here.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details.
	 * @param userDTO
	 *            {@link UserDTO} contains necessary information about the user to be soft deleted or revoked.
	 * @throws Exception
	 *             if any fatal exception occurred.
	 */
	@Override
	public void preSoftDelete(SessionContext sessionContext, UserDTO userDTO) throws Exception {
		IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
		IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
				.getAdapter(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
		SignerUserDetailsResponseDTO signerUserDetailsResponseDTO = null;
		LoginUserDetailsResponseDTO loginUserDetailsResponseDTO = null;
		LoginUserDetailsDTO loginUserDetailsDTO = new LoginUserDetailsDTO();
		SignerUserDetailsDTO signerUserDetailsDTO = new SignerUserDetailsDTO();
		boolean loginIdDeleteFlag = false, signerIdDeleteFlag = false;
		
		UserExtensionData userExtensionDomain = new UserExtensionData();
		UserExtensionDataKey userExtensionKey = new UserExtensionDataKey();
		userExtensionKey.setUserExtensionKey(userDTO.getUsername());
		userExtensionDomain = userExtensionDomain.read(userExtensionKey);
		
		if(userDTO.getDictionaryArray() != null) {
			for (Dictionary dictionary : userDTO.getDictionaryArray()) {
				for (NameValuePairDTO nameValuePairDTO : dictionary.getNameValuePairDTOArray()) {
					if (nameValuePairDTO.getName().equals(LOGINID)) {
						loginUserDetailsDTO.setLoginId(nameValuePairDTO.getValue());
					}

					if (nameValuePairDTO.getName().equals(SIGNERID)) {
						signerUserDetailsDTO.setSignerId(nameValuePairDTO.getValue());
					}
				}
			}	
		}

		if (loginUserDetailsDTO.getLoginId() != null) {
			loginUserDetailsDTO.setCdcId(userExtensionDomain.getCdcNo());
			loginUserDetailsResponseDTO = hostuserDetailsAdapter.deleteLoginId(loginUserDetailsDTO);

			if (!loginUserDetailsResponseDTO.isVerified()) {
				throw new Exception(CZCommonErrorConstants.DEFAULT_HOST_ERROR);
			} else {
				loginIdDeleteFlag = true;
			}
		}

		if (signerUserDetailsDTO.getSignerId() != null) {
			signerUserDetailsDTO.setCdcId(userExtensionDomain.getCdcNo());
			signerUserDetailsResponseDTO = hostuserDetailsAdapter.deleteSignerId(signerUserDetailsDTO);

			if (!signerUserDetailsResponseDTO.isVerified()) {
				throw new Exception(CZCommonErrorConstants.DEFAULT_HOST_ERROR);
			} else {
				signerIdDeleteFlag = true;
			}
		}
		
		/**
		 * Update UserExtensionData domain based on loginIdDeleteFlag and signerIdDeleteFlag flags
		 */
		if(loginIdDeleteFlag || signerIdDeleteFlag) {
//			UserExtensionData userExtensionDomain = new UserExtensionData();
//			UserExtensionDataKey userExtensionKey = new UserExtensionDataKey();
//			
//			userExtensionKey.setUserExtensionKey(userDTO.getUsername());
//			userExtensionDomain = userExtensionDomain.read(userExtensionKey);
			
			if(loginIdDeleteFlag) {
				userExtensionDomain.setLoginID("");
				userExtensionDomain.setLoginHoldReason("");
				userExtensionDomain.setLoginHoldStatus("");
				userExtensionDomain.setLoginPinReferenceNo("");
				userExtensionDomain.setLoginPinstatus("");
			}
			
			if(signerIdDeleteFlag) {
				userExtensionDomain.setSignerID("");
				userExtensionDomain.setSignerHoldReason("");
				userExtensionDomain.setSignerHoldStatus("");
				userExtensionDomain.setSignerPinReferenceNo("");
				userExtensionDomain.setSignerPinstatus("");
			}
			
			userExtensionDomain.update(userExtensionDomain);
		}
		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("delete repo for fmo" + taskId);
		SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
				"callFMOHelper", DeterminantType.Enterprise);
		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
		adapter.callFMOHelper(session, taskId, userExtensionDomain, true);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("delete repo fmo for corporate user");
		}

	}


	/**
	 * This is the extension point for
	 * {@code User#softDelete(SessionContext, userDetails)}. Post hook process like
	 * Output response manipulation, Custom data logging for subsequent processing
	 * or reporting can be performed here.
	 * 
	 * @param sessionContext    {@link SessionContext} containing session details.
	 * @param userDTO           {@link UserDTO} contains necessary information about
	 *                          the user to be soft deleted or revoked.
	 * @param transactionStatus {@link TransactionStatus} containing status of the
	 *                          transaction
	 * @throws Exception if any fatal exception occurred.
	 */
	@Override
	public void postSoftDelete(SessionContext sessionContext, UserDTO userDTO, TransactionStatus transactionStatus)
			throws Exception {
		if (sessionContext.getUserId() != null && userDTO.getUsername() != null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostDelete username=" + userDTO.getUsername());

			IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(HOSTUSER_DETAILS_ADAPTER_FACTORY);
			IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
					.getAdapter(HOST_USERDETAILS_INVOCATION_ADAPTER);
			hostuserDetailsAdapter.deleteUserFromGroups(sessionContext, userDTO.getUsername());

			//BCOCDC-7316 start
			//insert record for Revoked user
//			String email = CustomConfigUtil.readConfigValue("DUMMY_EMAIL", "dummy@dummy.dummy");
//			String mobileNo = CustomConfigUtil.readConfigValue("DUMMY_MOBILE_NO", "00000000");

			IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
			ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
					.getAdapter("CUSTOM_CONFIG_ADAPTER");
			String email  ="";
			String mobileNo= "";
			try {
				email = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DUMMY_EMAIL", "dummy@dummy.dummy");
				mobileNo = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DUMMY_MOBILE_NO", "00000000");
			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
			BeaSystemOut.println("postSoftDelete DUMMY_EMAIL is "+email);
			BeaSystemOut.println("postSoftDelete DUMMY_MOBILE_NO is "+mobileNo);
			UserExtensionData userExtensionDomain = new UserExtensionData();
			UserExtensionDataKey userExtensionKey = new UserExtensionDataKey();
			userExtensionKey.setUserExtensionKey(userDTO.getUsername());
			userExtensionDomain = userExtensionDomain.read(userExtensionKey);


			com.ofss.sms.dbAuthenticator.domain.UserProfile userProfileDomain = new com.ofss.sms.dbAuthenticator.domain.UserProfile();
			com.ofss.sms.dbAuthenticator.domain.UsersKey userProfileKey = new com.ofss.sms.dbAuthenticator.domain.UsersKey();
			userProfileKey.setUserName(userDTO.getUsername());
			userProfileDomain.setKey(userProfileKey);
			userProfileDomain = userProfileDomain.read(userProfileDomain);
			//insert RevokedUser record
			RevokedUser revokedUserDomain = getRevokedUser(userDTO, userProfileDomain, userExtensionDomain);
			revokedUserDomain.create(revokedUserDomain);

			//change UserExtensionData to dummy mobileNo
			userExtensionDomain.setMobileNo(mobileNo);
			userExtensionDomain.update(userExtensionDomain);
			BeaSystemOut.println("postSoftDelete DUMMY_MOBILE_NO update done");

			//change UserProfile to dummy email
			userProfileDomain.setEmailId(email);
			userProfileDomain.setMobileNumber(mobileNo);
			userProfileDomain.update(userProfileDomain);
			BeaSystemOut.println("postSoftDelete DUMMY_EMAIL update done");

			//BCOCDC-7316 end
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Delete from groups done");
			// add Auto cancellation of i-Token BCM-248 start   2026-04-07
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Auto cancellation of i-Token service Begin");
			String userId = userDTO.getUsername();
			String editBy = sessionContext.getUserId();
			ITokenDTO itoken = new ITokenDTO();
			itoken.setUserIdentifier(userId);
			itoken.setInstanceStatus("SUS");
			itoken.setScenario(UserItokenConstants.SUS_BY_ACCESS_REVOKE_CHANGE);
			itoken.setLastUpdatedBy(editBy);
			IITokenAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IITokenAdapter.class,
					"cancelItoken",
					DeterminantResolver.getInstance().getDeterminantTypeForObject(ITokenDTO.class.getName()));
			// call ITK_CANCEL_ITOKEN
			ITokenCancelResponseDTO response = adapter.cancelItoken(itoken);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Auto cancellation of i-Token service End respone:" + response.getReturnCode());
			// add Auto cancellation of i-Token BCM-248 end

			// BCM-2549: CDC6907 - Cancel I-Token on BCO
			boolean success = "200".equals(response.getReturnCode());
			sendITokenCancelCrm(sessionContext, userId, success);
		}
	}

	private void sendITokenCancelCrm(SessionContext sessionContext, String userName, boolean success) throws Exception {
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt add crm sendCrm CDC6907 begin ");
			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CRM_ADAPTER_FACTORY);
			ICRMAsserterCallAdapter crmAdapter = (ICRMAsserterCallAdapter) adapterFactory.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CRM_ADAPTER);

			// calling fetchCommonInfo to fill in most of the generic fields in CRMInputData, not passing serviceParameters and taskCode won't impact those generic fields
			Preferences crmConfiguration = ConfigurationFactory.getInstance().getConfigurations("CRMConfiguration");
			CRMInputData data = crmAdapter.fetchCommonInfo(null, "", crmConfiguration, null, sessionContext, userName);
			data.setEventActvTypeCode(CRMConstants.CRM_EVENT_CANCEL_ITOKEN);

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt UserExtension get Itoken begin ");
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserItokenInfo userItokenDomain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserItokenInfo();
			UserTokenDataDTO tokenDto = userItokenDomain.getUserItokenInfo(userName, null);
			data.setTokenId(tokenDto.getTokenId());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt UserExtension get Itoken status_ : " + tokenDto.getItokenStatus());

			data.setEventStatusCode(success ? "A": "R");

			crmAdapter.crmInsertForRaq(data);
		} catch (java.lang.Exception e) {
			BeaSystemOut.printErr(e);
			BeaSystemOut.println("CZUserExtensionDataExt CRMAsserter.crmInsertData( crm" + e);
		}
	}

	private static RevokedUser getRevokedUser(UserDTO userDTO, UserProfile userProfileDomain, UserExtensionData userExtensionDomain) {
		RevokedUser revokedUserDomain =new RevokedUser();
		RevokedUserKey revokedUserkEY =new RevokedUserKey();
		revokedUserkEY.setKey(userDTO.getUsername());
		revokedUserDomain.setRevokedUserKey(revokedUserkEY);

		revokedUserDomain.setUmMobileNo(userProfileDomain.getMobileNumber());
		revokedUserDomain.setUmEmail(userProfileDomain.getEmailId());
		revokedUserDomain.setCzMobileNo(userExtensionDomain.getMobileNo());
		revokedUserDomain.setCzMobileCode(userExtensionDomain.getMobileCode());
		revokedUserDomain.setLastUpdateTime(new Date());
		return revokedUserDomain;
	}

	@Override
	public void postUpdateCredentials(SessionContext sessionContext,
			UserUpdateCredentialsRequestDTO userUpdateCredentialsRequestDTO, TransactionStatus transactionStatus) throws Exception {
		
		UserDTO userDTO = new UserDTO();
		PasswordExpiryDTO passwordExpiryDTO = new PasswordExpiryDTO();
		com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
		userDTO.setUsername(userUpdateCredentialsRequestDTO.getUserId());
		userDTO = new User().read(sessionContext, userDTO).getUserDTO();
		UserResponseDTO userResponseDTO = new UserResponseDTO();
		
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(CommonAdapterFactoryConstants.USER_ME_ADAPTER_FACTORY);
		IUserMeAdapter adapter = (IUserMeAdapter) adapterFactory.getAdapter(CommonAdapterConstants.USER_ME_ADAPTER);
		userResponseDTO = adapter.readUser(sessionContext, userUpdateCredentialsRequestDTO.getUserId());
		
		String role = null;
		if (userDTO != null) {
			role = userDTO.getUserGroups().get(0);
		}
		// Fetching a password policy DTO to Validate the new password
		PasswordPolicy policyService = new PasswordPolicy();
		PasswordPolicyResponseDTO passwordPolicyResponse;
		Date todayDate = new Date();
		
		// set role to fetch password policy
		passwordPolicyResponse = policyService.fetchPasswordPolicy(sessionContext, role);
		Integer loginPinExtendDays = passwordPolicyResponse.getPasswordPolicyDTO().getPwdMaxExpiryDays();

		if(userResponseDTO.getUserDTO().getPwdExpiryDate().compareTo(todayDate) == -1) {
			passwordExpiryDTO.setPwdExpiryDate(todayDate.plusDays(loginPinExtendDays));
		} else {
			passwordExpiryDTO.setPwdExpiryDate(userResponseDTO.getUserDTO().getPwdExpiryDate().plusDays(loginPinExtendDays));
		}

		passwordExpiryDTO.setUserId(userResponseDTO.getUserDTO().getUsername());
		passwordExpiryDTO.setUserType("LOGIN");
		domain.updatePasswordExpiryDate(passwordExpiryDTO);
	}

	/**
	 * Called after the execution of actual business logic for searching a user. Calls the postSearch executional method
	 * of all the extensions.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details.
	 * @param userDTO
	 *            {@link UserDTO} contains necessary information to perform search operation.
	 * @param userListResponseDTO
	 *            {@link UserListResponseDTO} object returned from User service class.This response object, gives the
	 *            extensionExecutor class an option to handle it
	 * @throws Exception
	 *             if any fatal exception occurred.
	 */
	@Override
	public void postList(SessionContext sessionContext, UserDTO userDTO, UserListResponseDTO userListResponseDTO)
			throws Exception {
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("=========================== In Service Executor postList ===========================");
		
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("com.ofss.digx.infra.thread.ThreadAttribute.get(isAdmin) "+com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin"));
		
		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
			
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserDTOListFiltered postList inside if");
			
			List<UserDTO> UserDTOListFiltered = new ArrayList<UserDTO>();
			
			for (UserDTO UserDTO : userListResponseDTO.getUserDTOList())
			{
				if(!UserDTO.isDeleteStatus())
				{
					UserDTOListFiltered.add(UserDTO);
				}
			}

			userListResponseDTO.setUserDTOList(UserDTOListFiltered);

		}


		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
			try{
				System.out.println("###GAAS_ACCESS###:::::::  test" );
				Boolean flag = false;
				if(ThreadAttribute.get("GAAS_ACCESS_FLAG")!=null){
					flag = ((Boolean)ThreadAttribute.get("GAAS_ACCESS_FLAG")).booleanValue();
				}
				System.out.println("postList::::GAAS_ACCESS_FLAG:::"+flag);
				if(userDTO!=null && flag){
					for(UserDTO dto: userListResponseDTO.getUserDTOList()){
						IGaasAccessAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(
								IGaasAccessAdapter.class, "listGaasAccessByUser",
								DeterminantResolver.getInstance()
										.getDeterminantTypeForObject(GaasAccessListDTO.class.getName()));

						List<GaasAccessListDTO> itemList = adapter.listGaasAccessByUser(dto.getUsername());
						if(itemList!=null && !itemList.isEmpty()){
							dto.setAccountAccessSetupDone(true);
						} else {
							dto.setAccountAccessSetupDone(false);
						}
					}
				}
			}catch (Exception e) {

			}
		}


		if (Boolean.TRUE.equals(userDTO.isAccessSetupCheckRequired())
				&& userListResponseDTO.getUserDTOList() != null) {
			/*
			 * Enrich the standard user list without changing its response contract. HTH_USER_PROFILE
			 * supplies channel type and CloseID; active effective account grants supply setup status.
			 * Enterprise HTH enablement alone is intentionally not treated as user access setup.
			 */
			List<UserExtensionData> userExtensionDataList = new UserExtensionData().listUsers(userDTO);
			Map<String, String> userChannelTypes = new HashMap<String, String>();
			Map<String, String> closeIds = new HashMap<String, String>();
			Map<String, Boolean> hthAccessSetupStatuses = new HashMap<String, Boolean>();

			if (userExtensionDataList != null) {
				for (UserExtensionData userExtensionData : userExtensionDataList) {
					String userId = normalizeUserId(userExtensionData.getUserID());

					if (userId != null) {
						if (userExtensionData.getUserChannelType() != null) {
							userChannelTypes.put(userId, userExtensionData.getUserChannelType());
						}
						if (userExtensionData.getCloseId() != null) {
							closeIds.put(userId, userExtensionData.getCloseId());
						}
						if (userExtensionData.getHthAccessSetupDone() != null) {
							hthAccessSetupStatuses.put(userId, userExtensionData.getHthAccessSetupDone());
						}
					}
				}
			}

			for (UserDTO responseUser : userListResponseDTO.getUserDTOList()) {
				String userId = normalizeUserId(responseUser.getUsername());
				String userChannelType = userId == null ? null : userChannelTypes.get(userId);
				String normalizedUserChannelType =
						IHthUserProfileAdapter.normalizeUserChannelType(userChannelType);

				setDictionaryValue(responseUser, USER_CHANNEL_TYPE, normalizedUserChannelType);
				setDictionaryValue(responseUser, CLOSE_ID,
						"HTH".equals(normalizedUserChannelType) && userId != null ? closeIds.get(userId) : null);
				setDictionaryValue(responseUser, HTH_ACCESS_SETUP_DONE,
						"HTH".equals(normalizedUserChannelType) && userId != null
								? String.valueOf(Boolean.TRUE.equals(hthAccessSetupStatuses.get(userId))) : null);
			}
		}
	}

	private String normalizeUserId(String userId) {
		if (userId == null) {
			return null;
		}

		// User-list responses can contain login@party while the extension/profile lookup stores the
		// login portion. Case-normalization makes the join consistent with OBDX user-name semantics.
		int separatorIndex = userId.indexOf('@');
		String normalizedUserId = separatorIndex >= 0 ? userId.substring(0, separatorIndex) : userId;

		return normalizedUserId.trim().toUpperCase(Locale.ENGLISH);
	}

	private void setDictionaryValue(UserDTO userDTO, String name, String value) {
		// Reuse an existing extension entry where possible so repeated enrichment never produces
		// duplicate dictionary keys; otherwise append a new isolated dictionary entry.
		Dictionary[] dictionaryArray = userDTO.getDictionaryArray();

		if (dictionaryArray != null) {
			for (Dictionary dictionary : dictionaryArray) {
				if (dictionary == null || dictionary.getNameValuePairDTOArray() == null) {
					continue;
				}

				for (NameValuePairDTO nameValuePairDTO : dictionary.getNameValuePairDTOArray()) {
					if (nameValuePairDTO != null && (name.equals(nameValuePairDTO.getName())
							|| name.equals(nameValuePairDTO.getGenericName()))) {
						nameValuePairDTO.setName(name);
						nameValuePairDTO.setGenericName(name);
						nameValuePairDTO.setValue(value);
						nameValuePairDTO.setDatatype(String.class.getName());
						return;
					}
				}
			}
		}

		NameValuePairDTO nameValuePair = new NameValuePairDTO(name, value, String.class.getName());
		nameValuePair.setGenericName(name);

		Dictionary extensionDictionary = new Dictionary();
		extensionDictionary.setNameValuePairDTOArray(new NameValuePairDTO[] { nameValuePair });

		if (dictionaryArray == null) {
			userDTO.setDictionaryArray(new Dictionary[] { extensionDictionary });
		} else {
			Dictionary[] updatedDictionaryArray = Arrays.copyOf(dictionaryArray, dictionaryArray.length + 1);
			updatedDictionaryArray[dictionaryArray.length] = extensionDictionary;
			userDTO.setDictionaryArray(updatedDictionaryArray);
		}
	}
}
