package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordResponseDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordCodeResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordGenerateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordRevealDTO;

/** HTH API-password self-service contract. */
public interface IHostToHostApiPassword {
  HostToHostApiPasswordResponseDTO status(SessionContext sessionContext) throws Exception;
  HostToHostApiPasswordResponseDTO setup(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception;
  HostToHostApiPasswordResponseDTO reset(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception;

  /** Generates (or regenerates) a PENDING one-time setup code; plaintext returned once. */
  HthApiPasswordCodeResponseDTO generate(SessionContext sessionContext,
      HthApiPasswordGenerateDTO requestDTO) throws Exception;

  /** Masked lifecycle view for create/update/approval pages; never returns plaintext. */
  HthApiPasswordCodeResponseDTO masked(SessionContext sessionContext, String partyId,
      String userName) throws Exception;

  /** Authorized plaintext reveal, written to the audit trail by the reveal task. */
  HthApiPasswordCodeResponseDTO reveal(SessionContext sessionContext,
      HthApiPasswordRevealDTO requestDTO) throws Exception;

  /** Activates a PENDING code when the original user-maintenance flow is approved. */
  void activateOnUserApproval(String codeId, String operator) throws Exception;
  void activateOnUserApproval(String codeId, String operator, String partyId,
      String userName) throws Exception;
}
