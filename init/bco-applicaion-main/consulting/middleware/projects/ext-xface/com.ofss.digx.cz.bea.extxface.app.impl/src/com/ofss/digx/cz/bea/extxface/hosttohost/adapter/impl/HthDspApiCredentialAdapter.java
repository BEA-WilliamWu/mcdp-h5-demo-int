package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;

/** Caller of the provider-owned DSP contract; no private DSP protocol extensions. */
public class HthDspApiCredentialAdapter implements IHthApiCredentialAdapter {
  private static final Logger LOGGER = Logger.getLogger(HthDspApiCredentialAdapter.class.getName());
  private final DspApiPasswordClient client;
  public HthDspApiCredentialAdapter() { this(new DspApiPasswordClient()); }
  HthDspApiCredentialAdapter(DspApiPasswordClient client) { this.client = client; }

  public String getStatus(String clientId, String closeId) throws Exception {
    // Neither published endpoint queries credential existence. A key response is not a status response.
    // Do not infer NOT_SETUP from no local record or issue a trial password write.
    LOGGER.warning("HTH_API_PASSWORD stage=DSP_CONTRACT reason=CREDENTIAL_STATUS_UNAVAILABLE");
    return STATUS_UNKNOWN;
  }

  public void requireWriteSupported(String operation) throws Exception {
    // 1955 requires an encrypted password hash. The available provider implementation hashes
    // the decrypted value again; its M function also requires the actual old password.
    // No switch enables this until the provider supplies the agreed contract.
    if ("RESET".equals(operation)) {
      LOGGER.warning("HTH_API_PASSWORD stage=DSP_CONTRACT reason=RESET_WITHOUT_OLD_PASSWORD_UNSUPPORTED");
    } else {
      LOGGER.warning("HTH_API_PASSWORD stage=DSP_CONTRACT reason=HASH_STORAGE_CONTRACT_UNCONFIRMED");
    }
    throw new HthApiCredentialWriteException(false);
  }

  public String publicKey(String nonceHash) throws Exception {
    if (nonceHash == null || !nonceHash.matches("[0-9a-f]{64}")) {
      throw new HthApiCredentialWriteException(false);
    }
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("hashValue", nonceHash);
    JsonNode result = sendPublicKey(request);
    String key = result.path("publicKey").asText();
    if (key.isEmpty()) { throw new HthApiCredentialWriteException(false); }
    return key;
  }

  public void modify(String clientId, String closeId, String requestId, String operation,
      String encryptedHash, String randomNumber) throws Exception {
    // Defence in depth for direct callers. Do not send the hash into a plaintext-password API,
    // invent function R, use A to overwrite an existing credential, or fake an old password.
    requireWriteSupported(operation);
  }

  private JsonNode sendPublicKey(Map<String, Object> request) throws Exception {
    Response response = null;
    try {
      response = client.send(request, false);
      if (response.getStatus() != 200) { throw new HthApiCredentialWriteException(false); }
      JsonNode body = new ObjectMapper().readTree(response.readEntity(String.class));
      if (body == null || !"000000".equals(body.path("status").asText())) {
        throw new HthApiCredentialWriteException(false);
      }
      return body;
    } catch (HthApiCredentialWriteException failure) {
      throw failure;
    } catch (java.lang.Exception failure) {
      // No password write occurred; never include response bodies, headers or causes in logs.
      throw new HthApiCredentialWriteException(false);
    } finally { if (response != null) { response.close(); } }
  }
}
