package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import javax.transaction.TransactionManager;
import javax.transaction.Status;
import weblogic.transaction.TransactionHelper;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Session;

/** Runs only outside an active JTA transaction. Never suspends, resumes or commits the caller. */
final class HthMtbWriter {
    interface Resources {
        int transactionStatus() throws java.lang.Exception;
        Session open() throws java.lang.Exception;
        void close(Session session) throws java.lang.Exception;
    }
    static void write(HthCRMEvent3DomainDTO event) {
        write(event,new Resources() {
            public int transactionStatus() throws java.lang.Exception {
                TransactionManager manager=TransactionHelper.getTransactionHelper().getTransactionManager();
                return manager.getStatus();
            }
            public Session open() throws java.lang.Exception {
                return DataAccessManager.getManager().openNewSession("NONXA");
            }
            public void close(Session session) throws java.lang.Exception {
                DataAccessManager.getManager().closeSession(session);
            }
        });
    }
    static void write(HthCRMEvent3DomainDTO event,Resources resources) {
        Session session=null;
        try {
            int status=resources.transactionStatus();
            if(status!=Status.STATUS_NO_TRANSACTION && status!=Status.STATUS_COMMITTED && status!=Status.STATUS_ROLLEDBACK) {
                HthCRMAsserter.log("WRITE_TX_ACTIVE",event,null);
                return;
            }
            session=resources.open();
            session.beginTransaction();
            new HthCRMLocalRepository().create(session,event);
            session.fetchCurrentTransaction().commit();
            HthCRMAsserter.log("WRITE",event,null);
        } catch (java.lang.Exception failure) {
            HthCRMAsserter.log("WRITE_FAILED",event,failure);
        } finally {
            if (session != null) {
                try {
                    if (session.fetchCurrentTransaction()!=null && session.fetchCurrentTransaction().isActive())
                        session.fetchCurrentTransaction().rollback();
                } catch (java.lang.Exception failure) { HthCRMAsserter.log("ROLLBACK_FAILED",event,failure); }
                try { resources.close(session); }
                catch (java.lang.Exception failure) { HthCRMAsserter.log("CLOSE_FAILED",event,failure); }
            }
        }
    }
}
