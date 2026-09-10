(function () {
    "use strict";

    document.querySelector("body").classList.add("page-is-changing");

    require.config({
        baseUrl: document.currentScript && document.currentScript.dataset.baseUrl ? document.currentScript.dataset.baseUrl : "./",
        waitSeconds: 0,
        paths: {
            "knockout-helper": "framework/js/plugins/amd-helper",
            baseService: "framework/js/base-models/service-base",
            baseServiceNew: "framework/js/base-models/service-base-new",
            composite: "framework/elements",
            baseLogger: "framework/js/base-models/logging-base",
            paperAccordion: "framework/js/plugins/paper-accordion",
            baseModel: "framework/js/base-models/ko/base-model",
            "base-model": "framework/js/base-models/base-model",
            "base-models": "framework/js/base-models",
            worker: "framework/js/base-models/web-worker",
            thirdPartyLibs: "framework/js/libs",
            platform: "framework/js/base-models/platform",
            webAnalytics: "framework/js/base-models/third-party-data-aggregation",
            "jquery-private": "framework/js/plugins/jquery-private",
            load: "framework/js/plugins/load"
        },
        map: {
            "*": {
                jquery: "jquery-private"
            },
            "jquery-private": {
                jquery: "jquery"
            }
        },
        shim: {
            // These non-AMD extensions require the CryptoJS namespace initialized by AES.
            "framework/js/libs/webPinEncrypt/mode-ecb": {
                deps: ["framework/js/libs/webPinEncrypt/aes"]
            },
            "framework/js/libs/webPinEncrypt/pad-zeropadding-min": {
                deps: ["framework/js/libs/webPinEncrypt/aes"]
            },
            "framework/js/libs/webPinEncrypt/pad-nopadding-min": {
                deps: ["framework/js/libs/webPinEncrypt/aes"]
            },
            paperAccordion: {
                exports: "paperAccordion",
                deps: ["jquery"]
            }
        },
        config: {
            text: {
                useXhr: function () {
                    return true;
                }
            }
        },
        // eslint-disable-next-line no-storage/no-browser-storage
        locale: sessionStorage.getItem("user-locale") || document.getElementsByTagName("html")[0].getAttribute("lang") || "en"
    });

    function load(pathConfig) {
        require(["platform", "ojs/ojcore", "jquery"], function (Platform) {

            Platform.getInstance("device").then(function () {
                require(["framework/js/view-model/generic-view-model"]);
            });

            require(["framework/js/configurations/config"], function (SystemConfiguration) {
                const obdxFontCss = "framework/css/obdx-font" + (SystemConfiguration.development.enabled ? "" : "." + SystemConfiguration.system.buildTimestamp + ".css");

                require(["webAnalytics", "css!" + obdxFontCss, pathConfig.altaPath], function (webAnalytics) {
                    webAnalytics.getInstance();
                });
            });

        });
    }

    require(["framework/js/configurations/path-config"], function (PathConfig) {
        require(["base-models/polyfills"], function (Polyfills) {
            const polyfills = new Polyfills();

            polyfills.then(function () {
                require(["fetch"], function () {
                    load(PathConfig);
                });
            });
        });
    });
})();