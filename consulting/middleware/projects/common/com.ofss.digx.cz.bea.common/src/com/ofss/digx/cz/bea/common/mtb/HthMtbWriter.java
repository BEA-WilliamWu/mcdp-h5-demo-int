package com.ofss.digx.cz.bea.common.mtb;

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
    static void write(HthMtbEvent event) {
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
    static void write(HthMtbEvent event,Resources resources) {
        Session session=null;
        try {
            int status=resources.transactionStatus();
            if(status!=Status.STATUS_NO_TRANSACTION && status!=Status.STATUS_COMMITTED && status!=Status.STATUS_ROLLEDBACK) {
                HthMtbCollector.log("WRITE_TX_ACTIVE",event,null);
                return;
            }
            session=resources.open();
            session.beginTransaction();
            new HthMtbRepository().create(session,event);
            session.fetchCurrentTransaction().commit();
            HthMtbCollector.log("WRITE",event,null);
        } catch (java.lang.Exception failure) {
            HthMtbCollector.log("WRITE_FAILED",event,failure);
        } finally {
            if (session != null) {
                try {
                    if (session.fetchCurrentTransaction()!=null && session.fetchCurrentTransaction().isActive())
                        session.fetchCurrentTransaction().rollback();
                } catch (java.lang.Exception failure) { HthMtbCollector.log("ROLLBACK_FAILED",event,failure); }
                try { resources.close(session); }
                catch (java.lang.Exception failure) { HthMtbCollector.log("CLOSE_FAILED",event,failure); }
            }
        }
    }
}
