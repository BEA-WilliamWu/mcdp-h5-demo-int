package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccountKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

/** Local ORM adapter for account selections stored in maker request snapshots. */
public class LocalHthUserAccessRequestAccountRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthUserAccessRequestAccount>
    implements IHthUserAccessRequestAccountRepositoryAdapter {

  private LocalHthUserAccessRequestAccountRepositoryAdapter() {
  }

  public static LocalHthUserAccessRequestAccountRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public void create(HthUserAccessRequestAccount object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthUserAccessRequestAccount object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void delete(HthUserAccessRequestAccount object) throws Exception {
    try {
      super.delete(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public HthUserAccessRequestAccount read(HthUserAccessRequestAccountKey key)
      throws Exception {
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
      return super.get(HthUserAccessRequestAccount.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public List<HthUserAccessRequestAccount> listByRequestId(String requestId) throws Exception {
    Session session = null;
    boolean isSessionOpen = false;
    try {
      if (DataAccessManager.getManager().isSessionOpen()) {
        session = DataAccessManager.getManager().fetchCurrentSession();
      } else {
        session = DataAccessManager.getManager().openSession();
        isSessionOpen = true;
      }
      Criteria criteria = session.createCriteria(HthUserAccessRequestAccount.class);
      criteria.add(Expression.eq("hthUserAccessRequestId", requestId));
      return super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthUserAccessRequestAccountRepositoryAdapter INSTANCE =
        new LocalHthUserAccessRequestAccountRepositoryAdapter();
  }
}
