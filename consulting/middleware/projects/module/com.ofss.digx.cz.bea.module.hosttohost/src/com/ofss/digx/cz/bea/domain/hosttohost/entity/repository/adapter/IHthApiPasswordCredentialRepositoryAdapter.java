package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredential;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCredentialKey;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;

/** Session-bound persistence contract for credential data. */
public interface IHthApiPasswordCredentialRepositoryAdapter extends IRepositoryAdapter<HthApiPasswordCredential, HthApiPasswordCredentialKey> {
  String HTH_API_PASSWORD_CREDENTIAL_LOCAL_REPOSITORY_ADAPTER = "HTH_API_PASSWORD_CREDENTIAL_LOCAL_REPOSITORY_ADAPTER";
  /** Reads the stored credential status for the company/user key. Uses the supplied session. */
  List findStatus(Session session, String partyId, String userId) throws Exception;

  /** Inserts a SETUP credential or replaces an ACTIVE credential on reset; requires one affected row. Uses the supplied session. */
  void write(Session session, String partyId, String userId, String operation, String requestId, String passwordHash) throws Exception;
}
