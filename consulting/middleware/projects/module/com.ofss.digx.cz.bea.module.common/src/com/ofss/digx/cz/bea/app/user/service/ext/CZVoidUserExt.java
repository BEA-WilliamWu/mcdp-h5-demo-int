/*******************************************************************************
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *******************************************************************************/

package com.ofss.digx.cz.bea.app.user.service.ext;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.sms.adapter.user.IUserMeAdapter;
import com.ofss.digx.app.sms.adapter.user.password.policy.IPasswordPolicyAdapter;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.app.sms.dto.user.UserUpdateCredentialsRequestDTO;
import com.ofss.digx.app.sms.dto.user.password.PasswordPolicyResponseDTO;
import com.ofss.digx.app.user.dto.UserComponentDTO;
import com.ofss.digx.app.user.dto.UserProfileResponse;
import com.ofss.digx.app.user.service.User;
import com.ofss.digx.app.user.service.ext.VoidUserExt;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.common.helper.HostToHostManagementHelper;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.cz.bea.common.util.UserManagementUtils;
import com.ofss.digx.cz.bea.domain.party.entity.profile.CZPartyPreferences;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecord;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.datatype.complex.Email;
import com.ofss.digx.datatype.complex.PhoneNumber;
import com.ofss.digx.domain.config.entity.ConfigAllODomain;
import com.ofss.digx.domain.config.entity.ConfigAllODomainKey;
import com.ofss.digx.domain.config.entity.repository.adapter.LocalConfigAllORepositoryAdapter;
import com.ofss.digx.domain.party.entity.profile.PartyPreferencesKey;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.datatype.IDateAdapter;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.digx.common.constants.CommonConstants;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import java.util.Arrays;


/**
 * Represents service extension interface as hook point for {@link User}.</br>
 * Extensions can be called before/after executing business logic of service.
 * These methods are available in class with
 * 'pre and post' as prefix of the actual method name. The implementation of
 * these methods depend upon the actual
 * business requirement under different cases.
 */
public class CZVoidUserExt extends VoidUserExt {
	Preferences dayOneConfig = ConfigurationFactory.getInstance().getConfigurations(CommonConstants.DAY_ONE_CONFIG);

	/**
	 * Attribute to hold the component name.
	 */
	private static final String THIS_COMPONENT_NAME = CZVoidUserExt.class.getName();

	private static final String HTH_CERTIFICATE_MANAGEMENT_COMPONENT = "host-to-host-certificate-management";

	/**
	 * Instance of multi-entity logger.
	 */
	private transient com.ofss.fc.infra.log.impl.MultiEntityLogger formatter = com.ofss.fc.infra.log.impl.MultiEntityLogger
			.getUniqueInstance();

	/**
	 * Instance variable which is required to support multi-entity wide logging.
	 */
	private transient Logger logger = com.ofss.fc.infra.log.impl.MultiEntityLogger.getUniqueInstance()
			.getLogger(THIS_COMPONENT_NAME);

	/**
	 * Processes after the execution of actual business logic for fetching the user
	 * profile details. Calls all the pre
	 * executional methods of all the extensions.
	 *
	 * @param sessionContext
	 *                       {@link SessionContext} containing session details
	 * @param response
	 *                       {@link UserProfileResponse} containing user profile
	 * @throws Exception
	 *                   the exception
	 */

	private String formatMobileNo(String mobileCode, String mobileNo) {
		return mobileCode + "-" + mobileNo;
	}

