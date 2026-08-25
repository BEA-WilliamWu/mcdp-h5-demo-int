package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountApiKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Persistence contract for effective API grants under an HTH account grant. */
public interface IHthUserAccessAccountApiRepositoryAdapter
    extends IRepositoryAdapter<HthUserAccessAccountApi, HthUserAccessAccountApiKey> {
  String HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER =
      "HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER";

  HthUserAccessAccountApi read(HthUserAccessAccountApiKey key) throws Exception;

  void create(HthUserAccessAccountApi object) throws Exception;

  void update(HthUserAccessAccountApi object) throws Exception;

  void delete(HthUserAccessAccountApi object) throws Exception;

  /** Returns all API-grant statuses for the effective parent account. */
  List<HthUserAccessAccountApi> listByAccountId(String accountId) throws Exception;

  /** Finds the reusable API-grant row for an account/API business key. */
  HthUserAccessAccountApi findByAccountIdAndApiMasterId(String accountId,
      String apiMasterId) throws Exception;
}
