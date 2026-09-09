package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredential;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredentialKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;

/** Local ORM access and conditional SQL; supplied sessions are never committed or closed here. */
public class LocalHthApiPasswordCredentialRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthApiPasswordCredential>
    implements IHthApiPasswordCredentialRepositoryAdapter {
  private LocalHthApiPasswordCredentialRepositoryAdapter() { }

  public static LocalHthApiPasswordCredentialRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public HthApiPasswordCredential read(HthApiPasswordCredentialKey key) throws Exception {
    Session session = null;
    boolean owned = !DataAccessManager.getManager().isSessionOpen();
    try {
      if (owned) {
        session = DataAccessManager.getManager().openSession();
      }
      return super.get(HthApiPasswordCredential.class, key);
    } finally {
      if (owned && session != null) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public void create(HthApiPasswordCredential object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthApiPasswordCredential object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthApiPasswordCredentialRepositoryAdapter INSTANCE = new LocalHthApiPasswordCredentialRepositoryAdapter();
  }
  @Override
  public List findStatus(Session session, String partyId, String userId) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT CREDENTIAL_STATUS FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL "
            + "WHERE PARTY_ID = ? AND USER_ID = ?");
    query.setParameter(1, partyId);
    query.setParameter(2, userId);
    return query.list();
  }

  @Override
  public void write(Session session, String partyId, String userId, String operation, String requestId, String passwordHash) throws Exception {
    Query query;
    if ("SETUP".equals(operation)) {
      query = session.createSQLQuery(
          "INSERT INTO HTH_BEA.HTH_API_PASSWORD_CREDENTIAL "
              + "(PASSWORD_HASH, LAST_REQUEST_ID, LAST_UPDATED_BY, PARTY_ID, USER_ID, "
              + "CREDENTIAL_STATUS, CREDENTIAL_VERSION, CREATED_AT, UPDATED_AT) "
              + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 1, SYSTIMESTAMP, SYSTIMESTAMP)");
    } else {
      query = session.createSQLQuery(
          "UPDATE HTH_BEA.HTH_API_PASSWORD_CREDENTIAL SET PASSWORD_HASH = ?, LAST_REQUEST_ID = ?, "
              + "LAST_UPDATED_BY = ?, CREDENTIAL_VERSION = CREDENTIAL_VERSION + 1, UPDATED_AT = SYSTIMESTAMP "
              + "WHERE PARTY_ID = ? AND USER_ID = ? AND CREDENTIAL_STATUS = 'ACTIVE'");
    }
    query.setParameter(1, passwordHash);
    query.setParameter(2, requestId);
    query.setParameter(3, userId);
    query.setParameter(4, partyId);
    query.setParameter(5, userId);
    if (query.executeUpdate() != 1) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_006");
    }
  }
}
