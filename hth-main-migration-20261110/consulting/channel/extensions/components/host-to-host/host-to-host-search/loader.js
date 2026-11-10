define([
    "text!./host-to-host-search.html",
    "./host-to-host-search"
], function(template, viewModel) {
    "use strict";

    return {
        viewModel: viewModel,
        template: template
    };
});
