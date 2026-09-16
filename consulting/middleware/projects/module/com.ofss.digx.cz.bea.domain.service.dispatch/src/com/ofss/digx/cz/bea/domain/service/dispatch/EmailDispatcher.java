/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */

package com.ofss.digx.cz.bea.domain.service.dispatch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.CommandMap;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.MailcapCommandMap;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import com.ofss.digx.app.adapter.impl.AdapterContextHelper;
import com.ofss.digx.app.sms.adapter.user.IUserMeAdapter;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.app.customconfig.util.CustomConfigUtil;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGEmailAlertsDTO;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.datatype.MNGEmailMessageBody;
import com.ofss.digx.cz.bea.datatype.MNGEmailMessageHeader;
import com.ofss.digx.cz.bea.datatype.SendMessageEmail;
import com.ofss.digx.cz.bea.domain.emailmng.EmailMNG;
import com.ofss.digx.cz.bea.domain.emailmng.EmailMNGKey;
import com.ofss.digx.cz.bea.extxface.alert.IMNGEmailAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.infra.crypto.service.SymmetricCryptographyProviderFactory;
import com.ofss.digx.infra.crypto.spi.ICryptographyProvider;
import com.ofss.fc.app.adapter.AdapterFactoryConfigurator;
import com.ofss.fc.app.adapter.ModuleConstant;
import com.ofss.fc.app.adapter.ep.EventProcessingAdapterConstant;
import com.ofss.fc.app.adapter.ep.IDispatchAdapter;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.app.ep.dto.action.DispatchResultDTO;
import com.ofss.fc.app.ep.dto.action.IPreferredRecipient;
import com.ofss.fc.common.Constants;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.fc.domain.ep.entity.dispatch.EmailDispatchDetail;
import com.ofss.fc.domain.ep.service.dispatch.DispatchResult;
import com.ofss.fc.domain.ep.service.dispatch.Dispatcher;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.enumeration.ep.AlertType;
import com.ofss.fc.framework.domain.entity.ep.dto.IDispatchData;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.exception.FatalException;
import com.ofss.fc.infra.io.SafeFileService;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.text.StringHelper;
import com.ofss.fc.utils.SerializationUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * This class is use to dispatch the mail.
 * 
 * @author sudhirma
 */
public class EmailDispatcher extends Dispatcher {

	private static final String EMAIL_MESSAGE_SENT_SUCCESSFULLY = "Email message sent successfully.";
	private static final String DISPATCH_DETAILS = "DispatchDetails";
	private static final String THIS_COMPONENT_NAME = EmailDispatcher.class.getName();
	private static final Logger LOGGER = MultiEntityLogger.getUniqueInstance().getLogger(THIS_COMPONENT_NAME);
	private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();
	private static final String PROPERTY_FILE_PATH_FOR_IMAGES = "PROPERTY_FILE_PATH_FOR_IMAGES";
	private static final String DMS_MANIFEST_FILES_PATH = "/oracle/deployables/sails/DMSManifestFiles/";
	private static final String CONTENT_MANAGER_PROPERTIES = "contentmanager";
	private static final String SAFE_FILE_SERVICE_FLAG = "safefileserviceflag";
	private static final String EMAIL_CONFIGURATION = "EMailConfiguration";
	private static final String FALSE = "false";

	/**
	 * public constructor
	 */
	public EmailDispatcher() {

	}

