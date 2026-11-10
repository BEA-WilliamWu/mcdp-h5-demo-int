package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApiKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

/** Local ORM adapter for effective API grants attached to HTH account grants. */
public class LocalHthUserAccessAccountApiRepositoryAdapter
    extends AbstractLocalRepositoryAdapter<HthUserAccessAccountApi>
    implements IHthUserAccessAccountApiRepositoryAdapter {

  private LocalHthUserAccessAccountApiRepositoryAdapter() {
  }

  public static LocalHthUserAccessAccountApiRepositoryAdapter getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public void create(HthUserAccessAccountApi object) throws Exception {
    try {
      super.create(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void update(HthUserAccessAccountApi object) throws Exception {
    try {
      super.update(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public void delete(HthUserAccessAccountApi object) throws Exception {
    try {
      super.delete(object);
    } catch (java.lang.Exception e) {
      throw new Exception(e);
    }
  }

  @Override
  public HthUserAccessAccountApi read(HthUserAccessAccountApiKey key) throws Exception {
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
      return super.get(HthUserAccessAccountApi.class, key);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  @Override
  public List<HthUserAccessAccountApi> listByAccountId(String accountId) throws Exception {
    return list(accountId, null);
  }

  @Override
  public HthUserAccessAccountApi findByAccountIdAndApiMasterId(String accountId,
      String apiMasterId) throws Exception {
    List<HthUserAccessAccountApi> rows = list(accountId, apiMasterId);
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  private List<HthUserAccessAccountApi> list(String accountId, String apiMasterId)
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
      Criteria criteria = session.createCriteria(HthUserAccessAccountApi.class);
      criteria.add(Expression.eq("hthUserAccessAccountId", accountId));
      if (apiMasterId != null) {
        criteria.add(Expression.eq("apiMasterId", apiMasterId));
      }
      return super.executeCriteria(criteria);
    } finally {
      if (isSessionOpen) {
        DataAccessManager.getManager().closeSession(session);
      }
    }
  }

  private static final class SingletonHolder {
    private static final LocalHthUserAccessAccountApiRepositoryAdapter INSTANCE =
        new LocalHthUserAccessAccountApiRepositoryAdapter();
  }
}
