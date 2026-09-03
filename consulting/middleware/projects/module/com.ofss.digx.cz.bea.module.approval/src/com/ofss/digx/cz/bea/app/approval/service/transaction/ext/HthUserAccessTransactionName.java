package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.SerializationUtils;

import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.cz.bea.app.common.resource.task.enricher.TaskEnricher;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.locale.LocaleUtils;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;

/** Display-only naming for HTH tasks shared by related and associated account access. */
class HthUserAccessTransactionName {
    private static final Logger LOGGER = MultiEntityLogger.getUniqueInstance()
            .getLogger(HthUserAccessTransactionName.class.getName());

    void enrich(TransactionDTO transaction) {
        if (transaction == null || transaction.getTaskDTO() == null) {
            return;
        }
        String bcoTaskId = associatedTaskId(transaction.getTaskDTO().getId());
        if (bcoTaskId == null) {
            return;
        }
        try {
            Object snapshot = transaction.getTransactionSnapshot();
            if (snapshot == null && transaction.getTransactionId() != null) {
                // Pending lists can omit the snapshot. Read the saved request, not live access data.
                snapshot = readSnapshot(transaction.getTransactionId());
            }
            if (!(snapshot instanceof HostToHostUserAccessDTO)
                    || !"ASSOCIATED".equals(((HostToHostUserAccessDTO) snapshot).getLinkageType())) {
                return;
            }
            String bcoName = readBcoName(bcoTaskId);
            if (bcoName != null && !bcoName.isEmpty()) {
                // Do not rename a potentially shared TaskDTO or change its ID/aspects/permissions.
                TaskDTO displayTask = SerializationUtils.clone(transaction.getTaskDTO());
                displayTask.setName("HTH " + bcoName);
                transaction.setTaskDTO(displayTask);
            }
        } catch (java.lang.Exception e) {
            // A missing snapshot/translation must not hide an otherwise valid approval row.
            LOGGER.log(Level.WARNING, "Unable to resolve associated HTH transaction display name", e);
        }
    }

    Object readSnapshot(String transactionId) throws Exception {
        TransactionKey key = new TransactionKey();
        key.setId(transactionId);
        Transaction stored = new Transaction().read(key);
        return stored == null ? null : stored.getTransactionSnapshot();
    }

    String readBcoName(String taskId) {
        return new TaskEnricher(null).getString("resources.nls.CommonTask", taskId,
                LocaleUtils.getUserLocale());
    }

    private String associatedTaskId(String taskId) {
        if ("UAT_N_HUA_NEW".equals(taskId)) {
            return "LAT_N_CA";
        }
        if ("UAT_N_HUA_EDT".equals(taskId)) {
            return "LAT_N_UA";
        }
        if ("UAT_N_HUA_DEL".equals(taskId)) {
            return "LAT_N_DA";
        }
        return null;
    }
}
