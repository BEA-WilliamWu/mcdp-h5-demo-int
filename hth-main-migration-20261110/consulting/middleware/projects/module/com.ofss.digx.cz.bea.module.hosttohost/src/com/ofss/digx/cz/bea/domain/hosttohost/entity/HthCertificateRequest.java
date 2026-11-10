package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthCertificateRequestRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class HthCertificateRequest extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 7842174076207649458L;

  private HthCertificateRequestKey key;

  private String transactionId;

  private String referenceNo;

  private String partyId;

  private String actionType;

  private Integer seqNo;

  private String fileName;

  private String pemContent;

  private String objectStatus;

  public HthCertificateRequestKey getKey() {
    return key;
  }

  public void setKey(HthCertificateRequestKey key) {
    this.key = key;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public String getReferenceNo() {
    return referenceNo;
  }

  public void setReferenceNo(String referenceNo) {
    this.referenceNo = referenceNo;
  }

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  public Integer getSeqNo() {
    return seqNo;
  }

  public void setSeqNo(Integer seqNo) {
    this.seqNo = seqNo;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getPemContent() {
    return pemContent;
  }

  public void setPemContent(String pemContent) {
    this.pemContent = pemContent;
  }

  public String getObjectStatus() {
    return objectStatus;
  }

  public void setObjectStatus(String objectStatus) {
    this.objectStatus = objectStatus;
  }

  public HthCertificateRequest read(HthCertificateRequestKey requestKey) throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    return repository.read(requestKey);
  }

  public HthCertificateRequest findActiveByTransactionId(String transactionId) throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    return repository.findActiveByTransactionId(transactionId);
  }

  public List<HthCertificateRequest> listActiveByTransactionId(String transactionId)
      throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    return repository.listActiveByTransactionId(transactionId);
  }

  public boolean existsPendingApproval(String partyId) throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    return repository.existsPendingApproval(partyId);
  }

  public void create(HthCertificateRequest request) throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    repository.create(request);
  }

  public void update(HthCertificateRequest request) throws Exception {
    HthCertificateRequestRepository repository = HthCertificateRequestRepository.getInstance();
    repository.update(request);
  }

  @Override
  protected void validate() {
  }
}
