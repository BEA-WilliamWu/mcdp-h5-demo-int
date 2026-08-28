define([
    "knockout",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/batch-process-approvals",
    "ojs/ojbutton",
    "ojs/ojinputtext",
    "ojs/ojcheckboxset",
    "ojs/ojvalidationgroup",
    "ojs/ojknockout-validation",
    "framework/elements/api/page-section/loader"
], function (ko, $, BatchProcessApprovalModel, resourceBundle) {
    "use strict";

    const vm = function (rootParams) {
        const self = this;

        ko.utils.extend(self, rootParams.rootModel);
        self.nls = resourceBundle;

        rootParams.baseModel.registerElement("confirm-screen");
        self.toShow = ko.observable(true);
        self.canModify = ko.observable(false);
        self.isApprovalPending = ko.observable();
        self.currentDate = ko.observable();

        self.forceShow = ko.observable(false);
        self.exchangeRate = ko.observable();

        self.enabledMultiApproveButton = ko.observable(false);
        self.tncTransaction = ko.observableArray([]);
        self.tncInterestRate = ko.observableArray([]);
        self.validationTracker = ko.observable();
        self.parentDetailsLoaded = ko.observable(false);
        self.popupTransactionType = ko.observable("default");
        self.selectedTransactionId = ko.observable();
        self.workingWindowTransactions = ko.observableArray();
        self.couponCode = ko.observable();
        self.typhoonFlagTransactions = ko.observableArray();
        self.workingWindowTXNList = ko.observableArray([]);
        self.workingWindowTXNListWithDates = ko.observableArray([]);
        self.showApprovalButtons = self.showApprovalButtons ? self.showApprovalButtons : ko.observable(true);
        self.tdTXNTnC = ko.observable(false);
        self.tdTXNList = ko.observableArray([]);
        self.suspiciousPayeeTimeout = ko.observable(false);
        self.suspiciousAlertTimeout = ko.observable(false);
        self.suspiciousAlertTimer = ko.observable(10);

        self.suspiciousAlertTimerComputed = ko.computed(function() {
            return self.suspiciousAlertTimer() === 10 ? `00:${self.suspiciousAlertTimer()}` : `00:0${self.suspiciousAlertTimer()}`;
        });

        self.isSuspiciousPayeeCheckEnabled = ko.observable(true);
        self.isSuspiciousPayeeCheckRequired = ko.observable(true);
        self.isSuspiciousPayeeCheckDone = ko.observable(false);
        self.isSuspiciousIndicator = ko.observable(false);
        self.outwardValidationFlag = ko.observable("N");
        self.deleteFlag = ko.observable(false);
        self.canReinitiate = ko.observable(false);
        self.canCancelTransaction = ko.observable(false);

        const PRE_DESIGNATED_LOCAL_CHATS_FT = "PC_F_CRNDFT", PRE_DESIGNATED_LOCAL_FPS_FT = "PC_F_CRNDFT_FPS", NON_DESIGNATED_LOCAL_CHATS_FT = "PC_F_GCRNDFT", NON_DESIGNATED_LOCAL_FPS_FT = "PC_F_GCRNDFT_FPS", PRE_DESIGNATED_OVERSEAS_FT = "PC_F_CRNINFT", NON_DESIGNATED_OVERSEAS_FT = "PC_F_GCRNINFT", PRE_DESIGNATED_BEA_HK_FT = "PC_F_CRNIFT", NON_DESIGNATED_BEA_HK_FT = "PC_F_GCRNIFT", PRE_DESIGNATED_BEA_CHINA_FT = "PC_F_CRNBCT", NON_DESIGNATED_BEA_CHINA_FT = "PC_F_GCRNBCT", OWN_ACCOUNT_TRANSFER = "PC_F_CRNSFT", SI_PRE_DESIGNATED_LOCAL_CHATS_FT = "PC_F_CRNDFT_SI", SI_PRE_DESIGNATED_LOCAL_FPS_FT = "PC_F_CRNDFT_FPS_SI", SI_NON_DESIGNATED_LOCAL_CHATS_FT = "PC_F_GCRNDFT_SI", SI_NON_DESIGNATED_LOCAL_FPS_FT = "PC_F_GCRNDFT_FPS_SI", SI_PRE_DESIGNATED_OVERSEAS_FT = "PC_F_CRNINFT_SI", SI_NON_DESIGNATED_OVERSEAS_FT = "PC_F_GCRNINFT_SI", SI_PRE_DESIGNATED_BEA_HK_FT = "PC_F_CRNIFT_SI", SI_NON_DESIGNATED_BEA_HK_FT = "PC_F_GCRNIFT_SI", SI_PRE_DESIGNATED_BEA_CHINA_FT = "PC_F_CRNBCT_SI", SI_NON_DESIGNATED_BEA_CHINA_FT = "PC_F_GCRNBCT_SI", SI_OWN_ACCOUNT_TRANSFER = "PC_F_CRNSFT_SI", AUTOPAY = "BU_AUTOPAY_CREATE", COLLECTION = "BU_COLLECTION_CREATE", PAYROLL ="BU_PAYROLL_CREATE", FX_TRANSFER = "FT_F_PFR", TIME_DEPOSIT = "TD_F_OTD", FILE_UPLOAD = "FU_F_APC", reinitiateTaskCodeList = [PRE_DESIGNATED_LOCAL_CHATS_FT, PRE_DESIGNATED_LOCAL_FPS_FT, NON_DESIGNATED_LOCAL_CHATS_FT, NON_DESIGNATED_LOCAL_FPS_FT, PRE_DESIGNATED_OVERSEAS_FT, NON_DESIGNATED_OVERSEAS_FT, PRE_DESIGNATED_BEA_HK_FT, NON_DESIGNATED_BEA_HK_FT, PRE_DESIGNATED_BEA_CHINA_FT, NON_DESIGNATED_BEA_CHINA_FT, OWN_ACCOUNT_TRANSFER, SI_PRE_DESIGNATED_LOCAL_CHATS_FT, SI_PRE_DESIGNATED_LOCAL_FPS_FT, SI_NON_DESIGNATED_LOCAL_CHATS_FT, SI_NON_DESIGNATED_LOCAL_FPS_FT, SI_PRE_DESIGNATED_OVERSEAS_FT, SI_NON_DESIGNATED_OVERSEAS_FT, SI_PRE_DESIGNATED_BEA_HK_FT, SI_NON_DESIGNATED_BEA_HK_FT, SI_PRE_DESIGNATED_BEA_CHINA_FT, SI_NON_DESIGNATED_BEA_CHINA_FT,SI_OWN_ACCOUNT_TRANSFER, AUTOPAY, COLLECTION, PAYROLL, FX_TRANSFER, TIME_DEPOSIT, FILE_UPLOAD], scheduledTaskCodeList = [SI_PRE_DESIGNATED_LOCAL_CHATS_FT, SI_PRE_DESIGNATED_LOCAL_FPS_FT, SI_NON_DESIGNATED_LOCAL_CHATS_FT, SI_NON_DESIGNATED_LOCAL_FPS_FT, SI_PRE_DESIGNATED_OVERSEAS_FT, SI_NON_DESIGNATED_OVERSEAS_FT, SI_PRE_DESIGNATED_BEA_HK_FT, SI_NON_DESIGNATED_BEA_HK_FT, SI_PRE_DESIGNATED_BEA_CHINA_FT, SI_NON_DESIGNATED_BEA_CHINA_FT, SI_OWN_ACCOUNT_TRANSFER];

        if (self.params && self.params.approvalDetails && (self.params.approvalDetails.status === "APPROVED" || self.params.approvalDetails.status === "REJECTED" || self.params.approvalDetails.status === "EXPIRED") && reinitiateTaskCodeList.includes(self.params.taskCode)) {
            self.canReinitiate(true);
        }

        if (self.params && scheduledTaskCodeList.includes(self.params.taskCode)) {
            self.params.transferwhen = "later";
        }

        if(rootParams.rootModel.transactionDetails && (rootParams.rootModel.taskForApproval.initName === "adhoc-payments-generic" || rootParams.rootModel.taskForApproval.initName === "generic-money-transfer")){
            for( let i = 0; i < rootParams.rootModel.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray[0].nameValuePairDTOArray.length; i++){
                if(rootParams.rootModel.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray[0].nameValuePairDTOArray[i].genericName === "outwardValidationFlag"){
                    self.outwardValidationFlag = rootParams.rootModel.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray[0].nameValuePairDTOArray[i].value;
                }

            }
        }

        if (rootParams.rootModel.params && rootParams.rootModel.params.triggeredFromActivityLog
            && rootParams.rootModel.params.triggeredFromActivityLog()
            && self.params && self.params.approvalDetails
            && ((self.pendingModification && self.pendingModification()) || (self.pendingApproval && self.pendingApproval()) || self.params.approvalDetails.status === "PENDING_APPROVAL" || self.params.approvalDetails.status === "MODIFICATION_REQUESTED")) {
            self.canCancelTransaction(true);
        }

        if (rootParams.rootModel.taskForApproval && (rootParams.rootModel.taskForApproval.initName === "corp-auto-pay" || rootParams.rootModel.taskForApproval.initName === "corp-collection" || rootParams.rootModel.taskForApproval.initName === "corp-payroll") && rootParams.rootModel.transactionDetails() && rootParams.rootModel.transactionDetails().transactionSnapshot) {

            self.autoPayRecords = ko.observableArray([]);

            self.params.txnCurrency = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.transactionCurrency);
            self.params.accountCurrency = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.accountCurrency);
            self.params.internalRefNo = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.internalRefNo);
            self.params.execDate = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.executionDate);
            self.params.totalPayAmount = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.totalAmount);
            self.params.noOfPayments = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.paymentRecordCount);
            self.params.bankCharge = ko.observable(rootParams.rootModel.transactionDetails().transactionSnapshot.bankCharges);
            self.params.isFromReinitiate = ko.observable(true);

            if (rootParams.rootModel.taskForApproval.initName === "corp-collection") {
                self.params.additionalDetails = ko.observable({
                    account: {
                        id: {
                            displayValue: rootParams.rootModel.transactionDetails().transactionSnapshot.collectionAccount ? rootParams.rootModel.transactionDetails().transactionSnapshot.collectionAccount.displayValue : null,
                            value: rootParams.rootModel.transactionDetails().transactionSnapshot.collectionAccount ? rootParams.rootModel.transactionDetails().transactionSnapshot.collectionAccount.value : null
                        }
                    }
                });
            } else {
                self.params.additionalDetails = ko.observable({
                    account: {
                        id: {
                            displayValue: rootParams.rootModel.transactionDetails().transactionSnapshot.withdrawalAcctNo ? rootParams.rootModel.transactionDetails().transactionSnapshot.withdrawalAcctNo.displayValue : null,
                            value: rootParams.rootModel.transactionDetails().transactionSnapshot.withdrawalAcctNo ? rootParams.rootModel.transactionDetails().transactionSnapshot.withdrawalAcctNo.value : null
                        }
                    }
                });
            }

            self.params.autoPayRecords = ko.observableArray(rootParams.rootModel.transactionDetails().transactionSnapshot.gridRecords);
        }

        if (rootParams.rootModel.taskForApproval && rootParams.rootModel.taskForApproval.initName === "file-upload" && rootParams.rootModel.transactionDetails() && rootParams.rootModel.transactionDetails().transactionSnapshot) {

            self.params.isFromReinitiate = ko.observable(true);

            if (self.params.data.executionDate) {

                self.params.executionDate = ko.observable(self.params.data.executionDate.slice(0, 10));
            }

            self.params.paymentType = ko.observable(self.params.data.paymentType);
            self.params.debitAcoount = ko.observable(self.params.data.debitAcoount);
            self.params.transferCurrency = ko.observable(self.params.data.transferCurrency);
            self.params.approvalType = ko.observable(self.params.data.approvalType);
            self.params.fileIdentifier = ko.observable(self.params.data.fileIdentifier);
        }

        if(rootParams.rootModel.transactionDetails){
            if(rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_DLM"||
                rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_ADLM"||
                rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_LDLM"||
                rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_CDLM"||
                rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_TDLM"||
                rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_ODLM"){
                    self.deleteFlag(true);
                }
        }

        self.hideshow = ko.observable(false);
        rootParams.baseModel.registerComponent("transaction-detail-popup", "approvals");

        self.isModificationAspectEnabled = ko.observable(false);

        if (self.pendingModification !== undefined) {
            self.canModify(self.pendingModification());
            self.forceShow(true);

            if (self.pendingApproval()) {
                if (undefined !== self.transactionDetails()) {
                    for (let i = 0; i < self.transactionDetails().taskDTO.aspects.length; i++) {
                        if (self.transactionDetails().taskDTO.aspects[i].taskAspect === "send-to-modify") {
                            self.isModificationAspectEnabled(true);
                        }
                    }
                }
            }
        }

        BatchProcessApprovalModel.paymentscurrentDateget().then(function (response) {
            self.currentDate(response.currentDate.valueDate);

        });

        /*if (self.transactionDetails && self.transactionDetails()) {
            const taskCode = self.transactionDetails().taskDTO && self.transactionDetails().taskDTO.id ? self.transactionDetails().taskDTO.id : "",
                txnRefNo = self.transactionDetails().transactionId ? self.transactionDetails().transactionId : "",
                effectiveDate = self.currentDate && self.currentDate() ? self.currentDate() :"";

            BatchProcessApprovalModel.checkWorkingWindow(taskCode, effectiveDate, txnRefNo).done(function (response) {
                if (response && response.workingWindowDefined && response.nextWorkingDate) {
                    if (response.workingWindowStatus === "CLOSED_NOT_AVAILABLE") {
                        if (self.transactionDetails().transactionSnapshot && self.transactionDetails().transactionSnapshot.paymentDetails && self.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray && self.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray) {
                            self.transactionDetails().transactionSnapshot.paymentDetails.dictionaryArray[0].nameValuePairDTOArray.forEach(item => {
                                if (item.genericName === "finalApprovalDate") {
                                    item.value = response.nextWorkingDate;
                                } else if (item.genericName === "isApprovalDateChanged") {
                                    item.value = "true";
                                }
                            });
                        }
                    }

                }
            });
        }*/

        if (self.pendingApproval !== undefined) {
            self.isApprovalPending(self.pendingApproval());
            self.forceShow(true);
        }

        self.hostReferenceNumber = ko.observable();
        self.transactions = ko.observableArray();
        self.selectedTransactionList = ko.observableArray([]);
        self.natureOfTask = ko.observable();
        self.natureOfTaskForMessage = ko.observable();
        self.natureOfTask("approve");
        self.natureOfTaskForMessage("approve");
        self.remarks = ko.observable();
        self.transactionSuccess = ko.observable(false);
        self.responseData = ko.observable();
        self.successfulTransactions = ko.observable(0);
        self.erroneousTransaction = ko.observable(0);
        self.typeOfTransaction = ko.observable();
        self.transactionTaskCode = ko.observable();
        self.graceFlag = ko.observable(false);
        self.referenceIDs = ko.observableArray();
        self.responseDetails = ko.observable();
        self.workingWindowStatus = ko.observable();
        self.nextWorkingDate = ko.observable();
        self.macData = self.macData || ko.observable();
        self.multipleApprovalStatus = ko.observable(false);
        self.showTFA = ko.observable(false);
        self.multipleApprovalPayload = ko.observableArray();
        self.nextWorkingWindowDate = ko.observable();
        self.exclusionWorkingWindowList = ko.observableArray();

        function customHeader(taskCode) {
            return self.nls.customHeader[taskCode];
        }

        self.loadAnotherComponent = function () {

            if (self.taskForApproval.class === "flow") {
                rootParams.dashboard.loadComponent("flow", {
                    flowName: self.taskForApproval.initName,
                    flowStageRootModel: {
                        sendtoModificationData: self.params
                    },
                    flowStartIndex: 0
                });
            } else {
                rootParams.baseModel.registerComponent(self.taskForApproval.initName, self.taskForApproval.module);
                rootParams.dashboard.loadComponent(self.taskForApproval.initName, self.params);
            }
        };

        self.loadComponentToCopy = function () {

            if (self.canReinitiate()) {

                delete self.params.transactionId;
                self.params.isFromReInitiate = ko.observable(true);
            }

            if (self.taskForApproval.class === "flow") {
                rootParams.dashboard.loadComponent("flow", {
                    flowName: self.taskForApproval.initName,
                    flowStageRootModel: {
                        sendtoModificationData: self.params
                    },
                    flowStartIndex: 0
                });
            } else {
                rootParams.baseModel.registerComponent(self.taskForApproval.initName, self.taskForApproval.module);
                rootParams.dashboard.loadComponent(self.taskForApproval.initName, self.params);
            }
        };

        self.closeWWModal = function (response) {
            $("#workingWindowDialog").trigger("closeModal");

            if (response === "yes") {
                self.showModalWindow("approve");
            }
        };

        self.suspiciousPopupClick = function (option) {

            const data = {};

                let crmLoggerDTO = {};

                data.eventId = "ACCTNO";

            if (option === "proceed") {
                self.isSuspiciousPayeeCheckDone(true);
                $("#suspiciousPayee").trigger("closeModal");
                $("#approveButton").trigger("click");
            } else {
                self.isSuspiciousPayeeCheckDone(false);
                $("#suspiciousPayee").trigger("closeModal");

                if(self.suspiciousPayeeTimeout()){
                    data.type = "OOS";
                    data.suspiciousActivity = "N";
                    self.isSuspiciousIndicator("OOS");
                    data.suspiciousInd = "C";
                }else{
                    data.type = "SUS";
                    self.isSuspiciousIndicator("SUS");
                    data.suspiciousActivity = "Y";
                    data.suspiciousInd = "C";
                }

                data.txnStatus = true;
                data.txnId = self.transactionDetails().transactionId ;
                crmLoggerDTO = ko.toJSON(data);
                BatchProcessApprovalModel.suspiciousPayeeCRM(crmLoggerDTO);
            }
        };

        self.checkForSuspiciouPayee = function(){
            self.isSuspiciousIndicator(false);

            if(self.transactionDetails() && self.transactionDetails().transactionId && !self.isSuspiciousPayeeCheckDone()){
                BatchProcessApprovalModel.enquireSuspiciouPayeeByTxnId(self.transactionDetails().transactionId).then(function (response) {
                    if (response !== undefined && response.suspiciousIndicator && response.suspiciousIndicator === "R") {
                        self.isSuspiciousIndicator(true);
                        $("#suspiciousPayee").trigger("openModal");
                    } else if (response !== undefined && response.suspiciousIndicator && response.suspiciousIndicator === "NULL_TIMED_OUT") {
                        self.suspiciousPayeeTimeout(true);
                        $("#suspiciousPayee").trigger("openModal");
                    } else {
                        self.isSuspiciousPayeeCheckDone(true);
                        $("#suspiciousPayee").trigger("closeModal");
                        $("#approveButton").trigger("click");
                    }
                }).catch(function () {
                    self.checkWorkingWindow();
                });
            }else {
                self.checkWorkingWindow();
            }
        };

        self.checkWorkingWindow = function () {
            const taskCode = self.transactionDetails().taskDTO.id,
                effectiveDate = self.currentDate && self.currentDate() ? self.currentDate() : "",
                txnRefNo = self.transactionDetails().transactionId;

            rootParams.baseModel.nextWorkingDate(null);

            BatchProcessApprovalModel.checkWorkingWindow(taskCode, effectiveDate, txnRefNo).done(function (response) {
                if (response) {

                    if (response.workingWindowDefined && response.workingWindowStatus !== "OPEN") {
                        if (response.processingType === "SUCCESS") {

                            if (response.workingWindowStatus === "CLOSED_AVAILABLE") {
                                rootParams.baseModel.nextWorkingDate(response.nextWorkingDate);
                                self.workingWindowStatus("closed_available");
                            } else {
                                rootParams.baseModel.nextWorkingDate(response.nextWorkingDate);
                                self.workingWindowStatus("closed");
                            }
                        } else if (response.processingType === "REJECT") {
                            rootParams.baseModel.nextWorkingDate(response.nextWorkingDate);
                            self.workingWindowStatus("rejected");
                        }

                        $("#workingWindowDialog").trigger("openModal");
                    } else {
                        self.workingWindowStatus("working");
                        self.showModalWindow("approve");
                    }
                }
            });
        };

        self.showModalWindow = function (nature) {
            self.natureOfTask(nature);

            if(self.natureOfTask() === "reject"){
                self.deleteFlag(false);
            }

            if(self.natureOfTask() === "approve"){
                if(rootParams.rootModel.transactionDetails){
                    if(rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_DLM"||
                        rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_ADLM"||
                        rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_LDLM"||
                        rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_CDLM"||
                        rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_TDLM"||
                        rootParams.rootModel.transactionDetails().limitTaskCode==="LMI_F_ODLM"){
                            self.deleteFlag(true);
                        }
                }
            }

            self.natureOfTaskForMessage(nature);
            self.remarks("");

            /**
             * self.transactions($("input[name=selection]:checked").map(function () {
             *     return this.value;
             * }).get());
             */

            if (self.typhoonFlagTransactions().length === 0 && self.mainTransacionsList !== undefined) {
                self.transactions.removeAll();
                ko.utils.arrayPushAll(self.transactions(), self.mainTransacionsList());
            }

            if (self.loadModule) {
                self.typeOfTransaction(self.nls.batchProcessApprovals[self.loadModule().toUpperCase().replace(/\-/g, "_")]);
            }

            self.referenceIDs([]);

            for (let i = 0; i < self.transactions().length; i++) {

                if (self.tdTXNList().includes(self.transactions()[i])) {
                    self.tdTXNTnC(true);
                }

                for (let j = 0; j < self.transactionList().length; j++) {
                    if (self.transactionList()[j].transactionId === self.transactions()[i]) {
                        if (ko.isObservable(self.transactionList()[j].maxApprovalDate) && self.transactionList()[j].maxApprovalDate() === undefined) {
                            self.graceFlag(false);
                        } else if (self.transactionList()[j].maxApprovalDate !== null && self.transactionList()[j].maxApprovalDate) {
                            self.graceFlag(false);
                            self.referenceIDs.push(self.transactions()[i]);
                        }

                        break;
                    }
                }
            }

            if (self.exclusionWorkingWindowList() !== undefined && self.exclusionWorkingWindowList().length > 0) {
                ko.utils.arrayForEach(self.exclusionWorkingWindowList(), function (item) {
                    self.transactions.remove(item.id);
                });
            }

            if (self.transactions().length === 0 && self.transactionDetails().maxApprovalDate !== null && self.transactionDetails().maxApprovalDate) {
                self.referenceIDs.push(self.transactionDetails().transactionId);
                self.graceFlag(false);
            }

            if (nature === "approve" && self.graceFlag() && self.referenceIDs().length) {
                $("#graceTransactionsApproval").trigger("openModal", "textarea");
            } else {
                if(self.outwardValidationFlag === "Y"){
                    rootParams.baseModel.showMessages(null, [self.nls.TTAutoRouting.alertMessage], "info");
                }

                $(self.transactions().length > 0 ? "#multiTransactionsApproval" : "#otherTransactionsApproval").trigger("openModal", "textarea");
                self.graceFlag(false);
            }
        };

        /**
         * @function checkTyphoonFlag
         * This functions checks for Typhoon flag for each transaction
         * @param {*} nature this indicates mode of action either APPROVE or REJECT
         */
        self.checkTyphoonFlag = function (nature) {
            self.natureOfTask(nature);
            self.natureOfTaskForMessage(nature);
            self.tncTransaction([]);
            self.tncInterestRate([]);
            self.remarks("");
            self.showTFA(false);

            /**
             * self.transactions($("input[name=selection]:checked").map(function () {
             *     return this.value;
             * }).get());
             */

            self.transactions.removeAll();

            ko.utils.arrayPushAll(self.transactions(), self.mainTransacionsList());

            const transactionsCount = self.transactions().length;
            let transactionListParams;

            self.tdTXNTnC(false);
            self.tdTXNList.removeAll();

            ko.utils.arrayForEach(self.transactionList(), function (txn) {

                if (txn.transactionName === "TD_F_RTD" || txn.transactionName === "TD_F_ATD" || txn.transactionName === "TD_F_OTD") {
                    self.tdTXNList().push(txn.transactionId);
                }

            });

            /**
             * Prepares query param
             */
            ko.utils.arrayForEach(self.transactions(), function (id, index) {
                if (index !== 0) {
                    transactionListParams = transactionListParams.concat("&txnRefNo=");
                } else {
                    transactionListParams = "txnRefNo=";
                }

                transactionListParams = transactionListParams.concat(id);
            });

            if (self.natureOfTask() === "reject") {
                self.checkWorkingWindowList();
            } else {
                BatchProcessApprovalModel.checkTyphoonFlag(transactionListParams).done(function (data) {
                    self.typhoonFlagTransactions.removeAll();

                    if (data.typhoonFlagMaintenanceListDTO) {
                        if (data.typhoonFlagMaintenanceListDTO.typhoonFlag && data.typhoonFlagMaintenanceListDTO.txnRefNoList.length > 0) {
                            self.typhoonFlagTransactions(data.typhoonFlagMaintenanceListDTO.txnRefNoList);

                            ko.utils.arrayForEach(self.typhoonFlagTransactions(), function (item) {
                                self.transactions.remove(item);
                            });

                            if (transactionsCount === self.typhoonFlagTransactions().length) {
                                $("#typhoonFlagRejectDialog").trigger("openModal");
                            } else {
                                $("#typhoonFlagWarningDialog").trigger("openModal");
                            }
                        } else {
                            self.checkWorkingWindowList();
                        }
                    }
                });
            }

        };

        /**
         * @function closeTyphoonRejectModal
         * This function closes the reject modal of typhoon
         */
        self.closeTyphoonRejectModal = function () {
            $("#typhoonFlagRejectDialog").trigger("closeModal");
        };

        /**
         * @function closeTyphoonWarningModal
         * This function closes Typhoon warning modal winodw and calls to list working api
         * @param {*} mode this indicates mode of action either yes or no
         */
        self.closeTyphoonWarningModal = function (mode) {
            if (mode === "yes") {
                $("#typhoonFlagWarningDialog").trigger("closeModal");
                self.checkWorkingWindowList();
            } else {
                $("#typhoonFlagWarningDialog").trigger("closeModal");
            }
        };

        /**
         * @function checkWorkingWindowList
         * This function calls working winodw API and checks for next working date of each transaction
         */
        self.checkWorkingWindowList = function () {

            let transactionListParams;

            /**
             * Prepares query param
             */
            ko.utils.arrayForEach(self.transactions(), function (id, index) {
                if (index !== 0) {
                    transactionListParams = transactionListParams.concat("&txnRefNo=");
                } else {
                    transactionListParams = "txnRefNo=";
                }

                transactionListParams = transactionListParams.concat(id);
            });

            /**
             * @function BatchProcessApprovalModel.checkWorkingWindowList
             * This function calls working window list api to check
             */
            if (self.natureOfTask() === "reject") {
                if (self.exclusionWorkingWindowList() !== undefined && self.exclusionWorkingWindowList().length > 0) {
                    self.exclusionWorkingWindowList.removeAll();
                }

                self.showModalWindow(self.natureOfTask());
            } else {
                BatchProcessApprovalModel.checkWorkingWindowList(transactionListParams).done(function (data) {
                    self.workingWindowTransactions.removeAll();
                    self.workingWindowTXNList.removeAll();
                    self.exclusionWorkingWindowList.removeAll();

                    for (let i = 0; i < data.response.length; i++) {
                        if (data.response[i].workingWindowDefined && data.response[i].workingWindowStatus !== "OPEN") {

                            let workingWindowTransaction = {};

                            if (data.response[i].processingType === "SUCCESS") {

                                if (data.response[i].workingWindowStatus === "CLOSED_AVAILABLE") {
                                    workingWindowTransaction = {
                                        id: data.response[i].txnRefNo,
                                        nextWorkingDate: data.response[i].nextWorkingDate
                                    };

                                    self.exclusionWorkingWindowList.push(workingWindowTransaction);

                                } else {
                                    rootParams.baseModel.nextWorkingDate(data.response[i].nextWorkingDate);

                                    workingWindowTransaction = {
                                        id: data.response[i].txnRefNo,
                                        nextWorkingDate: data.response[i].nextWorkingDate
                                    };

                                    self.nextWorkingWindowDate(data.response[i].nextWorkingDate);
                                    self.workingWindowTransactions.push(workingWindowTransaction);
                                }
                            } else if (data.response[i].processingType === "REJECT") {
                                workingWindowTransaction = {
                                    id: data.response[i].txnRefNo,
                                    nextWorkingDate: data.response[i].nextWorkingDate
                                };

                                self.exclusionWorkingWindowList.push(workingWindowTransaction);
                            }

                            self.workingWindowTXNList().push(data.response[i].txnRefNo);
                        }
                    }

                    if (self.workingWindowTransactions().length > 0 || self.exclusionWorkingWindowList().length > 0) {
                        $("#workingWindowListDialog").trigger("openModal");
                    } else {
                        self.showModalWindow(self.natureOfTask());
                    }
                });
            }
        };

        /**
         * @function closeWWListDialog
         * This function closes working window list and calls approve api based on button clicked
         */
        self.closeWWListDialog = function (option) {
            if (option === "yes") {
                $("#workingWindowListDialog").trigger("closeModal");
                self.showModalWindow(self.natureOfTask());
            } else {
                $("#workingWindowListDialog").trigger("closeModal");
            }
        };

        self.closeMultiTransactionsApproval = function () {
            self.showTFA(false);
            $(".button-container").show();
            $("#generic-authentication .button-container").hide();
        };

        self.closeGraceModel = function () {
            self.graceFlag(false);
            $("#graceTransactionsApproval").hide().trigger("closeModal");
        };

        self.ok = function () {
            self.graceFlag(false);
            $("#graceTransactionsApproval").hide().trigger("closeModal");
            $(self.transactions().length > 0 ? "#multiTransactionsApproval" : "#otherTransactionsApproval").trigger("openModal", "textarea");
            self.referenceIDs([]);
        };

        self.hideModal = function () {
            $(self.transactions().length > 0 ? "#multiTransactionsApproval" : "#otherTransactionsApproval").hide().trigger("closeModal");
        };

        self.successHandler = function () {
            self.erroneousTransaction(0);
            self.successfulTransactions(self.multipleApprovalPayload().length);
            self.natureOfTask("approve");

            for (let i = 0; i < self.multipleApprovalPayload().length; i++) {
                for (let j = 0; j < self.arrayDataSource.dataSource.data().length; j++) {
                    if (self.multipleApprovalPayload()[i].transactionID === self.arrayDataSource.dataSource.data()[j].transactionId) {
                        self.arrayDataSource.dataSource.data.remove(self.arrayDataSource.dataSource.data()[j]);
                    }
                }
            }

            self.hideModal();
            self.toShow(false);
            self.showTFA(false);
            self.multipleApprovalStatus(true);
            self.mainTransacionsList.removeAll();
            $("input[name=selectionParent]").prop("checked", false);

            (function () {
                const interval = setInterval(() => {
                    if ($("#refreshView").length) {
                        clearInterval(interval);

                        $("#refreshView").click(function () {
                            self.multipleApprovalStatus(false);
                            self.fetchCount(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
                            self.refreshTable(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
                            rootParams.baseModel.refreshMyApprovedList(Math.random());

                        });
                    }
                }, 50);
            })();

        };

        self.failureHandler = function (data) {
            if (data.status === 417) {
                rootParams.baseModel.onTFAScreen(false);
                self.showTFA(true);
            }
        };

        self.hideDetails = function () {
            if (rootParams.baseModel.onTFAScreen() === true) {
                rootParams.baseModel.onTFAScreen(false);
            } else {
                rootParams.dashboard.switchModule(false);
            }
        };

        self.submit = function () {
            if (self.transactions().length > 0 && !rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return;
            }

            if(self.outwardValidationFlag === "Y"){
                if(document.getElementsByClassName("oj-message-detail")[0].innerText.substring(0, 10) === self.nls.TTAutoRouting.alertMessage.substring(0, 10)){
                    rootParams.baseModel.closeNotificationMessages();
                }
            }

            if (self.natureOfTask() === "cancel") {
                if (self.transactionDetails()) {

                    const cancelTxnPayload = {
                        txnRefNo: self.transactionDetails().transactionId,
                        rejectMessage: self.remarks()
                    }, expand = "";

                    if(!self.params.taskCode && self.transactionDetails().taskDTO && self.transactionDetails().taskDTO){
                        self.params.taskCode = self.transactionDetails().taskDTO.id;
                    }

                    BatchProcessApprovalModel.cancelTransactionByTxnRefNo(ko.toJSON(cancelTxnPayload), self.transactionDetails().transactionId).then(function (response) {

                        if (response) {
                            BatchProcessApprovalModel.transactionstransactionIdget(self.transactionDetails().transactionId, expand).then(function (data) {
                                self.responseData(data);
                                self.responseDetails(data);

                                if (data.result && data.result.dictionaryArray && data.result.dictionaryArray[0]) {

                                    data.result.dictionaryArray[0].nameValuePairDTOArray.forEach(function (item) {
                                        if (item.genericName === "CalculatedRate") {
                                            rootParams.baseModel.exchangeRate(item.value);
                                        }
                                    });
                                }

                                self.transactionTaskCode(data.transactionDTO.taskDTO.id);

                                if(self.transactionTaskCode() && self.transactionTaskCode() === "FT_F_PFR"){
                                    if (data.result && data.result.fxAgreeOWNCRUDDTO && data.result.fxAgreeOWNCRUDDTO.calculatedRate && self.params.data && self.params.data.cin === "") {
                                        rootParams.baseModel.exchangeRate(Number(data.result.fxAgreeOWNCRUDDTO.calculatedRate.split(/\s/)[1]).toFixed(8));
                                    }
                                }

                                if(data.transactionDTO.serviceId === "com.ofss.digx.cz.bea.app.td.service.account.core.CZTermDeposit.createCouponTimeDeposit"){
                                    data.transactionDTO.transactionSnapshot.dictionaryArray[0].nameValuePairDTOArray.forEach(function (item){
                                        if(item.name === "couponCode"){
                                            self.couponCode(item.value);
                                        }
                                    });
                                }

                                if (self.responseData().transactionDTO.processingDetails.status === "F" && self.responseData().transactionDTO.processingDetails.currentStep === "exec") {
                                    self.erroneousTransaction(self.erroneousTransaction() + 1);
                                } else if (self.responseData().transactionDTO.processingDetails.currentStep === "exec") {
                                    if (self.taskForApproval && self.taskForApproval.hostReferenceNumber) {
                                        self.hostReferenceNumber(self.setCustomHostRefNo(data, self.taskForApproval.hostReferenceNumber));
                                    } else if (data.transactionDTO.processingDetails && data.transactionDTO.processingDetails.referenceNumber) {
                                        self.hostReferenceNumber(data.transactionDTO.processingDetails.referenceNumber);
                                    }

                                    self.successfulTransactions(self.successfulTransactions() + 1);
                                } else {
                                    self.successfulTransactions(self.successfulTransactions() + 1);
                                }

                                if (self.hideOnSuccess) {
                                    self.hideOnSuccess(false);
                                }

                                const transactionAction = {
                                    transactionDTO: data.transactionDTO
                                };

                                data.status.referenceNumber = self.transactionDetails().transactionId;

                                const jqXhr = {
                                    responseJSON: {
                                        status: data.status,
                                        transactionAction: transactionAction,
                                        action: "REJECT"
                                    }
                                };

                                self.toShow(false);
                                self.transactionCompleted(jqXhr);

                            }).catch(function () {
                                self.erroneousTransaction(self.erroneousTransaction() + 1);
                            });
                        }
                    }).catch(function () {
                        self.erroneousTransaction(self.erroneousTransaction() + 1);
                    });
                }
            }

            if (self.transactions() && self.transactions().length > 0) {
                let macListPayload = {};

                self.workingWindowTXNListWithDates.removeAll();

                for (let index = 0; index < self.transactions().length; index++) {
                    if (self.workingWindowTXNList().includes(self.transactions()[index])) {
                        self.workingWindowTXNListWithDates().push(self.transactions()[index] + "~" + self.nextWorkingWindowDate());
                    } else {
                        self.workingWindowTXNListWithDates().push(self.transactions()[index]);
                    }
                }

                if (self.workingWindowTransactions() && self.workingWindowTransactions().length > 0) {
                    macListPayload = {
                        transactionIDs: self.workingWindowTXNListWithDates()
                    };
                } else {
                    macListPayload = {
                        transactionIDs: self.transactions()
                    };
                }

                BatchProcessApprovalModel.getMACDataList(ko.toJSON(macListPayload)).done(function (data) {

                    const multiApprovalTransactionList = [],
                        missingMacDataTXNs = macListPayload.transactionIDs.filter(function (txnListItem) {
                            return !data.mactxnList.some(function (macList) {
                                return txnListItem.split("~")[0] === macList.transactionID;
                            });
                        });

                    if (data.mactxnList !== undefined && data.mactxnList.length > 0) {
                        for (let i = 0; i < data.mactxnList.length; i++) {
                            const macApprovalData = rootParams.baseModel.getMACHeaders(data.mactxnList[i].macData);

                            multiApprovalTransactionList.push({
                                transactionID: data.mactxnList[i].transactionID,
                                macData: data.mactxnList[i].macData,
                                macRSAIndicator: macApprovalData.macRSAIndicator,
                                macModulus: macApprovalData.macModulus,
                                macKey: macApprovalData.macKey,
                                macEncryptedData: macApprovalData.macEncryptedData,
                                remarks: self.remarks()
                            });
                        }

                        if (missingMacDataTXNs.length > 0) {
                            for (let i = 0; i < missingMacDataTXNs.length; i++) {
                                multiApprovalTransactionList.push({
                                    transactionID: missingMacDataTXNs[i],
                                    remarks: self.remarks()
                                });
                            }
                        }
                    } else {
                        for (let i = 0; i < self.transactions().length; i++) {
                            multiApprovalTransactionList.push({
                                transactionID: self.transactions()[i],
                                remarks: self.remarks()
                            });
                        }
                    }

                    const multiApprovalPayload = {
                        multiApprovalTransactionList
                    };

                    self.multipleApprovalPayload(multiApprovalPayload.multiApprovalTransactionList);
                    BatchProcessApprovalModel.respondMultipleApprovalRequest(self.natureOfTask(), ko.toJSON(multiApprovalPayload), self.successHandler, self.failureHandler);
                });
            } else if (self.workingWindowStatus() && self.workingWindowStatus() === "closed") {
                if (self.transactionDetails() && self.transactionDetails().transactionId) {
                    rootParams.baseModel.isApprovalDateChanged(true);

                    const payload = {
                        txnDate: rootParams.baseModel.nextWorkingDate()
                    };

                    BatchProcessApprovalModel.transactionsMACTxnDatacontenttransactionIdput(self.transactionDetails().transactionId, ko.toJSON(payload)).then(function (response) {

                        self.macData(response.macData);

                        self.hideModal();

                        if (self.forceShow()) {
                            self.fireTransactions(self.transactionId, false);
                        } else {
                            for (let i = 0; i < self.transactions().length; i++) {
                                self.fireTransactions(self.transactions()[i], true);
                            }
                        }
                    });
                }
            } else if (self.forceShow() && !(self.natureOfTask() === "cancel")) {
                self.hideModal();
                self.fireTransactions(self.transactionId, false);
            }
        };

        self.close = function () {
            self.toShow(false);
            self.referenceIDs([]);
            self.multipleApprovalStatus(false);
            self.transactionSuccess(false);
            self.fetchCount(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
            self.refreshTable(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
        };

        self.loadModuleSubscription = null;

        if (self.loadModule) {
            self.loadModuleSubscription = self.loadModule.subscribe(function () {
                self.toShow(false);
                self.transactionSuccess(false);
                self.multipleApprovalStatus(false);
            });
        }

        self.setCustomHostRefNo = function (response, customHostReferenceNumberPath) {
            const xpath = customHostReferenceNumberPath.split(".");
            let hostReferenceNumber = response;

            for (let i = 0; i < xpath.length; i++) {
                hostReferenceNumber = hostReferenceNumber[xpath[i]];
            }

            return hostReferenceNumber;
        };

        self.createConfirmScreenArray = function (data, jqXhr) {

            if (self.workingWindowStatus() === "closed") {
                if (self.confirmScreenExtensions && self.confirmScreenExtensions.date && self.confirmScreenExtensions.date.ExecutionDate && rootParams.baseModel.nextWorkingDate()) {
                    self.confirmScreenExtensions.date.ExecutionDate = rootParams.baseModel.nextWorkingDate();
                }
            }

            if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "UM_ID_MC") {
                if (data !== null && data.result !== undefined && data.result.listUserIdMaintenance !== null && data.result.listUserIdMaintenance !== undefined && data.result.listUserIdMaintenance.length > 0) {

                    self.confirmScreenExtensions = {
                        isSet: true,
                        taskCode: jqXhr.responseJSON.transactionAction.transactionDTO.transactionName,
                        confirmScreenDetails: [],
                        template: "confirm-screen/user-id-maintenance"
                    };

                    self.confirmScreenExtensions.confirmScreenDetails[0] = [];

                    for (let i = 0; i < data.result.listUserIdMaintenance.length; i++) {

                        let item = {};

                        if (data.result.listUserIdMaintenance[i].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0096) {
                            item = {
                                label: data.result.listUserIdMaintenance[i].id,
                                value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.duplicateUserId, {
                                    userId: data.result.listUserIdMaintenance[i].id
                                })
                            };
                        } else if (data.result.listUserIdMaintenance[i].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0307) {

                            item = {
                                label: data.result.listUserIdMaintenance[i].pinRefNo,
                                value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.pinRefNotFound, {
                                    pinRefNo: data.result.listUserIdMaintenance[i].pinRefNo
                                })
                            };

                        } else if (data.result.listUserIdMaintenance[i].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0385) {

                            item = {
                                label: data.result.listUserIdMaintenance[i].pinRefNo,
                                value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.pinRefAlreadyUsed, {
                                    pinRefNo: data.result.listUserIdMaintenance[i].pinRefNo
                                })
                            };
                        } else if (/\s/.test(data.result.listUserIdMaintenance[i].rejectCode)) {

                            item = {
                                label: data.result.listUserIdMaintenance[i].pinRefNo,
                                value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.userIdCreated, {
                                    userId: data.result.listUserIdMaintenance[i].id,
                                    pinRefNo: data.result.listUserIdMaintenance[i].pinRefNo
                                })
                            };
                        }

                        self.confirmScreenExtensions.confirmScreenDetails[0].push(item);
                    }

                }
            }

            if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "MRCH_N_LDMU" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "MRCH_N_LDMC") {
                if (data !== null && data.result !== undefined && data.result.listMerchantUserMaintenance !== null && data.result.listMerchantUserMaintenance !== undefined && data.result.listMerchantUserMaintenance.length > 0) {

                    if (data.result.listMerchantUserMaintenance[0].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0096) {

                        self.confirmScreenExtensions = {
                            isSet: true,
                            taskCode: jqXhr.responseJSON.transactionAction.transactionDTO.transactionName,
                            confirmScreenDetails: [],
                            template: "confirm-screen/user-id-maintenance"
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0] = [];

                        const item = {
                            label: data.result.listMerchantUserMaintenance[0].loginId,
                            value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.duplicateUserId, {
                                userId: data.result.listMerchantUserMaintenance[0].loginId
                            })
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0].push(item);
                    } else if (data.result.listMerchantUserMaintenance[0].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0307) {

                        self.confirmScreenExtensions = {
                            isSet: true,
                            taskCode: jqXhr.responseJSON.transactionAction.transactionDTO.transactionName,
                            confirmScreenDetails: [],
                            template: "confirm-screen/user-id-maintenance"
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0] = [];

                        let refValue;

                        if (data.result.listMerchantUserMaintenance[0].newPinRefNo !== undefined) {
                            refValue = data.result.listMerchantUserMaintenance[0].newPinRefNo;
                        } else {
                            refValue = data.result.listMerchantUserMaintenance[0].pinRefNo;
                        }

                        const item = {
                            label: data.result.listUserIdMaintenance[0].pinRefNo,
                            value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.pinRefNotFound, {
                                pinRefNo: refValue
                            })
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0].push(item);
                    }
                    else if (data.result.listMerchantUserMaintenance[0].rejectCode === self.nls.UserIdMaintenance.errorCode.EBBE0385) {

                        self.confirmScreenExtensions = {
                            isSet: true,
                            taskCode: jqXhr.responseJSON.transactionAction.transactionDTO.transactionName,
                            confirmScreenDetails: [],
                            template: "confirm-screen/user-id-maintenance"
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0] = [];

                        let refValue;

                        if (data.result.listMerchantUserMaintenance[0].newPinRefNo !== undefined) {
                            refValue = data.result.listMerchantUserMaintenance[0].newPinRefNo;
                        } else {
                            refValue = data.result.listMerchantUserMaintenance[0].pinRefNo;
                        }

                        const item = {
                            label: data.result.listUserIdMaintenance[0].pinRefNo,
                            value: rootParams.baseModel.format(self.nls.UserIdMaintenance.errorCode.pinRefAlreadyUsed, {
                                pinRefNo: refValue
                            })
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0].push(item);
                    }

                }
            }

            if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "MRCH_N_CMU" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "MRCH_N_EMU") {
                if (data !== null && data.result !== undefined && data.result.merchantInActive !== "" && data.result.merchantInActive !== undefined) {

                    if (data.result.merchantInActive === true) {

                        self.confirmScreenExtensions = {
                            isSet: true,
                            taskCode: jqXhr.responseJSON.transactionAction.transactionDTO.transactionName,
                            confirmScreenDetails: [],
                            template: "confirm-screen/user-id-maintenance"
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0] = [];

                        const item = {
                            label: data.result.merchantInActive,
                            value: self.nls.MerchantUserMaintenance.errorCode.merchantNotAvailable
                        };

                        self.confirmScreenExtensions.confirmScreenDetails[0].push(item);
                    }
                }
            }

        };

        self.fireTransactions = function (id, ignore2FA) {
            BatchProcessApprovalModel.respondApprovalRequest(id, self.remarks(), self.natureOfTask(), ignore2FA, self.macData()).done(function (data, status, jqXhr) {
                self.responseData(data.transactionAction);
                self.responseDetails(data);

                if (data.result && data.result.dictionaryArray && data.result.dictionaryArray[0]) {

                    data.result.dictionaryArray[0].nameValuePairDTOArray.forEach(function (item) {
                        if (item.genericName === "CalculatedRate") {
                            rootParams.baseModel.exchangeRate(item.value);
                        }
                    });
                }

                self.transactionTaskCode(data.transactionAction.transactionDTO.taskDTO.id);

                if(self.transactionTaskCode() && self.transactionTaskCode() === "FT_F_PFR"){
                    if (data.result && data.result.fxAgreeOWNCRUDDTO && data.result.fxAgreeOWNCRUDDTO.calculatedRate && self.params.data && self.params.data.cin === "") {
                        rootParams.baseModel.exchangeRate(Number(data.result.fxAgreeOWNCRUDDTO.calculatedRate.split(/\s/)[1]).toFixed(8));
                    }
                }

                if(data.transactionAction.transactionDTO.serviceId === "com.ofss.digx.cz.bea.app.td.service.account.core.CZTermDeposit.createCouponTimeDeposit"){
                    data.transactionAction.transactionDTO.transactionSnapshot.dictionaryArray[0].nameValuePairDTOArray.forEach(function (item){
                        if(item.name === "couponCode"){
                            self.couponCode(item.value);
                        }
                    });
                }

                if (self.responseData().transactionDTO.processingDetails.status === "F" && self.responseData().transactionDTO.processingDetails.currentStep === "exec") {
                    self.erroneousTransaction(self.erroneousTransaction() + 1);
                } else if (self.responseData().transactionDTO.processingDetails.currentStep === "exec") {
                    if (self.taskForApproval && self.taskForApproval.hostReferenceNumber) {
                        self.hostReferenceNumber(self.setCustomHostRefNo(data, self.taskForApproval.hostReferenceNumber));
                    } else if (data.transactionAction.transactionDTO.processingDetails && data.transactionAction.transactionDTO.processingDetails.referenceNumber) {
                        self.hostReferenceNumber(data.transactionAction.transactionDTO.processingDetails.referenceNumber);
                    }

                    self.successfulTransactions(self.successfulTransactions() + 1);
                } else {
                    self.successfulTransactions(self.successfulTransactions() + 1);
                }

                if (self.hideOnSuccess) {
                    self.hideOnSuccess(false);
                }

                self.toShow(false);

                // The HTH approval API returns the canonical transaction result as the
                // success payload. Some transports keep a different wrapper in
                // jqXHR.responseJSON, but the shared confirmation screen gives that
                // property precedence and then expects transactionAction.transactionDTO.
                // Normalise only the HTH task responses so the shared screen receives
                // the same successful payload that this approval component processed.
                if (["UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL"].includes(self.transactionTaskCode())) {
                    jqXhr = jqXhr || {};
                    jqXhr.responseJSON = data;

                    if (typeof jqXhr.status !== "number") {
                        jqXhr.status = 200;
                    }
                }

                self.transactionCompleted(jqXhr);
            }).fail(function (jqXhr) {
                self.erroneousTransaction(self.erroneousTransaction() + 1);
                self.transactionCompleted(jqXhr);
            });
        };

        self.transactionCompleted = function (jqXhr) {
            self.transactionSuccess(true);

            if (self.responseData().action === "REJECT") {
                jqXhr.responseJSON.status.receiptAvailable = false;
            }

            if ((rootParams.rootModel.params && rootParams.rootModel.params.taskCode && rootParams.rootModel.params.taskCode === "FT_F_PFR") && (self.responseData().action === "REJECT" || jqXhr.responseJSON.transactionAction.transactionDTO.processingDetails === "F" || self.responseData().action === "REQUEST_MODIFICATION")) {
                const statusUpdate = {
                    statusToUpdate: "NOT_COMPLETED"
                };

                if(rootParams.rootModel.params.data.cin && rootParams.rootModel.params.data.treasuryReference){
                    BatchProcessApprovalModel.editStatus(rootParams.rootModel.params.data.cin, rootParams.rootModel.params.data.treasuryReference, ko.toJSON(statusUpdate));
                }
            }

            /**
             * self.createConfirmScreenArray(self.responseDetails(), jqXhr);
             * Added for User Id Maintenance
             */
            self.createConfirmScreenArray(self.responseDetails(), jqXhr);

            if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "TD_F_OTD") {
                let pushValue, pushLabel;

                if (jqXhr.responseJSON && jqXhr.responseJSON.result && jqXhr.responseJSON.result.termDepositDetails) {

                    for (let i = 0; i < jqXhr.responseJSON.result.termDepositDetails.dictionaryArray[0].nameValuePairDTOArray.length; i++) {
                        if (jqXhr.responseJSON.result.termDepositDetails.dictionaryArray[0].nameValuePairDTOArray[i].genericName === "com.ofss.digx.cz.bea.domain.td.entity.account.TermDepositAccount.depositnum") {
                            pushValue = jqXhr.responseJSON.result.termDepositDetails.dictionaryArray[0].nameValuePairDTOArray[i].value;
                            pushLabel = self.nls.batchProcessApprovals.newDeposit;

                            self.confirmScreenExtensions.confirmScreenDetails[1].unshift({
                                label: pushLabel,
                                value: pushValue
                            });
                        }
                    }
                }
            }

            if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_ACLM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_CLM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_LCLM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_TCLM" ||jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_OCLM" ||jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_CCLM" ) {

                if (jqXhr.responseJSON && jqXhr.responseJSON.result) {
                    if (jqXhr.responseJSON.result) {
                        self.confirmScreenExtensions.confirmScreenDetails[0][0].value = jqXhr.responseJSON.result.instructionNum;
                        self.confirmScreenExtensions.confirmScreenDetails[0][1].value = jqXhr.responseJSON.result.versionNum;
                        self.confirmScreenExtensions.confirmScreenDetails[0][2].value = jqXhr.responseJSON.result.priority;

                    }
                }
            }

        /**    if (jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_AULM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_ULM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_LULM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_TULM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_OULM" || jqXhr.responseJSON.transactionAction.transactionDTO.transactionName === "LMI_F_CULM") {
                if (jqXhr.responseJSON && jqXhr.responseJSON.result) {
                    if (jqXhr.responseJSON.result) {
                        self.confirmScreenExtensions.confirmScreenDetails[0][1].value = jqXhr.responseJSON.result.versionNum;
                    }
                }
            }*/

            if (self.forceShow()) {
                rootParams.dashboard.loadComponent("confirm-screen", {
                    jqXHR: jqXhr,
                    transactionName: customHeader(self.params.taskCode) || self.params.type,
                    remarks: self.remarks(),
                    couponCode: self.couponCode(),
                    hostReferenceNumber: self.hostReferenceNumber(),
                    imageType: self.responseData() && self.responseData().transactionDTO.processingDetails.status === "F" && self.responseData().transactionDTO.processingDetails.currentStep === "exec" ? "reject" : self.responseData() ? null : "reject",
                    confirmScreenExtensions: self.confirmScreenExtensions
                }, self);
            }

            $("input[name^=\"selection\"]").prop("checked", false);

            if (!self.forceShow() && self.erroneousTransaction() + self.successfulTransactions() === self.transactions().length) {
                self.fetchCount(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
                self.refreshTable(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
            }
        };

        self.loadTransactionDetails = function (data) {
            for (let i = 0; i < self.transactionList().length; i++) {
                if (self.transactionList()[i].transactionId === data) {
                    self.popupTransactionType(self.transactionList()[i].type);
                    break;
                }
            }

            const params = {
                    transactionId: data,
                    type: self.popupTransactionType(),
                    isPending: true
                },

                paramsObj = {
                    params
                };

            $.extend(self, paramsObj);
            self.selectedTransactionId(data);
            self.parentDetailsLoaded(true);
            rootParams.dashboard.headerName("");
            $("#transactionDeatilsPopup").trigger("openModal");
        };

        self.closeTxnPopup = function () {
            self.parentDetailsLoaded(false);
            rootParams.dashboard.headerName("");
            $("#transactionDeatilsPopup").trigger("closeModal");
        };

        self.addOrRemove = function (txnId, event) {

            if (self.mainTransacionsList().includes(txnId)) {
                const index = self.mainTransacionsList().indexOf(txnId);

                if (index !== -1) {
                    self.mainTransacionsList().splice(index, 1);
                }
            } else if (self.mainTransacionsList().length <= 9) {
                self.mainTransacionsList().push(txnId);
            } else if (self.mainTransacionsList().length === 10 || self.mainTransacionsList().length > 10) {
                event.target.checked = false;
            }
        };

        self.refreshViewData = function () {

            self.multipleApprovalStatus(false);
            self.fetchCount(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
            self.refreshTable(self.loadModule().toUpperCase().replace(/\-/g, "_") + "_PENDING");
            rootParams.baseModel.refreshMyApprovedList(Math.random());
        };

        $(document).ready(function () {
            $(document).on("change", "input[name=selection]", function (event) {

                self.addOrRemove(event.currentTarget.value, event);
                self.toShow(!!self.mainTransacionsList().length);
                self.hideshow(!!self.mainTransacionsList().length);
                self.multipleApprovalStatus(false);
                self.isApprovalPending(self.toShow());
                self.enabledMultiApproveButton(self.toShow());
                self.successfulTransactions(0);
                self.erroneousTransaction(0);

                /**
                 * $("input[name=selectionParent]").prop("checked", self.mainTransacionsList().length === 10);
                 *
                 * */

                self.transactionSuccess(false);

                if (self.mainTransacionsList().length > 0 || self.mainTransacionsList().length > 0) {
                    self.isApprovalPending(true);
                    self.canModify(false);
                    self.enabledMultiApproveButton(true);
                    $("#batch-approvals .button-container").show();
                } else {
                    self.isApprovalPending(false);
                    self.canModify(false);
                    self.enabledMultiApproveButton(false);
                    $("#batch-approvals .button-container").hide();
                }

            });

            $(document).on("change", "input[name=selectionParent]", function () {

                /**
                 * $("input[name=selection]").prop("checked", $("input[name=selectionParent]").prop("checked"));
                 */

                const checkboxes = document.querySelectorAll("input[name=selection]:not(:disabled)");

                self.successfulTransactions(0);
                self.erroneousTransaction(0);
                self.toShow(!!$("input[name=selection]").length && !!$("input[name=selectionParent]").prop("checked"));
                self.isApprovalPending(self.toShow());
                self.multipleApprovalStatus(false);
                self.transactionSuccess(false);

                if ($("input[name=selectionParent]").prop("checked") === true) {
                    self.isApprovalPending(true);
                    self.canModify(false);
                    self.enabledMultiApproveButton(true);
                    $("#batch-approvals .button-container").show();
                    self.mainTransacionsList.removeAll();

                    checkboxes.forEach(function (checkbox) {
                        checkbox.checked = $("input[name=selectionParent]").prop("checked");
                        self.mainTransacionsList().push(checkbox.value);
                    });

                    /*
                     * for (let index = 0; index < self.transactionList().length; index++) {
                     *     if(self.mainTransacionsList().length <= 9){
                     *         for (let i = 0; i < self.transactionList()[index].dictionaryArray.length; i++) {
                     *             for (let j = 0; j < self.transactionList()[index].dictionaryArray[i].nameValuePairDTOArray.length; j++) {
                     *                 if (self.transactionList()[index].dictionaryArray[i].nameValuePairDTOArray[j].name === "FX_INDICATOR" && self.transactionList()[index].dictionaryArray[i].nameValuePairDTOArray[j].value === "N") {
                     *                     self.mainTransacionsList().push(self.transactionList()[index].transactionId);
                     *                 }
                     *             }
                     *         }
                     *     } else {
                     *         break;
                     *     }
                     * }
                     * */

                } else {
                    checkboxes.forEach(function (checkbox) {
                        checkbox.checked = false;
                    });

                    self.mainTransacionsList.removeAll();
                    self.isApprovalPending(false);
                    self.canModify(false);
                    self.enabledMultiApproveButton(false);
                    $("#batch-approvals .button-container").hide();
                }
            });

            $(document).on("ojready", "table#table", function () {
                $("input[name^=\"selection\"]").prop("checked", false);
            });
        });

        setTimeout(() => {
            $("#suspiciousPayee").on("openModal", function() {
                if (!self.suspiciousPayeeTimeout()) {
                    const timer = setInterval(() => {
                            if (self.suspiciousAlertTimer() > 0) {
                                self.suspiciousAlertTimer(self.suspiciousAlertTimer() - 1);
                            } else {
                                self.suspiciousAlertTimeout(true);
                                clearInterval(timer);

                                const suspiciousCancel = document.getElementById("suspiciousCancel"),
                                suspiciousProceed = document.getElementById("suspiciousProceed");

                                if (suspiciousCancel) {
                                    suspiciousCancel.classList.add("action-button-primary");
                                }

                                if (suspiciousProceed) {
                                    suspiciousProceed.classList.add("action-button-secondary");
                                }
                            }
                        }, 1000);
                }
            });

            $("#suspiciousPayee").on("closeModal", function() {
                if (!self.suspiciousPayeeTimeout()) {
                    self.suspiciousAlertTimer(10);
                    self.suspiciousAlertTimeout(false);

                    const suspiciousCancel = document.getElementById("suspiciousCancel"),
                    suspiciousProceed = document.getElementById("suspiciousProceed");

                    if (suspiciousCancel) {
                        suspiciousCancel.classList.remove("action-button-primary");
                    }

                    if (suspiciousProceed) {
                        suspiciousProceed.classList.remove("action-button-secondary");
                    }
                }
            });
        }, 1000);
    };

    vm.prototype.dispose = function () {
        if (this.loadModuleSubscription) {
            this.loadModuleSubscription.dispose();
        }
    };

    return vm;
});