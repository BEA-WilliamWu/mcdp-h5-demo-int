define(["baseService"], function(BaseService) {
    "use strict";

    const DashboardModel = function() {
        const baseService = BaseService.getInstance();

        return {
            fetchPartyDetails: function() {
                return baseService.fetch({
                    url: "me/party",
                    showMessage: false
                });
            },
            getTaxonomyDefinition: function(dto, serviceName) {
                return baseService.fetch({
                    url: "taxonomy?dtoName={dtoName}&serviceName={serviceName}"
                }, {
                    dtoName: dto,
                    serviceName: serviceName
                });
            },
            getDay0Config: function(prop_id) {
                return baseService.fetch({
                    url: "configurations/base/dayoneconfig/properties/{prop_id}"
                }, {
                    prop_id: prop_id
                });
            },
            getDay0preference: function(prop_id) {
                return baseService.fetch({
                    url: "customconfigurations/listwithsearchtext?summaryText={prop_id}",
                    version: "cz/v1"
                }, {
                    prop_id: prop_id
                });
            },
            updatePasswordExpiry: function(userType, flag) {
                return baseService.update({
                    url: "userExtensionData/expiryDate/{userType}?flag={flag}",
                    version: "cz/v1"
                }, {
                    userType: userType,
                    flag: flag
                });
            },
            updateBounceBackFlag: function(flag) {
                return baseService.update({
                    url: "userExtensionData/bounceBackReminder?flag={flag}",
                    version: "cz/v1",
                    showMessage: false
                }, {
                    flag: flag
                });
            },
            getHthApiPasswordStatus: function() {
                return baseService.fetch({
                    url: "hostToHostApiPassword/status",
                    version: "cz/v1",
                    showMessage: false
                });
            }
        };
    };

    return new DashboardModel();
});
