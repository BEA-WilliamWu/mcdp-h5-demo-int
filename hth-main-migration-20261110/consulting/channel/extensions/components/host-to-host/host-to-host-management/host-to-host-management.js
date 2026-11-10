define([
    "ojL10n!extensions/resources/nls/host-to-host-management"
], function(resourceBundle) {
    "use strict";

    return function(rootParams) {
        rootParams.baseModel.registerComponent("party-validate", "common");
        rootParams.baseModel.registerComponent("host-to-host-search", "host-to-host");
        rootParams.baseModel.registerComponent("host-to-host-details", "host-to-host");
        rootParams.baseModel.registerComponent("host-to-host-review", "host-to-host");
        rootParams.dashboard.headerName(resourceBundle.componentHeader);
    };
});
