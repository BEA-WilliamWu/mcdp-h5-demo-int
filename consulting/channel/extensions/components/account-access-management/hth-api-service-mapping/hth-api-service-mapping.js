define([
    "knockout",
    "ojL10n!extensions/resources/nls/access-management",
    "ojs/ojbutton",
    "framework/elements/api/page-section/loader"
], function (ko, resourceBundle) {
    "use strict";

    /*
     * Second step of HTH maintenance. Only accounts selected on the linkage step are displayed.
     * API options already came from the enterprise-enabled server catalogue; the checker service
     * validates them again before an approved snapshot changes effective access.
     */
    return function (rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || rootModel;

        self.nls = resourceBundle;
        self.context = ko.unwrap(params.hthLinkageContext) || {};
        self.summaryParams = params.summaryParams || {};
        self.access = ko.unwrap(params.access) || {};
        self.originalAccounts = ko.toJS(ko.unwrap(params.originalAccounts)) || [];
        self.action = ko.unwrap(params.action) || "CREATE";
        self.isEditable = ko.pureComputed(function () {
            return self.action !== "VIEW";
        });
        self.accounts = ko.observableArray((ko.unwrap(params.accounts) || []).filter(function (account) {
            return account.selected();
        }));

        rootParams.dashboard.headerName(self.nls.headers.hthApiMapping);

        self.applyFirstToAll = function () {
            const accounts = self.accounts();

            if (!self.isEditable() || accounts.length < 2) {
                return;
            }
            const selectedCodes = {};

            // Copy by API code rather than array position because catalogue display order may vary.
            accounts[0].apiServices.forEach(function (api) {
                selectedCodes[api.apiCode] = api.selected();
            });
            accounts.slice(1).forEach(function (account) {
                account.apiServices.forEach(function (api) {
                    api.selected(!!selectedCodes[api.apiCode]);
                });
            });
        };

        self.next = function () {
            if (!self.isEditable()) {
                return;
            }
            const missingMapping = self.accounts().some(function (account) {
                return !account.apiServices.some(function (api) {
                    return api.selected();
                });
            });

            if (missingMapping) {
                rootParams.baseModel.showMessages(null,
                    [self.nls.info.hthSelectApi], "ERROR");
                return;
            }
            rootParams.dashboard.loadComponent("review-hth-user-access", {
                hthLinkageContext: self.context,
                summaryParams: self.summaryParams,
                access: self.access,
                accounts: ko.unwrap(params.accounts),
                originalAccounts: self.originalAccounts,
                action: self.action
            });
        };

        self.cancel = function () {
            rootParams.dashboard.switchModule(true);
        };

        self.back = function () {
            rootParams.dashboard.loadComponent("hth-account-linkage", {
                hthLinkageContext: self.context,
                summaryParams: self.summaryParams,
                preloadedAccess: self.access,
                preloadedAccounts: ko.unwrap(params.accounts),
                originalAccounts: self.originalAccounts,
                action: self.action
            });
        };
    };
});
