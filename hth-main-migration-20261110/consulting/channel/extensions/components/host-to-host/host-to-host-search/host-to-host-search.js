define([
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/host-to-host-management",
    "framework/elements/api/page-section/loader"
], function(ko, HostToHostManagementModel, resourceBundle) {
    "use strict";

    return function(rootParams) {
        const self = this;

        self.nls = resourceBundle;
        rootParams.dashboard.headerName(self.nls.componentHeader);

        self.isSearching = ko.observable(false);

        self.partySearchModel = {
            partyDetails: {
                party: {
                    value: ko.observable(""),
                    displayValue: ko.observable("")
                },
                userType: ko.observable(""),
                cinNumber: ko.observable(""),
                partyName: ko.observable(""),
                partyDetailsFetched: ko.observable(false),
                additionalDetails: ko.observable(""),
                partyFirstName: ko.observable(""),
                partyLastName: ko.observable("")
            }
        };

        let lastLoadedPartyId = null;

        const normalize = function(value) {
            if (value === null || value === undefined) {
                return "";
            }

            return String(value).trim();
        },

         showError = function(message) {
            rootParams.baseModel.showMessages(null, [message], "ERROR");
        },

         buildSearchContext = function(record, partyId) {
            const partyDisplayValue = normalize(self.partySearchModel.partyDetails.party.displayValue()),
                partyName = normalize(self.partySearchModel.partyDetails.partyName()),
                cinNumber = normalize(self.partySearchModel.partyDetails.cinNumber());

            return {
                searchCriteria: {
                    partyId: partyId,
                    partyDisplayValue: partyDisplayValue,
                    partyName: partyName,
                    cinNumber: cinNumber
                },
                record: Object.assign({}, record, {
                    accountNumber: partyDisplayValue || record.accountNumber,
                    accountName: partyName || record.accountName,
                    cinNumber: cinNumber || record.cinNumber,
                    partyId: partyId || record.partyId
                })
            };
        },

         loadSelectedParty = function() {
            const selectedPartyId = normalize(self.partySearchModel.partyDetails.party.value());

            if (!selectedPartyId || !self.partySearchModel.partyDetails.partyDetailsFetched()) {
                return;
            }

            if (self.isSearching() || selectedPartyId === lastLoadedPartyId) {
                return;
            }

            self.isSearching(true);

            HostToHostManagementModel.search(selectedPartyId).done(function(data) {
                const record = data || {};

                record.partyId = record.partyId || selectedPartyId;

                if (!record.partyId) {
                    showError(self.nls.messages.noRecordsFound);

                    return;
                }

                rootParams.dashboard.loadComponent("host-to-host-details", {
                    data: buildSearchContext(record, selectedPartyId)
                });

                lastLoadedPartyId = selectedPartyId;
            }).fail(function() {
                lastLoadedPartyId = null;
                showError(self.nls.messages.searchFailed);
            }).always(function() {
                self.isSearching(false);
            });
        };

        self.partySearchModel.partyDetails.party.value.subscribe(function() {
            loadSelectedParty();
        });

        self.partySearchModel.partyDetails.partyDetailsFetched.subscribe(function() {
            loadSelectedParty();
        });
    };
});
