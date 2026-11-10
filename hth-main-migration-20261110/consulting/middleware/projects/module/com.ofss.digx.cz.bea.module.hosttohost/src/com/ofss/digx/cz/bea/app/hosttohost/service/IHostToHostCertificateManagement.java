package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateFileDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateResponseDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;

public interface IHostToHostCertificateManagement {
  HostToHostCertificateResponseDTO search(SessionContext sessionContext) throws Exception;

  HostToHostCertificateResponseDTO submit(
      SessionContext sessionContext, HostToHostCertificateDTO requestDTO) throws Exception;

  HostToHostCertificateFileDTO download(SessionContext sessionContext, String certificateId) throws Exception;
}
