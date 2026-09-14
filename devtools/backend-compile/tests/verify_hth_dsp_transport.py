"""Exercise the production DSP client against a local HTTPS server, with synthetic credentials.

Verifies transport and the configured response policy against controlled responses;
never invokes the bank endpoint. Transaction tests cover Code consumption separately.
"""
from pathlib import Path
import base64
import hashlib
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
LIB = ROOT / 'consulting/middleware/lib'
SOURCE = ROOT / 'consulting/middleware/projects/ext-xface/com.ofss.digx.cz.bea.extxface.app.impl/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/impl'
preferred = [LIB / 'thirdparty' / ('jackson-' + name + '-2.19.4.jar')
             for name in ('core', 'annotations', 'databind')]
preferred += [LIB / 'OBDX_FW_LIB' / name for name in (
    'jakarta.ws.rs-api-2.1.6.jar', 'jersey-client.jar', 'jersey-common.jar',
    'jersey-hk2.jar', 'hk2-api-2.6.1.jar', 'hk2-utils-2.6.1.jar',
    'hk2-locator-2.6.1.jar', 'jakarta.inject-2.6.1.jar',
    'jakarta.annotation-api-1.3.5.jar', 'javassist-3.25.0-GA.jar')]
CP = os.pathsep.join(str(path) for path in preferred + sorted(set(LIB.rglob('*.jar')) - set(preferred)))
RUNTIME_CP = os.pathsep.join(str(path) for path in preferred + [
    LIB / 'OBDX_FW_LIB/jakarta.activation-api-1.2.1.jar',
    LIB / 'OBDX_FW_LIB/aopalliance-repackaged-2.6.1.jar',
    LIB / 'OBDX_FW_LIB/osgi-resource-locator-1.0.3.jar'])

