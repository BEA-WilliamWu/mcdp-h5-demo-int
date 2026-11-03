package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import com.ofss.digx.app.approval.dto.transaction.*;
import com.ofss.digx.app.messages.Status;
import com.ofss.digx.app.messages.Status.ResultType;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.enumeration.approval.*;
import com.ofss.digx.framework.domain.transaction.*;
import com.ofss.fc.app.context.SessionContext;

/** Calls production approval gates with real SDK DTOs and a repository/dispatch probe. */
public class Hth2110ApprovalLifecycleTest {
    static class Approval extends TransactionApprovalDetails {
        String signers="MAKER@PARTY~EARLIER@PARTY~FINAL@PARTY~";
        @Override public String getSignedBy(){return signers;}
    }
    static final String MARKER="HTH_2110_CONTACT_UPDATED";
    static class Probe extends HthContactUpdateApprovalLifecycle {
        Transaction saved;int calls,reads;String recipient;boolean fails;
        @Override Transaction readTransaction(String id){reads++;return saved;}
        @Override void notifyContacts(SessionContext c,String[] s,String signer){calls++;recipient=signer;if(fails)throw new IllegalStateException("SENSITIVE_SENTINEL");}
    }
    static void mark(){com.ofss.fc.infra.thread.ThreadAttribute.set(MARKER,new String[]{"TX","PARTY","TARGET@PARTY","old","new","1111","2222","852","86"});}
    public static void main(String[] args){
        SessionContext c=new SessionContext();c.setTargetUnit("OBDX_BU");c.setUserId("BACKGROUND");
        TransactionActionDTO request=new TransactionActionDTO();request.setAction(ApprovalAction.APPROVE);
        TransactionDTO client=new TransactionDTO();client.setTransactionName("MT_N_UUS");client.setTransactionId("TX");request.setTransactionDTO(client);
        TransactionActionResponse response=new TransactionActionResponse();Status status=new Status();status.setResult(ResultType.SUCCESSFUL);response.setStatus(status);
        Probe p=new Probe();p.saved=saved();mark();p.afterAction(c,request,response);
        check(p.calls==1&&"FINAL@PARTY".equals(p.recipient),"Final signer from committed server transaction, not session/background or client snapshot");
        p.afterAction(c,request,response);check(p.calls==1,"Consumed marker does not repeat");
        status.setResult(ResultType.FAILED);mark();p.afterAction(c,request,response);status.setResult(ResultType.SUCCESSFUL);
        for(ProcessingStatus failure:new ProcessingStatus[]{ProcessingStatus.FAIL,ProcessingStatus.PROCESSING}){
            p.saved.getProcessingDetails().setStatus(failure);mark();p.afterAction(c,request,response);
        }
        p.saved=saved();p.saved.getApprovalDetails().setStatus(ApprovalStatus.PENDING_APPROVAL);mark();p.afterAction(c,request,response);
        p.saved=saved();request.setAction(ApprovalAction.REJECT);mark();p.afterAction(c,request,response);request.setAction(ApprovalAction.APPROVE);
        client.setTransactionId("WRONG");mark();p.afterAction(c,request,response);client.setTransactionId("TX");
        client.setTransactionName("MT_N_CUS");mark();p.afterAction(c,request,response);client.setTransactionName("MT_N_UUS");
        ((UserExtensionDataDTO)p.saved.getTransactionSnapshot()).setUserID("ANOTHER@PARTY");mark();p.afterAction(c,request,response);
        p.saved=saved();((Approval)p.saved.getApprovalDetails()).signers="MAKER@PARTY";mark();p.afterAction(c,request,response);
        p.saved=saved();p.saved.setServiceId("other.service");mark();p.afterAction(c,request,response);
        p.saved=saved();c.setTargetUnit("BM");mark();p.afterAction(c,request,response);c.setTargetUnit("OBDX_BU");
        mark();p.afterAction(c,request,null);check(p.calls==1,"Failed, partial, unrelated, wrong owner and missing response do not notify");
        check(com.ofss.fc.infra.thread.ThreadAttribute.get(MARKER)==null,"All exit paths consume marker");
        // BCO and single-field updates never set a marker; even an approved transaction then has no callback work.
        int reads=p.reads;p.afterAction(c,request,response);check(p.reads==reads,"No marker means no reads or notification for ordinary BCO");
        p.fails=true;mark();p.afterAction(c,request,response);check(status.getResult()==ResultType.SUCCESSFUL,"Post-commit notice error never rewrites committed approval result");
        System.out.println("PASS 2110 approval: final successful execution/commit, signer binding, no failure/partial/replay/BCO notifications");
    }
    static Transaction saved(){
        Transaction t=new Transaction();t.setTransactionName("MT_N_UUS");t.setServiceId("com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update");
        Approval a=new Approval();a.setStatus(ApprovalStatus.APPROVED);a.setAction(ApprovalAction.APPROVE);t.setApprovalDetails(a);
        ProcessingDetails p=new ProcessingDetails();p.setStatus(ProcessingStatus.SUCCESS);t.setProcessingDetails(p);
        UserExtensionDataDTO u=new UserExtensionDataDTO();u.setCdcNo("PARTY");u.setUserID("TARGET@PARTY");t.setTransactionSnapshot(u);return t;
    }
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
