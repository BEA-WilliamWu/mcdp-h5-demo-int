define([
    "module",
    "text!./hth-account-linkage.html",
    "./hth-account-linkage",
    "text!./hth-account-linkage.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    // Registers the account-linkage step with component-scoped CSS transformation.
    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
