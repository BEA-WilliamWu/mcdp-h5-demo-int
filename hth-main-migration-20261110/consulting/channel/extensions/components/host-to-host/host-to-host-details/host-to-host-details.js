define([
    "knockout",
    "../host-to-host-search/model",
    "ojL10n!extensions/resources/nls/host-to-host-management",
    "ojs/ojbutton",
    "ojs/ojswitch",
    "ojs/ojinputtext",
    "ojs/ojcheckboxset",
    "ojs/ojradioset",
    "ojs/ojselectcombobox",
    "ojs/ojknockout-validation",
    "ojs/ojvalidationgroup",
    "framework/elements/api/page-section/loader"
], function(ko, HostToHostManagementModel, resourceBundle) {
    "use strict";

    return function(rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            rootModelParams = rootModel.params || {},
            data = rootModelParams.data || {},
            searchCriteria = data.searchCriteria || {
                accountNumberPrefix: data.accountNumberPrefix || "015",
                accountNumber: data.accountNumber || "",
                accountName: data.accountName || "",
                cinNumber: data.cinNumber || ""
            },
            record = data.record || {},
            flowState = data.flowState || {},
            normalizeApprovalFlowType = function() {
                return "STRAIGHT_THROUGH";
            },
            buildDetailAccountNumber = function() {
                if (record.accountNumber) {
                    return record.accountNumber;
                }

                if (searchCriteria.accountNumber) {
                    return self.accountNumberPrefix + searchCriteria.accountNumber;
                }

                return "";
            },
            isMaintainableApi = function(item) {
                return !!item && !!item.apiCode
                    && String(item.apiCode).trim().toUpperCase() !== "AUDIT_LOG";
            },
            initialApiAuthorizations = (flowState.apiAuthorizations && flowState.apiAuthorizations.length
                ? flowState.apiAuthorizations
                : record.apiAuthorizations || []).filter(isMaintainableApi),
            initialSelectedApiCodes = (flowState.selectedApiCodes && flowState.selectedApiCodes.length
                ? flowState.selectedApiCodes
                : initialApiAuthorizations.filter(function(item) {
                    return !!item.selected;
                }).map(function(item) {
                    return item.apiCode;
                })).filter(function(code) {
                    return initialApiAuthorizations.some(function(item) {
                        return item.apiCode === code;
                    });
                }),
            initialCorrelateUamClientId = flowState.correlateUamClientId !== undefined
                ? flowState.correlateUamClientId
                : record.correlateUamClientId || "",
            initialChargeAccount = flowState.chargeAccount !== undefined
                ? flowState.chargeAccount
                : record.chargeAccount || "";

        self.nls = resourceBundle;
        self.accountNumberPrefix = searchCriteria.accountNumberPrefix || "015";
        self.accountNumber = searchCriteria.accountNumber || "";
        self.accountName = searchCriteria.accountName || "";
        self.cinNumber = searchCriteria.cinNumber || "";
        self.detailAccountNumber = ko.observable(buildDetailAccountNumber());
        self.detailAccountName = ko.observable(record.accountName || "");
        self.detailCinNumber = ko.observable(record.cinNumber || "");
        self.partyId = ko.observable(record.partyId || "");
        self.existingHostToHostApiChannelEnabled = !!record.hostToHostApiChannelEnabled;
        self.editMode = ko.observable(flowState.editMode !== undefined ? !!flowState.editMode : false);

        self.showAuthorizationSettings = ko.observable(
            flowState.showAuthorizationSettings !== undefined
                ? !!flowState.showAuthorizationSettings
                : !!record.hostToHostApiChannelEnabled
        );

        self.hostToHostApiChannelEnabled = ko.observable(
            flowState.hostToHostApiChannelEnabled !== undefined
                ? !!flowState.hostToHostApiChannelEnabled
                : !!record.hostToHostApiChannelEnabled
        );

        self.correlateUamClientId = ko.observable(
            initialCorrelateUamClientId
        );

        self.apiApprovalFlowType = ko.observable(
            normalizeApprovalFlowType(flowState.apiApprovalFlowType || record.apiApprovalFlowType)
        );

        self.availableApis = ko.observableArray(initialApiAuthorizations.slice(0));
        self.selectedApiCodes = ko.observableArray(initialSelectedApiCodes);
        self.isValidatingUamClientId = ko.observable(false);

        self.chargeAccount = ko.observable(initialChargeAccount);
        self.chargeAccountOptions = ko.observableArray([]);
        self.isLoadingChargeAccounts = ko.observable(false);
        self.eligibleChargeAccounts = [];

        const getChargeAccountDisplayName = function() {
            const val = self.chargeAccount(),
                opts = self.chargeAccountOptions(),
                found = opts.find(function(opt) {
                    return opt.value === val;
                });

            return found ? found.label : val;
        },

         getAllApiCodes = function() {
            return self.availableApis().map(function(item) {
                return item.apiCode;
            });
        };

        self.actionType = ko.pureComputed(function() {
            if (self.existingHostToHostApiChannelEnabled && !self.hostToHostApiChannelEnabled()) {
                return "disable";
            }

            if (self.existingHostToHostApiChannelEnabled) {
                return "edit";
            }

            return "submit";
        });

        const updateHeader = function() {
            const actionType = self.actionType ? self.actionType() : null,
                headerName = self.editMode()
                    ? actionType === "disable"
                        ? self.nls.componentDisableHeader
                        : actionType === "edit" ? self.nls.componentEditHeader : self.nls.componentEnableHeader
                    : self.nls.componentHeader;

            rootParams.dashboard.headerName(headerName);
        };

        updateHeader();

        const getFailureMessage = function(error) {
            const response = error && error.responseJSON ? error.responseJSON : error,
                message = response && response.message,
                statusMessage = response && response.status && response.status.message,
                validationError = message && message.validationError && message.validationError.length
                    ? message.validationError[0]
                    : null;

            if (validationError && validationError.errorMessage) {
                return validationError.errorMessage;
            }

            if (validationError && validationError.message) {
                return validationError.message;
            }

            if (typeof message === "string") {
                return message;
            }

            if (message && message.detail) {
                return message.detail;
            }

            if (message && message.title) {
                return message.title;
            }

            if (typeof statusMessage === "string") {
                return statusMessage;
            }

            if (statusMessage && statusMessage.detail) {
                return statusMessage.detail;
            }

            if (statusMessage && statusMessage.title) {
                return statusMessage.title;
            }

            if (response && response.errorMessage) {
                return response.errorMessage;
            }

            return null;
        };

        self.hostToHostApiChannelStatusLabel = ko.pureComputed(function() {
            return self.hostToHostApiChannelEnabled()
                ? self.nls.PageHeading.Enable
                : self.nls.PageHeading.Disable;
        });

        self.approvalFlowDescription = ko.pureComputed(function() {
            return self.nls.descriptions.straightThrough;
        });

        self.showNextButton = ko.pureComputed(function() {
            return self.editMode()
                && self.hostToHostApiChannelEnabled()
                && !self.showAuthorizationSettings();
        });

        self.showSubmitButton = ko.pureComputed(function() {
            if (!self.editMode()) {
                return false;
            }

            if (self.actionType() === "disable") {
                return true;
            }

            return self.hostToHostApiChannelEnabled() && self.showAuthorizationSettings();
        });

        self.isSubmitDisabled = ko.pureComputed(function() {
            if (self.actionType() === "disable") {
                return false;
            }

            return !self.hostToHostApiChannelEnabled()
                || !self.showAuthorizationSettings()
                || !String(self.correlateUamClientId() || "").trim()
                || !String(self.chargeAccount() || "").trim()
                || !self.availableApis().some(function(item) {
                    return isMaintainableApi(item) && self.selectedApiCodes().indexOf(item.apiCode) > -1;
                });
        });

        self.isEditAction = ko.pureComputed(function() {
            return self.existingHostToHostApiChannelEnabled && self.hostToHostApiChannelEnabled();
        });

        self.showEditButton = ko.pureComputed(function() {
            return !self.editMode();
        });

        self.isApiChannelSwitchDisabled = ko.pureComputed(function() {
            return !self.editMode();
        });

        self.correlateUamClientIdReadOnly = ko.pureComputed(function() {
            return self.isEditAction();
        });

        self.primaryActionLabel = ko.pureComputed(function() {
            if (self.actionType() === "disable") {
                return self.nls.PageHeading.Save;
            }

            return self.isEditAction() ? self.nls.PageHeading.Save : self.nls.PageHeading.Submit;
        });

        self.chargeAccountDisplay = ko.pureComputed(function() {
            return getChargeAccountDisplayName();
        });

        self.allApisSelected = ko.pureComputed({
            read: function() {
                const allApiCodes = getAllApiCodes(),
                    selectedApiCodes = self.selectedApiCodes();

                return allApiCodes.length > 0 && selectedApiCodes.length === allApiCodes.length;
            },
            write: function(value) {
                self.selectedApiCodes(value ? getAllApiCodes() : []);
            }
        });

        self.displayedApiAuthorizations = ko.pureComputed(function() {
            if (self.editMode()) {
                return self.availableApis();
            }

            const selectedApiCodes = self.selectedApiCodes();

            return self.availableApis().filter(function(item) {
                return selectedApiCodes.indexOf(item.apiCode) > -1;
            });
        });

        const showError = function(message) {
            rootParams.baseModel.showMessages(null, [message], "ERROR");
        },

         loadChargeAccounts = function() {
            if (!self.partyId()) {
                return;
            }

            self.isLoadingChargeAccounts(true);

            HostToHostManagementModel.getEligibleChargeAccounts(self.partyId(), self.detailAccountNumber())
                .done(function(data) {
                    self.eligibleChargeAccounts = data || [];

                    const options = self.eligibleChargeAccounts.map(function(acc) {
                        const display = acc.accountName || acc.accountNumber;

                        return {
                            value: acc.accountNumber,
                            label: acc.productDescription
                                ? display + String.fromCharCode(32) + "(" + acc.productDescription + ")"
                                : display
                        };
                    });

                    self.chargeAccountOptions(options);

                    if (options.length === 0) {
                        showError(self.nls.messages.noEligibleAccounts);
                    }
                })
                .fail(function() {
                    showError(self.nls.messages.chargeAccountFetchFailed);
                })
                .always(function() {
                    self.isLoadingChargeAccounts(false);
                });
        },

         buildApiAuthorizations = function() {
            return self.availableApis().filter(isMaintainableApi).map(function(item) {
                return {
                    apiCode: item.apiCode,
                    apiName: item.apiName,
                    selected: self.selectedApiCodes().indexOf(item.apiCode) > -1
                };
            });
        },

         buildFlowData = function() {
            return {
                searchCriteria: {
                    accountNumberPrefix: self.accountNumberPrefix,
                    accountNumber: self.accountNumber,
                    accountName: self.accountName,
                    cinNumber: self.cinNumber
                },
                record: {
                    accountNumber: self.detailAccountNumber(),
                    accountName: self.detailAccountName(),
                    cinNumber: self.detailCinNumber(),
                    partyId: self.partyId(),
                    hostToHostApiChannelEnabled: self.hostToHostApiChannelEnabled(),
                    correlateUamClientId: self.correlateUamClientId(),
                    apiApprovalFlowType: self.apiApprovalFlowType(),
                    chargeAccount: self.chargeAccount(),
                    chargeAccountDisplay: getChargeAccountDisplayName(),
                    apiAuthorizations: buildApiAuthorizations()
                },
                action: self.actionType(),
                flowState: {
                    editMode: self.editMode(),
                    showAuthorizationSettings: self.showAuthorizationSettings(),
                    hostToHostApiChannelEnabled: self.hostToHostApiChannelEnabled(),
                    correlateUamClientId: self.correlateUamClientId(),
                    apiApprovalFlowType: self.apiApprovalFlowType(),
                    chargeAccount: self.chargeAccount(),
                    chargeAccountDisplay: getChargeAccountDisplayName(),
                    apiAuthorizations: buildApiAuthorizations(),
                    selectedApiCodes: buildApiAuthorizations().filter(function(item) {
                        return item.selected;
                    }).map(function(item) {
                        return item.apiCode;
                    })
                }
            };
        },

         applyAuthorizationData = function(response) {
            const apiAuthorizations = (response && response.apiAuthorizations && response.apiAuthorizations.length
                ? response.apiAuthorizations
                : []).filter(isMaintainableApi);

            self.correlateUamClientId(
                response && response.correlateUamClientId !== undefined
                    ? response.correlateUamClientId
                    : self.correlateUamClientId()
            );

            self.apiApprovalFlowType(
                normalizeApprovalFlowType(response && response.apiApprovalFlowType)
            );

            self.availableApis(apiAuthorizations);

            self.selectedApiCodes(apiAuthorizations.filter(function(item) {
                return !!item.selected;
            }).map(function(item) {
                return item.apiCode;
            }));

            self.showAuthorizationSettings(true);
            loadChargeAccounts();
        };

        if (self.showAuthorizationSettings() && self.partyId()) {
            loadChargeAccounts();
        }

        self.onApiChannelChange = function(event) {
            if (!self.editMode()) {
                return;
            }

            const enabled = Boolean(event && event.detail ? event.detail.value : self.hostToHostApiChannelEnabled());

            self.hostToHostApiChannelEnabled(enabled);

            if (enabled) {
                self.editMode(true);

                if (self.existingHostToHostApiChannelEnabled) {
                    self.correlateUamClientId(initialCorrelateUamClientId);
                    self.chargeAccount(initialChargeAccount);

                    self.apiApprovalFlowType(
                        normalizeApprovalFlowType(flowState.apiApprovalFlowType || record.apiApprovalFlowType)
                    );

                    self.availableApis(initialApiAuthorizations.slice(0));
                    self.selectedApiCodes(initialSelectedApiCodes.slice(0));
                    self.showAuthorizationSettings(true);
                    loadChargeAccounts();
                }

                updateHeader();

                return;
            }

            self.editMode(self.existingHostToHostApiChannelEnabled);
            self.showAuthorizationSettings(false);
            self.apiApprovalFlowType("STRAIGHT_THROUGH");
            self.availableApis([]);
            self.selectedApiCodes([]);
            updateHeader();
        };

        self.onNext = function() {
            const tracker = document.getElementById("hthEnableTracker");

            if (!self.hostToHostApiChannelEnabled()) {
                return;
            }

            if (!rootParams.baseModel.showComponentValidationErrors(tracker)) {
                return;
            }

            self.isValidatingUamClientId(true);

            HostToHostManagementModel.validateUamClientId(self.partyId(), self.correlateUamClientId())
                .done(function(response) {
                    applyAuthorizationData(response);
                })
                .fail(function(error) {
                    showError(getFailureMessage(error) || self.nls.messages.invalidUamClientId);
                })
                .always(function() {
                    self.isValidatingUamClientId(false);
                });
        };

        self.onEdit = function() {
            self.editMode(true);

            if (self.existingHostToHostApiChannelEnabled) {
                self.hostToHostApiChannelEnabled(true);
                self.correlateUamClientId(initialCorrelateUamClientId);
                self.chargeAccount(initialChargeAccount);

                self.apiApprovalFlowType(
                    normalizeApprovalFlowType(flowState.apiApprovalFlowType || record.apiApprovalFlowType)
                );

                self.availableApis(initialApiAuthorizations.slice(0));
                self.selectedApiCodes(initialSelectedApiCodes.slice(0));
                self.showAuthorizationSettings(true);
                loadChargeAccounts();
            }

            updateHeader();
        };

        self.onSubmit = function() {
            if (self.isSubmitDisabled()) {
                showError(self.nls.messages.apiAuthorizationRequired);

                return;
            }

            rootParams.dashboard.loadComponent("host-to-host-review", {
                data: buildFlowData()
            });
        };

        self.onCancel = function() {
            rootParams.dashboard.switchModule(true);
        };

        self.onBack = function() {
            rootParams.dashboard.loadComponent("host-to-host-management");
        };
    };
});
