package com.ofss.digx.cz.bea.app.approval.service.transaction;

import com.ofss.digx.cz.bea.common.hth.HthChannelSupport;
import com.ofss.digx.cz.bea.common.hth.IHthCRMAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.fc.app.context.SessionContext;

/** Shared approval boundary: check HTH first, then dispatch through the interface only. */
final class HthCRMApproval {
    private HthCRMApproval() { }
    static void committed(SessionContext context, com.ofss.digx.framework.domain.transaction.Transaction transaction,
            String action) {
        if (transaction == null) return;
        Object snapshot = transaction.getTransactionSnapshot();
        String channel = snapshot instanceof UserExtensionDataDTO
            ? ((UserExtensionDataDTO) snapshot).getUserChannelType() : null;
        if (!HthChannelSupport.isHthApproval(transaction.getServiceId(), channel)) return;
        try {
            com.ofss.digx.app.adapter.IAdapterFactory factory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
                .getInstance().getAdapterFactory(IHthCRMAdapter.FACTORY);
            ((IHthCRMAdapter) factory.getAdapter(IHthCRMAdapter.ADAPTER)).collectApproval(context, transaction, action);
        } catch (java.lang.Exception | LinkageError failure) {
            java.util.logging.Logger.getLogger(HthCRMApproval.class.getName()).log(java.util.logging.Level.WARNING,
                "HTH_CRM stage=APPROVAL_CAPTURE_FAILED, exceptionType={0}", failure.getClass().getName());
        }
    }
}
