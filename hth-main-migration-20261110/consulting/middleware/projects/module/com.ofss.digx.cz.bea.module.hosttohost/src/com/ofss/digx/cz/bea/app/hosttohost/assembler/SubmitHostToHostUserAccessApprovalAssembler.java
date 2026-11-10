package com.ofss.digx.cz.bea.app.hosttohost.assembler;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.party.adapter.IPartyDetailsAdapter;
import com.ofss.digx.app.party.dto.PersonalInfoDTO;
import com.ofss.digx.common.constants.CommonAdapterConstants;
import com.ofss.digx.common.constants.CommonAdapterFactoryConstants;
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
import java.util.logging.Level;
import java.util.logging.Logger;

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

  private static final String THIS_COMPONENT_NAME =
      SubmitHostToHostUserAccessApprovalAssembler.class.getName();

  private transient com.ofss.fc.infra.log.impl.MultiEntityLogger formatter =
      com.ofss.fc.infra.log.impl.MultiEntityLogger.getUniqueInstance();

  private transient Logger logger = com.ofss.fc.infra.log.impl.MultiEntityLogger
      .getUniqueInstance().getLogger(THIS_COMPONENT_NAME);

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
    transaction.setPartyName(fetchPartyName(requestDTO.getPartyId()));
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

  /** Fetches the company name for the transaction party, matching BCO User Account Access. */
  private PartyName fetchPartyName(String partyId) {
    PartyName partyName = null;
    try {
      IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance()
          .getAdapterFactory(CommonAdapterFactoryConstants.PARTY_DETAILS_ADAPTER_FACTORY);
      IPartyDetailsAdapter partyDetailsAdapter = (IPartyDetailsAdapter) adapterFactory
          .getAdapter(CommonAdapterConstants.PARTY_DETAILS_ADAPTER);
      PersonalInfoDTO personalInfo = partyDetailsAdapter.fetchPersonalInformation(partyId);
      if (personalInfo != null) {
        partyName = new PartyName();
        partyName.setFirstName(personalInfo.getFirstName());
        partyName.setMiddleName(personalInfo.getMiddleName());
        partyName.setLastName(personalInfo.getLastName());
        partyName.setSalutation(personalInfo.getSalutation());
        partyName.setFullName(personalInfo.getFullName());
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE,
          formatter.formatMessage("Exception occurred while fetching party name."), e);
    }
    return partyName;
  }

  private void setAdministrationDiscriminator(Transaction transaction) throws Exception {
    try {
      // Match the standard BCO User Account Access assembler: these requests belong in
      // Administrative Maintenance for Pending Approvals, My Approval List and Activity Log.
      Field field = Transaction.class.getDeclaredField("discriminator");
      field.setAccessible(true);
      field.set(transaction, TransactionDiscriminator.ADMIN_MAINTENANCE);
    } catch (ReflectiveOperationException | SecurityException reflectionFailure) {
      // Do not silently fall back to PARTY_MAINTENANCE: that creates a valid platform transaction
      // which the BCO administrative lists cannot retrieve, leaving an invisible pending request.
      throw new Exception(reflectionFailure);
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
