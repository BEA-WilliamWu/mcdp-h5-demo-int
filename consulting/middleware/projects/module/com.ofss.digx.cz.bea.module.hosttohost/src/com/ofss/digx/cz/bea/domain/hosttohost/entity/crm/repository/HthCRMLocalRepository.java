package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.HthCRMEvent3DomainDTO;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.adapter.LocalHthCRMRepositoryAdapter;
import com.ofss.fc.infra.das.orm.Session;

/** HTH event repository; the writer owns the independent session and transaction. */
public final class HthCRMLocalRepository {
    public void create(Session session, HthCRMEvent3DomainDTO event) throws java.lang.Exception {
        new LocalHthCRMRepositoryAdapter().create(session, event);
    }
}
