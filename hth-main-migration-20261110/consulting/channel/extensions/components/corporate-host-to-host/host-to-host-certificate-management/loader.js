define([
    "module",
    "text!./host-to-host-certificate-management.html",
    "./host-to-host-certificate-management",
    "text!./host-to-host-certificate-management.css",
    "base-models/css"
], function(module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