HARNESS = r'''
package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.dto.DspApiPasswordPersistRequest;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.prefs.*;
import javax.net.ssl.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

public class DspTransportTest {
  static final String PREFIX = "HTH_API_PASSWORD.DSP_";
  static final ObjectMapper MAPPER = new ObjectMapper();
  static class MemoryPreferences extends AbstractPreferences {
    final Map<String, String> values = new HashMap<>();
    MemoryPreferences() { super(null, ""); }
    protected void putSpi(String k, String v) { values.put(k, v); }
    protected String getSpi(String k) { return values.get(k); }
    protected void removeSpi(String k) { values.remove(k); }
    protected void removeNodeSpi() { values.clear(); }
    protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
    protected String[] childrenNamesSpi() { return new String[0]; }
    protected AbstractPreferences childSpi(String n) { throw new UnsupportedOperationException(); }
    protected void syncSpi() { }
    protected void flushSpi() { }
  }
  static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
  static DspApiPasswordPersistClient.TransportException expectFailure(DspApiPasswordPersistClient client,
      DspApiPasswordPersistRequest request, String stage) throws Exception {
    try {
      Response response = client.send(request);
      response.close();
      throw new AssertionError("Expected failure in " + stage);
    } catch (DspApiPasswordPersistClient.TransportException failure) {
      check(stage.equals(failure.getStage()), "Wrong failure stage");
      check(failure.getCause() == null, "Upstream exception propagated");
      check(!failure.toString().contains("synthetic-secret"), "Secret in exception");
      check(!failure.toString().contains("Example1234"), "Password in exception");
      check(!failure.toString().contains(request == null ? "not-used" : request.getPasswordHash()),
          "Hash in exception");
      return failure;
    }
  }
  static void rejectedPersist(HthDspApiCredentialAdapter adapter, boolean uncertain) throws Exception {
    try {
      adapter.setup("PARTY", "USER@PARTY", "business-client", "Example1234", "request");
      throw new AssertionError("Unconfirmed response accepted");
    } catch (com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException failure) {
      check(failure.isUncertain() == uncertain, "Wrong write outcome");
    }
  }
  public static void main(String[] args) throws Exception {
    try { run(args); }
    catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
  }
  static void run(String[] args) throws Exception {
    KeyStore keys = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(Paths.get(args[0]))) {
      keys.load(input, "test-store-only".toCharArray());
    }
    KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManager.init(keys, "test-store-only".toCharArray());
    SSLContext serverTls = SSLContext.getInstance("TLS");
    serverTls.init(keyManager.getKeyManagers(), null, null);
    HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
    ExecutorService executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    AtomicInteger status = new AtomicInteger(200), calls = new AtomicInteger(), delay = new AtomicInteger();
    AtomicReference<String> reply = new AtomicReference<>("{\"fixture\":\"uninterpreted\"}");
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    server.createContext("/hkbea/sit-int/dsp/api/auth/credentials/v1/persist", exchange -> {
      calls.incrementAndGet();
      try {
        check("POST".equals(exchange.getRequestMethod()), "Not POST");
        check(exchange.getRequestURI().getQuery() == null, "Unexpected query");
        check("gateway-test-id".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Id")),
            "Gateway ID not used");
        check("synthetic-secret".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Secret")),
            "Gateway secret not used");
        check(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"),
            "Wrong content type");
        JsonNode json = MAPPER.readTree(exchange.getRequestBody());
        check(json.size() == 3, "Wrong field count");
        check("business-client".equals(json.path("clientId").asText()), "Wrong business ID");
        check("USER@PARTY".equals(json.path("closeId").asText()), "CLOSE_ID truncated");
        check(args[1].equals(json.path("passwordHash").asText()), "Wrong hash encoding");
        if (delay.get() > 0) Thread.sleep(delay.get());
        int code = status.get();
        if (code == 302) exchange.getResponseHeaders().set("Location", exchange.getRequestURI().toString());
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] body = reply.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, code == 204 ? -1 : body.length);
        if (code != 204) exchange.getResponseBody().write(body);
      } catch (Throwable failure) {
        if (delay.get() == 0) serverFailure.set(failure);
      } finally { exchange.close(); }
    });
    server.start();
    Client http = ClientBuilder.newBuilder()
        .property("jersey.config.disableAutoDiscovery", true)
        .property("jersey.config.disableMetainfServicesLookup", true).build();
    try {
      MemoryPreferences config = new MemoryPreferences();
      String url = "https://localhost:" + server.getAddress().getPort()
          + "/hkbea/sit-int/dsp/api/auth/credentials/v1/persist";
      config.put(PREFIX + "PERSIST_URL", url);
      config.put(PREFIX + "APIC_CLIENT_ID", "gateway-test-id");
      config.put(PREFIX + "APIC_CLIENT_SECRET", "synthetic-secret");
      DspApiPasswordPersistRequest request = DspApiPasswordPersistRequest.fromPassword(
          "business-client", "USER@PARTY", "Example1234");
      DspApiPasswordPersistClient client = new DspApiPasswordPersistClient(http, config);
      for (int code : new int[] {200, 201, 204, 400, 401, 403, 500, 302}) {
        status.set(code);
        int before = calls.get();
        try (Response response = client.send(request)) {
          check(response.getStatus() == code, "HTTP status not preserved");
          if (code != 204) check(response.readEntity(String.class).contains("uninterpreted"),
              "Response was interpreted or replaced");
        }
        check(calls.get() == before + 1, "Request retried or redirect followed");
        if (serverFailure.get() != null) throw new AssertionError(serverFailure.get());
      }
      java.util.logging.Logger.getLogger(HthDspApiCredentialAdapter.class.getName()).setUseParentHandlers(false);
      HthDspApiCredentialAdapter adapter = new HthDspApiCredentialAdapter() {
        protected DspApiPasswordPersistClient client() { return client; }
        protected Preferences configuration() { return config; }
      };
      reply.set(""); status.set(201);
      check("setup-ref".equals(adapter.setup("PARTY","USER@PARTY","business-client","Example1234","setup-ref")),
          "Synchronous empty setup response rejected");
      status.set(204);
      check("reset-ref".equals(adapter.reset("PARTY","USER@PARTY","business-client","Example1234","reset-ref")),
          "Synchronous reset response rejected");
      status.set(202); rejectedPersist(adapter, true);
      status.set(200); reply.set("{\"unexpected\":true}"); rejectedPersist(adapter, true);
      config.put(PREFIX + "SUCCESS_JSON_POINTER", "/result/saved");
      config.put(PREFIX + "SUCCESS_JSON_VALUE", "true");
      reply.set("{\"result\":{\"saved\":true}}");
      check("json-ref".equals(adapter.reset("PARTY","USER@PARTY","business-client","Example1234","json-ref")),
          "Configured JSON match rejected");
      for (String body : new String[] {"{\"result\":{\"saved\":false}}", "{\"other\":true}", "<html>error</html>",
          "{\"result\":{\"saved\":true}} {}", ""}) {
        reply.set(body); rejectedPersist(adapter, true);
      }
      reply.set("{\"result\":{\"saved\":true}}");
      for (int failure : new int[] {400,401,403,404,500,302,202}) {
        status.set(failure); rejectedPersist(adapter, true);
      }
      int unchanged = calls.get();
      config.put(PREFIX + "SUCCESS_HTTP_STATUSES", "202");
      rejectedPersist(adapter, false);
      config.remove(PREFIX + "SUCCESS_HTTP_STATUSES");
      config.remove(PREFIX + "APIC_CLIENT_SECRET");
      rejectedPersist(adapter, false);
      config.put(PREFIX + "APIC_CLIENT_SECRET", "synthetic-secret");
      check(calls.get() == unchanged, "Invalid configuration transmitted a write");
      if (serverFailure.get() != null) throw new AssertionError(serverFailure.get());
      int before = calls.get();
      for (String invalid : new String[] {"http://localhost/credentials/v1/persist",
          "https://user:pass@localhost/credentials/v1/persist", url + "?password=x",
          url + "#fragment", "https://localhost/other", "https://", ""}) {
        config.put(PREFIX + "PERSIST_URL", invalid);
        expectFailure(client, request, "CONFIG");
      }
      config.put(PREFIX + "PERSIST_URL", url);
      for (String key : new String[] {"APIC_CLIENT_ID", "APIC_CLIENT_SECRET"}) {
        String original = config.get(PREFIX + key, "");
        config.remove(PREFIX + key);
        expectFailure(client, request, "CONFIG");
        config.put(PREFIX + key, "bad\r\nvalue");
        expectFailure(client, request, "CONFIG");
        config.put(PREFIX + key, original);
      }
      for (String invalid : new String[] {"0", "-1", "invalid"}) {
        config.put(PREFIX + "READ_TIMEOUT_MS", invalid);
        expectFailure(client, request, "CONFIG");
      }
      config.remove(PREFIX + "READ_TIMEOUT_MS");
      expectFailure(client, null, "SERIALIZE");
      check(calls.get() == before, "Invalid input caused a POST");

      // A new empty trust store really rejects the local server certificate.
      KeyStore empty = KeyStore.getInstance("PKCS12"); empty.load(null, null);
      TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trust.init(empty);
      SSLContext untrusted = SSLContext.getInstance("TLS");
      untrusted.init(null, trust.getTrustManagers(), null);
      Client strict = ClientBuilder.newBuilder().sslContext(untrusted)
          .property("jersey.config.disableAutoDiscovery", true)
          .property("jersey.config.disableMetainfServicesLookup", true).build();
      try {
        check(expectFailure(new DspApiPasswordPersistClient(strict, config), request, "POST")
            .getExceptionTypes().contains("javax.net.ssl."), "Failure did not come from TLS");
      } finally { strict.close(); }
      check(calls.get() == before, "Untrusted certificate accepted");
      status.set(200); delay.set(400);
      config.put(PREFIX + "READ_TIMEOUT_MS", "100");
      check(expectFailure(client, request, "POST").getExceptionTypes().contains("SocketTimeoutException"),
          "Failure did not come from timeout");
      check(calls.get() == before + 1, "Timed-out POST retried");
      System.out.println("PASS: real HTTPS POST, exact fields/hash/headers, status preservation, no redirects/retries, validation, TLS rejection, timeout, sanitized errors and setup/reset response policy");
    } finally {
      http.close(); server.stop(0); executor.shutdownNow();
    }
  }
}
'''

