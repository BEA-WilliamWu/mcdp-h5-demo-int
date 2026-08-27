package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.hkbea.dsp.api.ClientApi;
import com.hkbea.dsp.model.ClientValidationParam;
import com.hkbea.dsp.model.ClientValidationResult;
import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiAuthorizationDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementResponseDTO;
import com.ofss.digx.common.constants.ApprovalsErrorConstants;
import com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO;
import com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.common.constants.CZCommonConstants;
import com.ofss.digx.cz.bea.common.constants.UserExtensionDataConstants;
import com.ofss.digx.cz.bea.common.util.CZLocaleUtils;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApiKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistry;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthApiMasterRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthManagementRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthRequestApiRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthRequestRepositoryAdapter;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.DspClient;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.factory.DSPApiClientFactory;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.infra.exceptions.ExceptionTransformerFactory;
import com.ofss.digx.infra.exceptions.IExceptionTransformer;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HostToHostManagement extends AbstractApplication implements IHostToHostManagement {

    private static final String ACTIVITY_NAME = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.notifyHostToHostManagement";
    // todo  have no tem
    private static final String ACTIVITY_CREATE =
            "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create";

    private static final String ACTIVITY_UPDATE =
            "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";

    private static final String ACTIVITY_ACCOUNT_ACCESS_UPDATE =
            "com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update";
    private static final String EVENT_ENABLE_PREFIX ="USER_MANAGEMENT_CREATE";

    private static final String EVENT_DISABLE_PREFIX = "USER_MANAGEMENT_EDIT";

    private static final String EVENT_EDIT_PREFIX = "USER_ACCOUNT_ACCESS_UPDATE";

    private static final String THIS_COMPONENT_NAME = HostToHostManagement.class.getName();

    private static final MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();

    private static final Logger logger = formatter.getLogger(THIS_COMPONENT_NAME);

    private static final String SUBMIT_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.submit";
    private static final String EDIT_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.edit";
    private static final String DISABLE_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.disable";
    private static final String SEARCH_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.search";
    private static final String VALIDATE_UAM_SERVICE_ID =
            "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.validateUamClientId";

    private static final String ACTION_ENABLE = "ENABLE";
    private static final String ACTION_EDIT = "EDIT";
    private static final String ACTION_DISABLE = "DISABLE";

    private static final String HTH_STATUS_ENABLE = "ENABLE";
    private static final String HTH_STATUS_DISABLE = "DISABLE";

    private static final String APPROVAL_FLOW_STRAIGHT_THROUGH = "STRAIGHT_THROUGH";

    private static final String OBJECT_STATUS_ACTIVE = "A";
    private static final String OBJECT_STATUS_INACTIVE = "I";

    private static final String UAM_CLIENT_ID_ALREADY_BOUND_MESSAGE =
            "UAM Client ID has already been used for Host to Host and cannot be reused.";

    public HostToHostManagement() {
    }

    @Override
    @Entitlement(name = "Search Host To Host Management", action = ActionType.VIEW, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.Party_Preference)
    public HostToHostManagementResponseDTO search(SessionContext sessionContext,
                                                  HostToHostManagementDTO requestDTO) throws Exception {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, formatter.formatMessage("Entered into search() : requestDTO = %s in class %s ",
                    requestDTO, THIS_COMPONENT_NAME));
        }
        super.checkAccessPolicy(SEARCH_SERVICE_ID, sessionContext);

        HostToHostManagementResponseDTO response = new HostToHostManagementResponseDTO();
        response.setStatus(fetchStatus());
        TransactionStatus transactionStatus = fetchTransactionStatus();
        Interaction.begin(sessionContext);

        try {
            requestDTO.validate(sessionContext);
            HostToHostManagementDTO detail = buildDetail(requestDTO);
            populateResponse(response, detail);
            response.setStatus(buildStatus(transactionStatus));
        } catch (Exception e) {
            fillTransactionStatus(transactionStatus, e);
            logger.log(Level.SEVERE, formatter.formatMessage("Exception from search() for requestDTO '%s' in class %s",
                    requestDTO, THIS_COMPONENT_NAME), e);
            throw e;
        } catch (RuntimeException rte) {
            fillTransactionStatus(transactionStatus, rte);
            logger.log(Level.SEVERE,
                    formatter.formatMessage("RuntimeException from search() for requestDTO '%s' in class %s",
                            requestDTO, THIS_COMPONENT_NAME),
                    rte);
            throw new Exception(rte);
        } finally {
            Interaction.close();
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, formatter.formatMessage("Exiting from search() : response = %s", response));
        }
        super.checkResponsePolicy(sessionContext, response);
        return response;
    }

    @Override
    @Entitlement(name = "Validate Host To Host UAM Client ID", action = ActionType.VIEW, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.Party_Preference)
    public HostToHostManagementResponseDTO validateUamClientId(SessionContext sessionContext,
                                                               HostToHostManagementDTO requestDTO) throws Exception {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, formatter.formatMessage(
                    "Entered into validateUamClientId() : requestDTO = %s in class %s ", requestDTO,
                    THIS_COMPONENT_NAME));
        }
        super.checkAccessPolicy(VALIDATE_UAM_SERVICE_ID, sessionContext);

        HostToHostManagementResponseDTO response = new HostToHostManagementResponseDTO();
        response.setStatus(fetchStatus());
        TransactionStatus transactionStatus = fetchTransactionStatus();
        Interaction.begin(sessionContext);

        try {
            requestDTO.validate(sessionContext);
            populateUamValidationResponse(response, requestDTO);
            response.setStatus(buildStatus(transactionStatus));
        } catch (Exception e) {
            fillTransactionStatus(transactionStatus, e);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "Exception from validateUamClientId() for requestDTO '%s' in class %s", requestDTO,
                    THIS_COMPONENT_NAME), e);
            throw e;
        } catch (RuntimeException rte) {
            fillTransactionStatus(transactionStatus, rte);
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "RuntimeException from validateUamClientId() for requestDTO '%s' in class %s", requestDTO,
                    THIS_COMPONENT_NAME), rte);
            throw new Exception(rte);
        } finally {
            Interaction.close();
        }

        super.checkResponsePolicy(sessionContext, response);
        return response;
    }

    @Override
    @Entitlement(name = "Submit Host To Host Management", action = ActionType.PERFORM, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.Party_Preference)
    @Task(id = "PP_N_HTH_ENB", parent = "PP", name = "Host to Host Management - Enable", supportedAccountTypes = {},
            executable = true, moduleType = ModuleType.BACK_OFFICE,
            aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
            type = TaskType.ADMINISTRATION)
    public HostToHostManagementResponseDTO submit(SessionContext sessionContext,
                                                  HostToHostManagementDTO requestDTO) throws Exception {
        return save(sessionContext, requestDTO, SUBMIT_SERVICE_ID, ACTION_ENABLE);
    }

    @Override
    @Entitlement(name = "Edit Host To Host Management", action = ActionType.PERFORM, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.Party_Preference)
    @Task(id = "PP_N_HTH_EDT", parent = "PP", name = "Host to Host Management - Edit", supportedAccountTypes = {},
            executable = true, moduleType = ModuleType.BACK_OFFICE,
            aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
            type = TaskType.ADMINISTRATION)
    public HostToHostManagementResponseDTO edit(SessionContext sessionContext,
                                                HostToHostManagementDTO requestDTO) throws Exception {
        return save(sessionContext, requestDTO, EDIT_SERVICE_ID, ACTION_EDIT);
    }

    @Override
    @Entitlement(name = "Disable Host To Host Management", action = ActionType.PERFORM, requiredResources = {})
    @EntitlementGroup(category = EntitlementCategory.ADMIN_MAINTENANCE, subCategory = EntitlementSubCategory.Party_Preference)
    @Task(id = "PP_N_HTH_DIS", parent = "PP", name = "Host to Host Management - Disable", supportedAccountTypes = {},
            executable = true, moduleType = ModuleType.BACK_OFFICE,
            aspects = {TaskAspect.APPROVALS, TaskAspect.AUDIT, TaskAspect.BLACKOUT},
            type = TaskType.ADMINISTRATION)
    public HostToHostManagementResponseDTO disable(SessionContext sessionContext,
                                                   HostToHostManagementDTO requestDTO) throws Exception {
        return save(sessionContext, requestDTO, DISABLE_SERVICE_ID, ACTION_DISABLE);
    }

    private HostToHostManagementResponseDTO save(SessionContext sessionContext,
                                                 HostToHostManagementDTO requestDTO, String serviceId, String actionType) throws Exception {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, formatter.formatMessage("Entered into save() : action = %s, requestDTO = %s",
                    actionType, requestDTO));
        }
        super.checkAccessPolicy(serviceId, sessionContext, requestDTO);

        HostToHostManagementResponseDTO response = new HostToHostManagementResponseDTO();
        response.setStatus(fetchStatus());
        TransactionStatus transactionStatus = fetchTransactionStatus();
        boolean approvedExecution = isApprovedExecution();
        String referenceNumber = initializeReferenceNumber(requestDTO, response, actionType, approvedExecution);
        boolean saveCompleted = false;

        Interaction.begin(sessionContext);

        try {
            referenceNumber = processSave(sessionContext, requestDTO, response, actionType, approvedExecution,
                    referenceNumber);
            saveCompleted = true;
        } catch (Exception e) {
            handleSaveException(transactionStatus, e, requestDTO, actionType, referenceNumber, sessionContext,
                    approvedExecution, serviceId, "Exception");
        } catch (RuntimeException rte) {
            handleSaveException(transactionStatus, rte, requestDTO, actionType, referenceNumber, sessionContext,
                    approvedExecution, serviceId, "RuntimeException");
        } finally {
            Interaction.close();
        }

        restoreExternalReferenceNumber(saveCompleted, referenceNumber);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, formatter.formatMessage("Exiting from save() : response = %s", response));
        }
        super.checkResponsePolicy(sessionContext, response);
        restoreExternalReferenceNumber(saveCompleted, referenceNumber);
        return response;
    }

    private void notifyHostToHostManagement(SessionContext sessionContext, HostToHostManagementDTO requestDTO, String actionType) throws Exception {
        NotificationDetail[] details = new NotificationDetail[1];
        String eventId;
        System.out.println("[HTH-NOTIFICATION] Start notification,PartyId"+requestDTO.getPartyId());
        logger.log(Level.FINE,
                formatter.formatMessage(
                        "[HTH-NOTIFICATION] Start notification, actionType=%s, partyId=%s",
                        actionType,
                        requestDTO == null ? null : requestDTO.getPartyId()));
        com.ofss.digx.app.adapter.IAdapterFactory adapterFactory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
                .getInstance().getAdapterFactory(
                        com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.USER_EXTENSION_ADAPTER_FACTORY);
        IUserExtensionAdapter adapter = (IUserExtensionAdapter) adapterFactory
                .getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.USER_EXTENSION_ADAPTER);
        CZPartyPreferenceDTO partyDetails = adapter.getPartyPreferences(requestDTO.getPartyId());

        NotificationDetail detail = buildNotification(partyDetails);
        if (ObjectUtils.isEmpty(detail.getDestination())) {
            return;
        }
        // eventId=  getEventId(actionType,detail);
        if (ACTION_ENABLE.equals(actionType)) {
            UserProfUpdateActivityLogDTO activityLog = new UserProfUpdateActivityLogDTO();
            System.out.println("### Mobile update to new nob number Alert");
            NotificationDetail messageNotificationDetail = new NotificationDetail();
            messageNotificationDetail.setRecipientId(requestDTO.getPartyId());
            messageNotificationDetail.setDestination(DestinationType.SMS);
            messageNotificationDetail.setDispatchAddress(partyDetails.getOfficeTelNo());
            messageNotificationDetail.setRecipientType(SubscriberType.EXTERNAL.toString());
            System.out.println("#### User New mobile no: " + partyDetails.getOfficeTelNo());

            details[0] = messageNotificationDetail;
            activityLog.setNotificationDetails(details);
            activityLog.setCustomerId(requestDTO.getPartyId());
            activityLog.setUserId(sessionContext.getUserId());
            activityLog.setProfileUser(sessionContext.getUserId());
            System.out.println("#########################HTH-NOTIFICATION usermgmtActivityLog: - " + activityLog.toString());
            String activityId=ACTIVITY_CREATE;
            eventId=UserExtensionDataConstants.CORPORATEPLUS_WELCOME_MAIL;
            System.out.println("#########################HTH-NOTIFICATION##########activityId:"+activityId+"####  eventId:"+eventId);
            super.registerActivityAndGenerateEvent(sessionContext, activityId,
                    eventId, new Date(), activityLog);
            logger.log(Level.FINE,
                    formatter.formatMessage(
                            "[HTH-NOTIFICATION] Notification submitted successfully, activityId=%s, eventId=%s",
                            getActivityId(actionType),
                            eventId));
        }
    }

    private String getActivityId(String actionType) {

        if (ACTION_ENABLE.equals(actionType)) {
            return ACTIVITY_CREATE;
        }

        if (ACTION_EDIT.equals(actionType)) {
            return ACTIVITY_ACCOUNT_ACCESS_UPDATE;
        }

        if (ACTION_DISABLE.equals(actionType)) {
            return ACTIVITY_UPDATE;
        }

        return ACTIVITY_UPDATE;
    }

    private String getEventId(String actionType, NotificationDetail detail) {

        StringBuilder eventId = new StringBuilder();

        if (ACTION_ENABLE.equals(actionType)) {
            eventId.append(EVENT_ENABLE_PREFIX);
        } else if (ACTION_EDIT.equals(actionType)) {
            eventId.append(EVENT_EDIT_PREFIX);
        } else {
            eventId.append(EVENT_DISABLE_PREFIX);
        }
        return eventId.toString();
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
        System.out.println("Size: " + chList.size());
        return chList;
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
            } // System.out.println("str=" + str);// take out part that does not require
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
        System.out.println("#####rep String:- " + s);
        if (s == null || s.length() < 1) {
            return "";
        }
        int len = s.length();
        System.out.println("######s length:- " + len);
        if (len > 1) {
            s = s.substring(0, 1);
            System.out.println("#######s:- " + s);
            for (int i = 0; i < len - 1; i++) {
                s = s + "*";
            }
            System.out.println("######masked:- " + s);
            return s;
        } else {
            System.out.println("######unmaksed:- " + s);
            return s;
        }
    }

    private NotificationDetail buildNotification(CZPartyPreferenceDTO partyDetails) {

        NotificationDetail detail = new NotificationDetail();

        if (!isBlank(partyDetails.getOfficeEmailId())) {

            detail.setDestination(DestinationType.EMAIL);

            detail.setDispatchAddress(partyDetails.getOfficeEmailId());

        } else if (!isBlank(partyDetails.getOfficeTelNo())) {

            detail.setDestination(DestinationType.SMS);

            detail.setDispatchAddress(partyDetails.getOfficeTelNo());
        }
        detail.setRecipientType(SubscriberType.EXTERNAL.toString());
        detail.setRecipientId(partyDetails.getPartyIdValue());

        return detail;
    }



    private String initializeReferenceNumber(HostToHostManagementDTO requestDTO,
                                             HostToHostManagementResponseDTO response, String actionType,
                                             boolean approvedExecution) throws Exception {
        if (approvedExecution) {
            return null;
        }

        String referenceNumber = generateReferenceNumber(actionType);
        if (requestDTO != null) {
            requestDTO.setReferenceNumber(referenceNumber);
        }
        response.getStatus().setExternalReferenceNumber(referenceNumber);
        setExternalReferenceNumber(referenceNumber);
        return referenceNumber;
    }

    private String processSave(SessionContext sessionContext, HostToHostManagementDTO requestDTO,
                               HostToHostManagementResponseDTO response, String actionType, boolean approvedExecution,
                               String referenceNumber) throws Exception {
        validateRequestForSave(sessionContext, requestDTO, actionType, approvedExecution);
        logSaveDecision(requestDTO, actionType, approvedExecution, referenceNumber);
        System.out.println("==========processSave======="+approvedExecution);
        if (approvedExecution) {
            referenceNumber = executeApprovedSave(sessionContext, requestDTO, actionType);
            //  notify
            notifyHostToHostManagement(sessionContext,requestDTO,actionType);
        } else {
            createRequestSnapshotForCurrentTransaction(requestDTO, actionType, referenceNumber, sessionContext);
        }

        populateSaveResponse(response, requestDTO, referenceNumber);
        return referenceNumber;
    }

    private void validateRequestForSave(SessionContext sessionContext, HostToHostManagementDTO requestDTO,
                                        String actionType, boolean approvedExecution) throws Exception {
        validateSubmitRequest(requestDTO, actionType);
        requestDTO.validate(sessionContext);
        if (!approvedExecution) {
            checkPendingRequest(requestDTO.getPartyId());
        }
        validateCurrentConfiguration(requestDTO.getPartyId(), actionType);
        if (!approvedExecution && isEditAction(actionType)) {
            assertConfigurationChangedForEdit(requestDTO);
        }
    }

    private void logSaveDecision(HostToHostManagementDTO requestDTO, String actionType, boolean approvedExecution,
                                 String referenceNumber) {
        logger.log(Level.INFO, formatter.formatMessage(
                "[HTH-SNAPSHOT-DIAG] save() decision: action=%s, approvedExecution=%s, partyId=%s, transactionId=%s, referenceNumber=%s",
                actionType, String.valueOf(approvedExecution),
                requestDTO == null ? null : requestDTO.getPartyId(), readTransactionId(), referenceNumber));
    }

    private String executeApprovedSave(SessionContext sessionContext, HostToHostManagementDTO requestDTO,
                                       String actionType) throws Exception {
        String transactionId = readTransactionId();
        String referenceNumber = resolveApprovedReferenceNumber(requestDTO, transactionId);
        if (!isBlank(referenceNumber)) {
            requestDTO.setReferenceNumber(referenceNumber);
            setExternalReferenceNumber(referenceNumber);
        }
        applyApprovedConfiguration(requestDTO, actionType, readUserId(sessionContext));
        return referenceNumber;
    }

    private void populateSaveResponse(HostToHostManagementResponseDTO response, HostToHostManagementDTO requestDTO,
                                      String referenceNumber) {
        populateResponse(response, requestDTO);
        if (!isBlank(referenceNumber)) {
            response.getStatus().setExternalReferenceNumber(referenceNumber);
        }
    }

    private void handleSaveException(TransactionStatus transactionStatus, Exception exception,
                                     HostToHostManagementDTO requestDTO, String actionType, String referenceNumber,
                                     SessionContext sessionContext, boolean approvedExecution, String serviceId,
                                     String exceptionType) {
        fillTransactionStatus(transactionStatus, exception);
        logSaveException(exception, requestDTO, actionType, referenceNumber, sessionContext, approvedExecution,
                serviceId, exceptionType);
    }

    private void handleSaveException(TransactionStatus transactionStatus, RuntimeException exception,
                                     HostToHostManagementDTO requestDTO, String actionType, String referenceNumber,
                                     SessionContext sessionContext, boolean approvedExecution, String serviceId,
                                     String exceptionType) {
        fillTransactionStatus(transactionStatus, exception);
        logSaveException(exception, requestDTO, actionType, referenceNumber, sessionContext, approvedExecution,
                serviceId, exceptionType);
    }

    private void logSaveException(java.lang.Exception exception, HostToHostManagementDTO requestDTO, String actionType,
                                  String referenceNumber, SessionContext sessionContext, boolean approvedExecution,
                                  String serviceId, String exceptionType) {
        createRequestSnapshotIfTransactionStarted(requestDTO, actionType, referenceNumber, sessionContext,
                approvedExecution);
        logger.log(Level.SEVERE, formatter.formatMessage(
                "%s from save() for serviceId '%s', requestDTO '%s' in class %s", exceptionType, serviceId,
                requestDTO, THIS_COMPONENT_NAME), exception);
    }

    private void validateSubmitRequest(HostToHostManagementDTO requestDTO, String actionType) throws Exception {
        validateRequiredSubmitFields(requestDTO);
        if (isDisableAction(actionType)) {
            return;
        }
        validateEnableOrEditRequest(requestDTO, actionType);
    }

    private void validateRequiredSubmitFields(HostToHostManagementDTO requestDTO) {
        if (requestDTO == null) {
            throw new IllegalArgumentException("Host to Host Management request is required.");
        }
        if (isBlank(requestDTO.getPartyId())) {
            throw new IllegalArgumentException("Party ID is required.");
        }
    }

    private void validateEnableOrEditRequest(HostToHostManagementDTO requestDTO, String actionType) throws Exception {
        if (!Boolean.TRUE.equals(requestDTO.getHostToHostApiChannelEnabled())) {
            throw new IllegalArgumentException("Host to Host API Channel must be enabled.");
        }
        if (isBlank(requestDTO.getCorrelateUamClientId())) {
            throw new IllegalArgumentException("Correlate UAM Client ID is required.");
        }
        assertUamClientIdUnchangedForEdit(requestDTO, actionType);
        if (isEnableAction(actionType)) {
            assertUamClientIdAvailable(requestDTO.getPartyId(), requestDTO.getCorrelateUamClientId());
        }
        if (!hasSelectedApiAuthorization(requestDTO)) {
            throw new IllegalArgumentException("At least one API authorization is required.");
        }
    }

    private void validateCurrentConfiguration(String partyId, String actionType) throws Exception {
        String currentHthStatus = fetchCurrentHthStatus(partyId);
        boolean currentlyEnabled = HTH_STATUS_ENABLE.equals(currentHthStatus);

        if (isEnableAction(actionType) && currentlyEnabled) {
            throw new IllegalArgumentException(
                    "Host to Host API Channel is already enabled for this corporate customer.");
        }
        if ((isEditAction(actionType) || isDisableAction(actionType)) && !currentlyEnabled) {
            throw new IllegalArgumentException(
                    "Host to Host API Channel is not enabled for this corporate customer.");
        }
    }

    private void checkPendingRequest(String partyId) throws Exception {
        HthRequest request = new HthRequest();
        if (!request.existsPendingApproval(partyId)) {
            return;
        }

        logger.log(Level.SEVERE, formatter.formatMessage(
                "Duplicate Host to Host Management request detected for partyId '%s' in class %s", partyId,
                THIS_COMPONENT_NAME));
        ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
        IExceptionTransformer transformer = transformerFactory
                .getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
        Exception e = new Exception();
        transformer.translate(e, ApprovalsErrorConstants.DUPLICATE_TRANSACTION, HostToHostManagement.class);
        throw e;
    }

    private String fetchCurrentHthStatus(String partyId) throws Exception {
        HthManagement management = new HthManagement();
        management = management.findActiveByPartyId(partyId);
        return management == null ? null : normalize(management.getHthStatus());
    }

    private void createRequestSnapshot(HostToHostManagementDTO requestDTO, String actionType,
                                       String referenceNumber, String transactionId, String userId) throws Exception {
        Set<String> selectedCodes = extractSelectedApiCodesForAction(requestDTO, actionType);
        persistSnapshotInNewTransaction(requestDTO, actionType, referenceNumber, transactionId, userId, selectedCodes);
    }

    private void persistSnapshotInNewTransaction(HostToHostManagementDTO requestDTO, String actionType,
                                                 String referenceNumber, String transactionId, String userId,
                                                 Set<String> selectedCodes) throws Exception {
        Set<String> effectiveSelectedCodes = selectedCodes == null ? Collections.<String>emptySet() : selectedCodes;
        logger.log(Level.INFO, formatter.formatMessage(
                "[HTH-SNAPSHOT-DIAG] Persisting snapshot in dedicated NONXA transaction: transactionId=%s, apiCount=%s",
                transactionId, String.valueOf(effectiveSelectedCodes.size())));
        Session session = null;
        boolean committed = false;
        try {
            session = DataAccessManager.getManager().openNewSession("NONXA");
            session.beginTransaction();

            LocalHthRequestRepositoryAdapter requestAdapter = LocalHthRequestRepositoryAdapter.getInstance();
            HthRequest existingRequest = requestAdapter.findActiveByTransactionId(transactionId);
            if (existingRequest == null) {
                persistNewSnapshotRows(requestAdapter, requestDTO, actionType, referenceNumber, transactionId, userId,
                        effectiveSelectedCodes, session);
            } else {
                logger.log(Level.INFO, formatter.formatMessage(
                        "[HTH-SNAPSHOT-DIAG] Existing snapshot found in NONXA session for transactionId=%s; skipping",
                        transactionId));
            }

            session.fetchCurrentTransaction().commit();
            committed = true;
            logger.log(Level.INFO, formatter.formatMessage(
                    "[HTH-SNAPSHOT-DIAG] Snapshot committed independently (NONXA): transactionId=%s",
                    transactionId));
        } catch (java.lang.Exception e) {
            throw new Exception(e);
        } finally {
            if (session != null) {
                try {
                    if (!committed) {
                        session.fetchCurrentTransaction().rollback();
                    }
                } finally {
                    DataAccessManager.getManager().closeSession(session);
                }
            }
        }
    }

    private void persistNewSnapshotRows(LocalHthRequestRepositoryAdapter requestAdapter,
                                        HostToHostManagementDTO requestDTO, String actionType, String referenceNumber,
                                        String transactionId, String userId, Set<String> selectedCodes,
                                        Session session) throws Exception {
        Map<String, HthApiMaster> activeApiByCode = fetchActiveApiByCodeForSnapshot(selectedCodes);
        assertAllApiCodesResolved(selectedCodes, activeApiByCode);

        String requestId = generateId("HTH_REQUEST_ID");
        HthManagement currentManagement = findCurrentManagementForSnapshot(requestDTO, actionType);
        HthRequest request = buildRequest(requestDTO, actionType, referenceNumber, transactionId, userId, requestId,
                currentManagement);
        requestAdapter.create(request);
        session.flush();

        persistRequestApiSnapshotRows(requestId, selectedCodes, activeApiByCode, userId);
        session.flush();
        logger.log(Level.INFO, formatter.formatMessage(
                "[HTH-SNAPSHOT-DIAG] Snapshot rows persisted in NONXA session: requestId=%s, transactionId=%s, apiCount=%s",
                requestId, transactionId, String.valueOf(selectedCodes.size())));
    }

    private void persistRequestApiSnapshotRows(String requestId, Set<String> selectedCodes,
                                               Map<String, HthApiMaster> activeApiByCode, String userId)
            throws Exception {
        LocalHthRequestApiRepositoryAdapter requestApiAdapter = LocalHthRequestApiRepositoryAdapter.getInstance();
        for (String apiCode : selectedCodes) {
            HthApiMaster apiMaster = activeApiByCode.get(apiCode);
            requestApiAdapter.create(buildRequestApi(requestId, apiMaster, userId));
        }
    }

    private void createRequestSnapshotForCurrentTransaction(HostToHostManagementDTO requestDTO, String actionType,
                                                            String referenceNumber, SessionContext sessionContext) {
        try {
            String transactionId = requireTransactionId();
            logger.log(Level.INFO, formatter.formatMessage(
                    "[HTH-SNAPSHOT-DIAG] createRequestSnapshotForCurrentTransaction (initiation path): action=%s, transactionId=%s",
                    actionType, transactionId));
            createRequestSnapshot(requestDTO, actionType, referenceNumber, transactionId, readUserId(sessionContext));
        } catch (java.lang.Exception e) {
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "Failed to persist HTH request history snapshot for actionType '%s', referenceNumber '%s' in class %s (non-fatal; approval is unaffected)",
                    actionType, referenceNumber, THIS_COMPONENT_NAME), e);
        }
    }

    private void createRequestSnapshotIfTransactionStarted(HostToHostManagementDTO requestDTO, String actionType,
                                                           String referenceNumber, SessionContext sessionContext, boolean approvedExecution) {
        try {
            if (approvedExecution) {
                return;
            }

            String transactionId = normalize(readTransactionId());
            logger.log(Level.INFO, formatter.formatMessage(
                    "[HTH-SNAPSHOT-DIAG] Fallback (catch path) createRequestSnapshotIfTransactionStarted: action=%s, transactionId=%s",
                    actionType, transactionId));
            if (transactionId == null) {
                return;
            }

            logger.log(Level.INFO, formatter.formatMessage(
                    "[HTH-SNAPSHOT-DIAG] Fallback creating snapshot for transactionId=%s (committed in dedicated NONXA transaction)",
                    transactionId));
            createRequestSnapshot(requestDTO, actionType, referenceNumber, transactionId, readUserId(sessionContext));
        } catch (java.lang.Exception e) {
            logger.log(Level.SEVERE, formatter.formatMessage(
                    "Failed to create request snapshot in fallback for actionType '%s', referenceNumber '%s' in class %s",
                    actionType, referenceNumber, THIS_COMPONENT_NAME), e);
        }
    }

    private void assertAllApiCodesResolved(Set<String> selectedCodes, Map<String, HthApiMaster> activeApiByCode) {
        for (String apiCode : selectedCodes) {
            HthApiMaster apiMaster = activeApiByCode.get(apiCode);
            if (apiMaster == null || apiMaster.getKey() == null) {
                throw new IllegalArgumentException("Selected API authorization is not configured.");
            }
        }
    }

    private void applyApprovedConfiguration(HostToHostManagementDTO requestDTO, String actionType,
                                            String userId) throws Exception {
        String disableUamClientId = isDisableAction(actionType) ? resolveApprovedDisableUamClientId(requestDTO)
                : null;
        if (isEnableAction(actionType)) {
            registerApprovedUamClientId(requestDTO, userId);
        }
        HthManagement management = upsertEffectiveManagement(requestDTO, actionType, userId);
        refreshEffectiveManagementApis(management.getKey().getId(), requestDTO, actionType, userId);
        if (isDisableAction(actionType)) {
            retireApprovedUamClientId(requestDTO, disableUamClientId, userId);
        }
    }

    private void registerApprovedUamClientId(HostToHostManagementDTO requestDTO, String userId) throws Exception {
        String uamClientId = normalize(requestDTO.getCorrelateUamClientId());
        if (uamClientId == null) {
            throw new IllegalArgumentException("Correlate UAM Client ID is required.");
        }

        HthUamClientRegistry registryDomain = new HthUamClientRegistry();
        registryDomain.registerUamClientId(generateId("HTH_UAM_REGISTRY_ID"), requestDTO.getPartyId(), uamClientId,
                readTransactionId(), userId);
    }

    private void retireApprovedUamClientId(HostToHostManagementDTO requestDTO, String uamClientId, String userId)
            throws Exception {
        if (isBlank(uamClientId)) {
            return;
        }

        HthUamClientRegistry registryDomain = new HthUamClientRegistry();
        registryDomain.retireUamClientId(generateId("HTH_UAM_REGISTRY_ID"), requestDTO.getPartyId(), uamClientId,
                readTransactionId(), userId);
    }

    private HthManagement upsertEffectiveManagement(HostToHostManagementDTO requestDTO, String actionType,
                                                    String userId) throws Exception {
        HthManagement management = new HthManagement();
        HthManagement existing = management.findActiveByPartyId(requestDTO.getPartyId());

        if (existing == null) {
            HthManagementKey key = new HthManagementKey();
            key.setId(generateId("HTH_MANAGEMENT_ID"));
            management.setKey(key);
            management.setPartyId(requestDTO.getPartyId());
            management.setObjectStatus(OBJECT_STATUS_ACTIVE);
            management.setCreatedBy(userId);
            fillEffectiveManagement(management, requestDTO, actionType, userId);
            management.create(management);
            return management;
        }

        fillEffectiveManagement(existing, requestDTO, actionType, userId);
        existing.update(existing);
        return existing;
    }

    private void fillEffectiveManagement(HthManagement management, HostToHostManagementDTO requestDTO,
                                         String actionType, String userId) {
        boolean disable = isDisableAction(actionType);
        if (disable) {
            management.setUamClientId(null);
        } else {
            management.setUamClientId(normalize(requestDTO.getCorrelateUamClientId()));
        }
        management.setHthStatus(disable ? HTH_STATUS_DISABLE : HTH_STATUS_ENABLE);
        management.setApprovalFlowType(disable ? null : requestDTO.getApiApprovalFlowType());
        management.setLastUpdatedBy(userId);
    }

    private void refreshEffectiveManagementApis(String managementId, HostToHostManagementDTO requestDTO,
                                                String actionType, String userId) throws Exception {
        Set<String> selectedCodes = isDisableAction(actionType) ? Collections.<String>emptySet()
                : extractSelectedApiCodes(requestDTO);
        Map<String, HthApiMaster> activeApiByCode = fetchActiveApiByCode(selectedCodes);
        assertAllApiCodesResolved(selectedCodes, activeApiByCode);

        HthManagementApi managementApiDomain = new HthManagementApi();
        deactivateEffectiveManagementApis(managementApiDomain, managementId, userId);
        upsertEffectiveManagementApiLinks(managementApiDomain, managementId, selectedCodes, activeApiByCode, userId);
    }

    private void deactivateEffectiveManagementApis(
            HthManagementApi managementApiDomain, String managementId, String userId) throws Exception {
        List<HthManagementApi> existingList = managementApiDomain.listActiveByManagementId(managementId);
        if (existingList == null) {
            return;
        }

        for (HthManagementApi existing : existingList) {
            existing.setObjectStatus(OBJECT_STATUS_INACTIVE);
            existing.setLastUpdatedBy(userId);
            existing.update(existing);
        }
    }

    private void upsertEffectiveManagementApiLinks(
            HthManagementApi managementApiDomain, String managementId, Set<String> selectedCodes,
            Map<String, HthApiMaster> activeApiByCode, String userId) throws Exception {
        for (String apiCode : selectedCodes) {
            HthApiMaster apiMaster = activeApiByCode.get(apiCode);
            String apiMasterId = apiMaster.getKey().getId();

            HthManagementApi link = managementApiDomain.findByManagementIdAndApiMasterId(managementId, apiMasterId);
            if (link == null) {
                createEffectiveManagementApiLink(managementId, apiMasterId, userId);
            } else {
                reactivateEffectiveManagementApiLink(link, userId);
            }
        }
    }

    private void createEffectiveManagementApiLink(String managementId, String apiMasterId, String userId)
            throws Exception {
        HthManagementApi created = new HthManagementApi();
        HthManagementApiKey key = new HthManagementApiKey();
        key.setId(generateId("HTH_MANAGEMENT_API_ID"));
        created.setKey(key);
        created.setHthManagementId(managementId);
        created.setApiMasterId(apiMasterId);
        created.setObjectStatus(OBJECT_STATUS_ACTIVE);
        created.setCreatedBy(userId);
        created.setLastUpdatedBy(userId);
        created.create(created);
    }

    private void reactivateEffectiveManagementApiLink(HthManagementApi link, String userId) throws Exception {
        link.setObjectStatus(OBJECT_STATUS_ACTIVE);
        link.setLastUpdatedBy(userId);
        link.update(link);
    }

    private String readRequestReferenceNumber(String transactionId) throws Exception {
        if (isBlank(transactionId)) {
            return null;
        }
        HthRequest existing = LocalHthRequestRepositoryAdapter.getInstance().findActiveByTransactionId(transactionId);
        return existing == null ? null : existing.getReferenceNo();
    }

    private String resolveApprovedReferenceNumber(HostToHostManagementDTO requestDTO, String transactionId)
            throws Exception {
        String referenceNumber = readRequestReferenceNumber(transactionId);
        if (isBlank(referenceNumber) && requestDTO != null) {
            referenceNumber = normalize(requestDTO.getReferenceNumber());
        }
        return referenceNumber;
    }

    private HthRequest buildRequest(HostToHostManagementDTO requestDTO, String actionType, String referenceNumber,
                                    String transactionId, String userId, String requestId,
                                    HthManagement currentManagement) {
        boolean disable = isDisableAction(actionType);
        HthRequest request = new HthRequest();
        HthRequestKey key = new HthRequestKey();
        key.setId(requestId);
        request.setKey(key);
        request.setTransactionId(transactionId);
        request.setReferenceNo(referenceNumber);
        request.setPartyId(requestDTO.getPartyId());
        request.setActionType(actionType);
        request.setUamClientId(resolveRequestUamClientId(requestDTO, actionType, currentManagement));
        request.setHthStatus(disable ? HTH_STATUS_DISABLE : HTH_STATUS_ENABLE);
        request.setApprovalFlowType(disable ? null : requestDTO.getApiApprovalFlowType());
        request.setObjectStatus(OBJECT_STATUS_ACTIVE);
        request.setCreatedBy(userId);
        request.setLastUpdatedBy(userId);
        return request;
    }

    private String resolveRequestUamClientId(HostToHostManagementDTO requestDTO, String actionType,
                                             HthManagement currentManagement) {
        String requestUamClientId = normalize(requestDTO.getCorrelateUamClientId());
        if (!isDisableAction(actionType) || requestUamClientId != null) {
            return requestUamClientId;
        }

        return currentManagement == null ? null : normalize(currentManagement.getUamClientId());
    }

    private String resolveApprovedDisableUamClientId(HostToHostManagementDTO requestDTO) throws Exception {
        String requestUamClientId = normalize(requestDTO.getCorrelateUamClientId());
        if (requestUamClientId != null) {
            return requestUamClientId;
        }

        String transactionId = normalize(readTransactionId());
        if (transactionId != null) {
            HthRequest request = LocalHthRequestRepositoryAdapter.getInstance().findActiveByTransactionId(transactionId);
            if (request != null && !isBlank(request.getUamClientId())) {
                return normalize(request.getUamClientId());
            }
        }

        HthManagement currentManagement = LocalHthManagementRepositoryAdapter.getInstance()
                .findActiveByPartyId(requestDTO.getPartyId());
        return currentManagement == null ? null : normalize(currentManagement.getUamClientId());
    }

    private HthRequestApi buildRequestApi(String requestId, HthApiMaster apiMaster, String userId) {
        HthRequestApi requestApi = new HthRequestApi();
        HthRequestApiKey key = new HthRequestApiKey();
        key.setId(generateId("HTH_REQUEST_API_ID"));
        requestApi.setKey(key);
        requestApi.setHthRequestId(requestId);
        requestApi.setApiMasterId(apiMaster.getKey().getId());
        requestApi.setApiCode(apiMaster.getApiCode());
        requestApi.setApiName(apiMaster.getApiName());
        requestApi.setObjectStatus(OBJECT_STATUS_ACTIVE);
        requestApi.setCreatedBy(userId);
        requestApi.setLastUpdatedBy(userId);
        return requestApi;
    }

    private List<HostToHostApiAuthorizationDTO> fetchApiAuthorizations(Set<String> selectedCodes) throws Exception {
        HthApiMaster apiMasterDomain = new HthApiMaster();
        List<HthApiMaster> apiMasters = apiMasterDomain.listActive();
        if (apiMasters == null) {
            apiMasters = Collections.emptyList();
        }

        Set<String> selected = selectedCodes == null ? new HashSet<String>() : selectedCodes;
        List<HostToHostApiAuthorizationDTO> response = new ArrayList<HostToHostApiAuthorizationDTO>();
        for (HthApiMaster apiMaster : apiMasters) {
            HostToHostApiAuthorizationDTO dto = new HostToHostApiAuthorizationDTO();
            dto.setApiCode(apiMaster.getApiCode());
            dto.setApiName(apiMaster.getApiName());
            dto.setSelected(selected.contains(apiMaster.getApiCode()));
            response.add(dto);
        }
        return response;
    }

    private Set<String> fetchSelectedApiCodes(String managementId) throws Exception {
        Set<String> selectedCodes = new HashSet<String>();
        if (isBlank(managementId)) {
            return selectedCodes;
        }

        HthManagementApi managementApiDomain = new HthManagementApi();
        List<HthManagementApi> mappings = managementApiDomain.listActiveByManagementId(managementId);
        if (mappings == null || mappings.isEmpty()) {
            return selectedCodes;
        }

        HthApiMaster apiMasterDomain = new HthApiMaster();
        for (HthManagementApi mapping : mappings) {
            if (mapping == null || isBlank(mapping.getApiMasterId())) {
                continue;
            }
            HthApiMasterKey key = new HthApiMasterKey();
            key.setId(mapping.getApiMasterId());
            HthApiMaster apiMaster = apiMasterDomain.read(key);
            if (apiMaster != null && OBJECT_STATUS_ACTIVE.equals(apiMaster.getObjectStatus())
                    && !isBlank(apiMaster.getApiCode())) {
                selectedCodes.add(apiMaster.getApiCode());
            }
        }
        return selectedCodes;
    }

    private Map<String, HthApiMaster> fetchActiveApiByCode(Set<String> apiCodes) throws Exception {
        if (apiCodes == null || apiCodes.isEmpty()) {
            return new HashMap<String, HthApiMaster>();
        }

        HthApiMaster apiMasterDomain = new HthApiMaster();
        return mapApiMastersByCode(apiMasterDomain.listActiveByApiCodes(apiCodes));
    }

    private Map<String, HthApiMaster> fetchActiveApiByCodeForSnapshot(Set<String> apiCodes) throws Exception {
        if (apiCodes == null || apiCodes.isEmpty()) {
            return new HashMap<String, HthApiMaster>();
        }

        List<HthApiMaster> rows = LocalHthApiMasterRepositoryAdapter.getInstance().listActiveByApiCodes(apiCodes);
        return mapApiMastersByCode(rows);
    }

    private Map<String, HthApiMaster> mapApiMastersByCode(List<HthApiMaster> rows) {
        Map<String, HthApiMaster> result = new HashMap<String, HthApiMaster>();
        if (rows == null) {
            return result;
        }
        for (HthApiMaster row : rows) {
            result.put(row.getApiCode(), row);
        }
        return result;
    }

    private Set<String> extractSelectedApiCodesForAction(HostToHostManagementDTO requestDTO, String actionType) {
        if (isDisableAction(actionType)) {
            return Collections.emptySet();
        }
        return extractSelectedApiCodes(requestDTO);
    }

    private HthManagement findCurrentManagementForSnapshot(HostToHostManagementDTO requestDTO, String actionType)
            throws Exception {
        if (!isDisableAction(actionType) || requestDTO == null
                || !isBlank(requestDTO.getCorrelateUamClientId())) {
            return null;
        }
        return LocalHthManagementRepositoryAdapter.getInstance().findActiveByPartyId(requestDTO.getPartyId());
    }

    private Set<String> extractSelectedApiCodes(HostToHostManagementDTO requestDTO) {
        Set<String> selectedApiCodes = new LinkedHashSet<String>();
        if (requestDTO == null || requestDTO.getApiAuthorizations() == null) {
            return selectedApiCodes;
        }
        for (HostToHostApiAuthorizationDTO apiAuthorization : requestDTO.getApiAuthorizations()) {
            if (apiAuthorization == null || !Boolean.TRUE.equals(apiAuthorization.getSelected())) {
                continue;
            }
            String apiCode = normalize(apiAuthorization.getApiCode());
            if (!isBlank(apiCode)) {
                selectedApiCodes.add(apiCode);
            }
        }
        return selectedApiCodes;
    }

    private boolean hasSelectedApiAuthorization(HostToHostManagementDTO requestDTO) {
        if (requestDTO.getApiAuthorizations() == null || requestDTO.getApiAuthorizations().isEmpty()) {
            return false;
        }
        for (HostToHostApiAuthorizationDTO apiAuthorization : requestDTO.getApiAuthorizations()) {
            if (apiAuthorization != null && Boolean.TRUE.equals(apiAuthorization.getSelected())) {
                return true;
            }
        }
        return false;
    }

    private boolean isApprovedExecution() {
        Object approvalStatus = com.ofss.fc.infra.thread.ThreadAttribute
                .get(com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
        if (approvalStatus == null) {
            approvalStatus = ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.APPROVAL_STATUS);
        }
        return ApprovalStatus.APPROVED.toString().equals(String.valueOf(approvalStatus));
    }

    private void populateUamValidationResponse(HostToHostManagementResponseDTO response,
                                               HostToHostManagementDTO requestDTO) throws Exception {
        if (requestDTO == null || isBlank(requestDTO.getCorrelateUamClientId())) {
            response.setValidUamClientId(Boolean.FALSE);
            return;
        }

        String uamClientId = normalize(requestDTO.getCorrelateUamClientId());
        Boolean valid = validateDspClientId(uamClientId);
        populateUamValidationResult(response, requestDTO, uamClientId, valid);
        populateDefaultManagementOptions(response);
    }

    private Boolean validateDspClientId(String uamClientId) throws Exception {
        DspClient dspClient = DSPApiClientFactory.getDspClient();
        ClientApi clientApi = dspClient.clientApi();
        ClientValidationParam clientValidationParam = new ClientValidationParam();
        clientValidationParam.setClientId(uamClientId);
        ClientValidationResult clientValidationResult = clientApi.validateClientId(clientValidationParam);
        return Boolean.valueOf(Boolean.TRUE.equals(clientValidationResult.getValid()));
    }

    private void populateUamValidationResult(HostToHostManagementResponseDTO response,
                                             HostToHostManagementDTO requestDTO, String uamClientId,
                                             Boolean valid) throws Exception {
        if (Boolean.TRUE.equals(valid) && !isUamClientIdAvailable(requestDTO.getPartyId(), uamClientId)) {
            response.setValidUamClientId(Boolean.FALSE);
            response.setErrorMessage(UAM_CLIENT_ID_ALREADY_BOUND_MESSAGE);
        } else {
            response.setValidUamClientId(valid);
        }

        response.setPartyId(requestDTO.getPartyId());
        response.setCorrelateUamClientId(uamClientId);
    }

    private void populateDefaultManagementOptions(HostToHostManagementResponseDTO response) throws Exception {
        response.setApiApprovalFlowType(APPROVAL_FLOW_STRAIGHT_THROUGH);
        response.setApiAuthorizations(fetchApiAuthorizations(new HashSet<String>()));
    }

    private void assertUamClientIdAvailable(String partyId, String uamClientId) throws Exception {
        if (!isUamClientIdAvailable(partyId, uamClientId)) {
            throw new IllegalArgumentException(UAM_CLIENT_ID_ALREADY_BOUND_MESSAGE);
        }
    }

    private void assertUamClientIdUnchangedForEdit(HostToHostManagementDTO requestDTO, String actionType)
            throws Exception {
        if (!isEditAction(actionType)) {
            return;
        }

        HthManagement managementDomain = new HthManagement();
        HthManagement currentManagement = managementDomain.findActiveByPartyId(requestDTO.getPartyId());
        String currentUamClientId = currentManagement == null ? null : normalize(currentManagement.getUamClientId());
        String requestedUamClientId = normalize(requestDTO.getCorrelateUamClientId());
        if (currentUamClientId == null || !currentUamClientId.equals(requestedUamClientId)) {
            throw new IllegalArgumentException("Correlate UAM Client ID cannot be changed after Host to Host is enabled.");
        }
    }

    private void assertConfigurationChangedForEdit(HostToHostManagementDTO requestDTO) throws Exception {
        HthManagement managementDomain = new HthManagement();
        HthManagement currentManagement = managementDomain.findActiveByPartyId(requestDTO.getPartyId());
        if (currentManagement == null || currentManagement.getKey() == null) {
            return;
        }

        Set<String> currentApiCodes = fetchSelectedApiCodes(currentManagement.getKey().getId());
        Set<String> requestedApiCodes = extractSelectedApiCodes(requestDTO);

        if (isApprovalFlowUnchanged(currentManagement, requestDTO) && currentApiCodes.equals(requestedApiCodes)) {
            throw new IllegalArgumentException(
                    "No changes were detected for Host to Host Management. Please update the configuration before submitting.");
        }
    }

    private boolean isApprovalFlowUnchanged(HthManagement currentManagement, HostToHostManagementDTO requestDTO) {
        String currentApprovalFlowType = normalize(currentManagement.getApprovalFlowType());
        String requestedApprovalFlowType = normalize(requestDTO.getApiApprovalFlowType());
        return currentApprovalFlowType == null
                ? requestedApprovalFlowType == null
                : currentApprovalFlowType.equals(requestedApprovalFlowType);
    }

    private boolean isUamClientIdAvailable(String partyId, String uamClientId) throws Exception {
        String normalizedUamClientId = normalize(uamClientId);
        if (normalizedUamClientId == null) {
            return false;
        }

        if (isRegisteredUamClientId(normalizedUamClientId)) {
            return false;
        }

        if (isBoundToAnotherParty(partyId, normalizedUamClientId)) {
            return false;
        }

        return !hasPendingUamClientIdRequest(normalizedUamClientId);
    }

    private boolean isRegisteredUamClientId(String uamClientId) throws Exception {
        HthUamClientRegistry registryDomain = new HthUamClientRegistry();
        return registryDomain.existsRegisteredUamClientId(uamClientId);
    }

    private boolean isBoundToAnotherParty(String partyId, String uamClientId) throws Exception {
        HthManagement managementDomain = new HthManagement();
        HthManagement existing = managementDomain.findActiveByUamClientId(uamClientId);
        if (existing == null) {
            return false;
        }

        String existingPartyId = normalize(existing.getPartyId());
        String requestPartyId = normalize(partyId);
        return existingPartyId == null || !existingPartyId.equals(requestPartyId);
    }

    private boolean hasPendingUamClientIdRequest(String uamClientId) throws Exception {
        HthRequest requestDomain = new HthRequest();
        return requestDomain.existsPendingApprovalByUamClientId(uamClientId);
    }

    private void populateResponse(HostToHostManagementResponseDTO response, HostToHostManagementDTO detail) {
        if (detail == null) {
            return;
        }
        response.setPartyId(detail.getPartyId());
        response.setAccountNumber(detail.getAccountNumber());
        response.setAccountName(detail.getAccountName());
        response.setCinNumber(detail.getCinNumber());
        response.setHostToHostApiChannelEnabled(detail.getHostToHostApiChannelEnabled());
        response.setCorrelateUamClientId(detail.getCorrelateUamClientId());
        response.setApiApprovalFlowType(detail.getApiApprovalFlowType());
        response.setApiAuthorizations(detail.getApiAuthorizations());
    }

    private HostToHostManagementDTO buildDetail(HostToHostManagementDTO requestDTO) throws Exception {
        String partyId = normalize(requestDTO.getPartyId());
        if (partyId == null) {
            return null;
        }

        HostToHostManagementDTO record = new HostToHostManagementDTO();
        record.setPartyId(partyId);
        populateStoredHostToHostConfig(record, partyId);
        return record;
    }

    private void populateStoredHostToHostConfig(HostToHostManagementDTO record, String partyId) throws Exception {
        HthManagement managementDomain = new HthManagement();
        HthManagement management = managementDomain.findActiveByPartyId(partyId);
        String hthStatus = null;
        String managementId = null;

        if (management != null) {
            managementId = management.getKey() == null ? null : management.getKey().getId();
            hthStatus = normalize(management.getHthStatus());
            if (HTH_STATUS_ENABLE.equals(hthStatus)) {
                record.setCorrelateUamClientId(management.getUamClientId());
                record.setApiApprovalFlowType(management.getApprovalFlowType());
            }
        }

        Set<String> selectedCodes = HTH_STATUS_ENABLE.equals(hthStatus) ? fetchSelectedApiCodes(managementId)
                : new HashSet<String>();
        record.setHostToHostApiChannelEnabled(Boolean.valueOf(HTH_STATUS_ENABLE.equals(hthStatus)));
        record.setApiAuthorizations(fetchApiAuthorizations(selectedCodes));

        if (isBlank(record.getApiApprovalFlowType())) {
            record.setApiApprovalFlowType(APPROVAL_FLOW_STRAIGHT_THROUGH);
        }
    }

    private void setExternalReferenceNumber(String externalReferenceNumber) {
        ThreadAttribute.set(ThreadAttribute.EXTERNAL_REFERENCE_NUMBER, externalReferenceNumber);
        com.ofss.fc.infra.thread.ThreadAttribute.set(
                com.ofss.fc.infra.thread.ThreadAttribute.EXTERNAL_REFERENCE_NUMBER, externalReferenceNumber);
    }

    private void restoreExternalReferenceNumber(boolean saveCompleted, String referenceNumber) {
        if (saveCompleted && !isBlank(referenceNumber)) {
            setExternalReferenceNumber(referenceNumber);
        }
    }

    private String generateReferenceNumber(String actionType) {
        String code;
        if (isDisableAction(actionType)) {
            code = "DIS";
        } else if (isEditAction(actionType)) {
            code = "EDT";
        } else {
            code = "ENB";
        }
        return "HTH" + code + generateId("HTH_REFERENCE_NO");
    }

    private String generateId(String subcategory) {
        return UUID.randomUUID().toString();
    }

    private String requireTransactionId() {
        String transactionId = normalize(readTransactionId());
        if (transactionId == null) {
            throw new IllegalStateException("Transaction reference number is not available from approval framework.");
        }
        return transactionId;
    }

    private String readTransactionId() {
        Object transactionId = ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO);
        if (transactionId == null) {
            transactionId = ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
        }
        if (transactionId == null) {
            transactionId = com.ofss.fc.infra.thread.ThreadAttribute
                    .get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
        }
        if (transactionId == null) {
            transactionId = com.ofss.fc.infra.thread.ThreadAttribute
                    .get(com.ofss.fc.infra.thread.ThreadAttribute.INTERNAL_REFERENCE_NUMBER);
        }
        return transactionId == null ? null : String.valueOf(transactionId);
    }

    private String readUserId(SessionContext sessionContext) {
        if (sessionContext == null || isBlank(sessionContext.getUserId())) {
            return "system";
        }
        return sessionContext.getUserId();
    }

    private boolean isEnableAction(String actionType) {
        return ACTION_ENABLE.equals(actionType);
    }

    private boolean isEditAction(String actionType) {
        return ACTION_EDIT.equals(actionType);
    }

    private boolean isDisableAction(String actionType) {
        return ACTION_DISABLE.equals(actionType);
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
