package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthApiPasswordCode;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthApiPasswordCodeRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.List;
import com.ofss.fc.infra.das.orm.Session;

/** Repository facade for one-time HTH API Password Code lifecycle rows. */
public class HthApiPasswordCodeRepository {
  public static HthApiPasswordCodeRepository getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public HthApiPasswordCode findActiveByTransactionId(String transactionId) throws Exception {
    return repositoryAdapter().findActiveByTransactionId(transactionId);
  }

  public HthApiPasswordCode findLatestByOwner(String partyId, String userName) throws Exception {
    return repositoryAdapter().findLatestByOwner(partyId, userName);
  }

  public List<HthApiPasswordCode> listPendingByOwner(String partyId, String userName)
      throws Exception {
    return repositoryAdapter().listPendingByOwner(partyId, userName);
  }

  public HthApiPasswordCode findActiveByOwner(String partyId, String userName) throws Exception {
    return repositoryAdapter().findActiveByOwner(partyId, userName);
  }

  private IHthApiPasswordCodeRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthApiPasswordCodeRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(
            IHthApiPasswordCodeRepositoryAdapter.HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthApiPasswordCodeRepository INSTANCE =
        new HthApiPasswordCodeRepository();
  }
  public List findUsable(Session session, String partyId, String userId, String purpose) throws Exception {
    return repositoryAdapter().findUsable(session, partyId, userId, purpose);
  }

  public List findUsableCipher(Session session, String partyId, String userId, String purpose) throws Exception {
    return repositoryAdapter().findUsableCipher(session, partyId, userId, purpose);
  }

  /** Loads an expired Code so expiry is reported only after the submitted value matches. */
  public List findLatestExpiredCipher(Session session, String partyId, String userId, String purpose) throws Exception {
    return repositoryAdapter().findLatestExpiredCipher(session, partyId, userId, purpose);
  }

  public int recordFailedAttempt(Session session, String codeId) throws Exception {
    return repositoryAdapter().recordFailedAttempt(session, codeId);
  }

  public int reserve(Session session, String requestId, String codeId) throws Exception {
    return repositoryAdapter().reserve(session, requestId, codeId);
  }

  public int consume(Session session, String userId, String codeId, String requestId) throws Exception {
    return repositoryAdapter().consume(session, userId, codeId, requestId);
  }

  public int failReservation(Session session, String userId, String requestId, String codeId, boolean uncertain) throws Exception {
    return repositoryAdapter().failReservation(session, userId, requestId, codeId, uncertain);
  }

  public int retirePending(Session session, String partyId, String userName, String purpose, String operator) throws Exception {
    return repositoryAdapter().retirePending(session, partyId, userName, purpose, operator);
  }

  public List lockForApproval(Session session, String codeId) throws Exception {
    return repositoryAdapter().lockForApproval(session, codeId);
  }

  public int retireActive(Session session, String operator, String partyId, String userName, String purpose) throws Exception {
    return repositoryAdapter().retireActive(session, operator, partyId, userName, purpose);
  }

  public int activate(Session session, String transactionId, int expiryHours, String operator, String codeId) throws Exception {
    return repositoryAdapter().activate(session, transactionId, expiryHours, operator, codeId);
  }

}
