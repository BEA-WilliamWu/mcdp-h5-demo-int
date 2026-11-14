define([
    "framework/js/plugins/encryption/libs/js-big-integer"
], function (BigInteger) {
    "use strict";

    // Match DSP RsaCryptoCipher / SunJCE: OAEP SHA-256 with MGF1 SHA-1.
    // WebCrypto uses the same hash for OAEP and MGF1, so encode OAEP here,
    // then reuse BCO's public RSA arithmetic. RFC 8017 sections 7.1.1 / B.2.1.
    function bytesFromBase64(value) {
        const padded = value.replace(/-/g, "+").replace(/_/g, "/");

        return Uint8Array.from(window.atob(padded + "=".repeat((4 - (padded.length % 4)) % 4)), function (character) {
            return character.charCodeAt(0);
        });
    }

    function hex(bytes) {
        return Array.from(bytes, function (value) {
            return ("0" + value.toString(16)).slice(-2);
        }).join("");
    }

    function mask(seed, length) {
        const hashes = [];

        for (let counter = 0; counter < Math.ceil(length / 20); counter++) {
            const input = new Uint8Array(seed.length + 4);

            input.set(seed);
            new DataView(input.buffer).setUint32(seed.length, counter, false);
            hashes.push(window.crypto.subtle.digest("SHA-1", input));
        }

        return Promise.all(hashes).then(function (values) {
            const result = new Uint8Array(values.length * 20);

            values.forEach(function (value, index) {
                result.set(new Uint8Array(value), index * 20);
            });

            return result.slice(0, length);
        });
    }

    return function (publicKey, password) {
        const crypto = window.crypto,
            message = new TextEncoder().encode(password);

        if (message.length > 190) {
            return Promise.reject(new Error("DSP RSA message too long"));
        }

        // Export only the server's public key to obtain the RSA modulus/exponent.
        return crypto.subtle.importKey("spki", bytesFromBase64(publicKey), {
            name: "RSA-OAEP",
            hash: "SHA-256"
        }, true, ["encrypt"]).then(function (key) {
            if (key.algorithm.modulusLength !== 2048) {
                throw new Error("Unexpected DSP key size");
            }

            return Promise.all([
                crypto.subtle.exportKey("jwk", key),
                crypto.subtle.digest("SHA-256", new Uint8Array(0))
            ]);
        }).then(function (values) {
            const modulus = new BigInteger(hex(bytesFromBase64(values[0].n)), 16),
                exponent = parseInt(hex(bytesFromBase64(values[0].e)), 16),
                seed = crypto.getRandomValues(new Uint8Array(32)),
                data = new Uint8Array(223);

            if (exponent !== 65537) {
                throw new Error("Unexpected DSP public exponent");
            }

            data.set(new Uint8Array(values[1]));
            data[data.length - message.length - 1] = 1;
            data.set(message, data.length - message.length);

            return mask(seed, data.length).then(function (dataMask) {
                const maskedData = data.map(function (value, index) {
                    return value ^ dataMask[index];
                });

                return mask(maskedData, seed.length).then(function (seedMask) {
                    const encoded = new Uint8Array(256);

                    encoded.set(seed.map(function (value, index) {
                        return value ^ seedMask[index];
                    }), 1);

                    encoded.set(maskedData, 33);

                    const encrypted = new BigInteger(encoded).modPowInt(exponent, modulus).toString(16),
                        padded = "0".repeat(512 - encrypted.length) + encrypted;

                    return Uint8Array.from(padded.match(/../g), function (value) {
                        return parseInt(value, 16);
                    });
                });
            });
        });
    };
});
