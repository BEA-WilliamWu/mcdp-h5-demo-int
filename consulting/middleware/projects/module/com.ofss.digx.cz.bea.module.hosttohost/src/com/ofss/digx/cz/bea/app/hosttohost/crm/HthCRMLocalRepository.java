package com.ofss.digx.cz.bea.app.hosttohost.crm;

import com.ofss.fc.infra.das.orm.Session;

/** The caller owns its dedicated ORM session and transaction. */
final class HthCRMLocalRepository {
    void create(Session session, HthCRMEvent3DomainDTO event) throws java.lang.Exception {
        new LocalHthCRMRepositoryAdapter().create(session, event);
    }
}
