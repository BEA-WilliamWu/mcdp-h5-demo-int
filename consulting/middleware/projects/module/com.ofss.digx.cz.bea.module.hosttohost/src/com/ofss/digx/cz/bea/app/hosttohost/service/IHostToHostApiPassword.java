package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordResponseDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;

/** HTH API-password self-service contract. */
public interface IHostToHostApiPassword {
  HostToHostApiPasswordResponseDTO status(SessionContext sessionContext) throws Exception;
  HostToHostApiPasswordResponseDTO setup(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception;
  HostToHostApiPasswordResponseDTO reset(SessionContext sessionContext,
      HostToHostApiPasswordRequestDTO request) throws Exception;
}
