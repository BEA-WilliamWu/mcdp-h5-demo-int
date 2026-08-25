package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfile;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfileKey;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;

public class LocalHthUserProfileRepositoryAdapter extends AbstractLocalRepositoryAdapter<HthUserProfile>
		implements IHthUserProfileRepositoryAdapter, IHthUserProfileAdapter {
	private static final String OBJECT_STATUS_ACTIVE = "A";

	private static LocalHthUserProfileRepositoryAdapter singletonInstance;

	private LocalHthUserProfileRepositoryAdapter() {
	}

	public static LocalHthUserProfileRepositoryAdapter getInstance() {
		if (singletonInstance == null) {
			synchronized (LocalHthUserProfileRepositoryAdapter.class) {
				if (singletonInstance == null) {
					singletonInstance = new LocalHthUserProfileRepositoryAdapter();
				}
			}
		}
		return singletonInstance;
	}

	@Override
	public HthUserProfile read(HthUserProfileKey key) throws Exception {
		if (key == null) {
			return null;
		}

		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");
				isSessionOpen = true;
			}
			return super.get(HthUserProfile.class, key);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public void create(HthUserProfile object) throws Exception {
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
			if (isSessionOpen) {
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
	}

	@Override
	public void createUserProfile(String partyId, String closeId) throws Exception {
		HthUserProfileKey key = new HthUserProfileKey();
		key.setPartyId(partyId);
		key.setCloseId(closeId);

		HthUserProfile userProfile = new HthUserProfile();
		userProfile.setKey(key);
		create(userProfile);
	}

	@Override
	public List<HthUserProfile> listByPartyId(String partyId) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(HthUserProfile.class);
			if (partyId != null && !partyId.trim().isEmpty()) {
				criteria.add(Expression.eq("key.partyId", partyId));
			}
			return super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public Map<String, String> listCloseIdsByUserKey(String partyId) throws Exception {
		Map<String, String> closeIdsByUserKey = new HashMap<String, String>();
		List<HthUserProfile> userProfiles = listByPartyId(partyId);
		if (userProfiles == null) {
			return closeIdsByUserKey;
		}

		for (HthUserProfile userProfile : userProfiles) {
			HthUserProfileKey key = userProfile.getKey();
			if (key != null) {
				closeIdsByUserKey.put(IHthUserProfileAdapter.userProfileKey(key.getPartyId(), key.getCloseId()),
						key.getCloseId());
			}
		}
		return closeIdsByUserKey;
	}

	/**
	 * Optional BCOH2H-538 capability used by newer SMS deployments to derive setup status from
	 * effective grants. It intentionally is not part of {@link IHthUserProfileAdapter}, so older
	 * common and implementation JARs remain binary-compatible during a rolling deployment.
	 *
	 * @param partyId primary corporate party owning the HTH users
	 * @return distinct CloseIDs having at least one active effective account grant
	 * @throws Exception when effective access cannot be read
	 */
	public Set<String> listActiveAccessCloseIds(String partyId) throws Exception {
		// Setup status is derived from effective grants rather than request snapshots. Pending maker
		// requests must not make a user appear configured before checker approval.
		Set<String> closeIds = new LinkedHashSet<String>();
		if (partyId == null || partyId.trim().isEmpty()) {
			return closeIds;
		}

		Session session = null;
		boolean isSessionOpen = false;
		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");
				isSessionOpen = true;
			}

			Query query = session.createSQLQuery(
					"SELECT DISTINCT CLOSE_ID FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT "
							+ "WHERE PARTY_ID = ? AND OBJECT_STATUS = ?");
			query.setParameter(1, partyId);
			query.setParameter(2, OBJECT_STATUS_ACTIVE);
			List rows = query.list();
			if (rows != null) {
				for (Object row : rows) {
					if (row != null) {
						closeIds.add(String.valueOf(row));
					}
				}
			}
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}
		return closeIds;
	}
}
