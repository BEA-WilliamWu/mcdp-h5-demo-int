package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import javax.ws.rs.core.Response;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordCodeResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordGenerateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordRevealDTO;

/** REST contract for HTH API-password self service. */
public interface IHostToHostApiPassword {
  Response status();
  Response setup(HostToHostApiPasswordRequestDTO request);
  Response reset(HostToHostApiPasswordRequestDTO request);

  Response generate(HthApiPasswordGenerateDTO requestDTO);

  Response masked(String partyId, String userName);

  Response reveal(HthApiPasswordRevealDTO requestDTO);
}
