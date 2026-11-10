define([
    "module",
    "text!./hth-api-service-mapping.html",
    "./hth-api-service-mapping",
    "text!./hth-api-service-mapping.css",
    "base-models/css"
], function (module, template, viewModel, componentCSS, CSS) {
    "use strict";

    // Registers the per-account API mapping step with component-scoped CSS transformation.
    return {
        viewModel: viewModel,
        template: CSS.transformTemplate(template, componentCSS, CSS.getComponentName(module))
    };
});
