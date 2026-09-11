define([
    "knockout",
    "jquery",
    "./model",
    "ojL10n!extensions/resources/nls/profile",
    "ojs/ojbutton",
    "ojs/ojmenu",
    "ojs/ojinputtext",
    "framework/elements/api/page-section/loader"
], function (ko, $, PartyModel, resourceBundle) {
    "use strict";

    return function (rootParams) {
        const self = this;

        self.loadNextComponent = rootParams.rootModel.params.loadNextComponent;
        self.componentMandatory = rootParams.rootModel.params.componentMandatory;
        self.address = ko.observable();
        self.phoneNumber = ko.observable();
        self.faxNumber = ko.observable();
        self.dataFetched1 = ko.observable(false);
        self.partyData = ko.observable();
        self.nls = resourceBundle;
        self.response = ko.observable();
        self.profileconfig = ko.observable();
        self.attributeName = ko.observable();
        self.attributeValue = ko.observable();
        self.fullAddress = ko.observable();
        self.isCountryFetched = ko.observable(false);
        self.countries = ko.observableArray();
        self.countriesMap = {};
        self.country = ko.observable();
        self.attributeList = ko.observableArray([]);
        self.disableEdit = ko.observable(false);
        self.isMobileIsdSupport = ko.observable(false);
        self.isFaxIsdSupport = ko.observable(false);
        rootParams.baseModel.registerComponent("user-login-configuration-header", "user-login-configuration");

        self.headerMessages = ko.observableArray();

        self.showConfirmation = ko.observable(false);

        self.serializer = function (object) {
            return rootParams.baseModel.format("{line1}, {line2}, {line3}, {line4}, {city}, {country}, {postalCode}", {
                line1: object.line1,
                line2: object.line2,
                line3: object.line3,
                line4: object.line4,
                city: object.city,
                country: object.country,
                postalCode: object.zipCode
            });
        };

        self.headerMessages.push({
            icon: "dashboard/confirmation.svg",
            headerMessage: self.nls.confirm,
            summaryMessage: self.nls.successMessage,
            headerStyle: "successHeader"
        });

        self.readPartyDetails = function () {
            if (rootParams.dashboard.appData.segment === "RETAIL") {
                Promise.all([
                    PartyModel.fetchCountry(),
                    PartyModel.fetchParty(),
                    PartyModel.fetchProfileConfig()
                ]).then(function (response) {
                    const dataCountry = response[0],
                        dataParty = response[1],
                        dataPartyVar = dataParty.party.personalDetails,
                        dataPartyIdentification = dataParty.party.identifications,
                        dataProfileConfig = response[2].userProfileConfigDTO;

                    self.attributeList(dataProfileConfig);

                    let i;

                    for (i = 0; i < dataParty.party.contacts.length; i++) {
                        if (dataParty.party.contacts[i].contactType === "WMO") {
                            self.phoneNumber(dataParty.party.contacts[i].phone);
                        }

                        if (dataParty.party.contacts[i].contactType === "FAX") {
                            self.faxNumber(dataParty.party.contacts[i].fax);
                        }
                    }

                    for (i = 0; i < dataProfileConfig.contactDetails.length; i++) {
                        if (dataProfileConfig.contactDetails[i].fieldName === "PHONEISDNO") {
                            self.isMobileIsdSupport(true);
                        }

                        if (dataProfileConfig.contactDetails[i].fieldName === "FAXISDNO") {
                            self.isFaxIsdSupport(true);
                        }
                    }

                    if (self.isMobileIsdSupport()) {
                        dataPartyVar.PHONENO = (self.phoneNumber().areaCode ? self.phoneNumber().areaCode : "") + (self.phoneNumber().number ? self.phoneNumber().number : "");
                    } else {
                        dataPartyVar.PHONENO = self.phoneNumber() && self.phoneNumber().number ? self.phoneNumber().number : "";
                    }

                    if (self.isFaxIsdSupport()) {
                        dataPartyVar.FAXNO = (self.faxNumber().areaCode ? self.faxNumber().areaCode : "") + (self.faxNumber().number ? self.faxNumber().number : "");
                    } else {
                        dataPartyVar.FAXNO = self.faxNumber() && self.faxNumber().number ? self.faxNumber().number : "";
                    }

                    dataPartyVar.EMAIL = dataPartyVar.email;

                    for (i = 0; i < dataParty.party.addresses.length; i++) {
                        if (dataParty.party.addresses[i].type === "PST") {
                            self.fullAddress(dataParty.party.addresses[i].postalAddress);
                            self.address(self.serializer(dataParty.party.addresses[i].postalAddress));
                        }
                    }

                    dataPartyVar.ADDRESS = self.address();
                    self.partyData(dataPartyVar);

                    if (dataCountry.enumRepresentations) {
                        self.countries = ko.observableArray([]);

                        for (let i = 0; i < dataCountry.enumRepresentations[0].data.length; i++) {
                            self.countries.push({
                                text: dataCountry.enumRepresentations[0].data[i].description,
                                value: dataCountry.enumRepresentations[0].data[i].code
                            });

                            if (dataCountry.enumRepresentations[0].data[i].code.toUpperCase() === self.fullAddress().country.toUpperCase()) {
                                self.country(dataCountry.enumRepresentations[0].data[i].code);
                            }
                        }

                        self.isCountryFetched(true);
                    }

                    if (dataPartyIdentification !== undefined) {
                        for (i = 0; i < dataPartyIdentification.length; i++) {
                            dataPartyVar[dataPartyIdentification[i].type] = dataPartyIdentification[i].id;
                        }
                    }

                    self.dataFetched1(true);
                });
            } else {
                self.dataFetched1(false);
                self.partyData({});

                self.partyData().fullName = rootParams.baseModel.format(self.nls.name, {
                    firstName: rootParams.dashboard.userData.userProfile.firstName,
                    lastName: rootParams.dashboard.userData.userProfile.lastName
                });

                self.partyData().email = rootParams.dashboard.userData.userProfile.emailId.displayValue;
                self.phoneNumber(rootParams.dashboard.userData.userProfile.phoneNumber.displayValue);
                self.partyData().birthDate = rootParams.dashboard.userData.userProfile.dateOfBirth;

                if (rootParams.dashboard.userData.userProfile.address !== null) {
                    self.address(self.serializer(rootParams.dashboard.userData.userProfile.address));
                }

                self.dataFetched1(true);
            }
        };

        self.readPartyDetails();

        self.edit = function (data,value) {
            self.showConfirmation(false);
            self.attributeName(data);

            if(data==="EMAIL") {
                self.attributeValue(value ? value.displayValue ? value.displayValue : value : null);
                self.headerMessages()[0].summaryMessage=self.nls.emailUpdateMessage;
            }else{
                self.attributeValue(null);
                self.headerMessages()[0].summaryMessage=self.nls.successMessage;
           }

            $("#blockdetails_" + data).slideDown("slow");
            $("#blocktoshow_" + data).hide().trigger("closeModal");
            self.disableEdit(true);
        };

        self.hideUpdatedDetails = function (data) {
            self.attributeValue("");
            self.disableEdit(false);
            $("#blockdetails_" + data).hide().trigger("closeModal");
            $("#blocktoshow_" + data).show();
        };

        self.save = function () {
            if (self.attributeName() === "ADDRESS") {
                const obj = {
                    line1: self.fullAddress().line1,
                    line2: self.fullAddress().line2,
                    line3: self.fullAddress().line3,
                    city: self.fullAddress().city,
                    country: self.country(),
                    postalCode: self.fullAddress().postalCode
                };

                self.attributeValue(obj);
            }

            if (self.attributeName() === "PHONENO" && self.isMobileIsdSupport()) {
                const obj = {
                    areaCode: self.phoneNumber().areaCode ? self.phoneNumber().areaCode : "",
                    number: self.phoneNumber().number ? self.phoneNumber().number : ""
                };

                self.attributeValue(obj);
            }

            if (self.attributeName() === "FAXNO" && self.isFaxIsdSupport()) {
                const obj = {
                    areaCode: self.faxNumber().areaCode ? self.faxNumber().areaCode : "",
                    number: self.faxNumber().number ? self.faxNumber().number : ""
                };

                self.attributeValue(obj);
            }

            const payload = {
                attr_name: self.attributeName(),
                attr_value: self.attributeValue()
            };

            PartyModel.updateUserProfile(ko.toJSON(payload)).done(function (data) {
                self.response(data);
                self.readPartyDetails();
                self.showConfirmation(true);
                self.hideUpdatedDetails(self.attributeName());
                self.disableEdit(false);
            });
        };

        rootParams.dashboard.headerName(self.nls.heading);

        self.closeConfirmation = function () {
            self.showConfirmation(false);
        };

        self.downloadPartyDetails = function () {
            PartyModel.downloadPartyDetails(rootParams.dashboard.appData.segment === "RETAIL" ? "party" : "");
        };
    };
});