package com.ofss.digx.cz.bea.app.common.service.transactionlist;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.digx.cz.bea.app.common.HighRiskTransactionDTO;
import com.ofss.digx.cz.bea.app.common.HighRiskTransactionResponse;
import com.ofss.digx.cz.bea.app.common.helper.HthAuditTaskVisibility;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.locale.LocaleUtils;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;

public class TransactionList extends AbstractApplication {

	/**
	 * Holds the fully qualified class name.
	 */
	private static final String THIS_COMPONENT_NAME = TransactionList.class.getName();

	/**
	 * Holds the instance of formatter.
	 */
	private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

	/**
	 * Logger instance for Demand Deposit service.
	 */
	private static final Logger logger = FORMATTER.getLogger(THIS_COMPONENT_NAME);

	/**
	 * Default constructor of demand deposit service, instantiates the executor of
	 * extensions.
	 */
	public TransactionList() {
	}
	
	private static final String BUNDLE_NAME = "resources.nls.HighRiskTask";
	private static final String USER_LOCALE = "USER_LOCALE";
	
	public HighRiskTransactionResponse fetchHighRiskTransactionList(SessionContext sessionContext) throws Exception, UnsupportedEncodingException {
		
//		super.checkAccessPolicy("com.ofss.digx.cz.bea.app.approval.service.transaction.Transaction.performAction",sessionContext, multiApprovalTransactionListDTO);
		TransactionStatus transactionStatus = fetchTransactionStatus();
		Interaction.begin(sessionContext);
		
		HighRiskTransactionResponse responseDTO = new HighRiskTransactionResponse();
		
		try {
			Locale locale = LocaleUtils.getUserLocale();
			
			if (locale == null && ThreadAttribute.get(USER_LOCALE) != null)
				locale = Locale.forLanguageTag(ThreadAttribute.get(USER_LOCALE).toString());
			
			ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME,(locale != null) ? locale : LocaleUtils.getDefaultLocale());
			
			Enumeration<String> taskKeyList = (Enumeration<String>) bundle.getKeys();
			
			List<HighRiskTransactionDTO> highRiskTransactionDTOList = new ArrayList<>();
			
			HthAuditTaskVisibility hthVisibility = new HthAuditTaskVisibility();
			while (taskKeyList.hasMoreElements()) {
				
				HighRiskTransactionDTO highRiskTransactionDTO = new HighRiskTransactionDTO();
				String taskKey=taskKeyList.nextElement();
				if (!hthVisibility.isVisible(taskKey)) {
					continue;
				}
				
				BeaSystemOut.println("\n \t fetchHighRiskTransactionList :: taskKey : " + taskKey);
				
				highRiskTransactionDTO.setTaskCode(taskKey);
				
				String value = bundle.getString(taskKey);
				
				BeaSystemOut.println("\n \t fetchHighRiskTransactionList :: value : " + value);
				
				String convertedString = new String(value.getBytes("ISO-8859-1"), "UTF-8");
				
				BeaSystemOut.println("\n \t fetchHighRiskTransactionList :: convertedString : " + convertedString);
				
				highRiskTransactionDTO.setTaskName(convertedString);
				
				highRiskTransactionDTOList.add(highRiskTransactionDTO);
				
			}
			
			if(highRiskTransactionDTOList != null) {
				BeaSystemOut.println(" Loop START \n \n highRiskTransactionDTOList: { \n");
				
				for(HighRiskTransactionDTO highRiskTransactionDTO: highRiskTransactionDTOList) {
					BeaSystemOut.println(highRiskTransactionDTO.toString());
				}
				
				BeaSystemOut.println(" \n \n } \n \n Loop END");
			}
			
			responseDTO.setHighRiskTransactionDTOList(highRiskTransactionDTOList);
			
			
		} catch (UnsupportedEncodingException e) {
			
			logger.log(Level.SEVERE, FORMATTER.formatMessage("Exception encountered while invoking read service in %s",
					THIS_COMPONENT_NAME), e);
			
			fillTransactionStatus(transactionStatus, e);
			
		} catch (RuntimeException rte) {
			logger.log(Level.SEVERE, FORMATTER.formatMessage(
					"com.ofss.digx.infra.exceptions.Exception from fetchHighRiskTransactionList for DTO '%s' in class '%s'",
					responseDTO, THIS_COMPONENT_NAME), rte);
			fillTransactionStatus(fetchTransactionStatus(), rte);
		} finally {
			Interaction.close();
		}
		
//		super.checkResponsePolicy(sessionContext, responseDTO);
		
		return responseDTO;
		
	}
	
	
}
