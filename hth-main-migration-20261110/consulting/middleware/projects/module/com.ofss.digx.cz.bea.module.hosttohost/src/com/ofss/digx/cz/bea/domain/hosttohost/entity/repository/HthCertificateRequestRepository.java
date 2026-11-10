package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequestKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthCertificateRequestRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;

public class HthCertificateRequestRepository
    extends AbstractDomainObjectRepository<HthCertificateRequest, HthCertificateRequestKey> {

  public static HthCertificateRequestRepository getInstance() {
    return SingletonHolder._INSTANCE;
  }

  @Override
  public HthCertificateRequest read(HthCertificateRequestKey key) throws Exception {
    return repositoryAdapter().read(key);
  }

  public HthCertificateRequest findActiveByTransactionId(String transactionId) throws Exception {
    return repositoryAdapter().findActiveByTransactionId(transactionId);
  }

  public List<HthCertificateRequest> listActiveByTransactionId(String transactionId)
      throws Exception {
    return repositoryAdapter().listActiveByTransactionId(transactionId);
  }

  public boolean existsPendingApproval(String partyId) throws Exception {
    return repositoryAdapter().existsPendingApproval(partyId);
  }

  @Override
  public void create(HthCertificateRequest object) throws Exception {
    repositoryAdapter().create(object);
  }

  @Override
  public void update(HthCertificateRequest object) throws Exception {
    repositoryAdapter().update(object);
  }

  @Override
  public void delete(HthCertificateRequest object) throws Exception {
    repositoryAdapter().delete(object);
  }

  private IHthCertificateRequestRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthCertificateRequestRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(
            IHthCertificateRequestRepositoryAdapter.HTH_CERTIFICATE_REQUEST_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthCertificateRequestRepository _INSTANCE =
        new HthCertificateRequestRepository();
  }
}
