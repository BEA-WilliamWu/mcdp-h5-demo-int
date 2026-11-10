package com.ofss.digx.cz.bea.app.sms.service.user.ext;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.core.adapter.AdapterFactory;
import com.ofss.digx.core.adapter.alert.eventgen.IModuleToAlertAdapter;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.security.dto.authentication.UserSecurityQuestionActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfServChangeSignerPinLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.common.constants.UserExtensionDataConstants;
import com.ofss.digx.cz.bea.common.constants.CZCommonErrorConstants;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecord;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecordKey;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.infra.validation.error.ValidationError;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.utils.SerializationUtils;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class CZUserExtensionDataExt extends VoidUserExtensionDataExt implements IUserExtensionDataExt {
    public static final String HTH_LOGIN_PIN_RESET_NOTIFICATION = "HTH_LOGIN_PIN_RESET_NOTIFICATION";

	
	private static final String IS_ADMIN = "isAdmin";
	
	private static final String IS_OMB_ENABLED = "IS_OMB_ENABLED";
	
	private static final int CONSTANT_ZERO = 0;
	private static final int CONSTANT_FOUR = 4;
	private static final int CONSTANT_FIVE = 5;
	private static final int CONSTANT_SEVEN = 7;
	private static final int CONSTANT_EIGHT = 8;
	private static final int CONSTANT_NINETEEN = 19;
	private static final String ACCOUNT_START_STR = "015";

	private static final String RESET_REJECT_REMARK = "The transaction was rejected due to new application for resetting Signer PIN has been submitted by company's Authorised Person through BEA website/Branch.";

    public CZUserExtensionDataExt() {
    }

    public void preRead(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
        super.preRead(sessionContext, requestDTO);
    }

    public void postRead(SessionContext sessionContext, UserExtensionDataDTO requestDTO, UserExtensionDataResponseDTO response) throws Exception {
        super.postRead(sessionContext, requestDTO, response);
    }

    public void preUpdate(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
        super.preUpdate(sessionContext, requestDTO);
    }

    public void postUpdate(SessionContext sessionContext, UserExtensionDataDTO requestDTO, TransactionStatus transactionStatus) throws Exception {
    	BeaSystemOut.println("CZUserExtensionDataExt enter into postUpdate BypassFlag=" + requestDTO.getBypassFlag() + ", BypassCode=" + requestDTO.getBypassCode());
        Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN);
        BeaSystemOut.println("CZUserExtensionDataExt postUpdate admin type isAdmin ? " + isAdmin);
        if (isAdmin != null && !isAdmin) {
	        String txnid = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
	        BeaSystemOut.println("CZUserExtensionDataExt postUpdate txnid=" + txnid);
	        boolean flag = requestDTO!=null && requestDTO.getCdcNo()!=null && !"".equals(requestDTO.getCdcNo())
	        		&& requestDTO.getUserID()!=null && !"".equals(requestDTO.getUserID());
	        if(txnid != null && !"".equals(txnid) && flag
                && !Boolean.FALSE.equals(com.ofss.digx.infra.thread.ThreadAttribute.get(HTH_LOGIN_PIN_RESET_NOTIFICATION))) {
	            com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
	            final com.ofss.digx.framework.domain.transaction.TransactionKey transactionKey = new com.ofss.digx.framework.domain.transaction.TransactionKey();
	            transactionKey.setId(txnid);
	            transaction = transaction.read(transactionKey);
	        	if(transaction.getApprovalDetails() != null && transaction.getApprovalDetails().getSignedBy() != null) {
	        		BeaSystemOut.println("CZUserExtensionDataExt postUpdate SignedBy=" + transaction.getApprovalDetails().getSignedBy());
	        		String[] signers = transaction.getApprovalDetails().getSignedBy().split("~");
	        		Predicate<String> filterNon = it -> it != null && !"".equals(it);
	        		List<String> signerList = Stream.of(signers).filter(filterNon).collect(Collectors.toList());
	        		signerList.add(requestDTO.getUserID());
	        		Collections.reverse(signerList);
	        		this.sendNotifications(sessionContext, requestDTO, signerList.stream().distinct().collect(Collectors.toList()), requestDTO.getUserID());
	        	} else {
	        		boolean OMBflag = false;
	        		if (com.ofss.fc.infra.thread.ThreadAttribute.get(IS_OMB_ENABLED) != null) {
	        			OMBflag = (Boolean) com.ofss.fc.infra.thread.ThreadAttribute.get(IS_OMB_ENABLED);
	        			BeaSystemOut.println("OMB for transaction is enabled and true==" + OMBflag);
	        		}
	        		BeaSystemOut.println("CZUserExtensionDataExt postUpdate OMB : OMBflag : " + OMBflag);
	        		if (OMBflag) {
						BeaSystemOut.println("CZUserExtensionDataExt postUpdate OMB Flow : sessionContext.getUserId() : " + sessionContext.getUserId());
						List<String> userList = new ArrayList<>();
						userList.add(requestDTO.getUserID());
						userList.add(sessionContext.getUserId());
						this.sendNotifications(sessionContext, requestDTO, userList, requestDTO.getUserID());
					}
	        	}
	        }
        } else {
        	UserExtensionDataDTO userRequestDTO = requestDTO;
			BeaSystemOut.println("CZUserExtensionDataExt postUpdate-----------------------------------userRequestDTO ="+SerializationUtils.toJsonString(userRequestDTO));
			if(userRequestDTO.getSignerPinReferenceNo() != null && !"".equals(userRequestDTO.getSignerPinReferenceNo())
					&& "Being Reset".equals(userRequestDTO.getSignerPinstatus())) {
				BeaSystemOut.println("CZUserExtensionDataExt postUpdate-----------------------------------userRequestDTO.getSignerPinstatus():" + userRequestDTO.getSignerPinstatus());
				String partyId = userRequestDTO.getCdcNo();
				String userId = userRequestDTO.getUserID();
				BeaSystemOut.println("CZUserExtensionDataExt postUpdate-----------------------------------partyId:" + partyId + ", userId:"+userId);

				IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
	            		com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
	            IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
	            		.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
	        	List<Object[]> transactionList = hostuserDetailsAdapter.listChangeSignerPinPendingTransactions(partyId);

				ResetUserPinRecord record = new ResetUserPinRecord();
				List<ResetUserPinRecord> otherApprovalPendingRecords = record.listPendingResetUserPinRecordList(partyId, userId, Arrays.asList("PENDING_APPROVAL","APPROVED"));
				Optional.ofNullable(otherApprovalPendingRecords).orElse(Collections.emptyList()).stream()
				.forEach(it -> rejectOtherPendingRecord(sessionContext, it, transactionList, hostuserDetailsAdapter));
			}
        }
    }

    private void rejectOtherPendingRecord(SessionContext sessionContext, ResetUserPinRecord record,
    		List<Object[]> transactionList, IHostUserDetailsInvocationAdapter hostuserDetailsAdapter) {
        BeaSystemOut.println("CZUserExtensionDataExt rejectOtherPendingRecord in and record="+SerializationUtils.toJsonString(record));
		try {
			boolean handleFlag = false;

			if (transactionList != null && !transactionList.isEmpty()) {
	        	for(Object[] item : transactionList) {
	        		if(item != null && item.length==3) {
	    				String transactionUserID = String.valueOf(item[0]);
	    				String pendingTransactionId = String.valueOf(item[1]);
	    				String approveStatus = String.valueOf(item[2]);
	    				BeaSystemOut.println("CZUserExtensionDataExt rejectOtherPendingRecord : transactionUserID=" + transactionUserID
	    						+", pendingTransactionId="+pendingTransactionId+", approveStatus="+approveStatus);

	    				if(!handleFlag && pendingTransactionId.equals(record.getResetUserPinRecordKey().getKey())
		        				&& (ApprovalStatus.PENDING_APPROVAL==ApprovalStatus.fromValue(approveStatus)
		        				|| (ApprovalStatus.APPROVED==ApprovalStatus.fromValue(approveStatus) && Arrays.asList("PENDING_MODIFICATION", "MODIFIED", "EFFECTIVE").contains(record.getResetStatus())))) {

	    					BeaSystemOut.println("CZUserExtensionDataExt rejectOtherPendingRecord userDataDTO.UserID: " + transactionUserID);
							if(ApprovalStatus.PENDING_APPROVAL==ApprovalStatus.fromValue(approveStatus)) {
								hostuserDetailsAdapter.rejectOtherPendingTransaction(pendingTransactionId, RESET_REJECT_REMARK);
							}

							handleRejectUpdateRecord(sessionContext, record, ApprovalStatus.fromValue(approveStatus));
							handleFlag = true;
							break;
	    				}
		        	}
				}
			}
			if(!handleFlag) {
				handleRejectUpdateRecord(sessionContext, record, ApprovalStatus.EXPIRED);
			}
		} catch (Exception e) {
			BeaSystemOut.printErr(e);
		}
	}

	private void handleRejectUpdateRecord(SessionContext sessionContext, ResetUserPinRecord record, ApprovalStatus approveStatus) throws Exception {
		ResetUserPinRecord updateRecord = new ResetUserPinRecord();
		ResetUserPinRecordKey updateRecordKey = new ResetUserPinRecordKey();
		updateRecordKey.setKey(record.getResetUserPinRecordKey().getKey());
		updateRecord.setResetUserPinRecordKey(updateRecordKey);
		updateRecord = updateRecord.read(updateRecordKey);
		BeaSystemOut.println("CZUserExtensionDataExt rejectOtherPendingRecord updateRecord="+SerializationUtils.toJsonString(updateRecord));
		updateRecord.setWorkflowStatus("REJECTED");
		updateRecord.setForceChangePin("N");
		updateRecord.setEffectiveTime(null);
		updateRecord.setLastUpdatedBy(record.getLastUpdatedBy() + "~" + sessionContext.getUserId());
		updateRecord.setLastUpdatedDate(new Date());
		updateRecord.setApproveTime(new Date());
		updateRecord.update(updateRecord);
		if (approveStatus==ApprovalStatus.PENDING_APPROVAL){
            //BCOCDC-8218
            sendExceptionHandlingNotifications(record.getPartyId(),record.getUserId(),UserExtensionDataConstants.SYSTEM_REJECT_RESET_SIGNER_PIN_EMAIL_EVENT,"SYSTEM_REJECT_RESET_SIGNER_PIN_SMS");
        }else {
            //BCOCDC-6742/6741
            sendExceptionHandlingNotifications(record.getPartyId(),record.getUserId(),UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_EMAIL_EVENT,"DUPLICATED_SIGNER_PIN_REQUEST_SMS");
        }
	}

    public void preCreate(SessionContext sessionContext, UserExtensionDataDTO requestDTO) throws Exception {
        super.preCreate(sessionContext, requestDTO);
    }

    public void postCreate(SessionContext sessionContext, UserExtensionDataDTO requestDTO, UserExtensionDataResponseDTO response) throws Exception {
        super.postCreate(sessionContext, requestDTO, response);
    }

