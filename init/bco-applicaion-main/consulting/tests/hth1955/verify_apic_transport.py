from pathlib import Path
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

import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
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
  static final String PREFIX="HTH_API_PASSWORD.DSP_";
  static final ObjectMapper MAPPER=new ObjectMapper();
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
  static void check(boolean ok,String message){ if(!ok) throw new AssertionError(message); }
  interface Attempt { void run() throws Exception; }
  static void fails(Attempt action,boolean uncertain) throws Exception {
    try { action.run(); throw new AssertionError("Unconfirmed DSP response accepted"); }
    catch(HthApiCredentialWriteException failure){ check(failure.isUncertain()==uncertain,"Wrong uncertainty classification"); }
  }
  public static void main(String[] args) throws Exception {
    KeyStore keys=KeyStore.getInstance("PKCS12");
    try(InputStream input=Files.newInputStream(Paths.get(args[0]))){keys.load(input,"test-store-only".toCharArray());}
    KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(keys,"test-store-only".toCharArray());
    SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
    HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
    AtomicInteger calls=new AtomicInteger(),status=new AtomicInteger(200),delay=new AtomicInteger();
    AtomicReference<String> response=new AtomicReference<>("{\"status\":\"000000\"}");
    AtomicReference<JsonNode> request=new AtomicReference<>();
    AtomicReference<Throwable> assertion=new AtomicReference<>();
    server.createContext("/", exchange->{
      try {
        calls.incrementAndGet();
        check("POST".equals(exchange.getRequestMethod()),"POST required");
        check("BCO".equals(exchange.getRequestHeaders().getFirst("Channel")),"Missing Channel");
        UUID.fromString(exchange.getRequestHeaders().getFirst("Message-Id"));
        check("fixture-id".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Id")),"Missing subscription ID");
        check("fixture-secret".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Secret")),"Missing subscription secret");
        request.set(MAPPER.readTree(exchange.getRequestBody()));
        check(!request.get().has("protocol") && !request.get().has("queryOnly")
            && !request.get().has("requestId"),"Private DSP protocol leaked");
        if (exchange.getRequestURI().getPath().endsWith("/public-key")) {
          check(request.get().size()==1 && request.get().has("hashValue"),"Unexpected public-key field");
        }
        check(!request.get().has("password") && !request.get().has("passwordHash"),"CM leaked password/hash to HTTP body");
        if(delay.get()>0)Thread.sleep(delay.get());
        byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.getResponseHeaders().set("Location","https://localhost:"+server.getAddress().getPort()+"/password/v1/modification");
        exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);
      }catch(Throwable error){if(!(error instanceof IOException))assertion.set(error);}
      finally {exchange.close();}
    });
    ExecutorService executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.start();
    Client http=ClientBuilder.newBuilder().property("jersey.config.disableAutoDiscovery",true)
      .property("jersey.config.disableMetainfServicesLookup",true).build();
    try {
      MemoryPreferences config=new MemoryPreferences();String url="https://localhost:"+server.getAddress().getPort();
      config.put(PREFIX+"PUBLIC_KEY_URL",url+"/password/v1/public-key");
      config.put(PREFIX+"MODIFICATION_URL",url+"/password/v1/modification");
      config.put(PREFIX+"APIC_CLIENT_ID","fixture-id"); config.put(PREFIX+"APIC_CLIENT_SECRET","fixture-secret");
      DspApiPasswordClient client=new DspApiPasswordClient(http,config);
      HthDspApiCredentialAdapter adapter=new HthDspApiCredentialAdapter(client);
      int initial=calls.get();
      check("UNKNOWN".equals(adapter.getStatus("business-client","USER@PARTY")),"Invented credential state");
      for(String operation:new String[]{"SETUP","RESET"}){
        fails(()->adapter.requireWriteSupported(operation),false);
        fails(()->adapter.modify("business-client","USER@PARTY",UUID.randomUUID().toString(),operation,
              "synthetic-ciphertext","314159"),false);
      }
      check(calls.get()==initial,"Unsupported operation issued an HTTP request");
      String nonceHash="77d803e4073d578f460f6b48b1c755285b0766a4fb5fae39e510b68005d88c57";
      response.set("{\"status\":\"000000\",\"publicKey\":\"test-public-key\"}");
      check("test-public-key".equals(adapter.publicKey(nonceHash)),"Key missing");
      check(request.get().size()==1 && nonceHash.equals(request.get().path("hashValue").asText()),"Nonce hash changed");
      int before=calls.get();
      fails(()->adapter.publicKey("invalid"),false);
      check(calls.get()==before,"Invalid nonce hash sent");
      Attempt query=()->adapter.publicKey(nonceHash);
      for(int code:new int[]{400,401,403,404,405,422,450,460,470,490,500,502,302}){
        status.set(code);before=calls.get();fails(query,false);
        check(calls.get()==before+1,"Unexpected redirect or retry");
      }
      status.set(200);
      for(String malformed:new String[]{"{}","","not-json","{\"status\":\"SUCCESS\"}","{\"status\":\"000000\"}"}){
        response.set(malformed);fails(query,false);
      }
      response.set("{\"status\":\"000000\",\"publicKey\":\"test-public-key\"}");
      before=calls.get();config.put(PREFIX+"PUBLIC_KEY_URL","http://localhost/password/v1/public-key");
      fails(query,false); check(calls.get()==before,"Insecure URL invoked");
      config.put(PREFIX+"PUBLIC_KEY_URL",url+"/password/v1/public-key");
      config.put(PREFIX+"READ_TIMEOUT_MS","100");delay.set(400);fails(query,false);
      check(calls.get()==before+1,"Timeout retried");
      // Low-level APIC plumbing for the documented A/M endpoint only. Not a CM password write:
      // synthetic data is sent to this local fixture; the CM adapter remains blocked above.
      config.put(PREFIX+"READ_TIMEOUT_MS","15000");delay.set(0);response.set("{\"status\":\"000000\"}");
      for(String function:new String[]{"A","M"}){
        Map<String,Object> body=new LinkedHashMap<>();body.put("function",function);
        body.put("newEncryptedPassword","fixture-ciphertext");body.put("randomNumber","314159");
        body.put("clientId","business-client");body.put("closeId","USER@PARTY");
        if("M".equals(function))body.put("oldEncryptedPassword","fixture-old-ciphertext");
        try(Response result=client.send(body,true)){check(result.getStatus()==200,"Modification route transport failed");}
        check(function.equals(request.get().path("function").asText()),"Documented function changed");
      }
      check(assertion.get()==null,"Server request assertion failed");
      System.out.println("PASS: provider public-key contract, APIC headers, A/M transport fixture, strict response, no retry/redirect; UNKNOWN status and SETUP/RESET blocked without HTTP writes");
    }finally{http.close();server.stop(0);executor.shutdownNow();}
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
                    str(SOURCE / 'DspApiPasswordClient.java'),
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
    try:
        result = subprocess.run([str(JAVA / 'java'), '-Djavax.net.ssl.trustStore=' + str(store),
                             '-Djavax.net.ssl.trustStorePassword=test-store-only',
                             '-cp', temporary + os.pathsep + RUNTIME_CP,
                             'com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.DspTransportTest',
                             str(store)], capture_output=True, text=True, timeout=40)
    except subprocess.TimeoutExpired as failure:
        print(((failure.stdout or b'') + (failure.stderr or b'')).decode('utf-8', errors='replace')[-5000:])
        raise SystemExit('DSP transport test timed out')
    print((result.stdout + result.stderr)[-5000:])
    raise SystemExit(result.returncode)
