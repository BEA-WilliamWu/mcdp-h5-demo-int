package com.ofss.digx.cz.bea.app.approval.service.transaction;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.security.auth.Subject;

import com.ofss.digx.annotations.Entitlement;
import com.ofss.digx.annotations.EntitlementGroup;
import com.ofss.digx.annotations.NoEntitlement;
import com.ofss.digx.annotations.Task;
import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.app.ServiceInvocationHelper;
import com.ofss.digx.app.access.PolicyAssertionContext;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.adapter.account.IAccountAdapter;
import com.ofss.digx.app.alerts.dto.eventgen.ActivityLog;
import com.ofss.digx.app.approval.adapter.rule.IRuleAdapter;
import com.ofss.digx.app.approval.adapter.rulecriteria.IRuleCriteriaAdapter;
import com.ofss.digx.app.approval.adapter.transaction.workflowsnapshot.ITransactionWorkflowSnapshotAdapter;
import com.ofss.digx.app.approval.alert.mapper.ITransactionActivityLogMapper;
import com.ofss.digx.app.approval.alert.mapper.config.TransactionActivityLogMapperFactory;
import com.ofss.digx.app.approval.assembler.transaction.TransactionAssembler;
import com.ofss.digx.app.approval.dto.rule.RuleCriteriaDTO;
import com.ofss.digx.app.approval.dto.rule.RuleCriteriaHandlerDTO;
import com.ofss.digx.app.approval.dto.rule.RuleCriteriaListResponse;
import com.ofss.digx.app.approval.dto.rule.RuleDTO;
import com.ofss.digx.app.approval.dto.rule.RuleListResponse;
import com.ofss.digx.app.approval.dto.rule.relation.rulecriteria.RuleRuleCriteriaRelationshipDTO;
import com.ofss.digx.app.approval.dto.transaction.AccountTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.AmountAccountTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.ApprovalActivityLog;
import com.ofss.digx.app.approval.dto.transaction.PartyTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionHistoryDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionListResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionResponse;
import com.ofss.digx.app.approval.dto.transaction.transactioninfo.TransactionDataDTO;
import com.ofss.digx.app.approval.dto.transaction.workflowsnapshot.TransactionWorkflowSnapshotDTO;
import com.ofss.digx.app.approval.dto.transaction.workflowsnapshot.TransactionWorkflowSnapshotListResponseDTO;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupDTO;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupListResponseDTO;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupUserDTO;
import com.ofss.digx.app.approval.dto.workflow.WorkflowStepDTO;
import com.ofss.digx.app.approval.service.rule.IRuleWeightageEvaluator;
import com.ofss.digx.app.approval.service.rule.RuleWeightageEvaluator;
import com.ofss.digx.app.approval.service.rulecriteria.handler.AbstractRuleCriteriaHandlerFactory;
import com.ofss.digx.app.approval.service.rulecriteria.handler.IRuleCriteriaHandler;
import com.ofss.digx.app.approval.service.rulecriteria.handler.RuleCriteriaHandlerFactory;
import com.ofss.digx.app.approval.service.transaction.EvaluateWorkflowsPanelSystemConstraint;
import com.ofss.digx.app.approval.service.transaction.EvaluateWorkflowsRuleSystemConstraint;
import com.ofss.digx.app.approval.service.transaction.PerformActionSystemConstraint;
import com.ofss.digx.app.approval.service.transaction.TransactionApprovalAccessCheckConstraint;
import com.ofss.digx.app.approval.service.transaction.evaluator.ActionStatusEvaluatorFactory;
import com.ofss.digx.app.approval.service.transaction.evaluator.IActionStatusEvaluator;
import com.ofss.digx.app.approval.service.transaction.ext.ITransactionExtExecutor;
import com.ofss.digx.app.approval.service.transaction.processor.status.ITransactionProcessingDetailsBuilder;
import com.ofss.digx.app.approval.service.transaction.processor.status.TransactionProcessingDetailsBuilderFactory;
import com.ofss.digx.app.approval.service.transaction.snapshothandler.ITransactionSnapshotHandler;
import com.ofss.digx.app.approval.service.transaction.snapshothandler.TransactionSnapshotHandlerFactory;
import com.ofss.digx.app.batch.dto.BatchDetailRequestDTO;
import com.ofss.digx.app.collaboration.dto.mailer.MailerDTO;
import com.ofss.digx.app.collaboration.dto.mailer.RecipientDTO;
import com.ofss.digx.app.common.adapter.resource.task.ITaskAdapter;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.app.dto.accesspoint.AccessPointAspectDTO;
import com.ofss.digx.app.dto.accesspoint.AccessPointDTO;
import com.ofss.digx.app.ebpp.dto.billpayment.BillPaymentDTO;
import com.ofss.digx.app.ext.ServiceExtensionFactory;
import com.ofss.digx.app.finlimit.adapter.limitutilization.ILimitUtilizationAdapter;
import com.ofss.digx.app.finlimit.exceptions.FinancialLimitExhaustedException;
import com.ofss.digx.app.party.adapter.profile.IPartyPreferencesAdapter;
import com.ofss.digx.app.party.dto.PartyDetailsDTO;
import com.ofss.digx.app.party.dto.profile.PartyPreferencesDTO;
import com.ofss.digx.app.party.dto.profile.PartyPreferencesResponse;
import com.ofss.digx.app.security.adapter.IRolePreferencesAdapter;
import com.ofss.digx.app.security.exceptions.tfa.TFARequiredException;
import com.ofss.digx.app.security.utils.authentication.processing.TaskAuthProcessing;
import com.ofss.digx.app.sms.adapter.blackout.ITransactionBlackoutAdapter;
import com.ofss.digx.app.sms.adapter.user.IUserPartyAdapter;
import com.ofss.digx.app.sms.dto.user.UserPartyDetailsRequestDTO;
import com.ofss.digx.common.Constants;
import com.ofss.digx.common.constants.ApprovalConstants;
import com.ofss.digx.common.constants.ApprovalsErrorConstants;
import com.ofss.digx.common.constants.CommonConstants;
import com.ofss.digx.common.constants.PreferencesConstants;
import com.ofss.digx.common.constants.TransactionExpandParamsConstants;
import com.ofss.digx.core.adapter.AdapterFactory;
import com.ofss.digx.core.adapter.subject.ISubjectAdapter;
import com.ofss.digx.cz.bea.app.approval.adapter.transaction.ITransactionAdapter;
import com.ofss.digx.cz.bea.app.approval.dto.transaction.TransactionNlsNameResponse;
import com.ofss.digx.cz.bea.app.bulkupload.fileupload.dto.BulkFileUploadsDTO;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.common.service.BranchDateHelper;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.app.crm.adapter.ICRMAsserterCallAdapter;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.customconfig.util.CustomConfigUtil;
import com.ofss.digx.cz.bea.app.dda.adapter.statement.adhoc.IAdhocStatementAdapter;
import com.ofss.digx.cz.bea.app.dda.dto.statement.adhoc.AdhocStatementDTO;
import com.ofss.digx.cz.bea.app.itoken.dto.ITokenDTO;
import com.ofss.digx.cz.bea.app.liquiditymanagement.dto.lmInstruction.CZLMCreateRequestDTO;
import com.ofss.digx.cz.bea.app.partyaccountaccess.adapter.ICZPartyAccountAccess;
import com.ofss.digx.cz.bea.app.td.dto.preferentialtd.PreferentialTDDetailsDTO;
import com.ofss.digx.cz.bea.app.transactiontfa.dto.TransactionTFAResponseDTO;
import com.ofss.digx.cz.bea.approval.dto.*;
import com.ofss.digx.cz.bea.approval.dto.MultiApprovalTransactionDTO;
import com.ofss.digx.cz.bea.approval.dto.MultiApprovalTransactionListDTO;
import com.ofss.digx.cz.bea.approval.dto.RejectTransactionMakerDTO;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.digx.cz.bea.common.util.InputValidationUtils;
import com.ofss.digx.cz.bea.domain.approval.entity.transaction.MultipleApproval;
import com.ofss.digx.cz.bea.domain.itoken.entity.itktokens.repository.ITKTokensRepository;
import com.ofss.digx.cz.bea.domain.td.entity.preferentialtd.PreferentialTDM3WebmailDTO;
import com.ofss.digx.cz.bea.framework.domain.transaction.repository.adapter.CZLocalTransactionRepositoryAdapter;
import com.ofss.digx.datatype.complex.Account;
import com.ofss.digx.datatype.complex.Party;
import com.ofss.digx.domain.approval.entity.transaction.checkerdetails.ApprovalCheckerDetails;
import com.ofss.digx.domain.approval.entity.transaction.checkerdetails.repository.ApprovalCheckerDetailsRepository;
import com.ofss.digx.domain.approval.entity.transaction.graceperiodexpiryalert.ApprovalGracePeriodExpiryAlert;
import com.ofss.digx.domain.approval.entity.transaction.graceperiodexpiryalert.ApprovalGracePeriodExpiryAlertKey;
import com.ofss.digx.domain.approval.entity.transaction.graceperiodexpiryalert.repository.ApprovalGracePeriodExpiryAlertRepository;
import com.ofss.digx.domain.approval.entity.transaction.policy.CheckApprovalsBusinessPolicyDTO;
import com.ofss.digx.domain.approval.entity.transaction.policy.ReadTransactionBusinessPolicyDTO;
import com.ofss.digx.domain.approval.entity.transaction.policy.TransactionBusinessPolicyDTO;
import com.ofss.digx.domain.approval.entity.transaction.workflowsnapshot.TransactionWorkflowSnapshot;
import com.ofss.digx.domain.approval.entity.transaction.workflowsnapshot.TransactionWorkflowSnapshotKey;
import com.ofss.digx.enumeration.AccessPointStatus;
import com.ofss.digx.enumeration.AccessPointType;
import com.ofss.digx.enumeration.ModuleType;
import com.ofss.digx.enumeration.approval.ApprovalAction;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.approval.ProcessingStatus;
import com.ofss.digx.enumeration.approval.ProcessingStep;
import com.ofss.digx.enumeration.approval.UserType;
import com.ofss.digx.enumeration.collaboration.mailbox.message.mailer.RecipientType;
import com.ofss.digx.enumeration.collaboration.mailbox.message.mailer.TriggerType;
import com.ofss.digx.enumeration.common.EntityType;
import com.ofss.digx.enumeration.finlimit.LimitType;
import com.ofss.digx.enumeration.partyPreference.ApprovalType;
import com.ofss.digx.enumeration.security.ActionType;
import com.ofss.digx.enumeration.security.EntitlementCategory;
import com.ofss.digx.enumeration.security.EntitlementSubCategory;
import com.ofss.digx.enumeration.sr.PriorityType;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.TaskType;
import com.ofss.digx.extxface.exceptions.ExtSystemTempUnavailableExeception;
import com.ofss.digx.framework.domain.business.policy.factory.BusinessPolicyFactory;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.framework.domain.serviceworker.ServiceWorker;
import com.ofss.digx.framework.domain.serviceworker.ServiceWorkerKey;
import com.ofss.digx.framework.domain.transaction.AccountTransaction;
import com.ofss.digx.framework.domain.transaction.AmountAccountTransaction;
import com.ofss.digx.framework.domain.transaction.AmountTransaction;
import com.ofss.digx.framework.domain.transaction.TransactionApprovalDetails;
import com.ofss.digx.framework.domain.transaction.TransactionApprovalHistory;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.framework.domain.transaction.TransactionUserDetails;
import com.ofss.digx.framework.domain.transaction.assembler.AbstractApprovalAssembler;
import com.ofss.digx.framework.domain.transaction.assembler.ApprovalAssemblerFactory;
import com.ofss.digx.framework.domain.transaction.assembler.TransactionAssemblerFactory;
import com.ofss.digx.framework.security.principals.RolePrincipal;
import com.ofss.digx.framework.security.principals.UserPrincipal;
import com.ofss.digx.framework.security.twofactor.TFAErrorConstant;
import com.ofss.digx.framework.task.evaluator.ITaskEvaluator;
import com.ofss.digx.framework.task.evaluator.ITaskEvaluatorFactory;
import com.ofss.digx.framework.task.evaluator.TaskEvaluatorConfigurator;
import com.ofss.digx.infra.date.DateHelper;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.infra.exceptions.ExceptionTransformerFactory;
import com.ofss.digx.infra.exceptions.IExceptionTransformer;
import com.ofss.digx.service.response.BaseResponseObject;
import com.ofss.extsystem.framework.utils.dbaccess.JDBCEngine;
import com.ofss.extsystem.framework.utils.dbaccess.JDBCResultSet;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.app.sms.security.dto.UserDTO;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.domain.commonservice.entity.id.definition.AbstractGeneratorFactory;
import com.ofss.fc.domain.commonservice.entity.id.generation.IdGenerator;
import com.ofss.fc.enumeration.ep.SubscriberValue;
import com.ofss.fc.framework.domain.assembler.AbstractAssembler;
import com.ofss.fc.framework.domain.common.dto.DataTransferObject;
import com.ofss.fc.framework.domain.policy.AbstractBusinessPolicy;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.exception.ExtendedReply;
import com.ofss.fc.infra.exception.FatalException;
import com.ofss.fc.infra.exception.ReplyMessage;
import com.ofss.fc.infra.exception.RunTimeException;
import com.ofss.fc.infra.jdbc.ConnectionUtil;
import com.ofss.fc.infra.locale.LocaleUtils;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.infra.validation.error.ValidationError;
import com.ofss.fc.infra.validation.exception.SystemConstraintViolationException;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.utils.SerializationUtils;
import com.ofss.fc.xface.ep.dto.NotificationDetail;

import oracle.security.jps.util.SubjectUtil;

public class Transaction extends AbstractApplication implements ICZTransaction {

	private transient ITransactionExtExecutor executor;

	private static final String THIS_COMPONENT_NAME = Transaction.class.getName();

	/**
	 * @param expandQueryParams
	 */
	public Transaction(String expandQueryParams) {
		super.setExpandParamsList(expandQueryParams);
		executor = (ITransactionExtExecutor) ServiceExtensionFactory.getServiceExtensionExecutor(THIS_COMPONENT_NAME);
	}

	/**
	 *
	 */
	public Transaction() {
		executor = (ITransactionExtExecutor) ServiceExtensionFactory.getServiceExtensionExecutor(THIS_COMPONENT_NAME);
	}

	/**
	 * {@link String} key for Base Configuration.
	 */
	public static final String BASE_CONFIGURATION = "BaseConfig";

	private static final String BUNDLE_NAME = "resources.nls.CommonTask";

	private static final String BUNDLE_NAME_1 = "resources.nls.Tdm3CouponWebmail";

	private static final Preferences preferences = ConfigurationFactory.getInstance()
			.getConfigurations(CommonConstants.DAY_ONE_CONFIG);
	/**
	 * Constant for USER_LOCALE
	 */
	private static final String USER_LOCALE = "USER_LOCALE";

	/**
	 * {@link String} key for Base Request URL.
	 */
	public static final String BASE_REQUEST_URL = "BASE_REQUEST_URL";

	private static final String OMB_REF_NO = "OMB_REF_NO";

	/**
	 * Indicates request data transfer object containing batch request
	 */
	private BatchDetailRequestDTO request;

	public static final String FINANCIAL_TRANSACTION_INITIATED = "com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals.financial";
	/**
	 * Activity Id to trigger the alert when a amount financial transaction is
	 * initiated.
	 */
	public static final String AMOUNT_FINANCIAL_TRANSACTION_INITIATED = "com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals.amount_financial";
	/**
	 * Activity Id to trigger the alert when a non-financial transaction is
	 * initiated.
	 */
	public static final String NON_FINANCIAL_TRANSACTION_INITIATED = "com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals.nonfinancial";
	/**
	 * Activity Id to trigger the alert when a maintenance transaction is initiated.
	 */
	public static final String MAINTENANCE_TRANSACTION_INITIATED = "com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals.maintenance";

	private static final String TRANSACTION_REPOSITORY_ADAPTER = "TRANSACTION_REPOSITORY_ADAPTER";

	private static final String GENERIC_SERVICE_ID = "com.ofss.digx.app.generic.rest.service.GenericRestService";;
	/**
	 * Represents cookies
	 */
	private javax.servlet.http.Cookie[] cookies;

	/**
	 * Holds the instance of {@link MultiEntityLogger} used for sending messages on
	 * the console.
	 */
	private com.ofss.fc.infra.log.impl.MultiEntityLogger FORMATTER = com.ofss.fc.infra.log.impl.MultiEntityLogger
			.getUniqueInstance();

	/**
	 * Instance of type {@link java.util.logging.Logger} used for Logging in Rule
	 * service.
	 */
	private static transient Logger LOGGER = com.ofss.fc.infra.log.impl.MultiEntityLogger.getUniqueInstance()
			.getLogger(THIS_COMPONENT_NAME);

