package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.EligibleAccountDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementResponseDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import java.util.List;

public interface IHostToHostManagement {
  HostToHostManagementResponseDTO search(
      SessionContext sessionContext,
      HostToHostManagementDTO requestDTO) throws Exception;

  HostToHostManagementResponseDTO validateUamClientId(
      SessionContext sessionContext,
      HostToHostManagementDTO requestDTO) throws Exception;

  HostToHostManagementResponseDTO submit(
      SessionContext sessionContext,
      HostToHostManagementDTO requestDTO) throws Exception;

  HostToHostManagementResponseDTO edit(
      SessionContext sessionContext,
      HostToHostManagementDTO requestDTO) throws Exception;

  HostToHostManagementResponseDTO disable(
      SessionContext sessionContext,
      HostToHostManagementDTO requestDTO) throws Exception;

  List<EligibleAccountDTO> getEligibleChargeAccounts(
      SessionContext sessionContext,
      String partyId,
      String accountNumber) throws Exception;
}
