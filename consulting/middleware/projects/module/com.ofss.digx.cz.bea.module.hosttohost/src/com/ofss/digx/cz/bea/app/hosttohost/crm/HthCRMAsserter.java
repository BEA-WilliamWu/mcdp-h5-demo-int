package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.util.*;
import java.util.logging.*;
import javax.transaction.*;
import weblogic.transaction.TransactionHelper;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.DataAccessManager;

/** HTH-only sink for sanitized operation snapshots; no dependency on notification delivery. */
public final class HthCRMAsserter {
    private static final Logger LOG=Logger.getLogger(HthCRMAsserter.class.getName());
    private HthCRMAsserter() { }
    public static void collect(String service, Map<String,Object> source) {
        collect(new com.ofss.digx.cz.bea.common.hth.HthCRMInputData(service, source));
    }
    public static void collect(com.ofss.digx.cz.bea.common.hth.HthCRMInputData input) {
        if (input == null) return;
        String service = input.getService();
        Map<String,Object> source = input.getValues();
        try {
            String activity=HthCRMRequestAssembler.activity(service,HthCRMRequestAssembler.text(source,"operation"));
            if (activity==null) return;
            java.util.prefs.Preferences config=ConfigurationFactory.getInstance().getConfigurations("HTHMtbConfiguration");
            if (!"Y".equalsIgnoreCase(config.get("ENABLED","N"))) return;
            Map<String,Object> snapshot=new LinkedHashMap<String,Object>(source);
            Object task=com.ofss.digx.infra.thread.ThreadAttribute.get(com.ofss.fc.infra.thread.ThreadAttribute.CURRENT_TASK);
            Object ip=com.ofss.digx.infra.thread.ThreadAttribute.get("FMO_IP_ADDRESS");
            if(task instanceof String)snapshot.put("crmTask",task);
            if(ip instanceof String)snapshot.put("crmIp",ip);
            final HthCRMEvent3DomainDTO event=HthCRMRequestAssembler.assemble(service,snapshot,config.get("ACTIVITY_"+activity,null));
            if(event==null)return;
            TransactionManager manager=TransactionHelper.getTransactionHelper().getTransactionManager();
            boolean localActive=DataAccessManager.getManager().isSessionOpen()
                && DataAccessManager.getManager().fetchCurrentSession().fetchCurrentTransaction()!=null
                && DataAccessManager.getManager().fetchCurrentSession().fetchCurrentTransaction().isActive();
            schedule(event,manager.getTransaction(),localActive,new Sink() {
                public void write(HthCRMEvent3DomainDTO value) { HthCRMWriter.write(value); }
            });
        } catch (java.lang.Exception failure) { log("COLLECT_FAILED",null,failure); }
    }
    interface Sink { void write(HthCRMEvent3DomainDTO value); }
    static void schedule(final HthCRMEvent3DomainDTO event,Transaction transaction,boolean localActive,final Sink sink)
            throws java.lang.Exception {
        if(transaction!=null && (transaction.getStatus()==Status.STATUS_ACTIVE || transaction.getStatus()==Status.STATUS_MARKED_ROLLBACK)) {
            transaction.registerSynchronization(new Synchronization() {
                public void beforeCompletion() { }
                public void afterCompletion(int status) {
                    if(status==Status.STATUS_COMMITTED)sink.write(event);
                    else if(status==Status.STATUS_ROLLEDBACK)sink.write(event.rolledBack());
                    else log("TX_OUTCOME_UNKNOWN",event,null);
                }
            });

        } else if(transaction!=null && transaction.getStatus()!=Status.STATUS_NO_TRANSACTION) {
            log("TX_OUTCOME_UNKNOWN",event,null);
        } else if(localActive) {
            // Platform resource-local API has no completion callback: do not claim a committed success.
            log("LOCAL_TX_PENDING",event,null);
        } else sink.write(event);
    }
    static void log(String stage,HthCRMEvent3DomainDTO event,Throwable failure) {
        LOG.log(failure==null ? Level.INFO : Level.WARNING,
            "HTH_CRM stage={0}, eventId={1}, activity={2}, phase={3}, exceptionType={4}",
            new Object[]{stage,event==null?null:event.get("EVENT_ID"),event==null?null:event.get("ACTIVITY_KEY"),
                event==null?null:event.get("PHASE"),failure==null?null:failure.getClass().getName()});
    }
}
