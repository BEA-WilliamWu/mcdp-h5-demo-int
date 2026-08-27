define([
    "jquery",
    "baseService",
    "baseModel"
], function ($, BaseService, BaseModel) {
    "use strict";

    /*
     * HTH access transport wrapper. OBDX can return a successful HTTP status with a failed business
     * status, so both transport and response status are normalized into the same Deferred failure
     * path consumed by the view models. Approval-required is the one exception: older OBDX releases
     * report a successfully staged maker request as HTTP 400 with DIGX_APPROVAL_REQUIRED. That
     * transport response is normalized to the standard HTTP 202 confirmation contract.
     */
    const APPROVAL_REQUIRED_CODE = "DIGX_APPROVAL_REQUIRED",
        APPROVAL_ACCEPTED_STATUS = 202,
        baseService = BaseService.getInstance(),
        baseModel = BaseModel.getInstance(),
        responseBody = function (error) {
            return error && error.responseJSON ? error.responseJSON : error;
        },
        isApprovalRequiredResponse = function (error) {
            const response = responseBody(error) || {},
                status = response.status || {},
                message = response.message || status.message;

            return !!(message && message.code === APPROVAL_REQUIRED_CODE);
        },
        normalizeApprovalRequiredResponse = function (error) {
            const response = Object.assign({}, responseBody(error) || {}),
                status = Object.assign({}, response.status || {});

            status.result = status.result || response.result || "SUCCESSFUL";

            status.message = status.message || response.message || {
                code: APPROVAL_REQUIRED_CODE,
                type: "INFO"
            };

            if (status.receiptAvailable === undefined) {
                status.receiptAvailable = false;
            }

            response.status = status;
            baseModel.injectProps(response, "getResponseStatus", APPROVAL_ACCEPTED_STATUS);

            return response;
        },
        isFailureResponse = function (data) {
            const result = data && data.status && data.status.result
                ? String(data.status.result).toUpperCase() : "";

            return result === "FAILED" || result === "FAILURE";
        },
        request = function (options) {
            const deferred = $.Deferred(),
                requestOptions = Object.assign({}, options, {
                    version: "cz/v1",
                    success: function (data, status, jqXhr) {
                        if (isFailureResponse(data)) {
                            deferred.reject(data);

                            return;
                        }

                        deferred.resolve(data, status, jqXhr);
                    },
                    error: function (error) {
                        if (isApprovalRequiredResponse(error)) {
                            deferred.resolve(normalizeApprovalRequiredResponse(error),
                                "success", error);

                            return;
                        }

                        deferred.reject(error);
                    }
                });

            if (requestOptions.data) {
                baseService.add(requestOptions);
            } else {
                baseService.fetch(requestOptions);
            }

            return deferred;
        };

    return {
        read: function (context) {
            return request({
                url: baseModel.QueryParams.add("hostToHostUserAccess/accounts", {
                    partyId: context.partyId,
                    closeId: context.closeId,
                    accessPartyId: context.accessPartyId,
                    linkageType: context.linkageType
                })
            });
        },
        save: function (payload, action) {
            return request({
                url: "hostToHostUserAccess/" + (action === "EDIT"
                    ? "edit" : action === "DELETE" ? "delete" : "submit"),
                data: JSON.stringify(payload)
            });
        }
    };
});
