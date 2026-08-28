/*******************************************************************************
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 *******************************************************************************/
package com.ofss.digx.cz.bea.app.audit.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.audit.handler.AsyncAuditHandler;
import com.ofss.digx.app.audit.handler.AsyncJMSAuditHandler;
import com.ofss.digx.app.context.ChannelContext;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.app.sms.adapter.user.IUserPartyAdapter;
import com.ofss.digx.app.sms.dto.user.UserPartyDetailsRequestDTO;
import com.ofss.digx.app.sms.dto.user.UserResponseDTO;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.common.constants.UserAttributeConstant;
import com.ofss.digx.cz.bea.app.approval.adapter.transaction.ITransactionAdapter;
import com.ofss.digx.cz.bea.app.bulkupload.dto.BatchTransferRequestDTO;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.datatype.complex.AuditMap;
import com.ofss.digx.domain.fileupload.entity.transaction.FileLevelTransaction;
import com.ofss.digx.enumeration.approval.ProcessingStatus;
import com.ofss.digx.enumeration.audit.Action;
import com.ofss.digx.enumeration.audit.Type;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.extxface.sms.user.adapter.IUserAdapter;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.audit.IAuditHandler;
import com.ofss.digx.infra.audit.constants.AuditConstants;
import com.ofss.digx.infra.audit.dto.APIAuditDTO;
import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.digx.infra.audit.dto.AuditDetailsDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.enumeration.ServiceCallContextType;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.digx.framework.security.handlers.ISessionManager;
import com.ofss.digx.framework.security.handlers.SessionManager;
import com.ofss.digx.framework.security.session.entity.UserSession;
import com.ofss.digx.framework.security.session.entity.UserSessionKey;
import com.ofss.sms.dbAuthenticator.domain.UserProfile;
import com.ofss.sms.dbAuthenticator.domain.UsersKey;

/**
 * <p>
 * Represents the async audit handler for auditing. This is a singleton class.
 * </p>
 * The operations supported by this {@link AsyncAuditHandler} class are as follows:
 * <ol>
 * <li><strong>doAudit</strong> : sent the sudit data to DB.</li>
 *
 *
 * </ol>
 */
public class CZAsyncAuditHandler implements IAuditHandler {
	/**
	 * Stores the name of the entity(class) represented by this {@code Class} object as a {@code String}
	 */
	private static final String THIS_COMPONENT_NAME = CZAsyncAuditHandler.class.getName();

	/**
	 * Reference variable for {@code AsyncAuditHandler}
	 */
	private static CZAsyncAuditHandler singletonInstance;
	/**
	 * constant for anonymous user id.
	 */
	private static final String ANONYMOUS_USER = "anonymous";

	private static final String TRANSACTION_AUDIT_STATUS = "TRANSACTION_AUDIT_STATUS";

	private static final String IS_OMB_ENABLED = "IS_OMB_ENABLED";

	/**
	 * Holds the instance of {@link MultiEntityLogger} used for sending messages on the console.
	 *
	 */

	private com.ofss.fc.infra.log.impl.MultiEntityLogger formatter = com.ofss.fc.infra.log.impl.MultiEntityLogger
			.getUniqueInstance();

	/**
	 * Attribute to hold the instance of com.ofss.fc.infra.log.impl.MultiEntityLogger
	 */
	private static transient Logger logger = com.ofss.fc.infra.log.impl.MultiEntityLogger.getUniqueInstance()
			.getLogger(THIS_COMPONENT_NAME);
	/**
	 * A {@link Preferences} object to store day-one configuration to be loaded.
	 */
	private final Preferences auditPreference;

	/**
	 * Holds the constant for config in the form of {@link String}.
	 */
	private static final String AUDIT_CONFIG = "AuditConfig";

	public CZAsyncAuditHandler() {

		auditPreference = ConfigurationFactory.getInstance().getConfigurations(AUDIT_CONFIG);
		initializeHighRiskTxns();
	}

	private static List<String> highRiskTxnList = null;

