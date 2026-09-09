package com.ofss.digx.cz.bea.extxface.hosttohost.adapter;

import com.ofss.digx.infra.exceptions.Exception;

/** Boundary used only when UAM is explicitly selected as the credential backend. */
public interface IHthApiCredentialAdapter {
  String STATUS_NOT_SETUP = "NOT_SETUP";
  String STATUS_ACTIVE = "ACTIVE";
  String STATUS_LOCKED = "LOCKED";
  String STATUS_UNKNOWN = "UNKNOWN";

  String getStatus(String partyId, String userId, String uamClientId) throws Exception;

  String setup(String partyId, String userId, String uamClientId, String password,
      String requestId) throws Exception;

  String reset(String partyId, String userId, String uamClientId, String password,
      String requestId) throws Exception;
}
