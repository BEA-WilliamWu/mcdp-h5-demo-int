define([
    "jquery",
    "baseService",
    "baseModel"
], function($, BaseService, BaseModel) {
    "use strict";

    const HostToHostManagementModel = function() {
        const baseService = BaseService.getInstance(),
            baseModel = BaseModel.getInstance(),
            normalize = function(value) {
                if (value === null || value === undefined) {
                    return "";
                }

                return String(value).trim();
            },
            isFailureResponse = function(data) {
                const result = data && data.status && data.status.result
                        ? String(data.status.result).toUpperCase()
                        : "",
                    messageType = data && data.status && data.status.message && data.status.message.type
                        ? String(data.status.message.type).toUpperCase()
                        : "";

                return result === "FAILED" || result === "FAILURE" || messageType === "ERROR";
            },
            search = function(partyId, deferred) {
                const options = {
                    url: baseModel.QueryParams.add("hostToHostManagement/search", {
                        partyId: partyId
                    }),
                    version: "cz/v1",
                    success: function(data) {
                        if (isFailureResponse(data)) {
                            deferred.reject(data);

                            return;
                        }

                        deferred.resolve(data);
                    },
                    error: function(error) {
                        deferred.reject(error);
                    }
                };

                baseService.fetch(options);
            },
            validateUamClientId = function(partyId, correlateUamClientId, deferred) {
                const options = {
                    url: baseModel.QueryParams.add("hostToHostManagement/validateUamClientId", {
                        partyId: partyId,
                        correlateUamClientId: normalize(correlateUamClientId)
                    }),
                    version: "cz/v1",
                    success: function(data) {
                        if (isFailureResponse(data) || Boolean(data && data.validUamClientId === false)) {
                            deferred.reject(data);

                            return;
                        }

                        deferred.resolve(data);
                    },
                    error: function(error) {
                        deferred.reject(error);
                    }
                };

                baseService.fetch(options);
            },
            getEligibleChargeAccounts = function(partyId, accountNumber, deferred) {
                const queryParams = { partyId: partyId };

                if (accountNumber) {
                    queryParams.accountNumber = accountNumber;
                }

                const options = {
                    url: baseModel.QueryParams.add("hostToHostManagement/eligibleChargeAccounts", queryParams),
                    version: "cz/v1",
                    success: function(data) {
                        if (isFailureResponse(data)) {
                            deferred.reject(data);

                            return;
                        }

                        deferred.resolve(data);
                    },
                    error: function(error) {
                        deferred.reject(error);
                    }
                };

                baseService.fetch(options);
            },
            save = function(payload, deferred, action) {
                const requestBody = {
                        partyId: payload.partyId,
                        accountNumber: payload.accountNumber,
                        accountName: payload.accountName,
                        cinNumber: payload.cinNumber,
                        hostToHostApiChannelEnabled: payload.hostToHostApiChannelEnabled,
                        correlateUamClientId: payload.correlateUamClientId,
                        apiApprovalFlowType: payload.apiApprovalFlowType,
                        chargeAccount: payload.chargeAccount,
                        apiAuthorizations: payload.apiAuthorizations
                    },
                    options = {
                        url: action === "disable"
                            ? "hostToHostManagement/disable"
                            : action === "edit" ? "hostToHostManagement/edit" : "hostToHostManagement/submit",
                        version: "cz/v1",
                        data: JSON.stringify(requestBody),
                        success: function(data, status, jqXhr) {
                            deferred.resolve(data, status, jqXhr);
                        },
                        error: function(error) {
                            deferred.reject(error);
                        }
                    };

                baseService.add(options);
            };

        return {
            search: function(partyId) {
                const deferred = $.Deferred();

                search(partyId, deferred);

                return deferred;
            },
            validateUamClientId: function(partyId, correlateUamClientId) {
                const deferred = $.Deferred();

                validateUamClientId(partyId, correlateUamClientId, deferred);

                return deferred;
            },
            getEligibleChargeAccounts: function(partyId, accountNumber) {
                const deferred = $.Deferred();

                getEligibleChargeAccounts(partyId, accountNumber, deferred);

                return deferred;
            },
            submit: function(payload) {
                const deferred = $.Deferred();

                save(payload, deferred, "submit");

                return deferred;
            },
            edit: function(payload) {
                const deferred = $.Deferred();

                save(payload, deferred, "edit");

                return deferred;
            },
            disable: function(payload) {
                const deferred = $.Deferred();

                save(payload, deferred, "disable");

                return deferred;
            }
        };
    };

    return new HostToHostManagementModel();
});
