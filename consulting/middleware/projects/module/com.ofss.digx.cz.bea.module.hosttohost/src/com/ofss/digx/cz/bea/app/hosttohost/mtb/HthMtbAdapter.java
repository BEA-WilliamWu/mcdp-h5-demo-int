package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter;
import com.ofss.digx.cz.bea.common.mtb.HthCRMInputData;

public final class HthMtbAdapter implements IHthMtbAdapter {
    public void collect(HthCRMInputData snapshot) {
        if (snapshot != null) HthCRMAsserter.collect(snapshot);
    }
    public void collectApproval(com.ofss.fc.app.context.SessionContext context,
            com.ofss.digx.framework.domain.transaction.Transaction transaction, String action) {
        HthCRMApprovalAsserter.committed(context, transaction, action);
    }
}
