/**
 *****************************************************************************
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import javax.security.auth.Subject;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.approval.dto.transaction.AccountTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.AmountAccountTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.PartyTransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.ProcessingErrorDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionActionResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionApprovalDetailsDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionCountDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionCountListResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionListResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionRequestDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionResponse;
import com.ofss.digx.app.approval.dto.transaction.TransactionUserDetailsDTO;
import com.ofss.digx.app.approval.service.transaction.ext.VoidTransactionExt;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.app.ebpp.dto.billpayment.BillPaymentDTO;
import com.ofss.digx.app.ebpp.dto.billpayment.BillPaymentRelDetailsDTO;
import com.ofss.digx.app.ebpp.dto.billpayment.transaction.BillPaymentTransactionDTO;
import com.ofss.digx.app.fileupload.dto.FileIdentifierRegistrationCreateRequestDTO;
import com.ofss.digx.app.fileupload.dto.FileIdentifierRegistrationUpdateRequestDTO;
import com.ofss.digx.app.payment.dto.network.NetworkPaymentCreateRequestDTO;
import com.ofss.digx.app.payment.dto.network.NetworkPaymentCreateResponseDTO;
import com.ofss.digx.app.payment.dto.network.NetworkPaymentDTO;
import com.ofss.digx.app.payment.dto.transaction.PayeeTransactionDTO;
import com.ofss.digx.app.payment.dto.transaction.PaymentTransactionDTO;
import com.ofss.digx.app.td.dto.account.TermDepositAccountDTO;
import com.ofss.digx.common.constants.ApprovalConstants;
import com.ofss.digx.common.constants.CommonConstants;
import com.ofss.digx.core.adapter.AdapterFactory;
import com.ofss.digx.core.adapter.task.ITaskAdapter;
import com.ofss.digx.cz.bea.app.account.dto.dda.CurrentAccountDTO;
import com.ofss.digx.cz.bea.app.account.dto.eadviceestmt.acctpref.AcctPreferenceDTO;
import com.ofss.digx.cz.bea.app.account.dto.eadviceestmt.acctpref.AcctPreferenceRequestDTO;
import com.ofss.digx.cz.bea.app.agreerate.dto.fxagree.FxAgreeOWNCRUDDomainDTO;
import com.ofss.digx.cz.bea.app.bulkupload.dto.AutopayRequestDTO;
import com.ofss.digx.cz.bea.app.bulkupload.dto.BatchTransferRequestDTO;
import com.ofss.digx.cz.bea.app.bulkupload.dto.CollectionRequestDTO;
import com.ofss.digx.cz.bea.app.bulkupload.dto.PayrollRequestDTO;
import com.ofss.digx.cz.bea.app.bulkupload.fileupload.dto.BulkFileRecordDTO;
import com.ofss.digx.cz.bea.app.bulkupload.fileupload.dto.BulkFileUploadsDTO;
import com.ofss.digx.cz.bea.app.common.helper.AccountDetailsHelper;
import com.ofss.digx.cz.bea.app.common.service.BranchDateHelper;
import com.ofss.digx.cz.bea.app.common.util.CZCommonUtils;
import com.ofss.digx.cz.bea.app.common.util.CZCommonValidation;
import com.ofss.digx.cz.bea.app.common.util.DArrayUtils;
import com.ofss.digx.cz.bea.app.common.util.OneManBankUtils;
import com.ofss.digx.cz.bea.app.common.util.TaskUtils;
import com.ofss.digx.cz.bea.app.customconfig.adapter.ICustomConfigAdapter;
import com.ofss.digx.cz.bea.app.customconfig.util.CustomConfigUtil;
import com.ofss.digx.cz.bea.app.dda.adapter.statement.adhoc.IAdhocStatementAdapter;
import com.ofss.digx.cz.bea.app.dda.dto.statement.adhoc.AdhocStatementDTO;
import com.ofss.digx.cz.bea.app.dda.dto.statement.adhoc.AdhocStatementRequestDTO;
import com.ofss.digx.cz.bea.app.fps.dto.fpsservice.FPSAddressingServiceDTO;
import com.ofss.digx.cz.bea.app.fps.dto.fpsservice.FPSAddressingServiceListDTO;
import com.ofss.digx.cz.bea.app.fpsmerchant.dto.FpsMerchantAddressingDTO;
import com.ofss.digx.cz.bea.app.liquiditymanagement.dto.lmInstruction.CZLMCreateRequestDTO;
import com.ofss.digx.cz.bea.app.merchant.assembler.MerchantAssembler;
import com.ofss.digx.cz.bea.app.merchant.assembler.MerchantMappingAssembler;
import com.ofss.digx.cz.bea.app.merchant.dto.MerchantAdviceMappingDTO;
import com.ofss.digx.cz.bea.app.merchant.dto.MerchantDTO;
import com.ofss.digx.cz.bea.app.merchant.dto.MerchantMappingDTO;
import com.ofss.digx.cz.bea.app.merchant.dto.MerchantUserMaintenanceDTO;
import com.ofss.digx.cz.bea.app.party.dto.profile.StandaloneAccountMappingDTO;
import com.ofss.digx.cz.bea.app.payment.adapter.network.ICZNetworkPaymentCrossDomainAdapter;
import com.ofss.digx.cz.bea.app.payment.dto.common.CZPayeeEnquiryResponseDTO;
import com.ofss.digx.cz.bea.app.payment.dto.common.CZPaymentSearchRequest;
import com.ofss.digx.cz.bea.app.payment.dto.network.CZNetworkPaymentInternalDTO;
import com.ofss.digx.cz.bea.app.payment.dto.network.CZNetworkPaymentPayoutDTO;
import com.ofss.digx.cz.bea.app.payment.service.common.CZPaymentCommonFunc;
import com.ofss.digx.cz.bea.app.security.adapter.IMacDataAdapter;
import com.ofss.digx.cz.bea.app.security.dto.MACDataDTO;
import com.ofss.digx.cz.bea.app.td.assembler.CZTermDepositAssembler;
import com.ofss.digx.cz.bea.app.td.dto.CZTermDepositListRequestDTO;
import com.ofss.digx.cz.bea.app.td.service.account.core.CZTermDeposit;
import com.ofss.digx.cz.bea.common.BMTxn.BMTxnDTO;
import com.ofss.digx.cz.bea.common.BMTxn.BMTxnResponseDTO;
import com.ofss.digx.cz.bea.common.constants.CZCommonConstants;
import com.ofss.digx.cz.bea.common.constants.CZFieldConstants;
import com.ofss.digx.cz.bea.common.constants.CZPaymentConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.cz.bea.common.constants.FpsMerchantConstants;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.digx.cz.bea.common.util.CZDateUtils;
import com.ofss.digx.cz.bea.common.util.CZLocaleUtils;
import com.ofss.digx.cz.bea.common.util.InputValidationUtils;
import com.ofss.digx.cz.bea.common.util.SysoutLogger;
import com.ofss.digx.cz.bea.domain.approval.entity.hosttransaction.HostTransaction;
import com.ofss.digx.cz.bea.domain.approval.entity.hosttransaction.HostTransactionKey;
import com.ofss.digx.cz.bea.domain.approval.entity.transaction.TransactionCountLow;
import com.ofss.digx.cz.bea.domain.approval.entity.transaction.TransactionDiscriminatorLos;
import com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantTxnCrmKey;
import com.ofss.digx.cz.bea.domain.merchant.entity.Merchant;
import com.ofss.digx.cz.bea.domain.merchant.entity.MerchantKey;
import com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping;
import com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMappingKey;
import com.ofss.digx.cz.bea.domain.merchant.entity.repository.adapter.LocalMerchantMappingRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.merchant.entity.repository.adapter.LocalMerchantRepositoryAdapter;
import com.ofss.digx.cz.bea.domain.security.entity.session.MACData;
import com.ofss.digx.cz.bea.domain.td.entity.account.CZTermDepositAccount;
import com.ofss.digx.cz.bea.domain.td.entity.preferentialtd.PreferentialTDReportDetails;
import com.ofss.digx.cz.bea.enumeration.payment.CZPaymentStatusType;
import com.ofss.digx.cz.bea.framework.domain.transaction.repository.adapter.CZLocalTransactionRepositoryAdapter;
import com.ofss.digx.enumeration.approval.ApprovalAction;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.approval.ProcessingStatus;
import com.ofss.digx.enumeration.approval.TransactionDiscriminator;
import com.ofss.digx.enumeration.approval.ViewType;
import com.ofss.digx.enumeration.payment.payee.PayeeType;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.framework.domain.transaction.AmountAccountTransaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.date.DateHelper;
import com.ofss.digx.infra.error.ErrorManager;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.digx.infra.resource.ErrorMessageResourceBundle;
import com.ofss.digx.infra.resource.ResourseBundleControl;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.locale.LocaleUtils;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.fc.utils.SerializationUtils;
import com.ofss.digx.cz.bea.common.constants.FpsMerchantConstants;
import com.ofss.digx.app.fileupload.dto.UserFIMappingDTO;
import com.ofss.digx.app.fileupload.dto.UserFIMappingUpdateRequestDTO;
import com.ofss.digx.app.fileupload.service.UserFIMapping;
import com.ofss.digx.domain.fileupload.entity.UserFIMappingKey;
import com.ofss.digx.framework.determinant.DeterminantResolver;
import com.ofss.fc.domain.commonservice.entity.id.definition.AbstractGeneratorFactory;
import com.ofss.fc.domain.commonservice.entity.id.generation.IdGenerator;
import com.ofss.fc.infra.exception.FatalException;
import org.json.JSONObject;
import com.ofss.digx.domain.fileupload.entity.FileIdentifierRegistration;

import oracle.security.jps.util.SubjectUtil;

public class CZTransactionExt extends VoidTransactionExt {
	public static final String BM_TXN_REFERENCE = "BM-TXN-REFERENCE";

	public static final String MULTI_BM_TXN_REFERENCE = "MULTI_BM-TXN-REFERENCES";

	public static final String MAC_APPLICABLE = "macApplicable";

	public static final String MAC_MODULUS = "macModulus";

	public static final String MAC_RSA_INDICATOR = "macRSAIndicator";

	public static final String MAC_KEY = "macKey";

	public static final String MAC_DATA = "macData";

	public static final String MAC_ENC_DATA = "macEncryptedData";

	public static final String DEST_ACCT_CURRENCY = "destAcctCurrency";

	public static final String SRC_ACCT_CURRENCY = "srcAcctCurrency";

	public static final String IS_ADMIN = "isAdmin";

	public static final String MOBILE = "mobile";
	public static final String PAYEE_EMAIL = "payeeEmail";
	public static final String FPS_ID = "fpsId";
	public static final String BANK_NAME = "bankName";
	public static final String PAYEE_IDENTIFN = "payeeIdentifn";

	private static final String IS_OMB_ENABLED = "IS_OMB_ENABLED";

	private static final String TRANSACTION_AUDIT_STATUS = "TRANSACTION_AUDIT_STATUS";

	public static final String FPS_MAC_DATA = "fpsMacData";

	private static final String THIS_COMPONENT_NAME = CZTransactionExt.class.getName();

	private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

	private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

	Preferences dayOneConfig = ConfigurationFactory.getInstance().getConfigurations(CommonConstants.DAY_ONE_CONFIG);
	private String isEbppDeployed = dayOneConfig.get("IS_EBPP_DEPLOYED", "N");

	private static final MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();
	private static final Logger logger = formatter.getLogger(THIS_COMPONENT_NAME);

	@Override
	public void postTransactionsCount(SessionContext sessionContext, ViewType viewType,
		    com.ofss.digx.enumeration.sms.RoleType roleType, TransactionRequestDTO transactionRequestDto,
		    TransactionCountListResponse transactionCountListResponse) throws com.ofss.digx.infra.exceptions.Exception {
		    // Empty Implementation

		    if (getCZCountTempFlag()) {
		        System.out.println("&&&& Inside before");
		        if (transactionCountListResponse.getCountDTOList().size() > 0) {
		            for (TransactionCountDTO transactionCountDTO: transactionCountListResponse.getCountDTOList()) {
		                System.out.println("====Discriminitaor==" + transactionCountDTO.getTransactionType().toString());
		                System.out.println("Before getApproved=" + transactionCountDTO.getApproved());
		                System.out.println("Before getInitiated=" + transactionCountDTO.getInitiated());
		                System.out.println("Before getPendingApproval=" + transactionCountDTO.getPendingApproval());
		                System.out.println("Before getRejected=" + transactionCountDTO.getRejected());
		                System.out.println(
		                    "Before getRequestForModification=" + transactionCountDTO.getRequestForModification());
		                System.out.println("Before getModified=" + transactionCountDTO.getModified());
		                System.out.println("Before getSuccess=" + transactionCountDTO.getSuccess());
		            }
		        }
		    }

		    if ("A".equalsIgnoreCase(roleType.getValue())) {
		        if (transactionCountListResponse != null) {
		            List < TransactionCountDTO > dtoList = transactionCountListResponse.getCountDTOList();
		            List < TransactionCountDTO > modfiedDtoList = new ArrayList < TransactionCountDTO > ();

		            boolean payeeBillerAdded = false, payeeBillerExistsInList = false;

		            Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin");
		            System.out.println("postTransactionCOunt admin type isAdmin ? " + isAdmin);


		            for (TransactionCountDTO transactionDTO: dtoList) {
		                /**
		                 * Removing code to show all tabs on UI - Bank Admin
		                 */
		                if ("PARTY_MAINTENANCE".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
		                    modfiedDtoList.add(transactionDTO);
		                }
		                if ("ADMIN_MAINTENANCE".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
		                    modfiedDtoList.add(transactionDTO);
		                }

		                if ("PAYEE_BILLER".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
		                    payeeBillerExistsInList = true;
		                }
		            }

		            if (ThreadAttribute.get("APPROVAL_COUNT_LIST_FOR_CHECKER") != null) {
		                System.out.println("APPROVAL_COUNT_LIST_FOR_CHECKER if");
		                List < Object[] > threadTxnResponse = (List < Object[] > ) ThreadAttribute.get("APPROVAL_COUNT_LIST_FOR_CHECKER");
		                System.out.println("threadTxnResponse while " + viewType + " is " + ThreadAttribute.get("APPROVAL_COUNT_LIST_FOR_CHECKER"));
		                if (!isAdmin && threadTxnResponse != null) {

		                    System.out.println("ViewType is" + viewType);
		                    if (viewType.toString().equals("approval")) {
		                        for (Object[] result: threadTxnResponse) {
		                            System.out.println("result from thred in approval is " + result[1] + " and count" + result[0]);

		                            if (result[1].toString().equals("PAYEE_BILLER")) {
		                                TransactionCountDTO countDTO = new TransactionCountDTO();
		                                TransactionDiscriminator type = null;
		                                Integer count = null;
		                                count = toInteger(result[0]);
		                                type = TransactionDiscriminator.fromValue(result[1].toString());
		                                countDTO.setTransactionType(type);
		                                countDTO.setPendingApproval(count);
		                                System.out.println("Adding Payee biller in list");
		                                modfiedDtoList.add(countDTO);
		                                payeeBillerAdded = true;
		                            } else {
		                                System.out.println("Not a payee_biller");
		                            }
		                        }

		                        if (!isAdmin && !payeeBillerAdded) {
		                            System.out.println("payee txn not available");
		                            TransactionCountDTO payeeBillerDTO = new TransactionCountDTO();
		                            payeeBillerDTO.setTransactionType(TransactionDiscriminator.PAYEE_BILLER);
		                            payeeBillerDTO.setPendingApproval(0);
		                            modfiedDtoList.add(payeeBillerDTO);
		                        }


		                    } else if (viewType.toString().equals("created") || viewType.toString().equals("approved")) {
		                        System.out.println("result from thred in approval is " + viewType.toString());
		                        for (Object[] result: threadTxnResponse) {
		                            System.out.println("result from thred in approval is " + result[1] + " and count" + result[0]);

		                            if (result[1].toString().equals("PAYEE_BILLER")) {
		                                TransactionDiscriminator type = (TransactionDiscriminator) result[1];
		                                Integer count = null;
		                                count = toInteger(result[0]);
		                                String countFor = (String) result[2];
		                                TransactionCountDTO transactionCountDTO = null;
		                                for (TransactionCountDTO countDTO: modfiedDtoList) {
		                                    if (countDTO.getTransactionType() == type) {
		                                        transactionCountDTO = countDTO;
		                                        break;
		                                    }
		                                }

		                                if (transactionCountDTO == null) {
		                                    transactionCountDTO = new TransactionCountDTO();
		                                    transactionCountDTO.setTransactionType(type);
		                                    modfiedDtoList.add(transactionCountDTO);
		                                    payeeBillerAdded = true;
		                                }
		                                if (countFor.equals("REJECT")) {
		                                    transactionCountDTO.setRejected(count);
		                                } else if (countFor.equals("APPROVED")) {
		                                    transactionCountDTO.setApproved(count);
		                                } else if (countFor.equals("INITIATED")) {
		                                    transactionCountDTO.setInitiated(count);
		                                } else if (countFor.equals(ApprovalAction.REQUEST_MODIFICATION.toString())) {
		                                    transactionCountDTO.setModified(count);
		                                }

		                            } else {
		                                System.out.println("Not a payee_biller");
		                            }
		                        }

		                        if (!isAdmin && !payeeBillerAdded) {
		                            System.out.println("payee txn not available");
		                            TransactionCountDTO payeeBillerDTO = new TransactionCountDTO();
		                            payeeBillerDTO.setTransactionType(TransactionDiscriminator.PAYEE_BILLER);
		                            payeeBillerDTO.setPendingApproval(0);
		                            modfiedDtoList.add(payeeBillerDTO);
		                        }

		                    }
		                } else {
		                    System.out.println("Thread response null");
		                }
		            }else {
		            	System.out.println("APPROVAL_COUNT_LIST_FOR_CHECKER is null");
		            }

				transactionCountListResponse.setCountDTOList(modfiedDtoList);
			}
		}

		if ("P".equalsIgnoreCase(roleType.getValue())) {
			if (transactionCountListResponse != null) {
				List<TransactionCountDTO> dtoList = transactionCountListResponse.getCountDTOList();
				List<TransactionCountDTO> modfiedDtoList = new ArrayList<TransactionCountDTO>();
				for (TransactionCountDTO transactionDTO : dtoList) {

					/**
					 * Removing code to show all tabs on UI Corporate User
					 */

					if ("ACCOUNT_FINANCIAL".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
						modfiedDtoList.add(transactionDTO);
					}
					if ("PAYMENTS".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
						modfiedDtoList.add(transactionDTO);
					}
					if ("ACCOUNT_NON_FINANCIAL".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
						modfiedDtoList.add(transactionDTO);
					}

					if ("ELECTRONIC_BILL_PAYMENTS".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
						modfiedDtoList.add(transactionDTO);
					}

					if ("BULK_FILE".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
						modfiedDtoList.add(transactionDTO);
					}
					if (isLmEnabled()) {
						if ("LIQUIDITY_MANAGEMENT".equalsIgnoreCase(transactionDTO.getTransactionType().toString())) {
							modfiedDtoList.add(transactionDTO);
						}
					}

				}

				transactionCountListResponse.setCountDTOList(modfiedDtoList);
			}
		}

		if (getCZCountTempFlag()) {
			System.out.println("&&&& Inside after");
			if (transactionCountListResponse.getCountDTOList().size() > 0) {
				for (TransactionCountDTO transactionCountDTO : transactionCountListResponse.getCountDTOList()) {
					System.out.println("====Discriminitaor==" + transactionCountDTO.getTransactionType().toString());
					System.out.println("After getApproved=" + transactionCountDTO.getApproved());
					System.out.println("After getInitiated=" + transactionCountDTO.getInitiated());
					System.out.println("After getPendingApproval=" + transactionCountDTO.getPendingApproval());
					System.out.println("After getRejected=" + transactionCountDTO.getRejected());
					System.out.println(
							"After getRequestForModification=" + transactionCountDTO.getRequestForModification());
					System.out.println("After getModified=" + transactionCountDTO.getModified());
					System.out.println("After getSuccess=" + transactionCountDTO.getSuccess());
				}
			}
		}
		//BCOCDC-4064
		String partyID = dayOneConfig.get("OCTOPUS_USER", "");
		if (!"".equals(partyID)){
			List<String> partyLists = Arrays.asList(partyID.split(","));
			if (partyLists.contains(sessionContext.getTransactingPartyCode())){
				TransactionCountLow transactionCountLow = new TransactionCountLow();
				transactionCountLow.setTransactionType(TransactionDiscriminatorLos.BULK_PAYMENT);
				TransactionCountLow transactionCountLow1 = new TransactionCountLow();
				ArrayList<TransactionCountDTO> transactionCountDTOS = new ArrayList<>();
				transactionCountDTOS.add(transactionCountLow);
				transactionCountDTOS.add(transactionCountLow1);
				transactionCountDTOS.addAll(transactionCountListResponse.getCountDTOList());
				transactionCountListResponse.setCountDTOList(transactionCountDTOS);
			}

		}
		//BCOCDC-5595 Jason start
		Subject subject = SubjectUtil.getCurrentSubject();
		String userName = SubjectUtil.getUserName(subject);
		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN);
		boolean switchOn;
		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String switchValue = customConfigAdapter.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "SPECIAL_FUNCTION_GROUP_SWITCH", "");
		String bddMktUserIds = customConfigAdapter.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "BDD_MARKETING_USER_ID", "");
		Set<String> allowedBddMktUserIds = new HashSet<>();
		try {
			if (switchValue == null || switchValue.isEmpty()) {
				System.out.println("Switch configuration is empty...");
				throw new IllegalArgumentException("Switch configuration is empty.");
			}else {
				switchOn = "ON".equalsIgnoreCase(switchValue);
			}
			String[] bddMktUserIdList = bddMktUserIds.split("~");
			if (bddMktUserIdList.length > 0) {
				for (int i = 0; i < bddMktUserIdList.length; i++) {
					System.out.println("Allow function group: " + i + " is: "+ bddMktUserIdList[i]);
					allowedBddMktUserIds.add(bddMktUserIdList[i]);
				}
			}else{
				System.out.println("Invalid department code configuration format...");
				throw new IllegalArgumentException("Invalid configuration format.");
			}
		}catch (java.lang.Exception e){
			System.err.println("Error processing SPECIAL_FUNCTION_GROUP_SWITCH configuration: " + e.getMessage());
			e.printStackTrace();
			switchOn = false;
		}
		System.out.println("SPECIAL_FUNCTION_GROUP_SWITCH: " + switchOn);
		if(switchOn && isAdmin && isBddMktUser(userName, allowedBddMktUserIds)){
            if (transactionCountListResponse != null) {
				List < TransactionCountDTO > dtoList = transactionCountListResponse.getCountDTOList();
				List < TransactionCountDTO > modifiedDtoList = new ArrayList < TransactionCountDTO > ();
				System.out.println("Special post transaction count origin dto list size before filter is: " + dtoList.size());
				for (TransactionCountDTO countDTO: dtoList) {
					if ("ADMIN_MAINTENANCE".equalsIgnoreCase(countDTO.getTransactionType().toString())) {
						modifiedDtoList.add(countDTO);
					}
				}
				System.out.println("** EBKCDC04 post transaction count logic username is = " + userName);
				System.out.println("Special transaction count  modifiedDtoList size after filter is: " + modifiedDtoList.size());
				transactionCountListResponse.setCountDTOList(modifiedDtoList);
            }
		}
		//BCOCDC-5595 Jason end
	}

	public static Integer toInteger(Object obj) {
		System.out.println("Came to convert");
	    if (obj == null) return null;
	    if (obj instanceof BigDecimal) return ((BigDecimal) obj).intValue();
	    if (obj instanceof Long) return ((Long) obj).intValue();
	    if (obj instanceof Integer) return (Integer) obj;
	    throw new IllegalArgumentException("Unexpected type: " + obj.getClass());
	}

	private static boolean isLmEnabled() {
		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String isLmEnabled = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "LM_ENABLED", "false");
		System.out.println("\n \n isLmEnabled : " + isLmEnabled + "\n \n");
		return Boolean.parseBoolean(isLmEnabled);
	}

	@Override
	public void prePerformAction(SessionContext sessionContext, TransactionActionDTO transactionActionDTO)
			throws Exception {
		com.ofss.digx.infra.thread.ThreadAttribute.set("com.ofss.digx.framework.domain.transaction.Transaction.TXN_ID", null==transactionActionDTO||null==transactionActionDTO.getTransactionDTO()||null==transactionActionDTO.getTransactionDTO().getTransactionId()?"":transactionActionDTO.getTransactionDTO().getTransactionId());
		if (isTFARequired()) {
			throw new com.ofss.digx.app.security.exceptions.tfa.TFARequiredException(
					com.ofss.digx.framework.security.twofactor.TFAErrorConstant.AUTHENTICATION_REQUIRED);
		}
		ApprovalAction transactionAction = transactionActionDTO.getAction();
		String macApplicable = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_APPLICABLE);
		if (macApplicable != null && macApplicable.equalsIgnoreCase("true")
				&& transactionAction.equals(ApprovalAction.APPROVE)) {

			String macEncData = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_ENC_DATA);

			String macModulus = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_MODULUS);

			String macRsaIndicator = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_RSA_INDICATOR);

			String macGenKey = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_KEY);

			String transactionId = transactionActionDTO.getTransactionDTO().getTransactionId();

			if (transactionId == null) {
				com.ofss.digx.infra.thread.ThreadAttribute.set("com.ofss.digx.framework.domain.transaction.Transaction.TXN_ID", transactionId);
				String compositeValue = (String) com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID);
				if (compositeValue != null && !compositeValue.isEmpty()) {
					String[] compositeValueArray = compositeValue.split("#");
					transactionId = compositeValueArray[0];
				} else {
					transactionId = "TestingTxn";
				}
			}
			Subject subject = SubjectUtil.getCurrentSubject();

			String userName = SubjectUtil.getUserName(subject);

			String customerID = sessionContext.getTransactingPartyCode();
			MACDataDTO macData = new MACDataDTO();
			macData.setCdcID(customerID);
			System.out.println("Customer ID for MAC" + customerID);
			macData.setMacEncData(macEncData);
			System.out.println("macEncData ID for MAC" + macEncData);
			macData.setMacGenKey(macGenKey);
			System.out.println("macGenKey ID for MAC" + macGenKey);
			macData.setMacModulus(macModulus);
			System.out.println("macModulus ID for MAC" + macModulus);
			macData.setMacRsaIndicator(macRsaIndicator);
			System.out.println("macRsaIndicator ID for MAC" + macRsaIndicator);
			macData.setUserID(userName);
			System.out.println("userName ID for MAC" + userName);
			macData.setTransactionID(transactionId);
			System.out.println("transactionId ID for MAC" + transactionId);

			System.out.println("prePerformAction TransactionName="
					+ transactionActionDTO.getTransactionDTO().getTransactionName());
			if (transactionActionDTO.getTransactionDTO() != null && transactionId != null
					&& transactionActionDTO.getTransactionDTO().getApprovalDetails() != null) {
				String remarksOrig = transactionActionDTO.getTransactionDTO().getApprovalDetails().getRemarks();

				System.out.println("prePerformAction UI not reqady remarksOrig=" + remarksOrig);
				// remarksOrig+="~~intrstrate=7.000700";

				if (remarksOrig.contains("~~")) {
					String remarksReal = remarksOrig.split("~~")[0];
					String remarksRate = remarksOrig.split("~~")[1];
					transactionActionDTO.getTransactionDTO().getApprovalDetails().setRemarks(remarksReal);
					if (remarksRate.split("=") != null && remarksRate.split("=").length == 2) {
						ThreadAttribute.set("CHKR1_INTRATE", remarksRate.split("=")[1]);
					}
					System.out.println("prePerformAction remarksOrig=" + remarksOrig + " remarksReal=" + remarksReal
							+ " remarksRate=" + remarksRate);

				}
			}

			IAdapterFactory adapterfactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.MAC_DATA_ADAPTER_FACTORY);
			IMacDataAdapter adapter = (IMacDataAdapter) adapterfactory
					.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.MAC_DATA_ADAPTER);
			adapter.updateMacData(sessionContext, macData);
		}
		new CZTransactionExtFunc().prePerformAction(sessionContext, transactionActionDTO);
	}

	private boolean isTFARequired() {
		System.out.println("##X_CHALLENGE=" + com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE));
		System.out.println("##TFA_REQUIRED=" + com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED));
		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED) != null) {
			System.out.println("##TFA_REQUIRED Boolean=" + (boolean) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED));
		}
		return (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_CHALLENGE) != null
				&& !(com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED) != null
						&& (boolean) com.ofss.digx.infra.thread.ThreadAttribute
								.get(com.ofss.digx.infra.thread.ThreadAttribute.TFA_REQUIRED)));
	}

	@Override
	public void postCheckApprovals(SessionContext sessionContext, Object requestDTO,
			TransactionStatus transactionStatus) throws Exception {
		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		String transaction_ref_no = null;

		String finalPartyId = sessionContext.getTransactingPartyCode();

		StandaloneAccountMappingDTO mappedDTO = CZCommonUtils.getMappedEntryForStandAloneAcc(finalPartyId);

		if (mappedDTO != null) {
			System.out.println("For All the Txn's Standalone 89 Mapped will go in mac");
			finalPartyId = mappedDTO.getCdcIdExternal();
		}

		// String finalInternalAccNo = mappedDTO.getCdcIdInternal();

		// String transaction_ref_no = (String)
		// com.ofss.digx.infra.thread.ThreadAttribute
		// .get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);

		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null) {
			transaction_ref_no = (String) com.ofss.digx.infra.thread.ThreadAttribute
					.get(com.ofss.digx.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
			System.out.println(
					"CZTransactionExt123 postCheckApprovals transaction_ref_no inside if " + transaction_ref_no);
		} else if (com.ofss.fc.infra.thread.ThreadAttribute
				.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO) != null) {
			transaction_ref_no = (String) com.ofss.fc.infra.thread.ThreadAttribute
					.get(com.ofss.fc.infra.thread.ThreadAttribute.TRANSACTION_REFERENCE_NO);
			System.out.println(
					"CZTransactionExt postCheckApprovals transaction_ref_no inside else " + transaction_ref_no);
		}

		String taskID = (String) ThreadAttribute.get("BM-COUPONTD-TASKID");
		System.out.println("CZTransactionExt taskID for couponTD:  " + taskID);

		if (taskID != null && taskID.equalsIgnoreCase("CTD_C")) {
			System.out.println("CZTransactionExt taskId for couponTD:  " + taskId);
			if (requestDTO instanceof TermDepositAccountDTO) {
				System.out.println("CZTransactionExt CTD_C couponTD create data inserting inside table ");
				TermDepositAccountDTO tdrequestDTO = (TermDepositAccountDTO) requestDTO;
				tdrequestDTO.setOfferId(transaction_ref_no);
				CZTermDepositAssembler termDepositAssembler = new CZTermDepositAssembler();
				CZTermDepositAccount termDepositDomainObj = termDepositAssembler.toDomainObject(tdrequestDTO);
				termDepositDomainObj.setValueDate(new Date(sessionContext.getPostingDateText()));
				termDepositDomainObj.setStatus("PENDING");
				termDepositDomainObj.create(termDepositDomainObj);
				System.out.println("Exiting CZTransactionExt after entering coupon TD details in DB.");
			}
		}

		String bmTxnNo = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(BM_TXN_REFERENCE);
		String obj = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MULTI_BM_TXN_REFERENCE);
		String macData = (String) com.ofss.digx.infra.thread.ThreadAttribute.get(MAC_DATA);
		String fxIndicator = "N";

		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN);

		String compositeValue = (String) com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID);
		String xTransactionId = null;
		String versionNumber = null;
		boolean OMBflag = false;
		boolean xTransactionIdAvailable = false;

		if (com.ofss.fc.infra.thread.ThreadAttribute.get(IS_OMB_ENABLED) != null) {
			OMBflag = (Boolean) com.ofss.fc.infra.thread.ThreadAttribute.get(IS_OMB_ENABLED);
			System.out.println("OMB for transaction is enabled and true==" + OMBflag);
		}

		if (taskId != null && (taskId.equals("MT_N_AFP") || taskId.equals("EB_F_BP"))) {
			taskId = TaskUtils.getEvaluatedTask(TaskAspect.TWO_FACTOR_AUTHENTICATION);
			System.out.println("** FPS/Bill payment evaluated task3=" + taskId);
		}

		if (com.ofss.digx.infra.thread.ThreadAttribute
				.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID) != null
				&& !com.ofss.digx.infra.thread.ThreadAttribute
						.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID).equals("")) {
			xTransactionIdAvailable = true;
			System.out.println("X_TRANSACTION_ID available in cz transaction ext="
					+ (String) com.ofss.digx.infra.thread.ThreadAttribute
							.get(com.ofss.digx.infra.thread.ThreadAttribute.X_TRANSACTION_ID));
		}

		if (OMBflag && !xTransactionIdAvailable && transaction_ref_no != null
				&& !"".equalsIgnoreCase(transaction_ref_no) && taskId != null
				&& TaskUtils.checkAspectSupportedForTask(taskId, TaskAspect.TWO_FACTOR_AUTHENTICATION)) {
			com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
			TransactionKey transactionKey = new TransactionKey();
			System.out.println("Txn ref no is" + transaction_ref_no);
			transactionKey.setId(transaction_ref_no);
			Session session = DataAccessManager.getManager().fetchCurrentSession();
			session.flush();

			transaction = transaction.readFromPersistentStore(transactionKey);

			try {
				CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter.getInstance();

				localRepository.updateTransactionApprovalHistory(transaction_ref_no);

				localRepository.updateTransactionFromId(transaction_ref_no);
			} catch (java.lang.Exception e) {
				System.out.println("Line muner 375  cz approvale ext");
			}
		}

		if (compositeValue != null && !compositeValue.isEmpty()) {
			String[] compositeValueArray = compositeValue.split("#");
			xTransactionId = compositeValueArray[0];
			versionNumber = compositeValueArray[1];
		}

		com.ofss.digx.infra.thread.ThreadAttribute.set("TRANS_REF", transaction_ref_no);
		com.ofss.fc.infra.thread.ThreadAttribute.set("TRANS_REF", transaction_ref_no);

		System.out.println("transaction ref no" + transaction_ref_no);
		System.out.println("bm ref no" + bmTxnNo);
		System.out.println("MAC Data from thread" + macData);
		System.out.println("multiple test value" + obj);
		System.out.println("MAC Task id is " + taskId);
		System.out.println("X-Transaction ID is : " + xTransactionId + " Version Number is : " + versionNumber);
		System.out.println(
				"CURRENTLY_EXECUTING_SERVICE=" + ThreadAttribute.get(ThreadAttribute.CURRENTLY_EXECUTING_SERVICE));

		StringBuffer macTempData = new StringBuffer();
		System.out.println("** inside postCheckApprovals");
		if (taskId != null && taskId.equalsIgnoreCase("CH_N_RADHSTMT")) {
			System.out.println("Task ID=" + taskId);

			AdhocStatementRequestDTO request = (AdhocStatementRequestDTO) requestDTO;
			AdhocStatementDTO requestObj = request.getAdhocStatementDto();

			if (sessionContext.getTransactingPartyCode() != null
					&& !OneManBankUtils.isOneManBankEnabled(sessionContext.getTransactingPartyCode(), taskId)) {
				IAdapterFactory adapterfactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.ADHOC_STATEMENT_ADAPTER_FACTORY);

				requestObj
						.setTxnReferenceNumber((String) ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO));
				requestObj.setRequestStatus("INITIATED");
				IAdhocStatementAdapter adapter = (IAdhocStatementAdapter) adapterfactory.getAdapter(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.ADHOC_STATEMENT_ADAPTER);
				adapter.updateAdhocStatementRequest(request);
			}

			CurrentAccountDTO accountDTO = getAccountDetails(requestObj.getAccount().getValue());
			String[] masterAccountId = getMasterAccount(requestObj.getAccount().getValue(), accountDTO.getProductCode())
					.split("~");
			String partyID = finalPartyId;
			String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");
			String accountCurrency = accountDTO.getCurrencyCode();
			System.out.println("Inside MAC for STAC1 : Account currency : " + accountCurrency + " Product Code :"
					+ masterAccountId[1]);

			Boolean collectionAcc = false;
			String[] currency = null;
			String productCodeList = getFlagForAutopayPayroll("PRODUCT_CODE_LIST_STAC1_2");
			if (!productCodeList.equals("") && masterAccountId != null) {
				if (productCodeList.contains(masterAccountId[1])) {
					collectionAcc = true;
					System.out
							.println("Collection Account is True will call in loop : " + accountDTO.getCurrencyCode());
					currency = accountDTO.getCurrencyCode().split("~");
				}
			}

			if (collectionAcc) {
				accountCurrency = "HKD";
			} else {
				if (accountCurrency != null) {
					if (accountCurrency.contains("~") && !masterAccountId[1].equals("1025")) {
				accountCurrency = "MST";
					} else if (masterAccountId[1].equals("1025")) {
						accountCurrency = "   ";
					} else if (masterAccountId[1].equals("4040")) {
						accountCurrency = "HKD";
					} else if (masterAccountId[1].equals("4050")) {
						accountCurrency = "USD";
					} else if (masterAccountId[1].equals("4060")) {
						accountCurrency = "CNY";
					}
			}
			}
			macData = internalAccountNo + accountCurrency + masterAccountId[0];
		
		}

		if (taskId != null && (taskId.equalsIgnoreCase("EB_F_BP") || taskId.equalsIgnoreCase("EB_F_BP_LR")
				|| taskId.equalsIgnoreCase("EB_F_BP_HR"))) {
			if (requestDTO instanceof BillPaymentDTO) {
				String isMacDeployed = CustomConfigUtil.readConfigValue("IS_BILL_PAY_MAC_DEPLOYED", "N");
				if ("Y".equals(isMacDeployed)) {
				BillPaymentDTO request = (BillPaymentDTO) requestDTO;
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				CurrentAccountDTO currentAccountDTO = getAccountDetails(request.getDebitAccount().getValue());
				String compCode = CZAccountHelper.rightPadStringNChars(currentAccountDTO.getCompanyCode(), 4);
				String acCurr = null;
				if(currentAccountDTO.getCurrencyCode().split("~").length>0) {
					if ("HKD".equals(currentAccountDTO.getCurrencyCode().split("~")[0])) {
					acCurr = currentAccountDTO.getCurrencyCode().split("~")[0];
					}else {
						acCurr = request.getBillAmount().getCurrency();
					}
				}else {
					acCurr = request.getBillAmount().getCurrency();
				}
				String accNo = null;
				if(request.getDebitAccount().getValue().split("~").length>0) {
				 accNo = request.getDebitAccount().getValue().split("~")[0];
				}
				String debitCurr = request.getBillAmount().getCurrency();
				BigDecimal macAmount = request.getBillAmount().getAmount();
				macAmount = macAmount.multiply(BigDecimal.valueOf(100));
				String padMacAmount = macAmount.toString();
				padMacAmount = CZAccountHelper.leftPadStringNZeroes(padMacAmount, 15);
				String data6 = "HKD";
				String data7 = "000000000000000000";
				String collectionAmt = "000000000000000";
				String merchantId = request.getBillerId();
				String billPayAcc = null;
				List<BillPaymentRelDetailsDTO> billPayAccList = request.getBillPaymentRelDetails();
				for (BillPaymentRelDetailsDTO billPaymentRelDetailsDTO : billPayAccList) {
					billPayAcc = billPaymentRelDetailsDTO.getValue().toString();
				}
				String padBillPayAcc = CZAccountHelper.rightPadStringNChars(billPayAcc, 25);
				String billType = CZAccountHelper
						.getFieldFromDarray(request.getDictionaryArray(), "com.ofss.digx.cz.bea.domain.ebpp.entity.billpayment.CZBillPaymentDomain.BillType");

				if(billType==null || "".equals(billType)){
					billType =CZAccountHelper.rightPadStringNChars(billType, 2) + "~";
				}
				macTempData.append(internalAccountNo);
				macTempData.append(compCode);
				macTempData.append(acCurr);
				macTempData.append(accNo);
				macTempData.append(debitCurr);
				macTempData.append(padMacAmount);
				macTempData.append(data6);
				macTempData.append(data7);
				macTempData.append(debitCurr);
				macTempData.append(collectionAmt);
				macTempData.append(merchantId);
				macTempData.append(padBillPayAcc);
				macTempData.append(billType);
				macData = macTempData.toString();
				System.out.println("CZTransactionExt MacData Generated from backend :  macData = " + macData );
				}
				BillPaymentDTO request = (BillPaymentDTO) requestDTO;
				if (request.isPayLater()) {
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");
					macTempData.append(internalAccountNo);
					macTempData.append(macData);

					macData = macTempData.toString();

					System.out.println("MAC data for bill payment pay later is " + macTempData.toString());
				}

				System.out.println("MAC data for bill payment pay now is " + macData);

			}

		}
		if (taskId != null && (taskId.equalsIgnoreCase("EADESTMTC") || taskId.equalsIgnoreCase("EADESTMTU"))
				&& !isAdmin) {
			if (requestDTO instanceof AcctPreferenceRequestDTO) {
				String partyID = finalPartyId;
				if (partyID != null) {
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");
					macTempData.append(internalAccountNo);
					AcctPreferenceRequestDTO request = (AcctPreferenceRequestDTO) requestDTO;
					List<AcctPreferenceDTO> requestList = request.getAccountPreferenceDtos().stream()
							.filter(n -> !n.getAccountNumber().getValue().contains("SC")).collect(Collectors.toList());

					if (requestList != null && requestList.size() > 0) {
						System.out.println("postCheckApprovals requestList:" + SerializationUtils.toJsonString(requestList));
						AcctPreferenceDTO acctPreferenceDto = requestList.get(0);
						String statementSetting = acctPreferenceDto.getStatementSetting();
						macTempData.append("    "); // compcode
						macTempData.append("   "); // curr code

						String accountNumber = CZAccountHelper
								.rightPadStringNChars(acctPreferenceDto.getAccountNumber().getValue(), 18);
						macTempData.append(accountNumber);
						String estatmentFlag = statementSetting.equalsIgnoreCase("PEST") ? "B"
								: statementSetting.equalsIgnoreCase("EST") ? "E" : "P";
						macTempData.append(estatmentFlag);
					}
					System.out.println("Statement setting MAC data is " + macTempData.toString());
					macData = macTempData.toString();
				}
			}
		}

		String isMACMocked = CustomConfigUtil.readConfigValue("MOCK_MACK", "Y");
		String debitAcCurCode = null;
		String creditAcCurCode = null;
		String BLANK_DEBIT_AC_CUR_CODE = "   ";
		String BLANK_CREDIT_AC_CUR_CODE = "   ";
		if (!"Y".equalsIgnoreCase(isMACMocked)) {
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNSFT") || taskId.equalsIgnoreCase("PC_F_CRNSFT_SI"))) {
				// Own Account Transfer

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;

					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					creditAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), DEST_ACCT_CURRENCY);

					Date newDate = getNewValueDate(request, debitAcCurCode, creditAcCurCode,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}
					CurrentAccountDTO currentAccountDTOCr = AccountDetailsHelper.getAccountDetailsMcy(creditAcctId);

					String cibInternalAccount = internalAccountNo;
					String debitCompCode = null;
					debitCompCode = currentAccountDTO.getCompanyCode();

					if (debitAcCurCode == null) {
						debitAcCurCode = BLANK_DEBIT_AC_CUR_CODE;
					}
					String debitAccount = null;
					debitAccount = currentAccountDTO.getAccount();
					String debitAmountCurrency = null;
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String debitAmount = null;
					String creditAcCompanyCode = null;
					creditAcCompanyCode = currentAccountDTOCr.getCompanyCode();

					if (creditAcCurCode == null) {
						creditAcCurCode = BLANK_CREDIT_AC_CUR_CODE;
					}
					String creditAccount = null;
					creditAccount = currentAccountDTOCr.getAccount();
					String creditAmountCurrency = null;
					creditAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String creditAmount = null;

					if (debitAmountCurrency.equalsIgnoreCase(debitAcCurCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					} else if (debitAmountCurrency.equalsIgnoreCase(creditAcCurCode)) {
						debitAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
						creditAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					}

					macTempData.append(cibInternalAccount);
					if (isSI) {
						macTempData.append(cibInternalAccount);
					}
					macTempData.append(debitCompCode).append(debitAcCurCode).append(debitAccount)
							.append(debitAmountCurrency);
					macTempData.append(debitAmount).append(creditAcCompanyCode).append(creditAcCurCode)
							.append(creditAccount);
					macTempData.append(creditAmountCurrency).append(creditAmount);
					System.out.println("MAC own account data is" + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Own Acc - Source Currency ==" + debitAcCurCode);
					System.out.println("Own Acc - Dest Currency ==" + creditAcCurCode);
					System.out.println("Own Acc - Transfer Currency ==" + creditAmountCurrency);

					if (!debitAcCurCode.equalsIgnoreCase(creditAcCurCode)) {
						fxIndicator = "Y";
					} else if (!debitAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else if (!creditAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else {
						fxIndicator = "N";
					}
				}

			}

			if (taskId != null
					&& (taskId.equalsIgnoreCase("FT_F_PFR") || taskId.equalsIgnoreCase("FT_F_PFR"))) {
				// fX Agree Rate

				if (requestDTO instanceof FxAgreeOWNCRUDDomainDTO) {

					System.out.println("fxagree cztransaction inside instanof FxAgreeOWNCRUDDomainDTO");
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					FxAgreeOWNCRUDDomainDTO request = (FxAgreeOWNCRUDDomainDTO) requestDTO;

					//debitAcCurCode = CZAccountHelper
					//		.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					//creditAcCurCode = CZAccountHelper
					//		.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), DEST_ACCT_CURRENCY);


					debitAcCurCode =request.getDrAccCurr();

					creditAcCurCode = request.getCrAccCurr();

					//Date newDate = getNewValueDate(request, debitAcCurCode, creditAcCurCode,
					//		request.getPaymentDetails().getPaymentDate());

					Date newDate = BranchDateHelper.getCurrentDate();
					boolean isSI = false;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getDebitAccountId());

					String creditAcctId = request.getCreditAccountId();

					CurrentAccountDTO currentAccountDTOCr = AccountDetailsHelper.getAccountDetailsMcy(creditAcctId);

					String cibInternalAccount = internalAccountNo;
					String debitCompCode = null;
					debitCompCode = currentAccountDTO.getCompanyCode();

					if (debitAcCurCode == null) {
						debitAcCurCode = BLANK_DEBIT_AC_CUR_CODE;
					}
					String debitAccount = null;
					debitAccount = currentAccountDTO.getAccount();
					String debitAmountCurrency = null;
					debitAmountCurrency = request.getDebitCurrency();
					String debitAmount = null;
					String creditAcCompanyCode = null;
					creditAcCompanyCode = currentAccountDTOCr.getCompanyCode();

					if (creditAcCurCode == null) {
						creditAcCurCode = BLANK_CREDIT_AC_CUR_CODE;
					}
					String creditAccount = null;
					creditAccount = currentAccountDTOCr.getAccount();
					String creditAmountCurrency = null;
					creditAmountCurrency = request.getDebitCurrency();
					String creditAmount = null;

					if (null==request.getTreasuryReference() || "".equalsIgnoreCase(request.getTreasuryReference()))
					{
						System.out.println("FXAgree mac data fx non agree flow");

					if (debitAmountCurrency.equalsIgnoreCase(debitAcCurCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(request.getDebitAmount()), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					} else if (debitAmountCurrency.equalsIgnoreCase(creditAcCurCode)) {
						debitAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
						creditAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(request.getDebitAmount()), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(request.getDebitAmount()), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					}
					}
					else
					{
						System.out.println("FXAgree mac data fx agree flow");

						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(request.getDebitAmount()), "0000000000000.00");
						creditAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(request.getCreditAmount()), "0000000000000.00");

						creditAmountCurrency = request.getCreditCurrency();
					}

					macTempData.append(cibInternalAccount);
					if (isSI) {
						macTempData.append(cibInternalAccount);
					}
					macTempData.append(debitCompCode).append(debitAcCurCode).append(debitAccount)
							.append(debitAmountCurrency);
					macTempData.append(debitAmount).append(creditAcCompanyCode).append(creditAcCurCode)
							.append(creditAccount);
					macTempData.append(creditAmountCurrency).append(creditAmount);
					System.out.println("MAC own account data is" + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("FX AGAREE Own Acc - Source Currency ==" + debitAcCurCode);
					System.out.println("FX AGAREE Own Acc - Dest Currency ==" + creditAcCurCode);
					System.out.println("FX AGREE Own Acc - Transfer Currency ==" + creditAmountCurrency);

					if (!debitAcCurCode.equalsIgnoreCase(creditAcCurCode)) {
						fxIndicator = "Y";
					} else if (!debitAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else if (!creditAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else {
						fxIndicator = "N";
					}
				}

			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNIFT") || taskId.equalsIgnoreCase("PC_F_CRNIFT_SI"))) {

				// Internal Transfer

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					creditAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), DEST_ACCT_CURRENCY);

					Date newDate = getNewValueDate(request, debitAcCurCode, creditAcCurCode,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String cibInternalAccount = internalAccountNo;
					String debitCompCode = null;
					debitCompCode = currentAccountDTO.getCompanyCode();

					if (debitAcCurCode == null) {
						debitAcCurCode = BLANK_DEBIT_AC_CUR_CODE;
					}
					String debitAccount = null;
					debitAccount = currentAccountDTO.getAccount();
					String debitAmountCurrency = null;
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String debitAmount = null;
					String creditAcCompanyCode = "0000";

					if (creditAcCurCode == null) {
						creditAcCurCode = BLANK_CREDIT_AC_CUR_CODE;
					}
					String creditAccount = null;

					String prodCode = DArrayUtils.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(),
							"PayeeAcctSubType");
					System.out.println("CZTransactionExt ::: Line 807  The prodCode from screen has been set as "+prodCode);
					if (prodCode==null || "".equals(prodCode) ){

						prodCode="6805"; //The default value of prodcode is 6805

						System.out.println("CZTransactionExt ::: Line 812 The prodCode from screen is null hence has been defaulted as "+prodCode);
					}
					String BEA_INT_ACCT_NO = CZAccountHelper.ext2intAccNo(creditAcctId,
							CZAccountHelper.convPrdcode2EASATypeAIO(prodCode));
					System.out.println("CZTransactionExt BEA_INT_ACCT_NO=" + BEA_INT_ACCT_NO + " debitAcCurCode="
							+ debitAcCurCode + " creditAcCurCode=" + creditAcCurCode);
					creditAccount = CZAccountHelper.rightPadStringNChars(BEA_INT_ACCT_NO, 18);// creditAcctId
					String creditAmountCurrency = null;
					creditAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String creditAmount = null;

					if (debitAmountCurrency.equalsIgnoreCase(debitAcCurCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					} else if (debitAmountCurrency.equalsIgnoreCase(creditAcCurCode)) {
						debitAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
						creditAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					}

					macTempData.append(cibInternalAccount);
					if (isSI) {
						macTempData.append(cibInternalAccount);
					}
					macTempData.append(debitCompCode).append(debitAcCurCode).append(debitAccount)
							.append(debitAmountCurrency);
					macTempData.append(debitAmount).append(creditAcCompanyCode).append(creditAcCurCode)
							.append(creditAccount);
					macTempData.append(creditAmountCurrency).append(creditAmount);

					System.out.println("MAC internal transfer data is" + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Internal Transfer - Source Currency ==" + debitAcCurCode);
					System.out.println("Internal Transfer - Dest Currency ==" + creditAcCurCode);
					System.out.println("Internal Transfer - Transfer Currency ==" + creditAmountCurrency);

					if (!debitAcCurCode.equalsIgnoreCase(creditAcCurCode)) {
						fxIndicator = "Y";
					} else if (!creditAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else if (!creditAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else {
						fxIndicator = "N";
					}

				}
			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_GCRNIFT") || taskId.equalsIgnoreCase("PC_F_GCRNIFT_SI"))) {
				// Adhoc Internal Transfer

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;

					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					creditAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), DEST_ACCT_CURRENCY);
					Date newDate = getNewValueDate(request, debitAcCurCode, creditAcCurCode,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String cibInternalAccount = internalAccountNo;
					String debitCompCode = null;
					debitCompCode = currentAccountDTO.getCompanyCode();

					if (debitAcCurCode == null) {
						debitAcCurCode = BLANK_DEBIT_AC_CUR_CODE;
					}

					String debitAccount = null;
					debitAccount = currentAccountDTO.getAccount();
					String debitAmountCurrency = null;
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String debitAmount = null;
					String creditAcCompanyCode = "0000";

					if (creditAcCurCode == null) {
						creditAcCurCode = BLANK_CREDIT_AC_CUR_CODE;
					}

					String creditAccount = null;

					String prodCode = DArrayUtils.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(),
							"PayeeAcctSubType");
					System.out.println("CZTransactionExt ::: Line 928  The prodCode from screen has been set as "+prodCode);
					if (prodCode==null || "".equals(prodCode) ){

						prodCode="6805"; //The default value of prodcode is 6805

						System.out.println("CZTransactionExt ::: Line 933 The prodCode from screen is null hence has been defaulted as "+prodCode);
					}

					String BEA_INT_ACCT_NO = CZAccountHelper.ext2intAccNo(creditAcctId,
							CZAccountHelper.convPrdcode2EASATypeAIO(prodCode));
					System.out.println("CZTransactionExt BEA_INT_ACCT_NO=" + BEA_INT_ACCT_NO + " debitAcCurCode="
							+ debitAcCurCode + " creditAcCurCode=" + creditAcCurCode);
					creditAccount = CZAccountHelper.rightPadStringNChars(BEA_INT_ACCT_NO, 18);// creditAcctId

					System.out.println("CZTransactionExt BEA_INT_ACCT_NO=" + BEA_INT_ACCT_NO);
					creditAccount = CZAccountHelper.rightPadStringNChars(BEA_INT_ACCT_NO, 18);// was credit account id
					String creditAmountCurrency = null;
					creditAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					String creditAmount = null;

					if (debitAmountCurrency.equalsIgnoreCase(debitAcCurCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					} else if (debitAmountCurrency.equalsIgnoreCase(creditAcCurCode)) {
						debitAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
						creditAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						creditAmount = CZAccountHelper.leftPadStringNZeroes("0", 15);
					}

					macTempData.append(cibInternalAccount);
					if (isSI) {
						macTempData.append(cibInternalAccount);
					}
					macTempData.append(debitCompCode).append(debitAcCurCode).append(debitAccount)
							.append(debitAmountCurrency);
					macTempData.append(debitAmount).append(creditAcCompanyCode).append(creditAcCurCode)
							.append(creditAccount);
					macTempData.append(creditAmountCurrency).append(creditAmount);

					System.out.println("MAC addhoc internal data is" + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Adhoc Internal Transfer - Source Currency ==" + debitAcCurCode);
					System.out.println("Adhoc Internal Transfer - Dest Currency ==" + creditAcCurCode);
					System.out.println("Adhoc Internal Transfer - Transfer Currency ==" + creditAmountCurrency);

					if (!debitAcCurCode.equalsIgnoreCase(creditAcCurCode)) {
						fxIndicator = "Y";
					} else if (!debitAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else if (!creditAcCurCode.equalsIgnoreCase(creditAmountCurrency)) {
						fxIndicator = "Y";
					} else {
						fxIndicator = "N";
					}

				}
			}

			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNBCT") || taskId.equalsIgnoreCase("PC_F_CRNBCT_SI"))) {
				// BEA China Transfer
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String cibInternalTransfer = internalAccountNo;
				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
					String relatedCurrencyCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					Date newDate = getNewValueDate(request, relatedCurrencyCode, null,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					String relatedAccount = currentAccountDTO.getAccount();
					String tfrCcy = request.getPaymentDetails().getAmount().getCurrency();

					String debitAmountCurrency = relatedCurrencyCode;// request.getPaymentDetails().getAmount().getCurrency();
					String debitAmount = null;
					if (tfrCcy.equalsIgnoreCase(relatedCurrencyCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.getDummyStringOfNChars(15, "0");
					}

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String chinaAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 20);

					macTempData.append(cibInternalTransfer);
					if (isSI) {
						macTempData.append(cibInternalTransfer);
					}
					macTempData.append(relatedCurrencyCode).append(relatedAccount).append(debitAmountCurrency);
					macTempData.append(debitAmount).append(chinaAccount);

					System.out.println("MAC data for BEA china is " + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("BEA China Transfer - Source Currency ==" + relatedCurrencyCode);
					System.out.println("BEA China Transfer - Dest Currency ==" + creditAcCurCode);
					System.out.println("BEA China Transfer - Transfer Currency ==" + tfrCcy);

					fxIndicator = CZCommonValidation.isFXTxn(relatedCurrencyCode, tfrCcy, null) ? "Y" : "N";

				}

			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_GCRNBCT") || taskId.equalsIgnoreCase("PC_F_GCRNBCT_SI"))) {
				// Adhoc BEA China Transfer
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String cibInternalTransfer = internalAccountNo;
				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;

					String relatedCurrencyCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					Date newDate = getNewValueDate(request, relatedCurrencyCode, null,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					String relatedAccount = currentAccountDTO.getAccount();
					String tfrCcy = request.getPaymentDetails().getAmount().getCurrency();

					String debitAmountCurrency = relatedCurrencyCode;// request.getPaymentDetails().getAmount().getCurrency();
					String debitAmount = null;
					if (tfrCcy.equalsIgnoreCase(relatedCurrencyCode)) {
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
					} else {
						debitAmount = CZAccountHelper.getDummyStringOfNChars(15, "0");
					}

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String chinaAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 20);

					macTempData.append(cibInternalTransfer);
					if (isSI) {
						macTempData.append(cibInternalTransfer);
					}
					macTempData.append(relatedCurrencyCode).append(relatedAccount).append(debitAmountCurrency);
					macTempData.append(debitAmount).append(chinaAccount);

					System.out.println("MAC data for addhoc BEA china is " + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Adhoc BEA China Transfer - Source Currency ==" + relatedCurrencyCode);
					System.out.println("Adhoc BEA China Transfer - Dest Currency ==" + creditAcCurCode);
					System.out.println("Adhoc BEA China Transfer - Transfer Currency ==" + tfrCcy);

					fxIndicator = CZCommonValidation.isFXTxn(relatedCurrencyCode, tfrCcy, null) ? "Y" : "N";

				}
			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNDFT") || taskId.equalsIgnoreCase("PC_F_CRNDFT_SI"))) {
				// Domestic Payment
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String debitAccount = null;
				String debitAmountCurrency = null;
				String debitAmount = null;

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());
					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					Date newDate = getNewValueDate(request, debitAcCurCode, null,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					debitAccount = currentAccountDTO.getAccount();
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
							request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String chatsAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 16);
					macTempData.append(internalAccountNo);
					if (isSI) {
						macTempData.append(internalAccountNo);
					}
					macTempData.append(debitAcCurCode).append(debitAccount).append(debitAmountCurrency);
					macTempData.append(debitAmount).append(chatsAccount);

					System.out.println("MAC data for BEA CHATS is " + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Domestic Payment - Source Currency ==" + debitAcCurCode);
					System.out.println("Domestic Payment - Dest Currency ==" + creditAcCurCode);
					System.out.println("Domestic Payment - Transfer Currency ==" + debitAmountCurrency);

					fxIndicator = CZCommonValidation.isFXTxn(debitAcCurCode, debitAmountCurrency, null) ? "Y" : "N";

				}

			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_GCRNDFT") || taskId.equalsIgnoreCase("PC_F_GCRNDFT_SI"))) {
				// Adhoc Domestic Payment
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String debitAccount = null;
				String debitAmountCurrency = null;
				String debitAmount = null;

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());
					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					Date newDate = getNewValueDate(request, debitAcCurCode, null,
							request.getPaymentDetails().getPaymentDate());
					boolean isSI = CZDateUtils.compareDateWoTime(
							newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
							BranchDateHelper.getCurrentDate()) > 0;
					System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
							+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

					debitAccount = currentAccountDTO.getAccount();
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
							request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");

					String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
							&& request.getPaymentDetails().getBeneficiary().length > 0
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
							&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
									? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
									: null;
					if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
						creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
										: null;
					}

					String chatsAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 16);
					macTempData.append(internalAccountNo);
					if (isSI) {
						macTempData.append(internalAccountNo);
					}
					macTempData.append(debitAcCurCode).append(debitAccount).append(debitAmountCurrency);
					macTempData.append(debitAmount).append(chatsAccount);
					System.out.println("MAC data for addhoc BEA CHATS is " + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Adhoc Domestic Payment - Source Currency ==" + debitAcCurCode);
					System.out.println("Adhoc Domestic Payment - Dest Currency ==" + creditAcCurCode);
					System.out.println("Adhoc Domestic Payment - Transfer Currency ==" + debitAmountCurrency);

					fxIndicator = CZCommonValidation.isFXTxn(debitAcCurCode, debitAmountCurrency, null) ? "Y" : "N";
				}

			}

			if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {
				System.out.println("Resetting ThreadAtrribute of outwardFlag for CZTransactionEXT");
				NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
				String isOutwardCriteria = CZAccountHelper
						.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), "outwardValidationFlag");
				ThreadAttribute.set("IS_OUTWARD_CRITERIA", isOutwardCriteria);
				System.out.println(
						"CZTransactionExt setting ThreadAttribute for isOutwardCriteria as " + isOutwardCriteria);
			}
			// String isOutwardCriteria =
			// CustomConfigUtil.readConfigValue("IS_OUTWARD_CRITERIA", "N");
			String isOutwardCriteria = null;
			if (ThreadAttribute.get("IS_OUTWARD_CRITERIA") != null) {
				isOutwardCriteria = (String) ThreadAttribute.get("IS_OUTWARD_CRITERIA");
			}
			System.out.println("CZTransactionExt The Value of Criteria IS_OUTWARD_CRITERIA" + isOutwardCriteria);
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNINFT") || taskId.equalsIgnoreCase("PC_F_CRNINFT_SI"))) {
				// Outward MAC Generation
				// BEA Mac Generation Logic
				if (isOutwardCriteria != null && "Y".equalsIgnoreCase(isOutwardCriteria)) {
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					String cibInternalTransfer = internalAccountNo;
					if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {
						System.out.println("Auto Route Mac generation in progress");
						NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
						String relatedCurrencyCode = CZAccountHelper.getFieldFromDarray(
								request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
						Date newDate = getNewValueDate(request, relatedCurrencyCode, null,
								request.getPaymentDetails().getPaymentDate());
						boolean isSI = CZDateUtils.compareDateWoTime(
								newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
								BranchDateHelper.getCurrentDate()) > 0;
						System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
								+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

						
						//UATP-23203 start
						if (isSI && !taskId.endsWith("_SI")) {
							taskId = taskId.concat("_SI");
							ThreadAttribute.set(ThreadAttribute.CURRENT_TASK, taskId);
							System.out.println("CZTransactionExt update taskId to " + taskId);
						}
						//UATP-23203 end
						
						
						CurrentAccountDTO currentAccountDTO = getAccountDetails(
								request.getPaymentDetails().getDebitAccountId().getValue());

						String relatedAccount = currentAccountDTO.getAccount();
						String tfrCcy = request.getPaymentDetails().getAmount().getCurrency();

						String debitAmountCurrency = relatedCurrencyCode;// request.getPaymentDetails().getAmount().getCurrency();
						String debitAmount = null;
						if (tfrCcy.equalsIgnoreCase(relatedCurrencyCode)) {
							debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
									request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						} else {
							debitAmount = CZAccountHelper.getDummyStringOfNChars(15, "0");
						}

						String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
										: null;
						if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
							creditAcctId = request.getPaymentDetails().getBeneficiary() != null
									&& request.getPaymentDetails().getBeneficiary().length > 0
											? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
											: null;
						}

						String chinaAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 20);

						macTempData.append(cibInternalTransfer);
						if (isSI) {
							macTempData.append(cibInternalTransfer);
						}
						macTempData.append(relatedCurrencyCode).append(relatedAccount).append(debitAmountCurrency);
						macTempData.append(debitAmount).append(chinaAccount);

						System.out.println("MAC data for Auto Routed Overseas Txn is " + macTempData.toString());
						macData = macTempData.toString();

						System.out.println("Auto Routed Overseas Transfer - Source Currency ==" + relatedCurrencyCode);
						System.out.println("Auto Routed Overseas Transfer - Dest Currency ==" + creditAcCurCode);
						System.out.println("Auto Routed Overseas Transfer - Transfer Currency ==" + tfrCcy);

						fxIndicator = CZCommonValidation.isFXTxn(relatedCurrencyCode, tfrCcy, null) ? "Y" : "N";

					}

				} else {
					// International Payment
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					String debitAccount = null;
					String benefAccount = null;
					String debitAmountCurrency = null;
					String debitAmount = null;
					String executionDate = null;
					String newExecutionDate = null;

					if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

						NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
						CurrentAccountDTO currentAccountDTO = getAccountDetails(
								request.getPaymentDetails().getDebitAccountId().getValue());
						debitAcCurCode = CZAccountHelper.getFieldFromDarray(
								request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
						Date payNowDate = CZCommonUtils.handlePayNowDate(request.getPaymentDetails().getPaymentDate());
						Date newDate = getNewValueDate(request, debitAcCurCode, null,
								request.getPaymentDetails().getPaymentDate());
						System.out.println("CZTransactionExt CRNINFT Block NewDate Returned:" + newDate
								+ "HandlePaynowDate Reurned=" + payNowDate);
						// Checking payment Date and setting Value if 1970 date
						if (payNowDate != null) {
							newDate = getNewValueDate(request, debitAcCurCode, null, payNowDate);
							System.out.println("Updated NewDate is " + newDate);
						}

						boolean isSI = CZDateUtils.compareDateWoTime(
								newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
								BranchDateHelper.getCurrentDate()) > 0;
						System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
								+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

						//UATP-23203 start
						if (isSI && !taskId.endsWith("_SI")) {
							taskId = taskId.concat("_SI");
							ThreadAttribute.set(ThreadAttribute.CURRENT_TASK, taskId);
							System.out.println("CZTransactionExt update taskId to " + taskId);
						}
						//UATP-23203 end
						
						debitAccount = currentAccountDTO.getAccount();
						String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
										: null;

						debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");

						if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
							creditAcctId = request.getPaymentDetails().getBeneficiary() != null
									&& request.getPaymentDetails().getBeneficiary().length > 0
											? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
											: null;
						}

						benefAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 35);
						executionDate = CZAccountHelper
								.fcDateToBeaRequestString(request.getPaymentDetails().getPaymentDate(), "yyyyMMdd");
						if (payNowDate != null) {
							executionDate = CZAccountHelper.fcDateToBeaRequestString(payNowDate, "yyyyMMdd");
							System.out.println("Updated ExecutionDate is " + executionDate);
						}
						macTempData.append(internalAccountNo);
						if (isSI) {
							macTempData.append(internalAccountNo);
							if (newDate == null) {
								newExecutionDate = executionDate;
							} else {
								newExecutionDate = CZAccountHelper.fcDateToBeaRequestString(newDate, "yyyyMMdd");
							}
						}
						macTempData.append(debitAcCurCode).append(debitAccount).append(benefAccount);
						macTempData.append(debitAmountCurrency).append(debitAmount)
								.append(!isSI ? executionDate : newExecutionDate);
						System.out.println("MAC data for international payment is " + macTempData.toString());
						macData = macTempData.toString();

						System.out.println("International Payment - Source Currency ==" + debitAcCurCode);
						System.out.println("International Payment - Dest Currency ==" + creditAcCurCode);
						System.out.println("International Payment - Transfer Currency ==" + debitAmountCurrency);

						fxIndicator = CZCommonValidation.isFXTxn(debitAcCurCode, debitAmountCurrency, null) ? "Y" : "N";
					}
				}
			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_GCRNINFT") || taskId.equalsIgnoreCase("PC_F_GCRNINFT_SI"))) {
				// Outward MAC Generation
				// BEA Mac Generation Logic
				if (isOutwardCriteria != null && "Y".equalsIgnoreCase(isOutwardCriteria)) {
					// Adhoc BEA China Transfer
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					String cibInternalTransfer = internalAccountNo;
					if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

						NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;

						String relatedCurrencyCode = CZAccountHelper.getFieldFromDarray(
								request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
						Date newDate = getNewValueDate(request, relatedCurrencyCode, null,
								request.getPaymentDetails().getPaymentDate());
						boolean isSI = CZDateUtils.compareDateWoTime(
								newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
								BranchDateHelper.getCurrentDate()) > 0;
						System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
								+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

						
						//UATP-23203 start
						if (isSI && !taskId.endsWith("_SI")) {
							taskId = taskId.concat("_SI");
							ThreadAttribute.set(ThreadAttribute.CURRENT_TASK, taskId);
							System.out.println("CZTransactionExt update taskId to " + taskId);
						}
						//UATP-23203 end
						
						
						CurrentAccountDTO currentAccountDTO = getAccountDetails(
								request.getPaymentDetails().getDebitAccountId().getValue());

						String relatedAccount = currentAccountDTO.getAccount();
						String tfrCcy = request.getPaymentDetails().getAmount().getCurrency();

						String debitAmountCurrency = relatedCurrencyCode;// request.getPaymentDetails().getAmount().getCurrency();
						String debitAmount = null;
						if (tfrCcy.equalsIgnoreCase(relatedCurrencyCode)) {
							debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
									request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");
						} else {
							debitAmount = CZAccountHelper.getDummyStringOfNChars(15, "0");
						}

						String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
										: null;
						if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
							creditAcctId = request.getPaymentDetails().getBeneficiary() != null
									&& request.getPaymentDetails().getBeneficiary().length > 0
											? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
											: null;
						}

						String chinaAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 20);

						macTempData.append(cibInternalTransfer);
						if (isSI) {
							macTempData.append(cibInternalTransfer);
						}
						macTempData.append(relatedCurrencyCode).append(relatedAccount).append(debitAmountCurrency);
						macTempData.append(debitAmount).append(chinaAccount);

						System.out.println("MAC data for addhoc BEA china is " + macTempData.toString());
						macData = macTempData.toString();

						System.out.println("Auto Route BEA China Transfer - Source Currency ==" + relatedCurrencyCode);
						System.out.println("Auto Route BEA China Transfer - Dest Currency ==" + creditAcCurCode);
						System.out.println("Auto Route BEA China Transfer - Transfer Currency ==" + tfrCcy);

						fxIndicator = CZCommonValidation.isFXTxn(relatedCurrencyCode, tfrCcy, null) ? "Y" : "N";

					}

				} else {
					// Adhoc International Payment
					String partyID = finalPartyId;
					String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

					String debitAccount = null;
					String benefAccount = null;
					String debitAmountCurrency = null;
					String debitAmount = null;
					String executionDate = null;
					String newExecutionDate = null;

					if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

						NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
						CurrentAccountDTO currentAccountDTO = getAccountDetails(
								request.getPaymentDetails().getDebitAccountId().getValue());
						debitAcCurCode = CZAccountHelper.getFieldFromDarray(
								request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
						Date payNowDate = CZCommonUtils.handlePayNowDate(request.getPaymentDetails().getPaymentDate());
						Date newDate = getNewValueDate(request, debitAcCurCode, null,
								request.getPaymentDetails().getPaymentDate());
						System.out.println("CZTransactionExt GCRNINFT Block NewDate Returned:" + newDate
								+ "HandlePaynowDate Reurned=" + payNowDate);
						// Checking payment Date and setting Value if 1970 date
						if (payNowDate != null) {
							newDate = getNewValueDate(request, debitAcCurCode, null, payNowDate);
							System.out.println("Updated NewDate is " + newDate);
						}
						boolean isSI = CZDateUtils.compareDateWoTime(
								newDate != null ? newDate : request.getPaymentDetails().getPaymentDate(),
								BranchDateHelper.getCurrentDate()) > 0;
						System.out.println("CZTransactionExt taskId=" + taskId + " isSI=" + isSI + " paymentDate="
								+ request.getPaymentDetails().getPaymentDate() + " valueDate=" + newDate);

						
						//UATP-23203 start
						if (isSI && !taskId.endsWith("_SI")) {
							taskId = taskId.concat("_SI");
							ThreadAttribute.set(ThreadAttribute.CURRENT_TASK, taskId);
							System.out.println("CZTransactionExt update taskId to " + taskId);
						}
						//UATP-23203 end
						
						
						debitAccount = currentAccountDTO.getAccount();
						String creditAcctId = request.getPaymentDetails().getBeneficiary() != null
								&& request.getPaymentDetails().getBeneficiary().length > 0
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount() != null
								&& request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue() != null
										? request.getPaymentDetails().getBeneficiary()[0].getCreditAccount().getValue()
										: null;

						debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");

						if (InputValidationUtils.isNullOrBlank(creditAcctId)) {
							creditAcctId = request.getPaymentDetails().getBeneficiary() != null
									&& request.getPaymentDetails().getBeneficiary().length > 0
											? request.getPaymentDetails().getBeneficiary()[0].getCreditAccountId()
											: null;
						}

						benefAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 35);
						executionDate = CZAccountHelper
								.fcDateToBeaRequestString(request.getPaymentDetails().getPaymentDate(), "yyyyMMdd");
						if (payNowDate != null) {
							executionDate = CZAccountHelper.fcDateToBeaRequestString(payNowDate, "yyyyMMdd");
							System.out.println("Updated ExecutionDate is " + executionDate);
						}
						macTempData.append(internalAccountNo);
						if (isSI) {
							macTempData.append(internalAccountNo);
							if (newDate == null) {
								newExecutionDate = executionDate;
							} else {
								newExecutionDate = CZAccountHelper.fcDateToBeaRequestString(newDate, "yyyyMMdd");
							}
						}
						macTempData.append(debitAcCurCode).append(debitAccount).append(benefAccount);
						macTempData.append(debitAmountCurrency).append(debitAmount)
								.append(!isSI ? executionDate : newExecutionDate);
						System.out.println("MAC data for addhoc international is " + macTempData.toString());
						macData = macTempData.toString();

						System.out.println("Adhoc International Payment - Source Currency ==" + debitAcCurCode);
						System.out.println("Adhoc International Payment - Dest Currency ==" + creditAcCurCode);
						System.out.println("Adhoc International Payment - Transfer Currency ==" + debitAmountCurrency);

						fxIndicator = CZCommonValidation.isFXTxn(debitAcCurCode, debitAmountCurrency, null) ? "Y" : "N";
					}
				}
			}
			if (taskId != null
					&& (taskId.equalsIgnoreCase("PC_F_CRNDFT_FPS") || taskId.equalsIgnoreCase("PC_F_GCRNDFT_FPS"))) {

				// Domestic FPS Payment
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String debitAccount = null;
				String debitAmountCurrency = null;
				String debitAmount = null;
				String creditAcctId = null;

				if (requestDTO instanceof NetworkPaymentCreateRequestDTO) {

					NetworkPaymentCreateRequestDTO request = (NetworkPaymentCreateRequestDTO) requestDTO;
					CurrentAccountDTO currentAccountDTO = getAccountDetails(
							request.getPaymentDetails().getDebitAccountId().getValue());

					debitAcCurCode = CZAccountHelper
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), SRC_ACCT_CURRENCY);
					debitAccount = currentAccountDTO.getAccount();
					debitAmountCurrency = request.getPaymentDetails().getAmount().getCurrency();
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
							request.getPaymentDetails().getAmount().getAmount(), "0000000000000.00");

					String payeeIdentifn = DArrayUtils
							.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(), PAYEE_IDENTIFN);
					String fpsId = DArrayUtils.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(),
							FPS_ID);
					String mobile = DArrayUtils.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(),
							MOBILE);
					String payeeEmail = DArrayUtils.getFieldFromDarray(request.getPaymentDetails().getDictionaryArray(),
							PAYEE_EMAIL);

					if (CZPaymentConstants.PAYEE_IDENTIFICATION_MOBNO.equalsIgnoreCase(payeeIdentifn)) {
						creditAcctId = mobile;
					} else if (CZPaymentConstants.PAYEE_IDENTIFICATION_EMAILID.equalsIgnoreCase(payeeIdentifn)) {
						creditAcctId = payeeEmail;
					} else if (CZPaymentConstants.PAYEE_IDENTIFICATION_FPSID.equalsIgnoreCase(payeeIdentifn)) {
						creditAcctId = fpsId;
					}

					String fpsAccount = CZAccountHelper.rightPadStringNChars(creditAcctId, 16);
					macTempData.append(internalAccountNo);

					macTempData.append(debitAcCurCode).append(debitAccount).append(debitAmountCurrency);
					macTempData.append(debitAmount).append(fpsAccount);

					System.out.println("MAC data for BEA FPS is " + macTempData.toString());
					macData = macTempData.toString();

					System.out.println("Domestic FPS Payment - Source Currency ==" + debitAcCurCode);
					System.out.println("Domestic FPS Payment - Dest Currency ==" + creditAcCurCode);
					System.out.println("Domestic FPS Payment - Transfer Currency ==" + debitAmountCurrency);

					fxIndicator = CZCommonValidation.isFXTxn(debitAcCurCode, debitAmountCurrency, null) ? "Y" : "N";

				}
			}

			if (taskId != null && (taskId.equalsIgnoreCase("FU_F_APC"))) {

				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				System.out.println(
						"Inside CZ Transaction Ext : ThreadAttribute.get(TASK_CODE)=" + ThreadAttribute.get("TASK_CODE")
								+ "\n" + "ThreadAttribute.get(SIGN_TXN_CODE)=" + ThreadAttribute.get("SIGN_TXN_CODE")
								+ "\n" + "ThreadAttribute.get(SIGN_TXN_REF)=" + ThreadAttribute.get("SIGN_TXN_REF")
								+ "\n" + "ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO)="
								+ ThreadAttribute.get(ThreadAttribute.TRANSACTION_REFERENCE_NO));
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String debitAccount = null;
				String debitAmountCurrency = null;
				String debitAmount = null;
				CurrentAccountDTO currentAccountDTO = null;
				String debitCurrency = null;
				String accountCurrency = null;
				Date executionDate = null;
				String paymentType = null;

				if (requestDTO instanceof BulkFileUploadsDTO) {
					System.out.println("Instance of BulkFileUploadsDTO");
					BulkFileUploadsDTO request = (BulkFileUploadsDTO) requestDTO;
					System.out.println("Debit Account" + request.getDebitAcoount() + "ExecutionDate"
							+ request.getExecutionDate() + request.getTransferCurrency());
					currentAccountDTO = getAccountDetails(request.getDebitAcoount());
					debitCurrency = request.getTransferCurrency();
					executionDate = request.getExecutionDate();

					if (request instanceof BulkFileRecordDTO) {
						System.out.println("Instance of BulkFileRecordDTO");
						BulkFileRecordDTO recordDtls = (BulkFileRecordDTO) request;
						debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(
								new BigDecimal(recordDtls.getTransactionAmount()), "0000000000000.00");
						System.out.println("BulkFileRecordDTO : amount" + recordDtls.getTransactionAmount()
								+ "Formatted : " + debitAmount + "ExecutionDate : " + recordDtls.getExecutionDate()
								+ "PaymentType" + recordDtls.getPaymentType());
						paymentType = recordDtls.getPaymentType();

					}

				}

				if (currentAccountDTO != null) {
					accountCurrency = currentAccountDTO.getCurrencyCode();
					if (accountCurrency != null && accountCurrency.contains("~")) {
						accountCurrency = debitCurrency;
					}
					System.out.println("BranchDate from DB :" + BranchDateHelper.getCurrentDate());
					String currentDateStr = null;
					if (paymentType.equalsIgnoreCase("COLLECTION")) {
						currentDateStr = BranchDateHelper.getCurrentDate().toString("yyyyMMdd");
					} else {
						System.out.println("Calling nextWorkingDate");
						currentDateStr = BranchDateHelper.getNextWorkingDateAPC(BranchDateHelper.getCurrentDate(),
								BranchDateHelper.DF_yyyyMMdd, debitCurrency).toString("yyyyMMdd");
					}
					java.util.Date currentDate = null;
					try {
						currentDate = sdf.parse(currentDateStr);
					} catch (ParseException e) {
						e.printStackTrace();
					}

					String executionDateStr = executionDate.toString("yyyyMMdd");
					java.util.Date executionDateF = null;
					try {
						executionDateF = sdf.parse(executionDateStr);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("Formatted Current Date ====" + currentDate);
					System.out.println("executionDate ====" + executionDateF);

					if (executionDateF.compareTo(currentDate) == 0) {
						macTempData.append(internalAccountNo);
						macTempData.append(accountCurrency);
						macTempData.append(currentAccountDTO.getAccount());
						macTempData.append(debitCurrency).append(debitAmount);

						System.out.println("MAC data for File Upload is For VCAC1 fileupload" + macTempData.toString());
						macData = macTempData.toString();
					} else {
						macTempData.append(internalAccountNo);
						macTempData.append(internalAccountNo);
						macTempData.append(accountCurrency);
						macTempData.append(currentAccountDTO.getAccount());
						macTempData.append(debitCurrency).append(debitAmount);

						System.out.println("MAC data for File Upload is for ZUAC1 fileUpload" + macTempData.toString());
						macData = macTempData.toString();
					}
				}

			}

			if (taskId != null
					&& (taskId.equalsIgnoreCase("BU_AUTOPAY_CREATE") || taskId.equalsIgnoreCase("BU_PAYROLL_CREATE")
							|| taskId.equalsIgnoreCase("BU_COLLECTION_CREATE")|| taskId.equalsIgnoreCase("PC_F_BTFT"))) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				String debitAccount = null;
				String debitAmountCurrency = null;
				String debitAmount = null;
				CurrentAccountDTO currentAccountDTO = null;
				String debitCurrency = null;
				String accountCurrency = null;
				Date executionDate = null;

				if (requestDTO instanceof AutopayRequestDTO) {
					AutopayRequestDTO request = (AutopayRequestDTO) requestDTO;
					System.out.println("Entered in CZTransactionExt AutoPayRequestDTO"
							+ request.getWithdrawalAcctNo().getValue() + request.getTotalAmount());
					currentAccountDTO = getAccountDetails(request.getWithdrawalAcctNo().getValue());
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(request.getTotalAmount(),
							"0000000000000.00");
					debitCurrency = request.getTransactionCurrency();
					executionDate = request.getExecutionDate();

				} else if (requestDTO instanceof PayrollRequestDTO) {
					PayrollRequestDTO request = (PayrollRequestDTO) requestDTO;
					currentAccountDTO = getAccountDetails(request.getWithdrawalAcctNo().getValue());
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(request.getTotalAmount(),
							"0000000000000.00");
					debitCurrency = request.getTransactionCurrency();
					executionDate = request.getExecutionDate();

				} else if (requestDTO instanceof CollectionRequestDTO) {
					CollectionRequestDTO request = (CollectionRequestDTO) requestDTO;
					currentAccountDTO = getAccountDetails(request.getCollectionAccount().getValue());
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(request.getTotalAmount(),
							"0000000000000.00");
					debitCurrency = request.getTransactionCurrency();
					executionDate = request.getExecutionDate();
				}else if(requestDTO instanceof BatchTransferRequestDTO && null!=com.ofss.fc.infra.thread.ThreadAttribute.get("IS_BATCHTRANSFER_PAYOUT")){
					BatchTransferRequestDTO batchTransferRequestDTO = (BatchTransferRequestDTO) requestDTO;
					Object totalAmount = (batchTransferRequestDTO.getTotalAmount().startsWith("{")||batchTransferRequestDTO.getTotalAmount().endsWith("}"))?new JSONObject(batchTransferRequestDTO.getTotalAmount()).get("HKD"):batchTransferRequestDTO.getTotalAmount();
					System.out.println("Entered in CZTransactionExt BatchTransferRequestDTO" + batchTransferRequestDTO.getDebitAccountId().getValue() +":"+ totalAmount);
					currentAccountDTO = getAccountDetails(batchTransferRequestDTO.getDebitAccountId().getValue());
					debitAmount = CZAccountHelper.bigDecimalToBeaRequestString(new BigDecimal(totalAmount+""), "0000000000000.00");
					debitCurrency = batchTransferRequestDTO.getCurrency();
					executionDate = batchTransferRequestDTO.getValueDate();
				}

				if (currentAccountDTO != null) {
					accountCurrency = currentAccountDTO.getCurrencyCode();
					if (accountCurrency != null && accountCurrency.contains("~")) {
						accountCurrency = debitCurrency;
					}

					System.out.println("BranchDate from DB :" + BranchDateHelper.getCurrentDate());
					String currentDateStr = null;
					if (taskId.equalsIgnoreCase("BU_COLLECTION_CREATE")) {
						currentDateStr = BranchDateHelper.getCurrentDate().toString("yyyyMMdd");
					} else {
						System.out.println("Calling nextWorkingDate");
						currentDateStr = BranchDateHelper.getNextWorkingDateAPC(BranchDateHelper.getCurrentDate(),
								BranchDateHelper.DF_yyyyMMdd, debitCurrency).toString("yyyyMMdd");
					}

					java.util.Date currentDate = null;
					try {
						currentDate = sdf.parse(currentDateStr);
					} catch (ParseException e) {
						e.printStackTrace();
					}

					String executionDateStr = executionDate.toString("yyyyMMdd");
					java.util.Date executionDateF = null;
					try {
						executionDateF = sdf.parse(executionDateStr);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("Formatted Current Date in APC online txn====" + currentDate);
					System.out.println("executionDate ====" + executionDateF);

					if (executionDateF.compareTo(currentDate) == 0) {

						macTempData.append(internalAccountNo);
						macTempData.append(accountCurrency);
						macTempData.append(currentAccountDTO.getAccount());
						macTempData.append(debitCurrency).append(debitAmount);

						System.out.println("MAC data for Autopay/payroll/collections online is For VCAC1"
								+ macTempData.toString());
						macData = macTempData.toString();

					} else {
						macTempData.append(internalAccountNo);
						macTempData.append(internalAccountNo);
						macTempData.append(accountCurrency);
						macTempData.append(currentAccountDTO.getAccount());
						macTempData.append(debitCurrency).append(debitAmount);

						System.out.println("MAC data for Autopay/payroll/collections online is for ZUAC1 "
								+ macTempData.toString());
						macData = macTempData.toString();
					}
				}
			}

			System.out.println("FPS TASK CODE==" + taskId);
			if (taskId != null && (taskId.equalsIgnoreCase("MT_N_AFP_FPS") || taskId.equalsIgnoreCase("MT_N_AFP")
					|| taskId.equalsIgnoreCase("MT_N_EFP") || taskId.equalsIgnoreCase("MT_N_RFP")
					|| taskId.equalsIgnoreCase("MT_N_TFP"))) {

				System.out.println("INSIDE FPS TASK CODE==" + taskId);
				// Domestic FPS Addressing
				String partyID = finalPartyId;
				String internalAccountNo = CZAccountHelper.ext2intAccNo(partyID, " ");

				if (requestDTO instanceof FPSAddressingServiceDTO) {

					System.out.println("INSIDE FPS FPSAddressingServiceDTO check==" + taskId);
					FPSAddressingServiceDTO request = (FPSAddressingServiceDTO) requestDTO;
					macTempData.append(internalAccountNo);
					macTempData.append(partyID);

					if (request.getFpsServiceList() != null) {
						for (FPSAddressingServiceListDTO fpsAddressingServiceListDTO : request.getFpsServiceList()) {
							if (fpsAddressingServiceListDTO.getProxyIDValue() != null) {
								macTempData.append(fpsAddressingServiceListDTO.getProxyIDValue());
							}
							if (fpsAddressingServiceListDTO.getProxyIDType() != null) {
								macTempData.append(fpsAddressingServiceListDTO.getProxyIDType().toString());
							}
						}
					}

					System.out.println("MAC data for BEA FPS Addrssing is " + macTempData.toString());
					macData = macTempData.toString();
					com.ofss.digx.infra.thread.ThreadAttribute.set(FPS_MAC_DATA, macData);
				}
			}

		}
		if (!InputValidationUtils.isNullOrBlank(transaction_ref_no) && !InputValidationUtils.isNullOrBlank(bmTxnNo)) {
			// Save the ref no
			HostTransaction transaction = new HostTransaction();
			HostTransactionKey key = new HostTransactionKey();
			transaction.setKey(key);
			transaction.setCreatedBy(sessionContext.getUserId());
			transaction.setCreationDate(new Date());
			transaction.setEbkBMTxnRefNo(bmTxnNo);
			transaction.setObdxTxnRefNo(transaction_ref_no);
			transaction.create(transaction);
		}

		if (!InputValidationUtils.isNullOrBlank(transaction_ref_no) && !InputValidationUtils.isNullOrBlank(obj)) {
			// Save the ref no

			BMTxnResponseDTO response = null;
			try {
				ObjectMapper mapper = new ObjectMapper();
				response = mapper.readValue(obj, BMTxnResponseDTO.class);
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (response != null) {
				List<BMTxnDTO> bmTxnData = response.getBmTxnData();
				for (BMTxnDTO bmTxnDTO : bmTxnData) {
					HostTransaction transaction = new HostTransaction();
					HostTransactionKey key = new HostTransactionKey();
					transaction.setKey(key);
					transaction.setCreatedBy(sessionContext.getUserId());
					transaction.setCreationDate(new Date());
					transaction.setEbkBMTxnRefNo(bmTxnDTO.getTxnRefNo());
					transaction.setObdxTxnRefNo(transaction_ref_no);
					transaction.setTxn_code(bmTxnDTO.getTxnCode());
					transaction.create(transaction);
				}
			}
		}

		if (!InputValidationUtils.isNullOrBlank(macData) && !InputValidationUtils.isNullOrBlank(transaction_ref_no)) {

			Subject subject = SubjectUtil.getCurrentSubject();

			System.out.println("Subject current CZTransactionExt : " + subject);

			String userName = SubjectUtil.getUserName(subject);

			System.out.println("Subject username CZTransactionExt : " + userName + "Session UserName : "
					+ sessionContext.getUserId());

			if (userName == null || userName.equals("")) {
				userName = sessionContext.getUserId();
			}

			String customerID = sessionContext.getTransactingPartyCode();

			// Store data in the MAC Table

			MACData data = new MACData();

			String macArray = macData;
			String[] macDataArray = macArray.split("~");

			System.out.println("Calling handleMacDataCreation Task id is " + taskId + taskId + " macDataArray[0]="
					+ macDataArray[0]);

			data.handleMacDataCreation(userName, macDataArray[0], customerID, transaction_ref_no, fxIndicator);
		}

//		for TD data inserting in db for reports
		String enableMode2Flag = CustomConfigUtil.readConfigValue("ENABLE_MODE2_GLOBAL_FLAG", "N");
		System.out.println("CZTransactionExt enableMode2Flag ============ " + enableMode2Flag);

		if ("Y".equalsIgnoreCase(enableMode2Flag)) {
			System.out.println("CZTransactionExt taskId for TD report data insert " + taskId);
			if (taskId != null && taskId.equalsIgnoreCase("TD_F_OTD")) {
				if (requestDTO instanceof TermDepositAccountDTO) {
					System.out.println("CZTransactionExt taskId for TD report data insert inside ");
					TermDepositAccountDTO tdrequestDTO = (TermDepositAccountDTO) requestDTO;

					String couponCode = DArrayUtils.getFieldFromDarray(tdrequestDTO.getDictionaryArray(),
							"com.ofss.digx.cz.bea.domain.td.entity.account.TermDepositAccount.couponCode");
					System.out.println("CZTransactionExt toTDRateInquiryRequest couponCode " + couponCode);

					String promotionNo = DArrayUtils.getFieldFromDarray(tdrequestDTO.getDictionaryArray(),
							"com.ofss.digx.cz.bea.domain.td.entity.account.TermDepositAccount.promotionNo");
					System.out.println("CZTransactionExt toTDRateInquiryRequest promotionNo " + promotionNo);

					if (couponCode != null && couponCode != "" && promotionNo != null && promotionNo != "") {
						String customerID = sessionContext.getTransactingPartyCode();
						PreferentialTDReportDetails reportdata = new PreferentialTDReportDetails();
						reportdata.saveReportdata(customerID, transaction_ref_no, tdrequestDTO);
					}
				}
			}
		}

		if (taskId != null && taskId.equalsIgnoreCase("MRCH_N_CME")) {
			System.out.println("CZTransactionExt taskId for merchant:  " + taskId);
			if (requestDTO instanceof MerchantDTO) {
				System.out.println("CZTransactionExt taskId for merchant data insert inside ");
				try {
					MerchantDTO mmRequestDTO = (MerchantDTO) requestDTO;
					com.ofss.digx.cz.bea.domain.merchant.entity.Merchant domain = new com.ofss.digx.cz.bea.domain.merchant.entity.Merchant();

					MerchantAssembler assembler = new MerchantAssembler();
					mmRequestDTO.setMerchantId(mmRequestDTO.getMerchantId());
					domain = assembler.toDomainObject(mmRequestDTO);

					if (!validateMerchantCreatePolicy(mmRequestDTO)) {
						if (domain != null) {
							domain.create(domain);
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		if (taskId != null && taskId.equalsIgnoreCase("MRCH_N_CMEM")) {
			System.out.println("CZTransactionExt taskId for merchant mapping:  " + taskId);
			if (requestDTO instanceof MerchantMappingDTO) {
				System.out.println("CZTransactionExt taskId for merchant mapping data insert inside ");
				try {
					MerchantMappingDTO mmRequestDTO = (MerchantMappingDTO) requestDTO;
					com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();

					if (!validateMerchantMappingCreatePolicy(mmRequestDTO)) {
						for (String childMerchantId : mmRequestDTO.getChildMerchantIdList()) {
							MerchantMappingAssembler assembler = new MerchantMappingAssembler();
							mmRequestDTO.setChildMerchantId(childMerchantId);
							domain = assembler.toDomainObject(mmRequestDTO);
							if (domain != null) {
								domain.create(domain);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		if (taskId != null && taskId.equalsIgnoreCase("MRCH_N_UMEM")) {
			System.out.println("CZTransactionExt taskId for editing merchant mapping:  " + taskId);
			if (requestDTO instanceof MerchantMappingDTO) {
				System.out.println("CZTransactionExt taskId for editing merchant mapping data insert inside ");
				try {
					MerchantMappingDTO mmRequestDTO = (MerchantMappingDTO) requestDTO;
					com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();

					List<String> newList = mmRequestDTO.getChildMerchantIdList();
					List<String> merchantmappingdtoList = new ArrayList<>();

					MerchantMappingAssembler assembler = new MerchantMappingAssembler();
					mmRequestDTO.setParentMerchantId(mmRequestDTO.getParentMerchantId());
					domain = assembler.toDomainObject(mmRequestDTO);

					if (!validateMerchantMappingUpdatePoliy(mmRequestDTO)) {
						if (domain != null) {
							for (com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping merchantmapping : domain
									.listValidate(domain)) {
								merchantmappingdtoList.add(merchantmapping.getMerchantMappingKey().getChildMerchantId());
							}

							List<String> oldChildmerchantIdList = merchantmappingdtoList.stream() // need to delete
									.filter(element -> !newList.contains(element)).collect(Collectors.toList());

							for (String o : oldChildmerchantIdList) {
								com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain2 = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();
								domain2 = domain2.readValidate(new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMappingKey(o));
								domain2.setStatus("pendingDelete");
								domain2.update(domain2);
							}

							List<String> newChildmerchantIdList = newList.stream() // need to add
									.filter(element -> !merchantmappingdtoList.contains(element))
									.collect(Collectors.toList());
							for (String o : newChildmerchantIdList) {
								assembler = new MerchantMappingAssembler();
								MerchantMappingDTO m = mmRequestDTO;
								m.setChildMerchantId(o);
								domain.create(assembler.toDomainObject(m));
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		if (!InputValidationUtils.isNullOrBlank(macData) && !InputValidationUtils.isNullOrBlank(xTransactionId)
				&& taskId != null) {

			Subject subject = SubjectUtil.getCurrentSubject();
			String userName = SubjectUtil.getUserName(subject);
			if (userName == null || userName.equals("")) {
				userName = sessionContext.getUserId();
			}
			String customerID = sessionContext.getTransactingPartyCode();
			// Store data in the MAC Table
			MACData data = new MACData();
			String macArray = macData;
			String[] macDataArray = macArray.split("~");
			System.out.println("Calling handleMacDataCreationAfterModification Task id is " + taskId
					+ " macDataArray[0]=" + macDataArray[0]);
			String[] optionalParams = new String[5];

			if (fxIndicator != null) {
				optionalParams[0] = taskId;
				optionalParams[1] = fxIndicator;
			}

			data.handleMacDataCreationAfterModification(userName, macDataArray[0], customerID, xTransactionId,
					optionalParams);
		}
	}

	@Override
	public void postListTransactions(SessionContext sessionContext, TransactionRequestDTO transactionRequestDTO,
			ViewType viewType, ApprovalStatus approvalStatus, TransactionListResponse transactionListResponse)
			throws Exception {

		String isOverrideStatusReqd = CustomConfigUtil.readConfigValue("IS_OVERRIDE_STS_REQD", "N");

		if (transactionListResponse == null || transactionListResponse.getTransactionDTOs() == null
				|| transactionListResponse.getTransactionDTOs().size() < 1) {
			return;
		}
		// Date dt=getCurrentDate(sessionContext);
		// System.out.println("Get Current date is "+ dt.toFormattedString()+"--- "+
		// dt.toString());

		List<String> maskingReqdTask = Arrays.asList(CZPaymentConstants.TASK_CODE_INTERNAL_TRANSFER,
				CZPaymentConstants.TASK_CODE_ADHOC_INTERNAL_TRANSFER, CZPaymentConstants.TASK_CODE_BEACHINA_TRANSFER,
				CZPaymentConstants.TASK_CODE_ADHOC_BEACHINA_TRANSFER,
				CZPaymentConstants.TASK_CODE_INTERNAL_TRANSFER_PAYLATER,
				CZPaymentConstants.TASK_CODE_ADHOC_INTERNAL_TRANSFER_PAYLATER,
				CZPaymentConstants.TASK_CODE_BEACHINA_TRANSFER_PAYLATER,
				CZPaymentConstants.TASK_CODE_ADHOC_BEACHINA_TRANSFER_PAYLATER);

		System.out
				.println("CZTransactionExt sessionContext.getPostingDateText()=" + sessionContext.getPostingDateText());
		String dateType = ConfigurationFactory.getInstance().getConfigurations(CommonConstants.BASE_CONFIG)
				.get(ApprovalConstants.DATE_TYPE_FOR_APPROVAL_VALUE_DATE_EXPIRY, null);
		System.out.println("CZTransactionExt dateType=" + dateType + " DateForTargetUnit()="
				+ DateHelper.getInstance().getDateForTargetUnit() + " isOverrideStatusReqd=" + isOverrideStatusReqd
				+ " viewType=" + viewType);

		System.out.println("Current VIew Type" + viewType);

		for (TransactionDTO transactionDTO : transactionListResponse.getTransactionDTOs()) {
			new HthUserAccessTransactionName().enrich(transactionDTO);

			System.out.println("**Transaction ID==" + transactionDTO.getTransactionId());
			System.out.println("CZTransactionExt " + transactionDTO.getTransactionName() + " GRACEPERIOD ENABLED="
					+ transactionDTO.getTaskDTO().isAspectEnabled(TaskAspect.GRACE_PERIOD) + " Action="
					+ transactionDTO.getApprovalDetails().getAction());
			if ("Y".equalsIgnoreCase(isOverrideStatusReqd)
					&& (transactionDTO.getApprovalDetails().getAction() == ApprovalAction.CREATE
							|| transactionDTO.getApprovalDetails().getAction() == ApprovalAction.APPROVE
							|| transactionDTO.getApprovalDetails().getAction() == ApprovalAction.MODIFY)
					&& transactionDTO.getApprovalDetails().getStatus() == ApprovalStatus.EXPIRED) {
				System.out.println("CZTransactionExt Setting EXPIRED STATUS AS PENDING_APPROVAL"
						+ transactionDTO.getTransactionId() + " " + transactionDTO.getTransactionName());
				transactionDTO.getApprovalDetails().setStatus(ApprovalStatus.PENDING_APPROVAL);
			}
			if (transactionDTO.getTransactionId() != null) {
				MACData macDomain = new MACData();
				macDomain = macDomain.fetchMacData(transactionDTO.getTransactionId());

				if (macDomain != null && macDomain.getFxIndicator() != null) {
					System.out.println("**getFxIndicator==" + macDomain.getFxIndicator());
					transactionDTO
							.setDictionaryArray(DArrayUtils.addStringsToDarray(transactionDTO.getDictionaryArray(),
									new String[][] { { "FX_INDICATOR", macDomain.getFxIndicator() } }));
				} else {
					transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
							transactionDTO.getDictionaryArray(), new String[][] { { "FX_INDICATOR", "N" } }));
				}

				if (viewType.equals(ViewType.APPROVED)) {
					transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
							transactionDTO.getDictionaryArray(), new String[][] { { "IS_APPROVED", "true" } }));
				}

				if (viewType.equals(ViewType.CREATED)) {
					Subject subject = SubjectUtil.getCurrentSubject();
					String username = SubjectUtil.getUserName(subject);
					com.ofss.digx.framework.domain.transaction.Transaction transactionDomain = new com.ofss.digx.framework.domain.transaction.Transaction();
					TransactionKey txnKey = new TransactionKey();
					txnKey.setId(transactionDTO.getTransactionId());
					transactionDomain = transactionDomain.read(txnKey);
					String signedByFromDB = transactionDomain.getApprovalDetails().getSignedBy();

					System.out.println("** signedByFromDB is = " + signedByFromDB);
					System.out.println("** username is = " + username);
					if (signedByFromDB != null && signedByFromDB != "" && signedByFromDB.contains("~")
							&& signedByFromDB.startsWith("~") && signedByFromDB.endsWith("~")
							&& signedByFromDB.trim().length() > 1) {
						signedByFromDB = signedByFromDB.substring(1, signedByFromDB.length() - 1);

						String[] signedByList = signedByFromDB.split("~");
						List<String> signedList = new ArrayList<>(Arrays.asList(signedByList));

						if (!signedList.isEmpty())
							signedList.remove(0);

						if (signedList.contains(username)) {
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][] { { "IS_APPROVED", "true" } }));
						}
					}
				}

			}

			if (transactionDTO instanceof AccountTransactionDTO) {

				try {
					String accountId = ((AccountTransactionDTO) transactionDTO).getAccountId().getValue();
					if (SysoutLogger.ISENABLED) {
						SysoutLogger.println(
								"postListTransactions " + transactionDTO.getClass() + " accountId=" + accountId);
					}

					String fmtAcctNo = getFmtAcctNoForDisp(accountId, viewType);
					if (fmtAcctNo != null) {
						transactionDTO
								.setDictionaryArray(DArrayUtils.addStringsToDarray(transactionDTO.getDictionaryArray(),
										new String[][] { { "FORMATTED_ACCTNO", fmtAcctNo } }));
						if (SysoutLogger.ISENABLED)
							SysoutLogger.println("accountDetails.getFormatedAccount()" + fmtAcctNo);
					}
					// Formatting Credit Account Number in case of self transfer

					if ("FT_F_PFR".equalsIgnoreCase(transactionDTO.getTransactionName())) {
						String creditAccountId = ((PaymentTransactionDTO) transactionDTO).getCreditAccountId()
								.getValue();
						System.out.println("CZTransactionExt postListTransactions under FX RATE BLOCK creditAccountId=" + creditAccountId);

						try {
							String fmtCrAcctNo = getFmtAcctNoForDisp(creditAccountId, viewType);
							if (fmtCrAcctNo != null) {
								transactionDTO.setDictionaryArray(
										DArrayUtils.addStringsToDarray(transactionDTO.getDictionaryArray(),
												new String[][] { { "FORMATTED_CREDITACCTNO", fmtCrAcctNo } }));
								System.out.println("CZTransactionExt creditAccountDetails.getFormatedAccount()" + fmtCrAcctNo);
							}

							String creditAccountName = ((PaymentTransactionDTO) transactionDTO)
									.getCreditAccountName();
							if (creditAccountName != null) {

							//FX Agreed Rate Removing masking of Name
							//creditAccountName = CZCommonUtils
							//		.maskName(CZCommonUtils.sepChar(creditAccountName, " "), 1);
							((PaymentTransactionDTO) transactionDTO).setCreditAccountName(creditAccountName);
							System.out.println("CZTransactionExt creditAccountDetails.creditAccountName()" + creditAccountName);
							}

						} catch (java.lang.Exception e) {
							e.printStackTrace();
						}
					}

					else if (transactionDTO instanceof PaymentTransactionDTO) {
						if (CZPaymentConstants.TASK_CODE_SELF_TRANSFER
								.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase())
								|| CZPaymentConstants.TASK_CODE_SELF_TRANSFER_PAYLATER
										.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase())) {
							String creditAccountId = ((PaymentTransactionDTO) transactionDTO).getCreditAccountId()
									.getValue();
							if (SysoutLogger.ISENABLED) {
								SysoutLogger.println("postListTransactions creditAccountId=" + creditAccountId);
							}
							try {
								String fmtCrAcctNo = getFmtAcctNoForDisp(creditAccountId, viewType);
								if (fmtCrAcctNo != null) {
									transactionDTO.setDictionaryArray(
											DArrayUtils.addStringsToDarray(transactionDTO.getDictionaryArray(),
													new String[][] { { "FORMATTED_CREDITACCTNO", fmtCrAcctNo } }));
									if (SysoutLogger.ISENABLED)
										SysoutLogger.println("creditAccountDetails.getFormatedAccount()" + fmtCrAcctNo);
								}
							} catch (java.lang.Exception e) {
								e.printStackTrace();
							}
							
						}						
						else if (CZPaymentConstants.TASK_CODE_BEACHINA_TRANSFER
								.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase())
								|| CZPaymentConstants.TASK_CODE_BEACHINA_TRANSFER_PAYLATER
										.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase()) 
							|| CZPaymentConstants.TASK_CODE_ADHOC_BEACHINA_TRANSFER
							.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase())
							|| CZPaymentConstants.TASK_CODE_ADHOC_BEACHINA_TRANSFER_PAYLATER
							.equalsIgnoreCase(transactionDTO.getTransactionName().toUpperCase())){
						
						PaymentTransactionDTO paymentTransactionDTO = (PaymentTransactionDTO) transactionDTO;
						System.out.println("BEA China Transaction Instance");
						TransactionKey key = new TransactionKey();
						key.setId(transactionDTO.getTransactionId());
						com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
						transactionData = transactionData.read(key);
						if (transactionData != null) {
						System.out.println("TXN read Completed, fetching Credit account name since the DTO is Not Null ");
						
						
						if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof NetworkPaymentCreateRequestDTO) {
							System.out.println("Instance of BEA China Transaction");
							NetworkPaymentCreateRequestDTO paymentData = (NetworkPaymentCreateRequestDTO) transactionData.getTransactionSnapshot();
							
							NetworkPaymentDTO paymentDataArray = (NetworkPaymentDTO) paymentData.getPaymentDetails();
							
							if (paymentDataArray.getDictionaryArray() != null
									&& paymentDataArray.getDictionaryArray().length > 0
									&& paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray() != null) {
								System.out.println("CZTransactionEXT BEA China Transaction Has dictionary array");

								for (int i = 0; i < paymentDataArray.getDictionaryArray()[0]
										.getNameValuePairDTOArray().length; i++) {
									System.out.println("BEA China Transaction ::: Dict count CZ txn EXT " + i
											+ paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getGenericName()
											+ "\nvalue is "
											+ paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getValue());

									if (paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
											.getGenericName().compareTo(
													"enName") == 0
											&& paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getValue() != null) {
										String enName = paymentDataArray.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getValue();
									  ((PaymentTransactionDTO) transactionDTO).setCreditAccountName(enName);
										System.out.println("BEA China Transaction ::: Setting Beneficiary name as" + enName);
										
									}
								}
							
							}

						} 
						
						else {
							System.out.println("not an Instance of BEA China Transaction");

							}
						}
						
						}
					}
						 else if (maskingReqdTask.contains(transactionDTO.getTransactionName())) {
							try {
								String creditAccountName = ((PaymentTransactionDTO) transactionDTO)
										.getCreditAccountName();
								creditAccountName = CZCommonUtils
										.maskName(CZCommonUtils.sepChar(creditAccountName, " "), 1);
								((PaymentTransactionDTO) transactionDTO).setCreditAccountName(creditAccountName);
							} catch (java.lang.Exception e) {
								e.printStackTrace();
							}
						} else if (viewType == ViewType.APPROVAL){
							if (transactionDTO.getTransactionName().equals(CZPaymentConstants.TASK_CODE_INTERNATIONAL_TRANSFER)
									|| transactionDTO.getTransactionName().equals(CZPaymentConstants.TASK_CODE_INTERNATIONAL_TRANSFER_PAYLATER)
									|| transactionDTO.getTransactionName().equals(CZPaymentConstants.TASK_CODE_ADHOC_INTERNATIONAL_TRANSFER)
									|| transactionDTO.getTransactionName().equals(CZPaymentConstants.TASK_CODE_ADHOC_INTERNATIONAL_TRANSFER_PAYLATER)){
								NetworkPaymentCreateRequestDTO transactionSnapshot = (NetworkPaymentCreateRequestDTO) transactionDTO.getTransactionSnapshot();
								if (transactionSnapshot != null && transactionSnapshot.getPaymentDetails() != null) {
									NetworkPaymentDTO paymentDetails = transactionSnapshot.getPaymentDetails();
									String indicator59 = DArrayUtils.getFieldFromDarray(paymentDetails.getDictionaryArray(), "indicator59");
									if (indicator59 != null && indicator59.equals("1")
											&& paymentDetails.getBeneficiary() != null
											&& paymentDetails.getBeneficiary().length > 0
											&& paymentDetails.getBeneficiary()[0].getAddressDTO() != null
											&& paymentDetails.getBeneficiary()[0].getAddressDTO().length > 0
											&& paymentDetails.getBeneficiary()[0].getAddressDTO()[0].getLine1() != null) {
										String creditAccountName = ((PaymentTransactionDTO) transactionDTO).getCreditAccountName();
										creditAccountName = creditAccountName + paymentDetails.getBeneficiary()[0].getAddressDTO()[0].getLine1();
										((PaymentTransactionDTO) transactionDTO).setCreditAccountName(creditAccountName);
									}
								}
							}
						}
					} else if (transactionDTO instanceof BillPaymentTransactionDTO) {
						BillPaymentTransactionDTO billPaymentTransactionDTO = (BillPaymentTransactionDTO) transactionDTO;

						System.out.println("BillPaymentTransactionDTO instance");
						TransactionKey key = new TransactionKey();
						key.setId(transactionDTO.getTransactionId());
						com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
						transactionData = transactionData.read(key);
						System.out.println("TXN read " + transactionData != null);
						String billerLocaleName = null;
						String billerNameEng = null;
						String billerNameTC = null;
						String billerNameSC = null;



						if (transactionData.getTransactionSnapshot() != null) {
							System.out.println("classID is--> " + transactionData.getTransactionSnapshot().getClass());
						} else {
							System.out.println("classID is--> transactionData.getTransactionSnapshot() is null");

						}

						if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
							if (transactionData.getTransactionSnapshot() instanceof BillPaymentDTO) {
								System.out.println("Instance of BillPaymentDTO");
								BillPaymentDTO billPayData = (BillPaymentDTO) transactionData.getTransactionSnapshot();
								if (billPayData.getDictionaryArray() != null
										&& billPayData.getDictionaryArray().length > 0
										&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray() != null) {
									System.out.println("Has dictionary array");

									for (int i = 0; i < billPayData.getDictionaryArray()[0]
											.getNameValuePairDTOArray().length; i++) {
										System.out.println("Dict count CZ txn EXT " + i
												+ billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getGenericName()
												+ "\nvalue is "
												+ billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue());
										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"com.ofss.digx.cz.bea.domain.ebpp.entity.billpayment.CZBillPaymentDomain.BillTypeCode") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillTypeCode in ext category");
											transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
													transactionDTO.getDictionaryArray(),
													new String[][] { { "BillTypeCode",
															getLocalTransalatedData(billPayData.getDictionaryArray()[0]
																	.getNameValuePairDTOArray()[i].getValue()
																			.split("~")) } }));
										}

										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"com.ofss.digx.cz.bea.domain.ebpp.entity.billpayment.CZBillPaymentDomain.BillTypeDescription") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillTypeDesc in ext category");
											transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
													transactionDTO.getDictionaryArray(),
													new String[][] { { "BillTypeDescription",
															getLocalTransalatedData(billPayData.getDictionaryArray()[0]
																	.getNameValuePairDTOArray()[i].getValue()
																			.split("~")) } }));
										}

										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"com.ofss.digx.cz.bea.domain.ebpp.entity.billpayment.CZBillPaymentDomain.BillCategory") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillCategory in ext category");
											transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
													transactionDTO.getDictionaryArray(),
													new String[][] { { "BillCategory",
															getLocalTransalatedData(billPayData.getDictionaryArray()[0]
																	.getNameValuePairDTOArray()[i].getValue()
																			.split("~")) } }));
										}
										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"BillerNameEng") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillerNameEng");
											billerNameEng = billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i].getValue();
										}
										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"BillerNameTC") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillerNameTC");
											billerNameTC = billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i].getValue();
										}
										if (billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
												.getGenericName().compareTo(
														"BillerNameSC") == 0
												&& billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
														.getValue() != null) {
											System.out.println("setting BillerNameSC");
											billerNameSC = billPayData.getDictionaryArray()[0].getNameValuePairDTOArray()[i].getValue();
										}
									}
									//Adding BillerName and Bill Payment Number for showing on Dashboard.
								if(billerNameEng!=null && billerNameTC!=null &&  billerNameSC!=null) {
									billerLocaleName = CZLocaleUtils.getLocalebasedMerchantName(billerNameEng, billerNameTC, billerNameSC);
								}
								else if(billerLocaleName==null || billerLocaleName.isEmpty()) {
									billerLocaleName = billPayData.getBillerName();
								}
								System.out.println("CZTransactionExt Line 2357 :::: setting billerName in ext category as" +  billerLocaleName);
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(),
										new String[][] { { "billerName", billerLocaleName } }));
								System.out.println("CZTransactionExt Line 2322 :::: original billerName was" +  billPayData.getBillerName()+ "Translated Billername was" +getLocalTransalatedData(billPayData.getBillerName().split("~")) );
								List<BillPaymentRelDetailsDTO>  billPayRelData = billPayData.getBillPaymentRelDetails();
								String billAccountNumber = null;
								for (BillPaymentRelDetailsDTO data : billPayRelData) {
								billAccountNumber = data.getValue();
								}
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
								transactionDTO.getDictionaryArray(),
								new String[][] { { "billAccountNumber", billAccountNumber } }));
								System.out.println("CZTransactionExt Line 2370 :::: setting billAccountNumber in ext category as" +  billAccountNumber);



								}

							} else {
								System.out.println("not an Instance of BillPaymentDTO");

							}
						} else {
							System.out.println("transactionDTO.getTransactionSnapshot() is null");
						}

						if (!InputValidationUtils.isNullOrBlank(billPaymentTransactionDTO.getLabelId())) {
							String[] labelList = billPaymentTransactionDTO.getLabelId().split("~");
							String labelDesc = null;
							if (labelList.length == 1
									|| "N".equalsIgnoreCase(dayOneConfig.get("ENABLE_DD_TRANSLTN", "N"))) {
								labelDesc = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[0],
										labelList[0]);
							} else if (labelList.length == 2) {
								labelDesc = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1],
										labelList[1]);
							} else if (labelList.length == 3) {
								labelDesc = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1],
										labelList[2]);
							}
							billPaymentTransactionDTO.setLabelId(labelDesc);
							if (SysoutLogger.ISENABLED)
								SysoutLogger
										.println("CZTransactionExt BillPaymentTransactionDTO labelDesc=" + labelDesc);
							((BillPaymentTransactionDTO) transactionDTO).setLabelId(labelDesc);
						}
						if (!InputValidationUtils.isNullOrBlank(billPaymentTransactionDTO.getBillerName())) {
							String[] labelList = billPaymentTransactionDTO.getBillerName().split("~");
							String billerName = null;
							if (labelList.length == 1
									|| "N".equalsIgnoreCase(dayOneConfig.get("ENABLE_DD_TRANSLTN", "N"))) {
								billerName = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[0],
										labelList[0]);
							} else if (labelList.length == 2) {
								billerName = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1],
										labelList[1]);
							} else if (labelList.length == 3) {
								billerName = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1],
										labelList[2]);
							}
							billPaymentTransactionDTO.setBillerName(billerName);
							if (SysoutLogger.ISENABLED)
								SysoutLogger
										.println("CZTransactionExt BillPaymentTransactionDTO billerName=" + billerName);
							// ((BillPaymentTransactionDTO)transactionDTO).setBillerName(billerName);
						}

					}
					 else if (transactionDTO instanceof AmountAccountTransactionDTO) {
						 AmountAccountTransactionDTO amountAccountTransactionDTO = (AmountAccountTransactionDTO) transactionDTO;

							System.out.println("amountAccountTransactionDTO instance");
							TransactionKey key = new TransactionKey();
							key.setId(transactionDTO.getTransactionId());
							com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
							transactionData = transactionData.read(key);
							System.out.println("TXN read " + transactionData != null);

							if (transactionData.getTransactionSnapshot() != null) {
								System.out.println("classID is--> " + transactionData.getTransactionSnapshot().getClass());
							} else {
								System.out.println("classID is--> transactionData.getTransactionSnapshot() is null");

							}

							if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
								if (transactionData.getTransactionSnapshot() instanceof CZLMCreateRequestDTO) {
									System.out.println("Instance of CZLMCreateRequestDTO");
									CZLMCreateRequestDTO createLM = (CZLMCreateRequestDTO) transactionData.getTransactionSnapshot();
									if (createLM.getDictionaryArray() != null
											&& createLM.getDictionaryArray().length > 0
											&& createLM.getDictionaryArray()[0].getNameValuePairDTOArray() != null) {
										System.out.println("Has dictionary array");

										for (int i = 0; i < createLM.getDictionaryArray()[0]
												.getNameValuePairDTOArray().length; i++) {
											System.out.println("Dict count CZ txn EXT " + i
													+ createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
															.getGenericName()
													+ "\nvalue is "
													+ createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
															.getValue());
											if (createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getGenericName().compareTo(
															"com.ofss.digx.cz.bea.domain.liquiditymanagement.entity.lmInstruction.LMInstruction.InstructionNumber") == 0
													&& createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
															.getValue() != null) {
												System.out.println("setting InstructionNumber in ext category");
												transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
														transactionDTO.getDictionaryArray(),
														new String[][] { { "InstructionNumber",
																getLocalTransalatedData(createLM.getDictionaryArray()[0]
																		.getNameValuePairDTOArray()[i].getValue()
																				.split("~")) } }));
											}

											if (createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getGenericName().compareTo(
															"com.ofss.digx.cz.bea.domain.liquiditymanagement.entity.lmInstruction.LMInstruction.VersionNumber") == 0
													&& createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
															.getValue() != null) {
												System.out.println("setting VersionNumber in ext category");
												transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
														transactionDTO.getDictionaryArray(),
														new String[][] { { "VersionNumber",
																getLocalTransalatedData(createLM.getDictionaryArray()[0]
																		.getNameValuePairDTOArray()[i].getValue()
																				.split("~")) } }));
											}

											if (createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
													.getGenericName().compareTo(
															"com.ofss.digx.cz.bea.domain.liquiditymanagement.entity.lmInstruction.LMInstruction.Priority") == 0
													&& createLM.getDictionaryArray()[0].getNameValuePairDTOArray()[i]
															.getValue() != null) {
												System.out.println("setting Priority in ext category");
												transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
														transactionDTO.getDictionaryArray(),
														new String[][] { { "Priority",
																getLocalTransalatedData(createLM.getDictionaryArray()[0]
																		.getNameValuePairDTOArray()[i].getValue()
																				.split("~")) } }));
											}
										}

									}

								} else {
									System.out.println("not an Instance of CZLMCreateRequestDTO");

								}
							} else {
								System.out.println("transactionDTO.getTransactionSnapshot() is null");
							}




						}

				} catch (java.lang.Exception e) {
					e.printStackTrace();
				}
			} else if (transactionDTO instanceof PayeeTransactionDTO) {
				try {

					PayeeTransactionDTO payeeTransactionDTO = (PayeeTransactionDTO) transactionDTO;
					if (PayeeType.INTERNAL.toString().equalsIgnoreCase(payeeTransactionDTO.getCategory())
							|| CZPaymentConstants.CATEGORY_BEACHINA_PAYEE
									.equalsIgnoreCase(payeeTransactionDTO.getCategory())) {
						String userType = ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID) != null
								? (String) ThreadAttribute.get(ThreadAttribute.ENTERPRISE_ROLE_ID)
								: null;
						System.out.println("CZTransactionExt PayeeTransactionDTO userType=" + userType);
						if (!CZCommonConstants.USER_TYPE_ADMINISTRATOR.equalsIgnoreCase(userType)) {
							String payeeName = payeeTransactionDTO.getName();
							payeeName = CZCommonUtils.maskName(CZCommonUtils.sepChar(payeeName, " "), 1);
							((PayeeTransactionDTO) transactionDTO).setName(payeeName);
						}
					}
				} catch (java.lang.Exception e) {
					e.printStackTrace();
				}

			}



			if (transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_CME")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_UME")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_DME")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());
					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for merchant " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null) {
						System.out.println("classID for merchant is--> " + transactionData.getTransactionSnapshot().getClass());
					} else {
						System.out.println("classID for merchant  is--> transactionData.getTransactionSnapshot() is null");

					}

					if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof MerchantDTO) {
							System.out.println("Instance of MerchantDTO");
							MerchantDTO merchantDTO = (MerchantDTO) transactionData.getTransactionSnapshot();
							if(merchantDTO != null && merchantDTO.getMerchantName() != null) {
								System.out.println("CZTransactionExt.postListTransactions() merchant name :: " + merchantDTO.getMerchantName());
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(), new String[][] { { "merchantName", merchantDTO.getMerchantName() } }));
							}
						}
					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of merchantDTO :: e");
					e.printStackTrace();
				}
			}


			if (transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_CMEM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_UMEM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_DMEM")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());
					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for MerchantMappingDTO " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null) {
						System.out.println("classID for MerchantMappingDTO is--> " + transactionData.getTransactionSnapshot().getClass());
					} else {
						System.out.println("classID for MerchantMappingDTO  is--> transactionData.getTransactionSnapshot() is null");

					}

					if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof MerchantMappingDTO) {
							System.out.println("Instance of MerchantMappingDTO");
							MerchantMappingDTO merchantDTO = (MerchantMappingDTO) transactionData.getTransactionSnapshot();
							if(merchantDTO != null && merchantDTO.getParentMerchantId() != null) {
								com.ofss.digx.cz.bea.app.merchant.service.Merchant merchant = new com.ofss.digx.cz.bea.app.merchant.service.Merchant();
								com.ofss.digx.cz.bea.app.merchant.dto.MerchantDTO merchantDto = new MerchantDTO();
								merchantDto.setMerchantId(merchantDTO.getParentMerchantId());
								com.ofss.digx.cz.bea.app.merchant.dto.MerchantResponseDTO response = merchant.read(sessionContext, merchantDto);
								 String merchantName = response.getMerchantDTO().getMerchantName();
								 System.out.println("CZTransactionExt.postListTransactions() merchant mapping :: " + merchantName);
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(), new String[][] { { "merchantName", merchantName} }));
							}
						}
					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of MerchantMappingDTO :: e");
					e.printStackTrace();
				}
			}

			if (transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_CMU")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_EMU")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_DMU")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_LDMC")
				|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_LDMU")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());
					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for MerchantUserMaintenanceDTO " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null) {
						System.out.println("classID for MerchantUserMaintenanceDTO is--> " + transactionData.getTransactionSnapshot().getClass());
					} else {
						System.out.println("classID for MerchantUserMaintenanceDTO  is--> transactionData.getTransactionSnapshot() is null");

					}

					if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof MerchantUserMaintenanceDTO) {
							System.out.println("Instance of MerchantUserMaintenanceDTO");
							MerchantUserMaintenanceDTO maintenanceDTO = (MerchantUserMaintenanceDTO) transactionData.getTransactionSnapshot();
							if(maintenanceDTO != null && maintenanceDTO.getMerchantDetailsList() != null && maintenanceDTO.getMerchantDetailsList().size() >0) {
								String merchantName = maintenanceDTO.getMerchantDetailsList().get(0).getMerchantName();

								 System.out.println("CZTransactionExt.postListTransactions() merchant mapping :: " + merchantName);
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(), new String[][] { { "merchantName", merchantName} }));
							}
						}
					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of MerchantUserMaintenanceDTO :: e");
					e.printStackTrace();
				}
			}

			if (transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_CMEAM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_UMEAM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MRCH_N_DMEAM")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());
					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for MerchantAdviceMappingDTO " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null) {
						System.out.println("classID for MerchantAdviceMappingDTO is--> " + transactionData.getTransactionSnapshot().getClass());
					} else {
						System.out.println("classID for MerchantAdviceMappingDTO  is--> transactionData.getTransactionSnapshot() is null");

					}

					if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof MerchantAdviceMappingDTO) {
							System.out.println("Instance of MerchantAdviceMappingDTO");
							MerchantAdviceMappingDTO maintenanceDTO = (MerchantAdviceMappingDTO) transactionData.getTransactionSnapshot();
							if(maintenanceDTO != null && maintenanceDTO.getMerchantIdTo() != null && maintenanceDTO.getMerchantIdFrom() != null) {
								com.ofss.digx.cz.bea.domain.merchant.entity.MerchantAdviceMapping domain = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantAdviceMapping();

								 String merchantName = domain.getFirstMerchant(maintenanceDTO.getMerchantIdFrom(), maintenanceDTO.getMerchantIdTo());
								 System.out.println("CZTransactionExt.postListTransactions() merchant mapping :: " + merchantName);
								 if(merchantName != null) {
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(), new String[][] { { "merchantName", merchantName} }));
								 }
							}
						}
					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of MerchantUserMaintenanceDTO :: e");
					e.printStackTrace();
				}
			}

			if (transactionDTO.getTransactionName().equalsIgnoreCase("MT_N_CFM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MT_N_DFM")
					|| transactionDTO.getTransactionName().equalsIgnoreCase("MT_N_EFM")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());
					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for Fps Merchant " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null) {
						System.out.println("classID for Fps Merchant is--> " + transactionData.getTransactionSnapshot().getClass());
					} else {
						System.out.println("classID for Fps Merchant  is--> transactionData.getTransactionSnapshot() is null");

					}

					if (transactionData != null && transactionData.getTransactionSnapshot() != null) {
						if (transactionData.getTransactionSnapshot() instanceof FpsMerchantAddressingDTO) {
							System.out.println("Instance of FpsMerchantAddressingDTO");
							FpsMerchantAddressingDTO fpsMerchantAddressingDTO = (FpsMerchantAddressingDTO) transactionData.getTransactionSnapshot();
							if(fpsMerchantAddressingDTO != null && fpsMerchantAddressingDTO.getCompanyName() != null) {
								System.out.println("CZTransactionExt.postListTransactions() Company name :: " + fpsMerchantAddressingDTO.getCompanyName());
								String companyName = fpsMerchantAddressingDTO.getCompanyName();
								transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTO.getDictionaryArray(), new String[][] { { "companyName", companyName} }));
							}
						}
					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of fpsMerchantAddressingDTO :: e");
					e.printStackTrace();
				}
			}

			if (transactionDTO.getTransactionName().equalsIgnoreCase("FT_F_PFR")) {
				try {
					TransactionKey key = new TransactionKey();
					key.setId(transactionDTO.getTransactionId());

					com.ofss.digx.framework.domain.transaction.Transaction transactionData = new com.ofss.digx.framework.domain.transaction.Transaction();
					transactionData = transactionData.read(key);
					System.out.println("TXN read for FX Merchant " + transactionData != null);
					if (transactionData.getTransactionSnapshot() != null && transactionData.getTransactionSnapshot() instanceof FxAgreeOWNCRUDDomainDTO ) {
						System.out.println("classID for FX Merchant--> " + transactionData.getTransactionSnapshot().getClass());
						System.out.println("to string of the DTO Information for FX Merchant--> " + transactionData.getTransactionSnapshot().toString());
						System.out.println("to string of the DTO Information for FX Merchant--> " + transactionData.getTransactionApprovalHistory().toString());
					} else {
						System.out.println("to string of the DTO Information for FX Merchant--> " + transactionData.getTransactionSnapshot().toString());
						System.out.println("to string of the DTO Information for FX Merchant--> " + transactionData.getTransactionApprovalHistory().toString());

					}


				} catch (java.lang.Exception e) {
					System.out.println("CZTransactionExt.postListTransactions() :: inside of MerchantUserMaintenanceDTO :: e");
					e.printStackTrace();
				}
			}
			//BCOCDC-2578 Display planned execution date in the My approved list / Pending approval list/Activity log start
			if (transactionDTO.getDiscriminator().equals(TransactionDiscriminator.ELECTRONIC_BILL_PAYMENTS)
					|| transactionDTO.getDiscriminator().equals(TransactionDiscriminator.BULK_FILE)
					|| transactionDTO.getDiscriminator().equals(TransactionDiscriminator.PAYMENTS)) {
				if (transactionDTO instanceof AmountAccountTransactionDTO) {
					AmountAccountTransactionDTO amountAccountTransactionDTO = (AmountAccountTransactionDTO)transactionDTO;
					Date lastUpdateDate = transactionDTO.getLastUpdatedDate();
					Date creationDate = transactionDTO.getCreationDate();
					Date executionDate = amountAccountTransactionDTO.getValueDate();
					Boolean payLater = executionDate.isAfter(creationDate);

					DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
					DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

					LocalDateTime lCreationDate   = LocalDateTime.parse(creationDate.toString(), inputFormatter);
					LocalDateTime lLastUpdateDate = LocalDateTime.parse(lastUpdateDate.toString(), inputFormatter);
					LocalDateTime lExecutionDate  = LocalDateTime.parse(executionDate.toString(), inputFormatter);

					ZonedDateTime zoneCreationDate = lCreationDate.atZone(ZoneId.systemDefault());
					ZonedDateTime zoneLastUpdateDate = lLastUpdateDate.atZone(ZoneId.systemDefault());
					ZonedDateTime zoneExecutionDate = lExecutionDate.atZone(ZoneId.systemDefault());

					String fmtCreationDate = zoneCreationDate.format(outputFormatter);
					String fmtLastUpdateDate = zoneLastUpdateDate.format(outputFormatter);
					String fmtExecutionDate = zoneExecutionDate.format(outputFormatter);

					System.out.println("executionDate in CZtransactionExt is: " + fmtExecutionDate);
					System.out.println("paylater in CZtransactionExt is: " + payLater);
					System.out.println("EBP, BF and PAY creattionDate: "+ fmtCreationDate + " lastUpdateDate: " + fmtLastUpdateDate);
					if (viewType.equals(ViewType.APPROVAL)) {
						transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
								transactionDTO.getDictionaryArray(), new String[][] { { "initiationDate", fmtCreationDate } }));
						if (payLater) {
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", fmtExecutionDate }}));
						}else{
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", "Pay now"}}));
						}
					}
					if (viewType.equals(ViewType.APPROVED)) {
						transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
								transactionDTO.getDictionaryArray(), new String[][] { { "approvalDate", fmtLastUpdateDate } }));
						if (payLater) {
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", fmtExecutionDate }}));
						}else{
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", "Pay now" }}));
						}
					}
					if (viewType.equals(ViewType.CREATED)) {
						transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
								transactionDTO.getDictionaryArray(), new String[][] { { "initiationDate", fmtCreationDate } }));
						if (payLater) {
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", fmtExecutionDate }}));
						}else{
							transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
									transactionDTO.getDictionaryArray(), new String[][]{{ "executionDate", "Pay now" }}));
						}
					}
				}
			}
			if(transactionDTO.getDiscriminator().equals(TransactionDiscriminator.LIQUIDITY_MANAGEMENT)
					|| transactionDTO.getDiscriminator().equals(TransactionDiscriminator.ACCOUNT_FINANCIAL)
					|| transactionDTO.getDiscriminator().equals(TransactionDiscriminator.ACCOUNT_NON_FINANCIAL)){
				String creationDate = transactionDTO.getCreationDate().getDateString();
				String lastUpdateDate = transactionDTO.getLastUpdatedDate().getDateString();
				DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
				DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

				LocalDateTime lCreationDate   = LocalDateTime.parse(creationDate, inputFormatter);
				LocalDateTime lLastUpdateDate = LocalDateTime.parse(lastUpdateDate, inputFormatter);

				ZonedDateTime zoneCreationDate = lCreationDate.atZone(ZoneId.systemDefault());
				ZonedDateTime zoneLastUpdateDate = lLastUpdateDate.atZone(ZoneId.systemDefault());

				String fmtCreationDate = zoneCreationDate.format(outputFormatter);
				String fmtLastUpdateDate = zoneLastUpdateDate.format(outputFormatter);
				System.out.println("LM, AF and ANF creattionDate: "+ fmtCreationDate + " lastUpdateDate: " + fmtLastUpdateDate);
				if (viewType.equals(ViewType.APPROVAL)) {
					transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
							transactionDTO.getDictionaryArray(), new String[][] { { "initiationDate", fmtCreationDate } }));
				}
				if (viewType.equals(ViewType.APPROVED)) {
					transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
							transactionDTO.getDictionaryArray(), new String[][] { { "approvalDate", fmtLastUpdateDate } }));
				}
				if (viewType.equals(ViewType.CREATED)) {
					transactionDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(
							transactionDTO.getDictionaryArray(), new String[][] { { "initiationDate", fmtCreationDate } }));
				}
			}
			//BCOCDC-2578 Display planned execution date in the My approved list / Pending approval list/Activity log end
		}
		//BCOCDC-5595 Jason start
		Subject subject = SubjectUtil.getCurrentSubject();
		String userName = SubjectUtil.getUserName(subject);
		Boolean isAdmin = (Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get(IS_ADMIN);
		boolean switchOn;
		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String switchValue = customConfigAdapter.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "SPECIAL_FUNCTION_GROUP_SWITCH", "");
		String bddMktUserIds = customConfigAdapter.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "BDD_MARKETING_USER_ID", "");
		Set<String> allowedBddMktUserIds = new HashSet<>();
		try {
			if (switchValue == null || switchValue.isEmpty()) {
				System.out.println("Switch configuration is empty...");
				throw new IllegalArgumentException("Switch configuration is empty.");
			}else {
				switchOn = "ON".equalsIgnoreCase(switchValue);
			}
			String[] bddMktUserIdList = bddMktUserIds.split("~");
			if (bddMktUserIdList.length > 0) {
				for (int i = 0; i < bddMktUserIdList.length; i++) {
					System.out.println("Allow function group: " + i + " is: "+ bddMktUserIdList[i]);
					allowedBddMktUserIds.add(bddMktUserIdList[i]);
				}
			}else{
				System.out.println("Invalid department code configuration format...");
				throw new IllegalArgumentException("Invalid configuration format.");
			}
		}catch (java.lang.Exception e){
			System.err.println("Error processing SPECIAL_FUNCTION_GROUP_SWITCH configuration: " + e.getMessage());
			e.printStackTrace();
			switchOn = false;
		}
		System.out.println("SPECIAL_FUNCTION_GROUP_SWITCH: " + switchOn);
		if(switchOn && isAdmin && (viewType.equals(ViewType.APPROVAL) || (viewType.equals(ViewType.APPROVED)))){
			List < TransactionDTO > dtoList = transactionListResponse.getTransactionDTOs();
			List < TransactionDTO > modifiedDtoList = new ArrayList < TransactionDTO > ();
			if (isBddMktUser(userName, allowedBddMktUserIds)) {
				System.out.println("Special mkt origin dto list size before filter is: " + dtoList.size());
				//BDD marketing user allow to see txn that created by marketing user only
				for (TransactionDTO transactionDTO: dtoList) {
					String transactionCreateBy = transactionDTO.getCreatedBy();
					System.out.println("** EBKCDC04 logic username is = " + userName);
					System.out.println("** EBKCDC04 logic transaction create by is = " + transactionCreateBy);
					if(allowedBddMktUserIds.contains(transactionDTO.getCreatedBy())){
						modifiedDtoList.add(transactionDTO);
					}
				}
				System.out.println("Special mkt modifiedDtoList size after filter is: " + modifiedDtoList.size());
			} else {
				//Remove txn that created by marketing user
				System.out.println("Normal origin dto list size before filter is: " + dtoList.size());
				for (TransactionDTO transactionDTO: dtoList) {
					String transactionCreateBy = transactionDTO.getCreatedBy();
					System.out.println("** Normal case logic username is = " + userName);
					System.out.println("** Normal case logic transaction create by is = " + transactionCreateBy);
					if(!allowedBddMktUserIds.contains(transactionDTO.getCreatedBy())){
						modifiedDtoList.add(transactionDTO);
					}
				}
				System.out.println("Normal modifiedDtoList size after filter is: " + modifiedDtoList.size());
			}
			transactionListResponse.setTransactionDTOs(modifiedDtoList);
		}
		//BCOCDC-5595 Jason end
	}

	public String getLocalTransalatedData(String[] labelList) {
		String translatedValue = "";
		System.out.println("In getLocalTransalatedData with leng" + labelList.length);
		if (labelList != null && labelList.length > 0) {
			if (labelList.length == 1) {
				translatedValue = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[0], labelList[0]);
			} else if (labelList.length == 2) {
				translatedValue = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1], labelList[1]);
			} else if (labelList.length == 3) {
				translatedValue = CZLocaleUtils.getDescLocale("DUMMY", labelList[0], labelList[1], labelList[2]);
			}

			if (SysoutLogger.ISENABLED)
				SysoutLogger.println("translated vaue=" + translatedValue);
		}

		return translatedValue;

	}

	private String getFmtAcctNoForDisp(String accountId, ViewType viewType) {
		String fmtAcctNo = null;

		if (InputValidationUtils.isNullOrBlank(accountId)) {
			return fmtAcctNo;
		}

		try {

			if (viewType == ViewType.APPROVED) {
				if (accountId.contains("~")) {
					// Format ~ separated OBDX account number
					CurrentAccountDTO accountDetails = AccountDetailsHelper.getAccountDetails(accountId);
					fmtAcctNo = accountDetails != null ? accountDetails.getFormatedAccount() : null;
				} else {
					// Format 18-digit internal account number
					fmtAcctNo = CZAccountHelper.formatAccountNumber(accountId);
				}
			} else if (viewType == ViewType.CREATED) {
				// This includes records for both pending approval & approved
				if (accountId.contains("~")) {
					// Format ~ separated OBDX account number
					CurrentAccountDTO accountDetails = AccountDetailsHelper.getAccountDetails(accountId);
					fmtAcctNo = accountDetails != null ? accountDetails.getFormatedAccount() : null;
				} else {
					// Format 18-digit internal account number
					fmtAcctNo = CZAccountHelper.formatAccountNumber(accountId);
				}
			} else {
				if (accountId.contains("~")) {
					// Format ~ separated OBDX account number
					CurrentAccountDTO accountDetails = AccountDetailsHelper.getAccountDetails(accountId);
					fmtAcctNo = accountDetails != null ? accountDetails.getFormatedAccount() : null;
				} else {
					// Format 18-digit internal account number
					fmtAcctNo = CZAccountHelper.formatAccountNumber(accountId);
				}
			}

		} catch (java.lang.Exception e) {
			e.printStackTrace();
		}

		return fmtAcctNo;
	}

	/**
	 * Processes after the execution of actual business logic for updating a rule
	 * details. Executes the post extension logic after the actual business logic.
	 *
	 * @param sessionContext            {@link SessionContext} containing session
	 *                                  details
	 * @param transactionActionDTO      {@link TransactionActionDTO} containing
	 *                                  details about the action performed on the
	 *                                  transaction.
	 * @param transactionActionResponse {@link TransactionStatus} containing status
	 *                                  of the transaction
	 * @throws com.ofss.digx.infra.exceptions.Exception if any exception occurs
	 */
	@Override
	public void postPerformAction(SessionContext sessionContext, TransactionActionDTO transactionActionDTO,
			TransactionActionResponse transactionActionResponse) throws Exception {
		if (transactionActionDTO != null && transactionActionDTO.getTransactionDTO() != null) {
			TransactionDTO transactionDTO = transactionActionDTO.getTransactionDTO();
			String taskId = transactionDTO.getTransactionName();
			ApprovalAction transactionAction = transactionActionDTO.getAction();
			System.out.println("** inside postPerformAction123");
			if (taskId != null && taskId.equalsIgnoreCase("CH_N_RADHSTMT")) {
				IAdapterFactory adapterfactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.ADHOC_STATEMENT_ADAPTER_FACTORY);
				IAdhocStatementAdapter adapter = (IAdhocStatementAdapter) adapterfactory.getAdapter(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.ADHOC_STATEMENT_ADAPTER);
				AdhocStatementDTO request = new AdhocStatementDTO();
				request.setTxnReferenceNumber(transactionDTO.getTransactionId());
				if (transactionAction.equals(ApprovalAction.APPROVE)) {
					if (transactionActionDTO.getTransactionDTO().getErrors() != null
							&& transactionActionDTO.getTransactionDTO().getErrors().size() > 0) {
						System.out.println("Adding REJECTED case in host reject");
						request.setRequestStatus("REJECTED");
					} else {
						request.setRequestStatus("APPROVED");
					}

				} else {
					request.setRequestStatus("REJECTED");
				}
				adapter.updateTxnStatusPostApproval(request);
			}

			System.out.println(
					"postPerform :: transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName(): "
							+ transactionActionResponse.getTransactionAction().getTransactionDTO()
									.getTransactionName());

			if ("CTD_C".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				System.out.println("postPerform :: Entering to change status(CTD_C)." + taskId);
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.app.td.dto.account.TermDepositAccountDTO) {
					System.out.println("postPerform :: Entering to change status(CTD_C inside second if).");
					com.ofss.digx.app.td.dto.account.TermDepositAccountDTO reqDTO = (com.ofss.digx.app.td.dto.account.TermDepositAccountDTO) transactionActionResponse
							.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
					reqDTO.setOfferId(
							transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId());
					SessionContext sessionContext_t = (SessionContext) ThreadAttribute
							.get(ThreadAttribute.SESSION_CONTEXT);
					CZTermDeposit tdm1 = new CZTermDeposit();
					if (reqDTO != null && reqDTO.getOfferId() != null && reqDTO.getPartyId() != null
							&& reqDTO.getId() != null) {
						System.out.println(" reqDTO not null ");
						CZTermDepositAccount domain = new CZTermDepositAccount();
						CZTermDepositListRequestDTO depositListRequestDTO = new CZTermDepositListRequestDTO();
						depositListRequestDTO.setDebitAccountId(reqDTO.getId());
						depositListRequestDTO.setPartyId(reqDTO.getPartyId());
						depositListRequestDTO.setTdRefNo(reqDTO.getOfferId());
						System.out.println("postPerform :: TdRefNo: " + reqDTO.getOfferId());
						System.out.println("postPerform :: ref_no: " + transactionActionResponse.getTransactionAction()
								.getTransactionDTO().getTransactionId());
						CZTermDepositAccount termDepositDomainObj = domain.list(depositListRequestDTO).get(0);
						if (transactionAction.equals(ApprovalAction.APPROVE)) {
							System.out.println("postPerform :: After approval changring status to APPROVED");
							termDepositDomainObj.setStatus("APPROVED");
							String CouponCode = this.generateCouponCodeBCO();
							termDepositDomainObj.setCouponCode(CouponCode);
							System.out.println("postPerform :: CouponCode: " + CouponCode);

							// Adding Coupon Code to Response
							// reqDTO.getDictionaryArray()[0].getNameValuePairDTOArray();
							if (reqDTO.getDictionaryArray() != null && reqDTO.getDictionaryArray().length > 0
									&& reqDTO.getDictionaryArray()[0].getNameValuePairDTOArray() != null) {
								System.out.println("CZTransactionEXT reqDTO Has dictionary array");
								reqDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(reqDTO.getDictionaryArray(),
										new String[][] { { "couponCode", CouponCode } }));
								System.out.println(
										"setting CouponCode as " + CouponCode + " Completed IN CZTransactionEXT");
							}
						} else {
							termDepositDomainObj.setStatus("REJECTED");
							System.out.println("postPerform :: After rejecting changring status to REJECTED");
						}
						try {
							domain.amend(termDepositDomainObj);
							// Alert call
							System.out.println("###############Current transaction status is : "
									+ termDepositDomainObj.getStatus());

							if (termDepositDomainObj.getStatus().equals("APPROVED")) {
								System.out.println("###############After updating status sending alerts");
								tdm1.SendingTDM1Alerts(sessionContext_t, termDepositDomainObj);
							}

						} catch (java.lang.Exception e) {
							System.out.println("Error in updating status of CouponTD postPerform");
							e.printStackTrace();
						}
						System.out.println("postPerform :: Exiting after the status change(CTD_C).");
					}
				}
			}

			System.out.println("The approved task is " + taskId);

			ITaskAdapter taskAdapter = AdapterFactory.getInstance().getAdapter(ITaskAdapter.class);
			TaskDTO taskDTO = taskAdapter.read(taskId);

			com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.TASK_NAME_AUDIT,
					taskDTO.getName());

			com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.TASK_CODE_AUDIT,
					taskId);
			System.out.println("overrride perform task with " + taskDTO.getName());

			String isErrorFromHost = (String) ThreadAttribute.get("isErrorFromHost");// , "Y");
			isErrorFromHost = isErrorFromHost == null || "".equals(isErrorFromHost) ? "N" : isErrorFromHost;
			System.out.println("isErrorFromHost=" + isErrorFromHost);
			Dictionary[] dictionary = DArrayUtils.addStringsToDarray(
					transactionActionResponse.getTransactionAction().getDictionaryArray(),
					new String[][] { { "isErrorFromHost", isErrorFromHost } });
			if (transactionActionResponse.getTransactionAction() != null) {
				transactionActionResponse.getTransactionAction().setDictionaryArray(dictionary);
			}

			if (transactionActionResponse != null && transactionActionResponse.getTransactionAction() != null
					&& transactionActionResponse.getTransactionAction().getTransactionDTO() != null) {
				List<ProcessingErrorDTO> errors = transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getErrors();
				if (errors != null && errors.size() > 0) {
					for (ProcessingErrorDTO error : errors) {
						if ("DIGX_PROD_WSDL_TIMEOUT_0000".equals(error.getErrorCode())) {
							String errorMessage = ErrorManager.buildErrorMessage(error.getErrorCode(), null);
							System.out.println("Error message is : " + errorMessage);
							error.setErrorMessage(errorMessage);

							transactionDTO.getProcessingDetails().setStatus(ProcessingStatus.PROCESSING);
							transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails()
									.setStatus(ProcessingStatus.PROCESSING);

							System.out.println("Updating Domain : START");
							CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter
									.getInstance();
							localRepository.updateTransactionProcessingStatus(transactionDTO.getTransactionId(),
									error.getErrorCode(), errorMessage, null);
							System.out.println("Updating Domain : END");

							break;
						}

						if ("DIGX_PROD_DEF_0000".equals(error.getErrorCode())
								|| "DIGX_DEF_ERR_CD_001".equals(error.getErrorCode())) {
							String hostDisplayCode = (String) com.ofss.digx.infra.thread.ThreadAttribute
									.get("HOST_ERROR_DISPLAY_CODE");
							if (hostDisplayCode != null) {
								System.out.println("** hostDisplayCode=" + hostDisplayCode);
								System.out.println("** error code=" + error.getErrorCode());
								System.out.println("** error message=" + error.getErrorMessage());
								CZLocalTransactionRepositoryAdapter localRepository = CZLocalTransactionRepositoryAdapter
										.getInstance();
								localRepository.updateHostErrorDisplayCode(transactionDTO.getTransactionId(),
										error.getErrorCode(), hostDisplayCode + ":" + error.getErrorMessage());
							}
						}
					}
				}
			}

			if ("TD_F_OTD".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.app.td.dto.account.TermDepositAccountDTO) {
					com.ofss.digx.app.td.dto.account.TermDepositAccountDTO reqDTO = (com.ofss.digx.app.td.dto.account.TermDepositAccountDTO) transactionActionResponse
							.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
					if (ThreadAttribute.get("CHKR1_INTRATE") != null) {
						System.out
								.println("ThreadAttribute.get(CHKR1_INTRATE)=" + ThreadAttribute.get("CHKR1_INTRATE"));

						reqDTO.setDictionaryArray(DArrayUtils.addStringsToDarray(reqDTO.getDictionaryArray(),
								new String[][] { { "CHKR1_INTRATE", (String) ThreadAttribute.get("CHKR1_INTRATE") } }));
					}
				}
			}

			if ("MRCH_N_CME".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.cz.bea.app.merchant.dto.MerchantDTO) {
					System.out.println("CZTransactionExt taskId for merchant:  " + taskId);
					System.out.println("CZTransactionExt taskId for merchant data insert inside ");
					try {
						MerchantDTO mmRequestDTO = (MerchantDTO) transactionActionResponse
								.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
						com.ofss.digx.cz.bea.domain.merchant.entity.Merchant domain = new com.ofss.digx.cz.bea.domain.merchant.entity.Merchant();

						MerchantAssembler assembler = new MerchantAssembler();
						mmRequestDTO.setMerchantId(mmRequestDTO.getMerchantId());
						domain = assembler.toDomainObject(mmRequestDTO);

						if (domain != null) {

							domain.read(domain.getMerchantKey());

							if (transactionAction.equals(ApprovalAction.APPROVE)) {
								if (transactionActionDTO.getTransactionDTO().getErrors() != null
										&& transactionActionDTO.getTransactionDTO().getErrors().size() > 0) {
									System.out.println("Adding REJECTED case in host reject");
									domain.delete(domain);
								}

							} else {
								domain.delete(domain);

							}
						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}

			if ("MRCH_N_CMEM".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.cz.bea.app.merchant.dto.MerchantMappingDTO) {
					System.out.println("CZTransactionExt taskId for merchant mapping:  " + taskId);
					System.out.println("CZTransactionExt taskId for merchant mapping data insert inside ");
					try {
						MerchantMappingDTO mmRequestDTO = (MerchantMappingDTO) transactionActionResponse
								.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
						com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();

						for (String childMerchantId : mmRequestDTO.getChildMerchantIdList()) {
							MerchantMappingAssembler assembler = new MerchantMappingAssembler();
							mmRequestDTO.setChildMerchantId(childMerchantId);
							domain = assembler.toDomainObject(mmRequestDTO);
							if (domain != null) {
								domain.read(domain.getMerchantMappingKey());

								if (transactionAction.equals(ApprovalAction.APPROVE)) {
									if (transactionActionDTO.getTransactionDTO().getErrors() != null
											&& transactionActionDTO.getTransactionDTO().getErrors().size() > 0) {
										System.out.println("Adding REJECTED case in host reject");
										domain.delete(domain);
									}

								} else {
									domain.delete(domain);
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			if ("MRCH_N_UMEM".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.cz.bea.app.merchant.dto.MerchantMappingDTO) {
				System.out.println("CZTransactionExt taskId for editing merchant mapping:  " + taskId);
					System.out.println("CZTransactionExt taskId for editing merchant mapping data insert inside ");
					try {
						MerchantMappingDTO mmRequestDTO = (MerchantMappingDTO) transactionActionResponse
								.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
						com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();

						MerchantMappingAssembler assembler = new MerchantMappingAssembler();
						mmRequestDTO.setParentMerchantId(mmRequestDTO.getParentMerchantId());
						domain = assembler.toDomainObject(mmRequestDTO);

						List<String> newList = mmRequestDTO.getChildMerchantIdList();
						List<String> merchantmappingdtoList = new ArrayList<>();
						if (domain != null) {
							for (com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping merchantmapping : domain
									.list(domain)) {
								merchantmappingdtoList.add(merchantmapping.getMerchantMappingKey().getChildMerchantId());
							}

//							List<String> oldChildmerchantIdList = merchantmappingdtoList.stream() // need to delete
//									.filter(element -> !newList.contains(element)).collect(Collectors.toList());
							if (transactionAction.equals(ApprovalAction.APPROVE)) {
								if (transactionActionDTO.getTransactionDTO().getErrors() != null
										&& transactionActionDTO.getTransactionDTO().getErrors().size() > 0) {
									System.out.println("Adding REJECTED case in host reject");

									for (String o : merchantmappingdtoList) {
										com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain2 = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();
										domain2 = domain2.read(new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMappingKey(o));
										if ("pendingDelete".equals(domain2.getStatus())){

											domain2.setStatus("completed");
											domain2.update(domain2);
										}

										if("pendingAdd".equals(domain2.getStatus())) {
											domain2.delete(domain2);
										}

									}

								}

							} else {
								for (String o : merchantmappingdtoList) {
									com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping domain2 = new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping();
									domain2 = domain2.read(new com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMappingKey(o));
									if ("pendingDelete".equals(domain2.getStatus())){

										domain2.setStatus("completed");
										domain2.update(domain2);
									}

									if("pendingAdd".equals(domain2.getStatus())) {
										domain2.delete(domain2);
									}

								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			if ("MT_N_DFM".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				System.out.println("CZTransactionExt taskId for Delete Fps Merchant:  " + taskId);
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof FpsMerchantAddressingDTO) {

					if(transactionAction.equals(ApprovalAction.REJECT)) {
						System.out.println("CZTransactionExt taskId for Delete Fps Perchant Inside Reject call" + taskId);
					com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantTxnCrmAssembler merchantTxnCrmAssembler = new com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantTxnCrmAssembler();
					com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantTxnCrm merchantTxnCrmDomain = new com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantTxnCrm();

					com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantProxyTxnCrmAssembler proxyTxnCrmAssembler = new com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantProxyTxnCrmAssembler();
					com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantProxyTxnCrm proxyTxnCrmDomain = new com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantProxyTxnCrm();

					try {

						String txnId = transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId();

						com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
						TransactionKey transactionKey = new TransactionKey();
						transactionKey.setId(txnId);
						transaction = transaction.read(transactionKey);

						System.out.println("Fps Merchant Create Maker Id:" + transaction.getCreatedBy());
						System.out.println("Fps Merchant Create Maker dateTime:" + transaction.getCreationDate());

						String makerId = transaction.getCreatedBy();
						Date creationDate = transaction.getCreationDate();

						FpsMerchantAddressingDTO fpsRequestDTO = (FpsMerchantAddressingDTO) transactionActionResponse
								.getTransactionAction().getTransactionDTO().getTransactionSnapshot();

						merchantTxnCrmDomain = merchantTxnCrmAssembler.toDomainObject(fpsRequestDTO);
						FpsMerchantTxnCrmKey merchantTxnCrmkey = new FpsMerchantTxnCrmKey();
				    	merchantTxnCrmkey.setTxnNo(txnId);
				    	merchantTxnCrmDomain.setKey(merchantTxnCrmkey);

				    	merchantTxnCrmDomain.setFunctionAction("DELETE");
				    	merchantTxnCrmDomain.setTxnStatus(FpsMerchantConstants.FPS_DELETE);
				    	merchantTxnCrmDomain.setTxnResult("REJ");
				    	merchantTxnCrmDomain.setHostRefNo("");
				    	merchantTxnCrmDomain.setMerchantId(fpsRequestDTO.getMerchantId());
				    	merchantTxnCrmDomain.setCustomerId(fpsRequestDTO.getCustomerId());
				    	merchantTxnCrmDomain.setMerchantStatus(fpsRequestDTO.getMerchantStatus());
				    	merchantTxnCrmDomain.create(merchantTxnCrmDomain);
				    	merchantTxnCrmDomain.setCreatedBy(makerId);
				    	merchantTxnCrmDomain.setCreationDate(creationDate);

				    	for(int i=0; i<fpsRequestDTO.getFpsMerchantProxyList().size();i++) {
				    		proxyTxnCrmDomain = proxyTxnCrmAssembler.toDomainObject(fpsRequestDTO.getFpsMerchantProxyList().get(i));
				    	    if(proxyTxnCrmDomain.getKey()!= null) {
				    	    	proxyTxnCrmDomain.getKey().setTxnNo(txnId);
				    	    }
				    		proxyTxnCrmDomain.setFunctionAction("DELETE");
				        	proxyTxnCrmDomain.setTxnResult("REJ");
				        	proxyTxnCrmDomain.setTxnStatus(FpsMerchantConstants.FPS_DELETE);
				        	proxyTxnCrmDomain.setHostRefNo("");
				        	proxyTxnCrmDomain.setCdcId(fpsRequestDTO.getCdcId());
				        	proxyTxnCrmDomain.setCompanyCode(fpsRequestDTO.getCompanyCode());
				        	proxyTxnCrmDomain.setMerchantId(fpsRequestDTO.getMerchantId());
				        	proxyTxnCrmDomain.setProxyStatus(fpsRequestDTO.getFpsMerchantProxyList().get(i).getProxyStatus());
				        	proxyTxnCrmDomain.create(proxyTxnCrmDomain);
				        	proxyTxnCrmDomain.setCreatedBy(makerId);
				        	proxyTxnCrmDomain.setCreationDate(creationDate);
				    	}
					}catch(Exception e){
						e.printStackTrace();
					}

				}
				}

			}

			if ("MT_N_EFM".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName())) {
				System.out.println("CZTransactionExt taskId for Delete Fps Merchant:  " + taskId);
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof FpsMerchantAddressingDTO) {

					if(transactionAction.equals(ApprovalAction.REJECT)) {
						System.out.println("CZTransactionExt taskId for Edit Fps Perchant Inside Reject call" + taskId);
					com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantTxnCrmAssembler merchantTxnCrmAssembler = new com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantTxnCrmAssembler();
					com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantTxnCrm merchantTxnCrmDomain = new com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantTxnCrm();

					com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantProxyTxnCrmAssembler proxyTxnCrmAssembler = new com.ofss.digx.cz.bea.app.fpsmerchant.assembler.FpsMerchantProxyTxnCrmAssembler();
					com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantProxyTxnCrm proxyTxnCrmDomain = new com.ofss.digx.cz.bea.domain.fpsmerchant.entity.FpsMerchantProxyTxnCrm();

					try {

						String txnId = transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId();

						com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
						TransactionKey transactionKey = new TransactionKey();
						transactionKey.setId(txnId);
						transaction = transaction.read(transactionKey);

						System.out.println("Fps Merchant Create Maker Id:" + transaction.getCreatedBy());
						System.out.println("Fps Merchant Create Maker dateTime:" + transaction.getCreationDate());

						String makerId = transaction.getCreatedBy();
						Date creationDate = transaction.getCreationDate();

						FpsMerchantAddressingDTO fpsRequestDTO = (FpsMerchantAddressingDTO) transactionActionResponse
								.getTransactionAction().getTransactionDTO().getTransactionSnapshot();

						merchantTxnCrmDomain = merchantTxnCrmAssembler.toDomainObject(fpsRequestDTO);
						FpsMerchantTxnCrmKey merchantTxnCrmkey = new FpsMerchantTxnCrmKey();
				    	merchantTxnCrmkey.setTxnNo(txnId);
				    	merchantTxnCrmDomain.setKey(merchantTxnCrmkey);

				    	merchantTxnCrmDomain.setFunctionAction("EDIT");
				    	merchantTxnCrmDomain.setTxnStatus(FpsMerchantConstants.FPS_EDIT);
				    	merchantTxnCrmDomain.setTxnResult("REJ");
				    	merchantTxnCrmDomain.setHostRefNo("");
				    	merchantTxnCrmDomain.setMerchantId(fpsRequestDTO.getMerchantId());
				    	merchantTxnCrmDomain.setCustomerId(fpsRequestDTO.getCustomerId());
				    	merchantTxnCrmDomain.setMerchantStatus(fpsRequestDTO.getMerchantStatus());
				    	merchantTxnCrmDomain.create(merchantTxnCrmDomain);
				    	merchantTxnCrmDomain.setCreatedBy(makerId);
				    	merchantTxnCrmDomain.setCreationDate(creationDate);

				    	for(int i=0; i<fpsRequestDTO.getFpsMerchantProxyList().size();i++) {

				    		if(fpsRequestDTO.getFpsMerchantProxyList().get(i).getAction() != null && (fpsRequestDTO.getFpsMerchantProxyList().get(i).getAction().equals("EDIT") || fpsRequestDTO.getFpsMerchantProxyList().get(i).getAction().equals("DELETE") || fpsRequestDTO.getFpsMerchantProxyList().get(i).getAction().equals("REACTIVATE"))) {

				    			proxyTxnCrmDomain = proxyTxnCrmAssembler.toDomainObject(fpsRequestDTO.getFpsMerchantProxyList().get(i));
					    	    if(proxyTxnCrmDomain.getKey()!= null) {
					    	    	proxyTxnCrmDomain.getKey().setTxnNo(txnId);
					    	    }
					    		proxyTxnCrmDomain.setFunctionAction("EDIT");
					        	proxyTxnCrmDomain.setTxnResult("REJ");
					        	proxyTxnCrmDomain.setTxnStatus(FpsMerchantConstants.FPS_EDIT);
					        	proxyTxnCrmDomain.setHostRefNo("");
					        	proxyTxnCrmDomain.setCdcId(fpsRequestDTO.getCdcId());
					        	proxyTxnCrmDomain.setCompanyCode(fpsRequestDTO.getCompanyCode());
					        	proxyTxnCrmDomain.setMerchantId(fpsRequestDTO.getMerchantId());
					        	proxyTxnCrmDomain.setProxyStatus(fpsRequestDTO.getFpsMerchantProxyList().get(i).getProxyStatus());
					        	proxyTxnCrmDomain.create(proxyTxnCrmDomain);
					        	proxyTxnCrmDomain.setCreatedBy(makerId);
					        	proxyTxnCrmDomain.setCreationDate(creationDate);
				    	}
				    }
					}catch(Exception e){
						e.printStackTrace();
					}

				}
				}

			}


			//BCOCDC-3943
			if ("MT_N_CUS".equalsIgnoreCase(
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionName()) && (Boolean)com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
				System.out.println("CZTransactionExt taskId for enable file type after user create approve:  " + taskId);
				com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO userExtensionDataDTO = null;
				if (transactionActionResponse.getTransactionAction().getTransactionDTO()
						.getTransactionSnapshot() instanceof com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO) {
					userExtensionDataDTO = (com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO) transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionSnapshot();
				}
				com.ofss.digx.app.fileupload.dto.UserFIMappingUpdateRequestDTO userFIMappingUpdateRequestDTO = new com.ofss.digx.app.fileupload.dto.UserFIMappingUpdateRequestDTO();
				if (transactionActionResponse != null && transactionActionResponse.getTransactionAction() != null
						&& transactionActionResponse.getTransactionAction().getTransactionDTO() != null
						&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails() != null
						&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails()
						.getStatus() != null
						&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails()
						.getStatus().equals(ProcessingStatus.FAIL)) {
					System.out.println("user create is failed="
							+ transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId());
					com.ofss.digx.infra.thread.ThreadAttribute.set(TRANSACTION_AUDIT_STATUS, ProcessingStatus.FAIL);
				} else {

					String partyId = userExtensionDataDTO.getCdcNo();
					String userId = userExtensionDataDTO.getUserID();
					FileIdentifierRegistration domainObj = new FileIdentifierRegistration();
					userFIMappingUpdateRequestDTO.setPartyId(partyId);
					userFIMappingUpdateRequestDTO.setUserId(userId);
					List<UserFIMappingDTO> fileIdentifers = new ArrayList<>();
					List<FileIdentifierRegistration> fileIdentifierRegistration = domainObj.list(partyId);
					for (FileIdentifierRegistration identifierRegistration : fileIdentifierRegistration) {
						UserFIMappingDTO dto = new UserFIMappingDTO();
						if (identifierRegistration.getTemplateId().equalsIgnoreCase("AutoPayCSV")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						} else if (identifierRegistration.getTemplateId().equalsIgnoreCase("PayRollCSV")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						} else if (identifierRegistration.getTemplateId().equalsIgnoreCase("CollectionCSV")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						} else if (identifierRegistration.getTemplateId().equalsIgnoreCase("AutoPayMASVR3")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						} else if (identifierRegistration.getTemplateId().equalsIgnoreCase("PayRollMASVR3")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						} else if (identifierRegistration.getTemplateId().equalsIgnoreCase("CollectionMASVR3")) {
							dto.setFileIdentifier(identifierRegistration.getKey().getFileIdentifier());
							dto.setSensitiveCheck(false);
							fileIdentifers.add(dto);
						}
					}

					if (!fileIdentifers.isEmpty()){
						userFIMappingUpdateRequestDTO.setFileIdentifers(fileIdentifers);

						try {
							com.ofss.digx.domain.fileupload.entity.UserFIMapping domain = new com.ofss.digx.domain.fileupload.entity.UserFIMapping();
							List<com.ofss.digx.domain.fileupload.entity.UserFIMapping> mappingList = domain.list(userFIMappingUpdateRequestDTO.getUserId());
							int i;
							if (mappingList != null && mappingList.size() > 0) {
								for (i = 0; i < mappingList.size(); ++i) {
									((com.ofss.digx.domain.fileupload.entity.UserFIMapping) mappingList.get(i)).delete((com.ofss.digx.domain.fileupload.entity.UserFIMapping) mappingList.get(i));
								}
							}

							if (userFIMappingUpdateRequestDTO.getFileIdentifers() != null && userFIMappingUpdateRequestDTO.getFileIdentifers() != null && userFIMappingUpdateRequestDTO.getFileIdentifers().size() > 0) {
								for (i = 0; i < userFIMappingUpdateRequestDTO.getFileIdentifers().size(); ++i) {
									com.ofss.digx.domain.fileupload.entity.UserFIMapping domainToInsert = new com.ofss.digx.domain.fileupload.entity.UserFIMapping();
									IdGenerator secureGenerator = (IdGenerator) AbstractGeneratorFactory.getUniqueInstance().getIdGenerator("UserMapping", "Id");
									String generatedId = null;
									UserFIMappingKey partyTemplateRelationshipKey = new UserFIMappingKey();
									generatedId = secureGenerator.generateId("UserMapping", "Id", "", -1L, new HashMap());
									Integer generated = Integer.parseInt(generatedId);
									partyTemplateRelationshipKey.setUserMappingId(generated.toString());
									partyTemplateRelationshipKey.setDeterminantValue(DeterminantResolver.getInstance().fetchDeterminantValue(com.ofss.digx.domain.fileupload.entity.UserFIMapping.class.getName()));
									domainToInsert.setKey(partyTemplateRelationshipKey);
									domainToInsert.setUserId(userFIMappingUpdateRequestDTO.getUserId());
									domainToInsert.setPartyId(userFIMappingUpdateRequestDTO.getPartyId());
									domainToInsert.setSensitiveCheck(((UserFIMappingDTO) userFIMappingUpdateRequestDTO.getFileIdentifers().get(i)).isSensitiveCheck());
									domainToInsert.setFileIdentifier(((UserFIMappingDTO) userFIMappingUpdateRequestDTO.getFileIdentifers().get(i)).getFileIdentifier());
									domainToInsert.create(domainToInsert);
								}
							}

						} catch (Exception var17) {
							this.logger.log(Level.SEVERE, this.formatter.formatMessage(" Exception encountered while invoking the service %s while updatings user template relationship", new Object[]{THIS_COMPONENT_NAME}), var17);
						} catch (RuntimeException var18) {
							this.logger.log(Level.SEVERE, this.formatter.formatMessage("Runtime exception encountered while invoking the service %s while updatings user template relationship", new Object[0]), var18);
						} catch (FatalException var19) {
							FatalException e = var19;
							this.logger.log(Level.SEVERE, this.formatter.formatMessage("Runtime exception encountered while invoking the service %s while updatings user template relationship", new Object[0]), e);
						}
					}
				}
			}


		}

		if (getOMBAuditFlag() && transactionActionResponse != null
				&& transactionActionResponse.getTransactionAction() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId() != null) {
			System.out.println("Set transaction ref no="
					+ transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId());
			com.ofss.digx.infra.thread.ThreadAttribute.set("AUDIT_TXN_REF_NO",
					transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId());
		}

		if (transactionActionResponse != null && transactionActionResponse.getTransactionAction() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails()
						.getStatus() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO().getProcessingDetails()
						.getStatus().equals(ProcessingStatus.FAIL)) {
			System.out.println("EXT Transaction is failed="
					+ transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId());
			com.ofss.digx.infra.thread.ThreadAttribute.set(TRANSACTION_AUDIT_STATUS, ProcessingStatus.FAIL);
		}

		if (transactionActionResponse != null && transactionActionResponse.getTransactionAction() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO() != null
				&& transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId() != null) {
			String txnId = transactionActionResponse.getTransactionAction().getTransactionDTO().getTransactionId();
			CZPayeeEnquiryResponseDTO suspiciousPayeeResponse = null;
			System.out.println("suspicious check to be added here");
			com.ofss.digx.cz.bea.app.payment.service.common.CZPaymentCommonFunc serviceObj = new com.ofss.digx.cz.bea.app.payment.service.common.CZPaymentCommonFunc();
			System.out.println("TxnNo for suspicioue payee check " + txnId);
			suspiciousPayeeResponse = serviceObj.enquireSuspiciousPayeeByTxnId(sessionContext, txnId);

			if (suspiciousPayeeResponse != null && suspiciousPayeeResponse.getSuspiciousIndicator() != null
					&& (suspiciousPayeeResponse.getSuspiciousIndicator().equals("R")
							|| suspiciousPayeeResponse.getSuspiciousIndicator().equals("NULL_TIMED_OUT"))) {
				System.out.println("IS_SUSPICIOUS_INDICATOR in POST TXN APPROVE true"
						+ suspiciousPayeeResponse.getSuspiciousIndicator());
				ThreadAttribute.set("IS_SUSPICIOUS_INDICATOR", "true");
				com.ofss.digx.infra.thread.ThreadAttribute.set("IS_SUSPICIOUS_INDICATOR", "true");
			} else {
				System.out.println("IS_SUSPICIOUS_INDICATOR in POST TXN APPROVE else");
				ThreadAttribute.set("IS_SUSPICIOUS_INDICATOR", "false");
				com.ofss.digx.infra.thread.ThreadAttribute.set("IS_SUSPICIOUS_INDICATOR", "false");
			}
		}
		new CZTransactionExtFunc().postPerformAction(sessionContext, transactionActionDTO, transactionActionResponse);
	}

	/**
	 * Processes after the execution of actual business logic for checking approvals
	 * for a transaction. Executes the post extension logic after the actual
	 * business logic.
	 *
	 * @param sessionContext    {@link SessionContext} containing session details
	 * @param transactionDTO    {@link TransactionDTO} containing the current
	 *                          Transaction details.
	 * @param response          {@link Object} represents the response of the
	 *                          transaction.
	 * @param transactionStatus {@link TransactionStatus} containing status of the
	 *                          transaction
	 * @throws Exception
	 */
	@Override
	public void postSetUpCheckerDetails(SessionContext sessionContext, TransactionDTO transactionDTO, Object response,
			TransactionStatus transactionStatus) {
		if (transactionDTO != null) {
			String taskId = transactionDTO.getTransactionName();
			ApprovalAction transactionAction = transactionDTO.getApprovalDetails().getAction();
			System.out.println("In postSetUpCheckerDetails : Task Id = " + taskId + ", transactionAction = "
					+ transactionAction.toString());
			if (taskId != null && taskId.equalsIgnoreCase("CH_N_RADHSTMT")
					&& transactionAction.equals(ApprovalAction.REJECT)) {
				IAdapterFactory adapterfactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.ADHOC_STATEMENT_ADAPTER_FACTORY);
				IAdhocStatementAdapter adapter = (IAdhocStatementAdapter) adapterfactory.getAdapter(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.ADHOC_STATEMENT_ADAPTER);
				AdhocStatementDTO request = new AdhocStatementDTO();
				request.setTxnReferenceNumber(transactionDTO.getTransactionId());
				try {
					System.out.println("In postSetUpCheckerDetails : I am inside If and calling the service");
					adapter.updateTxnStatusPostApproval(request);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static String getMasterAccount(String accountNumber, String productCode) {
		String masterAccount = accountNumber;
		if (productCode.equalsIgnoreCase("6808") || productCode.equalsIgnoreCase("6802")
				|| productCode.equalsIgnoreCase("6805") || productCode.equalsIgnoreCase("6803")
				|| productCode.equalsIgnoreCase("8808") || productCode.equalsIgnoreCase("8802")
				|| productCode.equalsIgnoreCase("8805") || productCode.equalsIgnoreCase("8803")) {
			String[] accountId = accountNumber.split("~");
			StringBuffer formattedAccountId = new StringBuffer(accountId[0].substring(0, 10));
			formattedAccountId.append("10");
			formattedAccountId.append(accountId[0].substring(12, 18));
			formattedAccountId.append("~");
			formattedAccountId.append(accountId[1].substring(0, 2));
			formattedAccountId.append("00~MST");
			masterAccount = formattedAccountId.toString();
		}
		return masterAccount;
	}

	public CurrentAccountDTO getAccountDetails(String accountNumber) {
		// TODO Auto-generated method stub

		CurrentAccountDTO accountDTO = new CurrentAccountDTO();

		IAdapterFactory taskAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(CommonAdapterFactoryConstants.ACCOUNT_DETAILS_ADAPTER_FACTORY);

		com.ofss.digx.cz.bea.app.helper.adapter.IAccountDetailsAdapter customConfigAdapter = (com.ofss.digx.cz.bea.app.helper.adapter.IAccountDetailsAdapter) taskAdapterFactory
				.getAdapter(CommonAdapterConstants.ACCOUNT_DETAILS_ADAPTER);

		accountDTO = customConfigAdapter.getAccountDetails(accountNumber);

		return accountDTO;
	}

	public Date getNewValueDate(NetworkPaymentCreateRequestDTO request, String debitAcCurCode, String creditAcCurCode,
			Date paymentDate) throws Exception {

		String holidayErrorReqd = CustomConfigUtil.readConfigValue("IS_HOLIDAY_ERR_REQD_FT", "Y");
		Date newDate = null;

		if (!"Y".equalsIgnoreCase(holidayErrorReqd)) {
			try {
				if (CZCommonUtils.isDateHoliday(null, "HKD", paymentDate)) {
					// ***using HKD calendar instead of tfrCurr, as per generic FS
					System.out.println("CZTransactionExt paymentDate=" + paymentDate + " is a holiday");
					newDate = BranchDateHelper.getNextWorkingDate(paymentDate, BranchDateHelper.DF_yyyyMMdd);
				}
			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
		}

		if (newDate == null) {
			newDate = CZCommonValidation.isPaymentDataChgWW(request.getPaymentDetails().getNetwork(), debitAcCurCode,
					creditAcCurCode, request.getPaymentDetails().getAmount().getCurrency(),
					request.getPaymentDetails().isAdhocPayment(), request.getPaymentDetails().getPaymentDate());
		}

		if (SysoutLogger.ISENABLED)
			SysoutLogger.println("CZTransactionExt paymentDate=" + paymentDate + " holidayErrorReqd" + holidayErrorReqd
					+ " newDate=" + newDate);
		return newDate;

	}



	@Override
	public void postRead(SessionContext sessionContext, TransactionDTO transactionDTO, Boolean isDownload,
			TransactionResponse transactionResponse) throws Exception {
		if (transactionResponse != null) {
			new HthUserAccessTransactionName().enrich(transactionResponse.getTransactionDTO());
		}

		String txnId = null;
		String txnName = null;

		String isPrd146Dep = CustomConfigUtil.readConfigValue("IS_PRD146_DEP", "N");
		String isReg750Dep = CustomConfigUtil.readConfigValue("IS_REG750_DEP", "N");

		System.out.println("CZTransactionExt postRead() isReg750Dep=" + isReg750Dep + " isPrd146Dep=" + isPrd146Dep);

		try {
			if (transactionResponse != null && transactionResponse.getTransactionDTO() != null) {
				txnId = transactionResponse.getTransactionDTO().getTransactionId();
				txnName = transactionResponse.getTransactionDTO().getTransactionName();
				for (ProcessingErrorDTO error : transactionResponse.getTransactionDTO().getErrors()) {
					String originalErrorMessage = error.getErrorMessage();
					System.out.println("** originalErrorMessage is =" + originalErrorMessage);
					System.out.println("** original error code is =" + error.getErrorCode());
					String errorMessage = getExceptionMessage(error.getErrorCode());
					String errorCodeWhite = CustomConfigUtil.readConfigValue("ERROR_CODE_WHITE_LIST", "");
					List<String> errorCodeWhiteList = new ArrayList<>();
					if(StringUtils.isNotBlank(errorCodeWhite)) {
						errorCodeWhiteList = Arrays.asList(errorCodeWhite.split(","));
					}
					if (errorMessage != null && errorMessage.trim().length() > 0 && !errorCodeWhiteList.contains(error.getErrorCode())) {
						error.setErrorMessage(errorMessage);
					}
					System.out.println("Translating Error Message for Error Code=" + error.getErrorCode() + " txnid="
							+ transactionResponse.getTransactionDTO().getTransactionId() + " errorMessage="
							+ error.getErrorMessage());

					if (error.getErrorCode().equals("DIGX_PROD_DEF_0000")
							|| error.getErrorCode().equals("DIGX_DEF_ERR_CD_001")) {
						int colonIndex = originalErrorMessage.indexOf(":");

						if (colonIndex > 0) {
							String newErrorMessage = originalErrorMessage.substring(0, colonIndex);
							System.out.println("** newErrorMessage is = " + newErrorMessage);

							if (!newErrorMessage.trim().isEmpty() && errorMessage != null
									&& errorMessage.trim().length() > 0) {
								error.setErrorMessage(
										errorMessage.concat(" (").concat(newErrorMessage.trim()).concat(")"));
							}
						}
					}
				}
			}

			if (txnName != null && txnName.equalsIgnoreCase("FU_N_UFR")) {
				TransactionDTO transactionDTOResp = transactionResponse.getTransactionDTO();
				if (transactionDTOResp.getTransactionSnapshot() instanceof FileIdentifierRegistrationUpdateRequestDTO) {
					FileIdentifierRegistrationUpdateRequestDTO fileDTO = (FileIdentifierRegistrationUpdateRequestDTO) transactionDTOResp
							.getTransactionSnapshot();
					if (fileDTO != null && fileDTO.getFileIdentifierRegistrationDTO() != null) {
						System.out.println("Inside postRead of CZTransactionExt update");
						if (fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords() != null) {
							int maxRecords = Integer
									.parseInt(fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords()) - 1;
							System.out.println("Max Records before adding read: "
									+ fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords() + "After adding :"
									+ maxRecords);
							fileDTO.getFileIdentifierRegistrationDTO().setMaxNoOfRecords(String.valueOf(maxRecords));
						}

					}
				}
			}

			if (txnName != null && txnName.equalsIgnoreCase("FU_N_CFR")) {
				TransactionDTO transactionDTOResp = transactionResponse.getTransactionDTO();
				if (transactionDTOResp.getTransactionSnapshot() instanceof FileIdentifierRegistrationCreateRequestDTO) {
					FileIdentifierRegistrationCreateRequestDTO fileDTO = (FileIdentifierRegistrationCreateRequestDTO) transactionDTOResp
							.getTransactionSnapshot();
					if (fileDTO != null && fileDTO.getFileIdentifierRegistrationDTO() != null) {
						System.out.println("Inside postRead of CZTransactionExt create");
						if (fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords() != null) {
							int maxRecords = Integer
									.parseInt(fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords()) - 1;
							System.out.println("Max Records before adding read: "
									+ fileDTO.getFileIdentifierRegistrationDTO().getMaxNoOfRecords() + "After adding :"
									+ maxRecords);
							fileDTO.getFileIdentifierRegistrationDTO().setMaxNoOfRecords(String.valueOf(maxRecords));
						}

					}
				}
			}

			if (txnName != null && txnName.endsWith("_SI")) {

				Locale locale = LocaleUtils.getUserLocale();
				if (locale == null) {
					locale = LocaleUtils.getDefaultLocale();
				}

				TransactionApprovalDetailsDTO approvalDetails = transactionResponse.getTransactionDTO()
						.getApprovalDetails();
				TransactionUserDetailsDTO transactionUserDetails = transactionResponse.getTransactionDTO()
						.getUpdatedByDetails();
				if ("Y".equalsIgnoreCase(isPrd146Dep) && approvalDetails != null
						&& approvalDetails.getStatus() == ApprovalStatus.EXPIRED
						&& !locale.toString().toLowerCase().contains("en")) {
					String storedMessage = null;
					String storedFirstName = null;
					ResourceBundle rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
							LocaleUtils.getDefaultLocale(), ResourseBundleControl.class.getClassLoader(),
							ResourseBundleControl.getInstance());
					storedMessage = rb.getString("DIGX_CZ_AP_0002");
					storedFirstName = rb.getString("SYSTEM_EXPIRED_USER_NAME");

					if (storedMessage != null && storedMessage.equalsIgnoreCase(approvalDetails.getRemarks())) {
						rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(), locale,
								ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
					}
					String localizedMessage = rb.getString("DIGX_CZ_AP_0002");
					transactionResponse.getTransactionDTO().getApprovalDetails().setRemarks(localizedMessage);
					System.out.println("CZTransactionExt postRead replacing stored  remarks [" + storedMessage
							+ "] with [" + localizedMessage + "] for locale=" + locale);

					if (storedFirstName != null && storedFirstName.equalsIgnoreCase(transactionUserDetails.getFirstName())) {
						rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(), locale,
								ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
					}
					String localizedFirstName = rb.getString("SYSTEM_EXPIRED_USER_NAME");
					transactionResponse.getTransactionDTO().getUpdatedByDetails().setFirstName(localizedFirstName);
					System.out.println("CZTransactionExt postRead replacing stored  first name [" + storedFirstName
							+ "] with [" + localizedFirstName + "] for locale=" + locale);

					if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs() != null
							&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length > 1) {
						int len = transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length;
						for (int l = 0; l < len; l++) {
							if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
									.getApprovalDetails() != null
									&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
											.getApprovalDetails().getStatus() == ApprovalStatus.EXPIRED) {
								if (storedMessage != null && storedMessage.equalsIgnoreCase(
										transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
												.getApprovalDetails().getRemarks())) {
									rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
											locale, ResourseBundleControl.class.getClassLoader(),
											ResourseBundleControl.getInstance());
								}

								transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
										.getApprovalDetails().setRemarks(localizedMessage);
								System.out.println("CZTransactionExt postRead replacing stored  remarks ["
										+ storedMessage + "] with [" + localizedMessage + "] for locale=" + locale
										+ " for history DTO");
							}
						}
					}

				} else if ("Y".equalsIgnoreCase(isReg750Dep) && approvalDetails != null
						&& approvalDetails.getStatus() == ApprovalStatus.APPROVED && transactionResponse
								.getTransactionDTO().getProcessingDetails().getStatus() == ProcessingStatus.SUCCESS) {
					IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
							ICZNetworkPaymentCrossDomainAdapter.NWPAYMENT_CROSS_DOMAIN_ADAPTER_FACTORY);
					ICZNetworkPaymentCrossDomainAdapter adapter = (ICZNetworkPaymentCrossDomainAdapter) adapterFactory
							.getAdapter(ICZNetworkPaymentCrossDomainAdapter.NWPAYMENT_CROSS_DOMAIN_ADAPTER);

					CZPaymentSearchRequest requestDTO = new CZPaymentSearchRequest();
					requestDTO.setApTxnNo(txnId);

					if (txnName.contains("CRNSFT") || txnName.contains("CRNIFT") || txnName.contains("CRNBCT")) {
						System.out.println("CZTransactionExt : postRead() : Searching internal with txnId=" + txnId);
						CZNetworkPaymentInternalDTO readInternalResp = adapter.readNwPaymentInternal(requestDTO);
						System.out.println("CZTransactionExt : postRead() : Status=" + readInternalResp.getStatuscz());
						if (readInternalResp.getStatuscz() == CZPaymentStatusType.DELETED) {
							TransactionDTO transactionDTOResp = transactionResponse.getTransactionDTO();
							if (transactionDTOResp.getTransactionSnapshot() instanceof NetworkPaymentCreateRequestDTO) {
								transactionDTOResp.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTOResp.getDictionaryArray(),
										new String[][] { { "SI_STATUS", readInternalResp.getStatuscz().toString() },
												{ "SI_UPD_BY", readInternalResp.getLastUpdatedBy() },
												{ "SI_UPD_DATE", readInternalResp.getLastUpdatedDate() + "" } }));
							}
						}

					} else if (txnName.contains("CRNDFT") || txnName.contains("CRNINFT")) {
						System.out.println("Searching payout with txnId=" + txnId);
						adapter.readNwPaymentPayout(requestDTO);

						System.out.println("CZTransactionExt : postRead() : Searching payout with txnId=" + txnId);
						CZNetworkPaymentPayoutDTO readPayoutResp = adapter.readNwPaymentPayout(requestDTO);
						System.out.println("CZTransactionExt : postRead() : Status=" + readPayoutResp.getStatuscz());
						if (readPayoutResp.getStatuscz() == CZPaymentStatusType.DELETED) {
							TransactionDTO transactionDTOResp = transactionResponse.getTransactionDTO();
							if (transactionDTOResp.getTransactionSnapshot() instanceof NetworkPaymentCreateRequestDTO) {
								transactionDTOResp.setDictionaryArray(DArrayUtils.addStringsToDarray(
										transactionDTOResp.getDictionaryArray(),
										new String[][] { { "SI_STATUS", readPayoutResp.getStatuscz().toString() },
												{ "SI_UPD_BY", readPayoutResp.getLastUpdatedBy() },
												{ "SI_UPD_DATE", readPayoutResp.getLastUpdatedDate() + "" } }));
							}
						}

					} else {
						System.out.println("Not Searching any record as txnName=" + txnName);
					}

				}

			}

			if (txnName != null && ("TD_F_OTD".equals(txnName) || "CTD_C".equals(txnName))) {

				Locale locale = LocaleUtils.getUserLocale();
				if (locale == null) {
					locale = LocaleUtils.getDefaultLocale();
				}

				TransactionApprovalDetailsDTO approvalDetails = transactionResponse.getTransactionDTO()
						.getApprovalDetails();
				if ("Y".equalsIgnoreCase(isPrd146Dep) && approvalDetails != null
						&& approvalDetails.getStatus() == ApprovalStatus.EXPIRED
						&& !locale.toString().toLowerCase().contains("en")) {
					String storedMessage = null;
					ResourceBundle rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
							LocaleUtils.getDefaultLocale(), ResourseBundleControl.class.getClassLoader(),
							ResourseBundleControl.getInstance());
					storedMessage = rb.getString("DIGX_CZ_AP_0010");

					if (storedMessage != null && storedMessage.equalsIgnoreCase(approvalDetails.getRemarks())) {
						rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(), locale,
								ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
					}
					String localizedMessage = rb.getString("DIGX_CZ_AP_0010");
					transactionResponse.getTransactionDTO().getApprovalDetails().setRemarks(localizedMessage);
					System.out.println("CZTransactionExt postRead replacing stored  remarks [" + storedMessage
							+ "] with [" + localizedMessage + "] for locale=" + locale);

					if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs() != null
							&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length > 1) {
						int len = transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length;
						for (int l = 0; l < len; l++) {
							if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
									.getApprovalDetails() != null
									&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
											.getApprovalDetails().getStatus() == ApprovalStatus.EXPIRED) {
								if (storedMessage != null && storedMessage.equalsIgnoreCase(
										transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
												.getApprovalDetails().getRemarks())) {
									rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
											locale, ResourseBundleControl.class.getClassLoader(),
											ResourseBundleControl.getInstance());
								}

								transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
										.getApprovalDetails().setRemarks(localizedMessage);
								System.out.println("CZTransactionExt postRead replacing stored  remarks ["
										+ storedMessage + "] with [" + localizedMessage + "] for locale=" + locale
										+ " for history DTO");
							}
						}
					}

					else {
						System.out.println("Not Searching any record as txnName=" + txnName);
					}

				}

			}


			String[] lmTasks = { "LMI", "LMI_F_CLM", "LMI_F_ACLM", "LMI_F_CCLM", "LMI_F_LCLM", "LMI_F_TCLM","LMI_F_OCLM",
					"LMI_F_ULM", "LMI_F_AULM", "LMI_F_CULM", "LMI_F_LULM", "LMI_F_TULM","LMI_F_OULM" };
			List<String> lmTasksList = Arrays.asList(lmTasks);

			if (txnName != null && (lmTasksList.contains(txnName))) {

				Locale locale = LocaleUtils.getUserLocale();
				if (locale == null) {
					locale = LocaleUtils.getDefaultLocale();
				}
				TransactionApprovalDetailsDTO approvalDetails = transactionResponse.getTransactionDTO()
						.getApprovalDetails();

//				if ("Y".equalsIgnoreCase(isPrd146Dep) && approvalDetails != null
//						&& approvalDetails.getStatus() == ApprovalStatus.REJECTED
//						&& !locale.toString().toLowerCase().contains("en")) {
//					String storedMessages = null;
//					ResourceBundle rsb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
//							LocaleUtils.getDefaultLocale(), ResourseBundleControl.class.getClassLoader(),
//							ResourseBundleControl.getInstance());
//					storedMessages = rsb.getString("DIGX_HOST_LME_LME00083");
//
//					if (storedMessages != null && storedMessages.equalsIgnoreCase(approvalDetails.getRemarks())) {
//						rsb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(), locale,
//								ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
//					}
//					Preferences dayOneConfigPref = ConfigurationFactory.getInstance()
//							.getConfigurations(CommonConstants.DAY_ONE_CONFIG);
//					String maxInstruction = dayOneConfigPref.get("LM_MAX_INSTRUCTIONS_LIMIT", "99");
//					System.out.println("maxInstruction : " + maxInstruction);

//					if (rsb.getString=="DIGX_HOST_LME_LME00083";

//					String localizedMessages = storedMessages.replace("Day0", maxInstruction);
//					transactionResponse.getTransactionDTO().getApprovalDetails().setRemarks(localizedMessages);
//					System.out.println("CZTransactionExt LM_MAX_TXN remarks [" + storedMessages + "] with ["
//							+ localizedMessages + "] for locale=" + locale);
//				}

//				List<ProcessingErrorDTO> errorList = transactionResponse.getTransactionDTO().getErrors();
				System.out.println("txn responses: "+transactionResponse);
				if (transactionResponse != null && transactionResponse.getTransactionDTO() != null) {
					System.out.println("Error List:" + transactionResponse.getTransactionDTO().getErrors());
					for (ProcessingErrorDTO error : transactionResponse.getTransactionDTO().getErrors()) {
						System.out.println(
								"Entering Error List loop and LM Error Code: " + error.getErrorCode());
						if (error.getErrorCode().equals("DIGX_HOST_LME_LME00083")) {
							ResourceBundle rb = ResourceBundle.getBundle(
									ErrorMessageResourceBundle.class.getCanonicalName(), LocaleUtils.getDefaultLocale(),
									ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
							Preferences dayOneConfigPref = ConfigurationFactory.getInstance()
									.getConfigurations(CommonConstants.DAY_ONE_CONFIG);
							String maxInstruction = dayOneConfigPref.get("LM_MAX_INSTRUCTIONS_LIMIT", "99");
							String localizedMessage = rb.getString("DIGX_HOST_LME_LME00083");
							localizedMessage = localizedMessage.replace("Day0", maxInstruction);
//						transactionResponse.getTransactionDTO().getApprovalDetails().setRemarks(localizedMessage);
							error.setErrorMessage(localizedMessage);
							System.out.println("MAXIMUM LM INSTRUCTIONS : " + maxInstruction);
							System.out.println("Localized LM Message: " + error);
//						error.setErrorMessage(errorMessage);

						}
					}
				}

				if ("Y".equalsIgnoreCase(isPrd146Dep) && approvalDetails != null
						&& approvalDetails.getStatus() == ApprovalStatus.EXPIRED
						&& !locale.toString().toLowerCase().contains("en")) {
					String storedMessage = null;
					ResourceBundle rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
							LocaleUtils.getDefaultLocale(), ResourseBundleControl.class.getClassLoader(),
							ResourseBundleControl.getInstance());
					storedMessage = rb.getString("DIGX_CZ_LM_0010");

					if (storedMessage != null && storedMessage.equalsIgnoreCase(approvalDetails.getRemarks())) {
						rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(), locale,
								ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
					}
					String localizedMessage = rb.getString("DIGX_CZ_LM_0010");
					transactionResponse.getTransactionDTO().getApprovalDetails().setRemarks(localizedMessage);
					System.out.println("CZTransactionExt Liquidity Managementt postRead replacing stored  remarks [" + storedMessage
							+ "] with [" + localizedMessage + "] for locale=" + locale);

					if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs() != null
							&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length > 1) {
						int len = transactionResponse.getTransactionDTO().getTransactionHistoryDTOs().length;
						for (int l = 0; l < len; l++) {
							if (transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
									.getApprovalDetails() != null
									&& transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
											.getApprovalDetails().getStatus() == ApprovalStatus.EXPIRED) {
								if (storedMessage != null && storedMessage.equalsIgnoreCase(
										transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
												.getApprovalDetails().getRemarks())) {
									rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
											locale, ResourseBundleControl.class.getClassLoader(),
											ResourseBundleControl.getInstance());
								}

								transactionResponse.getTransactionDTO().getTransactionHistoryDTOs()[l]
										.getApprovalDetails().setRemarks(localizedMessage);
								System.out.println("CZTransactionExt Liquidity Managementt postRead replacing stored  remarks ["
										+ storedMessage + "] with [" + localizedMessage + "] for locale=" + locale
										+ " for LM");
							}
						}
					}

					else {
						System.out.println("Not Searching any LM record as txnName=" + txnName);
					}

				}

			}



			System.out.println("CZTransactionExt postRead  txnId=" + txnId + " isPendingApproval="
					+ transactionResponse.isPendingApproval() + " isPendingModification="
					+ transactionResponse.isPendingModification() + " txnName=" + txnName);


			try {
				if (transactionResponse.isPendingApproval() != null
						&& transactionResponse.isPendingApproval().booleanValue() == false
						&& transactionResponse.isPendingModification() != null
						&& transactionResponse.isPendingModification().booleanValue() == false
						&& transactionResponse != null && transactionResponse.getTransactionDTO() != null
						&& transactionResponse.getTransactionDTO()
								.getTransactionSnapshot() instanceof NetworkPaymentCreateRequestDTO) {
					NetworkPaymentCreateRequestDTO requestSnapshot = (NetworkPaymentCreateRequestDTO) transactionResponse
							.getTransactionDTO().getTransactionSnapshot();

					NetworkPaymentDTO paymentDetails = requestSnapshot.getPaymentDetails();// APPROVAL_PAYMENT_DATE
					String isOmbFlow = DArrayUtils.getFieldFromDarray(
							requestSnapshot.getPaymentDetails().getDictionaryArray(), "IS_OMB_FLOW");

					if (!InputValidationUtils.isNullOrBlankTrim(isOmbFlow) && "true".equalsIgnoreCase(isOmbFlow)) {
						Date chgValueDate = null;
						IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
								ICZNetworkPaymentCrossDomainAdapter.NWPAYMENT_CROSS_DOMAIN_ADAPTER_FACTORY);
						ICZNetworkPaymentCrossDomainAdapter adapter = (ICZNetworkPaymentCrossDomainAdapter) adapterFactory
								.getAdapter(ICZNetworkPaymentCrossDomainAdapter.NWPAYMENT_CROSS_DOMAIN_ADAPTER);

						CZPaymentSearchRequest requestDTO = new CZPaymentSearchRequest();
						requestDTO.setApTxnNo(txnId);

						if (txnName.contains("CRNSFT") || txnName.contains("CRNIFT") || txnName.contains("CRNBCT")) {
							System.out
									.println("CZTransactionExt : postRead() : Searching internal with txnId=" + txnId);
							CZNetworkPaymentInternalDTO readInternalResp = adapter.readNwPaymentInternal(requestDTO);

							if (readInternalResp != null) {
								chgValueDate = readInternalResp.getValueDate();
							}

							System.out.println("CZTransactionExt : postRead() : INTERNAL chgValueDate=" + chgValueDate);
						} else if (txnName.contains("CRNDFT") || txnName.contains("CRNINFT")) {
							System.out.println("Searching payout with txnId=" + txnId);
							adapter.readNwPaymentPayout(requestDTO);

							System.out.println("CZTransactionExt : postRead() : Searching payout with txnId=" + txnId);
							CZNetworkPaymentPayoutDTO readPayoutResp = adapter.readNwPaymentPayout(requestDTO);
							if (readPayoutResp != null) {
								chgValueDate = readPayoutResp.getValueDate();
							}
							System.out.println("CZTransactionExt : postRead() : PAYOUT chgValueDate=" + chgValueDate);
							;
						}

						if (chgValueDate != null && chgValueDate.isAfter(paymentDetails.getPaymentDate())) {
							System.out.println("CZTransactionExt postRead orig payment date="
									+ paymentDetails.getPaymentDate() + " resetting to " + chgValueDate);
							((NetworkPaymentCreateRequestDTO) transactionResponse.getTransactionDTO()
									.getTransactionSnapshot()).getPaymentDetails().setPaymentDate(chgValueDate);
						}

					} else {
						String approvalPaymentDate = DArrayUtils.getFieldFromDarray(
								requestSnapshot.getPaymentDetails().getDictionaryArray(), "APPROVAL_PAYMENT_DATE");
						if (approvalPaymentDate != null
								&& new Date(approvalPaymentDate).isAfter(paymentDetails.getPaymentDate())) {
							System.out.println("CZTransactionExt postRead orig payment date="
									+ paymentDetails.getPaymentDate() + " resetting to " + approvalPaymentDate);
							((NetworkPaymentCreateRequestDTO) transactionResponse.getTransactionDTO()
									.getTransactionSnapshot()).getPaymentDetails()
											.setPaymentDate(new Date(approvalPaymentDate));
						}
					}

				}

			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
			new CZTransactionExtFunc().postRead(sessionContext,transactionDTO, isDownload, transactionResponse);
			if (transactionResponse != null && transactionResponse.getTransactionDTO() != null
					&& transactionResponse.getTransactionDTO() instanceof PartyTransactionDTO
					&& transactionResponse.getTransactionDTO().getTransactionName() != null
					&& transactionResponse.getTransactionDTO().getTransactionName().startsWith("PC_N_")) {

				TransactionDTO transactionDTOResp = transactionResponse.getTransactionDTO();
				System.out.println("CZTransactionExt transactionDTOResp=" + transactionDTOResp);
				if (transactionDTOResp
						.getTransactionSnapshot() instanceof com.ofss.digx.app.common.dto.DataTransferObject) {
					com.ofss.digx.app.common.dto.DataTransferObject dto = (com.ofss.digx.app.common.dto.DataTransferObject) transactionDTOResp
							.getTransactionSnapshot();
					dto.setDictionaryArray(DArrayUtils.addStringsToDarray(dto.getDictionaryArray(), new String[][] { {
							"PARTY_NAME", ((PartyTransactionDTO) transactionDTOResp).getPartyName().getFullName() } }));
					if (SysoutLogger.ISENABLED)
						SysoutLogger.println("postRead Party Name"
								+ ((PartyTransactionDTO) transactionDTOResp).getPartyName().getFullName());
				}

			}

		} catch (java.lang.Exception e) {
			e.printStackTrace();
		}
	}

	public static String getExceptionMessage(String... errorCode) {
		Locale userLocale = LocaleUtils.getUserLocale();
		Locale defaultLocale = LocaleUtils.getDefaultLocale();

		String userLocaleString = (String) ThreadAttribute.get("USER_LOCALE");

		if (InputValidationUtils.isNullOrBlankTrim(userLocaleString)) {
			String locale = CZLocaleUtils.getUserLocale();
			System.out.println("getExceptionMessage Locale using CZLocaleUtils=" + locale);
			userLocale = new java.util.Locale(locale);
		}

		String localizedMessage = null;
		try {
			ResourceBundle rb = ResourceBundle.getBundle(ErrorMessageResourceBundle.class.getCanonicalName(),
					userLocale, ResourseBundleControl.class.getClassLoader(), ResourseBundleControl.getInstance());
			localizedMessage = rb.getString(errorCode[0]);
		} catch (MissingResourceException ex) {
			System.out.println("In CZCommonUtils.getExceptionMessage : " + errorCode[0] + " not found");
		}
		return localizedMessage;
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

	private boolean getCZCountTempFlag() {
		IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory("CUSTOM_CONFIG_ADAPTER_FACTORY");
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter("CUSTOM_CONFIG_ADAPTER");
		String flag = customConfigAdapter.getConfiguationDetails(
				com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, "CZ_COUNT_TEMP_FLAG", "false");
		return Boolean.parseBoolean(flag);
	}

	private String generateCouponCodeBCO() {
		// Get current date in YYMMDD format
		String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

		Query qry = DataAccessManager.getManager().fetchCurrentSession()
				.createSQLQuery("SELECT TD_MODE1_COUPON_CODE_SEQUENCE.NEXTVAL AS NEW_VAL FROM DUAL");
		long sequence = ((BigDecimal) qry.uniqueResult()).longValue();
		String sequenceValue = String.valueOf(sequence);

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 3 - sequenceValue.length(); i++) {
			sb.append('0');
		}
		sb.append(sequenceValue);
		System.out.println("generateCouponCodeBCO sb.toString() " + sb.toString());

		// Construct the coupon code in the specified format
		String couponCodeBCO = "AA" + currentDate + sb.toString();
		System.out.println("new generateCouponCodeBCO CouponCode: " + couponCodeBCO);

		return couponCodeBCO;
	}

	private static String getFlagForAutopayPayroll(String propId) {
		com.ofss.digx.app.adapter.IAdapterFactory customConfigAdapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(
						com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.CUSTOM_CONFIG_ADAPTER_FACTORY);
		ICustomConfigAdapter customConfigAdapter = (ICustomConfigAdapter) customConfigAdapterFactory
				.getAdapter(com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.CUSTOM_CONFIG_ADAPTER);
		String propValue = customConfigAdapter
				.getConfiguationDetails(com.ofss.digx.common.constants.CommonConstants.DAY_ONE_CONFIG, propId, "");
		return propValue;
	}


	private boolean validateMerchantCreatePolicy(MerchantDTO merchantDTO) {

		boolean hasError = false;

			 try {
					MerchantKey merchantKey = new MerchantKey();

					if(merchantDTO.getMerchantId() != null && !"".equals(merchantDTO.getMerchantId())) {
						if (merchantDTO.getMerchantId().length() > 9) {
							return true;
						}

						merchantKey.setMerchantId(merchantDTO.getMerchantId());
					}

					if (merchantDTO.getMerchantName() != null && !"".equals(merchantDTO.getMerchantName())){
						if (merchantDTO.getMerchantName().length() > 100) {
							return true;
						}
					}




					LocalMerchantRepositoryAdapter merchantRepositoryAdapter = LocalMerchantRepositoryAdapter.getInstance();
					Merchant merchant = merchantRepositoryAdapter.read(merchantKey);

					if (ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) != null
							&& (Boolean) ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) == true) {

						if (merchant != null) {

							if(merchant.getIsDeleted() != null && !merchant.getIsDeleted()) {
								return true;
							}

						}
					}

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

		return hasError;
	}

	private boolean validateMerchantMappingCreatePolicy(MerchantMappingDTO merchantMappingDTO) {


		boolean hasError = false;

			try {

			LocalMerchantRepositoryAdapter merchantRepositoryAdapter = LocalMerchantRepositoryAdapter.getInstance();

			LocalMerchantMappingRepositoryAdapter merchantMappingRepositoryAdapter = LocalMerchantMappingRepositoryAdapter
					.getInstance();

			if (merchantMappingDTO.getParentMerchantId() != null
					&& !"".equals(merchantMappingDTO.getParentMerchantId())) {
				if (merchantMappingDTO.getParentMerchantId().length() > 9) {
					return true;
				}

				MerchantKey merchantKey = new MerchantKey(merchantMappingDTO.getParentMerchantId());
				Merchant merchant = merchantRepositoryAdapter.read(merchantKey);

				if (merchant == null) {

					return true;

				}

				if (ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) != null
						&& (Boolean) ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) == true) {

					if (merchant != null && merchant.getIsEnabled() == false) {
						return true;
					}

					for (String childMerchantId : merchantMappingDTO.getChildMerchantIdList()) {

						MerchantKey mk = new MerchantKey(childMerchantId);
						Merchant m = merchantRepositoryAdapter.read(mk);

						if (m != null) {
							if (m.getIsDeleted() == true) {
								return true;
							}

							if (m.getIsEnabled() == false) {
								return true;
							}
						}
					}

					MerchantMapping tmpMerchantMapping = new MerchantMapping();
					tmpMerchantMapping.setParentMerchantId(merchantMappingDTO.getParentMerchantId());
					List<MerchantMapping> merchantMappingList = merchantMappingRepositoryAdapter
							.listValidate(tmpMerchantMapping);

					if (merchantMappingList.size() > 0) {
						for (MerchantMapping m : merchantMappingList) {
							if ("pendingAdd".equals(m.getStatus())) {
								return true;
							} else if ("completed".equals(m.getStatus())) {
								return true;
							}
						}
					}
				}

			}

			MerchantMappingKey merchantMappingKey = new MerchantMappingKey();

			if (merchantMappingDTO.getChildMerchantIdList() != null) {
				if (ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) != null
						&& (Boolean) ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) == true) {
					for (String childMerchantId : merchantMappingDTO.getChildMerchantIdList()) {

						MerchantMapping tmpMerchantMapping = new MerchantMapping();
						tmpMerchantMapping.setParentMerchantId(childMerchantId);
						List<MerchantMapping> merchantMappingList = merchantMappingRepositoryAdapter
								.listValidate(tmpMerchantMapping);

						if (merchantMappingList.size() > 0) {
							return true;
						}

						merchantMappingKey.setChildMerchantId(childMerchantId);
						MerchantMapping merchantMapping = merchantMappingRepositoryAdapter.readValidate(merchantMappingKey);

						if (merchantMapping != null) {

							if (merchantMapping.getStatus() != null) {
								if ("pendingAdd".equals(merchantMapping.getStatus())) {
									return true;
								} else if ("completed".equals(merchantMapping.getStatus())) {
									return true;
								}
							}

						}
					}
				}

			}

			if (merchantMappingDTO.getChildMerchantIdList().size() == 0) {
				return true;
			}


		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return hasError;

	}

	private boolean validateMerchantMappingUpdatePoliy(MerchantMappingDTO merchantMappingDTO){
			boolean hasError = false;
		  try {

				LocalMerchantRepositoryAdapter merchantRepositoryAdapter = LocalMerchantRepositoryAdapter.getInstance();

				LocalMerchantMappingRepositoryAdapter merchantMappingRepositoryAdapter = LocalMerchantMappingRepositoryAdapter.getInstance();

				if(merchantMappingDTO.getParentMerchantId() != null && !"".equals(merchantMappingDTO.getParentMerchantId())) {
					if (merchantMappingDTO.getParentMerchantId().length() > 9) {
						return true;
					}

					MerchantKey merchantKey = new MerchantKey(merchantMappingDTO.getParentMerchantId());
					Merchant merchant = merchantRepositoryAdapter.read(merchantKey);

					if (merchant == null) {

						return true;

					}

					if (merchant != null && merchant.getIsEnabled() == false) {
						return true;
					}

					for (String childMerchantId : merchantMappingDTO.getChildMerchantIdList()) {

						MerchantKey mk = new MerchantKey(childMerchantId);
						Merchant m = merchantRepositoryAdapter.read(mk);

						if (m != null) {
							if (m.getIsEnabled() == false) {
								return true;
							}
						}
					}

				}

				if(merchantMappingDTO.getChildMerchantIdList() != null ) {
					if (ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) != null
							&& (Boolean) ThreadAttribute.get(ThreadAttribute.APPROVAL_REQUIRED) == true) {
						for (String childMerchantId : merchantMappingDTO.getChildMerchantIdList()) {

							MerchantMapping tmpMerchantMapping = new MerchantMapping();
							tmpMerchantMapping.setParentMerchantId(childMerchantId);
							List<MerchantMapping> merchantMappingList = merchantMappingRepositoryAdapter.listValidate(tmpMerchantMapping);

							if (merchantMappingList.size() > 0) {
								return true;
							}

//							merchantMappingKey.setChildMerchantId(childMerchantId);
//							MerchantMapping merchantMapping = merchantMappingRepositoryAdapter.read(merchantMappingKey);
//
//							if (merchantMapping != null) {
//
//								if(merchantMapping.getStatus() != null) {
//									if ("pendingAdd".equals(merchantMapping.getStatus())){
//										return true;
//									} else if ("completed".equals(merchantMapping.getStatus())) {
//										return true;
//									}
//								}
//
//							}
						}

						MerchantMapping domain = new MerchantMapping();

						List<String> newList = merchantMappingDTO.getChildMerchantIdList();
						List<String> merchantmappingdtoList = new ArrayList<>();
						if (domain != null) {
							for (com.ofss.digx.cz.bea.domain.merchant.entity.MerchantMapping merchantmapping : merchantMappingRepositoryAdapter
									.listValidate(domain)) {
								merchantmappingdtoList.add(merchantmapping.getMerchantMappingKey().getChildMerchantId());
							}

							List<String> oldChildmerchantIdList = merchantmappingdtoList.stream() // need to delete
									.filter(element -> !newList.contains(element)).collect(Collectors.toList());


							List<String> newChildmerchantIdList = newList.stream() // need to add
									.filter(element -> !merchantmappingdtoList.contains(element))
									.collect(Collectors.toList());
							for (String childMerchantId : newChildmerchantIdList) {
								MerchantMappingKey merchantMappingKey = new MerchantMappingKey();
								merchantMappingKey.setChildMerchantId(childMerchantId);
								MerchantMapping merchantMapping = merchantMappingRepositoryAdapter.readValidate(merchantMappingKey);

								if (merchantMapping != null) {

									if(merchantMapping.getStatus() != null) {
										if ("pendingAdd".equals(merchantMapping.getStatus())){
											return true;
										} else if ("completed".equals(merchantMapping.getStatus())) {
											return true;
										}
									}

								}
							}
						}

					}

				}


			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  return hasError;
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

	//BCOCDC-5595 Jason start
	private boolean isBddMktUser(String username, Set<String> allowedBddMktUserIds) {
		return allowedBddMktUserIds.contains(username);
	}
	//BCOCDC-5595 Jason end

}
