define([
    "knockout",
    "../host-to-host-search/model",
    "ojL10n!extensions/resources/nls/host-to-host-management",
    "ojs/ojbutton",
    "ojs/ojswitch",
    "framework/elements/api/page-section/loader"
], function(ko, HostToHostManagementModel, resourceBundle) {
    "use strict";

    return function(rootParams) {
        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || {},
            data = params.data || {},
            transactionSnapshot = rootModel.transactionDetails
                && rootModel.transactionDetails()
                && rootModel.transactionDetails().transactionSnapshot
                ? rootModel.transactionDetails().transactionSnapshot
                : null,
            recordSource = data.record || data.hostToHostManagement || data.hostToHostManagementDTO
                || transactionSnapshot || data,
            record = Object.assign({}, recordSource, {
                apiAuthorizations: (recordSource.apiAuthorizations || []).filter(function(item) {
                    return !!item && !!item.apiCode
                        && String(item.apiCode).trim().toUpperCase() !== "AUDIT_LOG";
                })
            }),
            getFailureMessage = function(error) {
                const response = error && error.responseJSON ? error.responseJSON : error,
                    message = response && response.message,
                    statusMessage = response && response.status && response.status.message,
                    validationError = message && message.validationError && message.validationError.length
                        ? message.validationError[0]
                        : null,
                    details = response && response.details && response.details.length
                        ? response.details[0]
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

                if (details && details.message) {
                    return details.message;
                }

                if (response && response.errorMessage) {
                    return response.errorMessage;
                }

                if (response && response.errorCode) {
                    return response.errorCode;
                }

                return resourceBundle.messages.submitFailed;
            },
            showSubmitFailure = function(error) {
                rootParams.baseModel.showMessages(null, [getFailureMessage(error)], "ERROR");
            },
            resolveAction = function(taskCode) {
                if (taskCode === "PP_N_HTH_DIS") {
                    return "disable";
                }

                if (taskCode === "PP_N_HTH_EDT") {
                    return "edit";
                }

                return "submit";
            },
            action = data.action || resolveAction(params.taskCode),
            isEditAction = action === "edit",
            isDisableAction = action === "disable",
            headerName = isDisableAction
                ? resourceBundle.componentDisableHeader
                : isEditAction ? resourceBundle.componentEditHeader : resourceBundle.componentEnableHeader;

        self.nls = resourceBundle;
        self.approvalMode = ko.observable(params.mode === "approval");
        self.isConfirming = ko.observable(false);
        self.disableMode = ko.observable(isDisableAction);
        self.detailAccountNumber = ko.observable(record.accountNumber || "");
        self.detailAccountName = ko.observable(record.accountName || "");
        self.detailCinNumber = ko.observable(record.cinNumber || "");
        self.correlateUamClientId = ko.observable(record.correlateUamClientId || "");
        self.hostToHostApiChannelEnabled = ko.observable(!!record.hostToHostApiChannelEnabled);
        self.apiApprovalFlowType = ko.observable(record.apiApprovalFlowType || "STRAIGHT_THROUGH");
        self.chargeAccountDisplay = ko.observable(record.chargeAccountDisplay || record.chargeAccount || "");

        self.selectedApis = ko.observableArray((record.apiAuthorizations || []).filter(function(item) {
            return !!item.selected;
        }));

        self.reviewTransactionMessage = {
            header: self.nls.PageHeading.Review,
            reviewHeader: isDisableAction
                ? self.nls.messages.disableReviewHeader
                : isEditAction ? self.nls.messages.editReviewHeader : self.nls.messages.reviewHeader
        };

        self.hostToHostApiChannelStatusLabel = ko.pureComputed(function() {
            return self.hostToHostApiChannelEnabled()
                ? self.nls.PageHeading.Enable
                : self.nls.PageHeading.Disable;
        });

        self.apiApprovalFlowTypeLabel = ko.pureComputed(function() {
            return self.apiApprovalFlowType() === "MAKER_CHECKER"
                ? self.nls.PageHeading.WithMakerChecker
                : self.nls.PageHeading.StraightThrough;
        });

        self.approvalFlowDescription = ko.pureComputed(function() {
            return self.nls.descriptions.straightThrough;
        });

        if (!self.approvalMode()) {
            rootParams.baseModel.registerElement("confirm-screen");
        }

        rootParams.dashboard.headerName(headerName);

        self.onBack = function() {
            rootParams.dashboard.loadComponent("host-to-host-details", {
                data: data
            });
        };

        self.onCancel = function() {
            rootParams.dashboard.switchModule(true);
        };

        self.onConfirm = function() {
            if (self.approvalMode() || self.isConfirming()) {
                return;
            }

            self.isConfirming(true);

            const request = isDisableAction
                ? HostToHostManagementModel.disable(record)
                : isEditAction ? HostToHostManagementModel.edit(record) : HostToHostManagementModel.submit(record);

            request.done(function(response, _status, jqXhr) {
                const confirmParams = {
                    jqXHR: jqXhr,
                    transactionResponse: response,
                    hostReferenceNumber: response.status && response.status.externalReferenceNumber,
                    transactionName: headerName
                };

                rootParams.dashboard.loadComponent("confirm-screen", confirmParams, self);
            }).fail(function(error) {
                showSubmitFailure(error);
            }).always(function() {
                self.isConfirming(false);
            });
        };
    };
});
