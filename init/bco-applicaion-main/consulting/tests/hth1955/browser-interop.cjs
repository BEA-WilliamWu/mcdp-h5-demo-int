// Actual CM AMD transport + Node WebCrypto. Public-key fetch is a fixture; no remote calls.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const crypto = require("node:crypto");
const pair = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
const jwk = pair.publicKey.export({ format: "jwk" });
const requestId = crypto.randomUUID();
let encrypt, envelope;
class RSAKey {
    setPublic(modulus, exponent) { assert.ok(modulus); assert.equal(exponent, "010001"); }
    encryptb64(text) {
        envelope = JSON.parse(text);
        return crypto.publicEncrypt({ key: pair.publicKey, padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(text)).toString("base64");
    }
}
const context = {
    TextEncoder, Uint8Array, Promise, Error, encodeURIComponent,
    window: { crypto: crypto.webcrypto, atob: s => Buffer.from(s, "base64").toString("binary"), btoa: s => Buffer.from(s, "binary").toString("base64") },
    define: (deps, factory) => { encrypt = factory({ getInstance: () => ({ fetch: request => {
        assert.ok(request.url.endsWith("requestId=" + requestId));
        return Promise.resolve({ transportKey: { keyId: "test-key", modulus: Buffer.from(jwk.n,"base64url").toString("hex"),
            publicExponent: "010001", dspPublicKey: process.argv[2] } });
    } }) }, RSAKey); }
};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,"../../channel/extensions/components/host-to-host/api-password/transport.js"),"utf8"),context);
encrypt("Browser123","314159",requestId,"SETUP").then(result => {
    assert.equal(envelope[0],"HTH3"); assert.equal(envelope[1],"SETUP");
    assert.equal(envelope[2],requestId); assert.equal(envelope[3],"314159");
    assert.equal(envelope[4],crypto.createHash("sha256").update(result.dspEncryptedPassword).digest("base64"));
    assert.ok(!JSON.stringify(result).includes("Browser123"));
    process.stdout.write(JSON.stringify(result));
}).catch(error => { console.error(error); process.exitCode=1; });
