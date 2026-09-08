package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;

/**
 * Configurable HTTPS adapter for the UAM HTH API-password contract.
 *
 * <p>The endpoint paths are deliberately externalized because the bundled DSP client does not
 * expose password administration. No password, password code, or response body is logged here.
 */
public class HthApiCredentialAdapter implements IHthApiCredentialAdapter {
  private static final Client CLIENT = ClientBuilder.newClient();
  private static final String CATEGORY = "HthApiCredentialAdapterConfig";
  private static final String SERVICE_URL = "HTH_API_PASSWORD.SERVICE_URL";
  private static final String STATUS_PATH = "HTH_API_PASSWORD.STATUS_PATH";
  private static final String SETUP_PATH = "HTH_API_PASSWORD.SETUP_PATH";
  private static final String RESET_PATH = "HTH_API_PASSWORD.RESET_PATH";
  private static final String CLIENT_ID = "HTH_API_PASSWORD.APIC_CLIENT_ID";
  private static final String CLIENT_SECRET = "HTH_API_PASSWORD.APIC_CLIENT_SECRET";
  private static final String CHANNEL = "HTH_API_PASSWORD.CHANNEL";
  private static final String CONNECT_TIMEOUT = "HTH_API_PASSWORD.CONNECT_TIMEOUT_MS";
  private static final String READ_TIMEOUT = "HTH_API_PASSWORD.READ_TIMEOUT_MS";
  private static final String DSP_CATEGORY = "DSPApi";
  private static final String DSP_CLIENT_ID = "DSP.APIC_CLIENT_ID";
  private static final String DSP_CLIENT_SECRET = "DSP.APIC_CLIENT_SECRET";
  private static final String DEFAULT_STATUS_PATH = "/uam/hth/users/password/status";
  private static final String DEFAULT_SETUP_PATH = "/uam/hth/users/password";
  private static final String DEFAULT_RESET_PATH = "/uam/hth/users/password/reset";

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String getStatus(String partyId, String userId, String uamClientId) throws Exception {
    Preferences config = configurations();
    Response response = null;
    try {
      response = request(config, config.get(STATUS_PATH, DEFAULT_STATUS_PATH))
          .queryParam("partyId", partyId)
          .queryParam("userId", userId)
          .queryParam("uamClientId", uamClientId)
          .request(MediaType.APPLICATION_JSON_TYPE)
          .headers(headers(config))
          .get();
      Map<String, Object> body = readSuccessful(response);
      Object status = body.get("credentialStatus");
      return status == null ? STATUS_UNKNOWN : String.valueOf(status).toUpperCase();
    } catch (java.lang.Exception e) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009");
    } finally {
      close(response);
    }
  }

  @Override
  public String setup(String partyId, String userId, String uamClientId, String password,
      String requestId) throws Exception {
    return write("SETUP", partyId, userId, uamClientId, password, requestId);
  }

  @Override
  public String reset(String partyId, String userId, String uamClientId, String password,
      String requestId) throws Exception {
    return write("RESET", partyId, userId, uamClientId, password, requestId);
  }

  private String write(String operation, String partyId, String userId, String uamClientId,
      String password, String requestId) throws Exception {
    Preferences config = configurations();
    String path = "SETUP".equals(operation)
        ? config.get(SETUP_PATH, DEFAULT_SETUP_PATH)
        : config.get(RESET_PATH, DEFAULT_RESET_PATH);
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("partyId", partyId);
    payload.put("userId", userId);
    payload.put("uamClientId", uamClientId);
    payload.put("password", password);
    payload.put("requestId", requestId);

    Response response = null;
    try {
      Invocation.Builder builder = request(config, path).request(MediaType.APPLICATION_JSON_TYPE)
          .headers(headers(config));
      builder.header("X-Idempotency-Key", requestId);
      String json = mapper.writeValueAsString(payload);
      response = "SETUP".equals(operation)
          ? builder.post(Entity.entity(json, MediaType.APPLICATION_JSON_TYPE))
          : builder.put(Entity.entity(json, MediaType.APPLICATION_JSON_TYPE));
      Map<String, Object> body = readSuccessful(response);
      Object reference = body.get("referenceNumber");
      return reference == null ? requestId : String.valueOf(reference);
    } catch (java.lang.Exception e) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009");
    } finally {
      payload.put("password", null);
      close(response);
    }
  }

  private WebTarget request(Preferences config, String path) throws Exception {
    String serviceUrl = config.get(SERVICE_URL, "").trim();
    if (serviceUrl.length() == 0 || !serviceUrl.toLowerCase().startsWith("https://")) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_009");
    }
    return CLIENT.target(serviceUrl).path(path)
        .property(ClientProperties.CONNECT_TIMEOUT, config.getInt(CONNECT_TIMEOUT, 5000))
        .property(ClientProperties.READ_TIMEOUT, config.getInt(READ_TIMEOUT, 15000));
  }

  private javax.ws.rs.core.MultivaluedMap<String, Object> headers(Preferences config) {
    Preferences dspConfig = ConfigurationFactory.getInstance().getConfigurations(DSP_CATEGORY);
    javax.ws.rs.core.MultivaluedHashMap<String, Object> headers =
        new javax.ws.rs.core.MultivaluedHashMap<String, Object>();
    headers.add("X-IBM-Client-Id",
        config.get(CLIENT_ID, dspConfig.get(DSP_CLIENT_ID, "")));
    headers.add("X-IBM-Client-Secret",
        config.get(CLIENT_SECRET, dspConfig.get(DSP_CLIENT_SECRET, "")));
    headers.add("Channel", config.get(CHANNEL, "BCO"));
    return headers;
  }

  private Map<String, Object> readSuccessful(Response response) throws java.lang.Exception {
    if (response == null || response.getStatusInfo().getFamily()
        != Response.Status.Family.SUCCESSFUL) {
      throw new java.lang.IllegalStateException("UAM HTH API password service rejected request");
    }
    String body = response.readEntity(String.class);
    if (body == null || body.trim().length() == 0) {
      return new LinkedHashMap<String, Object>();
    }
    return mapper.readValue(body, new TypeReference<Map<String, Object>>() { });
  }

  private Preferences configurations() {
    return ConfigurationFactory.getInstance().getConfigurations(CATEGORY);
  }

  private void close(Response response) {
    if (response != null) {
      response.close();
    }
  }
}
