package com.ofss.digx.cz.bea.extxface.hosttohost.adapter;

import com.ofss.digx.infra.exceptions.Exception;

/** DSP is the sole HTH password store. This boundary never accepts a plaintext password. */
public interface IHthApiCredentialAdapter {
  String STATUS_NOT_SETUP = "NOT_SETUP";
  String STATUS_ACTIVE = "ACTIVE";
  String STATUS_LOCKED = "LOCKED";
  String STATUS_UNKNOWN = "UNKNOWN";
  String getStatus(String clientId, String closeId) throws Exception;
  /** Reject unsupported writes before reserving or consuming an approval Code. */
  void requireWriteSupported(String operation) throws Exception;
  String publicKey(String nonceHash) throws Exception;
  void modify(String clientId, String closeId, String requestId, String operation,
      String encryptedHash, String randomNumber) throws Exception;
}
