package com.ofss.digx.cz.bea.app.audit.service.ext;

import com.ofss.digx.cz.bea.common.audit.HthOnboardingAudit;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import com.ofss.digx.app.audit.dto.AuditListResponseDTO;
import com.ofss.digx.app.audit.dto.AuditResponseDTO;
import com.ofss.digx.app.audit.dto.SearchDTO;
import com.ofss.digx.app.audit.service.ext.VoidAuditExt;
import com.ofss.digx.app.common.resource.task.assembler.TaskAssembler;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.common.constants.CommonConstants;
import com.ofss.digx.cz.bea.app.common.resource.task.enricher.TaskEnricher;
import com.ofss.digx.cz.bea.extxface.fmo.adapter.IFMOHelperCallAdapter;
import com.ofss.digx.domain.common.resource.entity.task.Task;
import com.ofss.digx.domain.common.resource.entity.task.TaskKey;
import com.ofss.digx.domain.common.resource.entity.task.repository.adapter.LocalTaskRepositoryAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.framework.domain.enricher.AbstractEnricher;
import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;
import com.ofss.fc.infra.thread.ThreadAttribute;


public class CZVoidAuditExt extends VoidAuditExt {

	private static final String THIS_COMPONENT_NAME = CZVoidAuditExt.class.getName();

	private static MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

	private static transient Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

	public static final Preferences preferences = ConfigurationFactory.getInstance()
			.getConfigurations(CommonConstants.DAY_ONE_CONFIG);

	@Override
	public void postRead(SessionContext sessionContext, AuditDTO auditDto, AuditResponseDTO auditResponseDTO)
			throws Exception {

		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("The task ID calling the Audit Enquiry is : " + taskId);
		SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
				"callFMOHelper", DeterminantType.Enterprise);
		if (taskId == null) {
			taskId = "AUDIT_ENQUIRY";
		}
		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
			adapter.callFMOHelper(session, taskId, auditResponseDTO, true);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Audit Enquiry FMO called for corporate user");
		}

        if (auditResponseDTO != null && HthOnboardingAudit.applies(auditResponseDTO.getAuditDTO())) {
            HthOnboardingAudit.project(auditResponseDTO.getAuditDTO());
        }

	}
	
	@Override
	public void postSearch(SessionContext sessionContext, SearchDTO searchDto, AuditListResponseDTO auditResponseDTO)
			throws Exception {
		TaskAssembler assembler = new TaskAssembler();
		
		if(auditResponseDTO!=null && auditResponseDTO.getAuditList()!=null && auditResponseDTO.getAuditList().size()>0){
			List<AuditDTO> filteredList = auditResponseDTO.getAuditList().stream().filter(obj -> !isFilterOut(obj)).collect(Collectors.toList());
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt postSearch record count pre filter="+auditResponseDTO.getAuditList().size()+" post filter="+filteredList.size());
			auditResponseDTO.setAuditList(filteredList);
		}
		
		
		for (AuditDTO auditDTO : auditResponseDTO.getAuditList()) {
			TaskKey key = new TaskKey();
			key.setId(auditDTO.getTaskCode());
			com.ofss.digx.domain.common.resource.entity.task.Task taskDomain = new com.ofss.digx.domain.common.resource.entity.task.Task();
			Task taskDetails=LocalTaskRepositoryAdapter.getInstance().read(key);
			if (taskDetails == null) continue; // Retain the stored activity for retired/missing tasks.
			TaskDTO task = assembler.fromDomainObject(taskDetails);
			AbstractEnricher<TaskDTO> taskEnricher = TaskEnricher.getInstance(task);
			task = taskEnricher.enrich(sessionContext);
			if (task != null && task.getName() != null) {
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt postSearch Task name in enricher is" + task.getName());
				auditDTO.setActivity(task.getName());

			}

		}
		
		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("The task ID calling the Audit Enquiry is : " + taskId);
		SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
				"callFMOHelper", DeterminantType.Enterprise);
		if (taskId == null) {
			taskId = "AUDIT_ENQUIRY";
		}
		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
			adapter.callFMOHelper(session, taskId, auditResponseDTO, true);
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Audit Enquiry FMO called for corporate user");
		}
        for (AuditDTO audit : auditResponseDTO.getAuditList()) {
            if (HthOnboardingAudit.applies(audit)) HthOnboardingAudit.project(audit);
        }

	}

	public boolean isFilterOut(AuditDTO input){
		
		boolean res = false;
		
		if("VALCALL_NO_AUDIT".equalsIgnoreCase(input.getTaskCode())){
			res = true;
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt isFilterOut taskCode="+input.getTaskCode());
		}
		else if(input.getStatus()==com.ofss.digx.enumeration.audit.Status.CHALLENGED_2FA)
		{
			res = true;
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt isFilterOut taskCode="+input.getTaskCode()+" status="+input.getStatus());
		}
		else if(input.getTaskCode()!=null && input.getTaskCode().equalsIgnoreCase("PA_APT"))
		{
			res = true;
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt isFilterOut taskCode="+input.getTaskCode()+" status="+input.getStatus());
		}else if (input.getResolvedRequestUrl()!=null && input.getResolvedRequestUrl().contains("requestModification")) {
			
			res = true;
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("CZVoidAuditExt isFilterOut send to modifiy audit log");
		}
		
		return res;
	}
	
}
