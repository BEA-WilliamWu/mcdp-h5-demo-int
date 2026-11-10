package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestApiKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthRequestApiRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthRequestApi>
		implements IHthRequestApiRepositoryAdapter {

	private LocalHthRequestApiRepositoryAdapter() {
	}

	public static LocalHthRequestApiRepositoryAdapter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		private static final LocalHthRequestApiRepositoryAdapter INSTANCE = new LocalHthRequestApiRepositoryAdapter();
	}

	@Override
	public HthRequestApi read(HthRequestApiKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthRequestApi data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthRequestApi.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public List<HthRequestApi> listByRequestId(String requestId) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		List<HthRequestApi> requestApiList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthRequestApi.class);
			criteria.add(Expression.eq("hthRequestId", requestId));
			requestApiList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return requestApiList;
	}

	@Override
	public void create(HthRequestApi object) throws Exception {
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
	public void update(HthRequestApi object) throws Exception {
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
	public void delete(HthRequestApi object) throws Exception {
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
