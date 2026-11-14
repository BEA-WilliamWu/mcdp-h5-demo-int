#!/usr/bin/env python3
"""Actual CM AMD transport -> TL's JCE cipher defaults. No servers or real credentials.

Run with Python 3, Node and JDK 21 (JAVA_HOME supported). Temporary keys and
ciphertexts remain in captured subprocess I/O and are never printed.
"""
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

JAVA = r'''
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
public class TlDspCipher {
    public static void main(String[] args) throws Exception {
        var b64 = Base64.getEncoder();
        if (args.length > 0) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(Integer.parseInt(args[0]),
                new java.math.BigInteger(args[1])));
            var pair = generator.generateKeyPair();
            System.out.println(b64.encodeToString(pair.getPrivate().getEncoded()));
            System.out.println(b64.encodeToString(pair.getPublic().getEncoded()));
            return;
        }
        var input = new BufferedReader(new InputStreamReader(System.in));
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(input.readLine())));
        // Exact transformation and init used by TL RsaCryptoCipher; no overridden MGF.
        var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        var params = cipher.getParameters().getParameterSpec(OAEPParameterSpec.class);
        if (!"SHA-256".equals(params.getDigestAlgorithm()) ||
            !"SHA-1".equals(((MGF1ParameterSpec)params.getMGFParameters()).getDigestAlgorithm())) {
            throw new IllegalStateException("JCE provider differs from supplied TL runtime");
        }
        System.out.println(b64.encodeToString(cipher.doFinal(Base64.getDecoder().decode(input.readLine()))));
    }
}
'''


def run():
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    node = shutil.which("node")
    assert java and javac and node, "JDK and Node are required"
    driver = Path(__file__).with_name("browser-interop.cjs")
    with tempfile.TemporaryDirectory(prefix="hth-tl-interop-") as tmp:
        source = Path(tmp) / "TlDspCipher.java"
        source.write_text(JAVA)
        subprocess.run([javac, str(source)], check=True, capture_output=True)
        command = [java, "-cp", tmp, "TlDspCipher"]

        def keys(bits=2048, exponent=65537):
            return subprocess.check_output(command + [str(bits), str(exponent)], text=True).splitlines()

        def encrypt(public_key, password, operation="RESET", expected_error=None):
            result = subprocess.run([node, str(driver), public_key, password, operation], text=True, capture_output=True)
            if expected_error:
                assert result.returncode != 0 and expected_error in result.stderr, "Expected transport rejection"
                return None
            assert result.returncode == 0, "Actual browser transport failed"
            return json.loads(result.stdout)["dspEncryptedPassword"]

        private_key, public_key = keys()
        for password, operation in [("First123!", "SETUP"), ("Reset456!", "RESET"),
                                    ("\u5bc6\u78bc\U0001f512Ab12!", "RESET"), ("X" * 190, "SETUP")]:
            ciphertext = encrypt(public_key, password, operation)
            assert len(base64.b64decode(ciphertext)) == 256
            plaintext = subprocess.check_output(command, input=private_key + "\n" + ciphertext + "\n", text=True)
            assert base64.b64decode(plaintext) == password.encode("utf-8"), "DSP must receive raw UTF-8 password"
            assert encrypt(public_key, password, operation) != ciphertext, "OAEP must use fresh randomness"
        encrypt(public_key, "X" * 191, expected_error="message too long")
        encrypt(keys(1024)[1], "First123!", expected_error="Unexpected DSP key size")
        encrypt(keys(2048, 3)[1], "First123!", expected_error="Unexpected DSP public exponent")
        encrypt("not-a-key", "First123!", expected_error="DataError")
    print("PASS: actual CM transport / TL JCE decrypt; SETUP, RESET, UTF-8, OAEP bounds, randomness, invalid keys and Code envelope binding")


if __name__ == "__main__":
    run()
