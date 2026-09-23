package com.ofss.digx.cz.bea.common.hth;

import java.util.*;

/** Immutable scalar operation metadata. Never supply passwords, Codes or request bodies. */
public final class HthCRMInputData {
    private final String service;
    private final Map<String,Object> values;
    public HthCRMInputData(String service, Map<String,Object> source) {
        this.service = service;
        Map<String,Object> copy = new LinkedHashMap<String,Object>();
        for (Map.Entry<String,Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Boolean || value instanceof Number)
                copy.put(entry.getKey(), value);
        }
        this.values = Collections.unmodifiableMap(copy);
    }
    public String getService() { return service; }
    public Map<String,Object> getValues() { return values; }
}
