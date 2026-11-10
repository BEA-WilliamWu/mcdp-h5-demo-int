package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificate;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;

public interface IHthCertificateRepositoryAdapter
    extends IRepositoryAdapter<HthCertificate, HthCertificateKey> {
  String HTH_CERTIFICATE_REPOSITORY_ADAPTER = "HTH_CERTIFICATE_REPOSITORY_ADAPTER";
  String HTH_CERTIFICATE_LOCAL_REPOSITORY_ADAPTER = "HTH_CERTIFICATE_LOCAL_REPOSITORY_ADAPTER";

  HthCertificate read(HthCertificateKey key) throws Exception;

  List<HthCertificate> listActiveByPartyId(String partyId) throws Exception;

  List<HthCertificate> listActive() throws Exception;

  void create(HthCertificate object) throws Exception;

  void update(HthCertificate object) throws Exception;

  void delete(HthCertificate object) throws Exception;
}
