define([
    "module",
    "text!./api-password.html",
    "./api-password",
    "text!./api-password.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
