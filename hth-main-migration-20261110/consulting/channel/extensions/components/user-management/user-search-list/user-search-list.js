define([
    "ojs/ojcore",
    "knockout",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/user-search-list",
    "framework/js/configurations/config",
    "platform",
    "ojs/ojtable",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingcontrol",
    "ojs/ojpagingtabledatasource",
    "ojs/ojswitch",
    "framework/elements/api/page-section/loader"
], function (oj, ko, $, UserListDetailsModel, resourceBundle, Configurations, Platform) {
    "use strict";

    return function viewModel(rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.searchedResultResponse = rootParams.dataSource;
        self.nls = resourceBundle;
        self.authenticator = Configurations.authentication.type;
        self.userList = ko.observableArray();
        self.userListLoaded = ko.observable(false);
        self.selectedUserData = ko.observable();
        self.username = ko.observable();
        self.userFullData = ko.observable();
        self.componentName = ko.observable();
        self.moduleName = ko.observable();
        self.noStatus = ko.observable(false);
        self.partyData = ko.observable(false);
        self.statusOptionValue = ko.observable();
        self.reason = ko.observable();
        self.deleteStatus = ko.observable(false);
        self.statusOptionValueNew = ko.observable();
        self.deleteStatusVal = ko.observable(self.nls.header.granted);
        self.selecteduserName = ko.observable();
        self.selectedUserDetail = ko.observable();
        self.lockType = ko.observable();
        self.isLockToggleDisabled = ko.observable(false);
        self.lockTypeOption = ko.observable();
        self.reasonArea = ko.observable(true);
        self.unlock = self.nls.header.unlock;
        self.unmapLoginId = ko.observable(false);
        self.unmapSignerId = ko.observable(false);
        self.loginId = ko.observable();
        self.signerId = ko.observable();
        self.userExtensionData = ko.observable();
        self.baseStatusValue = ko.observable();
        self.baseAccessValue = ko.observable();

        if (rootParams.noStatus) {
            self.noStatus(true);
        }

        if (rootParams.partyData) {
            self.partyData(true);
        }

        if (rootParams.componentName) {
            self.componentName(rootParams.componentName);
            self.moduleName(rootParams.moduleName);
            rootParams.baseModel.registerComponent(self.componentName(), self.moduleName());
        } else {
            rootParams.baseModel.registerComponent("user-read", "user-management");
            self.componentName("user-read");
        }

        rootParams.baseModel.registerComponent("review-user-status", "user-management");
        rootParams.baseModel.registerComponent("review-user-channel-access", "user-management");
        rootParams.baseModel.registerComponent("users-update", "user-management");
        self.dynamicCols = ko.observableArray([]);

        self.getCols = function () {
            const tempCols = [{
                    headerText: self.nls.header.fullName,
                    field: "firstName"
                },
                {
                    headerText: self.nls.header.userName,
                    field: "username"
                },
                {
                    headerText: self.nls.header.authPerson,
                    field: "userExtendedType"
                },
                {
                    headerText: self.nls.header.status,
                    field: "lockStatus"
                }
            ];

            Platform.getInstance("authentication").then(function (platform) {
                if (platform("behaviour", "channelAccess")) {
                    tempCols.push({
                        headerText: self.nls.header.channelAccess,
                        field: "deleteStatus"
                    });
                }

                tempCols.push({
                    headerText: self.nls.header.userChannelType,
                    field: "userChannelType"
                });

                self.dynamicCols(tempCols);
            });
        };

        if (self.searchedResultResponse.length > 0) {
            ko.utils.arrayForEach(self.searchedResultResponse, function (item) {
                self.statusOptionValue(item.userData.lockStatus);
                self.baseStatusValue(item.userData.lockStatus);
                self.reason(item.userData.lockReason);
                self.lockType(item.userData.lockType);
                item.userData.userExtendedType = item.userExtendedType;
                item.userData.isDeleteAllowed = item.isDeleteAllowed;
                item.userData.userChannelType = item.userChannelType;
                item.userData.closeId = item.closeId;

                if (item.userData.deleteStatus) {
                    self.deleteStatus(item.userData.deleteStatus);

                    if (self.deleteStatus()) {
                        self.deleteStatusVal(self.nls.header.revoked);
                    } else {
                        self.deleteStatusVal(self.nls.header.granted);
                    }
                }

                self.userList().push(item.userData);
                self.getCols();

                self.userListDetailsDataSource = new oj.ArrayTableDataSource(self.userList(), {
                    idAttribute: "username"
                });

                self.paginationDataSource = new oj.PagingTableDataSource(self.userListDetailsDataSource);
                self.cancelButtonFlag(false);
            });
        }

        self.statusOptionChangeHandler = function (event) {
            if (event.detail) {
                self.statusOptionValueNew(event.detail.value);

                if (self.statusOptionValueNew()) {
                    self.statusOptionValue(self.nls.header.lock);
                    self.reasonArea(false);
                } else {
                    self.statusOptionValue(self.nls.header.unlock);
                    self.reason("");
                    self.reasonArea(true);

                    if (Configurations.authentication.type === "IDCSAuthenticator") {
                        self.isLockToggleDisabled(true);
                    }
                }
            }
        };

        self.channelAccessChangeHandler = function (event) {
            if (event.detail.value) {
                self.deleteStatusVal(self.nls.header.revoked);
                self.deleteStatus(true);
                self.reasonArea(false);
            } else {
                self.deleteStatusVal(self.nls.header.granted);
                self.deleteStatus(false);
                self.reason("");
                self.reasonArea(true);
            }
        };

        self.edit = function (statusModeldata) {
            self.selectedUserData(statusModeldata);
            self.statusOptionValue(statusModeldata.lockStatus);
            self.baseStatusValue(statusModeldata.lockStatus);
            self.reason("");

            if (self.statusOptionValue() === "UNLOCK") {
                self.statusOptionValueNew(false);
                self.reasonArea(false);
                self.reason("");
            } else {
                self.statusOptionValueNew(true);

                if (statusModeldata.lockReason !== undefined) {
                    self.reason(statusModeldata.lockReason);
                }

                self.lockTypeOption(statusModeldata.lockType);

                if (statusModeldata.lockType) {
                    if (self.lockTypeOption() === "AUTOMATIC") {
                        self.reasonArea(true);
                    } else {
                        self.reasonArea(false);
                    }
                }
            }

            if (Configurations.authentication.type === "IDCSAuthenticator") {
                self.isLockToggleDisabled(false);
            }

            UserListDetailsModel.readUserExtension(statusModeldata.username).done(function (data) {
                self.userExtensionData(data.userExtensionDataDTO);
            });

            $("#statusDialog").trigger("openModal");
        };

        self.editChannelAccess = function (data) {
            self.selectedUserDetail(data);
            self.selecteduserName(data.username);
            self.deleteStatus(data.deleteStatus);
            self.baseAccessValue(data.deleteStatus);
            self.reason("");

            if (self.deleteStatus()) {
                self.deleteStatusVal(self.nls.header.revoked);
                self.reasonArea(false);

                if (data.deleteReason !== undefined) {
                    self.reason(data.deleteReason);
                }
            } else {
                self.deleteStatusVal(self.nls.header.granted);
                self.reasonArea(true);
            }

            UserListDetailsModel.readUserExtension(data.username).done(function (data) {
                self.userExtensionData(data.userExtensionDataDTO);

                if (data.userExtensionDataDTO.loginID !== undefined && data.userExtensionDataDTO.loginID !== null) {
                    self.loginId(data.userExtensionDataDTO.loginID);
                    self.unmapLoginId(true);
                }

                if (data.userExtensionDataDTO.signerID !== undefined && data.userExtensionDataDTO.signerID !== null) {
                    self.signerId(data.userExtensionDataDTO.signerID);
                    self.unmapSignerId(true);
                }
            });

            $("#channelAccessDialog").trigger("openModal");
        };

        self.showUserDetails = function (userdetails) {
            if (self.noStatus() && self.partyData()) {
                UserListDetailsModel.readUser(userdetails.username).done(function (data) {
                    self.userFullData(data.userDTO);
                    self.statusOptionValue(data.userDTO.lockStatus);
                    self.username(data.userDTO.username);

                    rootParams.dashboard.loadComponent(self.componentName(), {
                        usernamesearched: self.usernamesearched(),
                        firstNamesearched: self.firstNamesearched(),
                        lastNamesearched: self.lastNamesearched(),
                        emailIdsearched: self.emailIdsearched(),
                        mobileNumbersearched: self.mobileNumbersearched(),
                        searchedUserList: self.user().searchedUserList(),
                        partyIdSearched: self.partyIdSearched(),
                        username: self.username(),
                        countries: self.countries ? self.countries() : self.countries,
                        salutationList: self.salutationList ? self.salutationList() : self.salutationList,
                        childRoleEnumsLoaded: self.childRoleEnumsLoaded ? self.childRoleEnumsLoaded() : self.childRoleEnumsLoaded,
                        isCountryFetched: self.isCountryFetched ? self.isCountryFetched() : self.isCountryFetched,
                        childRoleEnums: self.childRoleEnums ? self.childRoleEnums() : self.childRoleEnums,
                        userFullData: self.userFullData ? self.userFullData() : self.userFullData
                    });
                });
            } else {
                self.userFullData(userdetails);
                self.statusOptionValue(userdetails.lockStatus);
                self.username(userdetails.username);

                rootParams.dashboard.loadComponent(self.componentName(), {
                    usernamesearched: self.usernamesearched(),
                    firstNamesearched: self.firstNamesearched(),
                    lastNamesearched: self.lastNamesearched(),
                    emailIdsearched: self.emailIdsearched(),
                    mobileNumbersearched: self.mobileNumbersearched(),
                    searchedUserList: self.user().searchedUserList(),
                    partyIdSearched: self.partyIdSearched(),
                    username: self.username(),
                    countries: self.countries ? self.countries() : self.countries,
                    salutationList: self.salutationList ? self.salutationList() : self.salutationList,
                    childRoleEnumsLoaded: self.childRoleEnumsLoaded ? self.childRoleEnumsLoaded() : self.childRoleEnumsLoaded,
                    isCountryFetched: self.isCountryFetched ? self.isCountryFetched() : self.isCountryFetched,
                    childRoleEnums: self.childRoleEnums ? self.childRoleEnums() : self.childRoleEnums,
                    userFullData: self.userFullData ? self.userFullData() : self.userFullData
                });
            }
        };

        self.cancelModal = function () {
            $("#statusDialog").trigger("closeModal");
            $("#channelAccessDialog").trigger("closeModal");
        };

        self.submit = function () {

            if ((self.baseStatusValue() === self.statusOptionValue()) || (self.baseStatusValue() === "UNLOCK" && self.statusOptionValue() === self.nls.header.unlock)) {
                rootParams.baseModel.showMessages(null, [self.nls.header.statusNotChanged], "ERROR");

                return;
            }

            if ((self.baseStatusValue() === self.statusOptionValue()) || (self.baseStatusValue() === "LOCK" && self.statusOptionValue() === self.nls.header.lock)) {
                rootParams.baseModel.showMessages(null, [self.nls.header.statusNotChanged], "ERROR");

                return;
            }

            $("#statusDialog").trigger("closeModal");

            rootParams.dashboard.loadComponent("review-user-status", {
                statusOptionValueNew: self.statusOptionValueNew,
                statusOptionValue: self.statusOptionValue,
                reason: self.reason,
                selectedUserData: self.selectedUserData,
                userExtensionData: self.userExtensionData(),
                usernamesearched: self.usernamesearched(),
                firstNamesearched: self.firstNamesearched(),
                lastNamesearched: self.lastNamesearched(),
                emailIdsearched: self.emailIdsearched(),
                mobileNumbersearched: self.mobileNumbersearched(),
                searchedUserList: self.user().searchedUserList(),
                partyIdSearched: self.partyIdSearched(),
                username: self.username(),
                countries: self.countries ? self.countries() : self.countries,
                salutationList: self.salutationList ? self.salutationList() : self.salutationList,
                childRoleEnumsLoaded: self.childRoleEnumsLoaded ? self.childRoleEnumsLoaded() : self.childRoleEnumsLoaded,
                isCountryFetched: self.isCountryFetched ? self.isCountryFetched() : self.isCountryFetched,
                childRoleEnums: self.childRoleEnums ? self.childRoleEnums() : self.childRoleEnums,
                userFullData: self.userFullData ? self.userFullData() : self.userFullData
            });
        };

        self.submitLockStatus = function () {

            if (self.baseAccessValue() === self.deleteStatus()) {
                rootParams.baseModel.showMessages(null, [self.nls.header.accessNotChanged], "ERROR");

                return;
            }

            $("#channelAccessDialog").trigger("closeModal");

            const params = ko.mapping.toJS({
                deleteStatus: self.deleteStatus,
                selectedUserDetail: self.selectedUserDetail,
                reason: self.reason,
                unmapLoginId: self.unmapLoginId(),
                unmapSignerId: self.unmapSignerId(),
                loginId: self.loginId(),
                signerId: self.signerId(),
                userExtensionData: self.userExtensionData(),
                usernamesearched: self.usernamesearched(),
                firstNamesearched: self.firstNamesearched(),
                lastNamesearched: self.lastNamesearched(),
                emailIdsearched: self.emailIdsearched(),
                mobileNumbersearched: self.mobileNumbersearched(),
                searchedUserList: self.user().searchedUserList(),
                partyIdSearched: self.partyIdSearched(),
                username: self.username(),
                countries: self.countries ? self.countries() : self.countries,
                salutationList: self.salutationList ? self.salutationList() : self.salutationList,
                childRoleEnumsLoaded: self.childRoleEnumsLoaded ? self.childRoleEnumsLoaded() : self.childRoleEnumsLoaded,
                isCountryFetched: self.isCountryFetched ? self.isCountryFetched() : self.isCountryFetched,
                childRoleEnums: self.childRoleEnums ? self.childRoleEnums() : self.childRoleEnums,
                userFullData: self.userFullData ? self.userFullData() : self.userFullData
            });

            rootParams.dashboard.loadComponent("review-user-channel-access", params);
        };

        self.cancel = function () {
            rootParams.dashboard.switchModule();
        };
    };
});