	/**
	 * EmailDispatcher constructor.
	 * 
	 * @param dispatchDetail
	 * @throws FatalException
	 */
	public EmailDispatcher(EmailDispatchDetail dispatchDetail) throws FatalException {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered the constructor EmailDispatcher. "));
		}
		this.dispatchDetail = dispatchDetail;
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("SmtpServer is '%s'.", dispatchDetail.getSmtpServer()));
		}
	}

	@Override
	public EmailDispatchDetail getDispatchDetail() {

		return (EmailDispatchDetail) dispatchDetail;
	}

	/**
	 * This method is use to send the message to the recipient.
	 * 
	 * @see #getDispatchDetail
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * @param recipientId
	 * @param messageBody
	 * @param attachments
	 * @param subject
	 * @return DispatchResult
	 * 
	 */
	public DispatchResult sendMail(String recipientId, String messageBody, String[] attachments, String subject) {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered the method sendMail"));
		}
		DispatchResult dispatchResult = new DispatchResult();
		Transport transport = null;
		try {
			Session session;
			String messageFrom = getDispatchDetail().getMessageFrom();
			String smtpServer = getDispatchDetail().getSmtpServer();
			boolean isSmtpAuthRequired = getDispatchDetail().isSmtpAuthRequired();
			boolean isSmtpSSLEnable = getDispatchDetail().isSmtpSSLEnable();
			int smtpPort = getDispatchDetail().getSmtpPort();
			final String userLogin = getDispatchDetail().getUserLogin();
			final String userPassword = getDispatchDetail().getUserPassword();
			String mailProtocol = ConfigurationFactory.getInstance().getConfigurations(EMAIL_CONFIGURATION)
					.get("MailProtocol", "smtp");
			String isTlsEnabled = ConfigurationFactory.getInstance().getConfigurations(EMAIL_CONFIGURATION)
					.get("TLSEnable", FALSE);
			boolean isTlsRequired = ConfigurationFactory.getInstance().getConfigurations(EMAIL_CONFIGURATION)
					.getBoolean("TLSRequired", false);

			// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#####################" + messageBody);
			if (!(attachments != null && attachments.length > 0)) {
				if ((messageBody != null) && (messageBody.contains("img"))) {
					List<String> attachmentList = new ArrayList<String>();
					messageBody = getAllFileNames(messageBody, attachmentList);
					attachments = new String[attachmentList.size()];
					attachmentList.toArray(attachments);
				}

				if ((messageBody != null) && (messageBody.contains("attchmnt"))) {
					List<String> attachmentList = new ArrayList<String>();
					attachments = getAllAttachments(messageBody, attachmentList);

				}

				String rptName = CustomConfigUtil.readConfigValue("TT_REPORT_PATH", "/project/CDC/ftp/rcv/processed/");
				rptName = rptName + "tt_descrpancy_report.xlsx";

				attachments = new String[] { rptName };

			}
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before setting a mail server. Message body is :" + messageBody);
			}
			// Setup mail server
			Properties props = System.getProperties();
			props.put("mail.smtp.host", smtpServer);
			props.put("mail.smtp.port", smtpPort);
			props.put("mail.smtp.auth", isSmtpAuthRequired);
			if (isTlsRequired) {
				props.put("mail.smtp.starttls.enable", isTlsEnabled);
			} else {
				props.put("mail.smtp.ssl.enable", isSmtpSSLEnable);
			}
			props.setProperty("mail.transport.protocol", mailProtocol);
			props.put("mail.smtp.connectiontimeout", String.valueOf(getDispatchDetail().getSmtpConnectionTimeout()));
			props.put("mail.smtp.timeout", String.valueOf(getDispatchDetail().getSmtpTimeout()));

			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before getting a mail sesion");
			}

			if (userPassword != null && !userPassword.isEmpty()) {
				ICryptographyProvider cryptoProvider = SymmetricCryptographyProviderFactory.getInstance()
						.getLatestProvider();

				String decryptedUserPassword = cryptoProvider.decrypt(userPassword, null);
				session = Session.getInstance(props, new javax.mail.Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(userLogin, decryptedUserPassword);
					}
				});
			} else {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, "userpassword not maintained.");
				}
				session = Session.getInstance(props, new javax.mail.Authenticator() {

					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(userLogin, userPassword);
					}
				});
			}

			// Define a new mail message
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before defining a new mail message");
			}

			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(messageFrom));
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, FORMATTER.formatMessage("After setting messageFrom which is '%s'", messageFrom));
			}
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipientId));
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE,
						FORMATTER.formatMessage("Before setting subject of message which is '%s'", subject));
			}
			message.setSubject(subject, "UTF-8");
			// Create a message part to represent the body text
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before Creating a message part to represent the body text");
			}
			CommandMap.setDefaultCommandMap(new MailcapCommandMap());
			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(messageBody, "UTF-8");
			messageBodyPart.setHeader("Content-Type", "text/html;charset=UTF-8");
			// use a MimeMultipart as we need to handle the file attachments
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before using a MimeMultipart to handle the file attachments ");
			}
			Multipart multipart = new MimeMultipart("related");
			// add the message body to the mime message
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before adding the message body to the mime message ");
			}
			multipart.addBodyPart(messageBodyPart);
			// add any file attachments to the message
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before adding adding attachments to the message ");
			}

			addAtachments(attachments, multipart);
			// Put all message parts in the message
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before putting message parts in the message ");
			}
			message.setContent(multipart);
			// Send the message
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Before sending the message ");
			}
			Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
			String isDispatchMocked = patternPreference.get("isDispatchMocked", FALSE);
			if (FALSE.equalsIgnoreCase(isDispatchMocked)) {
				transport = session.getTransport();
				transport.connect();
				message.saveChanges();
				transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
			} else {
				Address[] recipients = message.getRecipients(Message.RecipientType.TO);
				if (recipients != null) {
					for (Address recipientTo : recipients) {
						((InternetAddress) recipientTo)
								.setAddress(patternPreference.get("email.message.to.mocked", Constants.EMPTY_STRING));
					}
				}
				message.setRecipients(Message.RecipientType.TO, recipients);
				Transport.send(message);
			}
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, FORMATTER.formatMessage("After sending the message "));
			}
			dispatchResult.setIsDispatchSuccessfull(true);
			dispatchResult.setMessage(EMAIL_MESSAGE_SENT_SUCCESSFULLY);
			dispatchResult.setDispatchMessage(messageBody);
		} catch (

		Throwable e) {
			LOGGER.log(Level.SEVERE, "AddressException from EmailDispatcher.sendMail", e);
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setMessage("Failed with error " + e.getMessage());
			dispatchResult.setDispatchMessage(messageBody);
		} finally {
			if (transport != null && transport.isConnected()) {
				try {
					transport.close();
				} catch (MessagingException e) {
					LOGGER.log(Level.SEVERE, "AddressException from EmailDispatcher.sendMail", e);
					dispatchResult.setMessage("Exception occurred in transport.close(); while closing the transport. "
							+ dispatchResult.getMessage());
				}
			}
		}
		return dispatchResult;
	}

	/**
	 * This method is use to send the message to the recipient.
	 * 
	 * @see #getDispatchDetail
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * @param recipientId
	 * @param messageBody
	 * @param attachments
	 * @param subject
	 * @return DispatchResult
	 * 
	 */
	public DispatchResult sendMNGMail(String recipientId, String messageBody, String[] attachments, String subject,
			AlertRequestDTO alertRequestDTO, IDispatchData data, String mngUserid, String mngPartyId) {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered the method sendMNGMail"));
		}
		DispatchResult dispatchResult = new DispatchResult();

		NameValuePair[] nvp = data.getDispatchData();
		int min = 0;
		int max = 999;
		String TxnName = "";
		Random rd = new Random(); // creating Random object
		//com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println();
		String TXnRefNo = getCurrentDateAndTime("yyMMddhhmmssSSS") + rd.nextInt(max - min) + min;
		String orgTxnRefNo = "";
		System.out.println("before+++++++++++++++++++"+ SerializationUtils.toJsonString(data.getDispatchData()));
		System.out.println("===== STACK TRACE START =====");

		for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			System.out.println(ste);
		}

		System.out.println("===== STACK TRACE END =====");
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG Fetching alertRequestDTO.getEventAction().getKeyDTO().getActivityId();");
		String activityId = alertRequestDTO.getEventAction().getKeyDTO().getActivityId();
		String actionId = alertRequestDTO.getEventAction().getKeyDTO().getActionId();
		String eventId = alertRequestDTO.getEventAction().getKeyDTO().getEventId();
		String codActDataId = alertRequestDTO.getCodActDataId();
		// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG actionId= "+activityId);

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
		}

		String refNumber = "CDC" + TXnRefNo + ShortString(TxnName);

		String userId = "";
		StringBuffer modifiedSub = new StringBuffer();

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##EventID:- " + eventId);
		eventId = alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##EventID from RecipientMessageTemplate :- " + eventId + " : mngUserid- " + mngUserid);

		if (eventId.equalsIgnoreCase("INFO_UPDATE_BY_CORP_ADMIN")) {
			if (subject != null && subject.contains("~")) {
				userId = subject.split("~")[1];

				modifiedSub.append(subject.split("~")[0]).append(subject.split("~")[2]);
				subject = modifiedSub.toString();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After modifying subject in email dispatcher:- " + subject);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After modifying userId in email dispatcher:- " + userId);
			}
		}

		String modifyUserEventIds = CustomConfigUtil.readConfigValue("EMAIL_DISPATCHER_MODIFY_MNG_USER_EVENTID_LIST",
				"");
		String[] modifyUserEventList = modifyUserEventIds.split(",");
		for (String modifyUserEvent : modifyUserEventList) {
			if (eventId.equalsIgnoreCase(modifyUserEvent)) {
				if (mngUserid != null && !mngUserid.equalsIgnoreCase("")) {
					userId = mngUserid;

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"After modifying userId in email dispatcher for:- " + eventId + " : UserId:- " + userId);
				}
			}
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("mngPartyId:- " + mngPartyId);
		if (mngPartyId != null && !"".equalsIgnoreCase(mngPartyId)) {

			alertRequestDTO.getPreferredRecipient().getContactDetails().setPartyId(mngPartyId);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("mngPartyId is not null and is set: "
					+ alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId());

		}

		messageBody = messageBody.replaceAll("\"", "");
		TxnName = TxnName.replaceAll("\"", "");

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Updated TxnName:- " + TxnName);
		List<MNGEmailAlertsDTO> request = buildMNGrequest(recipientId, messageBody, subject, refNumber, activityId,
				TxnName);

		String skipHash = CustomConfigUtil.readConfigValue("MOCK_BRANCH_DATE_SETUP", "N");
		EmailMNG emailMNG;
		//BCOCDC-4728
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##EventID from RecipientMessageTemplate in sendMNGMail :- " + eventId + " : userId- " + userId);

		// Below is for production
		if (null != skipHash && "N".equalsIgnoreCase(skipHash)) {
			emailMNG = populateDomainObject(recipientId, generateHash(messageBody), subject, alertRequestDTO, refNumber,
					activityId, TxnName, actionId, eventId, orgTxnRefNo, userId, codActDataId);
		}

		// Below is for non production environments.
		else {
			emailMNG = populateDomainObject(recipientId, messageBody, subject, alertRequestDTO, refNumber, activityId,
					TxnName, actionId, eventId, orgTxnRefNo, userId, codActDataId);
		}
		try {
			emailMNG.create(emailMNG);
		} catch (com.ofss.digx.infra.exceptions.Exception e1) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e1);
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG before fetchning adaper from extxfaceAdapterFactory");

		IMNGEmailAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IMNGEmailAdapter.class,
				"createAlert", DeterminantType.Enterprise);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG after fetchning adaper from extxfaceAdapterFactory");
		List<MNGEmailAlertsDTO> responseDTOList;

		try {

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
					"************************* MNG Name value pair in dispatch data ***********************************************");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG before adapter.createAlert");
			// ------
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Additional Sysout:- " + request.toString() + " : messagebody:-" + messageBody);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Additional Sysout:- " + subject);
			dispatchResult = convertToDispatchResult(adapter.createAlert(request), messageBody);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG after adpater.createAlert,result: " + dispatchResult.getIsDispatchSuccessfull());
			if (dispatchResult.getIsDispatchSuccessfull()) {
				emailMNG.setResponseStatus("Success");
			} else {
				emailMNG.setResponseStatus("Failed");
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "AddressException from EmailDispatcher.sendMail", e);
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setMessage("Failed with error " + e.getMessage());
			dispatchResult.setDispatchMessage(messageBody);
			emailMNG.setResponseStatus("Exception");
		}

		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("update responseStatus after call BORH: " + emailMNG.getResponseStatus());
			emailMNG.update(emailMNG);
		} catch (com.ofss.digx.infra.exceptions.Exception e1) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e1);
		}

		return dispatchResult;
	}

	private DispatchResult convertToDispatchResult(List<MNGEmailAlertsDTO> dtoList, String messageBody) {
		DispatchResult dispatchResult = new DispatchResult();

		MNGEmailAlertsDTO mead = dtoList.get(0);
		//BCOCDC-9402 Enhancement on MNG status update change != to ==
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Error code : " + mead.getErrorCode());
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Status code : " + mead.getStatusCode());
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Error msg : " + mead.getErrorMessage());
		if (null == mead.getErrorCode()) {
			dispatchResult.setIsDispatchSuccessfull(true);
			dispatchResult.setMessage(EMAIL_MESSAGE_SENT_SUCCESSFULLY);
			dispatchResult.setDispatchMessage(messageBody);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG Email message sent successfully...");
		} else {
			LOGGER.log(Level.SEVERE, "AddressException from EmailDispatcher.sendMail");
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setMessage("Failed with error " + mead.getErrorCode());
			dispatchResult.setDispatchMessage(messageBody);
		}

		return dispatchResult;

	}

	private List<MNGEmailAlertsDTO> buildMNGrequest(String recipientId, String messageBody, String subject,
			String RefNumber, String activityId, String txnName) {

		List<MNGEmailAlertsDTO> request = new ArrayList();

		SendMessageEmail sme = new SendMessageEmail();
		MNGEmailMessageBody body = new MNGEmailMessageBody();
		MNGEmailMessageHeader header = new MNGEmailMessageHeader();

		header.setAppID(89L);
		try {
			header.setIpAddress(java.net.InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}
		// header.setIpAddress("127.0.0.0.1");
		header.setMessageMode("o");

		sme.setToAddr(recipientId);
		sme.setBccAddr("");
		sme.setCcAddr("");
		sme.setMessage(messageBody);
		sme.setSubject(subject);

		body.setMessageType(6);
		body.setEmail(sme);

		body.setSourceSysRefNumber(RefNumber);
		body.setActivityTag(txnName);

		MNGEmailMessageBody[] mngbodyList = new MNGEmailMessageBody[1];
		mngbodyList[0] = body;

		MNGEmailAlertsDTO mngRequestDTO = new MNGEmailAlertsDTO();
		mngRequestDTO.setBody(mngbodyList);
		mngRequestDTO.setHeader(header);

		request.add(mngRequestDTO);

		return request;
	}

	private EmailMNG populateDomainObject(String recipientId, String messageBody, String subject,
			AlertRequestDTO alertRequestDTO, String refNumber, String activityId, String TxnName, String actionId,
			String eventId, String orgTxnRefNo, String userId, String codActDataId) {
		EmailMNG emailMNG = new EmailMNG();
		//bcocdc4728
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("alertRequestDTO in populateDomainObject: " + alertRequestDTO);
		String customerId = "";
		String partyId = "";
		try {
			SessionContext sessionContext = getSessionContext();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getServiceCode() : " + sessionContext.getServiceCode());

			if (userId != null && !userId.equalsIgnoreCase("")) {
				customerId = userId;
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserId from Alert is added:- " + customerId);
			} else {
				customerId = sessionContext.getUserId();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserId from SessionContext :- " + customerId);
			}

			partyId = sessionContext.getTransactingPartyCode();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getUserId() : " + sessionContext.getUserId());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getExternalReferenceNo() : " + sessionContext.getExternalReferenceNo());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.getInternalReferenceNo() : " + sessionContext.getInternalReferenceNo());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut
					.println("sessionContext.getTransactingPartyCode() : " + sessionContext.getTransactingPartyCode());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("sessionContext.toString() : " + sessionContext.toString());

			if ((partyId == null || partyId.equalsIgnoreCase(""))
					&& !alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId().equalsIgnoreCase("")
					&& alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId() != null) {
				partyId = alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("### Setting partyId:- " + partyId);
			}

			if (("NOT_AVAILABLE".equalsIgnoreCase(partyId)) && customerId != null && !"".equalsIgnoreCase(customerId)
					&& customerId.contains("@")) {
				partyId = customerId.split("@")[1];
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("### Setting partyId from user :- " + partyId);
			}

		} catch (FatalException e) {
			// TODO Auto-generated catch block
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}

		emailMNG.setRecipientId(recipientId);
		emailMNG.setMessageBody(messageBody);
		emailMNG.setSubject(subject);
		emailMNG.setCustomerId(customerId);
		emailMNG.setPartyId(partyId);
		emailMNG.setActivityId(activityId);
		emailMNG.setActionId(actionId);
		emailMNG.setEventId(eventId);
		emailMNG.setCodActDataId(codActDataId);
		emailMNG.setTxnType(TxnName);
		emailMNG.setLastUpdatedDate(new Date(new java.util.Date()));
		emailMNG.setOrgTxnRefNO(orgTxnRefNo);
		emailMNG.setAlert_type("EMAIL");
		emailMNG.setResponseStatus("Pending");

		EmailMNGKey emk = new EmailMNGKey();

		emk.setRefNumber(refNumber);
		emailMNG.setKey(emk);

		return emailMNG;
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

	/**
	 * This method is use to preview the template define with the images. Exception
	 * cases. 1. If folder does not exist and safe file service flag is false then
	 * IO exception will Occurred which then just suppress and all the images
	 * removed from the mail. 2. If any error occurred while fetching data IPM will
	 * show the template with error message. File is formed as HTMl and images
	 * included are fetched from the IPM server.
	 * 
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * @param facesContext
	 * @param outputStream
	 * 
	 */
	private String getAllFileNames(String messageBody, List<String> attachments) {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, "Entered in getAllFileNames with body : " + messageBody);
		}
		String messageBodyCopy = messageBody;
		if (messageBody != null) {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "messageBody for define template is" + messageBody);
			}
			InputStream is = null;
			try {
				messageBody = messageBody.replaceAll("amp;", Constants.EMPTY_STRING);
				if (messageBody.contains("img")) {
					HTMLEditorKit kit = new HTMLEditorKit();
					HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
					doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
					is = new ByteArrayInputStream(messageBodyCopy.getBytes("UTF-8"));
					Reader HTMLReader = new InputStreamReader(is);
					kit.read(HTMLReader, doc, 0);
					// Get an iterator for all HTML tags.
					ElementIterator it = new ElementIterator(doc);
					Element elem;
					while (it.next() != null) {
						elem = it.current();
						if (elem.getName().equalsIgnoreCase("img")) {

							String src = (String) elem.getAttributes().getAttribute(HTML.Attribute.SRC);
							if (src != null) {
								String fileName = setImages(attachments, src);
								if (fileName != null) {
									messageBody = messageBody.replace(src, "cid:" + fileName);
								}
							}
						}
					}
				}
			} catch (IOException e) {

				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							FORMATTER.formatMessage("IO Exception occured while processing file creation " + e), e);
					LOGGER.log(Level.FINE, FORMATTER.formatMessage(
							"File creation error--->Folder does not exist or write rights not given to folder" + e), e);
				}
				messageBody = messageBody.replaceAll("\\<.*?\\>", Constants.EMPTY_STRING);
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							FORMATTER.formatMessage(
									".................Skipping images and creating plain text mail.............. " + e),
							e);
				}
			} catch (BadLocationException e) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, FORMATTER.formatMessage("BadLocationException occurred " + e), e);
				}
				messageBody = messageBody.replaceAll("\\<.*?\\>", Constants.EMPTY_STRING);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						if (LOGGER.isLoggable(Level.SEVERE)) {
							LOGGER.log(Level.SEVERE,
									FORMATTER.formatMessage("IO Exception occured while closing the input stream" + e),
									e);
						}
					}
				}
			}
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "After processing message body " + messageBody);
				LOGGER.log(Level.FINE, "Exiting in to preview template");
			}
		} else {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Null Message found");
			}
		}
		return messageBody;
	}

	/**
	 * This method is use to preview the template define with the images. Exception
	 * cases. 1. If folder does not exist and safe file service flag is false then
	 * IO exception will Occurred which then just suppress and all the images
	 * removed from the mail. 2. If any error occurred while fetching data IPM will
	 * show the template with error message. File is formed as HTMl and images
	 * included are fetched from the IPM server.
	 * 
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * @param facesContext
	 * @param outputStream
	 * 
	 */
	private String[] getAllAttachments(String messageBody, List<String> attachments) {

		String[] fileNames = null;
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, "Entered in getAllFileNames with body : " + messageBody);
		}
		String messageBodyCopy = messageBody;
		if (messageBody != null) {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "messageBody for define template is" + messageBody);
			}
			InputStream is = null;
			try {
				messageBody = messageBody.replaceAll("amp;", Constants.EMPTY_STRING);
				if (messageBody.contains("attchmnt")) {
					HTMLEditorKit kit = new HTMLEditorKit();
					HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
					doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
					is = new ByteArrayInputStream(messageBodyCopy.getBytes("UTF-8"));
					Reader HTMLReader = new InputStreamReader(is);
					kit.read(HTMLReader, doc, 0);
					// Get an iterator for all HTML tags.
					ElementIterator it = new ElementIterator(doc);
					Element elem;
					while (it.next() != null) {
						elem = it.current();
						if (elem.getName().equalsIgnoreCase("attchmnt")) {

							String src = (String) elem.getAttributes().getAttribute(HTML.Attribute.SRC);
							if (src != null) {
								fileNames = src.split("#");
							}

							doc.removeElement(elem);
						}
					}
				}
			} catch (IOException e) {

				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							FORMATTER.formatMessage("IO Exception occured while processing file creation " + e), e);
					LOGGER.log(Level.FINE, FORMATTER.formatMessage(
							"File creation error--->Folder does not exist or write rights not given to folder" + e), e);
				}
				messageBody = messageBody.replaceAll("\\<.*?\\>", Constants.EMPTY_STRING);
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							FORMATTER.formatMessage(
									".................Skipping images and creating plain text mail.............. " + e),
							e);
				}
			} catch (BadLocationException e) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, FORMATTER.formatMessage("BadLocationException occurred " + e), e);
				}
				messageBody = messageBody.replaceAll("\\<.*?\\>", Constants.EMPTY_STRING);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						if (LOGGER.isLoggable(Level.SEVERE)) {
							LOGGER.log(Level.SEVERE,
									FORMATTER.formatMessage("IO Exception occured while closing the input stream" + e),
									e);
						}
					}
				}
			}
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "After processing message body " + messageBody);
				LOGGER.log(Level.FINE, "Exiting in to preview template");
			}
		} else {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "Null Message found");
			}
		}
		return fileNames;
	}

	/**
	 * This method is used to set the images name list with so that it is useful on
	 * later stage for attaching as file data source.
	 * 
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * @param attachments
	 * @param src
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * 
	 */
	private String setImages(List<String> attachments, String src) throws MalformedURLException, IOException {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, "Starting of setImages");
		}
		String fileName = null;
		if (src != null) {
			int slashLastindex = src.lastIndexOf(Constants.EQUAL);
			if (slashLastindex > -1) {
				fileName = src.substring(slashLastindex + 1);
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, "FileName found =>" + fileName);
				}
				File imgFile = null;
				Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
				String filePath = patternPreference.get(PROPERTY_FILE_PATH_FOR_IMAGES, DMS_MANIFEST_FILES_PATH);
				String safeFileServiceEnable = ConfigurationFactory.getInstance()
						.getConfigurations(CONTENT_MANAGER_PROPERTIES).get(SAFE_FILE_SERVICE_FLAG, "false");
				if ((safeFileServiceEnable != null) && (Boolean.valueOf(safeFileServiceEnable))) {
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, "Inside Safe file service flag");
					}
					File runAreaFolder = SafeFileService.getInstance().getCreatedIORunAreaPath(filePath);
					filePath = runAreaFolder.getPath() + File.separator + fileName;
					imgFile = new File(filePath);
					/**
					 * This is temporary sever logging for checking file path.
					 */
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE,
								"Safe file service flag not enable taking path from root.  please check this path is exist"
										+ filePath);
					}
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, "File deleted successfully");
					}
				} else {
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE,
								"Safe file service flag not enable taking path from root.  please check this path is exist"
										+ filePath);
					}
					imgFile = new File(filePath + fileName);
				}
				if (!imgFile.isFile()) {
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, "Imgae not already exist.reading from url");
					}
				}
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							"taking absolute path in attachment of file------->" + imgFile.getAbsolutePath());
				}
				attachments.add(imgFile.getAbsolutePath());
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, "Exisitng of setImages");
				}
			}
		}
		return fileName;
	}

	/**
	 * This method is use to add attachments to the email.
	 * 
	 * @param attachments
	 * @param multipart
	 * @throws MessagingException
	 * @throws AddressException
	 * 
	 */
	private void addAtachments(String[] attachments, Multipart multipart) throws MessagingException, AddressException {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered the method addAtachments. "));
		}
		if (attachments != null) {
			for (int i = 0; i <= attachments.length - 1; i++) {
				String filename = attachments[i];
				if (filename != null) {
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, FORMATTER.formatMessage("Before creating a MimeBodyPart "));
					}
					int lastIndexOfSlash = filename.lastIndexOf(System.getProperty("file.separator"));
					String filecode = filename.substring(lastIndexOfSlash + 1);
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE,
								FORMATTER.formatMessage("Name of the file after spllitting from . ", filecode));
					}
					MimeBodyPart attachmentBodyPart = new MimeBodyPart();
					// use a JAF FileDataSource as it does MIME type detection
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, FORMATTER.formatMessage("Before creating a FileDataSource "));
					}
					DataSource source = new FileDataSource(filename);
					attachmentBodyPart.setDataHandler(new DataHandler(source));
					// actual file name - could alter this to remove the file path
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE,
								FORMATTER.formatMessage("Before setting the file name.Filename is '%s' ", filename));
					}
					attachmentBodyPart.setFileName(filecode);
					attachmentBodyPart.setHeader("Content-ID", "<" + filecode + ">");
					// add the attachment
					if (LOGGER.isLoggable(Level.FINE)) {
						LOGGER.log(Level.FINE, FORMATTER.formatMessage("Before adding the attachment. "));
					}
					multipart.addBodyPart(attachmentBodyPart);
				}
			}
		}
	}

	/**
	 * This method is use to check whether message is secure
	 * 
	 * @param dispatchMessage
	 * @return is secured.
	 * 
	 */
	private boolean isSecureMessage(String dispatchMessage) {

		boolean isSecure = true;
		String patterns = null;
		String[] tokens = null;
		Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
		String separator = patternPreference.get("email.pattern.separator", Constants.EMPTY_STRING);
		if (separator != null && !Constants.EMPTY_STRING.equals(separator)) {
			patterns = patternPreference.get("email.invalid.patterns", Constants.EMPTY_STRING);
			if (patterns != null && !Constants.EMPTY_STRING.equals(patterns)) {
				tokens = patterns.split(separator);
			}
		}
		if (tokens != null && tokens.length > 0) {
			for (String token : tokens) {
				Pattern pattern = Pattern.compile(token);
				Matcher matcher = pattern.matcher(dispatchMessage);
				if (matcher.find()) {
					LOGGER.log(Level.FINE, FORMATTER.formatMessage(
							"invalid text found \"'%s'\" which matches the pattern \"'%s'\" ", matcher.group(), token));
					isSecure = false;
				}
			}
		}
		return isSecure;
	}

	/**
	 * @see IDispatchAdapter#dispatchEmail
	 * @see #getDispatchDetail
	 * @see #sendMail
	 * @see SafeFileService#getCreatedIORunAreaPath
	 * 
	 */
	@Override
	public DispatchResult dispatchAlert(AlertRequestDTO alertRequestDTO, IDispatchData data) throws FatalException {
		//BCOCDC4728
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("EmailDispatcher dispatchAlert alertRequestDTO===" + alertRequestDTO);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered the method EmailDispatcher.dispatchAlert"));
		}
		//BCOCDC4728
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("EmailDispatcher dispatchAlert alertRequestDTO===" + alertRequestDTO);
		if(null != alertRequestDTO) {
			safeDebugPrint("alertRequestDTO", alertRequestDTO);
			safeDebugPrint("getActivityLog", alertRequestDTO.getActivityLog());
			safeDebugPrint("getDispatchData", alertRequestDTO.getDispatchData());
			safeDebugPrint("getPreferredRecipient", alertRequestDTO.getPreferredRecipient());
			safeDebugPrint("getRecipientMessageTemplate", alertRequestDTO.getRecipientMessageTemplate());
			safeDebugPrint("RecipientMessageTemplateDTO", alertRequestDTO.getRecipientMessageTemplate().getKeyDTO());
			safeDebugPrint("getEventAction", alertRequestDTO.getEventAction());
			safeDebugPrint("getAlertContactPreference", alertRequestDTO.getAlertContactPreference());
			safeDebugPrint("getUserId", alertRequestDTO.getUserId());
			safeDebugPrint("IDispatchData", data);
		}
		
		AlertType alertType = null;
		if (alertRequestDTO.getEventAction() == null) {
			alertType = AlertType.MANDATORY;
		}
		String from = null;
		IPreferredRecipient recipient = alertRequestDTO.getPreferredRecipient();
		Preferences patternPreference = ConfigurationFactory.getInstance().getConfigurations(DISPATCH_DETAILS);
		safeDebugPrint("dispatchdetails",patternPreference);
		if (StringHelper.isNotNullCheckSpace(alertRequestDTO.getFromAddress())) {
			from = alertRequestDTO.getFromAddress();
		} else {
			if ((alertType != null) && (alertType.equals(AlertType.MANDATORY))) {
				from = patternPreference.get("email.message.from.notification",
						"bank_notification.obp.r2.2@oracle.com");
				getDispatchDetail().setMessageFrom(from);
			} else {
				from = patternPreference.get("email.message.from", "obp.r2.2@oracle.com");
				getDispatchDetail().setMessageFrom(from);
			}
		}
		getDispatchDetail().setMessageFrom(from);
		DispatchResult dispatchResult = null;
		String[] filenames = new String[0];
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Before setting templateMessage"));
		}
		String templateMessage = fetchDispatchMessageTemplate(data.getDispatchData());
		String messageSubject = fetchDispatchMessageSubject(data.getDispatchData());

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##messageSubject:- " + messageSubject);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("After setting templateMessage as '%s'", templateMessage));
		}
		String dispatchMessage = data.fetchFormattedData(templateMessage);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("After setting dispatchMessage as '%s'", dispatchMessage));
		}
		if (StringHelper.isNotNull(messageSubject)) {
			messageSubject = data.fetchFormattedData(messageSubject);
		} else {
			messageSubject = getDispatchDetail().getMessageSubject();
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##messageSubject 2:- " + messageSubject);
		safeDebugPrint("templateMessage", templateMessage);
		safeDebugPrint("dispatchMessage",dispatchMessage);
		if (isSecureMessage(dispatchMessage) && isSecureMessage(messageSubject) && recipient.getContactDetails() != null
				&& recipient.getContactDetails().getDestinationAddress() != null) {
			String[] recipientIds = null;
			String destinationAddress = recipient.getContactDetails().getDestinationAddress();
			if (destinationAddress.contains(Constants.COMMA)) {
				recipientIds = destinationAddress.split(Constants.COMMA);
			} else {
				recipientIds = new String[] { destinationAddress };
			}
			
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############destinationAddress: - " + destinationAddress);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Before RecipientIds: - " + recipientIds.toString());
			String eventIds = CustomConfigUtil.readConfigValue("EMAIL_DISPATCHER_ALERT_EVENTID_LIST",
					"OAC_REVOKE_SUCCESS,OAC_GRANT_SUCCESS,OAC_REFRESH_SUCCESS,STANDING_INSTRUCTION_CANCELLATION,PIN_RESET_SUCCESS,PIN_RESET_FAILURE_TEMPORARY_LOCKED,PIN_RESET_FAILURE_LOCKED,SUSPEND_EMAIL_REJECTION_ON_PIN_RESET,FPS_ID_REGISTRATION,FPS_ID_TERMINATION,FPS_ID_REACTIVATION,PIN_ACTIVATION_SUCCESS,PIN_ACTIVATION_FAILURE_TEMPORARY_LOCKED,PIN_ACTIVATION_FAILURE_LOCKED");

			String[] eventList = eventIds.split(",");
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
					"#######Request Event:- " + alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId());

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#################### Template Id "
					+ alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getMessageTemplateId());

			NameValuePair[] dispatchData2 = data.getDispatchData();
			String mngUserId = "";
			String mngPartyId = "";

			for (int i = 0; i < dispatchData2.length; i++) {
				mngUserId = "";
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("######dispatchData2 name: " + dispatchData2[i].getGenericName());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("######dispatchData2 value: " + dispatchData2[i].getValue());
				if ("EmailReminder".equalsIgnoreCase(dispatchData2[i].getGenericName())) {
					mngUserId = (String) dispatchData2[i].getValue();
					if (mngUserId.contains("~")) {
						mngUserId = mngUserId.split("~")[1];
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##Dispatch Value before modifying: " + dispatchData2[i].getValue());
						dispatchData2[i].setValue(dispatchData2[i].getValue().toString().split("~")[0]);
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##Dispatch Value after modifying: " + dispatchData2[i].getValue());
					}

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch UserID: " + mngUserId);
					break;
				}

				if ("MakerUserId".equalsIgnoreCase(dispatchData2[i].getGenericName())) {
					mngUserId = (String) dispatchData2[i].getValue();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch MakerUserId UserID: " + mngUserId);
					break;
				}

				if ("MngParty".equalsIgnoreCase(dispatchData2[i].getGenericName())) {
					mngPartyId = (String) dispatchData2[i].getValue();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch MngParty: " + mngPartyId);
					break;
				}

				if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId().contains("RESET_DL")) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("RestParty value:- " + dispatchData2[i].getValue().toString());
					if ("ResetParty".equalsIgnoreCase(dispatchData2[i].getGenericName())) {
						mngPartyId = dispatchData2[i].getValue().toString();
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch ResetDL Alert MngParty: " + mngPartyId);
						break;
					}
				}

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("mngUserId:-" + mngUserId + " for iteration:- " + i);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("mngPartyId:-" + mngPartyId + " for iteration:- " + i);
			}

			data.setDispatchData(dispatchData2);
			safeDebugPrint("data.setDispatchData(dispatchData2)", dispatchData2);
			for (String alertEventId : eventList) {

				if (alertEventId
						.equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Entered EventList If loop #Event IDs: -" + alertEventId);

					/*
					 * if ("OAC_REVOKE_SUCCESS"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "OAC_GRANT_SUCCESS"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "OAC_REFRESH_SUCCESS"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "STANDING_INSTRUCTION_CANCELLATION"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_RESET_SUCCESS"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_RESET_FAILURE_TEMPORARY_LOCKED"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_RESET_FAILURE_LOCKED"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "SUSPEND_EMAIL_REJECTION_ON_PIN_RESET"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "FPS_ID_REGISTRATION"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "FPS_ID_TERMINATION"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "FPS_ID_REACTIVATION"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_ACTIVATION_SUCCESS"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_ACTIVATION_FAILURE_TEMPORARY_LOCKED"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId()) || "PIN_ACTIVATION_FAILURE_LOCKED"
					 * .equalsIgnoreCase(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().
					 * getEventId())) {
					 */
					String custEmail = null;
					String userEmail = null;
					String custId = alertRequestDTO.getPreferredRecipient().getContactDetails().getPartyId();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###########Customer ID in Email Dispatcher: " + custId);

					NameValuePair[] dispatchData = data.getDispatchData();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Dispatch Data: " + dispatchData);
					String userId = "";
					for (int i = 0; i < dispatchData.length; i++) {
						
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generic Name :" + dispatchData[i].getGenericName());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generic Value for above Name :" + dispatchData[i].getValue());
						
						if ("UserId".equalsIgnoreCase(dispatchData[i].getGenericName())) {
							userId = (String) dispatchData[i].getValue();
							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch UserID: " + userId);
							// break;
						}

						if ("compEmail".equalsIgnoreCase(dispatchData[i].getGenericName())) {
							custEmail = (String) dispatchData[i].getValue();
							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############Dispatch custEmail: " + custEmail);
							// break;
						}
					}
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#################### Template Id "
							+ alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getMessageTemplateId());

					if (userId.equalsIgnoreCase("")) {
						userId = getSessionContext().getUserId();
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##########SI Deletion Userid from session context is " + userId);
					}
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#########userId (from alert DTO) ==" + userId);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut
							.println("###########User id from session context is : " + getSessionContext().getUserId());
					if (userId.equalsIgnoreCase("FRXMDB") || userId.equalsIgnoreCase("SYSTELLER")) {
						userId = alertRequestDTO.getUserId();
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("################ User Id in Email Dispatcher: " + userId);
					}

					String fetchMngUserEventIds = CustomConfigUtil
							.readConfigValue("EMAIL_DISPATCHER_FETCH_MNG_USER_EVENTID_LIST", "");
					String[] fetchMngUserEventList = fetchMngUserEventIds.split(",");
					for (String fetchMngUserEvent : fetchMngUserEventList) {
						if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
								.equalsIgnoreCase(fetchMngUserEvent)) {

							if (userId != null && !userId.equalsIgnoreCase("") && !"SYSTELLER".equalsIgnoreCase(userId)
									&& !"FRXMDB".equalsIgnoreCase(userId)) {
								mngUserId = userId;
								com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##MngUser for the event:- " + fetchMngUserEvent + " : MNG User:- "
										+ mngUserId);
							}
						}
					}

					// read party email id
					// IAdapterFactory adapterFactory =
					// AdapterFactoryConfigurator.getInstance().getAdapterFactory("PARTY_EP_FC_REMOTE_FACTORY");
					// IPartyEventProcessingAdapter partyEventProcessingAdapter =
					// (IPartyEventProcessingAdapter)adapterFactory.getAdapter("ModuleToPartyAdapter");
					// AlertContactPreferenceDTO alertContactPreferenceDTO =
					// partyEventProcessingAdapter.getContactPreference(custId,
					// DestinationType.EMAIL);

					UserResponseDTO user = null;
					try {
						com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
								.getInstance().getAdapterFactory(
										com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
						IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory.getAdapter(
								com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
						String eventIdsToSkipCustId = CustomConfigUtil.readConfigValue(
								"EMAIL_DISPATCHER_SKIP_PARTY_EMAIL_LIST", "INWARD_REMITTANCE_ALERT_SUCCESS");

						String[] eventIdsToSkipCustIdList = eventIdsToSkipCustId.split(",");
						List<String> skipCustEmailList = new ArrayList<String>();
						for (String eventToSkip : eventIdsToSkipCustIdList) {
							skipCustEmailList.add(eventToSkip);
						}
						if (skipCustEmailList
								.contains(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {

							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Entered EventList If loop to skip custEmail");

						} else if (custId != null && !custId.equalsIgnoreCase("")) {
							CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(custId, false);

							if (partyDetails != null && partyDetails.getOfficeEmailId() != null) {
								custEmail = partyDetails.getOfficeEmailId();
							}
							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Email Dispatcher custEmail == " + custEmail);
						}

						// read user email id
						com.ofss.digx.app.adapter.IAdapterFactory userAdapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
								.getInstance().getAdapterFactory(CommonAdapterFactoryConstants.USER_ME_ADAPTER_FACTORY);
						IUserMeAdapter userMeAdapter = (IUserMeAdapter) userAdapterFactory
								.getAdapter(CommonAdapterConstants.USER_ME_ADAPTER);
						String eventIdsToSkipUserId = CustomConfigUtil
								.readConfigValue("EMAIL_DISPATCHER_SKIP_CUST_EMAIL_LIST", "FPS_ID_REGISTRATION");

						String[] eventIdsToSkipUserIdList = eventIdsToSkipUserId.split(",");
						List<String> skipUserEmailList = new ArrayList<String>();
						for (String eventIdsToSkip : eventIdsToSkipUserIdList) {
							skipUserEmailList.add(eventIdsToSkip);
						}
						if (skipUserEmailList
								.contains(alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {

							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Entered EventList If loop to skip userEmail");
						} else if (getSessionContext() != null && (userId != null && !userId.equalsIgnoreCase("")
								&& !"SYSTELLER".equalsIgnoreCase(userId) && !"FRXMDB".equalsIgnoreCase(userId))) {
							SessionContext sessionContext = getSessionContext();
							user = userMeAdapter.readUser(sessionContext, userId);
							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
									"###########Email Dispatcher user email id = " + user.getUserDTO().getEmailId());
						}

					} catch (com.ofss.digx.infra.exceptions.Exception e) {
						// TODO Auto-generated catch block
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
					}
					if (user != null) {
						userEmail = user.getUserDTO().getEmailId();
					}
					/*
					 * if (userEmail == null) { userEmail = "smita.bamnote@oracle.com"; }
					 */

					if (custEmail != null && userEmail != null) {
						recipientIds = new String[] { custEmail, userEmail };
					}
					if (custEmail != null && userEmail == null) {
						recipientIds = new String[] { custEmail };
					}
					if (custEmail == null && userEmail != null) {
						recipientIds = new String[] { userEmail };
					}

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############ Email Dispatcher recipientIds " + recipientIds);
				}
			}

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("###############After RecipientIds: - " + recipientIds.toString());
			// changes done for Open API alerts
			for (String recipientId : recipientIds) {
				if (recipientId != null && !recipientId.trim().equalsIgnoreCase(Constants.EMPTY_STRING)) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Recipient Id : " + recipientId);
					com.ofss.fc.app.adapter.IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
							.getAdapterFactory(ModuleConstant.EVENT_PROCESSING_DISPATCH);
					IDispatchAdapter adapter = (IDispatchAdapter) adapterFactory
							.getAdapter(EventProcessingAdapterConstant.EP_TO_DISPATCH);
					if (adapter == null && dispatchResult != null) {
						dispatchResult.setIsDispatchSuccessfull(false);
						dispatchResult.setMessage(
								"No Outgoing Email Adapter " + EventProcessingAdapterConstant.EP_TO_DISPATCH);
						dispatchResult.setDispatchMessage(dispatchMessage);
					} else {
						DispatchResultDTO result = adapter.dispatchEmail(alertRequestDTO, dispatchMessage, filenames,
								messageSubject);
						if (result.getIsDefaultEmailProcessing()) {

							// rkeshari
							com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MNG befoe check for mock configuration -MOCK_EMAIL_SEND_IF");
							String isMocked = CustomConfigUtil.readConfigValue("MOCK_EMAIL_SEND_IF", "N");
							if ("TT_DISCREPANCY_REPORT_ATTACHMENT".equalsIgnoreCase(
									alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId())) {
								com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Sending SMTP mail with attachment  : entering in to sendMail");
								dispatchResult = sendMail(recipientId.trim(), dispatchMessage, filenames,
										messageSubject);
							} else {
								if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
										.equalsIgnoreCase("TD_MODE1_NOTIFICATION")) {
									com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
											"##################Entered TD_MODE1_NOTIFICATION recipientId loop");
									String recipientIdTD = alertRequestDTO.getAlertContactPreference()
											.getDispatchAddress();
									com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
											"#################TD_MODE1_NOTIFICATION recipientID:- " + recipientIdTD);
									com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entering in to sendMNGMail");
									if (recipientIdTD != null) {
										dispatchResult = sendMNGMail(recipientIdTD, dispatchMessage, filenames,
												messageSubject, alertRequestDTO, data, mngUserId, mngPartyId);
									}

								} else {
									com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entering in to sendMNGMail");
									dispatchResult = sendMNGMail(recipientId.trim(), dispatchMessage, filenames,
											messageSubject, alertRequestDTO, data, mngUserId, mngPartyId);
								}
							}
							//
						} else {
							dispatchResult = new DispatchResult(result);
						}
					}
				}
			}

			// Taking recipientId from alertRequestDTO for
			// TD_MODE1_NOTIFICATION alert since EMAIL is required to be sent
