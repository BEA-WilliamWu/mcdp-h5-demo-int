define([
    "knockout",
    "../hth-account-linkage/model",
    "ojL10n!extensions/resources/nls/access-management",
    "extensions/generic/service-extension",
    "ojs/ojbutton",
    "framework/elements/api/page-section/loader"
], function (ko, HthUserAccessModel, resourceBundle, serviceExtension) {
    "use strict";

    /*
     * Final maker review and read-only checker transaction detail. Maker navigation supplies the
     * current in-memory selection; approval navigation supplies transactionSnapshot from the OBDX
     * workflow. The precedence below also supports framework wrappers used by different releases.
     */
    return function (rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || rootModel,
            transactionDetails = rootModel.transactionDetails
                && ko.unwrap(rootModel.transactionDetails),
            transactionSnapshot = transactionDetails && transactionDetails.transactionSnapshot,
            data = params.data || {},
            dataRecord = data.record || data.hostToHostUserAccess
                || data.hostToHostUserAccessDTO,
            // Prefer a framework-provided record wrapper, then the raw data wrapper, then the
            // approval snapshot. params.access is a maker-screen fallback only.
            recordSource = dataRecord || (Object.keys(data).length ? data : null)
                || transactionSnapshot || ko.unwrap(params.access) || {},
            record = ko.toJS(ko.unwrap(recordSource)),
            read = function (value) {
                return ko.isObservable(value) ? value() : value;
            },
            normalizeAccount = function (account) {
                account = ko.toJS(account || {});

                const accountNumberObject = account.accountNumber,
                    canonicalNumber = String(accountNumberObject
                        && typeof accountNumberObject === "object"
                        ? accountNumberObject.value || accountNumberObject.displayValue || ""
                        : accountNumberObject || ""),
                    accountTypeValue = String(account.accountType || "").toUpperCase(),
                    accountType = accountTypeValue === "TRD"
                        || accountTypeValue === "TERM_DEPOSIT" ? "TD"
                            : accountTypeValue === "DEMAND_DEPOSIT" ? "CSA" : accountTypeValue,
                    currency = account.currency || account.currencyCode || "",
                    displayName = account.displayName || "",
                    suppliedDisplay = account.accountNumberDisplay
                        || (accountNumberObject && typeof accountNumberObject === "object"
                            ? accountNumberObject.displayValue : ""),
                    convertedDisplay = serviceExtension.int2extAccNo(
                        String(suppliedDisplay || canonicalNumber), "Y");

                return Object.assign({}, account, {
                    accountNumber: canonicalNumber,
                    accountNumberDisplay: convertedDisplay || suppliedDisplay
                        || canonicalNumber || "-",
                    accountType: accountType,
                    currency: currency,
                    displayName: displayName
                });
            },
            taskCode = ko.unwrap(params.taskCode || data.taskCode) || "";

        self.nls = resourceBundle;
        self.context = ko.unwrap(params.hthLinkageContext) || record;
        self.summaryParams = params.summaryParams || {};
        self.approvalMode = ko.observable(params.mode === "approval" || !!transactionSnapshot);

        self.action = read(params.action) || record.actionType
            || (taskCode === "UAT_N_HUA_DEL" ? "DELETE"
                : taskCode === "UAT_N_HUA_EDT" ? "EDIT" : "CREATE");

        self.access = record;

        self.allAccounts = (ko.toJS(ko.unwrap(params.accounts)) || record.accounts || [])
            .map(normalizeAccount);

        self.originalAccounts = ko.toJS(ko.unwrap(params.originalAccounts)) || [];

        self.accounts = self.allAccounts.filter(function (account) {
            return read(account.selected) !== false;
        });

        self.isSubmitting = ko.observable(false);

        self.isDelete = ko.pureComputed(function () {
            return self.action === "DELETE";
        });

        self.selectedApis = function (account) {
            return (account.apiServices || []).filter(function (api) {
                return read(api.selected) !== false;
            });
        };

        self.payload = function () {
            // Descriptive fields are included for review display, but the backend re-resolves
            // account ownership and API metadata and persists its own immutable maker snapshot.
            return {
                partyId: record.partyId || self.context.partyId,
                closeId: record.closeId || self.context.closeId,
                accessPartyId: record.accessPartyId || self.context.accessPartyId,
                linkageType: record.linkageType || self.context.linkageType,
                username: record.username || self.context.username,
                fullName: record.fullName || self.context.fullName,
                accessPartyName: record.accessPartyName || self.context.accessPartyName,
                accounts: self.isDelete() ? [] : self.accounts.map(function (account, accountIndex) {
                    return {
                        accountNumber: account.accountNumber,
                        maskedAccountNumber: account.maskedAccountNumber,
                        displayName: account.displayName,
                        accountType: String(account.accountType || "").toUpperCase(),
                        currency: account.currency,
                        selected: true,
                        displayOrder: account.displayOrder === undefined
                            ? accountIndex : account.displayOrder,
                        apiServices: self.selectedApis(account).map(function (api, apiIndex) {
                            return {
                                apiMasterId: api.apiMasterId,
                                apiCode: api.apiCode,
                                apiName: api.apiName,
                                selected: true,
                                displayOrder: api.displayOrder === undefined
                                    ? apiIndex : api.displayOrder
                            };
                        })
                    };
                })
            };
        };

        rootParams.dashboard.headerName(self.action === "DELETE"
            ? self.nls.headers.deleteMappingTxnName
            : self.action === "EDIT" ? self.nls.headers.editMappingTxnName
                : self.nls.headers.createMappingTxnName);

        if (!self.approvalMode()) {
            rootParams.baseModel.registerElement("confirm-screen");
        }

        self.confirm = function () {
            if (self.approvalMode() || self.isSubmitting()) {
                return;
            }

            self.isSubmitting(true);

            HthUserAccessModel.save(self.payload(), self.action).done(function (response, _status, jqXhr) {
                rootParams.dashboard.loadComponent("confirm-screen", {
                    jqXHR: jqXhr,
                    transactionResponse: response,
                    hostReferenceNumber: response.status && response.status.externalReferenceNumber,
                    transactionName: rootParams.dashboard.headerName()
                }, self);
            }).fail(function (error) {
                const response = error && error.responseJSON ? error.responseJSON : error,
                    code = response && (response.errorCode || response.message);

                rootParams.baseModel.showMessages(null,
                    [typeof code === "string" ? code : self.nls.info.hthSubmitFailed], "ERROR");
            }).always(function () {
                self.isSubmitting(false);
            });
        };

        self.cancel = function () {
            rootParams.dashboard.switchModule(true);
        };

        self.back = function () {
            if (self.isDelete()) {
                rootParams.dashboard.loadComponent("hth-account-linkage", {
                    hthLinkageContext: self.context,
                    summaryParams: self.summaryParams,
                    preloadedAccess: record,
                    preloadedAccounts: self.allAccounts,
                    originalAccounts: self.originalAccounts,
                    action: "VIEW"
                });

                return;
            }

            rootParams.dashboard.loadComponent("hth-api-service-mapping", {
                hthLinkageContext: self.context,
                summaryParams: self.summaryParams,
                access: record,
                accounts: self.allAccounts,
                originalAccounts: self.originalAccounts,
                action: self.action
            });
        };
    };
});
