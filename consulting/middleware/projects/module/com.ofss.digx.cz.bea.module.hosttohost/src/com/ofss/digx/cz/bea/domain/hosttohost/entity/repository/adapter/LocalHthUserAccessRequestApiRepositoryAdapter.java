package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApiKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

/** Local ORM/SQL adapter for API selections stored in maker request snapshots. */
public class LocalHthUserAccessRequestApiRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthUserAccessRequestApi>
    implements IHthUserAccessRequestApiRepositoryAdapter {

  private LocalHthUserAccessRequestApiRepositoryAdapter() {
  }

  public static LocalHthUserAccessRequestApiRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public void create(HthUserAccessRequestApi object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthUserAccessRequestApi object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void delete(HthUserAccessRequestApi object) throws Exception {
    try {
      super.delete(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public HthUserAccessRequestApi read(HthUserAccessRequestApiKey key) throws Exception {
    if (key == null || key.getId() == null) {
      return null;
    }
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      return super.get(HthUserAccessRequestApi.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public List<HthUserAccessRequestApi> listByRequestAccountId(String requestAccountId)
      throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      Criteria criteria = session.createCriteria(HthUserAccessRequestApi.class);
      criteria.add(Expression.eq("hthUserAccessRequestAccountId", requestAccountId));
      return super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public List<HthUserAccessRequestApi> listByRequestId(String requestId) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        isSessionOpen = true;
      }
      // Join through request accounts to rebuild the complete checker snapshot in one query.
      Query query = session.createSQLQuery(
          "SELECT G.* FROM HTH_BEA.HTH_USER_ACCESS_REQ_API G "
              + "JOIN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT ACC "
              + "ON ACC.ID = G.HTH_USER_ACCESS_REQ_ACC_ID "
              + "WHERE ACC.HTH_USER_ACCESS_REQUEST_ID = ?",
          (String) null, HthUserAccessRequestApi.class);
      query.setParameter(1, requestId);
      return query.list();
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthUserAccessRequestApiRepositoryAdapter INSTANCE =
        new LocalHthUserAccessRequestApiRepositoryAdapter();
  }
}
