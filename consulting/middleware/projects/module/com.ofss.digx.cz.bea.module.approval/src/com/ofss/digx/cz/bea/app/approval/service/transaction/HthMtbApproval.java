package com.ofss.digx.cz.bea.app.approval.service.transaction;

import java.util.*;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.common.mtb.HthMtbCollector;

/** Whitelist only. BCO snapshots return before configuration, transaction or database access. */
final class HthMtbApproval {
    private HthMtbApproval() { }
    static void committed(SessionContext context, com.ofss.digx.framework.domain.transaction.Transaction transaction,
            String action) {
        try {
            if(transaction==null)return;
            Object snapshot=transaction.getTransactionSnapshot();
            String service=transaction.getServiceId();
            if(service==null)return;
            Map<String,Object> values=new LinkedHashMap<String,Object>();
            String operation;
            if(snapshot instanceof UserExtensionDataDTO) {
                UserExtensionDataDTO user=(UserExtensionDataDTO)snapshot;
                if(!"HTH".equalsIgnoreCase(user.getUserChannelType()) && !"H2H".equalsIgnoreCase(user.getUserChannelType()))return;
                if(service.endsWith(".create"))operation="CREATE";
                else if(service.endsWith(".update"))operation="UPDATE";
                else return;
                service="UserExtensionData."+operation.toLowerCase(Locale.ROOT);
                values.put("newUserChannelType",user.getUserChannelType());
                values.put("targetUserId",user.getUserID());values.put("partyId",user.getCdcNo());
            } else if(snapshot instanceof HostToHostUserAccessDTO) {
                HostToHostUserAccessDTO access=(HostToHostUserAccessDTO)snapshot;
                if(service.endsWith(".submit"))operation="ACCESS_CREATE";
                else if(service.endsWith(".edit"))operation="ACCESS_EDIT";
                else if(service.endsWith(".delete"))operation="ACCESS_DELETE";
                else return;
                values.put("targetUserId",access.getCloseId());values.put("partyId",access.getPartyId());
                values.put("linkageType",access.getLinkageType());
            } else if(snapshot instanceof HostToHostManagementDTO) {
                if(service.endsWith(".submit"))operation="COMPANY_ENABLE";
                else if(service.endsWith(".edit"))operation="COMPANY_EDIT";
                else if(service.endsWith(".disable"))operation="COMPANY_DISABLE";
                else return;
                values.put("partyId",((HostToHostManagementDTO)snapshot).getPartyId());
            } else return;
            values.put("operation",operation);values.put("businessOutcome","SUCCESS");
            // Approval action success is not a claim that the underlying business has applied.
            values.put("mtbPhase","APPROVAL_"+("APPROVE".equals(action)?"APPROVE":("REJECT".equals(action)?"REJECT":"ACTION")));
            values.put("actorUserId",context==null?null:context.getUserId());
            values.put("approvalReference",transaction.getKey().getId());
            values.put("occurredAt",java.time.Instant.now().toString());
            HthMtbCollector.collect(service,values);
        } catch(java.lang.Exception failure) {
            java.util.logging.Logger.getLogger(HthMtbApproval.class.getName()).log(java.util.logging.Level.WARNING,
                "HTH_MTB stage=APPROVAL_CAPTURE_FAILED, exceptionType={0}",failure.getClass().getName());
        }
    }
}
