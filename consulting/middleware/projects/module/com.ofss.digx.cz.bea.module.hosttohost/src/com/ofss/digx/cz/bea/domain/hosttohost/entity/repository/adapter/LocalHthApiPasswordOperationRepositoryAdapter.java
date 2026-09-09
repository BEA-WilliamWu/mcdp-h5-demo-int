package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperation;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordOperationKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;

/** Local ORM access and conditional SQL; supplied sessions are never committed or closed here. */
public class LocalHthApiPasswordOperationRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthApiPasswordOperation>
    implements IHthApiPasswordOperationRepositoryAdapter {
  private LocalHthApiPasswordOperationRepositoryAdapter() { }

  public static LocalHthApiPasswordOperationRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public HthApiPasswordOperation read(HthApiPasswordOperationKey key) throws Exception {
    Session session = null;
    boolean owned = !DataAccessManager.getManager().isSessionOpen();
    try {
      if (owned) {
        session = DataAccessManager.getManager().openSession();
      }
      return super.get(HthApiPasswordOperation.class, key);
    } finally {
      if (owned && session != null) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public void create(HthApiPasswordOperation object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthApiPasswordOperation object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthApiPasswordOperationRepositoryAdapter INSTANCE = new LocalHthApiPasswordOperationRepositoryAdapter();
  }
  @Override
  public List findResult(Session session, String requestId, String partyId, String userId, String operation) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT STATUS, REFERENCE_NUMBER, STORAGE_BACKEND FROM HTH_BEA.HTH_API_PASSWORD_OPERATION "
            + "WHERE REQUEST_ID = ? AND PARTY_ID = ? AND USER_ID = ? AND OPERATION = ?");
    query.setParameter(1, requestId);
    query.setParameter(2, partyId);
    query.setParameter(3, userId);
    query.setParameter(4, operation);
    query.setMaxResults(1);
    return query.list();
  }

  @Override
  public int reserve(Session session, String requestId, String partyId, String userId, String purpose, String codeId, String backend) throws Exception {
    Query operation = session.createSQLQuery(
        "INSERT INTO HTH_BEA.HTH_API_PASSWORD_OPERATION "
            + "(REQUEST_ID, PARTY_ID, USER_ID, OPERATION, CODE_ID, STATUS, CREATED_BY, "
            + "CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, OBJECT_STATUS, "
            + "OBJECT_VERSION_NUMBER, STORAGE_BACKEND) VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, SYSDATE, ?, "
            + "SYSDATE, 'A', 1, ?)");
    operation.setParameter(1, requestId);
    operation.setParameter(2, partyId);
    operation.setParameter(3, userId);
    operation.setParameter(4, purpose);
    operation.setParameter(5, codeId);
    operation.setParameter(6, userId);
    operation.setParameter(7, userId);
    operation.setParameter(8, backend);
    return operation.executeUpdate();
  }

  @Override
  public int complete(Session session, String referenceNumber, String userId, String requestId) throws Exception {
    Query op = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_OPERATION SET STATUS = 'SUCCESS', "
            + "REFERENCE_NUMBER = ?, LAST_UPDATED_BY = ?, LAST_UPDATE_DATE = SYSDATE "
            + "WHERE REQUEST_ID = ? AND STATUS = 'IN_PROGRESS'");
    op.setParameter(1, referenceNumber);
    op.setParameter(2, userId);
    op.setParameter(3, requestId);
    return op.executeUpdate();
  }

  @Override
  public int failReservation(Session session, String userId, String requestId, boolean uncertain) throws Exception {
    Query op = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_OPERATION SET STATUS = ?, LAST_UPDATED_BY = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE REQUEST_ID = ? AND STATUS = 'IN_PROGRESS'");
    op.setParameter(1, uncertain ? "UNKNOWN" : "FAILED");
    op.setParameter(2, userId);
    op.setParameter(3, requestId);
    return op.executeUpdate();
  }
}
