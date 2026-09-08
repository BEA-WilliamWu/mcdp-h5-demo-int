package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostApiPasswordRequestDTO;
import javax.ws.rs.core.Response;

/** REST contract for HTH API-password self service. */
public interface IHostToHostApiPassword {
  Response status();
  Response setup(HostToHostApiPasswordRequestDTO request);
  Response reset(HostToHostApiPasswordRequestDTO request);
}
