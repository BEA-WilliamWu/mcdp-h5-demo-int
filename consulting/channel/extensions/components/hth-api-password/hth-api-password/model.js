define([
    "baseService"
], function (BaseService) {
    "use strict";

    const baseService = BaseService.getInstance();

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
                data: payload
            }, config || {}));
        },
        reset: function (payload, config) {
            return baseService.update(Object.assign({
                url: "hostToHostApiPassword/reset",
                version: "cz/v1",
                data: payload
            }, config || {}));
        }
    };
});