	private static void initializeHighRiskTxns() {
		if (highRiskTxnList == null) {
			highRiskTxnList = new ArrayList<String>();

			String[] highRiskTransactions = { "PC_F_GCRNDFT_FPS", "LAT_N_CA", "LAT_N_DA", "LAT_N_UA", "PC_N_CIP",
					"PC_N_UIP", "PC_N_DOP_VT", "PC_N_UDOP_VT", "PC_N_CITNP", "PC_N_UITNP", "PC_N_CBP", "PC_N_UBP",
					"PC_N_CIP_A", "PC_N_UIP_A", "PC_N_DOP_VT_A", "PC_N_UDOP_VT_A", "PC_N_CITNP_A", "PC_N_UITNP_A",
					"PC_N_CBP_A", "PC_N_UBP_A", "FU_F_APC", "BU_AUTOPAY_CREATE", "BU_PAYROLL_CREATE", "PC_F_SED",
					"PC_F_AED", "PC_F_AED_CONFIRM", "PC_F_AED_REJECT", "PC_F_AED_SUSPEND", "PC_F_AED_RESUME",
					"PC_F_AED_TERMINATE", "PC_F_GCRNIFT_SI", "PC_F_GCRNBCT_SI", "PC_F_GCRNDFT_SI",
					"PC_F_GCRNDFT_FPS_SI", "PC_F_GCRNINFT_SI", "PC_F_GCRNIFT", "PC_F_GCRNBCT", "PC_F_GCRNDFT",
					"PC_F_GCRNINFT", "UAT_N_CA", "UAT_N_DA", "UAT_N_UA", "UAT_N_HUA_NEW", "UAT_N_HUA_EDT",
					"UAT_N_HUA_DEL", "UM_N_ULS_UL", "UM_N_USD", "MT_N_CUS",
					"MT_N_UUS", "FL_C_MIHO", "EB_F_BP", "EB_F_BP_SI" };

					highRiskTxnList = Arrays.asList(highRiskTransactions);

		}
	}




