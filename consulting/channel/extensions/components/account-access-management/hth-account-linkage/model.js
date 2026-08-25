define([
    "jquery",
    "baseService",
    "baseModel"
], function ($, BaseService, BaseModel) {
    "use strict";

    /*
     * HTH access transport wrapper. OBDX can return a successful HTTP status with a failed business
     * status, so both transport and response status are normalized into the same Deferred failure
     * path consumed by the view models.
     */
    const baseService = BaseService.getInstance(),
        baseModel = BaseModel.getInstance(),
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