	@SuppressWarnings("unchecked")
	@Override
	@NoEntitlement
	public TransactionStatus checkApprovals(SessionContext sessionContext, Object requestDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into evaluateWorkFlow() : requestDTO=%s in class '%s'", requestDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals",
				sessionContext, requestDTO);
		Interaction.begin(sessionContext);
		try {
			// executor.preCheckApprovals(sessionContext, requestDTO);
			AbstractBusinessPolicy abstractBusinessPolicy = null;

			BusinessPolicyFactory businessPolicyFactory = BusinessPolicyFactory.getInstance();
			CheckApprovalsBusinessPolicyDTO transactionBusinessPolicyDTO = new CheckApprovalsBusinessPolicyDTO();
			transactionBusinessPolicyDTO.setRequestDTO(requestDTO);
			abstractBusinessPolicy = businessPolicyFactory.getBusinesPolicyInstance(
					"com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals",
					transactionBusinessPolicyDTO);
			abstractBusinessPolicy.validate();
			String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
			HashMap<String, Object> serviceInputs = (HashMap<String, Object>) ThreadAttribute
					.get(ThreadAttribute.SERVICE_INPUTS);
			List<Object> serviceParams = null;
			if (serviceInputs != null) {
				serviceParams = (List<Object>) serviceInputs.get(CommonConstants.PARAMETERS);
			}
			String evaluatedTaskId = TaskEvaluatorConfigurator.getInstance().getEvaluatorFactory(taskId)
					.getEvaluator(TaskAspect.APPROVALS).evaluateTaskCode(taskId, serviceParams);
			IAdapterFactory taskAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.common.constants.CommonAdapterFactoryConstants.TASK_ADAPTER_FACTORY);
			ITaskAdapter taskAdapter = (ITaskAdapter) taskAdapterFactory
					.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.TASK_ADAPTER);
			TaskDTO taskDTO = null;
			taskDTO = taskAdapter.read(evaluatedTaskId);
			if (taskDTO != null && taskDTO.isExecutable() != null && taskDTO.isExecutable()) {
				Boolean approvalSupported = taskDTO.isAspectEnabled(TaskAspect.APPROVALS);
				if (approvalSupported != null && approvalSupported) {

					ApprovalType approvalType = getApprovalType();
					if (approvalType != null && requestDTO instanceof DataTransferObject) {
						if (!isDuplicateRecord((DataTransferObject) requestDTO)) {
							TransactionStatus transactionStatus = evaluateWorkFlows((DataTransferObject) requestDTO,
									approvalType);

						} else {
							LOGGER.log(Level.SEVERE, "Duplicate transaction not permitted");
							ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
							IExceptionTransformer transformer = transformerFactory
									.getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
							Exception e = new Exception();
							transformer.translate(e, ApprovalsErrorConstants.DUPLICATE_TRANSACTION, Transaction.class);

						}
					}
				}
			}
			// executor.postCheckApprovals(sessionContext, requestDTO,
			// fetchTransactionStatus());
		} catch (com.ofss.digx.infra.exceptions.Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exception from update() for DomainObjectDTO '%s' in class %s", requestDTO,
							THIS_COMPONENT_NAME),
					e);
			fillTransactionStatus(fetchTransactionStatus(), e);
			ThreadAttribute.set(ThreadAttribute.WORKFLOWS_EVALUATED, false);
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from update() for DomainObjectDTO '%s' in class %s",
							requestDTO, THIS_COMPONENT_NAME),
					rte);
			ThreadAttribute.set(ThreadAttribute.WORKFLOWS_EVALUATED, false);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, fetchTransactionStatus());
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Exiting from CZ update() : TransactionStatus=%s",
					fetchTransactionStatus()));
		}
		return fetchTransactionStatus();
	}

	private ApprovalType getApprovalType() throws Exception {
		ApprovalType approvalType = null;
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.common.constants.CommonAdapterFactoryConstants.ROLE_PREFERENCES_ADAPTER_FACTORY);
		IRolePreferencesAdapter rolePreferencesAdapter = (IRolePreferencesAdapter) adapterFactory
				.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.ROLE_PREFERENCES_ADAPTER);
		if (rolePreferencesAdapter.fetchRolePreference(PreferencesConstants.CUSTOMER_PREFERENCES).hasValue()) {
			if (rolePreferencesAdapter.fetchRolePreference(PreferencesConstants.PARTY_MAPPING).hasValue()) {
				IAdapterFactory adapterFactoryPP = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.common.constants.CommonAdapterFactoryConstants.PARTY_PREFERENCES_ADAPTER_FACTORY);
				IPartyPreferencesAdapter adapterPP = (IPartyPreferencesAdapter) adapterFactoryPP
						.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.PARTY_PREFERENCES_ADAPTER);
				PartyPreferencesDTO partyPreferencesDTO = new PartyPreferencesDTO();
				SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
				Party party = new Party();
				party.setValue(sessionContext.getTransactingPartyCode());
				partyPreferencesDTO.setParty(party);
				PartyPreferencesResponse preferences = adapterPP.read(sessionContext, partyPreferencesDTO);
				Boolean isEnabled = false;
				if (preferences != null && preferences.getPartyPreferencesDTOs() != null) {
					approvalType = preferences.getPartyPreferencesDTOs().getApprovalType();
					if (approvalType == null) {
						LOGGER.log(Level.SEVERE, "No prefernces for approval mentioned for  customer");
						ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
						IExceptionTransformer transformer = transformerFactory
								.getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
						Exception e = new Exception();
						transformer.translate(e, ApprovalsErrorConstants.PARTY_PREF_APROVAL_NOT_GIVEN,
								Transaction.class);
					}
					isEnabled = preferences.getPartyPreferencesDTOs().isEnabled();
					if (!isEnabled) {
						LOGGER.log(Level.SEVERE, "Prefernces mentioned for customer is disabled");
						ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
						IExceptionTransformer transformer = transformerFactory
								.getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
						Exception e = new Exception();
						transformer.translate(e, ApprovalsErrorConstants.PARTY_PREFERENCE_DIABLED, Transaction.class);
					} else {

					}
				} else {
					LOGGER.log(Level.SEVERE, "No prefernces mentioned for customer");
					ExceptionTransformerFactory transformerFactory = ExceptionTransformerFactory.getInstance();
					IExceptionTransformer transformer = transformerFactory
							.getTransformer(ExceptionTransformerFactory.DEFAULT_TRANSFORMER);
					Exception e = new Exception();
					transformer.translate(e, ApprovalsErrorConstants.PARTY_PREF_NOT_GIVEN, Transaction.class);
				}
			} else {
				// TBD throw some exception ...ideally this will never occur.
			}
		} else {
			if (!rolePreferencesAdapter.fetchRolePreference(PreferencesConstants.PARTY_MAPPING).hasValue()
					&& rolePreferencesAdapter.fetchRolePreference(PreferencesConstants.APPROVAL).hasValue()) {
				approvalType = ApprovalType.SEQUENTIAL;
			}
		}
		return approvalType;
	}

	@SuppressWarnings("rawtypes")
	private boolean isDuplicateRecord(DataTransferObject requestDTO) throws Exception {
		String serviceId = (String) ThreadAttribute.get(ThreadAttribute.SERVICE);
		AbstractApprovalAssembler assembler = ApprovalAssemblerFactory.getInstance()
				.getAssemblerInstance(serviceId.substring(serviceId.indexOf("^") + 1));
		if (assembler != null) {
			com.ofss.digx.framework.domain.transaction.Transaction transaction = assembler.toDomainObject(requestDTO);
			if (transaction.getEntityIdentifiers() != null) {
				List<com.ofss.digx.framework.domain.transaction.Transaction> transactions = listTransactionsForEntity(
						transaction);
				if (transactions != null && transactions.size() > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private List<com.ofss.digx.framework.domain.transaction.Transaction> listTransactionsForEntity(
			com.ofss.digx.framework.domain.transaction.Transaction transaction) throws Exception {
		return transaction.listTransactionsForEntity(transaction);

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private TransactionStatus evaluateWorkFlows(DataTransferObject requestDTO, ApprovalType approvalType)
			throws Exception {
		boolean isAutoAuth = true;
		String serviceId = (String) ThreadAttribute.get(ThreadAttribute.SERVICE);
		AbstractApprovalAssembler assembler = ApprovalAssemblerFactory.getInstance()
				.getAssemblerInstance(serviceId.substring(serviceId.indexOf("^") + 1));
		boolean isRequestForModification = false;
		String referenceNumber = null, versionNumber = null;
		List<RuleDTO> rules = new ArrayList<>();
		TransactionStatus transactionStatus = fetchTransactionStatus();
		if (assembler != null) {
			TransactionDTO transactionDTO = null;
			if(requestDTO!=null) {
				System.out.println("evaluateWorkFlows requestDTO:" + SerializationUtils.toJsonString(requestDTO)); 
			}
			com.ofss.digx.framework.domain.transaction.Transaction transaction = assembler.toDomainObject(requestDTO);
			if(transaction!=null) {
				System.out.println("evaluateWorkFlows transaction:" + SerializationUtils.toJsonString(transaction)); 
			}
			TransactionAssembler transactionAssembler = null;
			TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			AbstractAssembler abstractAssembler = transactionAssemblerFactory
					.getAssemblerInstance(transaction.getDiscriminator());
			if (abstractAssembler != null && abstractAssembler instanceof TransactionAssembler) {
				transactionAssembler = (TransactionAssembler) abstractAssembler;
			}
			if (transaction instanceof AccountTransaction) {
				if (((AccountTransaction) transaction).getAccountPartyId() == null) {
					setAccountPartyId((AccountTransaction) transaction);
				}
			}
			if (transaction.getApprovalDetails() != null) {
				transaction.getApprovalDetails().setApprovalType(approvalType);
			} else {
				TransactionApprovalDetails approvalDetails = new TransactionApprovalDetails();
				approvalDetails.setApprovalType(approvalType);
				transaction.setApprovalDetails(approvalDetails);
			}
			List<TransactionWorkflowSnapshotDTO> transactionWorkflowSnapshotDTOsToPersist = new ArrayList<>();
			System.out.println("evaluateWorkFlows approvalType:" + SerializationUtils.toJsonString(approvalType) ); 
			if (approvalType != ApprovalType.ZERO) {
				UserGroupListResponseDTO userGroupListResponseDTO = new UserGroupListResponseDTO();
				EvaluateWorkflowsPanelSystemConstraint evaluateWorkflowsPanelSystemConstraint = new EvaluateWorkflowsPanelSystemConstraint(
						userGroupListResponseDTO);
				evaluateWorkflowsPanelSystemConstraint.isSatisfiedBy();
				if (userGroupListResponseDTO.getUserGroupDTOs() != null
						&& userGroupListResponseDTO.getUserGroupDTOs().size() > 0) {
					transactionDTO = transactionAssembler.fromDomainObject(transaction);
					RuleListResponse ruleList = new RuleListResponse();
					List<RuleDTO> ruleDTOs = new ArrayList<>();
					IAdapterFactory ruleCriteriaAdapterFactory = AdapterFactoryConfigurator.getInstance()
							.getAdapterFactory(
									com.ofss.digx.common.constants.CommonAdapterFactoryConstants.RULECRITERIA_ADAPTER_FACTORY);
					IRuleCriteriaAdapter ruleCriteriaAdapter = (IRuleCriteriaAdapter) ruleCriteriaAdapterFactory
							.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.RULECRITERIA_ADAPTER);
					RuleCriteriaDTO ruleCriteriaDTO = new RuleCriteriaDTO();
					if (transactionDTO != null && transactionDTO.getTaskDTO() != null)
						ruleCriteriaDTO.setTaskType(transactionDTO.getTaskDTO().getType());
					ruleCriteriaDTO.setUserType(UserType.CUSTOMER);
					RuleCriteriaListResponse criteriaList = ruleCriteriaAdapter.search(ruleCriteriaDTO);
					List<RuleRuleCriteriaRelationshipDTO> associatedRuleCriterias = null;
					if (criteriaList != null && criteriaList.getRuleCriteriaDTOs() != null) {
						associatedRuleCriterias = new ArrayList<>();
						AbstractRuleCriteriaHandlerFactory ruleCriteriaHandlerFactory = RuleCriteriaHandlerFactory
								.getInstance();
						for (RuleCriteriaDTO ruleCriteria : criteriaList.getRuleCriteriaDTOs()) {
							IRuleCriteriaHandler handler = ruleCriteriaHandlerFactory
									.getRuleCriteriaHandler(ruleCriteria.getRuleCriteriaName());
							List<RuleRuleCriteriaRelationshipDTO> ruleCriteriaRelationshipList = handler
									.addRuleCriteriaRelationships(transactionDTO);
							if (ruleCriteriaRelationshipList != null && !ruleCriteriaRelationshipList.isEmpty()) {
								associatedRuleCriterias.addAll(ruleCriteriaRelationshipList);
							}
						}
					}
					for (UserGroupDTO panelDTO : userGroupListResponseDTO.getUserGroupDTOs()) {
						RuleDTO ruleDTO = new RuleDTO();
						ruleDTO.setInitiatorUserGroup(panelDTO);
						ruleDTO.setParty(panelDTO.getPartyId());
						ruleDTO.setAssociatedRuleCriterias(associatedRuleCriterias);
						ruleDTOs.add(ruleDTO);
					}
					// Fetch all rules applicable for the panels belonging to
					// the user.
					IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
							com.ofss.digx.common.constants.CommonAdapterFactoryConstants.RULE_ADAPTER_FACTORY);
					IRuleAdapter ruleAdapter = (IRuleAdapter) adapterFactory
							.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.RULE_ADAPTER);
					List<RuleDTO> filteredRules = new ArrayList<>();
					for (RuleDTO ruleDTO : ruleDTOs) {
						RuleListResponse ruleListResponseLocal = ruleAdapter.search(ruleDTO);
						if (ruleListResponseLocal != null && ruleListResponseLocal.getRuleDTOs() != null) {
							rules.addAll(ruleListResponseLocal.getRuleDTOs());
						}
					}
					// do not fit in at all.
					if (rules != null && !rules.isEmpty()) {
						AbstractRuleCriteriaHandlerFactory ruleCriteriaHandlerFactory = RuleCriteriaHandlerFactory
								.getInstance();
						for (RuleDTO rule : rules) {
							double totalWeightage = 0;
							for (RuleRuleCriteriaRelationshipDTO ruleCriteriaRelationship : rule
									.getAssociatedRuleCriterias()) {
								IRuleCriteriaHandler handler = ruleCriteriaHandlerFactory.getRuleCriteriaHandler(
										ruleCriteriaRelationship.getRuleCriteriaDTO().getRuleCriteriaName());
								RuleCriteriaHandlerDTO handlerDTO = new RuleCriteriaHandlerDTO();
								handlerDTO.setRuleDTO(rule);
								handlerDTO.setTransactionDTO(transactionDTO);
								Double multiplier = handler.getRuleCriteriaMultiplierForRule(handlerDTO);
								if (Constants.DOUBLE_VALUE_ZERO.equals(multiplier)) {
									totalWeightage = Constants.DOUBLE_VALUE_ZERO;
									break;
								}
								totalWeightage += (multiplier
										* ruleCriteriaRelationship.getRuleCriteriaDTO().getWeightage());
							}
							rule.setWeightage(totalWeightage);
							if (totalWeightage != Constants.DOUBLE_VALUE_ZERO) {
								filteredRules.add(rule);
							}
						}
					}
					// Check if atleast 1 rule is available for the transaction
					// to initiate. Throw exception otherwise.
					ruleList.setRuleDTOs(filteredRules);
					EvaluateWorkflowsRuleSystemConstraint evaluateWorkflowsRuleSystemConstraint = new EvaluateWorkflowsRuleSystemConstraint(
							ruleList);
					evaluateWorkflowsRuleSystemConstraint.isSatisfiedBy();
					IRuleWeightageEvaluator ruleWeightageEvaluator = new RuleWeightageEvaluator();
					TransactionWorkflowSnapshotDTO transactionWorkflowSnapshotDTO = null;
					List<RuleDTO> sortedRules = ruleWeightageEvaluator.getRuleWeightage(rules);
					UserGroupDTO panelDTO = null;
					if (sortedRules.size() > 0) {
						Set<UserGroupDTO> approvalPanels = new HashSet<>();
						double maxWeightage = -1;
						for (RuleDTO ruleDTO : sortedRules) {
							if (Constants.DOUBLE_VALUE_ZERO == ruleDTO.getWeightage()) {
								continue;
							}
							if (maxWeightage <= ruleDTO.getWeightage()) {
								if (ruleDTO.isApprovalRequired()) {
									isAutoAuth = false;
									if (ruleDTO.getWorkflowDto() != null && ruleDTO.getWorkflowDto().getSteps() != null
											&& ruleDTO.getWorkflowDto().getSteps().size() > 0) {
										panelDTO = ruleDTO.getWorkflowDto().getSteps().get(0).getUserGroup();
										if (approvalPanels.add(panelDTO)) {
											for (int i = 0; i < ruleDTO.getWorkflowDto().getSteps().size(); i++) {
												transactionWorkflowSnapshotDTO = new TransactionWorkflowSnapshotDTO();
												WorkflowStepDTO workflowStepDTO = ruleDTO.getWorkflowDto().getSteps()
														.get(i);
												transactionWorkflowSnapshotDTO
														.setStepNo(workflowStepDTO.getSequenceNo());
												if (approvalType.equals(ApprovalType.NONSEQUENTIAL)) {
													workflowStepDTO.setSequenceNo(1);
												} else {
													workflowStepDTO.setSequenceNo(workflowStepDTO.getSequenceNo());
												}
												transactionWorkflowSnapshotDTO.setWorkflowStepDTO(workflowStepDTO);
												transactionWorkflowSnapshotDTO
														.setWorkflowId(ruleDTO.getWorkflowDto().getWorkFlowId());
												transactionWorkflowSnapshotDTOsToPersist
														.add(transactionWorkflowSnapshotDTO);
											}
										}
									}
								}
							} else {
								break;
							}
							maxWeightage = ruleDTO.getWeightage();
						}
					}
				}
			}

			String compositeValue = (String) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID);
			if (compositeValue != null && !compositeValue.isEmpty()) {
				isRequestForModification = true;
				String[] compositeValueArray = compositeValue.split("#");
				referenceNumber = compositeValueArray[0];
				versionNumber = compositeValueArray[1];
			}
			if (!isRequestForModification) {
				transaction.create(transaction);
				if(transaction!=null) {
					System.out.println("evaluateWorkFlows transaction2:" + SerializationUtils.toJsonString(transaction));
				}
				ThreadAttribute.set(ThreadAttribute.TRANSACTION_REFERENCE_NO,
						(transaction.getKey() != null ? transaction.getKey().getId() : null));
				com.ofss.digx.infra.thread.ThreadAttribute.set(
						com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO,
						transaction.getKey() != null ? transaction.getKey().getId() : null);
			}
			if (isAutoAuth) {
				transaction.getProcessingDetails().setCurrentStep(ProcessingStep.APPROVAL);
				transaction.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
				transaction.getApprovalDetails().setAction(ApprovalAction.CREATE);
				transaction.getApprovalDetails().setStatus(ApprovalStatus.PENDING_APPROVAL);
				if (transaction.getTransactionApprovalHistory() == null) {
					transaction.setTransactionApprovalHistory(new ArrayList<TransactionApprovalHistory>());
				}
				transaction.getTransactionApprovalHistory().add(0, transaction.toTransactionHistory());
				transaction.getProcessingDetails().setStatus(ProcessingStatus.SUCCESS);
				transaction.getApprovalDetails().setAction(ApprovalAction.APPROVE);
				transaction.getApprovalDetails().setStatus(ApprovalStatus.APPROVED);
				transaction.getTransactionApprovalHistory().add(0, transaction.toTransactionHistory());
				transaction.getProcessingDetails().setCurrentStep(ProcessingStep.EXECUTION);
				transaction.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
				validateLimit(transaction, true);
				setReceiptFlag(transaction);
			}

			if (isRequestForModification) {
				TransactionKey key = new TransactionKey();
				key.setId(referenceNumber);
				com.ofss.digx.framework.domain.transaction.Transaction previousState = new com.ofss.digx.framework.domain.transaction.Transaction();
				previousState = previousState.read(key);
				if (previousState != null) {
					List<TransactionApprovalHistory> transactionApprovalHistories = previousState
							.getTransactionApprovalHistory();
					if (transactionApprovalHistories == null)
						transactionApprovalHistories = new ArrayList<>();
					transactionApprovalHistories.add(0, previousState.toTransactionHistory());
					transaction.setTransactionApprovalHistory(transactionApprovalHistories);
					transaction.setCreatedByDetails(previousState.getCreatedByDetails());
					transaction.setCreatedBy(previousState.getCreatedBy());
					transaction.setCreationDate(previousState.getCreationDate());
					transaction.setEntityStatus(previousState.getEntityStatus());
				}
				transaction.setKey(key);
				transaction.getApprovalDetails().setAction(ApprovalAction.MODIFY);
				transactionDTO = transactionAssembler.fromDomainObject(transaction);
				transactionAssembler.setTransaction(previousState);
				transaction = transactionAssembler.toDomainObject(transactionDTO);
				transaction.setVersion(Integer.valueOf(versionNumber));
				removeFromGracePeriodExpiryAlert(transaction.getKey().getId());
			}
			transaction.update(transaction);
			ThreadAttribute.set(ThreadAttribute.INTERNAL_REFERENCE_NUMBER,
					transaction.getKey() != null ? transaction.getKey().getId() : null);
			transactionStatus.setInternalReferenceNumber(
					(String) ThreadAttribute.get(ThreadAttribute.INTERNAL_REFERENCE_NUMBER));
			fetchStatus().setReferenceNumber((String) ThreadAttribute.get(ThreadAttribute.INTERNAL_REFERENCE_NUMBER));
			// TBD -- Added by Sonal for reference number issue while deleting
			// alert.
			// Since readyByName method is called in LocalRepo before delete.
			SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
			sessionContext
					.setInternalReferenceNo((String) ThreadAttribute.get(ThreadAttribute.INTERNAL_REFERENCE_NUMBER));
			ThreadAttribute.set(ThreadAttribute.SESSION_CONTEXT, sessionContext);
			transactionDTO = transactionAssembler.fromDomainObject(transaction);
			if (!isAutoAuth) {
				ITransactionWorkflowSnapshotAdapter transactionWorkflowSnapshotAdapter = null;
				IAdapterFactory transactionWorkflowSnapshotAdapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(
								com.ofss.digx.common.constants.CommonAdapterFactoryConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER_FACTORY);
				transactionWorkflowSnapshotAdapter = (ITransactionWorkflowSnapshotAdapter) transactionWorkflowSnapshotAdapterFactory
						.getAdapter(
								com.ofss.digx.common.constants.CommonAdapterConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER);

				Map<String, Object> digxRequiredThreadAttributeMap = new HashMap<>();
				Map<String, Object> fcRequiredThreadAttributeMap = new HashMap<>();

				AccessPointDTO accessPointDTO = (AccessPointDTO) com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT);

				digxRequiredThreadAttributeMap.put(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT,
						accessPointDTO.getId());
				digxRequiredThreadAttributeMap.put(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO,
						com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO));
				digxRequiredThreadAttributeMap.put(com.ofss.digx.infra.thread.ThreadAttribute.USER_SEGMENT,
						com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.USER_SEGMENT));

				fcRequiredThreadAttributeMap.put(ThreadAttribute.ENTERPRISE_ROLE_ID,
						ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID));
				fcRequiredThreadAttributeMap.put(ThreadAttribute.LIMIT_CHECK_ENABLED_FOR_ROLE,
						ThreadAttribute.get(ThreadAttribute.LIMIT_CHECK_ENABLED_FOR_ROLE));
				fcRequiredThreadAttributeMap.put(ThreadAttribute.SUBJECTNAME,
						ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));
				fcRequiredThreadAttributeMap.put(ThreadAttribute.SERVICE_INPUTS,
						ThreadAttribute.get(ThreadAttribute.SERVICE_INPUTS));
				fcRequiredThreadAttributeMap.put(ThreadAttribute.BUSINESS_UNIT_CODE,
						ThreadAttribute.get(ThreadAttribute.BUSINESS_UNIT_CODE));

				for (TransactionWorkflowSnapshotDTO currTransactionWorkflowSnapshotDTO : transactionWorkflowSnapshotDTOsToPersist) {
					currTransactionWorkflowSnapshotDTO.setTransactionDTO(transactionDTO);
					transactionWorkflowSnapshotAdapter.create(currTransactionWorkflowSnapshotDTO);
				}

				TransactionDataDTO transactionDataDTO = new TransactionDataDTO();
				transactionDataDTO.setDigxRequiredThreadAttributeMap(digxRequiredThreadAttributeMap);
				transactionDataDTO.setFcRequiredThreadAttributeMap(fcRequiredThreadAttributeMap);

				ServiceWorker serviceWorker = new ServiceWorker();

				ServiceWorkerKey key = new ServiceWorkerKey();
				key.setId(transactionDTO.getTransactionId());
				serviceWorker.setKey(key);
				serviceWorker.setTxnDetails(transactionDataDTO);
				serviceWorker.setProcessed(false);
				if (isRequestForModification)
					serviceWorker.update(serviceWorker);
				else
					serviceWorker.create(serviceWorker);
				ThreadAttribute.set(ThreadAttribute.APPROVAL_REQUIRED, true);
			} else {
				registerAlert(transaction, ApprovalConstants.TRANSACTION_AUTO_APPROVED, null);
				analyseTransactionProcess(sessionContext, transaction, null);

			}
		}
		return transactionStatus;
	}

	private void analyseTransactionProcess(SessionContext sessionContext,
										   com.ofss.digx.framework.domain.transaction.Transaction transaction, Object response) throws Exception {
		ITransactionProcessingDetailsBuilder transactionProcessingDetailsBuilder = TransactionProcessingDetailsBuilderFactory
				.getInstance().getBuilder(transaction.getTransactionName());
		List<TransactionApprovalHistory> transactionApprovalHistories = transaction.getTransactionApprovalHistory();
		transactionApprovalHistories.add(0, transaction.toTransactionHistory());
		TransactionStatus transactionStatus = (TransactionStatus) ThreadAttribute
				.get(ThreadAttribute.TRANSACTION_STATUS);
		String eventId = null;
		if (response instanceof BaseResponseObject && ((BaseResponseObject) response).getStatus()
				.getLastKnownError() instanceof ExtSystemTempUnavailableExeception) {
			transaction.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
		} else {
			if (transaction.getProcessingDetails().getStatus() == ProcessingStatus.PROCESSING) {
				transaction.setProcessingDetails(
						transactionProcessingDetailsBuilder.buildProcessingDetails(transactionStatus));
				transaction.setErrors(transactionProcessingDetailsBuilder.buildProcessingErrors(transactionStatus));
			}
			if (transaction.getProcessingDetails().getStatus() == ProcessingStatus.SUCCESS) {
				if (transaction.getDiscriminator().getTaskType() == TaskType.FINANCIAL_TRANSACTION) {
					eventId = ApprovalConstants.TRANSACTION_F_PROCESSED_BY_HOST;
				} else {
					eventId = ApprovalConstants.TRANSACTION_N_PROCESSED_BY_HOST;
				}
				transaction.setProcessingDetails(
						transactionProcessingDetailsBuilder.buildProcessingDetails(transactionStatus));
				transaction.getProcessingDetails()
						.setReferenceNumber((String) com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.EXTERNAL_REFERENCE_NUMBER));
			} else if (transaction.getProcessingDetails().getStatus() == ProcessingStatus.FAIL) {
				eventId = ApprovalConstants.TRANSACTION_REJECTED_BY_HOST;
				reverseLimits((SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT), transaction);
			}
		}
		if (eventId != null) {
			registerAlert(transaction, eventId, null);
		}
	}

	private void reverseLimits(SessionContext sessionContext,
							   com.ofss.digx.framework.domain.transaction.Transaction transactionDomain) throws Exception {
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.common.constants.CommonAdapterFactoryConstants.LIMIT_UTILIZATION_ADAPTER_FACTORY);
		ILimitUtilizationAdapter limitUtilizationAdapter = (ILimitUtilizationAdapter) adapterFactory
				.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.LIMIT_UTILIZATION_ADAPTER);
		limitUtilizationAdapter.reverseUtilizedLimitForTransaction(sessionContext, transactionDomain.getKey().getId());
	}

	@SuppressWarnings("rawtypes")
	private void registerAlert(com.ofss.digx.framework.domain.transaction.Transaction transaction, String eventId,
							   List<TransactionWorkflowSnapshotDTO> transactionWorkflowSnapshotDTOs) throws Exception {
		if (eventId != null) {
			TransactionAssembler transactionAssembler = null;
			TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			AbstractAssembler assembler = transactionAssemblerFactory
					.getAssemblerInstance(transaction.getDiscriminator());
			if (assembler instanceof TransactionAssembler) {
				transactionAssembler = (TransactionAssembler) assembler;
			}
			TransactionDTO transactionDTO = transactionAssembler.fromDomainObject(transaction);
			boolean isAutoAuth = true;

			ActivityLog activityLog = null;
			ITransactionActivityLogMapper transactionActivityLogMapper = TransactionActivityLogMapperFactory
					.getInstance().getTransactionActivityLogMapper(transactionDTO.getTaskDTO().getId());
			if (transactionActivityLogMapper != null) {
				activityLog = transactionActivityLogMapper.getActivityLogForTransaction(transactionDTO);
			} else {
				activityLog = populateActivityLog(transactionDTO);
			}
			Set<NotificationDetail> notificationDetailSet = new HashSet<NotificationDetail>();
			if (eventId.equals(ApprovalConstants.TRANSACTION_APPROVED)
					|| eventId.equals(ApprovalConstants.TRANSACTION_REJECTED)
					|| eventId.equals(ApprovalConstants.TRANSACTION_MODIFICATION_REQUESTED)) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							FORMATTER.formatMessage(
									"Entered into registerAlert() : TransactionId= '%s',  EventId= '%s' in class '%s'",
									transaction.getKey().getId(), eventId, THIS_COMPONENT_NAME));
				}
				NotificationDetail notificationDetailsInit = new NotificationDetail();
				notificationDetailsInit.setRecipientType(SubscriberValue.INITIATOR.toString());
				notificationDetailsInit.setRecipientId(transactionDTO.getCreatedBy());
				notificationDetailSet.add(notificationDetailsInit);
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE, FORMATTER.formatMessage(
							"Entered into registerAlert() : : TransactionId= '%s',  EventId= '%s', getLoggedInUser= '%s', createdBy= '%s' in class '%s'",
							transaction.getKey().getId(), eventId, getLoggedInUserId(), transactionDTO.getCreatedBy(),
							THIS_COMPONENT_NAME));
				}
				if (!(getLoggedInUserId().contentEquals(transactionDTO.getCreatedBy()))) {
					NotificationDetail notificationDetailsAppr = new NotificationDetail();
					notificationDetailsAppr.setRecipientType(SubscriberValue.APPROVER.toString());
					notificationDetailsAppr.setRecipientId(getLoggedInUserId());
					notificationDetailSet.add(notificationDetailsAppr);
				}
				if (transactionDTO != null && transactionDTO.getTransactionHistoryDTOs() != null
						&& transactionDTO.getTransactionHistoryDTOs().length > 0) {
					for (TransactionHistoryDTO transactionHistoryDTO : transactionDTO.getTransactionHistoryDTOs()) {
						if (LOGGER.isLoggable(Level.FINE)) {
							LOGGER.log(Level.FINE, FORMATTER.formatMessage(
									"Entered into registerAlert() : TransactionId= '%s',  EventId= '%s', lastupdatedBy= '%s', createdBy= '%s' in class '%s'",
									transaction.getKey().getId(), eventId, transactionHistoryDTO.getLastUpdatedBy(),
									transactionHistoryDTO.getCreatedBy(), THIS_COMPONENT_NAME));
						}
						if (!(transactionHistoryDTO.getCreatedBy().equals(transactionHistoryDTO.getLastUpdatedBy()))
								&& transactionHistoryDTO.getProcessingDetailDTO()
								.getCurrentStep() == ProcessingStep.APPROVAL) {
							NotificationDetail notificationDetailsPreAppr = new NotificationDetail();
							notificationDetailsPreAppr.setRecipientType(SubscriberValue.PREVIOUS_APPROVER.toString());
							notificationDetailsPreAppr.setRecipientId(transactionHistoryDTO.getLastUpdatedBy());
							notificationDetailSet.add(notificationDetailsPreAppr);
						}
					}
				}
			} else if (eventId.equals(ApprovalConstants.TRANSACTION_F_PROCESSED_BY_HOST)
					|| eventId.equals(ApprovalConstants.TRANSACTION_N_PROCESSED_BY_HOST)
					|| eventId.equals(ApprovalConstants.TRANSACTION_REJECTED_BY_HOST)) {
				NotificationDetail notificationDetailsInit = new NotificationDetail();
				notificationDetailsInit.setRecipientType(SubscriberValue.INITIATOR.toString());
				notificationDetailsInit.setRecipientId(transactionDTO.getCreatedBy());
				notificationDetailSet.add(notificationDetailsInit);
				if (transactionDTO != null && transactionDTO.getTransactionHistoryDTOs() != null
						&& transactionDTO.getTransactionHistoryDTOs().length > 0) {
					for (TransactionHistoryDTO transactionHistoryDTO : transactionDTO.getTransactionHistoryDTOs()) {
						if (transactionHistoryDTO.getCreatedBy() != null
								&& !(transactionHistoryDTO.getCreatedBy()
								.equals(transactionHistoryDTO.getLastUpdatedBy()))
								&& transactionHistoryDTO.getProcessingDetailDTO()
								.getCurrentStep() == ProcessingStep.APPROVAL) {
							isAutoAuth = false;
							NotificationDetail notificationDetailsPreAppr = new NotificationDetail();
							notificationDetailsPreAppr.setRecipientType(SubscriberValue.APPROVER.toString());
							notificationDetailsPreAppr.setRecipientId(transactionHistoryDTO.getLastUpdatedBy());
							notificationDetailSet.add(notificationDetailsPreAppr);
						}
					}
				}
			} else if (eventId.equals(ApprovalConstants.TRANSACTION_PARTIALLY_APPROVED)) {
				NotificationDetail notificationDetailsInit = new NotificationDetail();
				notificationDetailsInit.setRecipientType(SubscriberValue.INITIATOR.toString());
				notificationDetailsInit.setRecipientId(transactionDTO.getCreatedBy());
				notificationDetailSet.add(notificationDetailsInit);
				NotificationDetail notificationDetailsAppr = new NotificationDetail();
				notificationDetailsAppr.setRecipientType(SubscriberValue.APPROVER.toString());
				notificationDetailsAppr.setRecipientId(getLoggedInUserId());
				notificationDetailSet.add(notificationDetailsAppr);
				if (transactionDTO != null && transactionDTO.getTransactionHistoryDTOs() != null
						&& transactionDTO.getTransactionHistoryDTOs().length > 0) {
					for (TransactionHistoryDTO transactionHistoryDTO : transactionDTO.getTransactionHistoryDTOs()) {
						if (!(transactionHistoryDTO.getCreatedBy().equals(transactionHistoryDTO.getLastUpdatedBy()))) {
							NotificationDetail notificationDetailsPreAppr = new NotificationDetail();
							notificationDetailsPreAppr.setRecipientType(SubscriberValue.PREVIOUS_APPROVER.toString());
							notificationDetailsPreAppr.setRecipientId(transactionHistoryDTO.getLastUpdatedBy());
							notificationDetailSet.add(notificationDetailsPreAppr);
						}
					}
				}
				Integer currentStep = transactionDTO.getApprovalDetails().getStepNo();
				for (TransactionWorkflowSnapshotDTO workflowSnapshotDTO : transactionWorkflowSnapshotDTOs) {
					if (workflowSnapshotDTO.getWorkflowStepDTO().getSequenceNo() == currentStep) {
						for (UserGroupUserDTO userGroupUserDTO : workflowSnapshotDTO.getWorkflowStepDTO().getUserGroup()
								.getUsers()) {
							if (userGroupUserDTO.getUserId() != null
									&& !userGroupUserDTO.getUserId().equals(transactionDTO.getCreatedBy())) {
								NotificationDetail notificationDetailsNextAppr = new NotificationDetail();
								notificationDetailsNextAppr.setRecipientType(SubscriberValue.NEXT_APPROVER.toString());
								notificationDetailsNextAppr.setRecipientId(userGroupUserDTO.getUserId());
								notificationDetailSet.add(notificationDetailsNextAppr);
							}
						}
					}
				}
			} else if (eventId.equals(ApprovalConstants.TRANSACTION_AUTO_APPROVED)) {
				NotificationDetail notificationDetailsInit = new NotificationDetail();
				notificationDetailsInit.setRecipientType(SubscriberValue.INITIATOR.toString());
				notificationDetailsInit.setRecipientId((String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME));
				notificationDetailSet.add(notificationDetailsInit);
			} else if (eventId.equals(ApprovalConstants.TRANSACTION_INITIATED)) {
				NotificationDetail notificationDetailsInit = new NotificationDetail();
				notificationDetailsInit.setRecipientType(SubscriberValue.INITIATOR.toString());
				notificationDetailsInit.setRecipientId(getLoggedInUserId());
				notificationDetailSet.add(notificationDetailsInit);
				for (TransactionWorkflowSnapshotDTO currTransactionWorkflowSnapshotDTO : transactionWorkflowSnapshotDTOs) {
					if (currTransactionWorkflowSnapshotDTO.getWorkflowStepDTO() != null
							&& currTransactionWorkflowSnapshotDTO.getWorkflowStepDTO().getSequenceNo() != null
							&& currTransactionWorkflowSnapshotDTO.getWorkflowStepDTO().getSequenceNo().equals(1)) {
						UserGroupDTO userGroupDTO = currTransactionWorkflowSnapshotDTO.getWorkflowStepDTO()
								.getUserGroup();
						for (UserGroupUserDTO userGroupUserDTO : userGroupDTO.getUsers()) {
							if (userGroupUserDTO.getUserId() != null
									&& !userGroupUserDTO.getUserId().equals(transactionDTO.getCreatedBy())) {
								NotificationDetail notificationDetailsAppr = new NotificationDetail();
								notificationDetailsAppr.setRecipientType(SubscriberValue.APPROVER.toString());
								notificationDetailsAppr.setRecipientId(userGroupUserDTO.getUserId());
								notificationDetailSet.add(notificationDetailsAppr);
							}
						}
					}
				}
			}
			NotificationDetail[] notificationDetails = notificationDetailSet
					.toArray(new NotificationDetail[notificationDetailSet.size()]);
			activityLog.setNotificationDetails(notificationDetails);
			if (transactionActivityLogMapper != null) {
				Calendar calendar = Calendar.getInstance();
				calendar.set(2014, 2, 9, 0, 0, 0);
				java.util.Date date = calendar.getTime();
				super.registerActivityAndGenerateEvent(
						(SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT),
						transactionDTO.getServiceId(), eventId, new Date(date), activityLog);
			} else {
				ApprovalActivityLog approvalActivityLog = (ApprovalActivityLog) activityLog;
				if (eventId.equals(ApprovalConstants.TRANSACTION_AUTO_APPROVED)
						|| eventId.equals(ApprovalConstants.TRANSACTION_INITIATED)
						|| eventId.equals(ApprovalConstants.TRANSACTION_MODIFICATION_REQUESTED)
						|| ((eventId.equals(ApprovalConstants.TRANSACTION_F_PROCESSED_BY_HOST)
						|| eventId.equals(ApprovalConstants.TRANSACTION_N_PROCESSED_BY_HOST)
						|| eventId.equals(ApprovalConstants.TRANSACTION_REJECTED_BY_HOST)) && isAutoAuth)) {
					String activityId = null;
					if (transaction instanceof AmountAccountTransaction) {
						activityId = FINANCIAL_TRANSACTION_INITIATED;
						approvalActivityLog.setCurrencyAmount(((AmountAccountTransaction) transaction).getAmount());
						approvalActivityLog.setSourceAccountNo(((AccountTransaction) transaction).getAccountId());
						approvalActivityLog.setValueDate(((AmountAccountTransaction) transaction).getValueDate());
					} else if (transaction instanceof AccountTransaction) {
						activityId = NON_FINANCIAL_TRANSACTION_INITIATED;
						approvalActivityLog.setSourceAccountNo(((AccountTransaction) transaction).getAccountId());
					} else if (transaction instanceof AmountTransaction) {
						activityId = AMOUNT_FINANCIAL_TRANSACTION_INITIATED;
						approvalActivityLog.setCurrencyAmount(((AmountTransaction) transaction).getAmount());
					} else {
						activityId = MAINTENANCE_TRANSACTION_INITIATED;
					}
					Calendar calendar = Calendar.getInstance();
					calendar.set(2014, 2, 9, 0, 0, 0);
					java.util.Date date = calendar.getTime();
					super.registerActivityAndGenerateEvent(
							(SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT), activityId, eventId,
							new Date(date), approvalActivityLog);
					if (LOGGER.isLoggable(Level.SEVERE)) {
						LOGGER.log(Level.SEVERE,
								FORMATTER.formatMessage("Transaction registerAlert : Activity Id:'%s' in class %s",
										activityId, THIS_COMPONENT_NAME));
					}
				} else {
					super.registerActivityAndGenerateEvent(
							(SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT), eventId,
							activityLog);
				}
			}
		}
	}

	private ApprovalActivityLog populateActivityLog(TransactionDTO transactionDTO) {
		ApprovalActivityLog activityLog = new ApprovalActivityLog();
		if (transactionDTO != null) {
			if (transactionDTO.getTaskDTO() != null && transactionDTO.getTaskDTO().getName() != null
					&& !transactionDTO.getTaskDTO().getName().equals(Constants.EMPTY_STRING)) {
				activityLog.setTxnName(transactionDTO.getTaskDTO().getName());
			}
			activityLog.setTxnReferenceNo(transactionDTO.getTransactionId());
			if (transactionDTO.getProcessingDetails() != null) {
				activityLog.setHostReferenceNo(transactionDTO.getProcessingDetails().getReferenceNumber());
			}
			if (transactionDTO.getCreatedByDetails() != null) {
				String initiatorName = (transactionDTO.getCreatedByDetails().getFirstName() != null
						? transactionDTO.getCreatedByDetails().getFirstName()
						: "")
						+ " "
						+ (transactionDTO.getCreatedByDetails().getMiddleName() != null
						? transactionDTO.getCreatedByDetails().getMiddleName()
						: "")
						+ " "
						+ (transactionDTO.getCreatedByDetails().getLastName() != null
						? transactionDTO.getCreatedByDetails().getLastName()
						: "");
				activityLog.setInitiator(initiatorName);
			}
			if (transactionDTO.getUpdatedByDetails() != null) {
				String approverName = (transactionDTO.getUpdatedByDetails().getFirstName() != null
						? transactionDTO.getUpdatedByDetails().getFirstName()
						: "")
						+ " "
						+ (transactionDTO.getUpdatedByDetails().getMiddleName() != null
						? transactionDTO.getUpdatedByDetails().getMiddleName()
						: "")
						+ " "
						+ (transactionDTO.getUpdatedByDetails().getLastName() != null
						? transactionDTO.getUpdatedByDetails().getLastName()
						: "");
				activityLog.setApprover(approverName);
			}
		}
		return activityLog;
	}

	private String getLoggedInUserId() {
		return (String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME);
	}

	private void removeFromGracePeriodExpiryAlert(String transactionId) {

		try {
			ApprovalGracePeriodExpiryAlertKey keyRequestObject = new ApprovalGracePeriodExpiryAlertKey();
			keyRequestObject.setId(transactionId);

			ApprovalGracePeriodExpiryAlert requestObject = new ApprovalGracePeriodExpiryAlert();
			requestObject.setKey(keyRequestObject);

			ApprovalGracePeriodExpiryAlertRepository repository = ApprovalGracePeriodExpiryAlertRepository
					.getInstance();
			repository.delete(requestObject);

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RunTimeException from removeFromGracePeriodExpiryAlert() for ApprovalGracePeriodExpiryAlert '%s' in class %s",
					transactionId, THIS_COMPONENT_NAME), e);
		}
	}

	private void setReceiptFlag(com.ofss.digx.framework.domain.transaction.Transaction transaction) throws Exception {
		String taskCode = transaction.getTransactionName();
		String ereceiptTaskCode = TaskEvaluatorConfigurator.getInstance().getEvaluatorFactory(taskCode)
				.getEvaluator(TaskAspect.ERECEIPT)
				.evaluateTaskCode(taskCode, getParameters(transaction.getTransactionSnapshot()));
		TaskDTO task = AdapterFactory.getInstance().getAdapter(com.ofss.digx.core.adapter.task.ITaskAdapter.class)
				.read(ereceiptTaskCode);
		if (task.isAspectEnabled(TaskAspect.ERECEIPT)) {
			com.ofss.digx.infra.thread.ThreadAttribute
					.set(com.ofss.digx.infra.thread.ThreadAttribute.ERECEIPT_AVAILABLE, Boolean.TRUE);
		}
	}

	private List<Object> getParameters(Serializable object) {
		List<Object> inputParams = new ArrayList<>();
		SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		inputParams.add(sessionContext);
		inputParams.add(object);
		return inputParams;
	}

	/**
	 * Validates transaction amount for the currently logged in user against
	 * allocated limit for the transaction. It calls the
	 * {@link com.ofss.digx.app.limits.service.limit.Limit} service via {
	 * com.ofss.digx.app.sms.adapter.limit.ILimitAdapter} adapter
	 *
	 * @param transaction An object of
	 *                    {@link com.ofss.digx.framework.domain.transaction.Transaction}
	 *                    that contains the transaction details.
	 * @throws Exception if any error occurs while validating limit for party.
	 */
	private void validateLimit(com.ofss.digx.framework.domain.transaction.Transaction transaction,
							   boolean isTransactionApproved) throws Exception {
		if (transaction instanceof AmountAccountTransaction || transaction instanceof AmountTransaction) {
			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.common.constants.CommonAdapterFactoryConstants.LIMIT_UTILIZATION_ADAPTER_FACTORY);
			ILimitUtilizationAdapter limitUtilizationAdapter = (ILimitUtilizationAdapter) adapterFactory
					.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.LIMIT_UTILIZATION_ADAPTER);
			List<Object> serviceParams = new ArrayList<>();
			serviceParams.add(ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT));
			serviceParams.add(transaction.getTransactionSnapshot());
			limitUtilizationAdapter.validate(EntityType.USER, new LimitType[] { LimitType.PERIODIC }, false,
					transaction.getTransactionName(), serviceParams, null);
			if (isTransactionApproved) {
				try {
					limitUtilizationAdapter.validate(EntityType.PARTY, new LimitType[] { LimitType.PERIODIC }, false,
							transaction.getTransactionName(), serviceParams, null);
				} catch (FinancialLimitExhaustedException e) {
					if (LOGGER.isLoggable(Level.INFO)) {
						LOGGER.log(Level.INFO,
								"Party level limit exhauseted. Reverse limit utilization for current approver.", e);
					}
					throw e;
				}
			}
		}
	}

	/**
	 * Set AccountPartyId for the transaction dto.
	 *
	 * @param transaction
	 * @throws Exception
	 */
	private void setAccountPartyId(AccountTransaction transaction) throws Exception {
		IAdapterFactory accountAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.common.constants.CommonAdapterFactoryConstants.ACCOUNTS_ADAPTER_FACTORY);
		IAccountAdapter accountAdapter = (IAccountAdapter) accountAdapterFactory
				.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.ACCOUNTS_ADAPTER);
		List<PartyDetailsDTO> partyDetailsDTOs = accountAdapter.fetchPartiesForAccount(transaction.getAccountId(),
				transaction.getAccountType());
		String accountPartyId;

		SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);

		if (partyDetailsDTOs.size() == 1) {
			accountPartyId = partyDetailsDTOs.get(0).getPartyId().getValue();
		} else {
			Set<String> allParties = new HashSet<>();
			allParties.addAll(sessionContext.getContextLinkedParties());
			allParties.add(sessionContext.getTransactingPartyCode());

			Set<String> partiesForAccount = partyDetailsDTOs.stream().map(p -> p.getPartyId().getValue())
					.collect(Collectors.toSet());

			Set<String> intersection = allParties.stream().filter(partiesForAccount::contains)
					.collect(Collectors.toSet());
			if ((intersection.isEmpty() || intersection.size() > 1)
					&& intersection.contains(sessionContext.getTransactingPartyCode())) {
				accountPartyId = sessionContext.getTransactingPartyCode();
			} else {
				accountPartyId = intersection.iterator().next();
			}
		}

		transaction.setAccountPartyId(accountPartyId);

	}

	@Override
	@NoEntitlement
	public TransactionStatus handleExpiredTransactions(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions");
			List<com.ofss.digx.framework.domain.transaction.Transaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredSITransactions();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTransactions : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Update Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());

