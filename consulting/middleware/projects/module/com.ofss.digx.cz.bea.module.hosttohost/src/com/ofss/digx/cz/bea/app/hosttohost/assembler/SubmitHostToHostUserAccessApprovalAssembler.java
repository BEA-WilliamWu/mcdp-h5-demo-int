package com.ofss.digx.cz.bea.app.hosttohost.assembler;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.enumeration.approval.TransactionDiscriminator;
import com.ofss.digx.framework.domain.transaction.PartyName;
import com.ofss.digx.framework.domain.transaction.PartyTransaction;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.assembler.AbstractApprovalAssembler;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.IAbstractDomainObject;
import com.ofss.fc.framework.domain.common.dto.DomainObjectDTO;
import com.ofss.fc.infra.exception.FatalException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts HTH user-access requests into approval-framework transactions.
 *
 * <p>Like the standard BCO user-access flow, the approval framework owns both workflow state and
 * the serialized maker DTO in {@code transactionSnapshot}. A hash of the complete HTH access
 * context is stored as the entity identifier so the platform's standard duplicate check prevents
 * concurrent maintenance of the same user/company relationship.
 */
public class SubmitHostToHostUserAccessApprovalAssembler
    extends AbstractApprovalAssembler<HostToHostUserAccessDTO, Transaction> {

  @Override
  public HostToHostUserAccessDTO fromDomainObject(Transaction transaction)
      throws Exception {
    // The checker page and approval re-entry receive the typed platform transactionSnapshot.
    return null;
  }

  @Override
  public PartyTransaction toDomainObject(HostToHostUserAccessDTO requestDTO)
      throws Exception {
    if (requestDTO == null) {
      return null;
    }
    PartyTransaction transaction = new PartyTransaction();
    transaction.setPartyId(requestDTO.getPartyId());
    PartyName partyName = new PartyName();
    String displayName = normalize(requestDTO.getFullName());
    if (displayName == null) {
      displayName = normalize(requestDTO.getUsername());
    }
    if (displayName == null) {
      displayName = requestDTO.getCloseId();
    }
    partyName.setFirstName(displayName);
    partyName.setFullName(displayName);
    transaction.setPartyName(partyName);
    transaction = (PartyTransaction) super.toDomainObject(requestDTO, transaction);

    List<String> entityIdentifiers = new ArrayList<String>();
    entityIdentifiers.add(super.getHash(buildContextIdentifier(requestDTO)));
    transaction.setEntityIdentifiers(entityIdentifiers);
    setAdministrationDiscriminator(transaction);
    return transaction;
  }

  @Override
  public DomainObjectDTO fromDomainObject(IAbstractDomainObject domainObject)
      throws FatalException {
    // The HTH flow uses the typed PartyTransaction conversion above.
    return null;
  }

  @Override
  public IAbstractDomainObject toDomainObject(DomainObjectDTO dto)
      throws FatalException {
    // The HTH flow uses the typed HostToHostUserAccessDTO conversion above.
    return null;
  }

  private void setAdministrationDiscriminator(Transaction transaction) {
    try {
      // Some supported framework releases do not expose a discriminator setter. Reflection keeps
      // the compiled extension compatible while still marking the transaction when the field exists.
      Field field = Transaction.class.getDeclaredField("discriminator");
      field.setAccessible(true);
      field.set(transaction, TransactionDiscriminator.ADMIN_MAINTENANCE);
    } catch (ReflectiveOperationException ignored) {
      // The approval framework applies its default discriminator on older releases.
    }
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String buildContextIdentifier(HostToHostUserAccessDTO requestDTO) {
    return valueOrEmpty(requestDTO.getPartyId()) + "#"
        + valueOrEmpty(requestDTO.getCloseId()) + "#"
        + valueOrEmpty(requestDTO.getAccessPartyId()) + "#"
        + valueOrEmpty(requestDTO.getLinkageType());
  }

  private String valueOrEmpty(String value) {
    String normalized = normalize(value);
    return normalized == null ? "" : normalized;
  }
}
