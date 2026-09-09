define([
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/hth-api-password",
    "ojs/ojformlayout",
    "ojs/ojvalidationgroup",
    "ojs/ojinputtext",
    "ojs/ojbutton",
    "ojs/ojlabel"
], function (ko, Model, ResourceBundle) {
    "use strict";

    /** Setup/reset form using the shared PIN transport encryption and dashboard lifecycle. */
    return function (params) {
        const self = this,
            mode = String((params.data && params.data.mode) || "SETUP").toUpperCase(),
            userProfile = params.dashboard.userData.userProfile,
            partyId = userProfile.partyId && userProfile.partyId.value
                ? userProfile.partyId.value : userProfile.partyId,
            createRequestId = function () {
                if (window.crypto && typeof window.crypto.randomUUID === "function") {
                    return window.crypto.randomUUID();
                }

                return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (char) {
                    const random = window.crypto.getRandomValues(new Uint8Array(1))[0] % 16,
                        value = char === "x" ? random : (random & 3) | 8;

                    return value.toString(16);
                });
            };

        self.nls = ResourceBundle;
        self.isSetup = mode === "SETUP";
        self.newPassword = ko.observable();
        self.confirmPassword = ko.observable();
        self.passwordCode = ko.observable();
        self.submitting = ko.observable(false);
        self.showConfirmation = ko.observable(false);
        // Keep the request ID across retries so an uncertain response cannot rotate twice.
        self.requestId = createRequestId();

        self.policy = ko.observable({
            minLength: 8,
            maxLength: 16,
            numericRequired: 2,
            alphabetRequired: 1,
            specialCharsAllowed: false,
            spacesAllowed: false
        });

        params.dashboard.headerName(self.isSetup
            ? self.nls.setupHeader : self.nls.resetHeader);

        self.clearSecrets = function () {
            self.newPassword(null);
            self.confirmPassword(null);
            self.passwordCode(null);
        };

        self.cancel = function () {
            self.clearSecrets();
            params.dashboard.switchModule();
        };

        // Client feedback uses the API policy; the service also validates every submission.
        self.matchesPolicy = function (password) {
            const policy = self.policy(),
                minimumLength = Number(policy.minLength || 0),
                maximumLength = Number(policy.maxLength || 0),
                alphabetCount = (password.match(/[A-Za-z]/g) || []).length,
                numericCount = (password.match(/[0-9]/g) || []).length;

            if (password.length < minimumLength || password.length > maximumLength ||
                alphabetCount < Number(policy.alphabetRequired || 0) ||
                numericCount < Number(policy.numericRequired || 0)) {
                return false;
            }

            return password.split("").every(function (character) {
                if (/[A-Za-z0-9]/.test(character)) {
                    return true;
                }

                if (/\s/.test(character)) {
                    return policy.spacesAllowed === true;
                }

                return policy.specialCharsAllowed === true;
            });
        };

        self.submit = function () {
            const tracker = document.getElementById("hthApiPasswordTracker"),
                password = self.newPassword() || "",
                confirmation = self.confirmPassword() || "";

            if (!tracker || !params.baseModel.showComponentValidationErrors(tracker)) {
                return;
            }

            if (password !== confirmation) {
                params.baseModel.showMessages(null, [self.nls.passwordMismatch], "ERROR");

                return;
            }

            if (!self.matchesPolicy(password)) {
                params.baseModel.showMessages(null, [self.nls.passwordPolicyError], "ERROR");

                return;
            }

            if (self.submitting()) {
                return;
            }

            self.submitting(true);

            require(["framework/js/plugins/customer-pin-encrypt"], function (Encrypt) {
                Encrypt([password, self.passwordCode()], userProfile.userName, partyId)
                    .then(function (encrypted) {
                        const payload = JSON.stringify({
                            encryptedCredentials: encrypted[0],
                            requestId: self.requestId
                        }),
                            config = {
                                headers: {
                                    RSAKeyIndicator: encrypted[2],
                                    token: encrypted[1]
                                }
                            };

                        return (self.isSetup ? Model.setup : Model.reset)(payload, config);
                    }).then(function () {
                        self.clearSecrets();
                        self.requestId = createRequestId();
                        self.showConfirmation(true);
                    }).catch(function () {
                        self.clearSecrets();
                    }).finally(function () {
                        self.submitting(false);
                    });
            });
        };

        Model.status().then(function (data) {
            if (data && data.passwordPolicy) {
                self.policy(data.passwordPolicy);
            }
        }).catch(function () {
            return null;
        });

        // Both dashboard disposal and custom-element detachment clear the form secrets.
        self.disconnected = self.clearSecrets;
        self.dispose = self.clearSecrets;
    };
});
