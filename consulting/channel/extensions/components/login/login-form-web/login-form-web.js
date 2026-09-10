define([
    "knockout",
    "jquery",
    "./model",
    "framework/js/plugins/encrypt",
    "platform",
    "ojs/ojbutton",
    "ojs/ojinputtext",
    "ojs/ojcheckboxset"
], function (ko, $, LoginModel, Encrypt, Platform) {
    "use strict";

    return function (rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.alternateLogin = ko.observable();
        $("#dbContainer").wrapAll("<div class='main-content'></div>");
        self.rightClickDisableIds = ko.observableArray(["login_cdcid|input", "login_username|input", "login_password|input"]);

        if (rootParams.baseModel.large()) {
            rootParams.dashboard.isDashboard(false);
        } else {
            rootParams.dashboard.isDashboard(true);
        }

        rootParams.baseModel.showMerchantHeaderFooterChanges(false);
        rootParams.baseModel.showMerchantLink(true);

        /** if (rootParams.dashboard.isDashboard() === false && rootParams.baseModel.small()) {
            rootParams.dashboard.headerName(self.nls.loginForm.labels.loginHeaderMobile);
        } else {
            rootParams.dashboard.headerName(self.nls.loginForm.labels.loginHeader);
        }

        rootParams.dashboard.headerCaption(self.nls.loginForm.labels.subHeader); */

        self.openAPIFlag = ko.observable(false);
        self.enbleSecurityCodeLogin = ko.observable(false);

        rootParams.baseModel.registerComponent("user-credentials", "registration");

        let GenericViewModel = null;

        self.showPopup = false;

        // eslint-disable-next-line no-storage/no-browser-storage
        sessionStorage.setItem("merchantLoginForm", "N");

        if (rootParams.baseModel.large()) {
            rootParams.baseModel.preFetch(["ojs/ojchart", "ojs/ojtable", "ojs/ojconveyorbelt", "ojs/ojarraytabledatasource"]);
        }

        /**
         * Check if token is recieved and store it in local storage
         */
        if (rootParams.root.queryMap && rootParams.root.queryMap.token) {
            // eslint-disable-next-line no-storage/no-browser-storage
            localStorage.setItem("token", rootParams.root.queryMap.token);
        }

        /**
         * Check if locale is recieved and store it in session storage
         */
        if (rootParams.root.queryMap && rootParams.root.queryMap.locale) {
            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("user-locale", rootParams.root.queryMap.locale);
        }

        /**
         * Check if token is present in local storage and call API to fetch user details
         */
        // eslint-disable-next-line no-storage/no-browser-storage
        if (localStorage.getItem("token") !== null) {
            // eslint-disable-next-line no-storage/no-browser-storage
            LoginModel.fetchUserDetailsFromToken(localStorage.getItem("token")).done(function (data) {
                self.cdcId(data.cdcId);
                self.username(data.userId);
                self.isMigratedUser(data.migratedUser);
                // eslint-disable-next-line no-storage/no-browser-storage
                localStorage.removeItem("token");
            }).fail(function () {
                // eslint-disable-next-line no-storage/no-browser-storage
                localStorage.removeItem("token");
            });
        }

        self.isLogonWithToken = ko.observable(false);

        self.afterRender = function (genericViewModel) {
            GenericViewModel = genericViewModel;

            if (genericViewModel.queryMap) {
                if (genericViewModel.queryMap.p_error_code !== null && genericViewModel.queryMap.p_error_code === "OAM-10") {
                    self.message(self.nls.loginForm.validationMsgs.errrorOAM10);
                } else if (genericViewModel.queryMap.p_error_code !== null && genericViewModel.queryMap.p_error_code === "OAM-5") {
                    self.message(self.nls.loginForm.validationMsgs.errrorOAM5);
                } else if (genericViewModel.queryMap.p_error_code) {
                    self.message(self.nls.loginForm.validationMsgs.invalidCredentials);
                }
            }
        };

        function handleGetEnbleSecurityCodeLogin() {
            LoginModel.getEnbleSecurityCodeLogin().done(function (data) {
                if (data && data.listCustomConfigDTO && data.listCustomConfigDTO.length > 0) {
                    const securityCodeConfig = data.listCustomConfigDTO.find(item => {
                        return item.propertyId === "ENABLE_SECURITYCODE_LOGIN";
                    });

                    if (securityCodeConfig) {
                        self.enbleSecurityCodeLogin(securityCodeConfig.propertyValue === "Y");
                    }
                } else {
                    self.enbleSecurityCodeLogin(false);
                }
            });
        }

        handleGetEnbleSecurityCodeLogin();

        function loginOauth(genericViewModel) {

            Encrypt(self.password(), genericViewModel.queryMap.applicationType).then(function (password) {

                const form = document.createElement("form");

                form.setAttribute("method", "POST");
                form.setAttribute("action", "/" + genericViewModel.queryMap.applicationType + "/j_security_check");

                let hiddenField = document.createElement("input");

                hiddenField.setAttribute("type", "hidden");
                hiddenField.setAttribute("name", "j_username");
                hiddenField.setAttribute("value", self.username().toUpperCase());
                form.appendChild(hiddenField);
                hiddenField = document.createElement("input");
                hiddenField.setAttribute("type", "hidden");
                hiddenField.setAttribute("name", "j_password");
                hiddenField.setAttribute("value", password);
                form.appendChild(hiddenField);
                document.body.appendChild(form);
                form.submit();

            });

        }

        function loginAuthenticator(genericViewModel) {

            Platform.getInstance("authentication").then(function (platform) {
                let username, loginType = "login";

                if (self.cdcId() === "1") {
                    username = self.username();
                } else {
                    username = self.username().toUpperCase() + "@015" + self.cdcId();
                }

                if(self.isOffshoreEnv()) {
                    loginType = self.isMigratedUser() ? "loginMigratedUser" : "ebkLogin";
                } else {
                    loginType = "login";
                }

                self.userType="customer";

                if(self.isLogonWithToken()){
                    self.userType="securityCode";
                }

                platform(loginType, username.toUpperCase(), self.password(), self.userType, "015" + self.cdcId(), genericViewModel.queryMap).then(function () {
                        const tabName = Math.random();

                        window.name = tabName;

                        // eslint-disable-next-line no-storage/no-browser-storage
                        localStorage.setItem("tabname", tabName);
                        // eslint-disable-next-line no-storage/no-browser-storage
                        localStorage.setItem("loggedIn", "Y");

                    if (self.rememberCdcId()[0] !== undefined) {
                        // eslint-disable-next-line no-storage/no-browser-storage
                        localStorage.setItem("rememberCdcId", self.cdcId());
                    } else {
                        // eslint-disable-next-line no-storage/no-browser-storage
                        localStorage.removeItem("rememberCdcId");
                    }

                        genericViewModel.resetLayout(null, true);
                    })
                    .catch(function (error) {
                        self.isLoginPending(false);
                        $("#login-button").prop("disabled", false);

                        if (error && error.type === "INVALID_CRED") {
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("tabname");
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("loggedIn");

                            switch (window.decodeURIComponent(error.message)) {
                                case "INVALID_LOGIN_DETAILS":
                                    if(self.isLogonWithToken()){
                                        rootParams.baseModel.showMessages(null, [self.nls.loginForm.validationMsgs.SECURITY_CODE_INVALID_LOGIN_DETAILS], "ERROR");
                                    }else{
                                        self.message(self.nls.loginForm.validationMsgs.CM_Login_INVALID_CRED);
                                    }

                                    break;
                                case "INVALID_LOGIN_DETAILS_CCB":
                                    self.message(self.nls.loginForm.validationMsgs.CCB_INVALID_CRED);
                                    break;
                                case "MAX_NUMBER_OF_ATTEMPTS_REACHED":
                                    if(self.isLogonWithToken()){
                                        rootParams.baseModel.showMessages(null, [self.nls.loginForm.validationMsgs.SECURITY_CODE_MAX_NUMBER_OF_ATTEMPTS_REACHED], "ERROR");
                                    }else{
                                        self.message(self.nls.loginForm.validationMsgs.CM_Login_MAX_NUMBER_OF_ATTEMPTS_REACHED);
                                    }

                                    break;
                                case "ITOKEN_LAST_TRY_LEFT":
                                    if(self.isLogonWithToken()){
                                        rootParams.baseModel.showMessages(null, [self.nls.loginForm.validationMsgs.SECURITY_CODE_LAST_TRY_LEFT], "ERROR");
                                    }else{
                                        self.message(self.nls.loginForm.validationMsgs.ITOKEN_LAST_TRY_LEFT);
                                    }

                                    break;
                                case "PIN_HAS_EXPIRED":
                                    self.message(self.nls.loginForm.validationMsgs.PIN_HAS_EXPIRED);
                                    break;
                                case "PARTY_ACCESS_NOT_ALLOWED":
                                    self.message(self.nls.loginForm.validationMsgs.PARTY_ACCESS_NOT_ALLOWED);
                                    break;
                                case "ACCOUNT_CLOSED":
                                    self.message(self.nls.loginForm.validationMsgs.ACCOUNT_CLOSED);
                                    break;
                                case "BEING_RESET":
                                    self.message(self.nls.loginForm.validationMsgs.BEING_RESET);
                                    break;
                                case "DIGX_CZ_AUTH_PIN_NOT_ACTIVATED":
                                    self.message(self.nls.loginForm.validationMsgs.DIGX_CZ_AUTH_PIN_NOT_ACTIVATED);
                                    break;
                                case "ACCOUNT_FREEZE_ERROR":
                                    self.message(self.nls.loginForm.validationMsgs.ACCOUNT_FREEZE_ERROR);
                                    break;
                                case "HOLD_FOR_DIFF_REASON":
                                    self.message(self.nls.loginForm.validationMsgs.HOLD_FOR_DIFF_REASON);
                                    break;
                                case "DIGX_HOST_EBK_EBIE0398":
                                    self.message(self.nls.loginForm.validationMsgs.DIGX_HOST_EBK_EBIE0398);
                                    break;
                                case "INVALID_AUTH_REDIRECT":
                                    rootParams.baseModel.showMessages(null, [self.nls.loginForm.validationMsgs.INVALID_AUTH_REDIRECT], "ERROR");
                                    break;
                               /** //CCB Decommission Stage 2 Changes
                                case "MIGRATED_NOT_ONBOARDED_WITHOUT_SIGNER":
                                   self.message(self.nls.loginForm.validationMsgs.MIGRATED_NOT_ONBOARDED_WITHOUT_SIGNER);
                                    break;
                                case "MIGRATED_NOT_ONBOARDED_WITH_SIGNER":
                                    self.message(self.nls.loginForm.validationMsgs.MIGRATED_NOT_ONBOARDED_WITH_SIGNER);
                                    break;*/
                                default:
                                    self.message(window.decodeURIComponent(error.message));
                                    break;
                            }
                        } else if (error && error.type === "FORCE_CHANGE") {
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("tabname");
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("loggedIn");

                            rootParams.baseModel.registerComponent("force-change-password", "force-change-password");

                            rootParams.dashboard.loadComponent("force-change-password", {
                                userName: username.toUpperCase(),
                                cdcId: "015" + self.cdcId(),
                                isMigratedUser: self.isMigratedUser(),
                                isOffshoreEnv: self.isOffshoreEnv(),
                                genericViewModel: genericViewModel
                            });
                        } else if (error && error.type === "FORCE_CHANGE_SIGNER") {
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("tabname");
                            // eslint-disable-next-line no-storage/no-browser-storage
                            localStorage.removeItem("loggedIn");

                            rootParams.baseModel.registerComponent("change-signer-pin", "change-signer-pin");

                            rootParams.dashboard.loadComponent("change-signer-pin", {
                                changeType: "forceChangeSigner",
                                userName: username.toUpperCase(),
                                cdcId: "015" + self.cdcId(),
                                password: self.password(),
                                isMigratedUser: self.isMigratedUser(),
                                isOffshoreEnv: self.isOffshoreEnv(),
                                genericViewModel: genericViewModel
                            });
                        }
                    });
            });
        }

        $(window).keypress(function (e) {
            if (e.which === 13 && e.target.parentNode.id !== "login-button") {
                document.activeElement.blur();

                if (self.stepOneLogin() && self.cdcId() && self.username()) {
                    self.nextToLogin();
                } else {
                    setTimeout(function () {
                        if (self.isCDCAccAvl() && self.isUserNameAvl() && self.isPasswordAvl()) {
                            self.onLogin(GenericViewModel);
                        }
                    }, 500);
                }
            }
        });

        $(document).bind("contextmenu", function () {
            if (self.rightClickDisableIds().indexOf(document.activeElement.id) !== -1) {
                rootParams.baseModel.showMessages(null, [self.nls.loginForm.validationMsgs.rightClickDisabled], "ERROR");

                return false;
            }
        });

        self.onLogin = function (genericViewModel) {
            self.message(null);
            self.isLoginPending(true);
            $("#login-button").prop("disabled", true);

            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("loginPinReminderLoaded", "false");
            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("signerPinReminderLoaded", "false");
            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("signerPinResetPageLoaded", "false");
            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("bounceBackReminderLoaded", "false");
            // eslint-disable-next-line no-storage/no-browser-storage
            sessionStorage.setItem("hthApiPasswordSetupPromptLoaded", "false");

            if (genericViewModel.queryMap && genericViewModel.queryMap.applicationType === "digx-auth") {
                loginOauth(genericViewModel);
            } else {
                LoginModel.getOffshoreEnvFlag().done(function(data) {
                    if(data && data.listCustomConfigDTO && data.listCustomConfigDTO.length > 0) {
                        self.isOffshoreEnv(data.listCustomConfigDTO[0].propertyValue === "true");
                    } else {
                        self.isOffshoreEnv(false);
                    }

                    loginAuthenticator(genericViewModel);
                }).fail(function() {
                    loginAuthenticator(genericViewModel);
                });
            }
        };

        self.changeLoginMethod = function () {
            self.isLogonWithToken(!self.isLogonWithToken());
            self.password("");

            if(self.isLogonWithToken()){

                const a = setInterval(() => {
                    if ($("#login_security_code input").length) {
                        clearInterval(a);
                        $("#login_security_code input").focus();
                    }
                }, 10);

            }else{

                const a = setInterval(() => {
                    if ($("#login_password input").length) {
                        clearInterval(a);
                        $("#login_password input").focus();
                    }
                }, 10);

            }
        };

        self.nextToLogin = function () {
            self.stepOneLogin(false);
            self.stepTwoLogin(true);

            if (self.enbleSecurityCodeLogin()) {
                self.isShowLoginModeSel(true);
            }
        };

        self.forgotPass = function () {
            rootParams.baseModel.registerComponent("user-information", "recovery");
            rootParams.dashboard.loadComponent("user-information", {});
        };

        self.forgotUserId = function () {
            rootParams.baseModel.registerComponent("user-recovery-info", "recovery");
            rootParams.dashboard.loadComponent("user-recovery-info", {});
        };

        /**
         * @function redirectToPinActivation
         * This function redirects user to WHP link with selected language
         */
        self.redirectToPinActivation = function () {
            require(["load!extensions/json/configuration/whp-urls.json"], function (data) {
                window.open(data.pinActivation[rootParams.baseModel.getLocale()], "_blank");
            });
        };

        /**
         * @function redirectToResetPin
         * This function redirects user to WHP link with selected language
         */
        self.redirectToResetPin = function () {
            require(["load!extensions/json/configuration/whp-urls.json"], function (data) {
                window.open(data.resetPin[rootParams.baseModel.getLocale()], "_blank");
            });
        };

        let loginPasswordShow = true;

        self.toggleEye = function () {
            loginPasswordShow = !loginPasswordShow;

            const eye = $("#login_password_eye");

            eye.removeClass("icon-eye icon-eye-slash");

            if (loginPasswordShow) {
                eye.addClass("icon-eye-slash");

                if(!this.isLogonWithToken()){
                    $("#login_password input").prop({
                        type: "password"
                    });
                }else{
                    $("#login_security_code input").prop({
                        type: "password"
                    });
                }

            } else {

               eye.addClass("icon-eye");

               if(!this.isLogonWithToken()){
                    $("#login_password input").prop({
                        type: "text"
                    });
                }else{
                    $("#login_security_code input").prop({
                        type: "text"
                    });
                }

            }

        };

        $(document).on("blur", "#forgotPassword", function () {
            $("input[name='username']").focus();
        });

        $(document).ready(function () {
            const a = setInterval(() => {
                if ($("#login_cdcid input").length) {
                    clearInterval(a);
                    $("#login_cdcid input").focus();
                }
            }, 10);
        });

        self.registerUser = function () {
            rootParams.dashboard.loadComponent("user-credentials", {});
        };

        self.attachRegisterNowClick = function () {
            const interval = setInterval(() => {
                if ($("#registerNow").length) {
                    clearInterval(interval);

                    $("#registerNow").click(function () {
                        self.registerUser();
                    });
                }
            }, 50);
        };

        /*
         *  Defect #4887 - Regression Test - Customer Service Authentication - Login Page issues
         *  3. Clicking previous page should direct to login page without any value being entered.
         */
        $(document).ready(function ($) {
            $(window).on("popstate", function () {
                location.reload(true);
            });
        });

        /*
         *  Defect #4887 - Regression Test - Customer Service Authentication - Login Page issues
         *  4. Login page full page should not allow right click action.
         */
        document.addEventListener("contextmenu", (event) => {
            event.preventDefault();
        });
    };
});