package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.dto.DspApiPasswordPersistRequest;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.net.URI;
import java.util.prefs.Preferences;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;

/**
 * Transport for DSP credentials/v1/persist. Both setup and reset use POST.
 *
 * <p>This client does not determine business success or consume a Code. The caller must
 * validate the confirmed DSP response contract and close the returned response. In particular,
 * a returned HTTP response alone is not proof that DSP has persisted the credential.
 */
public final class DspApiPasswordPersistClient {
  private static final String CATEGORY = "HthApiCredentialAdapterConfig";
  private static final String PREFIX = "HTH_API_PASSWORD.DSP_";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Client client;
  private final Preferences config;

  private static final class ClientHolder {
    // Use the application trust store and standard hostname verification.
    private static final Client CLIENT = ClientBuilder.newClient();
  }

  public DspApiPasswordPersistClient() {
    this(ClientHolder.CLIENT,
        ConfigurationFactory.getInstance().getConfigurations(CATEGORY));
  }

  DspApiPasswordPersistClient(Client client, Preferences config) {
    this.client = client;
    this.config = config;
  }

  /**
   * Submit once without following redirects or retrying a possibly accepted password update.
   * No request, response or credential content is included in logs or transport exceptions.
   */
  public Response send(DspApiPasswordPersistRequest request) throws TransportException {
    String stage = "CONFIG";
    try {
      URI endpoint = endpoint(required("PERSIST_URL"));
      String clientId = required("APIC_CLIENT_ID");
      String clientSecret = required("APIC_CLIENT_SECRET");
      int connectTimeout = timeout("CONNECT_TIMEOUT_MS", 5000);
      int readTimeout = timeout("READ_TIMEOUT_MS", 15000);
      stage = "SERIALIZE";
      if (request == null) {
        throw new IllegalArgumentException();
      }
      String json = MAPPER.writeValueAsString(request);
      stage = "POST";
      return client.target(endpoint)
          .property(ClientProperties.CONNECT_TIMEOUT, connectTimeout)
          .property(ClientProperties.READ_TIMEOUT, readTimeout)
          .property(ClientProperties.FOLLOW_REDIRECTS, false)
          .request(MediaType.APPLICATION_JSON_TYPE)
          .header("X-IBM-Client-Id", clientId)
          .header("X-IBM-Client-Secret", clientSecret)
          .post(Entity.entity(json, MediaType.APPLICATION_JSON_TYPE));
    } catch (java.lang.Exception failure) {
      // Upstream exception messages can contain headers, bodies or URLs. Retain only the type.
      StringBuilder types = new StringBuilder();
      Throwable cause = failure;
      for (int depth = 0; cause != null && depth < 8; depth++) {
        if (depth > 0) {
          types.append(" -> ");
        }
        types.append(cause.getClass().getName());
        cause = cause.getCause();
      }
      throw new TransportException(stage, types.toString());
    }
  }

  private String required(String key) {
    String value = config.get(PREFIX + key, "").trim();
    if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
      throw new IllegalArgumentException();
    }
    return value;
  }

  private int timeout(String key, int defaultValue) {
    int value = Integer.parseInt(config.get(PREFIX + key, String.valueOf(defaultValue)));
    if (value <= 0) {
      throw new IllegalArgumentException();
    }
    return value;
  }

  private static URI endpoint(String value) throws java.net.URISyntaxException {
    URI uri = new URI(value);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
        || uri.getRawUserInfo() != null || uri.getRawQuery() != null
        || uri.getRawFragment() != null || uri.getPath() == null
        || !uri.getPath().endsWith("/credentials/v1/persist")) {
      throw new IllegalArgumentException();
    }
    return uri;
  }

  /** Sanitized failure; callers must treat a POST-stage failure as potentially accepted. */
  public static final class TransportException extends java.lang.Exception {
    private static final long serialVersionUID = 1L;
    private final String stage;
    private final String exceptionTypes;

    private TransportException(String stage, String exceptionTypes) {
      super("DSP API password transport: stage=" + stage + ", exceptionTypes=" + exceptionTypes);
      this.stage = stage;
      this.exceptionTypes = exceptionTypes;
    }

    public String getStage() {
      return stage;
    }

    public String getExceptionTypes() {
      return exceptionTypes;
    }
  }
}
