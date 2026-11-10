package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessSummaryRecord;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Persistence contract for active/inactive effective HTH account grants. */
public interface IHthUserAccessAccountRepositoryAdapter
    extends IRepositoryAdapter<HthUserAccessAccount, HthUserAccessAccountKey> {
  String HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER =
      "HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER";

  HthUserAccessAccount read(HthUserAccessAccountKey key) throws Exception;

  void create(HthUserAccessAccount object) throws Exception;

  void update(HthUserAccessAccount object) throws Exception;

  void delete(HthUserAccessAccount object) throws Exception;

  /** Returns all statuses for an exact user/company relationship context. */
  List<HthUserAccessAccount> listByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception;

  /** Finds the reusable row identified by the context and canonical account number. */
  HthUserAccessAccount findByContextAndAccountNumber(String partyId, String closeId,
      String accessPartyId, String linkageType, String accountNumber) throws Exception;

  /** Returns whether the context currently has at least one active effective grant. */
  boolean hasActiveByContext(String partyId, String closeId, String accessPartyId,
      String linkageType) throws Exception;

  /** Aggregates active grants for the BCOH2H-538 access summary. */
  List<HthUserAccessSummaryRecord> listActiveSummary(String partyId, String closeId)
      throws Exception;

  /**
   * Checks the complete runtime chain: active user/account grant, active account/API grant, active
   * API master, and enterprise-enabled HTH API assignment.
   */
  boolean isAuthorized(String partyId, String closeId, String accountNumber,
      String apiCode) throws Exception;
}
