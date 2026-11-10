package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagement;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthManagementKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthManagementRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthManagement>
		implements IHthManagementRepositoryAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";

    private static LocalHthManagementRepositoryAdapter singletonInstance;

	private LocalHthManagementRepositoryAdapter() {
	}

	public static LocalHthManagementRepositoryAdapter getInstance() {
        if (singletonInstance == null) {
            synchronized (LocalHthManagementRepositoryAdapter.class) {
                if (singletonInstance == null) {
                    singletonInstance = new LocalHthManagementRepositoryAdapter();
                }
            }
        }
        return singletonInstance;
	}

	private static final class SingletonHolder {
		private static final LocalHthManagementRepositoryAdapter INSTANCE = new LocalHthManagementRepositoryAdapter();
	}




	@Override
	public HthManagement read(HthManagementKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthManagement data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthManagement.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public HthManagement findActiveByPartyId(String partyId) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		List<HthManagement> managementList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthManagement.class);
			criteria.add(Expression.eq("partyId", partyId));
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			managementList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return managementList == null || managementList.isEmpty() ? null : managementList.get(0);
	}

	@Override
	public HthManagement findActiveByUamClientId(String uamClientId) throws Exception {
		if (uamClientId == null || uamClientId.trim().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		List<HthManagement> managementList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthManagement.class);
			criteria.add(Expression.eq("uamClientId", uamClientId));
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			managementList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return managementList == null || managementList.isEmpty() ? null : managementList.get(0);
	}

	@Override
	public void create(HthManagement object) throws Exception {
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
	public void update(HthManagement object) throws Exception {
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
	public void delete(HthManagement object) throws Exception {
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
