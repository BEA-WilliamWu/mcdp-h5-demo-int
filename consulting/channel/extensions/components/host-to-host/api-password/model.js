define([
    "baseService"
], function (BaseService) {
    "use strict";

    const baseService = BaseService.getInstance();

    /** Uses BaseService transport; the form presents submission errors, including code-only responses. */
    return {
        status: function (config) {
            return baseService.fetch(Object.assign({
                url: "hostToHostApiPassword/status",
                version: "cz/v1",
                showMessage: false
            }, config || {}));
        },
        setup: function (payload, config) {
            return baseService.add(Object.assign({
                url: "hostToHostApiPassword/setup",
                version: "cz/v1",
                showMessage: false,
                data: payload
            }, config || {}));
        },
        reset: function (payload, config) {
            return baseService.update(Object.assign({
                url: "hostToHostApiPassword/reset",
                version: "cz/v1",
                showMessage: false,
                data: payload
            }, config || {}));
        }
    };
});
