define([

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
    "ojs/ojarraytabledatasource"
], function(ko, ExclusionModel, resourceBundle) {
    "use strict";

    return function viewModel(rootParams) {
        const self = this,
            inputParams = rootParams.rootModel;

        inputParams.createObservables(inputParams,
            ["accountAccessSummaryObject", "transactionNames",
                "casaRequestPayload", "tdRequestPayload",
                "loanRequestPayload", "lmRequestPayload", "vamEnabledRealAccRequestPayload", "virtualRequestPayload"
            ]);

        ko.utils.extend(self, inputParams);
        self.nls = resourceBundle;
        self.showAccountAccess = ko.observable(false);
        self.selectedModule = ko.observable();
        self.userList = ko.observableArray([]);
        self.summarydataSource = ko.observableArray();
        self.isLoaded = ko.observable(false);
        self.selectedUserData = ko.observable();
        self.editBackFromReview = ko.observable(false);
        rootParams.baseModel.registerComponent("user-list-details", "common");
        rootParams.baseModel.registerComponent("summary", "account-access-management");
        self.isDataRecieved = ko.observable(false);
        self.tableHeading = ko.observable(self.nls.headers.ownAccount);
        self.linkedpartyName = ko.observable();
        self.linkedPartyId = ko.observable();
        self.linkagetableHeading = ko.observable(self.nls.headers.linkedpartyAccount);
        self.parentChannelAccessNotExists = ko.observable(false);
        self.tableHeading().toUpperCase();
        self.linkagetableHeading().toUpperCase();
        self.isCorpAdmin = ko.observable(false);

        if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
            rootParams.dashboard.headerName(self.nls.pageTitle.accessManagementParty);
        } else if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
            rootParams.dashboard.headerName(rootParams.baseModel.format(self.nls.pageTitle.accessManagement, {
                user: self.nls.navLabels.UserLevel_title
            }));
        }

        const getNewKoModel = function() {
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

        if (rootParams.dashboard.appData.segment === "CORPADMIN") {
            self.backForPartyAccess(false);
        }

        if (isCorpAdmin) {
            self.isCorpAdmin(true);

            ExclusionModel.fetchCorpAdminPartyDetails().done(function(data) {
                self.ExclusionModelInstance().partyDetails.partyName(data.party.personalDetails.fullName);
                self.ExclusionModelInstance().partyDetails.party.value(partyId.value);
                self.ExclusionModelInstance().partyDetails.party.displayValue(partyId.displayValue);
                self.ExclusionModelInstance().partyDetails.cinNumber(data.party.personalDetails.citizenship);
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

        self.selectedUserData.subscribe(function(selectedUserData) {
            self.showUserAccountAccess(selectedUserData);
        });

        self.ExclusionModelInstance().partyDetails.party.value.subscribe(function(updatedPartyID) {
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
                self.cinNumber(self.ExclusionModelInstance().partyDetails.cinNumber());

                if (self.accessLevel() === "USER") {
                    rootParams.dashboard.headerName(rootParams.baseModel.format(self.nls.pageTitle.accessManagement, {
                        user: self.nls.navLabels.UserLevel_title
                    }));

                    self.loadUserListComponent(false);
                    self.loadUserListComponent(true);

                }

                if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                    self.fullCasaAccountList([]);
                    self.fulltdAccountList([]);
                    self.fullloanAccountList([]);
                    self.fullLMAccountList([]);
                    self.selectedTdAccounts([]);
                    self.selectedLoanAccounts([]);
                    self.selectedCasaAccounts([]);
                    self.selectedVirtualAccounts([]);
                    self.selectedVAMEnabledRealAccounts([]);
                    self.selectedLMAccounts([]);
                    self.fullVirtualAccountList([]);
                    self.fullVAMEnabledRealAccountList([]);
                }
            }
        });

        self.getAllUsersList = function() {
            rootParams.dashboard.headerName(rootParams.baseModel.format(self.nls.pageTitle.accessManagement, {
                user: self.nls.navLabels.UserLevel_title
            }));

            ExclusionModel.fetchAssociatedUserForParty(isCorpAdmin ? partyId.value : self.partyID()).done(function(data) {
                self.userList(data.userDTOList);
                self.accessCreatedUserList = ko.observableArray();
                self.nonAccessCreatedUserList = ko.observableArray();

                ko.utils.arrayForEach(self.userList(), function(item) {
                    if (item.accountAccessSetupDone === true) {
                        self.accessCreatedUserList().push(item);
                    } else {
                        self.nonAccessCreatedUserList().push(item);
                    }
                });

                self.accessCreatedUserList.sort(function(left, right) {
                    return left.username === right.username ? 0 : left.username < right.username ? -1 : 1;
                });

                self.nonAccessCreatedUserList.sort(function(left, right) {
                    return left.username === right.username ? 0 : left.username < right.username ? -1 : 1;
                });

                self.userList([]);

                ko.utils.arrayForEach(self.accessCreatedUserList(), function(item) {
                    self.userList().push(item);
                });

                ko.utils.arrayForEach(self.nonAccessCreatedUserList(), function(item) {
                    self.userList().push(item);
                });

                self.userListLoaded(true);
            });
        };

        // Channel routing is decided from server-enriched user metadata. HTH never falls back to
        // the legacy BCO flow when CloseID is missing because that could expose the wrong policy UI.
        self.buildAccessPolicy = function(data) {
            const userChannelType = String(data && data.userChannelType || "BCO").toUpperCase(),
                isHthUser = userChannelType === "HTH";

            return {
                channelMode: isHthUser ? "HTH" : "BCO",
                closeId: isHthUser && data.closeId ? String(data.closeId).trim() : null,
                allowedAccountTypes: isHthUser ? ["CSA"] : null,
                serviceMappingType: isHthUser ? "HTH_API" : "OBDX_TASK"
            };
        };

        self.showUserAccountAccess = function(data) {
            if (data) {
                const accessPolicy = self.buildAccessPolicy(data),
                    selectedUserFullName = data.fullName || rootParams.baseModel.format(self.nls.generic.common.name, {
                        firstName: data.firstName,
                        lastName: data.lastName
                    });

                if (accessPolicy.channelMode === "HTH" && !accessPolicy.closeId) {
                    rootParams.baseModel.showMessages(null, [self.nls.info.hthMissingCloseId], "ERROR");
                    return;
                }

                self.showPartyValidateComponent(false);
                self.selectedUserId(data.username);
                self.selectedUserName(selectedUserFullName);

                const params = ko.mapping.toJS({
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
                    accessLevel: self.accessLevel,
                    showPartyValidateComponent: self.showPartyValidateComponent,
                    partyID: self.partyID,
                    backForPartyAccess: self.backForPartyAccess,
                    loadUserListComponent: self.loadUserListComponent,
                    selectedAccountsResources: self.selectedAccountsResources,
                    maskedPartyId: self.maskedPartyId,
                    partyName: self.partyName,
                    fullCasaAccountList: self.fullCasaAccountList,
                    fulltdAccountList: self.fulltdAccountList,
                    fullloanAccountList: self.fullloanAccountList,
                    fullLMAccountList: self.fullLMAccountList,
                    fullVirtualAccountList: self.fullVirtualAccountList,
                    fullVAMEnabledRealAccountList: self.fullVAMEnabledRealAccountList,
                    selectedTdAccounts: self.selectedTdAccounts,
                    selectedLoanAccounts: self.selectedLoanAccounts,
                    selectedCasaAccounts: self.selectedCasaAccounts,
                    selectedLMAccounts: self.selectedLMAccounts,
                    selectedVirtualAccounts: self.selectedVirtualAccounts,
                    selectedVAMEnabledRealAccounts: self.selectedVAMEnabledRealAccounts,
                    casaFullResourceTaskList: self.casaFullResourceTaskList,
                    tdFullResourceTaskList: self.tdFullResourceTaskList,
                    loanFullResourceTaskList: self.loanFullResourceTaskList,
                    lmFullResourceTaskList: self.lmFullResourceTaskList,
                    virtualFullResourceTaskList: self.virtualFullResourceTaskList,
                    vamEnabledRealFullResourceTaskList: self.vamEnabledRealFullResourceTaskList,
                    selectedUserId: self.selectedUserId,
                    selectedUserName: self.selectedUserName,
                    showEditableForm: self.showEditableForm,
                    resourceListCasa: self.resourceListCasa,
                    resourceListTD: self.resourceListTD,
                    resourceListLON: self.resourceListLON,
                    resourceListLM: self.resourceListLM,
                    resourceListVRA: self.resourceListVRA,
                    resourceListVER: self.resourceListVER,
                    accountAccessSummaryObject: self.accountAccessSummaryObject,
                    isTDAllowed: self.isTDAllowed,
                    isCasaAllowed: self.isCasaAllowed,
                    isLoanAllowed: self.isLoanAllowed,
                    isLMAllowed: self.isLMAllowed,
                    isVirtualAllowed: self.isVirtualAllowed,
                    isVAMEnabledRealAllowed: self.isVAMEnabledRealAllowed,
                    transactionName: self.transactionName,
                    showModuleToMap: self.showModuleToMap,
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
                    casaAccountTabVisited: self.casaAccountTabVisited,
                    loanAccountTabVisited: self.loanAccountTabVisited,
                    tdAccountTabVisited: self.tdAccountTabVisited,
                    lmAccountTabVisited: self.lmAccountTabVisited,
                    virtualAccountTabVisited: self.virtualAccountTabVisited,
                    vamEnabledRealAccountTabVisited: self.vamEnabledRealAccountTabVisited,
                    disableAccountSelection: self.disableAccountSelection,
                    highlightedTabTrans: self.highlightedTabTrans,
                    casaTransactionTabVisited: self.casaTransactionTabVisited,
                    loanTransactionTabVisited: self.loanTransactionTabVisited,
                    tdTransactionTabVisited: self.tdTransactionTabVisited,
                    lmTransactionTabVisited: self.lmTransactionTabVisited,
                    virtualTransactionTabVisited: self.virtualTransactionTabVisited,
                    vamEnabledRealAccTransactionTabVisited: self.vamEnabledRealAccTransactionTabVisited,
                    editButtonPressed: self.editButtonPressed,
                    casaRequestPayload: self.casaRequestPayload,
                    tdRequestPayload: self.tdRequestPayload,
                    loanRequestPayload: self.loanRequestPayload,
                    virtualRequestPayload: self.virtualRequestPayload,
                    lmRequestPayload: self.lmRequestPayload,
                    vamEnabledRealAccRequestPayload: self.vamEnabledRealAccRequestPayload,
                    isBatchEnable: self.isBatchEnable,
                    linkedPartyId: self.linkedPartyId,
                    linkedpartyName: self.linkedpartyName,
                    casaAllowedButtonsPressed: self.casaAllowedButtonsPressed,
                    tdAllowedButtonsPressed: self.tdAllowedButtonsPressed,
                    loanAllowedButtonsPressed: self.loanAllowedButtonsPressed,
                    lmAllowedButtonsPressed: self.lmAllowedButtonsPressed,
                    virtualAllowedButtonsPressed: self.virtualAllowedButtonsPressed,
                    vamEnabledRealAllowedButtonsPressed: self.vamEnabledRealAllowedButtonsPressed,
                    selectedCasaPolicy: self.selectedCasaPolicy,
                    selectedTdPolicy: self.selectedTdPolicy,
                    selectedLoanPolicy: self.selectedLoanPolicy,
                    selectedLMPolicy: self.selectedLMPolicy,
                    selectedVirtualPolicy: self.selectedVirtualPolicy,
                    selectedVamEnabledRealAccPolicy: self.selectedVamEnabledRealAccPolicy,
                    selectedCasaPolicyChecked: self.selectedCasaPolicyChecked,
                    createObservables: self.createObservables,
                    cameBack: self.cameBack,
                    cinNumber: self.cinNumber,
                    channelMode: accessPolicy.channelMode,
                    closeId: accessPolicy.closeId,
                    selectedUserChannelType: data.userChannelType || "BCO",
                    selectedUserFullName: selectedUserFullName,
                    hthAccessPolicy: accessPolicy
                });

                rootParams.dashboard.loadComponent("summary", params);
            }
        };

        self.placeInitials = function(firstName, lastName) {
            const initial = firstName.charAt(0) + lastName.charAt(0);

            return initial.toUpperCase();
        };

        self.back = function() {
            self.showAccountAccess(false);
            self.showPartyValidateComponent(true);
            self.loadUserListComponent(false);
            self.ExclusionModelInstance().partyDetails.partyDetailsFetched(false);
            self.ExclusionModelInstance().partyDetails.party.value("");
            self.ExclusionModelInstance().partyDetails.partyName("");
            self.ExclusionModelInstance().partyDetails.additionalDetails("");
            self.ExclusionModelInstance().partyDetails.cinNumber("");
            self.isDataRecieved(false);

            if (self.accessLevel() === "PARTY" || self.accessLevel() === "LINKAGE") {
                rootParams.dashboard.headerName(self.nls.pageTitle.accessManagementParty);
            } else if (self.accessLevel() === "USER" || self.accessLevel() === "USERLINKAGE") {
                self.selectedUserId("");
                self.backForPartyAccess(false);

                rootParams.dashboard.headerName(rootParams.baseModel.format(self.nls.pageTitle.accessManagement, {
                    user: self.nls.navLabels.UserLevel_title
                }));

            }
        };

    };
});