//				Date hostDate=BranchDateHelper.getCurrentDate();
//				java.util.Date date=hostDate.fetchJavaDate();
//
//			java.util.Date serverDate= new java.util.Date();
//
//					if(date.after(serverDate)) {
//						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("the host date in DB is future hence retunr server date"+ serverDate);
//						date=serverDate;
//					}else if(date.before(serverDate)) {
//						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("the host date in DB is past hence retunr server date"+ serverDate);
//					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("the host date in DB is past hence retunr server date"+ date);
//						date=serverDate;
//					}
//					else {
//						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("the host date in DB is equal hence retunr host DB date"+ date);
///=					}

				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						/*
						 * MACDataHelper.updateMacDataForPayLaterToPayNowFT(sessionContext,
						 * txnObj.getKey().getId()); adapter.updateTxnTaskCode(txnObj.getKey().getId(),
						 * txnObj.getTransactionName());////taskCode.replace("_SI", "") com.ofss.digx.cz.bea.app.logger.BeaSystemOut.
						 * println("In handleExpiredTransactions (value date = current date) : Transaction Id : "
						 * + txnObj.getKey().getId() + " TransactionName=" + txnObj.getTransactionName()
						 * + " MAC & transaction updated as pay now ");
						 */

						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTransactions (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}

				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}

					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	@Override
	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.app.approval.service.transaction.Transaction.listTransactions",
			"com.ofss.digx.app.approval.service.transaction.Transaction.transactionsCount" })
	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.APPROVE, requiredResources = { "" })
	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Approvals)
	@Task(id = "CZ_PA_APT", parent = "APT", name = "Perform Action", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.APPROVALS, aspects = {
			TaskAspect.GRACE_PERIOD, TaskAspect.AUDIT }, type = TaskType.FINANCIAL_TRANSACTION)
	public TransactionActionResponse performAction(SessionContext sessionContext,
												   MultiApprovalTransactionListDTO multiApprovalTransactionListDTO) throws Exception {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Entered into performAction() : TransactionActionDTO=%s in class %s ",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME));
		}
		com.ofss.fc.infra.thread.ThreadAttribute.set("MultipleApproval", "Y"); // Set multi approval Y to handle OTP

		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.performAction",
				sessionContext, multiApprovalTransactionListDTO);


		super.canonicalizeInput(multiApprovalTransactionListDTO);

		Interaction.begin(sessionContext);
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		TransactionStatus transactionStatus = fetchTransactionStatus();
		transactionActionResponse.setStatus(fetchStatus());

		String subjectName = (String) com.ofss.fc.infra.thread.ThreadAttribute
				.get(com.ofss.fc.infra.thread.ThreadAttribute.SUBJECTNAME);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Subject name is" + subjectName);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In multiple approval txn check if TFA required "+isTFARequired());
		Subject subject = SubjectUtil.getCurrentSubject();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("check the subject value" + subject);
		AccessPointDTO accessPoint = (AccessPointDTO) com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT);
		String enterpriseRoleId = (String) ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("role id is" + enterpriseRoleId);

		// call sendCRM data to insert CRM event for Bulk appoval - confirm
		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE_RESPONSE) == null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.performAction() :: calling CRM :: X_CHALLENGE_RESPONSE is null");
			int noOfTrnx = multiApprovalTransactionListDTO.getMultiApprovalTransactionList().size();
			sessionContext.setExternalBatchNumber(noOfTrnx); // setting no of trnx value here only for
			callCRMasserter(sessionContext, fetchTransactionStatus());
		} else {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.performAction() :: calling CRM :: X_CHALLENGE_RESPONSE is not null");
		}
		try {

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Inside TXN");

			if (isTFARequired()) {
				throw new TFARequiredException(TFAErrorConstant.AUTHENTICATION_REQUIRED);
			}

			/*
			 * Code to initiate TFA
			 */

			IRepositoryAdapter adapter = RepositoryAdapterFactory.getInstance()
					.getRepositoryAdapter("USEREXTENSIONDATA_REPOSITORY_ADAPTER");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Inside TXN after adapter");

			if ((Stack<String>) ThreadAttribute.get("HOST_AUDIT_OPERATIONNAME_STACK") != null) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("ThreadAttribute.get(\"HOST_AUDIT_OPERATIONNAME_STACK\") is not null");
				ThreadAttribute.set("HOST_AUDIT_OPERATIONNAME_STACK",
						com.ofss.digx.infra.thread.ThreadAttribute.get("HOST_AUDIT_OPERATIONNAME_STACK"));
			}

			//LGM BCOCDC-980 20250402 check account access for multiple approve start
			TransactionKey txnKey = null;
			TransactionDTO transactionDTO = null;
			TransactionAssembler transactionAssembler = null;
			TransactionApprovalAccessCheckConstraint transactionApprovalAccessCheckConstraint = null;
			Session session = DataAccessManager.getManager().openNewSession("NONXA");
			final TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			List<String> nonAccountAccessTxns = new ArrayList<String>();
			List<String> signedByList = new ArrayList<String>();
			String userId = sessionContext.getUserId(); //ADD By YZL
			for (MultiApprovalTransactionDTO multiApprovalTransaction : multiApprovalTransactionListDTO.getMultiApprovalTransactionList()) {

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("inside multiple approve account access checking loop");
				com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("transaction Id: " + multiApprovalTransaction.getTransactionID());
				txnKey = new TransactionKey();
				txnKey.setId(multiApprovalTransaction.getTransactionID());

	            transactionDomain = (com.ofss.digx.framework.domain.transaction.Transaction)session.get((Class)com.ofss.digx.framework.domain.transaction.Transaction.class, (Serializable)txnKey);

	            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("load transaction from DIGX_AP_TRANSACTION: " + transactionDomain);
	            transactionAssembler = transactionAssemblerFactory.getAssemblerInstance(transactionDomain.getDiscriminator());
	            transactionDTO = transactionAssembler.fromDomainObject(transactionDomain);

	            if (transactionDomain.getApprovalDetails()!=null && transactionDomain.getApprovalDetails().getSignedBy() !=null) {
	            	int counter=0;
	            	String approverDetails[] =transactionDomain.getApprovalDetails().getSignedBy().split("~");

	            	com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("approverDetails Array list is "+Arrays.toString(approverDetails));
	            	boolean firstValidSkipped = false;
	            	for(int iCount=0 ;iCount<approverDetails.length;iCount++) {

	            		if(!firstValidSkipped && approverDetails[iCount]!=null && !approverDetails[iCount].isEmpty()) {
	            			firstValidSkipped = true;
	            			continue;
	            		}

	            		if (approverDetails[iCount]!=null && approverDetails[iCount].equals(subjectName)) {
	            			counter++;
	            		}
	            	}
	            	if (counter > 0) {
	            		//ignore the transaction
	            		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("The curren user "+subjectName +" already signed this transaction "+multiApprovalTransaction.getTransactionID());
	            		signedByList.add(multiApprovalTransaction.getTransactionID());
	            	}

	            }
	            String accountIdInt = ""; //ADD By YZL
				if (transactionDTO instanceof AccountTransactionDTO) {
					final String accountId = ((AccountTransactionDTO)transactionDTO).getAccountId().getValue();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("performAction accountId before " + accountId);
					final Account accountNumber = new Account();
					if (accountId != null) {
						accountNumber.setValue(accountId.split("~")[0]);
					}
					else {
						accountNumber.setValue(accountId);
					}
					((AccountTransactionDTO)transactionDTO).setAccountId(accountNumber);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("performAction accountId after " + ((AccountTransactionDTO)transactionDTO).getAccountId());
					accountIdInt = ((AccountTransactionDTO)transactionDTO).getAccountId().toString(); //ADD By YZL
				}
				transactionApprovalAccessCheckConstraint = new TransactionApprovalAccessCheckConstraint(transactionDTO);
				try {

					//YZL BCOCDC-980 20250402 check account access for multiple approve start
					IAdapterFactory acountAdtaptor = AdapterFactoryConfigurator.getInstance().
							getAdapterFactory("PARTY_ACCOUNTACESS_ADAPTER_FACTORY");
					ICZPartyAccountAccess adapterAcct = (ICZPartyAccountAccess) acountAdtaptor.
							getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.PARTY_ACCOUNTACESS_ADAPTER);

					int checkUser = 0;
					if(null!=accountIdInt && !"".equals(accountIdInt)) {
						checkUser = adapterAcct.checkAccountLinked(accountIdInt, userId);
					}

					if(0 == checkUser) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("account access checking failed for transaction Id: " + multiApprovalTransaction.getTransactionID());
						nonAccountAccessTxns.add(multiApprovalTransaction.getTransactionID());
					} else {
					//YZL BCOCDC-980 20250402 check account access for multiple approve end
						transactionApprovalAccessCheckConstraint.isSatisfiedBy();
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("account access checking pass for transaction Id: " + multiApprovalTransaction.getTransactionID());
					}

				} catch (SystemConstraintViolationException e) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("account access checking failed for transaction Id: " + multiApprovalTransaction.getTransactionID());
					List<ValidationError> errors = e.getValidationErrors();
					for(ValidationError error : errors) {
						if ("DIGX_AP_0036".equalsIgnoreCase(error.getErrorCode())) {
							nonAccountAccessTxns.add(multiApprovalTransaction.getTransactionID());
							break;
						}
					}
				}
			}
			//thow exception if there is account access checking failed.
			if (!nonAccountAccessTxns.isEmpty()) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("without account access: " + nonAccountAccessTxns.size());
				String txnIds = String.join(", ", nonAccountAccessTxns);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("without account access, txnIds: " + txnIds);
				List<ValidationError> errorsList = new ArrayList<ValidationError>();
				errorsList.add(new ValidationError("AccountTransactionDTO", "accountId","", "DIGX_CZ_AP_0036", txnIds));
				throw new SystemConstraintViolationException("10011", (List) errorsList);
			}
			//LGM BCOCDC-980 20250402 check account access for multiple approve end

			int index = 0;
			for (MultiApprovalTransactionDTO multiApprovalTransaction : multiApprovalTransactionListDTO
					.getMultiApprovalTransactionList()) {
				try {

					com.ofss.digx.framework.domain.transaction.Transaction transactionInfo = new com.ofss.digx.framework.domain.transaction.Transaction();
					TransactionKey transactionKey = new TransactionKey();
					transactionKey.setId(multiApprovalTransaction.getTransactionID());
					transactionInfo = transactionInfo.read(transactionKey);

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Inside For Loop");
					Map<String, Object> digxThreadMap = new HashMap<>();
					Map<String, Object> fcThreadMap = new HashMap<>();
					String signerToken = null, rsaIndicator = null, msgContent = "", signRefNumber = null;

					if (!InputValidationUtils
							.isNullOrBlank((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"))) {
						signerToken = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGNER_TOKEN In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"));
					}

					if (!InputValidationUtils.isNullOrBlank(
							(String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"))) {
						msgContent = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGN_MSG_CONTENT In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"));

					}

					if (!InputValidationUtils.isNullOrBlank(
							(String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"))) {
						rsaIndicator = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGN_RSA_INDICATOR In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"));

					}

					digxThreadMap = com.ofss.digx.infra.thread.ThreadAttribute.exportAll();
					fcThreadMap = com.ofss.fc.infra.thread.ThreadAttribute.exportAll();
					AccessControlContext context = AccessController.getContext();

					Stack<String> hostAudit = (Stack<String>) com.ofss.digx.infra.thread.ThreadAttribute
							.get("HOST_AUDIT_OPERATIONNAME_STACK");
					
					String signTxnMethod = (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_TXN_METHOD");
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("fetch signTxnMethod for CZApprovalWorker: "+signTxnMethod);
					signRefNumber = (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_REF_NUMBER");
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("fetch signRefNumber for CZApprovalWorker: "+signRefNumber);
					String crmIpAddress = (String) com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS");
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("fetch crmIpAddress for CZApprovalWorker: "+crmIpAddress);
					CZApprovalWorker workerObject = new CZApprovalWorker(sessionContext,
							multiApprovalTransaction.getTransactionActionDTO(), multiApprovalTransaction, digxThreadMap,
							fcThreadMap, subjectName, subject, accessPoint, enterpriseRoleId, context, signerToken,
							msgContent, rsaIndicator, signRefNumber, hostAudit, index, signTxnMethod, crmIpAddress);

					CZApprovalExecutorService executeBusinessLogic = CZApprovalExecutorService.getInstance();

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Checking if need to put thread to sleep for "+transactionInfo.getTransactionName());
					if (transactionInfo != null && transactionInfo.getTransactionName() != null
							&& !signedByList.contains(multiApprovalTransaction.getTransactionID())) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction has been submitted with async processing "+multiApprovalTransaction.getTransactionID() );
						executeBusinessLogic.submitProcess(workerObject);
					}

					index++;
				} catch (Exception e) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				}

			}
			transactionActionResponse.setStatus(buildStatus(transactionStatus));

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"Exception encountered while invoking the service %s while performAction of transaction",
							THIS_COMPONENT_NAME),
					e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from performAction() for TransactionActionDTO '%s' in class %s",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionActionResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from performAction() : TransactionActionResponse=%s", transactionActionResponse));
		}
		return transactionActionResponse;
	}



	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.app.approval.service.transaction.Transaction.listTransactions",
			"com.ofss.digx.app.approval.service.transaction.Transaction.transactionsCount" })
	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.APPROVE, requiredResources = { "" })
	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Approvals)
	@Task(id = "CZ_PA_APT", parent = "APT", name = "Perform Action", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.APPROVALS, aspects = {
			TaskAspect.GRACE_PERIOD, TaskAspect.AUDIT }, type = TaskType.FINANCIAL_TRANSACTION)
	public TransactionActionResponse performActionBulkWithoutOTP(SessionContext sessionContext,
																 MultiApprovalTransactionListDTO multiApprovalTransactionListDTO) throws Exception {

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Bulk Entered into performAction() : TransactionActionDTO=%s in class %s ",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME));
		}
		com.ofss.fc.infra.thread.ThreadAttribute.set("MultipleApproval", "Y"); // Set multi approval Y to handle OTP

//		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.performAction",
		//			sessionContext, multiApprovalTransactionListDTO);


