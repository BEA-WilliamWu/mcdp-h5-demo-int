define([
    "ojs/ojcore",
    "knockout",
    "../hth-account-linkage/model",
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
], function (oj, ko, HthUserAccessModel, resourceBundle, serviceExtension) {
    "use strict";

    /*
     * Final maker review and read-only checker transaction detail. Maker navigation supplies the
     * current in-memory selection; approval navigation supplies transactionSnapshot from the OBDX
     * workflow. The precedence below also supports framework wrappers used by different releases.
     */
    return function (rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            read = function (value) {
                return ko.isObservable(value) ? value() : value;
            },
            asObject = function (value) {
                value = ko.toJS(read(value));

                return value && typeof value === "object" ? value : {};
            },
            asArray = function (value) {
                value = ko.toJS(read(value));

                return Array.isArray(value) ? value : [];
            },
            params = asObject(rootModel.params || rootModel),
            transactionDetails = asObject(rootModel.transactionDetails),
            transactionSnapshot = asObject(transactionDetails.transactionSnapshot),
            data = asObject(params.data),
            looksLikeAccessRecord = function (source) {
                source = asObject(source);

                return source.partyId !== undefined || source.closeId !== undefined
                    || source.accessPartyId !== undefined || Array.isArray(source.accounts);
            },
            unwrapRecord = function (source) {
                source = asObject(source);

                const nestedRecord = source.record || source.hostToHostUserAccess
                    || source.hostToHostUserAccessDTO || source.access,
                    nestedTransactionDetails = asObject(source.transactionDetails),
                    nestedSnapshot = source.transactionSnapshot
                        || nestedTransactionDetails.transactionSnapshot;

                if (nestedRecord) {
                    return unwrapRecord(nestedRecord);
                }

                if (nestedSnapshot) {
                    return unwrapRecord(nestedSnapshot);
                }

                if (source.data && source.data !== source) {
                    const nestedData = unwrapRecord(source.data);

                    if (looksLikeAccessRecord(nestedData)) {
                        return nestedData;
                    }
                }

                return looksLikeAccessRecord(source) ? source : {};
            },
            // The platform transaction snapshot is the checker source of truth, matching BCO.
            // Maker data is considered only when no snapshot exists.
            recordCandidates = [transactionSnapshot, data, params.access, params],
            recordSource = recordCandidates.map(unwrapRecord).filter(function (candidate) {
                return Object.keys(candidate).length > 0;
            })[0] || {},
            record = asObject(recordSource),
            normalizeApi = function (api) {
                api = asObject(api);

                return Object.assign({}, api, {
                    apiMasterId: read(api.apiMasterId),
                    apiCode: read(api.apiCode),
                    apiName: read(api.apiName),
                    displayOrder: read(api.displayOrder),
                    selected: read(api.selected) !== false
                });
            },
            normalizeAccount = function (account) {
                account = asObject(account);

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
                    convertedDisplay = !suppliedDisplay && canonicalNumber
                        ? serviceExtension.int2extAccNo(String(canonicalNumber), "Y") : "";

                return Object.assign({}, account, {
                    accountNumber: canonicalNumber,
                    accountNumberDisplay: suppliedDisplay || convertedDisplay
                        || canonicalNumber || "-",
                    accountType: accountType,
                    currency: currency,
                    displayName: displayName,
                    displayCurrency: currency || "-",
                    displayAccountType: displayName || (accountType === "TD"
                        ? resourceBundle.navLabels.TD : accountType === "CSA"
                            ? resourceBundle.navLabels.CASA : accountType || "-"),
                    selected: read(account.selected) !== false,
                    apiServices: asArray(account.apiServices).map(normalizeApi)
                });
            },
            taskCode = ko.unwrap(params.taskCode || data.taskCode
                || transactionDetails.taskCode || transactionDetails.taskId
                || rootModel.taskCode) || "";

        self.nls = resourceBundle;
        self.context = ko.unwrap(params.hthLinkageContext) || record;
        self.summaryParams = params.summaryParams || {};

        self.userIdDisplay = String(record.username || record.closeId
            || self.context.username || self.context.closeId || "-").split("@")[0];

        self.approvalMode = ko.observable(params.mode === "approval"
            || Object.keys(transactionSnapshot).length > 0);

        self.action = read(params.action) || record.actionType
            || (taskCode === "UAT_N_HUA_DEL" ? "DELETE"
                : taskCode === "UAT_N_HUA_EDT" ? "EDIT" : "CREATE");

        self.access = record;

        const makerAccounts = asArray(params.accounts),
            snapshotAccounts = asArray(record.accounts);

        // Maker review uses the exact in-memory selection from the API mapping step. Checker
        // review uses the immutable transaction snapshot supplied by the approval framework.
        self.allAccounts = (makerAccounts.length ? makerAccounts : snapshotAccounts)
            .map(normalizeAccount);

        self.originalAccounts = asArray(params.originalAccounts);

        self.accounts = self.allAccounts.filter(function (account) {
            return read(account.selected) !== false;
        });

        self.activeAccountType = ko.observable();
        self.filteredAccounts = ko.observableArray([]);
        self.accountDataSource = ko.observable();

        self.menuSelection = ko.observable(self.accounts.some(function (account) {
            return account.accountType === "CSA";
        }) ? "CASA" : "TRD");

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

        self.selectedApis = function (account) {
            return (account.apiServices || []).filter(function (api) {
                return read(api.selected) !== false;
            });
        };

        const activateAccountType = function (accountType) {
            const activeAccounts = self.accounts.filter(function (account) {
                return account.accountType === accountType;
            }),
                accountTree = activeAccounts.map(function (account, index) {
                    return {
                        id: `hthReviewAccount_${accountType}_${index}`,
                        attr: account,
                        children: [{
                            id: `hthReviewApis_${accountType}_${index}`,
                            attr: {
                                accountNumber: account.accountNumber,
                                apiServices: self.selectedApis(account)
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

            // Replace the rendered collection explicitly. Older OBDX JET builds can refresh the
            // active tab class without re-rendering a containerless foreach backed by a
            // pureComputed, leaving the previous tab's account rows visible.
            self.activeAccountType(accountType);
            self.filteredAccounts(activeAccounts);

            self.accountDataSource(new oj.FlattenedTreeTableDataSource(
                new oj.FlattenedTreeDataSource(new oj.JsonTreeDataSource(accountTree), treeOptions)
            ));
        };

        self.noAccountsForActiveType = ko.pureComputed(function () {
            return self.filteredAccounts().length === 0;
        });

        self.activateTab = function () {
            activateAccountType(self.menuSelection() === "TRD" ? "TD" : "CSA");
        };

        const menuSelectionSubscription = self.menuSelection.subscribe(self.activateTab);

        self.dispose = function () {
            menuSelectionSubscription.dispose();
        };

        self.activateTab();

        self.isSubmitting = ko.observable(false);

        self.isDelete = ko.pureComputed(function () {
            return self.action === "DELETE";
        });

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
                // Keep the selected account/API rows in DELETE snapshots as BCO does. The backend
                // ignores them for deletion, but checker review and audit details remain complete.
                accounts: self.accounts.map(function (account, accountIndex) {
                    return {
                        accountNumber: account.accountNumber,
                        accountNumberDisplay: account.accountNumberDisplay,
                        productCode: account.productCode,
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

        self.transactionName = self.action === "DELETE"
            ? self.nls.headers.deleteMappingTxnName
            : self.action === "EDIT" ? self.nls.headers.editMappingTxnName
                : self.nls.headers.createMappingTxnName;

        rootParams.dashboard.headerName(self.transactionName);

        if (!self.approvalMode()) {
            rootParams.baseModel.registerElement("confirm-screen");
        }

        self.confirm = function () {
            if (self.approvalMode() || self.isSubmitting()) {
                return;
            }

            self.isSubmitting(true);

            HthUserAccessModel.save(self.payload(), self.action).done(function (response) {
                // Approval-required is returned as HTTP 400 by this OBDX release but is normalized
                // by the model to an accepted transaction response. Passing the original jqXHR to
                // confirm-screen would make it parse the error transport as a transactionAction.
                rootParams.dashboard.loadComponent("confirm-screen", {
                    transactionResponse: response,
                    hostReferenceNumber: response.status && response.status.externalReferenceNumber,
                    // BCO passes the action-specific transaction name to confirm-screen. Apart
                    // from displaying the correct Create/Edit/Delete title, the shared component
                    // uses it together with the platform reference number when the checker clicks
                    // the Approve shortcut and reopens the immutable transaction snapshot.
                    transactionName: self.transactionName
                }, self);
            }).fail(function (error) {
                const response = error && error.responseJSON ? error.responseJSON : error,
                    message = response && response.message,
                    statusMessage = response && response.status && response.status.message,
                    responseCode = response && response.errorCode,
                    messageDetail = message && typeof message === "object" && message.detail,
                    messageCode = typeof message === "string" ? message : message && message.code,
                    statusCode = typeof statusMessage === "string"
                        ? statusMessage : statusMessage && statusMessage.code,
                    code = messageDetail || responseCode || messageCode || statusCode;

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
