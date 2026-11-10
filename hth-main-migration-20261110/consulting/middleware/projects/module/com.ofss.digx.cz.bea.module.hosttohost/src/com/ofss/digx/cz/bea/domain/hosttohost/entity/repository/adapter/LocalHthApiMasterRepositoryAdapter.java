package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMaster;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiMasterKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.OrderBy;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import java.util.Set;

public class LocalHthApiMasterRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthApiMaster>
		implements IHthApiMasterRepositoryAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";

	private LocalHthApiMasterRepositoryAdapter() {
	}

	public static LocalHthApiMasterRepositoryAdapter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		private static final LocalHthApiMasterRepositoryAdapter INSTANCE = new LocalHthApiMasterRepositoryAdapter();
	}

	@Override
	public HthApiMaster read(HthApiMasterKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthApiMaster data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthApiMaster.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public List<HthApiMaster> listActive() throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		List<HthApiMaster> apiMasterList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthApiMaster.class);
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			criteria.addOrder(OrderBy.asc("displayOrder"));
			criteria.addOrder(OrderBy.asc("apiName"));
			apiMasterList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return apiMasterList;
	}

	@Override
	public List<HthApiMaster> listActiveByApiCodes(Set<String> apiCodes) throws Exception {
		if (apiCodes == null || apiCodes.isEmpty()) {
			return java.util.Collections.emptyList();
		}

		Session session = null;
		boolean isSessionOpen = false;
		List<HthApiMaster> apiMasterList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthApiMaster.class);
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			criteria.add(Expression.in("apiCode", apiCodes));
			apiMasterList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return apiMasterList;
	}

	@Override
	public void create(HthApiMaster object) throws Exception {
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
	public void update(HthApiMaster object) throws Exception {
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
	public void delete(HthApiMaster object) throws Exception {
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
