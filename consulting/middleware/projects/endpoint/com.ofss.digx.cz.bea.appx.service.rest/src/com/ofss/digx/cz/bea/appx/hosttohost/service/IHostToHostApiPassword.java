package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import javax.ws.rs.core.Response;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordGenerateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordRevealDTO;

/** REST contract for HTH API-password self service. */
public interface IHostToHostApiPassword {
  /** Returns the authenticated user's credential status and password policy. */
  Response status();
  /** Accepts an encrypted password/SETUP Code envelope and a retry-stable request ID. */
  Response setup(HostToHostApiPasswordRequestDTO request);
  /** Accepts an encrypted password/RESET Code envelope for an ACTIVE credential. */
  Response reset(HostToHostApiPasswordRequestDTO request);

  /** Generates a PENDING Code for the entitled user-maintenance operation. */
  Response generate(HthApiPasswordGenerateDTO requestDTO);

  /** Returns Code lifecycle metadata without its plaintext. */
  Response masked(String partyId, String userName);

  /** Returns plaintext Code under the dedicated reveal entitlement and audit task. */
  Response reveal(HthApiPasswordRevealDTO requestDTO);
}
