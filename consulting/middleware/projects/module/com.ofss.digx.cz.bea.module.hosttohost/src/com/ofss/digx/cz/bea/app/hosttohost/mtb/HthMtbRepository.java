package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import com.ofss.fc.infra.das.orm.Session;

/** The caller owns its dedicated ORM session and transaction. */
final class HthMtbRepository {
    void create(Session session, HthMtbEvent event) throws java.lang.Exception {
        new LocalHthMtbRepositoryAdapter().create(session, event);
    }
}
