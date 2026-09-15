package com.ofss.digx.cz.bea.domain.service.dispatch;

import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.adapter.impl.AdapterContextHelper;
import com.ofss.digx.app.sms.adapter.user.IUserMeAdapter;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.customconfig.util.CustomConfigUtil;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO;
import com.ofss.digx.cz.bea.datatype.MNGSmsMessageBody;
import com.ofss.digx.cz.bea.datatype.MNGSmsMessageHeader;
import com.ofss.digx.cz.bea.datatype.SMS;
import com.ofss.digx.cz.bea.domain.emailmng.CountryCode;
import com.ofss.digx.cz.bea.domain.emailmng.CountryCodeKey;
import com.ofss.digx.cz.bea.domain.emailmng.EmailMNG;
import com.ofss.digx.cz.bea.domain.emailmng.EmailMNGKey;
import com.ofss.digx.cz.bea.extxface.alert.IMNGSmsAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.fc.app.adapter.ep.IDispatchAdapter;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.app.ep.dto.action.DispatchResultDTO;
import com.ofss.fc.app.ep.service.event.AlertPollerPoolConstant;
import com.ofss.fc.common.Constants;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.fc.domain.ep.entity.dispatch.SMSDispatchDetail;
import com.ofss.fc.domain.ep.service.dispatch.DispatchResult;
import com.ofss.fc.domain.ep.service.dispatch.Dispatcher;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.framework.domain.entity.ep.dto.IDispatchData;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.exception.FatalException;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.utils.SerializationUtils;

public class SMSDispatcher extends Dispatcher {

	private static final String THIS_COMPONENT_NAME = SMSDispatcher.class.getName();
	private transient Logger logger = MultiEntityLogger.getUniqueInstance().getLogger(THIS_COMPONENT_NAME);
	private static final String DISPATCH_DETAILS = "DispatchDetails";
	private transient MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();

	public SMSDispatcher() {

	}

