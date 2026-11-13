from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
LIB = ROOT / 'consulting/middleware/lib'
SOURCE = ROOT / 'consulting/middleware/projects/ext-xface/com.ofss.digx.cz.bea.extxface.app.impl/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/impl'
preferred = [LIB / 'thirdparty' / ('jackson-' + name + '-2.19.4.jar')
             for name in ('core', 'annotations', 'databind')]
preferred += [LIB / 'thirdparty' / name for name in (
    'jackson-datatype-jsr310-2.19.4.jar', 'jakarta.ws.rs-api-3.1.0.jar',
    'h2h-common-client-0.1.0.jar', 'h2h-common-api-0.1.0.jar',
    'dsp-ms-support-api-0.1.0.jar', 'dsp-ms-support-client-0.1.0.jar',
    'feign-core-13.6.jar', 'feign-jackson-13.6.jar', 'feign-jaxrs-13.6-jakarta.jar',
    'feign-okhttp-13.6.jar', 'feign-slf4j-13.6.jar', 'slf4j-api-2.0.17.jar',
    'okhttp-4.12.0.jar', 'logging-interceptor-4.12.0.jar', 'okio-jvm-3.6.0.jar',
    'kotlin-stdlib-1.9.25.jar', 'kotlin-stdlib-jdk8-1.9.25.jar',
    'kotlin-stdlib-jdk7-1.9.25.jar', 'fastjson2-2.0.61.jar')]
CP = os.pathsep.join(str(path) for path in preferred + sorted(set(LIB.rglob('*.jar')) - set(preferred)))
RUNTIME_CP = os.pathsep.join(str(path) for path in preferred)
SDK_SOURCE = SOURCE.parents[2] / 'microservice/api/dsp'
LOGGER_SOURCE = ROOT / 'consulting/middleware/projects/framework/com.ofss.digx.cz.bea.appcore.logger/src/com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java'
SERVICE_SOURCE = ROOT / 'consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java'
LIFECYCLE_LOG_METHOD = re.search(r'  private static void logPhaseFailure\(.*?\n  }', SERVICE_SOURCE.read_text(), re.S)[0]

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
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import javax.net.ssl.*;
import feign.Response;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.dsp.model.ClientValidationParam;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.DspClient;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.factory.DSPApiClientFactory;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.impl.DSPCommonHeaderImpl;
import com.ofss.fc.infra.config.ConfigurationFactory;

