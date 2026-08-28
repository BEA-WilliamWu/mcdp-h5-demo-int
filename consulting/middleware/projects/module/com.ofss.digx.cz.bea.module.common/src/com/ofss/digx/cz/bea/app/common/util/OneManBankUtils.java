/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.app.common.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.ofss.digx.app.account.adapter.exchangerate.IAccountExchangeAdapter;
import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupResponseDTO;
import com.ofss.digx.app.approval.dto.usergroup.UserGroupUserDTO;
import com.ofss.digx.app.common.currency.dto.exchangerate.ExchangeRateDetailDTO;
import com.ofss.digx.app.common.currency.dto.exchangerate.ExchangeRateResponseDTO;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
import com.ofss.digx.common.constants.CommonConstants;
import com.ofss.digx.cz.bea.app.approval.dto.signergroup.SignerRuleResponseDTO;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.common.dto.omb.OneManBankDTO;
import com.ofss.digx.cz.bea.approval.dto.rule.CZRuleDetailsDTO;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.framework.task.evaluator.TaskEvaluatorConfigurator;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.thread.ThreadAttribute;

/**
 * 
 * @author ppjadhav
 *
 */
public class OneManBankUtils {

	private static final String TRFTOA_FT = "TRFTOA_FT";
	private static final String FTOA_NF = "FTOA_NF";
	private static final String AH_MN = "AH_MN";
	private static final String AL_MN = "AL_MN";
	private static final String TRFTOTA_FT = "TRFTOTA_FT";
	private static final String APC = "APC";
	

	private static final String AMOUNT_CRITERIA = "RULE_CRITERIA_011";

	/**
	 * String constant for Standard.
	 */
	private static final String STANDARD = "STANDARD";

	private static HashMap<String, List<String>> map = null;

	private static IHostUserDetailsInvocationAdapter hostuserDetailsAdapter = null;