//BCOCDC-6741/6742/8218  AC002 start
    private void sendExceptionHandlingNotifications(String partyId,String userId,String emailEventCode,String smsEventCodeType) throws Exception {
        BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications parameters  partyId= "
                + partyId +"//////userId= "+userId + "////emailEventCode="+emailEventCode+"///smsEventCodeType="+smsEventCodeType);
        try {
            if (StringUtils.isBlank(partyId) || StringUtils.isBlank(userId)) {
            	BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications parameter userId or partyId is null");
                return;
            }
            List<String> userList = new ArrayList<String>();
            Set<String> sendEmailSet = new HashSet<>();
            String userMobileNumber = null;

            userList.add(userId);
            String resetSignerPinAccountNumber = getFormatAccountNumber(CZAccountHelper.formatExtAccountNumber(partyId));
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications resetSignerPinAccountNumber= " + resetSignerPinAccountNumber);

            // All AP's Email Address and Users' Email Address
            com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
            UserExtensionDataKey key = new UserExtensionDataKey();
            key.setUserExtensionKey(userId);
            domain.setUserExtensionDataKey(key);
            domain = domain.read(key);
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications domain= " + SerializationUtils.toJsonString(domain));
            List<String> apUsers = listAPUsersByParty(domain);
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications apUsers= " + SerializationUtils.toJsonString(apUsers));
            if(CollectionUtils.isNotEmpty(apUsers)){
                userList.addAll(apUsers);
            }
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications userList= " +SerializationUtils.toJsonString(userList));

            // get companyEmail Address
            userList.add(userId);

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
                    BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications userDomain= " + SerializationUtils.toJsonString(userDomain)+"/////////signerId="+signerId);
                    String signerEmail = userDomain.getEmailId();
                    if (StringUtils.isNotBlank(signerEmail)) {
                        sendEmailSet.add(signerEmail);
                    }
                    if (StringUtils.isNotBlank(userId) && userId.equals(signerId)) {
                        userMobileNumber = userDomain.getMobileNumber();
                        BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications userMobileNumber=" + userMobileNumber);
                    }
                }
            }
            String userName = getUsernameFromUserId(userId);
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications userName= " + userName);

            //send Email
            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications sendEmailSet= " + SerializationUtils.toJsonString(sendEmailSet));
            if (CollectionUtils.isNotEmpty(sendEmailSet)) {
                sendEmailEvent(partyId,userName,emailEventCode,sendEmailSet,resetSignerPinAccountNumber);
            }

            BeaSystemOut.println("CZUserExtensionDataExt sendExceptionHandlingNotifications userMobileNumber= " + userMobileNumber);
            //send user SMS
            if (StringUtils.isNotBlank(userMobileNumber)) {
                sendMobileNumberEvent(userId,partyId,userMobileNumber,smsEventCodeType);
            }
        } catch (Exception e) {
            BeaSystemOut.printErr(e);
        }
    }

	private void sendEmailEvent(String partyId, String userName, String emailEventCode, Set<String> sendEmailSet,
			String resetSignerPinAccountNumber) throws Exception {
		BeaSystemOut.println("CZUserExtensionDataExt sendEmailEvent parameter partyId= " + partyId + ",userName=" + userName
				+ ",emailEventCode=" + emailEventCode + ",sendEmailSet=" + SerializationUtils.toJsonString(sendEmailSet)
				+ ",resetSignerPinAccountNumber=" + resetSignerPinAccountNumber);
		IModuleToAlertAdapter alertAdapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
		SelfServChangeSignerPinLogDTO emailActivityLog = new SelfServChangeSignerPinLogDTO();
		emailActivityLog.setResetSignerPinUserName(userName);
		emailActivityLog.setResetSignerPinAccountNumber(resetSignerPinAccountNumber);
		for (String signerEmail : sendEmailSet) {
            BeaSystemOut.println("CZUserExtensionDataExt sendEmailEvent:ready to send email : "
                    + signerEmail);
            NotificationDetail emailNotificationDetail = new NotificationDetail();
            NotificationDetail[] details = new NotificationDetail[1];
            emailNotificationDetail.setRecipientId(partyId);
            emailNotificationDetail.setDestination(DestinationType.EMAIL);
            emailNotificationDetail.setDispatchAddress(signerEmail);
            emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

            details[0] = emailNotificationDetail;
            emailActivityLog.setNotificationDetails(details);
            emailActivityLog.setCustomerId(partyId);

            BeaSystemOut.println("CZUserExtensionDataExt sendEmailEvent to send EMAIL : emailActivityLog="+SerializationUtils.toJsonString(emailActivityLog));
            alertAdapter.registerActivityAndGenerateEvent("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin",
                    emailEventCode, new Date(),
                    emailActivityLog);
            BeaSystemOut.println("CZUserExtensionDataExt sendEmailEvent:send email  ok email="+signerEmail);
        }
	}

	private void sendMobileNumberEvent(String userId,String partyId,String mobileNumber,String smsEventCodeType) throws Exception{
		BeaSystemOut.println("CZUserExtensionDataExt sendMobileNumberEvent:ready to send SMS : parameters partyId="+partyId +",mobileNumber="+mobileNumber+",smsEventCodeType="+smsEventCodeType+",userId="+userId);
        IModuleToAlertAdapter alertAdapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
        SelfServChangeSignerPinLogDTO smsActivityLog = new SelfServChangeSignerPinLogDTO();
        NotificationDetail[] details = new NotificationDetail[1];
        NotificationDetail messageNotificationDetail = new NotificationDetail();
        messageNotificationDetail.setRecipientId(partyId);
        messageNotificationDetail.setDestination(DestinationType.SMS);
        messageNotificationDetail.setDispatchAddress(mobileNumber);
        messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());

        details[0] = messageNotificationDetail;
        smsActivityLog.setNotificationDetails(details);
        smsActivityLog.setCustomerId(partyId);
        smsActivityLog.setUserId(userId);
        BeaSystemOut.println("CZUserExtensionDataExt sendMobileNumberEvent:send to send SMS : smsActivityLog="+SerializationUtils.toJsonString(smsActivityLog));
        String newSmsEventCode = getSmsEventCodeByUserLocale(userId,smsEventCodeType);
        alertAdapter.registerActivityAndGenerateEvent("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.selfServChangeSignerPin",
                newSmsEventCode, new Date(),
                smsActivityLog);
        BeaSystemOut.println("CZUserExtensionDataExt sendMobileNumberEvent:send SMS ok, mobileNumber="+mobileNumber);
	}


	private String getSmsEventCodeByUserLocale(String userId, String smsEventCodeType) throws Exception {
		BeaSystemOut.println(
				"CZUserExtensionDataExt getSmsEventCodeByUserLocale userId=" + userId + ",smsEventCodeType=" + smsEventCodeType);
		String newSmsEventCode = null;

        IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
                com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
        IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
                .getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
        String locale = hostuserDetailsAdapter.getUserLastLoginLocale(userId);

		BeaSystemOut.println("CZUserExtensionDataExt getSmsEventCodeByUserLocale User Locale: " + locale);
		if (locale != null && !locale.equalsIgnoreCase("") && locale.equalsIgnoreCase("zh-Hant")) {
			if (smsEventCodeType.equals("SYSTEM_REJECT_RESET_SIGNER_PIN_SMS")) {
				newSmsEventCode = UserExtensionDataConstants.SYSTEM_REJECT_RESET_SIGNER_PIN_SMS_TC;
			} else {
				newSmsEventCode = UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_TC;
			}
		} else if (locale != null && !locale.equalsIgnoreCase("") && locale.equalsIgnoreCase("zh-Hans-CN")) {
			if (smsEventCodeType.equals("SYSTEM_REJECT_RESET_SIGNER_PIN_SMS")) {
				newSmsEventCode = UserExtensionDataConstants.SYSTEM_REJECT_RESET_SIGNER_PIN_SMS_SC;
			} else {
				newSmsEventCode = UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_SC;
			}
		} else if (locale != null && !locale.equalsIgnoreCase("") && locale.equalsIgnoreCase("en")) {
			if (smsEventCodeType.equals("SYSTEM_REJECT_RESET_SIGNER_PIN_SMS")) {
				newSmsEventCode = UserExtensionDataConstants.SYSTEM_REJECT_RESET_SIGNER_PIN_SMS_EN;
			} else {
				newSmsEventCode = UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_EN;
			}
		} else {
			if (smsEventCodeType.equals("SYSTEM_REJECT_RESET_SIGNER_PIN_SMS")) {
				newSmsEventCode = UserExtensionDataConstants.SYSTEM_REJECT_RESET_SIGNER_PIN_SMS_EN;
			} else {
				newSmsEventCode = UserExtensionDataConstants.DUPLICATED_SIGNER_PIN_REQUEST_SMS_EN;
			}
		}
		BeaSystemOut.println(
				"CZUserExtensionDataExt getSmsEventCodeByUserLocale newSmsEventCode=" + newSmsEventCode);

		return newSmsEventCode;
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
                "CZUserExtensionDataExt getCompanyEmail companyEmail= " + companyEmail);
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

	private List<String> listAPUsersByParty(com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain) throws Exception {
		if(domain != null && domain.getCdcNo() != null) {
			List<com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData> userExtensionDataList = domain.listUsersByParty(domain);
			//ap userId
			List<String> apUserList =
					Optional.ofNullable(userExtensionDataList).orElse(Collections.emptyList())
					.stream().filter(com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData::getIsAuthorisedPerson)
					.map(user -> user.getUserExtensionDataKey().getUserExtensionKey()).collect(Collectors.toList());
			BeaSystemOut.println("CZUserExtensionDataExt listAPUsersByParty : apUserList=" + SerializationUtils.toJsonString(apUserList));
			return apUserList;
		}
		return null;
	}

    private String getFormatAccountNumber(String accountNumber) throws Exception {
    	BeaSystemOut.println("CZUserExtensionDataExt getFormatAccountNumber start accountNumber= " + accountNumber);
        String formatAccountNumber = accountNumber;
        if (formatAccountNumber.startsWith(ACCOUNT_START_STR)) {
            if (formatAccountNumber.length() == CONSTANT_NINETEEN) {
                BeaSystemOut.println("CZUserExtensionDataExt getFormatAccountNumber Account is standalone so masking will be different : " + formatAccountNumber);
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - CONSTANT_EIGHT, formatAccountNumber.length() - CONSTANT_FIVE, '*');
            } else {
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - CONSTANT_SEVEN, formatAccountNumber.length() - CONSTANT_FOUR, '*');
            }
        } else {
            formatAccountNumber = formatAccountNumber.replaceAll(".(?=.{5})", "*");
        }
        BeaSystemOut.println("CZUserExtensionDataExt getFormatAccountNumber end accountNumber= " + accountNumber);
    	return formatAccountNumber;
    }

    private void sendNotifications(SessionContext sessionContext, UserExtensionDataDTO requestDTO, List<String> signerList, String updateUserId) throws Exception {
    	String partyId = requestDTO.getCdcNo();
    	String bypassFlag = requestDTO.getBypassFlag();
    	BeaSystemOut.println("CZUserExtensionDataExt enter into sendNotifications partyId: " + partyId + ", updateUserId: " + updateUserId);
    	String userEventId = null;
    	String companyEventId = null;
    	if(bypassFlag != null && bypassFlag.equals("Y")){
    		userEventId = "LOGIN_PIN_RESET_ENABLE_REMINDER_CORPORATE_USER";
    		companyEventId = "LOGIN_PIN_RESET_ENABLE_REMINDER_COMPANY";
    	} else {
    		userEventId = "LOGIN_PIN_RESET_DISABLE_REMINDER_CORPORATE_USER";
    		companyEventId = "LOGIN_PIN_RESET_DISABLE_REMINDER_COMPANY";
    	}
        BeaSystemOut.println("CZUserExtensionDataExt enter into sendNotifications userEventId: " + userEventId + ", companyEventId: " + companyEventId);
        
        Set<String> sendedEmailAddressSet = new HashSet<>();
        IModuleToAlertAdapter alertAdapter = AdapterFactory.getInstance().getAdapter(IModuleToAlertAdapter.class);
        
        UserSecurityQuestionActivityLogDTO emailActivityLog = new UserSecurityQuestionActivityLogDTO();
        String[] formatDateArray = this.getFormatDate(requestDTO.getBypassExpiryTime());
        emailActivityLog.setResetLoginPinExpiredDateTime(formatDateArray[0]);
        emailActivityLog.setResetLoginPinExpiredYear(formatDateArray[1]);
        emailActivityLog.setResetLoginPinExpiredMonth(formatDateArray[2]);
        emailActivityLog.setResetLoginPinExpiredDay(formatDateArray[3]);
        emailActivityLog.setResetLoginPinExpiredHour(formatDateArray[4]);
        emailActivityLog.setResetLoginPinExpiredMinute(formatDateArray[5]);
        emailActivityLog.setResetLoginPinExpiredSecond(formatDateArray[6]);

        String currentLoginId = updateUserId;
        if(Objects.nonNull(updateUserId) && updateUserId.indexOf("@") != -1) {
			String[] userDtls = updateUserId.split("@");
			currentLoginId = userDtls[0];
			BeaSystemOut.println("CZUserExtensionDataExt sendNotifications get currentLoginId = " + currentLoginId);
		}        
		emailActivityLog.setResetLoginPinUserName(currentLoginId);
		String formatAccountNumber = CZAccountHelper.formatExtAccountNumber(partyId);
        if (formatAccountNumber.startsWith("015")) {
            if (formatAccountNumber.length() == 19) {
                BeaSystemOut.println("Account is standalone so masking will be different : " + formatAccountNumber);
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - 8, formatAccountNumber.length() - 5, '*');
            } else {
                formatAccountNumber = maskActNo(formatAccountNumber, formatAccountNumber.length() - 7, formatAccountNumber.length() - 4, '*');
            }
        } else {
        	formatAccountNumber = formatAccountNumber.replaceAll(".(?=.{5})", "*");
        }
		emailActivityLog.setResetLoginPinAccountNumber(formatAccountNumber);
		
        for(String userId : signerList) {
            User userDomain = new User();
            UserKey userKey = new UserKey();
            userKey.setUserId(userId);
            userDomain = userDomain.read(userKey);
            BeaSystemOut.println("CZUserExtensionDataExt sendNotifications AccountNumber:- " + userDomain.getAccountNumber());
            if (userDomain.getEmailId() != null && !"".equals(userDomain.getEmailId()) && !sendedEmailAddressSet.contains(userDomain.getEmailId())) {
                BeaSystemOut.println("CZUserExtensionDataExt sendNotifications user Email, login user EmailId: " + userDomain.getEmailId());
                NotificationDetail emailNotificationDetail = new NotificationDetail();
                NotificationDetail[] notificationSetForEmail = new NotificationDetail[1];
                emailNotificationDetail.setRecipientId(partyId);
                emailNotificationDetail.setDestination(DestinationType.EMAIL);
                emailNotificationDetail.setDispatchAddress(userDomain.getEmailId());
                emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
                notificationSetForEmail[0] = emailNotificationDetail;
                emailActivityLog.setNotificationDetails(notificationSetForEmail);
                emailActivityLog.setCustomerId(partyId);
                BeaSystemOut.println("CZUserExtensionDataExt sendNotifications***Before triggering user Email notification in activityLog : " + emailActivityLog.toString());
        		alertAdapter.registerActivityAndGenerateEvent("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update",
        				userEventId, new Date(), emailActivityLog);
                BeaSystemOut.println("CZUserExtensionDataExt sendNotifications*********After triggering user Email notification");
                sendedEmailAddressSet.add(userDomain.getEmailId());
            }
        }
        
		com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
				.getInstance().getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
		IUserExtensionAdapter userExtensionAdapter = (IUserExtensionAdapter) adapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
        CZPartyPreferenceDTO partyDetails = userExtensionAdapter.getPartyPreferences(partyId);
        String officeEmail = partyDetails.getOfficeEmailId();
        if (partyDetails != null && officeEmail != null && !"".equals(officeEmail) && !sendedEmailAddressSet.contains(officeEmail)) {
            BeaSystemOut.println("CZUserExtensionDataExt sendNotifications Office Email: " + officeEmail);
            NotificationDetail[] details = new NotificationDetail[1];
            NotificationDetail emailNotificationDetail = new NotificationDetail();
            emailNotificationDetail.setRecipientId(partyId);
            emailNotificationDetail.setDestination(DestinationType.EMAIL);
            emailNotificationDetail.setDispatchAddress(officeEmail);
            emailNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
            details[0] = emailNotificationDetail;
            emailActivityLog.setNotificationDetails(details);
            emailActivityLog.setCustomerId(partyId);
            BeaSystemOut.println("CZUserExtensionDataExt sendNotifications***Before triggering company Email notification in activityLog : " + emailActivityLog.toString());
            alertAdapter.registerActivityAndGenerateEvent("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update",
            		companyEventId, new Date(),	emailActivityLog);
            BeaSystemOut.println("CZUserExtensionDataExt sendNotifications*********After triggering company Email notification");
            sendedEmailAddressSet.add(officeEmail);
        }
        BeaSystemOut.println("CZUserExtensionDataExt sendNotifications*********Done");
    }

    private String[] getFormatDate(Date expiryDateTime) {
        LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDateTime newDateTime = null;
		if (expiryDateTime == null || expiryDateTime.getYear() == 1970) {
			newDateTime = currentDateTime;
		} else {
			newDateTime = LocalDateTime.of(expiryDateTime.getYear(), expiryDateTime.getMonth(), expiryDateTime.getDayOfMonth(),
					currentDateTime.getHour(), currentDateTime.getMinute());
		}
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String[] dateTimeArrays = new String[]{newDateTime.format(formatter) + " 23:59:59", String.valueOf(newDateTime.getYear()), 
        		String.valueOf(newDateTime.getMonthValue()), String.valueOf(newDateTime.getDayOfMonth()), "23", "59", "59"};
        return dateTimeArrays;
    }

    private static String maskActNo(String strText, int start, int end, char maskChar) throws Exception {
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

//BCOCDC-6741/6742/8218 AC002  end
}
