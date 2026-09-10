define([
    "module",
    "text!./profile.html",
    "./profile",
    "text!./profile.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});