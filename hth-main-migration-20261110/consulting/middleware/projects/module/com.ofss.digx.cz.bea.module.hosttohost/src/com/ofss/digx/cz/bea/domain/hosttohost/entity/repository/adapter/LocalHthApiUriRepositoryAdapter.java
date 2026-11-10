package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUri;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiUriKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.OrderBy;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthApiUriRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthApiUri>
		implements IHthApiUriRepositoryAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";

	private LocalHthApiUriRepositoryAdapter() {
	}

	public static LocalHthApiUriRepositoryAdapter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		private static final LocalHthApiUriRepositoryAdapter INSTANCE = new LocalHthApiUriRepositoryAdapter();
	}

	@Override
	public HthApiUri read(HthApiUriKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthApiUri data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthApiUri.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public List<HthApiUri> listActiveByApiMasterId(String apiMasterId) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		List<HthApiUri> apiUriList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthApiUri.class);
			criteria.add(Expression.eq("apiMasterId", apiMasterId));
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			criteria.addOrder(OrderBy.asc("displayOrder"));
			apiUriList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return apiUriList;
	}

	@Override
	public void create(HthApiUri object) throws Exception {
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
	public void update(HthApiUri object) throws Exception {
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
	public void delete(HthApiUri object) throws Exception {
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

	private void finishWriteTransaction(Session session, boolean isSessionOpen, boolean success) throws Exception {
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
