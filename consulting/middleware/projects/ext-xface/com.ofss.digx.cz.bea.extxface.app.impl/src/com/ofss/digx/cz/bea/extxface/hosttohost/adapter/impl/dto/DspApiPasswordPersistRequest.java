package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/** DSP credentials/v1/persist body. Contains no plaintext password or APIC credentials. */
public final class DspApiPasswordPersistRequest {
  private final String clientId;
  private final String closeId;
  private final String passwordHash;

  private DspApiPasswordPersistRequest(String clientId, String closeId, String passwordHash) {
    this.clientId = clientId;
    this.closeId = closeId;
    this.passwordHash = passwordHash;
  }

  /** Use the business client ID and full authenticated CLOSE_ID resolved by the service. */
  public static DspApiPasswordPersistRequest fromPassword(
      String clientId, String closeId, String password) {
    if (clientId == null || clientId.trim().isEmpty()
        || closeId == null || closeId.trim().isEmpty()
        || password == null || password.isEmpty()) {
      throw new IllegalArgumentException("DSP credential input is incomplete");
    }
    byte[] input = password.getBytes(StandardCharsets.UTF_8);
    byte[] digest = null;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(input);
      return new DspApiPasswordPersistRequest(
          clientId, closeId, Base64.getEncoder().encodeToString(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    } finally {
      Arrays.fill(input, (byte) 0);
      if (digest != null) {
        Arrays.fill(digest, (byte) 0);
      }
    }
  }

  public String getClientId() {
    return clientId;
  }

  public String getCloseId() {
    return closeId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  @Override
  public String toString() {
    return "DspApiPasswordPersistRequest{credentials=[PROTECTED]}";
  }
}
