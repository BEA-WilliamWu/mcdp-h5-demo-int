package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.ws.rs.core.Response;

/** Explicit synchronous success contract; an unknown response is never treated as success. */
final class DspApiPasswordResponsePolicy {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final Set<Integer> statuses = new HashSet<Integer>();
  private final JsonPointer pointer;
  private final JsonNode expected;

  DspApiPasswordResponsePolicy(Preferences config) throws java.lang.Exception {
    String prefix = "HTH_API_PASSWORD.DSP_";
    for (String value : config.get(prefix + "SUCCESS_HTTP_STATUSES", "200,201,204").split(",", -1)) {
      int status = Integer.parseInt(value.trim());
      // In particular, 202 means accepted for processing, not persisted.
      if (status != 200 && status != 201 && status != 204) {
        throw new IllegalArgumentException();
      }
      statuses.add(status);
    }
    String path = config.get(prefix + "SUCCESS_JSON_POINTER", "").trim();
    String match = config.get(prefix + "SUCCESS_JSON_VALUE", "").trim();
    if (path.isEmpty() && match.isEmpty()) {
      pointer = null;
      expected = null;
    } else {
      if (!path.startsWith("/") || match.isEmpty()) {
        throw new IllegalArgumentException();
      }
      pointer = JsonPointer.compile(path);
      expected = MAPPER.readTree(match);
      if (expected == null || expected.isNull() || !expected.isValueNode()) {
        throw new IllegalArgumentException();
      }
    }
  }

  boolean confirms(Response response) throws java.lang.Exception {
    if (response == null || !statuses.contains(response.getStatus())) {
      return false;
    }
    byte[] body;
    try (InputStream input = response.hasEntity() ? response.readEntity(InputStream.class) : null;
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (input != null) {
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (output.size() + count > 65536) {
            return false;
          }
          output.write(buffer, 0, count);
        }
      }
      body = output.toByteArray();
    }
    if (pointer == null) {
      // Default only accepts an empty synchronous HTTP response; no guessed JSON field names.
      return body.length == 0;
    }
    if (body.length == 0 || response.getMediaType() == null
        || !("json".equalsIgnoreCase(response.getMediaType().getSubtype())
        || response.getMediaType().getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"))) {
      return false;
    }
    JsonNode document = MAPPER.readTree(body);
    return document != null && expected.equals(document.at(pointer));
  }
}
