define([
    "jquery",
    "baseService",
    "baseModel"
], function($, BaseService, BaseModel) {
    "use strict";

    const HostToHostCertificateManagementModel = function() {
        const baseService = BaseService.getInstance(),
            baseModel = BaseModel.getInstance();

        return {
            search: function() {
                const deferred = $.Deferred();

                baseService.fetch({
                    url: "hostToHostCertificate",
                    version: "cz/v1",
                    success: function(data) {
                        deferred.resolve(data);
                    },
                    error: function(error) {
                        deferred.reject(error);
                    }
                });

                return deferred;
            },

            submit: function(payload) {
                const deferred = $.Deferred();

                baseService.add({
                    url: "hostToHostCertificate/submit",
                    version: "cz/v1",
                    data: JSON.stringify(payload),
                    success: function(data) {
                        deferred.resolve(data);
                    },
                    error: function(error) {
                        deferred.reject(error);
                    }
                });

                return deferred;
            },

            download: function(certificateId) {
                return baseService.downloadFile({
                    url: baseModel.QueryParams.add("hostToHostCertificate/download", {
                        certificateId: certificateId
                    }),
                    version: "cz/v1"
                });
            }
        };
    };

    return new HostToHostCertificateManagementModel();
});
