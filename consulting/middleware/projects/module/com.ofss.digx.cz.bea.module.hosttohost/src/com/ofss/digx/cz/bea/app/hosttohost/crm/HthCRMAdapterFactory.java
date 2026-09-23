package com.ofss.digx.cz.bea.app.hosttohost.crm;

import com.ofss.digx.app.adapter.AdapterFactory;
import com.ofss.digx.datatype.NameValuePair;
import com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter;

public final class HthCRMAdapterFactory extends AdapterFactory {
    private static final HthCRMAdapterFactory INSTANCE = new HthCRMAdapterFactory();
    public static HthCRMAdapterFactory getInstance() { return INSTANCE; }
    public Object getAdapter(String name) {
        return IHthCRMAdapter.ADAPTER.equals(name) ? new HthCRMAdapter() : null;
    }
    public Object getAdapter(String name, NameValuePair[] parameters) { return getAdapter(name); }
}
