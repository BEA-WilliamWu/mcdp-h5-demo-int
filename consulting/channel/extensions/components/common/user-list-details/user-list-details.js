define([
    "ojs/ojcore",
    "knockout",
    "./model",
    "ojL10n!resources/nls/user-list-details",
    "extensions/generic/service-extension",
    "ojs/ojtable",
    "ojs/ojarraytabledatasource",
    "framework/elements/api/page-section/loader"
], function (oj, ko, UserListDetailsModel, resourceBundle, serviceExtension) {
    "use strict";

    return function viewModel(rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.partyDetails = rootParams.partyDetails;
        self.partyID = rootParams.rootModel.partyID();
        self.nls = resourceBundle;
        self.userList = ko.observableArray();
        self.accessCreatedUserList = ko.observableArray();
        self.nonAccessCreatedUserList = ko.observableArray();
        self.userListLoaded = ko.observable(false);
        self.validateSucces = ko.observable(false);

        // HTH metadata is returned through the extension dictionary to avoid changing the standard
        // user-list DTO contract; direct properties remain supported for forward compatibility.
        const readDictionaryValue = function (item, key) {
            let value = item[key];

            ko.utils.arrayForEach(item.dictionaryArray || [], function (dictionary) {
                ko.utils.arrayForEach((dictionary && dictionary.nameValuePairDTOArray) || [], function (nameValuePair) {
                    if (nameValuePair && (nameValuePair.name === key || nameValuePair.genericName === key)) {
                        value = nameValuePair.value;
                    }
                });
            });

            return value;
        },

        // BCO setup status remains the original accountAccessSetupDone value. HTH setup status is
        // derived independently from approved effective grants and is supplied by the backend.
         normalizeUser = function (item) {
            const userChannelType = String(readDictionaryValue(item, "userChannelType") || "BCO").toUpperCase(),
                firstName = item.firstName || "",
                lastName = item.lastName || "",
                isHthUser = userChannelType === "HTH",
                hthAccessSetupDone = String(readDictionaryValue(item, "hthAccessSetupDone") || "false").toLowerCase() === "true";

            return {
                username: item.username,
                enrolledfor2fa: item.enrolledfor2fa,
                firstName: firstName,
                accountAccessSetupDone: isHthUser ? hthAccessSetupDone : item.accountAccessSetupDone,
                lastName: lastName,
                fullName: `${item.firstName || ""} ${item.lastName || ""}`.trim(),
                partyID: item.partyId,
                customer: item.customer,
                userChannelType: isHthUser ? "HTH" : "BCO",
                closeId: readDictionaryValue(item, "closeId") || null
            };
        };

        UserListDetailsModel.fetchAssociatedUserForParty(self.partyID).done(function (data) {
            if (data.userDTOList && data.userDTOList.length > 0) {
                self.userList(data.userDTOList);

                ko.utils.arrayForEach(self.userList(), function (item) {
                    const normalizedUser = normalizeUser(item);

                    if (normalizedUser.accountAccessSetupDone === true) {
                        self.accessCreatedUserList().push(normalizedUser);
                    } else {
                        self.nonAccessCreatedUserList().push(normalizedUser);
                    }
                });

                self.accessCreatedUserList.sort(function (left, right) {
                    return left.username.toLowerCase().localeCompare(right.username.toLowerCase());
                });

                self.nonAccessCreatedUserList.sort(function (left, right) {
                    return left.username.toLowerCase().localeCompare(right.username.toLowerCase());
                });

                self.userList([]);

                ko.utils.arrayForEach(self.accessCreatedUserList(), function (item) {
                    self.userList().push(item);
                    self.userListDetailsDataSource = new oj.ArrayTableDataSource(self.userList(), { idAttribute: "username" });
                });

                ko.utils.arrayForEach(self.nonAccessCreatedUserList(), function (item) {
                    self.userList().push(item);
                    self.userListDetailsDataSource = new oj.ArrayTableDataSource(self.userList(), { idAttribute: "username" });
                });

                self.userListLoaded(true);
            } else {
                rootParams.baseModel.showMessages(null, [self.nls.info.recordNotFound], "ERROR");
            }
        });

        self.showUserAccountAccess = function (data) {
            const isHthUser = data && String(data.userChannelType || "").toUpperCase() === "HTH";

            if (!isHthUser && data !== undefined && data.accountAccessSetupDone !== undefined && data.accountAccessSetupDone === false) {
                serviceExtension.checkTransactionQualifiesOMB("UAT_N_CA").then(function () {
                    self.selectedUserData(data);
                });
            } else {
                self.selectedUserData(data);
            }
        };

        self.placeInitials = function (firstName, lastName) {
            const initial = firstName.charAt(0) + lastName.charAt(0);

            return initial.toUpperCase();
        };
    };
});
