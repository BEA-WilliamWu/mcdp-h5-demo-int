package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.TransactionKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;

/** Response-only channel metadata for pending onboarding approvals. */
class HthApprovalUserChannel {
    private static final String FIELD = "userChannelType";

    void enrich(List<TransactionDTO> transactions) {
        if (transactions == null) {
            return;
        }
        for (TransactionDTO transaction : transactions) {
            if (transaction == null) {
                continue;
            }
            String taskId = transaction.getTransactionName();
            if ((taskId == null || taskId.isEmpty()) && transaction.getTaskDTO() != null) {
                taskId = transaction.getTaskDTO().getId();
            }
            if (isHthTask(taskId)) {
                addChannel(transaction, "HTH");
            } else if ("MT_N_CUS".equals(taskId) || "MT_N_UUS".equals(taskId)) {
                addChannel(transaction, submittedUserChannel(transaction));
            }
        }
    }

    private String submittedUserChannel(TransactionDTO transaction) {
        try {
            Object snapshot = transaction.getTransactionSnapshot();
            if (snapshot == null && transaction.getTransactionId() != null) {
                // New users may not exist yet. Read the submitted request, not the live profile.
                snapshot = readSnapshot(transaction.getTransactionId());
            }
            if (snapshot instanceof UserExtensionDataDTO) {
                String channel = ((UserExtensionDataDTO) snapshot).getUserChannelType();
                if ("HTH".equalsIgnoreCase(channel == null ? null : channel.trim())) {
                    return "HTH";
                }
                if ("BCO".equalsIgnoreCase(channel == null ? null : channel.trim())) {
                    return "BCO";
                }
            }
        } catch (java.lang.Exception e) {
            // Metadata must not prevent an existing approval row from being returned.
            BeaSystemOut.println("HTH_PENDING_APPROVAL stage=CHANNEL_UNAVAILABLE exceptionType="
                    + e.getClass().getSimpleName());
        }
        // Missing historical channel data does not prove that the request is BCO or HTH.
        return "UNKNOWN";
    }

    Object readSnapshot(String transactionId) throws Exception {
        TransactionKey key = new TransactionKey();
        key.setId(transactionId);
        Transaction stored = new Transaction().read(key);
        return stored == null ? null : stored.getTransactionSnapshot();
    }

    private boolean isHthTask(String taskId) {
        return "UAT_N_HUA_NEW".equals(taskId) || "UAT_N_HUA_EDT".equals(taskId)
                || "UAT_N_HUA_DEL".equals(taskId) || "PP_N_HTH_CERT_UPL".equals(taskId)
                || "PP_N_HTH_ENB".equals(taskId) || "PP_N_HTH_EDT".equals(taskId)
                || "PP_N_HTH_DIS".equals(taskId);
    }

    private void addChannel(TransactionDTO transaction, String channel) {
        Dictionary[] original = transaction.getDictionaryArray();
        Dictionary[] dictionaries = original == null || original.length == 0
                ? new Dictionary[1] : Arrays.copyOf(original, original.length);
        Dictionary first = new Dictionary();
        List<NameValuePairDTO> pairs = new ArrayList<NameValuePairDTO>();
        if (dictionaries[0] != null) {
            first.setFullyQualifiedClassName(dictionaries[0].getFullyQualifiedClassName());
            NameValuePairDTO[] existing = dictionaries[0].getNameValuePairDTOArray();
            if (existing != null) {
                for (NameValuePairDTO pair : existing) {
                    if (pair == null || (!FIELD.equals(pair.getGenericName()) && !FIELD.equals(pair.getName()))) {
                        pairs.add(pair);
                    }
                }
            }
        }
        NameValuePairDTO field = new NameValuePairDTO();
        field.setName(FIELD);
        field.setGenericName(FIELD);
        field.setValue(channel);
        field.setDatatype("java.lang.String");
        pairs.add(field);
        first.setNameValuePairDTOArray(pairs.toArray(new NameValuePairDTO[pairs.size()]));
        // Copy the changed containers so a shared dictionary/snapshot is not modified.
        dictionaries[0] = first;
        transaction.setDictionaryArray(dictionaries);
    }
}
