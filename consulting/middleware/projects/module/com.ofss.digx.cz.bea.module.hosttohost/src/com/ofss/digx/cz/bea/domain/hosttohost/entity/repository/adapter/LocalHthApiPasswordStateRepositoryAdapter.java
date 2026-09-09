package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordState;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordStateKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;

/** Local ORM access and conditional SQL; supplied sessions are never committed or closed here. */
public class LocalHthApiPasswordStateRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthApiPasswordState>
    implements IHthApiPasswordStateRepositoryAdapter {
  private LocalHthApiPasswordStateRepositoryAdapter() { }

  public static LocalHthApiPasswordStateRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public HthApiPasswordState read(HthApiPasswordStateKey key) throws Exception {
    Session session = null;
    boolean owned = !DataAccessManager.getManager().isSessionOpen();
    try {
      if (owned) {
        session = DataAccessManager.getManager().openSession();
      }
      return super.get(HthApiPasswordState.class, key);
    } finally {
      if (owned && session != null) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public void create(HthApiPasswordState object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthApiPasswordState object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthApiPasswordStateRepositoryAdapter INSTANCE = new LocalHthApiPasswordStateRepositoryAdapter();
  }
  @Override
  public List findStatus(Session session, String partyId, String userId) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT CREDENTIAL_STATUS FROM HTH_BEA.HTH_API_PASSWORD_STATE "
            + "WHERE PARTY_ID = ? AND USER_ID = ? AND OBJECT_STATUS = 'A'");
    query.setParameter(1, partyId);
    query.setParameter(2, userId);
    query.setMaxResults(1);
    return query.list();
  }

  @Override
  public int complete(Session session, String partyId, String userId, String operation, String requestId, String referenceNumber) throws Exception {
    Query state = session.createSQLQuery(
        "MERGE INTO HTH_BEA.HTH_API_PASSWORD_STATE T USING "
            + "(SELECT ? PARTY_ID, ? USER_ID FROM DUAL) S "
            + "ON (T.PARTY_ID = S.PARTY_ID AND T.USER_ID = S.USER_ID) "
            + "WHEN MATCHED THEN UPDATE SET T.CREDENTIAL_STATUS = 'ACTIVE', "
            + "T.CREDENTIAL_VERSION = NVL(T.CREDENTIAL_VERSION, 0) + 1, "
            + "T.LAST_RESET_AT = CASE WHEN ? = 'RESET' THEN SYSDATE ELSE T.LAST_RESET_AT END, "
            + "T.SETUP_AT = CASE WHEN T.SETUP_AT IS NULL THEN SYSDATE ELSE T.SETUP_AT END, "
            + "T.LAST_REQUEST_ID = ?, T.LAST_REFERENCE_NUMBER = ?, T.LAST_UPDATED_BY = ?, "
            + "T.LAST_UPDATE_DATE = SYSDATE, T.OBJECT_STATUS = 'A' "
            + "WHEN NOT MATCHED THEN INSERT (PARTY_ID, USER_ID, CREDENTIAL_STATUS, "
            + "CREDENTIAL_VERSION, SETUP_AT, LAST_RESET_AT, LAST_REQUEST_ID, "
            + "LAST_REFERENCE_NUMBER, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, "
            + "LAST_UPDATE_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER) "
            + "VALUES (?, ?, 'ACTIVE', 1, SYSDATE, CASE WHEN ? = 'RESET' THEN SYSDATE END, "
            + "?, ?, ?, SYSDATE, ?, SYSDATE, 'A', 1)");
    int i = 1;
    state.setParameter(i++, partyId);
    state.setParameter(i++, userId);
    state.setParameter(i++, operation);
    state.setParameter(i++, requestId);
    state.setParameter(i++, referenceNumber);
    state.setParameter(i++, userId);
    state.setParameter(i++, partyId);
    state.setParameter(i++, userId);
    state.setParameter(i++, operation);
    state.setParameter(i++, requestId);
    state.setParameter(i++, referenceNumber);
    state.setParameter(i++, userId);
    state.setParameter(i++, userId);
    return state.executeUpdate();
  }
}
