define([
    "module",
    "text!./hth-api-password.html",
    "./hth-api-password",
    "text!./hth-api-password.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