def checked(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=40)
    if result.returncode:
        print((result.stdout + result.stderr)[-5000:])
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth-dsp-https-') as temporary:
    tmp = Path(temporary)
    store = tmp / 'test.p12'
    checked([str(JAVA / 'keytool'), '-genkeypair', '-alias', 'local-test',
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                    '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1',
                    '-storetype', 'PKCS12', '-keystore', str(store),
                    '-storepass', 'test-store-only', '-keypass', 'test-store-only', '-noprompt'])
    harness = tmp / 'DspTransportTest.java'
    harness.write_text(HARNESS)
    common = ROOT / 'consulting/middleware/projects/common/com.ofss.digx.cz.bea.extxface/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter'
    checked([str(JAVA / 'javac'), '--release', '8', '-proc:none', '-encoding', 'UTF-8',
                    '-cp', CP, '-d', temporary,
                    str(SOURCE / 'DspApiPasswordPersistClient.java'),
                    str(SOURCE / 'dto/DspApiPasswordPersistRequest.java'),
                    str(SOURCE / 'DspApiPasswordResponsePolicy.java'),
                    str(SOURCE / 'HthDspApiCredentialAdapter.java'),
                    str(common / 'IHthApiCredentialAdapter.java'),
                    str(common / 'HthApiCredentialWriteException.java')])
    # Replace only the bank exception bootstrap; production policy, adapter and HTTP are unchanged.
    exception = tmp / 'Exception.java'
    exception.write_text('package com.ofss.digx.infra.exceptions; public class Exception extends java.lang.Exception { public Exception(String code) { super(code); } }')
    checked([str(JAVA / 'javac'), '--release', '8', '-proc:none', '-d', temporary, str(exception)])
    # The local HTTPS harness uses the JDK httpserver test module; production remains Java 8.
    checked([str(JAVA / 'javac'), '-proc:none', '-encoding', 'UTF-8',
                    '-cp', temporary + os.pathsep + CP, '-d', temporary, str(harness)])
    expected = base64.b64encode(hashlib.sha256(b'Example1234').digest()).decode('ascii')
    try:
        result = subprocess.run([str(JAVA / 'java'), '-Djavax.net.ssl.trustStore=' + str(store),
                             '-Djavax.net.ssl.trustStorePassword=test-store-only',
                             '-cp', temporary + os.pathsep + RUNTIME_CP,
                             'com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.DspTransportTest',
                             str(store), expected], capture_output=True, text=True, timeout=40)
    except subprocess.TimeoutExpired as failure:
        print(((failure.stdout or b'') + (failure.stderr or b'')).decode('utf-8', errors='replace')[-5000:])
        raise SystemExit('DSP transport test timed out')
    print((result.stdout + result.stderr)[-5000:])
    raise SystemExit(result.returncode)
