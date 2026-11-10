package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificate;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.OrderBy;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthCertificateRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthCertificate>
    implements IHthCertificateRepositoryAdapter {
  private static final String OBJECT_STATUS_ACTIVE = "A";

  private LocalHthCertificateRepositoryAdapter() {
  }

  public static LocalHthCertificateRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  private static final class SingletonHolder {
    private static final LocalHthCertificateRepositoryAdapter INSTANCE =
        new LocalHthCertificateRepositoryAdapter();
  }

  @Override
  public HthCertificate read(HthCertificateKey key) throws Exception {
    if (key == null || key.getId() == null || key.getId().isEmpty()) {
      return null;
    }

    Session session = null;
    boolean isSessionOpen = false;
    HthCertificate data = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      data = super.get(HthCertificate.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return data;
  }

  @Override
  public List<HthCertificate> listActiveByPartyId(String partyId) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    List<HthCertificate> certificateList = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }

      Criteria criteria = session.createCriteria(HthCertificate.class);
      criteria.add(Expression.eq("partyId", partyId));
      criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
      criteria.addOrder(OrderBy.asc("seqNo"));
      certificateList = super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return certificateList;
  }

  @Override
  public List<HthCertificate> listActive() throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    List<HthCertificate> certificateList = null;

    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }

      Criteria criteria = session.createCriteria(HthCertificate.class);
      criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
      criteria.addOrder(OrderBy.asc("partyId"));
      criteria.addOrder(OrderBy.asc("seqNo"));
      certificateList = super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }

    return certificateList;
  }

  @Override
  public void create(HthCertificate object) throws Exception {
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
  public void update(HthCertificate object) throws Exception {
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
  public void delete(HthCertificate object) throws Exception {
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