	private static void initializeMap() {
		if (map == null) {
			map = new HashMap<String, List<String>>();

			String[] ftToOwnArray = { "TRFTOA_FT", "PC_F_CRNSFT", "FTOA_TD", "PC_F_CRNSFT_WW", "PC_F_CRNSFT_CNY_WW", "PC_F_CRNSFT_FCY_WW", "TD_F_OTD", "TD_F_RTD",
					"PC_F_CRNSFT_SI", "TD_F_FCY_OTD", "TD_F_FCY_RTD","LMI_F_OCLM","LMI_F_OULM","LMI_F_ODLM", "FT_F_PFR"};

			String[] ftToNonFinArray = { "FTOA_NF", "TD_NT", "CH_NF", "EA_NT", "CH_N_CBR", "CH_N_RADHSTMT",
					"CH_N_DADHSTMT", "CH_N_LADHSTMT", "CH_N_EADV", "CH_N_ESTM", "CH_I_CSE", "CH_N_CIN", "TD_N_ATD",
					"CH_N_ESTMEADV_DW", "CH_N_FCY_CBR", "CH_N_HKD_CBR" };

			String[] adminHighArray = { "AH_MN", "FL_C_MIHO", "PC_N_UDOP_VT", "PC_N_CITNP", "PC_N_CIP","PC_N_IDP", "PC_N_UITNP",
					"PC_N_DOP_VT", "PC_N_UIP", "PC_N_CBP", "PC_N_UBP", "TPS_MT", "EA_MN", "EADESTMTC", "OAC_REVOKE",
					"OAC_GRANT", "OAC_REFRESH", "EADESTMTU", "OAC_RETRIEVE", "EADESTMTUR", "OPN_MT", "EADESTMTL",
					"MT_M_COP", "MT_M_UOP", "MT_M_ROP", "AH_MT", "LAT", "UAT", "UM_MT", "UAT_N_VA", "UM_N_ULS_UL",
					"UM_N_ULS", "UM_N_USD", "UAT_N_UA", "UAT_N_DA", "UAT_N_HUA_NEW", "UAT_N_HUA_EDT",
					"UAT_N_HUA_DEL", "LAT_N_UA", "LAT_N_CA", "UAT_N_CA", "LAT_N_DA",
					"MT_N_RUS", "MT_N_UUS", "MT_N_CUS", "FU_N_UM", "FU_N_CUM", "FU_N_UUM", "FU_N_DUM" ,"PP_N_HTH_CERT_UPL"};

			// check again
			String[] adminLowArray = { "AL_MN", "PC_N_DDP_VT", "FL_C_MDHO", "PC_N_DIP", "PC_N_DITNP", "PC_N_DBP", "FPS",
					"MT_N_AFP", "MT_N_AFP_FPS", "MT_N_TFP", "MT_N_EFP", "MT_N_VFP", "MT_N_SFP", "MT_N_RFP", "AL_MT",
					"UM_N_ULS_L" };

			// check again
			String[] ftToOtherArray = { "TRFTOTA_FT", "EDDA", "PC_F_CRNBCT", "PC_F_GCRNBCT", "PC_F_CRNIFT",
					"PC_F_GCRNIFT", "PC_F_GCRNDFT", "PC_F_GCRNINFT", "PC_F_CRNINFT", "PC_F_CRNDFT",
					"PC_F_GCRNIFT_FCY_WW", "PC_F_CRNBCT_WW", "PC_F_GCRNBCT_WW", "PC_F_CRNDFT_WW", "PC_F_GCRNDFT_WW",
					"PC_F_CRNDFT_FPS_WW", "PC_F_GCRNDFT_FPS_WW", "PC_F_CRNIFT_WW", "PC_F_GCRNIFT_WW",
					"PC_F_CRNIFT_CNY_WW", "PC_F_GCRNIFT_CNY_WW", "PC_F_CRNIFT_FCY_WW", "PC_F_CRNINFT_WW",
					"PC_F_GCRNINFT_WW", "PC_F_CRNINFT_CNY_WW", "PC_F_GCRNINFT_CNY_WW", "PC_F_AED", "PC_F_SED",
					"PC_F_LED", "PC_F_CRNDFT_FPS", "PC_F_GCRNDFT_FPS", "PC_F_CRNIFT_SI", "PC_F_GCRNIFT_SI",
					"PC_F_CRNBCT_SI", "PC_F_GCRNBCT_SI", "PC_F_CRNDFT_SI", "PC_F_GCRNDFT_SI", "PC_F_CRNDFT_FPS_SI",
					"PC_F_GCRNDFT_FPS_SI", "PC_F_CRNINFT_SI", "PC_F_GCRNINFT_SI", "PC_F_AED_CONFIRM", "PC_F_AED_REJECT",
					"PC_F_AED_SUSPEND", "PC_F_AED_RESUME", "PC_F_AED_TERMINATE","EB_F_BP","EB_F_BP_HR","EB_F_BP_LR","EB_F_BP_SI","EB_F_BP_D","LMI",
					"LMI_F_CLM","LMI_F_ACLM","LMI_F_CCLM","LMI_F_LCLM","LMI_F_TCLM",
					"LMI_F_ULM","LMI_F_AULM","LMI_F_CULM","LMI_F_LULM","LMI_F_TULM",
					"LMI_F_DLM","LMI_F_ADLM","LMI_F_CDLM","LMI_F_LDLM","LMI_F_TDLM"};
			
			
			String[] APCArray = { "BU_AUTOPAY_CREATE", "BU_PAYROLL_CREATE", "BU_COLLECTION_CREATE", "BU_SI_LIST",
					"BU_SI_DELETE", "BU_FAV_CREATE", "BU_FAV_LIST", "FU_F_APC", "BU_AUTOPAY_HKD", "BU_AUTOPAY_CNY",
					"BU_PAYROLL_HKD", "BU_PAYROLL_CNY", "BU_COLLECTION_HKD", "BU_COLLECTION_CNY" };

			
			
			
			List<String> ftToOwnList = Arrays.asList(ftToOwnArray);
			List<String> ftToNonFinList = Arrays.asList(ftToNonFinArray);
			List<String> adminHighList = Arrays.asList(adminHighArray);
			List<String> adminLowList = Arrays.asList(adminLowArray);
			List<String> ftToOtherList = Arrays.asList(ftToOtherArray);
			List<String> apcArrayList = Arrays.asList(APCArray);
			
			map.put(TRFTOA_FT, ftToOwnList);
			map.put(FTOA_NF, ftToNonFinList);
			map.put(AH_MN, adminHighList);
			map.put(AL_MN, adminLowList);
			map.put(TRFTOTA_FT, ftToOtherList);
			map.put(APC, apcArrayList);
			
		}

		if (hostuserDetailsAdapter == null) {
			IAdapterFactory hostUserDetailsAdapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
			hostuserDetailsAdapter = (IHostUserDetailsInvocationAdapter) hostUserDetailsAdapterFactory.getAdapter(
					com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
		}
	}

	/**
	 * This method returns true/false based on one man bank support
	 * 
	 * @param partyId
	 * @param task
	 * @return
	 * @throws Exception
	 */
	public static boolean isOneManBankEnabled(String partyId, String task) throws Exception {
		OneManBankDTO oneManBankDTO = getOneManBankDetails(partyId, task, null, null);
		return oneManBankDTO != null ? !oneManBankDTO.isApprovalRequired() : false;
	}

	/**
	 * This method returns true/false based on one man bank support
	 * 
	 * @param partyId
	 * @param task
	 * @param amount
	 * @return
	 * @throws Exception
	 */
	public static boolean isOneManBankEnabled(String partyId, String task, BigDecimal amount, String currency)
			throws Exception {
		OneManBankDTO oneManBankDTO = getOneManBankDetails(partyId, task, amount, currency);
		return oneManBankDTO != null ? !oneManBankDTO.isApprovalRequired() : false;
	}

	/**
	 * This method returns true/false based on one man bank support
	 * 
	 * @param partyId
	 * @param task
	 * @param amount
	 * @return
	 * @throws Exception
	 */
	public static boolean isOneManBankEnabledHndException(String partyId, String task, BigDecimal amount,
			String currency) {
		boolean res = false;
		String isOmbEnb = null;
		try {
			
			isOmbEnb = CZCommonValidation.getValueFromCZConfig("IS_OMB_ENABLED","N");
			
			if(!"Y".equalsIgnoreCase(isOmbEnb)){
				return res;
			}
			
			System.out.println("isOneManBankEnabledHndException : partyId=" + partyId + " task=" + task + " amount="
					+ amount + " currency=" + currency);
			
			if(amount==null){
				res = isOneManBankEnabled(partyId, task);
			}else{
				res = isOneManBankEnabled(partyId, task, amount, currency);
			}
			
			
		} catch (java.lang.Exception e) {
			e.printStackTrace();
		}finally{
			System.out.println("isOneManBankEnabledHndException : res=" + res+" isOmbEnb="+isOmbEnb);
		}		
		return res;
	}

	/**
	 * This method checks if transaction qualifies OMB or not
	 * 
	 * @param partyId
	 * @param username
	 * @param task
	 * @param amount
	 * @return
	 * @throws Exception
	 */
	public static boolean checkTransactionQualifiesOMB(String partyId, String username, String task, BigDecimal amount,
			String currency) throws Exception {
		OneManBankDTO oneManBankDTO = null;
		String signerGroups = null;
		if (partyId != null && username != null && task != null) {
			if (amount != null && currency != null) {
				oneManBankDTO = getOneManBankDetails(partyId, task, amount, currency);

				if (oneManBankDTO != null && !oneManBankDTO.isApprovalRequired()) {
					signerGroups = readSignerGroupsByRule(oneManBankDTO.getRuleId());
					if (signerGroups != null) {
						System.out.println("OMB Return 1");
						return checkUserExistsInSignerGroup(signerGroups, username);
					} else {
						System.out.println("OMB Return 2");
						return false;
					}
				} else {
					System.out.println("OMB Return 3");
					return true;
				}
			} else {
				oneManBankDTO = getOneManBankDetails(partyId, task, null, null);

				if (oneManBankDTO != null && !oneManBankDTO.isApprovalRequired()) {
					signerGroups = readSignerGroupsByRule(oneManBankDTO.getRuleId());
					if (signerGroups != null) {
						System.out.println("OMB Return 4");
						return checkUserExistsInSignerGroup(signerGroups, username);
					} else {
						System.out.println("OMB Return 5");
						return false;
					}
				} else {
					System.out.println("OMB Return 6");
					return true;
				}
			}
		}

		System.out.println("OMB Return 7");
		return true;
	}

	/**
	 * This method evaluates task, checks for aspect support and returns one man
	 * bank details based on party and task
	 * 
	 * @param partyId
	 * @param task
	 * @return OneManBankDTO
	 * @throws Exception
	 */
	public static OneManBankDTO getOneManBankDetails(String partyId, String task, BigDecimal amount, String currency)
			throws Exception {
		String transactionType = null;

		if (partyId != null && task != null) {

			System.out.println("OMB:" + task);

			/**
			 * Check task aspect support
			 */
			if (TaskUtils.checkAspectSupportedForTask(task, TaskAspect.APPROVALS)) {
				/*
				 * Fetch transaction type
				 */
				transactionType = getTransactionType(task);
				System.out.println("OMB:transactionType" + transactionType);
				if (transactionType != null) {
					return (amount != null && currency != null)
							? isApprovalRequired(partyId, transactionType, amount, currency)
							: isApprovalRequired(partyId, transactionType, null, null);
				}
			}
		}
		return null;
	}

	/**
	 * This method returns evaluated task code based on approval aspect
	 * 
	 * @param taskId
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static String getEvaluatedTask(String taskId) throws Exception {
		HashMap<String, Object> serviceInputs = (HashMap<String, Object>) ThreadAttribute
				.get(ThreadAttribute.SERVICE_INPUTS);
		List<Object> serviceParams = null;
		if (serviceInputs != null) {
			serviceParams = (List<Object>) serviceInputs.get(CommonConstants.PARAMETERS);
		}
		String evaluatedTaskId = TaskEvaluatorConfigurator.getInstance().getEvaluatorFactory(taskId)
				.getEvaluator(TaskAspect.APPROVALS).evaluateTaskCode(taskId, serviceParams);
		return evaluatedTaskId;
	}

	/**
	 * This method searches task code in map and returns key that is transaction
	 * type of rule
	 * 
	 * @param task contains task code of transaction
	 * @return
	 */
	public static String getTransactionType(String task) {

		initializeMap();
		for (Entry<String, List<String>> entry : map.entrySet()) {
			for (String taskValue : entry.getValue()) {
				if (taskValue.equals(task)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	/**
	 * This method reads and returns DTO details fetched from Rules table
	 * 
	 * @param partyId
	 * @param transactionType
	 * @return OneManBankDTO
	 * @throws Exception
	 */
	private static OneManBankDTO isApprovalRequired(String partyId, String transactionType, BigDecimal amount,
			String currency) throws Exception {

		if (partyId != null && transactionType != null && (transactionType.equals(FTOA_NF)
				|| transactionType.equals(AH_MN) || transactionType.equals(AL_MN))) {
			System.out.println("OMB :: No Amount Rule~" + partyId + "~" + transactionType);
			return evaluateNoAmountRules(partyId, transactionType);
		}

		if (partyId != null && transactionType != null && amount != null && currency != null
				&& (transactionType.equals(TRFTOA_FT) || transactionType.equals(TRFTOTA_FT)|| transactionType.equals(APC))) {
			System.out.println("OMB :: Amount Rule~" + partyId + "~" + transactionType + "~" + amount + "~" + currency);
			return evaluateAmountRules(partyId, transactionType, calculateAmount(amount, currency));
		}

		return null;
	}

	/**
	 * Evaluates and returns no amount rules
	 * 
	 * @param partyId
	 * @param transactionType
	 * @return
	 * @throws Exception
	 */
	private static OneManBankDTO evaluateNoAmountRules(String partyId, String transactionType) throws Exception {
		String approvalRequired = null;
		OneManBankDTO oneManBankDTO = null;

		List<CZRuleDetailsDTO> ruleDetails = hostuserDetailsAdapter.listOMBRules(partyId, transactionType);

		if (ruleDetails != null && ruleDetails.size() > 0) {
			approvalRequired = ruleDetails.get(0).getApprovalRequired();
			oneManBankDTO = new OneManBankDTO();
			oneManBankDTO.setRuleId(ruleDetails.get(0).getRule_id());
			oneManBankDTO.setApprovalRequired(approvalRequired != null && approvalRequired.equals("Y"));
		}
		
		/**
		 * This is part of approval matrix CR, OBDX checks if rule is present in both maker/checker and OMB, then system
		 * will mark current transaction as maker/checker transaction and will set OMB enabled as false.
		 */
		if (hostuserDetailsAdapter.getCustomConfigUtilValue("APPROVAL_MATRIX_FLAG", "N").equals("Y")
				&& ruleDetails != null && ruleDetails.size() > 0 && oneManBankDTO != null) {

			System.out.println("Size of ruleDetails=" + ruleDetails.size() + "\n oneManBankDTO.isApprovalRequired() value=" + oneManBankDTO.isApprovalRequired());
			/**
			 * Checks if evaluated rule is OMB, then traverse through rules list to check if maker/checker rule exists
			 */
			if (!oneManBankDTO.isApprovalRequired()) {
				for (CZRuleDetailsDTO rule : ruleDetails) {
					System.out.println("Rule ID=" + rule.getRule_id() + "\n Approval Req Flag=" + rule.getApprovalRequired());
					if (rule.getApprovalRequired().equalsIgnoreCase("Y")) {
						/**
						 * Sets rule as maker/checker
						 */
						oneManBankDTO.setApprovalRequired(true);
						break;
					}
				}
			}
		}

		return oneManBankDTO;

	}

	/**
	 * Evaluates and returns rules having amount
	 * 
	 * @param partyId
	 * @param transactionType
	 * @param amount
	 * @param currency
	 * @return
	 * @throws Exception
	 */
	private static OneManBankDTO evaluateAmountRules(String partyId, String transactionType, BigDecimal amount)
			throws Exception {

		OneManBankDTO oneManBankDTO = null;
		List<String> ruleIdList = null;

		System.out.println("OneManBankUtils evaluateAmountRules :: partyId : "+partyId+", transactionType : "+transactionType+", amount : "+amount);
		List<CZRuleDetailsDTO> ruleDetails = hostuserDetailsAdapter.listAllPartyRules(partyId);

		if (ruleDetails != null && ruleDetails.size() > 0) {
			ruleIdList = new ArrayList<String>();

			for (CZRuleDetailsDTO rule : ruleDetails) {
				if (rule.getConstraint_value1().equals(transactionType)) {
					System.out.println("OMB : rule id list=" + rule.getRule_id() + "~");
					ruleIdList.add(rule.getRule_id());
				}
			}

			outerLoop: // label for outer loop
			for (CZRuleDetailsDTO rule : ruleDetails) {
				for (String ruleId : ruleIdList) {
					if (rule.getRule_id().equals(ruleId)) {
						if (rule.getRule_criteria_id().equals(AMOUNT_CRITERIA)) {
							if (amount.compareTo(new BigDecimal(rule.getConstraint_value1())) >= 0
									&& amount.compareTo(new BigDecimal(rule.getConstraint_value2())) <= 0) {
								System.out.println("OMB Matched");
								oneManBankDTO = new OneManBankDTO();
								oneManBankDTO.setRuleId(rule.getRule_id());
								oneManBankDTO.setApprovalRequired(
										rule.getApprovalRequired() != null && rule.getApprovalRequired().equals("Y"));
								
								/**
								 * This is part of approval matrix CR, OBDX checks if rule is present in both maker/checker and OMB, then system
								 * will mark current transaction as maker/checker transaction and will set OMB enabled as false.
								 * 
								 * ApprovalRequired = Y means maker/checker rule
								 */
								if (hostuserDetailsAdapter.getCustomConfigUtilValue("APPROVAL_MATRIX_FLAG", "N")
										.equals("Y") && rule.getApprovalRequired().equalsIgnoreCase("Y")) {
									System.out.println("Breaking the loop got maker/checker");
									break outerLoop;
								}
							}
				
						}
					}
				}
			}
		}
		return oneManBankDTO;

	}

	/**
	 * This method reads signer group ids by ruleID
	 * 
	 * @param ruleId
	 * @return
	 * @throws Exception
	 */
	private static String readSignerGroupsByRule(String ruleId) throws Exception {

		SignerRuleResponseDTO signerRuleResponseDTO = hostuserDetailsAdapter.readSignerGroupByRule(ruleId);
		
		if(signerRuleResponseDTO != null && signerRuleResponseDTO.getSignerRuleRequestDTO() != null) {
			return signerRuleResponseDTO.getSignerRuleRequestDTO().getSignerGroups();
		}
		
		return null;
	}

	/**
	 * This method checks if username is present in signer group or not
	 * 
	 * @param signerGroups
	 * @param username
	 * @return
	 * @throws Exception
	 */
	private static boolean checkUserExistsInSignerGroup(String signerGroups, String username) throws Exception {

		String[] signerGroupsList = null;
		UserGroupResponseDTO userGroupResponseDTO = null;
		if (signerGroups != null && username != null) {
			signerGroupsList = signerGroups.split("~");

			System.out.println("OMB : Signergroups =" + signerGroups + "--- username=" + username);
			for (String groupId : signerGroupsList) {
				userGroupResponseDTO = hostuserDetailsAdapter.readUserGroup(groupId);
				for (UserGroupUserDTO userGroupUserDTO : userGroupResponseDTO.getUserGroup().getUsers()) {
					System.out.println("OMB Username = " + userGroupUserDTO.getUserId());
					if (userGroupUserDTO.getUserId().equals(username)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * This method calculates and return amount based on currency
	 * 
	 * @param amount
	 * @param currency
	 * @return
	 * @throws Exception
	 */
	private static BigDecimal calculateAmount(BigDecimal amount, String currency) throws Exception {
		BigDecimal exchangeRate = new BigDecimal(1);
		IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
				.getAdapterFactory(CommonAdapterFactoryConstants.ACCOUNT_EXCHANGE_ADAPTER_FACTORY);
		IAccountExchangeAdapter adapter = (IAccountExchangeAdapter) adapterFactory
				.getAdapter(CommonAdapterConstants.ACCOUNT_EXCHANGE_ADAPTER);
		SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		ExchangeRateResponseDTO exchangeRateResponseDTO = adapter.fetchExchangeRateForBaseCurrency(sessionContext,
				currency);
		System.out.println("OMB - Currency==" + currency);
		for (ExchangeRateDetailDTO exchangeRateDetailDTO : exchangeRateResponseDTO.getExchangeRateDetails()) {
			if (exchangeRateDetailDTO.getExchangeRateDetailKey().getRateType().equals(STANDARD)) {
				exchangeRate = exchangeRateDetailDTO.getMidRate();
				break;
			}
		}
		System.out.println("OMB : Exchange rate =" + exchangeRate);
		return amount.multiply(exchangeRate);
	}
}
