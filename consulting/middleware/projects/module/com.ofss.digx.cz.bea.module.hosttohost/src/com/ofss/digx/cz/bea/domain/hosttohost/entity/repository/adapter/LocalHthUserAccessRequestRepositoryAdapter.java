package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessPendingRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local ORM/SQL adapter for maker request headers and pending approval projections. */
public class LocalHthUserAccessRequestRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthUserAccessRequest>
    implements IHthUserAccessRequestRepositoryAdapter {
  private static final String ACTIVE = "A";

  private static final String PENDING_APPROVAL = "PENDING_APPROVAL";

  private static final String MODIFICATION_REQUESTED = "MODIFICATION_REQUESTED";

  private LocalHthUserAccessRequestRepositoryAdapter() {
  }

  public static LocalHthUserAccessRequestRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public HthUserAccessRequest read(HthUserAccessRequestKey key) throws Exception {
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
      return super.get(HthUserAccessRequest.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public void create(HthUserAccessRequest object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthUserAccessRequest object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthUserAccessRequestRepositoryAdapter INSTANCE =
        new LocalHthUserAccessRequestRepositoryAdapter();
  }

  @Override
  public List<HthUserAccessPendingRecord> listPendingSummary(String partyId, String closeId)
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

      // Workflow status is owned by the approval framework, so pending state must be derived by
      // joining immutable request snapshots to the current transaction status rather than by a
      // status copied into the feature table.
      Query query = session.createSQLQuery(
          "SELECT R.ACCESS_PARTY_ID, R.LINKAGE_TYPE, R.ACTION_TYPE, R.REFERENCE_NO "
              + "FROM HTH_BEA.HTH_USER_ACCESS_REQUEST R "
              + "JOIN DIGX_AP_TRANSACTION T ON T.TXN_ID = R.TRANSACTION_ID "
              + "WHERE R.PARTY_ID = ? AND R.CLOSE_ID = ? AND R.OBJECT_STATUS = ? "
              + "AND T.APPR_STATUS IN (?, ?) ORDER BY R.CREATION_DATE DESC");
      query.setParameter(1, partyId);
      query.setParameter(2, closeId);
      query.setParameter(3, ACTIVE);
      query.setParameter(4, PENDING_APPROVAL);
      query.setParameter(5, MODIFICATION_REQUESTED);

      List rows = query.list();
      Map<String, HthUserAccessPendingRecord> records =
          new LinkedHashMap<String, HthUserAccessPendingRecord>();
      if (rows != null) {
        for (Object rowValue : rows) {
          Object[] row = (Object[]) rowValue;
          String accessPartyId = stringValue(row[0]);
          String linkageType = stringValue(row[1]);
          String contextKey = linkageType + "#" + accessPartyId;
          if (records.containsKey(contextKey)) {
            continue;
          }

          HthUserAccessPendingRecord record = new HthUserAccessPendingRecord();
          record.setAccessPartyId(accessPartyId);
          record.setLinkageType(linkageType);
          record.setActionType(stringValue(row[2]));
          record.setReferenceNumber(stringValue(row[3]));
          records.put(contextKey, record);
        }
      }
      return new ArrayList<HthUserAccessPendingRecord>(records.values());
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public HthUserAccessRequest findActiveByTransactionId(String transactionId) throws Exception {
    return findOne(transactionId, null, null, null, null, false);
  }

  @Override
  public HthUserAccessRequest findPendingByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception {
    return findOne(null, partyId, closeId, accessPartyId, linkageType, true);
  }

  private HthUserAccessRequest findOne(String transactionId, String partyId, String closeId,
      String accessPartyId, String linkageType, boolean pendingOnly) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      if (!pendingOnly) {
        Criteria criteria = session.createCriteria(HthUserAccessRequest.class);
        criteria.add(Expression.eq("transactionId", transactionId));
        criteria.add(Expression.eq("objectStatus", ACTIVE));
        List<HthUserAccessRequest> rows = super.executeCriteria(criteria);
        return rows == null || rows.isEmpty() ? null : rows.get(0);
      }

      // The four context fields form the logical maintenance boundary. Account numbers are not
      // included because a single request replaces the complete set for that context.
      Query query = session.createSQLQuery(
          "SELECT R.* FROM HTH_BEA.HTH_USER_ACCESS_REQUEST R "
              + "JOIN DIGX_AP_TRANSACTION T ON T.TXN_ID = R.TRANSACTION_ID "
              + "WHERE R.PARTY_ID = ? AND R.CLOSE_ID = ? AND R.ACCESS_PARTY_ID = ? "
              + "AND R.LINKAGE_TYPE = ? AND R.OBJECT_STATUS = ? "
              + "AND T.APPR_STATUS IN (?, ?) ORDER BY R.CREATION_DATE DESC",
          (String) null, HthUserAccessRequest.class);
      query.setParameter(1, partyId);
      query.setParameter(2, closeId);
      query.setParameter(3, accessPartyId);
      query.setParameter(4, linkageType);
      query.setParameter(5, ACTIVE);
      query.setParameter(6, PENDING_APPROVAL);
      query.setParameter(7, MODIFICATION_REQUESTED);
      query.setMaxResults(1);
      List<HthUserAccessRequest> rows = query.list();
      return rows == null || rows.isEmpty() ? null : rows.get(0);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
