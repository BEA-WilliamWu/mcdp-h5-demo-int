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
                status = Object.assign({}, response.status || {}),
                access = response.access || {},
                referenceNumber = response.referenceNumber || status.referenceNumber
                    || access.referenceNumber || status.externalReferenceNumber;

            status.result = status.result || response.result || "SUCCESSFUL";

            status.message = status.message || response.message || {
                code: APPROVAL_REQUIRED_CODE,
                type: "INFO"
            };

            if (status.receiptAvailable === undefined) {
                status.receiptAvailable = false;
            }

            // confirm-screen uses the platform reference as transactionId when the checker
            // selects its quick-Approve action.  Older HTH responses expose that reference on
            // the nested access DTO or as externalReferenceNumber, while BCO exposes the same
            // value as status.referenceNumber.  Publish the canonical BCO shape to the UI.
            if (referenceNumber) {
                response.referenceNumber = referenceNumber;
                status.referenceNumber = referenceNumber;
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
                            const normalizedResponse = normalizeApprovalRequiredResponse(error);

                            // Keep the jQuery Deferred callback contract intact: the third argument
                            // is the transport jqXHR, while the first argument is the normalized
                            // approval response consumed by the confirmation screen.
                            deferred.resolve(normalizedResponse, "success", error);

                            return;
                        }

                        deferred.reject(error);
                    }
                }),
                transportPromise = requestOptions.data
                    ? baseService.add(requestOptions) : baseService.fetch(requestOptions);

            // BaseService exposes a native Promise and also invokes the callback handlers above.
            // This wrapper deliberately returns its jQuery Deferred, so consume the native rejection
            // after the callback has routed it; otherwise an expected approval-required HTTP 400 is
            // reported as an unhandled Promise even though the maker request was staged correctly.
            if (transportPromise && typeof transportPromise.catch === "function") {
                transportPromise.catch(function () {
                    return null;
                });
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