//		super.canonicalizeInput(multiApprovalTransactionListDTO);

		Interaction.begin(sessionContext);
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		TransactionStatus transactionStatus = fetchTransactionStatus();
		transactionActionResponse.setStatus(fetchStatus());

		String subjectName = (String) com.ofss.fc.infra.thread.ThreadAttribute
				.get(com.ofss.fc.infra.thread.ThreadAttribute.SUBJECTNAME);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk Subject name is" + subjectName);


		com.ofss.digx.infra.thread.ThreadAttribute
				.set(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE, null);
		com.ofss.digx.infra.thread.ThreadAttribute
				.set(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED, null);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk In multiple approval txn check if TFA required for bulk transfer "+isTFARequired());
		Subject subject = SubjectUtil.getCurrentSubject();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk check the subject value" + subject);
		AccessPointDTO accessPoint = (AccessPointDTO) com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT);
		String enterpriseRoleId = (String) ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk role id is" + enterpriseRoleId);

		// call sendCRM data to insert CRM event for Bulk appoval - confirm
		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE_RESPONSE) == null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk Transaction.performAction() :: calling CRM :: X_CHALLENGE_RESPONSE is null");
			int noOfTrnx = multiApprovalTransactionListDTO.getMultiApprovalTransactionList().size();
			sessionContext.setExternalBatchNumber(noOfTrnx); // setting no of trnx value here only for
			callCRMasserter(sessionContext, fetchTransactionStatus());
		} else {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk Transaction.performAction() :: calling CRM :: X_CHALLENGE_RESPONSE is not null");
		}
		try {

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Bulk Inside TXN");

			//	if (isTFARequired()) {
			//		throw new TFARequiredException(TFAErrorConstant.AUTHENTICATION_REQUIRED);
			//	}

			/*
			 * Code to initiate TFA
			 */

			IRepositoryAdapter adapter = RepositoryAdapterFactory.getInstance()
					.getRepositoryAdapter("USEREXTENSIONDATA_REPOSITORY_ADAPTER");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Bulk Inside TXN after adapter");

			if ((Stack<String>) ThreadAttribute.get("HOST_AUDIT_OPERATIONNAME_STACK") != null) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("ThreadAttribute.get(\"HOST_AUDIT_OPERATIONNAME_STACK\") is not null");
				ThreadAttribute.set("HOST_AUDIT_OPERATIONNAME_STACK",
						com.ofss.digx.infra.thread.ThreadAttribute.get("HOST_AUDIT_OPERATIONNAME_STACK"));
			}

			int index = 0;
			for (MultiApprovalTransactionDTO multiApprovalTransaction : multiApprovalTransactionListDTO
					.getMultiApprovalTransactionList()) {
				try {

					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Bulk Inside For Loop");
					Map<String, Object> digxThreadMap = new HashMap<>();
					Map<String, Object> fcThreadMap = new HashMap<>();
					String signerToken = null, rsaIndicator = null, msgContent = null, signRefNumber = null;

					if (!InputValidationUtils
							.isNullOrBlank((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"))) {
						signerToken = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGNER_TOKEN In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGNER_TOKEN"));
					}

					if (!InputValidationUtils.isNullOrBlank(
							(String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"))) {
						msgContent = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGN_MSG_CONTENT In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_MSG_CONTENT"));

					}

					if (!InputValidationUtils.isNullOrBlank(
							(String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"))) {
						rsaIndicator = ((String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"));
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("SIGN_RSA_INDICATOR In Loop "
								+ (String) com.ofss.digx.infra.thread.ThreadAttribute.get("SIGN_RSA_INDICATOR"));

					}

					digxThreadMap = com.ofss.digx.infra.thread.ThreadAttribute.exportAll();
					fcThreadMap = com.ofss.fc.infra.thread.ThreadAttribute.exportAll();
					AccessControlContext context = AccessController.getContext();

					Stack<String> hostAudit = (Stack<String>) com.ofss.digx.infra.thread.ThreadAttribute
							.get("HOST_AUDIT_OPERATIONNAME_STACK");

					CZApprovalWorker2 workerObject = new CZApprovalWorker2(sessionContext,
							multiApprovalTransaction.getTransactionActionDTO(), multiApprovalTransaction, digxThreadMap,
							fcThreadMap, subjectName, subject, accessPoint, enterpriseRoleId, context, signerToken,
							msgContent, rsaIndicator, signRefNumber, hostAudit, index);

					CZApprovalExecutorService executeBusinessLogic = CZApprovalExecutorService.getInstance();
					executeBusinessLogic.submitProcess(workerObject);
					index++;
				} catch (Exception e) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				}

			}
			transactionActionResponse.setStatus(buildStatus(transactionStatus));

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"Exception encountered while invoking the service %s while performAction of transaction",
							THIS_COMPONENT_NAME),
					e);
			fillTransactionStatus(transactionStatus, e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from performAction() for TransactionActionDTO '%s' in class %s",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionActionResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from performAction() : TransactionActionResponse=%s", transactionActionResponse));
		}
		return transactionActionResponse;
	}



	private void callCRMasserter(SessionContext sessionContext, TransactionStatus transactionStatus) {
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.callCRMasserter() :: starts");
			String userName = sessionContext.getUserId();
			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(CommonAdapterFactoryConstants.CRM_ADAPTER_FACTORY);
			ICRMAsserterCallAdapter crmAdapter = (ICRMAsserterCallAdapter) adapterFactory
					.getAdapter(CommonAdapterConstants.CRM_ADAPTER);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.callCRMasserter() :: before callingCRMAsserter");
			crmAdapter.callCRMAsserter(sessionContext, null, null, transactionStatus, false, userName);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.callCRMasserter() :: after callingCRMAsserter");

		} catch (java.lang.Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.callCRMasserter() :: inside catch() ");
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exception in callCRMasserter() in class %s", THIS_COMPONENT_NAME), e);
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void processApprovalOfTransaction(SessionContext sessionContext, TransactionActionDTO transactionActionDTO,
											 Map<String, Object> digxRequiredThreadAttributeMap, Map<String, Object> fcRequiredThreadAttributeMap,
											 String subjectName, Subject subject, AccessPointDTO accessPoint, String enterpriseRoleId)
			throws com.ofss.digx.infra.exceptions.Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Entered into processApprovalOfTransaction() : requestDTO= '%s' in class '%s'",
							transactionActionDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(
				"com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.processApprovalOfTransaction",
				sessionContext, transactionActionDTO, digxRequiredThreadAttributeMap, fcRequiredThreadAttributeMap);
		super.canonicalizeInput(transactionActionDTO);

		Interaction.begin(sessionContext);

		Subject a_oSubject = SubjectUtil.getCurrentSubject();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### after setting subject userName ===" + a_oSubject);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### after setting subject userName ===" + SubjectUtil.getUserName(a_oSubject));

		TransactionStatus transactionStatus = fetchTransactionStatus();
		List<com.ofss.digx.domain.approval.entity.transaction.checkerdetails.ApprovalCheckerDetails> approvalCheckerDetailsList = new ArrayList<>();
		com.ofss.digx.app.approval.service.transaction.Transaction transactionService = new com.ofss.digx.app.approval.service.transaction.Transaction();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("#### Final perform===" + transactionActionDTO.getTransactionDTO().getTransactionId());
			performActionWithoutSession(sessionContext, transactionActionDTO);

		} catch (Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
						"Exception from setUpCheckerForTheTransaction() for listTransactionWorkflowSnapshotDTO '%s' in class %s",
						transactionActionDTO, THIS_COMPONENT_NAME), e);
				fillTransactionStatus(transactionStatus, e);
			}
		} catch (RuntimeException rte) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(rte);
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RunTimeException from setUpCheckerForTheTransaction for listTransactionWorkflowSnapshotDTO '%s' in class '%s'",
					transactionActionDTO, THIS_COMPONENT_NAME), rte);
		} finally {

			Interaction.close();

		}
		super.checkResponsePolicy(sessionContext, approvalCheckerDetailsList);
		if (LOGGER.isLoggable(Level.SEVERE)) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"Exiting from setUpCheckerForTheTransaction() : setUpCheckerForTheTransaction= '%s'", ""));
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from setUpCheckerForTheTransaction() : setUpCheckerForTheTransaction= '%s'", ""));
		}
	}


	@SuppressWarnings("rawtypes")
	public void processApprovalOfTransactionBulk(SessionContext sessionContext, TransactionActionDTO transactionActionDTO,
			Map<String, Object> digxRequiredThreadAttributeMap, Map<String, Object> fcRequiredThreadAttributeMap,
			String subjectName, Subject subject, AccessPointDTO accessPoint, String enterpriseRoleId)
			throws com.ofss.digx.infra.exceptions.Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Entered into processApprovalOfTransactionBulk() : requestDTO= '%s' in class '%s'",
							transactionActionDTO, THIS_COMPONENT_NAME));
		}
	/*	super.checkAccessPolicy(
				"com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.processApprovalOfTransaction",
				sessionContext, transactionActionDTO, digxRequiredThreadAttributeMap, fcRequiredThreadAttributeMap);
		super.canonicalizeInput(transactionActionDTO);
*/
		Interaction.begin(sessionContext);

		Subject a_oSubject = SubjectUtil.getCurrentSubject();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk #### after setting subject userName ===" + a_oSubject);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Bulk #### after setting subject userName ===" + SubjectUtil.getUserName(a_oSubject));

		TransactionStatus transactionStatus = fetchTransactionStatus();
		List<com.ofss.digx.domain.approval.entity.transaction.checkerdetails.ApprovalCheckerDetails> approvalCheckerDetailsList = new ArrayList<>();
		com.ofss.digx.app.approval.service.transaction.Transaction transactionService = new com.ofss.digx.app.approval.service.transaction.Transaction();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("####Bulk Final perform OTP===" + transactionActionDTO.getTransactionDTO().getTransactionId());
			transactionService.performAction(sessionContext, transactionActionDTO);

		} catch (Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
						"Exception from setUpCheckerForTheTransaction() for listTransactionWorkflowSnapshotDTO '%s' in class %s",
						transactionActionDTO, THIS_COMPONENT_NAME), e);
				fillTransactionStatus(transactionStatus, e);
			}
		} catch (RuntimeException rte) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(rte);
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RunTimeException from setUpCheckerForTheTransaction for listTransactionWorkflowSnapshotDTO '%s' in class '%s'",
					transactionActionDTO, THIS_COMPONENT_NAME), rte);
		} finally {

			Interaction.close();

		}
		super.checkResponsePolicy(sessionContext, approvalCheckerDetailsList);
		if (LOGGER.isLoggable(Level.SEVERE)) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"Exiting from setUpCheckerForTheTransaction() : setUpCheckerForTheTransaction= '%s'", ""));
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from setUpCheckerForTheTransaction() : setUpCheckerForTheTransaction= '%s'", ""));
		}
	}



	@Override
	public String evaluateAuthTypeForMultiApproval(SessionContext sessionContext,
												   MultiApprovalTransactionListDTO multiApprovalTransactionListDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Entered into evaluateAuthTypeForMultiApproval() : requestDTO= '%s' in class '%s'",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.transactiontfa.service.TransactionTFA.checkTFA",
				sessionContext, multiApprovalTransactionListDTO);
		super.canonicalizeInput(multiApprovalTransactionListDTO);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		String evaluatedAuthType = null;
		String txnId = null;
		try {

			HashMap<String, String> taskCodeAndIDs = new HashMap<String, String>();
			taskCodeAndIDs = getTransactionTasks(multiApprovalTransactionListDTO);
			// List<String> taskIDs = getTransactionTasks(multiApprovalTransactionListDTO);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("final taskIDs-----------" + taskCodeAndIDs);

			List<String> taskIDs = new ArrayList<String>();
			for (Map.Entry<String, String> taskIdSet : taskCodeAndIDs.entrySet()) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TaskIdInList--> key  " + taskIdSet.getKey() + " and Value " + taskIdSet.getValue());
				taskIDs.add(taskIdSet.getKey());
			}

			HashMap<String, String> authTypeList = getAuthTypesForTasks(taskIDs);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("distinct authTypeList-----------" + authTypeList);

			List<String> authTypePriorityList = new ArrayList<String>();
			authTypePriorityList.add("SIGNEROTPITOKEN");
			authTypePriorityList.add("SIGNER");
			authTypePriorityList.add("OTP");
			authTypePriorityList.add("SEC_QUE");
			authTypePriorityList.add("FPSEMAILOTP");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("authTypePriorityList-----------" + authTypePriorityList);

			boolean matchFound = false;
			for (int i = 0; i < authTypePriorityList.size(); i++) {
				for (Map.Entry<String, String> set : authTypeList.entrySet()) {
					if (authTypePriorityList.get(i).equals(set.getKey())) {
						evaluatedAuthType = set.getValue();
						matchFound = true;
						break;
					}
				}

				if (matchFound == true)
					break;
			}

			com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey key = new TransactionKey();
			List<Object> serviceParameters = (List<Object>) ((HashMap<String, Object>) ThreadAttribute
					.get(ThreadAttribute.SERVICE_INPUTS)).get("Parameters");

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("evaluatedAuthType-----------" + evaluatedAuthType);

			for (Map.Entry<String, String> txnIdList : taskCodeAndIDs.entrySet()) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"txnIdList.getKey() " + txnIdList.getKey() + "txnIdList.getValue() " + txnIdList.getValue());
				if (evaluatedAuthType.equals(txnIdList.getKey())) {
					ThreadAttribute.set("TXN_ID_TO_READ", txnIdList.getValue());
					ThreadAttribute.set("MULTIPLE_TXN_COUNT",
							multiApprovalTransactionListDTO.getMultiApprovalTransactionList().size());
					key.setId(txnIdList.getValue());
					transactionDomain = transactionDomain.read(key);
					txnId = txnIdList.getValue();

					break;
				}
			}

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before Adding " + (String) ThreadAttribute.get("ADDED_IN_SERVICE_INPUTS"));
			if (sessionContext != null && serviceParameters != null && transactionDomain != null
					&& ((String) ThreadAttribute.get("ADDED_IN_SERVICE_INPUTS")).equals("false")) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Reading txn for servceParam");
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Size before adding" + serviceParameters.size());
				serviceParameters.add(transactionDomain.getTransactionSnapshot());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Size after adding" + serviceParameters.size());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"transactionDomain.getTransactionSnapshot()" + transactionDomain.getTransactionSnapshot());
				ThreadAttribute.set("ADDED_IN_SERVICE_INPUTS", "true");
				ThreadAttribute.set("ADDED_AT_INDEX", serviceParameters.size() - 1);
				for (Object serviceParameter : serviceParameters) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("in serviceParameter Type is " + serviceParameter);
				}

			}

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After Adding " + (String) ThreadAttribute.get("ADDED_IN_SERVICE_INPUTS"));

		} catch (Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE,
						FORMATTER.formatMessage(
								"Exception from evaluateAuthTypeForMultiApproval() for Transaction '%s' in class %s",
								multiApprovalTransactionListDTO, THIS_COMPONENT_NAME),
						e);
				fillTransactionStatus(transactionStatus, e);
			}
		} catch (RuntimeException rte) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(rte);
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from evaluateAuthTypeForMultiApproval for Transaction '%s' in class '%s'",
							multiApprovalTransactionListDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, evaluatedAuthType);
		if (LOGGER.isLoggable(Level.SEVERE)) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exiting from evaluateAuthTypeForMultiApproval() : Transaction= '%s'", ""));
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Exiting from evaluateAuthTypeForMultiApproval() : Transaction= '%s'", ""));
		}
		return evaluatedAuthType;
	}

	private HashMap<String, String> getTransactionTasks(MultiApprovalTransactionListDTO multiApprovalTransactionListDTO)
			throws Exception {
		List<String> transactionIDs = new ArrayList<String>();
		String[] taskIdToEvaluate = {"EB_F_BP_SI","EB_F_BP","APC","LMI_F_ACLM","LMI_F_CCLM","LMI_F_LCLM","LMI_F_TCLM","LMI_F_OCLM"};

		List<String> taskIDs = new ArrayList<String>();
		HashMap<String, String> taskCodeAndIDs = new HashMap<String, String>();
		List<com.ofss.digx.framework.domain.transaction.Transaction> transactionDomainList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
		MultipleApproval domain = new MultipleApproval();

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
				"Multi App DTO Size==" + multiApprovalTransactionListDTO.getMultiApprovalTransactionList().size());
		for (MultiApprovalTransactionDTO multiApprovalTransactionDTO : multiApprovalTransactionListDTO
				.getMultiApprovalTransactionList()) {

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction ID list-----------" + multiApprovalTransactionDTO.getTransactionID());
			transactionIDs.add(multiApprovalTransactionDTO.getTransactionID());
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("All Transaction ID list-----------" + transactionIDs);
		transactionDomainList = domain.listTransactionTasks(transactionIDs);

		for (com.ofss.digx.framework.domain.transaction.Transaction transaction : transactionDomainList) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("transaction.getTransactionName()-----------" + transaction.getTransactionName());
			// taskIDs.add(transaction.getTransactionName());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("transaction.getKey().getId() -----------" + transaction.getKey().getId());

			String newEvalutedTaskCode = transaction.getTransactionName();

			if(Arrays.asList(taskIdToEvaluate).contains(transaction.getTransactionName())){
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> calling custom evaluateTaskCode");
				newEvalutedTaskCode = evaluateTaskCode(transaction.getTransactionName(), transaction);
				taskCodeAndIDs.put(newEvalutedTaskCode, transaction.getKey().getId());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("newEvalutedTaskCode--> "+newEvalutedTaskCode);
			} else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In newEvalutedTaskCode else");
				taskCodeAndIDs.put(transaction.getTransactionName(), transaction.getKey().getId());
			}

		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("taskIDs LIST-----------" + taskIDs);
		return taskCodeAndIDs;
	}

	public String evaluateTaskCode(String taskCode,
								   com.ofss.digx.framework.domain.transaction.Transaction transactionData) {

		String newTaskCode = null;
		String [] taskCodesList = {"LMI_F_ACLM","LMI_F_CCLM","LMI_F_LCLM","LMI_F_TCLM","LMI_F_OCLM"};
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode --> taskCode sent is " + taskCode);

		if (taskCode.equals("EB_F_BP") || taskCode.equals("EB_F_BP_SI")) {
			BillPaymentDTO billPaymentRequestDTO = null;
			List<Object> objectList = new ArrayList<>();
			billPaymentRequestDTO = new BillPaymentDTO();

			if (transactionData.getTransactionSnapshot() instanceof BillPaymentDTO) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> TransactionDTO is instance of BillPaymentDTO");
				billPaymentRequestDTO = (BillPaymentDTO) transactionData.getTransactionSnapshot();
				objectList.add(billPaymentRequestDTO);

			} else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut
						.println("MultiApprovalEvaluateTaskCode--> not a TransactionDTO is instance of BillPaymentDTO");
			}

			ITaskEvaluatorFactory taskEvaluatorFactory = TaskEvaluatorConfigurator.getInstance()
					.getEvaluatorFactory(taskCode);
			ITaskEvaluator taskEvaluator = taskEvaluatorFactory.getEvaluator(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			try {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> try " + taskCode);
				newTaskCode = taskEvaluator.evaluateTaskCode(taskCode, objectList);
			} catch (Exception e) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> Error in evaluateTaskCode catch");
			}

		} else if (taskCode.equals("FU_F_APC")) {
			BulkFileUploadsDTO fileuploadDTO=null;
			List<Object> objectList = new ArrayList<>();
			fileuploadDTO = new BulkFileUploadsDTO();
			if (transactionData.getTransactionSnapshot() instanceof BulkFileUploadsDTO) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> FU_F_APC Evaluate");
				fileuploadDTO = (BulkFileUploadsDTO) transactionData.getTransactionSnapshot();
				objectList.add(fileuploadDTO);

			} else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode-->  FU_F_APC Evaluate false");
			}

			ITaskEvaluatorFactory taskEvaluatorFactory = TaskEvaluatorConfigurator.getInstance()
					.getEvaluatorFactory(taskCode);
			ITaskEvaluator taskEvaluator = taskEvaluatorFactory.getEvaluator(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			try {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> try " + taskCode);
				newTaskCode = taskEvaluator.evaluateTaskCode(taskCode, objectList);
			} catch (Exception e) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> Error in evaluateTaskCode catch");
			}

		} else if (Arrays.asList(taskCodesList).contains(taskCode)) {
			CZLMCreateRequestDTO czLMCreateRequestDTO = new CZLMCreateRequestDTO();
			List<Object> objectList = new ArrayList<>();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Class in LM TXN DATA-->"+transactionData.getClass());

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> LM Evaluate");
			if (transactionData.getTransactionSnapshot() instanceof CZLMCreateRequestDTO) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZLMCreateRequestDTO If");
				czLMCreateRequestDTO = (CZLMCreateRequestDTO) transactionData.getTransactionSnapshot();
				objectList.add(czLMCreateRequestDTO);

			} else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode-->  CZLMCreateRequestDTO Evaluate false");
			}

			ITaskEvaluatorFactory taskEvaluatorFactory = TaskEvaluatorConfigurator.getInstance()
					.getEvaluatorFactory(taskCode);
			ITaskEvaluator taskEvaluator = taskEvaluatorFactory.getEvaluator(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			try {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode-->  CZLMCreateRequestDTOtry " + taskCode);
				newTaskCode = taskEvaluator.evaluateTaskCode(taskCode, objectList);
			} catch (Exception e) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> CZLMCreateRequestDTO Error in evaluateTaskCode catch");
			}


		}else {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("MultiApprovalEvaluateTaskCode--> in first else");
		}

		return newTaskCode;
	}

	private HashMap<String, String> getAuthTypesForTasks(List<String> taskIDs) throws Exception {
		List<com.ofss.digx.domain.security.entity.authentication.AuthenticationMapping> authenticationMapping = new ArrayList<com.ofss.digx.domain.security.entity.authentication.AuthenticationMapping>();
		List<String> authTypeList = new ArrayList<String>();
		HashMap<String, String> authTypeMap = new HashMap<String, String>();
		MultipleApproval domain = new MultipleApproval();

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getAuthTypesForTasks--taskIDs LIST-----------" + taskIDs);
		authenticationMapping = domain.listAuthTypesByTaskList(taskIDs);

		if(authenticationMapping.size() > 0) {
			for (com.ofss.digx.domain.security.entity.authentication.AuthenticationMapping authentication : authenticationMapping) {
				if(authentication.getAuthenticationInfoList() != null && authentication.getAuthenticationInfoList().size() > 0) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getAuthType-----------"+ authentication.getAuthenticationInfoList().get(0).getAuthType().getKey().getId());
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getAuthType TASK::-----------" + authentication.getTask());
					authTypeMap.put(authentication.getAuthenticationInfoList().get(0).getAuthType().getKey().getId(),authentication.getTask());
				}else {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("authentication.getAuthenticationInfoList() is null");
				}
			}
		}else {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("authenticationMapping size is 0");
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("authTypeList-------" + authTypeMap);
		return authTypeMap;
	}

	@Override
	public TransactionStatus updateProcessingDetails(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into updateProcessingDetails() : requestDTO= '%s' in class '%s'", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(
				"com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.updateProcessingDetails",
				sessionContext);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("From updateProcessingDetails : Updating Domain : START");
			String partyId = sessionContext.getTransactingPartyCode();
			if (partyId != null && partyId.trim().length() > 0) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateTransactionProcessingStatus(null, null, null,
						sessionContext.getTransactingPartyCode());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("From updateProcessingDetails : Updating Domain : END");
			}
		} catch (Exception e) {
			fillTransactionStatus(transactionStatus, e);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"Exception from method updateProcessingDetails() in class %s", THIS_COMPONENT_NAME), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method updateProcessingDetails() in class %s", THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.encodeOutput(transactionStatus);
		super.checkResponsePolicy(sessionContext, transactionStatus);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from method updateProcessingDetails() in Service Output: Transaction Status : %s in class %s",
					transactionStatus, THIS_COMPONENT_NAME));
		}
		return transactionStatus;
	}

	/**
	 * @param sessionContext
	 * @param rejectedTransactionList
	 * @throws Exception
	 */
	@Override
	public TransactionStatus handleRejectedTransactions(SessionContext sessionContext,
														List<com.ofss.digx.framework.domain.transaction.Transaction> rejectedTransactionList) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleRejectedTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleRejectedTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleRejectedTransactions : Number of Transactions for rejection : "
					+ rejectedTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : rejectedTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Update Transaction Id : " + txnObj.getKey().getId()
						+ " TransactionName=" + txnObj.getTransactionName());

//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleRejectedTransactions BEFORE REJECT: SIGNEDBY = "
//						+ txnObj.getApprovalDetails().getSignedBy());
				adapter.rejectTransaction(txnObj.getKey().getId());

//				TransactionKey key = new TransactionKey();
//				key.setId(txnObj.getProcessingDetails().getReferenceNumber());
//
//
//				com.ofss.digx.framework.domain.transaction.Transaction txnObjNew2 = txnObj.read(txnObj.getKey());

//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleRejectedTransactions AFTER REJECT: SIGNEDBY = "
//						+ txnObjNew2.getApprovalDetails().getSignedBy());


				if (txnObj.getTransactionName() != null
						&& txnObj.getTransactionName().equalsIgnoreCase("CH_N_RADHSTMT")) {
					IAdapterFactory adapterfactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.ADHOC_STATEMENT_ADAPTER_FACTORY);
					IAdhocStatementAdapter Adhocadapter = (IAdhocStatementAdapter) adapterfactory.getAdapter(
							com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.ADHOC_STATEMENT_ADAPTER);
					AdhocStatementDTO request = new AdhocStatementDTO();
					request.setTxnReferenceNumber(txnObj.getKey().getId());
					request.setRequestStatus("REJECTED");
					Adhocadapter.updateTxnStatusPostApproval(request);
				}

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions (value date < current date) : Transaction Id : "
						+ txnObj.getKey().getId() + " updated to rejected");
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleRejectedTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleRejectedTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	@Override
	public TransactionTFAResponseDTO check2FA(PolicyAssertionContext accessContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method check2FA() in Service  Input: TransactionTFARequestDTO: %s in class %s",
					request, THIS_COMPONENT_NAME));
		}
		TransactionStatus transactionStatus = fetchTransactionStatus();
		TransactionTFAResponseDTO response = new TransactionTFAResponseDTO();
		boolean firstTimeAuthFlag = false;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside check2FA service");
			response.setStatus(fetchStatus());
			TaskAuthProcessing taskAuthProcessing = new TaskAuthProcessing();

			String generatedId = null;
			IdGenerator secureGenerator = (IdGenerator) AbstractGeneratorFactory.getUniqueInstance()
					.getIdGenerator("OpenAPIConsent", "AccountId");

			generatedId = secureGenerator.generateId("OpenAPIConsent", "AccountId", "", -1,
					new HashMap<String, Object>());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("OMB generatedId=" + generatedId);
			com.ofss.fc.infra.thread.ThreadAttribute.set(OMB_REF_NO, generatedId);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##OMB Ref No 1=" + (String) com.ofss.fc.infra.thread.ThreadAttribute.get(OMB_REF_NO));

			taskAuthProcessing.check2FA(accessContext);

			if (isTFARequired()) {
				firstTimeAuthFlag = true;
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTransactionExt 2 throwing exception");
				throw new TFARequiredException(TFAErrorConstant.AUTHENTICATION_REQUIRED);
			}

			response.setStatus(buildStatus(transactionStatus));
		} catch (Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside catch exception - omb");
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method check2FA() in Service  Input: TransactionTFARequestDTO: '%s' in class %s",
					TransactionTFAResponseDTO.class.getName(), THIS_COMPONENT_NAME, request, e), e);
			fillTransactionStatus(transactionStatus, e);

			if (!firstTimeAuthFlag) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("throw firstTimeAuthFlag =" + e);
				throw e;
			}
		} catch (RunTimeException rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method check2FA() in Service  Input: TransactionTFARequestDTO: '%s' in class %s",
					request, THIS_COMPONENT_NAME), rte);
		} catch (FatalException fte) {
			fillTransactionStatus(transactionStatus, fte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method check2FA() in Service  Input: TransactionTFARequestDTO: '%s' in class %s",
					request, THIS_COMPONENT_NAME), fte);
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from method checkTFA() in Service Output: TransactionTFAResponseDTO : %s in class %s",
					response, THIS_COMPONENT_NAME));
		}

		return response;
	}

	private boolean isTFARequired() {
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##X_CHALLENGE=" + com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE));
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##TFA_REQUIRED=" + com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED));
		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED) != null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##TFA_REQUIRED Boolean=" + (boolean) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED));
		}
		return (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE) != null
				&& !(com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED) != null
				&& (boolean) com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED)));
	}

	/**
	 * @param sessionContext
	 * @param transactionRefNo
	 * @return
	 * @throws Exception
	 */

	public void updatePartyId(SessionContext sessionContext, String transactionRefNo) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered into method updatePartyId() in Service in class %s",
					THIS_COMPONENT_NAME));
		}
