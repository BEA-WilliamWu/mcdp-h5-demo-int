package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import com.ofss.digx.app.adapter.AdapterFactory;
import com.ofss.digx.datatype.NameValuePair;
import com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter;

public final class HthMtbAdapterFactory extends AdapterFactory {
    private static final HthMtbAdapterFactory INSTANCE = new HthMtbAdapterFactory();
    public static HthMtbAdapterFactory getInstance() { return INSTANCE; }
    public Object getAdapter(String name) {
        return IHthMtbAdapter.ADAPTER.equals(name) ? new HthMtbAdapter() : null;
    }
    public Object getAdapter(String name, NameValuePair[] parameters) { return getAdapter(name); }
}
