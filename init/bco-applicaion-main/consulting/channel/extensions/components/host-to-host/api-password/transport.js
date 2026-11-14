define([
    "baseService",
    "framework/js/plugins/encryption/rsa-encrypt",
    "./dsp-encrypt"
], function (BaseService, RSAKey, encryptForDsp) {
    "use strict";

    const baseService = BaseService.getInstance(),
        encoder = new TextEncoder(),
        base64 = function (buffer) {
            return window.btoa(String.fromCharCode.apply(null, new Uint8Array(buffer)));
        };

    // DSP decrypts and hashes the password; CM can decrypt only the approval Code envelope.
    return function (password, code, requestId, operation) {
        const crypto = window.crypto;

        if (!crypto || !crypto.subtle) {
            return Promise.reject(new Error("Secure browser context required"));
        }

        return baseService.fetch({
            url: "hostToHostApiPassword/status?transport=true&requestId=" + encodeURIComponent(requestId),
            version: "cz/v1",
            throttle: false,
            showMessage: false
        }).then(function (response) {
            const key = response && response.transportKey,
                rsa = new RSAKey();

            if (!key || !key.keyId || !key.dspPublicKey || !/^[0-9a-f]+$/i.test(key.modulus || "") ||
                !/^[0-9a-f]+$/i.test(key.publicExponent || "") || key.modulus.replace(/^0+/, "").length < 512) {
                throw new Error("HTH transport key unavailable");
            }

            rsa.setPublic(key.modulus, key.publicExponent);

            return encryptForDsp(key.dspPublicKey, password).then(function (ciphertext) {
                const dspEncryptedPassword = base64(ciphertext);

                return crypto.subtle.digest("SHA-256", encoder.encode(dspEncryptedPassword)).then(function (digest) {
                    const encrypted = rsa.encryptb64(JSON.stringify([
                        "HTH3", operation, requestId, code, base64(digest)
                    ]));

                    if (typeof encrypted !== "string" || !encrypted.length) {
                        throw new Error("HTH Code encryption failed");
                    }

                    return {
                        encryptedCredentials: encrypted,
                        transportKeyId: key.keyId,
                        dspEncryptedPassword: dspEncryptedPassword
                    };
                });
            });
        });
    };
});
