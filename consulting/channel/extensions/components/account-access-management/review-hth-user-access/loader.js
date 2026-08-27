define([
    "module",
    "text!./review-hth-user-access.html",
    "./review-hth-user-access",
    "text!./review-hth-user-access.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    // Registers the maker review/checker detail step with component-scoped CSS transformation.
    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
