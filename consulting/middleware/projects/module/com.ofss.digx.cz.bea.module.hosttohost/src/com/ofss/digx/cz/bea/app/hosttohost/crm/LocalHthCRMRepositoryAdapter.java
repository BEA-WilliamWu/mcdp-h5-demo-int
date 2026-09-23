package com.ofss.digx.cz.bea.app.hosttohost.crm;

import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.das.orm.Query;

/** Parameterized SQL through the same ORM API used by the HTH password repositories. */
final class LocalHthCRMRepositoryAdapter {
    static final String[] COLUMNS = {"EVENT_ID","EVENT_DTE","EVENT_TIME","SOURCE_SYSTEM",
        "CHANNEL_TYPE","TARGET_USER_CHANNEL","ACTIVITY_KEY","EVENT_ACTV_TYPE_CODE",
        "EVENT_STATUS_CODE","PHASE","FIN_IND","USER_ID","TARGET_USER_ID","ACCT_NBR",
        "RELATIONSHIP_TYPE","SERVICE_ID","TASK_CODE","SOURCE_TRX_REF_NBR","SOURCE_ACTION_ID",
        "REQUEST_ID","IP_ADDRESS","ERROR_CODE","DEDUP_KEY"};
    void create(Session session, HthCRMEvent3DomainDTO event) throws java.lang.Exception {
        StringBuilder sql = new StringBuilder("INSERT INTO HTH_BEA.HTH_MTB_EVENT_DETAILS (");
        StringBuilder values = new StringBuilder();
        for (int i=0;i<COLUMNS.length;i++) {
            if (i>0) { sql.append(','); values.append(','); }
            sql.append(COLUMNS[i]); values.append('?');
        }
        sql.append(",CREATED_AT) VALUES (").append(values).append(",CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Hong_Kong' AS TIMESTAMP))");
        Query query=session.createSQLQuery(sql.toString());
        for (int i=0;i<COLUMNS.length;i++) query.setParameter(i+1,event.get(COLUMNS[i]));
        query.setTimeout(5);
        query.executeUpdate();
    }
}
