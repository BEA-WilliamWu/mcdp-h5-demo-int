package com.ofss.digx.cz.bea.app.sms.service.user.ext;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.core.adapter.AdapterFactory;
import com.ofss.digx.core.adapter.alert.eventgen.IModuleToAlertAdapter;
import com.ofss.digx.cz.bea.app.crm.adapter.ICRMAsserterCallAdapter;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.itoken.dto.ITokenCancelResponseDTO;
import com.ofss.digx.cz.bea.app.itoken.dto.ITokenDTO;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.security.dto.authentication.UserSecurityQuestionActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.SelfServChangeSignerPinLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserTokenDataDTO;
import com.ofss.digx.cz.bea.common.constants.*;
import com.ofss.digx.cz.bea.common.framework.crm.CRMConstants;
import com.ofss.digx.cz.bea.common.framework.crm.CRMInputData;
import com.ofss.digx.cz.bea.common.constants.UserExtensionDataConstants;
import com.ofss.digx.cz.bea.common.constants.CZCommonErrorConstants;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecord;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecordKey;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.domain.sms.entity.user.User;
import com.ofss.digx.domain.sms.entity.user.UserKey;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserItokenInfo;
import com.ofss.digx.cz.bea.extxface.itoken.adapter.IITokenAdapter;
import com.ofss.digx.domain.sms.entity.user.UserPrincipal;
import com.ofss.digx.domain.sms.entity.user.UserPrincipalKey;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.framework.determinant.DeterminantResolver;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecord;
import com.ofss.digx.cz.bea.domain.sms.entity.user.ResetUserPinRecordKey;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.infra.validation.error.ValidationError;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.utils.SerializationUtils;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import org.apache.commons.lang3.StringUtils;

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
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class CZUserExtensionDataExt extends VoidUserExtensionDataExt implements IUserExtensionDataExt {
	
    public static final String HTH_LOGIN_PIN_RESET_NOTIFICATION = "HTH_LOGIN_PIN_RESET_NOTIFICATION";

	private static final String IS_ADMIN = "isAdmin";
	
	private static final String IS_OMB_ENABLED = "IS_OMB_ENABLED";

    private static final String OLD_USER_PRINCIPALS = "CZ_USER_EXTENSION_DATA_OLD_USER_PRINCIPALS";

    private static final String REQUESTED_USER_PRINCIPALS = "CZ_USER_EXTENSION_DATA_REQUESTED_USER_PRINCIPALS";

    private static final String REQUESTED_ITOKEN_STATUS = "CZ_USER_EXTENSION_DATA_REQUESTED_ITOKEN_STATUS";

    private static final String PRE_DOC_ID = "preDocId";

    private static final String PRE_DOC_TYPE = "preDocType";

    private static final String PRE_DOC_COUNTRY = "preDocCountry";

    private static final String SIGNER_ID_IS_EMPTY = "signerIdIsEmpty";

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
        com.ofss.digx.infra.thread.ThreadAttribute.set(REQUESTED_ITOKEN_STATUS,
                requestDTO == null || requestDTO.getItokenStatus() == null ? "" : requestDTO.getItokenStatus());
        if (requestDTO != null && !isBlank(requestDTO.getUserID())) {
            com.ofss.digx.infra.thread.ThreadAttribute.set(OLD_USER_PRINCIPALS, getUserPrincipals(requestDTO.getUserID()));
            if (requestDTO.getUserDTO() != null && requestDTO.getUserDTO().getApplicationRoles() != null) {
                com.ofss.digx.infra.thread.ThreadAttribute.set(REQUESTED_USER_PRINCIPALS,
                        new ArrayList<String>(requestDTO.getUserDTO().getApplicationRoles()));
            }
        }
    }

    public void postUpdate(SessionContext sessionContext, UserExtensionDataDTO requestDTO, TransactionStatus transactionStatus) throws Exception {
        BeaSystemOut.println("CZUserExtensionDataExt enter into postUpdate");
        handleItokenAfterUserUpdate(sessionContext, requestDTO, transactionStatus);
        Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN);
        BeaSystemOut.println("CZUserExtensionDataExt postUpdate admin type isAdmin ? " + isAdmin);
        if (isAdmin != null && !isAdmin) {
	        String txnid = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
	        BeaSystemOut.println("CZUserExtensionDataExt postUpdate txnid=" + txnid);
	        boolean flag = requestDTO!=null && requestDTO.getCdcNo()!=null && !"".equals(requestDTO.getCdcNo()) 
	        		&& requestDTO.getUserID()!=null && !"".equals(requestDTO.getUserID());
            // HTH contact edits must not emit PIN reset enable/disable reminders.
            // A missing marker retains the original BCO and other-operation behavior.
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

    private void handleItokenAfterUserUpdate(SessionContext sessionContext, UserExtensionDataDTO requestDTO,
                                             TransactionStatus transactionStatus) throws Exception {
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("start to cancel itoken");
        if (!isItokenManagementEnabled()) {
            return;
        }
        if (requestDTO == null || isBlank(requestDTO.getUserID())) {
            return;
        }
        String userId = requestDTO.getUserID();
        UserItokenInfo domain = new UserItokenInfo();
        String userItokenStatus = domain.getUserItokenStaus(userId);
        boolean isAdmin = Boolean.TRUE.equals(com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN));

        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate i-Token status=" + userItokenStatus + ", userId=" + userId);
        if (isAdmin && "Locked".equalsIgnoreCase(userItokenStatus) && isInactiveItokenStatus(requestDTO)) {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate unlock i-Token service Begin");
            IITokenAdapter adapter = getItokenAdapter("unLockItoken");
            adapter.unLockItoken(userId, sessionContext.getUserId());
            return;
        }

        if (!"Activated".equalsIgnoreCase(userItokenStatus)) {
            return;
        }

        String itokenStatus = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(REQUESTED_ITOKEN_STATUS);
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("update ItokenStatus to: "+itokenStatus);
        if (!isBlank(itokenStatus) && "Inactive".equalsIgnoreCase(itokenStatus)) {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("start update inactive to INT");
            String scenario = isAdmin ? UserItokenConstants.CANCEL_BY_BM : UserItokenConstants.CANCEL_BY_AP;
            cancelItoken(sessionContext, userId, "INT", scenario);
            return;
        }

        boolean isUpdatedUserRoles = isApproverRoleRemoved(userId);
        boolean isRemovedSignerId = isSignerRemoved(requestDTO);
        if (isRemovedSignerId) {
            isUpdatedUserRoles = true;
        }
        boolean isUpdatedUserProfile = isDocumentChanged(requestDTO);

        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate isUpdatedUserRoles=" + isUpdatedUserRoles
                + ", isUpdatedUserProfile=" + isUpdatedUserProfile + ", isRemovedSignerId=" + isRemovedSignerId);
        if (isUpdatedUserRoles || isUpdatedUserProfile) {
            String scenario = null;
            if (isUpdatedUserRoles && !isUpdatedUserProfile) {
                scenario = UserItokenConstants.USER_AP_AND_APPROVER_ROLE_REMOVED;
            } else if (!isUpdatedUserRoles && isUpdatedUserProfile) {
                scenario = UserItokenConstants.SUS_BY_PROFILE_CHANGE;
            }
            cancelItoken(sessionContext, userId, "SUS", scenario);

        }
    }

    private boolean isItokenManagementEnabled() {
        String enableItokenManagement = com.ofss.fc.infra.config.ConfigurationFactory.getInstance()
                .getConfigurations(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG)
                .get("ENABLE_ITOKEN_MANAGEMENT", "Y");
        return "Y".equals(enableItokenManagement);
    }

    private boolean isInactiveItokenStatus(UserExtensionDataDTO requestDTO) {
        return requestDTO.getItokenStatus() != null && "Inactive".equalsIgnoreCase(requestDTO.getItokenStatus());
    }

    private boolean isSignerRemoved(UserExtensionDataDTO requestDTO) {
        Object signerIdIsEmpty = com.ofss.digx.infra.thread.ThreadAttribute.get(SIGNER_ID_IS_EMPTY);
        return Boolean.FALSE.equals(signerIdIsEmpty) && isBlank(requestDTO.getSignerID());
    }

    private boolean isDocumentChanged(UserExtensionDataDTO requestDTO) {
        String oldDocumentID = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(PRE_DOC_ID);
        String oldDocumentType = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(PRE_DOC_TYPE);
        String oldDocumentCountry = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(PRE_DOC_COUNTRY);

        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate before modification DocumentType: " + oldDocumentType);
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate before modification DocumentCountry: " + oldDocumentCountry);
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate before modification DocumentID: " + oldDocumentID);

        return !Objects.equals(requestDTO.getDocumentType(), oldDocumentType)
                || !Objects.equals(requestDTO.getDocumentCountry(), oldDocumentCountry)
                || !Objects.equals(requestDTO.getDocumentID(), oldDocumentID);
    }

    private boolean isApproverRoleRemoved(String userId) throws Exception {
        List<String> oldPrincipals = getThreadStringList(OLD_USER_PRINCIPALS);
        List<String> requestedPrincipals = getThreadStringList(REQUESTED_USER_PRINCIPALS);
        List<String> newPrincipals = requestedPrincipals.isEmpty() ? getUserPrincipals(userId) : requestedPrincipals;

        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate old user roles: " + oldPrincipals);
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate new user roles: " + newPrincipals);
        return hasApproverRole(oldPrincipals) && !hasApproverRole(newPrincipals);
    }

    private boolean hasApproverRole(List<String> principals) {
        return principals != null && (principals.contains(UserPrincipalConstants.USER_ROLE_AP)
                || principals.contains(UserPrincipalConstants.USER_ROLE_APPROVER));
    }

    private List<String> getUserPrincipals(String userId) throws Exception {
        List<String> principals = new ArrayList<>();
        if (isBlank(userId)) {
            return principals;
        }
        UserPrincipal userPrincipal = new UserPrincipal();
        UserPrincipalKey key = new UserPrincipalKey();
        key.setUsername(userId);
        userPrincipal.setKey(key);
        List<UserPrincipal> listUserPrincipal = userPrincipal.search(userPrincipal);
        if (listUserPrincipal != null) {
            for (UserPrincipal principal : listUserPrincipal) {
                if (principal != null && principal.getKey() != null && principal.getKey().getPrincipal() != null) {
                    principals.add(principal.getKey().getPrincipal());
                }
            }
        }
        return principals;
    }

    @SuppressWarnings("unchecked")
    private List<String> getThreadStringList(String key) {
        Object value = com.ofss.digx.infra.thread.ThreadAttribute.get(key);
        if (value instanceof List) {
            return new ArrayList<String>((List<String>) value);
        }
        return new ArrayList<>();
    }

    private void cancelItoken(SessionContext sessionContext, String userId, String instanceStatus, String scenario)
            throws Exception {
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate Auto cancellation of i-Token service Begin");
        ITokenDTO itoken = new ITokenDTO();
        itoken.setUserIdentifier(userId);
        itoken.setInstanceStatus(instanceStatus);
        if (scenario != null){
            itoken.setScenario(scenario);
        }
        itoken.setLastUpdatedBy(sessionContext.getUserId());
        IITokenAdapter adapter = getItokenAdapter("cancelItoken");
        ITokenCancelResponseDTO response = adapter.cancelItoken(itoken);
        String returnCode = response == null ? null : response.getReturnCode();
        com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate Auto cancellation of i-Token service End response: "
                + returnCode);
        if ("200".equals(returnCode)) {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt postUpdate Auto cancellation success");
        } else {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
                    "CZUserExtensionDataExt postUpdate User Profile Update Successful, but auto cancel i-Token failed, please try to manual cancel i-Token.");
        }

        // BCM-2549: CDC6907 - Cancel I-Token on BCO
        boolean success = "200".equals(returnCode);
        sendITokenCancelCrm(sessionContext, userId, success);
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
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZUserExtensionDataExt CRMAsserter.crmInsertData( crm" + e);
        }
    }

    private IITokenAdapter getItokenAdapter(String operation) {
        return ExtxfaceAdapterFactory.getInstance().getAdapter(IITokenAdapter.class, operation,
                DeterminantResolver.getInstance().getDeterminantTypeForObject(ITokenDTO.class.getName()));
    }

    private boolean isBlank(String value) {
        return value == null || "".equals(value);
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
