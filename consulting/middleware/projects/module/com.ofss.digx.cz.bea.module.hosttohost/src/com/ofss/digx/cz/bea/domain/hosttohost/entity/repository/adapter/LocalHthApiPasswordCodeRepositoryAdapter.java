package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCodeKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

/** Local ORM/SQL adapter for one-time HTH API Password Code lifecycle rows. */
public class LocalHthApiPasswordCodeRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthApiPasswordCode>
    implements IHthApiPasswordCodeRepositoryAdapter {
  private static final String ACTIVE = "A";

  private static final String STATUS_PENDING = "PENDING";

  private static final String STATUS_ACTIVE = "ACTIVE";

  private LocalHthApiPasswordCodeRepositoryAdapter() {
  }

  public static LocalHthApiPasswordCodeRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public HthApiPasswordCode read(HthApiPasswordCodeKey key) throws Exception {
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
      return super.get(HthApiPasswordCode.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public void create(HthApiPasswordCode object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthApiPasswordCode object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public HthApiPasswordCode findActiveByTransactionId(String transactionId) throws Exception {
    if (transactionId == null) {
      return null;
    }
    return firstByCriteria(transactionId, null, null, STATUS_ANY);
  }

  @Override
  public List<HthApiPasswordCode> listPendingByOwner(String partyId, String userName)
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
      Criteria criteria = session.createCriteria(HthApiPasswordCode.class);
      criteria.add(Expression.eq("partyId", partyId));
      criteria.add(Expression.eq("userName", userName));
      criteria.add(Expression.eq("status", STATUS_PENDING));
      criteria.add(Expression.eq("objectStatus", ACTIVE));
      return super.executeCriteria(criteria);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public HthApiPasswordCode findActiveByOwner(String partyId, String userName) throws Exception {
    return firstByCriteria(null, partyId, userName, STATUS_ACTIVE);
  }

  @Override
  public List<HthApiPasswordCode> listActiveByOwner(String partyId, String userName)
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
      Criteria criteria = session.createCriteria(HthApiPasswordCode.class);
      criteria.add(Expression.eq("partyId", partyId));
      criteria.add(Expression.eq("userName", userName));
      criteria.add(Expression.eq("status", STATUS_ACTIVE));
      criteria.add(Expression.eq("objectStatus", ACTIVE));
      return super.executeCriteria(criteria);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public HthApiPasswordCode findLatestByOwner(String partyId, String userName) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      Query query = session.createSQLQuery(
          "SELECT C.* FROM HTH_BEA.HTH_API_PASSWORD_CODE C "
              + "WHERE C.PARTY_ID = ? AND C.USER_NAME = ? AND C.OBJECT_STATUS = ? "
              + "ORDER BY C.CREATION_DATE DESC",
          (String) null, HthApiPasswordCode.class);
      query.setParameter(1, partyId);
      query.setParameter(2, userName);
      query.setParameter(3, ACTIVE);
      query.setMaxResults(1);
      List<HthApiPasswordCode> rows = query.list();
      return rows == null || rows.isEmpty() ? null : rows.get(0);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private static final String STATUS_ANY = null;

  private HthApiPasswordCode firstByCriteria(String transactionId, String partyId,
      String userName, String status) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      Criteria criteria = session.createCriteria(HthApiPasswordCode.class);
      if (transactionId != null) {
        criteria.add(Expression.eq("transactionId", transactionId));
      }
      if (partyId != null) {
        criteria.add(Expression.eq("partyId", partyId));
      }
      if (userName != null) {
        criteria.add(Expression.eq("userName", userName));
      }
      if (status != null) {
        criteria.add(Expression.eq("status", status));
      }
      criteria.add(Expression.eq("objectStatus", ACTIVE));
      List<HthApiPasswordCode> rows = super.executeCriteria(criteria);
      return rows == null || rows.isEmpty() ? null : rows.get(0);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthApiPasswordCodeRepositoryAdapter INSTANCE =
        new LocalHthApiPasswordCodeRepositoryAdapter();
  }
}
