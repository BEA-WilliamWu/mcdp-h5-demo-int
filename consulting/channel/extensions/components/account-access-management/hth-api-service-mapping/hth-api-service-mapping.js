define([
    "ojs/ojcore",
    "knockout",
    "ojL10n!extensions/resources/nls/access-management",
    "extensions/generic/service-extension",
    "ojs/ojbutton",
    "ojs/ojtable",
    "ojs/ojrowexpander",
    "ojs/ojflattenedtreedatagriddatasource",
    "ojs/ojjsontreedatasource",
    "ojs/ojflattenedtreetabledatasource",
    "framework/elements/api/nav-bar/loader",
    "framework/elements/api/page-section/loader"
], function (oj, ko, resourceBundle, serviceExtension) {
    "use strict";

    /*
     * Second step of HTH maintenance. Only accounts selected on the linkage step are displayed.
     * API options already came from the enterprise-enabled server catalogue; the checker service
     * validates them again before an approved snapshot changes effective access.
     */
    return function (rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || rootModel,
            read = function (value) {
                return ko.isObservable(value) ? value() : value;
            },
            normalizeAccountType = function (value) {
                const accountType = String(read(value) || "").toUpperCase();

                if (accountType === "TRD" || accountType === "TERM_DEPOSIT") {
                    return "TD";
                }

                if (accountType === "DEMAND_DEPOSIT") {
                    return "CSA";
                }

                return accountType;
            },
            mapApi = function (api) {
                return Object.assign({}, api || {}, {
                    apiMasterId: read(api && api.apiMasterId),
                    apiCode: read(api && api.apiCode),
                    apiName: read(api && api.apiName),
                    displayOrder: read(api && api.displayOrder),
                    selected: ko.observable(!!read(api && api.selected))
                });
            },
            mapAccount = function (account) {
                account = account || {};

                const accountNumberObject = read(account.accountNumber),
                    canonicalNumber = String(accountNumberObject
                        && typeof accountNumberObject === "object"
                        ? read(accountNumberObject.value) || read(accountNumberObject.displayValue) || ""
                        : accountNumberObject || ""),
                    suppliedDisplay = read(account.accountNumberDisplay)
                        || (accountNumberObject && typeof accountNumberObject === "object"
                            ? read(accountNumberObject.displayValue) : ""),
                    accountType = normalizeAccountType(account.accountType),
                    currency = read(account.currency) || read(account.currencyCode) || "",
                    displayName = read(account.displayName) || "",
                    convertedDisplay = !suppliedDisplay && canonicalNumber
                        ? serviceExtension.int2extAccNo(String(canonicalNumber), "Y") : "";

                return Object.assign({}, account, {
                    accountNumber: canonicalNumber,
                    accountNumberDisplay: suppliedDisplay || convertedDisplay
                        || canonicalNumber || "-",
                    maskedAccountNumber: read(account.maskedAccountNumber) || "",
                    accountType: accountType,
                    currency: currency,
                    displayName: displayName,
                    displayCurrency: currency || "-",
                    displayAccountType: displayName || (accountType === "TD"
                        ? resourceBundle.navLabels.TD : accountType === "CSA"
                            ? resourceBundle.navLabels.CASA : accountType || "-"),
                    selected: ko.observable(!!read(account.selected)),
                    apiServices: (read(account.apiServices) || []).map(mapApi)
                });
            };

        self.nls = resourceBundle;
        self.context = ko.unwrap(params.hthLinkageContext) || {};

        self.userIdDisplay = String(self.context.username || self.context.closeId || "-")
            .split("@")[0];

        self.summaryParams = params.summaryParams || {};
        self.access = ko.unwrap(params.access) || {};
        self.originalAccounts = ko.toJS(ko.unwrap(params.originalAccounts)) || [];
        self.action = ko.observable(ko.unwrap(params.action) || "CREATE");
        self.editing = ko.observable(self.action() !== "VIEW");
        self.activeAccountType = ko.observable("CSA");
        self.menuSelection = ko.observable("CASA");

        self.tabLists = ko.observableArray([{
            id: "CASA",
            label: self.nls.navLabels.CASA
        }, {
            id: "TRD",
            label: self.nls.navLabels.TD
        }]);

        self.uiOptions = {
            menuFloat: "right",
            fullWidth: false,
            defaultOption: self.menuSelection
        };

        self.isEditable = ko.pureComputed(function () {
            return self.editing();
        });

        self.allAccounts = (ko.unwrap(params.accounts) || []).map(mapAccount);

        self.accounts = ko.observableArray(self.allAccounts.filter(function (account) {
            return account.selected();
        }));

        self.filteredAccounts = ko.observableArray([]);
        self.accountDataSource = ko.observable();

        self.noAccountsForActiveType = ko.pureComputed(function () {
            return self.filteredAccounts().length === 0;
        });

        self.instructions = ko.pureComputed(function () {
            return self.isEditable()
                ? self.nls.notes.UAC07_CREATE_EDIT : self.nls.notes.UAC05_REVIEW;
        });

        self.activateTab = function () {
            const accountType = self.menuSelection() === "TRD" ? "TD" : "CSA",
                activeAccounts = self.accounts().filter(function (account) {
                    return String(read(account.accountType) || "").toUpperCase() === accountType;
                }),
                accountTree = activeAccounts.map(function (account, index) {
                    return {
                        id: `hthApiAccount_${accountType}_${index}`,
                        attr: account,
                        children: [{
                            id: `hthApiServices_${accountType}_${index}`,
                            attr: {
                                accountNumber: account.accountNumber,
                                apiServices: account.apiServices
                            }
                        }]
                    };
                }),
                treeOptions = {
                    expanded: "all",
                    columns: [
                        "accountNumber",
                        "currency",
                        "displayName"
                    ]
                };

            // Match the BCO tab lifecycle: replace the rendered collection after selection.
            // This avoids stale rows on older Knockout/JET builds when only a computed filter changes.
            self.activeAccountType(accountType);
            self.filteredAccounts(activeAccounts);

            self.accountDataSource(new oj.FlattenedTreeTableDataSource(
                new oj.FlattenedTreeDataSource(new oj.JsonTreeDataSource(accountTree), treeOptions)
            ));
        };

        const menuSelectionSubscription = self.menuSelection.subscribe(self.activateTab);

        self.dispose = function () {
            menuSelectionSubscription.dispose();
        };

        self.activateTab();

        rootParams.dashboard.headerName(self.nls.headers.hthApiMapping);

        self.applyFirstToAll = function () {
            const accounts = self.filteredAccounts();

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

        self.edit = function () {
            if (self.action() === "VIEW") {
                self.action("EDIT");
            }

            self.editing(true);
        };

        self.save = function () {
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
                accounts: self.allAccounts,
                originalAccounts: self.originalAccounts,
                action: self.action()
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
                preloadedAccounts: self.allAccounts,
                originalAccounts: self.originalAccounts,
                action: self.action()
            });
        };
    };
});
