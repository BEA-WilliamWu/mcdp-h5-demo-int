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
              + "WHERE C.PARTY_ID = ? AND C.USER_NAME IN (?, ?) AND C.OBJECT_STATUS = ? "
              + "ORDER BY C.CREATION_DATE DESC, C.ID DESC",
          (String) null, HthApiPasswordCode.class);
      String suffix = "@" + partyId;
      String owner = userName.endsWith(suffix)
          ? userName.substring(0, userName.length() - suffix.length()) : userName;
      query.setParameter(1, partyId);
      query.setParameter(2, owner);
      query.setParameter(3, owner + suffix);
      query.setParameter(4, ACTIVE);
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
  @Override
  public List findUsable(Session session, String partyId, String userId, String purpose) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT ID FROM HTH_BEA.HTH_API_PASSWORD_CODE "
            + "WHERE PARTY_ID = ? AND USER_NAME IN (?, ?) AND PURPOSE = ? "
            + "AND STATUS = 'ACTIVE' AND OBJECT_STATUS = 'A' AND EXPIRY_TIME > SYSTIMESTAMP "
            + "AND ATTEMPT_COUNT < MAX_ATTEMPTS ORDER BY CREATION_DATE DESC");
    query.setParameter(1, partyId);
    query.setParameter(2, userId);
    query.setParameter(3, userId + "@" + partyId);
    query.setParameter(4, purpose);
    query.setMaxResults(1);
    return query.list();
  }

  @Override
  public List findUsableCipher(Session session, String partyId, String userId, String purpose) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT ID, CODE_CIPHER FROM HTH_BEA.HTH_API_PASSWORD_CODE "
            + "WHERE PARTY_ID = ? AND USER_NAME IN (?, ?) AND PURPOSE = ? "
            + "AND STATUS = 'ACTIVE' AND OBJECT_STATUS = 'A' AND EXPIRY_TIME > SYSTIMESTAMP "
            + "AND ATTEMPT_COUNT < MAX_ATTEMPTS ORDER BY CREATION_DATE DESC");
    query.setParameter(1, partyId);
    query.setParameter(2, userId);
    query.setParameter(3, userId + "@" + partyId);
    query.setParameter(4, purpose);
    query.setMaxResults(1);
    return query.list();
  }

  @Override
  public List findLatestExpiredCipher(Session session, String partyId, String userId, String purpose) throws Exception {
    Query query = session.createSQLQuery(
        "SELECT ID, CODE_CIPHER, CASE WHEN STATUS IN ('ACTIVE', 'EXPIRED') AND EXPIRY_TIME <= SYSTIMESTAMP "
            + "AND ATTEMPT_COUNT < MAX_ATTEMPTS THEN 1 ELSE 0 END "
            + "FROM HTH_BEA.HTH_API_PASSWORD_CODE "
            + "WHERE PARTY_ID = ? AND USER_NAME IN (?, ?) AND PURPOSE = ? AND OBJECT_STATUS = 'A' "
            + "ORDER BY CREATION_DATE DESC, ID DESC");
    query.setParameter(1, partyId);
    query.setParameter(2, userId);
    query.setParameter(3, userId + "@" + partyId);
    query.setParameter(4, purpose);
    query.setMaxResults(1);
    List rows = query.list();
    return rows != null && !rows.isEmpty() && ((Number) ((Object[]) rows.get(0))[2]).intValue() == 1
        ? rows : java.util.Collections.emptyList();
  }

  @Override
  public int recordFailedAttempt(Session session, String codeId) throws Exception {
    Query failed = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET ATTEMPT_COUNT = ATTEMPT_COUNT + 1, "
            + "STATUS = CASE WHEN ATTEMPT_COUNT + 1 >= MAX_ATTEMPTS THEN 'INVALID' "
            + "ELSE STATUS END, LAST_UPDATE_DATE = SYSDATE "
            + "WHERE ID = ? AND STATUS IN ('ACTIVE', 'EXPIRED') AND OBJECT_STATUS = 'A' "
            + "AND ATTEMPT_COUNT < MAX_ATTEMPTS");
    failed.setParameter(1, codeId);
    return failed.executeUpdate();
  }

  @Override
  public int reserve(Session session, String requestId, String codeId) throws Exception {
    Query reserve = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'IN_PROGRESS', REQUEST_ID = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE ID = ? AND STATUS = 'ACTIVE' "
            + "AND EXPIRY_TIME > SYSTIMESTAMP AND OBJECT_STATUS = 'A' "
            + "AND ATTEMPT_COUNT < MAX_ATTEMPTS");
    reserve.setParameter(1, requestId);
    reserve.setParameter(2, codeId);
    return reserve.executeUpdate();
  }

  @Override
  public int consume(Session session, String userId, String codeId, String requestId) throws Exception {
    Query code = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'USED', USED_TIME = SYSTIMESTAMP, "
            + "LAST_UPDATED_BY = ?, LAST_UPDATE_DATE = SYSDATE WHERE ID = ? "
            + "AND STATUS = 'IN_PROGRESS' AND REQUEST_ID = ?");
    code.setParameter(1, userId);
    code.setParameter(2, codeId);
    code.setParameter(3, requestId);
    return code.executeUpdate();
  }

  @Override
  public int failReservation(Session session, String userId, String requestId, String codeId, boolean uncertain) throws Exception {
    Query code = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = ?, LAST_UPDATED_BY = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE ID = ? AND STATUS = 'IN_PROGRESS' "
            + "AND REQUEST_ID = ?");
    code.setParameter(1, uncertain ? "UNKNOWN" : "ACTIVE");
    code.setParameter(2, userId);
    code.setParameter(3, codeId);
    code.setParameter(4, requestId);
    return code.executeUpdate();
  }

  @Override
  public int retirePending(Session session, String partyId, String userName, String purpose, String operator) throws Exception {
    Query query = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET OBJECT_STATUS = 'I', LAST_UPDATED_BY = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE PARTY_ID = ? AND USER_NAME IN (?, ?) "
            + "AND PURPOSE = ? AND STATUS = 'PENDING' AND OBJECT_STATUS = 'A'");
    query.setParameter(1, operator);
    query.setParameter(2, partyId);
    query.setParameter(3, userName);
    query.setParameter(4, userName + "@" + partyId);
    query.setParameter(5, purpose);
    return query.executeUpdate();
  }

  @Override
  public List lockForApproval(Session session, String codeId) throws Exception {
    Query lock = session.createSQLQuery(
        "SELECT STATUS, OBJECT_STATUS FROM HTH_BEA.HTH_API_PASSWORD_CODE WHERE ID = ? FOR UPDATE");
    lock.setParameter(1, codeId);
    return lock.list();
  }

  @Override
  public int retireActive(Session session, String operator, String partyId, String userName, String purpose) throws Exception {
    Query retire = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'INVALID', LAST_UPDATED_BY = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE PARTY_ID = ? AND USER_NAME IN (?, ?) "
            + "AND PURPOSE = ? AND STATUS = 'ACTIVE' AND OBJECT_STATUS = 'A'");
    retire.setParameter(1, operator);
    retire.setParameter(2, partyId);
    String suffix = "@" + partyId;
    String bareName = userName.endsWith(suffix)
        ? userName.substring(0, userName.length() - suffix.length()) : userName;
    retire.setParameter(3, bareName);
    retire.setParameter(4, bareName + suffix);
    retire.setParameter(5, purpose);
    return retire.executeUpdate();
  }

  @Override
  public int activate(Session session, String transactionId, int expiryHours, String operator, String codeId) throws Exception {
    Query activate = session.createSQLQuery(
        "UPDATE HTH_BEA.HTH_API_PASSWORD_CODE SET STATUS = 'ACTIVE', TRANSACTION_ID = ?, "
            + "EXPIRY_TIME = SYSTIMESTAMP + NUMTODSINTERVAL(?, 'HOUR'), LAST_UPDATED_BY = ?, "
            + "LAST_UPDATE_DATE = SYSDATE WHERE ID = ? AND STATUS = 'PENDING' "
            + "AND OBJECT_STATUS = 'A'");
    activate.setParameter(1, transactionId);
    activate.setParameter(2, expiryHours);
    activate.setParameter(3, operator);
    activate.setParameter(4, codeId);
    return activate.executeUpdate();
  }
}
