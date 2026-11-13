package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import feign.Response;
import com.hkbea.common.header.CommonHeader;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.DspClient;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.factory.DSPApiClientFactory;

/** Caller of the provider-owned DSP contract; no private DSP protocol extensions. */
public class HthDspApiCredentialAdapter implements IHthApiCredentialAdapter {
  private static final Logger LOGGER = Logger.getLogger(HthDspApiCredentialAdapter.class.getName());
  private final DspClient client;
  public HthDspApiCredentialAdapter() { this(null); }
  HthDspApiCredentialAdapter(DspClient client) { this.client = client; }

  public String getStatus(String clientId, String closeId) throws Exception {
    // State belongs to HTH_USER_PROFILE. No DSP status endpoint exists.
    return STATUS_UNKNOWN;
  }

  public void requireWriteSupported(String operation) throws Exception {
    if (!"SETUP".equals(operation) && !"RESET".equals(operation)) {
      throw new HthApiCredentialWriteException(false);
    }
  }

  public String publicKey(String nonceHash) throws Exception {
    if (nonceHash == null || !nonceHash.matches("[0-9a-f]{64}")) {
      throw new HthApiCredentialWriteException(false);
    }
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("hashValue", nonceHash);
    JsonNode result = sendPublicKey(request);
    String key = result.path("publicKey").asText();
    if (key.isEmpty()) {
      LOGGER.warning("HTH_API_PASSWORD stage=DSP_RESPONSE operation=PUBLIC_KEY reason=MISSING_PUBLIC_KEY httpStatus=200");
      throw new HthApiCredentialWriteException(false);
    }
    return key;
  }

  public void modify(String clientId, String closeId, String requestId, String operation,
      String encryptedHash, String randomNumber) throws Exception {
    requireWriteSupported(operation);
    if (clientId == null || clientId.trim().isEmpty() || closeId == null || closeId.trim().isEmpty()
        || encryptedHash == null || encryptedHash.isEmpty()
        || randomNumber == null || !randomNumber.matches("[0-9]{6}")) {
      throw new HthApiCredentialWriteException(false);
    }
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("function", "SETUP".equals(operation) ? "A" : "M");
    request.put("clientId", clientId);
    request.put("closeId", closeId);
    request.put("newEncryptedPassword", encryptedHash);
    request.put("randomNumber", randomNumber);
    // requestId stays in CM's Code reservation. DSP does not promise replay/idempotency.
    send(request, true);
  }

  private JsonNode sendPublicKey(Map<String, Object> request) throws Exception {
    return send(request, false);
  }

  private JsonNode send(Map<String, Object> request, boolean write) throws Exception {
    Response response = null;
    CommonHeader header = null;
    String operation = write ? "MODIFICATION" : "PUBLIC_KEY";
    String phase = "CLIENT_INIT";
    try {
      DspClient dspClient = client == null ? DSPApiClientFactory.getDspClient() : client;
      header = dspClient.getCommonHeader();
      phase = "REQUEST";
      response = write ? dspClient.passwordApi().modifyPassword(request)
          : dspClient.passwordApi().queryPublicKey(request);
      if (response.status() != 200 || response.body() == null) {
        LOGGER.warning("HTH_API_PASSWORD stage=DSP_RESPONSE operation=" + operation
            + " reason=" + (response.status() != 200 ? "HTTP_STATUS" : "EMPTY_BODY")
            + " httpStatus=" + response.status());
        throw new HthApiCredentialWriteException(write && !definiteRejection(response.status()));
      }
      phase = "READ_BODY";
      JsonNode body = new ObjectMapper().readTree(response.body().asInputStream());
      if (body == null || !"000000".equals(body.path("status").asText())) {
        LOGGER.warning("HTH_API_PASSWORD stage=DSP_RESPONSE operation=" + operation
            + " reason=INVALID_STATUS httpStatus=" + response.status());
        throw new HthApiCredentialWriteException(write);
      }
      return body;
    } catch (HthApiCredentialWriteException failure) {
      throw failure;
    } catch (java.lang.Exception failure) {
      // Timeout, malformed response or network loss may follow a committed DSP write.
      // Log categories only: exception messages/bodies may contain credentials or keys.
      StringBuilder types = new StringBuilder();
      Throwable cause = failure;
      for (int depth = 0; cause != null && depth < 8; depth++) {
        if (depth > 0) { types.append("->"); }
        types.append(cause.getClass().getSimpleName());
        cause = cause.getCause();
      }
      LOGGER.warning("HTH_API_PASSWORD stage=DSP_RESPONSE operation=" + operation
          + " reason=EXCEPTION phase=" + phase
          + " httpStatus=" + (response == null ? "NONE" : String.valueOf(response.status()))
          + " exceptionTypes=" + types);
      throw new HthApiCredentialWriteException(write);
    } finally {
      try { if (response != null) { response.close(); } }
      finally { if (header != null) { header.reset(); } }
    }
  }
  private boolean definiteRejection(int status) {
    // DSP's parameter/service errors are transactional rejections; gateway/system failures are uncertain.
    return status == 400 || status == 401 || status == 403 || status == 404 || status == 450 || status == 460;
  }
}
