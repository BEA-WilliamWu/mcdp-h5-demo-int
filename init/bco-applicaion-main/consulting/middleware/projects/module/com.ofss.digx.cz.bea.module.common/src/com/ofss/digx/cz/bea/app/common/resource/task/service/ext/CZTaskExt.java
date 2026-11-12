package com.ofss.digx.cz.bea.app.common.resource.task.service.ext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ofss.digx.app.common.resource.task.assembler.TaskAssembler;
import com.ofss.digx.app.common.resource.task.service.Task;
import com.ofss.digx.app.common.resource.task.service.ext.ITaskExt;
import com.ofss.digx.app.common.task.dto.TaskAspectDTO;
import com.ofss.digx.app.common.task.dto.TaskAspectRequestDTO;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.app.common.task.dto.TaskListResponseDTO;
import com.ofss.digx.app.common.task.dto.TaskRequestDTO;
import com.ofss.digx.app.common.task.dto.TaskResponseDTO;
import com.ofss.digx.cz.bea.app.common.resource.task.enricher.TaskEnricher;
import com.ofss.digx.cz.bea.app.common.helper.HthAuditTaskVisibility;
import com.ofss.digx.enumeration.task.TaskAspect;
import com.ofss.digx.enumeration.task.ViewType;
import com.ofss.digx.cz.bea.app.common.util.OneManBankUtils;
import com.ofss.digx.datatype.CurrencyAmount;
import com.ofss.digx.domain.common.resource.entity.task.TaskKey;
import com.ofss.digx.framework.domain.enricher.AbstractEnricher;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.thread.ThreadAttribute;
import com.ofss.fc.service.response.TransactionStatus;

/**
 * Represents extension executor as hook point for {@link Task}.</br>
 * Extensions can be called before/after executing business logic of service. These methods are available in class with
 * 'pre and post' as prefix of the actual method name. The implementation of these methods depend upon the actual
 * business requirement under different cases.
 */
public class CZTaskExt implements ITaskExt {

	/**
	 * Processes before the execution of actual business logic for listing task details. Executes the pre extension
	 * logic before the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param request
	 *            {@link TaskRequestDTO} containing details for listing task.
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void preList(SessionContext sessionContext, TaskRequestDTO request) throws Exception {
		// Empty Implementation
	}

	/**
	 * Processes after the execution of actual business logic for listing task details. Executes the post extension
	 * logic after the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param request
	 *            {@link TaskRequestDTO} containing details for listing task.
	 * @param response
	 *            {@link TaskListResponseDTO} containing task details
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void postList(SessionContext sessionContext, TaskRequestDTO request, TaskListResponseDTO response)
			throws Exception {
		// Only the audit dropdown (aspects=audit&view=list); access setup is unchanged.
		boolean auditList = request != null && request.getView() == ViewType.LIST
				&& request.getTask() != null && request.getTask().getAspects() != null
				&& request.getTask().isAspectEnabled(TaskAspect.AUDIT);
		HthAuditTaskVisibility hthVisibility = new HthAuditTaskVisibility();
		List<TaskDTO> taskList = new ArrayList<TaskDTO>();
		for (int i=0;i<response.getTaskList().size();i++) {
		if (auditList && !hthVisibility.isVisible(response.getTaskList().get(i).getId())) {
			continue;
		}
		AbstractEnricher<TaskDTO> taskEnricher = new TaskEnricher(response.getTaskList().get(i));
		taskList.add(taskEnricher.enrich(sessionContext));
		
		}
		response.setTaskList(taskList);
	}

	/**
	 * Processes before the execution of actual business logic for reading task details. Executes the pre extension
	 * logic before the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param taskId
	 *            Unique Identifier for task.
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void preRead(SessionContext sessionContext, String taskId) throws Exception {
		// Empty Implementation
	}

	/**
	 * Processes after the execution of actual business logic for reading task details. Executes the post extension
	 * logic after the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param taskId
	 *            Unique Identifier for task.
	 * @param response
	 *            {@link TaskResponseDTO} containing task details
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void postRead(SessionContext sessionContext, String taskId, TaskResponseDTO response) throws Exception {
		// Empty Implementation
		
		AbstractEnricher<TaskDTO> taskEnricher = new TaskEnricher(response.getTask());
		response.setTask(taskEnricher.enrich(sessionContext));
	}

	/**
	 * Processes before the execution of actual business logic for listing task details. Executes the pre extension
	 * logic before the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param request
	 *            {@link TaskDTO} containing details for listing task.
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void preListExecutableTasks(SessionContext sessionContext, TaskDTO request) throws Exception {
		// Empty Implementation
	}

	/**
	 * Processes after the execution of actual business logic for listing task details. Executes the post extension
	 * logic after the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param request
	 *            {@link TaskDTO} containing details for listing task.
	 * @param response
	 *            {@link TaskListResponseDTO} containing task details
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void postListExecutableTasks(SessionContext sessionContext, TaskDTO request, TaskListResponseDTO response)
			throws Exception {
		// Empty Implementation
		List<TaskDTO> taskList = new ArrayList<TaskDTO>();
		for (int i=0;i<response.getTaskList().size();i++) {
		AbstractEnricher<TaskDTO> taskEnricher = new TaskEnricher(response.getTaskList().get(i));
		taskList.add(taskEnricher.enrich(sessionContext));
		
		}
		response.setTaskList(taskList);
	}

	/**
	 * Processes before the execution of actual business logic for enabling or disabling task aspects. Executes the pre
	 * extension logic before the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param taskAspectRequestDTO
	 *            {@link TaskDTO} containing id of particular task whose task aspects are to enabled or disabled and a
	 *            set of {@link TaskAspectDTO} that contains enumeration of Task Aspect name and enabled field to enable
	 *            or disable the aspect.
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void preEnableTaskAspect(SessionContext sessionContext, TaskAspectRequestDTO taskAspectRequestDTO) {

	}

	/**
	 * Processes after the execution of actual business logic for enabling or disabling task aspects. Executes the post
	 * extension logic after the actual business logic.
	 * 
	 * @param sessionContext
	 *            {@link SessionContext} containing session details
	 * @param taskAspectRequestDTO
	 *            {@link TaskDTO} containing id of particular task whose task aspects are to enabled or disabled and a
	 *            set of {@link TaskAspectDTO} that contains enumeration of Task Aspect name and enabled field to enable
	 *            or disable the aspect.
	 * @param transactionStatus
	 *            {@link TaskListResponseDTO} status of the corresponding request.
	 * @throws Exception
	 *             if any exception occurs.
	 */
	@Override
	public void postEnableTaskAspect(SessionContext sessionContext, TaskAspectRequestDTO request,
			TransactionStatus transactionStatus) {

	}
}
