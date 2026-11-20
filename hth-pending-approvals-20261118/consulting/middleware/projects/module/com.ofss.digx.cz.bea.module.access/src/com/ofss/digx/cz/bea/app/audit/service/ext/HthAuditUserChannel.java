package com.ofss.digx.cz.bea.app.audit.service.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;

/** Response-only onboarding channel marker; never changes stored audit records. */
final class HthAuditUserChannel {
    private static final String FIELD = "userChannelType";
    // Same explicit onboarding IDs as the audit dropdown. Released Inquiry is excluded.
    private static final Set<String> HTH_TASKS = new HashSet<String>(Arrays.asList(
            "MT_N_CUS_HTH", "MT_N_UUS_HTH", "MT_N_RUS_HTH",
            "UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL",
            "UAT_N_HAP_GEN", "UAT_N_HAP_REGEN", "UAT_N_HAP_RVL",
            "CM_N_HAP_SETUP", "CM_N_HAP_RESET", "PP_N_HTH_CERT_UPL",
            "PP_N_HTH_ENB", "PP_N_HTH_EDT", "PP_N_HTH_DIS"));

    void enrich(List<AuditDTO> audits) {
        if (audits == null) {
            return;
        }
        for (AuditDTO audit : audits) {
            if (audit == null) {
                continue;
            }
            // The list does not contain target-user snapshots. A shared/legacy task ID
            // or the acting user's channel cannot establish the target user's channel.
            String channel = HTH_TASKS.contains(audit.getTaskCode()) ? "HTH" : "UNKNOWN";
            addChannel(audit, channel);
        }
    }

    private void addChannel(AuditDTO audit, String channel) {
        Dictionary[] original = audit.getDictionaryArray();
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
        // Preserve the original dictionaries in case another response shares them.
        dictionaries[0] = first;
        audit.setDictionaryArray(dictionaries);
    }
}
