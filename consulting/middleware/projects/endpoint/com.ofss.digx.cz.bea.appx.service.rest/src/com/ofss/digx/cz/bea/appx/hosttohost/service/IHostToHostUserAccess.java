package com.ofss.digx.cz.bea.appx.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import javax.ws.rs.core.Response;

/** REST boundary exposed to the channel for HTH user access maintenance. */
public interface IHostToHostUserAccess {
  /** Reads the related/associated access summary for an HTH user. */
  Response search(String partyId, String closeId);

  /** Reads account and API details for one related or associated company context. */
  Response accounts(String partyId, String closeId, String accessPartyId,
      String linkageType);

  /** Starts a create approval transaction. */
  Response submit(HostToHostUserAccessDTO requestDTO);

  /** Starts an edit approval transaction. */
  Response edit(HostToHostUserAccessDTO requestDTO);

  /** Starts a delete approval transaction. */
  Response delete(HostToHostUserAccessDTO requestDTO);
}
