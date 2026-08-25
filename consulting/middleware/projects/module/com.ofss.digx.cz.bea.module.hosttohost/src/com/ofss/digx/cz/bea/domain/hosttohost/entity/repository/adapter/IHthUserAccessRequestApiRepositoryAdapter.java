package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApi;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessRequestApiKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Persistence contract for API rows captured in a maker request snapshot. */
public interface IHthUserAccessRequestApiRepositoryAdapter
    extends IRepositoryAdapter<HthUserAccessRequestApi, HthUserAccessRequestApiKey> {
  String HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER =
      "HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER";

  HthUserAccessRequestApi read(HthUserAccessRequestApiKey key) throws Exception;

  void create(HthUserAccessRequestApi object) throws Exception;

  void update(HthUserAccessRequestApi object) throws Exception;

  void delete(HthUserAccessRequestApi object) throws Exception;

  /** Returns APIs captured under one request account in display order. */
  List<HthUserAccessRequestApi> listByRequestAccountId(String requestAccountId)
      throws Exception;

  /** Returns all APIs for a request, joined through its request-account rows. */
  List<HthUserAccessRequestApi> listByRequestId(String requestId) throws Exception;
}
