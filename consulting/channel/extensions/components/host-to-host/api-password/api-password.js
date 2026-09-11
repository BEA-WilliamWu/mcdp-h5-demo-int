define([
    "knockout",
    "./model",
    "./transport",
    "ojL10n!extensions/resources/nls/hth-api-password",
    "ojs/ojformlayout",
    "ojs/ojvalidationgroup",
    "ojs/ojinputtext",
    "ojs/ojbutton",
    "ojs/ojlabel"
], function (ko, Model, Encrypt, ResourceBundle) {
    "use strict";

    /** Setup/reset form using application RSA encryption and the dashboard lifecycle. */
    return function (params) {
        const self = this,
            mode = String((params.data && params.data.mode) || "SETUP").toUpperCase(),
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

        self.showSubmissionError = function (error) {
            const response = error && (error.responseJSON || error),
                status = response && (response.status && typeof response.status === "object"
                    ? response.status : response),
                message = status && status.message,
                code = message && typeof message.code === "string" ? message.code : null,
                text = error && error.hthInputError ? error.hthInputError
                    : (code && self.nls.submissionErrors[code]) || self.nls.submissionError;

            params.baseModel.showMessages(null, [text], "ERROR");
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

            if (!/^\d{6}$/.test(self.passwordCode() || "")) {
                params.baseModel.showMessages(null, [self.nls.codeFormatError], "ERROR");

                return;
            }

            if (self.submitting()) {
                return;
            }

            self.submitting(true);

            Promise.resolve().then(function () {
                return Encrypt(password, self.passwordCode(), self.requestId,
                    self.isSetup ? "SETUP" : "RESET");
            }).catch(function () {
                throw { hthInputError: self.nls.encryptionError };
            }).then(function (encrypted) {
                const payload = JSON.stringify({
                    encryptedCredentials: encrypted.encryptedCredentials,
                    transportKeyId: encrypted.transportKeyId,
                    requestId: self.requestId
                });

                return (self.isSetup ? Model.setup : Model.reset)(payload);
            }).then(function (data) {
                const status = data && (data.status || data);

                if (!status || status.result !== "SUCCESSFUL" ||
                    (status.message && status.message.type === "ERROR")) {
                    throw { responseJSON: data };
                }

                self.clearSecrets();
                self.requestId = createRequestId();
                self.showConfirmation(true);
            }).catch(function (error) {
                self.clearSecrets();
                self.showSubmissionError(error);
            }).finally(function () {
                self.submitting(false);
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