	/**
	 * It will send the audit DTO to DB.
	 *
	 * @param auditDTO
	 *            of type {@link AuditDTO} containing the audit DTO.
	 * @throws Exception
	 *
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void doAudit(AuditDTO auditDTO) throws Exception {

		AuditDetailsDTO auditDetailsDTO;

		List<String> exclusionTaskList = getAuditExclusionTaskTaskList();
		System.out.println("inside Audit DTO for task code "+ auditDTO.getTaskCode() );

		if(auditDTO!=null && auditDTO.getTaskCode()!=null)
		{
			if(auditDTO.getTaskCode().equalsIgnoreCase("PC_I_INSTRL") || auditDTO.getTaskCode().equalsIgnoreCase("MT_N_VFP"))
				{
					auditDTO.setAction(Action.ENQUIRED);
				}
			else if(auditDTO.getTaskCode().equalsIgnoreCase("AU_CZ_LOGOUT"))
				{
					auditDTO.setAction(Action.EDITED);
				}

			if(auditDTO.getResolvedRequestUrl().contains("requestModification")) {
				auditDTO.setAction(Action.INITIATED);
			}
		}

		if(auditDTO!=null && auditDTO.getTaskCode()!=null && auditDTO.getTaskCode().equals("MT_N_CFM")) {
			System.out.println("Inside do audit for Fps Merchant Addressing create");
		}


		// Add changes for HKMA

		Boolean auditBatchIdFlag = false;
		Boolean validationOnly = false;

		  List<AuditMap> auditMap=auditDTO.getRequestHeaders();

		if (auditMap != null && auditMap.size() > 0) {

			for (AuditMap listAuditMap : auditMap) {
				System.out.println("Name : " + listAuditMap.getName() + "Value : " + listAuditMap.getValue());
				if (listAuditMap.getName().equals("X-BATCH-ID")) {
					auditBatchIdFlag = true;
					break;
				}

				if (listAuditMap.getName().equals("X-Validate-Only") && listAuditMap.getValue().equals("true")) {
					validationOnly = true;
					break;
				}
			}
		}

		if (!auditBatchIdFlag) {

		  if (auditMap!=null) {

		  AuditMap map = new AuditMap();
		  map.setName("HighRiskIndicator");
		  String indicator="N";
		  if(auditDTO!=null && auditDTO.getTaskCode()!=null && highRiskTxnList!=null) {

			  indicator=  getHighRiskIndicator(auditDTO.getTaskCode());

		  }

		if ( com.ofss.digx.infra.thread.ThreadAttribute.get("MULTIPLE_TXN_HIGH_RISK_INDICATOR") !=null) {
			String multiApproveValue= (String)com.ofss.digx.infra.thread.ThreadAttribute.get("MULTIPLE_TXN_HIGH_RISK_INDICATOR") ;
			if (multiApproveValue.equalsIgnoreCase("Y")){
				System.out.println("Multiapproval txn high risk indicator Y");
				indicator="Y";
			}

		}

		if (com.ofss.fc.infra.thread.ThreadAttribute.get("MULTIPLE_TXN_HIGH_RISK_INDICATOR") !=null) {
			String multiApproveValue= (String)com.ofss.fc.infra.thread.ThreadAttribute.get("MULTIPLE_TXN_HIGH_RISK_INDICATOR") ;
			if (multiApproveValue.equalsIgnoreCase("Y")){
				System.out.println("Multiapproval txn high risk indicator Y");
				indicator="Y";
			}

		}

		AuditMap suspMap = new AuditMap();
		suspMap.setName("isSuspiciousIndicator");
		suspMap.setValue("no");


		System.out.println("IS_SUSPICIOUS_INDICATOR in asynch com.ofss.digx.infra.thread.ThreadAttribute"+ (String)com.ofss.digx.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR"));
		System.out.println("IS_SUSPICIOUS_INDICATOR in asynch com.ofss.fc.infra.thread.ThreadAttribute"+ (String)com.ofss.fc.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR"));


		if (com.ofss.digx.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR") !=null) {
			String isSuspiciousIndicatorFromDict = (String)com.ofss.digx.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR");
			System.out.println("isSuspiciousIndicatorFromDict "+isSuspiciousIndicatorFromDict);
			if (isSuspiciousIndicatorFromDict.equalsIgnoreCase("Y") || isSuspiciousIndicatorFromDict.equalsIgnoreCase("SUS") || isSuspiciousIndicatorFromDict.equalsIgnoreCase("true")){
				System.out.println("IS_SUSPICIOUS_INDICATOR in asynch audit true");
				suspMap.setValue("yes");

			}

		}

		if (com.ofss.fc.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR") !=null) {
			String isSuspiciousIndicatorFromDict = (String)com.ofss.fc.infra.thread.ThreadAttribute.get("IS_SUSPICIOUS_INDICATOR");
			System.out.println("isSuspiciousIndicatorFromDict com.ofss.fc.infra.thread.ThreadAttribute "+isSuspiciousIndicatorFromDict);
			if (isSuspiciousIndicatorFromDict.equalsIgnoreCase("Y") || isSuspiciousIndicatorFromDict.equalsIgnoreCase("SUS") || isSuspiciousIndicatorFromDict.equalsIgnoreCase("true")){
				System.out.println("IS_SUSPICIOUS_INDICATOR in asynch audit true com.ofss.fc.infra.thread.ThreadAttribute");
				suspMap.setValue("yes");

			}

		}

		auditMap.add(suspMap);

		  map.setValue(indicator);
		  auditMap.add(map);
		  map = new AuditMap();
		  map.setName("IPRange");
			if (com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS")!=null && !("".equalsIgnoreCase((String) com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS"))) ){
				String iPAddress=(String) com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS");
				System.out.println("The FMO IP address is "+iPAddress);
				  map.setValue(	getIPRangeValue(iPAddress));
			}else {
				  map.setValue(" ");
			}
			auditMap.add(map);
			map = new AuditMap();
			map.setName("LoginTime");


			if (auditDTO.getSessionID()!=null) {
			Session session = null;
		    boolean isSessionOpen = false;

			UserSession userSession = new UserSession();
			UserSessionKey userSessionKey = new UserSessionKey();
			userSessionKey.setUserID(auditDTO.getUserId().toLowerCase());
			userSessionKey.setSessionId(auditDTO.getSessionID());
			userSession.setKey(userSessionKey);
			ISessionManager isessionManager = new SessionManager();
			UserSession userSessionResponse = null;
			try {
				System.out.println("CZAsynchAudit read User session");
				if (DataAccessManager.getManager().isSessionOpen()) {
					System.out.println("CZAsynchAudit session is open");
					session = DataAccessManager.getManager().fetchCurrentSession();
				} else {
					System.out.println("CZAsynchAudit Opening DIGX session");
					session = DataAccessManager.getManager().openSession("DIGX");
					isSessionOpen = true;
				}

				if(session != null) {
					System.out.println("CZAsynchAudit session not null");
					userSessionResponse = (UserSession) session.get(UserSession.class, userSessionKey);
					Date loginTime=userSessionResponse.getCreationTime();
					map.setValue(loginTime.toString("yyyy-MM-dd'T'HH:mm:ss.SSSz"));
					System.out.println("Login Time set "+loginTime.toString("yyyy-MM-dd'T'HH:mm:ss.SSSz"));

				}else {
					System.out.println("CZAsynchAudit session is null");
					map.setValue("");
				}

			} catch (java.lang.Exception e) {
				System.out.println("Error while reading session for CRM");
				e.printStackTrace();
				map.setValue("");
			}finally {
				if (isSessionOpen) {
					DataAccessManager.getManager().closeSession(session);
					System.out.println("Session closed");
				}
			}
			}else {
				map.setValue("");
			}
			auditMap.add(map);
		  }

		switch (auditDTO.getAuditDetailsDTOList().get(0).getAuditType()) {
		case HOST:
			auditDetailsDTO = new AuditDetailsDTO();
			auditDetailsDTO.setAuditType(Type.HOST);
			auditDetailsDTO.setServiceName((String) com.ofss.fc.infra.thread.ThreadAttribute
					.get(com.ofss.fc.infra.thread.ThreadAttribute.CURRENTLY_EXECUTING_SERVICE));
			auditDetailsDTO.setRequest(auditDTO.getAuditDetailsDTOList().get(0).getRequest());
			//auditDetailsDTO.setResponse(auditDTO.getAuditDetailsDTOList().get(0).getResponse());
			auditDetailsDTO.setOperationName(auditDTO.getAuditDetailsDTOList().get(0).getOperationName());
			pushIntoAuditStack(auditDetailsDTO);
			break;
		case SERVICE:
			auditDetailsDTO = new AuditDetailsDTO();
			boolean serviceRequestAuditEnabled = true;
			String taskCode = (String) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.TASK_CODE_AUDIT);
			if (taskCode != null && exclusionTaskList.contains(taskCode)) {
				serviceRequestAuditEnabled = false;
			}
			auditDetailsDTO.setAuditType(Type.SERVICE);
			auditDetailsDTO.setServiceName((String) com.ofss.fc.infra.thread.ThreadAttribute
					.get(com.ofss.fc.infra.thread.ThreadAttribute.CURRENTLY_EXECUTING_SERVICE));
			if (serviceRequestAuditEnabled) {
				auditDetailsDTO.setRequest(auditDTO.getAuditDetailsDTOList().get(0).getRequest());
			}
			//auditDetailsDTO.setResponse(auditDTO.getAuditDetailsDTOList().get(0).getResponse());
			pushIntoAuditStack(auditDetailsDTO);
			break;
		case REST:
			if (auditDTO.getTargetUnit() == null || auditDTO.getTargetUnit().isEmpty()) {
				SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
				auditDTO.setTargetUnit(sessionContext.getTargetUnit());
			}
			Map<String, Object> threadAttributeMap = auditDTO.getThreadAttributeMap();
			if (threadAttributeMap == null) {
				threadAttributeMap = new HashMap<String, Object>();
				auditDTO.setThreadAttributeMap(threadAttributeMap);
			}
			threadAttributeMap.put(ThreadAttribute.LEGAL_ENTITY_CODE,
					ThreadAttribute.get(ThreadAttribute.LEGAL_ENTITY_CODE));
			threadAttributeMap.put(ThreadAttribute.MARKET_ENTITY_CODE,
					ThreadAttribute.get(ThreadAttribute.MARKET_ENTITY_CODE));
			threadAttributeMap.put(ThreadAttribute.TRANSACTION_BRANCH,
					ThreadAttribute.get(ThreadAttribute.TRANSACTION_BRANCH));
			threadAttributeMap.put(ThreadAttribute.BUSINESS_UNIT_CODE,
					ThreadAttribute.get(ThreadAttribute.BUSINESS_UNIT_CODE));
			threadAttributeMap.put(ThreadAttribute.REGULATORY_REGION,
					ThreadAttribute.get(ThreadAttribute.REGULATORY_REGION));

			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, formatter.formatMessage("Entered into doAudit() : auditDTO=%s in class %s ",
						auditDTO, THIS_COMPONENT_NAME));
			}
			AuditDetailsDTO auditRestDetailsDTO = new AuditDetailsDTO();
			boolean restRequestAuditEnabled = true;
			String taskCodeRest = (String) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.TASK_CODE_AUDIT);
			if (taskCodeRest != null && exclusionTaskList.contains(taskCodeRest)) {
				restRequestAuditEnabled = false;
			}
			if (restRequestAuditEnabled) {
				auditRestDetailsDTO.setRequest(auditDTO.getAuditDetailsDTOList().get(0).getRequest());
			}
			  if (null != auditDTO.getHttpMethod())
              {
                              if(!"GET".equalsIgnoreCase( auditDTO.getHttpMethod().toUpperCase()))
                              {
                            	  System.out.println("Inside the audit API response data");
                            	  auditRestDetailsDTO.setResponse(auditDTO.getAuditDetailsDTOList().get(0).getResponse());
                              }
              }

			auditRestDetailsDTO.setAuditType(auditDTO.getAuditDetailsDTOList().get(0).getAuditType());
			if ((Stack<AuditDetailsDTO>) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK) != null) {
				auditDTO.setAuditDetailsDTOList((Stack<AuditDetailsDTO>) com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK));
				auditDTO.getAuditDetailsDTOList().add(auditRestDetailsDTO);
			}
			if (auditDTO.getUserId() != null & !ANONYMOUS_USER.equals((auditDTO.getUserId()))) {

				IUserAdapter userAdapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IUserAdapter.class, "read",
						DeterminantType.Enterprise);


				com.ofss.sms.dbAuthenticator.domain.UserProfile userProfileDomain = new com.ofss.sms.dbAuthenticator.domain.UserProfile();
				com.ofss.sms.dbAuthenticator.domain.UsersKey userProfileKey = new com.ofss.sms.dbAuthenticator.domain.UsersKey();
				userProfileKey.setUserName(auditDTO.getUserId());
				userProfileDomain.setKey(userProfileKey);
				userProfileDomain = userProfileDomain.read(userProfileDomain);
				if(userProfileDomain!=null ) {
					String firstname = userProfileDomain.getFirstName() == null ? UserAttributeConstant.BLANK : userProfileDomain.getFirstName();
					String lastname = userProfileDomain.getLastName() == null ? UserAttributeConstant.BLANK : " "+userProfileDomain.getLastName();
					String usernameFromUMuserprofile = firstname + lastname;
					auditDTO.setUserName(usernameFromUMuserprofile);
					System.out.println("AuditDTO username set from CZ_UM_USERPROFILE = " + usernameFromUMuserprofile);

				}

//				UserResponseDTO userResponseDTO = userAdapter.read(auditDTO.getUserId());
//				if (userResponseDTO != null && userResponseDTO.getUserDTO() != null) {
//					auditDTO.setUserName(userResponseDTO.getUserDTO().getFirstName() + " "
//							+ userResponseDTO.getUserDTO().getLastName());
//				}

			}

			if (auditDTO.getPartyId() != null) {
				IAdapterFactory partyAdapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.USER_PARTY_ADAPTER_FACTORY);
				IUserPartyAdapter userPartyAdapter = (IUserPartyAdapter) partyAdapterFactory
						.getAdapter(CommonAdapterConstants.USER_PARTY_ADAPTER);

				ChannelContext channelContext = (ChannelContext) auditDTO.getThreadAttributeMap()
						.get(com.ofss.digx.infra.thread.ThreadAttribute.CHANNEL_CONTEXT);

				if (channelContext.getSessionContext() != null) {
					ThreadAttribute.set(ThreadAttribute.SESSION_CONTEXT, channelContext.getSessionContext());
				}
				UserPartyDetailsRequestDTO userPartyDetailsRequestDTO = new UserPartyDetailsRequestDTO();
				userPartyDetailsRequestDTO.setPartyId(auditDTO.getPartyId().getValue());

//				PartyResponse partyResponse = userPartyAdapter.fetchParty(channelContext.getSessionContext(),
//						userPartyDetailsRequestDTO);

//				if (partyResponse != null && partyResponse.getParty() != null
//						&& partyResponse.getParty().getPersonalDetails() != null) {
//					auditDTO.setPartyName(partyResponse.getParty().getPersonalDetails().getFullName());
//				}

				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
				IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
						.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);

				System.out.println("CZAsyncAuditHandler userPartyDetailsRequestDTO.getPartyId() "+userPartyDetailsRequestDTO.getPartyId());
				CZPartyPreferenceDTO partyDetails = adapter.getPartyName(userPartyDetailsRequestDTO.getPartyId());
				if (partyDetails != null && partyDetails.getCompanyName() != null) {
					System.out.println("CZAsyncAuditHandler partyDetails.getCompanyName() "+partyDetails.getCompanyName());
					auditDTO.setPartyName(partyDetails.getCompanyName());
				}
			}

			if (auditDTO.getReferenceNo() == null || auditDTO.getReferenceNo() == "") {
				String referenceNumber = null;
				if (com.ofss.fc.infra.thread.ThreadAttribute
						.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null
						|| com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null) {
					referenceNumber = ((String) com.ofss.fc.infra.thread.ThreadAttribute
							.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO));
					System.out.println("CZAsync Ref No FC="+referenceNumber);

					if (referenceNumber == null) {
						referenceNumber = (String) com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
						System.out.println("CZAsync Ref No DIGX="+referenceNumber);
					}

					auditDTO.setReferenceNo(referenceNumber);
				}else if(com.ofss.digx.infra.thread.ThreadAttribute.get("QR_CODE_CREATE_REF_NUMBER") != null) {
					System.out.println("Ref No for QR Code Generation");
					referenceNumber  = (String) com.ofss.digx.infra.thread.ThreadAttribute.get("QR_CODE_CREATE_REF_NUMBER");
					auditDTO.setReferenceNo(referenceNumber);
				}
			}

			if(com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.FILE_REF_ID) != null) {
				String fileRefNo = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.FILE_REF_ID);
				System.out.println("** Audit file ref no="+fileRefNo);
				auditDTO.setReferenceNo(fileRefNo);
			}

			if (com.ofss.digx.infra.thread.ThreadAttribute.get(TRANSACTION_AUDIT_STATUS) != null
					&& com.ofss.digx.infra.thread.ThreadAttribute.get(TRANSACTION_AUDIT_STATUS)
							.equals(ProcessingStatus.FAIL)) {
				System.out.println("Failure status set in audit DTO");
				auditDTO.setStatus(com.ofss.digx.enumeration.audit.Status.FAILURE);
			}

			if (auditDTO.getReferenceNo() != null && auditDTO.getTaskCode() != null
					&& auditDTO.getTaskCode().equals("FU_F_APC")) {
				System.out.println("** Inside audit FU_F_APC");
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory.getAdapter(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);
				com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = adapter
						.readTransactionDomain(auditDTO.getReferenceNo());

				if (transactionDomain instanceof FileLevelTransaction) {
					FileLevelTransaction fileDetails = (FileLevelTransaction) transactionDomain;
					System.out.println("** File ref id audit is ==" + fileDetails.getFileRefId());
					auditDTO.setReferenceNo(fileDetails.getFileRefId());
				}
			}

				if (auditDTO.getTaskCode() != null && auditDTO.getTaskCode().equals("FU_UP")) {
					System.out.println("** Inside audit FU_UP");
					if (auditDTO.getAction() != null && auditDTO.getAction().equals(Action.CREATED)) {
						auditDTO.setAction(Action.INITIATED);
					}
				}

				if (auditDTO.getTaskCode() != null && auditDTO.getTaskCode().equals("TL_N")) {
					System.out.println("** Inside audit TL_N");
					if (auditDTO.getAction() != null && auditDTO.getAction().equals(Action.CREATED)) {
						auditDTO.setAction(Action.ENQUIRED);
					}
				}

			if (getOMBAuditFlag()) {

				String referenceNumber = null;
				if (com.ofss.fc.infra.thread.ThreadAttribute
						.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null
						|| com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null) {
					referenceNumber = ((String) com.ofss.fc.infra.thread.ThreadAttribute
							.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO));
					System.out.println("CZAsync Ref No FC audit=" + referenceNumber);

					if (referenceNumber == null) {
						referenceNumber = (String) com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
						System.out.println("CZAsync Ref No DIGX audit=" + referenceNumber);
					}

				}

				if (referenceNumber == null && com.ofss.digx.infra.thread.ThreadAttribute.get("AUDIT_TXN_REF_NO") != null) {
					referenceNumber = (String) com.ofss.digx.infra.thread.ThreadAttribute.get("AUDIT_TXN_REF_NO");
					System.out.println("Audit txn ref no=" + referenceNumber);
				}

				if (referenceNumber != null) {
					try {
					Transaction transaction = new Transaction();
					TransactionKey key = new TransactionKey();
					key.setId(referenceNumber);
					transaction = transaction.read(key);

					System.out.println("Reading transaction domain for audit log=" + transaction.getKey().getId());
					auditDTO.setStartTime(transaction.getLastUpdatedDate());
					}catch(java.lang.Exception e) {
						e.printStackTrace();
						auditDTO.setStartTime(new Date());
					}
				}
			}

				System.out.println("CZAsyncAudit Handler User Id : " + auditDTO.getUserId() + " Task Code : "
						+ auditDTO.getTaskCode() + " Status : " + auditDTO.getStatus());

				if (com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN")!=null) {

					boolean flag =(boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN");
					if (flag) {

						if (com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN_Code")!=null) {
							String auditTaskCode=(String)com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN_Code");
							auditDTO.setTaskCode(auditTaskCode);
							auditDTO.setAction(Action.REJECTED);
							if (com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN_ID")!=null) {
								String refNo=(String)com.ofss.digx.infra.thread.ThreadAttribute.get("MAKER_CANEL_TXN_ID");
								auditDTO.setReferenceNo(refNo);
							}

						}
					}
				}


				if (auditDTO.getTaskCode() != null && auditDTO.getTaskCode().equals("IVM_R_A")) {
					System.out.println("** Inside audit IVM_R_A , status: " + auditDTO.getStatus());
					if(com.ofss.digx.enumeration.audit.Status.FAILURE.equals(auditDTO.getStatus())) {
						auditDTO.setId("");
						auditDTO.setPartyId(null);
						auditDTO.setUserId("");
						auditDTO.setUserName("");
						auditDTO.setPartyName("");
						auditDTO.setHashValue("");
						auditDTO.setAccessKey("");
						auditDTO.setVerified(com.ofss.digx.enumeration.audit.Status.SUCCESS);
						List<AuditDetailsDTO> collect = auditDTO.getAuditDetailsDTOList().stream().filter(e -> !(e.getRequest() instanceof BatchTransferRequestDTO)).collect(Collectors.toList());
						auditDTO.setAuditDetailsDTOList(collect);
					} else {
						if(auditDTO.getResolvedRequestUrl()!=null && auditDTO.getResolvedRequestUrl().contains("getRaq")) {
							System.out.println("IVM_R_A audit log getRaq");
							auditDTO.setAction(Action.INITIATED);
						} else if(auditDTO.getResolvedRequestUrl()!=null && auditDTO.getResolvedRequestUrl().contains("create")) {
							System.out.println("IVM_R_A audit log create");
							auditDTO.setAction(Action.CREATED);
						} else {
							System.out.println("IVM_R_A audit log ENQUIRED");
							if (auditDTO.getAction() != null) {
								auditDTO.setAction(Action.ENQUIRED);
							}
						}
					}
				}


				if (!ANONYMOUS_USER.equals(auditDTO.getUserId()) || !auditDTO.getTaskCode().equalsIgnoreCase("PC_CM_ME")
						|| !auditDTO.getStatus().equals(com.ofss.digx.enumeration.audit.Status.FAILURE)) {
					if(!(auditDTO.getResolvedRequestUrl()!=null && auditDTO.getResolvedRequestUrl().contains("requestModification"))) {

						if(auditDTO!=null && auditDTO.getTaskCode()!=null && auditDTO.getTaskCode().equals("MT_N_CFM")) {
							System.out.println("Inside getAsyncHandler().doAudit for Fps Merchant Addressing create");
						}
						if (auditDTO!=null && auditDTO.getTaskCode()!=null && auditDTO.getTaskCode().equals("EADESTMTC")) {
							System.out.println("Inside do audit for Account Preference Setting create");
							if(validationOnly)
								break;
						}

						if(auditDTO!=null && auditDTO.getTaskCode()!=null && auditDTO.getTaskCode().equals("PC_F_BTFT")) {
							auditDTO.setId("");
							auditDTO.setUserName("");
							auditDTO.setPartyName("");
							auditDTO.setHashValue("");
							auditDTO.setAccessKey("");
							auditDTO.setVerified(com.ofss.digx.enumeration.audit.Status.SUCCESS);
							List<AuditDetailsDTO> collect = auditDTO.getAuditDetailsDTOList().stream().filter(e -> !(e.getRequest() instanceof BatchTransferRequestDTO)).collect(Collectors.toList());
							auditDTO.setAuditDetailsDTOList(collect);
						}
						System.out.println("insert audit log "+ auditDTO.toString());
						getAsyncHandler().doAudit(auditDTO);
					}
				}



			System.out.println("Sent Audit DTO for task code "+ auditDTO.getTaskCode() );
			break;
		case ALL:
			break;
		case ENDPOINT:
			break;

		}

	}

	}

	private void pushIntoAuditStack(AuditDetailsDTO auditDetailsDTO) {
		Stack<AuditDetailsDTO> auditItemStack;

		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK) == null) {
			auditItemStack = new Stack<>();
			com.ofss.digx.infra.thread.ThreadAttribute
					.set(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK, auditItemStack);
		} else {
			auditItemStack = (Stack<AuditDetailsDTO>) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.AUDIT_DETAILS_STACK);
		}
		auditItemStack.push(auditDetailsDTO);
	}

	public IAuditHandler getAsyncHandler() {
		return new AsyncJMSAuditHandler();
	}

	/**
	 * Returns unique instance of AsyncAuditHandler
	 *
	 * @return AsyncAuditHandler
	 */
	public static CZAsyncAuditHandler getInstance() {
		if (singletonInstance == null) {
			synchronized (CZAsyncAuditHandler.class) {
				if (singletonInstance == null) {
					singletonInstance = new CZAsyncAuditHandler();
				}
			}
		}
		return singletonInstance;

	}

