package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccount;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessAccountKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserAccessSummaryRecord;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserAccessAccountRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;

/** Domain repository for effective HTH account grants and runtime authorization lookups. */
public class HthUserAccessAccountRepository
    extends AbstractDomainObjectRepository<HthUserAccessAccount, HthUserAccessAccountKey> {
  public static HthUserAccessAccountRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public List<HthUserAccessAccount> listByContext(String partyId, String closeId,
      String accessPartyId, String linkageType) throws Exception {
    return adapter().listByContext(partyId, closeId, accessPartyId, linkageType);
  }

  public HthUserAccessAccount findByContextAndAccountNumber(String partyId, String closeId,
      String accessPartyId, String linkageType, String accountNumber) throws Exception {
    return adapter().findByContextAndAccountNumber(partyId, closeId, accessPartyId,
        linkageType, accountNumber);
  }

  public boolean hasActiveByContext(String partyId, String closeId, String accessPartyId,
      String linkageType) throws Exception {
    return adapter().hasActiveByContext(partyId, closeId, accessPartyId, linkageType);
  }

  public List<HthUserAccessSummaryRecord> listActiveSummary(String partyId, String closeId)
      throws Exception {
    return adapter().listActiveSummary(partyId, closeId);
  }

  public boolean isAuthorized(String partyId, String closeId, String accountNumber,
      String apiCode) throws Exception {
    return adapter().isAuthorized(partyId, closeId, accountNumber, apiCode);
  }

  @Override
  public HthUserAccessAccount read(HthUserAccessAccountKey key) throws Exception {
    return adapter().read(key);
  }

  @Override
  public void create(HthUserAccessAccount object) throws Exception {
    adapter().create(object);
  }

  @Override
  public void update(HthUserAccessAccount object) throws Exception {
    adapter().update(object);
  }

  @Override
  public void delete(HthUserAccessAccount object) throws Exception {
    adapter().delete(object);
  }

  private IHthUserAccessAccountRepositoryAdapter adapter() throws Exception {
    return (IHthUserAccessAccountRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(IHthUserAccessAccountRepositoryAdapter
            .HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthUserAccessAccountRepository INSTANCE =
        new HthUserAccessAccountRepository();
  }
}
