package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostManagementDTO;
import javax.ws.rs.core.Response;

public interface IHostToHostManagement {
  Response search(String partyId);

  Response validateUamClientId(String partyId, String correlateUamClientId);

  Response submit(HostToHostManagementDTO requestDTO);

  Response edit(HostToHostManagementDTO requestDTO);

  Response disable(HostToHostManagementDTO requestDTO);

  Response getEligibleChargeAccounts(String partyId, String accountNumber);
}
