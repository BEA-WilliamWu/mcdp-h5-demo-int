define([
    "jquery",
    "baseService"
], function ($, BaseService) {
    "use strict";

    const SecurityMenuModel = function () {
        const baseService = BaseService.getInstance(),
            getEnbleItokenManagement = function (deferred) {
            const options = {
                url: "customconfigurations/listwithsearchtext?summaryText=ENABLE_ITOKEN_MANAGEMENT",
                version: "cz/v1",
                success: function (data) {
                    deferred.resolve(data);
                },
                error: function (data) {
                    deferred.reject(data);
                }
            };

            baseService.fetch(options);
            };
        let getEnbleItokenManagementDeferred;

        return {
            getEnbleItokenManagement: function () {
                getEnbleItokenManagementDeferred = $.Deferred();
                getEnbleItokenManagement(getEnbleItokenManagementDeferred);

                return getEnbleItokenManagementDeferred;
            }
        };
    };

    return new SecurityMenuModel();
});
