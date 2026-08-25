package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessSummaryRecord;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local ORM/SQL adapter for effective HTH account grants. */
public class LocalHthUserAccessAccountRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthUserAccessAccount>
    implements IHthUserAccessAccountRepositoryAdapter {
  private static final String ACTIVE = "A";

  private LocalHthUserAccessAccountRepositoryAdapter() {
  }

  public static LocalHthUserAccessAccountRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public void create(HthUserAccessAccount object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthUserAccessAccount object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void delete(HthUserAccessAccount object) throws Exception {
    try {
      super.delete(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public HthUserAccessAccount read(HthUserAccessAccountKey key) throws Exception {
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
      return super.get(HthUserAccessAccount.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public List<HthUserAccessAccount> listByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception {
    return list(partyId, closeId, accessPartyId, linkageType, null);
  }

  @Override
  public HthUserAccessAccount findByContextAndAccountNumber(String partyId, String closeId,
      String accessPartyId, String linkageType, String accountNumber) throws Exception {
    List<HthUserAccessAccount> rows = list(partyId, closeId, accessPartyId,
        linkageType, accountNumber);
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public boolean hasActiveByContext(String partyId, String closeId, String accessPartyId,
      String linkageType) throws Exception {
    List<HthUserAccessAccount> rows = listByContext(partyId, closeId, accessPartyId, linkageType);
    if (rows != null) {
      for (HthUserAccessAccount row : rows) {
        if (row != null && ACTIVE.equals(row.getObjectStatus())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public List<HthUserAccessSummaryRecord> listActiveSummary(String partyId, String closeId)
      throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        isSessionOpen = true;
      }
      // Aggregate in the database so BCOH2H-538 does not load or expose unmasked account rows.
      Query query = session.createSQLQuery(
          "SELECT A.ACCESS_PARTY_ID, A.LINKAGE_TYPE, A.ACCOUNT_TYPE, COUNT(A.ID) "
              + "FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT A "
              + "WHERE A.PARTY_ID = ? AND A.CLOSE_ID = ? AND A.OBJECT_STATUS = ? "
              + "GROUP BY A.ACCESS_PARTY_ID, A.LINKAGE_TYPE, A.ACCOUNT_TYPE");
      query.setParameter(1, partyId);
      query.setParameter(2, closeId);
      query.setParameter(3, ACTIVE);

      List rows = query.list();
      Map<String, HthUserAccessSummaryRecord> records =
          new LinkedHashMap<String, HthUserAccessSummaryRecord>();
      if (rows != null) {
        for (Object rowValue : rows) {
          Object[] row = (Object[]) rowValue;
          String accessPartyId = stringValue(row[0]);
          String linkageType = stringValue(row[1]);
          String contextKey = linkageType + "#" + accessPartyId;
          HthUserAccessSummaryRecord record = records.get(contextKey);
          if (record == null) {
            record = new HthUserAccessSummaryRecord();
            record.setAccessPartyId(accessPartyId);
            record.setLinkageType(linkageType);
            records.put(contextKey, record);
          }
          String accountType = stringValue(row[2]);
          if (accountType != null) {
            record.getAccountCountByType().put(accountType, integerValue(row[3]));
          }
        }
      }
      return new ArrayList<HthUserAccessSummaryRecord>(records.values());
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public boolean isAuthorized(String partyId, String closeId, String accountNumber,
      String apiCode) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        isSessionOpen = true;
      }
      // Keep the runtime check in one query so every part of the authorization chain is evaluated
      // against the same database view: effective user/account, account/API, API master, and the
      // enterprise HTH API assignment must all remain active.
      Query query = session.createSQLQuery(
          "SELECT A.ID FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT A "
              + "JOIN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API G "
              + "ON G.HTH_USER_ACCESS_ACCOUNT_ID = A.ID AND G.OBJECT_STATUS = ? "
              + "JOIN HTH_BEA.HTH_API_MASTER M "
              + "ON M.ID = G.API_MASTER_ID AND M.OBJECT_STATUS = ? "
              + "JOIN HTH_BEA.HTH_MANAGEMENT HM "
              + "ON HM.PARTY_ID = A.PARTY_ID AND HM.OBJECT_STATUS = ? AND HM.HTH_STATUS = ? "
              + "JOIN HTH_BEA.HTH_MANAGEMENT_API HMA "
              + "ON HMA.HTH_MANAGEMENT_ID = HM.ID AND HMA.API_MASTER_ID = M.ID "
              + "AND HMA.OBJECT_STATUS = ? "
              + "WHERE A.PARTY_ID = ? AND A.CLOSE_ID = ? AND A.OBJECT_STATUS = ? "
              + "AND A.ACCOUNT_NUMBER = ? AND M.API_CODE = ?");
      query.setParameter(1, ACTIVE);
      query.setParameter(2, ACTIVE);
      query.setParameter(3, ACTIVE);
      query.setParameter(4, "ENABLE");
      query.setParameter(5, ACTIVE);
      query.setParameter(6, partyId);
      query.setParameter(7, closeId);
      query.setParameter(8, ACTIVE);
      query.setParameter(9, accountNumber);
      query.setParameter(10, apiCode);
      query.setMaxResults(1);
      List rows = query.list();
      return rows != null && !rows.isEmpty();
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private List<HthUserAccessAccount> list(String partyId, String closeId,
      String accessPartyId, String linkageType, String accountNumber) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      Criteria criteria = session.createCriteria(HthUserAccessAccount.class);
      criteria.add(Expression.eq("partyId", partyId));
      criteria.add(Expression.eq("closeId", closeId));
      criteria.add(Expression.eq("accessPartyId", accessPartyId));
      criteria.add(Expression.eq("linkageType", linkageType));
      if (accountNumber != null) {
        criteria.add(Expression.eq("accountNumber", accountNumber));
      }
      return super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Integer integerValue(Object value) {
    return value instanceof Number ? Integer.valueOf(((Number) value).intValue())
        : Integer.valueOf(0);
  }

  private static final class SingletonHolder {
    private static final LocalHthUserAccessAccountRepositoryAdapter INSTANCE =
        new LocalHthUserAccessAccountRepositoryAdapter();
  }
}
