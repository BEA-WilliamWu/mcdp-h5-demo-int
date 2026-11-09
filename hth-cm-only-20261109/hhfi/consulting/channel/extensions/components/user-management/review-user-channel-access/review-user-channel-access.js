define([
    "knockout",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/user-search-list",
    "extensions/generic/service-extension",
    "ojs/ojtable",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingcontrol",
    "ojs/ojpagingtabledatasource",
    "ojs/ojswitch",
    "framework/elements/api/page-section/loader"
], function (ko, $, ReviewUserChannelAccessModel, resourceBundle, serviceExtension) {
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

        self.userSearchResponse = ko.observable();
        self.userExtensionData = ko.observable();
        self.nls = resourceBundle;
        self.approverReview = ko.observable(false);
        self.mobileflag = ko.observable(false);
        self.isCorpAdmin = ko.observable(false);
        self.dataLoaded = ko.observable(false);
        self.usernamesearched = ko.observable(rootParams.rootModel.params.usernamesearched);
        self.firstNamesearched = ko.observable(rootParams.rootModel.params.firstNamesearched);
        self.lastNamesearched = ko.observable(rootParams.rootModel.params.lastNamesearched);
        self.emailIdsearched = ko.observable(rootParams.rootModel.params.emailIdsearched);
        self.mobileNumbersearched = ko.observable(rootParams.rootModel.params.mobileNumbersearched);
        self.partyIdSearched = ko.observable(rootParams.rootModel.params.partyIdSearched);
        self.userList = ko.observable(rootParams.rootModel.params.searchedUserList);
        self.username = ko.observable(rootParams.rootModel.params.username);
        self.countries = ko.observable(rootParams.rootModel.params.countries);

        rootParams.dashboard.headerName(self.nls.header.userChannelAccess);

        if (rootParams.dashboard.appData.segment === "CORPADMIN") {
            self.isCorpAdmin(true);
        } else {
            self.isCorpAdmin(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.data) {
            self.userSearchResponse(ko.toJS(rootParams.rootModel.params.data));
            self.channelAccessValue = ko.observable(self.userSearchResponse().deleteStatus);
            self.deleteReason = ko.observable(self.userSearchResponse().deleteReason);

            ReviewUserChannelAccessModel.readUserExtension(self.userSearchResponse().username).done(function (data) {
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

            self.approverReview(true);
        } else {
            self.userSearchResponse = ko.observable(rootParams.rootModel.params.selectedUserDetail);
            self.channelAccessValue = ko.observable(rootParams.rootModel.params.deleteStatus);
            self.deleteReason = ko.observable(rootParams.rootModel.params.reason);
            self.unmapLoginId = ko.observable(rootParams.rootModel.params.unmapLoginId);
            self.unmapSignerId = ko.observable(rootParams.rootModel.params.unmapSignerId);
            self.loginId = ko.observable(rootParams.rootModel.params.loginId);
            self.signerId = ko.observable(rootParams.rootModel.params.signerId);
            self.userExtensionData(rootParams.rootModel.params.userExtensionData);
            self.mobileflag(true);
            self.dataLoaded(true);

        }

        if (self.channelAccessValue()) {
            self.userChannelAccess = ko.observable(self.nls.header.revoked);
            rootParams.dashboard.headerName(self.nls.header.userChannelAccessRevoke);

        } else {
            self.userChannelAccess = ko.observable(self.nls.header.granted);
            self.deleteReason = ko.observable("");
        }

        rootParams.baseModel.registerElement("confirm-screen");

        self.successHandler = function (data) {
            rootParams.dashboard.loadComponent("confirm-screen", {
                transactionResponse: data,
                transactionName: self.nls.header.userChannelAccess
            }, self);
        };

        self.successHandler2 = function (data) {
            rootParams.dashboard.loadComponent("confirm-screen", {
                transactionResponse: data,
                transactionName: self.nls.header.userChannelAccessRevoke
            }, self);
        };

        self.confirm = function () {
            if (!self.hthPageAllowed()) {
                return;
            }

            if (self.channelAccessValue()) {
                const payload = {
                    dictionaryArray: [{
                        nameValuePairDTOArray: [{
                            name: "loginId",
                            genericName: "loginId",
                            value: self.loginId()
                        }, {
                            name: "signerId",
                            genericName: "signerId",
                            value: self.signerId()
                        }]
                    }]
                };

                if (self.deleteReason() !== "") {
                    $.extend(payload, {
                        deleteReason: self.deleteReason()
                    });
                }

                serviceExtension.fetchBMTransactionNo("UM_N_USD_DLU01~UM_N_USD_DES01").then(function (BMTxnData) {
                    Promise.all([
                        self.unmapLoginId() ? ReviewUserChannelAccessModel.deleteLoginId(self.loginId(), self.userExtensionData().cdcNo, BMTxnData.UM_N_USD_DLU01) : "",
                        self.unmapSignerId() ? ReviewUserChannelAccessModel.deleteSignerId(self.signerId(), self.userExtensionData().cdcNo, BMTxnData.UM_N_USD_DES01) : ""
                    ]).then(function () {

                        const BMTxnRefToSent = {
                            bmTxnData: [{
                                txnRefNo: BMTxnData.UM_N_USD_DLU01,
                                txnCode: "UM_N_USD_DLU01"
                            }, {
                                txnRefNo: BMTxnData.UM_N_USD_DES01,
                                txnCode: "UM_N_USD_DES01"
                            }]
                        };

                        ReviewUserChannelAccessModel.deleteUser(self.userSearchResponse().username, ko.toJSON(BMTxnRefToSent), ko.toJSON(payload), self.successHandler2);
                    });
                });
            } else {
                ReviewUserChannelAccessModel.grantUser(self.userSearchResponse().username, self.successHandler);
            }
        };

        self.back = function () {

            rootParams.dashboard.loadComponent("users", self);
        };
    };
});