//		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleRejectedTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
//		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
//		TransactionStatus transactionStatus = fetchTransactionStatus();
		try {
			if (transactionRefNo != null) {
				com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
				TransactionKey transactionKey = new TransactionKey();
				transactionKey.setId(transactionRefNo);
				transaction = transaction.read(transactionKey);

				TransactionApprovalDetails transactionApprovalDetails = transaction.getApprovalDetails();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction Status : " + transactionApprovalDetails.getStatus() + "Step No : "
						+ transactionApprovalDetails.getStepNo() + "Created By Transaction : "
						+ transaction.getCreatedBy() + "Session User : " + sessionContext.getUserId());
				if (transactionApprovalDetails.getStatus().equals(ApprovalStatus.APPROVED)
						&& transactionApprovalDetails.getStepNo() == 1
						&& transaction.getCreatedBy().equals(sessionContext.getUserId())) {

					List<TransactionApprovalHistory> transactionApprovalHistories = transaction
							.getTransactionApprovalHistory();
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("List of Transaction Approval History : " + transactionApprovalHistories.size());
					// transactionApprovalHistories.clear();
					// transaction.setTransactionApprovalHistory(transactionApprovalHistories);
					// transaction.getApprovalDetails().setStatus(ApprovalStatus.EXPIRED);
					// transaction.setCreatedBy("OFSSUser");
					// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Created By in Transaction after updating : " +
					// transaction.getCreatedBy()
					// + "Status : " + transaction.getApprovalDetails().getStatus());
					// transaction.update(transaction);

					CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter
							.getInstance();

					localRepository.updateTransactionApprovalHistory(transactionRefNo);

					localRepository.updateTransactionFromId(transactionRefNo);

				}

				// Session session = null;
				// boolean isSessionOpen = false;
				// try {
				// if (DataAccessManager.getManager().isSessionOpen()) {
				// session = DataAccessManager.getManager().fetchCurrentSession();
				// } else {
				// session = DataAccessManager.getManager().openSession("DIGX");
				// session.beginTransaction();
				// isSessionOpen = true;
				// }
				// transaction.setCreatedBy("OFSSUser");
				// session.update(transaction);
				// } catch (PersistenceException e) {
				// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				// }
				// catch (InfraException e) {
				// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				// }
				// catch (javax.persistence.PersistenceException pex) {
				// pcom.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(ex);
				// }
				// finally {
				// if (isSessionOpen) {
				// session.fetchCurrentTransaction().commit();
				// DataAccessManager.getManager().closeSession(session);
				// }
				// }
				// if (transaction instanceof PartyTransaction) {
				// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside Party Transaction updating party id");
				// com.ofss.digx.framework.domain.transaction.PartyTransaction partyTransaction
				// = new
				// com.ofss.digx.framework.domain.transaction.PartyTransaction();
				// partyTransaction = (PartyTransaction) transaction;
				// com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside Party Transaction updating party id : " +
				// partyTransaction.getPartyId());
				// partyTransaction.setPartyId(partyTransaction.getPartyId().concat("9999"));
				// partyTransaction.update(partyTransaction);
				// }

			}
//			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (RuntimeException rte) {
//			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from read for updatePartyId() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
//		super.checkResponsePolicy(sessionContext, transactionListResponse);
//		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from method updatePartyId() in Service Output : in class %s", THIS_COMPONENT_NAME));
		}
//		return transactionStatus;

	}

	public void updateOMBTransactionDetails(SessionContext sessionContext, String transactionRefNo, String username,
											String apprStatus, String hostRefNo) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateOMBTransactionDetails() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateOMBTransactionDetails service=" + transactionRefNo);
			if (transactionRefNo != null && username != null && apprStatus != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateOMBTransactionDetails(transactionRefNo, username, apprStatus, hostRefNo);
				localRepository.updateOMBTxnHistoryDetails(transactionRefNo, username);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from read for updateOMBTransactionDetails() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method updateOMBTransactionDetails() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
	}

	public void updateCreatedBy(SessionContext sessionContext, String transactionRefNo, String username)
			throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateCreatedBy() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateCreatedBy service=" + transactionRefNo);
			if (transactionRefNo != null && username != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateCreatedBy(transactionRefNo, username);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from read for updateCreatedBy() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from method updateCreatedBy() in Service Output : in class %s", THIS_COMPONENT_NAME));
		}
	}

	public void updateProcessingTransaction(SessionContext sessionContext, String transactionRefNo, String status,
											String errorCode, String errorMessage) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateProcessingTransaction() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateProcessingTransaction service=" + transactionRefNo);
			if (transactionRefNo != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateProcessingTransaction(transactionRefNo, status, errorCode, errorMessage);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from read for updateProcessingTransaction() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method updateProcessingTransaction() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
	}

	/**
	 * @param sessionContext
	 * @param transactionRefNo
	 * @return
	 * @throws Exception
	 */
	public TransactionNlsNameResponse getNlsTransactionName(SessionContext sessionContext, String transactionRefNo)
			throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method getNlsTransactionName() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleRejectedTransactions", sessionContext);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		TransactionNlsNameResponse response = null;
		try {
			response = new TransactionNlsNameResponse();
			String nlsName = "Transaction";
			Boolean isOmb = false;
			if (transactionRefNo != null) {
				com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
				TransactionKey transactionKey = new TransactionKey();
				transactionKey.setId(transactionRefNo);
				transaction = transaction.read(transactionKey);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction Code from ref no :" + transaction.getTransactionName());
				String transactionName = transaction.getTransactionName();
				nlsName = getTranslatedRoleName(BUNDLE_NAME, transactionName, LocaleUtils.getUserLocale(), nlsName);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Nls from txn code :" + nlsName);

				TransactionApprovalDetails transactionApprovalDetails = transaction.getApprovalDetails();
				Subject subject = SubjectUtil.getCurrentSubject();
				String userName = SubjectUtil.getUserName(subject);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction Status : " + transactionApprovalDetails.getStatus() + "Step No : "
						+ transactionApprovalDetails.getStepNo() + "Created By Transaction : "
						+ transaction.getCreatedBy() + "Subject Util User : " + userName);
				if (transactionApprovalDetails.getStatus().equals(ApprovalStatus.APPROVED)
						&& transactionApprovalDetails.getStepNo() == 1 && transaction.getCreatedBy().equals(userName)) {
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("It is an omb transaction setting value true");
					isOmb = true;
				}
			}
			response.setIsOmb(isOmb);
			response.setTransactionNlsName(nlsName);
			response.setStatus(buildStatus(transactionStatus));
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from get for getNlsTransactionName() '%s' in class '%s'",
							response, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, response);
		super.encodeOutput(response);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method getNlsTransactionName() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}

		return response;

	}

	private String getTranslatedRoleName(String bundleName, String key, Locale locale, String originalRoleName) {
		if (locale == null && ThreadAttribute.get(USER_LOCALE) != null)
			locale = Locale.forLanguageTag(ThreadAttribute.get(USER_LOCALE).toString());

		try {
			ResourceBundle bundle = ResourceBundle.getBundle(bundleName,
					(locale != null) ? locale : LocaleUtils.getDefaultLocale());
			String value = bundle.getString(key);
			String convertedString = new String(value.getBytes("ISO-8859-1"), "UTF-8");
			return convertedString;
		} catch (MissingResourceException e) {
			if (LOGGER.isLoggable(Level.FINE))
				LOGGER.log(Level.FINE, FORMATTER.formatMessage("No resource bundle found for bundle %s and key %s",
						new Object[] { bundleName, key }), e);

		} catch (UnsupportedEncodingException e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}
		return originalRoleName;
	}

	public void updateXTransactionRecord(SessionContext sessionContext, String transactionRefNo, String username,
										 String status) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateXTransactionRecord() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateXTransactionRecord service=" + transactionRefNo);
			if (transactionRefNo != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateXTransactionRecord(transactionRefNo, username, status);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from read for updateXTransactionRecord() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method updateXTransactionRecord() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
	}

	public void updateHistoryIndex(SessionContext sessionContext, String transactionRefNo) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateHistoryIndex() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateHistoryIndex service=" + transactionRefNo);
			if (transactionRefNo != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateHistoryIndex(transactionRefNo);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from read for updateHistoryIndex() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from method updateHistoryIndex() in Service Output : in class %s", THIS_COMPONENT_NAME));
		}
	}

	public void updateTransactionValueDate(SessionContext sessionContext, String transactionRefNo, Date valueDate)
			throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateTransactionValueDate() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateTransactionValueDate service=" + transactionRefNo);
			if (transactionRefNo != null && valueDate != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateTransactionValueDate(transactionRefNo, valueDate);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from read for updateTransactionValueDate() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method updateTransactionValueDate() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
	}

	public void updateHostErrorDisplayCode(SessionContext sessionContext, String txnId, String errorCode,
										   String errorMessage) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method updateHostErrorDisplayCode() in Service in class %s", THIS_COMPONENT_NAME));
		}
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		Interaction.begin(sessionContext);
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in updateHostErrorDisplayCode service=" + txnId + "=" + errorCode);
			if (txnId != null && errorCode != null) {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				localRepository.updateHostErrorDisplayCode(txnId, errorCode, errorMessage);
			}
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from read for updateHostErrorDisplayCode() '%s' in class '%s'",
							transactionListResponse, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method updateHostErrorDisplayCode() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
	}

	@Override
	@NoEntitlement
	public TransactionStatus handleExpiredTDTransactionsMod2(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactions");
			List<com.ofss.digx.framework.domain.transaction.Transaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredTDTransactionsMod2();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTDTransactionsMod2 : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactionsMod2 :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTransactions (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}
					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	@Override
	@NoEntitlement
	public TransactionStatus handleExpiredTDTransactionsMod1CM(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactionsMod1CM");
			List<com.ofss.digx.framework.domain.transaction.Transaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredTDTransactionsMod1CM();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTDTransactionsMod1CM : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactionsMod1CM :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactionsMod1CM : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTDTransactionsMod1CM (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}
					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTDTransactionsMod1CM() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTDTransactionsMod1CM() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}




	@Override
	@NoEntitlement
	public TransactionStatus handleExpiredLMTransactions(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredLMTransactions");
			List<com.ofss.digx.framework.domain.transaction.Transaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredLMTransactions();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredLMTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredLMTransactions : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredLMTransactions :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredLMTransactions : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredLMTransactions (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}
					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredLMTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredLMTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}



	public TransactionStatus handleExpiredTransactionsMod1BM(SessionContext sessionContext)  throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM");
			List<com.ofss.digx.framework.domain.transaction.AmountAccountTransaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.AmountAccountTransaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredTDTransactionsMod1BM();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTransactionsMod1BM : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.AmountAccountTransaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransactionTDMod1BM(txnObj.getKey().getId(), txnObj.getAccountId(),txnObj.getPartyId(), txnObj.getModuleReferenceId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTransactionsMod1BM (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransactionTDMod1BM(txnObj.getKey().getId(), txnObj.getAccountId(),txnObj.getPartyId(), txnObj.getModuleReferenceId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}
					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}

			// Adding code for Marking Approved Transactions as Expired
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM Marking Txns as Expired that were in approved Status in DIGX_CZ_AC_TDA Table");
			List<com.ofss.digx.framework.domain.transaction.AmountAccountTransaction> expiredTransactionListBM = new ArrayList<com.ofss.digx.framework.domain.transaction.AmountAccountTransaction>();
			expiredTransactionListBM = localRepo.listExpiredTDTransactionsMod1BMTable();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BMTable : Number of Transactions for expiry : "
					+ expiredTransactionListBM.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionListBM) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTransactionsMod1BMTable : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.AmountAccountTransaction txnObj : expiredTransactionListBM) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {

					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactionsMod1BM : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransactionTDMod1BMTable(txnObj.getKey().getId(), txnObj.getAccountId(),txnObj.getPartyId(), txnObj.getModuleReferenceId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTransactionsMod1BM (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						// adapter.updateTransaction(txnObj.getKey().getId());
						adapter.expireTransactionTDMod1BMTable(txnObj.getKey().getId(), txnObj.getAccountId(),txnObj.getPartyId(), txnObj.getModuleReferenceId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					}
					else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTDTransactionsMod1CM() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTDTransactionsMod1CM() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	/**
	 * @param sessionContext
	 * @param taskId
	 * @param currency
	 * @param amount
	 * @param partyId
	 * @return
	 * @throws Exception
	 */
	public List<com.ofss.digx.framework.domain.transaction.Transaction> getSameTransactionList(
			SessionContext sessionContext, String taskId, String currency, BigDecimal amount, String partyId)
			throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered into getSameTransactionList", taskId));
		}
		Interaction.begin(sessionContext);
		List<com.ofss.digx.framework.domain.transaction.Transaction> getSameTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside Transaction service getSameTransactionList check 6 : taskId" + taskId);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered in getSameTransactionList service=" + taskId);
			if (taskId != null && currency != null && amount != null) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Inside if of service getSameTransactionList check 7");
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();
				getSameTransactionList = localRepository.getSameTransactionList(taskId, currency, amount, partyId);

			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"com.ofss.digx.infra.exceptions.Exception from listRejectedRuleTransactions for in class '%s'",
					THIS_COMPONENT_NAME), e);
		} finally {
			Interaction.close();
		}

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Exiting from listRejectedRuleTransactions() : fetchTransactionStatus()=%s",
							fetchTransactionStatus()));
		}
		return getSameTransactionList;
	}

	public TransactionStatus handleExpiredFxAgreedRateTransactions(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleExpiredTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredFxAgreedRateTransactions");
			List<com.ofss.digx.framework.domain.transaction.Transaction> expiredTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
			expiredTransactionList = localRepo.listExpiredFxAgreedRateTransactions();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : Number of Transactions for expiry : "
					+ expiredTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredFxAgreedRateTransactions : List of SI Transactions Picked Up: " + txnObj.getKey().getId()
								+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
			}
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : expiredTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				valueDate = txnObj.getCreationDate();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTDTransactions :  Transaction Id : " + txnObj.getKey().getId()
						+ " valueDate=" + valueDate + " TransactionName=" + txnObj.getTransactionName());
				Date hostDate = BranchDateHelper.getCurrentDate();

				if (hostDate.isBeforeOrEqual(valueDate)) {
					if (valueDate.getYear() == hostDate.getYear()) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In handleExpiredTransactions : in Year Comparison Block: Value Date: "
								+ valueDate.getYear() + "Branch Date is " + hostDate.getYear());
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut
								.println("In handleExpiredTransactions (value date == current date) : Transaction Id : "
										+ txnObj.getKey().getId()
										+ " The Transaction is marked expired as date was reached");
					}
				} else {
					if (valueDate.getYear() == hostDate.getYear()) {
						adapter.expireTransaction(txnObj.getKey().getId());
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" In the expire transaction block : Transaction Id : "
								+ txnObj.getKey().getId() + " updated to expired as value date as passed");
					} else {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
								"Years were different, Expire Action Not performed for:" + txnObj.getKey().getId());
					}
				}
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	public TransactionStatus rejectTxnMaker(SessionContext sessionContext, RejectTransactionMakerDTO rejectTransactionMakerDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into rejectTxnMaker()  in class '%s'",  THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.rejectTxnMaker",
				sessionContext);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = new TransactionStatus();
		try {
			// executor.preCheckApprovals(sessionContext, requestDTO);
//			AbstractBusinessPolicy abstractBusinessPolicy = null;
//
//			BusinessPolicyFactory businessPolicyFactory = BusinessPolicyFactory.getInstance();
//			CheckApprovalsBusinessPolicyDTO transactionBusinessPolicyDTO = new CheckApprovalsBusinessPolicyDTO();
//			transactionBusinessPolicyDTO.setRequestDTO(requestDTO);
//			abstractBusinessPolicy = businessPolicyFactory.getBusinesPolicyInstance(
//					"com.ofss.digx.app.approval.service.transaction.Transaction.checkApprovals",
//					transactionBusinessPolicyDTO);
//			abstractBusinessPolicy.validate();

			String userId = sessionContext.getUserId();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("userId from session Context: " +userId);
			String makerFromDB = new String();
			String apprvStatus = new String();
//			String signedByPlusApprovalStatus = new String();
//			CZLocalTransactionRepositoryAdapter localRepo = CZLocalTransactionRepositoryAdapter.getInstance();
//			signedByPlusApprovalStatus = localRepo.getSignedBy(transactionRefNo);
//			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("signedByPlusApprovalStatus: " +signedByPlusApprovalStatus);
//			String[] parts = signedByPlusApprovalStatus.split("~");
//			if(parts!=null) {
//				makerFromDB = parts[1];
//				apprvStatus = parts[parts.length-1];
//			}
			com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey key = new TransactionKey();
			key.setId(rejectTransactionMakerDTO.getTxnRefNo());
			transactionDomain.setKey(key);
			transactionDomain = transactionDomain.read(key);
			makerFromDB = transactionDomain.getApprovalDetails().getSignedBy().split("~")[1];
			apprvStatus = transactionDomain.getApprovalDetails().getStatus().toString();
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_CANEL_TXN", true);
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_CANEL_TXN_Code", transactionDomain.getTransactionName());
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_CANEL_TXN_ID", rejectTransactionMakerDTO.getTxnRefNo());

			if (makerFromDB.equalsIgnoreCase(userId) && (("PENDING_APPROVAL").equalsIgnoreCase(apprvStatus) || ("MODIFICATION_REQUESTED").equalsIgnoreCase(apprvStatus))) {
				ThreadAttribute.set("MAKER_REJECT_LAST_UPDATED_BY", "SYSTEMWITHDRAW");
				ThreadAttribute.set("MAKER_REJECT_MESSAGE", rejectTransactionMakerDTO.getRejectMessage());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before call to handleRejectedTransactions ");
				List<com.ofss.digx.framework.domain.transaction.Transaction> rejectedTransactionList = new ArrayList<com.ofss.digx.framework.domain.transaction.Transaction>();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before call to handleRejectedTransactions SIgned_by = " + transactionDomain.getApprovalDetails().getSignedBy());
				rejectedTransactionList.add(transactionDomain);
				transactionStatus = handleRejectedTransactions(sessionContext, rejectedTransactionList);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After call to handleRejectedTransactions");
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TransactionStatus: " +transactionStatus.toString());
			}


		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from update()  in class %s",
							THIS_COMPONENT_NAME), rte);
			ThreadAttribute.set(ThreadAttribute.WORKFLOWS_EVALUATED, false);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, fetchTransactionStatus());
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Exiting from CZ update() : TransactionStatus=%s",
					fetchTransactionStatus()));
		}
		return transactionStatus;
	}

//	@Override
//	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.PERFORM, requiredResources = {
//			"com.ofss.digx.app.approval.service.transaction.Transaction.listTransactions",
//			"com.ofss.digx.app.approval.service.transaction.Transaction.transactionsCount" })
//	@Entitlement(name = "Approve/Reject Transaction", action = ActionType.APPROVE, requiredResources = { "" })
//	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Approvals)
//	@Task(id = "PA_APT", parent = "APT", name = "Perform Action", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.APPROVALS, aspects = {
//			TaskAspect.GRACE_PERIOD, TaskAspect.AUDIT }, type = TaskType.FINANCIAL_TRANSACTION)
	public TransactionActionResponse performAction(SessionContext sessionContext,
			TransactionActionDTO transactionActionDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Entered into performAction() : TransactionActionDTO=%s in class %s ",
							transactionActionDTO, THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.rejectTxnMaker",
				sessionContext);
		super.canonicalizeInput(transactionActionDTO);
		com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = null;
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		transactionActionResponse.setStatus(fetchStatus());
		AbstractBusinessPolicy abstractBusinessPolicy = null;
		Interaction.begin(sessionContext);
		Session session = null;
		TransactionAssembler transactionAssembler = null;
		try {
//			executor.prePerformAction(sessionContext, transactionActionDTO);

			BusinessPolicyFactory businessPolicyFactory = BusinessPolicyFactory.getInstance();
			String eventId = null;
			String remarks = transactionActionDTO.getTransactionDTO().getApprovalDetails().getRemarks();
			ThreadAttribute.set(ThreadAttribute.TRANSACTION_REFERENCE_NO,
					transactionActionDTO.getTransactionDTO().getTransactionId());
			com.ofss.digx.infra.thread.ThreadAttribute.set(
					com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO,
					transactionActionDTO.getTransactionDTO().getTransactionId());
			transactionActionResponse.setTransactionAction(transactionActionDTO);
			transactionActionDTO.validate(sessionContext);
			transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey transactionKey = new TransactionKey();
			transactionKey.setId(transactionActionDTO.getTransactionDTO().getTransactionId());
			session = DataAccessManager.getManager().openNewSession(com.ofss.fc.common.Constants.FCRJ_NON_XA_APP);
			session.beginTransaction();
			transactionDomain = (com.ofss.digx.framework.domain.transaction.Transaction) session
					.get(com.ofss.digx.framework.domain.transaction.Transaction.class, transactionKey);
			ApprovalType approvalType = transactionDomain.getApprovalDetails().getApprovalType();
			TransactionDTO transactionDTO;
			PartyPreferencesResponse preferences = null;
			if (sessionContext.getTransactingPartyCode() != null) {
				IAdapterFactory adapterFactoryPartyPreference = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(com.ofss.digx.common.constants.CommonAdapterFactoryConstants.PARTY_PREFERENCES_ADAPTER_FACTORY);
				IPartyPreferencesAdapter adapterPartyPreference = (IPartyPreferencesAdapter) adapterFactoryPartyPreference
						.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.PARTY_PREFERENCES_ADAPTER);
				PartyPreferencesDTO partyPreferencesDTO = new PartyPreferencesDTO();
				Party party = new Party();
				party.setValue(sessionContext.getTransactingPartyCode());
				partyPreferencesDTO.setParty(party);
				preferences = adapterPartyPreference.read(sessionContext, partyPreferencesDTO);
			}
			TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			transactionAssembler = transactionAssemblerFactory
					.getAssemblerInstance(transactionDomain.getDiscriminator());
			transactionDTO = transactionAssembler.fromDomainObject(transactionDomain);
			transactionActionDTO.setTransactionDTO(transactionDTO);
			PerformActionSystemConstraint peformActionSystemConstraint = new PerformActionSystemConstraint(
					transactionActionDTO);
			peformActionSystemConstraint.isSatisfiedBy();
			TransactionApprovalAccessCheckConstraint systemConstraint = new TransactionApprovalAccessCheckConstraint(
					transactionDTO);
			systemConstraint.isSatisfiedBy();
			TransactionBusinessPolicyDTO transactionBusinessPolicyDTO = new TransactionBusinessPolicyDTO();
			transactionBusinessPolicyDTO.setTransactionDTO(transactionDTO);
			transactionBusinessPolicyDTO.setAction(transactionActionDTO.getAction());
			transactionBusinessPolicyDTO.setCurrentDate(getCurrentDate(sessionContext));
			if (preferences != null && preferences.getPartyPreferencesDTOs() != null
					&& preferences.getPartyPreferencesDTOs().getGracePeriod() != null)
				transactionBusinessPolicyDTO.setGracePeriod(preferences.getPartyPreferencesDTOs().getGracePeriod());
			abstractBusinessPolicy = businessPolicyFactory.getBusinesPolicyInstance(
					"com.ofss.digx.app.approval.service.transaction.Transaction.performAction",
					transactionBusinessPolicyDTO);
			abstractBusinessPolicy.validate();
			IActionStatusEvaluator actionEvaluator = ActionStatusEvaluatorFactory.getInstance()
					.getActionStatusEvaluator(transactionActionDTO.getAction().toString());
			ApprovalStatus nextStatus = actionEvaluator
					.getNextStatus(transactionDomain.getApprovalDetails().getStatus(), transactionDomain);
			switch (nextStatus) {
			case APPROVED:
				eventId = ApprovalConstants.TRANSACTION_APPROVED;
				break;
			case REJECTED:
				eventId = ApprovalConstants.TRANSACTION_REJECTED;
				break;
			case MODIFICATION_REQUESTED:
				eventId = ApprovalConstants.TRANSACTION_MODIFICATION_REQUESTED;
				break;
			case PENDING_APPROVAL:
				eventId = ApprovalConstants.TRANSACTION_PARTIALLY_APPROVED;
				break;
			default:
				eventId = null;
				break;
			}
			List<TransactionApprovalHistory> transactionApprovalHistories;
			if (transactionDomain.getTransactionApprovalHistory() != null) {
				transactionApprovalHistories = transactionDomain.getTransactionApprovalHistory();
			} else {
				transactionApprovalHistories = new ArrayList<>();
				transactionDomain.setTransactionApprovalHistory(transactionApprovalHistories);
			}
			transactionApprovalHistories.add(0, transactionDomain.toTransactionHistory());
			transactionDomain.setLastUpdatedBy(getLoggedInUserId());
			transactionDomain.setLastUpdatedDate(new Date());
			transactionDomain.setTransactionApprovalHistory(transactionApprovalHistories);
			TransactionApprovalDetails transactionApprovalDetails = transactionDomain.getApprovalDetails();
			transactionApprovalDetails.setStatus(nextStatus);
			transactionApprovalDetails.setAction(transactionActionDTO.getAction());
			TransactionUserDetails transactionUserDetails = new TransactionUserDetails();
			ISubjectAdapter subjectAdapter = AdapterFactory.getInstance().getAdapter(ISubjectAdapter.class);
			UserDTO userDTO = subjectAdapter.getUserProfile();
			transactionUserDetails.setEmailId(userDTO.getBusinessEmail());
			transactionUserDetails.setTitle(userDTO.getTitle());
			transactionUserDetails.setFirstName(userDTO.getFirstName());
			transactionUserDetails.setLastName(userDTO.getLastName());
			transactionUserDetails.setMiddleName(userDTO.getMiddleName());
			transactionUserDetails.setMobileNumber(userDTO.getBusinessMobile());
			transactionUserDetails.setPhoneNumber(userDTO.getHomePhone());
			transactionUserDetails.setUsername(userDTO.getUserName());
			transactionDomain.setUpdatedByDetails(transactionUserDetails);
			transactionDomain.getProcessingDetails().setCurrentStep(ProcessingStep.APPROVAL);
			transactionDomain.getProcessingDetails()
					.setStatus(actionEvaluator.getProcessingStatus(nextStatus, transactionDomain));
			if (com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE) != null) {
				throw new TFARequiredException(TFAErrorConstant.AUTHENTICATION_REQUIRED);
			}
			Integer nextStepNo = transactionApprovalDetails.getStepNo();
			if (transactionActionDTO.getAction().equals(ApprovalAction.APPROVE)) {
				validateLimit(transactionDomain, nextStatus == ApprovalStatus.APPROVED);
				if (nextStatus == ApprovalStatus.APPROVED) {
					validateTransactionBlackout(transactionDomain);
				}
				if (ApprovalType.NONSEQUENTIAL != (approvalType)) {
					nextStepNo++;
				}
			} else {
				reverseLimits(sessionContext, transactionDomain);
			}
			List<TransactionWorkflowSnapshotDTO> workflowSnapshotsToBeRemoved = actionEvaluator
					.fetchWorkflowsForElimination(transactionDomain);
			transactionApprovalDetails.setStepNo(nextStepNo);
			transactionDomain.setApprovalDetails(transactionApprovalDetails);
			transactionDomain.getApprovalDetails().setRemarks(remarks);
			List<TransactionWorkflowSnapshot> workflowSnapshotObjectRemoveList = new ArrayList<>();
			for (TransactionWorkflowSnapshotDTO workflowSnapshotToBeRemoved : workflowSnapshotsToBeRemoved) {
				TransactionWorkflowSnapshotKey transactionWorkflowSnapshotKey = new TransactionWorkflowSnapshotKey();
				transactionWorkflowSnapshotKey.setId(workflowSnapshotToBeRemoved.getTransactionWorkflowSnapshotId());
				TransactionWorkflowSnapshot transactionWorkflowSnapshot = (TransactionWorkflowSnapshot) session
						.load(TransactionWorkflowSnapshot.class, transactionWorkflowSnapshotKey);
				if (!session.contains(transactionWorkflowSnapshot)) {
					transactionWorkflowSnapshot = (TransactionWorkflowSnapshot) session
							.merge(transactionWorkflowSnapshot);
				}
				session.delete(transactionWorkflowSnapshot);
				workflowSnapshotObjectRemoveList.add(transactionWorkflowSnapshot);
			}
			session.update(transactionDomain);
			session.flush();
			session.fetchCurrentTransaction().commit();
			removePendingTransactionsForApprover(workflowSnapshotObjectRemoveList); // Checker Details
			transactionDomain = transactionDomain.readFromPersistentStore(transactionDomain.getKey());
			DataAccessManager.getManager().fetchCurrentSession().refresh(transactionDomain);
			transactionDomain = transactionDomain.read(transactionDomain.getKey());
			TransactionWorkflowSnapshotDTO transactionWorkflowSnapshotDTO = new TransactionWorkflowSnapshotDTO();
			transactionWorkflowSnapshotDTO.setTransactionDTO(transactionDTO);
			IAdapterFactory transactionWorkflowSnapshotAdapterFactory = AdapterFactoryConfigurator.getInstance()
					.getAdapterFactory(com.ofss.digx.common.constants.CommonAdapterFactoryConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER_FACTORY);
			ITransactionWorkflowSnapshotAdapter transactionWorkflowSnapshotAdapter = (ITransactionWorkflowSnapshotAdapter) transactionWorkflowSnapshotAdapterFactory
					.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER);
			TransactionWorkflowSnapshotListResponseDTO transactionWorkflowSnapshotListResponseDTO = transactionWorkflowSnapshotAdapter
					.search(transactionWorkflowSnapshotDTO);
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE,
						FORMATTER.formatMessage(
								"Transaction performAction Register Alert Call: EventId:'%s' in class %s", eventId,
								THIS_COMPONENT_NAME));
			}
			registerAlert(transactionDomain, eventId,
					transactionWorkflowSnapshotListResponseDTO.getTransactionWorkflowSnapshotDTOs());
			if (nextStatus.equals(ApprovalStatus.APPROVED)) {
				ThreadAttribute.set(ThreadAttribute.APPROVAL_STATUS, ApprovalStatus.APPROVED.toString());
				Object obj = transactionDomain.getTransactionSnapshot();
				if (obj != null) {
					transactionDomain.getTransactionApprovalHistory().add(0, transactionDomain.toTransactionHistory());
					transactionDomain.getProcessingDetails().setCurrentStep(ProcessingStep.EXECUTION);
					transactionDomain.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
					// store current transaction status.
					TransactionStatus transactionStatus = copyTransactionStatus();
					if (transactionDomain instanceof AmountAccountTransaction) {
						AmountAccountTransaction amountAccountTransaction = (AmountAccountTransaction) transactionDomain;
						if (amountAccountTransaction.getValueDate().isBefore(getCurrentDate(sessionContext)))
							sessionContext.setMaxValueDate(amountAccountTransaction.getValueDate()
									.plusDays(preferences.getPartyPreferencesDTOs().getGracePeriod()));
					}
					Serializable result = ServiceInvocationHelper.getInstance()
							.invokeService(transactionDomain.getServiceId(), obj.getClass(), sessionContext, obj);
					transactionActionResponse.setResult(result);
					analyseTransactionProcess(sessionContext, transactionDomain, null);
					// restore stored transaction status to replace the services
					// transaction status.
					restoreTransactionStatus(transactionStatus);
				}
			}
			session.beginTransaction();
			session.update(transactionDomain);
			session.fetchCurrentTransaction().commit();
            HthCRMApproval.committed(sessionContext, transactionDomain, String.valueOf(transactionActionDTO.getAction()));
			if (transactionDomain.getProcessingDetails().getCurrentStep().equals(ProcessingStep.EXECUTION)) {
				removeFromGracePeriodExpiryAlert(transactionDomain.getKey().getId());
			}
			transactionDTO = transactionAssembler.fromDomainObject(transactionDomain);
			transactionActionResponse.getTransactionAction().setTransactionDTO(transactionDTO);
			fetchTransactionStatus()
					.setInternalReferenceNumber(transactionActionDTO.getTransactionDTO().getTransactionId());
			transactionActionResponse.setStatus(buildStatus(fetchTransactionStatus()));
