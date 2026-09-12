define([
    "knockout",
    "./model",
    "jquery",
    "ojL10n!extensions/resources/nls/user-read",
    "ojs/ojswitch",
    "ojs/ojcheckboxset",
    "framework/elements/api/page-section/loader"
], function (ko, UserReadModel, $, resourceBundle) {
    "use strict";

    return function (rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.isBM = ko.observable(rootParams.dashboard.appData.segment === "ADMIN");
        self.nls = resourceBundle;
        self.userFullData = ko.observable();
        self.approverReview = ko.observable(false);
        self.roleHeader = ko.observable(self.nls.headers.role);
        self.androidDevice = ko.observable(false);
        self.androidDisabled = ko.observable(true);
        self.iOsDevice = ko.observable(false);
        self.iOsDisabled = ko.observable(true);
        self.accessPoint = ko.observableArray([]);
        self.selectedAccessPoint = ko.observableArray(["APINTERNET","APMOBRESP"]);
        self.selectedAccessType = ko.observable("INT");
        self.selectedAccessPointEntity = ko.observableArray();
        self.androidDeviceForPushNotification = ko.observable(false);
        self.androidDisabledForPushNotification = ko.observable(true);
        self.iOsDeviceForPushNotification = ko.observable(false);
        self.iOsDisabledForPushNotification = ko.observable(true);
        self.deviceDataLoadedForPushNotification = ko.observable(false);
        self.selectedSegmentCode = ko.observable();
        self.selectedSegmentName = ko.observable();
        self.userSegments = ko.observableArray();
        self.entityDetails = rootParams.entityDetails;
        self.isSegmentContainsRole = ko.observable(false);
        self.userGroupValues = ko.observableArray();
        self.showUserGroup = ko.observable(false);
        self.selectedUserGroupValues = ko.observableArray();
        self.userTypeSelectionIdle = ko.observable(false);
        self.userExtensionData = ko.observable();
        self.userTokenDataDTO = ko.observable();
        self.bmItokenService = ko.observable(null);

        /** Code visibility and lifecycle details returned by the authorized masked/reveal APIs. */
        self.hthApiPasswordCode = ko.observable();
        self.hthApiPasswordCodeVisible = ko.observable(false);
        self.hthApiPasswordCodeId = ko.observable();
        self.hthApiPasswordCodeStatus = ko.observable();
        self.hthApiPasswordCodeCanReveal = ko.observable(false);
        self.hthApiPasswordCodePurpose = ko.observable();
        self.hthApiPasswordCodeExpiryTime = ko.observable();

        self.hthApiPasswordCodeStatusText = ko.pureComputed(function () {
            const status = self.hthApiPasswordCodeStatus();

            return self.nls.hthCodeStatuses[status] || status || self.nls.fieldname.nil;
        });

        self.hthApiPasswordCodeExpiryText = ko.pureComputed(function () {
            const expiry = self.hthApiPasswordCodeExpiryTime();

            if (expiry) {
                return rootParams.baseModel.formatDate(expiry, "headerTimeFormat");
            }

            return self.hthApiPasswordCodeStatus() === "PENDING"
                ? self.nls.info.hthExpiryPendingApproval : self.nls.fieldname.nil;
        });

        /** Apply the current Code lifecycle to both the masked view and an authorized reveal. */
        self.updateHthApiPasswordCode = function (data) {
            if (data && data.status && data.status.result === "SUCCESSFUL" && data.codeId) {
                const canReveal = !!data.canReveal;

                self.hthApiPasswordCodeId(data.codeId);
                self.hthApiPasswordCodeStatus(data.codeStatus);
                self.hthApiPasswordCodePurpose(data.purpose);
                self.hthApiPasswordCodeExpiryTime(data.expiryTime);
                self.hthApiPasswordCodeCanReveal(canReveal);
                self.hthApiPasswordCode(canReveal ? data.code || data.maskedCode : null);
                self.hthApiPasswordCodeVisible(canReveal && !!data.code);
                self.userExtensionData().hthApiPasswordCodeId = data.codeId;
            }
        };

        if (rootParams.data) {
            self.username = ko.observable(rootParams.data.username());
            self.approverReview(true);
        }

        self.userTypeList = ko.observableArray([]);
        self.childRoleEnums = ko.observableArray([]);
        self.childRoleEnumsLoaded = ko.observable(false);
        self.selectedChildRole = ko.observableArray([]);
        self.showConfirmation = ko.observable(false);
        self.transactionStatus = ko.observable();
        self.transactionName = ko.observable();
        self.dataLoaded = ko.observable(false);
        self.deviceDataLoaded = ko.observable(false);
        self.homeEntityLimitPackage = ko.observable(false);
        self.isAccessPointFetched = ko.observable(false);
        self.selectedAccessPoints = ko.observableArray();
        rootParams.baseModel.registerComponent("users-update", "user-management");
        rootParams.baseModel.registerElement("confirm-screen");
        self.usernamesearched = ko.observable(rootParams.rootModel.params.usernamesearched);
        self.firstNamesearched = ko.observable(rootParams.rootModel.params.firstNamesearched);
        self.lastNamesearched = ko.observable(rootParams.rootModel.params.lastNamesearched);
        self.emailIdsearched = ko.observable(rootParams.rootModel.params.emailIdsearched);
        self.mobileNumbersearched = ko.observable(rootParams.rootModel.params.mobileNumbersearched);
        self.partyIdSearched = ko.observable(rootParams.rootModel.params.partyIdSearched);
        self.userList = ko.observable(rootParams.rootModel.params.searchedUserList);
        self.username = ko.observable(rootParams.rootModel.params.username);
        self.countries = ko.observable(rootParams.rootModel.params.countries);
        rootParams.dashboard.headerName(self.nls.headers.usermanagement);
        self.entityLimitPackages = ko.observableArray();
        self.accessPointGroup = ko.observableArray();
        self.accessPointExt = ko.observableArray();
        self.selectedExtAccessPoint = ko.observableArray([]);
        self.selectedSegmentRoles = ko.observableArray();
        self.idDocumentChecked = ko.observableArray();
        self.isAuthorizedChecked = ko.observableArray();
        self.companyActivityLog = ko.observableArray();
        self.taskCode = ko.observable();
        self.currentExtensionData = ko.observable();
        self.tokenStatus = ko.observable();
        self.isShowToken = ko.observable(false);
        self.currentExtensionLoaded = ko.observable(false);
        self.isSignerIdDeletedSection = ko.observable(false);

        function getIsItokenEnable(data) {
            if (data && data.listCustomConfigDTO && data.listCustomConfigDTO.length > 0) {
                const config = data.listCustomConfigDTO.find(item => {
                    return item.propertyId === "ENABLE_ITOKEN_MANAGEMENT";
                });

                if (config && config.propertyValue === "Y") {
                    return true;
                }
            }

            return false;
        }

        function setItokenEnable() {
            UserReadModel.getEnbleItokenManagement().done(function (data) {
                const isItokenEnable = getIsItokenEnable(data);

                if (isItokenEnable) {
                    self.isShowToken(true);
                    self.tokenStatus(self.nls.fieldname[rootParams.rootModel.params.data.itokenStatus]);
                    self.setBmItokenServie(rootParams.rootModel.params.data.userTokenDataDTO);
                }
            });
        }

        self.setBmItokenServie = function (data) {
            if (!data || !self.isBM()) {
                return null;
            }

            const itokenService = {
                linkedDevice: data.linkedDevice || "N/A",
                lastUpdatedDate: data.lastUpdatedDate ? data.lastUpdatedDate.replace(/[Tt]/g, self.nls.fieldname.emptyString).replace(/[Tt]/g, "") : "N/A",
                creationDate: data.creationDate ? data.creationDate.replace(/[Tt]/g, self.nls.fieldname.emptyString).replace(/[Tt]/g, "") : "N/A",
                itokenStatus: data.itokenStatus || "N/A",
                lastUpdatedBy: data.lastUpdatedBy || "N/A"
            };

            self.bmItokenService(itokenService);

        };

        /**
         * Code for approval mode
         */
        if(rootParams.rootModel.params && rootParams.rootModel.params.data) {

            self.approverReview(rootParams.rootModel.params.mode === "approval");
            self.taskCode(rootParams.rootModel.params.taskCode);

            /**
             * Make mobile number empty if user is migrated and have dummy mobile number
             */
            if (rootParams.rootModel.params.data.migtationStatus && (rootParams.rootModel.params.data.migtationStatus === "N" || rootParams.rootModel.params.data.migtationStatus === "Y" || rootParams.rootModel.params.data.migtationStatus === "S") && rootParams.rootModel.params.data.userDTO.mobileNumber === "00000000") {
                rootParams.rootModel.params.data.userDTO.mobileNumber = "";
            }

            /**
             * Make email address empty if user is migrated and have dummy mobile number
             */
            if (rootParams.rootModel.params.data.migtationStatus && (rootParams.rootModel.params.data.migtationStatus === "N" || rootParams.rootModel.params.data.migtationStatus === "Y" || rootParams.rootModel.params.data.migtationStatus === "S") && rootParams.rootModel.params.data.userDTO.emailId === "dummy@dummy.dummy") {
                rootParams.rootModel.params.data.userDTO.emailId = "";
            }

            self.userFullData(rootParams.rootModel.params.data.userDTO);
            self.userExtensionData(rootParams.rootModel.params.data);
            self.idDocumentChecked(rootParams.rootModel.params.data.idDocSubmitted ? ["true"] : ["false"]);
            self.isAuthorizedChecked(rootParams.rootModel.params.data.isAuthorisedPerson ? ["true"] : ["false"]);
            self.companyActivityLog(rootParams.rootModel.params.data.companyActivityLog ? ["true"] : ["false"]);

            if (rootParams.rootModel.params.data.itokenStatus) {
                setItokenEnable();
            }

            if(self.userExtensionData().dictionaryArray !== undefined){
                for(let i = 0; i < self.userExtensionData().dictionaryArray.length; i++) {
                    for(let j = 0; j < self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray.length; j++) {
                        if(self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray[j].name === "signerDeleteFlag") {
                            self.isSignerIdDeletedSection(self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray[j].value === "true");
                        }
                    }
                }
            }

            if(rootParams.rootModel.params.data.userDTO.applicationRoles !== null) {
                UserReadModel.fetchChildRole(self.userFullData().userType.enterpriseRoleId).done(function (data) {
                    for(let i = 0; i < rootParams.rootModel.params.data.userDTO.applicationRoles.length; i++) {
                        for(let j = 0; j < data.applicationRoleDTOs.length; j++) {
                            if(rootParams.rootModel.params.data.userDTO.applicationRoles[i] === data.applicationRoleDTOs[j].applicationRoleId) {
                                rootParams.rootModel.params.data.userDTO.applicationRoles[i] = data.applicationRoleDTOs[j].applicationRoleName;
                                break;
                            }
                        }
                    }

                    self.selectedChildRole(rootParams.rootModel.params.data.userDTO.applicationRoles);
                    self.childRoleEnums(rootParams.rootModel.params.data.userDTO.applicationRoles);
                    self.childRoleEnumsLoaded(true);
                });
            }

            if(self.userFullData().userGroupDTOs !== null) {
                self.userGroupValues(self.userFullData().userGroupDTOs);

                self.userFullData().userGroupDTOs.forEach(function (selectedGroup) {
                    if(self.taskCode() === "MT_N_CUS"){
                        self.selectedUserGroupValues.push(selectedGroup.id);
                    }else{
                        for(let i = 0; i < selectedGroup.users.length;i++){
                            if(selectedGroup.users[i].userId === self.userExtensionData().userID){
                                self.selectedUserGroupValues.push(selectedGroup.id);
                            }
                        }
                    }
                });

                self.showUserGroup(true);
            }

            if(self.taskCode() === "MT_N_UUS") {
                const oldExtensionData = {};

                if (self.userExtensionData().dictionaryArray !== undefined) {
                    if (self.userExtensionData().dictionaryArray[0].nameValuePairDTOArray.length > 1) {
                        for (let i = 0; i < self.userExtensionData().dictionaryArray.length; i++) {
                            for (let j = 0; j < self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray.length; j++) {
                                oldExtensionData[self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray[j].name] = self.userExtensionData().dictionaryArray[i].nameValuePairDTOArray[j].value;
                            }
                        }

                        self.currentExtensionData(oldExtensionData);
                        self.currentExtensionLoaded(true);
                    } else {
                        Promise.all([UserReadModel.readUserExtension(rootParams.rootModel.params.data.userID), UserReadModel.getEnbleItokenManagement()]).then(function (data) {
                            const readUserExtensionData = data[0], getEnbleItokenManagementData = data[1];

                            self.currentExtensionData(readUserExtensionData.userExtensionDataDTO);
                            self.userTokenDataDTO(readUserExtensionData.userTokenDataDTO || null);
                            self.setBmItokenServie(self.userTokenDataDTO());
                            self.currentExtensionLoaded(true);

                             const isItokenEnable = getIsItokenEnable(getEnbleItokenManagementData);

                            if (readUserExtensionData.userTokenDataDTO && readUserExtensionData.userTokenDataDTO.itokenStatus && isItokenEnable) {
                                self.isShowToken(true);
                                self.tokenStatus(self.nls.fieldname[readUserExtensionData.userTokenDataDTO.itokenStatus]);

                            }
                        });
                    }

                } else {
                    Promise.all([UserReadModel.readUserExtension(rootParams.rootModel.params.data.userID), UserReadModel.getEnbleItokenManagement()]).then(function (data) {
                        const readUserExtensionData = data[0], getEnbleItokenManagementData = data[1];

                        self.currentExtensionData(data.userExtensionDataDTO);
                        self.userTokenDataDTO(data.userTokenDataDTO || null);
                        self.setBmItokenServie(self.userTokenDataDTO());
                        self.currentExtensionLoaded(true);

                        const isItokenEnable = getIsItokenEnable(getEnbleItokenManagementData);

                        if (readUserExtensionData.userTokenDataDTO && readUserExtensionData.userTokenDataDTO.itokenStatus && isItokenEnable) {
                            self.isShowToken(true);
                            self.tokenStatus(self.nls.fieldname[data.userTokenDataDTO.itokenStatus]);

                        }
                    });
                }

            }

            self.dataLoaded(true);
        }

        const retailUser = self.nls.fieldname.retailUser,
            adminUser = self.nls.fieldname.administrator,
            partyId = {};

        self.isCorpAdmin = false;
        partyId.value = rootParams.dashboard.userData.userProfile.partyId.value;
        partyId.displayValue = rootParams.dashboard.userData.userProfile.partyId.displayValue;

        if (partyId.value !== null && partyId.value.trim() !== "") {
            self.isCorpAdmin = true;
        }

        /**
         * @function getDocumentTypeResource
         * This function gets translated value from resource bundle
         * @param {*} value holds selected document type
         * @returns value from resource bundle
         */
         self.getDocumentTypeResource = function(value) {
            switch(value) {
                case "Hong Kong Identity Card":
                    return self.nls.fieldname.hkId;
                case "Passport":
                    return self.nls.fieldname.passport;
                case "Chinese Travel Permit":
                    return self.nls.fieldname.chineseTravelPermit;
                case "ID":
                    return self.nls.fieldname.id;
                default:
                    return value;
            }
        };

        /**
         * @function getPinStatusResource
         * This function gets translated value from resource bundle
         * @param {*} value holds selected document type
         * @returns value from resource bundle
         */
         self.getPinStatusResource = function(value) {
            switch(value) {
                case "Normal":
                    return self.nls.fieldname.normal;
                case "Inactive":
                    return self.nls.fieldname.inactive;
                case "Being Reset":
                    return self.nls.fieldname.beingReset;
                default:
                    return value;
            }
          };

        /**
         * @function getHoldStatusResource
         * This function gets translated value from resource bundle
         * @param {*} value holds selected document type
         * @returns value from resource bundle
         */
         self.getHoldStatusResource = function(value) {
            switch(value) {
                case "Unhold":
                    return self.nls.fieldname.unhold;
                case "No Activity Allowed":
                    return self.nls.fieldname.noActivityAllowed;
                default:
                    return value;
            }
         };

        /**
         * @function getHoldReasonResource
         * This function gets translated value from resource bundle
         * @param {*} value holds selected document type
         * @returns value from resource bundle
         */
         self.getHoldReasonResource = function(value) {
            switch(value) {
                case "NIL":
                    return self.nls.fieldname.nil;
                case "Retry Count Exceeded":
                    return self.nls.fieldname.retryExceeded;
                case "Requested by Customer":
                    return self.nls.fieldname.reqByCustomer;
                case "Corporate Customer":
                    return self.nls.fieldname.corpCustomer;
                case "Others":
                    return self.nls.fieldname.others;
                default:
                    return value;
            }
         };

        self.limitPackageSearch = function (data, item) {
            let temp;

            for (let h = 0; h < data.entityLimitPackageMappingDTO.length; h++) {
                if (data.entityLimitPackageMappingDTO[h].limitPackage.accessPointValue === item.value) {
                    temp = {
                        limitPackage: ko.observable(data.entityLimitPackageMappingDTO[h].limitPackage.key.id),
                        accessPointDescription: item.text
                    };

                    break;
                }
            }

            return temp;
        };

        self.setEntityLimitPackages = function () {
            for (let f = 0; f < self.userFullData().limitPackages.length; f++) {
                const tempArray = [];
                let data;

                for (let b = 0; b < self.accessPoint().length; b++) {
                    data = self.limitPackageSearch(self.userFullData().limitPackages[f], self.accessPoint()[b]);

                    if (data) {
                        tempArray.push(data);
                    }
                }

                for (let b = 0; b < self.accessPointGroup().length; b++) {
                    data = self.limitPackageSearch(self.userFullData().limitPackages[f], self.accessPointGroup()[b]);

                    if (data) {
                        tempArray.push(data);
                    }
                }

                const glob = {
                    text: "Global",
                    value: "GLOBAL"
                };

                data = self.limitPackageSearch(self.userFullData().limitPackages[f], glob);

                if (data) {
                    tempArray.push(data);
                }

                self.entityLimitPackages().push({
                    targetUnit: self.userFullData().limitPackages[f].targetUnit,
                    entityLimitPackageMappingDTO: tempArray
                });
            }
        };

        self.setHomeEntityParty = function () {
            for (let f = 0; f < self.userFullData().userPartyRelationshipDTOs.length; f++) {
                if (self.userFullData().userPartyRelationshipDTOs[f].determinantValue === self.userFullData().homeEntity) {
                    self.userFullData().partyId = self.userFullData().userPartyRelationshipDTOs[f].partyId;
                }
            }

            for (let g = 0; g < self.userFullData().accessibleEntities.length; g++) {
                if (self.userFullData().accessibleEntities[g].entityId === self.userFullData().homeEntity) {
                    self.userFullData().partyName = self.userFullData().accessibleEntities[g].partyName;
                }
            }
        };

        const searchParameters = {
            accessType: "INT"
        };

        Promise.all([
            UserReadModel.fetchAccess(searchParameters),
            UserReadModel.listAccessPointGroup()
        ]).then(function (response) {
            const data = response[0],
                data1 = response[1];

            for (let i = 0; i < data1.accessPointGroupListDTO.length; i++) {
                self.accessPointGroup.push({
                    text: data1.accessPointGroupListDTO[i].description,
                    value: data1.accessPointGroupListDTO[i].accessPointGroupId
                });
            }

            self.accessPointGroup().sort(function (a, b) {
                if (a.value < b.value) {
                    return -1;
                }

                if (a.value > b.value) {
                    return 1;
                }

                return 0;
            });

            for (let i = 0; i < data.accessPointListDTO.length; i++) {
                if ((data.accessPointListDTO[i].type === "INT" && data.accessPointListDTO[i].id === "APINTERNET") || (data.accessPointListDTO[i].type === "INT" && data.accessPointListDTO[i].id === "APMOBRESP")) {
                        self.accessPoint().push({
                            text: data.accessPointListDTO[i].description,
                            value: data.accessPointListDTO[i].id
                        });

                        self.accessPoint().sort(function (a, b) {
                            if (a.value < b.value) {
                                return -1;
                            }

                            if (a.value > b.value) {
                                return 1;
                            }

                            return 0;
                        });
                } else {
                    self.accessPointExt().push({
                        text: data.accessPointListDTO[i].description,
                        value: data.accessPointListDTO[i].id
                    });
                }
            }

            self.isAccessPointFetched(true);
        });

        Promise.all([
            UserReadModel.readUser(self.username()),
            UserReadModel.getEnterpriseRoles(),
            UserReadModel.readUserExtension(self.username()),
            UserReadModel.getEnbleItokenManagement()
        ]).then(function (response) {
            const data = response[0],
                dataEntRole = response[1],
                extensionData = response[2],
                itokenData = response[3];

            self.userTypeList = dataEntRole.enterpriseRoleDTOs;
            self.userFullData(data.userDTO);
            self.userExtensionData(extensionData.userExtensionDataDTO);
            self.userTokenDataDTO(extensionData.userTokenDataDTO || null);
            self.setBmItokenServie(self.userTokenDataDTO());
            self.idDocumentChecked(extensionData.userExtensionDataDTO.idDocSubmitted ? ["true"] : ["false"]);
            self.isAuthorizedChecked(extensionData.userExtensionDataDTO.isAuthorisedPerson ? ["true"] : ["false"]);
            self.companyActivityLog(extensionData.userExtensionDataDTO.companyActivityLog ? ["true"] : ["false"]);

            const isItokenEnable = getIsItokenEnable(itokenData);

            if(extensionData.userTokenDataDTO!==undefined && extensionData.userTokenDataDTO.itokenStatus!==undefined && isItokenEnable){
                self.isShowToken(true);
                self.tokenStatus(self.nls.fieldname[extensionData.userTokenDataDTO.itokenStatus]);
            }

            for (let c = 0; c < self.countries().length; c++) {
                if (self.userFullData().address.country === self.countries()[c].value) {
                    self.countryName = self.countries()[c].text;
                }
            }

            let i;

            ko.utils.arrayForEach(self.userTypeList, function (item) {
                for (i = 0; i < self.userFullData().userGroups.length; i++) {
                    if (item.enterpriseRoleId.toLowerCase() === self.userFullData().userGroups[i].toLowerCase()) {
                        self.userFullData().userType = item;

                        const index = self.userFullData().userGroups.indexOf(self.userFullData().userGroups[i]);

                        self.userFullData().userGroups.splice(index, 1);
                    }
                }
            });

            for (i = 0; i < data.userDTO.userAccessPointRelationshipList.length; i++) {
                for (let j = 0; j < self.accessPointExt().length; j++) {
                    if (data.userDTO.userAccessPointRelationshipList[i].accessPointId === self.accessPointExt()[j].value) {
                        self.selectedExtAccessPoint.push(data.userDTO.userAccessPointRelationshipList[i]);
                    }
                }
            }

            for (i = 0; i < data.userDTO.userAccessPointRelationshipList.length; i++) {
                if (self.userFullData().homeEntity === data.userDTO.userAccessPointRelationshipList[i].determinantValue) {
                    if (data.userDTO.userAccessPointRelationshipList[i].status === true) {
                        self.selectedAccessPoint.push(data.userDTO.userAccessPointRelationshipList[i].accessPointId);
                    }
                }
            }

            if (data.userDTO.accessibleEntity.length > 1) {
                for (let k = 0; k < data.userDTO.accessibleEntity.length; k++) {
                    data.userDTO.accessibleEntities[k].selectedAccessPoints = [];

                    for (let m = 0; m < self.userFullData().userAccessPointRelationshipList.length; m++) {
                        if (data.userDTO.accessibleEntity[k] !== self.userFullData().homeEntity && data.userDTO.accessibleEntity[k] === self.userFullData().userAccessPointRelationshipList[m].determinantValue && data.userDTO.userAccessPointRelationshipList[m].status === true) {
                            for (let n = 0; n < self.accessPoint().length; n++) {
                                if (self.userFullData().userAccessPointRelationshipList[m].accessPointId === self.accessPoint()[n].value) {
                                    self.selectedAccessPointEntity.push(self.userFullData().userAccessPointRelationshipList[m].accessPointId);
                                    data.userDTO.accessibleEntities[k].selectedAccessPoints.push(self.userFullData().userAccessPointRelationshipList[m].accessPointId);
                                }
                            }
                        }
                    }
                }
            }

            for (i = 0; i < self.userFullData().limitPackages.length; i++) {
                if (self.userFullData().limitPackages[i].targetUnit === self.userFullData().homeEntity && self.userFullData().limitPackages[i].entityLimitPackageMappingDTO.length) {
                    self.homeEntityLimitPackage(true);
                }
            }

            UserReadModel.fetchChildRole(self.userFullData().userType.enterpriseRoleId).done(function (data) {
                if(!self.userFullData().updatable)
                {
                    self.childRoleEnums(self.userFullData().applicationRoles);
                }
                else{
                    self.childRoleEnums(data.applicationRoleDTOs);
                }

                self.childRoleEnumsLoaded(true);
                self.selectedChildRole(self.userFullData().applicationRoles);
            });

            if (self.userFullData().userType.enterpriseRoleName === "Corporate User") {
                UserReadModel.fetchUserGroupList(self.userFullData().partyId.value).then(function (userGroups) {
                    userGroups.userGroupDTOs.forEach(function (group) {
                        self.userGroupValues.push({
                            text: group.name,
                            value: group.id,
                            object: group
                        });

                    });

                    self.showUserGroup(true);

                    if (self.userFullData().userGroupDTOs) {
                        self.userFullData().userGroupDTOs.forEach(function (selectedGroup) {
                            self.selectedUserGroupValues.push(selectedGroup.id);

                        });
                    }

                });
            }

            if (self.userFullData().userType.enterpriseRoleName === retailUser) {
                self.userFullData().phoneNumber = self.userFullData().homePhone;
            }

            if (self.userFullData().userType.enterpriseRoleName === adminUser) {
                self.roleHeader(self.nls.headers.role);
            } else {
                self.roleHeader(self.nls.headers.limitrole);
            }

            self.setHomeEntityParty();
            self.setEntityLimitPackages();

            if (self.userFullData().userType.enterpriseRoleName === retailUser) {
                const searchParameter = {
                    selectedUser: self.userFullData().userType.enterpriseRoleId
                };

                UserReadModel.fetchUserSegments(searchParameter).done(function (data) {
                    self.userSegments([]);

                    for (let j = 0; j < data.segmentdtos.length; j++) {
                        if (data.segmentdtos[j].code === self.userFullData().segmentCode) {
                            self.selectedSegmentName(data.segmentdtos[j].name);
                            self.selectedSegmentCode(data.segmentdtos[j].code);

                            if (data.segmentdtos[j].roles !== undefined) {
                                self.selectedSegmentRoles(data.segmentdtos[j].roles);
                                self.isSegmentContainsRole(true);
                            }
                        }

                        self.userSegments().push({
                            text: data.segmentdtos[j].name,
                            value: data.segmentdtos[j].code
                        });
                    }
                });
            }

            if (self.userExtensionData() && self.userExtensionData().userChannelType === "HTH") {
                UserReadModel.getHthApiPasswordCodeMasked(
                    self.userFullData().partyId.value,
                    self.userFullData().username
                ).done(self.updateHthApiPasswordCode);
            }

            self.dataLoaded(true);
        });

        self.cancel = function () {
            rootParams.dashboard.switchModule(true);
        };

        self.disableEditButton = ko.pureComputed(function() {
            if(self.userFullData().deleteStatus) {
                return true;
            }

            if(self.isCorpAdmin && self.userExtensionData().isAuthorisedPerson) {
                return true;
            }

            if(self.isCorpAdmin && (self.userExtensionData().isAuthorisedPerson === false)) {
                return false;
            }

            return false;
        });

        /**
         * @function successHandler
         * Success Handler for Edit user 2FA
         */
        self.successHandler = function() {
            rootParams.dashboard.loadComponent("users-update", {
                androidDevice: self.androidDevice(),
                iOsDevice: self.iOsDevice(),
                androidDeviceForPushNotification: self.androidDeviceForPushNotification(),
                iOsDeviceForPushNotification: self.iOsDeviceForPushNotification(),
                androidDisabled: self.androidDisabled(),
                userFullData: self.userFullData(),
                userExtensionData: self.userExtensionData(),
                selectedAccessPoint: self.selectedAccessPoint(),
                iOsDisabled: self.iOsDisabled(),
                androidDisabledForPushNotification: self.androidDisabledForPushNotification(),
                iOsDisabledForPushNotification: self.iOsDisabledForPushNotification(),
                username: self.username(),
                countries: self.countries(),
                salutationList: self.params.salutationList,
                childRoleEnumsLoaded: self.childRoleEnumsLoaded(),
                isCountryFetched: self.params.isCountryFetched,
                childRoleEnums: self.childRoleEnums(),
                selectedSegmentCode: self.selectedSegmentCode(),
                selectedExtAccessPoint: self.selectedExtAccessPoint(),
                userSegments: self.userSegments(),
                selectedSegmentName: self.selectedSegmentName(),
                showToolTip: self.showToolTip,
                hideToolTip: self.hideToolTip,
                selectedUserGroupValues: self.selectedUserGroupValues(),
                userGroupValues: self.userGroupValues(),
                showUserGroup: self.showUserGroup(),
                isCorpAdmin : self.isCorpAdmin,
                userTokenDataDTO: self.userTokenDataDTO()
            });
        };

        self.edit = function () {

            if(self.isCorpAdmin) {
                UserReadModel.checkTFA(self.successHandler);
            } else {
                self.successHandler();
            }
        };

        self.back = function () {
            self.userTypeSelectionIdle(false);
            rootParams.dashboard.loadComponent("users", self);
        };

        self.showModalWindow = function () {
            $("#resetPassword").trigger("openModal");
        };

        self.hideModalWindow = function () {
            $("#resetPassword").trigger("closeModal");
        };

        self.transactionName(self.nls.fieldname.transactionName);

        self.resetPassword = function () {
            UserReadModel.resetPassword(self.userFullData().username).done(function (data, status, jqXhr) {
                $("#resetPassword").hide().trigger("closeModal");
                self.transactionStatus(data.status);
                self.showConfirmation(true);

                rootParams.dashboard.loadComponent("confirm-screen", {
                    jqXHR: jqXhr,
                    transactionName: self.transactionName()
                });
            });
        };

        self.downloadUserDetails = function () {
            UserReadModel.downloadUserDetails(self.username());
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
                $("#" + id).css("left", (position.left - toolTipWidth) / 2);
                $("#" + id).css("display", "block");
            }
        };

        self.hideToolTip = function (id) {
            $("#" + id).css("display", "none");
        };

        self.getBypassStatus = function() {
            if (self.userExtensionData().bypassFlag === "Y") {
                return self.userExtensionData().bypassExpiryTime ? rootParams.baseModel.format(self.nls.fieldname.loginPINResetCodeEnabled, {date: rootParams.baseModel.formatDate(self.userExtensionData().bypassExpiryTime, "headerTimeFormat")}) : self.nls.fieldname.enable;
            }

            return self.nls.fieldname.loginPINResetCodeDisabled;
        };

        /** Each reveal checks authorization and refreshes lifecycle details; hiding is local. */
        self.toggleHthApiPasswordCodeVisible = function () {
            if (!self.hthApiPasswordCodeCanReveal()) {
                return;
            }

            if (!self.hthApiPasswordCodeVisible() && self.hthApiPasswordCodeId()) {
                UserReadModel.revealHthApiPasswordCode(self.hthApiPasswordCodeId()).done(self.updateHthApiPasswordCode).fail(function (error) {
                    const errorMsg = error && error.status && error.status.message && error.status.message.message
                        ? error.status.message.message
                        : "Failed to reveal HTH API Password Code";

                    rootParams.baseModel.showMessages(null, [errorMsg], "ERROR");
                });
            } else {
                self.hthApiPasswordCodeVisible(!self.hthApiPasswordCodeVisible());
            }
        };
    };
});
