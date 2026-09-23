package com.ofss.digx.cz.bea.common.mtb;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, explicitly projected record. Never retains a request, response or Session. */
public final class HthMtbEvent {
    private final Map<String,String> fields;
    HthMtbEvent(Map<String,String> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String,String>(fields));
    }
    public String get(String column) { return fields.get(column); }
    public Map<String,String> fields() { return fields; }
    HthMtbEvent rolledBack() {
        Map<String,String> copy = new LinkedHashMap<String,String>(fields);
        copy.put("EVENT_STATUS_CODE", "R");
        copy.put("ERROR_CODE", "BUSINESS_ROLLBACK");
        // Outcome is part of deduplication; a later committed retry is a distinct result.
        copy.put("DEDUP_KEY", null);
        return new HthMtbEvent(copy);
    }
}