//			if (alertRequestDTO.getRecipientMessageTemplate().getKeyDTO().getEventId()
//					.equalsIgnoreCase("TD_MODE1_NOTIFICATION")) {
//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################Entered TD_MODE1_NOTIFICATION recipientId loop");
//				String recipientId = alertRequestDTO.getAlertContactPreference().getDispatchAddress();
//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#################TD_MODE1_NOTIFICATION recipientID:- " + recipientId);
//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entering in to sendMNGMail");
//				if (recipientId != null) {
//					dispatchResult = sendMNGMail(recipientId, dispatchMessage, filenames, messageSubject,
//							alertRequestDTO, data, mngUserId, mngPartyId);
//				}
//
//			}
			
		} else {
			dispatchResult = new DispatchResult();
			dispatchResult.setIsDispatchSuccessfull(false);
			dispatchResult.setDispatchMessage(dispatchMessage);
			if (recipient.getContactDetails() == null
					|| recipient.getContactDetails().getDestinationAddress() == null) {
				dispatchResult.setMessage("Email Address not found");
			} else {
				dispatchResult.setMessage("Unacceptable input found in the message text");
			}
		}
		return dispatchResult;
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

	public String getCurrentDateAndTime(String format) {
		DateFormat df = new SimpleDateFormat(format);
		String text = df.format(new java.util.Date());
		return text;
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
	//BCOCDC4728
	private void safeDebugPrint(String tag, Object obj) {
		if(obj == null){
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("[Debug_" + tag +"] The Object is NULL.");
			return;
		}
		try {
			String content = ReflectionToStringBuilder.toString(obj, ToStringStyle.MULTI_LINE_STYLE);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("[Debug_" + tag + "]\n" + content);
		} catch(Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("[Debug_" + tag +"] Error during reflection: "+ e.getMessage());
		}
	}
}
