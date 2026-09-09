package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCodeKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Read/write contract for one-time HTH API Password Code lifecycle rows. */
public interface IHthApiPasswordCodeRepositoryAdapter
    extends IRepositoryAdapter<HthApiPasswordCode, HthApiPasswordCodeKey> {
  String HTH_API_PASSWORD_CODE_REPOSITORY_ADAPTER = "HTH_API_PASSWORD_CODE_REPOSITORY_ADAPTER";

  String HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER =
      "HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER";

  /** Finds the active snapshot associated with approval-framework re-entry. */
  HthApiPasswordCode findActiveByTransactionId(String transactionId) throws Exception;

  /** Latest logical row for a user, used by masked display regardless of lifecycle status. */
  HthApiPasswordCode findLatestByOwner(String partyId, String userName) throws Exception;

  /** PENDING rows for a user; superseded (OBJECT_STATUS='I') when a newer code is generated. */
  List<HthApiPasswordCode> listPendingByOwner(String partyId, String userName) throws Exception;

  /** ACTIVE row for a user, consumed by first-time setup. */
  HthApiPasswordCode findActiveByOwner(String partyId, String userName) throws Exception;

  /** ACTIVE rows for a user; superseded (INVALID) when a regenerated code is activated. */
  List<HthApiPasswordCode> listActiveByOwner(String partyId, String userName) throws Exception;
}
