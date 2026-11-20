"""Compile the audit extension and test response-only userChannelType metadata.

Run: python3 consulting/tests/hth_onboarding/verify_audit_user_channel.py
Requires JAVA_HOME, repository middleware jars and existing compiled dependencies.
Tests real SDK AuditDTO/Dictionary serialization; no database or bank API calls.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
PROJECTS = ROOT / 'consulting/middleware/projects'
PACKAGE = 'com/ofss/digx/cz/bea/app/audit/service/ext'
SOURCE = PROJECTS / 'module/com.ofss.digx.cz.bea.module.access/src' / PACKAGE


def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True,
                            text=True, timeout=120)
    print((result.stdout + result.stderr)[-6000:])
    if result.returncode:
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth-audit-channel-') as directory:
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
    # Match the legacy source encoding in consulting/ant/build_backend.xml.
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'cp1252',
         '-sourcepath', os.pathsep.join(str(path) for path in PROJECTS.glob('*/*/src')),
         '-cp', classpath, '-d', production,
         SOURCE / 'CZVoidAuditExt.java', SOURCE / 'HthAuditUserChannel.java'])

    fixture = work / 'AuditUserChannelTest.java'
    fixture.write_text('''
package com.ofss.digx.cz.bea.app.audit.service.ext;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.digx.app.audit.dto.AuditListResponseDTO;
import com.ofss.digx.enumeration.audit.Action;
import com.ofss.digx.enumeration.audit.Status;
import com.ofss.digx.infra.audit.dto.AuditDTO;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;

public class AuditUserChannelTest {
    private static int checks;
    private static final ObjectMapper JSON = new ObjectMapper();
    static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    static AuditDTO row(String task) {
        AuditDTO dto = new AuditDTO();
        dto.setId("audit-id");
        dto.setTaskCode(task);
        dto.setUserId("acting-user");
        dto.setActivity("existing translated label");
        dto.setStatus(Status.SUCCESS);
        dto.setAction(Action.CREATED);
        dto.setReferenceNo("original-reference");
        return dto;
    }
    static String channel(AuditDTO row) {
        int count = 0;
        String result = null;
        for (NameValuePairDTO pair : row.getDictionaryArray()[0].getNameValuePairDTOArray()) {
            if (pair != null && "userChannelType".equals(pair.getGenericName())) {
                count++;
                check("userChannelType".equals(pair.getName()), "matching name and genericName");
                check("java.lang.String".equals(pair.getDatatype()), "string data type");
                result = pair.getValue();
            }
        }
        check(count == 1, "exactly one channel field");
        return result;
    }
    public static void main(String[] args) throws Exception {
        HthAuditUserChannel enricher = new HthAuditUserChannel();
        String[] hth = {"MT_N_CUS_HTH", "MT_N_UUS_HTH", "MT_N_RUS_HTH",
            "UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL",
            "UAT_N_HAP_GEN", "UAT_N_HAP_REGEN", "UAT_N_HAP_RVL",
            "CM_N_HAP_SETUP", "CM_N_HAP_RESET", "PP_N_HTH_CERT_UPL",
            "PP_N_HTH_ENB", "PP_N_HTH_EDT", "PP_N_HTH_DIS"};
        List<AuditDTO> rows = new ArrayList<AuditDTO>();
        for (String task : hth) {
            AuditDTO dto = row(task);
            if ("CM_N_HAP_RESET".equals(task)) dto.setStatus(Status.FAILURE);
            rows.add(dto);
            JsonNode before = JSON.valueToTree(dto);
            enricher.enrich(Collections.singletonList(dto));
            check("HTH".equals(channel(dto)), "known onboarding task " + task);
            JsonNode after = JSON.valueToTree(dto);
            ((com.fasterxml.jackson.databind.node.ObjectNode) before).remove("dictionaryArray");
            ((com.fasterxml.jackson.databind.node.ObjectNode) after).remove("dictionaryArray");
            check(before.equals(after), "all original audit properties unchanged " + task);
        }
        // These shared/historical tasks do not identify the target user's channel.
        for (String task : new String[]{"MT_N_CUS", "MT_N_UUS", "MT_N_RUS",
                "INQ01", "HTH_INQUIRY_EXISTING", "FUTURE_HTH_TASK", "PC_F_BTFT", "", null}) {
            AuditDTO dto = row(task);
            dto.setUserId("hth-acting-user");
            dto.setActivity("HTH User Management");
            dto.setRequestUrl("/users?userChannelType=HTH");
            rows.add(dto);
            enricher.enrich(Collections.singletonList(dto));
            check("UNKNOWN".equals(channel(dto)), "never guess from actor/label/prefix/query: " + task);
        }
        List<AuditDTO> immutable = Collections.unmodifiableList(rows);
        List<AuditDTO> order = new ArrayList<AuditDTO>(rows);
        enricher.enrich(immutable);
        check(immutable.equals(order), "no records removed or reordered");

        AuditDTO dto = rows.get(0);
        Dictionary first = new Dictionary();
        first.setFullyQualifiedClassName("existing.dictionary");
        NameValuePairDTO retained = new NameValuePairDTO("existing", "value", "java.lang.String");
        retained.setGenericName("existing");
        NameValuePairDTO obsolete = new NameValuePairDTO("userChannelType", "BCO", "java.lang.String");
        obsolete.setGenericName("userChannelType");
        first.setNameValuePairDTOArray(new NameValuePairDTO[]{retained, obsolete, null});
        Dictionary second = new Dictionary();
        Dictionary[] original = {first, second};
        dto.setDictionaryArray(original);
        AuditDTO shared = row("MT_N_CUS");
        shared.setDictionaryArray(original);
        enricher.enrich(Collections.singletonList(dto));
        enricher.enrich(Collections.singletonList(dto));
        check("HTH".equals(channel(dto)), "upsert is idempotent");
        check(dto.getDictionaryArray() != original && dto.getDictionaryArray()[0] != first,
                "copy changed containers");
        check(dto.getDictionaryArray().length == 2 && dto.getDictionaryArray()[1] == second,
                "preserve other dictionaries");
        check("existing.dictionary".equals(dto.getDictionaryArray()[0].getFullyQualifiedClassName()),
                "preserve dictionary class name");
        check(dto.getDictionaryArray()[0].getNameValuePairDTOArray()[0] == retained,
                "preserve existing fields");
        check("BCO".equals(obsolete.getValue()) && shared.getDictionaryArray()[0] == first,
                "shared original dictionary is untouched");
        check(first.getNameValuePairDTOArray().length == 3, "original pair array is untouched");
        for (Dictionary[] empty : new Dictionary[][]{null, new Dictionary[0],
                new Dictionary[]{null}, new Dictionary[]{new Dictionary()}}) {
            dto.setDictionaryArray(empty);
            enricher.enrich(Collections.singletonList(dto));
            check("HTH".equals(channel(dto)), "null/empty dictionaries supported");
        }
        AuditListResponseDTO response = new AuditListResponseDTO();
        response.setAuditList(Collections.singletonList(dto));
        JsonNode json = JSON.readTree(JSON.writeValueAsString(response));
        JsonNode pair = json.path("auditList").get(0).path("dictionaryArray").get(0)
                .path("nameValuePairDTOArray").get(0);
        check("userChannelType".equals(pair.path("genericName").asText()), "real response field path");
        check("HTH".equals(pair.path("value").asText()), "real response field value");
        check(json.path("auditList").size() == 1, "response list retained");
        enricher.enrich(null);
        enricher.enrich(Collections.<AuditDTO>emptyList());
        enricher.enrich(Collections.<AuditDTO>singletonList(null));
        System.out.println("PASS: " + checks + " audit user channel checks");
    }
}
''')
    runtime_classpath = os.pathsep.join(map(str, [production, classpath]))
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'UTF-8',
         '-cp', runtime_classpath, '-d', production, fixture])
    run([JAVA / 'java', '-cp', runtime_classpath,
         PACKAGE.replace('/', '.') + '.AuditUserChannelTest'])
