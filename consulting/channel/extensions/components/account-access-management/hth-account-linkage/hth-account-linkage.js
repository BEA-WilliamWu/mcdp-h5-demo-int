define([
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/access-management",
    "extensions/generic/service-extension",
    "ojs/ojbutton",
    "ojs/ojcheckboxset",
    "ojs/ojdialog",
    "framework/elements/api/page-section/loader"
], function (ko, HthUserAccessModel, resourceBundle, serviceExtension) {
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
            asArray = function (value) {
                value = read(value);

                return Array.isArray(value) ? value : [];
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
            accountNumberValue = function (account) {
                const value = read(account && account.accountNumber);

                return value && typeof value === "object"
                    ? read(value.value) || read(value.displayValue) || "" : value || "";
            },
            accountNumberDisplay = function (account, canonicalValue) {
                const value = read(account && account.accountNumber),
                    suppliedDisplay = read(account && account.accountNumberDisplay)
                        || (value && typeof value === "object" ? read(value.displayValue) : ""),
                    displaySource = suppliedDisplay || canonicalValue,
                    converted = displaySource && serviceExtension.int2extAccNo(
                        String(displaySource), "Y");

                return converted || suppliedDisplay || canonicalValue || "-";
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
        self.activeAccountType = ko.observable("CSA");
        self.originalAccounts = [];

        self.isEditable = ko.pureComputed(function () {
            return self.mode() !== "VIEW" && !self.pendingRequest();
        });

        self.selectedCount = ko.pureComputed(function () {
            return self.accounts().filter(function (account) {
                return account.selected();
            }).length;
        });

        // Bind JET to a named observable. Complex inline expressions are evaluated only once by
        // some OBDX/JET versions and previously left Next disabled after a row was selected.
        self.nextDisabled = ko.pureComputed(function () {
            return self.selectedCount() === 0;
        });

        self.filteredAccounts = ko.pureComputed(function () {
            return self.accounts().filter(function (account) {
                return String(read(account.accountType) || "").toUpperCase()
                    === self.activeAccountType();
            }).sort(function (left, right) {
                return String(read(left.accountNumber) || "")
                    .localeCompare(String(read(right.accountNumber) || ""));
            });
        });

        self.isActiveAccount = function (account) {
            return String(read(account && account.accountType) || "").toUpperCase()
                === self.activeAccountType();
        };

        self.activeAllSelection = ko.pureComputed(function () {
            const activeAccounts = self.filteredAccounts();

            return activeAccounts.length > 0 && activeAccounts.every(function (account) {
                return account.selected();
            }) ? ["ALL"] : [];
        });

        self.instructions = ko.pureComputed(function () {
            return self.mode() === "VIEW"
                ? self.nls.notes.UAC04_REVIEW : self.nls.notes.UAC06_CREATE_EDIT;
        });

        self.noAccountsForActiveType = ko.pureComputed(function () {
            return !self.loading() && !self.errorMessage() && self.filteredAccounts().length === 0;
        });

        rootParams.dashboard.headerName(self.nls.pageTitle.hthUserAccess);

        const mapApi = function (api) {
                api = api || {};

                return Object.assign({}, api, {
                    selected: ko.observable(!!read(api.selected))
                });
            },
            mapAccount = function (account) {
                account = account || {};

                const canonicalNumber = String(accountNumberValue(account)),
                    accountType = normalizeAccountType(account.accountType),
                    currency = read(account.currency) || read(account.currencyCode) || "",
                    displayName = read(account.displayName) || "";

                return Object.assign({}, account, {
                    accountNumber: canonicalNumber,
                    accountNumberDisplay: accountNumberDisplay(account, canonicalNumber),
                    maskedAccountNumber: read(account.maskedAccountNumber) || "",
                    accountType: accountType,
                    currency: currency,
                    displayName: displayName,
                    displayCurrency: currency || "-",
                    displayAccountType: displayName || (accountType === "TD"
                        ? self.nls.navLabels.TD : accountType === "CSA"
                            ? self.nls.navLabels.CASA : accountType || "-"),
                    selected: ko.observable(!!read(account.selected)),
                    apiServices: asArray(account.apiServices).map(mapApi)
                });
            },
            showError = function (message) {
                self.errorMessage(message || self.nls.info.hthMaintenanceLoadError);
            };

        self.toggleAll = function (event) {
            if (!self.isEditable()) {
                return;
            }

            const selectedValues = event && event.detail
                    && Array.isArray(event.detail.value) ? event.detail.value : [],
                selected = selectedValues.indexOf("ALL") !== -1;

            self.filteredAccounts().forEach(function (account) {
                account.selected(selected);
            });
        };

        self.showCurrentAndSavings = function () {
            self.activeAccountType("CSA");
        };

        self.showTimeDeposits = function () {
            self.activeAccountType("TD");
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

        const selectionState = function (accounts) {
                // Compare only business selections. Display labels and ordering metadata can be
                // refreshed by the server without representing an unsaved maintenance change.
                return (ko.toJS(accounts) || []).map(function (account) {
                    return {
                        accountNumber: account.accountNumber,
                        accountType: account.accountType,
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
                    accessAccounts = asArray(access.accounts),
                    eligibleAccounts = asArray(data.eligibleAccounts),
                    accounts = accessAccounts.length ? accessAccounts : eligibleAccounts;

                self.access(access);
                self.accounts(accounts.map(mapAccount));
                self.originalAccounts = ko.toJS(originalAccounts || accounts);
                self.pendingRequest(!!data.pendingRequest);

                self.mode(requestedMode || (context.setupStatus === "ACTIVE"
                    ? "VIEW" : "CREATE"));
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

        self.confirmDiscard = function () {
            document.querySelector("#hthDiscardChangesDialog").close();
            rootParams.dashboard.loadComponent("summary", self.summaryParams);
        };

        self.dismissDiscard = function () {
            document.querySelector("#hthDiscardChangesDialog").close();
        };

        self.back = function () {
            if (hasUnsavedChanges()) {
                document.querySelector("#hthDiscardChangesDialog").open();

                return;
            }

            rootParams.dashboard.loadComponent("summary", self.summaryParams);
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
            try {
                initialize(data);
            } catch (error) {
                // A malformed optional collection must not leave the page in an endless loading
                // state. Keep the technical error out of the UI and show the standard load error.
                showError();
            } finally {
                self.loading(false);
            }
        }).fail(function () {
            showError();
            self.loading(false);
        });
    };
});
