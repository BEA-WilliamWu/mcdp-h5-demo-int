"""Check DSP JSON and Base64(SHA-256(UTF-8(password))) against independent vectors.

Runs the production request builder with Jackson. Does not invoke any DSP endpoint.
"""
from pathlib import Path
import base64
import hashlib
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
SOURCE = ROOT / 'consulting/middleware/projects/ext-xface/com.ofss.digx.cz.bea.extxface.app.impl/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/impl/dto/DspApiPasswordPersistRequest.java'
LIB = ROOT / 'consulting/middleware/lib/thirdparty'
CP = os.pathsep.join(str(LIB / name) for name in [
    'jackson-core-2.19.4.jar', 'jackson-annotations-2.19.4.jar', 'jackson-databind-2.19.4.jar'])
HARNESS = r'''
import com.fasterxml.jackson.databind.*;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.dto.DspApiPasswordPersistRequest;
import java.util.*;
public class DspPayloadTest {
  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    for (int i = 0; i < args.length; i += 2) {
      String password = args[i], expected = args[i + 1];
      DspApiPasswordPersistRequest request = DspApiPasswordPersistRequest.fromPassword(
          "business-client", "USER@PARTY", password);
      String json = mapper.writeValueAsString(request);
      JsonNode body = mapper.readTree(json);
      Set<String> names = new HashSet<>(); body.fieldNames().forEachRemaining(names::add);
      check(names.equals(new HashSet<>(Arrays.asList("clientId", "closeId", "passwordHash"))));
      check(body.get("clientId").asText().equals("business-client"));
      check(body.get("closeId").asText().equals("USER@PARTY"));
      String hash = body.get("passwordHash").asText();
      check(hash.equals(expected) && hash.length() == 44 && hash.endsWith("="));
      check(Base64.getDecoder().decode(hash).length == 32);
      check(!hash.contains("\n") && !hash.contains("\r"));
      check(!json.contains(password));
      check(!request.toString().contains(hash) && !request.toString().contains(password));
    }
    for (String[] invalid : new String[][] {
        {null,"USER@PARTY","Example1234"}, {" ","USER@PARTY","Example1234"},
        {"client",null,"Example1234"}, {"client"," ","Example1234"},
        {"client","USER@PARTY",null}, {"client","USER@PARTY",""}}) {
      try {
        DspApiPasswordPersistRequest.fromPassword(invalid[0],invalid[1],invalid[2]);
        throw new AssertionError("Incomplete input accepted");
      } catch (IllegalArgumentException expected) {
        check(!expected.getMessage().contains("Example1234"));
      }
    }
    System.out.println("PASS: DSP JSON has exactly 3 fields; full CLOSE_ID, independent SHA-256/Base64 vectors, UTF-8/case/space preservation, redacted diagnostics and invalid-input rejection");
  }
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
'''
inputs = ['Example1234', 'example1234', ' Example1234 ', '测试Pass1234', 'abc']
args = []
for password in inputs:
    args.extend([password, base64.b64encode(hashlib.sha256(password.encode('utf-8')).digest()).decode('ascii')])
with tempfile.TemporaryDirectory(prefix='hth-dsp-payload-') as temporary:
    test = Path(temporary) / 'DspPayloadTest.java'
    test.write_text(HARNESS)
    subprocess.run([str(JAVA / 'javac'), '--release', '8', '-proc:none', '-encoding', 'UTF-8',
                    '-cp', CP, '-d', temporary, str(SOURCE), str(test)], check=True)
    result = subprocess.run([str(JAVA / 'java'), '-cp', temporary + os.pathsep + CP,
                             'DspPayloadTest', *args], capture_output=True, text=True)
    print((result.stdout + result.stderr)[-4000:])
    raise SystemExit(result.returncode)
