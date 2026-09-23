package com.ofss.digx.cz.bea.app.approval.service.transaction;

import com.ofss.digx.cz.bea.common.mtb.HthChannelSupport;
import com.ofss.digx.cz.bea.common.mtb.IHthMtbAdapter;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.fc.app.context.SessionContext;

/** Shared approval boundary: check HTH first, then dispatch through the interface only. */
final class HthMtbApproval {
    private HthMtbApproval() { }
    static void committed(SessionContext context, com.ofss.digx.framework.domain.transaction.Transaction transaction,
            String action) {
        if (transaction == null) return;
        Object snapshot = transaction.getTransactionSnapshot();
        String channel = snapshot instanceof UserExtensionDataDTO
            ? ((UserExtensionDataDTO) snapshot).getUserChannelType() : null;
        if (!HthChannelSupport.isHthApproval(transaction.getServiceId(), channel)) return;
        try {
            com.ofss.digx.app.adapter.IAdapterFactory factory = com.ofss.digx.app.adapter.AdapterFactoryConfigurator
                .getInstance().getAdapterFactory(IHthMtbAdapter.FACTORY);
            ((IHthMtbAdapter) factory.getAdapter(IHthMtbAdapter.ADAPTER)).collectApproval(context, transaction, action);
        } catch (java.lang.Exception | LinkageError failure) {
            java.util.logging.Logger.getLogger(HthMtbApproval.class.getName()).log(java.util.logging.Level.WARNING,
                "HTH_MTB stage=APPROVAL_CAPTURE_FAILED, exceptionType={0}", failure.getClass().getName());
        }
    }
}
