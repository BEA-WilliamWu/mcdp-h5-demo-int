"""Regression checks for HTH audit visibility and the DSP exists contract.
Run with JAVA_HOME pointing to JDK 21 (production is compiled with --release 8):
    python3 consulting/tests/hth_onboarding/verify_audit_dropdowns_and_client_exists.py
Requires repository middleware libraries and existing compiled BCO dependencies.
Uses actual production bytecode, SDK, DTOs and bundles. Only container/config/
logging boundaries are substituted. HTTPS calls stay on localhost; no bank DB/API.
"""
from pathlib import Path
import os
import subprocess
import tempfile

R = Path(__file__).resolve().parents[3]
J = Path(os.environ['JAVA_HOME']) / 'bin'


def run(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True, timeout=90)
    print((result.stdout + result.stderr)[-6000:])
    if result.returncode:
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth-audit-exists-') as temporary:
    W = Path(temporary)
    P = R / 'consulting/middleware/projects'
    lib = R / 'consulting/middleware/lib'
    # Prefer the SDK's Jackson/JAX-RS versions to the older platform copies.
    preferred = [lib / 'thirdparty' / ('jackson-' + n + '-2.19.4.jar')
                 for n in ('core', 'annotations', 'databind')]
    preferred += [lib / 'thirdparty' / n for n in (
        'jackson-datatype-jsr310-2.19.4.jar', 'jakarta.ws.rs-api-3.1.0.jar',
        'slf4j-api-2.0.17.jar')]
    classes = [p for p in (R / 'consulting/dist/classes/middleware/projects').glob('*/*') if p.is_dir()]
    CP = os.pathsep.join(map(str, preferred + classes + sorted(set(lib.rglob('*.jar')) - set(preferred))))
    (W / 'prod').mkdir()
    sources = []
    for name in ('HthAuditTaskVisibility.java', 'CZTaskExt.java', 'DspClientExistenceApi.java',
                 'DspClient.java', 'DspPasswordApi.java', 'HostToHostManagement.java', 'BeaSystemOut.java'):
        sources += [p for p in P.rglob(name) if '/src/' in str(p) and '/endpoint/' not in str(p)]
    sources += list(P.glob('module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/app/common/service/transactionlist/TransactionList.java'))
    run([J / 'javac', '--release', '8', '-proc:none', '-encoding', 'UTF-8',
         '-sourcepath', os.pathsep.join(str(p) for p in P.glob('*/*/src')),
         '-cp', CP, '-d', W / 'prod', *sources])
    F = W / 'fixtures'
    F.mkdir()
    files = []

    def put(name, source):
        path = F / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        files.append(path)

    put('com/ofss/fc/infra/log/impl/MultiEntityLogger.java','''package com.ofss.fc.infra.log.impl;
    public class MultiEntityLogger {
     public static MultiEntityLogger getUniqueInstance(){return new MultiEntityLogger();}
     public java.util.logging.Logger getLogger(String n){return java.util.logging.Logger.getLogger(n);}
     public String formatMessage(String m,Object...a){return m;}
    }''')
    put('com/ofss/digx/app/AbstractApplication.java','''package com.ofss.digx.app;
    public class AbstractApplication {
     public com.ofss.fc.service.response.TransactionStatus fetchTransactionStatus(){return null;}
     public void fillTransactionStatus(com.ofss.fc.service.response.TransactionStatus s,java.lang.Exception e){throw new AssertionError(e);}
    }''')
    put('com/ofss/digx/app/Interaction.java','''package com.ofss.digx.app;
    public class Interaction {public static void begin(com.ofss.fc.app.context.SessionContext s){} public static void close(){} }''')
    put('com/ofss/fc/infra/locale/LocaleUtils.java','''package com.ofss.fc.infra.locale;
    public class LocaleUtils {public static java.util.Locale getUserLocale(){return java.util.Locale.ENGLISH;} public static java.util.Locale getDefaultLocale(){return java.util.Locale.ENGLISH;} }''')
    put('com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java','''package com.ofss.digx.cz.bea.app.logger;
    public class BeaSystemOut {public static void println(String s){} public static void printErr(java.lang.Exception e){throw new AssertionError(e);} }''')
    put('com/ofss/fc/infra/thread/ThreadAttribute.java','''package com.ofss.fc.infra.thread;
    public class ThreadAttribute {
     private static final ThreadLocal<java.util.Map<String,Object>> DATA=ThreadLocal.withInitial(java.util.HashMap::new);
     public static Object get(String k){return DATA.get().get(k);}
     public static void set(String k,Object v){DATA.get().put(k,v);}
    }''')
    put('com/ofss/fc/infra/config/ConfigurationFactory.java','''package com.ofss.fc.infra.config;
    public class ConfigurationFactory {
     public static java.util.prefs.Preferences preferences=new Memory();
     public static ConfigurationFactory getInstance(){return new ConfigurationFactory();}
     public java.util.prefs.Preferences getConfigurations(String key){return preferences;}
     public java.util.prefs.Preferences getRootConfigurations(){return preferences;}
     static class Memory extends java.util.prefs.AbstractPreferences {
      java.util.Map<String,String> values=new java.util.HashMap<>();Memory(){super(null,"");}
      protected void putSpi(String k,String v){values.put(k,v);} protected String getSpi(String k){return values.get(k);}
      protected void removeSpi(String k){values.remove(k);} protected void removeNodeSpi(){}
      protected String[] keysSpi(){return values.keySet().toArray(new String[0]);} protected String[] childrenNamesSpi(){return new String[0];}
      protected java.util.prefs.AbstractPreferences childSpi(String n){throw new AssertionError("unused");}
      protected void syncSpi(){} protected void flushSpi(){}
     }
    }''')
    put('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java','''package com.ofss.digx.app.adapter;
    public class AdapterFactoryConfigurator {
     public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
     public IAdapterFactory getAdapterFactory(String name){
      fixture.AuditToggleTest.check(name.equals(com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY),"factory");
      return (IAdapterFactory)java.lang.reflect.Proxy.newProxyInstance(IAdapterFactory.class.getClassLoader(),new Class[]{IAdapterFactory.class},(p,m,a)->fixture.AuditToggleTest.adapter());
     }
    }''')
    put('fixture/AuditToggleTest.java',r'''package fixture;
    import java.util.*;
    import com.ofss.digx.cz.bea.app.common.helper.HthAuditTaskVisibility;
    import com.ofss.digx.cz.bea.app.common.resource.task.service.ext.CZTaskExt;
    import com.ofss.digx.cz.bea.app.common.service.transactionlist.TransactionList;
    import com.ofss.digx.app.common.task.dto.*;
    import com.ofss.digx.enumeration.task.*;
    import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
    public class AuditToggleTest {
     static final String CM="ENABLE_HTH_CM_ONBOARDING",BM="ENABLE_HTH_BM_ONBOARDING";
     static Map<String,String> config=new HashMap<>();static int reads,checks;static boolean fail;
     static final List<String> cm=Arrays.asList("MT_N_CUS_HTH","MT_N_UUS_HTH","MT_N_RUS_HTH","UAT_N_HUA_NEW","UAT_N_HUA_EDT","UAT_N_HUA_DEL","UAT_N_HAP_GEN","UAT_N_HAP_REGEN","UAT_N_HAP_RVL","CM_N_HAP_SETUP","CM_N_HAP_RESET","PP_N_HTH_CERT_UPL");
     static final List<String> bm=Arrays.asList("PP_N_HTH_ENB","PP_N_HTH_EDT","PP_N_HTH_DIS");
     static final List<String> bco=Arrays.asList("MT_N_CUS","MT_N_UUS","MT_N_RUS","UAT_N_CA","UAT_N_UA","UAT_N_DA","INQ01","HTH_INQUIRY_EXISTING");
     public static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
     public static Object adapter(){return java.lang.reflect.Proxy.newProxyInstance(IHostUserDetailsInvocationAdapter.class.getClassLoader(),new Class[]{IHostUserDetailsInvocationAdapter.class},(p,m,a)->{
      check(m.getName().equals("getCustomConfigUtilValue"),"live config bridge");check("N".equals(a[1]),"default disabled");reads++;
      if(fail)throw new IllegalStateException("fixture");return config.get(a[0]);
     });}
     static TaskRequestDTO request(boolean audit,ViewType view){TaskRequestDTO req=new TaskRequestDTO();TaskDTO t=new TaskDTO();TaskAspectDTO a=new TaskAspectDTO();a.setTaskAspect(audit?TaskAspect.AUDIT:TaskAspect.ACCOUNT_ACCESS);a.setEnabled(true);t.setAspects(new HashSet<>(Arrays.asList(a)));req.setTask(t);req.setView(view);return req;}
     static List<TaskDTO> original(){List<TaskDTO> list=new ArrayList<>();List<String> all=new ArrayList<>(bco);all.addAll(cm);all.addAll(bm);for(String id:all){TaskDTO t=new TaskDTO();t.setId(id);t.setName("unchanged");list.add(t);}return Collections.unmodifiableList(list);}
     static List<String> invoke(TaskRequestDTO req) throws Exception {List<TaskDTO> list=original();TaskListResponseDTO res=new TaskListResponseDTO();res.setTaskList(list);new CZTaskExt().postList(null,req,res);check(list.size()==bco.size()+cm.size()+bm.size(),"must not shrink cached/source list");List<String> ids=new ArrayList<>();for(TaskDTO t:res.getTaskList())ids.add(t.getId());return ids;}
     public static void main(String[] args) throws Exception {
      for(boolean c:new boolean[]{false,true})for(boolean b:new boolean[]{false,true}){
       config.put(CM,c?"Y":"N");config.put(BM,b?"Y":"N");reads=0;
       List<String> ids=invoke(request(true,ViewType.LIST));check(ids.containsAll(bco),"BCO and inquiry retained");
       for(String id:cm)check(ids.contains(id)==c,"CM "+id);for(String id:bm)check(ids.contains(id)==b,"BM "+id);check(reads==2,"at most one read per task group and request");
       reads=0;List<String> high=new ArrayList<>();for(com.ofss.digx.cz.bea.app.common.HighRiskTransactionDTO t:new TransactionList().fetchHighRiskTransactionList(null).getHighRiskTransactionDTOList())high.add(t.getTaskCode());
       ResourceBundle bundle=ResourceBundle.getBundle("resources.nls.HighRiskTask",Locale.ENGLISH);
       for(String id:bundle.keySet())check(high.contains(id)==(!cm.contains(id)||c),"high risk "+id);
       check(reads==1,"high risk lazily reads only CM key in current bundle");
      }
      config.clear();reads=0;check(invoke(request(true,ViewType.LIST)).equals(bco),"missing flags disabled");
      fail=true;check(invoke(request(true,ViewType.LIST)).equals(bco),"configuration failure preserves BCO only");fail=false;
      config.put(CM," y ");config.put(BM,"Y");check(invoke(request(true,ViewType.LIST)).size()==original().size(),"trim/case enabled");
      config.put(CM,"true");config.put(BM,"");check(invoke(request(true,ViewType.LIST)).equals(bco),"non-Y disabled");
      reads=0;for(TaskRequestDTO req:Arrays.asList(request(false,ViewType.LIST),request(true,ViewType.HIERARCHICHAL),new TaskRequestDTO(),null))check(invoke(req).size()==original().size(),"non-audit task requests unchanged");check(reads==0,"non-audit does not read toggle");
      config.put(CM,"N");HthAuditTaskVisibility off=new HthAuditTaskVisibility();check(!off.isVisible(cm.get(0)),"off");config.put(CM,"Y");check(new HthAuditTaskVisibility().isVisible(cm.get(0)),"next request uses fresh config");
      System.out.println("PASS audit dropdowns: "+checks+" assertions; both production hooks, real DTOs and HighRiskTask bundle; only infrastructure isolated.");
     }
    }''')
    run([J/'javac','-proc:none','--release','8','-cp',str(W/'prod')+os.pathsep+CP,'-d',F,*files])
    # Real enricher uses only the isolated locale/config/logger infrastructure.
    run([J/'java','-cp',os.pathsep.join([str(F),str(W/'prod'),str(R/'consulting/config'),CP]),'fixture.AuditToggleTest'])

    store=W/'test.p12'
    if not store.exists():run([J/'keytool','-genkeypair','-alias','local-test','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=localhost','-ext','SAN=dns:localhost,ip:127.0.0.1','-storetype','PKCS12','-keystore',store,'-storepass','test-store-only','-keypass','test-store-only','-noprompt'])
    h=W/'DspExistsTest.java'
    h.write_text(r'''package fixture;
    import com.sun.net.httpserver.*;
    import com.fasterxml.jackson.databind.*;
    import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import javax.net.ssl.*;
    import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
    import com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement;
    import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.factory.DSPApiClientFactory;
    import com.ofss.fc.infra.config.ConfigurationFactory;
    public class DspExistsTest {
     static int assertions;static Method validate;static Object target;
     static void check(boolean ok,String m){assertions++;if(!ok)throw new AssertionError(m);}
     static Object validate() throws Throwable {try{return validate.invoke(target,"fixture-business-client");}catch(InvocationTargetException e){throw e.getCause();}}
     static void failure() throws Throwable {
      boolean failed=false;try{validate();}catch(RuntimeException e){failed=true;}
      check(failed,"service failure returned a business result");
      check(DSPApiClientFactory.getDspClient().getCommonHeader().getAccessToken()==null,"header not reset on failure");
     }
     public static void main(String[] args) throws Throwable {
      KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Paths.get(args[0]))){keys.load(in,"test-store-only".toCharArray());}
      KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-store-only".toCharArray());
      SSLContext tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
      HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(tls));
      AtomicReference<String> response=new AtomicReference<>("{\"exists\":true}");AtomicInteger status=new AtomicInteger(200),calls=new AtomicInteger();AtomicReference<Throwable> serverError=new AtomicReference<>();
      Set<String> messageIds=new HashSet<>();
      server.createContext("/",e->{try{
       calls.incrementAndGet();check("POST".equals(e.getRequestMethod()),"method");
       check("/catalog/shared/client/v1/exists".equals(e.getRequestURI().getPath()),"path or APIC prefix incorrect");
       check("fixture-id".equals(e.getRequestHeaders().getFirst("x-ibm-client-id")),"APIC ID");
       check("fixture-secret".equals(e.getRequestHeaders().getFirst("x-ibm-client-secret")),"APIC secret");
       check("BCO".equals(e.getRequestHeaders().getFirst("Channel")),"Channel");
       String message=e.getRequestHeaders().getFirst("Message-Id");UUID.fromString(message);check(messageIds.add(message),"stale Message-Id");
       JsonNode body=new ObjectMapper().readTree(e.getRequestBody());check(body.size()==1 && "fixture-business-client".equals(body.path("clientId").asText()),"request contract");
       byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status.get(),bytes.length);e.getResponseBody().write(bytes);
      }catch(Throwable error){serverError.set(error);}finally{e.close();}});
      ExecutorService pool=Executors.newCachedThreadPool();server.setExecutor(pool);server.start();
      try{
       java.util.prefs.Preferences config=ConfigurationFactory.preferences;
       config.put("DSP.SERVICE_URL","https://localhost:"+server.getAddress().getPort()+"/catalog/shared");
       config.put("DSP.APIC_CLIENT_ID","fixture-id");config.put("DSP.APIC_CLIENT_SECRET","fixture-secret");
       target=new HostToHostManagement();validate=HostToHostManagement.class.getDeclaredMethod("validateDspClientId",String.class);validate.setAccessible(true);
       for(boolean exists:new boolean[]{true,false}){response.set("{\"exists\":"+exists+"}");check(Boolean.valueOf(exists).equals(validate()),"boolean result incorrect");}
       for(String bad:new String[]{"{}","{\"exists\":null}","{\"exists\":\"true\"}","{\"exists\":1}","{\"valid\":true}","null","not-json","{\"message\":\"temporarily unavailable\",\"errCode\":500050,\"exceptionType\":\"com.hkbea.common.exception.SystemException\"}","{\"exists\":true,\"errCode\":500050}"}){
        response.set(bad);DSPApiClientFactory.getDspClient().getCommonHeader().setAccessToken("fixture-token");failure();
       }
       for(int code:new int[]{400,401,403,404,450,460,500,502,503}){
        status.set(code);response.set("{\"message\":\"Authentication service is temporarily unavailable. Please try again later.\",\"errCode\":500050,\"referenceCode\":\"fixture\",\"exceptionType\":\"com.hkbea.common.exception.SystemException\"}");failure();
       }
       check(serverError.get()==null,"server assertion: "+serverError.get());
       System.out.println("PASS DSP: "+assertions+" assertions; "+calls.get()+" real HTTPS calls through production SDK/factory + HostToHostManagement; exists true/false, malformed and UAM/HTTP failures.");
      }finally{server.stop(0);pool.shutdownNow();}
     }
    }''')
    run([J/'javac','-proc:none','-cp',os.pathsep.join([str(F),str(W/'prod'),CP]),'-d',F,h])
    run([J/'java','-Djavax.net.ssl.trustStore='+str(store),'-Djavax.net.ssl.trustStorePassword=test-store-only','-cp',os.pathsep.join([str(F),str(W/'prod'),CP]),'fixture.DspExistsTest',store])