//			executor.postPerformAction(sessionContext, transactionActionDTO, transactionActionResponse);
		} catch (com.ofss.digx.infra.exceptions.Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exception from performAction() for TransactionActionDTO '%s' in class %s",
							transactionActionDTO, THIS_COMPONENT_NAME),
					e);
			fillTransactionStatus(fetchTransactionStatus(), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage(
							"RunTimeException from performAction() for TransactionActionDTO '%s' in class %s",
							transactionActionDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			DataAccessManager.getManager().closeSession(session);
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionActionResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Exiting from performAction() : TransactionActionResponse=%s", transactionActionResponse));
		}
		return transactionActionResponse;
	}

	private void validateTransactionBlackout(com.ofss.digx.framework.domain.transaction.Transaction transaction)
			throws Exception {
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(com.ofss.digx.common.constants.CommonAdapterFactoryConstants.TRANSACTION_BLACKOUT_ADAPTER_FACTORY);
		ITransactionBlackoutAdapter blackoutAdapter = (ITransactionBlackoutAdapter) adapterFactory
				.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.TRANSACTION_BLACKOUT_ADAPTER);
		List<Object> serviceParams = new ArrayList<>();
		serviceParams.add(ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT));
		serviceParams.add(transaction.getTransactionSnapshot());
		blackoutAdapter.checkTransactionBlackout(transaction.getTransactionName(), serviceParams);
	}

	private Date getCurrentDate(SessionContext sessionContext) {
		Date currentDate = null;
		String dateType = ConfigurationFactory.getInstance().getConfigurations(CommonConstants.BASE_CONFIG)
				.get(ApprovalConstants.DATE_TYPE_FOR_APPROVAL_VALUE_DATE_EXPIRY, null);
		if (dateType == null || dateType.equals(ApprovalConstants.DATE_TYPE_FOR_APPROVAL_VALUE_DATE_EXPIRY_WORKING)) {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "WORKING date would be used to return the current date.");
			}
			if (sessionContext == null || sessionContext.getPostingDateText() == null
					|| sessionContext.getPostingDateText().isEmpty()) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.log(Level.FINE,
							"Since session context or posting date in the session context was null or empty, the calendar date would be considered.");
				}
				currentDate = DateHelper.getInstance().getDateForTargetUnit();
			} else {
				currentDate = new Date(sessionContext.getPostingDateText());
			}
		} else {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "CALENDAR date would be used to return the current date.");
			}
			currentDate = DateHelper.getInstance().getDateForTargetUnit();
		}
		return currentDate;
	}

	private void removePendingTransactionsForApprover(
			List<TransactionWorkflowSnapshot> workflowSnapshotObjectRemoveList) {
		workflowSnapshotObjectRemoveList.forEach(transactionWorkflowSnapShot -> {
			try {
				ApprovalCheckerDetails requestObject = new ApprovalCheckerDetails();
				requestObject.setTransactionWorkflowSnapshot(transactionWorkflowSnapShot.getKey().getId());
				requestObject.setTransaction(transactionWorkflowSnapShot.getTransaction());
				ApprovalCheckerDetailsRepository repository = ApprovalCheckerDetailsRepository.getInstance();

				repository.delete(requestObject);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE,
						FORMATTER.formatMessage(
								"RunTimeException from removeEntryFromChildTable() for TransactionWorkflowSnapshot '%s' in class %s",
								workflowSnapshotObjectRemoveList, THIS_COMPONENT_NAME),
						e);
			}
		});
	}

	private TransactionStatus copyTransactionStatus() {
		TransactionStatus threadTransactionStatus = fetchTransactionStatus();
		TransactionStatus newTransactionStatus = new TransactionStatus();
		newTransactionStatus.setErrorCode(new String(threadTransactionStatus.getErrorCode()));
		if (threadTransactionStatus.getExtendedReply() != null) {
			newTransactionStatus.setExtendedReply(new ExtendedReply());
			ExtendedReply extendedReply = threadTransactionStatus.getExtendedReply();
			if (extendedReply.getMessages() != null && extendedReply.getMessages().length > 0) {
				ReplyMessage[] replyMessages = new ReplyMessage[extendedReply.getMessages().length];
				for (int i = 0; i < extendedReply.getMessages().length; i++) {
					replyMessages[i] = new ReplyMessage();
					replyMessages[i].setCode(extendedReply.getMessages()[i].getCode());
					replyMessages[i].setMessage(new String(extendedReply.getMessages()[i].getMessage()));
				}
				newTransactionStatus.getExtendedReply().setMessages(replyMessages);
			}
		}
		if (threadTransactionStatus.getExternalReferenceNo() != null)
			newTransactionStatus.setExternalReferenceNo(new String(threadTransactionStatus.getExternalReferenceNo()));
		if (threadTransactionStatus.getInternalReferenceNumber() != null)
			newTransactionStatus
					.setInternalReferenceNumber(new String(threadTransactionStatus.getInternalReferenceNumber()));
		newTransactionStatus.setIsOverriden(threadTransactionStatus.getIsOverriden());
		if (threadTransactionStatus.getMemo() != null)
			newTransactionStatus.setMemo(new String(threadTransactionStatus.getMemo()));
		if (threadTransactionStatus.getPostingDate() != null)
			newTransactionStatus.setPostingDate(new Date(threadTransactionStatus.getPostingDate().toString()));
		newTransactionStatus.setReplyCode(threadTransactionStatus.getReplyCode());
		if (threadTransactionStatus.getReplyText() != null)
			newTransactionStatus.setReplyText(new String(threadTransactionStatus.getReplyText()));
		if (threadTransactionStatus.getUserReferenceNumber() != null)
			newTransactionStatus.setUserReferenceNumber(new String(threadTransactionStatus.getUserReferenceNumber()));
		if (threadTransactionStatus.getValidationErrors() != null
				&& threadTransactionStatus.getValidationErrors().length > 0) {
			ValidationError[] errors = new ValidationError[threadTransactionStatus.getValidationErrors().length];
			newTransactionStatus.setValidationErrors(errors);
			int index = 0;
			for (ValidationError error : threadTransactionStatus.getValidationErrors()) {
				errors[index] = new ValidationError();
				if (error.getApplicableAttributes() != null)
					errors[index].setApplicableAttributes(new String(error.getApplicableAttributes()));
				if (error.getAttributeName() != null)
					errors[index].setAttributeName(new String(error.getAttributeName()));
				if (error.getAttributeValue() != null)
					errors[index].setAttributeValue(new String(error.getAttributeValue()));
				if (error.getErrorCode() != null)
					errors[index].setErrorCode(new String(error.getErrorCode()));
				if (error.getErrorMessage() != null)
					errors[index].setErrorMessage(new String(error.getErrorMessage()));
				if (error.getMethodName() != null)
					errors[index].setMethodName(new String(error.getMethodName()));
				if (error.getObjectName() != null)
					errors[index].setObjectName(new String(error.getObjectName()));
				errors[index].assignAssociatedSeverity(error.fetchAssociatedSeverity());
			}
		}
		return newTransactionStatus;
	}

	private void restoreTransactionStatus(TransactionStatus transactionStatus) {
		ThreadAttribute.set(ThreadAttribute.TRANSACTION_STATUS, transactionStatus);
	}

	public List<Map<String, String>> listPartyPendingTransactions(String partyId) throws Exception {
		CZLocalTransactionRepositoryAdapter localTransactionRepository = CZLocalTransactionRepositoryAdapter.getInstance();
		return localTransactionRepository.listPartyOtherPendingTransactions(partyId);
	}

	public void rejectOtherPendingTransaction(String transactionId) throws Exception {
		CZLocalTransactionRepositoryAdapter.getInstance().rejectOtherPendingTransaction(transactionId);;
	}

	/**
	 * @param sessionContext
	 * @param rejectTransactionMakerDTO
	 * @return
	 * @throws Exception
	 */
	public TransactionStatus editTxnMaker(SessionContext sessionContext,
			RejectTransactionMakerDTO rejectTransactionMakerDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Entered into rejectTxnMaker()  in class '%s'", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.rejectTxnMaker", sessionContext);
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = new TransactionStatus();
		try {

			String userId = sessionContext.getUserId();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("USER Id from session Context: " + userId);
			String makerFromDB = new String();
			String apprvStatus = new String();

			com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey key = new TransactionKey();
			key.setId(rejectTransactionMakerDTO.getTxnRefNo());
			transactionDomain.setKey(key);
			transactionDomain = transactionDomain.read(key);
			makerFromDB = transactionDomain.getApprovalDetails().getSignedBy().split("~")[1];
			apprvStatus = transactionDomain.getApprovalDetails().getStatus().toString();
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_EDIT_TXN", true);
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_EDIT_TXN_Code",
					transactionDomain.getTransactionName());
			com.ofss.digx.infra.thread.ThreadAttribute.set("MAKER_EDIT_TXN_ID",
					rejectTransactionMakerDTO.getTxnRefNo());

			if (makerFromDB.equalsIgnoreCase(userId) && (("PENDING_APPROVAL").equalsIgnoreCase(apprvStatus))) {
				ThreadAttribute.set("MAKER_EDIT_LAST_UPDATED_BY", userId);
				ThreadAttribute.set("MAKER_EDIT_MESSAGE", "EDITBYMAKER");
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before call to editTransaction");

				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In handleExpiredTransactions : Update Transaction Id : " + transactionDomain.getKey().getId()
								+ " TransactionName=" + transactionDomain.getTransactionName());

				adapter.editTransaction(transactionDomain.getKey().getId());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After call to editTransaction");
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TransactionStatus: " + transactionStatus.toString());
			}

		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from update()  in class %s", THIS_COMPONENT_NAME), rte);
			ThreadAttribute.set(ThreadAttribute.WORKFLOWS_EVALUATED, false);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, fetchTransactionStatus());
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Exiting from CZ update() : TransactionStatus=%s",
					fetchTransactionStatus()));
		}
		return transactionStatus;
	}

	/**
	 * @param sessionContext
	 * @param rejectedTransactionList
	 * @param userGroupDTO
	 * @return
	 * @throws Exception
	 */
	public TransactionStatus addNewUserInCheckerDetails(SessionContext sessionContext,
			List<com.ofss.digx.framework.domain.transaction.Transaction> rejectedTransactionList,
			UserGroupDTO userGroupDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method addNewUserInCheckerDetails() in Service in class %s", THIS_COMPONENT_NAME));
		}
		super.checkAccessPolicy(THIS_COMPONENT_NAME + ".handleRejectedTransactions", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In addNewUserInCheckerDetails : Number of Transactions for addition : "
					+ rejectedTransactionList.size());
			for (com.ofss.digx.framework.domain.transaction.Transaction txnObj : rejectedTransactionList) {
				IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
						.getAdapterFactory(CommonAdapterFactoryConstants.CZ_TRANSACTION_ADAPTER_FACTORY);
				ITransactionAdapter adapter = (ITransactionAdapter) adapterFactory
						.getAdapter(CommonAdapterConstants.CZ_TRANSACTION_ADAPTER);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In addNewUserInCheckerDetails : Update Transaction Id : " + txnObj.getKey().getId()
						+ " TransactionName=" + txnObj.getTransactionName() + " User Group Id : "
						+ userGroupDTO.getId());

				adapter.addNewUserToCheckerDetails(txnObj.getKey().getId(), userGroupDTO);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"In addNewUserInCheckerDetails  : Transaction Id : " + txnObj.getKey().getId() + " updated");
			}
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method addNewUserInCheckerDetails() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method addNewUserInCheckerDetails() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	/**
	 * This method fetches transaction details for the given transaction id.
	 * <p>
	 *
	 * @param sessionContext
	 *            The session context of request in the form of {@link SessionContext}.
	 * @param transactionDTO
	 *            of type {@link TransactionDTO} containing Transaction id for which the details are to be fetched.
	 * @param isDownload
	 *            true if request is for eReceipts download.
	 * @return
	 * @throws Exception
	 *             If transaction could not be fetched by system or there is an irrecoverable error condition then it
	 *             will throw exception with detailed message.
	 */
	@SuppressWarnings("rawtypes")
	@Entitlement(name = "Fetch Transaction Details", action = ActionType.PERFORM, requiredResources = {
			"com.ofss.digx.app.approval.service.transaction.Transaction.listTransactions",
			"com.ofss.digx.app.approval.service.transaction.Transaction.transactionsCount",
			"com.ofss.digx.app.fileupload.service.FileTemplate.read" })
	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Activity_log)
	public TransactionResponse read(SessionContext sessionContext, TransactionDTO transactionDTO, Boolean isDownload)
			throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered into read() : TransactionDTO=%s in class '%s'",
					transactionDTO, THIS_COMPONENT_NAME));
		}
		super.canonicalizeInput(transactionDTO);
		super.checkAccessPolicy("com.ofss.digx.app.approval.service.transaction.Transaction.read", sessionContext,
				transactionDTO, isDownload);
		TransactionResponse transactionResponse = new TransactionResponse();
		transactionResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		try {
			executor.preRead(sessionContext, transactionDTO, isDownload);
			AbstractBusinessPolicy abstractBusinessPolicy = null;

			BusinessPolicyFactory businessPolicyFactory = BusinessPolicyFactory.getInstance();
			ReadTransactionBusinessPolicyDTO transactionBusinessPolicyDTO = new ReadTransactionBusinessPolicyDTO();
			transactionBusinessPolicyDTO.setTransactionDTO(transactionDTO);
			transactionBusinessPolicyDTO.setIsDownload(isDownload);
			abstractBusinessPolicy = businessPolicyFactory.getBusinesPolicyInstance(
					"com.ofss.digx.app.approval.service.transaction.Transaction.read", transactionBusinessPolicyDTO);
			transactionDTO.validate(sessionContext);
			abstractBusinessPolicy.validate();

			if (sessionContext.getTransactingPartyCode() == null) {
				Class<?>[] bypassMasking = (Class<?>[]) com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.BYPASS_MASKING);
				List<Class<?>> bypassMaskingList = null;
				if (bypassMasking != null) {
					bypassMaskingList = new ArrayList<>(Arrays.asList(bypassMasking));
				} else {
					bypassMaskingList = new ArrayList<>();
				}
				if (!bypassMaskingList.contains(Account.class))
					bypassMaskingList.add(Account.class);
				if (!bypassMaskingList.contains(Party.class))
					bypassMaskingList.add(Party.class);
				com.ofss.digx.infra.thread.ThreadAttribute.set(
						com.ofss.digx.infra.thread.ThreadAttribute.BYPASS_MASKING,
						bypassMaskingList.toArray(new Class<?>[0]));
			}

			TransactionAssembler transactionAssembler = null;
			com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey transactionKey = new TransactionKey();
			transactionKey.setId(transactionDTO.getTransactionId());
			transaction = transaction.read(transactionKey);
			if (super.getExpandParamsList() != null && super.getExpandParamsList().size() > 0) {
				for (int i = 0; i < super.getExpandParamsList().size(); i++) {
					String expandParam = super.getExpandParamsList().get(i);
					if (expandParam.equals(TransactionExpandParamsConstants.HISTORY)) {
						transaction.getTransactionApprovalHistory();
					}
				}
			}
			TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			AbstractAssembler assembler = transactionAssemblerFactory
					.getAssemblerInstance(transaction.getDiscriminator());
			if (assembler instanceof TransactionAssembler) {
				transactionAssembler = (TransactionAssembler) assembler;
			}
			setReceiptFlag(transaction);
			TransactionDTO transactionDTOFromDB = transactionAssembler.fromDomainObject(transaction);
			if (transactionDTO != null) {
				// Adding CZ Constraint
				CZTransactionViewAccessCheckConstraint systemConstraint = new CZTransactionViewAccessCheckConstraint(
						transactionDTOFromDB);
				systemConstraint.isSatisfiedBy();
			}
			if ((isDownload != null && isDownload && transactionDTOFromDB.getTaskDTO() != null)
					|| transactionDTOFromDB.getServiceId().contains(GENERIC_SERVICE_ID)) {
				ITransactionSnapshotHandler transactionSnapshotHandler = TransactionSnapshotHandlerFactory.getInstance()
						.getTransactionSnapshotHandler(transactionDTOFromDB.getTaskDTO().getId());
				addPartyInformation(sessionContext, transactionDTOFromDB);
				if (transactionSnapshotHandler != null) {
					transactionDTOFromDB
							.setTransactionSnapshot(transactionSnapshotHandler.fetchTransaction(transactionDTOFromDB));
				}
			}
			// /////////////////////////////
			Date currentDate = getCurrentDate(sessionContext);
			currentDate = new Date(currentDate.toString("yyyyMMdd"));// Causing copy issue
			Boolean isGracePeriodSupported = false;
			if (transactionDTOFromDB instanceof AmountAccountTransactionDTO) {
				if (transactionDTOFromDB.getApprovalDetails().getStatus() == ApprovalStatus.PENDING_APPROVAL
						&& ((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate() != null) {
					IAdapterFactory adapterFactoryPartyPreference = AdapterFactoryConfigurator.getInstance()
							.getAdapterFactory(
									com.ofss.digx.common.constants.CommonAdapterFactoryConstants.PARTY_PREFERENCES_ADAPTER_FACTORY);
					IPartyPreferencesAdapter adapterPartyPreference = (IPartyPreferencesAdapter) adapterFactoryPartyPreference
							.getAdapter(
									com.ofss.digx.common.constants.CommonAdapterConstants.PARTY_PREFERENCES_ADAPTER);
					PartyPreferencesDTO partyPreferencesDTO = new PartyPreferencesDTO();
					Party party = new Party();
					party.setValue(sessionContext.getTransactingPartyCode());
					partyPreferencesDTO.setParty(party);
					PartyPreferencesResponse preferences = adapterPartyPreference.read(sessionContext,
							partyPreferencesDTO);
					if (transactionDTOFromDB.getTaskDTO().isAspectEnabled(TaskAspect.GRACE_PERIOD)) {
						isGracePeriodSupported = true;
					}
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("currentDate = " + currentDate);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
							"valueDate   = " + ((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate());
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("current > value = "
							+ currentDate.isAfter(((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate()));

					if (preferences != null && preferences.getPartyPreferencesDTOs() != null
							&& preferences.getPartyPreferencesDTOs().getGracePeriod() != null && isGracePeriodSupported
							&& ((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate()
									.isBefore(currentDate)) {
						((AmountAccountTransactionDTO) transactionDTOFromDB)
								.setMaxApprovalDate(((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate()
										.plusDays(preferences.getPartyPreferencesDTOs().getGracePeriod()));
						if (currentDate
								.isAfter(((AmountAccountTransactionDTO) transactionDTOFromDB).getMaxApprovalDate())) {
							transactionDTOFromDB.getApprovalDetails().setStatus(ApprovalStatus.EXPIRED);
						}
					} else {
						if (currentDate.isAfter(((AmountAccountTransactionDTO) transactionDTOFromDB).getValueDate())) {
							transactionDTOFromDB.getApprovalDetails().setStatus(ApprovalStatus.EXPIRED);
						}
					}
				}
			}
			// /////////////////////////////
			transactionDTOFromDB.setCreationDate(
					DateHelper.getInstance().getDateWithSystemTimeZone(transactionDTOFromDB.getCreationDate()));
			transactionDTOFromDB.setLastUpdatedDate(
					DateHelper.getInstance().getDateWithSystemTimeZone(transactionDTOFromDB.getLastUpdatedDate()));
			if (transactionDTOFromDB.getTransactionHistoryDTOs() != null
					&& transactionDTOFromDB.getTransactionHistoryDTOs().length > 0) {
				for (TransactionHistoryDTO transactionHistoryDTO : transactionDTOFromDB.getTransactionHistoryDTOs()) {
					transactionHistoryDTO.setLastUpdatedDate(DateHelper.getInstance()
							.getDateWithSystemTimeZone(transactionHistoryDTO.getLastUpdatedDate()));
				}
			}
			transactionResponse
					.setPendingModification(isTransactionPendingForModificationWithCurrentUser(transactionDTOFromDB));
			transactionResponse
					.setPendingApproval(isTransactionPendingForApprovalWithCurrentUser(transactionDTOFromDB));
			transactionResponse.setTransactionDTO(transactionDTOFromDB);
			executor.postRead(sessionContext, transactionDTO, isDownload, transactionResponse);
		} catch (com.ofss.digx.infra.exceptions.Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exception from read for TransactionDTO '%s' in class '%s'", transactionDTO,
							THIS_COMPONENT_NAME),
					e);
			fillTransactionStatus(fetchTransactionStatus(), e);
		} catch (RuntimeException rte) {
			fillTransactionStatus(fetchTransactionStatus(), rte);
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("RunTimeException from read for TransactionDTO '%s' in class '%s'",
							transactionDTO, THIS_COMPONENT_NAME),
					rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionResponse);
		super.canonicalizeInput(transactionResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage("Exiting from read() : TransactionResponse=%s", transactionResponse));
		}
		return transactionResponse;
	}

	private Boolean isTransactionPendingForModificationWithCurrentUser(TransactionDTO transactionDTOFromDB) {
		String userId = (String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME);
		return (transactionDTOFromDB.getApprovalDetails().getStatus() == ApprovalStatus.MODIFICATION_REQUESTED
				&& userId.equals(transactionDTOFromDB.getCreatedBy())) ? true : false;
	}

	private Boolean isTransactionPendingForApprovalWithCurrentUser(TransactionDTO transactionDTOFromDB) {

		Boolean isPending = false;
		String userId = (String) ThreadAttribute.get(ThreadAttribute.SUBJECTNAME);
		IAdapterFactory transactionWorkflowSnapshotAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(
						com.ofss.digx.common.constants.CommonAdapterFactoryConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER_FACTORY);
		ITransactionWorkflowSnapshotAdapter transactionWorkflowSnapshotAdapter = (ITransactionWorkflowSnapshotAdapter) transactionWorkflowSnapshotAdapterFactory
				.getAdapter(
						com.ofss.digx.common.constants.CommonAdapterConstants.TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER);
		TransactionWorkflowSnapshotListResponseDTO transactionWorkflowSnapshotListResponseDTO = null;
		try {
			TransactionWorkflowSnapshotDTO transactionWorkflowSnapshotDTO = new TransactionWorkflowSnapshotDTO();
			transactionWorkflowSnapshotDTO.setTransactionDTO(transactionDTOFromDB);
			transactionWorkflowSnapshotListResponseDTO = transactionWorkflowSnapshotAdapter
					.search(transactionWorkflowSnapshotDTO);
		} catch (Exception e) {
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.log(Level.INFO, "Workflow steps not found for transaction.", e);
			}
		}
		if (transactionWorkflowSnapshotListResponseDTO != null) {
			if (transactionWorkflowSnapshotListResponseDTO.getTransactionWorkflowSnapshotDTOs() != null
					&& !transactionWorkflowSnapshotListResponseDTO.getTransactionWorkflowSnapshotDTOs().isEmpty()) {
				for (TransactionWorkflowSnapshotDTO transactionWorkflowSnapshotDTO : transactionWorkflowSnapshotListResponseDTO
						.getTransactionWorkflowSnapshotDTOs()) {
					if (transactionWorkflowSnapshotDTO.getWorkflowStepDTO().getUserGroup().getUsers() != null
							&& !transactionWorkflowSnapshotDTO.getWorkflowStepDTO().getUserGroup().getUsers()
									.isEmpty()) {
						for (UserGroupUserDTO userGroupUserDTO : transactionWorkflowSnapshotDTO.getWorkflowStepDTO()
								.getUserGroup().getUsers()) {
							if (userGroupUserDTO.getUserId().equals(userId)) {
								isPending = true;
								break;
							}
						}
					}
				}
			}
		}

		return isPending;

	}
	/**
	 * Add party information to the transacion dto.
	 *
	 * @param sessionContext
	 * @param transaction
	 * @throws Exception
	 */
	private void addPartyInformation(SessionContext sessionContext, TransactionDTO transaction) throws Exception {
		UserPartyDetailsRequestDTO partyDetailsRequest = new UserPartyDetailsRequestDTO();
		String partyId = sessionContext.getTransactingPartyCode();
		String partyName = null;
		if (transaction instanceof AccountTransactionDTO) {
			partyId = ((AccountTransactionDTO) transaction).getAccountParty().getValue();
		} else if (transaction instanceof PartyTransactionDTO) {
			partyId = ((PartyTransactionDTO) transaction).getPartyId().getValue();
			partyName = ((PartyTransactionDTO) transaction).getPartyName().getFullName();
		}
		if (partyName == null) {
			partyDetailsRequest.setPartyId(partyId);
			IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.common.constants.CommonAdapterFactoryConstants.USER_PARTY_ADAPTER_FACTORY);
			IUserPartyAdapter adapter = (IUserPartyAdapter) adapterFactory
					.getAdapter(com.ofss.digx.common.constants.CommonAdapterConstants.USER_PARTY_ADAPTER);
			com.ofss.digx.app.party.dto.PartyResponse partyResponse = adapter.fetchParty(sessionContext,
					partyDetailsRequest);
			partyName = partyResponse.getParty().getPersonalDetails().getFullName();
		}
		transaction.setFullPartyName(partyName);
	}

	public TransactionStatus sendWebmailReminderToTDMode3Users(SessionContext sessionContext) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage(
					"Entered into method handleExpiredTransactions() in Service in class %s", THIS_COMPONENT_NAME));
		}
		//super.checkAccessPolicy(THIS_COMPONENT_NAME + ".sendWebmailReminderToTDMode3Users", sessionContext);
		TransactionListResponse transactionListResponse = new TransactionListResponse();
		transactionListResponse.setStatus(fetchStatus());
		Interaction.begin(sessionContext);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		com.ofss.fc.datatype.Date valueDate = null;
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("In sendWebmailReminderToTDMode3Users");
			com.ofss.digx.cz.bea.app.td.service.account.core.CZTermDeposit service = new com.ofss.digx.cz.bea.app.td.service.account.core.CZTermDeposit();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############Sending webmails to TDM3 VIP users");
			List<PreferentialTDM3WebmailDTO> userIdList = getUserCouponListMode3(sessionContext);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############After fetching UserId's for webmail");
			if (!userIdList.isEmpty()) {
				for (PreferentialTDM3WebmailDTO res : userIdList) {

					if (res.getCouponCode()!=null && res.getTdAcctNoValue()!=null) {
					boolean redeemedCheck = checkCouponRedeemed(res.getCouponCode(),res.getTdAcctNoValue());
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entered redeemedCheck, value is " + redeemedCheck);
					if (!redeemedCheck) {
						com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Coupon not Redeemed starting webmail steps");
					MailerDTO mailerDTO = new MailerDTO();
					if (null != res.getUserid()) {
						mailerDTO = createTDM3WebmailToUser(res, res.getUserid());
					}
					IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance()
							.getAdapterFactory("HOSTUSER_DETAILS_ADAPTER_FACTORY");
					IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory
							.getAdapter("HOST_USERDETAILS_INVOCATION_ADAPTER");

					ThreadAttribute.set(ThreadAttribute.ENTERPRISE_ROLE_ID, "administrator");
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("(String) ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID) :: " + (String) ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID));
					hostuserDetailsAdapter.createMailForStandaloneForTDM3(sessionContext, mailerDTO);
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############After Sending webmails to UserId's :: " + res.getUserid());
					}
					}

				}
			}
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("############After Sending webmails to all UserId's");
			transactionListResponse.setStatus(buildStatus(transactionStatus));
		} catch (java.lang.Exception rte) {
			fillTransactionStatus(transactionStatus, rte);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
					"RuntimeException from method handleExpiredTransactions() in Service  Input: TransactionRequestDTO: in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		super.checkResponsePolicy(sessionContext, transactionListResponse);
		super.canonicalizeInput(transactionListResponse);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE,
					FORMATTER.formatMessage(
							"Exiting from method handleExpiredTransactions() in Service Output : in class %s",
							THIS_COMPONENT_NAME));
		}
		return transactionStatus;

	}

	@SuppressWarnings("unused")
	private MailerDTO createTDM3WebmailToUser(PreferentialTDM3WebmailDTO preferentialTDDetailsDTO,String userid) {
		// Adding TDM2 and TDM3 coupon code webmail - Start
		MailerDTO mailerDTO = new MailerDTO();

		String emailSub2 = getMessageBody(BUNDLE_NAME_1, "MessageSubjectTDM2", LocaleUtils.getUserLocale(), "MessageSubjectTDM2");

		String emailBody2 = getMessageBody(BUNDLE_NAME_1, "MessageBodyTDM2", LocaleUtils.getUserLocale(), "MessageBodyTDM2");

		String emailSub3 = getMessageBody(BUNDLE_NAME_1, "MessageSubjectTDM3", LocaleUtils.getUserLocale(), "MessageSubjectTDM3");

		String emailBody3 = getMessageBody(BUNDLE_NAME_1, "MessageBodyTDM3", LocaleUtils.getUserLocale(), "MessageBodyTDM3");

		String deposit_type = "";
		String deposit_type_ch = "";
		String term_en = "";
		String term_tc = "";
		String currency_en = "";
		String currency_tc = "";
		String formatted_amt = "";
		String accountNo = "";
		String formatted_coupon_rate = "";
		String modeType = "";
		String promotion_number = "";
		String couponDueDateString = "";
		String systemdate = "";
		String td_account_number="";
		String coupon_effDt=preferentialTDDetailsDTO.getCouponEffectiveDate();
		String coupon_dueDt=preferentialTDDetailsDTO.getCouponDueDate();
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TD Account number : " + preferentialTDDetailsDTO.getTdAcctNoValue());
		String extacc = CZAccountHelper.int2extFullAccNo(preferentialTDDetailsDTO.getTdAcctNoValue());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TD External Account number : " + extacc);

		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
				com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);


		if (null != extacc) {
			accountNo = extacc.substring(0, 3) + "-" + extacc.substring(3, 6) + "-" + extacc.substring(6, 8) + "***"
					+ extacc.substring(11, 13) + "-" + extacc.substring(13);
		}
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TD Formatted Masked Account number : " + accountNo);

		if (preferentialTDDetailsDTO.getCouponRate() != null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Coupon Rate before formatting : " + preferentialTDDetailsDTO.getCouponRate());
			BigDecimal couponrate = preferentialTDDetailsDTO.getCouponRate();
			formatted_coupon_rate = String.format("%,.6f", couponrate);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Amount After formatting : " + formatted_coupon_rate);
		}

		if (preferentialTDDetailsDTO.getMinAmt() != null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Amount before formatting : " + preferentialTDDetailsDTO.getMinAmt());
			BigDecimal amtBD = preferentialTDDetailsDTO.getMinAmt();
			formatted_amt = String.format("%,.2f", amtBD);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Amount After formatting : " + formatted_amt);
		}

		if (null != preferentialTDDetailsDTO.getFlag()) {
			if ("Y".equalsIgnoreCase(preferentialTDDetailsDTO.getFlag())) {
				deposit_type = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE_GREEN",
						"DepositType in EN");
				deposit_type_ch = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE_GREEN_CH",
						"DepositType in TC");
			} else if ("N".equalsIgnoreCase(preferentialTDDetailsDTO.getFlag())) {
				deposit_type = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE", "DepositType in EN");
				deposit_type_ch = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE_CH",
						"DepositType in TC");
			} else if ("B".equalsIgnoreCase(preferentialTDDetailsDTO.getFlag())) {
				deposit_type = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE_TIME_GREEN", "Time deposit or Green Deposit in EN");
				deposit_type_ch = customConfigAdapter.getConfiguationDetails(
						com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "DEPOSIT_TYPE_TIME_GREEN_CH",
						"Time deposit or Green Deposit in TC");
			}
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Sending webmail to eligible user : " + userid);

		Date currentDate = new Date();
		systemdate = CZAccountHelper.fcDateToBeaRequestString(currentDate,"dd/MM/yyyy");

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Formatted current date for webmail suject: " + systemdate);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("coupon_dueDt : " + coupon_dueDt);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("coupon_effDt : " + coupon_effDt);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("deposit_type EN : " + deposit_type);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("deposit_type_ch TC : " + deposit_type_ch);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("term_en : EN." + preferentialTDDetailsDTO.getDepositTerm());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("term_tc : TC." + preferentialTDDetailsDTO.getDepositTerm());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("coupon_code : " + preferentialTDDetailsDTO.getCouponCode());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Amount before formatting : " + preferentialTDDetailsDTO.getMinAmt());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Amount After formatting : " + formatted_amt);

		java.util.Map<String, String> replacementValues_Subject = new java.util.HashMap<>();

		if (coupon_effDt != null) {
			replacementValues_Subject.put("coupon_effDt", coupon_effDt);
		} else {
			replacementValues_Subject.put("coupon_effDt", "");
		}

		if (coupon_dueDt != null) {
			replacementValues_Subject.put("coupon_dueDt", coupon_dueDt);
		} else {
			replacementValues_Subject.put("coupon_dueDt", "");
		}

		java.util.Map<String, String> replacementValues = new java.util.HashMap<>();

		if (coupon_effDt != null) {
			replacementValues.put("coupon_effDt", coupon_effDt);
		} else {
			replacementValues.put("coupon_effDt", "");
		}

		if (coupon_dueDt != null) {
			replacementValues.put("coupon_dueDt", coupon_dueDt);
		} else {
			replacementValues.put("coupon_dueDt", "");
		}

		if (formatted_coupon_rate != null) {
			replacementValues.put("formatted_coupon_rate", formatted_coupon_rate);
		} else {
			replacementValues.put("formatted_coupon_rate", "");
		}
		if (accountNo != null) {
			replacementValues.put("accountNo", accountNo);
		} else {
			replacementValues.put("accountNo", "");
		}

		if (deposit_type != null) {
			replacementValues.put("deposit_type", deposit_type);
			replacementValues.put("deposit_type_ch", deposit_type_ch);
		} else {
			replacementValues.put("deposit_type", "Time Deposit");
			replacementValues.put("deposit_type_ch", "Time Deposit");
		}

		if (preferentialTDDetailsDTO.getDepositCurrency() != null) {
			currency_en = CZCommonUtils.getStringISO("resources.nls.CZTDM1_Currency",
					"EN." + preferentialTDDetailsDTO.getDepositCurrency());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Currency in EN=" + preferentialTDDetailsDTO.getDepositCurrency());
			replacementValues.put("currency_en", currency_en);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Currency in EN map=" + currency_en);

			currency_tc = CZCommonUtils.getStringISO("resources.nls.CZTDM1_Currency",
					"TC." + preferentialTDDetailsDTO.getDepositCurrency());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Currency in TC =" + preferentialTDDetailsDTO.getDepositCurrency());
			replacementValues.put("currency_tc", currency_tc);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Currency in TC map=" + currency_tc);

		} else {
			replacementValues.put("currency_en", preferentialTDDetailsDTO.getDepositCurrency());
			replacementValues.put("currency_tc", preferentialTDDetailsDTO.getDepositCurrency());

		}

		if (formatted_amt != null) {
			replacementValues.put("formatted_amt", formatted_amt);
		} else {
			replacementValues.put("formatted_amt", "");
		}

		if (preferentialTDDetailsDTO.getDepositTerm() != null) {
			term_en = CZCommonUtils.getStringISO("resources.nls.CZTDM1_Term",
					"EN." + preferentialTDDetailsDTO.getDepositTerm());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Term in EN=" + preferentialTDDetailsDTO.getDepositTerm());
			replacementValues.put("term_en", term_en);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Term in EN map=" + term_en);

			term_tc = CZCommonUtils.getStringISO("resources.nls.CZTDM1_Term",
					"TC." + preferentialTDDetailsDTO.getDepositTerm());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Term in TC =" + preferentialTDDetailsDTO.getDepositTerm());
			replacementValues.put("term_tc", term_tc);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZTD Term in TC map=" + term_tc);

		} else {
			replacementValues.put("term_en", "");
			replacementValues.put("term_tc", "");
		}
		if (preferentialTDDetailsDTO.getCouponCode() != null) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Coupon Code in UpperCase : "+ preferentialTDDetailsDTO.getCouponCode().toUpperCase());
			replacementValues.put("coupon_code", preferentialTDDetailsDTO.getCouponCode().toUpperCase());
		} else {
			replacementValues.put("coupon_code", "");
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TD Debit account number before formatting : " + preferentialTDDetailsDTO.getTdAcctNoValue());
		if (preferentialTDDetailsDTO.getTdAcctNoValue() != null) {
			td_account_number = preferentialTDDetailsDTO.getTdAcctNoValue();

			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("TD Debit account number before replacing in webmail : " + td_account_number);
			replacementValues.put("td_account_number", td_account_number);
		}

		if (preferentialTDDetailsDTO.getPromotionNumber() != null)
			promotion_number = preferentialTDDetailsDTO.getPromotionNumber();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Current promotion_number : " + promotion_number);
			replacementValues.put("promotion_number", promotion_number);
		if (preferentialTDDetailsDTO.getModeType() != null) {
			modeType = preferentialTDDetailsDTO.getModeType();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Current Mode Type : " + modeType);
			replacementValues.put("modetype", modeType);
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final currency_en : " + currency_en);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final currency_tc : " + currency_tc);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final coupon_dueDt : " + coupon_dueDt);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final coupon_effDt : " + coupon_effDt);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final deposit_type : " + deposit_type);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final deposit_type_tc : " + deposit_type_ch);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final td account number accountNo : " + accountNo);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final td_account_number : " + td_account_number);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final term_en : " + term_en);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final term_tc : " + term_tc);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final coupon_code : " + preferentialTDDetailsDTO.getCouponCode());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final Amount before formatting : " + preferentialTDDetailsDTO.getMinAmt());
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Final Amount After formatting : " + formatted_amt);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" Before replacing values TD mode 2 Webmail Body : " + emailBody2);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before replacing values TD mode 2 Webmail subject : " + emailSub2);

		// Replace placeholders with actual values
		for (java.util.Map.Entry<String, String> entry : replacementValues.entrySet()) {
			if (entry.getValue() != null) {
				emailBody2 = emailBody2.replace("#" + entry.getKey() + "#", entry.getValue());
			}
		}

		for (java.util.Map.Entry<String, String> entry : replacementValues_Subject.entrySet()) {
			if (entry.getValue() != null) {
				emailSub2 = emailSub2.replace("#" + entry.getKey() + "#", entry.getValue());
			}
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After replacing values TD mode 2 Webmail Body : " + emailBody2);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After replacing values TD mode 2 Webmail subject : " + emailSub2);

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(" Before replacing values TD mode 3 Webmail Body : " + emailBody3);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Before replacing values TD mode 3 Webmail subject : " + emailSub3);

		for (java.util.Map.Entry<String, String> entry : replacementValues.entrySet()) {
			if (entry.getValue() != null) {
				emailBody3 = emailBody3.replace("#" + entry.getKey() + "#", entry.getValue());
			}
		}

		for (java.util.Map.Entry<String, String> entry : replacementValues_Subject.entrySet()) {
			if (entry.getValue() != null) {
				emailSub3 = emailSub3.replace("#" + entry.getKey() + "#", entry.getValue());
			}
		}

		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After replacing values TD mode 3 Webmail Body : " + emailBody3);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("After replacing values TD mode 3 Webmail subject : " + emailSub3);

		if (modeType != null) {
			if (modeType.equalsIgnoreCase("3")) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("################Webmail will be sent to TD mode 3 user");
				Calendar calendar = Calendar.getInstance();

				String generatedId = null;
				IdGenerator secureGenerator = (IdGenerator) AbstractGeneratorFactory.getUniqueInstance()
						.getIdGenerator("Mailer", "TDM3Coupon");
				try {
					generatedId = secureGenerator.generateId("Mailer", "TDM3Coupon", "", -1,
							new HashMap<String, Object>());
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generated Id for Mail : " + generatedId);
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				}

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Activation date of mail" + calendar.getTime());

				mailerDTO.setActivationDate(new Date(calendar.getTime()));
				mailerDTO.setExpiryDate(new Date("20991201"));
				mailerDTO.setBannerBody("");
				mailerDTO.setCode("TDM3_" + generatedId);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generated ID for webmail " + generatedId);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"Code for mail " + mailerDTO.getCode() + " Expiry Date : " + mailerDTO.getExpiryDate());
				mailerDTO.setDescription("TDM3 Coupon Webmail");
				mailerDTO.setMessageBody(emailBody3);
				mailerDTO.setSubject(emailSub3);
				mailerDTO.setPriority(PriorityType.HIGH);
				mailerDTO.setMessageType(com.ofss.digx.enumeration.collaboration.mailbox.MessageType.BULLETIN);
				mailerDTO.setTriggerType(TriggerType.MANUAL);
				List<RecipientDTO> recipientList = new ArrayList<>();
				RecipientDTO recipient;
				recipient = new RecipientDTO();
				recipient.setType(RecipientType.USER);
				recipient.setValue(userid);
				recipientList.add(recipient);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Successfully added users in recepient TDM3:: " + userid);

				mailerDTO.setRecipients(recipientList);
			} else if (modeType.equalsIgnoreCase("2")) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##############Webmail will be sent to TD mode 2 user");

				Calendar calendar = Calendar.getInstance();

				String generatedId = null;
				IdGenerator secureGenerator = (IdGenerator) AbstractGeneratorFactory.getUniqueInstance()
						.getIdGenerator("Mailer", "TDM2Coupon");
				try {
					generatedId = secureGenerator.generateId("Mailer", "TDM2Coupon", "", -1,
							new HashMap<String, Object>());
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generated Id for Mail : " + generatedId);
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
				}

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Activation date of mail" + calendar.getTime());

				mailerDTO.setActivationDate(new Date(calendar.getTime()));
				mailerDTO.setExpiryDate(new Date("20991201"));
				mailerDTO.setBannerBody("");
				mailerDTO.setCode("TDM2_" + generatedId);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Generated ID for webmail " + generatedId);

				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println(
						"Code for mail " + mailerDTO.getCode() + " Expiry Date : " + mailerDTO.getExpiryDate());
				mailerDTO.setDescription("TDM2 Coupon Webmail");
				mailerDTO.setMessageBody(emailBody3);
				mailerDTO.setSubject(emailSub3);
				mailerDTO.setPriority(PriorityType.HIGH);
				mailerDTO.setMessageType(com.ofss.digx.enumeration.collaboration.mailbox.MessageType.BULLETIN);
				mailerDTO.setTriggerType(TriggerType.MANUAL);
				List<RecipientDTO> recipientList = new ArrayList<>();
				RecipientDTO recipient;
				recipient = new RecipientDTO();
				recipient.setType(RecipientType.USER);
				recipient.setValue(userid);
				recipientList.add(recipient);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Successfully added users in recepient TDM2 :: " + userid);

				mailerDTO.setRecipients(recipientList);

			}else {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("######Webmail will not be sent to any user######");
			}
		}
		return mailerDTO;
	}

	private List<PreferentialTDM3WebmailDTO> getUserCouponListMode3(SessionContext sessionContext) throws Exception {
		List<PreferentialTDM3WebmailDTO> userId = null;
		Interaction.begin(sessionContext);
		com.ofss.digx.cz.bea.domain.td.entity.preferentialtd.PreferentialTDDetails domain = new com.ofss.digx.cz.bea.domain.td.entity.preferentialtd.PreferentialTDDetails();
		try {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Fetching UserId to send Webmail");
			userId = domain.getUserCouponListMode3(sessionContext);
			//com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("UserId list " + userId.toString());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE,
					FORMATTER.formatMessage("Exception from list for webmail '%s' in class %s", THIS_COMPONENT_NAME),
					e);
		} catch (RuntimeException rte) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("RunTime Exception from list for webmail '%s' in class %s",
					THIS_COMPONENT_NAME), rte);
		} finally {
			Interaction.close();
		}
		return userId;
	}

	private String getMessageBody(String bundleName, String key, Locale locale, String originalRoleName) {
		if (locale == null && ThreadAttribute.get(USER_LOCALE) != null)
			locale = Locale.forLanguageTag(ThreadAttribute.get(USER_LOCALE).toString());

		try {
			ResourceBundle bundle = ResourceBundle.getBundle(bundleName,
					(locale != null) ? locale : LocaleUtils.getDefaultLocale());
			String value = bundle.getString(key);
			String convertedString = new String(value.getBytes("ISO-8859-1"), "UTF-8");
			return convertedString;
		} catch (MissingResourceException e) {
			if (LOGGER.isLoggable(Level.FINE))
				LOGGER.log(Level.FINE, FORMATTER.formatMessage("No resource bundle found for bundle %s and key %s",
						new Object[] { bundleName, key }), e);

		} catch (UnsupportedEncodingException e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		}
		return originalRoleName;
	}

	public boolean checkCouponRedeemed(String couponCode, String tdAccountNo) {
//		Coupon code has been redeemed. Please click �Select coupon code� icon and use another coupon code (if any) to try again.

		String query = "select TXN_ID,PROCESSING_STATUS,COUPON_CODE,TD_ACCOUNT_NUMBER, APPR_STATUS from DIGX_CZ_TD_COUPON_TXN_DETAILS_M2 dep,digx_ap_transaction txn"
				+ " where dep.cm_txn_id = txn.txn_id and coupon_code = ? and td_account_number = ? and processing_status='S' and appr_status <> 'EXPIRED'";
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed query "+query);

		ArrayList<String> l_args = new ArrayList<String>();
		Connection l_con = null;
		JDBCResultSet l_rs = null;
		String l_datasrc = "DIGX";
		boolean flag = false;

		try {
			l_args.add(couponCode);
			l_args.add(tdAccountNo);

			l_con = ConnectionUtil.getConnection(l_datasrc, null);
			l_rs = JDBCEngine.executeQuery(query, l_args.size(), l_args, l_con);
//			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed JDBCEngine query "+JFGlobalFunctions.getMergedQuery(query, l_args));

			while (l_rs.next()) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed TXN_ID "+l_rs.getString("TXN_ID"));
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed PROCESSING_STATUS "+l_rs.getString("PROCESSING_STATUS"));
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed COUPON_CODE "+l_rs.getString("COUPON_CODE"));
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("checkCouponRedeemed TD_ACCOUNT_NUMBER "+l_rs.getString("TD_ACCOUNT_NUMBER"));
				flag = true;
				break;
			}
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Transaction.java checkCouponRedeemed couponCode ==>"+ couponCode+" tdAccountNo ==> "+tdAccountNo+" flag ==> "+flag);

		} catch (java.lang.Exception e) {
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
		} finally {
			ConnectionUtil.closeConnection(l_datasrc, l_con);
			l_con = null;
			l_args.clear();
			l_args = null;
		}

		return flag;
	}

    @Override
    public List<Object[]> listChangeSignerPinPendingTransactions(String partyId) throws Exception {
        CZLocalTransactionRepositoryAdapter localTransactionRepository = CZLocalTransactionRepositoryAdapter.getInstance();
        return localTransactionRepository.listChangeSignerPinPendingTransactions(partyId);
    }


	@Override
	@Entitlement(name = "Bcm Approve", action = ActionType.APPROVE, requiredResources = { "" })
	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Approvals)
	@Task(id = "BCM_PA_APT", parent = "APT", name = "Perform Action", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.APPROVALS, aspects = {
			TaskAspect.AUDIT }, type = TaskType.FINANCIAL_TRANSACTION)
	public TransactionActionResponse bcmApprove(SessionContext sessionContext, TransactionActionDTO transactionActionDTO, BcmApproveRequestDTO requestDTO) throws Exception {
		LOGGER.log(Level.INFO,
				FORMATTER.formatMessage("Entered into bcmApprove() : TransactionActionDTO=%s in class %s ",
						transactionActionDTO, THIS_COMPONENT_NAME));

		Interaction.begin(sessionContext);
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		transactionActionResponse.setStatus(fetchStatus());

		String serialNumber = requestDTO.getSerialNumber();
		String sequenceNumber = requestDTO.getSequenceNumber();
		ITokenDTO iTokenDTO = ITKTokensRepository.getInstance().readByNumberPair(serialNumber, sequenceNumber);
		if (iTokenDTO != null && !InputValidationUtils.isNullOrBlankTrim(iTokenDTO.getUserId())) {
			String userId = iTokenDTO.getUserId();

			sessionContext.setUserId(userId);
			sessionContext.setTransactingPartyCode(userId.split("@")[1]);
			sessionContext.setBankCode("015");
			sessionContext.setTransactionBranch("001");

			Subject subject = new Subject();
			subject.getPrincipals().add(new UserPrincipal((String) userId));
			subject.getPrincipals().add(new RolePrincipal((String) "corporateuser"));
			ThreadAttribute.set(ThreadAttribute.SUBJECTNAME, userId);
			ThreadAttribute.set(ThreadAttribute.SUBJECT, subject);
			com.ofss.digx.infra.thread.ThreadAttribute.set(ThreadAttribute.SUBJECTNAME, userId);
			com.ofss.digx.infra.thread.ThreadAttribute.set(ThreadAttribute.SUBJECT, subject);

			AccessPointDTO accessPoint = new AccessPointDTO();
			accessPoint.setId("APMOBRESP");
			accessPoint.setType(AccessPointType.INTERNAL);
			accessPoint.setStatus(AccessPointStatus.ENABLED);
			accessPoint.setHeadlessMode(Boolean.TRUE);
			accessPoint.setDefaultSelect(Boolean.TRUE);
			accessPoint.setSelfOnboard(Boolean.FALSE);
			accessPoint.setSkipLoginFlow(Boolean.TRUE);
			accessPoint.setPwdEncryptionEnabled(Boolean.FALSE);
			accessPoint.setConsentRequired(Boolean.FALSE);
			accessPoint.setScopes(new ArrayList<>());
			HashSet<AccessPointAspectDTO> accessPointAspectDTOS = new HashSet<>();
			AccessPointAspectDTO accessPointAspectDTO = new AccessPointAspectDTO();
			accessPointAspectDTO.setTaskAspect(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			accessPointAspectDTO.setEnabled(Boolean.TRUE);
			accessPointAspectDTOS.add(accessPointAspectDTO);
			accessPoint.setAspects(accessPointAspectDTOS);
			com.ofss.digx.infra.thread.ThreadAttribute
					.set(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT, accessPoint);

			com.ofss.digx.infra.thread.ThreadAttribute.set(
					com.ofss.digx.infra.thread.ThreadAttribute.CURRENT_TARGET_UNIT,
					"OBDX_BU");
			ThreadAttribute.set(ThreadAttribute.CHANNEL_ID, "IB");
			ThreadAttribute.set(ThreadAttribute.BUSINESS_UNIT_CODE, "OBDX_BU");
			com.ofss.digx.infra.thread.ThreadAttribute.set("isAdmin", false);
			ThreadAttribute.set(ThreadAttribute.SECURITY_INTERACTION, true);
			ThreadAttribute.set("IS_BCM_APPROVE", true);

			AccessControlContext context = AccessController.getContext();
			String enterpriseRoleId = "corporateuser";
			ThreadAttribute.set(ThreadAttribute.ENTERPRISE_ROLE_ID, enterpriseRoleId);

			try {
				transactionActionResponse = Subject.doAsPrivileged(subject, (PrivilegedAction<TransactionActionResponse>) () -> {
					try {
						return performActionWithoutSession(sessionContext, transactionActionDTO);
					} catch (Exception e) {
						throw new RuntimeException(e.getMessage(), e);
					}
				}, context);
			} catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("BCM Approve RuntimeException"),e.getCause());
				throw (Exception) e.getCause();
			} finally {
				Interaction.close();
			}
		}
		return transactionActionResponse;
	}

	@Override
	@Entitlement(name = "Bcm Bulk Approve", action = ActionType.APPROVE, requiredResources = { "" })
	@EntitlementGroup(category = EntitlementCategory.CUSTOMER_SERVICING, subCategory = EntitlementSubCategory.Approvals)
	@Task(id = "BCM_PA_APT", parent = "APT", name = "Perform Action", supportedAccountTypes = {}, executable = true, moduleType = ModuleType.APPROVALS, aspects = {
			TaskAspect.AUDIT }, type = TaskType.FINANCIAL_TRANSACTION)
	public TransactionActionResponse bcmBulkApprove(SessionContext sessionContext,
													BcmMultiApprovalTransactionListDTO multiApprovalTransactionListDTO) throws java.lang.Exception {
		LOGGER.log(Level.FINE,
				FORMATTER.formatMessage("Entered into bcmBulkApprove() : MultiApprovalTransactionListDTO=%s in class %s ",
						multiApprovalTransactionListDTO, THIS_COMPONENT_NAME));

		Interaction.begin(sessionContext);
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		transactionActionResponse.setStatus(fetchStatus());

		String serialNumber = multiApprovalTransactionListDTO.getSerialNumber();
		String sequenceNumber = multiApprovalTransactionListDTO.getSequenceNumber();
		ITokenDTO iTokenDTO = ITKTokensRepository.getInstance().readByNumberPair(serialNumber, sequenceNumber);
		if (iTokenDTO != null && !InputValidationUtils.isNullOrBlankTrim(iTokenDTO.getUserId())) {
			String userId = iTokenDTO.getUserId();

			sessionContext.setUserId(userId);
			sessionContext.setTransactingPartyCode(userId.split("@")[1]);
			sessionContext.setBankCode("015");
			sessionContext.setTransactionBranch("001");

			Subject subject = new Subject();
			subject.getPrincipals().add(new UserPrincipal((String) userId));
			subject.getPrincipals().add(new RolePrincipal((String) "corporateuser"));
			ThreadAttribute.set(ThreadAttribute.SUBJECTNAME, userId);
			ThreadAttribute.set(ThreadAttribute.SUBJECT, subject);

			AccessPointDTO accessPoint = new AccessPointDTO();
			accessPoint.setId("APMOBRESP");
			accessPoint.setType(AccessPointType.INTERNAL);
			accessPoint.setStatus(AccessPointStatus.ENABLED);
			accessPoint.setHeadlessMode(Boolean.TRUE);
			accessPoint.setDefaultSelect(Boolean.TRUE);
			accessPoint.setSelfOnboard(Boolean.FALSE);
			accessPoint.setSkipLoginFlow(Boolean.TRUE);
			accessPoint.setPwdEncryptionEnabled(Boolean.FALSE);
			accessPoint.setConsentRequired(Boolean.FALSE);
			accessPoint.setScopes(new ArrayList<>());
			HashSet<AccessPointAspectDTO> accessPointAspectDTOS = new HashSet<>();
			AccessPointAspectDTO accessPointAspectDTO = new AccessPointAspectDTO();
			accessPointAspectDTO.setTaskAspect(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			accessPointAspectDTO.setEnabled(Boolean.TRUE);
			accessPointAspectDTOS.add(accessPointAspectDTO);
			accessPoint.setAspects(accessPointAspectDTOS);
			com.ofss.digx.infra.thread.ThreadAttribute
					.set(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT, accessPoint);

			com.ofss.digx.infra.thread.ThreadAttribute.set(
					com.ofss.digx.infra.thread.ThreadAttribute.CURRENT_TARGET_UNIT,
					"OBDX_BU");
			ThreadAttribute.set(ThreadAttribute.CHANNEL_ID, "IB");
			ThreadAttribute.set(ThreadAttribute.BUSINESS_UNIT_CODE, "OBDX_BU");
			com.ofss.digx.infra.thread.ThreadAttribute.set("isAdmin", false);
			ThreadAttribute.set(ThreadAttribute.SECURITY_INTERACTION, true);
			ThreadAttribute.set("IS_BCM_APPROVE", true);

			AccessControlContext context = AccessController.getContext();
			String enterpriseRoleId = "corporateuser";
			ThreadAttribute.set(ThreadAttribute.ENTERPRISE_ROLE_ID, enterpriseRoleId);

			try {
				transactionActionResponse = Subject.doAsPrivileged(subject, (PrivilegedAction<TransactionActionResponse>) () -> {
					try {
						MultiApprovalTransactionListDTO requestDTO = new MultiApprovalTransactionListDTO();
						requestDTO.setMultiApprovalTransactionList(multiApprovalTransactionListDTO.getMultiApprovalTransactionList());
						return performAction(sessionContext, requestDTO);
					} catch (Exception e) {
						throw new RuntimeException(e.getMessage(), e);
					}
				}, context);
			} catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("BCM Approve RuntimeException"),e.getCause());
				throw (Exception) e.getCause();
			}
		}

		return transactionActionResponse;
	}

    @Override
    public BcmTxnMappingResponseDTO checkTFAResult(SessionContext sessionContext, BcmTxnMappingRequestDTO requestDTO) throws java.lang.Exception {
        Interaction.begin(sessionContext);
        BcmTxnMappingResponseDTO responseDTO =  new BcmTxnMappingResponseDTO();

        super.checkAccessPolicy(THIS_COMPONENT_NAME + ".checkTFAResult", sessionContext);
        responseDTO.setStatus(fetchStatus());
        try {
            String status = getApTfaMappingStatus(requestDTO.getTfaId());
            responseDTO.setTxnTfaMappingStatus(status);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("BCM checkTFAResult RuntimeException"),e.getCause());
            fillTransactionStatus(fetchTransactionStatus(), e.getCause());
        } catch (java.lang.Exception e) {
            LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("BCM checkTFAResult Exception"),e);
            fillTransactionStatus(fetchTransactionStatus(), e);
        } finally {
            Interaction.close();
        }
        super.checkResponsePolicy(sessionContext, responseDTO);
        return responseDTO;
    }

    private String getApTfaMappingStatus(String tfaId) {
        String query = "SELECT STATUS FROM DIGX_CZ_AP_TXN_TFA_MAPPING WHERE TFA_ID = ? ";
        ArrayList<String> listArgs = new ArrayList<String>();
        Connection connection = null;
        JDBCResultSet resultSet = null;
        String dataSrc = "DIGX";
        String status = null;
        try {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getApTfaMappingStatus TFA_ID" + tfaId);
            listArgs.add(tfaId);
            connection = ConnectionUtil.getConnection(dataSrc, null);
            resultSet = JDBCEngine.executeQuery(query, listArgs.size(), listArgs, connection);
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getApTfaMappingStatus resultSet:" + SerializationUtils.toJsonString(resultSet));
            if (resultSet.next()) {
                com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("getApTfaMappingStatus found entry");
                status = resultSet.getString("STATUS");
            }
        } catch (java.lang.Exception e) {
            com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
        } finally {
            ConnectionUtil.closeConnection(dataSrc, connection);
            connection = null;
            listArgs.clear();
            listArgs = null;
        }
        return status;
    }

    public TransactionActionResponse performActionWithoutSession(SessionContext sessionContext, TransactionActionDTO transactionActionDTO) throws Exception {
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.log(Level.FINE, FORMATTER.formatMessage("Entered into performAction() : TransactionActionDTO=%s in class %s ", new Object[]{transactionActionDTO, THIS_COMPONENT_NAME}));
		}
		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.bcmApprove", new Object[] { sessionContext, transactionActionDTO });
		super.canonicalizeInput(transactionActionDTO);

		com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = null;
		TransactionActionResponse transactionActionResponse = new TransactionActionResponse();
		transactionActionResponse.setStatus(this.fetchStatus());
		AbstractBusinessPolicy abstractBusinessPolicy = null;
		Interaction.begin(sessionContext);
		Session session = null;
		TransactionAssembler transactionAssembler = null;

		try {
			this.executor.prePerformAction(sessionContext, transactionActionDTO);
			BusinessPolicyFactory businessPolicyFactory = BusinessPolicyFactory.getInstance();
			String eventId = null;
			String remarks = transactionActionDTO.getTransactionDTO().getApprovalDetails().getRemarks();
			com.ofss.fc.infra.thread.ThreadAttribute.set("TRANSACTION_REFERENCE_NO", transactionActionDTO.getTransactionDTO().getTransactionId());
			com.ofss.digx.infra.thread.ThreadAttribute.set("TRANSACTION_REFERENCE_NO", transactionActionDTO.getTransactionDTO().getTransactionId());
			transactionActionResponse.setTransactionAction(transactionActionDTO);
			transactionActionDTO.validate(sessionContext);
			
			TransactionKey transactionKey = new TransactionKey();
			transactionKey.setId(transactionActionDTO.getTransactionDTO().getTransactionId());
			session = DataAccessManager.getManager().openNewSession("NONXA");
			session.beginTransaction();
			transactionDomain = (com.ofss.digx.framework.domain.transaction.Transaction)session.get(com.ofss.digx.framework.domain.transaction.Transaction.class, transactionKey);
			ApprovalType approvalType = transactionDomain.getApprovalDetails().getApprovalType();
			PartyPreferencesResponse preferences = null;
			if (sessionContext.getTransactingPartyCode() != null) {
				IAdapterFactory adapterFactoryPartyPreference = AdapterFactoryConfigurator.getInstance().getAdapterFactory("PARTY_PREFERENCES_ADAPTER_FACTORY");
				IPartyPreferencesAdapter adapterPartyPreference = (IPartyPreferencesAdapter)adapterFactoryPartyPreference.getAdapter("PARTY_PREFERENCES_ADAPTER");
				PartyPreferencesDTO partyPreferencesDTO = new PartyPreferencesDTO();
				Party party = new Party();
				party.setValue(sessionContext.getTransactingPartyCode());
				partyPreferencesDTO.setParty(party);
				preferences = adapterPartyPreference.read(sessionContext, partyPreferencesDTO);
			}
			
			TransactionAssemblerFactory transactionAssemblerFactory = TransactionAssemblerFactory.getInstance();
			transactionAssembler = transactionAssemblerFactory.getAssemblerInstance(transactionDomain.getDiscriminator());
			TransactionDTO transactionDTO = transactionAssembler.fromDomainObject(transactionDomain);
			transactionActionDTO.setTransactionDTO(transactionDTO);

			boolean isOmbFlow = (transactionDomain.getCreatedBy() != null && transactionDomain.getCreatedBy().equalsIgnoreCase("OFSSUser"))
					&& (transactionDomain.getLastUpdatedBy() != null && transactionDomain.getLastUpdatedBy().equalsIgnoreCase(getLoggedInUserId()))
					&& (transactionDomain.getApprovalDetails() != null && transactionDomain.getApprovalDetails().getStatus() != null && transactionDomain.getApprovalDetails().getStatus().equals(ApprovalStatus.EXPIRED));
			if (!isOmbFlow) {
				PerformActionSystemConstraint peformActionSystemConstraint = new PerformActionSystemConstraint(transactionActionDTO);
				peformActionSystemConstraint.isSatisfiedBy();
			}

			CZTransactionApprovalAccessCheckConstraint systemConstraint = new CZTransactionApprovalAccessCheckConstraint(transactionDTO);
			systemConstraint.isSatisfiedBy();
			TransactionBusinessPolicyDTO transactionBusinessPolicyDTO = new TransactionBusinessPolicyDTO();
			transactionBusinessPolicyDTO.setTransactionDTO(transactionDTO);
			transactionBusinessPolicyDTO.setAction(transactionActionDTO.getAction());
			transactionBusinessPolicyDTO.setCurrentDate(this.getCurrentDate(sessionContext));
			if (preferences != null && preferences.getPartyPreferencesDTOs() != null && preferences.getPartyPreferencesDTOs().getGracePeriod() != null) {
				transactionBusinessPolicyDTO.setGracePeriod(preferences.getPartyPreferencesDTOs().getGracePeriod());
			}

			abstractBusinessPolicy = businessPolicyFactory.getBusinesPolicyInstance("com.ofss.digx.app.approval.service.transaction.Transaction.performAction", transactionBusinessPolicyDTO);
			abstractBusinessPolicy.validate();
			IActionStatusEvaluator actionEvaluator = ActionStatusEvaluatorFactory.getInstance().getActionStatusEvaluator(transactionActionDTO.getAction().toString());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("fetch currentStatus: "+transactionDomain.getApprovalDetails().getStatus().toString()+" for txnId:"+transactionActionDTO.getTransactionDTO().getTransactionId());
			ApprovalStatus nextStatus = actionEvaluator.getNextStatus(transactionDomain.getApprovalDetails().getStatus(), transactionDomain);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("fetch nextStatus: "+nextStatus.toString()+" for txnId:"+transactionActionDTO.getTransactionDTO().getTransactionId());
			switch (nextStatus) {
				case APPROVED:
					eventId = "TRANSACTION_APPROVED";
					break;
				case REJECTED:
					eventId = "TRANSACTION_REJECTED";
					break;
				case MODIFICATION_REQUESTED:
					eventId = "TRANSACTION_MODIFICATION_REQUESTED";
					break;
				case PENDING_APPROVAL:
					eventId = "TRANSACTION_PARTIALLY_APPROVED";
					break;
				default:
					eventId = null;
			}

			Object transactionApprovalHistories;
			if (transactionDomain.getTransactionApprovalHistory() != null) {
				transactionApprovalHistories = transactionDomain.getTransactionApprovalHistory();
			} else {
				transactionApprovalHistories = new ArrayList();
				transactionDomain.setTransactionApprovalHistory((List)transactionApprovalHistories);
			}

			((List)transactionApprovalHistories).add(0, transactionDomain.toTransactionHistory());
			transactionDomain.setLastUpdatedBy(this.getLoggedInUserId());
			transactionDomain.setLastUpdatedDate(new Date());
			transactionDomain.setTransactionApprovalHistory((List)transactionApprovalHistories);
			TransactionApprovalDetails transactionApprovalDetails = transactionDomain.getApprovalDetails();
			transactionApprovalDetails.setStatus(nextStatus);
			// For OMB, signedBy originally was empty. transactionApprovalDetails.setStatus has a side effect, it will add the current subject user to the signedBy field. In order to maker the signed By field to be ~{user}~{user}~, we need to call this setStatus twice
			if (isOmbFlow) {
				transactionApprovalDetails.setStatus(nextStatus);
			}
			transactionApprovalDetails.setAction(transactionActionDTO.getAction());
			TransactionUserDetails transactionUserDetails = new TransactionUserDetails();
			ISubjectAdapter subjectAdapter = (ISubjectAdapter)AdapterFactory.getInstance().getAdapter(ISubjectAdapter.class);
			UserDTO userDTO = subjectAdapter.getUserProfile(sessionContext.getUserId());
			transactionUserDetails.setEmailId(userDTO.getBusinessEmail());
			transactionUserDetails.setTitle(userDTO.getTitle());
			transactionUserDetails.setFirstName(userDTO.getFirstName());
			transactionUserDetails.setLastName(userDTO.getLastName());
			transactionUserDetails.setMiddleName(userDTO.getMiddleName());
			transactionUserDetails.setMobileNumber(userDTO.getBusinessMobile());
			transactionUserDetails.setPhoneNumber(userDTO.getHomePhone());
			transactionUserDetails.setUsername(userDTO.getUserName());
			transactionDomain.setUpdatedByDetails(transactionUserDetails);
			transactionDomain.getProcessingDetails().setCurrentStep(ProcessingStep.APPROVAL);
			transactionDomain.getProcessingDetails().setStatus(actionEvaluator.getProcessingStatus(nextStatus, transactionDomain));

			if (isTFARequired()) {
				throw new TFARequiredException(TFAErrorConstant.AUTHENTICATION_REQUIRED);
			}

			Integer nextStepNo = transactionApprovalDetails.getStepNo();
			if (transactionActionDTO.getAction().equals(ApprovalAction.APPROVE)) {
				this.validateLimit(transactionDomain, nextStatus == ApprovalStatus.APPROVED);
				if (nextStatus == ApprovalStatus.APPROVED) {
					this.validateTransactionBlackout(transactionDomain);
				}

				if (ApprovalType.NONSEQUENTIAL != approvalType) {
					nextStepNo = nextStepNo + 1;
				}
			} else {
				this.reverseLimits(sessionContext, transactionDomain);
			}

			List<TransactionWorkflowSnapshotDTO> workflowSnapshotsToBeRemoved = actionEvaluator.fetchWorkflowsForElimination(transactionDomain);
			transactionApprovalDetails.setStepNo(nextStepNo);
			transactionDomain.setApprovalDetails(transactionApprovalDetails);
			transactionDomain.getApprovalDetails().setRemarks(remarks);
			List<TransactionWorkflowSnapshot> workflowSnapshotObjectRemoveList = new ArrayList();
			Iterator var29 = workflowSnapshotsToBeRemoved.iterator();

			while(var29.hasNext()) {
				TransactionWorkflowSnapshotDTO workflowSnapshotToBeRemoved = (TransactionWorkflowSnapshotDTO)var29.next();
				TransactionWorkflowSnapshotKey transactionWorkflowSnapshotKey = new TransactionWorkflowSnapshotKey();
				transactionWorkflowSnapshotKey.setId(workflowSnapshotToBeRemoved.getTransactionWorkflowSnapshotId());
				TransactionWorkflowSnapshot transactionWorkflowSnapshot = (TransactionWorkflowSnapshot)session.load(TransactionWorkflowSnapshot.class, transactionWorkflowSnapshotKey);
				if (!session.contains(transactionWorkflowSnapshot)) {
					transactionWorkflowSnapshot = (TransactionWorkflowSnapshot)session.merge(transactionWorkflowSnapshot);
				}

				session.delete(transactionWorkflowSnapshot);
				workflowSnapshotObjectRemoveList.add(transactionWorkflowSnapshot);
			}

			session.update(transactionDomain);
			session.flush();
			session.fetchCurrentTransaction().commit();
			this.removePendingTransactionsForApprover(workflowSnapshotObjectRemoveList);
			transactionDomain = transactionDomain.readFromPersistentStore(transactionDomain.getKey());
			DataAccessManager.getManager().fetchCurrentSession().refresh(transactionDomain);
			transactionDomain = transactionDomain.read(transactionDomain.getKey());
			TransactionWorkflowSnapshotDTO transactionWorkflowSnapshotDTO = new TransactionWorkflowSnapshotDTO();
			transactionWorkflowSnapshotDTO.setTransactionDTO(transactionDTO);
			IAdapterFactory transactionWorkflowSnapshotAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory("TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER_FACTORY");
			ITransactionWorkflowSnapshotAdapter transactionWorkflowSnapshotAdapter = (ITransactionWorkflowSnapshotAdapter)transactionWorkflowSnapshotAdapterFactory.getAdapter("TRANSACTION_WORKFLOW_SNAPSHOT_ADAPTER");
			TransactionWorkflowSnapshotListResponseDTO transactionWorkflowSnapshotListResponseDTO = transactionWorkflowSnapshotAdapter.search(transactionWorkflowSnapshotDTO);
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("Transaction performAction Register Alert Call: EventId:'%s' in class %s", new Object[]{eventId, THIS_COMPONENT_NAME}));
			}

			this.registerAlert(transactionDomain, eventId, transactionWorkflowSnapshotListResponseDTO.getTransactionWorkflowSnapshotDTOs());
			if (nextStatus.equals(ApprovalStatus.APPROVED)) {
				com.ofss.fc.infra.thread.ThreadAttribute.set("APPROVAL_STATUS", ApprovalStatus.APPROVED.toString());
				Object obj = transactionDomain.getTransactionSnapshot();
				if (obj != null) {
					transactionDomain.getTransactionApprovalHistory().add(0, transactionDomain.toTransactionHistory());
					transactionDomain.getProcessingDetails().setCurrentStep(ProcessingStep.EXECUTION);
					transactionDomain.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
					TransactionStatus transactionStatus = this.copyTransactionStatus();
					if (transactionDomain instanceof AmountAccountTransaction) {
						AmountAccountTransaction amountAccountTransaction = (AmountAccountTransaction)transactionDomain;
						if (amountAccountTransaction.getValueDate().isBefore(this.getCurrentDate(sessionContext))) {
							sessionContext.setMaxValueDate(amountAccountTransaction.getValueDate().plusDays(preferences.getPartyPreferencesDTOs().getGracePeriod()));
						}
					}

					Serializable result = ServiceInvocationHelper.getInstance().invokeService(transactionDomain.getServiceId(), obj.getClass(), new Object[]{sessionContext, obj});
					transactionActionResponse.setResult(result);
					this.analyseTransactionProcess(sessionContext, transactionDomain, (Object)null);
					this.restoreTransactionStatus(transactionStatus);
				}
			}

			session.beginTransaction();
			session.update(transactionDomain);
			session.fetchCurrentTransaction().commit();
            HthCRMApproval.committed(sessionContext, transactionDomain, String.valueOf(transactionActionDTO.getAction()));
			if (transactionDomain.getProcessingDetails().getCurrentStep().equals(ProcessingStep.EXECUTION)) {
				this.removeFromGracePeriodExpiryAlert(transactionDomain.getKey().getId());
			}

			transactionDTO = transactionAssembler.fromDomainObject(transactionDomain);
			transactionActionResponse.getTransactionAction().setTransactionDTO(transactionDTO);
			this.fetchTransactionStatus().setInternalReferenceNumber(transactionActionDTO.getTransactionDTO().getTransactionId());
			transactionActionResponse.setStatus(this.buildStatus(this.fetchTransactionStatus()));
			this.executor.postPerformAction(sessionContext, transactionActionDTO, transactionActionResponse);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("Exception from performAction() for TransactionActionDTO '%s' in class %s", new Object[]{transactionActionDTO, THIS_COMPONENT_NAME}), e);
			this.fillTransactionStatus(this.fetchTransactionStatus(), e);
		} catch (RuntimeException e) {
			this.fillTransactionStatus(this.fetchTransactionStatus(), e);
			LOGGER.log(Level.SEVERE, FORMATTER.formatMessage("RunTimeException from performAction() for TransactionActionDTO '%s' in class %s", new Object[]{transactionActionDTO, THIS_COMPONENT_NAME}), e);
		} finally {
			DataAccessManager.getManager().closeSession(session);
			Interaction.close();
		}

		super.checkResponsePolicy(sessionContext, transactionActionResponse);

		LOGGER.log(Level.FINE, FORMATTER.formatMessage("Exiting from performActionWithoutSession() : TransactionActionResponse=%s", new Object[]{transactionActionResponse}));

		return transactionActionResponse;
	}

    public void rejectOtherPendingTransaction(String transactionId, String remark) throws Exception {
        CZLocalTransactionRepositoryAdapter.getInstance().rejectOtherPendingTransaction(transactionId, remark);;
    }
}