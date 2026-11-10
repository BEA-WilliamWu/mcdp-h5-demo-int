define([
    "text!./host-to-host-management.html",
    "./host-to-host-management"
], function(template, viewModel) {
    "use strict";

    return {
        viewModel: viewModel,
        template: template
    };
});
