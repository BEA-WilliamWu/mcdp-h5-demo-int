package com.ofss.digx.cz.bea.scheduler.generic;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.dto.accesspoint.AccessPointDTO;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.domain.scheduler.service.BatchAlertGeneric;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;

@DisallowConcurrentExecution
public class BatchExecutionScheduler implements Serializable, Job {

	/**
	 * Create instance of multi-entity logger
	 */
	protected static final MultiEntityLogger formatter = MultiEntityLogger.getUniqueInstance();

	/**
	 * Instance variable which is required to support multi-entity wide logging.
	 */
	protected static transient Logger logger = formatter.getLogger(BatchExecutionScheduler.class.getName());

	@Override
	public void execute(JobExecutionContext paramJobExecutionContext) throws JobExecutionException {
		
			if (logger.isLoggable(Level.SEVERE)) {
				logger.log(Level.SEVERE, formatter.formatMessage("Entered inside the execute method in class :%s",
						this.getClass().getName()));
			}
			SessionContext sessionContext = (SessionContext) paramJobExecutionContext.getJobDetail().getJobDataMap()
					.get("sessionContext");
			AccessPointDTO accessPoint = (AccessPointDTO) paramJobExecutionContext.getJobDetail().getJobDataMap()
					.get("accessPoint");
			com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.ACCESS_POINT,
					accessPoint);
			ThreadAttribute.set(ThreadAttribute.SESSION_CONTEXT, sessionContext);
	
			String entity = "OBDX_BU";
			com.ofss.digx.infra.thread.ThreadAttribute.set(com.ofss.digx.infra.thread.ThreadAttribute.CURRENT_TARGET_UNIT,
					entity);
	
			sessionContext.setTargetUnit(entity);
			sessionContext.setUserId("BatchAlertUser1");
			sessionContext.setBankCode("015");
			sessionContext.setUserLocale("en");
	
			try {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Entering the executor method to trigger the sendBatchAlert method");
	
				BatchAlertGeneric service = new BatchAlertGeneric();
				service.sendBatchAlert(sessionContext);
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Exiting the batch alert execute method ");
	
				/*
				 * com.ofss.digx.cz.bea.app.logger.BeaSystemOut.
				 * println("Entering the executor method to trigger the notifyWMG>>callWMG method"
				 * ); com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("##################################" +
				 * sessionContext.toString()); WMGTTSIBatchService wmg_service = new
				 * WMGTTSIBatchService(); wmg_service.notifyWMG(sessionContext);
				 * com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Exiting the execute method ");
				 */
	
			} catch (Exception e) {
				logger.log(Level.SEVERE,
						"Error occured while executing BatchExecutionScheduler class at : " + new java.util.Date(), e);
			} catch (java.lang.Exception e) {
				logger.log(Level.SEVERE,
						"Lang Error occured while executing BatchExecutionScheduler class at : " + new java.util.Date(), e);
			}
	
			// 851 work is isolated; runs after the existing BCO batch and restores its session context.
            try {
                new com.ofss.digx.cz.bea.domain.service.dispatch.HthContactNotificationService().process(sessionContext);
            } catch (java.lang.Exception e) {
                logger.log(Level.WARNING, "HTH_CONTACT stage=SCHEDULER exception={0}", e.getClass().getSimpleName());
            }
            try {
                new com.ofss.digx.cz.bea.domain.service.dispatch.HthUserAccessNotificationService().process(sessionContext);
            } catch (java.lang.Exception e) {
                logger.log(Level.WARNING, "HTH_ACCESS stage=SCHEDULER exception={0}", e.getClass().getSimpleName());
            }

			if (logger.isLoggable(Level.SEVERE)) {
				logger.log(Level.SEVERE,
						formatter.formatMessage("Exited the execute method in class :%s", this.getClass().getName()));
			}
		}
	
}
