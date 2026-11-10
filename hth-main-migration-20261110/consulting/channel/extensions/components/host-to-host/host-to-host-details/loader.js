define([
    "module",
    "text!./host-to-host-details.html",
    "./host-to-host-details",
    "text!./host-to-host-details.css",
    "base-models/css"
], function(module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
