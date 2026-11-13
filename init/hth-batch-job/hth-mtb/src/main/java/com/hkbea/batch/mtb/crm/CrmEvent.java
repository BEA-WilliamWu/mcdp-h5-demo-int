package com.hkbea.batch.mtb.crm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One streamed row. The database ID is for validation only, not an extra CSV column. */
public record CrmEvent(String sourceId, List<String> fields) {
    public CrmEvent {
        if (sourceId == null || sourceId.isEmpty()) {
            throw new IllegalStateException("Missing HTH_CRM_EVENT.ID");
        }
        if (fields.size() != CrmFileLayout.COLUMNS.size()) {
            throw new IllegalStateException("Expected 61 CRM fields");
        }
        // Database NULLs are allowed and become empty CSV fields.
        fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }
}
