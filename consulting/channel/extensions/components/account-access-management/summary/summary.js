define([
    "ojs/ojcore",
    "knockout",
    "./model",
    "ojL10n!extensions/resources/nls/access-management",
    "ojs/ojinputtext",
    "ojs/ojpopup",
    "ojs/ojradioset",
    "ojs/ojselectcombobox",
    "ojs/ojtable",
    "ojs/ojrowexpander",
    "ojs/ojchart",
    "ojs/ojflattenedtreedatagriddatasource",
    "ojs/ojjsontreedatasource",
    "ojs/ojarraytabledatasource",
    "ojs/ojbutton",
    "framework/elements/api/page-section/loader"
], function (oj, ko, ExclusionModel, resourceBundle) {
    "use strict";

    return function viewModel(rootParams) {
        const self = this,
            inputParams = rootParams.rootModel.createObservables ? rootParams.rootModel : rootParams.rootModel.params;

        inputParams.createObservables(inputParams, [
            "accountAccessSummaryObject",
            "transactionNames",
            "casaRequestPayload",
            "tdRequestPayload",
            "loanRequestPayload",
            "lmRequestPayload",
            "vamEnabledRealAccRequestPayload",
            "virtualRequestPayload"
        ]);

        ko.utils.extend(self, inputParams);
        self.nls = resourceBundle;
        self.showAccountAccess = ko.observable(false);
        self.isAccessCreated = ko.observable(false);
        self.isAccessUpdated = ko.observable();
        self.mappedAccts = ko.observable();
        self.selectedModule = ko.observable();
        self.userList = ko.observableArray([]);
        self.showPartyValidateComponent = ko.observable(false);
        self.casaTransactionList = ko.observable([]);
        self.tdTransactionList = ko.observable([]);
        self.loanTransactionList = ko.observable([]);
        self.lmTransactionList = ko.observable([]);
        self.virtualTransactionList = ko.observable([]);
        self.vamRealTransactionList = ko.observable([]);
        self.totalTransactionList = ko.observableArray();
        self.summarydataSource = ko.observableArray();
        self.loadSummaryTable = ko.observable(false);
        self.isPreferenceExist = ko.observable(false);
        self.isLoaded = ko.observable(false);
        self.casaFullResourceTaskList([]);
        self.tdFullResourceTaskList([]);
        self.loanFullResourceTaskList([]);
        self.lmFullResourceTaskList([]);
        self.virtualFullResourceTaskList([]);
        self.vamEnabledRealFullResourceTaskList([]);
        self.fullPartiesCasaAccountList = ko.observableArray();
        self.fullPartiesLoanAccountList = ko.observableArray();
        self.fullPartiesTDAccountList = ko.observableArray();
        self.fullPartiesLMAccountList = ko.observableArray();
        self.fullPartiesVirtualAccountList = ko.observableArray();
        self.fullPartiesVAMRealAccountList = ko.observableArray();
        self.loadUserListComponent = ko.observable(false);
        self.selectedUserData = ko.observable();
        self.editBackFromReview = ko.observable(false);
        self.isDataRecieved = ko.observable(false);
        self.tableHeading = ko.observable(self.nls.headers.ownAccount);
        self.linkedpartyName = ko.observable();
        self.linkedPartyId = ko.observable();
        self.linkagetableHeading = ko.observable(self.nls.headers.linkedpartyAccount);
        self.indexSelected = ko.observable();
        self.isLinkageExist = ko.observable(false);
        self.partySetUpNotExists = ko.observable(false);
        self.parentChannelAccessNotExists = ko.observable(false);
        self.parentAccessLevel = ko.observable();
        self.tableHeading().toUpperCase();
        self.linkagetableHeading().toUpperCase();
        self.isCorpAdmin = ko.observable(false);
        self.showCopyOptions = ko.observable(false);
        self.copyAccountAccessSettingSelected = ko.observable("No");
        self.copyAccountAccessSettingUserSelected = ko.observableArray();
        self.showUsersList = ko.observable(false);
        self.userDisplayList = ko.observableArray();
        self.accessCreatedUserDisplayList = ko.observableArray();
        self.setupNotCreated = ko.observable(false);
        self.applyAllTransactionsCasaFlag = ko.observableArray();
        self.applyAllTransactionsTdFlag = ko.observableArray();
        self.applyAllTransactionsMPFFlag = ko.observableArray();

        self.mapAllFutureCasaAccounts = ko.observableArray([]);
        self.mapAllFutureTdAccounts = ko.observableArray([]);
        self.mapAllFutureLerAccounts = ko.observableArray([]);
        self.mapAllFutureLonAccounts = ko.observableArray([]);

        self.displayEditButtons = ko.observable(false);

        const readValue = function (value) {
            return ko.isObservable(value) ? value() : value;
        };

        self.isHthMode = ko.pureComputed(function () {
            return String(readValue(self.channelMode) || "BCO").toUpperCase() === "HTH";
        });

        self.hthSummaryLoading = ko.observable(false);
        self.hthSummaryError = ko.observable("");
        self.hthEnterpriseStatus = ko.observable();
        self.hthRelatedSummary = ko.observable();
        self.hthAssociatedSummaries = ko.observableArray([]);
        self.hthLinkageContext = ko.observable();

        self.hthEnterpriseDisabled = ko.pureComputed(function () {
            return self.hthEnterpriseStatus() && self.hthEnterpriseStatus() !== "ENABLE";
        });

        self.hthSummaryReady = ko.pureComputed(function () {
            return !self.hthSummaryLoading() && !self.hthSummaryError();
        });

        self.userAccessPageTitle = function () {
            return self.isHthMode() ? self.nls.pageTitle.hthUserAccess
                : self.nls.pageTitle.bcoUserAccess;
        };

        if (rootParams.dashboard.appData.segment === "ADMIN") {
            self.displayEditButtons(true);
        }

        if (rootParams.dashboard.appData.segment === "CORPADMIN") {
            if (self.accessLevel() !== "PARTY" && self.accessLevel() !== "LINKAGE") {
                self.displayEditButtons(true);
            }
        }

        self.copyAccountAccessSettingOptions = [{
            id: "Yes",
            label: self.nls.fieldname.yes
        },
        {
            id: "No",
            label: self.nls.fieldname.no
        }
        ];

        // Pending workflow state affects available actions but never contributes to effective
        // account counts. Enterprise disablement also blocks maintenance without deleting grants.
        const normalizeHthSummary = function (summary) {
            if (!summary) {
                return null;
            }

            summary.accountCountByType = summary.accountCountByType || {};
            summary.casaAccountCount = Number(summary.accountCountByType.CSA || 0);
            summary.tdAccountCount = Number(summary.accountCountByType.TD || 0);
            summary.totalAccountCount = summary.casaAccountCount + summary.tdAccountCount;

            summary.hasPendingRequest = Boolean(summary.pendingAction ||
                String(summary.setupStatus || "").indexOf("PENDING_") === 0);

            summary.canMaintain = !self.hthEnterpriseDisabled() && !summary.hasPendingRequest &&
                summary.setupStatus !== "ERROR";

            return summary;
        };

        self.hthSetupStatusText = function (summary) {
            const setupStatus = summary && summary.setupStatus;

            if (setupStatus === "ACTIVE") {
                return self.nls.info.hthActive;
            }

            if (setupStatus === "DISABLED") {
                return self.nls.info.hthDisabled;
            }

            if (setupStatus && setupStatus.indexOf("PENDING_") === 0) {
                return self.nls.info.hthPendingApproval;
            }

            return self.nls.info.hthNotSetup;
        };

        self.hthPendingText = function (summary) {
            if (!summary || !summary.hasPendingRequest) {
                return "";
            }

            return rootParams.baseModel.format(self.nls.info.hthPendingDetail, {
                action: summary.pendingAction || "",
                reference: summary.pendingReferenceNumber || "-"
            });
        };

        self.buildHthLinkageContext = function (summary) {
            // Preserve the complete backend context. In particular, accessPartyId differs from the
            // primary party for ASSOCIATED companies and must not be inferred by later screens.
            const context = {
                channelMode: "HTH",
                partyId: readValue(self.partyID),
                closeId: readValue(self.closeId),
                username: readValue(self.selectedUserId),
                fullName: readValue(self.selectedUserFullName) || readValue(self.selectedUserName),
                userChannelType: readValue(self.selectedUserChannelType) || "HTH",
                linkageType: summary.linkageType,
                accessPartyId: summary.accessPartyId,
                accessPartyName: summary.accessPartyName,
                setupStatus: summary.setupStatus
            };

            self.hthLinkageContext(context);

            return context;
        };

        self.openHthLinkage = function (summary) {
            const context = self.buildHthLinkageContext(summary),
                navigationHandler = readValue(self.hthLinkageNavigationHandler);

            if (typeof navigationHandler === "function") {
                navigationHandler(context);

                return;
            }

            // HTH uses its own template chain. Loading the legacy mapping-modules component here
            // would bind its BCO-only template before the HTH redirect completes.
            rootParams.baseModel.registerComponent("hth-account-linkage", "account-access-management");
            rootParams.baseModel.registerComponent("hth-api-service-mapping", "account-access-management");
            rootParams.baseModel.registerComponent("review-hth-user-access", "account-access-management");
            rootParams.baseModel.registerElement("confirm-screen");

            rootParams.dashboard.loadComponent("hth-account-linkage", {
                hthLinkageContext: context,
                summaryParams: inputParams
            });
        };

        self.loadHthSummary = function () {
            self.hthSummaryLoading(true);
            self.hthSummaryError("");
            self.hthRelatedSummary(null);
            self.hthAssociatedSummaries([]);

            ExclusionModel.readHthUserAccessSummary(readValue(self.partyID), readValue(self.closeId))
                .done(function (data) {
                    data = data || {};
                    self.hthEnterpriseStatus(data.enterpriseHthStatus || "DISABLE");
                    self.hthRelatedSummary(normalizeHthSummary(data.related));
                    self.hthAssociatedSummaries(ko.utils.arrayMap(data.associated || [], normalizeHthSummary));
                })
                .fail(function () {
                    self.hthSummaryError(self.nls.info.hthSummaryLoadError);
                })
                .always(function () {
                    self.hthSummaryLoading(false);
                });
        };

        if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
            rootParams.dashboard.headerName(self.userAccessPageTitle());
        }

        const getNewKoModel = function () {
            const KoModel = ExclusionModel.getNewModel();

            return ko.mapping.fromJS(KoModel);
        };

        self.ExclusionModelInstance = ko.observable(getNewKoModel());

        const partyId = {};

        partyId.value = rootParams.dashboard.userData.userProfile.partyId.value;
        partyId.displayValue = rootParams.dashboard.userData.userProfile.partyId.displayValue;

        const userProfile = {};

        userProfile.firstName = rootParams.dashboard.userData.userProfile.firstName;

        let isCorpAdmin = false;

        if (rootParams.dashboard.appData.segment === "CORPADMIN") {
            isCorpAdmin = true;
        } else {
            isCorpAdmin = false;
        }

        self.ifvraPayload = function (vraPayload) {
            if (vraPayload) {
                ko.utils.arrayForEach(vraPayload, function (item) {
                    self.newVRAObject = {
                        accountType: "",
                        accountNumber: {
                            value: "",
                            displayValue: ""
                        },
                        accountStatus: "",
                        displayName: "",
                        resourceListVRA: [],
                        selectedTask: [],
                        nonSelectedTask: [],
                        currencyCode: "",
                        fullResourceTaskList: []
                    };

                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                        self.newResourceObject = {
                            childTasks: [],
                            name: ""
                        };

                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                            self.newTaskObject = {
                                id: "",
                                name: "",
                                supportedAccountTypes: [],
                                approvalSupported: "",
                                limitRequired: "",
                                moduleType: "",
                                type: "",
                                allowed: ""
                            };

                            self.newTaskObject.id = thisItem.childTask.id;
                            self.newTaskObject.name = thisItem.childTask.name;
                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                            self.newTaskObject.type = thisItem.childTask.type;
                            self.newTaskObject.allowed = thisItem.allowed;

                            if (thisItem.allowed) {
                                self.newVRAObject.selectedTask.push(thisItem.childTask.id);
                            } else {
                                self.newVRAObject.nonSelectedTask.push(thisItem.childTask.id);
                            }

                            self.newVRAObject.fullResourceTaskList.push(thisItem.childTask.id);
                            self.newResourceObject.childTasks.push(self.newTaskObject);
                            self.newResourceObject.name = thisChildTaskItem.name;
                        });

                        self.newVRAObject.resourceListVRA.push(self.newResourceObject);
                    });

                    self.newVRAObject.accountType = item.accountType;
                    self.newVRAObject.accountNumber.value = item.accountNumber.value;
                    self.newVRAObject.accountNumber.displayValue = item.accountNumber.displayValue;
                    self.newVRAObject.displayName = item.displayName;
                    self.newVRAObject.currencyCode = item.currencyCode;
                    self.newVRAObject.accountStatus = item.accountStatus;
                    self.resourceListVRA([self.newVRAObject.resourceListVRA]);
                    self.fullVirtualAccountList().push(self.newVRAObject);
                });

                self.fullPartiesVirtualAccountList().push(self.fullVirtualAccountList());
                self.accountAccessSummaryObject.totalVRAaccts = vraPayload.length;

                let mappedVRACount = 0,
                    x;

                for (x = 0; x < vraPayload.length; x++) {
                    for (let a = 0; a < vraPayload[x].tasks.length; a++) {
                        for (let z = 0; z < vraPayload[x].tasks[a].childTasks.length; z++) {
                            if (vraPayload[x].tasks[a].childTasks[z].allowed) {
                                mappedVRACount++;
                                a = vraPayload[x].tasks.length;
                                break;
                            }
                        }
                    }
                }

                self.accountAccessSummaryObject.mappedVRAaccts = mappedVRACount;
            } else {
                self.accountAccessSummaryObject.mappedVRAaccts = 0;
                self.accountAccessSummaryObject.totalVRAaccts = 0;
            }
        };

        if (isCorpAdmin) {
            self.isCorpAdmin(true);

            ExclusionModel.fetchCorpAdminPartyDetails().done(function (data) {
                self.ExclusionModelInstance().partyDetails.partyName(data.party.personalDetails.fullName);
                self.ExclusionModelInstance().partyDetails.party.value(partyId.value);
                self.ExclusionModelInstance().partyDetails.party.displayValue(partyId.displayValue);
                self.ExclusionModelInstance().partyDetails.partyDetailsFetched(true);
                self.showPartyValidateComponent(false);
            });
        } else {
            self.showPartyValidateComponent(true);
            self.isCorpAdmin(false);
        }

        if (self.partyID()) {
            self.ExclusionModelInstance().partyDetails.party.value(self.partyID());
            self.ExclusionModelInstance().partyDetails.party.displayValue(self.maskedPartyId());
            self.ExclusionModelInstance().partyDetails.partyName(self.partyName());
            self.showPartyValidateComponent(false);
        }

        self.selectedUserData.subscribe(function (selectedUserData) {
            self.showUserAccountAccess(selectedUserData);
        });

        self.ExclusionModelInstance().partyDetails.party.value.subscribe(function (updatedPartyID) {
            if (updatedPartyID === null || updatedPartyID === undefined || updatedPartyID === "") {
                self.partyID("");
                self.partyName("");
                self.summarydataSource([]);
                self.showAccountAccess(false);
                self.userListLoaded(false);
            } else {
                self.selectedAccountsResources([]);
                self.partyID(self.ExclusionModelInstance().partyDetails.party.value());
                self.maskedPartyId(self.ExclusionModelInstance().partyDetails.party.displayValue());
                self.partyName(self.ExclusionModelInstance().partyDetails.partyName());

                if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
                    rootParams.dashboard.headerName(self.userAccessPageTitle());

                    self.loadUserListComponent(false);
                    self.loadUserListComponent(true);
                }

                if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                    self.fullCasaAccountList([]);
                    self.fulltdAccountList([]);
                    self.fullloanAccountList([]);
                    self.fullLMAccountList([]);
                    self.fullVirtualAccountList([]);
                    self.fullVAMEnabledRealAccountList([]);
                    self.selectedTdAccounts([]);
                    self.selectedLoanAccounts([]);
                    self.selectedCasaAccounts([]);
                    self.selectedLMAccounts([]);
                    self.selectedVirtualAccounts([]);
                    self.selectedVAMEnabledRealAccounts([]);
                }
            }
        });

        self.getAllAccountsCount = function () {
            if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                rootParams.dashboard.headerName(self.nls.pageTitle.accessManagementParty);
            } else if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
                rootParams.dashboard.headerName(self.userAccessPageTitle());
            }

            ExclusionModel.readAllAccountDetails(isCorpAdmin ? partyId.value : self.partyID()).done(function (data) {
                self.summarydataSource([]);
                self.parentAccessLevel("PARTY");

                const partyAccounts = data.accounts;

                if (partyAccounts.length > 0) {
                    ko.utils.arrayForEach(partyAccounts, function (partyItem) {
                        self.linkedpartyName("");
                        self.fullCasaAccountList([]);
                        self.fulltdAccountList([]);
                        self.fullloanAccountList([]);
                        self.fullLMAccountList([]);
                        self.fullVirtualAccountList([]);
                        self.fullVAMEnabledRealAccountList([]);
                        self.Loader = false;

                        if (partyItem.setupInformation === "SETUP_EXISTS") {
                            self.isAccessCreated(true);
                            self.showEditableForm(true);
                        } else {
                            self.isAccessCreated(false);
                            self.showEditableForm(false);
                        }

                        if (partyItem.preferenceStatus === "ENABLED") {
                            self.isPreferenceExist(true);
                        } else if (partyItem.preferenceStatus === "NOT_FOUND") {
                            self.isPreferenceExist(true);
                        }

                        if (partyItem.accessLevel === "LINKAGE") {
                            self.isLinkageExist(true);

                            if (partyItem.partyName) {
                                self.linkedpartyName(partyItem.partyName);
                            }

                            self.linkedPartyId(partyItem.party);

                            if (partyItem.preferenceStatus === "DISABLED") {
                                self.parentChannelAccessNotExists(true);
                            }
                        }

                        self.accessLevel(partyItem.accessLevel);

                        if (partyItem.accountsList) {
                            const casaPayload = [],
                                tdPayload = [],
                                loanPayload = [],
                                lmPayload = [],
                                verPayload = [],
                                vraPayload = [];
                            let x;

                            for (x = 0; x < partyItem.accountsList.length; x++) {
                                if (partyItem.accountsList[x].accountType === "CSA") {

                                    // const vmapAllFutureCasaAccounts = "Y";

                                    const vmapAllFutureCasaAccounts = partyItem.accountsList[x].dictionaryArray[0].nameValuePairDTOArray.find(function (o) {
                                        return o.genericName === "com.ofss.digx.cz.bea.app.access.dto.AccountExclusionDTO.mapAllFutureCasaAccounts";
                                    });

                                    let v_phoneBankingStatus;

                                    const phoneBankingStatus = partyItem.accountsList[x].dictionaryArray[0].nameValuePairDTOArray.find(function (o) {
                                        return o.genericName === "com.ofss.digx.cz.bea.app.access.dto.AccountExclusionDTO.phoneBankingStatus";
                                    });

                                    if (phoneBankingStatus.value === undefined) {
                                        v_phoneBankingStatus = "Disable";
                                    } else {
                                        v_phoneBankingStatus = phoneBankingStatus.value;
                                    }

                                    partyItem.accountsList[x].phoneBankingStatus = v_phoneBankingStatus;
                                    partyItem.accountsList[x].mapAllFutureCasaAccounts = vmapAllFutureCasaAccounts.value;

                                    casaPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "LON") {
                                    const vmapAllFutureLonAccounts = partyItem.accountsList[x].dictionaryArray[0].nameValuePairDTOArray.find(function (o) {
                                        return o.genericName === "com.ofss.digx.cz.bea.app.access.dto.AccountExclusionDTO.mapAllFutureLonAccounts";
                                    });

                                    partyItem.accountsList[x].mapAllFutureLonAccounts = vmapAllFutureLonAccounts.value;
                                    loanPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "TRD") {

                                    // const vmapAllFutureTdAccounts = "Y";

                                    const vmapAllFutureTdAccounts = partyItem.accountsList[x].dictionaryArray[0].nameValuePairDTOArray.find(function (o) {
                                        return o.genericName === "com.ofss.digx.cz.bea.app.access.dto.AccountExclusionDTO.mapAllFutureTdAccounts";
                                    });

                                    partyItem.accountsList[x].mapAllFutureTdAccounts = vmapAllFutureTdAccounts.value;

                                    tdPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "LER") {

                                    const vmapAllFutureLerAccounts = partyItem.accountsList[x].dictionaryArray[0].nameValuePairDTOArray.find(function (o) {
                                        return o.genericName === "com.ofss.digx.cz.bea.app.access.dto.AccountExclusionDTO.mapAllFutureLerAccounts";
                                    });

                                    partyItem.accountsList[x].mapAllFutureLerAccounts = vmapAllFutureLerAccounts.value;
                                    lmPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "VER") {
                                    verPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "VRA") {
                                    vraPayload.push(partyItem.accountsList[x]);
                                }
                            }

                            self.isDataRecieved(true);

                            if (casaPayload) {
                                ko.utils.arrayForEach(casaPayload, function (item) {
                                    self.newCasaObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListCasa: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newCasaObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newCasaObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newCasaObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newCasaObject.resourceListCasa.push(self.newResourceObject);
                                    });

                                    self.newCasaObject.accountType = item.accountType;
                                    self.newCasaObject.accountNumber.value = item.accountNumber.value;
                                    self.newCasaObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newCasaObject.displayName = item.displayName;
                                    self.newCasaObject.currencyCode = item.currencyCode;
                                    self.newCasaObject.accountStatus = item.accountStatus;
                                    self.newCasaObject.phoneBankingStatus = item.phoneBankingStatus;
                                    self.newCasaObject.mapAllFutureCasaAccounts = item.mapAllFutureCasaAccounts;

                                    self.resourceListCasa([self.newCasaObject.resourceListCasa]);
                                    self.fullCasaAccountList().push(self.newCasaObject);
                                });

                                self.fullPartiesCasaAccountList().push(self.fullCasaAccountList());
                                self.accountAccessSummaryObject.totalCasaAccts = casaPayload.length;

                                let mappedCasaCount = 0;

                                for (let i = 0; i < casaPayload.length; i++) {
                                    for (let j = 0; j < casaPayload[i].tasks.length; j++) {
                                        for (let y = 0; y < casaPayload[i].tasks[j].childTasks.length; y++) {
                                            if (casaPayload[i].tasks[j].childTasks[y].allowed) {
                                                mappedCasaCount++;
                                                j = casaPayload[i].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedCasaAccts = mappedCasaCount;
                            } else {
                                self.accountAccessSummaryObject.mappedCasaAccts = 0;
                                self.accountAccessSummaryObject.totalCasaAccts = 0;
                            }

                            if (tdPayload) {
                                ko.utils.arrayForEach(tdPayload, function (item) {
                                    self.newTdObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListTD: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newTdObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newTdObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newTdObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newTdObject.resourceListTD.push(self.newResourceObject);
                                    });

                                    self.newTdObject.accountType = item.accountType;
                                    self.newTdObject.accountNumber.value = item.accountNumber.value;
                                    self.newTdObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newTdObject.displayName = item.displayName;
                                    self.newTdObject.currencyCode = item.currencyCode;
                                    self.newTdObject.accountStatus = item.accountStatus;
                                    self.newTdObject.mapAllFutureTdAccounts = item.mapAllFutureTdAccounts;

                                    self.resourceListTD([self.newTdObject.resourceListTD]);
                                    self.fulltdAccountList().push(self.newTdObject);
                                });

                                self.fullPartiesTDAccountList().push(self.fulltdAccountList());
                                self.accountAccessSummaryObject.totalTrdAccts = tdPayload.length;

                                let mappedtdCount = 0;

                                for (let l = 0; l < tdPayload.length; l++) {
                                    for (let m = 0; m < tdPayload[l].tasks.length; m++) {
                                        for (let n = 0; n < tdPayload[l].tasks[m].childTasks.length; n++) {
                                            if (tdPayload[l].tasks[m].childTasks[n].allowed) {
                                                mappedtdCount++;
                                                m = tdPayload[l].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedTrdAccts = mappedtdCount;
                            } else {
                                self.accountAccessSummaryObject.mappedTrdAccts = 0;
                                self.accountAccessSummaryObject.totalTrdAccts = 0;
                            }

                            if (loanPayload) {
                                ko.utils.arrayForEach(loanPayload, function (item) {
                                    self.newLoanObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListLON: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newLoanObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newLoanObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newLoanObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newLoanObject.resourceListLON.push(self.newResourceObject);
                                    });

                                    self.newLoanObject.accountType = item.accountType;
                                    self.newLoanObject.accountNumber.value = item.accountNumber.value;
                                    self.newLoanObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newLoanObject.displayName = item.displayName;
                                    self.newLoanObject.currencyCode = item.currencyCode;
                                    self.newLoanObject.accountStatus = item.accountStatus;
                                    self.newLoanObject.mapAllFutureLonAccounts = item.mapAllFutureLonAccounts;
                                    self.resourceListLON([self.newLoanObject.resourceListLON]);
                                    self.fullloanAccountList().push(self.newLoanObject);
                                });

                                self.fullPartiesLoanAccountList().push(self.fullloanAccountList());
                                self.accountAccessSummaryObject.totalLonAccts = loanPayload.length;

                                let mappedLonCount = 0;

                                for (x = 0; x < loanPayload.length; x++) {
                                    for (let a = 0; a < loanPayload[x].tasks.length; a++) {
                                        for (let z = 0; z < loanPayload[x].tasks[a].childTasks.length; z++) {
                                            if (loanPayload[x].tasks[a].childTasks[z].allowed) {
                                                mappedLonCount++;
                                                a = loanPayload[x].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedLonAccts = mappedLonCount;
                            } else {
                                self.accountAccessSummaryObject.mappedLonAccts = 0;
                                self.accountAccessSummaryObject.totalLonAccts = 0;
                            }

                            if (verPayload) {
                                ko.utils.arrayForEach(verPayload, function (item) {
                                    self.newVERObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListVER: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newVERObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newVERObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newVERObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newVERObject.resourceListVER.push(self.newResourceObject);
                                    });

                                    self.newVERObject.accountType = item.accountType;
                                    self.newVERObject.accountNumber.value = item.accountNumber.value;
                                    self.newVERObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newVERObject.displayName = item.displayName;
                                    self.newVERObject.currencyCode = item.currencyCode;
                                    self.newVERObject.accountStatus = item.accountStatus;
                                    self.resourceListVER([self.newVERObject.resourceListVER]);
                                    self.fullVAMEnabledRealAccountList().push(self.newVERObject);
                                });

                                self.fullPartiesVAMRealAccountList().push(self.fullVAMEnabledRealAccountList());
                                self.accountAccessSummaryObject.totalVERAccounts = verPayload.length;

                                let mappedVERCount = 0,
                                    x;

                                for (x = 0; x < verPayload.length; x++) {
                                    for (let a = 0; a < verPayload[x].tasks.length; a++) {
                                        for (let z = 0; z < verPayload[x].tasks[a].childTasks.length; z++) {
                                            if (verPayload[x].tasks[a].childTasks[z].allowed) {
                                                mappedVERCount++;
                                                a = verPayload[x].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedVERAccts = mappedVERCount;
                            } else {
                                self.accountAccessSummaryObject.mappedVERAccts = 0;
                                self.accountAccessSummaryObject.totalVERAccounts = 0;
                            }

                            if (lmPayload) {
                                ko.utils.arrayForEach(lmPayload, function (item) {
                                    self.newLMObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListLM: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: [],
                                        virtual: ""
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newLMObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newLMObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newLMObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newLMObject.resourceListLM.push(self.newResourceObject);
                                    });

                                    self.newLMObject.accountType = item.accountType;
                                    self.newLMObject.accountNumber.value = item.accountNumber.value;
                                    self.newLMObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newLMObject.displayName = item.displayName;
                                    self.newLMObject.currencyCode = item.currencyCode;
                                    self.newLMObject.accountStatus = item.accountStatus;
                                    self.newLMObject.virtual = item.virtual;
                                    self.newLMObject.mapAllFutureLerAccounts = item.mapAllFutureLerAccounts;
                                    self.resourceListLM([self.newLMObject.resourceListLM]);
                                    self.fullLMAccountList().push(self.newLMObject);
                                });

                                self.fullPartiesLMAccountList().push(self.fullLMAccountList());
                                self.accountAccessSummaryObject.totalLMAccts = lmPayload.length;

                                let mappedLMCount = 0;

                                for (let i = 0; i < lmPayload.length; i++) {
                                    for (let j = 0; j < lmPayload[i].tasks.length; j++) {
                                        for (let y = 0; y < lmPayload[i].tasks[j].childTasks.length; y++) {
                                            if (lmPayload[i].tasks[j].childTasks[y].allowed) {
                                                mappedLMCount++;
                                                j = lmPayload[i].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedLMAccts = mappedLMCount;
                            } else {
                                self.accountAccessSummaryObject.mappedLMAccts = 0;
                                self.accountAccessSummaryObject.totalLMAccts = 0;
                            }

                            self.ifvraPayload(vraPayload);

                            const totalMappedAccts = self.accountAccessSummaryObject.mappedLonAccts + self.accountAccessSummaryObject.mappedTrdAccts + self.accountAccessSummaryObject.mappedCasaAccts + self.accountAccessSummaryObject.mappedLMAccts + self.accountAccessSummaryObject.mappedVRAaccts + self.accountAccessSummaryObject.mappedVERAccts;

                            self.mappedAccts(totalMappedAccts);
                        } else {
                            self.isDataRecieved(false);
                            self.fullPartiesCasaAccountList().push(self.fullCasaAccountList());
                            self.fullPartiesLoanAccountList().push(self.fullloanAccountList());
                            self.fullPartiesTDAccountList().push(self.fulltdAccountList());
                            self.fullPartiesLMAccountList().push(self.fullLMAccountList());
                            self.fullPartiesVirtualAccountList().push(self.fullVirtualAccountList());
                            self.fullPartiesVAMRealAccountList().push(self.fullVAMEnabledRealAccountList());
                        }

                        self.createDataSource();
                    });
                }
            }).fail(function () {
                self.isDataRecieved(false);
                self.back();
            });
        };

        self.getAllUsersList = function () {
            rootParams.dashboard.headerName(self.userAccessPageTitle());

            ExclusionModel.fetchAssociatedUserForParty(isCorpAdmin ? partyId.value : self.partyID()).done(function (data) {
                self.userList(data.userDTOList);
                self.accessCreatedUserList = ko.observableArray();
                self.nonAccessCreatedUserList = ko.observableArray();

                ko.utils.arrayForEach(self.userList(), function (item) {
                    if (item.accountAccessSetupDone === true) {
                        self.accessCreatedUserList().push(item);
                    } else {
                        self.nonAccessCreatedUserList().push(item);
                    }
                });

                self.accessCreatedUserList.sort(function (left, right) {
                    return left.username === right.username ? 0 : left.username < right.username ? -1 : 1;
                });

                self.nonAccessCreatedUserList.sort(function (left, right) {
                    return left.username === right.username ? 0 : left.username < right.username ? -1 : 1;
                });

                self.userList([]);

                ko.utils.arrayForEach(self.accessCreatedUserList(), function (item) {
                    self.userList().push(item);
                });

                ko.utils.arrayForEach(self.nonAccessCreatedUserList(), function (item) {
                    self.userList().push(item);
                });

                self.userListLoaded(true);
            });
        };

        self.loadAccountMappingComponent = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.linkedpartyName(linkedpartyName);
            self.linkedPartyId(linkedPartyId);
            self.isAccessCreated(isAccessCreated);
            self.showEditableForm(showEditableForm);
            self.accessLevel(accessLevel);
            self.fullCasaAccountList(self.fullPartiesCasaAccountList()[index]);
            self.fulltdAccountList(self.fullPartiesTDAccountList()[index]);
            self.fullloanAccountList(self.fullPartiesLoanAccountList()[index]);
            self.fullLMAccountList(self.fullPartiesLMAccountList()[index]);
            self.fullVirtualAccountList(self.fullPartiesVirtualAccountList()[index]);
            self.fullVAMEnabledRealAccountList(self.fullPartiesVAMRealAccountList()[index]);
            self.indexSelected(index);
            self.selectedCasaAccounts.removeAll();
            self.selectedTdAccounts.removeAll();
            self.selectedLoanAccounts.removeAll();
            self.selectedLMAccounts.removeAll();
            self.selectedVAMEnabledRealAccounts.removeAll();
            self.selectedVirtualAccounts.removeAll();

            if (accessLevel === "PARTY") {

                if (self.fullPartiesCasaAccountList()[index].length > 0) {
                    self.mapAllFutureCasaAccounts([self.fullPartiesCasaAccountList()[index][0].mapAllFutureCasaAccounts]);
                } else {
                    self.mapAllFutureCasaAccounts(["N"]);
                }

                if (self.fullPartiesTDAccountList()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.fullPartiesTDAccountList()[index][0].mapAllFutureTdAccounts]);
                } else {
                    self.mapAllFutureTdAccounts(["N"]);
                }

                if (self.fullPartiesLMAccountList()[index].length > 0) {
                    self.mapAllFutureLerAccounts([self.fullPartiesLMAccountList()[index][0].mapAllFutureLerAccounts]);
                } else {
                    self.mapAllFutureLerAccounts(["N"]);
                }

                if (self.fullPartiesLoanAccountList()[index].length > 0) {
                    self.mapAllFutureLonAccounts([self.fullPartiesLoanAccountList()[index][0].mapAllFutureLonAccounts]);
                } else {
                    self.mapAllFutureLonAccounts(["N"]);
                }

                if (self.selectedVAMEnabledRealAccounts().length > 0 && self.selectedVAMEnabledRealAccounts()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.selectedVAMEnabledRealAccounts()[index][0].mapAllFutureTdAccounts]);
                }

                if (self.selectedVAMEnabledRealAccounts().length > 0 && self.fullPartiesVirtualAccountList()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.fullPartiesVirtualAccountList()[index][0].mapAllFutureTdAccounts]);
                }

                const params = ko.mapping.toJS({
                    isCorpAdmin: isCorpAdmin,
                    ExclusionModelInstance: self.ExclusionModelInstance,
                    isTDAllowed: self.isTDAllowed,
                    isCasaAllowed: self.isCasaAllowed,
                    isLoanAllowed: self.isLoanAllowed,
                    isLMAllowed: self.isLMAllowed,
                    isVirtualAllowed: self.isVirtualAllowed,
                    isVAMEnabledRealAllowed: self.isVAMEnabledRealAllowed,
                    partyID: self.partyID,
                    indexSelected: self.indexSelected,
                    selectedCasaAccounts: self.selectedCasaAccounts,
                    selectedTdAccounts: self.selectedTdAccounts,
                    selectedLoanAccounts: self.selectedLoanAccounts,
                    selectedLMAccounts: self.selectedLMAccounts,
                    selectedVirtualAccounts: self.selectedVirtualAccounts,
                    selectedVAMEnabledRealAccounts: self.selectedVAMEnabledRealAccounts,
                    fullPartiesCasaAccountList: self.fullPartiesCasaAccountList,
                    fullPartiesTDAccountList: self.fullPartiesTDAccountList,
                    fullPartiesLoanAccountList: self.fullPartiesLoanAccountList,
                    fullPartiesLMAccountList: self.fullPartiesLMAccountList,
                    fullPartiesVAMRealAccountList: self.fullPartiesVAMRealAccountList,
                    fullPartiesVirtualAccountList: self.fullPartiesVirtualAccountList,
                    fullCasaAccountList: self.fullCasaAccountList,
                    fulltdAccountList: self.fulltdAccountList,
                    fullloanAccountList: self.fullloanAccountList,
                    fullLMAccountList: self.fullLMAccountList,
                    fullVirtualAccountList: self.fullVirtualAccountList,
                    fullVAMEnabledRealAccountList: self.fullVAMEnabledRealAccountList,
                    isAccessCreated: self.isAccessCreated,
                    showEditableForm: self.showEditableForm,
                    transactionName: self.transactionName,
                    editBackFromReview: self.editBackFromReview,
                    casaExclusionAccountNumberList: self.casaExclusionAccountNumberList,
                    tdExclusionAccountNumberList: self.tdExclusionAccountNumberList,
                    loanExclusionAccountNumberList: self.loanExclusionAccountNumberList,
                    lmExclusionAccountNumberList: self.lmExclusionAccountNumberList,
                    virtualExclusionAccountNumberList: self.virtualExclusionAccountNumberList,
                    vamEnabledRealExclusionAccountNumberList: self.vamEnabledRealExclusionAccountNumberList,
                    updatedCASAExclusionNumberList: self.updatedCASAExclusionNumberList,
                    updatedTDExclusionNumberList: self.updatedTDExclusionNumberList,
                    updatedLOANExclusionNumberList: self.updatedLOANExclusionNumberList,
                    updatedLMExclusionNumberList: self.updatedLMExclusionNumberList,
                    updatedVirtualExclusionNumberList: self.updatedVirtualExclusionNumberList,
                    updatedVAMEnabledRealExclusionNumberList: self.updatedVAMEnabledRealExclusionNumberList,
                    selectedUserId: self.selectedUserId,
                    partyName: self.partyName,
                    showConfirmationForCreate: self.showConfirmationForCreate,
                    showModuleToMap: self.showModuleToMap,
                    selectedUserName: self.selectedUserName,
                    loanAccountAccessId: self.loanAccountAccessId,
                    casaAccountAccessId: self.casaAccountAccessId,
                    tdAccountAccessId: self.tdAccountAccessId,
                    lmAccountAccessId: self.lmAccountAccessId,
                    virtualAccountAccessId: self.virtualAccountAccessId,
                    vamEnabledRealAccountAccessId: self.vamEnabledRealAccountAccessId,
                    casaAccountNumbersArray: self.casaAccountNumbersArray,
                    tdAccountNumbersArray: self.tdAccountNumbersArray,
                    loanAccountNumbersArray: self.loanAccountNumbersArray,
                    lmAccountNumbersArray: self.lmAccountNumbersArray,
                    virtualAccountNumbersArray: self.virtualAccountNumbersArray,
                    vamEnabledRealAccountNumbersArray: self.vamEnabledRealAccountNumbersArray,
                    showDeleteButton: self.showDeleteButton,
                    accessLevel: self.accessLevel,
                    highlightedTab: self.highlightedTab,
                    selectedModule: self.selectedModule,
                    casaAccountTabVisited: self.casaAccountTabVisited,
                    loanAccountTabVisited: self.loanAccountTabVisited,
                    tdAccountTabVisited: self.tdAccountTabVisited,
                    lmAccountTabVisited: self.lmAccountTabVisited,
                    virtualAccountTabVisited: self.virtualAccountTabVisited,
                    vamEnabledRealAccountTabVisited: self.vamEnabledRealAccountTabVisited,
                    selectedAccountsResources: self.selectedAccountsResources,
                    editButtonPressed: self.editButtonPressed,
                    parentAccessLevel: self.parentAccessLevel,
                    disableAccountSelection: self.disableAccountSelection,
                    highlightedTabTrans: self.highlightedTabTrans,
                    casaTransactionTabVisited: self.casaTransactionTabVisited,
                    loanTransactionTabVisited: self.loanTransactionTabVisited,
                    tdTransactionTabVisited: self.tdTransactionTabVisited,
                    lmTransactionTabVisited: self.lmTransactionTabVisited,
                    virtualTransactionTabVisited: self.virtualTransactionTabVisited,
                    vamEnabledRealAccTransactionTabVisited: self.vamEnabledRealAccTransactionTabVisited,
                    isAccessUpdated: self.isAccessUpdated,
                    resourceListCasa: self.resourceListCasa,
                    resourceListTD: self.resourceListTD,
                    resourceListLON: self.resourceListLON,
                    resourceListLM: self.resourceListLM,
                    resourceListVRA: self.resourceListVRA,
                    resourceListVER: self.resourceListVER,
                    casaRequestPayload: self.casaRequestPayload,
                    tdRequestPayload: self.tdRequestPayload,
                    loanRequestPayload: self.loanRequestPayload,
                    lmRequestPayload: self.lmRequestPayload,
                    virtualRequestPayload: self.virtualRequestPayload,
                    vamEnabledRealAccRequestPayload: self.vamEnabledRealAccRequestPayload,
                    isBatchEnable: self.isBatchEnable,
                    selectedCasaPolicy: self.selectedCasaPolicy,
                    selectedTdPolicy: self.selectedTdPolicy,
                    selectedLoanPolicy: self.selectedLoanPolicy,
                    selectedLMPolicy: self.selectedLMPolicy,
                    selectedVirtualPolicy: self.selectedVirtualPolicy,
                    selectedVamEnabledRealAccPolicy: self.selectedVamEnabledRealAccPolicy,
                    selectedTdPolicyChecked: self.selectedTdPolicyChecked,
                    selectedLoanPolicyChecked: self.selectedLoanPolicyChecked,
                    selectedCasaPolicyChecked: self.selectedCasaPolicyChecked,
                    selectedLMPolicyChecked: self.selectedLMPolicyChecked,
                    selectedVirtualPolicyChecked: self.selectedVirtualPolicyChecked,
                    selectedVERPolicyChecked: self.selectedVERPolicyChecked,
                    transactionNames: self.transactionNames,
                    createObservables: self.createObservables,
                    casaAllowedButtonsPressed: self.casaAllowedButtonsPressed,
                    tdAllowedButtonsPressed: self.tdAllowedButtonsPressed,
                    loanAllowedButtonsPressed: self.loanAllowedButtonsPressed,
                    lmAllowedButtonsPressed: self.lmAllowedButtonsPressed,
                    virtualAllowedButtonsPressed: self.virtualAllowedButtonsPressed,
                    vamEnabledRealAllowedButtonsPressed: self.vamEnabledRealAllowedButtonsPressed,
                    backForPartyAccess: self.backForPartyAccess,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    maskedPartyId: self.maskedPartyId,
                    applyAllTransactionsCasaFlag: self.applyAllTransactionsCasaFlag,
                    applyAllTransactionsTdFlag: self.applyAllTransactionsTdFlag,
                    applyAllTransactionsMPFFlag: self.applyAllTransactionsMPFFlag,
                    mapAllFutureCasaAccounts: self.mapAllFutureCasaAccounts,
                    mapAllFutureTdAccounts: self.mapAllFutureTdAccounts,
                    mapAllFutureLerAccounts: self.mapAllFutureLerAccounts,
                    mapAllFutureLonAccounts: self.mapAllFutureLonAccounts,
                    cinNumber: self.cinNumber,
                    displayEditButtons: self.displayEditButtons
                });

                rootParams.dashboard.loadComponent("party-access-exclusion", params);
            } else if (accessLevel === "USER") {
                const params = ko.mapping.toJS({
                    isCorpAdmin: isCorpAdmin,
                    ExclusionModelInstance: self.ExclusionModelInstance,
                    isTDAllowed: self.isTDAllowed,
                    isCasaAllowed: self.isCasaAllowed,
                    isLoanAllowed: self.isLoanAllowed,
                    isLMAllowed: self.isLMAllowed,
                    isVirtualAllowed: self.isVirtualAllowed,
                    isVAMEnabledRealAllowed: self.isVAMEnabledRealAllowed,
                    partyID: self.partyID,
                    indexSelected: self.indexSelected,
                    selectedCasaAccounts: self.selectedCasaAccounts,
                    selectedTdAccounts: self.selectedTdAccounts,
                    selectedLoanAccounts: self.selectedLoanAccounts,
                    selectedLMAccounts: self.selectedLMAccounts,
                    selectedVirtualAccounts: self.selectedVirtualAccounts,
                    selectedVAMEnabledRealAccounts: self.selectedVAMEnabledRealAccounts,
                    fullPartiesCasaAccountList: self.fullPartiesCasaAccountList,
                    fullPartiesTDAccountList: self.fullPartiesTDAccountList,
                    fullPartiesLoanAccountList: self.fullPartiesLoanAccountList,
                    fullPartiesLMAccountList: self.fullPartiesLMAccountList,
                    fullPartiesVAMRealAccountList: self.fullPartiesVAMRealAccountList,
                    fullPartiesVirtualAccountList: self.fullPartiesVirtualAccountList,
                    fullCasaAccountList: self.fullCasaAccountList,
                    fulltdAccountList: self.fulltdAccountList,
                    fullloanAccountList: self.fullloanAccountList,
                    fullLMAccountList: self.fullLMAccountList,
                    fullVirtualAccountList: self.fullVirtualAccountList,
                    fullVAMEnabledRealAccountList: self.fullVAMEnabledRealAccountList,
                    isAccessCreated: self.isAccessCreated,
                    showEditableForm: self.showEditableForm,
                    transactionName: self.transactionName,
                    editBackFromReview: self.editBackFromReview,
                    casaExclusionAccountNumberList: self.casaExclusionAccountNumberList,
                    tdExclusionAccountNumberList: self.tdExclusionAccountNumberList,
                    loanExclusionAccountNumberList: self.loanExclusionAccountNumberList,
                    lmExclusionAccountNumberList: self.lmExclusionAccountNumberList,
                    virtualExclusionAccountNumberList: self.virtualExclusionAccountNumberList,
                    vamEnabledRealExclusionAccountNumberList: self.vamEnabledRealExclusionAccountNumberList,
                    updatedCASAExclusionNumberList: self.updatedCASAExclusionNumberList,
                    updatedTDExclusionNumberList: self.updatedTDExclusionNumberList,
                    updatedLOANExclusionNumberList: self.updatedLOANExclusionNumberList,
                    updatedLMExclusionNumberList: self.updatedLMExclusionNumberList,
                    updatedVirtualExclusionNumberList: self.updatedVirtualExclusionNumberList,
                    updatedVAMEnabledRealExclusionNumberList: self.updatedVAMEnabledRealExclusionNumberList,
                    selectedUserId: self.selectedUserId,
                    partyName: self.partyName,
                    showConfirmationForCreate: self.showConfirmationForCreate,
                    showModuleToMap: self.showModuleToMap,
                    selectedUserName: self.selectedUserName,
                    loanAccountAccessId: self.loanAccountAccessId,
                    casaAccountAccessId: self.casaAccountAccessId,
                    tdAccountAccessId: self.tdAccountAccessId,
                    lmAccountAccessId: self.lmAccountAccessId,
                    virtualAccountAccessId: self.virtualAccountAccessId,
                    vamEnabledRealAccountAccessId: self.vamEnabledRealAccountAccessId,
                    casaAccountNumbersArray: self.casaAccountNumbersArray,
                    tdAccountNumbersArray: self.tdAccountNumbersArray,
                    loanAccountNumbersArray: self.loanAccountNumbersArray,
                    lmAccountNumbersArray: self.lmAccountNumbersArray,
                    virtualAccountNumbersArray: self.virtualAccountNumbersArray,
                    vamEnabledRealAccountNumbersArray: self.vamEnabledRealAccountNumbersArray,
                    showDeleteButton: self.showDeleteButton,
                    accessLevel: self.accessLevel,
                    highlightedTab: self.highlightedTab,
                    selectedModule: self.selectedModule,
                    casaAccountTabVisited: self.casaAccountTabVisited,
                    loanAccountTabVisited: self.loanAccountTabVisited,
                    tdAccountTabVisited: self.tdAccountTabVisited,
                    lmAccountTabVisited: self.lmAccountTabVisited,
                    virtualAccountTabVisited: self.virtualAccountTabVisited,
                    vamEnabledRealAccountTabVisited: self.vamEnabledRealAccountTabVisited,
                    selectedAccountsResources: self.selectedAccountsResources,
                    editButtonPressed: self.editButtonPressed,
                    parentAccessLevel: self.parentAccessLevel,
                    disableAccountSelection: self.disableAccountSelection,
                    highlightedTabTrans: self.highlightedTabTrans,
                    casaTransactionTabVisited: self.casaTransactionTabVisited,
                    loanTransactionTabVisited: self.loanTransactionTabVisited,
                    tdTransactionTabVisited: self.tdTransactionTabVisited,
                    lmTransactionTabVisited: self.lmTransactionTabVisited,
                    virtualTransactionTabVisited: self.virtualTransactionTabVisited,
                    vamEnabledRealAccTransactionTabVisited: self.vamEnabledRealAccTransactionTabVisited,
                    isAccessUpdated: self.isAccessUpdated,
                    resourceListCasa: self.resourceListCasa,
                    resourceListTD: self.resourceListTD,
                    resourceListLON: self.resourceListLON,
                    resourceListLM: self.resourceListLM,
                    resourceListVRA: self.resourceListVRA,
                    resourceListVER: self.resourceListVER,
                    casaRequestPayload: self.casaRequestPayload,
                    tdRequestPayload: self.tdRequestPayload,
                    loanRequestPayload: self.loanRequestPayload,
                    lmRequestPayload: self.lmRequestPayload,
                    virtualRequestPayload: self.virtualRequestPayload,
                    vamEnabledRealAccRequestPayload: self.vamEnabledRealAccRequestPayload,
                    isBatchEnable: self.isBatchEnable,
                    createObservables: self.createObservables,
                    backForPartyAccess: self.backForPartyAccess,
                    selectedCasaPolicy: self.selectedCasaPolicy,
                    selectedTdPolicy: self.selectedTdPolicy,
                    selectedLoanPolicy: self.selectedLoanPolicy,
                    selectedLMPolicy: self.selectedLMPolicy,
                    selectedVirtualPolicy: self.selectedVirtualPolicy,
                    selectedVamEnabledRealAccPolicy: self.selectedVamEnabledRealAccPolicy,
                    selectedTdPolicyChecked: self.selectedTdPolicyChecked,
                    selectedLoanPolicyChecked: self.selectedLoanPolicyChecked,
                    selectedCasaPolicyChecked: self.selectedCasaPolicyChecked,
                    selectedLMPolicyChecked: self.selectedLMPolicyChecked,
                    selectedVirtualPolicyChecked: self.selectedVirtualPolicyChecked,
                    selectedVERPolicyChecked: self.selectedVERPolicyChecked,
                    lmAllowedButtonsPressed: self.lmAllowedButtonsPressed,
                    casaAllowedButtonsPressed: self.casaAllowedButtonsPressed,
                    tdAllowedButtonsPressed: self.tdAllowedButtonsPressed,
                    loanAllowedButtonsPressed: self.loanAllowedButtonsPressed,
                    virtualAllowedButtonsPressed: self.virtualAllowedButtonsPressed,
                    vamEnabledRealAllowedButtonsPressed: self.vamEnabledRealAllowedButtonsPressed,
                    loadUserListComponent: self.loadUserListComponent,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    cameBack: self.cameBack,
                    maskedPartyId: self.maskedPartyId,
                    copyAccountAccessSettingUserSelected: self.copyAccountAccessSettingUserSelected[self.indexSelected()],
                    copyAccountAccessSettingSelected: self.copyAccountAccessSettingSelected,
                    cinNumber: self.cinNumber,
                    displayEditButtons: self.displayEditButtons
                });

                rootParams.dashboard.loadComponent("user-access-exclusion", params);
            } else if (accessLevel === "LINKAGE") {

                if (self.fullPartiesCasaAccountList()[index].length > 0) {
                    self.mapAllFutureCasaAccounts([self.fullPartiesCasaAccountList()[index][0].mapAllFutureCasaAccounts]);
                } else {
                    self.mapAllFutureCasaAccounts(["N"]);
                }

                if (self.fullPartiesTDAccountList()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.fullPartiesTDAccountList()[index][0].mapAllFutureTdAccounts]);
                } else {
                    self.mapAllFutureTdAccounts(["N"]);
                }

                if (self.fullPartiesLMAccountList()[index].length > 0) {
                    self.mapAllFutureLerAccounts([self.fullPartiesLMAccountList()[index][0].mapAllFutureLerAccounts]);
                } else {
                    self.mapAllFutureLerAccounts(["N"]);
                }

                if (self.fullPartiesLoanAccountList()[index].length > 0) {
                    self.mapAllFutureLonAccounts([self.fullPartiesLoanAccountList()[index][0].mapAllFutureLonAccounts]);
                } else {
                    self.mapAllFutureLonAccounts(["N"]);
                }

                if (self.fullPartiesVAMRealAccountList()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.fullPartiesVAMRealAccountList()[index][0].mapAllFutureTdAccounts]);
                }

                if (self.fullPartiesVirtualAccountList()[index].length > 0) {
                    self.mapAllFutureTdAccounts([self.fullPartiesVirtualAccountList()[index][0].mapAllFutureTdAccounts]);
                }

                const params = ko.mapping.toJS({
                    isCorpAdmin: isCorpAdmin,
                    ExclusionModelInstance: self.ExclusionModelInstance,
                    isTDAllowed: self.isTDAllowed,
                    isCasaAllowed: self.isCasaAllowed,
                    isLoanAllowed: self.isLoanAllowed,
                    isLMAllowed: self.isLMAllowed,
                    isVirtualAllowed: self.isVirtualAllowed,
                    isVAMEnabledRealAllowed: self.isVAMEnabledRealAllowed,
                    partyID: self.partyID,
                    indexSelected: self.indexSelected,
                    selectedCasaAccounts: self.selectedCasaAccounts,
                    selectedTdAccounts: self.selectedTdAccounts,
                    selectedLoanAccounts: self.selectedLoanAccounts,
                    selectedLMAccounts: self.selectedLMAccounts,
                    selectedVirtualAccounts: self.selectedVirtualAccounts,
                    selectedVAMEnabledRealAccounts: self.selectedVAMEnabledRealAccounts,
                    fullPartiesCasaAccountList: self.fullPartiesCasaAccountList,
                    fullPartiesTDAccountList: self.fullPartiesTDAccountList,
                    fullPartiesLoanAccountList: self.fullPartiesLoanAccountList,
                    fullPartiesLMAccountList: self.fullPartiesLMAccountList,
                    fullPartiesVAMRealAccountList: self.fullPartiesVAMRealAccountList,
                    fullPartiesVirtualAccountList: self.fullPartiesVirtualAccountList,
                    fullCasaAccountList: self.fullCasaAccountList,
                    fulltdAccountList: self.fulltdAccountList,
                    fullloanAccountList: self.fullloanAccountList,
                    fullLMAccountList: self.fullLMAccountList,
                    fullVirtualAccountList: self.fullVirtualAccountList,
                    fullVAMEnabledRealAccountList: self.fullVAMEnabledRealAccountList,
                    isAccessCreated: self.isAccessCreated,
                    showEditableForm: self.showEditableForm,
                    transactionName: self.transactionName,
                    editBackFromReview: self.editBackFromReview,
                    casaExclusionAccountNumberList: self.casaExclusionAccountNumberList,
                    tdExclusionAccountNumberList: self.tdExclusionAccountNumberList,
                    loanExclusionAccountNumberList: self.loanExclusionAccountNumberList,
                    lmExclusionAccountNumberList: self.lmExclusionAccountNumberList,
                    virtualExclusionAccountNumberList: self.virtualExclusionAccountNumberList,
                    vamEnabledRealExclusionAccountNumberList: self.vamEnabledRealExclusionAccountNumberList,
                    updatedCASAExclusionNumberList: self.updatedCASAExclusionNumberList,
                    updatedTDExclusionNumberList: self.updatedTDExclusionNumberList,
                    updatedLOANExclusionNumberList: self.updatedLOANExclusionNumberList,
                    updatedLMExclusionNumberList: self.updatedLMExclusionNumberList,
                    updatedVirtualExclusionNumberList: self.updatedVirtualExclusionNumberList,
                    updatedVAMEnabledRealExclusionNumberList: self.updatedVAMEnabledRealExclusionNumberList,
                    selectedUserId: self.selectedUserId,
                    partyName: self.partyName,
                    showConfirmationForCreate: self.showConfirmationForCreate,
                    showModuleToMap: self.showModuleToMap,
                    selectedUserName: self.selectedUserName,
                    loanAccountAccessId: self.loanAccountAccessId,
                    casaAccountAccessId: self.casaAccountAccessId,
                    tdAccountAccessId: self.tdAccountAccessId,
                    lmAccountAccessId: self.lmAccountAccessId,
                    virtualAccountAccessId: self.virtualAccountAccessId,
                    vamEnabledRealAccountAccessId: self.vamEnabledRealAccountAccessId,
                    casaAccountNumbersArray: self.casaAccountNumbersArray,
                    tdAccountNumbersArray: self.tdAccountNumbersArray,
                    loanAccountNumbersArray: self.loanAccountNumbersArray,
                    lmAccountNumbersArray: self.lmAccountNumbersArray,
                    virtualAccountNumbersArray: self.virtualAccountNumbersArray,
                    vamEnabledRealAccountNumbersArray: self.vamEnabledRealAccountNumbersArray,
                    showDeleteButton: self.showDeleteButton,
                    accessLevel: self.accessLevel,
                    highlightedTab: self.highlightedTab,
                    selectedModule: self.selectedModule,
                    casaAccountTabVisited: self.casaAccountTabVisited,
                    loanAccountTabVisited: self.loanAccountTabVisited,
                    tdAccountTabVisited: self.tdAccountTabVisited,
                    lmAccountTabVisited: self.lmAccountTabVisited,
                    virtualAccountTabVisited: self.virtualAccountTabVisited,
                    vamEnabledRealAccountTabVisited: self.vamEnabledRealAccountTabVisited,
                    selectedAccountsResources: self.selectedAccountsResources,
                    editButtonPressed: self.editButtonPressed,
                    parentAccessLevel: self.parentAccessLevel,
                    disableAccountSelection: self.disableAccountSelection,
                    highlightedTabTrans: self.highlightedTabTrans,
                    casaTransactionTabVisited: self.casaTransactionTabVisited,
                    loanTransactionTabVisited: self.loanTransactionTabVisited,
                    tdTransactionTabVisited: self.tdTransactionTabVisited,
                    lmTransactionTabVisited: self.lmTransactionTabVisited,
                    virtualTransactionTabVisited: self.virtualTransactionTabVisited,
                    vamEnabledRealAccTransactionTabVisited: self.vamEnabledRealAccTransactionTabVisited,
                    isAccessUpdated: self.isAccessUpdated,
                    resourceListCasa: self.resourceListCasa,
                    resourceListTD: self.resourceListTD,
                    resourceListLON: self.resourceListLON,
                    resourceListLM: self.resourceListLM,
                    resourceListVRA: self.resourceListVRA,
                    resourceListVER: self.resourceListVER,
                    casaRequestPayload: self.casaRequestPayload,
                    tdRequestPayload: self.tdRequestPayload,
                    loanRequestPayload: self.loanRequestPayload,
                    lmRequestPayload: self.lmRequestPayload,
                    virtualRequestPayload: self.virtualRequestPayload,
                    vamEnabledRealAccRequestPayload: self.vamEnabledRealAccRequestPayload,
                    isBatchEnable: self.isBatchEnable,
                    linkedPartyId: self.linkedPartyId,
                    linkedpartyName: self.linkedpartyName,
                    selectedCasaPolicy: self.selectedCasaPolicy,
                    selectedTdPolicy: self.selectedTdPolicy,
                    selectedLoanPolicy: self.selectedLoanPolicy,
                    selectedLMPolicy: self.selectedLMPolicy,
                    selectedVirtualPolicy: self.selectedVirtualPolicy,
                    selectedVamEnabledRealAccPolicy: self.selectedVamEnabledRealAccPolicy,
                    selectedTdPolicyChecked: self.selectedTdPolicyChecked,
                    selectedCasaPolicyChecked: self.selectedCasaPolicyChecked,
                    selectedLMPolicyChecked: self.selectedLMPolicyChecked,
                    selectedLoanPolicyChecked: self.selectedLoanPolicyChecked,
                    selectedVirtualPolicyChecked: self.selectedVirtualPolicyChecked,
                    selectedVERPolicyChecked: self.selectedVERPolicyChecked,
                    transactionNames: self.transactionNames,
                    createObservables: self.createObservables,
                    casaAllowedButtonsPressed: self.casaAllowedButtonsPressed,
                    tdAllowedButtonsPressed: self.tdAllowedButtonsPressed,
                    loanAllowedButtonsPressed: self.loanAllowedButtonsPressed,
                    lmAllowedButtonsPressed: self.lmAllowedButtonsPressed,
                    virtualAllowedButtonsPressed: self.virtualAllowedButtonsPressed,
                    vamEnabledRealAllowedButtonsPressed: self.vamEnabledRealAllowedButtonsPressed,
                    backForPartyAccess: self.backForPartyAccess,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    maskedPartyId: self.maskedPartyId,
                    mapAllFutureCasaAccounts: self.mapAllFutureCasaAccounts,
                    mapAllFutureTdAccounts: self.mapAllFutureTdAccounts,
                    mapAllFutureLerAccounts: self.mapAllFutureLerAccounts,
                    mapAllFutureLonAccounts: self.mapAllFutureLonAccounts,
                    cinNumber: self.cinNumber,
                    displayEditButtons: self.displayEditButtons
                });

                rootParams.dashboard.loadComponent("linked-party-access-exclusion", params);
            } else if (accessLevel === "USERLINKAGE") {
                const params = ko.mapping.toJS({
                    isCorpAdmin: isCorpAdmin,
                    ExclusionModelInstance: self.ExclusionModelInstance,
                    isTDAllowed: self.isTDAllowed,
                    isCasaAllowed: self.isCasaAllowed,
                    isLoanAllowed: self.isLoanAllowed,
                    isLMAllowed: self.isLMAllowed,
                    isVirtualAllowed: self.isVirtualAllowed,
                    isVAMEnabledRealAllowed: self.isVAMEnabledRealAllowed,
                    partyID: self.partyID,
                    indexSelected: self.indexSelected,
                    selectedCasaAccounts: self.selectedCasaAccounts,
                    selectedTdAccounts: self.selectedTdAccounts,
                    selectedLoanAccounts: self.selectedLoanAccounts,
                    fullPartiesCasaAccountList: self.fullPartiesCasaAccountList,
                    fullPartiesTDAccountList: self.fullPartiesTDAccountList,
                    fullPartiesLoanAccountList: self.fullPartiesLoanAccountList,
                    fullPartiesLMAccountList: self.fullPartiesLMAccountList,
                    fullPartiesVAMRealAccountList: self.fullPartiesVAMRealAccountList,
                    fullPartiesVirtualAccountList: self.fullPartiesVirtualAccountList,
                    fullCasaAccountList: self.fullCasaAccountList,
                    fulltdAccountList: self.fulltdAccountList,
                    fullloanAccountList: self.fullloanAccountList,
                    fullLMAccountList: self.fullLMAccountList,
                    fullVirtualAccountList: self.fullVirtualAccountList,
                    fullVAMEnabledRealAccountList: self.fullVAMEnabledRealAccountList,
                    selectedLMAccounts: self.selectedLMAccounts,
                    selectedVirtualAccounts: self.selectedVirtualAccounts,
                    selectedVAMEnabledRealAccounts: self.selectedVAMEnabledRealAccounts,
                    isAccessCreated: self.isAccessCreated,
                    showEditableForm: self.showEditableForm,
                    transactionName: self.transactionName,
                    editBackFromReview: self.editBackFromReview,
                    casaExclusionAccountNumberList: self.casaExclusionAccountNumberList,
                    tdExclusionAccountNumberList: self.tdExclusionAccountNumberList,
                    loanExclusionAccountNumberList: self.loanExclusionAccountNumberList,
                    lmExclusionAccountNumberList: self.lmExclusionAccountNumberList,
                    virtualExclusionAccountNumberList: self.virtualExclusionAccountNumberList,
                    vamEnabledRealExclusionAccountNumberList: self.vamEnabledRealExclusionAccountNumberList,
                    updatedCASAExclusionNumberList: self.updatedCASAExclusionNumberList,
                    updatedTDExclusionNumberList: self.updatedTDExclusionNumberList,
                    updatedLOANExclusionNumberList: self.updatedLOANExclusionNumberList,
                    updatedLMExclusionNumberList: self.updatedLMExclusionNumberList,
                    updatedVirtualExclusionNumberList: self.updatedVirtualExclusionNumberList,
                    updatedVAMEnabledRealExclusionNumberList: self.updatedVAMEnabledRealExclusionNumberList,
                    selectedUserId: self.selectedUserId,
                    partyName: self.partyName,
                    showConfirmationForCreate: self.showConfirmationForCreate,
                    showModuleToMap: self.showModuleToMap,
                    selectedUserName: self.selectedUserName,
                    loanAccountAccessId: self.loanAccountAccessId,
                    casaAccountAccessId: self.casaAccountAccessId,
                    tdAccountAccessId: self.tdAccountAccessId,
                    lmAccountAccessId: self.lmAccountAccessId,
                    virtualAccountAccessId: self.virtualAccountAccessId,
                    vamEnabledRealAccountAccessId: self.vamEnabledRealAccountAccessId,
                    casaAccountNumbersArray: self.casaAccountNumbersArray,
                    tdAccountNumbersArray: self.tdAccountNumbersArray,
                    loanAccountNumbersArray: self.loanAccountNumbersArray,
                    virtualAccountNumbersArray: self.virtualAccountNumbersArray,
                    vamEnabledRealAccountNumbersArray: self.vamEnabledRealAccountNumbersArray,
                    lmAccountNumbersArray: self.lmAccountNumbersArray,
                    showDeleteButton: self.showDeleteButton,
                    accessLevel: self.accessLevel,
                    highlightedTab: self.highlightedTab,
                    selectedModule: self.selectedModule,
                    casaAccountTabVisited: self.casaAccountTabVisited,
                    loanAccountTabVisited: self.loanAccountTabVisited,
                    tdAccountTabVisited: self.tdAccountTabVisited,
                    virtualAccountTabVisited: self.virtualAccountTabVisited,
                    vamEnabledRealAccountTabVisited: self.vamEnabledRealAccountTabVisited,
                    lmAccountTabVisited: self.lmAccountTabVisited,
                    selectedAccountsResources: self.selectedAccountsResources,
                    editButtonPressed: self.editButtonPressed,
                    parentAccessLevel: self.parentAccessLevel,
                    disableAccountSelection: self.disableAccountSelection,
                    highlightedTabTrans: self.highlightedTabTrans,
                    casaTransactionTabVisited: self.casaTransactionTabVisited,
                    loanTransactionTabVisited: self.loanTransactionTabVisited,
                    tdTransactionTabVisited: self.tdTransactionTabVisited,
                    virtualTransactionTabVisited: self.virtualTransactionTabVisited,
                    lmTransactionTabVisited: self.lmTransactionTabVisited,
                    vamEnabledRealAccTransactionTabVisited: self.vamEnabledRealAccTransactionTabVisited,
                    isAccessUpdated: self.isAccessUpdated,
                    resourceListCasa: self.resourceListCasa,
                    resourceListTD: self.resourceListTD,
                    resourceListLON: self.resourceListLON,
                    resourceListVER: self.resourceListVER,
                    resourceListLM: self.resourceListLM,
                    casaRequestPayload: self.casaRequestPayload,
                    tdRequestPayload: self.tdRequestPayload,
                    loanRequestPayload: self.loanRequestPayload,
                    virtualRequestPayload: self.virtualRequestPayload,
                    vamEnabledRealAccRequestPayload: self.vamEnabledRealAccRequestPayload,
                    lmRequestPayload: self.lmRequestPayload,
                    isBatchEnable: self.isBatchEnable,
                    linkedPartyId: self.linkedPartyId,
                    linkedpartyName: self.linkedpartyName,
                    casaAllowedButtonsPressed: self.casaAllowedButtonsPressed,
                    selectedCasaPolicy: self.selectedCasaPolicy,
                    selectedTdPolicy: self.selectedTdPolicy,
                    selectedLoanPolicy: self.selectedLoanPolicy,
                    selectedVirtualPolicy: self.selectedVirtualPolicy,
                    selectedVamEnabledRealAccPolicy: self.selectedVamEnabledRealAccPolicy,
                    selectedLMPolicy: self.selectedLMPolicy,
                    selectedCasaPolicyChecked: self.selectedCasaPolicyChecked,
                    createObservables: self.createObservables,
                    selectedTdPolicyChecked: self.selectedTdPolicyChecked,
                    selectedLoanPolicyChecked: self.selectedLoanPolicyChecked,
                    selectedVirtualPolicyChecked: self.selectedVirtualPolicyChecked,
                    selectedVERPolicyChecked: self.selectedVERPolicyChecked,
                    selectedLMPolicyChecked: self.selectedLMPolicyChecked,
                    loadUserListComponent: self.loadUserListComponent,
                    cameBack: self.cameBack,
                    backForPartyAccess: self.backForPartyAccess,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    copyAccountAccessSettingUserSelected: self.copyAccountAccessSettingUserSelected[self.indexSelected()],
                    copyAccountAccessSettingSelected: self.copyAccountAccessSettingSelected,
                    cinNumber: self.cinNumber,
                    displayEditButtons: self.displayEditButtons
                });

                rootParams.dashboard.loadComponent("linked-user-access-exclusion", params);
            }
        };

        self.loadCasaModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("CASA");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.loadLoansModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("LON");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.loadTdModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("TRD");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.loadLMModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("LER");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.loadVERModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("VER");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.loadVRAModule = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            self.selectedModule("VRA");
            self.loadAccountMappingComponent(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
        };

        self.ifloanPayload = function (loanPayload) {
            if (loanPayload) {
                ko.utils.arrayForEach(loanPayload, function (item) {
                    self.newLoanObject = {
                        accountType: "",
                        accountNumber: {
                            value: "",
                            displayValue: ""
                        },
                        accountStatus: "",
                        displayName: "",
                        resourceListLON: [],
                        selectedTask: [],
                        nonSelectedTask: [],
                        currencyCode: "",
                        fullResourceTaskList: []
                    };

                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                        self.newResourceObject = {
                            childTasks: [],
                            name: ""
                        };

                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                            self.newTaskObject = {
                                id: "",
                                name: "",
                                supportedAccountTypes: [],
                                approvalSupported: "",
                                limitRequired: "",
                                moduleType: "",
                                type: "",
                                allowed: ""
                            };

                            self.newTaskObject.id = thisItem.childTask.id;
                            self.newTaskObject.name = thisItem.childTask.name;
                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                            self.newTaskObject.type = thisItem.childTask.type;
                            self.newTaskObject.allowed = thisItem.allowed;

                            if (thisItem.allowed) {
                                self.newLoanObject.selectedTask.push(thisItem.childTask.id);
                            } else {
                                self.newLoanObject.nonSelectedTask.push(thisItem.childTask.id);
                            }

                            self.newLoanObject.fullResourceTaskList.push(thisItem.childTask.id);
                            self.newResourceObject.childTasks.push(self.newTaskObject);
                            self.newResourceObject.name = thisChildTaskItem.name;
                        });

                        self.newLoanObject.resourceListLON.push(self.newResourceObject);
                    });

                    self.newLoanObject.accountType = item.accountType;
                    self.newLoanObject.accountNumber.value = item.accountNumber.value;
                    self.newLoanObject.accountNumber.displayValue = item.accountNumber.displayValue;
                    self.newLoanObject.displayName = item.displayName;
                    self.newLoanObject.currencyCode = item.currencyCode;
                    self.newLoanObject.accountStatus = item.accountStatus;
                    self.resourceListLON([self.newLoanObject.resourceListLON]);
                    self.fullloanAccountList().push(self.newLoanObject);
                });

                self.fullPartiesLoanAccountList().push(self.fullloanAccountList());
                self.accountAccessSummaryObject.totalLonAccts = loanPayload.length;

                let mappedLonCount = 0,
                    x;

                for (x = 0; x < loanPayload.length; x++) {
                    for (let a = 0; a < loanPayload[x].tasks.length; a++) {
                        for (let z = 0; z < loanPayload[x].tasks[a].childTasks.length; z++) {
                            if (loanPayload[x].tasks[a].childTasks[z].allowed) {
                                mappedLonCount++;
                                a = loanPayload[x].tasks.length;
                                break;
                            }
                        }
                    }
                }

                self.accountAccessSummaryObject.mappedLonAccts = mappedLonCount;
            } else {
                self.accountAccessSummaryObject.mappedLonAccts = 0;
                self.accountAccessSummaryObject.totalLonAccts = 0;
            }
        };

        self.checkAccountAccessForUser = function (selecteduserId) {
            ExclusionModel.readAllUserAccountDetails(isCorpAdmin ? partyId.value : self.partyID(), selecteduserId).done(function (data) {
                self.summarydataSource([]);
                self.parentAccessLevel("USER");

                const userAccounts = data.accounts;

                if (userAccounts.length > 0) {
                    ko.utils.arrayForEach(userAccounts, function (partyItem) {
                        self.linkedpartyName("");
                        self.fullCasaAccountList([]);
                        self.fulltdAccountList([]);
                        self.fullloanAccountList([]);
                        self.fullLMAccountList([]);
                        self.fullVAMEnabledRealAccountList([]);
                        self.fullVirtualAccountList([]);
                        self.isAccessCreated(false);
                        self.showEditableForm(false);
                        self.partySetUpNotExists(false);
                        self.Loader = false;

                        if (partyItem.setupInformation === "SETUP_EXISTS") {
                            self.isAccessCreated(true);
                            self.showEditableForm(true);
                            self.partySetUpNotExists(false);
                        } else if (partyItem.setupInformation === "SETUP_NOT_CREATED") {
                            self.isAccessCreated(false);
                            self.showEditableForm(false);
                            self.partySetUpNotExists(false);
                            self.setupNotCreated(true);
                        } else if (partyItem.setupInformation === "PARTY_SETUP_MISSING") {
                            self.partySetUpNotExists(true);
                            self.isAccessCreated(false);
                            self.showEditableForm(false);
                        }

                        if (partyItem.preferenceStatus === "ENABLED") {
                            self.isPreferenceExist(true);
                        } else if (partyItem.preferenceStatus === "NOT_FOUND") {
                            self.isPreferenceExist(false);
                        }

                        self.accessLevel(partyItem.accessLevel);

                        if (partyItem.accessLevel === "USERLINKAGE") {
                            self.isLinkageExist(true);

                            if (partyItem.partyName) {
                                self.linkedpartyName(partyItem.partyName);
                            }

                            self.linkedPartyId(partyItem.party);

                            if (partyItem.preferenceStatus === "DISABLED") {
                                self.parentChannelAccessNotExists(true);
                            }
                        }

                        if (partyItem.accountsList) {
                            const casaPayload = [],
                                tdPayload = [],
                                loanPayload = [],
                                lmPayload = [],
                                verPayload = [],
                                vraPayload = [];
                            let x;

                            for (x = 0; x < partyItem.accountsList.length; x++) {
                                if (partyItem.accountsList[x].accountType === "CSA") {
                                    casaPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "LON") {
                                    loanPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "TRD") {
                                    tdPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "LER") {
                                    lmPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "VER") {
                                    verPayload.push(partyItem.accountsList[x]);
                                }

                                if (partyItem.accountsList[x].accountType === "VRA") {
                                    vraPayload.push(partyItem.accountsList[x]);
                                }
                            }

                            self.isDataRecieved(true);

                            if (casaPayload) {
                                ko.utils.arrayForEach(casaPayload, function (item) {
                                    self.newCasaObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListCasa: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newCasaObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newCasaObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newCasaObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newCasaObject.resourceListCasa.push(self.newResourceObject);
                                    });

                                    self.newCasaObject.accountType = item.accountType;
                                    self.newCasaObject.accountNumber.value = item.accountNumber.value;
                                    self.newCasaObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newCasaObject.displayName = item.displayName;
                                    self.newCasaObject.currencyCode = item.currencyCode;
                                    self.newCasaObject.accountStatus = item.accountStatus;
                                    self.resourceListCasa([self.newCasaObject.resourceListCasa]);
                                    self.fullCasaAccountList().push(self.newCasaObject);
                                });

                                self.fullPartiesCasaAccountList().push(self.fullCasaAccountList());
                                self.accountAccessSummaryObject.totalCasaAccts = casaPayload.length;

                                let mappedCasaCount = 0;

                                for (let i = 0; i < casaPayload.length; i++) {
                                    for (let j = 0; j < casaPayload[i].tasks.length; j++) {
                                        for (let y = 0; y < casaPayload[i].tasks[j].childTasks.length; y++) {
                                            if (casaPayload[i].tasks[j].childTasks[y].allowed) {
                                                mappedCasaCount++;
                                                j = casaPayload[i].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedCasaAccts = mappedCasaCount;
                            } else {
                                self.accountAccessSummaryObject.mappedCasaAccts = 0;
                                self.accountAccessSummaryObject.totalCasaAccts = 0;
                            }

                            if (tdPayload) {
                                ko.utils.arrayForEach(tdPayload, function (item) {
                                    self.newTdObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListTD: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newTdObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newTdObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newTdObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newTdObject.resourceListTD.push(self.newResourceObject);
                                    });

                                    self.newTdObject.accountType = item.accountType;
                                    self.newTdObject.accountNumber.value = item.accountNumber.value;
                                    self.newTdObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newTdObject.displayName = item.displayName;
                                    self.newTdObject.currencyCode = item.currencyCode;
                                    self.newTdObject.accountStatus = item.accountStatus;
                                    self.resourceListTD([self.newTdObject.resourceListTD]);
                                    self.fulltdAccountList().push(self.newTdObject);
                                });

                                self.fullPartiesTDAccountList().push(self.fulltdAccountList());
                                self.accountAccessSummaryObject.totalTrdAccts = tdPayload.length;

                                let mappedtdCount = 0;

                                for (let l = 0; l < tdPayload.length; l++) {
                                    for (let m = 0; m < tdPayload[l].tasks.length; m++) {
                                        for (let n = 0; n < tdPayload[l].tasks[m].childTasks.length; n++) {
                                            if (tdPayload[l].tasks[m].childTasks[n].allowed) {
                                                mappedtdCount++;
                                                m = tdPayload[l].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedTrdAccts = mappedtdCount;
                            } else {
                                self.accountAccessSummaryObject.mappedTrdAccts = 0;
                                self.accountAccessSummaryObject.totalTrdAccts = 0;
                            }

                            if (verPayload) {
                                ko.utils.arrayForEach(verPayload, function (item) {
                                    self.newVERObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListVER: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: []
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newVERObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newVERObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newVERObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newVERObject.resourceListVER.push(self.newResourceObject);
                                    });

                                    self.newVERObject.accountType = item.accountType;
                                    self.newVERObject.accountNumber.value = item.accountNumber.value;
                                    self.newVERObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newVERObject.displayName = item.displayName;
                                    self.newVERObject.currencyCode = item.currencyCode;
                                    self.newVERObject.accountStatus = item.accountStatus;
                                    self.resourceListVER([self.newVERObject.resourceListVER]);
                                    self.fullVAMEnabledRealAccountList().push(self.newVERObject);
                                });

                                self.fullPartiesVAMRealAccountList().push(self.fullVAMEnabledRealAccountList());
                                self.accountAccessSummaryObject.totalVERAccounts = verPayload.length;

                                let mappedVERCount = 0,
                                    x;

                                for (x = 0; x < verPayload.length; x++) {
                                    for (let a = 0; a < verPayload[x].tasks.length; a++) {
                                        for (let z = 0; z < verPayload[x].tasks[a].childTasks.length; z++) {
                                            if (verPayload[x].tasks[a].childTasks[z].allowed) {
                                                mappedVERCount++;
                                                a = verPayload[x].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedVERAccts = mappedVERCount;
                            } else {
                                self.accountAccessSummaryObject.mappedVERAccts = 0;
                                self.accountAccessSummaryObject.totalVERAccounts = 0;
                            }

                            if (lmPayload) {
                                ko.utils.arrayForEach(lmPayload, function (item) {
                                    self.newLMObject = {
                                        accountType: "",
                                        accountNumber: {
                                            value: "",
                                            displayValue: ""
                                        },
                                        accountStatus: "",
                                        displayName: "",
                                        resourceListLM: [],
                                        selectedTask: [],
                                        nonSelectedTask: [],
                                        currencyCode: "",
                                        fullResourceTaskList: [],
                                        virtual: ""
                                    };

                                    ko.utils.arrayForEach(item.tasks, function (thisChildTaskItem) {
                                        self.newResourceObject = {
                                            childTasks: [],
                                            name: ""
                                        };

                                        ko.utils.arrayForEach(thisChildTaskItem.childTasks, function (thisItem) {
                                            self.newTaskObject = {
                                                id: "",
                                                name: "",
                                                supportedAccountTypes: [],
                                                approvalSupported: "",
                                                limitRequired: "",
                                                moduleType: "",
                                                type: "",
                                                allowed: ""
                                            };

                                            self.newTaskObject.id = thisItem.childTask.id;
                                            self.newTaskObject.name = thisItem.childTask.name;
                                            self.newTaskObject.supportedAccountTypes = thisItem.childTask.supportedAccountTypes;
                                            self.newTaskObject.approvalSupported = thisItem.childTask.approvalSupported;
                                            self.newTaskObject.limitRequired = thisItem.childTask.limitRequired;
                                            self.newTaskObject.moduleType = thisItem.childTask.moduleType;
                                            self.newTaskObject.type = thisItem.childTask.type;
                                            self.newTaskObject.allowed = thisItem.allowed;

                                            if (thisItem.allowed) {
                                                self.newLMObject.selectedTask.push(thisItem.childTask.id);
                                            } else {
                                                self.newLMObject.nonSelectedTask.push(thisItem.childTask.id);
                                            }

                                            self.newLMObject.fullResourceTaskList.push(thisItem.childTask.id);
                                            self.newResourceObject.childTasks.push(self.newTaskObject);
                                            self.newResourceObject.name = thisChildTaskItem.name;
                                        });

                                        self.newLMObject.resourceListLM.push(self.newResourceObject);
                                    });

                                    self.newLMObject.accountType = item.accountType;
                                    self.newLMObject.accountNumber.value = item.accountNumber.value;
                                    self.newLMObject.accountNumber.displayValue = item.accountNumber.displayValue;
                                    self.newLMObject.displayName = item.displayName;
                                    self.newLMObject.currencyCode = item.currencyCode;
                                    self.newLMObject.accountStatus = item.accountStatus;
                                    self.newLMObject.virtual = item.virtual;
                                    self.resourceListLM([self.newLMObject.resourceListLM]);
                                    self.fullLMAccountList().push(self.newLMObject);
                                });

                                self.fullPartiesLMAccountList().push(self.fullLMAccountList());
                                self.accountAccessSummaryObject.totalLMAccts = lmPayload.length;

                                let mappedLMCount = 0;

                                for (let i = 0; i < lmPayload.length; i++) {
                                    for (let j = 0; j < lmPayload[i].tasks.length; j++) {
                                        for (let y = 0; y < lmPayload[i].tasks[j].childTasks.length; y++) {
                                            if (lmPayload[i].tasks[j].childTasks[y].allowed) {
                                                mappedLMCount++;
                                                j = lmPayload[i].tasks.length;
                                                break;
                                            }
                                        }
                                    }
                                }

                                self.accountAccessSummaryObject.mappedLMAccts = mappedLMCount;
                            } else {
                                self.accountAccessSummaryObject.mappedLMAccts = 0;
                                self.accountAccessSummaryObject.totalLMAccts = 0;
                            }

                            self.ifloanPayload(loanPayload);
                            self.ifvraPayload(vraPayload);

                            const totalMappedAccts = self.accountAccessSummaryObject.mappedLonAccts + self.accountAccessSummaryObject.mappedTrdAccts + self.accountAccessSummaryObject.mappedCasaAccts + self.accountAccessSummaryObject.mappedLMAccts + self.accountAccessSummaryObject.mappedVERAccts + self.accountAccessSummaryObject.mappedVRAaccts;

                            self.mappedAccts(totalMappedAccts);
                        } else {
                            self.isDataRecieved(false);
                        }

                        self.createDataSource();
                    });
                }
            });
        };

        self.showUserAccountAccess = function (data) {
            if (data) {
                self.showPartyValidateComponent(false);
                self.selectedUserId(data.username);

                self.selectedUserName(rootParams.baseModel.format(self.nls.generic.common.name, {
                    firstName: data.firstName,
                    lastName: data.lastName
                }));
            }
        };

        self.placeInitials = function (firstName, lastName) {
            const initial = firstName.charAt(0) + lastName.charAt(0);

            return initial.toUpperCase();
        };

        self.back = function () {
            self.showAccountAccess(false);
            self.showPartyValidateComponent(true);
            self.fullCasaAccountList([]);
            self.fulltdAccountList([]);
            self.fullloanAccountList([]);
            self.fullLMAccountList([]);
            self.fullVirtualAccountList([]);
            self.fullVAMEnabledRealAccountList([]);
            self.fullPartiesCasaAccountList([]);
            self.fullPartiesTDAccountList([]);
            self.fullPartiesLoanAccountList([]);
            self.fullPartiesLMAccountList([]);
            self.fullVirtualAccountList([]);
            self.fullVAMEnabledRealAccountList([]);
            self.loadSummaryTable(false);
            self.loadUserListComponent(false);
            self.ExclusionModelInstance().partyDetails.partyDetailsFetched(false);
            self.ExclusionModelInstance().partyDetails.party.value("");
            self.ExclusionModelInstance().partyDetails.partyName("");
            self.ExclusionModelInstance().partyDetails.additionalDetails("");
            self.isDataRecieved(false);

            if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                rootParams.dashboard.headerName(self.nls.pageTitle.accessManagementParty);
            } else if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
                rootParams.dashboard.headerName(self.userAccessPageTitle());
            }
        };

        self.createDataSource = function () {
            if (self.accessLevel() === "PARTY" || self.accessLevel() === "USER") {
                const summaryData = [{
                    id: "CSA",
                    accountType: self.nls.fieldname.casaMapping,
                    isPreferenceExist: self.isPreferenceExist(),
                    totalAccts: self.accountAccessSummaryObject.totalCasaAccts,
                    mappedAccts: self.accountAccessSummaryObject.mappedCasaAccts
                },
                {
                    id: "TD",
                    accountType: self.nls.fieldname.tdMapping,
                    isPreferenceExist: self.isPreferenceExist(),
                    totalAccts: self.accountAccessSummaryObject.totalTrdAccts,
                    mappedAccts: self.accountAccessSummaryObject.mappedTrdAccts
                }

                    /**,
                    {
                        id: "VER",
                        accountType: self.nls.fieldname.verMapping,
                        totalAccts: self.accountAccessSummaryObject.totalVERAccounts,
                        mappedAccts: self.accountAccessSummaryObject.mappedVERAccts
                    }
                     */

                    /**
                     * ,
                     * {
                        id: "LON",
                        accountType: self.nls.fieldname.loansMapping,
                        totalAccts: self.accountAccessSummaryObject.totalLonAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLonAccts
                    },
                    {
                        id: "LER",
                        accountType: self.nls.fieldname.lmMapping,
                        totalAccts: self.accountAccessSummaryObject.totalLMAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLMAccts
                    },
                    {
                        id: "VER",
                        accountType: self.nls.fieldname.verMapping,
                        totalAccts: self.accountAccessSummaryObject.totalVERAccounts,
                        mappedAccts: self.accountAccessSummaryObject.mappedVERAccts
                    },
                    {
                        id: "VRA",
                        accountType: self.nls.fieldname.vraMapping,
                        totalAccts: self.accountAccessSummaryObject.totalVRAaccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedVRAaccts
                    }
                    **/
                ];

                if (rootParams.baseModel.isMPFMocked !== undefined && rootParams.baseModel.isMPFMocked()) {
                    summaryData.push({
                        id: "VER",
                        accountType: self.nls.fieldname.verMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalVERAccounts,
                        mappedAccts: self.accountAccessSummaryObject.mappedVERAccts
                    });
                }

                if (rootParams.baseModel.isLERMocked !== undefined && rootParams.baseModel.isLERMocked()) {
                    summaryData.push({
                        id: "LER",
                        accountType: self.nls.fieldname.lmMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalLMAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLMAccts
                    });
                }

                if (rootParams.baseModel.isLONMocked !== undefined && rootParams.baseModel.isLONMocked()) {
                    summaryData.push({
                        id: "LON",
                        accountType: self.nls.fieldname.loansMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalLonAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLonAccts
                    });
                }

                self.mappingSummary = new oj.ArrayTableDataSource(summaryData, {
                    idAttribute: "id"
                });

                self.summarydataSource().push({
                    dataSource: self.mappingSummary,
                    isPreferenceExist: self.isPreferenceExist(),
                    isAccessCreated: self.isAccessCreated(),
                    totalMappedAccts: self.mappedAccts(),
                    linkedPartyName: self.linkedpartyName(),
                    linkedPartyId: self.linkedPartyId(),
                    accessLevel: self.accessLevel(),
                    showEditableForm: self.showEditableForm(),
                    partySetUpNotExists: self.partySetUpNotExists(),
                    parentAccessLevel: self.parentAccessLevel(),
                    parentChannelAccessNotExists: self.parentChannelAccessNotExists()
                });

                self.loadSummaryTable(true);
            } else {
                const summaryData = [{
                    id: "CSA",
                    accountType: self.nls.fieldname.casaMapping,
                    isPreferenceExist: self.isPreferenceExist(),
                    totalAccts: self.accountAccessSummaryObject.totalCasaAccts,
                    mappedAccts: self.accountAccessSummaryObject.mappedCasaAccts
                },
                {
                    id: "TD",
                    accountType: self.nls.fieldname.tdMapping,
                    isPreferenceExist: self.isPreferenceExist(),
                    totalAccts: self.accountAccessSummaryObject.totalTrdAccts,
                    mappedAccts: self.accountAccessSummaryObject.mappedTrdAccts
                }
                    /**
                     * ,
                    {
                        id: "LON",
                        accountType: self.nls.fieldname.loansMapping,
                        totalAccts: self.accountAccessSummaryObject.totalLonAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLonAccts
                    }
                    **/
                ];

                if (rootParams.baseModel.isMPFMocked !== undefined && rootParams.baseModel.isMPFMocked()) {
                    summaryData.push({
                        id: "VER",
                        accountType: self.nls.fieldname.verMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalVERAccounts,
                        mappedAccts: self.accountAccessSummaryObject.mappedVERAccts
                    });
                }

                if (rootParams.baseModel.isLERMocked !== undefined && rootParams.baseModel.isLERMocked()) {
                    summaryData.push({
                        id: "LER",
                        accountType: self.nls.fieldname.lmMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalLMAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLMAccts
                    });
                }

                if (rootParams.baseModel.isLONMocked !== undefined && rootParams.baseModel.isLONMocked()) {
                    summaryData.push({
                        id: "LON",
                        accountType: self.nls.fieldname.loansMapping,
                        isPreferenceExist: self.isPreferenceExist(),
                        totalAccts: self.accountAccessSummaryObject.totalLonAccts,
                        mappedAccts: self.accountAccessSummaryObject.mappedLonAccts
                    });
                }

                self.mappingSummary = new oj.ArrayTableDataSource(summaryData, {
                    idAttribute: "id"
                });

                self.summarydataSource().push({
                    dataSource: self.mappingSummary,
                    isPreferenceExist: self.isPreferenceExist(),
                    isAccessCreated: self.isAccessCreated(),
                    totalMappedAccts: self.mappedAccts(),
                    linkedPartyName: self.linkedpartyName(),
                    linkedPartyId: self.linkedPartyId(),
                    accessLevel: self.accessLevel(),
                    showEditableForm: self.showEditableForm(),
                    partySetUpNotExists: self.partySetUpNotExists(),
                    parentAccessLevel: self.parentAccessLevel(),
                    parentChannelAccessNotExists: self.parentChannelAccessNotExists()
                });

                self.loadSummaryTable(true);
            }
        };

        self.goBack = function () {
            self.fullCasaAccountList([]);
            self.fulltdAccountList([]);
            self.fullloanAccountList([]);
            self.fullLMAccountList([]);
            self.fullVirtualAccountList([]);
            self.fullVAMEnabledRealAccountList([]);
            self.selectedTdAccounts([]);
            self.selectedLoanAccounts([]);
            self.selectedLMAccounts([]);
            self.selectedCasaAccounts([]);
            self.selectedVAMEnabledRealAccounts([]);
            self.selectedVirtualAccounts([]);
            self.fullPartiesCasaAccountList([]);
            self.fullPartiesTDAccountList([]);
            self.fullPartiesLoanAccountList([]);
            self.fullPartiesLMAccountList([]);
            self.fullPartiesVAMRealAccountList([]);
            self.fullPartiesVirtualAccountList([]);

            if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                rootParams.dashboard.loadComponent("access-management-base", {});
            } else {
                self.backForPartyAccess(true);
                self.loadUserListComponent(true);

                const params = {
                    accessLevel: self.accessLevel,
                    showPartyValidateComponent: self.showPartyValidateComponent,
                    partyID: self.partyID,
                    partyName: self.partyName,
                    maskedPartyId: self.maskedPartyId,
                    backForPartyAccess: self.backForPartyAccess,
                    loadUserListComponent: self.loadUserListComponent,
                    selectedUserId: self.selectedUserId,
                    createObservables: self.createObservables,
                    userListLoaded: self.userListLoaded,
                    selectedAccountsResources: self.selectedAccountsResources,
                    selectedUserName: self.selectedUserName,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    cinNumber: self.cinNumber
                };

                rootParams.dashboard.loadComponent("user-access-management-base", params);
            }
        };

        if (self.isHthMode()) {
            rootParams.dashboard.headerName(self.userAccessPageTitle());
            self.loadHthSummary();
        } else if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
            self.getAllAccountsCount();
        } else if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
            self.checkAccountAccessForUser(self.selectedUserId());
        }

        self.showAccountAccess(true);

        self.onRowClicked = function (index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel, event) {
            if (event.id === "CSA") {
                self.loadCasaModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            } else if (event.id === "TD") {
                self.loadTdModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            } else if (event.id === "LON") {
                self.loadLoansModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            } else if (event.id === "LER") {
                self.loadLMModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            } else if (event.id === "VER") {
                self.loadVERModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            } else if (event.id === "VRA") {
                self.loadVRAModule(index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            }
        };

        self.fetchAssociatedUserForDisplay = function () {
            ExclusionModel.fetchAssociatedUserForParty(isCorpAdmin ? partyId.value : self.partyID()).done(function (data) {
                self.userDisplayList.removeAll();

                if (data.userDTOList && data.userDTOList.length > 0) {
                    self.userDisplayList(data.userDTOList);

                    ko.utils.arrayForEach(self.userDisplayList(), function (item) {
                        if (item.accountAccessSetupDone === true && item.username !== self.selectedUserId()) {
                            self.accessCreatedUserDisplayList().push({
                                username: item.username,
                                enrolledfor2fa: item.enrolledfor2fa,
                                firstName: item.firstName,
                                accountAccessSetupDone: item.accountAccessSetupDone,
                                lastName: item.lastName,
                                partyID: item.partyId,
                                customer: item.customer,
                                onlyUsername: item.username.split("@")[0]
                            });
                        }
                    });

                    if (self.accessCreatedUserDisplayList().length === 0) {
                        self.showCopyOptions(false);
                    } else {
                        self.showCopyOptions(true);
                    }

                } else {
                    self.showCopyOptions(false);
                }
            });
        };

        self.userDetailsCopyClick = function (row_index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel) {
            if (self.copyAccountAccessSettingUserSelected[row_index] === null || self.copyAccountAccessSettingUserSelected[row_index] === undefined) {
                rootParams.baseModel.showMessages(null, [self.nls.info.pleaseSelectUser], "ERROR");
            } else {
                isAccessCreated = true;
                showEditableForm = true;
                self.loadCasaModule(row_index, linkedpartyName, linkedPartyId, isAccessCreated, showEditableForm, accessLevel);
            }
        };

        self.fetchAssociatedUserForDisplay();

        self.copyAccountAccessSettingSelected.subscribe(function (newValue) {
            if (newValue === "Yes") {
                self.showUsersList(true);
            } else {
                self.showUsersList(false);
            }
        });
    };
});
