package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthRequestRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthRequest>
		implements IHthRequestRepositoryAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";
	private static final String PENDING_APPROVAL_STATUS = "PENDING_APPROVAL";
	private static final String MODIFICATION_REQUESTED_STATUS = "MODIFICATION_REQUESTED";

	private LocalHthRequestRepositoryAdapter() {
	}

	public static LocalHthRequestRepositoryAdapter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		private static final LocalHthRequestRepositoryAdapter INSTANCE = new LocalHthRequestRepositoryAdapter();
	}

	@Override
	public HthRequest read(HthRequestKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthRequest data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthRequest.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public HthRequest findActiveByTransactionId(String transactionId) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		List<HthRequest> requestList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthRequest.class);
			criteria.add(Expression.eq("transactionId", transactionId));
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			requestList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return requestList == null || requestList.isEmpty() ? null : requestList.get(0);
	}

	@Override
	public boolean existsPendingApproval(String partyId) throws Exception {
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

			Query query = session.getNamedQuery("CheckHTHPendingApproval");
			query.setParameter("partyId", partyId);
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
	public boolean existsPendingApprovalByUamClientId(String uamClientId) throws Exception {
		if (uamClientId == null || uamClientId.trim().isEmpty()) {
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
					"SELECT R.ID FROM HTH_BEA.HTH_MANAGEMENT_REQUEST R "
							+ "JOIN DIGX_AP_TRANSACTION T ON T.TXN_ID = R.TRANSACTION_ID "
							+ "WHERE R.OBJECT_STATUS = ? "
							+ "AND R.UAM_CLIENT_ID = ? "
							+ "AND T.APPR_STATUS IN (?, ?)");
			query.setParameter(1, OBJECT_STATUS_ACTIVE);
			query.setParameter(2, uamClientId);
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
	public void create(HthRequest object) throws Exception {
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
	public void update(HthRequest object) throws Exception {
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
	public void delete(HthRequest object) throws Exception {
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
