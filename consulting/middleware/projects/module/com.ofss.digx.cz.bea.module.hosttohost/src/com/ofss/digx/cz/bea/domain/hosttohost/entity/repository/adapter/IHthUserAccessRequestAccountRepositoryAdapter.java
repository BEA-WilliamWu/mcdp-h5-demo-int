package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestAccountKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Persistence contract for account rows captured in a maker request snapshot. */
public interface IHthUserAccessRequestAccountRepositoryAdapter
    extends IRepositoryAdapter<HthUserAccessRequestAccount, HthUserAccessRequestAccountKey> {
  String HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER =
      "HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER";

  HthUserAccessRequestAccount read(HthUserAccessRequestAccountKey key) throws Exception;

  void create(HthUserAccessRequestAccount object) throws Exception;

  void update(HthUserAccessRequestAccount object) throws Exception;

  void delete(HthUserAccessRequestAccount object) throws Exception;

  /** Returns request accounts in the stored display order. */
  List<HthUserAccessRequestAccount> listByRequestId(String requestId) throws Exception;
}
