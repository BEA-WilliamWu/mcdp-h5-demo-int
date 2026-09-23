package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter;
import com.ofss.digx.cz.bea.common.mtb.HthMtbSnapshot;

public final class HthMtbAdapter implements IHthMtbAdapter {
    public void collect(HthMtbSnapshot snapshot) {
        if (snapshot != null) HthMtbCollector.collect(snapshot.getService(), snapshot.getValues());
    }
}