	@Override
	public void doApiAudit(APIAuditDTO audit) throws Exception {
	}

	private List<String> getAuditExclusionTaskTaskList() {
		String taskList = null;
		StringBuilder task = new StringBuilder();
		taskList = auditPreference.get(AuditConstants.AUDIT_REQUEST_TASK_EXCLUSION_LIST, "");
		task.append(taskList);
		return Arrays.asList(task.toString().split(","));
	}

	protected Status fetchStatus() {
		Status status = (Status) com.ofss.fc.infra.thread.ThreadAttribute.get("STATUS");
		if (status == null) {
			status = new Status();
			com.ofss.fc.infra.thread.ThreadAttribute.set("STATUS", status);
		}
		return status;

	}


	private String getHighRiskIndicator(String taskCode) {
		initializeHighRiskTxns();
		if (highRiskTxnList.contains(taskCode)) {
			return "Y";
		}else {
			return "N";
		}
	}

	private String getIPRangeValue(String ipAddress) {

		String ipArray[]=ipAddress.split("\\.");
		if (ipArray.length==4) {
		int w=Integer.parseInt(ipArray[0]);
		int x =Integer.parseInt(ipArray[1]);
		int y=Integer.parseInt(ipArray[2]);
		int z=Integer.parseInt(ipArray[3]);
		long ipRangeValue = 16777216l*w+ 65536l*x+ 256*y +  z;
		System.out.println("The IP range value is "+ipRangeValue);
		return Long.toString(ipRangeValue);
		}else {
			return "";
		}

	}

	private boolean getOMBAuditFlag() {
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter("CUSTOM_CONFIG_ADAPTER");
		String flag = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "OMB_AUDIT_FLAG", "false");
		return Boolean.parseBoolean(flag);
	}
}
