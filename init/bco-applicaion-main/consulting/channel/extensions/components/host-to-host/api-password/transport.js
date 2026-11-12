define([
    "baseService",
    "framework/js/plugins/encryption/rsa-encrypt"
], function (BaseService, RSAKey) {
    "use strict";

    const baseService = BaseService.getInstance(),
        encoder = new TextEncoder(),
        base64 = function (buffer) {
            return window.btoa(String.fromCharCode.apply(null, new Uint8Array(buffer)));
        };

    // The password hash is encrypted for DSP; CM can decrypt only the approval Code envelope.
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

            const publicBytes = Uint8Array.from(window.atob(key.dspPublicKey), function (character) {
                return character.charCodeAt(0);
            });

            return Promise.all([
                crypto.subtle.importKey("spki", publicBytes, { name: "RSA-OAEP", hash: "SHA-256" }, false, ["encrypt"]),
                crypto.subtle.digest("SHA-256", encoder.encode(password))
            ]).then(function (values) {
                if (values[0].algorithm.modulusLength !== 2048) {
                    throw new Error("Unexpected DSP key size");
                }

                return crypto.subtle.encrypt({ name: "RSA-OAEP" }, values[0], encoder.encode(base64(values[1])));
            }).then(function (ciphertext) {
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
