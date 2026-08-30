define([
    "knockout",
    "ojs/ojcore",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/confirm-screen",
    "framework/js/constants/constants",
    "ojs/ojbutton",
    "ojs/ojarraydataprovider",
    "ojs/ojconverter-number",
    "ojs/ojselectcombobox",
    "ojs/ojradioset",
    "ojs/ojswitch",
    "ojs/ojnavigationlist",
    "ojs/ojbootstrap",
    "ojs/ojswitcher",
    "ojs/ojcore",
    "ojs/ojaccordion",
    "ojs/ojtable",
    "ojs/ojmodule-element-utils",
    "framework/elements/api/marketing-banner/loader"
], function (ko, oj, $, ConfirmScreenModel, ResourceBundle, Constants) {
    "use strict";

    return function (rootParams) {
        const self = this;

        Object.assign(self, {
            params: rootParams.rootModel.params
        });

        self.marketBannerParams = ko.observable(null);

        if (rootParams.rootModel.params && rootParams.rootModel.params.jqXHR && rootParams.rootModel.params.jqXHR.responseJSON.transactionAction && rootParams.rootModel.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails) {
            const taskDTOId = rootParams.rootModel.params.jqXHR.responseJSON.transactionAction.transactionDTO.taskDTO.id,
                widgetIdConfig = {
                    FT_F_PFR: "W004",
                    PC_F_CRNSFT: "W006",
                    PC_F_CRNIFT: "W006",
                    PC_F_CRNBCT: "W006",
                    PC_F_CRNDFT: "W006",
                    PC_F_CRNDFT_FPS: "W006",
                    PC_F_CRNINFT: "W006",
                    PC_F_GCRNIFT: "W006",
                    PC_F_GCRNBCT: "W006",
                    PC_F_GCRNDFT: "W006",
                    PC_F_GCRNDFT_FPS: "W006",
                    PC_F_GCRNINFT: "W006",
                    PC_F_CRNBCT_SI: "W006",
                    PC_F_CRNDFT_FPS_SI: "W006",
                    PC_F_CRNDFT_SI: "W006",
                    PC_F_CRNINFT_SI: "W006",
                    PC_F_CRNIFT_SI: "W006",
                    PC_F_CRNSFT_SI: "W006",
                    PC_F_GCRNIFT_SI: "W006",
                    PC_F_GCRNBCT_SI: "W006",
                    PC_F_GCRNDFT_SI: "W006",
                    PC_F_GCRNINFT_SI: "W006",
                    PC_F_GCRNDFT_FPS_SI: "W006"
                }, widgetId = widgetIdConfig[taskDTOId];

            if (widgetId) {
                const marketingBannerParams =
                    rootParams.dashboard.getMarketingWidgetParams();

                self.marketBannerParams(
                    Object.assign({}, marketingBannerParams, {
                        dashboard: rootParams.dashboard,
                        widgetId: widgetId
                    })
                );
            }
        }

        self.confirmScreenResources = ResourceBundle;
        self.headerMessages = ko.observableArray();
        rootParams.baseModel.registerComponent("feedback-capture", "feedback");
        rootParams.baseModel.registerComponent("qr-code-create", "fps-merchant");
        self.image = self.params.imageType || "confirm";
        self.enableEReceipt = ko.observable(false);
        self.reason = ko.observable();
        self.renderFeedbackModule = ko.observable(false);
        self.showModal = ko.observable(false);
        self.recordsTab = ko.observable("successRecords");
        self.hideExchange = ko.observable(false);
        self.showExchangeRate = ko.observable(false);
        self.showDisclaimer = ko.observable(false);
        self.isFPSMerchant = ko.observable(false);
        self.feedbackTemplateDTO = ko.observable();
        self.todayDate = ko.observable(rootParams.baseModel.formatDate(oj.IntlConverterUtils.dateToLocalIso(rootParams.baseModel.getDate("DATE_TIME")), "dateFormat") + rootParams.baseModel.getDate("NEW_DATE_TIME"));
        rootParams.baseModel.registerElement("action-header");
        self.showSuccessList = ko.observable(true);
        self.showErrorList = ko.observable(false);
        self.qrRecords = ko.observable(true);
        self.showQRButtons = ko.observable(false);
        self.successRecordDataSource = ko.observable();
        self.errorRecordDataSource = ko.observable();
        self.isSingleRecord = ko.observable(true);
        self.sequenceId = ko.observable();
        self.payerSequenceId = ko.observable();
        self.base64 = ko.observable();
        self.showSingleQR = ko.observable(false);
        self.fpsQRrefNo = ko.observable();
        self.isFPSQRrefNo = ko.observable(false);
        self.isFPSQRCreatedDate = ko.observable(false);
        self.fpsQRCreatedDate = ko.observable();
        self.itokenRemarks = ko.observable("");
        self.isPrintUser = ko.observable(false);

        if(rootParams.dashboard.appData.segment === "CORP"){
            self.isPrintUser(true);
        }

        self.transactionID = self.params.transactionResponse ? self.params.transactionResponse.referenceNumber || self.params.transactionResponse.status.referenceNumber : self.params.jqXHR.responseJSON.referenceNumber || self.params.jqXHR.responseJSON.status.referenceNumber;

        self.testFPS = self.params.confirmScreenExtensions && self.params.confirmScreenExtensions.isOMB ? self.params.network : self.params.jqXHR && self.params.jqXHR.responseJSON && self.params.jqXHR.responseJSON.transactionAction && self.params.jqXHR.responseJSON.transactionAction.transactionDTO && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.transactionSnapshot && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.transactionSnapshot.paymentDetails && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.transactionSnapshot.paymentDetails.network;

        self.isUserManagementUsersUpdate = ko.observable(self.params.jqXHR && self.params.jqXHR.responseJSON && self.params.jqXHR.responseJSON.transactionAction && self.params.jqXHR.responseJSON.transactionAction.transactionDTO && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.taskDTO && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.taskDTO.id === "MT_N_UUS" && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails.status === "APPROVED");

        if (self.isUserManagementUsersUpdate()) {
            self.itokenRemarks(self.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails.remarks || null);
        }

        self.testWhetherFps = function (data) {

            if (data) {
                data = data.trim();

                const x = data.startsWith("FRN") || data.startsWith("M") || data.startsWith("EPC") || data.startsWith("FTR") || data.startsWith("RMT") ? data : data === "" ? undefined : rootParams.baseModel.format(self.confirmScreenResources.CDCReferenceNo, {
                        refNo: data
                    });

                return x;
            }
        };

        self.downloadQRCodes = function(){
            ConfirmScreenModel.downloadQR(self.sequenceId(), self.payerSequenceId()).then(function (data) {
                if (data !== null && data.allQrCodeData && data.allQrCodeData.length > 0) {
                    self.base64(data.allQrCodeData[0].qrCodeRawData);
                    self.showSingleQR(true);
                }
            });
        };

        self.createAnotherQR = function(){
            rootParams.dashboard.loadComponent("qr-code-create");
        };

        if(rootParams.rootModel.params && rootParams.rootModel.params.confirmScreenExtensions && rootParams.rootModel.params.confirmScreenExtensions.successRecordList){
            if(rootParams.rootModel.params.confirmScreenExtensions.successRecordList.length > 1){

                self.showQRButtons(true);
                self.isSingleRecord(false);

                if(rootParams.rootModel.params.transactionResponse && rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO && rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.sequenceId){
                    self.sequenceId(rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.sequenceId);
                }

            }else{
                self.showQRButtons(true);
                self.isSingleRecord(true);

                if(rootParams.rootModel.params.transactionResponse && rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO && rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.sequenceId){
                    self.sequenceId(rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.sequenceId);

                    if(rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.payerData && rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.payerData[0].sequenceId){
                        self.payerSequenceId(rootParams.rootModel.params.transactionResponse.fpsmerchantQrCodeDTO.payerData[0].sequenceId);

                        ConfirmScreenModel.getQR(self.sequenceId(), self.payerSequenceId()).then(function (data) {
                            if (data !== null && data.allQrCodeData && data.allQrCodeData.length > 0) {
                                self.base64(data.allQrCodeData[0].qrCodeRawData);
                                self.showSingleQR(true);
                            }
                        });
                    }
                }
            }

            rootParams.rootModel.params.successRecordDataSource = ko.observable(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(rootParams.rootModel.params.confirmScreenExtensions.successRecordList, {
                idAttribute: "srNo"
            })));
        }

        if(rootParams.rootModel.params && rootParams.rootModel.params.confirmScreenExtensions && rootParams.rootModel.params.confirmScreenExtensions.errorRecordList){
            rootParams.rootModel.params.errorRecordDataSource = ko.observable(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(rootParams.rootModel.params.confirmScreenExtensions.errorRecordList, {
                idAttribute: "srNo"
            })));
        }

        if (self.params && self.params.fpsQRrefNo) {
            self.isFPSQRrefNo(true);
            self.fpsQRrefNo(self.params.fpsQRrefNo);
        }

        if (self.params && self.params.fpsQRCreatedDate) {
            self.isFPSQRCreatedDate(true);
            self.fpsQRCreatedDate(self.params.fpsQRCreatedDate);
        }

        if (self.params && self.params.hostReferenceNumber) {
            self.params.hostReferenceNumber = self.params.hostReferenceNumber.trim();
        }

        if (self.params && self.params.confirmScreenExtensions && self.params.confirmScreenExtensions.isOMB) {
            if (self.testFPS === "FPS" && self.params.hostReferenceNumber) {
                self.hostReferenceNumber = self.params.hostReferenceNumber;
            } else if (self.params.transactionResponse && self.params.transactionResponse.status && self.params.transactionResponse.status.externalReferenceNumber) {
                self.hostReferenceNumber = self.testWhetherFps(self.params.transactionResponse.status.externalReferenceNumber);
            } else {
                self.hostReferenceNumber = self.params.hostReferenceNumber ? self.testWhetherFps(self.params.hostReferenceNumber) : self.params.hostReferenceNumber;
            }
        } else if (self.testFPS === "FPS" && self.params.hostReferenceNumber) {
            self.hostReferenceNumber = self.testWhetherFps(self.params.hostReferenceNumber);
        } else if (self.params.confirmScreenExtensions && self.params.confirmScreenExtensions.hostReferenceNumber) {
            self.hostReferenceNumber = self.params.confirmScreenExtensions.hostReferenceNumber;
        } else if (self.params.transactionResponse && self.params.transactionResponse.status && self.params.transactionResponse.status.externalReferenceNumber) {
            self.hostReferenceNumber = self.testWhetherFps(self.params.transactionResponse.status.externalReferenceNumber);
        } else {
            self.hostReferenceNumber = self.testWhetherFps(self.params.hostReferenceNumber);
        }

        if (self.params && self.params.hostReferenceNumber) {
            self.params.hostReferenceNumber = self.params.hostReferenceNumber.trim();
        }

        if (self.params.transferType) {
            self.transferType = self.params.transferType;
        }

        if (self.params.targetAccountType) {
            self.targetAccountType = self.params.targetAccountType;
        }

        if (self.params.transactionMethod) {
            self.transactionMethod = self.params.transactionMethod;
        }

        if (self.params.maxInstruction) {
            self.maxInstruction = self.params.confirmScreenExtensions.maxInstruction;
        }

        if (self.params.mode) {
            self.mode = self.params.mode;
        }

        if (self.params.taskCodes) {
            self.taskCodes = self.params.confirmScreenExtensions.taskCodes;
        }

        self.remarks = self.params.remarks ? self.params.remarks : null;
        self.exchangeRate = self.params.exchangeRate ? self.params.exchangeRate : null;
        self.httpStatus = self.params.transactionResponse ? self.params.transactionResponse.getResponseStatus() : self.params.jqXHR.status;
        self.serviceNo = self.params.serviceNo ? self.params.serviceNo : null;
        self.srNo = self.params.srNo ? self.params.srNo : null;
        self.isStatusWordRequired = Constants.userSegment !== "RETAIL" && self.params.isStatusWordRequired === undefined;
        self.transactionName = self.params.transactionName;
        self.buttonTemplate = self.params.template;
        self.confirmScreenExtensions = self.params.confirmScreenExtensions && self.params.confirmScreenExtensions.isSet ? self.params.confirmScreenExtensions : null;
        self.enableEReceipt(self.params.transactionResponse ? self.params.transactionResponse.status.receiptAvailable : self.params.jqXHR.responseJSON.status.receiptAvailable);
        self.isEdit = ko.observable(self.params.isEdit ? self.params.isEdit : false);

        const errorCodes = [];

        self.eReceiptDetails = {
            enableEReceipt: self.enableEReceipt(),
            header: self.confirmScreenResources.confirm.eReceipt,
            altText: self.confirmScreenResources.confirm.downloadEreceipt,
            title: self.confirmScreenResources.confirm.downloadEreceiptAlt
        };

        if (self.hostReferenceNumber) {
            self.FPSreferenceNo = ko.observable(self.hostReferenceNumber.startsWith("FRN"));
        }

        if (self.confirmScreenExtensions && self.confirmScreenExtensions.taskCode) {
            if (self.params.transactionResponse ? !self.params.transactionResponse.transactionAction : !self.params.jqXHR.responseJSON.transactionAction) {
                ConfirmScreenModel.fetchFeedbackTemplates(self.confirmScreenExtensions.taskCode).then(function (data) {
                    if (data.feedbackEnabled && (data.feedbackTemplateDTO.length > 0) && data.feedbackTemplateDTO[0].definitionDTOs[0].transactionId) {
                        if (data.feedbackforTransaction === "ONCE") {
                            if (!data.feedbackCaptured) {
                                self.feedbackTemplateDTO(data);
                            }
                        } else {
                            self.feedbackTemplateDTO(data);
                        }
                    }
                });
            }
        }

        if (self.params.transactionName === "Preferential Time Deposit Interest rate - Setup" || self.params.transactionName === "Preferential Time Deposit Enquiry and Maintenance") {
            if (self.confirmScreenExtensions.confirmScreenDetails[1].length > 4 && self.confirmScreenExtensions.confirmScreenDetails[1][4].label !== undefined && self.confirmScreenExtensions.confirmScreenDetails[1][4].label === "Coupon Code") {
                self.confirmScreenExtensions.confirmScreenDetails[1][4].value = self.params.couponCode;
            }
        }

        if(self.params.transactionName && (self.params.transactionName.toLowerCase().startsWith("fx") || self.params.transactionName === "外匯交易" || self.params.transactionName === "外汇交易")){
            if(self.params.confirmScreenExtensions && self.params.confirmScreenExtensions.confirmScreenDetails){
                if(self.params.confirmScreenExtensions.confirmScreenDetails.mode && self.params.confirmScreenExtensions.confirmScreenDetails.mode === "approval" && self.params.confirmScreenExtensions.confirmPayload && self.params.confirmScreenExtensions.confirmPayload.cin === ""){
                    if(rootParams.rootModel.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails.status === "PENDING_APPROVAL"){
                        self.showDisclaimer(true);
                    }
                    else if(rootParams.rootModel.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails.status === "APPROVED" && self.params.jqXHR.responseJSON.transactionAction.transactionDTO.processingDetails.status !== "F" && self.params.jqXHR.responseJSON.result.status.result !== "FAILED"){
                        if((self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom === "HKD" && self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo !== "HKD") || (self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo === "HKD" && self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom !== "HKD")){
                            self.params.confirmScreenExtensions.exchangeRate = rootParams.baseModel.format(self.confirmScreenResources.confirm.fxAgreedRate.exchangePanel, {
                                option: self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom === "HKD" ? self.confirmScreenResources.confirm.fxAgreedRate.sell : self.confirmScreenResources.confirm.fxAgreedRate.buy,
                                currency1: self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo === "HKD" ? self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom] : self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo],
                                currency2: self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom === "HKD" ? self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom] : self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo],
                                rate: Number(rootParams.baseModel.exchangeRate()).toFixed(8)
                            });
                        }
                        else if(self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom !== "HKD" && self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo !== "HKD"){
                            self.params.confirmScreenExtensions.exchangeRate = rootParams.baseModel.format(self.confirmScreenResources.confirm.fxAgreedRate.exchangePanelCurrency, {
                                from: self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyFrom],
                                to: self.confirmScreenResources.confirm.fxAgreedRate.toCurrencyList1[self.params.confirmScreenExtensions.confirmScreenDetails.accountCurrencyTo],
                                rate: Number(rootParams.baseModel.exchangeRate()).toFixed(8)
                            });
                        }

                        self.showExchangeRate(true);
                    }
                    else if(rootParams.rootModel.params.jqXHR.responseJSON.transactionAction.transactionDTO.approvalDetails.status === "MODIFICATION_REQUESTED"){
                        self.hideExchange(true);
                    }
                    else{
                        self.hideExchange(true);
                    }

                }
                else if(self.params.confirmScreenExtensions.confirmScreenDetails.mode && self.params.confirmScreenExtensions.confirmScreenDetails.mode === "review"){
                    if(self.params.confirmScreenExtensions.isOMB() && self.params.confirmScreenExtensions.isOMB() === true && self.params.transactionResponse.fxAgreeOWNCRUDDTO){
                        if(self.params.confirmScreenExtensions.confirmPayload.cin === ""){
                            rootParams.baseModel.exchangeRate(Number(self.params.transactionResponse.fxAgreeOWNCRUDDTO.calculatedRate.split(/\s/)[1]).toFixed(8));

                            const tempExchangeRate = self.params.confirmScreenExtensions.exchangeRateOmb.replace(/\S+$/, rootParams.baseModel.exchangeRate());

                            self.params.confirmScreenExtensions.exchangeRateOmb = tempExchangeRate;
                        }

                    }
                }
            }

        }

        self.confirmationModalData = {
            componentName: null,
            data: null,
            header: null,
            openHandler: function () {
                $("#confirm-modal").trigger("openModal");
            },
            closeHandler: function () {
                self.showModal(false);
            }
        };

        const txnName = self.transactionName || self.confirmScreenResources.confirm.DEFAULT_TXN_NAME;

        self.componentNameFromNLS = ko.observable();

        self.componentNameFromNLS(txnName);

        rootParams.dashboard.headerName(self.componentNameFromNLS());

        const evaluateStatus = function (jqXHR) {
            const JSONResponse = jqXHR.responseJSON || jqXHR;

            if (JSONResponse.transactionAction && JSONResponse.transactionAction.transactionDTO) {
                const response = JSONResponse.transactionAction.transactionDTO;

                if (response.errors && response.errors.length > 0) {
                    response.errors.forEach(function (errors) {
                        errorCodes.push(errors.errorCode);
                    });
                }

                if (response.processingDetails && response.processingDetails.status === "S") {
                    return "FINAL_LEVEL_APPROVED";
                } else if (response.processingDetails && response.processingDetails.status === "P" && errorCodes.length > 0 && errorCodes.indexOf("DIGX_PROD_WSDL_TIMEOUT_0000") > -1) {
                    return "PROCESSING";
                } else if (response.processingDetails && response.processingDetails.status === "P" && response.approvalDetails.status === "MODIFICATION_REQUESTED") {
                    return "MODIFY";
                } else if (response.processingDetails && response.processingDetails.status === "P") {
                    return "MID_LEVEL_APPROVED";
                } else if (response.processingDetails.status === "F" && response.processingDetails.currentStep === "exec") {
                    return "REJECT_BY_HOST";
                } else if (response.processingDetails && response.processingDetails.status === "F") {
                    return "REJECT";
                }
            } else if (jqXHR.status === 202 || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 202)) {
                return "INITIATED";
            } else if ((jqXHR.status === 200 || jqXHR.status === 201 || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 200) || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 201)) && (jqXHR.status.lastKnownError && jqXHR.status.lastKnownError.errorCode && jqXHR.status.lastKnownError.errorCode === "DIGX_PROD_WSDL_TIMEOUT_0000")) {
                return "PROCESSING";
            } else if ((jqXHR.status === 200 || jqXHR.status === 201 || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 200) || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 201)) && rootParams.rootModel.params && rootParams.rootModel.params.transactionResponse && rootParams.rootModel.params.transactionResponse.fileUpload !== undefined) {
                return "FILE_UPLOADED";
            } else if (jqXHR.status === 200 || jqXHR.status === 201 || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 200) || (jqXHR.getResponseStatus && jqXHR.getResponseStatus() === 201)) {
                return "AUTO_AUTH";
            } else {
                return "REJECT_BY_HOST";
            }
        };

        self.showOBMFlowMarketingWidgetBanner = function () {
            const MARKETING_WIDGET_CONFIG = {
                "fx-rate-transaction": "W004",
                "adhoc-payments-generic": "W006",
                "generic-money-transfer": "W006"
            },
                params = rootParams.rootModel.params || {},
                makeAnotherParams = params.makeAnotherParams || {},
                transactionResponse = params.transactionResponse,
                componentName = makeAnotherParams.componentName,
                flowName = makeAnotherParams.params &&
                    makeAnotherParams.params.flowName,

                widgetId =
                    MARKETING_WIDGET_CONFIG[componentName] ||
                    MARKETING_WIDGET_CONFIG[flowName];

            if (!widgetId || !transactionResponse) {
                return;
            }

            if (evaluateStatus(transactionResponse) !== "AUTO_AUTH") {
                return;
            }

            self.marketBannerParams(
                Object.assign(
                    {},
                    rootParams.dashboard.getMarketingWidgetParams(),
                    {
                        dashboard: rootParams.dashboard,
                        widgetId: widgetId
                    }
                )
            );
        };

        self.showOBMFlowMarketingWidgetBanner();

        self.getStatusMessage = function (jqXHR) {
            const status = evaluateStatus(jqXHR),
                transactionAction = (jqXHR.responseJSON && jqXHR.responseJSON.transactionAction) || jqXHR.transactionAction,
                transactionDTO = transactionAction && transactionAction.transactionDTO,
                transactionError = transactionDTO && transactionDTO.errors && transactionDTO.errors[0];

            if (status === "REJECT_BY_HOST" && transactionError) {
                self.reason($("<div/>").html(transactionError.errorMessage).text());
            }

            if (status === "PROCESSING" && transactionError) {
                self.reason($("<div/>").html(transactionError.errorMessage).text());
            } else if (status === "PROCESSING" && jqXHR.status && jqXHR.status.message && jqXHR.status.message.detail) {
                self.reason(jqXHR.status.message && jqXHR.status.message.detail);
            }

            if (!(errorCodes.length > 0 && errorCodes.indexOf("DIGX_PROD_WSDL_TIMEOUT_0000") > -1) && self.confirmScreenExtensions && self.confirmScreenExtensions.confirmScreenStatusEval) {
                if(jqXHR.responseJSON && jqXHR.responseJSON.transactionAction && jqXHR.responseJSON.transactionAction.transactionDTO && jqXHR.responseJSON.transactionAction.transactionDTO.lastUpdatedBy){
                    if(jqXHR.responseJSON.transactionAction.transactionDTO.lastUpdatedBy === "SYSTEMWITHDRAW"){
                        return self.confirmScreenResources.confirm.status.WITHDRAWAL;
                    }
                }

                return self.confirmScreenExtensions.confirmScreenStatusEval(jqXHR, status);
            }

            if(jqXHR.responseJSON && jqXHR.responseJSON.transactionAction && jqXHR.responseJSON.transactionAction.transactionDTO && jqXHR.responseJSON.transactionAction.transactionDTO.lastUpdatedBy){
                if(jqXHR.responseJSON.transactionAction.transactionDTO.lastUpdatedBy === "SYSTEMWITHDRAW"){
                    return self.confirmScreenResources.confirm.status.WITHDRAWAL;
                }
            }

            return self.confirmScreenResources.confirm.status[status];
        };

        self.successMessage = function (jqXHR) {
            let status = evaluateStatus(jqXHR);

            if(self.transactionName === "FPS Merchant Addressing - Create" || self.transactionName === "FPS Merchant Addressing - Delete" || self.transactionName === "FPS Merchant Addressing - Edit") {
                self.isFPSMerchant(true);
            }

            if(rootParams.rootModel.params && rootParams.rootModel.params.taskCode && rootParams.rootModel.params.taskCode === "FPSMRCH_N_CFP"){
                return self.confirmScreenResources.confirm.FPSMerchantSuccessMessage;
            }

            if (status !== "PROCESSING" && self.confirmScreenExtensions && self.confirmScreenExtensions.confirmScreenMsgEval) {
                return self.confirmScreenExtensions.confirmScreenMsgEval(jqXHR, txnName, status, self.transactionID, self.hostReferenceNumber, self.isEdit(), self.confirmScreenExtensions);
            } else if (self.confirmScreenResources.confirm.staticMessages[Constants.userSegment] && self.transactionName !== null && Constants.userSegment === "CORP" && status === "PROCESSING") {
                if (self.transactionName === "Fund Transfers") {
                    self.transactionName = jqXHR.responseJSON.transactionAction.transactionDTO.taskDTO.name;
                }

                return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                    transactionName: self.transactionName

                });

            } else if (self.confirmScreenResources.confirm.staticMessages[Constants.userSegment] && self.transactionName !== null && Constants.userSegment === "ADMIN") {

                if(status === "INITIATED" && (self.transactionName === "FPS Merchant Addressing - Create" || self.transactionName === "FPS Merchant Addressing - Delete" || self.transactionName === "FPS Merchant Addressing - Edit")) {
                    status = "FPS_" + status + "_TXNNAME";

                    return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                        transactionName: self.transactionName,
                        todayDate: self.todayDate()
                    });
                }

                if(status === "FINAL_LEVEL_APPROVED" && (self.transactionName === "FPS Merchant Addressing - Create" || self.transactionName === "FPS Merchant Addressing - Delete" || self.transactionName === "FPS Merchant Addressing - Edit")) {
                    status = "FPS_" + status + "_TXNNAME";

                    return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                        transactionName: self.transactionName,
                        todayDate: self.todayDate()
                    });
                }

                status = status + "_TXNNAME";

                return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                    transactionName: self.transactionName,
                    todayDate: self.todayDate()
                });
            } else if (self.confirmScreenResources.confirm.staticMessages[Constants.userSegment] && self.transactionName !== null && Constants.userSegment === "CORPADMIN") {
                if (status === "MID_LEVEL_APPROVED" && jqXHR.responseJSON && jqXHR.responseJSON.transactionAction && jqXHR.responseJSON.transactionAction.transactionDTO && jqXHR.responseJSON.transactionAction.transactionDTO.transactionName === "MT_N_UUS_SPR") {
                    return self.confirmScreenResources.confirm.restSignerPINMidLevelApprovedSuccessMessage;
                }

                status = status + "_TXNNAME";

                return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                    transactionName: self.transactionName
                });
            } else if (self.confirmScreenResources.confirm.staticMessages[Constants.userSegment] && self.transactionName !== null && Constants.userSegment === "CORP") {
                if (status === "INITIATED" || status === "FINAL_LEVEL_APPROVED") {

                    return rootParams.baseModel.format(self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                        transactionName: self.transactionName
                    });
                } else if (status === "REJECT" || status === "REJECT_BY_HOST" || status === "MODIFY") {
                    if ((status === "REJECT_BY_HOST" && jqXHR.responseJSON && jqXHR.responseJSON.transactionAction && jqXHR.responseJSON.transactionAction.transactionDTO) || (status === "REJECT_BY_HOST" && jqXHR.transactionAction && jqXHR.transactionAction.transactionDTO)) {
                        return $("<div/>").html((jqXHR.responseJSON && jqXHR.responseJSON.transactionAction.transactionDTO.errors[0].errorMessage) || jqXHR.transactionAction.transactionDTO.errors[0].errorMessage).text();
                    }

                    return self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status];

                }
            } else if (self.confirmScreenResources.confirm.staticMessages[Constants.userSegment]) {
                return self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status];
            }

            const message = status === "AUTO_AUTH" || status === "FINAL_LEVEL_APPROVED" ? self.confirmScreenResources.confirm[Constants.userSegment + "_SUCCESS_MESSAGE"] : null;

            if (jqXHR && jqXHR.responseJSON && jqXHR.responseJSON.transactionAction && jqXHR.responseJSON.transactionAction.action === "REJECT") {
                return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.defaultRejectMessage, {
                    todayDate: self.todayDate(),
                    status: self.confirmScreenResources.confirm.status[status],
                    transactionID: self.transactionID,
                    hostReferenceNumber: self.hostReferenceNumber
                });
            }

            if (self.confirmScreenExtensions && self.confirmScreenExtensions.taskCode && self.confirmScreenExtensions.taskCode === "PC_F_INTRNL") {
                if (status && status === "INITIATED") {
                    return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.successMessageConfirm, {
                        txnName: self.componentNameFromNLS(),
                        todayDate: self.todayDate(),
                        status: self.confirmScreenResources.confirm.status[status],
                        transactionID: self.transactionID,
                        hostReferenceNumber: self.hostReferenceNumber
                    });
                }

                if (status && status === "FINAL_LEVEL_APPROVED") {
                    return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.successMessageComplete, {
                        txnName: self.componentNameFromNLS(),
                        todayDate: self.todayDate(),
                        status: self.confirmScreenResources.confirm.status[status],
                        transactionID: self.transactionID,
                        hostReferenceNumber: self.hostReferenceNumber
                    });
                }
            }

            if (status && status === "MID_LEVEL_APPROVED") {
                return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                    txnName: self.componentNameFromNLS(),
                    todayDate: self.todayDate(),
                    status: self.confirmScreenResources.confirm.status[status],
                    transactionID: self.transactionID,
                    hostReferenceNumber: self.hostReferenceNumber
                });
            }

            if (status && status === "FILE_UPLOADED") {
                return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.staticMessages[Constants.userSegment][status], {
                    txnName: self.componentNameFromNLS(),
                    todayDate: self.todayDate(),
                    status: self.confirmScreenResources.confirm.status[status],
                    transactionID: self.transactionID,
                    hostReferenceNumber: self.hostReferenceNumber
                });
            }

            return rootParams.baseModel.format(message || self.confirmScreenResources.confirm.defaultSuccessMessage, {
                txnName: self.componentNameFromNLS(),
                todayDate: self.todayDate(),
                status: self.confirmScreenResources.confirm.status[status],
                transactionID: self.transactionID,
                hostReferenceNumber: self.hostReferenceNumber
            });
        };

        (function (jqXHRs) {
            if (!Array.isArray(jqXHRs)) {
                jqXHRs = [jqXHRs];
            }

            jqXHRs.forEach(function (jqXHR) {
                const currentStatus = evaluateStatus(jqXHR),
                    isRejected = currentStatus === "REJECT_BY_HOST";

                self.headerMessages.push({
                    icon: isRejected ? "dashboard/status_error.svg" : "dashboard/confirmation.svg",
                    headerMessage: isRejected ? self.confirmScreenResources.confirm.errorText : self.confirmScreenResources.confirm.confirmText,
                    summaryMessage: self.successMessage(jqXHR),
                    headerStyle: isRejected ? "errorHeader" : "successHeader",
                    eReceiptDetails: self.eReceiptDetails
                });
            });
        })(self.params.transactionResponse ? self.params.transactionResponse.batchDetailResponseDTOList || self.params.transactionResponse :
            self.params.jqXHR.responseJSON.batchDetailResponseDTOList || self.params.jqXHR);

        self.openTransaction = function (compname, applicationType, moduleURL, standalone) {
            rootParams.baseModel.registerComponent(compname, applicationType);

            if (Constants.userSegment === "RETAIL" && !standalone) {
                self.selectedTab = null;

                rootParams.dashboard.loadComponent("manage-accounts", {
                    defaultTab: compname,
                    applicationType: applicationType,
                    moduleURL: moduleURL,
                    isSuccess: true
                });
            } else {
                rootParams.dashboard.loadComponent(compname);
            }
        };

        self.openNewTransaction = function (compname, applicationType, params) {
            rootParams.baseModel.registerComponent(compname, applicationType);
            rootParams.dashboard.loadComponent(compname, params);
        };

        self.share = function () {
            window.plugins.sharing.shareWithOptions({
                message: self.params.shareMessage
            });
        };

        self.openModalWindow = function (componentName, params, header) {
            Object.assign(self.confirmationModalData, {
                componentName: componentName,
                data: params,
                header: header
            });

            self.showModal(true);
        };

        self.handleOk = function () {
            if (self.params.handleOk) {
                self.params.handleOk();
            } else if (self.homeComponent) {
                rootParams.dashboard.loadComponent(self.homeComponent, {});
            } else {
                rootParams.dashboard.switchModule();
            }
        };

        self.downloadEreceipt = function () {
            ConfirmScreenModel.downloadEreceipt(self.transactionID);
        };

        self.showFeedbackOverlay = function () {
            self.renderFeedbackModule(true);
        };

        self.openApprovalPage = function () {
            rootParams.dashboard.loadComponent("transaction-detail", {
                transactionId: self.transactionID,
                type: self.transactionName,
                isPending: true,
                isApprovalPending: true
            });
        };

        (function () {
            const interval = setInterval(() => {
                if ($("#tdPopup").length) {
                    clearInterval(interval);

                    $("#tdPopup").click(function () {
                        $("#myModal").trigger("openModal");
                    });
                }
            }, 50);
        })();

        self.removeElementForPrintingUsingClassName = function (element, className) {

            const list = element.getElementsByClassName(className);

            if (list) {

                while ( list.length > 0) {
                    list[0].remove();
                }
            }
        };

        self.recordsTabChanged = function (event) {
            if (event.detail.value) {
                self.qrRecords(false);

                if(event.detail.value === "successRecords"){
                    self.showSuccessList(false);
                    self.showErrorList(false);

                    rootParams.rootModel.params.successRecordDataSource = ko.observable(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(rootParams.rootModel.params.confirmScreenExtensions.successRecordList, {
                        idAttribute: "srNo"
                    })));

                    ko.tasks.runEarly();
                    $("#successRecordDataSourceId").ojTable("refresh");
                    self.showSuccessList(true);
                }else if(event.detail.value === "errorRecords"){
                    self.showErrorList(false);
                    self.showSuccessList(false);

                    rootParams.rootModel.params.errorRecordDataSource = ko.observable(new oj.PagingTableDataSource(new oj.ArrayTableDataSource(rootParams.rootModel.params.confirmScreenExtensions.errorRecordList, {
                        idAttribute: "srNo"
                    })));

                    ko.tasks.runEarly();
                    $("#errorRecordDataSourceId").ojTable("refresh");
                    self.showErrorList(true);
                }

                ko.tasks.runEarly();
                self.qrRecords(true);

            }
        };

        self.printPage = function () {
            const src = document.getElementById("maincontent") || document.body,
            cloned = src.cloneNode(true),

            isSafariBrowser = (function () {
                const ua = navigator.userAgent || "",
                    vendor = navigator.vendor || "";

                return vendor.indexOf("Apple") !== -1 &&
                    /Safari/i.test(ua) &&
                    !/(Chrome|Chromium|CriOS|FxiOS|Edg|OPR|OPiOS|Android)/i.test(ua);
            })(),

            isWindowsChromeOrEdgeBrowser = (function () {
                const ua = navigator.userAgent || "",
                    platform = navigator.platform || "",
                    isWindows = /Windows/i.test(ua) || /Win/i.test(platform),
                    isEdge = /\bEdg\//i.test(ua),
                    isChrome = /\b(Chrome|Chromium)\//i.test(ua) && !/\b(Edg|OPR|Opera)\//i.test(ua);

                return isWindows && (isEdge || isChrome);
            })();

            self.removeElementForPrintingUsingClassName(cloned, "confirm-screen__list");
            self.removeElementForPrintingUsingClassName(cloned, "oj-helper-detect-contraction");
            self.removeElementForPrintingUsingClassName(cloned, "oj-helper-detect-expansion");
            self.removeElementForPrintingUsingClassName(cloned, "oj-table-column-header-acc-select-row");
            self.removeElementForPrintingUsingClassName(cloned, "oj-table-data-cell-acc-select");
            self.removeElementForPrintingUsingClassName(cloned, "modal-window-container");
            self.removeElementForPrintingUsingClassName(cloned, "payee-card-dialog");
            self.removeElementForPrintingUsingClassName(cloned, "oj-table-data-cell-acc-select");
            self.removeElementForPrintingUsingClassName(cloned, "oj-button");
            self.removeElementForPrintingUsingClassName(cloned, "button-container");

            if (cloned.querySelector(".oj-flex-item[data-bind='text:$component.confirmScreenResources.confirm.actions.nextAction']")) {
                cloned.querySelector(".oj-flex-item[data-bind='text:$component.confirmScreenResources.confirm.actions.nextAction']").remove();
            }

            const marketingBanner = cloned.querySelector(".marketing-banner-container");

            if (marketingBanner) {
                marketingBanner.remove();
            }

            const toArr = function (list) {
                return Array.prototype.slice.call(list);
            },
            confirmScreenExtensionTemplate = self.confirmScreenExtensions && self.confirmScreenExtensions.template,
            is3ColumnsMode = self.confirmScreenExtensions && self.confirmScreenExtensions.is3ColumnsMode,
            confirmScreenLocale = ResourceBundle._ojLocale_ || "en",
            isEnglishConfirmScreenLocale = confirmScreenLocale === "en",
            isChineseConfirmScreenLocale = confirmScreenLocale === "zh-Hant" || confirmScreenLocale === "zh-Hans-CN",
            isLiquidityManagementConfirmTemplate = confirmScreenExtensionTemplate === "confirm-screen/liquidity-management-template" ||
                confirmScreenExtensionTemplate === "liquidity-management-template",
            isSafariEnglishLiquidityConfirmTemplate = isSafariBrowser && isEnglishConfirmScreenLocale && isLiquidityManagementConfirmTemplate,
            isCorpBatchConfirmTemplate = [
                "confirm-screen/corp-auto-pay-template",
                "confirm-screen/corp-payroll-template",
                "confirm-screen/corp-collection-template"
            ].indexOf(confirmScreenExtensionTemplate) > -1,
            isTimeDepositConfirmTemplate = confirmScreenExtensionTemplate === "confirm-screen/td-template" && isChineseConfirmScreenLocale;

            toArr(cloned.querySelectorAll("a")).forEach(function (a) {

                const p = a.parentNode;

                if(!p){
                    return;
                }

                while(a.firstChild){
                    p.insertBefore(a.firstChild, a);
                }

                p.removeChild(a);
            });

            (() => {
                if(rootParams.dashboard.headerName()){
                    const hasHeader = cloned.querySelector("h1.comp-title, .page-title h1, .proxy-header h1"),
                    headerText = rootParams.dashboard.headerName(),
                    headerContainer = document.createElement("div"),
                    h1 = document.createElement("h1");

                    if (hasHeader) {
                        return;
                    }

                    headerContainer.className = "page-title proxy-header";
                    h1.className = "comp-title";
                    h1.textContent = headerText;
                    headerContainer.appendChild(h1);

                    if (cloned.firstChild) {
                        cloned.insertBefore(headerContainer, cloned.firstChild);
                    } else {
                        cloned.appendChild(headerContainer);
                    }
                }
            })();

            const extensionTemplateContainers = toArr(cloned.querySelectorAll("#confirmScreenPrint [data-bind*='confirmScreenExtensions.template']"));

            extensionTemplateContainers.forEach(function (extensionTemplateContainer) {
                if (isSafariEnglishLiquidityConfirmTemplate) {
                    extensionTemplateContainer.classList.add("confirm-screen-extension-print");
                }

                if (isCorpBatchConfirmTemplate) {
                    extensionTemplateContainer.classList.add("confirm-screen-corp-batch-print");
                }

                if (isSafariBrowser && isTimeDepositConfirmTemplate) {
                    extensionTemplateContainer.classList.add("confirm-screen-td-print");
                }

                if (!isSafariEnglishLiquidityConfirmTemplate) {
                    return;
                }

                toArr(extensionTemplateContainer.querySelectorAll("obdx-row")).forEach(function (row) {
                    const values = toArr(row.querySelectorAll(".row__value")).filter(function (valueNode) {
                        return valueNode.textContent && valueNode.textContent.trim();
                    });

                    if (values.length > 3) {
                        row.classList.add("confirm-screen-splittable-row");
                    }
                });

                toArr(extensionTemplateContainer.querySelectorAll(
                    "[data-bind*='disclaimer'],[data-bind*='details-notes'],[data-bind*='international-disclaimer'],[data-bind*='domestic-disclaimer'],[data-bind*='ownAccounts-disclaimer'],[data-bind*='internal-disclaimer'],[data-bind*='bea-china-disclaimer']"
                )).forEach(function (section) {
                    section.classList.add("confirm-screen-splittable-section");

                    if (section.parentElement) {
                        section.parentElement.classList.add("confirm-screen-splittable-section");
                    }
                });
            });

            if (isSafariBrowser) {
                toArr(cloned.querySelectorAll("oj-table")).forEach(function (tableElement) {
                    tableElement.classList.remove("oj-table-scroll", "oj-table-scroll-vertical", "oj-table-scroll-horizontal");

                    toArr(tableElement.querySelectorAll("table, thead, tbody, tr, th, td, .oj-table-column-header, .oj-table-column-header-text")).forEach(function (element) {
                        element.style.removeProperty("width");
                        element.style.removeProperty("min-width");
                        element.style.removeProperty("max-width");
                        element.style.removeProperty("height");
                        element.style.removeProperty("min-height");
                    });
                });
            }

            const contentHtml = cloned.outerHTML,

            headCss = Array.prototype.map.call(
                document.querySelectorAll("link[rel='stylesheet'], style"),
                function (n) { return n.outerHTML; }
                ).join("") +
                    "<style>@media print{body::before,body::after{display:none!important;position:static!important;}}</style>",

            whiteBgCss = "<style>html,body{background:#fff!important;width:100%!important;max-width:100%!important;min-width:0!important;overflow:visible!important;} .main-flex,.oj-panel,.dashboard,.dashboard .container,.form-main-container,.form-main-container-border,#maincontent{width:100%!important;max-width:100%!important;min-width:0!important;height:auto!important;max-height:none!important;overflow:visible!important;box-sizing:border-box!important;} h1{padding-left: 15px !important;color: #004068 !important;} .transaction-journey,.transaction-journey-row{position:relative!important;} .transaction-journey__connector{display:none!important;} .transaction-journey-train-box__block--icon-container{position:relative!important;z-index:2!important;} .journey-connector-svg{position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;pointer-events:none;z-index:1;} .journey-connector-svg line{stroke:#e0e0e0;stroke-width:2;shape-rendering:crispEdges;stroke-linecap:round;} a[data-original-href]{text-decoration: underline !important; color: #0645AD !important; pointer-events: none !important; cursor: default !important;} a[href]{pointer-events:none !important;} a[href^='tel'],a[href^='mailto'],a[href^='sms'],a[href^='cal']{text-decoration: none !important; color: inherit !important; pointer-events: none !important; cursor: default !important; } @media print{body{display:block!important;transform:none!important;-webkit-transform:none!important;scale:1!important;max-width:none!important;height:auto!important;margin:0!important;width:100%!important;min-width:0!important;overflow:visible!important;}.main-flex,.oj-panel,#maincontent{max-height:none!important;overflow:visible!important;width:100%!important;}::after{content:none!important;font-size:0!important;display:none!important;}header,footer{display:block!important;}.form-main-container,.form-main-container-border{height:auto!important;overflow:visible!important;}.dashboard,.dashboard .container{height:auto!important;min-height:0!important;overflow:visible!important;}.dashboard .container .main-content{padding-bottom:0!important;}main,section,article{height:auto!important;overflow:visible!important;}.oj-panel{height:auto!important;overflow:visible!important;}#maincontent .page-header-container.txn-page,#maincontent .page-header-container.txn-page>.oj-flex,#maincontent .page-header-container.txn-page .oj-flex-item,#maincontent .main-content,#maincontent .main-content>.oj-flex,#maincontent .main-content>.oj-flex>.oj-flex-item,obdxcomponent.confirm-screen-container,.confirm-screen-container,.confirm-screen-container #confirmScreenPrint,.confirm-screen-container>.confirm-screen,.confirm-screen-container>.form-main-container{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}#maincontent .page-header-container.txn-page{break-after:avoid!important;page-break-after:avoid!important;}.transaction-journey-train-box__block--icon-container{display:inline-flex!important;align-items:center!important;justify-content:center!important;width:36px!important;height:36px!important;min-width:36px!important;min-height:36px!important;border-radius:50%!important;background-color:#1a3d5c!important;position:relative!important;z-index:2!important;margin:0 auto!important;box-sizing:border-box!important;-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important;}.notes{background-color:#f2f2f2!important;-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important;} .oj-table-header-row{background-color:#F2F5F7!important;-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important;}.confirm-screen-container .confirm-screen__messageContainer,.confirm-screen-container .confirm-screen-messageContainer,.confirm-screen-container .confirm-screen-messageContainer__message,.confirm-screen-container page-section,.confirm-screen-container obdxcomponent,.confirm-screen-container .page-section,.confirm-screen-container .page-section__container,.confirm-screen-container .page-section-content,.confirm-screen-container .page-section-content__data{display:block!important;height:auto!important;max-height:none!important;overflow:visible!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container obdx-row,.confirm-screen-container .corp-custom-css.row{display:block!important;break-inside:avoid!important;page-break-inside:avoid!important;}.notes,.notes>.oj-flex-item,.notes-content{display:block!important;height:auto!important;overflow:visible!important;break-inside:auto!important;page-break-inside:auto!important;}.notes>.oj-flex-item:first-child,.notes-title{break-after:avoid!important;page-break-after:avoid!important;}.notes>.oj-flex-item:nth-child(2),.notes-content{break-before:avoid!important;page-break-before:avoid!important;}}</style>",

            bannerPrintCss = "<style>@media print{.confirm-screen-container .confirm-screen.successHeader,.confirm-screen-container .confirm-screen__header.successHeader,.confirm-screen-container .corp-confirm-screen.successHeader,.confirm-screen-container .corp-confirm-screen__header.successHeader{background-color:var(--confirm-screen-success-gradient-start-color)!important;background-image:linear-gradient(to var(--confirm-screen-success-gradient-direction),var(--confirm-screen-success-gradient-start-color),var(--confirm-screen-success-gradient-end-color))!important;-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important;}.confirm-screen-container .confirm-screen.errorHeader,.confirm-screen-container .confirm-screen__header.errorHeader,.confirm-screen-container .corp-confirm-screen.errorHeader,.confirm-screen-container .corp-confirm-screen__header.errorHeader{background-color:var(--confirm-screen-error-gradient-start-color)!important;background-image:linear-gradient(to var(--confirm-screen-error-gradient-direction),var(--confirm-screen-error-gradient-start-color),var(--confirm-screen-error-gradient-end-color))!important;-webkit-print-color-adjust:exact!important;print-color-adjust:exact!important;}}</style>",

            confirmScreenExtensionPrintCss = isSafariEnglishLiquidityConfirmTemplate ? "<style>@media print{.confirm-screen-container .confirm-screen-extension-print,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'],.confirm-screen-container .confirm-screen-extension-print page-section,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] page-section,.confirm-screen-container .confirm-screen-extension-print obdxcomponent,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] obdxcomponent,.confirm-screen-container .confirm-screen-extension-print .page-section,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .page-section,.confirm-screen-container .confirm-screen-extension-print .page-section__container,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .page-section__container,.confirm-screen-container .confirm-screen-extension-print .page-section-content,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .page-section-content,.confirm-screen-container .confirm-screen-extension-print .page-section-content__data,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .page-section-content__data,.confirm-screen-container .confirm-screen-extension-print .oj-flex,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-flex,.confirm-screen-container .confirm-screen-extension-print .oj-flex-items-pad,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-flex-items-pad,.confirm-screen-container .confirm-screen-extension-print .oj-flex-item,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-flex-item,.confirm-screen-container .confirm-screen-extension-print .oj-sm-12,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-sm-12,.confirm-screen-container .confirm-screen-extension-print .oj-md-12,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-md-12,.confirm-screen-container .confirm-screen-extension-print .oj-lg-12,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-lg-12,.confirm-screen-container .confirm-screen-splittable-section,.confirm-screen-container .confirm-screen-splittable-section .oj-flex,.confirm-screen-container .confirm-screen-splittable-section .oj-flex-items-pad,.confirm-screen-container .confirm-screen-splittable-section .oj-flex-item,.confirm-screen-container .confirm-screen-splittable-section .notes,.confirm-screen-container .confirm-screen-splittable-section .notes>.oj-flex-item,.confirm-screen-container .confirm-screen-splittable-section .notes-content{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;max-width:100%!important;min-width:0!important;overflow:visible!important;box-sizing:border-box!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container .confirm-screen-extension-print .notes,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes,.confirm-screen-container .confirm-screen-extension-print .notes>.oj-flex-item,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes>.oj-flex-item,.confirm-screen-container .confirm-screen-extension-print .notes-content,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes-content{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container .confirm-screen-extension-print .notes>.oj-flex-item:first-child,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes>.oj-flex-item:first-child,.confirm-screen-container .confirm-screen-extension-print .notes-title,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes-title{break-after:avoid!important;page-break-after:avoid!important;}.confirm-screen-container .confirm-screen-extension-print .notes>.oj-flex-item:nth-child(2),.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes>.oj-flex-item:nth-child(2),.confirm-screen-container .confirm-screen-extension-print .notes-content,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .notes-content{break-before:avoid!important;page-break-before:avoid!important;}.confirm-screen-container .confirm-screen-extension-print .margin-bottom-32,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .margin-bottom-32,.confirm-screen-container .confirm-screen-extension-print .mar-bottom-32,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .mar-bottom-32,.confirm-screen-container .confirm-screen-extension-print .mar-top-32,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .mar-top-32,.confirm-screen-container .confirm-screen-extension-print .leftPad,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .leftPad,.confirm-screen-container .confirm-screen-extension-print .marginCustomFormat,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .marginCustomFormat,.confirm-screen-container .confirm-screen-extension-print .marginCustomFormatFx,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .marginCustomFormatFx{height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container obdx-row.confirm-screen-splittable-row,.confirm-screen-container obdx-row.confirm-screen-splittable-row .corp-custom-css.row{break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container obdx-row.confirm-screen-splittable-row .row__label,.confirm-screen-container obdx-row.confirm-screen-splittable-row .label-container{break-after:avoid!important;page-break-after:avoid!important;}.confirm-screen-container obdx-row.confirm-screen-splittable-row .row__value{break-inside:avoid!important;page-break-inside:avoid!important;}.confirm-screen-container .confirm-screen-extension-print .bt-note,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .bt-note{transform:none!important;position:static!important;break-inside:avoid!important;page-break-inside:avoid!important;}.confirm-screen-container .confirm-screen-extension-print .widthSetterFx,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .widthSetterFx,.confirm-screen-container .confirm-screen-extension-print .noteStyleFx,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .noteStyleFx{width:auto!important;max-width:100%!important;min-width:0!important;overflow:visible!important;}.confirm-screen-container .confirm-screen-extension-print img,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] img{max-width:100%!important;}.confirm-screen-container #confirmScreenPrint [data-bind*='international-disclaimer'],.confirm-screen-container #confirmScreenPrint [data-bind*='domestic-disclaimer'],.confirm-screen-container #confirmScreenPrint [data-bind*='ownAccounts-disclaimer'],.confirm-screen-container #confirmScreenPrint [data-bind*='internal-disclaimer'],.confirm-screen-container #confirmScreenPrint [data-bind*='bea-china-disclaimer'],.confirm-screen-container #confirmScreenPrint [data-bind*='details-notes']{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container .confirm-screen-extension-print oj-table,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] oj-table,.confirm-screen-container .confirm-screen-extension-print .oj-table-container,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-table-container,.confirm-screen-container .confirm-screen-extension-print .oj-table,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-table{display:block!important;width:100%!important;max-width:100%!important;min-width:0!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;box-sizing:border-box!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container .confirm-screen-extension-print table.oj-table-element,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element{display:table!important;width:100%!important;max-width:100%!important;min-width:0!important;overflow:visible!important;box-sizing:border-box!important;table-layout:fixed!important;border-collapse:collapse!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}.confirm-screen-container .confirm-screen-extension-print table.oj-table-element thead,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element thead{display:table-header-group!important;}.confirm-screen-container .confirm-screen-extension-print table.oj-table-element tbody,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element tbody{display:table-row-group!important;}.confirm-screen-container .confirm-screen-extension-print table.oj-table-element tr,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element tr{display:table-row!important;break-inside:avoid!important;page-break-inside:avoid!important;}.confirm-screen-container .confirm-screen-extension-print .oj-table-column-header-cell,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-table-column-header-cell,.confirm-screen-container .confirm-screen-extension-print .oj-table-data-cell,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-table-data-cell,.confirm-screen-container .confirm-screen-extension-print table.oj-table-element th,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element th,.confirm-screen-container .confirm-screen-extension-print table.oj-table-element td,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] table.oj-table-element td{white-space:normal!important;overflow:visible!important;text-overflow:clip!important;word-break:break-word!important;overflow-wrap:anywhere!important;box-sizing:border-box!important;}.confirm-screen-container .confirm-screen-extension-print .oj-table-sort-icon-container,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-table-sort-icon-container,.confirm-screen-container .confirm-screen-extension-print oj-paging-control,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] oj-paging-control,.confirm-screen-container .confirm-screen-extension-print .oj-pagingcontrol,.confirm-screen-container #confirmScreenPrint [data-bind*='confirmScreenExtensions.template'] .oj-pagingcontrol{display:none!important;}}</style>" : "",

            safariConfirmScreenPrintCss = isSafariBrowser ? "<style>@media print{.confirm-screen-container .payments-confirm-template,.confirm-screen-container .payments-confirm-template .oj-flex,.confirm-screen-container .payments-confirm-template .oj-flex-item,.confirm-screen-container .payments-confirm-template .oj-flex-items-pad,.confirm-screen-container .payments-confirm-template .oj-sm-12,.confirm-screen-container .payments-confirm-template .oj-md-12,.confirm-screen-container .payments-confirm-template .oj-lg-12{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}}</style>" : "",

            safariTimeDepositConfirmScreenPrintCss = isSafariBrowser && isTimeDepositConfirmTemplate ? "<style>@media print{" +
                ".confirm-screen-container .confirm-screen-td-print{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-td-print>.oj-flex.oj-sm-padding-2x-horizontal{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;column-gap:16px!important;align-items:start!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-td-print .oj-flex,.confirm-screen-container .confirm-screen-td-print .oj-flex-items-pad,.confirm-screen-container .confirm-screen-td-print .row-group,.confirm-screen-container .confirm-screen-td-print .leftPad,.confirm-screen-container .confirm-screen-td-print .mar-top-32,.confirm-screen-container .confirm-screen-td-print .margin-bottom-32{height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-td-print>.oj-flex.oj-sm-padding-2x-horizontal>.oj-flex-item.oj-md-4{display:block!important;float:none!important;clear:none!important;width:auto!important;max-width:none!important;min-width:0!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;box-sizing:border-box!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-td-print>.oj-flex.oj-sm-padding-2x-horizontal>.oj-flex-item.oj-lg-12{display:block!important;grid-column:1 / -1!important;float:none!important;clear:both!important;width:100%!important;max-width:100%!important;min-width:0!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;box-sizing:border-box!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                "}</style>" : "",

            windowsChromeEdgeConfirmScreenPrintCss = isWindowsChromeOrEdgeBrowser && isCorpBatchConfirmTemplate ? "<style>@media print{" +
                ".confirm-screen-container .confirm-screen-corp-batch-print,.confirm-screen-container .confirm-screen-corp-batch-print .oj-flex,.confirm-screen-container .confirm-screen-corp-batch-print .oj-flex-item,.confirm-screen-container .confirm-screen-corp-batch-print .oj-flex-items-pad,.confirm-screen-container .confirm-screen-corp-batch-print .oj-sm-12,.confirm-screen-container .confirm-screen-corp-batch-print .oj-md-12,.confirm-screen-container .confirm-screen-corp-batch-print .oj-lg-12,.confirm-screen-container .confirm-screen-corp-batch-print .margin-bottom-32,.confirm-screen-container .confirm-screen-corp-batch-print .mar-bottom-32{display:block!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;break-before:auto!important;page-break-before:auto!important;break-after:auto!important;page-break-after:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-corp-batch-print oj-table,.confirm-screen-container .confirm-screen-corp-batch-print .oj-table-container,.confirm-screen-container .confirm-screen-corp-batch-print .oj-table{display:block!important;width:100%!important;max-width:100%!important;min-width:0!important;height:auto!important;min-height:0!important;max-height:none!important;overflow:visible!important;box-sizing:border-box!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-corp-batch-print table.oj-table-element{display:table!important;width:100%!important;max-width:100%!important;min-width:0!important;overflow:visible!important;box-sizing:border-box!important;table-layout:fixed!important;border-collapse:collapse!important;break-before:auto!important;page-break-before:auto!important;break-inside:auto!important;page-break-inside:auto!important;}" +
                ".confirm-screen-container .confirm-screen-corp-batch-print table.oj-table-element thead{display:table-header-group!important;}.confirm-screen-container .confirm-screen-corp-batch-print table.oj-table-element tbody{display:table-row-group!important;}.confirm-screen-container .confirm-screen-corp-batch-print table.oj-table-element tr{display:table-row!important;break-inside:avoid!important;page-break-inside:avoid!important;}" +
                "}</style>" : "",

            safariTablePrintCss = "<style>@media print{oj-table.oj-table-container,.oj-table-container,.oj-table,table.oj-table-element{width:100%!important;max-width:100%!important;min-width:0!important;overflow:visible!important;box-sizing:border-box!important;}table.oj-table-element{table-layout:fixed!important;border-collapse:collapse!important;}table.oj-table-element thead,table.oj-table-element tbody,table.oj-table-element tr{width:auto!important;max-width:none!important;min-width:0!important;height:auto!important;min-height:0!important;}.oj-table-column-header-cell,.oj-table-data-cell,table.oj-table-element th,table.oj-table-element td{width:auto!important;max-width:none!important;min-width:0!important;height:auto!important;min-height:0!important;white-space:normal!important;overflow:visible!important;text-overflow:clip!important;word-break:break-word!important;overflow-wrap:anywhere!important;box-sizing:border-box!important;}.oj-table-sort-icon-container{display:none!important;}}</style>",

            htmlClass = document.documentElement.className || "",
            dirAttr = document.documentElement.getAttribute("dir") || "ltr",
            bodyClass = document.body.className || "",

            adjustScript = "<script>(function(){function qsa(s,r){return [].slice.call((r||document).querySelectorAll(s));}function rect(el){return el.getBoundingClientRect();}function getGroups(){return qsa('.transaction-journey').filter(function(t){return qsa('.transaction-journey-train-box__block--icon-container',t).length>=2;});}function centers(g){var gr=rect(g);var icons=qsa('.transaction-journey-train-box__block--icon-container',g).filter(function(el){return el.offsetParent!==null;});icons.sort(function(a,b){return rect(a).left-rect(b).left;});return icons.map(function(icon){var r=rect(icon);var rad=Math.min(r.width,r.height)/2;return {x:(r.left+r.width/2)-gr.left,y:(r.top+r.height/2)-gr.top,r:rad};});}function median(arr){var a=arr.slice().sort(function(x,y){return x-y;});var m=Math.floor(a.length/2);return a.length%2?a[m]:(a[m-1]+a[m])/2;}function drawSvg(g){if(getComputedStyle(g).position==='static'){g.style.position='relative';}var svg=g.querySelector('.journey-connector-svg');if(svg){svg.parentNode.removeChild(svg);}var pts=centers(g);if(pts.length<2){return;}svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('class','journey-connector-svg');svg.setAttribute('width',g.clientWidth);svg.setAttribute('height',g.clientHeight);g.insertBefore(svg,g.firstChild);var ys=pts.map(function(p){return p.y;});var y=Math.round(median(ys));for(var i=0;i<pts.length-1;i++){var p=pts[i],nx=pts[i+1];var padding=2;var x1=Math.round(p.x+(p.r||0)+padding);var x2=Math.round(nx.x-(nx.r||0)-padding);if(x2>x1){var l=document.createElementNS('http://www.w3.org/2000/svg','line');l.setAttribute('x1',x1);l.setAttribute('y1',y);l.setAttribute('x2',x2);l.setAttribute('y2',y);svg.appendChild(l);}}}function drawAll(){getGroups().forEach(drawSvg);}function schedule(){requestAnimationFrame(function(){drawAll();setTimeout(drawAll,50);setTimeout(drawAll,200);setTimeout(drawAll,500);setTimeout(drawAll,1000);});}if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',schedule);}else{schedule();}window.addEventListener('load',schedule);window.addEventListener('resize',schedule);window.addEventListener('orientationchange',schedule);try{var mo=new MutationObserver(schedule);mo.observe(document.body,{childList:true,subtree:true,attributes:true});}catch(e){}})();</script>",
            fitToA4Script = is3ColumnsMode ? "<script>!function(){const PRINT_MARGIN_MM=10,PRINT_WIDTH_MM=190,PRINT_HEIGHT_MM=277;function getPrintableA4Size(){const page=document.createElement('div');page.style.cssText='position:fixed;left:-99999px;top:0;width:190mm;height:277mm;visibility:hidden;pointer-events:none;contain:layout;',document.body.appendChild(page);const rect=page.getBoundingClientRect(),result={width:rect.width,height:rect.height};return page.remove(),result}function createMeasurePage(printWidth){const source=document.getElementById('maincontent');if(!source)return null;const old=document.getElementById('__measure_page');old&&old.remove();const page=document.createElement('div');page.id='__measure_page',page.style.cssText='position:fixed;left:-99999px;top:0;visibility:hidden;pointer-events:none;contain:layout;overflow:visible;box-sizing:border-box;width:'+printWidth+'px;min-width:'+printWidth+'px;max-width:'+printWidth+'px;';const clone=source.cloneNode(!0);return page.appendChild(clone),document.body.appendChild(page),{page:page,clone:clone}}async function waitLayout(){await new Promise(resolve=>{requestAnimationFrame(resolve)}),await new Promise(resolve=>{requestAnimationFrame(resolve)})}async function calculateScale(){const a4=getPrintableA4Size(),measure=createMeasurePage(a4.width);if(!measure)return 1;await waitLayout();const MIN_REMAINING=10,MAX_REMAINING=20,root=measure.clone;let actualHeight=root.getBoundingClientRect().height;if(actualHeight<=a4.height-10)return measure.page.remove(),console.log('[A4 Print]','Already fits in one page','height='+actualHeight,'pageHeight='+a4.height),1;{let scale=a4.height/actualHeight,upperLimit=1,lowerLimit=scale;for(let i=0;i<15;i++){root.style.zoom=scale,await waitLayout();const actualHeight=root.getBoundingClientRect().height,remaining=a4.height-actualHeight;if(remaining>=10&&remaining<=20)break;{remaining<10?(upperLimit=scale,lowerLimit>=upperLimit&&(lowerLimit=.5*scale)):lowerLimit=scale;const nextScale=.5*(upperLimit+lowerLimit);console.log('[A4 Iteration]','step='+i,'A4 Height='+a4.height,'height='+actualHeight,'scale='+scale,'upperLimit='+upperLimit,'lowerLimit='+lowerLimit,'next='+nextScale),scale=nextScale}}return measure.page.remove(),console.log('[A4 Final Scale]',scale),scale}}function applyPrintScale(scale){let style=document.getElementById('__a4_print_scale');style||(style=document.createElement('style'),style.id='__a4_print_scale',document.head.appendChild(style)),style.textContent=`@media print{#maincontent{zoom:${scale}}`}async function fitToOnePage(){try{const scale=await calculateScale();applyPrintScale(scale)}catch(e){console.error('[A4 Print Error]',e)}}function initialize(){setTimeout(fitToOnePage,1e3)}'complete'===document.readyState?initialize():window.addEventListener('load',initialize)}();</script>" : "",
            locale = ResourceBundle._ojLocale_ || "en";

            let langCode = "en";

            if (locale === "zh-Hant") {
                langCode = "zh-Hant";
            } else if (locale === "zh-Hans-CN") {
                langCode = "zh-Hans-CN";
            } else if (locale === "en" ) {
                langCode = "en";
            }

            const html = "<!doctype html><html lang='" + langCode + "' class='" + htmlClass + "' dir='" + dirAttr + "'>" +
            "<head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=1'><meta name='format-detection' content='telephone=no, email=no, address=no'><base href='" + document.baseURI + "'>" +
            headCss + whiteBgCss + bannerPrintCss + confirmScreenExtensionPrintCss + safariConfirmScreenPrintCss + safariTimeDepositConfirmScreenPrintCss + windowsChromeEdgeConfirmScreenPrintCss + safariTablePrintCss +
            "</head><body class='" + bodyClass + "'>" +
            contentHtml +
            adjustScript +
            fitToA4Script +
            "</body></html>",

            win = window.open();

            if (win && win.document) {
                win.document.open();
                win.document.write(html);
                win.document.close();
            }

            const blob = new Blob([html], { type: "text/html" }),
                url = URL.createObjectURL(blob),
                a = document.createElement("a");

            a.href = url;
            a.download = self.componentNameFromNLS() + ".html";
            a.click();

        };

        self.makeAnotherTransaction = function() {
            if (self.params.makeAnotherParams.componentName === "flow") {
                self.params.makeAnotherParams.params.flowStageRootModel = self.params.makeAnotherParams.params.flowStageRootModel || {};
                self.params.makeAnotherParams.params.flowStageRootModel.isMakeAnotherTransaction = true;

                rootParams.dashboard.loadComponent(self.params.makeAnotherParams.componentName, Object.assign(self.params.makeAnotherParams.params, {
                    flowStartIndex: 0
                }));
            }

            rootParams.dashboard.loadComponent(self.params.makeAnotherParams.componentName, self.params.makeAnotherParams.params);
        };

        const type2LabelArrayMap = {
            commonMessage: [
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.transferType,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.TransferType,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.TransactionMethod,
                self.confirmScreenResources.nonDesignatedNLS.StartNote,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TemplateName,
                self.confirmScreenResources.preDesignatedNLS.AccountType,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferType,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransactionMethod,
                self.confirmScreenResources.preDesignatedNLS.StartNote
            ],
            beneficiaryDetails: [
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountNumber,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountName,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.transferToCurrency,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.accountNumber,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountNameChinese,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountNameEnglish,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.Type,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountNumberorProxyID,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.MobileNumber,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BankName,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryName,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.EmailAddress,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.FPSID,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.payeeEmail,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.accNoIBan,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.beneficiaryName,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.SWIFTBICandAddress,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.type,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountAddress,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.BeneficiaryAccountNameandAddress,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.AccountNumber,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferToCurrency,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.accountnamee,
                self.confirmScreenResources.preDesignatedNLS.accountNameChinese,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.AccountName,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.Type,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.OtherPayeeIdentification,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.MobileNo,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.FPSID,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.EmailAddress,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.BankName,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.BeneficiaryName,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferTo,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.IntAccountNumberIBANNo,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.beneficiaryName,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.IntName,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.IntAddress,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.BankDetails,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.type
            ],
            fromAccount: [
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.AccountName,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.TransferFrom,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.transferFromCurrency,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.transferFromChina,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.TransferFromCurrency,
                self.confirmScreenResources.nonDesignatedNLS.adhocPayments.transferFrom,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferFrom,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferFromCurrency,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.AccountName2,
                self.confirmScreenResources.preDesignatedNLS.AccountName1,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.TransferFromAs
            ],
            charges: [
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.ServiceCharge,
                self.confirmScreenResources.nonDesignatedNLS.AdhocPaymentsStage.ChargeOption,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.serviceCharge,
                self.confirmScreenResources.preDesignatedNLS.TransferMoney.ChargeOption
            ]
        }, nonTransactionInformationLabelArray = [...type2LabelArrayMap.commonMessage, ...type2LabelArrayMap.beneficiaryDetails, ...type2LabelArrayMap.fromAccount, ...type2LabelArrayMap.charges];

        self.getConfirmScreenDetailsByType = function(data, type) {
            if (type === "transactionInformation") {
                return data.filter((item) => !nonTransactionInformationLabelArray.includes(item.label));
            }

            return data.filter((item) => type2LabelArrayMap[type].includes(item.label));
        };
    };
});
