package com.ofss.digx.cz.bea.app.hosttohost.assembler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.app.party.adapter.IPartyDetailsAdapter;
import com.ofss.digx.app.party.dto.PersonalInfoDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.enumeration.approval.TransactionDiscriminator;
import com.ofss.digx.framework.domain.transaction.PartyName;
import com.ofss.digx.framework.domain.transaction.PartyTransaction;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.domain.transaction.assembler.AbstractApprovalAssembler;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.framework.domain.IAbstractDomainObject;
import com.ofss.fc.framework.domain.common.dto.DomainObjectDTO;
import com.ofss.fc.infra.exception.FatalException;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;

public class SubmitHostToHostManagementApprovalAssembler
    extends AbstractApprovalAssembler<HostToHostManagementDTO, Transaction> {

  private static final String THIS_COMPONENT_NAME =
      SubmitHostToHostManagementApprovalAssembler.class.getName();

  private static final MultiEntityLogger FORMATTER = MultiEntityLogger.getUniqueInstance();

  private static final Logger LOGGER = FORMATTER.getLogger(THIS_COMPONENT_NAME);

  @Override
  public HostToHostManagementDTO fromDomainObject(Transaction transaction) throws Exception {
    return null;
  }

  @Override
  public PartyTransaction toDomainObject(HostToHostManagementDTO requestDTO) throws Exception {
    PartyTransaction transaction = null;
    if (requestDTO != null) {
      transaction = new PartyTransaction();
      transaction.setPartyId(requestDTO.getPartyId());
      transaction.setPartyName(buildPartyName(requestDTO));
      transaction = (PartyTransaction) super.toDomainObject(requestDTO, transaction);

      List<String> entityIdentifiers = new ArrayList<String>();
      if (requestDTO.getPartyId() != null) {
        entityIdentifiers.add(super.getHash(requestDTO.getPartyId()));
      }
      transaction.setEntityIdentifiers(entityIdentifiers);

      try {
        Field discriminatorField = Transaction.class.getDeclaredField("discriminator");
        discriminatorField.setAccessible(true);
        discriminatorField.set(transaction, TransactionDiscriminator.ADMIN_MAINTENANCE);
      } catch (NoSuchFieldException | SecurityException | IllegalArgumentException
          | IllegalAccessException e) {
        LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
            "Exception occurred while preparing Host to Host approval transaction."), e);
      }
    }
    return transaction;
  }

  @Override
  public DomainObjectDTO fromDomainObject(IAbstractDomainObject domainObject) throws FatalException {
    return null;
  }

  @Override
  public IAbstractDomainObject toDomainObject(DomainObjectDTO dto) throws FatalException {
    return null;
  }

  private PartyName buildPartyName(HostToHostManagementDTO requestDTO) {
    String companyName = normalize(requestDTO.getAccountName());
    if (companyName != null) {
      PartyName partyName = new PartyName();
      partyName.setFirstName(companyName);
      partyName.setFullName(companyName);
      return partyName;
    }

    PartyName partyName = new PartyName();
    if (requestDTO.getPartyId() == null || requestDTO.getPartyId().trim().isEmpty()) {
      return partyName;
    }

    try {
      IAdapterFactory adapterFactory = AdapterFactoryConfigurator.getInstance().getAdapterFactory(
          com.ofss.digx.common.constants.CommonAdapterFactoryConstants.PARTY_DETAILS_ADAPTER_FACTORY);
      IPartyDetailsAdapter partyDetailsAdapter = (IPartyDetailsAdapter) adapterFactory.getAdapter(
          com.ofss.digx.common.constants.CommonAdapterConstants.PARTY_DETAILS_ADAPTER);
      PersonalInfoDTO personalInfo = partyDetailsAdapter.fetchPersonalInformation(requestDTO.getPartyId());
      if (personalInfo != null) {
        partyName.setFirstName(personalInfo.getFirstName());
        partyName.setMiddleName(personalInfo.getMiddleName());
        partyName.setLastName(personalInfo.getLastName());
        partyName.setSalutation(personalInfo.getSalutation());
        partyName.setFullName(personalInfo.getFullName());
      }
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, FORMATTER.formatMessage(
          "Exception occurred while fetching party name for Host to Host approval transaction."), e);
    }

    return partyName;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }

    String normalizedValue = value.trim();
    return normalizedValue.isEmpty() ? null : normalizedValue;
  }
}