public class DspTransportTest {
  __LIFECYCLE_LOG_METHOD__
  static final String PREFIX="DSP.";
  static final ObjectMapper MAPPER=new ObjectMapper();
  static final List<String> diagnostics=new ArrayList<>();
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
  static void failsLogged(Attempt action,boolean uncertain,String... expected) throws Exception {
    int before=diagnostics.size();fails(action,uncertain);
    check(diagnostics.size()==before+1,"Expected one safe diagnostic for DSP failure");
    String diagnostic=diagnostics.get(before);
    for(String field:expected)check(diagnostic.contains(field),"Missing diagnostic field: "+field);
  }
  public static void main(String[] args) throws Exception {
    PrintStream originalOut=System.out;
    System.setOut(new PrintStream(originalOut,true,"UTF-8"){
      @Override public void println(String line){
        if(line.contains("HTH_API_PASSWORD")){
          check(line.startsWith("[< fixture-trace-1955 >] "),"Missing common logger request trace");
          diagnostics.add(line);
        }else{super.println(line);}
      }
    });
    logPhaseFailure("DSP_ADAPTER",new IOException("sensitive-fixture-message",
        new IllegalStateException("sensitive-fixture-message")));
    check(diagnostics.size()==1 && diagnostics.get(0).contains("stage=DSP_ADAPTER")
        && diagnostics.get(0).contains("java.io.IOException -> java.lang.IllegalStateException"),
        "Lifecycle helper lost stage or nested exception types");
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
    Set<String> messages=ConcurrentHashMap.newKeySet();
    AtomicInteger clientValidationCalls=new AtomicInteger(),clientExistenceCalls=new AtomicInteger();
    AtomicReference<Throwable> assertion=new AtomicReference<>();
    server.createContext("/", exchange->{
      try {
        calls.incrementAndGet();
        check("POST".equals(exchange.getRequestMethod()),"POST required");
        check("BCO".equals(exchange.getRequestHeaders().getFirst("Channel")),"Missing Channel");
        String messageId=exchange.getRequestHeaders().getFirst("Message-Id");
        UUID.fromString(messageId);
        check(messages.add(messageId),"Message-Id reused after header reset");
        check("fixture-id".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Id")),"Missing subscription ID");
        check("fixture-secret".equals(exchange.getRequestHeaders().getFirst("X-IBM-Client-Secret")),"Missing subscription secret");
        request.set(MAPPER.readTree(exchange.getRequestBody()));
        check(exchange.getRequestURI().getPath().startsWith("/catalog/shared/"),"APIC base path lost");
        if(exchange.getRequestURI().getPath().endsWith("/client/v1/validate")){
          clientValidationCalls.incrementAndGet();
          byte[] valid="{\"clientId\":\"business-client\",\"valid\":true,\"validationTime\":1}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type","application/json");
          exchange.sendResponseHeaders(200,valid.length);exchange.getResponseBody().write(valid);return;
        }
        if(exchange.getRequestURI().getPath().endsWith("/client/v1/exists")){
          clientExistenceCalls.incrementAndGet();
          byte[] exists="{\"exists\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type","application/json");
          exchange.sendResponseHeaders(200,exists.length);exchange.getResponseBody().write(exists);return;
        }
        check(!request.get().has("protocol") && !request.get().has("queryOnly")
            && !request.get().has("requestId"),"Private DSP protocol leaked");
        if (exchange.getRequestURI().getPath().endsWith("/public-key")) {
          check(request.get().size()==1 && request.get().has("hashValue"),"Unexpected public-key field");
        }
        check(!request.get().has("password") && !request.get().has("passwordHash"),"CM leaked password/hash to HTTP body");
        if(delay.get()>0)Thread.sleep(delay.get());
        byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.getResponseHeaders().set("Location","https://127.0.0.1:"+server.getAddress().getPort()+"/password/v1/modification");
        exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);
      }catch(Throwable error){if(!(error instanceof IOException))assertion.set(error);}
      finally {exchange.close();}
    });
    ExecutorService executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.start();
    try {
      MemoryPreferences config=new MemoryPreferences();
      // Pin the loopback address: disabled retries must not select an unbound IPv6 localhost route.
      String url="https://127.0.0.1:"+server.getAddress().getPort()+"/catalog/shared";
      // Controls: this server is neither trusted by the default JVM nor named localhost.
      // A normal client must reject it; the original BCO DSP transport must still work.
      okhttp3.Request tlsProbe=new okhttp3.Request.Builder().url(url).build();
      okhttp3.OkHttpClient strict=new okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).build();
      try (okhttp3.Response ignored=strict.newCall(tlsProbe).execute()) {
        throw new AssertionError("Fixture certificate unexpectedly trusted by default JVM");
      } catch (SSLHandshakeException expected) { }
      TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trust.init(keys);
      SSLContext trusted=SSLContext.getInstance("TLS");trusted.init(null,trust.getTrustManagers(),null);
      okhttp3.OkHttpClient verifyHostname=strict.newBuilder()
          .sslSocketFactory(trusted.getSocketFactory(),(X509TrustManager)trust.getTrustManagers()[0]).build();
      try (okhttp3.Response ignored=verifyHostname.newCall(tlsProbe).execute()) {
        throw new AssertionError("Fixture hostname unexpectedly accepted by default verifier");
      } catch (SSLPeerUnverifiedException expected) { }
      check(calls.get()==0,"TLS control unexpectedly reached HTTP handler");
      config.put(PREFIX+"SERVICE_URL",url+"/");
      config.put(PREFIX+"APIC_CLIENT_ID","fixture-id"); config.put(PREFIX+"APIC_CLIENT_SECRET","fixture-secret");
      // Leave DSP.CHANNEL unset to verify the original BCO default.
      MemoryPreferences inq01=new MemoryPreferences();
      inq01.put("H2H.CLIENT_ID","unrelated-inquiry-id");inq01.put("H2H.CLIENT_SECRET","unrelated-inquiry-secret");
      inq01.put("H2H.SERVICE_URL","https://unrelated-inquiry.invalid");
      ConfigurationFactory.preferences=config;ConfigurationFactory.inq01=inq01;
      DspClient client=DSPApiClientFactory.getDspClient();
      check(client==DSPApiClientFactory.getDspClient(),"Factory did not reuse SDK client");
      CommonHeader header=client.getCommonHeader();
      // Existing BM validation works before password calls and still works afterwards.
      ClientValidationParam bmBefore=new ClientValidationParam();bmBefore.setClientId("business-client");
      check(Boolean.TRUE.equals(client.clientApi().validateClientId(bmBefore).getValid()),"BM validation changed");
      header.reset();
      check(Boolean.TRUE.equals(client.clientExistenceApi().checkClientIdExists(bmBefore).get("exists")),"BM exists failed with original TLS transport");
      header.reset();
      HthDspApiCredentialAdapter adapter=new HthDspApiCredentialAdapter(client);
      int initial=calls.get();
      check("UNKNOWN".equals(adapter.getStatus("business-client","USER@PARTY")),"Invented credential state");
      for(String operation:new String[]{"SETUP","RESET"}){ adapter.requireWriteSupported(operation); }
      fails(()->adapter.requireWriteSupported("OTHER"),false);
      check(calls.get()==initial,"Operation validation issued an HTTP request");
      String nonceHash="77d803e4073d578f460f6b48b1c755285b0766a4fb5fae39e510b68005d88c57";
      response.set("{\"status\":\"000000\",\"publicKey\":\"test-public-key\"}");
      header.setAccessToken("fixture-token");
      check("test-public-key".equals(adapter.publicKey(nonceHash)),"Key missing");
      check(header.getAccessToken()==null,"Success did not reset thread headers");
      check(request.get().size()==1 && nonceHash.equals(request.get().path("hashValue").asText()),"Nonce hash changed");
      int before=calls.get();
      fails(()->adapter.publicKey("invalid"),false);
      check(calls.get()==before,"Invalid nonce hash sent");
      Attempt query=()->adapter.publicKey(nonceHash);
      for(int code:new int[]{400,401,403,404,405,422,450,460,470,490,500,502,302}){
        status.set(code);before=calls.get();header.setAccessToken("fixture-token");
        failsLogged(query,false,"operation=PUBLIC_KEY","reason=HTTP_STATUS","httpStatus="+code);
        check(header.getAccessToken()==null,"HTTP failure did not reset thread headers");
        check(calls.get()==before+1,"Unexpected redirect or retry");
      }
      status.set(200);
      for(String malformed:new String[]{"{}","","not-json","{\"status\":\"SUCCESS\"}","{\"status\":\"000000\"}"}){
        response.set(malformed);
        failsLogged(query,false,"operation=PUBLIC_KEY","httpStatus=200",
            "not-json".equals(malformed)?"reason=EXCEPTION phase=READ_BODY":
            "{\"status\":\"000000\"}".equals(malformed)?"reason=MISSING_PUBLIC_KEY":"reason=INVALID_STATUS");
      }
      response.set("{\"status\":\"000000\",\"publicKey\":\"test-public-key\"}");
      for(String badUrl:new String[]{"http://localhost", "https://user@localhost", url+"?query=1", url+"#fragment"}){
        before=calls.get();DspClient invalid=new DspClient();invalid.setServiceUrl(badUrl);invalid.setCommonHeader(header);
        failsLogged(()->new HthDspApiCredentialAdapter(invalid).publicKey(nonceHash),false,
            "operation=PUBLIC_KEY","reason=EXCEPTION phase=REQUEST","httpStatus=NONE","IllegalArgumentException");
        check(calls.get()==before,"Invalid URL invoked");
      }
      before=calls.get();
      DspClient slow=new DspClient(){
        protected okhttp3.OkHttpClient getOkHttpClient(){return super.getOkHttpClient().newBuilder()
          .readTimeout(1000,TimeUnit.MILLISECONDS).build();}
      };
      slow.setServiceUrl(url);slow.setCommonHeader(header);
      delay.set(2500);
      failsLogged(()->new HthDspApiCredentialAdapter(slow).publicKey(nonceHash),false,
          "operation=PUBLIC_KEY","reason=EXCEPTION phase=REQUEST","httpStatus=NONE","SocketTimeoutException");
      check(calls.get()==before+1,"Timeout retried");
      delay.set(0);response.set("{\"status\":\"000000\"}");
      for(String operation:new String[]{"SETUP","RESET"}){
        adapter.modify("business-client","USER@PARTY",UUID.randomUUID().toString(),operation,
            "fixture-ciphertext","314159");
        check(("SETUP".equals(operation)?"A":"M").equals(request.get().path("function").asText()),"Wrong function");
        check(request.get().size()==5 && !request.get().has("oldEncryptedPassword"),"Reset must not require old password");
      }
      Attempt write=()->adapter.modify("business-client","USER@PARTY",UUID.randomUUID().toString(),"RESET","fixture-ciphertext","314159");
      for(int code:new int[]{400,401,403,404,450,460}) {
        status.set(code);failsLogged(write,false,"operation=MODIFICATION","reason=HTTP_STATUS","httpStatus="+code);
      }
      for(int code:new int[]{470,500,502,302}) { status.set(code);before=calls.get();fails(write,true);check(calls.get()==before+1,"Write retried or redirected"); }
      status.set(200);
      for(String bad:new String[]{"{}","not-json","{\"status\":\"FAILED\"}"}) {response.set(bad);fails(write,true);}
      response.set("{\"status\":\"000000\"}");
      delay.set(2500);before=calls.get();
      failsLogged(()->new HthDspApiCredentialAdapter(slow).modify("business-client","USER@PARTY","request","RESET","fixture-ciphertext","314159"),true,
          "operation=MODIFICATION","reason=EXCEPTION phase=REQUEST","SocketTimeoutException");
      check(calls.get()==before+1,"Password timeout request count: before="+before+", after="+calls.get()+", handlerFailure="+assertion.get()+", diagnostic="+diagnostics.get(diagnostics.size()-1));delay.set(0);
      // Existing SDK client validation still uses its original interface and route.
      ClientValidationParam validate=new ClientValidationParam();validate.setClientId("business-client");
      check(Boolean.TRUE.equals(client.clientApi().validateClientId(validate).getValid()),"Existing SDK API failed");
      header.reset();check(clientValidationCalls.get()==2,"Existing client route changed");
      check(Boolean.TRUE.equals(client.clientExistenceApi().checkClientIdExists(validate).get("exists")),"BM exists failed after password calls");
      header.reset();check(clientExistenceCalls.get()==2,"Existing exists route changed");
      // Separate instances must not inherit the first password proxy's URL from the SDK's static cache.
      DspClient separate=new DspClient();separate.setServiceUrl("http://localhost");separate.setCommonHeader(header);
      fails(()->new HthDspApiCredentialAdapter(separate).publicKey(nonceHash),false);
      check(assertion.get()==null,"Server request assertion failed: "+assertion.get());
      String allDiagnostics=String.join("\n",diagnostics);
      for(String secret:new String[]{nonceHash,"fixture-id","fixture-secret","fixture-token",
          "fixture-ciphertext","business-client","USER@PARTY","314159","test-public-key",
          "hashValue","newEncryptedPassword","not-json","http://","https://","sensitive-fixture-message"}) {
        check(!allDiagnostics.contains(secret),"Sensitive input or response leaked to diagnostic log");
      }
      System.out.println("PASS: original DSP TLS behavior: untrusted certificate and mismatched hostname accepted for BM validate/exists and both password APIs, without JVM trustStore override; strict-client controls reject both cases");
      System.out.println("PASS: shared BeaSystemOut request trace retained for adapter and lifecycle diagnostics; safe DSP diagnostics distinguish HTTP status, malformed/missing result, missing public key, local configuration and timeout; no request/body/header/secret logging");
      System.out.println("PASS: SDK factory, original BM DSP base URL/authentication, unrelated INQ01 configuration ignored, public-key and A/M HTTPS routes, header cleanup, strict errors, no retry/redirect, existing client validation; no-old-password SETUP/RESET writes and definite/uncertain failure classification");
    }finally{server.stop(0);executor.shutdownNow();}
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
                    '-dname', 'CN=fixture.invalid', '-ext', 'SAN=dns:fixture.invalid',
                    '-storetype', 'PKCS12', '-keystore', str(store),
                    '-storepass', 'test-store-only', '-keypass', 'test-store-only', '-noprompt'])
    harness = tmp / 'DspTransportTest.java'
    harness.write_text(HARNESS.replace('__LIFECYCLE_LOG_METHOD__', LIFECYCLE_LOG_METHOD))
    common = ROOT / 'consulting/middleware/projects/common/com.ofss.digx.cz.bea.extxface/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter'
    checked([str(JAVA / 'javac'), '--release', '8', '-proc:none', '-encoding', 'UTF-8',
                    '-cp', CP, '-d', temporary,
                    *[str(path) for path in SDK_SOURCE.rglob('*.java')],
                    str(SOURCE / 'HthDspApiCredentialAdapter.java'), str(LOGGER_SOURCE),
                    str(common / 'IHthApiCredentialAdapter.java'),
                    str(common / 'HthApiCredentialWriteException.java')])
    # Replace only the bank exception bootstrap; production policy, adapter and HTTP are unchanged.
    exception = tmp / 'Exception.java'
    exception.write_text('package com.ofss.digx.infra.exceptions; public class Exception extends java.lang.Exception { public Exception(String code) { super(code); } }')
    checked([str(JAVA / 'javac'), '--release', '8', '-proc:none', '-d', temporary, str(exception)])
    # Replace only environment-owned preference/thread storage, retaining production factory/header code.
    config_stub=tmp/'ConfigurationFactory.java'
    config_stub.write_text('''package com.ofss.fc.infra.config;
public class ConfigurationFactory {
 public static java.util.prefs.Preferences preferences,inq01;
 public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
 public java.util.prefs.Preferences getConfigurations(String category){
  if("DSPApiAdapterImplConfig".equals(category))return preferences;
  throw new AssertionError("Wrong configuration category");
 }
}''')
    thread_stub=tmp/'ThreadAttribute.java'
    thread_stub.write_text('''package com.ofss.fc.infra.thread;
public class ThreadAttribute {
 private static final ThreadLocal<java.util.Map<String,Object>> VALUES=ThreadLocal.withInitial(java.util.HashMap::new);
 public static Object get(String key){return VALUES.get().get(key);}
 public static void set(String key,Object value){if(value==null)VALUES.get().remove(key);else VALUES.get().put(key,value);}
}''')
    trace_stub=tmp/'ThreadContext.java'
    trace_stub.write_text('''package com.ofss.fc.infra.thread;
public class ThreadContext {
 public static ThreadContext getThreadInstance(){return new ThreadContext();}
 public String getTraceReferenceNumber(){return "fixture-trace-1955";}
}''')
    checked([str(JAVA/'javac'),'--release','8','-proc:none','-d',temporary,str(config_stub),str(thread_stub),str(trace_stub)])
    # The local HTTPS harness uses the JDK httpserver test module; production remains Java 8.
    checked([str(JAVA / 'javac'), '-proc:none', '-encoding', 'UTF-8',
                    '-cp', temporary + os.pathsep + CP, '-d', temporary, str(harness)])
    try:
        result = subprocess.run([str(JAVA / 'java'),
                             '-cp', temporary + os.pathsep + RUNTIME_CP,
                             'com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.DspTransportTest',
                             str(store)], capture_output=True, text=True, timeout=40)
    except subprocess.TimeoutExpired as failure:
        print(((failure.stdout or b'') + (failure.stderr or b'')).decode('utf-8', errors='replace')[-5000:])
        raise SystemExit('DSP transport test timed out')
    print((result.stdout + result.stderr)[-5000:])
    raise SystemExit(result.returncode)
