define([
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/access-management",
    "ojs/ojbutton",
    "framework/elements/api/page-section/loader"
], function (ko, HthUserAccessModel, resourceBundle) {
    "use strict";

    /*
     * First step of HTH maintenance. The server response is converted to Knockout observables for
     * editing, while originalAccounts remains a plain deep copy used only for discard detection
     * and Back navigation. The exact relationship context is carried unchanged through all steps.
     */
    return function (rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || rootModel,
            read = function (value) {
                return ko.isObservable(value) ? value() : value;
            },
            context = read(params.hthLinkageContext) || {};

        self.nls = resourceBundle;
        self.context = context;
        self.summaryParams = params.summaryParams || {};
        self.loading = ko.observable(true);
        self.errorMessage = ko.observable("");
        self.pendingRequest = ko.observable(false);
        self.access = ko.observable();
        self.accounts = ko.observableArray([]);
        self.mode = ko.observable("CREATE");
        self.linkAllAccounts = ko.observable(false);
        self.originalAccounts = [];

        self.isEditable = ko.pureComputed(function () {
            return self.mode() !== "VIEW" && !self.pendingRequest();
        });

        self.selectedCount = ko.pureComputed(function () {
            return self.accounts().filter(function (account) {
                return account.selected();
            }).length;
        });

        rootParams.dashboard.headerName(self.nls.pageTitle.accessManagement.replace("{user}", "HTH User"));

        const mapApi = function (api) {
                return Object.assign({}, api, {
                    selected: ko.observable(!!read(api.selected))
                });
            },
            mapAccount = function (account) {
                return Object.assign({}, account, {
                    selected: ko.observable(!!read(account.selected)),
                    apiServices: (account.apiServices || []).map(mapApi)
                });
            },
            showError = function (message) {
                self.errorMessage(message || self.nls.info.hthMaintenanceLoadError);
            };

        self.toggleAll = function () {
            if (!self.isEditable()) {
                return;
            }
            const selected = !self.accounts().every(function (account) {
                return account.selected();
            });

            self.linkAllAccounts(selected);
            self.accounts().forEach(function (account) {
                account.selected(selected);
            });
        };

        self.edit = function () {
            self.mode("EDIT");
        };

        self.next = function () {
            if (!self.selectedCount()) {
                rootParams.baseModel.showMessages(null,
                    [self.nls.info.hthSelectAccount], "ERROR");
                return;
            }
            rootParams.dashboard.loadComponent("hth-api-service-mapping", {
                hthLinkageContext: context,
                summaryParams: self.summaryParams,
                access: self.access(),
                accounts: self.accounts(),
                originalAccounts: self.originalAccounts,
                action: self.mode()
            });
        };

        self.deleteAccess = function () {
            rootParams.dashboard.loadComponent("review-hth-user-access", {
                hthLinkageContext: context,
                summaryParams: self.summaryParams,
                access: self.access(),
                accounts: self.accounts(),
                originalAccounts: self.originalAccounts,
                action: "DELETE"
            });
        };

        self.cancel = function () {
            if (self.mode() === "EDIT") {
                initialize({
                    access: Object.assign({}, self.access(), {
                        accounts: ko.toJS(self.originalAccounts)
                    })
                }, "VIEW", self.originalAccounts);
                return;
            }
            rootParams.dashboard.switchModule(true);
        };

        self.back = function () {
            if (hasUnsavedChanges() && typeof window !== "undefined"
                && !window.confirm(self.nls.info.hthDiscardChanges)) {
                return;
            }
            rootParams.dashboard.loadComponent("summary", self.summaryParams);
        };

        const selectionState = function (accounts) {
                // Compare only business selections. Display labels and ordering metadata can be
                // refreshed by the server without representing an unsaved maintenance change.
                return (ko.toJS(accounts) || []).map(function (account) {
                    return {
                        accountNumber: account.accountNumber,
                        selected: !!account.selected,
                        apiCodes: (account.apiServices || []).filter(function (api) {
                            return !!api.selected;
                        }).map(function (api) {
                            return api.apiCode;
                        }).sort()
                    };
                });
            },
            hasUnsavedChanges = function () {
                return JSON.stringify(selectionState(self.accounts()))
                    !== JSON.stringify(selectionState(self.originalAccounts));
            },
            initialize = function (data, requestedMode, originalAccounts) {
                data = data || {};
                const access = data.access || {},
                    accounts = access.accounts || data.eligibleAccounts || [];

                self.access(access);
                self.accounts(accounts.map(mapAccount));
                self.originalAccounts = ko.toJS(originalAccounts || accounts);
                self.pendingRequest(!!data.pendingRequest);
                self.mode(requestedMode || (context.setupStatus === "ACTIVE"
                    ? "VIEW" : "CREATE"));
                self.linkAllAccounts(self.accounts().length > 0
                    && self.accounts().every(function (account) {
                        return account.selected();
                    }));
            };

        if (params.preloadedAccounts) {
            // Back navigation must retain the in-memory selection instead of re-reading effective
            // data and discarding edits made on the API mapping step.
            initialize({
                access: Object.assign({}, params.preloadedAccess || {}, {
                    accounts: ko.unwrap(params.preloadedAccounts)
                })
            }, read(params.action), read(params.originalAccounts));
            self.loading(false);
            return;
        }

        HthUserAccessModel.read(context).done(function (data) {
            initialize(data);
        }).fail(function () {
            showError();
        }).always(function () {
            self.loading(false);
        });
    };
});
