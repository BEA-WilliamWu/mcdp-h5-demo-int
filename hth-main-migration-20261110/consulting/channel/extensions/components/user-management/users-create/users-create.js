define([
    "ojs/ojcore",
    "knockout",
    "./model",
    "jquery",
    "ojL10n!extensions/resources/nls/user-management",
    "framework/js/constants/constants",
    "extensions/generic/service-extension",
    "ojs/ojinputtext",
    "ojs/ojselectcombobox",
    "ojs/ojdatetimepicker",
    "ojs/ojradioset",
    "ojs/ojvalidationgroup",
    "ojs/ojknockout-validation",
    "ojs/ojcheckboxset",
    "ojs/ojswitch"
], function (oj, ko, UsersCreateModel, $, resourceBundle, Constants, serviceExtension) {
    "use strict";

    return function (rootParams) {
        const getNewKoModel = function () {
                const KoModel = UsersCreateModel.getNewModel();

                return ko.mapping.fromJS(KoModel);
            },
            self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.nls = resourceBundle;
        self.usernamesearched = ko.observable(rootParams.rootModel.params.usernamesearched);
        self.firstNamesearched = ko.observable(rootParams.rootModel.params.firstNamesearched);
        self.lastNamesearched = ko.observable(rootParams.rootModel.params.lastNamesearched);
        self.emailIdsearched = ko.observable(rootParams.rootModel.params.emailIdsearched);
        self.mobileNumbersearched = ko.observable(rootParams.rootModel.mobileNumbersearched);
        self.partyIdSearched = ko.observable(rootParams.rootModel.partyIdSearched);
        self.userList = ko.observable(rootParams.rootModel.params.user.searchedUserList);
        self.fromBack = ko.observable(false);
        self.selectedParentRole = ko.observable("corporateuser");
        self.partyList = ko.observableArray();
        self.retailuser = ko.observable("RETAIL");
        self.employee = ko.observable("EMPLOYEE");
        self.corporate = ko.observable("CORPORATE");
        self.emptyPlaceholder = ko.observable(false);
        self.validationTracker = ko.observable();
        self.partyTracker = ko.observable();
        self.rootModelInstance = ko.observable(getNewKoModel());
        self.additionalDetails = ko.observable();
        self.countries = ko.observableArray();
        self.countriesMap = {};
        self.selectedLimitPackage = ko.observable();
        self.header = self.nls.fieldname.limit;
        self.selectedLimitPackagesEditMode = ko.observableArray();
        self.isDisabled = ko.observable(false);
        self.firstName = ko.observable(rootParams.rootModel.params.uData.firstName);
        self.partyId = ko.observable(rootParams.rootModel.params.uData.partyId);
        self.rootModelInstance().partyDetails.partyId(rootParams.rootModel.params.uData.partyId);
        self.partyName = ko.observable(rootParams.rootModel.params.uData.partyName);
        self.createReviewFlag = ko.observable(false);
        self.reviewVisited = ko.observable(false);
        self.selectedSegmentRoles = ko.observableArray();
        self.isSegmentContainsRole = ko.observable(false);
        self.selectedUserGroupValues = ko.observableArray();
        self.userGroupValues = ko.observableArray();
        self.countryCallingCodes = ko.observableArray();
        self.parentRole = ko.observable();
        self.touchPointDisable = ko.observable(true);
        self.signerPinRequired = ko.observable(false);
        self.helpInstruction = ko.observable();

        self.address = ko.observable({
            line1: ko.observable(rootParams.rootModel.params.uData.address1),
            line2: ko.observable(rootParams.rootModel.params.uData.address2),
            line3: ko.observable(rootParams.rootModel.params.uData.address3),
            line4: ko.observable(rootParams.rootModel.params.uData.address4),
            city: ko.observable([rootParams.rootModel.params.uData.city]),
            state: ko.observable(rootParams.rootModel.params.uData.state),
            country: ko.observable([rootParams.rootModel.params.uData.country]),
            zipCode: ko.observable(rootParams.rootModel.params.uData.postalCode)
        });

        self.currentUsername = "";
        self.username = ko.observable(rootParams.rootModel.params.uData.username);
        self.middleName = ko.observable(rootParams.rootModel.params.uData.middleName);
        self.lastName = ko.observable(rootParams.rootModel.params.uData.lastName);
        self.dateOfBirth = ko.observable(rootParams.rootModel.params.uData.dateOfBirth);
        self.mobileNumber = ko.observable(rootParams.rootModel.params.uData.mobileNumber);
        self.emailId = ko.observable(rootParams.rootModel.params.uData.emailId);
        self.phoneNumber = ko.observable(rootParams.rootModel.params.uData.phoneNumber);
        self.employeeNumber = ko.observable(rootParams.rootModel.params.uData.employeeNumber);
        self.employeeType = ko.observable(rootParams.rootModel.params.uData.employeeType);
        self.organization = ko.observable(rootParams.rootModel.params.uData.organization);
        self.manager = ko.observable(rootParams.rootModel.params.uData.manager);
        self.homeBranch = ko.observable("");
        self.homeEntity = ko.observable("");
        self.accessibleEntity = ko.observable("");
        self.title = ko.observable(rootParams.rootModel.params.uData.title);
        self.userType = ko.observable("corporateuser");
        self.userGroups = ko.observableArray(rootParams.rootModel.params.uData.userGroups);
        self.showUpdateButton = ko.observable(rootParams.rootModel.params.showUpdateButton);
        self.validateButtonPressed = ko.observable(rootParams.rootModel.params.validateButtonPressed());
        self.headingUserType = ko.observable("nls.headers.usertypeselected" + rootParams.rootModel.params.uData.userType);
        self.selectedLimit = ko.observable();
        self.selectedLimitDescription = ko.observable();
        self.userLimitsListLoaded = ko.observable(false);
        self.userLimitsList = ko.observableArray([]);
        self.selectedAccessPoint = ko.observable();
        self.accessPointArray = ko.observable();
        self.selectedEntityAccessPoint = ko.observable();
        self.userPreferenceRelationship = ko.observableArray([]);
        self.entityAccessPoint = ko.observable();
        self.selectedUserLimit = ko.observable();
        self.previousSelectedUserLimit = ko.observable(rootParams.rootModel.params.uData.limitGroupId);
        self.limitGroupId = ko.observable(rootParams.rootModel.params.uData.limitGroupId);
        self.limitGroupName = ko.observable(rootParams.rootModel.params.uData.limitGroupName);
        self.limitGroupDescription = ko.observable(rootParams.rootModel.params.uData.limitGroupDescription);
        self.selectedSegmentCode = ko.observable(self.selectedSegmentCode);
        self.selectedSegmentName = ko.observable(self.selectedSegmentName);
        self.showChildRole = ko.observable(false);
        self.selectedChildRole = ko.observableArray([]);
        self.userChannelType = ko.observable("BCO");

        /**
         * HTH API Password Code observables (BCOH2H-787).
         * - codeId: written to extensionData.hthApiPasswordCodeId on user create/update submit
         * - code: plain text held only in memory for the current session (never persisted)
         * - codeStatus: PENDING / ACTIVE / EXPIRED / USED / "" (no code)
         * - codeVisible: eye-toggle state
         */
        self.hthApiPasswordCodeId = ko.observable();
        self.hthApiPasswordCode = ko.observable();
        self.hthApiPasswordCodeStatus = ko.observable();
        self.hthApiPasswordCodeVisible = ko.observable(false);

        self.isViewerRole = function (roleId) {
            const normalizedRoleId = String(roleId || "").trim().toUpperCase(),
                role = self.childRoleEnums && ko.utils.arrayFirst(self.childRoleEnums(), function (roleOption) {
                    return String(roleOption.applicationRoleId || roleOption).trim().toUpperCase() === normalizedRoleId;
                }),
                normalizedRoleName = role && role.applicationRoleName ? String(role.applicationRoleName).trim().toUpperCase() : "";

            return normalizedRoleId === "VIEWER" || normalizedRoleId === "VIEWER_TL" ||
                normalizedRoleName === "VIEWER" || normalizedRoleName === "VIEWER_TL";
        };

        self.isUserRoleDisabled = function (roleId) {
            return self.userChannelType() === "HTH" && !self.isViewerRole(roleId);
        };

        self.hasInvalidHthRoleSelection = function () {
            return self.userChannelType() === "HTH" && self.selectedChildRole().some(function (roleId) {
                return !self.isViewerRole(roleId);
            });
        };

        self.userChannelType.subscribe(function (userChannelType) {
            if (userChannelType === "HTH") {
                self.selectedChildRole.remove(function (roleId) {
                    return !self.isViewerRole(roleId);
                });
            }
        });

        /**
         * Generate HTH API Password Code (create page).
         * Calls POST /hostToHostApiPassword/generate, displays code in reminder modal.
         */
        self.generateHthApiPasswordCode = function () {
            const partyId = self.rootModelInstance().partyDetails.party.value(),
             userName = (self.username() ? self.username().toUpperCase() : self.username()) + "@" + partyId;

            if (!userName || !self.checkIfUserExistsFlag()) {
                rootParams.baseModel.showMessages(null, [self.nls.required.username], "ERROR");

                return;
            }

            UsersCreateModel.generateHthApiPasswordCode(partyId, userName).done(function (data) {
                if (data.status && data.status.result === "SUCCESSFUL") {
                    self.hthApiPasswordCodeId(data.codeId);
                    self.hthApiPasswordCode(data.code);
                    self.hthApiPasswordCodeStatus(data.codeStatus);
                    $("#hthApiPasswordCodeReminder").trigger("openModal");
                }
            }).fail(function (error) {
                const errorMsg = error && error.status && error.status.message && error.status.message.message
                    ? error.status.message.message
                    : "Failed to generate HTH API Password Code";

                rootParams.baseModel.showMessages(null, [errorMsg], "ERROR");
            });
        };

        /**
         * Re-generate HTH API Password Code (create/update page).
         * Calls POST /hostToHostApiPassword/generate, invalidates old code.
         */
        self.regenerateHthApiPasswordCode = function () {
            const partyId = self.rootModelInstance().partyDetails.party.value(),
             userName = (self.username() ? self.username().toUpperCase() : self.username()) + "@" + partyId;

            if (!userName || !self.checkIfUserExistsFlag()) {
                rootParams.baseModel.showMessages(null, [self.nls.required.username], "ERROR");

                return;
            }

            UsersCreateModel.generateHthApiPasswordCode(partyId, userName).done(function (data) {
                if (data.status && data.status.result === "SUCCESSFUL") {
                    self.hthApiPasswordCodeId(data.codeId);
                    self.hthApiPasswordCode(data.code);
                    self.hthApiPasswordCodeStatus(data.codeStatus);
                    $("#hthApiPasswordCodeReminder").trigger("openModal");
                }
            }).fail(function (error) {
                const errorMsg = error && error.status && error.status.message && error.status.message.message
                    ? error.status.message.message
                    : "Failed to regenerate HTH API Password Code";

                rootParams.baseModel.showMessages(null, [errorMsg], "ERROR");
            });
        };

        /**
         * Toggle HTH API Password Code visibility (eye icon).
         * First reveal calls /reveal endpoint (audit logged), subsequent toggles are local.
         */
        self.toggleHthApiPasswordCodeVisible = function () {
            if (!self.hthApiPasswordCodeVisible() && self.hthApiPasswordCodeId()) {
                UsersCreateModel.revealHthApiPasswordCode(self.hthApiPasswordCodeId()).done(function (data) {
                    if (data.status && data.status.result === "SUCCESSFUL") {
                        self.hthApiPasswordCode(data.code);
                        self.hthApiPasswordCodeVisible(true);
                    }
                }).fail(function (error) {
                    const errorMsg = error && error.status && error.status.message && error.status.message.message
                        ? error.status.message.message
                        : "Failed to reveal HTH API Password Code";

                    rootParams.baseModel.showMessages(null, [errorMsg], "ERROR");
                });
            } else {
                self.hthApiPasswordCodeVisible(!self.hthApiPasswordCodeVisible());
            }
        };

        /**
         * Close HTH API Password Code reminder modal.
         */
        self.closeHthApiPasswordCodeReminder = function () {
            $("#hthApiPasswordCodeReminder").trigger("closeModal");
        };

        /**
         * Close HTH API Password auto-generated reminder modal.
         * Continue with review flow without re-running validation.
         */
        self.closeHthApiPasswordAutoGenReminder = function () {
            $("#hthApiPasswordAutoGenReminder").trigger("closeModal");
            // Continue with the review flow (codeId is now set)
            self.proceedWithReviewCreate();
        };

        /**
         * Continue with review create after validation and optional auto-gen.
         * Extracted from reviewCreateUser to avoid re-running validation.
         */
        self.proceedWithReviewCreate = function () {
            ko.utils.arrayForEach(self.accessibleEntityArray(), function (item) {
                ko.utils.arrayForEach(item.selectedAccessPoints, function (selectedAccessPointItem) {
                    self.accessibleEntityList.push({
                        accessPointId: selectedAccessPointItem,
                        userId: null,
                        status: true,
                        determinantValue: item.entityId()
                    });
                });
            });

            ko.utils.arrayForEach(self.accessibleEntityArray(), function (item) {
                ko.utils.arrayForEach(item.accessPointList, function (item1) {
                    if (item.selectedAccessPoints.indexOf(item1.value) === -1) {
                        self.accessibleEntityList.push({
                            accessPointId: item1.value,
                            userId: null,
                            status: false,
                            determinantValue: item.entityId()
                        });
                    }
                });
            });

            self.userGroups = ko.observableArray([self.userType(), self.selectedParentRole()]);

            const data = {
                username: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                firstName: self.firstName(),
                middleName: self.middleName(),
                lastName: self.lastName(),
                dateOfBirth: self.dateOfBirth(),
                mobileNumber: self.mobileNumber(),
                emailId: self.emailId() ? self.emailId().toLowerCase() : "",
                phoneNumber: self.phoneNumber(),
                employeeType: self.employeeType(),
                userType: self.selectedParentRole(),
                userGroups: self.selectedChildRole(),
                applicationRoles: self.selectedChildRole(),
                homeBranch: "",
                address: {
                    line1: self.address().line1(),
                    line2: self.address().line2(),
                    line3: self.address().line3(),
                    line4: self.address().line4() ? self.address().line4() : "",
                    city: self.address().city(),
                    state: self.address().state(),
                    country: self.address().country() ? self.address().country() : "",
                    zipCode: self.address().zipCode()
                },
                homeEntity: null,
                accessibleEntity: [],
                accessibleEntities: [{
                    entityId: null,
                    entityName: null,
                    partyName: null,
                    userPartyRelationship: {
                        determinantValue: null,
                        partyId: {
                            value: null,
                            displayValue: null
                        },
                        userId: null
                    }
                }],
                partyId: {
                    displayValue: self.rootModelInstance().partyDetails.party.displayValue(),
                    value: self.rootModelInstance().partyDetails.party.value()
                },
                partyName: self.partyName(),
                title: self.title() ? self.title() : "",
                organization: self.organization(),
                manager: self.manager(),
                employeeNumber: self.employeeNumber(),
                limitPackages: [{
                    targetUnit: null
                }],
                userPartyRelationshipDTOs: [{
                    userId: null,
                    determinantValue: null,
                    partyId: {
                        value: null,
                        displayValue: null
                    }
                }],
                segmentCode: self.selectedSegmentCode(),
                userGroupDTOs: []
            },
                extensionData = {
                    signerPinReferenceNo: self.signerIdPinRefNo(),
                    documentType: self.documentType(),
                    cdcNo: self.rootModelInstance().partyDetails.party.value(),
                    userID: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                    mobileCode: self.countryCallingCode(),
                    mobileNo: self.mobileNumber(),
                    loginHoldStatus: self.loginHoldStatus(),
                    signerHoldStatus: self.signerHoldStatus(),
                    isAuthorisedPerson: self.authorizedPerson(),
                    companyActivityLog: self.companyActivityLog(),
                    loginPinstatus: self.loginPinStatus(),
                    loginHoldReason: self.loginHoldReason(),
                    userExtensionKey: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                    signerID: self.signerId(),
                    documentID: self.documentId(),
                    documentCountry: self.documentCountry(),
                    loginID: self.loginId(),
                    loginPinReferenceNo: self.loginIdPinRefNo(),
                    signerHoldReason: self.signerHoldReason(),
                    signerPinstatus: self.signerPinStatus(),
                    idDocSubmitted: self.showIdDocCheckbox() ? ["true"] : ["false"],
                    userChannelType: self.userChannelType(),
                    hthApiPasswordCodeId: self.hthApiPasswordCodeId(),
                    hthApiPasswordCode: self.hthApiPasswordCode()
                },
                reviewData = {};

            ko.utils.extend(reviewData, data);
            ko.utils.extend(reviewData, extensionData);

            $.extend(self.params, {
                data: reviewData,
                accessibleEntityTemplate: self.accessibleEntityArray(),
                limitGroupName: self.limitGroupName
            });

            self.setEntityRelatedFields(data);
            self.createUserData(data);
            self.createReviewFlag(true);
            self.isNewUser(false);
            self.isUpdateUser(false);
            self.setEntityList();
            self.setUserGroupList(data);

            rootParams.baseModel.closeNotificationMessages();
        };

        self.createUserData = ko.observable();
        self.createConfrimFlag = ko.observable(false);
        self.updateReviewFlag = ko.observable(false);
        self.updateUserData = ko.observable();
        self.updateConfrimFlag = ko.observable(false);
        self.reviewData = ko.observable();
        self.transactionStatus = ko.observable();
        self.httpStatus = ko.observable();
        self.transactionDetail = ko.observable();
        self.userTypeDisplayValue = ko.observable();
        self.roleSelectionDone = ko.observable(false);
        rootParams.baseModel.registerComponent("review-user-create", "user-management");
        rootParams.baseModel.registerComponent("access-point-mapping", "financial-limits");
        rootParams.baseModel.registerElement("confirm-screen");
        rootParams.baseModel.registerComponent("accessible-entity", "user-management");
        self.uType = rootParams.rootModel.params.uData.userGroups[1];
        self.countryDisplayValue = ko.observable();
        self.countryCode = ko.observable();
        self.partyIdAvailable = ko.observable();
        self.partyIdDisplayValue = ko.observable();
        self.partyIdFetched = ko.observable(false);
        self.roleId = ko.observable();
        self.accessPointType = ko.observable("INT");
        self.accessPoint = ko.observableArray([]);
        self.rolePreferencesList = ko.observableArray();
        self.isAccessPointFetched = ko.observable(false);
        self.accessibleEntityList = ko.observableArray();
        self.isNewUser = ko.observable(rootParams.rootModel.params.isNewUser);
        self.user = ko.observable(rootParams.rootModel.params.user);
        self.showUserGroup = ko.observable(false);

        const searchParameters = {
            accessType: "INT"
        };

        self.statusOptionValue = ko.observable();
        self.isChecked = ko.observable();
        self.showCreateOption = ko.observable(false);
        self.checkAvailability = ko.observable(self.nls.common.checkAvailability);
        self.diableUserType = ko.observable(false);
        self.isSegmentFetched = ko.observable(false);
        self.userSegments = ko.observableArray();
        rootParams.dashboard.headerName(self.nls.headers.userManagement);
        self.transactionName = ko.observable();
        self.transactionName(rootParams.rootModel.params.transactionNameCreate);
        self.checkIfUserExistsFlag = ko.observable(false);
        self.documentType = ko.observable();
        self.documentCountry = ko.observable();
        self.documentId = ko.observable();
        self.defaultUserGroupLoaded = ko.observable(false);
        self.defaultUserGroup = ko.observable();
        self.loginId = ko.observable();
        self.signerId = ko.observable();
        self.loginIdPinRefNo = ko.observable();
        self.signerIdPinRefNo = ko.observable();
        self.loginPinStatus = ko.observable();
        self.loginHoldStatus = ko.observable();
        self.loginHoldReason = ko.observable();
        self.signerPinStatus = ko.observable();
        self.signerHoldStatus = ko.observable();
        self.signerHoldReason = ko.observable();
        self.idDocumentSubmitted = ko.observableArray(["true"]);
        self.authorizedPerson = ko.observableArray();
        self.companyActivityLog = ko.observableArray();
        self.loginIdList = ko.observableArray();
        self.signerIdList = ko.observableArray();
        self.isSignerIdLoaded = ko.observable(false);
        self.isLoginIdLoaded = ko.observable(false);
        self.countryCallingCode = ko.observable();
        self.showAuthorizedCheckbox = ko.observable(false);
        self.showActivityLogAtCompanyLevelCheckbox = ko.observable(false);
        self.showIdDocCheckbox = ko.observable(false);

        self.roleId(rootParams.dashboard.appData.segment);

        if (self.roleId() === "ADMIN") {
            self.roleId("administrator");
        } else if (self.roleId() === "CORP" || self.roleId() === "CORPADMIN") {
            self.roleId("corporateuser");
        } else if (self.roleId() === "RETAIL") {
            self.roleId("retailuser");
        }

        self.documentTypesList = ko.observableArray([{
                code: "Hong Kong Identity Card",
                description: self.nls.fieldname.hkId
            },
            {
                code: "Passport",
                description: self.nls.fieldname.passport
            },
            {
                code: "Chinese Travel Permit",
                description: self.nls.fieldname.chineseTravelPermit
            },
            {
                code: "ID",
                description: self.nls.fieldname.id
            },
            {
                code: "Others",
                description: self.nls.fieldname.others
            }
        ]);

        self.pinStatusList = ko.observableArray([{
                text: self.nls.info.normal,
                value: "Normal"
            },
            {
                text: self.nls.info.inactive,
                value: "Inactive"
            },
            {
                text: self.nls.info.beingReset,
                value: "Being Reset"
            }
        ]);

        self.holdStatusList = ko.observableArray([{
                text: self.nls.info.unhold,
                value: "Unhold"
            },
            {
                text: self.nls.info.noActivityAllowed,
                value: "No Activity Allowed"
            }
        ]);

        self.holdReasonList = ko.observableArray([{
                text: self.nls.info.nil,
                value: "NIL"
            },
            {
                text: self.nls.info.retryExceeded,
                value: "Retry Count Exceeded"
            },
            {
                text: self.nls.info.reqByCustomer,
                value: "Requested by Customer"
            },
            {
                text: self.nls.info.corpCustomer,
                value: "Corporate Customer"
            },
            {
                text: self.nls.info.others,
                value: "Others"
            }
        ]);

        self.fetchMe = function () {
            UsersCreateModel.fetchMeWithParty().done(function (data) {
                if (data.party.personalDetails.fullName) {
                    self.additionalDetails(data);
                    self.rootModelInstance().partyDetails.partyDetailsFetched(true);
                }
            });
        };

        const partyId = {};

        self.isCorpAdmin = false;
        partyId.value = rootParams.dashboard.userData.userProfile.partyId.value;
        partyId.displayValue = rootParams.dashboard.userData.userProfile.partyId.displayValue;

        if (partyId.value !== null && partyId.value.trim() !== "") {
            self.isCorpAdmin = true;

            UsersCreateModel.fetchPartyPreferences(partyId.value).done(function (data) {
                self.rootModelInstance().partyDetails.party.value(partyId.value);
                self.rootModelInstance().partyDetails.party.displayValue(partyId.displayValue);
                self.fetchMe();
                self.rootModelInstance().partyDetails.partyName(data.partyPreferencesDTOs.partyName);
                self.diableUserType(true);
                self.userType("corporateuser");
                self.selectedParentRole("corporateuser");
                self.fetchAccesspoints();
                self.setChildRolesForCorpAdm();

                self.assignableEntitiesData = [{
                    key: {
                        value: self.parentRole(),
                        type: "ROLE"
                    }
                }];

                UsersCreateModel.fetchUserLimitOptions(Constants.currentEntity, ko.toJSON(self.assignableEntitiesData)).done(function (data) {
                    self.userLimitsList(data.limitPackageDTOList);
                    self.userLimitsListLoaded(true);
                });

                self.isSegmentFetched(false);
                self.userSegments([]);

                const searchParameter = {
                    selectedUser: self.selectedParentRole()
                };

                UsersCreateModel.fetchUserSegments(searchParameter).done(function (data) {
                    self.userSegments([]);
                    self.isSegmentFetched(false);

                    for (let j = 0; j < data.segmentdtos.length; j++) {
                        self.userSegments().push({
                            text: data.segmentdtos[j].name,
                            value: data.segmentdtos[j].code,
                            roles: data.segmentdtos[j].roles
                        });
                    }

                    if (self.userSegments()[0].roles !== undefined) {
                        self.selectedSegmentRoles(self.userSegments()[0].roles);
                        self.isSegmentContainsRole(true);
                    }

                    self.isSegmentFetched(true);
                });
            });
        } else {
            self.diableUserType(false);
        }

        /**
         * This function gets the signerId and loginIds maintained in User Maintenance
         * @function fetchUserIdMaintenance
         */
        self.fetchUserIdMaintenance = function () {
            if (self.isCorpAdmin) {
                UsersCreateModel.fetchUserIdMaintenance(self.rootModelInstance().partyDetails.party.value()).done(function (data) {
                    for (let i = 0; i < data.listUserIdMaintenance.length; i++) {
                        if (data.listUserIdMaintenance[i].idType === "02" && data.listUserIdMaintenance[i].pinStatus === "Normal") {
                            self.loginIdList.push({
                                text: data.listUserIdMaintenance[i].id,
                                value: data.listUserIdMaintenance[i].id,
                                holdReason: data.listUserIdMaintenance[i].holdReason,
                                holdStatus: data.listUserIdMaintenance[i].holdStatus,
                                pinStatus: data.listUserIdMaintenance[i].pinStatus,
                                pinRefNo: data.listUserIdMaintenance[i].pinRefNo
                            });
                        }
                    }

                    self.isSignerIdLoaded(false);
                    self.isLoginIdLoaded(true);
                });
            } else {

                UsersCreateModel.fetchDayZeroUserIds("02", self.rootModelInstance().partyDetails.party.displayValue(), "ID").done(function (data) {
                    for (let i = 0; i < data.userIdsList.length; i++) {
                        const userObject = {
                            text: data.userIdsList[i].id,
                            value: data.userIdsList[i].id
                        };

                        self.loginIdList.push(userObject);
                    }

                    self.loginPinStatus("Inactive");
                    self.loginHoldStatus("Unhold");
                    self.loginHoldReason("NIL");
                    self.isLoginIdLoaded(true);
                });

                UsersCreateModel.fetchDayZeroUserIds("01", self.rootModelInstance().partyDetails.party.displayValue(), "UM").done(function (data) {
                    for (let i = 0; i < data.userIdsList.length; i++) {
                        const userObject = {
                            text: data.userIdsList[i].id,
                            value: data.userIdsList[i].id
                        };

                        self.signerIdList.push(userObject);
                    }

                    self.signerPinStatus("Inactive");
                    self.signerHoldStatus("Unhold");
                    self.signerHoldReason("NIL");
                    self.isSignerIdLoaded(true);
                });
            }

        };

        /**
         * Value change handler for loginID
         * @function loginIdChanged
         * @param {String} data indicates selected id
         */
        self.loginIdChanged = function (data) {
            if (self.isCorpAdmin) {
                for (let i = 0; i < self.loginIdList().length; i++) {
                    if (self.loginIdList()[i].value === data.detail.value) {
                        self.loginPinStatus(self.loginIdList()[i].pinStatus);
                        self.loginHoldStatus(self.loginIdList()[i].holdStatus);
                        self.loginHoldReason(self.loginIdList()[i].holdReason || self.nls.info.nil);
                        self.loginIdPinRefNo(self.loginIdList()[i].pinRefNo);
                        break;
                    }
                }
            }
        };

        self.pinRefNoValidator = {
            validate: function (value) {

                if (!self.signerPinRequired() && (self.signerIdPinRefNo() === "" || self.signerIdPinRefNo() === undefined)) {
                    return true;
                }

                if (value === "") {
                    throw new oj.ValidatorError("ERROR", self.nls.info.validRefNo);
                } else if (/^[0-9]*$/.test(value) === false || value.length > 8 || value.length < 8) {
                    throw new oj.ValidatorError("ERROR", self.nls.info.validRefNo);
                }

                return true;
            }
        };

        self.firstNameValidator = {
            validate: function (value) {

                if (/[!#$%^]/.test(value)) {
                    throw new oj.ValidatorError("ERROR", self.nls.info.validFirstName1);
                } else if (!/^[A-Za-z0-9~@&*()_+{}:"<>?`\-=[\];',./\\| ]*$/.test(value)) {
                    throw new oj.ValidatorError("ERROR", self.nls.info.validFirstName2);
                }

                return true;
            }
        };

        /**
         * Value change handler for signerID
         * @function signerIdChanged
         * @param {String} data indicates selected id
         */
        self.signerIdChanged = function () {
            if (self.isCorpAdmin === false && (self.signerId() !== null || self.signerId() !== "" || self.signerId() !== undefined)) {
                if (self.signerId() === "") {
                    self.showAuthorizedCheckbox(false);
                    self.showIdDocCheckbox(false);
                    self.signerPinRequired(false);
                    self.signerIdPinRefNo(undefined);
                } else {
                    self.showAuthorizedCheckbox(true);
                    self.showIdDocCheckbox(true);
                    self.signerPinRequired(true);
                }
            }
        };

        self.showActivityLogAtCompanyLevelCheckbox(true);

        self.documentIdValidator = {
            validate: function (value) {

                if (self.documentType() === "Hong Kong Identity Card") {

                    if (value === "") {
                        return;
                    }

                    const TEXT_REGEX = /^[a-zA-Z0-9]*$/;

                    if (!TEXT_REGEX.test(value)) {
                        throw new oj.ValidatorError("ERROR", self.nls.info.enterValidHKID);
                    }

                    if (value.length !== 8 && value.length !== 9) {
                        throw new oj.ValidatorError("ERROR", self.nls.info.enterValidHKID);
                    }

                    if (self.documentType() === "Hong Kong Identity Card" && value !== undefined && value !== "" && !serviceExtension.checkHKId(value)) {
                        throw new oj.ValidatorError("ERROR", self.nls.info.enterValidHKID);
                    }

                } else {
                    const docType = /^[a-zA-Z0-9 \)\(]*$/;

                    if (!docType.test(value)) {
                        throw new oj.ValidatorError("ERROR", self.nls.info.DOCUMENT_ID);
                    }
                }

            }
        };

        self.documentTypeChangeHandler = function (event) {

            self.documentId("");

            if(document.getElementById("documentId").valid === "invalidShown"){
                document.getElementById("documentId").reset();
            }

            if (event.detail && event.detail.value && event.detail.value === "Hong Kong Identity Card") {
               self.helpInstruction(self.nls.info.hkidToolTip);
            }else{
                self.helpInstruction("");
            }
        };

        /**
         * This function gets title and fullname based on document details
         * @function validateCustDetails
         */
        self.validateCustDetails = function () {

            if (self.documentType() === undefined || self.documentType() === "") {
                rootParams.baseModel.showMessages(null, [self.nls.info.selectDocType], "ERROR");

                return;
            }

            UsersCreateModel.validateCustDetails(self.documentType(), self.getCountryCode(self.documentCountry()), self.documentId()).done(function (data) {
                if (data.userResponseDTO.userDTO.firstName === "") {
                    rootParams.baseModel.showMessages(null, [self.nls.info.validateId], "ERROR");
                } else {
                    self.title(data.userResponseDTO.userDTO.title);
                    self.firstName(data.userResponseDTO.userDTO.firstName);
                }
            });
        };

        /**
         * @function getCountryCode
         * This fuction returns country name.
         * @param countryName this contains country full name as per enum.
         */
        self.getCountryCode = function (countryName) {
            if (self.countries().length > 0) {
                for (let i = 0; i < self.countries().length; i++) {
                    if (self.countries()[i].text === countryName) {
                        return self.countries()[i].value;
                    }
                }
            }
        };

        if (!self.isNewUser()) {
            self.showEditBlock = ko.observable(self.showEditBlock());
            self.selectedUserLimit = ko.observable(self.limitGroupId());
        }

        if (self.userType() === "corporateuser") {
            self.userTypeDisplayValue(self.nls.fieldname.corpUser);
        } else if (self.userType() === "administrator") {
            self.userTypeDisplayValue(self.nls.fieldname.admin);
        } else if (self.userType() === "retailuser") {
            self.userTypeDisplayValue(self.nls.fieldname.retailuser);
        }

        self.selectedCountryCode = ko.observable();

        if (typeof self.address().country() === "object") {
            self.selectedCountryCode(self.address().country()[0]);
        } else {
            self.selectedCountryCode(self.address().country());
        }

        self.setChildRolesForCorpAdm = function () {
            self.parentRole = ko.observable(rootParams.rootModel.params.uData.parentRole);
            self.parentRole("corporateuser");

            UsersCreateModel.fetchChildRole(self.parentRole()).done(function (data) {
                self.childRoleEnums(data.applicationRoleDTOs);
                self.childRoleEnumsLoaded(true);
                self.showChildRole(true);
            });
        };

        self.fetchAccesspoints = function () {
            UsersCreateModel.fetchAccess(searchParameters).done(function (data) {
                self.accessPoint([]);

                for (let i = 0; i < data.accessPointListDTO.length; i++) {
                    if (data.accessPointListDTO[i].id === "APINTERNET" || data.accessPointListDTO[i].id === "APMOBRESP") {
                        const tempAccessPoint = {
                            text: data.accessPointListDTO[i].description,
                            value: data.accessPointListDTO[i].id
                        };

                        if (self.isCorpAdmin) {
                            self.selectedAccessPoint(tempAccessPoint.value);
                        }

                        self.accessPoint().push(tempAccessPoint);
                        self.selectedAccessPoint(["APINTERNET", "APMOBRESP"]);

                    }
                }

                self.isAccessPointFetched(true);

            });
        };

        self.fetchUserGroupList = function (partyId) {
            UsersCreateModel.fetchUserGroupList(partyId).then(function (userGroups) {
                userGroups.userGroupDTOs.forEach(function (group) {
                    self.userGroupValues.push({
                        text: group.name,
                        value: group.id,
                        object: group
                    });

                });

                self.showUserGroup(true);
            });
        };

        self.assignableEntitiesData = [];

        self.parentOptionChangedHandler = function (event) {
            const val = event;

            if (val) {
                self.accessPoint = ko.observableArray([]);
                self.userType(self.selectedParentRole());
                self.parentRole(val);
                self.showChildRole(false);

                UsersCreateModel.fetchChildRole(self.parentRole()).done(function (data) {
                    self.childRoleEnums(data.applicationRoleDTOs);
                    self.childRoleEnumsLoaded(true);
                    self.roleSelectionDone(false);

                    if (self.selectedChildRole()) {
                        self.selectedChildRole.removeAll();
                    }

                    self.rootModelInstance().partyDetails.partyDetailsFetched(false);
                    self.showChildRole(true);
                });

                self.assignableEntitiesData = [{
                    key: {
                        value: self.parentRole(),
                        type: "ROLE"
                    }
                }];

                self.isAccessPointFetched(false);
                self.fetchAccesspoints();

                UsersCreateModel.fetchUserLimitOptions(Constants.currentEntity, ko.toJSON(self.assignableEntitiesData)).done(function (data) {
                    self.userLimitsList(data.limitPackageDTOList);
                    self.userLimitsListLoaded(true);
                });

                self.isSegmentFetched(false);
                self.userSegments([]);

                const searchParameter = {
                    selectedUser: self.selectedParentRole()
                };

                UsersCreateModel.fetchUserSegments(searchParameter).done(function (data) {
                    self.userSegments([]);
                    self.isSegmentFetched(false);

                    for (let j = 0; j < data.segmentdtos.length; j++) {
                        self.userSegments().push({
                            text: data.segmentdtos[j].name,
                            value: data.segmentdtos[j].code,
                            roles: data.segmentdtos[j].roles
                        });
                    }

                    self.isSegmentFetched(true);
                });
            } else if (self.parentRole()) {
                self.assignableEntitiesData = [{
                    key: {
                        value: self.parentRole(),
                        type: "ROLE"
                    }
                }];

                UsersCreateModel.fetchUserLimitOptions(Constants.currentEntity, ko.toJSON(self.assignableEntitiesData)).done(function (data) {
                    self.userLimitsList(data.limitPackageDTOList);
                    self.userLimitsListLoaded(true);
                });

                self.isSegmentFetched(false);
                self.userSegments([]);

                const searchParameter = {
                    selectedUser: self.selectedParentRole()
                };

                UsersCreateModel.fetchUserSegments(searchParameter).done(function (data) {
                    self.userSegments([]);
                    self.isSegmentFetched(false);

                    for (let j = 0; j < data.segmentdtos.length; j++) {
                        self.userSegments().push({
                            text: data.segmentdtos[j].name,
                            value: data.segmentdtos[j].code,
                            roles: data.segmentdtos[j].roles
                        });
                    }

                    self.isSegmentFetched(true);
                });
            }
        };

        self.parentOptionChangedHandler("corporateuser");

        self.childOptionChangedHandler = function (event) {
            if (event.detail.value) {
                UsersCreateModel.showPartyDetails().done(function (dataId) {
                    if (!dataId.userProfile.partyId.displayValue) {
                        self.partyIdFetched(true);
                    } else {
                        self.partyIdAvailable = dataId.userProfile.partyId.value;
                        self.partyIdDisplayValue = dataId.userProfile.partyId.displayValue;
                        self.fetchMeData();
                    }
                });

                self.fetchMeData = function () {
                    if (self.partyIdAvailable) {
                        self.rootModelInstance().partyDetails.party.value(self.partyIdAvailable);

                        UsersCreateModel.fetchPartyDetailsByName().done(function (data) {
                            if (data.party.personalDetails.fullName) {
                                self.rootModelInstance().partyDetails.partyDetailsFetched(true);
                                self.rootModelInstance().partyDetails.partyName(data.party.personalDetails.fullName);
                                self.rootModelInstance().partyDetails.party.displayValue(self.partyIdDisplayValue);
                            }
                        });
                    } else {
                        self.rootModelInstance().partyDetails.partyName(null);
                        self.rootModelInstance().partyDetails.party.value(null);
                        self.rootModelInstance().partyDetails.party.displayValue(null);
                    }
                };

                self.isDisabled(false);
                self.roleSelectionDone(true);
                self.refreshData();
            }
        };

        self.isNewUser = ko.observable(self.isNewUser());
        self.isUpdateUser = ko.observable(!self.isNewUser());
        self.accessibleEntityFetched = ko.observableArray([]);
        self.accessibleEntityList = ko.observableArray();

        self.salutationList = ko.observableArray([{
                code: "mr",
                description: self.nls.fieldname.mr
            },
            {
                code: "ms",
                description: self.nls.fieldname.ms
            },
            {
                code: "mrs",
                description: self.nls.fieldname.mrs
            },
            {
                code: "miss",
                description: self.nls.fieldname.miss
            },
            {
                code: "other",
                description: self.nls.fieldname.other
            }
        ]);

        self.showPartyId = ko.observable(false);
        self.showFormDetails = ko.observable(false);
        self.showPartyList = ko.observable(false);
        self.updateRecordId = ko.observable();
        self.todayDate = ko.observable(oj.IntlConverterUtils.dateToLocalIso(rootParams.baseModel.getDate()));
        self.countryList = ko.observable([]);
        self.cityList = ko.observable([]);
        self.isCountryFetched = ko.observable(false);
        self.isCountryCodesFetched = ko.observable(false);
        self.isCityFetched = ko.observable(true);
        self.childRoleEnums = ko.observableArray([]);
        self.childRoleEnumsLoaded = ko.observable(false);

        if (!self.isCorpAdmin) {
            self.parentRole = ko.observable(rootParams.rootModel.params.uData.parentRole);
        }

        self.disableTyping = function () {
            return false;
        };

        if (!self.isNewUser()) {
            if (self.userType().toLowerCase() === "retailuser") {
                self.isDisabled(true);
            }

            self.showFormDetails(true);
        }

        self.hidePanel = function () {
            self.showPartyList(false);
            self.partyList([]);
        };

        self.backOnReview = function () {
            self.isUpdateUser(true);
            self.updateReviewFlag(false);
        };

        self.backOnCreateReview = function () {
            self.accessibleEntityList([]);
            self.reviewVisited(true);
            self.selectedLimitPackagesEditMode.removeAll();

            if (self.selectedLimitPackage()) {
                for (let n = 0; n < self.selectedLimitPackage().length; n++) {
                    if (self.selectedLimitPackage()[n].selectedLimitPackage()) {
                        self.selectedLimitPackagesEditMode.push({
                            key: {
                                id: self.selectedLimitPackage()[n].selectedLimitPackage()
                            },
                            accessPointValue: self.selectedLimitPackage()[n].accessPoint
                        });
                    }
                }
            }

            self.isNewUser(true);
            self.createReviewFlag(false);
        };

        const today = rootParams.baseModel.getDate();

        today.setFullYear(today.getFullYear() - 18);

        function formatDate(date) {
            const d = rootParams.baseModel.getDate(date);
            let month = "" + (d.getMonth() + 1),
                day = "" + d.getDate();
            const year = d.getFullYear();

            if (month.length < 2) {
                month = "0" + month;
            }

            if (day.length < 2) {
                day = "0" + day;
            }

            return [
                year,
                month,
                day
            ].join("-");
        }

        self.maxDate = ko.observable(formatDate(today));
        self.userTypeEnums = ko.observableArray([]);
        self.userTypeEnumsLoaded = ko.observable(false);

        /**
         * This function to fetch the party details of specific party id
         * accepts params {data} - either its fetched data from service
         * or passed data from previous screen.
         *
         * @function populatePartydetails
         * @memberOf UsersCreateModel
         **/
        self.populatePartydetails = function (data) {
            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if (self.userType().toLowerCase() === "retailuser") {
                self.isDisabled(true);
            }

            if (self.isNewUser()) {
                self.showPartyId(false);
                self.showFormDetails(false);
                self.partyName(self.rootModelInstance().partyDetails.partyName());

                if (self.userType().toLowerCase() === "retailuser") {
                    let countryFound = false;

                    ko.utils.arrayForEach(self.countries(), function (item) {
                        if (item.value === data.party.addresses[0].postalAddress.country) {
                            data.party.addresses[0].postalAddress.country = item.value;
                            self.countryName = item.text;
                            countryFound = true;
                        }
                    });

                    if (!countryFound && data !== undefined) {
                        data.party.addresses[0].postalAddress.country = data.party.addresses[0].postalAddress.country.substring(0, 2);
                    }
                }

                if (self.userType().toLowerCase() === "corporateuser") {
                    self.fetchUserGroupList(self.rootModelInstance().partyDetails.party.value());
                }

                /**
                    Fill data only if user type is retailuser
                    **/
                self.firstName(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.firstName : "");
                self.middleName(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.middleName : "");
                self.lastName(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.lastName : "");
                self.title(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.salutation : "");
                self.emailId(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.email : "");
                self.address().line1(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.line1 : "");
                self.address().line2(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.line2 : "");
                self.address().line3(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.line3 : "");
                self.address().city(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.city : "");
                self.address().state(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.state : "");
                self.address().country(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.country : "");
                self.address().zipCode(self.userType().toLowerCase() === "retailuser" ? data.party.addresses[0].postalAddress.postalCode : "");
                self.dateOfBirth(self.userType().toLowerCase() === "retailuser" ? data.party.personalDetails.birthDate ? data.party.personalDetails.birthDate.substring(0, 10) : "" : "");

                ko.utils.arrayForEach(data.party.contacts, function (item) {
                    if (item.contactType === "WMO") {
                        self.mobileNumber(self.userType().toLowerCase() === "retailuser" ? item.phone.number : "");
                    } else if (item.contactType === "HPH") {
                        self.phoneNumber(self.userType().toLowerCase() === "retailuser" ? item.phone.number : "");
                    }
                });

                self.hidePanel();
                self.showPartyId(true);
                self.showCreateOption(true);
            } else {
                self.partyName(data.party.personalDetails.fullName);
                self.validateButtonPressed(true);
                self.showUpdateButton(false);
            }
        };

        self.loadFormDetails = function () {
            if (self.userType() === "corporateuser") {
                self.fetchUserIdMaintenance();
            }

            self.diableUserType(true);
            self.showCreateOption(false);
            self.showFormDetails(true);
        };

        const partyDetailsFetchedSubscription = self.rootModelInstance().partyDetails.partyDetailsFetched.subscribe(function () {
            if (self.rootModelInstance().partyDetails.partyDetailsFetched()) {
                self.populatePartydetails(self.additionalDetails());

                if (self.validateButtonPressed() === true) {
                    self.showUpdateButton(false);
                } else {
                    self.showUpdateButton(true);
                }
            }
        });

        /**
         * This function gets called on change of user Type from UI
         * on change of userType, formDetails are hidden and party details are hidden
         * @function userTypeChangeHandler
         * @memberOf UsersCreateModel
         **/
        self.userType.subscribe(function (newUserType) {
            if (self.userType()) {
                self.userType(newUserType);
                self.showPartyId(false);
                self.showFormDetails(false);
                self.firstName("");
                self.middleName("");
                self.lastName("");
                self.title("");
                self.emailId("");
                self.address().line1("");
                self.address().line2("");
                self.address().line3("");
                self.address().city("");
                self.address().state("");
                self.address().country("");
                self.address().zipCode("");
                self.dateOfBirth("");
                self.mobileNumber("");
                self.phoneNumber("");
            }
        });

        if (!self.isNewUser()) {
            self.id = ko.observable(rootParams.id);
        }

        self.assign = function (fetched) {
            for (let i = 0; i < fetched.length; i++) {
                const entry = {};

                entry.Entity = fetched[i].description;
                self.accessibleEntityList().push(entry);
            }
        };

        /**
         * This function fetches all the usergroup options
         * @function fetchUserGroupOptions
         * @memberOf UsersCreateModel
         **/
        UsersCreateModel.init();

        UsersCreateModel.fetchUserGroupOptions().done(function (data) {
            self.userTypeEnums(data.enterpriseRoleDTOs);

            UsersCreateModel.fetchRolePreference(self.roleId()).done(function (rolePreference) {
                self.rolePreferencesList(rolePreference.rolePreferencesList);

                const rolePreferencesList = self.rolePreferencesList().filter(function (rolePreferenceObject) {
                    if (rolePreferenceObject.preferenceId === "administrator" && rolePreferenceObject.value) {
                        return rolePreferenceObject.preferenceId === "administrator" && rolePreferenceObject.value;
                    } else if (rolePreferenceObject.preferenceId === "corporateuser" && rolePreferenceObject.value) {
                        return rolePreferenceObject.preferenceId === "corporateuser" && rolePreferenceObject.value;
                    } else if (rolePreferenceObject.preferenceId === "retailuser" && rolePreferenceObject.value) {
                        return rolePreferenceObject.preferenceId === "retailuser" && rolePreferenceObject.value;
                    }

                    return false;
                });

                self.rolePreferencesList(rolePreferencesList);

                const role = self.userTypeEnums().filter(function (roleObject) {
                    for (let i = 0; i < rolePreferencesList.length; i++) {
                        if (roleObject.enterpriseRoleId === rolePreferencesList[i].preferenceId) {
                            return roleObject;
                        }
                    }

                    return false;
                });

                self.userTypeEnums(role);
                self.userTypeEnumsLoaded(true);
            });
        });

        self.userLimitChangeHandler = function (event) {
            if (event.detail.value) {
                self.selectedUserLimit(event.detail.value);

                for (let i = 0; i < self.userLimitsList().length; i++) {
                    if (event.detail.value === self.userLimitsList()[i].key.id) {
                        self.limitGroupName(self.userLimitsList()[i].key.id);
                        self.limitGroupId(self.userLimitsList()[i].key.id);
                        self.limitGroupDescription(self.userLimitsList()[i].description);
                    }
                }
            }
        };

        self.refreshData = function () {
            self.username("");
            self.firstName("");
            self.middleName("");
            self.lastName("");
            self.dateOfBirth("");
            self.mobileNumber("");
            self.emailId("");
            self.phoneNumber("");
            self.employeeType("");
            self.title("");
            self.organization("");
            self.manager("");
            self.employeeNumber("");
            self.selectedUserLimit("");
            self.address().line1("");
            self.address().line2("");
            self.address().line3("");
            self.address().line4("");
            self.address().city("");
            self.address().state("");
            self.address().country("");
            self.address().zipCode("");
            self.rootModelInstance().partyDetails.partyId("");
            self.rootModelInstance().partyDetails.partyName("");
        };

        self.createUserExtension = function (userData) {
            const data = {
                signerPinReferenceNo: self.signerIdPinRefNo(),
                documentType: self.documentType(),
                cdcNo: self.rootModelInstance().partyDetails.party.value(),
                userID: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                mobileCode: self.countryCallingCode(),
                mobileNo: self.mobileNumber(),
                loginHoldStatus: self.loginHoldStatus(),
                signerHoldStatus: self.signerHoldStatus(),
                isAuthorisedPerson: self.authorizedPerson()[0] === "true",
                companyActivityLog: self.companyActivityLog()[0] === "true",
                loginPinstatus: self.loginPinStatus(),
                loginHoldReason: self.loginHoldReason(),
                userExtensionKey: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                signerID: self.signerId(),
                documentID: self.documentId(),
                documentCountry: self.documentCountry(),
                loginID: self.loginId(),
                loginPinReferenceNo: self.loginIdPinRefNo(),
                signerHoldReason: self.signerHoldReason(),
                signerPinstatus: self.signerPinStatus(),
                idDocSubmitted: self.showIdDocCheckbox(),
                userChannelType: self.userChannelType()
            };

            UsersCreateModel.init();

            UsersCreateModel.createUserExtension(ko.toJSON(data)).done(function (data) {
                rootParams.dashboard.loadComponent("confirm-screen", {
                    hostReferenceNumber: "121212",
                    transactionResponse: userData,
                    transactionName: self.nls.info.createTxnName
                });
            });
        };

        /**
         * This function creates the user,
         * posts all the details filled in the form as request payload.
         *
         * @function createUser
         * @memberOf UsersCreateModel
         **/
        self.createUser = function () {
            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if (self.hasInvalidHthRoleSelection()) {
                rootParams.baseModel.showMessages(null, [self.nls.info.hthViewerOnly], "ERROR");

                return;
            }

            self.userPreferenceRelationship([]);

            ko.utils.arrayForEach(self.accessPoint(), function (item) {
                if (self.selectedAccessPoint().indexOf(item.value) === -1) {
                    self.userPreferenceRelationship.push({
                        accessPointId: item.value,
                        userId: null,
                        status: false,
                        determinantValue: Constants.currentEntity
                    });
                } else {
                    self.userPreferenceRelationship.push({
                        accessPointId: item.value,
                        userId: null,
                        status: true,
                        determinantValue: Constants.currentEntity
                    });
                }
            });

            for (let h = 0; h < self.accessibleEntityList().length; h++) {
                self.userPreferenceRelationship.push({
                    accessPointId: self.accessibleEntityList()[h].accessPointId,
                    userId: self.accessibleEntityList()[h].userId,
                    status: self.accessibleEntityList()[h].status,
                    determinantValue: self.accessibleEntityList()[h].determinantValue
                });
            }

            const data = {
                username: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                firstName: self.firstName(),
                middleName: self.middleName(),
                lastName: self.lastName(),
                dateOfBirth: "1990-01-01",
                mobileNumber: self.mobileNumber(),
                emailId: self.emailId() ? self.emailId().toLowerCase() : "",
                phoneNumber: self.phoneNumber(),
                employeeType: self.employeeType(),
                userType: self.selectedParentRole(),
                homeBranch: "",
                address: {
                    line1: "Hong Kong",
                    line2: self.address().line2(),
                    line3: self.address().line3(),
                    line4: self.address().line4() ? self.address().line4() : "",
                    city: "Hong Kong",
                    state: self.address().state(),
                    country: "Hong Kong",
                    zipCode: self.address().zipCode()
                },
                homeEntity: null,
                accessibleEntity: [],
                accessibleEntities: [{
                    entityId: null,
                    entityName: null,
                    partyName: null,
                    userPartyRelationship: {
                        determinantValue: null,
                        partyId: {
                            value: null,
                            displayValue: null
                        },
                        userId: null
                    }
                }],
                userGroups: [self.selectedParentRole()],
                partyId: {
                    value: self.rootModelInstance().partyDetails.party.value() ? self.rootModelInstance().partyDetails.party.value() : ""
                },
                partyName: self.partyName(),
                title: self.title() ? self.title().toString() : "",
                organization: self.organization(),
                manager: self.manager(),
                employeeNumber: self.employeeNumber(),
                forceChangePassword: true,
                deleteStatus: false,
                limitPackages: [{
                    targetUnit: null
                }],
                userPartyRelationshipDTOs: [{
                    userId: null,
                    determinantValue: null,
                    partyId: {
                        value: null,
                        displayValue: null
                    }
                }],
                userAccessPointRelationshipList: self.userPreferenceRelationship(),
                applicationRoles: self.selectedChildRole(),
                segmentCode: self.selectedSegmentCode(),
                userGroupDTOs: self.selectedUserGroupValues()
            },
                extensionData = {
                    signerPinReferenceNo: self.signerIdPinRefNo(),
                    documentType: self.documentType(),
                    cdcNo: self.rootModelInstance().partyDetails.party.value(),
                    userID: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                    mobileCode: self.countryCallingCode(),
                    mobileNo: self.mobileNumber(),
                    loginHoldStatus: self.loginHoldStatus(),
                    signerHoldStatus: self.signerHoldStatus(),
                    isAuthorisedPerson: self.authorizedPerson()[0] === "true",
                    companyActivityLog: self.companyActivityLog()[0] === "true",
                    loginPinstatus: self.loginPinStatus(),
                    loginHoldReason: self.loginHoldReason(),
                    userExtensionKey: (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value(),
                    signerID: self.signerId(),
                    documentID: self.documentId(),
                    documentCountry: self.documentCountry(),
                    loginID: self.loginId(),
                    loginPinReferenceNo: self.loginIdPinRefNo(),
                    signerHoldReason: self.signerHoldReason(),
                    signerPinstatus: self.signerPinStatus(),
                    idDocSubmitted: self.showIdDocCheckbox(),
                    userChannelType: self.userChannelType(),
                    hthApiPasswordCodeId: self.hthApiPasswordCodeId(),
                    userDTO: data
                },
                addSigner = {
                    userId: self.signerId(),
                    signerId: self.signerId(),
                    hkID: self.documentId(),
                    hkIDCheckDigit: self.documentId(),
                    docTypeCode: self.documentType(),
                    documentCountry: self.documentCountry(),
                    mpfSignFlag: "N",
                    ldsSignFlag: "N",
                    scsSignFlag: "N",
                    caSignFlag: "N",
                    pinReferenceNo: self.signerIdPinRefNo(),
                    saqSignFlag: "N",
                    holdCode: self.signerHoldStatus,
                    holdReason: self.signerHoldReason,
                    pinStatus: self.signerPinStatus(),
                    pinChangeFlag: "Y",
                    subOption: "01",
                    cdcId: self.rootModelInstance().partyDetails.party.value(),
                    authPersonFlag: self.authorizedPerson()[0] === "true" ? "Y" : "N"
                },
                addLogin = {
                    idType: "02",
                    party: self.rootModelInstance().partyDetails.party,
                    ids: [{
                        id: self.loginId(),
                        pinRefNo: self.loginIdPinRefNo(),
                        pinStatus: self.loginPinStatus(),
                        holdStatus: self.loginHoldStatus(),
                        holdReason: self.loginHoldReason()
                    }],
                    subOption: "01",
                    idKey: {
                        partyId: self.rootModelInstance().partyDetails.party.value(),
                        id: self.loginId()
                    }
                };

            self.setEntityRelatedFields(data);
            UsersCreateModel.init();

            self.successHandler = function (data) {
                self.createReviewFlag(false);
                self.isNewUser(false);
                self.isUpdateUser(false);
                self.updateList(data.userResponseDTO);
                self.user().showCreateUser(false);
                self.transactionStatus(data.status);
                self.createConfrimFlag(true);

                rootParams.dashboard.loadComponent("confirm-screen", {
                    transactionResponse: data,
                    transactionName: self.nls.info.createTxnName
                });
            };

            UsersCreateModel.checkIfUserExists((self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value()).done(function (data) {
                if (!data.exists) {
                    serviceExtension.fetchBMTransactionNo("MT_N_CUS~UM_ID_MC_ALU01").then(function (BMTxnRef) {
                        Promise.all([
                            self.loginId() && self.isCorpAdmin ? "" : UsersCreateModel.addUserIdMaintenance(BMTxnRef.UM_ID_MC_ALU01, ko.toJSON(addLogin), self.targetUnit),
                            self.signerIdPinRefNo() ? UsersCreateModel.addSigner("01", BMTxnRef.MT_N_CUS, ko.toJSON(addSigner)) : ""
                        ]).then(function () {

                            const BMTxnRefToSent = {
                                bmTxnData: [{
                                    txnRefNo: BMTxnRef.MT_N_CUS,
                                    txnCode: "MT_N_CUS"
                                }, {
                                    txnRefNo: BMTxnRef.UM_ID_MC_ALU01,
                                    txnCode: "UM_ID_MC_ALU01"
                                }]
                            };

                            UsersCreateModel.createUserExtension(ko.toJSON(BMTxnRefToSent), ko.toJSON(extensionData), self.successHandler);
                        });
                    });
                } else {
                    rootParams.baseModel.showMessages(null, [self.nls.info.usernameTaken], "ERROR");
                }
            });
        };

        self.cancelreview = function () {
            self.createReviewFlag(false);
            self.isNewUser(true);
        };

        self.cancelUpdatereview = function () {
            self.updateReviewFlag(false);
            self.isNewUser(false);
            self.isUpdateUser(true);
        };

        self.done = function () {
            if (self.createConfrimFlag()) {
                self.reviewData(self.createUserData());
            } else {
                self.reviewData(self.updateUserData());
            }

            self.createConfrimFlag(false);
            self.createReviewFlag(false);
            self.updateConfrimFlag(false);
            self.updateReviewFlag(false);
            window.location.reload();
        };

        self.validateAccessibleEntityList = function () {
            let validationSucess = true;

            for (let l = 0; l < self.accessibleEntityArray().length; l++) {
                for (let m = 0; m < self.accessibleEntityArray().length; m++) {
                    if (l !== m && validationSucess && (self.accessibleEntityArray()[m].entityId() instanceof Array ? self.accessibleEntityArray()[m].entityId()[0] : self.accessibleEntityArray()[m].entityId()) === (self.accessibleEntityArray()[l].entityId() instanceof Array ? self.accessibleEntityArray()[l].entityId()[0] : self.accessibleEntityArray()[l].entityId())) {
                        rootParams.baseModel.showMessages(null, [self.nls.common.duplicateEntity], "ERROR");
                        validationSucess = false;
                    }
                }
            }

            return validationSucess;
        };

        /**
         * This function gets default user group from DayOneConfig
         * @function fetchDayOneConfig
         */
        UsersCreateModel.fetchDayOneConfig("DEFAULT_GROUP_USER_MGNT").then(function (data) {
            if (data.configResponseList.length > 0) {
                for (let i = 0; i < data.configResponseList.length; i++) {
                    if (data.configResponseList[i] !== undefined) {
                        self.defaultUserGroup(data.configResponseList[i].propertyValue);
                        self.defaultUserGroupLoaded(true);
                    }
                }
            }
        });

        /**
         * @function initiateCreateUser
         * This function checks for OMB and initate.
         */
        self.initiateCreateUser = function () {
            serviceExtension.checkTransactionQualifiesOMB("MT_N_CUS").then(function() {
                self.toCheckApproval();
            });
        };

        self.toCheckApproval = function () {
            const userId = (self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value();

            self.userGroupList = ko.observable();

            const j = 0;

            if (self.selectedUserGroupValues().length > 0) {
                for (let i = 0; i < self.selectedUserGroupValues().length; i++) {
                    if (i === j) {
                        self.userGroupList(self.selectedUserGroupValues()[i].id);
                    } else {
                        const userGroupId = self.userGroupList() + "~" + self.selectedUserGroupValues()[i].id;

                        self.userGroupList(userGroupId);
                    }
                }
            } else {
                self.selectedUserGroupValues.push(self.fetchUserGroupfromId(self.defaultUserGroup()));
                self.userGroupList(self.selectedUserGroupValues()[0].id);
            }

            UsersCreateModel.checkApproval(self.rootModelInstance().partyDetails.party.value(), self.userGroupList(), userId).done(function (resp) {
                if (resp.ifExists) {
                    $("#userGroupRejectNotification").trigger("openModal");
                } else {
                    self.reviewCreateUser();
                }
            });

        };

        /**
         * @function closeuserGroupNotification
         * This function closes UserGroup Reject warning modal window.
         */
        self.closeuserGroupNotification = function () {
            $("#userGroupRejectNotification").trigger("closeModal");
        };

        self.reviewCreateUser = function () {
            self.closeuserGroupNotification();

            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if (self.hasInvalidHthRoleSelection()) {
                rootParams.baseModel.showMessages(null, [self.nls.info.hthViewerOnly], "ERROR");

                return;
            }

            if (self.selectedChildRole().length === 0 && (self.selectedSegmentName() === undefined || self.selectedSegmentName() === "")) {
                rootParams.baseModel.showMessages(null, [self.nls.info.noRoleSegment], "ERROR");

                return;
            }

            if (self.companyActivityLog()[0] === "true") {
                if ((self.selectedChildRole().length === 1 && (self.selectedChildRole()[0] === "Viewer_TL" || self.selectedChildRole()[0] === "Migrated_SYSADM_TL"))
                || (self.selectedChildRole().length === 2 && ((self.selectedChildRole()[0] === "Viewer_TL" && self.selectedChildRole()[1] === "Migrated_SYSADM_TL")) || (self.selectedChildRole()[0] === "Migrated_SYSADM_TL" && self.selectedChildRole()[1] === "Viewer_TL"))) {
                    rootParams.baseModel.showMessages(null, [self.nls.info.noOtherRole], "ERROR");

                    return;
                }
            }

            if (!self.validateAccessibleEntityList()) {
                return;
            }

            // BCOH2H-787: auto-generate fallback — if HTH and no code generated yet,
            // call /generate, show the auto-gen reminder modal.
            // User clicks Proceed → closeHthApiPasswordAutoGenReminder → proceedWithReviewCreate.
            if (self.userChannelType() === "HTH" && !self.hthApiPasswordCodeId()) {
                const partyId = self.rootModelInstance().partyDetails.party.value(),
                 userName = (self.username() ? self.username().toUpperCase() : self.username()) + "@" + partyId;

                UsersCreateModel.generateHthApiPasswordCode(partyId, userName).done(function (data) {
                    if (data.status && data.status.result === "SUCCESSFUL") {
                        self.hthApiPasswordCodeId(data.codeId);
                        self.hthApiPasswordCode(data.code);
                        self.hthApiPasswordCodeStatus(data.codeStatus);
                        $("#hthApiPasswordAutoGenReminder").trigger("openModal");
                    }
                }).fail(function (error) {
                    const errorMsg = error && error.status && error.status.message && error.status.message.message
                        ? error.status.message.message
                        : "Failed to auto-generate HTH API Password Code";

                    rootParams.baseModel.showMessages(null, [errorMsg], "ERROR");
                });

                return;
            }

            // Validation passed, proceed with review
            self.proceedWithReviewCreate();
        };

        /**
         * This function updates the user,
         * internallly calls updateList method
         * posts updated details filled in the form as request payload.
         *
         * @function createUser
         * @memberOf UsersCreateModel
         **/
        self.userUpdateChangeHandler = function (event) {
            if (event.detail.value) {
                self.selectedUserLimit(event.detail.value);

                for (let i = 0; i < self.userLimitsList().length; i++) {
                    if (event.detail.value === self.userLimitsList()[i].key.id) {
                        self.limitGroupName(self.userLimitsList()[i].key.id);
                    }
                }
            }
        };

        self.updateUser = function () {
            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if (typeof self.title() === "object") {
                self.title(self.title()[0]);
            } else {
                self.title(self.title());
            }

            if (typeof self.address().city() === "object") {
                self.address().city(self.address().city()[0]);
            } else {
                self.address().city(self.address().city());
            }

            if (typeof self.address().country() === "object") {
                self.address().country(self.address().country()[0]);
            } else {
                self.address().country(self.address().country());
            }

            self.userGroups().push(self.userType());

            const data = {
                username: self.username() ? self.username().toUpperCase() : "",
                firstName: self.firstName() ? self.firstName() : "",
                middleName: self.middleName() ? self.middleName() : "",
                lastName: self.lastName() ? self.lastName() : "",
                dateOfBirth: self.dateOfBirth() ? self.dateOfBirth() : "",
                mobileNumber: self.mobileNumber() ? self.mobileNumber() : "",
                emailId: self.emailId() !== "" ? self.emailId().toLowerCase() : "",
                employeeType: self.employeeType() ? self.employeeType() : "",
                phoneNumber: self.phoneNumber() ? self.phoneNumber() : "",
                userType: self.uType ? self.uType : "",
                address: {
                    line1: self.address().line1() ? self.address().line1() : "",
                    line2: self.address().line2() ? self.address().line2() : "",
                    line3: self.address().line3() ? self.address().line3() : "",
                    line4: self.address().line4() ? self.address().line4() : "",
                    city: self.address().city() ? self.address().city() : "",
                    state: self.address().state() ? self.address().state() : "",
                    country: self.address().country() ? self.address().country() : "",
                    zipCode: self.address().zipCode() ? self.address().zipCode() : ""
                },
                partyId: self.partyId().value ? self.partyId().value : "",
                partyName: self.partyName() ? self.partyName() : "",
                userGroups: self.userGroups(),
                title: self.title() ? self.title() : "",
                organization: self.organization() ? self.organization() : "",
                manager: self.manager() ? self.manager() : "",
                employeeNumber: self.employeeNumber() ? self.employeeNumber() : ""
            };

            UsersCreateModel.init();

            UsersCreateModel.updateUser(ko.toJSON(data), self.username() ? self.username().toUpperCase() : self.username()).done(function (data, status, jqXhr) {
                self.updateReviewFlag(false);
                self.updateList(data);
                self.user().showCreateUser(false);
                self.httpStatus(jqXhr.status);
                self.transactionStatus(data);
                self.updateConfrimFlag(true);
            });
        };

        self.reviewUpdateUser = function () {
            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if (self.selectedUserLimit() === null || self.selectedUserLimit() === "") {
                self.limitGroupName(self.nls.info.limitGroupSelection);
            }

            if (typeof self.title() === "object") {
                self.title(self.title()[0]);
            } else {
                self.title(self.title());
            }

            const data = {
                username: self.username() ? self.username().toUpperCase() : "",
                firstName: self.firstName() ? self.firstName() : "",
                middleName: self.middleName() ? self.middleName() : "",
                lastName: self.lastName() ? self.lastName() : "",
                dateOfBirth: self.dateOfBirth() ? self.dateOfBirth() : "",
                mobileNumber: self.mobileNumber() ? self.mobileNumber() : "",
                emailId: self.emailId() !== "" ? self.emailId().toLowerCase() : "",
                employeeType: self.employeeType() ? self.employeeType() : "",
                phoneNumber: self.phoneNumber() ? self.phoneNumber() : "",
                userType: self.userType(),
                userRole: self.userGroups(),
                address: {
                    line1: self.address().line1() ? self.address().line1() : "",
                    line2: self.address().line2() ? self.address().line2() : "",
                    line3: self.address().line3() ? self.address().line3() : "",
                    line4: self.address().line4() ? self.address().line4() : "",
                    city: self.address().city() ? self.address().city() : "",
                    state: self.address().state() ? self.address().state() : "",
                    country: self.countryDisplayValue() ? self.countryDisplayValue() : "",
                    zipCode: self.address().zipCode() ? self.address().zipCode() : ""
                },
                partyId: self.rootModelInstance().partyDetails.partyId() ? self.rootModelInstance().partyDetails.partyId() : "",
                partyName: self.partyName() ? self.partyName() : "",
                title: self.title() ? self.title() : "",
                userGroups: self.userGroups,
                organization: self.organization() ? self.organization() : "",
                manager: self.manager() ? self.manager() : "",
                employeeNumber: self.employeeNumber() ? self.employeeNumber() : "",
                selectedUserLimit: self.limitGroupName() ? self.limitGroupName() : ""
            };

            self.updateUserData(data);
            self.updateReviewFlag(true);
            self.isUpdateUser(false);

            $.extend(self.params, {
                data: data
            });
        };

        /**
         * This function cancels the requested service.
         *
         * @function cancelCreateUser
         * @memberOf UsersCreateModel
         **/
        self.cancelCreateUser = function () {
            history.back();
        };

        self.onselectUserSegment = function (event) {
            const value = event.detail.value;

            self.isSegmentContainsRole(false);
            self.selectedSegmentRoles([]);
            self.selectedSegmentName("");

            ko.utils.arrayForEach(self.userSegments(), function (item) {
                if (item.value === value) {
                    self.selectedSegmentRoles(item.roles);
                    self.selectedSegmentName(item.text);

                    if (item.roles !== undefined) {
                        self.isSegmentContainsRole(true);
                    }
                }
            });
        };

        /**
         * This function updates the model data.
         *
         * @function updateList
         * @memberOf UsersCreateModel
         **/
        self.updateList = function () {
            if (typeof self.title() === "object") {
                self.title(self.title()[0]);
            } else {
                self.title(self.title());
            }

            const data = {
                username: self.username() ? self.username().toUpperCase() : self.username(),
                firstName: self.firstName(),
                middleName: self.middleName(),
                lastName: self.lastName(),
                dateOfBirth: self.dateOfBirth(),
                mobileNumber: self.mobileNumber(),
                emailId: self.emailId() ? self.emailId().toLowerCase() : "",
                phoneNumber: self.phoneNumber(),
                employeeNumber: self.employeeNumber(),
                employeeType: self.employeeType(),
                manager: self.manager(),
                userType: self.userType(),
                address: {
                    line1: self.address().line1(),
                    line2: self.address().line2(),
                    line3: self.address().line3(),
                    line4: self.address().line4(),
                    city: self.address().city(),
                    state: self.address().state(),
                    country: self.address().country(),
                    zipCode: self.address().zipCode()
                },
                homeBranch: "",
                homeEntity: "",
                accessibleEntity: [],
                partyId: self.rootModelInstance().partyDetails.partyId(),
                partyName: self.partyName(),
                title: self.title() ? self.title() : "",
                organization: self.organization()
            };

            if (self.user().searchedUserList() === undefined) {
                const emptyArray = [
                    "a",
                    "b"
                ];

                self.user().searchedUserList(emptyArray);
            }

            self.user().searchedUserList().splice(0, self.user().searchedUserList().length);
            self.user().searchedUserList().push(data);
            self.user().loadSearchData(false);
            self.user().loadSearchData(true);
        };

        self.cityChangeHandler = function (event) {
            if (event.detail.value) {
                self.address().city(event.detail.value);
            }
        };

        self.cancel = function () {
            rootParams.dashboard.switchModule(true);
        };

        self.countryChangeHandler = function (event) {
            if (typeof self.address().country() === "object") {
                self.countryCode = self.address().country()[0];
            } else {
                self.countryCode = self.address().country();
            }

            ko.utils.arrayForEach(self.countries(), function (item) {
                if (item.value === self.countryCode) {
                    self.countryDisplayValue(item.text);
                }
            });

            if (event.detail.value) {
                self.address().country(event.detail.value);
                self.selectedCountryCode(event.detail.value);
            }
        };

        self.statusOptionChangeHandler = function (event) {
            if (event.detail.value) {
                self.statusOptionValue(event.detail.value);
            }
        };

        self.backOnUpdate = function () {
            rootParams.dashboard.loadComponent("users", {});
        };

        self.backOnCreate = function () {
            rootParams.dashboard.loadComponent("users", {});
        };

        UsersCreateModel.fetchCountry().done(function (data) {
            if (data.enumRepresentations) {
                for (let i = 0; i < data.enumRepresentations[0].data.length; i++) {
                    self.countries.push({
                        text: data.enumRepresentations[0].data[i].description,
                        value: data.enumRepresentations[0].data[i].code
                    });

                    self.countriesMap[data.enumRepresentations[0].data[i].code] = data.enumRepresentations[0].data[i].description;
                }

                self.isCountryFetched(true);
            }
        });

        UsersCreateModel.fetchCountryCodes().done(function (data) {
            if (data.enumRepresentations) {
                for (let i = 0; i < data.enumRepresentations[0].data.length; i++) {
                    self.countryCallingCodes.push({
                        text: data.enumRepresentations[0].data[i].code,
                        value: data.enumRepresentations[0].data[i].value
                    });
                }

                self.isCountryCodesFetched(true);
                self.countryCallingCode("852");
            }
        });

        self.username.subscribe(function () {
            self.checkAvailability(self.nls.common.checkAvailability);
            self.checkIfUserExistsFlag(false);
        });

        let count = 1;

        $(document).on("blur", "#userName", function (e) {
            if (count > 0) {
                self.currentUsername = e.target.value;
                count--;
            } else {
                count = 1;
            }

            self.checkAvailability(self.nls.common.checkAvailability);
            self.checkIfUserExistsFlag(false);
        });

        self.checkIfUserExists = function () {
            if (self.currentUsername === "" || ((self.currentUsername ? self.currentUsername.toUpperCase() : self.currentUsername) !== (self.username() ? self.username().toUpperCase() : self.username()))) {
                self.checkAvailability(self.nls.common.checkAvailability);

                return;
            }

            UsersCreateModel.checkIfUserExists((self.username() ? self.username().toUpperCase() : self.username()) + "@" + self.rootModelInstance().partyDetails.party.value()).done(function (data) {
                if (!data.exists) {
                    self.checkAvailability(self.nls.common.available);
                    self.checkIfUserExistsFlag(true);
                } else {
                    self.username("");
                    self.checkAvailability(self.nls.common.checkAvailability);
                    rootParams.baseModel.showMessages(null, [self.nls.info.usernameTaken], "ERROR");
                }
            });
        };

        rootParams.baseModel.registerComponent("users", "user-management");
        rootParams.baseModel.registerElement("action-header");
        rootParams.baseModel.registerComponent("party-validate", "common");
        self.accessibleEntityArray = ko.observableArray([]);
        self.reloadPartyValidate = ko.observable(true);
        self.entityList = ko.observableArray([]);
        self.entitiesListLoaded = ko.observable(false);

        self.setEntityList = function () {
            const array = [];

            for (let i = 0; i < rootParams.dashboard.userData.userProfile.accessibleEntityDTOs.length; i++) {
                array.push(rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[i]);
            }

            let index = null;

            for (let j = 0; j < array.length; j++) {
                if (array[j].entityId === Constants.currentEntity) {
                    index = j;
                }
            }

            array.splice(index, 1);
            self.entityList(array.slice(0));
            self.entitiesListLoaded(true);
        };

        self.setEntityList();

        self.addAccessibleEntity = function () {
            self.partyInfo = {
                partyFirstName: ko.observable(),
                partyLastName: ko.observable(),
                userType: "CUSTOMER",
                partyName: ko.observable(),
                partyDetailsFetched: ko.observable(),
                additionalDetails: ko.observable(),
                userTypeLabel: ko.observable(),
                party: {
                    value: ko.observable(),
                    displayValue: ko.observable()
                }
            };

            self.accessibleEntityArray.push({
                entityId: ko.observable(),
                entityName: ko.observable(),
                entityList: self.entityList,
                partyInfo: self.partyInfo,
                limitPackage: ko.observable(),
                childRoles: ko.observableArray([]),
                entityChange: ko.observable(false),
                entityUserLimitListLoaded: ko.observable(false),
                entityUserLimit: ko.observable(),
                userType: self.userType(),
                limitPackageName: ko.observable(),
                entitiesListLoaded: self.entitiesListLoaded,
                accessibleEntitySet: ko.observable(false),
                accessPointList: self.accessPoint(),
                isLimitPackageAttached: ko.observable(false),
                selectedAccessPoints: self.accessPointArray()
            });
        };

        const entityNameList = [];

        for (let c = 0; c < rootParams.dashboard.userData.userProfile.accessibleEntityDTOs.length; c++) {
            entityNameList[rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[c].entityId] = rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[c].entityName;
        }

        self.refresh = ko.observable(true);

        self.deleteAccessibleEntity = function (index) {
            if (self.accessibleEntityArray()[index].entityId() instanceof Array) {
                self.entityList().push({
                    entityId: self.accessibleEntityArray()[index].entityId()[0],
                    entityName: entityNameList[self.accessibleEntityArray()[index].entityId()[0]]
                });
            } else if (self.accessibleEntityArray()[index].entityId()) {
                self.entityList().push({
                    entityId: self.accessibleEntityArray()[index].entityId(),
                    entityName: entityNameList[self.accessibleEntityArray()[index].entityId()]
                });
            }

            self.accessibleEntityArray().splice(index, 1);
            self.tempArray = ko.observableArray(self.accessibleEntityArray.slice(0));
            self.accessibleEntityArray.removeAll();
            self.accessibleEntityArray(self.tempArray());
            self.refresh(false);
            self.refresh(true);
        };

        let relationship_Type;

        self.fetchUserGroupfromId = function (id) {
            for (let i = 0; i < self.userGroupValues().length; i++) {
                if (self.userGroupValues()[i].text === id) {
                    return self.userGroupValues()[i].object;
                }
            }
        };

        self.setUserGroupList = function (data) {
            if (self.selectedParentRole() === "corporateuser") {
                self.selectedUserGroupValues().forEach(function (group) {
                    if (group !== undefined) {
                        data.userGroupDTOs.push(group);
                    }
                });

                let itemFoundFlag = false;

                if (self.defaultUserGroup !== undefined && self.defaultUserGroup() !== null) {
                    for (let i = 0; i < self.selectedUserGroupValues().length; i++) {
                        if (self.selectedUserGroupValues()[i].name === self.defaultUserGroup()) {
                            itemFoundFlag = true;
                            break;
                        }
                    }

                    if (itemFoundFlag === false) {
                        self.selectedUserGroupValues.push(self.fetchUserGroupfromId(self.defaultUserGroup()));
                        data.userGroupDTOs.push(self.fetchUserGroupfromId(self.defaultUserGroup()));
                    }
                }
            }
        };

        self.setEntityRelatedFields = function (data) {
            if (self.selectedParentRole() === "corporateuser") {
                relationship_Type = "O";
            }

            if (self.selectedParentRole() === "retailuser") {
                relationship_Type = "I";
            }

            let i;

            if (self.userType() !== "administrator") {
                data.userPartyRelationshipDTOs = [];
                data.accessibleEntities = [];

                data.userPartyRelationshipDTOs.push({
                    determinantValue: Constants.currentEntity,
                    partyId: {
                        value: self.rootModelInstance().partyDetails.party.value()
                    },
                    userId: data.username,
                    relationshipType: relationship_Type
                });

                data.accessibleEntities.push({
                    entityId: Constants.currentEntity,
                    entityName: null,
                    partyName: self.rootModelInstance().partyDetails.partyName(),
                    userPartyRelationship: {
                        determinantValue: Constants.currentEntity,
                        partyId: {
                            value: self.rootModelInstance().partyDetails.party.value()
                        },
                        userId: data.username,
                        relationshipType: relationship_Type
                    }
                });

                for (i = 0; i < self.accessibleEntityArray().length; i++) {
                    data.userPartyRelationshipDTOs.push({
                        determinantValue: self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId(),
                        partyId: {
                            value: self.accessibleEntityArray()[i].partyInfo.party.value()
                        },
                        userId: data.username,
                        relationshipType: relationship_Type
                    });

                    data.accessibleEntity.push(self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId());

                    data.accessibleEntities.push({
                        entityId: self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId(),
                        entityName: self.accessibleEntityArray()[i].entityName(),
                        partyName: self.accessibleEntityArray()[i].partyInfo.partyName(),
                        userPartyRelationship: {
                            determinantValue: self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId(),
                            partyId: {
                                value: self.accessibleEntityArray()[i].partyInfo.party.value()
                            },
                            userId: data.username,
                            relationshipType: relationship_Type
                        }
                    });
                }

                data.limitPackages = [];

                let tempLimitPackageMappingDTO;

                for (i = 0; i < self.accessibleEntityArray().length; i++) {
                    tempLimitPackageMappingDTO = [];
                    self.accessibleEntityArray()[i].isLimitPackageAttached(false);

                    for (let p = 0; p < self.accessibleEntityArray()[i].limitPackage().length; p++) {
                        if (self.accessibleEntityArray()[i].limitPackage()[p].selectedLimitPackage()) {
                            tempLimitPackageMappingDTO.push({
                                limitPackage: {
                                    accessPointGroupType: self.accessibleEntityArray()[i].limitPackage()[p].isGroup ? "GROUP" : "SINGLE",
                                    accessPointValue: self.accessibleEntityArray()[i].limitPackage()[p].accessPoint,
                                    key: {
                                        id: self.accessibleEntityArray()[i].limitPackage()[p].selectedLimitPackage()
                                    }
                                }
                            });

                            self.accessibleEntityArray()[i].isLimitPackageAttached(true);
                        }
                    }

                    data.limitPackages.push({
                        targetUnit: self.accessibleEntityArray()[i].entityId(),
                        entityLimitPackageMappingDTO: tempLimitPackageMappingDTO
                    });
                }

                if (self.selectedLimitPackage()) {
                    const entityLimitPackageMappingDTO = ko.observableArray([]);

                    for (i = 0; i < self.selectedLimitPackage().length; i++) {
                        if (self.selectedLimitPackage()[i].selectedLimitPackage()) {
                            entityLimitPackageMappingDTO.push({
                                limitPackage: {
                                    accessPointGroupType: self.selectedLimitPackage()[i].isGroup ? "GROUP" : "SINGLE",
                                    accessPointValue: self.selectedLimitPackage()[i].accessPoint,
                                    key: {
                                        id: self.selectedLimitPackage()[i].selectedLimitPackage()
                                    }
                                }
                            });
                        }
                    }

                    data.limitPackages.push({
                        targetUnit: Constants.currentEntity,
                        entityLimitPackageMappingDTO: entityLimitPackageMappingDTO()
                    });
                }

                if (!data.limitPackages.length) {
                    data.limitPackages = null;
                }
            } else {
                for (i = 0; i < self.accessibleEntityArray().length; i++) {
                    data.accessibleEntity.push(self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId());

                    data.accessibleEntities.push({
                        entityId: self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId(),
                        entityName: self.accessibleEntityArray()[i].entityName(),
                        partyName: null,
                        userPartyRelationship: null
                    });
                }

                data.accessibleEntities.push({
                    entityId: Constants.currentEntity,
                    entityName: null,
                    partyName: self.rootModelInstance().partyDetails.partyName(),
                    userPartyRelationship: null
                });

                delete data.limitPackages;
                delete data.userPartyRelationshipDTOs;
            }

            data.accessibleEntity.push(Constants.currentEntity);
            data.homeEntity = Constants.currentEntity;
        };

        self.disableEntity = function () {
            if (self.accessibleEntityArray().length === 0) {
                return false;
            }

            return !self.accessibleEntityArray()[self.accessibleEntityArray().length - 1].accessibleEntitySet();
        };

        self.showToolTip = function (id, holder) {
            const p = $("#" + holder),
                position = p.position(),
                toolTipHeight = $("#" + id).outerHeight(),
                toolTipWidth = $("#" + id).outerWidth(),
                viewableOffset = $("#" + holder).offset().top - $(window).scrollTop(),
                positionTop = viewableOffset > toolTipHeight ? position.top - toolTipHeight : position.top + 50;

            if (rootParams.baseModel.large()) {
                $("#" + id).css("position", "absolute");
                $("#" + id).css("top", positionTop);
                $("#" + id).css("left", position.left - (toolTipWidth / 2));
                $("#" + id).css("display", "block");
            }
        };

        self.hideToolTip = function (id) {
            $("#" + id).css("display", "none");
        };

        self.dispose = function () {
            partyDetailsFetchedSubscription.dispose();
        };
    };
});
