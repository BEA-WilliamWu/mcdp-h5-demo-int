package com.ofss.digx.cz.bea.app.hosttohost.crm;

import com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter;
import com.ofss.digx.cz.bea.common.hth.HthCRMInputData;

public final class HthCRMAdapter implements IHthCRMAdapter {
    public void collect(HthCRMInputData snapshot) {
        if (snapshot != null) HthCRMAsserter.collect(snapshot);
    }
    public void collectApproval(com.ofss.fc.app.context.SessionContext context,
            com.ofss.digx.framework.domain.transaction.Transaction transaction, String action) {
        HthCRMApprovalAsserter.committed(context, transaction, action);
    }
}