	@Override
	public void postFetchProfile(SessionContext sessionContext, UserProfileResponse response) throws Exception {
		// Empty Implementation
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in postFetchProfile method of CZVoidUserExt");

		Integer pwdMinExpiryDays;
		// Integer tempDiffDays;
		String tempDiffDays = null;

		UserExtensionData extensionData = new UserExtensionData();
		UserExtensionDataKey extensionKey = new UserExtensionDataKey();
		UserResponseDTO userResponseDTO = new UserResponseDTO();

		Date preLastLoginDate = new Date();

		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");

		if (!isAdmin) {
			extensionKey.setUserExtensionKey(sessionContext.getUserId());
			extensionData = extensionData.read(extensionKey);

			preLastLoginDate = extensionData.getPreLastLogin(sessionContext.getUserId());

			if (extensionData != null) {

				Field emailField;
				try {
					Email email = response.getUserProfile().getEmailId();
					if (response.getUserProfile().getEmailId().getValue() != null
							&& !response.getUserProfile().getEmailId().getValue().trim().equals("")) {
						emailField = Email.class.getSuperclass().getDeclaredField("displayValue");
						emailField.setAccessible(true);
						emailField.set(email,
								UserManagementUtils
										.getMaskedEmailID(response.getUserProfile().getEmailId().getValue()));
					}
				} catch (NoSuchFieldException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching email id."), e);
				} catch (SecurityException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching email id."), e);
				} catch (IllegalArgumentException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching email id."), e);
				} catch (IllegalAccessException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching email id."), e);
				}

				Field phoneField;
				try {
					PhoneNumber phoneNumber = response.getUserProfile().getPhoneNumber();
					if (response.getUserProfile().getPhoneNumber().getValue() != null
							&& !response.getUserProfile().getPhoneNumber().getValue().trim().equals("")) {
						phoneField = PhoneNumber.class.getSuperclass().getDeclaredField("displayValue");
						phoneField.setAccessible(true);
						phoneField.set(phoneNumber,
								UserManagementUtils.getMaskedMobileNumber(formatMobileNo(extensionData.getMobileCode(),
										response.getUserProfile().getPhoneNumber().getValue())));
					}

				} catch (NoSuchFieldException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching phone number."),
							e);
				} catch (SecurityException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching phone number."),
							e);
				} catch (IllegalArgumentException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching phone number."),
							e);
				} catch (IllegalAccessException e) {
					logger.log(Level.SEVERE, formatter.formatMessage("Exception occurred while fetching phone number."),
							e);
				}
			}

			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(CommonAdapterFactoryConstants.USER_ME_ADAPTER_FACTORY);
			IUserMeAdapter adapter = (IUserMeAdapter) adapterFactory.getAdapter(CommonAdapterConstants.USER_ME_ADAPTER);
			userResponseDTO = adapter.readUser(sessionContext, extensionData.getUserID().toString());

			// Manually setting setPreLastLoggedInDateTime since the ORM provided for
			// userProfile does not convert the
			// date values properly
			// References:: userprofile.orm.xml and FCLDAPDATEConverter in product
			if (preLastLoginDate != null) {

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("userResponseDTO.getUserDTO().setPreLastLoggedInDateTime(preLastLoginDate);"
						+ preLastLoginDate.toString());
				response.getUserProfile().setPreLastLoggedInDateTime(preLastLoginDate);
				// userResponseDTO.getUserDTO().setPreLastLoggedInDateTime(preLastLoginDate);
			}
		}

		IAdapterFactory passwordPolicyAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(CommonAdapterFactoryConstants.PASSWORD_POLICY_ADAPTER_FACTORY);
		IPasswordPolicyAdapter passwordPolicyAdapter = (IPasswordPolicyAdapter) passwordPolicyAdapterFactory
				.getAdapter(CommonAdapterConstants.PASSWORD_POLICY_ADAPTER);
		PasswordPolicyResponseDTO passwordPolicyResponse = passwordPolicyAdapter
				.fetchPasswordPolicy(ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID).toString());

		if (extensionData != null && passwordPolicyResponse != null
				&& passwordPolicyResponse.getPasswordPolicyDTO() != null
				&& passwordPolicyResponse.getPasswordPolicyDTO().getPwdMinExpiryDays() != null) {
			Date todayDate = new Date();
			Date warningRangeDate = null;
			long diff;
			if (extensionData != null & extensionData.getSignPinExpiryDate() != null && passwordPolicyResponse != null
					&& passwordPolicyResponse.getPasswordPolicyDTO() != null
					&& passwordPolicyResponse.getPasswordPolicyDTO().getPwdMinExpiryDays() != null) {
				warningRangeDate = extensionData.getSignPinExpiryDate()
						.minusDays(passwordPolicyResponse.getPasswordPolicyDTO().getPwdMinExpiryDays());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("warningRangeDate::" + warningRangeDate);
			}
			// password expiry date, Till then show warning for password change
			if (warningRangeDate != null && extensionData != null && extensionData.getSignPinExpiryDate() != null) {
				if (todayDate.compareTo(warningRangeDate) == 0 || (todayDate.isAfter(warningRangeDate)
						&& todayDate.isBefore(extensionData.getSignPinExpiryDate()))) {
					// todayDate will always be less than password Expiry date
					// when computing warning period days left
					diff = extensionData.getSignPinExpiryDate().getMillis() - todayDate.getMillis();
					tempDiffDays = String.valueOf(TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS));
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("tempDiffDays String ::" + tempDiffDays);

				}
			}

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Extension Data : Migration Status : " + extensionData.getMigtationStatus());

			NameValuePairDTO[] nameValuePairDTOArray = null;
			if (extensionData.getLogOutDate() != null) {
				nameValuePairDTOArray = new NameValuePairDTO[7];
			} else {
				nameValuePairDTOArray = new NameValuePairDTO[6];
			}

			if (tempDiffDays != null) {
				nameValuePairDTOArray[0] = new NameValuePairDTO();
				nameValuePairDTOArray[0].setDatatype("String");
				nameValuePairDTOArray[0].setGenericName("signPwdExpiryWarningDays");
				nameValuePairDTOArray[0].setName("signPwdExpiryWarningDays");
				nameValuePairDTOArray[0].setValue(tempDiffDays.toString());
			} else {
				nameValuePairDTOArray[0] = new NameValuePairDTO();
				nameValuePairDTOArray[0].setDatatype("String");
				nameValuePairDTOArray[0].setGenericName("signPwdExpiryWarningDays");
				nameValuePairDTOArray[0].setName("signPwdExpiryWarningDays");
				nameValuePairDTOArray[0].setValue("0");

			}

			if (extensionData.getIsLoginPinReminder() != null) {
				nameValuePairDTOArray[1] = new NameValuePairDTO();
				nameValuePairDTOArray[1].setGenericName("isLoginPinReminder");
				nameValuePairDTOArray[1].setName("isLoginPinReminder");
				nameValuePairDTOArray[1].setValue(extensionData.getIsLoginPinReminder().toString());
			} else {
				nameValuePairDTOArray[1] = new NameValuePairDTO();
				nameValuePairDTOArray[1].setGenericName("isLoginPinReminder");
				nameValuePairDTOArray[1].setName("isLoginPinReminder");
				nameValuePairDTOArray[1].setValue("false");
			}

			if (extensionData.getIsSignerPinReminder() != null) {
				nameValuePairDTOArray[2] = new NameValuePairDTO();
				nameValuePairDTOArray[2].setGenericName("isSignerPinReminder");
				nameValuePairDTOArray[2].setName("isSignerPinReminder");
				nameValuePairDTOArray[2].setValue(extensionData.getIsSignerPinReminder().toString());
			} else {
				nameValuePairDTOArray[2] = new NameValuePairDTO();
				nameValuePairDTOArray[2].setGenericName("isSignerPinReminder");
				nameValuePairDTOArray[2].setName("isSignerPinReminder");
				nameValuePairDTOArray[2].setValue("false");
			}
			if (extensionData.getLogOutDate() != null) {

				nameValuePairDTOArray[4] = new NameValuePairDTO();
				nameValuePairDTOArray[4].setDatatype("String");
				nameValuePairDTOArray[4].setGenericName("LogOutDate");
				nameValuePairDTOArray[4].setName("LogOutDate");

				IDateAdapter adapter = extensionData.getLogOutDate().fetchAdapter();
				java.util.Date logOutDate = adapter.fetchJavaDate();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+hh:mm");
				String dateValue = sdf.format(logOutDate);
				nameValuePairDTOArray[4].setValue(dateValue);

				nameValuePairDTOArray[5] = new NameValuePairDTO();
				nameValuePairDTOArray[5].setDatatype("String");
				nameValuePairDTOArray[5].setGenericName("Bounce_back_reminder");
				nameValuePairDTOArray[5].setName("Bounce_back_reminder");

				String bounceBackReminder = extensionData.getBounceBackReminder();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Exited in postFetchProfile method Bounce_back_reminder is " + bounceBackReminder);
				nameValuePairDTOArray[5].setValue(bounceBackReminder);
			} else {
				nameValuePairDTOArray[4] = new NameValuePairDTO();
				nameValuePairDTOArray[4].setDatatype("String");
				nameValuePairDTOArray[4].setGenericName("Bounce_back_reminder");
				nameValuePairDTOArray[4].setName("Bounce_back_reminder");

				String bounceBackReminder = extensionData.getBounceBackReminder();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Exited in postFetchProfile method Bounce_back_reminder is " + bounceBackReminder);
				nameValuePairDTOArray[4].setValue(bounceBackReminder);

			}

			if (userResponseDTO != null && userResponseDTO.getUserDTO() != null
					&& userResponseDTO.getUserDTO().getPwdExpiryDate() != null
					&& userResponseDTO.getUserDTO().getPwdExpiryDate().compareTo(todayDate) == -1) {
				nameValuePairDTOArray[1] = new NameValuePairDTO();
				nameValuePairDTOArray[1].setGenericName("isLoginPinReminder");
				nameValuePairDTOArray[1].setName("isLoginPinReminder");
				nameValuePairDTOArray[1].setValue("true");
			}

			if (extensionData.getSignPinExpiryDate() != null
					&& extensionData.getSignPinExpiryDate().compareTo(todayDate) == -1) {
				nameValuePairDTOArray[2] = new NameValuePairDTO();
				nameValuePairDTOArray[2].setGenericName("isSignerPinReminder");
				nameValuePairDTOArray[2].setName("isSignerPinReminder");
				nameValuePairDTOArray[2].setValue("true");
			}
			
			if (extensionData.getIsMerchantUser() != null) {
				nameValuePairDTOArray[1] = new NameValuePairDTO();
				nameValuePairDTOArray[1].setGenericName("isMerchantUser");
				nameValuePairDTOArray[1].setName("isMerchantUser");
				nameValuePairDTOArray[1].setValue(extensionData.getIsMerchantUser().toString());
			} else {
				nameValuePairDTOArray[1] = new NameValuePairDTO();
				nameValuePairDTOArray[1].setGenericName("isMerchantUser");
				nameValuePairDTOArray[1].setName("isMerchantUser");
				nameValuePairDTOArray[1].setValue("false");
			}

			if (extensionData.getLogOutDate() != null) {
				if (extensionData.getMigtationStatus() != null
						&& (extensionData.getMigtationStatus().equalsIgnoreCase("Y")
								|| extensionData.getMigtationStatus().equalsIgnoreCase("N"))
						&& extensionData.getCcbLastLoginDate() != null
						&& response.getUserProfile().getPreLastLoggedInDateTime() != null
						&& extensionData.getCcbLastLoginDate()
								.isAfterOrEqual(response.getUserProfile().getPreLastLoggedInDateTime())) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"PostFetchProfile : LogOutDate is null and User is Migrated : Setting CcbLastLoginDate");
					nameValuePairDTOArray[6] = new NameValuePairDTO();
					nameValuePairDTOArray[6].setDatatype("String");
					nameValuePairDTOArray[6].setGenericName("CcbLastLoginDate");
					nameValuePairDTOArray[6].setName("CcbLastLoginDate");

					IDateAdapter adapter = extensionData.getCcbLastLoginDate().fetchAdapter();
					java.util.Date ccbLastLoginDate = adapter.fetchJavaDate();
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+hh:mm");
					String dateValue = sdf.format(ccbLastLoginDate);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : CcbLastLoginDate when logout not null : " + dateValue);
					nameValuePairDTOArray[6].setValue(dateValue);
				} else {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : CcbLastLoginDate Setting false: ");
					nameValuePairDTOArray[6] = new NameValuePairDTO();
					nameValuePairDTOArray[6].setGenericName("CcbLastLoginDate");
					nameValuePairDTOArray[6].setName("CcbLastLoginDate");
					nameValuePairDTOArray[6].setValue("false");
				}
			} else {
				if (extensionData.getMigtationStatus() != null
						&& (extensionData.getMigtationStatus().equalsIgnoreCase("Y")
								|| extensionData.getMigtationStatus().equalsIgnoreCase("N"))
						&& extensionData.getCcbLastLoginDate() != null
						&& response.getUserProfile().getPreLastLoggedInDateTime() != null
						&& extensionData.getCcbLastLoginDate()
								.isAfterOrEqual(response.getUserProfile().getPreLastLoggedInDateTime())) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"PostFetchProfile : LogOutDate is null and User is Migrated : Setting CcbLastLoginDate");
					nameValuePairDTOArray[5] = new NameValuePairDTO();
					nameValuePairDTOArray[5].setDatatype("String");
					nameValuePairDTOArray[5].setGenericName("CcbLastLoginDate");
					nameValuePairDTOArray[5].setName("CcbLastLoginDate");

					IDateAdapter adapter = extensionData.getCcbLastLoginDate().fetchAdapter();
					java.util.Date ccbLastLoginDate = adapter.fetchJavaDate();
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+hh:mm");
					String dateValue = sdf.format(ccbLastLoginDate);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : CcbLastLoginDate : " + dateValue);
					nameValuePairDTOArray[5].setValue(dateValue);
				} else {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : CcbLastLoginDate Setting false: ");
					nameValuePairDTOArray[5] = new NameValuePairDTO();
					nameValuePairDTOArray[5].setGenericName("CcbLastLoginDate");
					nameValuePairDTOArray[5].setName("CcbLastLoginDate");
					nameValuePairDTOArray[5].setValue("false");
				}
			}

			if (extensionData.getMigtationStatus() != null && (extensionData.getMigtationStatus().equalsIgnoreCase("Y")
					|| extensionData.getMigtationStatus().equalsIgnoreCase("N"))) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : User is Migrated : Setting isMigatedUser to TRUE");
				nameValuePairDTOArray[3] = new NameValuePairDTO();
				nameValuePairDTOArray[3].setGenericName("isMigatedUser");
				nameValuePairDTOArray[3].setName("isMigratedUser");
				nameValuePairDTOArray[3].setValue("true");
			} else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("PostFetchProfile : User is not Migrated : Setting isMigatedUser to false");
				nameValuePairDTOArray[3] = new NameValuePairDTO();
				nameValuePairDTOArray[3].setGenericName("isMigatedUser");
				nameValuePairDTOArray[3].setName("isMigratedUser");
				nameValuePairDTOArray[3].setValue("false");
			}

			Dictionary[] dictionary = new Dictionary[1];
			dictionary[0] = new Dictionary();
			dictionary[0].setNameValuePairDTOArray(nameValuePairDTOArray);

			if(nameValuePairDTOArray != null) {
				NameValuePairDTO item = new NameValuePairDTO();
				item.setGenericName("SelfForceChangeSignerPin");
				item.setName("SelfForceChangeSignerPin");
				IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
		        		com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
		        IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
		        		.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
		        List<Object[]> transactionList = hostuserDetailsAdapter.listChangeSignerPinPendingTransactions(extensionData.getCdcNo());
				ResetUserPinRecord resetUserPinRecord = new ResetUserPinRecord();
				resetUserPinRecord = resetUserPinRecord.listApprovedResetUserPinRecord(extensionData.getCdcNo(), extensionData.getUserID());
				boolean transactionApproved = false;
				if(resetUserPinRecord != null && transactionList != null) {
					boolean preCondition = Stream.of(resetUserPinRecord).allMatch(r -> "PENDING_MODIFICATION".equals(r.getResetStatus()))
							&& Stream.of(resetUserPinRecord).allMatch(r -> "APPROVED".equals(r.getWorkflowStatus()))
							&& Stream.of(resetUserPinRecord).allMatch(r -> "Y".equals(r.getForceChangePin()));

					if(preCondition && !transactionList.isEmpty()) {
						for(Object[] tran : transactionList) {
            				if(tran == null || tran.length != 3) {
            					continue;
            				}
            				String transactionUserID = String.valueOf(tran[0]);
            				String transactionId = String.valueOf(tran[1]);
            				String approveStatus = String.valueOf(tran[2]);
            				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidUserExt : transactionUserID=" + transactionUserID
            						+", transactionId="+transactionId+", approveStatus="+approveStatus);

            				if (ApprovalStatus.APPROVED==ApprovalStatus.fromValue(approveStatus)
            						&& resetUserPinRecord.getUserId().equals(transactionUserID)
            						&& transactionId.equals(resetUserPinRecord.getResetUserPinRecordKey().getKey())) {
            					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidUserExt userDataDTO.UserID: " + transactionUserID);
            					transactionApproved = true;
            					break;
            				}
            			}
					}
				}

				item.setValue(transactionApproved ? Optional.ofNullable(resetUserPinRecord).map(ResetUserPinRecord::getForceChangePin).orElse("N") : "N");

				// Derive channel membership from the same HTH profiles used by user maintenance.
				IHthUserProfileAdapter hthUserProfileAdapter = (IHthUserProfileAdapter) RepositoryAdapterFactory
						.getInstance().getRepositoryAdapter(IHthUserProfileAdapter.HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER);
				boolean isHthUser = hthUserProfileAdapter.listCloseIdsByUserKey(extensionData.getCdcNo())
						.containsKey(IHthUserProfileAdapter.userProfileKey(extensionData.getCdcNo(), extensionData.getUserID()));
				NameValuePairDTO channelType = new NameValuePairDTO();
				channelType.setGenericName("userChannelType");
				channelType.setName("userChannelType");
				channelType.setDatatype("String");
				channelType.setValue(isHthUser ? "HTH" : "BCO");
				NameValuePairDTO[] nextNameValuePairDTOArray = Stream.concat(Arrays.stream(nameValuePairDTOArray), Stream.of(item, channelType)).toArray(NameValuePairDTO[]::new);
				Dictionary[] nextDictionary = new Dictionary[1];
				nextDictionary[0] = new Dictionary();
				nextDictionary[0].setNameValuePairDTOArray(nextNameValuePairDTOArray);
				response.getUserProfile().setDictionaryArray(nextDictionary);
			}
		} else {
			if (!isAdmin) {
				NameValuePairDTO[] nameValuePairDTOArray = new NameValuePairDTO[1];

				if (extensionData.getLogOutDate() != null) {

					nameValuePairDTOArray[0] = new NameValuePairDTO();
					nameValuePairDTOArray[0].setDatatype("String");
					nameValuePairDTOArray[0].setGenericName("LogOutDate");
					nameValuePairDTOArray[0].setName("LogOutDate");

					IDateAdapter adapter = extensionData.getLogOutDate().fetchAdapter();
					java.util.Date logOutDate = adapter.fetchJavaDate();
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS+hh:mm");
					String dateValue = sdf.format(logOutDate);
					nameValuePairDTOArray[0].setValue(dateValue);
				}

				Dictionary[] dictionary = new Dictionary[1];
				dictionary[0] = new Dictionary();
				dictionary[0].setNameValuePairDTOArray(nameValuePairDTOArray);
				response.getUserProfile().setDictionaryArray(dictionary);
			}
		}
		if (!isAdmin) {
			extensionData.updateUserLocale(
					(String) com.ofss.fc.infra.thread.ThreadAttribute
							.get(com.ofss.fc.infra.thread.ThreadAttribute.SUBJECTNAME),
					sessionContext.getTransactingPartyCode(), (String) com.ofss.fc.infra.thread.ThreadAttribute
							.get(com.ofss.fc.infra.thread.ThreadAttribute.USER_LOCALE));
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Exited in postFetchProfile method of CZVoidUserExt");

	}

	@Override
	public void postChangeCredentials(SessionContext sessionContext,
			UserUpdateCredentialsRequestDTO userUpdateCredentialsRequestDTO, TransactionStatus transactionStatus)
			throws Exception {

		UserExtensionData extensionData = new UserExtensionData();
		UserExtensionDataKey extensionKey = new UserExtensionDataKey();
		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");

		if (!isAdmin) {
			extensionKey.setUserExtensionKey(sessionContext.getUserId());
			extensionData = extensionData.read(extensionKey);
			extensionData.setIsLoginPinReminder(false);
			extensionData.update(extensionData);
		}

	}

	@Override
	public void postFetchUIComponent(SessionContext sessionContext, UserComponentDTO userProfileResponse)
			throws Exception {

		String migComponent = "mig-audit-log";
		String migSystemAuditComponent = "mig-system-audit-log";

		CZPartyPreferences partyPreferencesDomain = new CZPartyPreferences();
		PartyPreferencesKey key = new PartyPreferencesKey();

		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");

		if (!isAdmin && sessionContext.getTransactingPartyCode() != null) {
			key.setPartyId(sessionContext.getTransactingPartyCode());
			partyPreferencesDomain.setKey(key);
			partyPreferencesDomain = (CZPartyPreferences) partyPreferencesDomain.read(partyPreferencesDomain);

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered into method as CM : user : " + sessionContext.getUserId());

			if (partyPreferencesDomain != null) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("ExtensionData not null of user : Migration status : "
						+ partyPreferencesDomain.getpartyIdType());

				if (partyPreferencesDomain.getpartyIdType() == null
						|| (partyPreferencesDomain.getpartyIdType() != null
								&& partyPreferencesDomain.getpartyIdType().equals("SE"))
						|| (partyPreferencesDomain.getpartyIdType() != null
								&& partyPreferencesDomain.getpartyIdType().equals("SN"))) {

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("As migration Status is null : Removing the component : mig-audit-log");
					List<String> newComponentList = userProfileResponse.getAuthorizedUIComponents();
					newComponentList.remove(migComponent);
					newComponentList.remove(migSystemAuditComponent);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Removed the migrated Component and updated the new list");
					userProfileResponse.setAuthorizedUIComponents(newComponentList);
				}

			}
			//BCOCDC-3805 BCOCDC-4064
			String partyID = dayOneConfig.get("OCTOPUS_USER", "");
			if ("".equals(partyID) || !Arrays.asList(partyID.split(",")).contains(sessionContext.getTransactingPartyCode())){
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-upload");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-inquiry");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-overview");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-input");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-upload-confirm");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-upload-review");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-file-upload-verification");
				userProfileResponse.getAuthorizedUIComponents().remove("batch-transfer-inquiry-detail");
			}
			LocalConfigAllORepositoryAdapter localConfigAllORepositoryAdapter = LocalConfigAllORepositoryAdapter.getInstance();
			ConfigAllODomain read = localConfigAllORepositoryAdapter.read(new ConfigAllODomainKey() {{
				setPropertyId("BULKPAYMENT_USER");
				setDeterminantValue("OBDX_BU");
				setPreferenceName(CommonConstants.DAY_ONE_CONFIG);
			}});
			String bulPartyID = read.getPropertyValue();
			if ("".equals(bulPartyID) || !Arrays.asList(bulPartyID.split(",")).contains(sessionContext.getTransactingPartyCode())){
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-file-input");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-file-upload-confirm");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-file-upload-review");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-file-upload-verification");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-file-upload");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-inquiry");
				userProfileResponse.getAuthorizedUIComponents().remove("bulk-payment-inquiry-detail");
			}
		}


		removeHostToHostCertificateComponentIfDisabled(sessionContext, userProfileResponse);
	}

	private void removeHostToHostCertificateComponentIfDisabled(SessionContext sessionContext,
			UserComponentDTO userProfileResponse) {
		if (userProfileResponse == null || userProfileResponse.getAuthorizedUIComponents() == null) {
			return;
		}

		try {
			if (!HostToHostManagementHelper
					.isHostToHostEnabled(sessionContext == null ? null : sessionContext.getTransactingPartyCode())) {
				userProfileResponse.getAuthorizedUIComponents().remove(HTH_CERTIFICATE_MANAGEMENT_COMPONENT);
			}
		} catch (java.lang.Exception e) {
			userProfileResponse.getAuthorizedUIComponents().remove(HTH_CERTIFICATE_MANAGEMENT_COMPONENT);
			logger.log(Level.WARNING, formatter.formatMessage(
					"Unable to evaluate Host to Host status. Removing component %s from authorised components.",
					HTH_CERTIFICATE_MANAGEMENT_COMPONENT), e);
		}
	}

	}
