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
 * <p>The entity identifier hashes the complete access context rather than an account number. This
 * keeps create/edit/delete requests for the same party, CloseID, account-owning party, and linkage
 * type in the same approval identity while avoiding exposure of those values in task metadata.
 * The detailed maker payload is persisted separately as an immutable database snapshot.
 */
public class SubmitHostToHostUserAccessApprovalAssembler
    extends AbstractApprovalAssembler<HostToHostUserAccessDTO, Transaction> {

  @Override
  public HostToHostUserAccessDTO fromDomainObject(Transaction transaction)
      throws Exception {
    // Approval detail is reconstructed from the persisted request snapshot, not a generic
    // transaction domain object. This direction is intentionally unsupported.
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

    List<String> identifiers = new ArrayList<String>();
    identifiers.add(super.getHash(contextIdentifier(requestDTO)));
    transaction.setEntityIdentifiers(identifiers);
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

  private String contextIdentifier(HostToHostUserAccessDTO request) {
    // All four values are required to distinguish RELATED from ASSOCIATED access belonging to the
    // same HTH user. Account numbers are excluded because one request can contain many accounts.
    return String.valueOf(request.getPartyId()) + "#" + String.valueOf(request.getCloseId())
        + "#" + String.valueOf(request.getAccessPartyId()) + "#"
        + String.valueOf(request.getLinkageType());
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
