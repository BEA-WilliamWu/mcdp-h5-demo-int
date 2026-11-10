define([
    "ojs/ojcore",
    "knockout",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/audit",
    "ojs/ojknockout-validation",
    "ojs/ojinputtext",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingcontrol",
    "ojs/ojpagingtabledatasource",
    "ojs/ojtable",
    "ojs/ojselectcombobox",
    "ojs/ojdatetimepicker",
    "ojs/ojbutton",
    "ojs/ojcheckboxset",
    "ojs/ojvalidationgroup",
    "framework/elements/api/page-section/loader"
], function (oj, ko, $, AuditLogModel, resourceBundle) {
    "use strict";

    return function (rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.nls = resourceBundle;
        rootParams.baseModel.registerElement("action-header");

        $("label").parent("div").addClass("paddingModifierForLabels");

        if (rootParams.rootModel.params && rootParams.rootModel.params.dateType !== undefined) {
            self.dateType = rootParams.rootModel.params.dateType;
        } else {
            self.dateType = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.fromDateTime !== undefined) {
            self.fromDateTime = rootParams.rootModel.params.fromDateTime;
        } else {
            self.fromDateTime = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.toDateTime !== undefined) {
            self.toDateTime = rootParams.rootModel.params.toDateTime;
        } else {
            self.toDateTime = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.fromDate !== undefined) {
            self.fromDate = rootParams.rootModel.params.fromDate;
        } else {
            self.fromDate = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.toDate !== undefined) {
            self.toDate = rootParams.rootModel.params.toDate;
        } else {
            self.toDate = ko.observable();
        }

        self.userid = ko.observable();

        if (rootParams.rootModel.params && rootParams.rootModel.params.partyid !== undefined) {
            self.partyId = rootParams.rootModel.params.partyid;
        } else {
            self.partyId = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.action !== undefined) {
            self.action = rootParams.rootModel.params.action;
        } else {
            self.action = ko.observable([]);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.status !== undefined) {
            self.status = rootParams.rootModel.params.status;
        } else {
            self.status = ko.observable([]);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.dateRange !== undefined) {
            self.dateRange = rootParams.rootModel.params.dateRange;
        } else {
            self.dateRange = ko.observable(false);
        }

        self.partyName = ko.observable();
        self.validationTracker = ko.observable();
        self.userValidationTracker = ko.observable();
        self.partyValidationTracker = ko.observable();
        self.dateRangeTracker = ko.observable();

        if (rootParams.rootModel.params && rootParams.rootModel.params.moreSearchOptions !== undefined) {
            self.moreSearchOptions = rootParams.rootModel.params.moreSearchOptions;
        } else {
            self.moreSearchOptions = ko.observable(false);
        }

        self.indirectedParty = ko.observable();
        self.loadSearchData = ko.observable(false);
        self.searchedUserList = ko.observableArray();
        self.userList = ko.observableArray();
        self.searchedPartyList = ko.observableArray();
        self.partyList = ko.observableArray();

        if (rootParams.rootModel.params && rootParams.rootModel.params.userType !== undefined) {
            self.userType = rootParams.rootModel.params.userType;
        } else {
            self.userType = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.username !== undefined) {
            self.username = rootParams.rootModel.params.username;
        } else {
            self.username = ko.observable();
        }

        self.usernamesearched = ko.observable();
        self.firstName = ko.observable();
        self.datasource = ko.observableArray();
        self.userListDetailsDataSource = ko.observableArray();
        self.partyNamesearched = ko.observable();
        self.userTypeEnums = ko.observableArray();
        self.loginUserType = ko.observable();
        self.userTypeEnumsLoaded = ko.observable(false);
        self.adminLogin = ko.observable(false);
        self.activityEnum = ko.observableArray();
        self.activityEnumLoaded = ko.observable(false);
        self.transactionEnum = ko.observableArray();
        self.transactionEnumCode = ko.observableArray();
        self.taskCode = ko.observable();
        self.highRiskTransaction = ko.observable([]);
        self.codesArray = ko.observableArray([]);
        self.transactionEnumLoaded = ko.observable(false);
        self.merchantUser = ko.observable(false);
        self.isBM = ko.observable(rootParams.dashboard.appData.segment === "ADMIN");

        if(rootParams.dashboard.userData.userProfile.roles.indexOf("Merchant_TL") !== -1){
            self.merchantUser(true);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.selectedActivity !== undefined) {
            self.selectedActivity = rootParams.rootModel.params.selectedActivity;
        } else {
            self.selectedActivity = ko.observable([]);
        }

        self.showList = ko.observable(false);

        if (rootParams.rootModel.params && rootParams.rootModel.params.referenceNo !== undefined) {
            self.referenceNo = rootParams.rootModel.params.referenceNo;
        } else {
            self.referenceNo = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.loadSearchedAudits !== undefined) {
            self.loadSearchedAudits = rootParams.rootModel.params.loadSearchedAudits;
        } else {
            self.loadSearchedAudits = ko.observable(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.auditResult !== undefined) {
            self.auditResult = rootParams.rootModel.params.auditResult;
        } else {
            self.auditResult = ko.observable();
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.dateFlag !== undefined) {
            self.dateFlag = rootParams.rootModel.params.dateFlag;
        } else {
            self.dateFlag = ko.observable(false);
        }

        self.actionHeaderheading = ko.observable();
        self.partyToSearch = ko.observable();
        self.indirectedPartyValue = ko.observable();
        rootParams.baseModel.registerComponent("audit-log-search-results", "audit");
        rootParams.dashboard.headerName(self.nls.header.auditlogmaintenance);

        if(self.merchantUser()){
            AuditLogModel.fetchDayOneConfig("MERCHANT_ACCOUNT_ID").then(function (data) {
                if (data && data.listCustomConfigDTO && data.listCustomConfigDTO.length > 0) {
                    rootParams.dashboard.userData.userProfile.partyId.value = data.listCustomConfigDTO[0].propertyValue;
                    rootParams.dashboard.userData.userProfile.partyId.displayValue = data.listCustomConfigDTO[0].propertyValue;

                }
            });
        }

        const partyId = {};

        partyId.value = rootParams.dashboard.userData.userProfile.partyId.value;
        partyId.displayValue = rootParams.dashboard.userData.userProfile.partyId.displayValue;

        if (partyId.value) {
            self.isCorpAdmin = true;
        } else {
            self.isCorpAdmin = false;
        }

        self.formatDateTime = function (date, index, dateFlag) {
            if (self.dateRange() === false) {
                let time = date.toTimeString();

                time = time.substring(0, time.indexOf(""));
                date = oj.IntlConverterUtils.dateToLocalIso(date);

                if (dateFlag) {
                    if (index === "today" || index === "l3d") {
                        date = date.substring(0, date.indexOf("T"));
                        date = date + time;
                    } else {
                        date = date.substring(0, date.indexOf("T"));
                        date = date + "000000";
                    }
                } else {
                    date = date.substring(0, date.indexOf("T"));
                    date = date + "235959";
                }
            }

            date = date.replace(/T|-|:/g, "");

            return date;
        };

        if (rootParams.rootModel.params && rootParams.rootModel.params.selectedUserType !== undefined) {
            self.selectedUserType = rootParams.rootModel.params.selectedUserType;
        } else {
            self.selectedUserType = ko.observable([]);
        }

        AuditLogModel.highRiskTransactionList().done(function (data) {

            ko.utils.arrayForEach(data.highRiskTransactionDTOList, function (item) {
                self.transactionEnum.push({
                    text: item.taskName
                });

                self.transactionEnumCode.push(item);
            });

            self.transactionEnumLoaded(true);
        });

        self.searchAudit = function () {
            if (!rootParams.baseModel.showComponentValidationErrors(self.validationTracker())) {
                return;
            }

            self.loadSearchedAudits(false);

            if (self.dateRange()) {
                self.fromDateTime(self.formatDateTime(self.fromDate(), true));
                self.toDateTime(self.formatDateTime(self.toDate(), false));
            }

            if (self.isCorpAdmin) {
                self.partyToSearch(self.indirectedParty() || rootParams.dashboard.userData.userProfile.partyId.value);
            } else {
                self.partyToSearch(self.indirectedPartyValue());
            }

            //TaskCode Implementation Starts

            self.taskCode("");
            self.codesArray.removeAll();

            if(self.highRiskTransaction().length > 0){
                for(let j = 0; j < self.highRiskTransaction().length; j++){
                    for(let i = 0; i < self.transactionEnumCode().length; i++){
                        if(self.transactionEnumCode()[i].taskName === String(self.highRiskTransaction()[j])){
                            self.codesArray().push(self.transactionEnumCode()[i].taskCode);
                        }
                    }
                }
            }

            if(self.selectedActivity().length > 0){
                for(let j = 0; j < self.selectedActivity().length; j++){
                    for(let i = 0; i < self.activityEnum().length; i++){
                        if(self.activityEnum()[i].name === String(self.selectedActivity()[j])){
                            self.codesArray().push(self.activityEnum()[i].id);
                        }
                    }
                }
            }

            self.taskCode(self.codesArray().toString());
            //TaskCode Implementation Ends

            let actionArr = [];

            if(self.action().includes("Created")){
                actionArr = self.action();
                actionArr.push("Edited");
                actionArr.push("Deleted");
            }else{
                actionArr = self.action();
            }

            if(self.merchantUser()){
                AuditLogModel.searchAudit(rootParams.dashboard.userData.userProfile.userName, self.fromDateTime(), self.toDateTime(), self.taskCode(), self.partyToSearch(), actionArr, self.status(), self.referenceNo(), self.selectedUserType()).done(function (data) {
                    self.loadSearchedAudits(true);

                    data.auditList = data.auditList.sort((a, b) => {
                        if (new Date(a.startTime) > new Date(b.startTime)) {
                            return -1;
                        }

                        return 0;
                    });

                    self.auditResult(data.auditList);
                });
        }else{
            AuditLogModel.searchAudit(self.username() ? self.username().toUpperCase() : self.username(), self.fromDateTime(), self.toDateTime(), self.taskCode(), self.partyToSearch(), actionArr, self.status(), self.referenceNo(), self.selectedUserType()).done(function (data) {
                self.loadSearchedAudits(true);

                data.auditList = data.auditList.sort((a, b) => {
                    if (new Date(a.startTime) > new Date(b.startTime)) {
                        return -1;
                    }

                    return 0;
                });

                self.auditResult(data.auditList);
            });
        }

            self.indirectedPartyValue("");
        };

        if (rootParams.rootModel.params && rootParams.rootModel.params.backFlag === true) {
            self.searchAudit();
        }

        if (self.isCorpAdmin) {
            self.indirectedParty(rootParams.dashboard.userData.userProfile.partyId.value);

            AuditLogModel.fetchPreferenceForParty(self.indirectedParty()).done(function (data) {
                if (data.partyPreferencesDTOs) {
                    self.partyId(data.partyPreferencesDTOs.party.displayValue);
                }
            });

            self.selectedUserType(["corporateuser"]);
        }

        self.actionHeaderheading(self.nls.header.auditLog);

        AuditLogModel.fetchUserGroupOptions().done(function (data) {
            ko.utils.arrayForEach(data.enterpriseRoleDTOs, function (item) {
                self.userTypeEnums.push(item);
            });

            self.userTypeEnumsLoaded(true);
        });

        AuditLogModel.fetchActivities().done(function (data) {
            ko.utils.arrayForEach(data.taskList, function (item) {
                if (item.type !== undefined){
                    // Only the three CM HTH access tasks supplement the existing BM filter.
                    const roles = rootParams.dashboard.userData.userProfile.roles, isBM = self.isBM(), isAdmin = roles.some(role => role.toLowerCase() === "administrator"),
                        isHthUserAccess = ["UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL"].indexOf(item.id) !== -1;

                    if ((!isBM && !isAdmin && (item.type !== "ADMINISTRATION" || isHthUserAccess)) || (isBM && isAdmin)) {
                        self.activityEnum.push(item);
                    }
                }
            });

            self.activityEnumLoaded(true);
        });

        if (rootParams.dashboard.userData.userProfile.roles.indexOf("administrator") !== -1) {
            self.adminLogin(true);
        } else if (rootParams.dashboard.userData.userProfile.roles.indexOf("Administrator") !== -1) {
            self.adminLogin(true);
        } else {
            self.adminLogin(false);

            ko.utils.arrayForEach(self.userTypeEnums, function (item) {
                for (let i = 0; i < rootParams.dashboard.userData.userProfile.roles.length; i++) {
                    if (rootParams.dashboard.userData.userProfile.roles[i] === item.enterpriseRoleId) {
                        self.selectedUserType().push(item.enterpriseRoleId);
                    }
                }
            });
        }

        self.showMoreSearchOptions = function () {
            self.moreSearchOptions(!self.moreSearchOptions());
        };

        self.resetForm = function () {
            self.username("reset");
            self.fromDateTime("reset");
            self.fromDate("reset");
            self.toDateTime("reset");
            self.toDate("reset");
            self.referenceNo("reset");
            self.username("");
            self.highRiskTransaction([]);
            self.taskCode("");

            if (self.dateRange()) {
                self.fromDateTime("");
                self.toDateTime("");
            } else {
                self.fromDateTime(self.formatDateTime(rootParams.baseModel.getDate(), "today", true));
                self.toDateTime(self.formatDateTime(rootParams.baseModel.getDate(), "today", false));
            }

            self.fromDate("");
            self.toDate("");
            self.referenceNo("");

            if (self.isCorpAdmin === false) {
                self.partyId("reset");
                self.partyId("");
                self.selectedUserType([]);
            }

            self.dateType("today");
            self.dateRange(false);
            self.selectedActivity([]);
            self.status([]);
            self.action([]);
            self.loadSearchData(false);
            self.showList(false);
            self.loadSearchedAudits(false);
        };

        self.showUserSearch = function () {
            self.resetDialogue();
            self.showUserData(true);
            self.userList([]);
            $("#userSearch").trigger("openModal");
        };

        self.showPartySearch = function () {
            self.resetParty();
            self.showPartyData(true);
            $("#partySearch").trigger("openModal");
        };

        self.userSelected = function (data) {
            self.username(data.username);
            $("#userSearch").hide().trigger("closeModal");
            self.showUserData(false);
        };

        self.partySelected = function (data) {
            self.partyId(data.displayValue);
            $("#partySearch").hide().trigger("closeModal");
            self.showPartyData(false);
        };

        self.resetDialogue = function () {
            self.username("reset");
            self.username("");
            self.userList([]);
            self.loadSearchData(false);
        };

        self.resetParty = function () {
            self.partyName("reset");
            self.partyId("reset");
            self.partyName("");
            self.partyId("");
            self.showList(false);
        };

        self.userTypeOptionChangeHandler = function (event) {
            self.selectedUserType(event.detail.value);
        };

        self.fetchUsers = function () {
            self.userList([]);

            if (!rootParams.baseModel.showComponentValidationErrors(self.userValidationTracker())) {
                return;
            }

            if (self.partyId() && self.isCorpAdmin === false) {
                AuditLogModel.fetchDetails(self.partyId()).done(function (data) {
                    if (data.parties.length === 0) {
                        self.loadSearchData(false);
                        self.partyId("");
                        rootParams.baseModel.showMessages(null, [self.nls.info.incorrectInfo], "ERROR");
                    } else if (data.parties[0] !== null && data.parties[0]) {
                        self.indirectedParty(data.parties[0].id.value);
                        self.fetchUsersList();
                    }
                });
            } else {
                self.fetchUsersList();
            }
        };

        self.fetchUsersList = function () {
            if (self.username() === null || self.username() === undefined) {
                self.username("");
            }

            if (self.firstName() === null || self.firstName() === undefined) {
                self.firstName("");
            }

            const userParameters = {
                username: self.username() ? self.username().toUpperCase() : self.username(),
                firstName: self.firstName(),
                partyId: self.indirectedParty(),
                userType: self.selectedUserType(),
                isAccessSetupCheckRequired: false
            };

            self.usernamesearched(self.username() ? self.username().toUpperCase() : self.username());
            self.loadSearchData(false);

            if (self.username() !== "") {
                AuditLogModel.fetchUsersList(userParameters).done(function (data) {
                    self.searchedUserList(data.userDTOList);

                    if (data.userDTOList) {
                        self.loadSearchData(true);

                        if (data.userDTOList.length > 0) {
                            ko.utils.arrayForEach(data.userDTOList, function (item) {
                                self.userList().push(item);

                                self.userListDetailsDataSource(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(self.userList(), {
                                    idAttribute: "username"
                                })));
                            });
                        } else {
                            self.userList([]);

                            self.userListDetailsDataSource(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(self.userList(), {
                                idAttribute: "username"
                            })));
                        }
                    } else {
                        rootParams.baseModel.showMessages(null, [self.nls.info.recordNotFound], "ERROR");
                    }
                });
            } else {
                rootParams.baseModel.showMessages(null, [self.nls.info.dataRequired], "ERROR");
            }
        };

        self.fetchPartyDetailsByName = function () {
            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("Validator"))) {
                return;
            }

            self.showList(false);

            AuditLogModel.fetchDetailsByName(self.partyName()).done(function (data) {
                const partyList = $.map(data.parties, function (party) {
                    party.partyName = party.personalDetails.firstName;
                    party.value = party.id.value;
                    party.displayValue = party.id.displayValue;

                    return party;
                });

                if (data.parties.length > 0) {
                    self.showList(true);

                    self.datasource(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(partyList, {
                        idAttribute: "partyName"
                    })));
                } else {
                    rootParams.baseModel.showMessages(null, [self.nls.info.incorrectInfo], "ERROR");
                }
            });
        };

        self.setDate = function (event) {
            if (event.detail.value) {
                let today = rootParams.baseModel.getDate();
                const index = event.detail.value;

                if (index === "today") {
                    self.dateRange(false);
                    self.fromDateTime(self.formatDateTime(today, index, true));
                    self.toDateTime(self.formatDateTime(today, index, false));
                } else if (index === "yesterday") {
                    self.dateRange(false);
                    today = rootParams.baseModel.getDate();
                    today.setDate(today.getDate() - 1);
                    self.fromDateTime(self.formatDateTime(today, index, true));
                    self.toDateTime(self.formatDateTime(today, index, false));
                } else if (index === "l3d") {
                    self.dateRange(false);

                    const date = rootParams.baseModel.getDate();

                    date.setDate(date.getDate() - 2);
                    self.fromDateTime(self.formatDateTime(date, index, true));
                    today = rootParams.baseModel.getDate();
                    self.toDateTime(self.formatDateTime(today, index, false));
                } else if (index === "dr") {
                    self.dateRange(true);
                }
            }
        };

        self.formatDateTime = function (date, index, dateFlag) {
            if (self.dateRange() === false) {
                let time = date.toTimeString();

                time = time.substring(0, time.indexOf(""));
                date = oj.IntlConverterUtils.dateToLocalIso(date);

                if (dateFlag) {
                    if (index === "today" || index === "l3d") {
                        date = date.substring(0, date.indexOf("T"));
                        date = date + time;
                    } else {
                        date = date.substring(0, date.indexOf("T"));
                        date = date + "000000";
                    }
                } else {
                    date = date.substring(0, date.indexOf("T"));
                    date = date + "235959";
                }
            }

            date = date.replace(/T|-|:/g, "");

            return date;
        };

        self.dateType.subscribe(function (newValue) {
            if (newValue[0] === "dr" || newValue === "dr") {
                self.dateRange(true);

                if (typeof self.dateType() === "object") {
                    self.dateType(self.dateType()[0]);
                }
            } else {
                self.dateRange(false);
            }
        });

        self.getIndirectedPartyId = function () {

            if (!self.isCorpAdmin && (self.dateType() && self.dateType() === "dr" && self.fromDate() !== null && self.toDate() !== null)) {

                if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("dateRangeTracker"))) {
                    return;
                }

                const fromDate = new Date(self.fromDate()),
                toDate = new Date(self.toDate()),
                difference = Math.abs(fromDate - toDate) / (1000 * 3600 * 24);

                if(!(difference <= 5)) {
                    rootParams.baseModel.showMessages(null, [self.nls.info.invalidDateRange], "ERROR");

                    return;
                }
            }

            if (self.partyId() && self.isCorpAdmin === false) {
                AuditLogModel.fetchDetails(self.partyId()).done(function (data) {
                    if (data.parties.length === 0) {
                        self.user().loadSearchData(false);
                        self.partyId("");
                        rootParams.baseModel.showMessages(null, [self.nls.info.invalidInfo], "ERROR");
                    } else if (data.parties[0] !== null && data.parties[0]) {
                        self.indirectedPartyValue(data.parties[0].id.value);
                        self.searchAudit();
                    }
                });
            } else {
                self.searchAudit();
            }
        };

        self.cancel = function () {
            rootParams.dashboard.switchModule();
        };

        if (rootParams.rootModel.params && rootParams.rootModel.params.showUserData !== undefined) {
            self.showUserData = rootParams.rootModel.params.showUserData;
        } else {
            self.showUserData = ko.observable(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.showPartyData !== undefined) {
            self.showPartyData = rootParams.rootModel.params.showPartyData;
        } else {
            self.showPartyData = ko.observable(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.mainSearch !== undefined) {
            self.mainSearch = rootParams.rootModel.params.mainSearch;
        } else {
            self.mainSearch = ko.observable(false);
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.backFlag === true) {
            self.getIndirectedPartyId();
        }

    };
});