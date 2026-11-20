"""Compile both list hooks and exercise their shared CM/BM channel filter.

Run with JAVA_HOME set: python3 consulting/tests/hth_onboarding/verify_list_channel_visibility.py
Uses real SDK DTOs, channel enrichers and serialization; isolates only config/logging.
No database or bank API calls. Existing metadata regressions remain separate.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
PROJECTS = ROOT / 'consulting/middleware/projects'


def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True,
                            text=True, timeout=120)
    print((result.stdout + result.stderr)[-6000:])
    if result.returncode:
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth-list-visibility-') as directory:
    work = Path(directory)
    lib = ROOT / 'consulting/middleware/lib'
    preferred = [lib / 'thirdparty' / ('jackson-' + name + '-2.19.4.jar')
                 for name in ('core', 'annotations', 'databind')]
    classes = [path for path in
               (ROOT / 'consulting/dist/classes/middleware/projects').glob('*/*')
               if path.is_dir()]
    classpath = os.pathsep.join(map(str, preferred + classes
                                   + sorted(set(lib.rglob('*.jar')) - set(preferred))))
    production = work / 'production'
    production.mkdir()
    sources = []
    for name in ('CZTransactionExt.java', 'HthApprovalUserChannel.java',
                 'CZVoidAuditExt.java', 'HthAuditUserChannel.java', 'HthAuditTaskVisibility.java'):
        sources.extend(PROJECTS.glob('module/*/src/**/' + name))
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'cp1252',
         '-sourcepath', os.pathsep.join(str(path) for path in PROJECTS.glob('*/*/src')),
         '-cp', classpath, '-d', production, *sources])
    fixtures = work / 'fixtures'
    fixture_sources = []

    def put(name, source):
        path = fixtures / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        fixture_sources.append(path)

    put('com/ofss/fc/infra/log/impl/MultiEntityLogger.java', '''
package com.ofss.fc.infra.log.impl;
public class MultiEntityLogger {
    public static MultiEntityLogger getUniqueInstance() { return new MultiEntityLogger(); }
    public java.util.logging.Logger getLogger(String name) { return java.util.logging.Logger.getLogger(name); }
    public String formatMessage(String message, Object... args) { return message; }
}
''')
    put('com/ofss/fc/infra/thread/ThreadAttribute.java', '''
package com.ofss.fc.infra.thread;
public class ThreadAttribute {
    private static final ThreadLocal<java.util.Map<String,Object>> DATA =
            ThreadLocal.withInitial(java.util.HashMap::new);
    public static Object get(String key) { return DATA.get().get(key); }
    public static void set(String key, Object value) { DATA.get().put(key, value); }
}
''')
    put('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java', '''
package com.ofss.digx.app.adapter;
public class AdapterFactoryConfigurator {
    public static AdapterFactoryConfigurator getInstance() { return new AdapterFactoryConfigurator(); }
    public IAdapterFactory getAdapterFactory(String name) {
        fixture.ListVisibilityTest.check(name.equals(
                com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY),
                "existing host-user config factory");
        return (IAdapterFactory) java.lang.reflect.Proxy.newProxyInstance(IAdapterFactory.class.getClassLoader(),
                new Class[]{IAdapterFactory.class}, (p,m,a) -> fixture.ListVisibilityTest.adapter());
    }
}
''')
    put('fixture/ListVisibilityTest.java', '''
package fixture;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.audit.dto.AuditListResponseDTO;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.app.common.helper.HthAuditTaskVisibility;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.digx.infra.thread.ThreadAttribute;
import com.ofss.fc.framework.domain.common.dto.DataTransferObject;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;

public class ListVisibilityTest {
    static final String CM = "ENABLE_HTH_CM_ONBOARDING", BM = "ENABLE_HTH_BM_ONBOARDING";
    static final Map<String,String> config = new HashMap<String,String>();
    static int checks, reads;
    static boolean fail;
    public static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    public static Object adapter() {
        return java.lang.reflect.Proxy.newProxyInstance(IHostUserDetailsInvocationAdapter.class.getClassLoader(),
                new Class[]{IHostUserDetailsInvocationAdapter.class}, (p,m,a) -> {
            check("getCustomConfigUtilValue".equals(m.getName()), "existing live config method");
            check("N".equals(a[1]), "existing missing-value default");
            check((Boolean.TRUE.equals(ThreadAttribute.get("isAdmin")) ? BM : CM).equals(a[0]),
                    "read only the current calling portal's toggle");
            reads++;
            if (fail) throw new IllegalStateException("simulated config failure");
            return config.get(a[0]);
        });
    }
    static TransactionDTO transaction(String task, String channel) {
        TransactionDTO dto = new TransactionDTO();
        dto.setTransactionName(task);
        UserExtensionDataDTO user = new UserExtensionDataDTO();
        user.setUserChannelType(channel);
        dto.setTransactionSnapshot(user);
        return dto;
    }
    static AuditDTO audit(String task) {
        AuditDTO dto = new AuditDTO();
        dto.setTaskCode(task);
        return dto;
    }
    static void enrich(String type, List<?> rows) throws Exception {
        Class<?> clazz = Class.forName(type);
        java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        java.lang.reflect.Method method = clazz.getDeclaredMethod("enrich", List.class);
        method.setAccessible(true);
        method.invoke(constructor.newInstance(), rows);
    }
    static AuditDTO marked(String name, String genericName, String value) {
        AuditDTO dto = audit("legacy");
        Dictionary first = new Dictionary();
        NameValuePairDTO pair = new NameValuePairDTO(name, value, "java.lang.String");
        pair.setGenericName(genericName);
        first.setNameValuePairDTOArray(new NameValuePairDTO[]{null, pair});
        dto.setDictionaryArray(new Dictionary[]{first});
        return dto;
    }
    static <T extends DataTransferObject> void verify(List<T> source, List<T> retained, boolean enabled)
            throws Exception {
        ObjectMapper json = new ObjectMapper();
        String before = json.writeValueAsString(source);
        reads = 0;
        List<T> actual = new HthAuditTaskVisibility().filterByUserChannel(source);
        check(actual.equals(enabled ? source : retained), "exact rows and order for portal/toggle combination");
        check(before.equals(json.writeValueAsString(source)), "source rows and metadata are unchanged");
        check(reads == 1, "one live toggle lookup per result, not per row");
        for (int i = 0; i < actual.size(); i++) {
            check(actual.get(i) == (enabled ? source : retained).get(i), "retained row identity");
        }
    }
    public static void main(String[] args) throws Exception {
        TransactionDTO create = transaction("MT_N_CUS", " hth ");
        TransactionDTO update = transaction("MT_N_UUS", "HTH");
        TransactionDTO bco = transaction("MT_N_UUS", "BCO");
        TransactionDTO unknown = transaction("MT_N_CUS", null);
        TransactionDTO cmTask = transaction("UAT_N_HUA_NEW", null);
        TransactionDTO bmTask = transaction("PP_N_HTH_ENB", null);
        TransactionDTO inquiry = transaction("INQ01", "HTH");
        List<TransactionDTO> transactions = Collections.unmodifiableList(
                Arrays.asList(create, bco, cmTask, unknown, bmTask, inquiry, update, null));
        enrich("com.ofss.digx.cz.bea.app.approval.service.transaction.ext.HthApprovalUserChannel", transactions);
        List<TransactionDTO> visibleTransactions = Arrays.asList(bco, unknown, inquiry, null);

        AuditDTO auditCm = audit("CM_N_HAP_RESET"), auditBm = audit("PP_N_HTH_ENB");
        AuditDTO historical = audit("MT_N_UUS"), auditInquiry = audit("HTH_INQUIRY_EXISTING");
        AuditDTO missing = audit(null);
        List<AuditDTO> audits = Collections.unmodifiableList(
                Arrays.asList(auditCm, historical, auditBm, auditInquiry, missing, null));
        enrich("com.ofss.digx.cz.bea.app.audit.service.ext.HthAuditUserChannel", audits);
        List<AuditDTO> visibleAudits = Arrays.asList(historical, auditInquiry, missing, null);
        for (boolean admin : new boolean[]{false, true}) {
            ThreadAttribute.set("isAdmin", admin);
            for (boolean cm : new boolean[]{false, true}) for (boolean bm : new boolean[]{false, true}) {
                config.put(CM, cm ? "Y" : "N");
                config.put(BM, bm ? "Y" : "N");
                verify(transactions, visibleTransactions, admin ? bm : cm);
                verify(audits, visibleAudits, admin ? bm : cm);
                HthAuditTaskVisibility dropdown = new HthAuditTaskVisibility();
                check(dropdown.isVisible("CM_N_HAP_RESET") == (admin ? bm : cm), "CM task follows caller");
                check(dropdown.isVisible("PP_N_HTH_ENB") == (admin ? bm : cm), "BM task follows caller");
                check(dropdown.isVisible("INQ01"), "released inquiry remains visible");
            }
        }
        ThreadAttribute.set("isAdmin", false);
        config.put(CM, "N");
        AuditDTO nameOnly = marked("userChannelType", null, "HTH");
        AuditDTO genericOnly = marked(null, "userChannelType", "HTH");
        AuditDTO otherField = marked("other", "other", "HTH");
        AuditDTO unknownMarker = marked("userChannelType", "userChannelType", "UNKNOWN");
        AuditDTO bcoMarker = marked("userChannelType", "userChannelType", "BCO");
        AuditDTO blankMarker = marked("userChannelType", "userChannelType", null);
        AuditDTO old = audit("no dictionary"), empty = audit("empty dictionary"), nullDictionary = audit("null dictionary");
        empty.setDictionaryArray(new Dictionary[0]);
        nullDictionary.setDictionaryArray(new Dictionary[]{null});
        AuditDTO noPairs = audit("no pairs");
        noPairs.setDictionaryArray(new Dictionary[]{new Dictionary()});
        AuditDTO anotherDictionary = marked("userChannelType", "userChannelType", "UNKNOWN");
        anotherDictionary.setDictionaryArray(new Dictionary[]{anotherDictionary.getDictionaryArray()[0],
                nameOnly.getDictionaryArray()[0]});
        List<AuditDTO> legacy = Arrays.asList(otherField, unknownMarker, bcoMarker, blankMarker, old, empty,
                nullDictionary, noPairs, anotherDictionary, null);
        reads = 0;
        check(new HthAuditTaskVisibility().filterByUserChannel(legacy).equals(legacy), "unknown/unmarked rows retained");
        check(reads == 0, "no toggle read needed without an explicit HTH marker");
        List<AuditDTO> named = new ArrayList<AuditDTO>(legacy);
        named.add(nameOnly);
        named.add(genericOnly);
        verify(named, legacy, false);
        check(new HthAuditTaskVisibility().filterByUserChannel(Arrays.asList(nameOnly, genericOnly)).isEmpty(),
                "all HTH rows filtered without source mutation");
        check(new HthAuditTaskVisibility().filterByUserChannel(null) == null, "null list retained");
        check(new HthAuditTaskVisibility().filterByUserChannel(Collections.<AuditDTO>emptyList()).isEmpty(), "empty list");

        config.clear();
        verify(audits, visibleAudits, false);
        fail = true;
        verify(audits, visibleAudits, false);
        fail = false;
        config.put(CM, " y ");
        verify(audits, visibleAudits, true);
        config.put(CM, "N");
        verify(audits, visibleAudits, false);
        config.put(CM, "Y");
        verify(audits, visibleAudits, true);

        AuditListResponseDTO response = new AuditListResponseDTO();
        config.put(CM, "N");
        response.setAuditList(new HthAuditTaskVisibility().filterByUserChannel(audits));
        String serialized = new ObjectMapper().writeValueAsString(response);
        check(serialized.contains("userChannelType") && serialized.contains("UNKNOWN"), "channel metadata retained");
        check(!serialized.contains("CM_N_HAP_RESET") && !serialized.contains("PP_N_HTH_ENB"), "HTH absent in JSON");
        check(serialized.contains("HTH_INQUIRY_EXISTING"), "Inquiry retained in JSON");
        System.out.println("PASS: " + checks + " list channel visibility checks");
    }
}
''')
    runtime_classpath = os.pathsep.join(map(str, [fixtures, production, classpath]))
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'UTF-8',
         '-cp', runtime_classpath, '-d', fixtures, *fixture_sources])
    run([JAVA / 'java', '-cp', runtime_classpath, 'fixture.ListVisibilityTest'])
