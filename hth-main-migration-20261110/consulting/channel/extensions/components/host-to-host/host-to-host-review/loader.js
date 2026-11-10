define([
    "module",
    "text!./host-to-host-review.html",
    "./host-to-host-review",
    "text!./host-to-host-review.css",
    "base-models/css"
], function(module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
