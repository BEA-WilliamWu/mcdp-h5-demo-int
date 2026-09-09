package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordState;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordStateKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;

/** Session-bound persistence contract for state data. */
public interface IHthApiPasswordStateRepositoryAdapter extends IRepositoryAdapter<HthApiPasswordState, HthApiPasswordStateKey> {
  String HTH_API_PASSWORD_STATE_LOCAL_REPOSITORY_ADAPTER = "HTH_API_PASSWORD_STATE_LOCAL_REPOSITORY_ADAPTER";
  /** Reads the stored credential status for the company/user key. Uses the supplied session. */
  List findStatus(Session session, String partyId, String userId) throws Exception;

  /** Records successful credential state within the caller-owned transaction. Uses the supplied session. */
  int complete(Session session, String partyId, String userId, String operation, String requestId, String referenceNumber) throws Exception;
}
