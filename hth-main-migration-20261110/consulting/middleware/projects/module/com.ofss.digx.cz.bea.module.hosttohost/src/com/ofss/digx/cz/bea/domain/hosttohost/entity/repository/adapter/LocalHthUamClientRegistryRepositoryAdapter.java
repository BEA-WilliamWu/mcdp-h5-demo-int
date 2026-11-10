package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistry;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUamClientRegistryKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class LocalHthUamClientRegistryRepositoryAdapter
		extends AbstractLocalRepositoryAdapter<HthUamClientRegistry>
		implements IHthUamClientRegistryRepositoryAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";
	private static final String REGISTRY_STATUS_ACTIVE = "ACTIVE";
	private static final String REGISTRY_STATUS_RETIRED = "RETIRED";

	private LocalHthUamClientRegistryRepositoryAdapter() {
	}

	public static LocalHthUamClientRegistryRepositoryAdapter getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {
		private static final LocalHthUamClientRegistryRepositoryAdapter INSTANCE =
				new LocalHthUamClientRegistryRepositoryAdapter();
	}

	@Override
	public HthUamClientRegistry read(HthUamClientRegistryKey key) throws Exception {
		if (key == null || key.getId() == null || key.getId().isEmpty()) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		HthUamClientRegistry data = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}
			data = super.get(HthUamClientRegistry.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public boolean existsRegisteredUamClientId(String uamClientId) throws Exception {
		return findActiveByUamClientId(uamClientId) != null;
	}

	@Override
	public void registerUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		if (isBlank(uamClientId)) {
			return;
		}
		HthUamClientRegistry registry = buildRegistry(id, partyId, uamClientId, REGISTRY_STATUS_ACTIVE, userId);
		registry.setEnableTransactionId(transactionId);
		create(registry);
	}

	@Override
	public void retireUamClientId(String id, String partyId, String uamClientId, String transactionId, String userId)
			throws Exception {
		if (isBlank(uamClientId)) {
			return;
		}

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

			HthUamClientRegistry registry = findActiveByUamClientId(uamClientId);
			if (registry == null) {
				registry = buildRegistry(id, partyId, uamClientId, REGISTRY_STATUS_RETIRED, userId);
				registry.setDisableTransactionId(transactionId);
				super.create(registry);
			} else {
				registry.setPartyId(partyId);
				registry.setStatus(REGISTRY_STATUS_RETIRED);
				registry.setDisableTransactionId(transactionId);
				registry.setLastUpdatedBy(userId);
				super.update(registry);
			}
			success = true;
		} catch (java.lang.Exception e) {
			throw new Exception(e);
		} finally {
			finishWriteTransaction(session, isSessionOpen, success);
		}
	}

	@Override
	public void create(HthUamClientRegistry object) throws Exception {
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
	public void update(HthUamClientRegistry object) throws Exception {
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
	public void delete(HthUamClientRegistry object) throws Exception {
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

	private HthUamClientRegistry findActiveByUamClientId(String uamClientId) throws Exception {
		if (isBlank(uamClientId)) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;
		List<HthUamClientRegistry> registryList = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthUamClientRegistry.class);
			criteria.add(Expression.eq("uamClientId", uamClientId));
			criteria.add(Expression.eq("objectStatus", OBJECT_STATUS_ACTIVE));
			registryList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return registryList == null || registryList.isEmpty() ? null : registryList.get(0);
	}

	private HthUamClientRegistry buildRegistry(String id, String partyId, String uamClientId, String status,
			String userId) {
		HthUamClientRegistry registry = new HthUamClientRegistry();
		HthUamClientRegistryKey key = new HthUamClientRegistryKey();
		key.setId(id);
		registry.setKey(key);
		registry.setPartyId(partyId);
		registry.setUamClientId(uamClientId);
		registry.setStatus(status);
		registry.setObjectStatus(OBJECT_STATUS_ACTIVE);
		registry.setCreatedBy(userId);
		registry.setLastUpdatedBy(userId);
		return registry;
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

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
