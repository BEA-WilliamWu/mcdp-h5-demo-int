package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

/** Strict internal APIC contract: only HTTP 200 / status 000000 confirms DSP success. */
public class HthDspApiCredentialAdapter implements IHthApiCredentialAdapter {
  private static final String PROTOCOL = "CM_SHA256_BASE64_V1";
  private final DspApiPasswordClient client;
  public HthDspApiCredentialAdapter() { this(new DspApiPasswordClient()); }
  HthDspApiCredentialAdapter(DspApiPasswordClient client) { this.client = client; }

  public String getStatus(String clientId, String closeId) throws Exception {
    Map<String, Object> request = request(clientId, closeId, UUID.randomUUID().toString());
    request.put("queryOnly", Boolean.TRUE);
    request.put("hashValue", "0000000000000000000000000000000000000000000000000000000000000000");
    JsonNode result = send(request, false);
    String state = result.path("credentialStatus").asText();
    if (!PROTOCOL.equals(result.path("protocol").asText())
        || (!STATUS_ACTIVE.equals(state) && !STATUS_NOT_SETUP.equals(state) && !STATUS_LOCKED.equals(state))) {
      throw new HthApiCredentialWriteException(false);
    }
    return state;
  }

  public String publicKey(String clientId, String closeId, String requestId, String nonceHash) throws Exception {
    Map<String, Object> request = request(clientId, closeId, requestId);
    request.put("hashValue", nonceHash);
    request.put("queryOnly", Boolean.FALSE);
    JsonNode result = send(request, false);
    String key = result.path("publicKey").asText();
    if (!PROTOCOL.equals(result.path("protocol").asText()) || key.isEmpty()) {
      throw new HthApiCredentialWriteException(false);
    }
    return key;
  }

  public void modify(String clientId, String closeId, String requestId, String function,
      String encryptedHash, String randomNumber) throws Exception {
    Map<String, Object> request = request(clientId, closeId, requestId);
    request.put("function", function);
    request.put("newEncryptedPassword", encryptedHash);
    request.put("randomNumber", randomNumber);
    send(request, true);
  }

  private Map<String, Object> request(String clientId, String closeId, String requestId) {
    Map<String, Object> request = new LinkedHashMap<String, Object>();
    request.put("protocol", PROTOCOL);
    request.put("clientId", clientId);
    request.put("closeId", closeId);
    request.put("requestId", requestId);
    return request;
  }

  private JsonNode send(Map<String, Object> request, boolean modification) throws Exception {
    Response response = null;
    try {
      response = client.send(request, modification);
      int status = response.getStatus();
      if (status != 200) {
        // A proxy timeout or server failure can occur after commit; never release that Code.
        throw new HthApiCredentialWriteException(modification && status != 400 && status != 401 && status != 403
            && status != 404 && status != 405 && status != 422);
      }
      JsonNode body = new ObjectMapper().readTree(response.readEntity(String.class));
      if (body == null || !"000000".equals(body.path("status").asText())) {
        throw new HthApiCredentialWriteException(modification);
      }
      return body;
    } catch (HthApiCredentialWriteException failure) {
      throw failure;
    } catch (DspApiPasswordClient.TransportException failure) {
      throw new HthApiCredentialWriteException(modification && "POST".equals(failure.getStage()));
    } catch (java.lang.Exception failure) {
      // Do not propagate bodies or transport exception messages containing credentials.
      throw new HthApiCredentialWriteException(modification);
    } finally { if (response != null) { response.close(); } }
  }
}
