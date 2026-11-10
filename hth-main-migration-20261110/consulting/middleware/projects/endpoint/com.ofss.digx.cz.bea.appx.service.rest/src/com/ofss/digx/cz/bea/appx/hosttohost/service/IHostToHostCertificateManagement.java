package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostCertificateSubmitDTO;
import javax.ws.rs.core.Response;

public interface IHostToHostCertificateManagement {
  Response search();

  Response submit(HostToHostCertificateSubmitDTO requestDTO);

  Response download(String certificateId);
}
