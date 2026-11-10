package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthCertificateRepository;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class HthCertificate extends AbstractDomainObject implements IPersistenceObject {
  private static final long serialVersionUID = 4276077505682617299L;

  private HthCertificateKey key;

  private String partyId;

  private Integer seqNo;

  private String fileName;

  private String pemContent;

  private String referenceNo;

  private String effectiveTransactionId;

  private String objectStatus;

  public HthCertificateKey getKey() {
    return key;
  }

  public void setKey(HthCertificateKey key) {
    this.key = key;
  }

  public String getPartyId() {
    return partyId;
  }

  public void setPartyId(String partyId) {
    this.partyId = partyId;
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

  public String getReferenceNo() {
    return referenceNo;
  }

  public void setReferenceNo(String referenceNo) {
    this.referenceNo = referenceNo;
  }

  public String getEffectiveTransactionId() {
    return effectiveTransactionId;
  }

  public void setEffectiveTransactionId(String effectiveTransactionId) {
    this.effectiveTransactionId = effectiveTransactionId;
  }

  public String getObjectStatus() {
    return objectStatus;
  }

  public void setObjectStatus(String objectStatus) {
    this.objectStatus = objectStatus;
  }

  public HthCertificate read(HthCertificateKey certificateKey) throws Exception {
    HthCertificateRepository repository = HthCertificateRepository.getInstance();
    return repository.read(certificateKey);
  }

  public List<HthCertificate> listActiveByPartyId(String partyId) throws Exception {
    HthCertificateRepository repository = HthCertificateRepository.getInstance();
    return repository.listActiveByPartyId(partyId);
  }

  public List<HthCertificate> listActive() throws Exception {
    HthCertificateRepository repository = HthCertificateRepository.getInstance();
    return repository.listActive();
  }

  public void create(HthCertificate certificate) throws Exception {
    HthCertificateRepository repository = HthCertificateRepository.getInstance();
    repository.create(certificate);
  }

  public void update(HthCertificate certificate) throws Exception {
    HthCertificateRepository repository = HthCertificateRepository.getInstance();
    repository.update(certificate);
  }

  @Override
  protected void validate() {
  }
}
