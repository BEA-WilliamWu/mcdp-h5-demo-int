define([], function () {
    "use strict";

    /** Reads the channel classification supplied by the existing login profile. */
    return {
        isHthUser: function (profile) {
            return Boolean(profile && (profile.dictionaryArray || []).some(function (dictionary) {
                return (dictionary.nameValuePairDTOArray || []).some(function (entry) {
                    return entry && entry.name === "userChannelType" &&
                        String(entry.value || "").trim().toUpperCase() === "HTH";
                });
            }));
        }
    };
});
