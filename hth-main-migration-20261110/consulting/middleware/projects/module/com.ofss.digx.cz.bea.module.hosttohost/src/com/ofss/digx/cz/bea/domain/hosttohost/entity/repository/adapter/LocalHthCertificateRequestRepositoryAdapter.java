package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequestKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.OrderBy;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthCertificateRequestRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthCertificateRequest>
    implements IHthCertificateRequestRepositoryAdapter {
  private static final String OBJECT_STATUS_ACTIVE = "A";
  private static final String PENDING_APPROVAL_STATUS = "PENDING_APPROVAL";
  private static final String MODIFICATION_REQUESTED_STATUS = "MODIFICATION_REQUESTED";

  private LocalHthCertificateRequestRepositoryAdapter() {
  }

  public static LocalHthCertificateRequestRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  private static final class SingletonHolder {
    private static final LocalHthCertificateRequestRepositoryAdapter INSTANCE =
        new LocalHthCertificateRequestRepositoryAdapter();
  }

  @Override
  public HthCertificateRequest read(HthCertificateRequestKey key) throws Exception {
    if (key == null || key.getId() == null || key.getId().isEmpty()) {
      return null;
    }

    Session session = null;
    boolean isSessionOpen = false;
    HthCertificateRequest data = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      data = super.get(HthCertificateRequest.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return data;
  }

  @Override
  public HthCertificateRequest findActiveByTransactionId(String transactionId) throws Exception {
    List<HthCertificateRequest> requestList = listActiveByTransactionId(transactionId);
    return requestList == null || requestList.isEmpty() ? null : requestList.get(0);
  }

  @Override
  public List<HthCertificateRequest> listActiveByTransactionId(String transactionId)
      throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    List<HthCertificateRequest> requestList = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }

      Criteria criteria = session.createCriteria(HthCertificateRequest.class);
      criteria.add(Expression.eq("transactionId", transactionId));
      criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
      criteria.addOrder(OrderBy.asc("seqNo"));
      requestList = super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return requestList;
  }

  @Override
  public boolean existsPendingApproval(String partyId) throws Exception {
    if (partyId == null || partyId.trim().isEmpty()) {
      return false;
    }

    Session session = null;
    boolean isSessionOpen = false;
    List results = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }

      Query query = session.createSQLQuery(
          "SELECT R.ID FROM HTH_BEA.HTH_CERTIFICATE_REQUEST R "
              + "JOIN DIGX_AP_TRANSACTION T ON T.TXN_ID = R.TRANSACTION_ID "
              + "WHERE R.OBJECT_STATUS = ? "
              + "AND R.PARTY_ID = ? "
              + "AND T.APPR_STATUS IN (?, ?)");
      query.setParameter(1, OBJECT_STATUS_ACTIVE);
      query.setParameter(2, partyId);
      query.setParameter(3, PENDING_APPROVAL_STATUS);
      query.setParameter(4, MODIFICATION_REQUESTED_STATUS);
      query.setMaxResults(1);
      results = query.list();
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return results != null && !results.isEmpty();
  }

  @Override
  public void create(HthCertificateRequest object) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    boolean success = false;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        session.beginTransaction();
        isSessionOpen = true;
      }
      super.create(object);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      finishWriteTransaction(session, isSessionOpen, success);
    }
  }

  @Override
  public void update(HthCertificateRequest object) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    boolean success = false;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        session.beginTransaction();
        isSessionOpen = true;
      }
      super.update(object);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      finishWriteTransaction(session, isSessionOpen, success);
    }
  }

  @Override
  public void delete(HthCertificateRequest object) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    boolean success = false;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession("DIGX");
        session.beginTransaction();
        isSessionOpen = true;
      }
      super.delete(object);
      success = true;
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    } finally {
      finishWriteTransaction(session, isSessionOpen, success);
    }
  }

  private void finishWriteTransaction(Session session, boolean isSessionOpen, boolean success)
      throws Exception {
    if (!isSessionOpen || session == null) {
      return;
    }
    try {
      if (success) {
        session.fetchCurrentTransaction().commit();
      } else {
        DataAccessManager.getManager().rollbackTransaction();
      }
    } finally {
      DataAccessManager.getManager().closeSession(session);
    }
  }
}