	public SMSDispatcher(SMSDispatchDetail dispatchDetail) throws FatalException {

		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered the constructor SMSDispatcher"));
		}
		this.dispatchDetail = dispatchDetail;
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE,
					formatter.formatMessage("After assigning the values to all the parameters of SMSDispatcher"));
		}
	}

	/**
	 * 
	 */
	@Override
	public SMSDispatchDetail getDispatchDetail() {

		return (SMSDispatchDetail) dispatchDetail;
	}

	private boolean isSecureMessage(String dispatchMessage) {

		Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
		boolean isSecure = true;
		String patterns = null;
		String[] tokens = null;
		String separator = patternPreference.get("sms.pattern.separator", Constants.EMPTY_STRING);
		if (separator != null && !Constants.EMPTY_STRING.equals(separator)) {
			patterns = patternPreference.get("sms.invalid.patterns", Constants.EMPTY_STRING);
			if (patterns != null && !Constants.EMPTY_STRING.equals(patterns)) {
				tokens = patterns.split(separator);
			}
		}
		if (tokens != null && tokens.length > 0) {
			for (String token : tokens) {
				Pattern pattern = Pattern.compile(token);
				Matcher matcher = pattern.matcher(dispatchMessage);
				if (matcher.find()) {
					logger.log(Level.SEVERE, formatter.formatMessage(
							"invalid text found \"'%s'\" which matches the pattern \"'%s'\" ", matcher.group(), token));
					isSecure = false;
				}
			}
		}
		return isSecure;
	}

	/**
	 * @see AlertPollerPoolConstant#fetchSMSLength
	 * @see AlertPollerPoolConstant#fetchUniqueInstance
	 * @see IDispatchAdapter#dispatchSMS
	 */
	@Override
	public DispatchResult dispatchAlert(AlertRequestDTO alertRequestDTO, IDispatchData data) throws FatalException {
        if (HthContactNotificationDispatch.matches(alertRequestDTO)) {
            String body = data.fetchFormattedData(fetchDispatchMessageTemplate(data.getDispatchData()));
            String subject = "";
            if (body != null && isSecureMessage(body)) {
                body = body.replaceAll("\\<.*?\\>", "").replace("\"", "");
                if (body.length() <= AlertPollerPoolConstant.fetchUniqueInstance().fetchSMSLength()) {
                    return HthContactNotificationDispatch.dispatch(alertRequestDTO, "SMS", body, subject);
                }
            }
            DispatchResult rejected = new DispatchResult();
            rejected.setIsDispatchSuccessfull(false);
            rejected.setMessage("Unacceptable HTH contact notification template");
            return rejected;
        }


		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Entered the method SMSDispatcher.dispatchAlert"));
		}
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("Before setting templateMessage"));
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before+++++++++++++++++++"+ SerializationUtils.toJsonString(data.getDispatchData()));
		String templateMessage = fetchDispatchMessageTemplate(data.getDispatchData());
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("After setting templateMessage as '%s'", templateMessage));
		}
		DispatchResult dispatchResult = new DispatchResult();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before***********************=================="+templateMessage);
		String dispatchMessage = data.fetchFormattedData(templateMessage);
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, formatter.formatMessage("After setting dispatchMessage as '%s'", dispatchMessage));
		}
		if (isSecureMessage(dispatchMessage)) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, formatter.formatMessage("Entered the method SMSDispatcher.send"));
			}
			/**
			 * This is added to remove all html tags from the message.
			 */
			if (dispatchMessage != null) {
				dispatchMessage = dispatchMessage.replaceAll("\\<.*?\\>", Constants.EMPTY_STRING);
			}
			Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
			String isSMSDispatchMocked = patternPreference.get("isDispatchMocked", "false");
			if ("false".equalsIgnoreCase(isSMSDispatchMocked)) {
				try {
					int SMSLength = AlertPollerPoolConstant.fetchUniqueInstance().fetchSMSLength();
					if (dispatchMessage.toCharArray().length > SMSLength) {
						dispatchResult.setIsDispatchSuccessfull(false);
						dispatchResult.setDispatchMessage(dispatchMessage);
						dispatchResult.setMessage(
								"Message length is greater than maximum allowed limit of " + SMSLength + " characters");
						if (logger.isLoggable(Level.SEVERE)) {
							logger.log(Level.SEVERE, formatter.formatMessage(
									"Failed to send SMS due to text length exceeding allowed limit of '%s' characters",
									SMSLength));
						}
					} else {
						// SEND SMS CODE
						/*
						 * com.ofss.fc.app.adapter.IAdapterFactory adapterFactory =
						 * AdapterFactoryConfigurator
						 * .getInstance().getAdapterFactory(ModuleConstant.EVENT_PROCESSING_DISPATCH);
						 * IDispatchAdapter adapter = (IDispatchAdapter) adapterFactory
						 * .getAdapter(EventProcessingAdapterConstant.EP_TO_DISPATCH);
						 */
						/*
						 * if (adapter == null) { dispatchResult.setIsDispatchSuccessfull(false);
						 * dispatchResult.setMessage( "NO Outgoing SMS Adapter " +
						 * EventProcessingAdapterConstant.EP_TO_DISPATCH);
						 * dispatchResult.setDispatchMessage(dispatchMessage); } else {
						 */
						// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before calling dispatchMNGSms with dispatchMessage as " +
						// dispatchMessage);
						// dispatchResult=dispatchMNGSms(alertRequestDTO, data,dispatchMessage);

						dispatchMessage = dispatchMessage.replaceAll("\"", "");
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("alertRequestDTO.getActivityLog() : " + alertRequestDTO.getActivityLog());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("alertRequestDTO.toString() : " +SerializationUtils.toJsonString(alertRequestDTO));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("data : " + data.toString());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("dispatchMessage : " + dispatchMessage);
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" #Message template without \" - " + dispatchMessage);
						DispatchResultDTO result = dispatchMNGSms(alertRequestDTO, data, dispatchMessage);
						dispatchResult = new DispatchResult(result);
						dispatchResult.setMessage("SMS Sent Successfully");
						// }
						if (logger.isLoggable(Level.FINE)) {
							logger.log(Level.FINE, formatter.formatMessage("After sending the SMS"));
						}
					}
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Exception from SMSDispatcher.send", e);
					dispatchResult.setIsDispatchSuccessfull(false);
					dispatchResult.setMessage("Failed with error " + e.getMessage());
					dispatchResult.setDispatchMessage(dispatchMessage);
				}
			} else {
				dispatchResult.setIsDispatchSuccessfull(true);
				dispatchResult.setDispatchMessage(dispatchMessage);
				dispatchResult.setMessage("SMS Dispatch Has Been Mocked");
			}
		} else {
			dispatchResult = new DispatchResult();
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setDispatchMessage(dispatchMessage);
			dispatchResult.setMessage("Unacceptable input found in the message text");
		}
		return dispatchResult;
	}

	private DispatchResultDTO dispatchMNGSms(AlertRequestDTO alertRequestDTO, IDispatchData data,
			String dispatchMessage) {

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered the method dispatchMNGSms");

		if (this.logger.isLoggable(Level.FINE)) {
			this.logger.log(Level.FINE,
					this.formatter.formatMessage("Entered the method dispatchMNGSms", new Object[0]));
		}

		DispatchResultDTO dispatchResult = new DispatchResultDTO();
		String selectedEventsUserId = "";
		String defaultUserCountryCode = null;
		
		if(alertRequestDTO.getAlertContactPreference().getDispatchAddress().contains("~")) {
			defaultUserCountryCode = alertRequestDTO.getAlertContactPreference().getDispatchAddress().split("~")[1];
			alertRequestDTO.getAlertContactPreference().setDispatchAddress(alertRequestDTO.getAlertContactPreference().getDispatchAddress().split("~")[0]);
		}
		
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("**print dispact address="+alertRequestDTO.getAlertContactPreference().getDispatchAddress());
		
		NameValuePair[] nvp = data.getDispatchData();
		int min = 0;
		int max = 999;
		String TxnName = "";
		Random rd = new Random(); // creating Random object
		//com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println();
		String TXnRefNo = getCurrentDateAndTime("yyMMddhhmmssSSS") + rd.nextInt(max - min) + min;
		String orgTxnRefNo = "";

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNGEmail Fetching alertRequestDTO.getEventAction().getKeyDTO().getActivityId();");
		String activityId = alertRequestDTO.getEventAction().getKeyDTO().getActivityId();
		String actionId = alertRequestDTO.getEventAction().getKeyDTO().getActionId();
		String eventId = alertRequestDTO.getEventAction().getKeyDTO().getEventId();
		String codActDataId = alertRequestDTO.getCodActDataId();
		// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG actionId= "+activityId);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("eventId ===" + eventId);

		/*
		 ************************* MNG Name value pair in dispatch data
		 * *********************************************** Datatype
		 * com.ofss.fc.domain.ep.entity.dispatch.message.MessageTemplate GenericeName
		 * DispatchTemplate Name Approved Approver EMAIL Value MessageTemplate
		 * [id=MessageTemplateKey [messageTemplateId=Approved_Approver_EMAIL,
		 * determinantValue=OBDX_BU], name=Approved Approver EMAIL, description=Approved
		 * Approver EMAIL, templateBuffer=Dear Customer,<br><p>#TxnName# initiated by
		 * #Initiator# has been approved by you successfully. The reference number for
		 * this transaction is #TxnReferenceNo#.</p><p>Regards</p>Customer Service -
		 * #BankName#., destinationType=EMAIL, templateObject=null,
		 * dataAttributes=[com.ofss.fc.domain.ep.entity.dispatch.message.
		 * MessageDataAttribute@d1e8ed03,
		 * com.ofss.fc.domain.ep.entity.dispatch.message.MessageDataAttribute@a093c7e7,
		 * com.ofss.fc.domain.ep.entity.dispatch.message.MessageDataAttribute@dc77e8e2,
		 * com.ofss.fc.domain.ep.entity.dispatch.message.MessageDataAttribute@a0fbcb5d],
		 * stringBuffer=Transaction Approved] Datatype java.lang.String GenericeName
		 * TxnName Name TxnName Value Adhoc International Payment Datatype
		 * java.lang.String GenericeName Bank Name Name BankName Value BEA Datatype
		 * java.lang.String GenericeName Txn ReferenceNo Name TxnReferenceNo Value
		 * 1505E2756ED4 Datatype java.lang.String GenericeName Initiator Name Initiator
		 * Value ABC s Datatype AlertRecipientId GenericeName null Name RecipientId
		 * Value corpchecker5 MNG Name value pair in dispatch data
		 * ***********************************************
		 * 
		 */

		String forgotPinUserId = "";
		String taskId = "";

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
				"************************* MNG Name value pair in dispatch data ***********************************************");

		for (int i = 0; i < nvp.length; i++) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" Datatype " + nvp[i].getDatatype() + " GenericeName " + nvp[i].getGenericName()
					+ " Name " + nvp[i].getName() + " Value " + nvp[i].getValue());
			if (nvp[i].getName().equalsIgnoreCase("TxnName")) {
				TxnName = (String) nvp[i].getValue();
			}
			if (nvp[i].getName().equalsIgnoreCase("TxnReferenceNo")) {
				orgTxnRefNo = (String) nvp[i].getValue();
			}

			if (nvp[i].getName().equalsIgnoreCase("UserId")) {
				forgotPinUserId = (String) nvp[i].getValue();
			}

			if (nvp[i].getName().equalsIgnoreCase("TaskId")) {
				taskId = (String) nvp[i].getValue();
			}
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#######SMS Dispatcher ForgotPinUserId:- " + forgotPinUserId);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#######SMS Dispatcher TaskId:- " + taskId);

		String recipientId = alertRequestDTO.getAlertContactPreference().getDispatchAddress();

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##########recipientId ===" + recipientId);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########alertRequestDTO.getUserId() ===" + alertRequestDTO.getUserId());

		String custId = alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###########Customer ID in SMS Dispatcher: " + custId);

		String countryCode = "";
		CountryCode domain = new CountryCode();
		String onScreenUserId = "";

		String eventIds = CustomConfigUtil.readConfigValue("SMS_DISPATCHER_ALERT_EVENTID_LIST",
				"OAC_REVOKE_SUCCESS,OAC_GRANT_SUCCESS,OAC_REFRESH_SUCCESS,PIN_ACTIVATION_SUCCESS,INWARD_REMITTANCE_ALERT_SUCCESS");

		String[] eventList = eventIds.split(",");
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###################################"
				+ alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########### SMS Template Id: "
				+ alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getMessageTemplateId());
		for (String alertEventId : eventList) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########### SMS Event IDs: - " + alertEventId);
			if (alertEventId.equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Entered EventList If loop");
				// changes done for open API alerts
				/*
				 * if ("OAC_REVOKE_SUCCESS"
				 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
				 * getEventId()) || "OAC_GRANT_SUCCESS"
				 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
				 * getEventId()) || "OAC_REFRESH_SUCCESS"
				 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
				 * getEventId())) {
				 */
				String userMobile = null;

				NameValuePair[] dispatchData = data.getDispatchData();
				String userId = "";
				String fxTemplateLocale = "";
				String fxTemplateId = alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getMessageTemplateId();

				for (int i = 0; i < dispatchData.length; i++) {
                    com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################### UserId in SMS getGenericName == " + dispatchData[i].getGenericName());
					if ("UserId".equalsIgnoreCase(dispatchData[i].getGenericName())) {
						userId = (String) dispatchData[i].getValue();
                        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################### UserId in SMS userId == " + userId);
						selectedEventsUserId = userId;
						break;
					}
				}

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################### UserId in SMS Dispatcher == " + userId);

				// read user email id
				UserResponseDTO user = null;
				try {
					com.ofss.digx.app.adapter.IAdapterFactory userAdapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
							.getInstance().getAdapterFactory(CommonAdapterFactoryConstants.USER_ME_ADAPTER_FACTORY);
					IUserMeAdapter userMeAdapter = (IUserMeAdapter) userAdapterFactory
							.getAdapter(CommonAdapterConstants.USER_ME_ADAPTER);
					if (getSessionContext() != null && (userId != null && !userId.equalsIgnoreCase("")
							&& !"SYSTELLER".equalsIgnoreCase(userId) && !"FRXMDB".equalsIgnoreCase(userId))) {
						SessionContext sessionContext = getSessionContext();
						user = userMeAdapter.readUser(sessionContext, userId);
					}
				} catch (com.ofss.digx.infra.exceptions.Exception e) {
					// TODO Auto-generated catch block
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				}
				
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################Before TD_MODE1_NOTIFICATION SMS recipientId loop");
		
				// Taking recipientId from alertRequestDTO for
				// TD_MODE1_NOTIFICATION alert since SMS is required to be sent
				if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
						.equalsIgnoreCase("TD_MODE1_NOTIFICATION_SMS_EN")
						|| alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
								.equalsIgnoreCase("TD_MODE1_NOTIFICATION_SMS_TC")
						|| alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
								.equalsIgnoreCase("TD_MODE1_NOTIFICATION_SMS_SC")
						|| alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
								.equalsIgnoreCase("TD_MODE1_NOTIFICATION")) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################Entered TD_MODE1_NOTIFICATION SMS recipientId loop");
					recipientId = alertRequestDTO.getAlertContactPreference().getDispatchAddress();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"#################TD_MODE1_NOTIFICATION SMS recipientID:- " + recipientId);

				}
				if (user != null && user.getUserDTO() != null) {
					if (null != user.getUserDTO().getEmailId() && !user.getUserDTO().getEmailId().isEmpty()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"################ user email id in SMS Dispatcher: " + user.getUserDTO().getEmailId());
					}
					if (null != user.getUserDTO().getMobileNumber() && !user.getUserDTO().getMobileNumber().isEmpty()) {
						userMobile = user.getUserDTO().getMobileNumber();
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("################### User Mobile No: " + userMobile);
					}
				}
				if (userMobile != null) {
					recipientId = userMobile;
				}
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########### open api recipientId" + recipientId);

				// Taking recipientId from alertRequestDTO for
				// USER_MOBILE_NUMBER_UPDATED_REMINDER alert since SMS is required to be sent
				// user's old mobileNumber
				if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
						.equalsIgnoreCase("USER_MOBILE_NUMBER_UPDATED_REMINDER")) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut
							.println("##################Entered USER_MOBILE_NUMBER_UPDATED_REMINDER recipientId loop");
					recipientId = alertRequestDTO.getAlertContactPreference().getDispatchAddress();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"#################USER_MOBILE_NUMBER_UPDATED_REMINDER recipientID:- " + recipientId);

				}
			}
		}

		if (null != alertRequestDTO.getUserId()) {
			onScreenUserId = alertRequestDTO.getUserId();
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########### open api onScreenUserId=" + alertRequestDTO.getUserId());
		}

		// FOR SPECIAL EVENT CASES
		if (!selectedEventsUserId.equalsIgnoreCase("")) {
			onScreenUserId = selectedEventsUserId;
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("########### open api onScreenUserId=" + selectedEventsUserId);
		}

		// For Forgot Login Pin OTP case
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String forgotPinTaskIds = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "FORGOT_PIN_OTP_TASK_ID_LIST",
				"CM_N_FPN,CM_N_FP");
		if (!forgotPinUserId.equalsIgnoreCase("") && forgotPinTaskIds.contains(taskId)) {
			onScreenUserId = forgotPinUserId;
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##Forgot pin case:-");
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("***** S M S ONSCREEN USER_ID IS : " + alertRequestDTO.getUserId());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("######TaskCode:- " + (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK));

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("***** S M S onScreenUserId final value: " + onScreenUserId);

		CountryCode dmn = new CountryCode();
		CountryCodeKey dmnKey = new CountryCodeKey();
		SessionContext sessionContext1;
		try {
			sessionContext1 = getSessionContext();

			if (onScreenUserId.equalsIgnoreCase("")) {
				dmnKey.setId(sessionContext1.getUserId());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut
						.println("***** S M S ONSCREEN USER_ID FROM SESSION CONTEXT : " + sessionContext1.getUserId());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("selected event id case eventuserid is :" + selectedEventsUserId);
			} else {
				dmnKey.setId(onScreenUserId);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("***** S M S ONSCREEN USER_ID" + onScreenUserId);
				alertRequestDTO.setUserId(onScreenUserId);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"***** Setting alertRequestDTO with ONSCREEN USER_ID IS : " + alertRequestDTO.getUserId());
			}

		} catch (FatalException e1) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("inside exception " + e1);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e1);
		}

		CountryCode dmn1 = new CountryCode();
		try {

			// to skip sms for INWARD_REMITTANCE_ALERT_SUCCESS alert
			String smsCountryCodeSkipEventIds = CustomConfigUtil.readConfigValue(
					"SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST",
					"INWARD_REMITTANCE_ALERT_SUCCESS,TD_MODE1_NOTIFICATION,TD_MODE1_NOTIFICATION_SMS_EN,TD_MODE1_NOTIFICATION_SMS_TC,TD_MODE1_NOTIFICATION_SMS_SC");
			String[] skipEventIdsArray = smsCountryCodeSkipEventIds.split(",");
	        Set<String> skipEventIdsSet = new HashSet<>();
	        for (String eventIdskip : skipEventIdsArray) {
	            skipEventIdsSet.add(eventIdskip.trim().toUpperCase());
	        }

	        String eventIdToCheck = alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId().trim().toUpperCase();

	        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("EventId to skip country code: " + eventIdToCheck);
	        
	        if (!skipEventIdsSet.contains(eventIdToCheck)) {
	            dmn1 = dmn.read(dmnKey);
	        }
		} catch (com.ofss.digx.infra.exceptions.Exception e) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}
		
		if (null != dmn1.getMobile_code()) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Country code from dmn1");
			countryCode = dmn1.getMobile_code();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("S M S DISPATCHER CountryCode is :" + countryCode);
		}
		else {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Country code from ThreadAttribute");
			countryCode = com.ofss.digx.infra.thread.ThreadAttribute.get("NEW_MOB_NO_CODE") != null ? (String) com.ofss.digx.infra.thread.ThreadAttribute.get("NEW_MOB_NO_CODE") : "";
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("countryCode="+countryCode);
			if(defaultUserCountryCode != null) {
				countryCode = defaultUserCountryCode;
			}
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#########post country block ");
		String refNumber = "CDC" + TXnRefNo + ShortString(TxnName);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##############SMS DISPATCHER : recipientId is " + recipientId);
		recipientId = countryCode + recipientId;
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##############SMS DISPATCHER : NEW recipientId is " + recipientId);
		if ("INWARD_REMITTANCE_ALERT_SUCCESS"
				.equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {

			// to skip sms trigger for Inward remittance alert
			recipientId = "";

		}
		List<MNGSmsAlertDTO> request = buildMNGrequest(recipientId, dispatchMessage, refNumber, activityId, TxnName);

		String skipHash = CustomConfigUtil.readConfigValue("MOCK_BRANCH_DATE_SETUP", "N");
		EmailMNG EmailMNG;

		// Below is for production.
		if (null != skipHash && "N".equalsIgnoreCase(skipHash)) {
			EmailMNG = populateDomainObject(recipientId, generateHash(dispatchMessage), "", alertRequestDTO, refNumber,
					activityId, TxnName, actionId, eventId, orgTxnRefNo, onScreenUserId, codActDataId);
		}
		// Below is for non production environments.
		else {
			EmailMNG = populateDomainObject(recipientId, dispatchMessage, "", alertRequestDTO, refNumber, activityId,
					TxnName, actionId, eventId, orgTxnRefNo, onScreenUserId, codActDataId);
		}

		try {
			EmailMNG.create(EmailMNG);
		} catch (com.ofss.digx.infra.exceptions.Exception e1) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e1);
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG before fetchning adaper from extxfaceAdapterFactory");

		IMNGSmsAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IMNGSmsAdapter.class, "createAlert",
				DeterminantType.Enterprise);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG after fetchning adaper from extxfaceAdapterFactory");
		List<MNGSmsAlertDTO> responseDTOList;

		try {

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
					"************************* MNG Name value pair in dispatch data ***********************************************");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG SMS before adapter.createAlert");
			dispatchResult = convertToDispatchResult(adapter.createAlert(request), dispatchMessage);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG SMS after adpater.createAlert,result: " + dispatchResult.getIsDispatchSuccessfull());
			if (dispatchResult.getIsDispatchSuccessfull()) {
				EmailMNG.setResponseStatus("Success");
			} else {
				EmailMNG.setResponseStatus("Failed");
			}
		} catch (Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			this.logger.log(Level.SEVERE, "AddressException from SMSDispatcher.dispatchMNGSms", e);
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setMessage("Failed with error " + e.getMessage());
			dispatchResult.setDispatchMessage(dispatchMessage);
			EmailMNG.setResponseStatus("Exception");
		}

		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("update responseStatus after call BORH: " + EmailMNG.getResponseStatus());
			EmailMNG.update(EmailMNG);
		} catch (com.ofss.digx.infra.exceptions.Exception e1) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e1);
		}

		return dispatchResult;

		// return new DispatchResultDTO();
	}

	public String getCurrentDateAndTime(String format) {
		DateFormat df = new SimpleDateFormat(format);
		String text = df.format(new java.util.Date());
		return text;
	}

	public static String ShortString(String string) {
		char[] chars = string.toCharArray();
		boolean found = false;
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < chars.length; i++) {
			if (!found && Character.isLetter(chars[i])) {
				chars[i] = Character.toUpperCase(chars[i]);
				sb.append(chars[i]);
				found = true;
			} else if (Character.isWhitespace(chars[i]) || chars[i] == '-' || chars[i] == '\'') {
				found = false;
			}
		}
		return sb.toString();
	}

	List<MNGSmsAlertDTO> buildMNGrequest(String recipientId, String messageBody, String RefNumber,
			String activityId, String txnName) {

		List<MNGSmsAlertDTO> request = new ArrayList();

		SMS sms = new SMS();
		MNGSmsMessageBody body = new MNGSmsMessageBody();
		MNGSmsMessageHeader header = new MNGSmsMessageHeader();

		header.setAppID(89L);
		try {
			header.setIpAddress(java.net.InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}
		// header.setIpAddress("127.0.0.0.1");
		header.setMessageMode("o");

		sms.setMessage(messageBody);
		sms.setDistNo(recipientId);

		body.setMessageType(1);
		body.setSms(sms);

		body.setSourceSysRefNumber(RefNumber);
		
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("before txnName "+txnName);
		txnName = txnName.replaceAll("\"", "");
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("after txnName "+txnName);
		
		body.setActivityTag(txnName);

		MNGSmsMessageBody[] mngbodyList = new MNGSmsMessageBody[1];
		mngbodyList[0] = body;

		MNGSmsAlertDTO mngRequestDTO = new MNGSmsAlertDTO();
		mngRequestDTO.setBody(mngbodyList);
		mngRequestDTO.setHeader(header);
		request.add(mngRequestDTO);

		return request;
	}

	private EmailMNG populateDomainObject(String recipientId, String messageBody, String subject,
			AlertRequestDTO alertRequestDTO, String refNumber, String activityId, String TxnName, String actionId,
			String eventId, String orgTxnRefNo, String onScreenUserId, String codActDataId) {
		EmailMNG EmailMNG = new EmailMNG();

		String customerId = "";
		String partyId = "";
		try {
			SessionContext sessionContext = getSessionContext();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getServiceCode() : " + sessionContext.getServiceCode());
			if (onScreenUserId != null && !onScreenUserId.equalsIgnoreCase("")) {
				customerId = onScreenUserId;
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserId from Alert is added:- " + customerId);
			} else {
				customerId = sessionContext.getUserId();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserId from SessionContext :- " + customerId);
			}

			partyId = sessionContext.getTransactingPartyCode();

			if (customerId.equalsIgnoreCase("SYSTELLER") && alertRequestDTO.getUserId() != null
					&& !alertRequestDTO.getUserId().equalsIgnoreCase("")) {
				customerId = alertRequestDTO.getUserId();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("### Setting customerId:- " + customerId);
			}

			if ((partyId == null || partyId.equalsIgnoreCase(""))
					&& !alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId().equalsIgnoreCase("")
					&& alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId() != null) {
				partyId = alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("### Setting partyId:- " + partyId);
			}
			if (("NOT_AVAILABLE".equalsIgnoreCase(partyId)) && customerId != null && !"".equalsIgnoreCase(customerId)
					&& customerId.contains("@")) {
				partyId = customerId.split("@")[1];
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("### Setting partyId from user for SMS :- " + partyId);
			}

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getUserId() : " + sessionContext.getUserId());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getExternalReferenceNo() : " + sessionContext.getExternalReferenceNo());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getInternalReferenceNo() : " + sessionContext.getInternalReferenceNo());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut
					.println("sessionContext.getTransactingPartyCode() : " + sessionContext.getTransactingPartyCode());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.toString() : " + sessionContext.toString());

		} catch (FatalException e) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}

		EmailMNG.setRecipientId(recipientId);
		EmailMNG.setMessageBody(messageBody);
		EmailMNG.setSubject(subject);
		EmailMNG.setCustomerId(customerId);
		EmailMNG.setPartyId(partyId);
		EmailMNG.setActivityId(activityId);
		EmailMNG.setActionId(actionId);
		EmailMNG.setEventId(eventId);
		EmailMNG.setCodActDataId(codActDataId);
		EmailMNG.setTxnType(TxnName);
		EmailMNG.setLastUpdatedDate(new Date(new java.util.Date()));
		EmailMNG.setOrgTxnRefNO(orgTxnRefNo);
		EmailMNG.setAlert_type("SMS");
		EmailMNG.setResponseStatus("Pending");

		EmailMNGKey emk = new EmailMNGKey();

		emk.setRefNumber(refNumber);
		EmailMNG.setKey(emk);

		return EmailMNG;
	}

	private DispatchResultDTO convertToDispatchResult(List<MNGSmsAlertDTO> dtoList, String messageBody) {
		DispatchResultDTO dispatchResult = new DispatchResultDTO();

		MNGSmsAlertDTO mead = dtoList.get(0);
		//BCOCDC-9402 Enhancement on MNG status update change != to ==
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Error code : " + mead.getErrorCode());
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Status code : " + mead.getStatusCode());
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Error msg : " + mead.getErrorMessage());
		if (null == mead.getErrorCode()) {
			dispatchResult.setIsDispatchSuccessfull(true);
			dispatchResult.setMessage("SMS Message sent successfully");
			dispatchResult.setDispatchMessage(messageBody);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG SMS message sent successfully...");
		} else {
			this.logger.log(Level.SEVERE, "AddressException from EmailDispatcher.sendMail");
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setMessage("Failed with error " + mead.getErrorCode());
			dispatchResult.setDispatchMessage(messageBody);
		}

		return dispatchResult;

	}

	/**
	 * (non-Javadoc)
	 * 
	 * @return SessionContext
	 * @throws FatalException
	 * @see com.ofss.fc.app.party.service.contact.IContactPreferenceApplicationService#getSessionContext()
	 */
	public SessionContext getSessionContext() throws FatalException {

		SessionContext sessionContext = null;
		try {
			sessionContext = AdapterContextHelper.getInstance().setContext();
		} finally {
		}
		return sessionContext;
	}

	private String generateHash(String answer) {
		byte[] encodedhash = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			encodedhash = digest.digest(answer.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Failed to generate hash");

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

}
