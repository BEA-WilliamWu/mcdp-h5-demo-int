"""Compile the approval extension and test its response-only channel enrichment.

Run: python3 consulting/tests/hth_onboarding/verify_pending_approval_channel.py
Requires JAVA_HOME, repository middleware jars and existing compiled dependencies.
Uses real SDK DTOs; substitutes container logging and the saved-snapshot read.
Does not connect to a database or change approval state.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
PROJECTS = ROOT / 'consulting/middleware/projects'
PACKAGE = 'com/ofss/digx/cz/bea/app/approval/service/transaction/ext'
SOURCE = PROJECTS / 'module/com.ofss.digx.cz.bea.module.approval/src' / PACKAGE


def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True,
                            text=True, timeout=120)
    print((result.stdout + result.stderr)[-6000:])
    if result.returncode:
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth-approval-channel-') as directory:
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
    # Match consulting/ant/build_backend.xml (legacy sources include cp1252 comments).
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'cp1252',
         '-sourcepath', os.pathsep.join(str(path) for path in PROJECTS.glob('*/*/src')),
         '-cp', classpath, '-d', production,
         SOURCE / 'CZTransactionExt.java', SOURCE / 'HthApprovalUserChannel.java'])

    fixtures = work / 'fixtures'
    fixtures.mkdir()
    sources = []

    def put(name, content):
        path = fixtures / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(path)

    put('com/ofss/fc/infra/log/impl/MultiEntityLogger.java', '''
package com.ofss.fc.infra.log.impl;
public class MultiEntityLogger {
    public static MultiEntityLogger getUniqueInstance() { return new MultiEntityLogger(); }
    public java.util.logging.Logger getLogger(String name) { return java.util.logging.Logger.getLogger(name); }
    public String formatMessage(String message, Object... args) { return message; }
}
''')
    put('com/ofss/digx/cz/bea/app/logger/BeaSystemOut.java', '''
package com.ofss.digx.cz.bea.app.logger;
public class BeaSystemOut {
    public static void println(String message) { System.out.println(message); }
}
''')
    put(PACKAGE + '/PendingApprovalChannelTest.java', '''
package com.ofss.digx.cz.bea.app.approval.service.transaction.ext;

import java.util.*;
import com.ofss.digx.app.approval.dto.transaction.TransactionDTO;
import com.ofss.digx.app.approval.dto.transaction.TransactionApprovalDetailsDTO;
import com.ofss.digx.app.common.task.dto.TaskDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.fc.framework.domain.common.dto.Dictionary;
import com.ofss.fc.framework.domain.common.dto.NameValuePairDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PendingApprovalChannelTest {
    private static int checks;
    static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    static class Enricher extends HthApprovalUserChannel {
        Object saved;
        int reads;
        boolean fail;
        Object readSnapshot(String id) {
            reads++;
            check("pending-id".equals(id), "read the same transaction");
            if (fail) throw new IllegalStateException("simulated unavailable snapshot");
            return saved;
        }
    }
    static UserExtensionDataDTO user(String channel) {
        UserExtensionDataDTO user = new UserExtensionDataDTO();
        user.setUserChannelType(channel);
        return user;
    }
    static TransactionDTO transaction(String task, java.io.Serializable snapshot) {
        TransactionDTO row = new TransactionDTO();
        row.setTransactionName(task);
        row.setTransactionId("pending-id");
        row.setTransactionSnapshot(snapshot);
        return row;
    }
    static String channel(TransactionDTO row) {
        NameValuePairDTO[] pairs = row.getDictionaryArray()[0].getNameValuePairDTOArray();
        int count = 0;
        String value = null;
        for (NameValuePairDTO pair : pairs) {
            if (pair != null && "userChannelType".equals(pair.getGenericName())) {
                count++;
                check("userChannelType".equals(pair.getName()), "name and genericName");
                check("java.lang.String".equals(pair.getDatatype()), "string type");
                value = pair.getValue();
            }
        }
        check(count == 1, "one channel entry per row");
        return value;
    }
    public static void main(String[] args) throws java.lang.Exception {
        Enricher enricher = new Enricher();
        for (String task : new String[]{"MT_N_CUS", "MT_N_UUS"}) {
            for (String value : new String[]{"HTH", "BCO", " hth ", "bco", null, "", "OTHER"}) {
                TransactionDTO row = transaction(task, user(value));
                enricher.enrich(Collections.singletonList(row));
                String expected = value == null || value.isEmpty() || "OTHER".equals(value)
                        ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
                check(expected.equals(channel(row)), "submitted channel: " + task + "/" + value);
                check(row.getTransactionSnapshot() instanceof UserExtensionDataDTO, "snapshot preserved");
            }
        }
        check(enricher.reads == 0, "available snapshots require no DB lookup");
        enricher.saved = user("HTH");
        TransactionDTO missing = transaction("MT_N_CUS", null);
        enricher.enrich(Collections.singletonList(missing));
        check("HTH".equals(channel(missing)), "pending new user uses saved request");
        check(missing.getTransactionSnapshot() == null, "do not expose saved request in response");
        check(enricher.reads == 1, "single saved snapshot read");
        enricher.saved = null;
        enricher.enrich(Collections.singletonList(missing));
        check("UNKNOWN".equals(channel(missing)), "missing historical snapshot is unknown");
        enricher.fail = true;
        enricher.enrich(Collections.singletonList(missing));
        check("UNKNOWN".equals(channel(missing)), "read failure keeps row and unknown channel");
        enricher.fail = false;
        TransactionDTO wrong = transaction("MT_N_UUS", "legacy snapshot");
        enricher.enrich(Collections.singletonList(wrong));
        check("UNKNOWN".equals(channel(wrong)), "unrecognized snapshot is unknown");
        int readsBefore = enricher.reads;
        for (String task : new String[]{"UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL",
                "PP_N_HTH_CERT_UPL", "PP_N_HTH_ENB", "PP_N_HTH_EDT", "PP_N_HTH_DIS"}) {
            TransactionDTO row = transaction(task, null);
            enricher.enrich(Collections.singletonList(row));
            check("HTH".equals(channel(row)), "dedicated HTH task: " + task);
        }
        for (String task : new String[]{"UAT_N_CA", "MT_N_CMUS", "PC_F_BTFT", "INQ01", "HTH_INQUIRY", null}) {
            TransactionDTO row = transaction(task, null);
            enricher.enrich(Collections.singletonList(row));
            check(row.getDictionaryArray() == null, "unrelated task unchanged: " + task);
        }
        check(readsBefore == enricher.reads, "no snapshot reads for unrelated or dedicated tasks");
        TransactionDTO fallback = transaction(null, user("HTH"));
        TaskDTO task = new TaskDTO();
        task.setId("MT_N_UUS");
        fallback.setTaskDTO(task);
        enricher.enrich(Collections.singletonList(fallback));
        check("HTH".equals(channel(fallback)), "task DTO fallback");
        fallback.setTransactionName("MT_N_CUS");
        TransactionApprovalDetailsDTO approval = new TransactionApprovalDetailsDTO();
        fallback.setApprovalDetails(approval);
        Dictionary first = new Dictionary();
        first.setFullyQualifiedClassName("existing.dictionary");
        NameValuePairDTO retained = new NameValuePairDTO("existing", "value", "java.lang.String");
        retained.setGenericName("existing");
        NameValuePairDTO obsolete = new NameValuePairDTO("userChannelType", "BCO", "java.lang.String");
        obsolete.setGenericName("userChannelType");
        first.setNameValuePairDTOArray(new NameValuePairDTO[]{retained, obsolete, null});
        Dictionary second = new Dictionary();
        Dictionary[] original = new Dictionary[]{first, second};
        fallback.setDictionaryArray(original);
        List<TransactionDTO> list = Collections.unmodifiableList(Arrays.asList(fallback, missing, null));
        enricher.enrich(list);
        enricher.enrich(list);
        check(list.size() == 3 && list.get(0) == fallback && list.get(1) == missing, "rows/order unchanged");
        check(fallback.getTaskDTO() == task && fallback.getApprovalDetails() == approval, "approval/task untouched");
        check("HTH".equals(channel(fallback)), "channel replaced without duplicates");
        check(fallback.getDictionaryArray().length == 2 && fallback.getDictionaryArray()[1] == second, "other dictionaries preserved");
        check(fallback.getDictionaryArray()[0].getNameValuePairDTOArray()[0] == retained, "existing entries preserved");
        check("existing.dictionary".equals(fallback.getDictionaryArray()[0].getFullyQualifiedClassName()), "dictionary metadata retained");
        check(original[0] == first && "BCO".equals(obsolete.getValue()), "source dictionary not mutated");
        check(first.getNameValuePairDTOArray().length == 3, "source pairs not mutated");
        String json = new ObjectMapper().writeValueAsString(fallback.getDictionaryArray());
        check(json.contains("\\\"genericName\\\":\\\"userChannelType\\\"") && json.contains("\\\"value\\\":\\\"HTH\\\""), "serialized contract");
        for (Dictionary[] empty : new Dictionary[][]{new Dictionary[0], new Dictionary[]{null}, new Dictionary[]{new Dictionary()}}) {
            fallback.setDictionaryArray(empty);
            enricher.enrich(Collections.singletonList(fallback));
            check("HTH".equals(channel(fallback)), "empty dictionary supported");
        }
        enricher.enrich(null);
        enricher.enrich(Collections.<TransactionDTO>emptyList());
        System.out.println("PASS: " + checks + " pending approval channel checks");
    }
}
''')
    runtime_classpath = os.pathsep.join(map(str, [fixtures, production, classpath]))
    run([JAVA / 'javac', '--release', '8', '-proc:none', '-encoding', 'UTF-8',
         '-cp', runtime_classpath, '-d', fixtures, *sources])
    run([JAVA / 'java', '-cp', runtime_classpath,
         PACKAGE.replace('/', '.') + '.PendingApprovalChannelTest'])
