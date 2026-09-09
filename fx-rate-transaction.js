define([
    "knockout",
    "jquery",
    "ojs/ojcore",
    "./model",
    "ojL10n!extensions/resources/nls/fx-rate-transaction",
    "extensions/generic/service-extension",
    "framework/elements/api/account-input/loader",
    "framework/elements/api/modal-window/loader",
    "framework/elements/api/amount-input/loader",
    "framework/elements/api/row/loader",
    "ojs/ojbutton",
    "ojs/ojmenu",
    "ojs/ojinputtext",
    "ojs/ojtable",
    "ojs/ojcheckboxset",
    "ojs/ojinputnumber",
    "ojs/ojvalidation",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingtabledatasource",
    "ojs/ojvalidationgroup",
    "framework/elements/api/marketing-banner/loader"
    ], function (ko, $, oj, RateModel, resourceBundle, _serviceExtension) {
    "use strict";

    return function (rootParams) {
        const self = this;

        self.nls = resourceBundle;
        rootParams.dashboard.headerName(self.nls.header);
        rootParams.baseModel.registerComponent("fx-treasury-reference", "fx-rate");

        if (rootParams.dashboard && rootParams.dashboard.marketingBannerParams) {
            const marketingBannerParams = rootParams.dashboard.marketingBannerParams;

            self.marketBannerParams = ko.observable({
                baseModel: marketingBannerParams.baseModel,
                dashboard: rootParams.dashboard,
                filterMenu: marketingBannerParams.filterMenu,
                menuOptionSelect: marketingBannerParams.menuOptionSelect,
                computeContext: marketingBannerParams.computeContext,
                dashboardName: marketingBannerParams.dashboardName
            });
        }

        let payload, macpayload;

        self.treasuryList = [];
        self.preFilledBoolean = ko.observable(false);
        self.dataSource = new oj.ArrayTableDataSource([], { idAttribute: "treasuryRef" });
        self.partyDatasource = new oj.ArrayTableDataSource([]);
        self.paginationDataSource = new oj.PagingTableDataSource(self.dataSource);
        self.accountNumberFrom = ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails) ? ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails).account.id.value : ko.observable();
        self.additionalDetails = ko.observable();
        self.accountCurrencyFrom = ko.observable();
        self.accountNumberTo = ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails) ? ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails).account.id.value : ko.observable();
        self.additionalDetailsInfo = ko.observable();
        self.accountCurrencyTo = ko.observable();
        self.returnCode = rootParams.rootModel.params.returnCode || ko.observable();
        self.cinNumber = ko.observable();
        self.exchangeRate = ko.observable();
        self.prefExchangeRate = ko.observable();
        self.companyModal = ko.observable();
        self.subExchange = ko.observable();
        self.prefSubExchange = ko.observable();
        self.currency = ko.observable();
        self.amount = ko.observable();
        self.currency2 = ko.observable();
        self.amount2 = ko.observable();
        //Added for FX-COUPON
        self.CouponCode = ko.observable();
        self.ShowCouponRate = ko.observable(false);
        self.CPList = [];
        self.CPdataSource = new oj.ArrayTableDataSource([], { idAttribute: "CouponCodeName" });
        self.SpreadDiscount = ko.observable();
        self.CpExchangeRate = ko.observable();
        self.CpTableVisible = ko.observable(false);
        self.FromAcctdata = ko.observable([]);
        self.ToAcctdata = ko.observable([]);
        //Ended
        self.treasuryRefNo = ko.observable();
        self.companyList = ko.observableArray();
        self.maxAmount = ko.observable(99999999999.99);
        self.isDiffCurrency = ko.observable(false);
        self.loadedResults = ko.observable(false);
        self.tableVisible = ko.observable(false);
        self.typesLoaded = ko.observable(true);
        self.accountLoaded = ko.observable(true);
        self.displayModal = ko.observable(false);
        self.isFxAgreedRate = ko.observable(false);
        self.backFlag = ko.observable(false);
        self.sendToModifyFlag = ko.observable(false);
        self.isSpecialAccount = ko.observable(false);
        self.account_filter = ko.observable();
        self.txnId = ko.observable();
        self.isOMB = ko.observable(false);
        self.treasuryRefEnable = ko.observable(true);
        self.mode = rootParams.rootModel.params.mode || ko.observable();
        self.invalidCurrencyPairFlag = ko.observable(false);
        self.oneInstanceFlag = ko.observable(false);
        self.twoInstanceFlag = ko.observable(false);
        self.threeInstanceFlag = ko.observable(false);
        self.reviewFlag = rootParams.rootModel.params.reviewFlag || ko.observable(false);

        self.cssSetter = function (){
            setTimeout(function() {
                for(let i = 0; i < document.getElementsByClassName("oj-flex-item").length; i++){
                    if(document.getElementsByClassName("oj-flex-item")[i].innerText.startsWith("Transfer From Currency") || document.getElementsByClassName("oj-flex-item")[i].innerText.startsWith("Transfer To Currency")){
                        document.getElementsByClassName("oj-flex-item")[i].classList.add("oj-sm-8");
                    }
                }

                for(let m = 0; m < document.getElementsByClassName("oj-select oj-component oj-enabled oj-select-jet oj-form-control oj-required").length; m++){
                    if(document.getElementsByClassName("oj-select oj-component oj-enabled oj-select-jet oj-form-control oj-required")[m].id === "ojChoiceId_bill-amount_currency"){
                        document.getElementsByClassName("oj-select oj-component oj-enabled oj-select-jet oj-form-control oj-required")[m].classList.add("oj-sm-6");
                    }
                }

            }, 100);
        };

        if(self.reviewFlag === true){
            self.typesLoaded(false);
            self.accountNumberFrom = ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails) ? ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetails).account.id.value : ko.observable();
            self.additionalDetails = rootParams.rootModel.params.additionalDetails;
            self.accountCurrencyFrom = rootParams.rootModel.params.accountCurrencyFrom;
            self.accountNumberTo = ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetailsInfo) ? ko.utils.unwrapObservable(rootParams.rootModel.params.additionalDetailsInfo).account.id.value : ko.observable();
            self.additionalDetailsInfo = rootParams.rootModel.params.additionalDetailsInfo;
            self.accountCurrencyTo = rootParams.rootModel.params.accountCurrencyTo;
            self.cinNumber = rootParams.rootModel.params.cin;
            self.amount2 = rootParams.rootModel.params.amount2;
            self.currency2 = rootParams.rootModel.params.currency2;
            self.isFxAgreedRate(rootParams.rootModel.params.isFxAgreedRate());
            self.exchangeRate = rootParams.rootModel.params.exchangeRate;
            self.prefExchangeRate = rootParams.rootModel.params.prefExchangeRate;
            self.treasuryRefNo = rootParams.rootModel.params.treasuryRefNo;
            self.subExchange = rootParams.rootModel.params.subExchange;
            self.prefSubExchange = rootParams.rootModel.params.prefSubExchange;
            self.account_filter(rootParams.rootModel.params.account_filter);
            self.isDiffCurrency = ko.observable(true);

            self.sendToModifyFlag = rootParams.rootModel.params.sendToModifyFlag;

            if(self.isFxAgreedRate() === true){
                self.preFilledBoolean = rootParams.rootModel.params.prefilled;
                self.currency = rootParams.rootModel.params.currency;
                self.amount = rootParams.rootModel.params.amount;

                self.fxParser = function (data) {
                    if(self.account_filter() === undefined){
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency)) : [];
                    }
                    else{
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency) && e.partyId.value === self.account_filter()) : [];
                    }

                    if(data.length > 0){
                        self.accountNumberTo = data[0].id.value;
                        self.accountCurrencyTo = rootParams.rootModel.params.accountCurrencyTo;
                    }
                    else{
                        data = [];
                    }

                    return data;
                };

                self.fxParser2 = function (data) {
                    if(self.account_filter() === undefined){
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency2)) : [];
                    }
                    else{
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency2) && e.partyId.value === self.account_filter()) : [];
                    }

                    if(data.length > 0){
                        self.accountNumberFrom = data[0].id.value;
                        self.accountCurrencyFrom = rootParams.rootModel.params.accountCurrencyFrom;
                    }
                    else{
                        data = [];
                    }

                    return data;
                };

                self.currencyParserFx = function (data) {
                    data = data && self.currency ? data.filter(e => e.value === self.currency) : data;

                    return data;
                };

                self.currencyParserFx2 = function (data) {
                    data = data && self.currency2 ? data.filter(e => e.value === self.currency2) : data;

                    return data;
                };
            }

            if(self.isFxAgreedRate() !== true && self.accountCurrencyFrom() === self.accountCurrencyTo()){
                self.isDiffCurrency = ko.observable(false);
            }

            if(self.isFxAgreedRate() !== true){
                if(self.sendToModifyFlag() === true){
                    self.treasuryRefEnable(false);
                }
            }

            self.typesLoaded(true);

            self.cssSetter();
        }

        if(rootParams.rootModel.params && rootParams.rootModel.params.preFilledBoolean && rootParams.rootModel.params.preFilledBoolean === true){
            self.isDiffCurrency(true);
        }

        //Added by XiaoPK on 260316 start
        function rewriteCNYCode (ccy){
            if(ccy === "CNY"){
                return "RMB";
            }

            return ccy;
        }
        //Ended

        self.currencyParser1 = function () {
            const output = {};

            output.currencies = [];

            output.currencies.push(
                {
                    code: "HKD",
                    description: "HKD"
                },
                {
                    code: "USD",
                    description: "USD"
                },
                {
                    code: "EUR",
                    description: "EUR"
                },
                {
                    code: "AUD",
                    description: "AUD"
                },
                {
                    code: "CNY",
                    description: "CNY"
                },
                {
                    code: "JPY",
                    description: "JPY"
                },
                {
                    code: "CAD",
                    description: "CAD"
                },
                {
                    code: "CHF",
                    description: "CHF"
                },
                {
                    code: "GBP",
                    description: "GBP"
                },
                {
                    code: "NZD",
                    description: "NZD"
                },
                {
                    code: "SGD",
                    description: "SGD"
                }
            );

            return output;
        };

        if(self.isFxAgreedRate() === false){
            self.amount2.subscribe(function (newValue) {
                if (newValue) {
                    if(self.currency2() && self.amount2() && self.accountCurrencyFrom() && self.accountCurrencyTo() && self.accountCurrencyFrom() !== self.accountCurrencyTo()){
                        self.isDiffCurrency(false);
                        self.isSpecialAccount(false);
                        self.exchangeRate = "";
                        self.prefExchangeRate = "";
                        self.invalidCurrencyPairFlag(false);

                        //Amended by XiaoPK for FX-CNY on 260316 start
                        const toCurrency = rewriteCNYCode(self.accountCurrencyTo()),fromcurrency = rewriteCNYCode(self.accountCurrencyFrom()),currency2 = rewriteCNYCode(self.currency2());

                        RateModel.exchangeRate(self.amount2() + "~" + currency2, fromcurrency, toCurrency).done(function (response) {
                        //Ended
                            if (response && response.preferentialRatesCalcDto) {
                                if((self.accountCurrencyFrom() === "HKD" && self.accountCurrencyTo() !== "HKD") || (self.accountCurrencyTo() === "HKD" && self.accountCurrencyFrom() !== "HKD")){
                                    self.exchangeRate = rootParams.baseModel.format(self.nls.exchangePanel, {
                                        option: self.accountCurrencyFrom() === "HKD" ? self.nls.sell : self.nls.buy,
                                        currency1: self.accountCurrencyTo() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                        currency2: self.accountCurrencyFrom() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                        rate: response.preferentialRatesCalcDto.toAmount.amount.toFixed(8)
                                    });

                                    if(response.preferentialRatesCalcDto.specialRateIndicator === true){
                                        self.prefExchangeRate = rootParams.baseModel.format(self.nls.prefExchangePanel, {
                                            option: self.accountCurrencyFrom() === "HKD" ? self.nls.sell : self.nls.buy,
                                            currency1: self.accountCurrencyTo() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                            currency2: self.accountCurrencyFrom() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                            rate: response.preferentialRatesCalcDto.toSpecialAmount.amount.toFixed(8)
                                        });

                                        self.isSpecialAccount(true);
                                    }

                                    //Added for FX-COUPON Start
                                    if(self.ShowCouponRate){
                                        let CpRate = response.preferentialRatesCalcDto.toAmount.amount.toFixed(8);

                                        if(response.preferentialRatesCalcDto.toAmount.amount !== 0){
                                            if(self.accountCurrencyFrom() !== response.preferentialRatesCalcDto.currencyLabel1){
                                                CpRate = (Number(response.preferentialRatesCalcDto.toAmount.amount) + Number(self.SpreadDiscount().split(/ /)[0])).toFixed(8);
                                            }else{
                                                CpRate = (Number(response.preferentialRatesCalcDto.toAmount.amount) - Number(self.SpreadDiscount().split(/ /)[0])).toFixed(8);
                                            }
                                        }

                                        self.CpExchangeRate = rootParams.baseModel.format(self.nls.exchangePanel, {
                                            option: self.accountCurrencyFrom() === "HKD" ? self.nls.sell : self.nls.buy,
                                            currency1: self.accountCurrencyTo() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                            currency2: self.accountCurrencyFrom() === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom()] : self.nls.toCurrencyList1[self.accountCurrencyTo()],
                                            rate: CpRate
                                        });
                                    }
                                    //Ended
                                }
                                else if(self.accountCurrencyFrom() !== "HKD" && self.accountCurrencyTo() !== "HKD"){
                                    self.exchangeRate = rootParams.baseModel.format(self.nls.exchangePanelCurrency, {
                                        from: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel1],
                                        to: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel2],
                                        rate: response.preferentialRatesCalcDto.toAmount.amount.toFixed(8)
                                    });

                                    if(response.preferentialRatesCalcDto.specialRateIndicator === true){
                                        self.prefExchangeRate = rootParams.baseModel.format(self.nls.prefExchangePanelCurrency, {
                                            from: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel1],
                                            to: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel2],
                                            rate: response.preferentialRatesCalcDto.toSpecialAmount.amount.toFixed(8)
                                        });

                                        self.isSpecialAccount(true);
                                    }

                                    //Added for FX-COUPON Start
                                    if(self.ShowCouponRate){
                                        let CpRate = response.preferentialRatesCalcDto.toAmount.amount.toFixed(8);

                                        if(response.preferentialRatesCalcDto.toAmount.amount !== 0){
                                            if(self.accountCurrencyFrom() !== response.preferentialRatesCalcDto.currencyLabel1){
                                                CpRate = (Number(response.preferentialRatesCalcDto.toAmount.amount) - Number(self.SpreadDiscount().split(/ /)[0])).toFixed(8);
                                            }else{
                                                CpRate = (Number(response.preferentialRatesCalcDto.toAmount.amount) + Number(self.SpreadDiscount().split(/ /)[0])).toFixed(8);
                                            }
                                        }

                                        self.CpExchangeRate = rootParams.baseModel.format(self.nls.exchangePanelCurrency, {
                                            from: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel1],
                                            to: self.nls.toCurrencyList1[response.preferentialRatesCalcDto.currencyLabel2],
                                            rate: CpRate
                                        });
                                    }
                                    //Ended

                                }
                            }

                            self.isDiffCurrency(true);
                        }).catch(function (errorData){
                            if(errorData.responseJSON && errorData.responseJSON.message.code && errorData.responseJSON.message.code === "DIGX_PROD_DEF_0000"){
                                self.invalidCurrencyPairFlag(true);
                            }
                        });
                    }
                    else if(self.accountCurrencyFrom() === self.accountCurrencyTo()){
                        self.isDiffCurrency(false);
                        self.exchangeRate = undefined;
                        self.prefExchangeRate = undefined;
                    }
                }
            });
        }

        if(self.isFxAgreedRate() === false){
            self.currency2.subscribe(function (newValue) {
                if (newValue) {
                    if(self.twoInstanceFlag() === false){

                        self.amount2(undefined);
                    }
                    else{
                        self.twoInstanceFlag(false);
                    }

                }
            });

            self.accountCurrencyFrom.subscribe(function (newVal) {
                if (newVal) {
                    if(self.oneInstanceFlag() !== false){
                        self.oneInstanceFlag(false);
                    }

                    //Added for FX-COUPON
                    if (self.CouponCode() && (newVal !== self.CPdataSource.data.find(item => item.CouponCodeName === self.CouponCode()).FromCcy ||
                        self.FromAcctdata.find(item => item.id.value === self.accountNumberFrom()).partyId.value !== self.CPdataSource.data.find(item => item.CouponCodeName === self.CouponCode()).FromAcctNo)) {
                            self.CleanCoupon();
                    }
                    //Ended
                }
            });

            self.accountCurrencyTo.subscribe(function (newVal) {
                if (newVal) {
                    if(self.threeInstanceFlag() !== false){
                        self.threeInstanceFlag(false);
                    }

                    if (!self.sendToModifyFlag() && !self.data) {
                        self.currency2(newVal);
                    }

                    //Added for FX-COUPON
                    if (self.CouponCode() && newVal !== self.CPdataSource.data.find(item => item.CouponCodeName === self.CouponCode()).ToCcy) {
                        self.CleanCoupon();
                    }
                    //Ended
                }
            });
        }

        self.showTreasuryList = function () {
            self.cinNumber = self.companyModal().cin;

            if(self.backFlag() === false){

                RateModel.treasuryDisplay(self.cinNumber).done(function (response) {

                    self.treasuryList.length = 0;

                    for(let i = 0; i < response.fxAgreePendingListResponseDTO.cdcTrefDetails.length; i++){
                        self.treasuryList.push(response.fxAgreePendingListResponseDTO.cdcTrefDetails[i]);
                    }

                    const processedData = self.treasuryList.map(function (item) {
                        return {
                            treasuryRef: item.treasuryRef,
                            companyName: item.customerName,
                            bankBuy: self.nls.toCurrencyList1[item.buyCcy],
                            bankSell: self.nls.toCurrencyList1[item.sellCcy],
                            agreedFxRate: item.exchangeRate
                        };
                    });

                    self.dataSource.reset(processedData, {
                        idAttribute: "treasuryRef"
                    });

                    self.dataSource.data = processedData;

                    self.tableVisible(true);
                });
            }
            else{
                self.backFlag(false);
            }
        };

        self.goToMap = function (selectedTreasury) {
            RateModel.treasuryDetails(self.companyModal().cin, selectedTreasury.treasuryRef).then(function (response) {
                self.isFxAgreedRate(true);
                self.prefilledWebMail(response.fxAgreeResponseDTO);
            }).catch( function (){
                self.isFxAgreedRate(false);
                self.typesLoaded(true);
            });

            $("#treasuryReferenceModal").trigger("closeModal");
            self.typesLoaded(false);

        };

        //Added for FX-COUPON
        self.CPgoToMap = function (selectedCoupon) {
            $("#SelectCouponCodeModal").trigger("closeModal");
            self.typesLoaded(false);

            setTimeout(function() {
                self.PreFilledCoupon(selectedCoupon);
            }, 0);
        };

        self.fxParser = function (data) {
            self.ToAcctdata = data;

            return data;
        };

        self.fxParser2 = function (data) {
            self.FromAcctdata = data;

            return data;
        };

        self.formatCouponDate = function(date) {
            if (!date || date.length !== 14) {
                return date;
            }

            const year = date.substring(0, 4),
                month = date.substring(4, 6),
                day = date.substring(6, 8),
                locale = rootParams.baseModel.getLocale ? rootParams.baseModel.getLocale() : "en",
                enMonths = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

            let formattedDate = date;

            if (locale === "zh-Hans-CN" || locale === "zh-Hant") {
                formattedDate = `${year}${self.nls.year}${month}${self.nls.month}${day}${self.nls.day}`;
            } else if (locale === "en") {
                formattedDate = `${day} ${enMonths[parseInt(month, 10) - 1]} ${year}`;
            }

            return formattedDate;
        };

        self.CleanCoupon = function (){
            self.ShowCouponRate(false);
            self.CouponCode("");
        };

        self.PreFilledCoupon = function (selectedCoupon) {
            self.accountLoaded(false);

            ko.tasks.schedule(function () {
                self.data = selectedCoupon;
                self.CouponCode(self.data.CouponCodeName);

                if (ko.isObservable(self.accountNumberTo)) {
                    self.accountNumberTo(self.ToAcctdata[0].id.value);
                } else {
                    self.accountNumberTo = self.ToAcctdata[0].id.value;
                }

                self.accountCurrencyTo(self.data.ToCcy);
                self.SpreadDiscount(self.data.SpDiscount);
                self.ShowCouponRate(true);

                const matchedAccount = (Array.isArray(ko.unwrap(self.FromAcctdata)) ? ko.unwrap(self.FromAcctdata) : []).find(e => e.partyId && e.partyId.value === self.data.FromAcctNo);

                if (matchedAccount) {
                    const firstFromId = matchedAccount.id.value;

                    if(self.FromAcctdata.length > 0){
                        if (ko.isObservable(self.accountNumberFrom)) {
                            self.accountNumberFrom(firstFromId);
                        } else {
                            self.accountNumberFrom = firstFromId;
                        }

                        self.accountCurrencyFrom(self.data.FromCcy);
                    }
                }

                self.accountLoaded(true);
                self.typesLoaded(true);
            });
        };
        //Ended

        self.searchTreasuryRef = function () {

            self.displayModal(true);
            self.backFlag(false);

            $("#treasuryReferenceModal").trigger("openModal");

            RateModel.companySearch().done(function (response) {
                self.companyList().length = 0;

                for(let i = 0; i < response.fxAgreeResponseDTO.length; i++){
                    const values = {
                        company: response.fxAgreeResponseDTO[i].customerName,
                        cin: response.fxAgreeResponseDTO[i].cin,
                        isFav: response.fxAgreeResponseDTO[i].is_favorites
                    };

                    if(response.fxAgreeResponseDTO[i].is_favorites === "Y"){
                        self.companyModal(values);

                        self.showTreasuryList();
                    }

                    self.companyList.push(values);
                }

                self.loadedResults(true);

            });

            for (let a = 0; a < document.getElementsByClassName("icons icon-cross").length; a++){
                if(document.getElementsByClassName("icons icon-cross")[a].checkVisibility() === true){
                    document.getElementsByClassName("icons icon-cross")[a].classList.add("removed");
                    $(".removed").hide();
                }
            }

        };

        //Added for FX-COUPON Start
        self.searchCouponCode = function(){
            $("#SelectCouponCodeModal").trigger("openModal");

            RateModel.couponCodeSearch(ko.utils.unwrapObservable(self.accountNumberFrom) ? ko.utils.unwrapObservable(self.accountNumberFrom).split("~")[0] : null).done(function (response) {
                self.CPList.length = 0;

                if (response && response.CPCodeResponseDTO) {
                        const AcctNumFrom = ko.utils.unwrapObservable(self.accountNumberFrom),
                        CcyTo = ko.utils.unwrapObservable(self.accountCurrencyTo),
                        CcyFrom = ko.utils.unwrapObservable(self.accountCurrencyFrom),
                        matchedAcct = self.FromAcctdata.find(item => item.id.value === AcctNumFrom) || {},
                        AcctFrom = matchedAcct.partyId ? matchedAcct.partyId.value : null,
                        UserPrefillFlag = Boolean(AcctFrom || CcyTo || CcyFrom);

                    if(UserPrefillFlag){
                        response.CPCodeResponseDTO = response.CPCodeResponseDTO.filter(item => {
                            const matchAcct = !AcctFrom || item.fromAcctNo === AcctFrom,
                             matchFromCcy = !CcyFrom || item.fromCcy === CcyFrom,
                             matchToCcy = !CcyTo || item.toCcy === CcyTo;

                            return matchAcct && matchFromCcy && matchToCcy;
                        });
                    }

                    response.CPCodeResponseDTO.sort(function (a, b) {
                        const dateA = a.expDate || "",
                            dateB = b.expDate || "";

                        return dateA.localeCompare(dateB);
                    });

                    for(let i = 0; i < response.CPCodeResponseDTO.length; i++){
                        const values = {
                            CouponCodeName: response.CPCodeResponseDTO[i].couponCodeName,
                            FromAcctNo: response.CPCodeResponseDTO[i].fromAcctNo,
                            FromCcy: response.CPCodeResponseDTO[i].fromCcy,
                            ToCcy: response.CPCodeResponseDTO[i].toCcy,
                            MinAmt: response.CPCodeResponseDTO[i].minAmt,
                            MaxAmt: response.CPCodeResponseDTO[i].maxAmt,
                            SpDiscount: response.CPCodeResponseDTO[i].spDiscount,
                            ExpDate: self.formatCouponDate(response.CPCodeResponseDTO[i].expDate),
                            RemainCP: response.CPCodeResponseDTO[i].remainCP
                        };

                        self.CPList.push(values);
                    }
                }

                const processedData = self.CPList.map(function (item) {
                    return {
                        CouponCodeName: item.CouponCodeName,
                        FromAcctNo: item.FromAcctNo,
                        FromCcy: item.FromCcy,
                        ToCcy: item.ToCcy,
                        MinAmt: `${item.FromCcy} ${item.MinAmt}\n${self.nls.AmtEquivalent}`,
                        MaxAmt: `${item.FromCcy} ${item.MaxAmt}\n${self.nls.AmtEquivalent}`,
                        SpDiscount: `${item.SpDiscount} ${self.nls.pips}`,
                        ExpDate: item.ExpDate,
                        RemainCP: item.RemainCP
                    };
                });

                self.CPdataSource.reset(processedData, {
                    idAttribute: "CouponCodeName"
                });

                self.CPdataSource.data = processedData;
                self.loadedResults(true);
                self.CpTableVisible(true);

            });

        };
        //Ended

        self.setFavorite = function () {
            RateModel.editFavorites(self.companyModal().cin);
        };

        self.exchangeRateFormat = function (){

            if((self.accountCurrencyFrom === "HKD" && self.accountCurrencyTo !== "HKD") || (self.accountCurrencyTo === "HKD" && self.accountCurrencyFrom !== "HKD")){
                self.exchangeRate = rootParams.baseModel.format(self.nls.exchangePanel, {
                    option: self.accountCurrencyFrom === "HKD" ? self.nls.sell : self.nls.buy,
                    currency1: self.accountCurrencyTo === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom] : self.nls.toCurrencyList1[self.accountCurrencyTo],
                    currency2: self.accountCurrencyFrom === "HKD" ? self.nls.toCurrencyList1[self.accountCurrencyFrom] : self.nls.toCurrencyList1[self.accountCurrencyTo],
                    rate: Number(self.subExchange).toFixed(8)
                });
            }
            else if(self.accountCurrencyFrom !== "HKD" && self.accountCurrencyTo !== "HKD"){
                self.exchangeRate = Number(self.subExchange).toFixed(8);
            }
            else if(self.accountCurrencyFrom === "HKD" && self.accountCurrencyTo === "HKD"){
                self.exchangeRate = rootParams.baseModel.format(self.nls.exchangePanelCurrency, {
                    from: self.nls.toCurrencyList1[self.accountCurrencyFrom],
                    to: self.nls.toCurrencyList1[self.accountCurrencyTo],
                    rate: Number(self.subExchange).toFixed(8)
                });
            }
        };

        self.sendToModify = function(prefilledItems) {
            self.typesLoaded(false);
            self.data = prefilledItems;

            if(self.data.cin !== ""){
                self.isFxAgreedRate(true);
                self.accountNumberFrom = self.data.debitAccountId;
                self.accountNumberTo = self.data.creditAccountId;
                self.currency = self.data.creditCurrency;
                self.amount = Number(self.data.creditAmount);
                self.currency2 = self.data.debitCurrency;
                self.amount2 = Number(self.data.debitAmount);
                self.accountCurrencyFrom = self.data.debitCurrency;
                self.accountCurrencyTo = self.data.creditCurrency;
                self.cinNumber = self.data.cin;
                self.treasuryRefNo = self.data.treasuryReference;
                self.subExchange = self.data.exchangeRate;
                self.preFilledBoolean(true);
                self.exchangeRateFormat();
                self.isDiffCurrency(true);

                self.fxParser = function (data) {
                    if(self.data.acc_filter === undefined){
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency)) : [];
                    }
                    else{
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency) && e.partyId.value === self.data.acc_filter) : [];
                    }

                    if(data.length > 0){
                        self.accountNumberTo = data[0].id.value;
                        self.accountCurrencyTo = self.data.creditCurrency;
                    }
                    else{
                        data = [];
                    }

                    return data;
                };

                self.fxParser2 = function (data) {
                    if(self.data.acc_filter === undefined){
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency2)) : [];
                    }
                    else{
                        data = data ? data.filter(e => e.currencyCode.includes(self.currency2) && e.partyId.value === self.data.acc_filter) : [];
                    }

                    if(data.length > 0){
                        self.accountNumberFrom = data[0].id.value;
                        self.accountCurrencyFrom = self.data.debitCurrency;
                    }
                    else{
                        data = [];
                    }

                    return data;
                };

                self.currencyParserFx = function (data) {
                    data = data && self.currency ? data.filter(e => e.value === self.currency) : data;

                    return data;
                };

                self.currencyParserFx2 = function (data) {
                    data = data && self.currency2 ? data.filter(e => e.value === self.currency2) : data;

                    return data;
                };
            }

            if(self.data.cin === ""){
                self.accountNumberFrom = self.data.debitAccountId;
                self.accountNumberTo = self.data.creditAccountId;
                self.currency = self.data.creditCurrency;
                self.amount = Number(self.data.creditAmount);
                self.currency2(self.data.debitCurrency);
                self.amount2(Number(self.data.debitAmount));
                self.accountCurrencyFrom(self.data.drAccCurr);
                self.accountCurrencyTo(self.data.crAccCurr);
                self.cinNumber = self.data.cin;
                self.treasuryRefNo = self.data.treasuryReference;
                self.subExchange = self.data.exchangeRate;
                self.treasuryRefEnable(false);
            }

            self.typesLoaded(true);

            self.cssSetter();

        };

        if (rootParams.rootModel.params.data && rootParams.rootModel.params.approvalDetails && (rootParams.rootModel.params.approvalDetails.status === "APPROVED" || rootParams.rootModel.params.approvalDetails.status === "REJECTED" || rootParams.rootModel.params.approvalDetails.status === "EXPIRED")) {
            self.oneInstanceFlag(true);
            self.twoInstanceFlag(true);
            self.threeInstanceFlag(true);
            self.sendToModify(rootParams.rootModel.params.data);
        }

        //Web Mail Autofill
        self.prefilledWebMail = function (prefillItems) {

            self.isFxAgreedRate(true);

            self.data = prefillItems;
            self.currency = self.data.creditCurrency;
            self.amount = Number(self.data.creditAmount);
            self.currency2 = self.data.debitCurrency;
            self.amount2 = Number(self.data.debitAmount);
            self.accountCurrencyFrom = self.data.debitCurrency;
            self.accountCurrencyTo = self.data.creditCurrency;
            self.cinNumber = self.data.cin;
            self.treasuryRefNo = self.data.treasuryReference;
            self.subExchange = self.data.exchangeRate;
            self.account_filter = self.data.acc_filter;

            self.exchangeRateFormat();

            self.fxParser = function (data) {

                if(self.data.acc_filter === undefined){
                    data = data ? data.filter(e => e.currencyCode.includes(self.currency)) : [];
                }
                else{
                    data = data ? data.filter(e => e.currencyCode.includes(self.currency) && e.partyId.value === self.data.acc_filter) : [];
                }

                if(data.length > 0){
                    self.accountNumberTo(data[0].id.value);
                    self.accountCurrencyTo = self.data.creditCurrency;
                }
                else{
                    data = [];
                }

                return data;
            };

            self.fxParser2 = function (data) {
                if(self.data.acc_filter === undefined){
                    data = data ? data.filter(e => e.currencyCode.includes(self.currency2)) : [];
                }
                else{
                    data = data ? data.filter(e => e.currencyCode.includes(self.currency2) && e.partyId.value === self.data.acc_filter) : [];
                }

                if(data.length > 0){
                    self.accountNumberFrom(data[0].id.value);
                    self.accountCurrencyFrom = self.data.debitCurrency;
                }
                else{
                    data = [];
                }

                return data;
            };

            self.currencyParserFx = function (data) {
                data = data && self.currency ? data.filter(e => e.value === self.currency) : data;

                return data;
            };

            self.currencyParserFx2 = function (data) {
                data = data && self.currency2 ? data.filter(e => e.value === self.currency2) : data;

                return data;
            };

            self.preFilledBoolean(true);
            self.isDiffCurrency(true);
            self.typesLoaded(true);

            self.cssSetter();
        };

        if(rootParams.rootModel.params.treasuryRef && rootParams.rootModel.params.webMailFlag === true){

            rootParams.baseModel.showMessages(null, [rootParams.rootModel.params.errorMessage], "ERROR");

        }

        if(rootParams.rootModel.params.treasuryReference){
            self.prefilledWebMail(rootParams.rootModel.params);
            self.isFxAgreedRate(true);

        }

        if(rootParams.rootModel.params.approvalDetails && rootParams.rootModel.params.approvalDetails.status === "MODIFICATION_REQUESTED"){
            self.oneInstanceFlag(true);
            self.twoInstanceFlag(true);
            self.threeInstanceFlag(true);
            self.sendToModify(rootParams.rootModel.params.data);
            self.txnId(rootParams.rootModel.params.transactionId + "#" + rootParams.rootModel.params.versionId);
            self.sendToModifyFlag(true);
        }

        self.submit = async function() {

            if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
                return false;
            }

            if(self.invalidCurrencyPairFlag() === true){
                rootParams.baseModel.showMessages(null, [self.nls.unsupportedCurrencyPair], "ERROR");

                return;
            }

            if (self.isFxAgreedRate() !== true && self.currency2() !== self.accountCurrencyTo() && self.currency2() !== self.accountCurrencyFrom()){
                rootParams.baseModel.showMessages(null, [self.nls.differentCurrencies], "ERROR");

                return;
            }

            else if(self.isFxAgreedRate() !== true && self.accountCurrencyFrom() === self.accountCurrencyTo() && self.additionalDetailsInfo().account.id.value === self.additionalDetails().account.id.value){
                rootParams.baseModel.showMessages(null, [self.nls.selfsameaccerror], "ERROR");

                return;
            }

            else if(self.isFxAgreedRate() === true && self.accountCurrencyFrom === self.accountCurrencyTo && self.additionalDetailsInfo().account.id.value === self.additionalDetails().account.id.value){
                rootParams.baseModel.showMessages(null, [self.nls.selfsameaccerror], "ERROR");

                return;
            }

            else if(self.isFxAgreedRate() !== true && self.accountCurrencyFrom() === self.accountCurrencyTo()){
                rootParams.baseModel.showMessages(null, [self.nls.sameCurr], "ERROR");

                return;
            }

            self.mode = "review";
            rootParams.baseModel.registerComponent("fx-rate-transaction-review", "fx-rate");

            if(self.isFxAgreedRate() === true){
                await RateModel.isOMBChecker(self.amount2, self.currency2, "FT_F_PFR").then(function (response) {
                    self.isOMB(response.ombEnabled);
                });
            }
            else{
                await RateModel.isOMBChecker(self.amount2(), self.currency2(), "FT_F_PFR").then(function (response) {
                    self.isOMB(response.ombEnabled);
                });
            }

            if (self.isFxAgreedRate() !== true){
                payload = {
                    debitAmount: self.amount2,
                    exchangeRate: 0,
                    treasuryReference: "",
                    cin: "",
                    creditAmount: 0,
                    debitAccountId: self.additionalDetails().account.id.value,
                    creditCurrency: "",
                    creditAccountId: self.additionalDetailsInfo().account.id.value,
                    debitCurrency: self.currency2,
                    crCompName: self.additionalDetailsInfo().account.partyName,
                    drCompName: self.additionalDetails().account.partyName,
                    crAccDisplay: self.additionalDetailsInfo().account.label,
                    drAccDisplay: self.additionalDetails().account.label,
                    crAccCurr: self.accountCurrencyTo,
                    drAccCurr: self.accountCurrencyFrom,
                    addnlDtls: "payCheck"
                };

                self.partyId = rootParams.dashboard.userData.userProfile.partyId.value;
                self.partyIdDisplay = rootParams.dashboard.userData.userProfile.partyId.displayValue;

                macpayload = {
                    partyId: {
                        displayValue: self.partyIdDisplay,
                        value: self.partyId
                    },
                    amount: {
                        currency: self.currency2,
                        amount: self.amount2
                    },
                    debitAccountId: {
                        displayValue: self.additionalDetails().account.id.displayValue,
                        value: self.additionalDetails().account.id.value
                    },
                    adhocPayment: false,
                    dealId: null,
                    accountType: "CSA",
                    paymentType: "SELF",
                    paymentDate: "1970-01-01T00:00:00",
                    network: "FXAGREE_SELF",
                    beneficiary: [
                    {
                    addressDTO: [
                        {},
                        {},
                        {}
                    ],
                    creditAccount: {
                        value: self.additionalDetailsInfo().account.id.value
                    },
                    accountType: "CSA"
                    }
                    ],
                    otherDetails: {
                        line3: null,
                        line4: null
                    },
                    currencyExchange: {
                        sourceCurrency: "",
                        targetCurrency: "",
                        exchangeRate: "",
                        instructedAmount: {}
                    },
                    dictionaryArray: [
                        {
                            nameValuePairDTOArray: [
                            {
                                genericName: "srcAcctCurrency",
                                value: self.accountCurrencyFrom
                            },
                            {
                                genericName: "purposeType",
                                value: ""
                            },
                            {
                                genericName: "destAcctCurrency",
                                value: self.accountCurrencyTo
                            },
                            {
                                genericName: "isApprovalDateChanged",
                                value: false
                            },
                            {
                                genericName: "finalApprovalDate",
                                value: ""
                            }
                            ]
                        }
                    ]
                };

                RateModel.transactionMACCall(ko.toJSON(macpayload)).then(function (response1){
                    const macResponsePayload = {
                        macData: response1.macData
                    };

                    RateModel.postFXAgreedRate(ko.toJSON(payload), macResponsePayload).then(function (){
                        const params = {
                            additionalDetails: self.additionalDetails,
                            accountCurrencyFrom: self.accountCurrencyFrom,
                            additionalDetailsInfo: self.additionalDetailsInfo,
                            accountCurrencyTo: self.accountCurrencyTo,
                            currency: self.currency,
                            amount: self.amount,
                            currency2: self.currency2,
                            amount2: self.amount2,
                            treasuryRefNo: self.treasuryRefNo,
                            mode: self.mode,
                            prefilled: self.preFilledBoolean,
                            isFxAgreedRate: self.isFxAgreedRate,
                            cin: self.cinNumber,
                            subExchange: self.subExchange,
                            exchangeRate: self.exchangeRate,
                            prefSubExchange: self.prefSubExchange,
                            //Added for FX-COUPON Start
                            CouponCode:self.CouponCode,
                            CpExchangeRate: self.CpExchangeRate,
                            //Ended
                            prefExchangeRate: self.prefExchangeRate,
                            txnId: self.txnId,
                            sendToModifyFlag: self.sendToModifyFlag,
                            isOMB: self.isOMB,
                            isSpecialAccount: self.isSpecialAccount
                        };

                        rootParams.dashboard.loadComponent("fx-rate-transaction-review", params);
                    });
                });
            }
            else{

                const params = {
                    additionalDetails: self.additionalDetails,
                    accountCurrencyFrom: self.accountCurrencyFrom,
                    additionalDetailsInfo: self.additionalDetailsInfo,
                    accountCurrencyTo: self.accountCurrencyTo,
                    currency: self.currency,
                    amount: self.amount,
                    currency2: self.currency2,
                    amount2: self.amount2,
                    treasuryRefNo: self.treasuryRefNo,
                    mode: self.mode,
                    prefilled: self.preFilledBoolean,
                    isFxAgreedRate: self.isFxAgreedRate,
                    cin: self.cinNumber,
                    subExchange: self.subExchange,
                    exchangeRate: self.exchangeRate,
                    prefSubExchange: self.prefSubExchange,
                    prefExchangeRate: self.prefExchangeRate,
                    txnId: self.txnId,
                    sendToModifyFlag: self.sendToModifyFlag,
                    isOMB: self.isOMB,
                    isSpecialAccount: self.isSpecialAccount,
                    account_filter: self.account_filter
                };

                rootParams.dashboard.loadComponent("fx-rate-transaction-review", params);
            }

        };

        self.cancel = function() {
            rootParams.dashboard.switchModule(true);
        };

        self.modalBack = function() {
            self.companyModal("");
            $("#treasuryReferenceModal").trigger("closeModal");
            self.loadedResults(false);
            self.backFlag(true);
        };

        //Added for FX-COUPON
        self.CPmodalBack = function() {
            $("#SelectCouponCodeModal").trigger("closeModal");
            self.loadedResults(false);
            self.backFlag(true);
        };
        //Ended

        self.back = function() {
            rootParams.dashboard.switchModule(true);
        };
    };
});