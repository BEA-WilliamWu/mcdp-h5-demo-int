package com.ofss.digx.cz.bea.common.hth;

/** Cross-module contract only; implementation is owned by hosttohost. */
public interface IHthCRMAdapter {
    String FACTORY = "HTH_MTB_ADAPTER_FACTORY";
    String ADAPTER = "HTH_MTB_ADAPTER";
    void collect(HthCRMInputData snapshot);
    void collectApproval(com.ofss.fc.app.context.SessionContext context,
        com.ofss.digx.framework.domain.transaction.Transaction transaction, String action);
}
