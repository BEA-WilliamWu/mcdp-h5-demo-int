package com.ofss.digx.cz.bea.app.approval.service.transaction;

import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;

/** Configuration fixture counts attempts to access the new HTH path. */
public class HthMtbApprovalTest {
    public static void main(String[] args) {
        com.ofss.digx.framework.domain.transaction.Transaction transaction = new com.ofss.digx.framework.domain.transaction.Transaction();
        com.ofss.digx.framework.domain.transaction.TransactionKey key = new com.ofss.digx.framework.domain.transaction.TransactionKey();
        key.setId("test-approval");transaction.setKey(key);
        transaction.setServiceId("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update");
        UserExtensionDataDTO user = new UserExtensionDataDTO();user.setUserChannelType("BCO");
        transaction.setTransactionSnapshot(user);
        System.setProperty("hth849.configReads","0");
        HthMtbApproval.committed(null,transaction,"APPROVE");
        if(!"0".equals(System.getProperty("hth849.configReads")))throw new AssertionError("BCO entered HTH config path");
        user.setUserChannelType("HTH");
        HthMtbApproval.committed(null,transaction,"APPROVE");
        if(!"1".equals(System.getProperty("hth849.configReads")))throw new AssertionError("HTH path not reached");
        System.out.println("PASS: ordinary BCO approval never reads HTH configuration; HTH approval does");
    }
}
