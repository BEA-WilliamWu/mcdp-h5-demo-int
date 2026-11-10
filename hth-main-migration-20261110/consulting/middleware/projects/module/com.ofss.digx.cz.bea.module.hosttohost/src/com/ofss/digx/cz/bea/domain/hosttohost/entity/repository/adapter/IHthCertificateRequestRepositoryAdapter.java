package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthCertificateRequestKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.String;
import java.util.List;

public interface IHthCertificateRequestRepositoryAdapter
    extends IRepositoryAdapter<HthCertificateRequest, HthCertificateRequestKey> {
  String HTH_CERTIFICATE_REQUEST_REPOSITORY_ADAPTER =
      "HTH_CERTIFICATE_REQUEST_REPOSITORY_ADAPTER";
  String HTH_CERTIFICATE_REQUEST_LOCAL_REPOSITORY_ADAPTER =
      "HTH_CERTIFICATE_REQUEST_LOCAL_REPOSITORY_ADAPTER";

  HthCertificateRequest read(HthCertificateRequestKey key) throws Exception;

  HthCertificateRequest findActiveByTransactionId(String transactionId) throws Exception;

  List<HthCertificateRequest> listActiveByTransactionId(String transactionId) throws Exception;

  boolean existsPendingApproval(String partyId) throws Exception;

  void create(HthCertificateRequest object) throws Exception;

  void update(HthCertificateRequest object) throws Exception;

  void delete(HthCertificateRequest object) throws Exception;
}
