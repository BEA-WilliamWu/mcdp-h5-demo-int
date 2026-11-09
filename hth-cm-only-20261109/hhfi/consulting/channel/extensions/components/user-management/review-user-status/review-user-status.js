define([
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/user-search-list",
    "ojs/ojtable",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingcontrol",
    "ojs/ojpagingtabledatasource",
    "ojs/ojswitch",
    "framework/elements/api/page-section/loader"
], function (ko, ReviewUserStatusModel, resourceBundle) {
    "use strict";

    return function viewModel(rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.hthAvailable = rootParams.dashboard.appData.segment === "CORPADMIN";

        const hthTarget = ko.toJS(ko.unwrap(rootParams.rootModel.params.userExtensionData)
            || ko.unwrap(rootParams.rootModel.userExtensionData) || rootParams.rootModel.params.data || {});

        self.hthPageAllowed = ko.observable(self.hthAvailable || hthTarget.userChannelType !== "HTH");

        if (!self.hthPageAllowed()) {
            rootParams.baseModel.registerElement("access-denied", "core");
            rootParams.dashboard.loadComponent("access-denied", {});

            return;
        }

        self.searchedResultResponse = ko.observable();
        self.nls = resourceBundle;
        self.userExtensionData = ko.observable();
        self.mobileflag = ko.observable(false);
        self.dataLoaded = ko.observable(false);

        self.isCorpAdmin = ko.observable(false);
        self.usernamesearched = ko.observable(rootParams.rootModel.params.usernamesearched);
        self.firstNamesearched = ko.observable(rootParams.rootModel.params.firstNamesearched);
        self.lastNamesearched = ko.observable(rootParams.rootModel.params.lastNamesearched);
        self.emailIdsearched = ko.observable(rootParams.rootModel.params.emailIdsearched);
        self.mobileNumbersearched = ko.observable(rootParams.rootModel.params.mobileNumbersearched);
        self.partyIdSearched = ko.observable(rootParams.rootModel.params.partyIdSearched);
        self.userList = ko.observable(rootParams.rootModel.params.searchedUserList);
        self.username = ko.observable(rootParams.rootModel.params.username);
        self.countries = ko.observable(rootParams.rootModel.params.countries);

        if (rootParams.dashboard.appData.segment === "CORPADMIN") {
            self.isCorpAdmin(true);
        } else {
            self.isCorpAdmin(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.mode && rootParams.rootModel.params.mode === "approval") {
            rootParams.dashboard.headerName(rootParams.rootModel.params.taskCode === "UM_N_ULS_L" ? self.nls.header.lockUserStatus : self.nls.header.unlockUserStatus);
        } else {
            rootParams.dashboard.headerName(rootParams.rootModel.params.statusOptionValue && rootParams.rootModel.params.statusOptionValue().toUpperCase() === self.nls.header.lock.toUpperCase() ? self.nls.header.lockUserStatus : self.nls.header.unlockUserStatus);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.data) {
            self.searchedResultResponse(ko.toJS(rootParams.rootModel.params.data));

            if (rootParams.rootModel.params.data.lockStatus === "LOCK") {
                self.reviewstatusOptionValue = ko.observable(true);
            } else {
                self.reviewstatusOptionValue = ko.observable(false);
            }

            self.lockStatusValue = ko.observable(self.nls.header[rootParams.rootModel.params.data.lockStatus.toLowerCase()]);
            self.lockReason = ko.observable(rootParams.rootModel.params.data.lockReason);

            ReviewUserStatusModel.readUserExtension(self.searchedResultResponse().username).done(function (data) {
                if (!self.hthAvailable && data.userExtensionDataDTO.userChannelType === "HTH") {
                    self.hthPageAllowed(false);
                    rootParams.baseModel.registerElement("access-denied", "core");
                    rootParams.dashboard.loadComponent("access-denied", {});

                    return;
                }

                self.userExtensionData(data.userExtensionDataDTO);
                self.mobileflag(true);
                self.dataLoaded(true);
            });

        } else {
            self.searchedResultResponse = rootParams.rootModel.params.selectedUserData;
            self.reviewstatusOptionValue = ko.observable(rootParams.rootModel.params.statusOptionValueNew());
            self.lockStatusValue = ko.observable(rootParams.rootModel.params.statusOptionValue());
            self.lockReason = ko.observable(rootParams.rootModel.params.reason());
            self.userExtensionData(rootParams.rootModel.params.userExtensionData);
            self.mobileflag(true);
            self.dataLoaded(true);
        }

        rootParams.baseModel.registerElement("confirm-screen");

        self.successHandler = function (data) {
            rootParams.dashboard.loadComponent("confirm-screen", {
                transactionResponse: data,
                transactionName: self.nls.header.lockUserStatus
            });
        };

        self.successHandler2 = function (data) {
            rootParams.dashboard.loadComponent("confirm-screen", {
                transactionResponse: data,
                transactionName: self.nls.header.unlockUserStatus
            });
        };

        self.confirm = function () {
            if (!self.hthPageAllowed()) {
                return;
            }

            if (self.lockStatusValue().toUpperCase() === self.nls.header.lock.toUpperCase()) {
                const payload = {
                    lockReason: self.lockReason()
                };

                ReviewUserStatusModel.lockStatus(self.searchedResultResponse().username, ko.toJSON(payload), self.successHandler);
            } else {
                ReviewUserStatusModel.unlockStatus(self.searchedResultResponse().username, self.successHandler2);
            }
        };

        self.back = function () {

            rootParams.dashboard.loadComponent("users", self);
        };
    };
});