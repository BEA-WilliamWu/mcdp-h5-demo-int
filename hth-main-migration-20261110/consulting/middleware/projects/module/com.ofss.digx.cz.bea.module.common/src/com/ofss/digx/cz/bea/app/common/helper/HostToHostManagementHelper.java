/**
 *****************************************************************************
* Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.app.common.helper;

import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

public class HostToHostManagementHelper {

	private static final String HTH_STATUS_ENABLE = "ENABLE";
	private static final String OBJECT_STATUS_ACTIVE = "A";
	private static final String HTH_ENABLED_QUERY = "SELECT ID FROM HTH_BEA.HTH_MANAGEMENT "
			+ "WHERE PARTY_ID = ? AND HTH_STATUS = ? AND OBJECT_STATUS = ?";

	public static boolean isHostToHostEnabled(String partyId) throws java.lang.Exception {
		if (partyId == null || partyId.trim().isEmpty()) {
			return false;
		}

		Session session = null;
		boolean isSessionOpen = false;
		List results = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");
				isSessionOpen = true;
			}

			Query query = session.createSQLQuery(HTH_ENABLED_QUERY);
			query.setParameter(1, partyId.trim());
			query.setParameter(2, HTH_STATUS_ENABLE);
			query.setParameter(3, OBJECT_STATUS_ACTIVE);
			query.setMaxResults(1);
			results = query.list();
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return results != null && !results.isEmpty();
	}

}
