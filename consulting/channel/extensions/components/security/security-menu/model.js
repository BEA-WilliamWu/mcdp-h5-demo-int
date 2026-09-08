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
            },
            getHthApiPasswordStatus = function () {
                return baseService.fetch({
                    url: "hostToHostApiPassword/status",
                    version: "cz/v1",
                    showMessage: false
                });
            };
        let getEnbleItokenManagementDeferred;

        return {
            getEnbleItokenManagement: function () {
                getEnbleItokenManagementDeferred = $.Deferred();
                getEnbleItokenManagement(getEnbleItokenManagementDeferred);

                return getEnbleItokenManagementDeferred;
            },
            getHthApiPasswordStatus: getHthApiPasswordStatus
        };
    };

    return new SecurityMenuModel();
});
