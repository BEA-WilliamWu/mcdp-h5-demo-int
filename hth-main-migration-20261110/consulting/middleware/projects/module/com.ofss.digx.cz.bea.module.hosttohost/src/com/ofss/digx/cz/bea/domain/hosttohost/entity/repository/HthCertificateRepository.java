package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificate;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthCertificateRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;
import java.util.List;

public class HthCertificateRepository
    extends AbstractDomainObjectRepository<HthCertificate, HthCertificateKey> {

  public static HthCertificateRepository getInstance() {
    return SingletonHolder._INSTANCE;
  }

  @Override
  public HthCertificate read(HthCertificateKey key) throws Exception {
    return repositoryAdapter().read(key);
  }

  public List<HthCertificate> listActiveByPartyId(String partyId) throws Exception {
    return repositoryAdapter().listActiveByPartyId(partyId);
  }

  public List<HthCertificate> listActive() throws Exception {
    return repositoryAdapter().listActive();
  }

  @Override
  public void create(HthCertificate object) throws Exception {
    repositoryAdapter().create(object);
  }

  @Override
  public void update(HthCertificate object) throws Exception {
    repositoryAdapter().update(object);
  }

  @Override
  public void delete(HthCertificate object) throws Exception {
    repositoryAdapter().delete(object);
  }

  private IHthCertificateRepositoryAdapter repositoryAdapter() throws Exception {
    return (IHthCertificateRepositoryAdapter) RepositoryAdapterFactory.getInstance()
        .getRepositoryAdapter(IHthCertificateRepositoryAdapter.HTH_CERTIFICATE_LOCAL_REPOSITORY_ADAPTER);
  }

  private static final class SingletonHolder {
    private static final HthCertificateRepository _INSTANCE = new HthCertificateRepository();
  }
}
