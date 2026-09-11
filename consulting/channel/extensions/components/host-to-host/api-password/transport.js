define([
    "baseService",
    "framework/js/plugins/encryption/rsa-encrypt"
], function (BaseService, RSAKey) {
    "use strict";

    const baseService = BaseService.getInstance();

    /** Encrypt the HTH envelope using the short-lived key from the authenticated session. */
    return function (password, code, requestId, operation) {
        return baseService.fetch({
            url: "hostToHostApiPassword/status?transport=true",
            version: "cz/v1",
            throttle: false,
            showMessage: false
        }).then(function (response) {
            const key = response && response.transportKey,
                rsa = new RSAKey();

            if (!key || typeof key.keyId !== "string" || !key.keyId || !/^[0-9a-f]+$/i.test(key.modulus || "") ||
                !/^[0-9a-f]+$/i.test(key.publicExponent || "") ||
                key.modulus.replace(/^0+/, "").length < 512) {
                throw new Error("HTH application public key unavailable");
            }

            rsa.setPublic(key.modulus, key.publicExponent);

            const encrypted = rsa.encryptb64(JSON.stringify([
                "HTH2", operation, requestId, password, code
            ]));

            if (typeof encrypted !== "string" || !encrypted.length) {
                throw new Error("HTH credential encryption failed");
            }

            return {
                encryptedCredentials: encrypted,
                transportKeyId: key.keyId
            };
        });
    };
});
