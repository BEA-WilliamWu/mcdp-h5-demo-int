define([
  "ojs/ojcore",
  "knockout",
  "./model",
  "jquery",
  "ojL10n!extensions/resources/nls/user-management",
  "extensions/generic/service-extension",
  "ojs/ojinputtext",
  "ojs/ojselectcombobox",
  "ojs/ojdatetimepicker",
  "ojs/ojradioset",
  "ojs/ojvalidationgroup",
  "ojs/ojknockout-validation",
  "ojs/ojcheckboxset",
  "ojs/ojswitch",
  "framework/js/libs/rsaOaepUtils/rsaOaepUtils"
], function (oj, ko, UsersUpdateModel, $, resourceBundle, serviceExtension) {
  "use strict";

  return function (rootParams) {
    const self = this;
    let isModified = false;

    isModified = rootParams.rootModel.params.transactionId !== undefined;

    ko.utils.extend(self, rootParams.rootModel);
    self.isNewUser = ko.observable(false);
    self.updateReviewFlag = ko.observable(false);
    self.updateConfirmFlag = ko.observable(false);
    self.changeName = ko.observable(true);
    self.isDisabled = ko.observable(false);
    self.updateUserData = ko.observable();
    self.userLimitsListLoaded = ko.observable(false);
    self.userLimitsList = ko.observableArray([]);
    self.userLimitsListKey = ko.observable([]);
    self.showChildRole = ko.observable(true);
    self.testhome = ko.observable(false);
    self.homeEntity = ko.observable("");
    self.selectedUserLimit = ko.observable();
    ko.utils.extend(self, rootParams.rootModel);
    self.accessibleEntityArray = ko.observableArray([]);
    self.countries = ko.observableArray();
    self.countriesMap = {};
    self.isCountryFetched = ko.observable(false);
    self.nls = resourceBundle;
    self.transactionName = ko.observable(rootParams.rootModel.params.transactionName);
    self.transactionName(self.nls.info.transactionNameUpdate);
    self.selectedDeviceList = ko.observableArray();
    self.selectedDeviceListForPushNotification = ko.observableArray();
    self.accessibleEntity = ko.observable("");
    rootParams.dashboard.headerName(self.nls.headers.userManagement);

    rootParams.baseModel.registerComponent("access-point-mapping", "financial-limits");
    rootParams.baseModel.registerComponent("accessible-entity", "user-management");
    rootParams.baseModel.registerComponent("review-user-update", "user-management");
    self.previousSelectedUserLimit = ko.observable();
    self.limitGroupId = ko.observable();
    self.limitGroupName = ko.observable();
    self.limitGroupDescription = ko.observable();
    self.helpInstruction = ko.observable();

    self.rolesFetched = ko.observable();
    self.rolesFetched(false);
    self.accessPoint = ko.observableArray([]);
    self.isAccessPointFetched = ko.observable(false);
    self.defaultUserGroupLoaded = ko.observable(false);
    self.defaultUserGroup = ko.observable();
    self.isCountryCodesFetched = ko.observable(false);
    self.countryCallingCodes = ko.observableArray();
    self.loginIdList = ko.observableArray();
    self.signerIdList = ko.observableArray();
    self.isSignerIdLoaded = ko.observable(false);
    self.isLoginIdLoaded = ko.observable(false);
    self.showLoginIdData = ko.observable(false);
    self.deleteSignerMessage = ko.observable();
    self.isSignerIdDeleted = ko.observable(false);
    self.isLoginHoldReasonMandatory = ko.observable(false);
    self.isSignerHoldReasonMandatory = ko.observable(false);
    self.isLoginPinStatusMandatory = ko.observable(false);
    self.isSignerPinStatusMandatory = ko.observable(false);

    /**
     * New loginID and SignerID observables
     */
    self.newSignerId = ko.observable();
    self.newLoginPinRefNo = ko.observable();
    self.newLoginPinStatus = ko.observable();
    self.newLoginHoldStatus = ko.observable();
    self.newLoginHoldReason = ko.observable();
    self.newSignerPinRefNo = ko.observable();
    self.newSignerPinStatus = ko.observable();
    self.newSignerHoldStatus = ko.observable();
    self.newSignerHoldReason = ko.observable();
    self.deletedSignerId = ko.observable();
    self.disableLoginStatusValues = ko.observable(false);
    self.disableLoginNewPinStatus = ko.observable(false);
    self.disableLoginNewHoldStatus = ko.observable(false);
    self.disableLoginNewHoldReason = ko.observable(false);
    self.newHoldReasonList = ko.observable([]);
    self.newSignerHoldReasonList = ko.observable([]);
    self.disableSignerStatusValues = ko.observable(false);
    self.disableSignerNewPinStatus = ko.observable(false);
    self.disableSignerNewHoldStatus = ko.observable(false);
    self.disableSignerNewHoldReason = ko.observable(false);
    self.party = ko.observable();

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
      label: self.nls.info.nil,
      value: "NIL"
    },
    {
      text: self.nls.info.retryExceeded,
      label: self.nls.info.retryExceeded,
      value: "Retry Count Exceeded"
    },
    {
      text: self.nls.info.reqByCustomer,
      label: self.nls.info.reqByCustomer,
      value: "Requested by Customer"
    },
    {
      text: self.nls.info.corpCustomer,
      label: self.nls.info.corpCustomer,
      value: "Corporate Customer"
    },
    {
      text: self.nls.info.others,
      label: self.nls.info.others,
      value: "Others"
    }
    ]);

    // Displayed/approved Code and the new Code submitted by this edit are independent.
    const hthDraft = isModified ? rootParams.rootModel.params.data : {};

    self.hthApiPasswordCodeId = ko.observable(hthDraft.hthApiPasswordCodeId);
    self.hthApiPasswordPendingCodeId = ko.observable(hthDraft.hthApiPasswordPendingCodeId);
    self.hthApiPasswordCode = ko.observable(hthDraft.hthApiPasswordCode);
    self.hthApiPasswordCodeStatus = ko.observable(hthDraft.hthApiPasswordCodeStatus);
    self.hthApiPasswordCodeVisible = ko.observable(false);

    if (!isModified) {

      if (rootParams.rootModel.params.userFullData.mobileNumber.trim() === "") {
        rootParams.rootModel.params.userFullData.mobileNumber = "";
      }

      if (rootParams.rootModel.params.userFullData.emailId.trim() === "") {
        rootParams.rootModel.params.userFullData.emailId = "";
      }

      self.userFullData = ko.observable(rootParams.rootModel.params.userFullData);
      self.userExtensionData = ko.observable(rootParams.rootModel.params.userExtensionData);
      self.isBM = ko.observable(rootParams.dashboard.appData.segment === "ADMIN");
      self.userTokenDataDTO = ko.observable(rootParams.rootModel.params.userTokenDataDTO);
      self.iTokenStatus = ko.observable();
      self.bmItokenService = ko.observable(null);

      if (self.userTokenDataDTO() && self.userTokenDataDTO().itokenStatus) {
        if (self.userTokenDataDTO().itokenStatus === "Locked" || self.userTokenDataDTO().itokenStatus === "Activated") {
          self.iTokenStatus(self.userTokenDataDTO().itokenStatus);
        }
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

      self.setBmItokenServie(self.userTokenDataDTO());

      self.selectedChildRole = ko.observableArray(rootParams.rootModel.params.userFullData.applicationRoles);
      self.selectedAccessPoint = ko.observableArray(rootParams.rootModel.params.selectedAccessPoint);
      self.childRoleEnumsLoaded = ko.observable(rootParams.rootModel.params.childRoleEnumsLoaded);
      self.childRoleEnums = ko.observableArray(rootParams.rootModel.params.childRoleEnums);
      self.rolesFetched(true);
      self.selectedExtAccessPoint = ko.observableArray([rootParams.rootModel.params.selectedExtAccessPoint]);
      self.selectedUserGroupValues = ko.observableArray(rootParams.rootModel.params.selectedUserGroupValues);
      self.userGroupValues = ko.observableArray(rootParams.rootModel.params.userGroupValues);
      self.showUserGroup = ko.observable(rootParams.rootModel.params.showUserGroup);
      self.isCountryFetched(true);
      self.idDocumentChecked = ko.observableArray(rootParams.rootModel.params.userExtensionData.idDocSubmitted ? ["true"] : ["false"]);
      self.isAuthorizedChecked = ko.observableArray(rootParams.rootModel.params.userExtensionData.isAuthorisedPerson ? ["true"] : ["false"]);
      self.companyActivityLog = ko.observableArray(rootParams.rootModel.params.userExtensionData.companyActivityLog ? ["true"] : ["false"]);
      self.showIsAuthorizedCheckBox = ko.observable(false);
      self.showActivityLogAtCompanyLevelCheckbox = ko.observable(false);
      self.isCorpAdmin = rootParams.rootModel.params.isCorpAdmin;
      self.isPersonalInfoMandatory = ko.observable(true);
      self.showIdDocCheckbox = ko.observable(false);
      self.bypassFlag = ko.observable(rootParams.rootModel.params.userExtensionData.bypassFlag === "Y");
      self.bypassCode = ko.observable(rootParams.rootModel.params.userExtensionData.bypassCode);
      self.bypassExpiryTime = ko.observable(rootParams.rootModel.params.userExtensionData.bypassExpiryTime);
      self.byPassCodeVisible = ko.observable(false);
      self.byPassCodeSwitchIconVisible = ko.observable(false);
      self.hasDisabledBypassStatus = ko.observable(false);
      self.isPendingConfirmBypassStatus = ko.observable(false);

      /**
       * BCOH2H-787: HTH API Password Code observables.
       * - displayed Code ID is independent of the new draft submitted for approval
       * - code: plain text (held only in memory for current session)
       * - codeStatus: PENDING / ACTIVE / EXPIRED / USED / ""
       * - codeVisible: eye-toggle state
       */

      // Fetch masked code status on load for HTH users
      if (self.userExtensionData() && self.userExtensionData().userChannelType === "HTH") {
        UsersUpdateModel.getHthApiPasswordCodeMasked(
          self.userFullData().partyId.value,
          self.userFullData().username
        ).done(function (data) {
          if (data.status && data.status.result === "SUCCESSFUL" && data.codeId && !self.hthApiPasswordPendingCodeId()) {
            self.hthApiPasswordCodeId(data.codeId);
            self.hthApiPasswordCodeStatus(data.codeStatus);
            self.hthApiPasswordCode(data.maskedCode);
          }
        });
      }

      if (self.userExtensionData().signerID) {
        self.showIdDocCheckbox(true);
        self.idDocumentChecked(["true"]);
      }

      /**
       * Make MobileNo, EmailID, DocID, DocCountry and DocType fields to not mandatory for migrated customer
       */
      if(!self.isCorpAdmin && self.userExtensionData().migtationStatus && (self.userExtensionData().migtationStatus === "Y" || self.userExtensionData().migtationStatus === "N" || self.userExtensionData().migtationStatus === "S")) {
        self.isPersonalInfoMandatory(false);
      }

      if (self.userExtensionData() && self.userExtensionData().documentType && self.userExtensionData().documentType === "Hong Kong Identity Card") {
        self.helpInstruction(self.nls.info.hkidToolTip);
      } else {
        self.helpInstruction("");
      }

      const searchParameters = {
        accessType: "INT"
      };

      if (self.userExtensionData().signerID !== null) {
        self.deletedSignerId(self.userExtensionData().signerID);
        self.showIsAuthorizedCheckBox(true);
      }

      self.showActivityLogAtCompanyLevelCheckbox(true);

      UsersUpdateModel.fetchAccess(searchParameters).done(function (data) {
        for (let i = 0; i < data.accessPointListDTO.length; i++) {
          if (data.accessPointListDTO[i].id === "APINTERNET" || data.accessPointListDTO[i].id === "APMOBRESP") {
            self.accessPoint().push({
              text: data.accessPointListDTO[i].description,
              value: data.accessPointListDTO[i].id
            });
          }
        }

        self.isAccessPointFetched(true);
      });
    } else {
      self.userType = ko.observable(rootParams.rootModel.params.data.userType);
      self.userFullData = ko.observable(rootParams.rootModel.params.data);

      UsersUpdateModel.fetchChildRole(rootParams.rootModel.params.data.userType).done(function (data) {
        self.childRoleEnums = ko.observableArray(data.applicationRoleDTOs);
        self.childRoleEnumsLoaded = ko.observable(true);
        self.selectedChildRole = ko.observableArray(self.userFullData().applicationRoles);
        rootParams.rootModel.params.childRoleEnumsLoaded = ko.observable(true);
        rootParams.rootModel.params.childRoleEnums = ko.observableArray(data.applicationRoleDTOs);
        self.rolesFetched(true);
      });

      self.selectedAccessPoint = ko.observableArray([]);

      for (let i = 0; i < rootParams.rootModel.params.data.userAccessPointRelationshipList.length; i++) {
        if (rootParams.rootModel.params.data.userAccessPointRelationshipList[i].status === true) {
          self.selectedAccessPoint.push(rootParams.rootModel.params.data.userAccessPointRelationshipList[i].accessPointId);
        }
      }

      self.selectedExtAccessPoint = ko.observableArray([]);
      self.accessPointExt = ko.observableArray([]);
      self.accessPointGroup = ko.observableArray([]);

      const searchParameters = {
        accessType: "INT"
      };

      Promise.all([
        UsersUpdateModel.fetchAccess(searchParameters),
        UsersUpdateModel.listAccessPointGroup()
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

      for (let i = 0; i < rootParams.rootModel.params.data.userAccessPointRelationshipList.length; i++) {
        for (let j = 0; j < self.accessPointExt().length; j++) {
          if (rootParams.rootModel.params.data.userAccessPointRelationshipList[i].accessPointId === self.accessPointExt()[j].value) {
            self.selectedExtAccessPoint.push(rootParams.rootModel.params.data.userAccessPointRelationshipList[i]);
          }
        }
      }

      const enterpriseRoleId = rootParams.rootModel.params.data.userType;

      self.userFullData().userType = [];
      self.userFullData().userType.enterpriseRoleId = enterpriseRoleId;
      self.userFullData().userType.enterpriseRoleName = enterpriseRoleId === "corporateuser" ? "Corporate user" : enterpriseRoleId === "administrator" ? "Admin User" : "Retail User";
      self.userFullData().title = rootParams.rootModel.params.data.title;

      rootParams.rootModel.params.salutationList = ko.observableArray([{
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

      UsersUpdateModel.fetchCountry().done(function (data) {
        if (data.enumRepresentations) {
          for (let i = 0; i < data.enumRepresentations[0].data.length; i++) {
            self.countries.push({
              text: data.enumRepresentations[0].data[i].description,
              value: data.enumRepresentations[0].data[i].code
            });

            self.countriesMap[data.enumRepresentations[0].data[i].code] = data.enumRepresentations[0].data[i].description;
          }

          self.isCountryFetched(true);
          rootParams.rootModel.params.countries = ko.observableArray(self.countries());
          rootParams.rootModel.params.isCountryFetched = ko.observable(true);
        }
      });

      self.userGroupValues = ko.observableArray();
      self.showUserGroup = ko.observable(false);
      self.selectedUserGroupValues = ko.observableArray();

      if (self.userFullData().userType.enterpriseRoleId === "corporateuser") {
        UsersUpdateModel.fetchUserGroupList(self.userFullData().partyId.value).then(function (userGroups) {
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

    }

    self.limitPackageDetails = ko.observable();
    self.header = self.nls.fieldname.limit;
    self.selectedUserLimitPackages = ko.observableArray();
    self.userPreferenceAccessPointRelationship = ko.observableArray([]);
    self.entityAccessPoint = ko.observable();
    self.accessPointType = ko.observable("INT");
    self.isUserSegmentsFetched = ko.observable(false);
    self.selectedSegmentRoles = ko.observableArray();
    self.isSegmentContainsRole = ko.observable(false);
    self.androidDevice = ko.observable(rootParams.rootModel.params.androidDevice);
    self.iOsDevice = ko.observable(rootParams.rootModel.params.iOsDevice);
    self.androidDeviceForPushNotification = ko.observable(rootParams.rootModel.params.androidDeviceForPushNotification);
    self.iOsDeviceForPushNotification = ko.observable(rootParams.rootModel.params.iOsDeviceForPushNotification);
    self.iOsDisabled = ko.observable(rootParams.rootModel.params.iOsDisabled);
    self.androidDisabledForPushNotification = ko.observable(rootParams.rootModel.params.androidDisabledForPushNotification);
    self.iOsDisabledForPushNotification = ko.observable(rootParams.rootModel.params.iOsDisabledForPushNotification);
    self.username = ko.observable(rootParams.rootModel.params.username);
    self.countries = ko.observable(rootParams.rootModel.params.countries);
    self.selectedSegmentCode = ko.observable(rootParams.rootModel.params.selectedSegmentCode);
    self.androidDisabled = ko.observable(rootParams.rootModel.params.androidDisabled);
    self.userSegments = ko.observableArray(rootParams.rootModel.params.userSegments);
    self.selectedSegmentName = ko.observable(rootParams.rootModel.params.selectedSegmentName);
    self.hideToolTip = rootParams.rootModel.params.hideToolTip;
    self.showToolTip = rootParams.rootModel.params.showToolTip;
    self.newHoldReasonList(self.holdReasonList());
    self.newSignerHoldReasonList(self.holdReasonList());

    self.documentTypeChangeHandler = function (event) {

      document.getElementById("documentId").value = "";

      if (document.getElementById("documentId").valid === "invalidShown") {
        document.getElementById("documentId").reset();
      }

      if (event.detail && event.detail.value && event.detail.value === "Hong Kong Identity Card") {
        self.helpInstruction(self.nls.info.hkidToolTip);
      } else {
        self.helpInstruction("");
      }
    };

    /**
     * Disable login pin status dropdowns
     */
    if (!(self.newLoginPinRefNo() === null || self.newLoginPinRefNo() === undefined || self.newLoginPinRefNo() === "")) {
      self.disableLoginNewPinStatus(true);
      self.disableLoginNewHoldStatus(true);
      self.disableLoginNewHoldReason(true);
    }

    /**
     * Disable Signer pin status dropdowns
     */
    if (!(self.newSignerPinRefNo() === null || self.newSignerPinRefNo() === undefined || self.newSignerPinRefNo() === "")) {
      self.disableSignerNewPinStatus(true);
      self.disableSignerNewHoldStatus(true);
      self.disableSignerNewHoldReason(true);
    }

    /**
     * This function deletes signer ID
     * @function deleteSignerId
     */
    self.deleteSignerId = function () {
      if (self.isSignerIdDeleted()) {
        self.userExtensionData().signerID = null;
        self.userExtensionData().signerPinReferenceNo = null;
        self.userExtensionData().signerPinstatus = null;
        self.userExtensionData().signerHoldStatus = null;
        self.userExtensionData().signerHoldReason = null;
        self.userExtensionData().idDocSubmitted = false;
        self.userExtensionData().isAuthorisedPerson = false;
        self.isSignerIdDeleted(true);
        self.showIdDocCheckbox(false);
        self.idDocumentChecked(["false"]);
        $("#deleteSignerId").trigger("closeModal");
      } else {
        $("#deleteSignerId").trigger("closeModal");
      }
    };

    /**
     * This function opens popup
     * @function openDeleteSignerPopup
     */
    self.openDeleteSignerPopup = function () {
      if (self.isAuthorizedChecked()[0] === "true") {
        self.deleteSignerMessage(self.nls.info.deleteSignerError);
        $("#deleteSignerId").trigger("openModal");
      } else {
        self.deleteSignerMessage(self.nls.info.deleteSignerInfo);
        self.isSignerIdDeleted(true);
        $("#deleteSignerId").trigger("openModal");
      }
    };

    const newLoginPinRefNo = self.newLoginPinRefNo.subscribe(function (data) {
        if (data !== null) {
          self.isLoginPinStatusMandatory(true);
        }

        if (data !== undefined && data !== null && data !== "") {
          self.disableLoginNewPinStatus(false);
          self.disableLoginNewHoldStatus(false);
          self.disableLoginNewHoldReason(false);
        } else {
          self.disableLoginNewPinStatus(true);
          self.disableLoginNewHoldStatus(true);
          self.disableLoginNewHoldReason(true);
        }
      }),
      newSignerPinRefNo = self.newSignerPinRefNo.subscribe(function (data) {
        if (data !== null) {
          self.isSignerPinStatusMandatory(true);
        }

        if (data !== undefined && data !== null && data !== "") {
          self.disableSignerNewPinStatus(false);
          self.disableSignerNewHoldStatus(false);
          self.disableSignerNewHoldReason(false);
        } else {
          self.disableSignerNewPinStatus(true);
          self.disableSignerNewHoldStatus(true);
          self.disableSignerNewHoldReason(true);
        }
      });

    self.newLoginHoldStatusHandler = function (event) {
      const data = event.detail.value;

      self.newHoldReasonList(self.holdReasonList());

      if (data === self.nls.info.noActivityAllowed && self.isCorpAdmin === false) {
        self.isLoginHoldReasonMandatory(true);
      }else {
        self.isLoginHoldReasonMandatory(false);
      }

      if (data === self.nls.info.unhold && event.detail.trigger === "option_selected") {
        self.newLoginHoldReason(self.nls.info.nil);
        self.disableLoginNewHoldReason(true);
      }else if (event.detail.trigger === "option_selected") {
        self.disableLoginNewHoldReason(false);

        if (self.newLoginHoldReason() === self.nls.info.nil) {
          self.newLoginHoldReason(null);
        }

        if (data === self.nls.info.noActivityAllowed) {
          self.newHoldReasonList([{
            label: "Please Select",
            text: "Please Select",
            value: ""
          },...self.holdReasonList().slice(1)]);
        }
      }
    };

    self.newLoginHoldReasonHandler = function (event) {
      const data = event.detail.value;

      if (data === self.nls.info.nil && event.detail.trigger === "option_selected") {
        self.newLoginHoldStatus(self.nls.info.unhold);
        self.disableLoginNewHoldStatus(true);
      }else if(event.detail.trigger === "option_selected"){
        self.disableLoginNewHoldStatus(false);

        if (self.newLoginHoldStatus() === self.nls.info.unhold){
          self.newLoginHoldStatus(null);
        }
      }
    };

    self.newSignerHoldStatusHandler = function (event) {
      const data = event.detail.value;

      self.newSignerHoldReasonList(self.holdReasonList());

      if (data === self.nls.info.noActivityAllowed && self.isCorpAdmin === false) {
        self.isSignerHoldReasonMandatory(true);
      }else {
        self.isSignerHoldReasonMandatory(false);
      }

      if (data === self.nls.info.unhold && event.detail.trigger === "option_selected") {
        self.newSignerHoldReason(self.nls.info.nil);
        self.disableSignerNewHoldReason(true);
      }else if (event.detail.trigger === "option_selected") {
        self.disableSignerNewHoldReason(false);

        if (self.newSignerHoldReason() === self.nls.info.nil) {
          self.newSignerHoldReason(null);
        }

        if (data === self.nls.info.noActivityAllowed) {
          self.newSignerHoldReasonList([{
            label: "Please Select",
            text: "Please Select",
            value: ""
          },...self.holdReasonList().slice(1)]);
        }
      }
    };

    self.newSignerHoldReasonHandler = function (event) {
      const data = event.detail.value;

      if (data === self.nls.info.nil && event.detail.trigger === "option_selected") {
        self.newSignerHoldStatus(self.nls.info.unhold);
        self.disableSignerNewHoldStatus(true);
      }else if(event.detail.trigger === "option_selected"){
        self.disableSignerNewHoldStatus(false);

        if (self.newSignerHoldStatus() === self.nls.info.unhold){
          self.newSignerHoldStatus(null);
        }
      }
    };

    UsersUpdateModel.fetchCountryCodes().done(function (data) {
      if (data.enumRepresentations) {
        for (let i = 0; i < data.enumRepresentations[0].data.length; i++) {
          self.countryCallingCodes.push({
            text: data.enumRepresentations[0].data[i].code,
            value: data.enumRepresentations[0].data[i].value
          });
        }

        self.isCountryCodesFetched(true);
      }
    });

    /**
     * This function gets the signerId and loginIds maintained in User Maintenance
     * @function fetchUserIdMaintenance
     */
    if (self.userFullData().userType.enterpriseRoleId === "corporateuser" && self.isCorpAdmin) {
      UsersUpdateModel.fetchUserIdMaintenance(self.userFullData().partyId.value).done(function (data) {
        for (let i = 0; i < data.listUserIdMaintenance.length; i++) {
          if (data.listUserIdMaintenance[i].idType === "01") {
            self.signerIdList.push({
              text: data.listUserIdMaintenance[i].id,
              value: data.listUserIdMaintenance[i].id,
              holdReason: data.listUserIdMaintenance[i].holdReson,
              holdStatus: data.listUserIdMaintenance[i].holdStatus,
              pinStatus: "Inactive"
            });
          } else if (data.listUserIdMaintenance[i].idType === "02") {
            self.loginIdList.push({
              text: data.listUserIdMaintenance[i].id,
              value: data.listUserIdMaintenance[i].id,
              holdReason: data.listUserIdMaintenance[i].holdReson,
              holdStatus: data.listUserIdMaintenance[i].holdStatus,
              pinStatus: "Inactive"
            });
          }
        }

        self.isSignerIdLoaded(true);
        self.isLoginIdLoaded(true);
      });
    }

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
      return self.userExtensionData && self.userExtensionData() && self.userExtensionData().userChannelType === "HTH" && !self.isViewerRole(roleId);
    };

    if (self.userFullData().userType.enterpriseRoleId === "corporateuser" && self.isCorpAdmin === false) {

      UsersUpdateModel.fetchDayZeroUserIds("02", self.userFullData().partyId.displayValue).done(function (data) {
        for (let i = 0; i < data.userIdsList.length; i++) {
          const userObject = {
            text: data.userIdsList[i].id,
            value: data.userIdsList[i].id
          };

          self.loginIdList.push(userObject);
        }

        self.isLoginIdLoaded(true);
      });

      UsersUpdateModel.fetchDayZeroUserIds("01", self.userFullData().partyId.displayValue).done(function (data) {
        for (let i = 0; i < data.userIdsList.length; i++) {
          const userObject = {
            text: data.userIdsList[i].id,
            value: data.userIdsList[i].id
          };

          self.signerIdList.push(userObject);
        }

        self.isSignerIdLoaded(true);
      });
    }

    /**
     * Value change handler for loginID
     * @function loginIdChanged
     * @param {String} data indicates selected id
     */
    self.loginIdChanged = function (data) {
      for (let i = 0; i < self.loginIdList().length; i++) {
        if (self.loginIdList()[i].value === data.detail.value) {
          self.newLoginPinStatus(self.loginIdList()[i].pinStatus);
          self.newLoginHoldStatus(self.loginIdList()[i].holdStatus);
          self.newLoginHoldReason(self.loginIdList()[i].holdReason || self.nls.info.nil);
          self.showLoginIdData(true);
          break;
        }
      }
    };

    /**
     * Value change handler for signerID
     * @function signerIdChanged
     * @param {String} data indicates selected id
     */
    self.signerIdChanged = function (data) {
      for (let i = 0; i < self.signerIdList().length; i++) {
        if (self.signerIdList()[i].value === data.detail.value) {
          self.userExtensionData().signerPinStatus = self.signerIdList()[i].pinStatus;
          self.userExtensionData().signerHoldStatus = self.signerIdList()[i].holdStatus;
          self.userExtensionData().signerHoldReason = self.signerIdList()[i].holdReason;
          break;
        }
      }

      if (self.userExtensionData().signerID !== null) {
        self.showIsAuthorizedCheckBox(true);
      }

      if (self.newSignerId()) {
        self.showIdDocCheckbox(true);
        self.idDocumentChecked(["true"]);
      } else {
        self.showIdDocCheckbox(false);
        self.idDocumentChecked(["false"]);
      }
    };

    self.documentIdValidator = {
      validate: function (value) {

        if (self.userExtensionData().documentType === "Hong Kong Identity Card") {

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

          if (self.userExtensionData().documentType === "Hong Kong Identity Card" && value !== undefined && value !== "" && !serviceExtension.checkHKId(value)) {
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
     * This function gets title and fullname based on document details
     * @function validateCustDetails
     */
    self.validateCustDetails = function () {
      if (self.userExtensionData().documentType === undefined || self.userExtensionData().documentType === "") {
        rootParams.baseModel.showMessages(null, [self.nls.info.selectDocType], "ERROR");

        return;
      }

      UsersUpdateModel.validateCustDetails(self.userExtensionData().documentType, self.getCountryCode(self.userExtensionData().documentCountry), self.userExtensionData().documentID).done(function (data) {
        self.changeName(false);
        ko.tasks.runEarly();
        ko.tasks.runEarly();

        if (data.userResponseDTO.userDTO.firstName === "") {
          rootParams.baseModel.showMessages(null, [self.nls.info.validateId], "ERROR");
        } else {
          $("#basicSelect").val(data.userResponseDTO.userDTO.title);
          $("#firstName").val(data.userResponseDTO.userDTO.firstName);
          self.userFullData().firstName = data.userResponseDTO.userDTO.firstName;
          self.changeName(true);
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

    self.getHomeEntityLimit = function () {
      if (!isModified) {
        const assignableEntitiesData = [{
          key: {
            value: self.userFullData().userType.enterpriseRoleId,
            type: "ROLE"
          }
        }];

        UsersUpdateModel.fetchUserLimitOptions(self.userFullData().homeEntity, ko.toJSON(assignableEntitiesData)).done(function (data) {
          let i;

          self.userLimitsList(data.limitPackageDTOList);

          if (self.userFullData().limitPackages !== undefined && self.userFullData().limitPackages.length) {
            for (i = 0; i < self.userFullData().limitPackages.length; i++) {
              if (self.userFullData().limitPackages[i].targetUnit === self.userFullData().homeEntity) {
                for (let m = 0; m < self.userFullData().limitPackages[i].entityLimitPackageMappingDTO.length; m++) {
                  self.selectedUserLimitPackages.push({
                    key: {
                      id: self.userFullData().limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.key.id
                    },
                    accessPointValue: self.userFullData().limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.accessPointValue,
                    accessPointGroupType: self.userFullData().limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.accessPointGroupType
                  });
                }

                break;
              }
            }
          }

          self.userLimitsListLoaded(true);
        });
      } else {
        const assignableEntitiesData = [{
          key: {
            value: rootParams.rootModel.params.data.userType,
            type: "ROLE"
          }
        }];

        UsersUpdateModel.fetchUserLimitOptions(rootParams.rootModel.params.homeEntity, ko.toJSON(assignableEntitiesData)).done(function (data) {
          let i;

          self.userLimitsList(data.limitPackageDTOList);

          if (rootParams.rootModel.params.data.limitPackages !== undefined && rootParams.rootModel.params.data.limitPackages.length) {
            for (i = 0; i < rootParams.rootModel.params.data.limitPackages.length; i++) {
              if (rootParams.rootModel.params.data.limitPackages[i].targetUnit === rootParams.rootModel.params.data.homeEntity) {
                for (let m = 0; m < rootParams.rootModel.params.data.limitPackages[i].entityLimitPackageMappingDTO.length; m++) {
                  self.selectedUserLimitPackages.push({
                    key: {
                      id: rootParams.rootModel.params.data.limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.key.id
                    },
                    accessPointValue: rootParams.rootModel.params.data.limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.accessPointValue,
                    accessPointGroupType: rootParams.rootModel.params.data.limitPackages[i].entityLimitPackageMappingDTO[m].limitPackage.accessPointGroupType
                  });
                }

                break;
              }
            }
          }

          self.userLimitsListLoaded(true);
          rootParams.rootModel.params.userLimitsListLoaded = ko.observable(true);
        });
      }
    };

    if (self.androidDevice()) {
      self.androidDisabled(false);
    }

    if (self.iOsDevice()) {
      self.iOsDisabled(false);
    }

    if (self.androidDeviceForPushNotification()) {
      self.androidDisabledForPushNotification(false);
    }

    if (self.iOsDeviceForPushNotification()) {
      self.iOsDisabledForPushNotification(false);
    }

    const androidDeviceSubscription = self.androidDevice.subscribe(function () {
      if (!self.androidDevice()) {
        self.selectedDeviceList.push("ANDROID");
      } else {
        self.selectedDeviceList.remove("ANDROID");
      }
    }),
      iosDeviceSubscription = self.iOsDevice.subscribe(function () {
        if (!self.iOsDevice()) {
          self.selectedDeviceList.push("IOS");
        } else {
          self.selectedDeviceList.remove("IOS");
        }
      }),
      androidDeviceForPushNotificationSubscription = self.androidDeviceForPushNotification.subscribe(function () {
        if (!self.androidDeviceForPushNotification()) {
          self.selectedDeviceListForPushNotification.push("ANDROID");
        } else {
          self.selectedDeviceListForPushNotification.remove("ANDROID");
        }
      }),
      iosDeviceForPushNotificationSubscription = self.iOsDeviceForPushNotification.subscribe(function () {
        if (!self.iOsDeviceForPushNotification()) {
          self.selectedDeviceListForPushNotification.push("IOS");
        } else {
          self.selectedDeviceListForPushNotification.remove("IOS");
        }
      });

    self.getHomeEntityLimit();

    if (!self.isNewUser()) {
      self.id = ko.observable(rootParams.id);
    }

    if (!isModified) {
      if (self.userFullData().userType.enterpriseRoleId === "retailuser") {
        self.isDisabled(true);

        const searchParameter = {
          selectedUser: self.userFullData().userType.enterpriseRoleId
        };

        UsersUpdateModel.fetchUserSegments(searchParameter).done(function (data) {
          self.userSegments([]);
          self.isUserSegmentsFetched(false);
          self.selectedSegmentName("");
          self.selectedSegmentCode("");

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
              value: data.segmentdtos[j].code,
              roles: data.segmentdtos[j].roles
            });
          }

          self.isUserSegmentsFetched(true);
        });
      }
    } else if (rootParams.rootModel.params.data.userType === "retailuser") {
      self.isDisabled(true);

      const searchParameter = {
        selectedUser: rootParams.rootModel.params.data.userType
      };

      UsersUpdateModel.fetchUserSegments(searchParameter).done(function (data) {
        self.userSegments([]);
        self.isUserSegmentsFetched(false);
        self.selectedSegmentName("");
        self.selectedSegmentCode("");

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
            value: data.segmentdtos[j].code,
            roles: data.segmentdtos[j].roles
          });
        }

        self.isUserSegmentsFetched(true);
      });
    }

    self.statusOptionChangeHandler = function (event) {
      if (event.detail.value) {
        self.statusOptionValue(event.detail.value);
      }
    };

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

    const today = rootParams.baseModel.getDate();

    today.setFullYear(today.getFullYear() - 18);
    self.maxDate = ko.observable(formatDate(today));
    self.validationTracker = ko.observable();

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

    self.rePopulateEntityList = function () {
      const array = [];

      for (let i = 0; i < rootParams.dashboard.userData.userProfile.accessibleEntityDTOs.length; i++) {
        array.push(rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[i]);
      }

      let index = null;

      for (let j = 0; j < array.length; j++) {
        if (array[j].entityId === self.userFullData().homeEntity) {
          index = j;
        }
      }

      array.splice(index, 1);
      self.entityList(array.slice(0));
      self.entitiesListLoaded(true);
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
     * @function initiateCreateUser
     * This function checks for OMB and initate.
     */
    self.initiateUserUpdate = function () {
      serviceExtension.checkTransactionQualifiesOMB("MT_N_UUS").then(function () {
        self.toCheckApproval();
      });
    };

    self.toCheckApproval = function () {
      self.userGroupList = ko.observable();

      const j = 0;

      if (self.selectedUserGroupValues().length > 0) {
        for (let i = 0; i < self.selectedUserGroupValues().length; i++) {
          if (i === j) {
            self.userGroupList(self.selectedUserGroupValues()[i]);
          } else {
            const userGroupId = self.userGroupList() + "~" + self.selectedUserGroupValues()[i];

            self.userGroupList(userGroupId);
          }
        }
      } else {
        self.userGroupList("EMPTY");
      }

      UsersUpdateModel.checkApproval(self.userFullData().partyId.displayValue, self.userGroupList(), self.userFullData().username).done(function (resp) {
        if (resp.ifExists) {
          $("#userGroupRejectNotification").trigger("openModal");
        } else {
          self.reviewUpdateUser();
        }
      });
    };

    self.reviewUpdateUser = function () {
      self.closeuserGroupNotification();

      if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
        return;
      }

      if (self.companyActivityLog()[0] === "true") {
          if ((self.selectedChildRole().length === 1 && (self.selectedChildRole()[0] === "Viewer_TL" || self.selectedChildRole()[0] === "Migrated_SYSADM_TL"))
          || (self.selectedChildRole().length === 2 && ((self.selectedChildRole()[0] === "Viewer_TL" && self.selectedChildRole()[1] === "Migrated_SYSADM_TL")) || (self.selectedChildRole()[0] === "Migrated_SYSADM_TL" && self.selectedChildRole()[1] === "Viewer_TL"))) {
              rootParams.baseModel.showMessages(null, [self.nls.info.noOtherRole], "ERROR");

              return;
          }
      }

      self.populateAccessPointForPayload();

      if (self.selectedChildRole().length === 0 && (self.selectedSegmentCode() === undefined || self.selectedSegmentCode() === "")) {
        rootParams.baseModel.showMessages(null, [self.nls.info.noRoleSegment], "ERROR");

        return;
      }

      if (!self.validateAccessibleEntityList()) {
        return;
      }

      if (!self.userExtensionData().documentID || !self.userExtensionData().documentType || !self.userExtensionData().documentCountry) {
        rootParams.baseModel.showMessages(null, [self.nls.info.DOCUMENT_ID_EMPTY], "ERROR");

        return;
      }

      const data = {
        username: self.userFullData().username ? self.userFullData().username : "",
        firstName: self.userFullData().firstName ? self.userFullData().firstName : "",
        middleName: self.userFullData().middleName ? self.userFullData().middleName : "",
        lastName: self.userFullData().lastName ? self.userFullData().lastName : "",
        dateOfBirth: self.userFullData().dateOfBirth ? self.userFullData().dateOfBirth : "",
        mobileNumber: self.userFullData().mobileNumber ? self.userFullData().mobileNumber : "",
        emailId: self.userFullData().emailId !== "" ? self.userFullData().emailId.toLowerCase() : "",
        employeeType: self.userFullData().employeeType ? self.userFullData().employeeType : "",
        phoneNumber: self.userFullData().phoneNumber ? self.userFullData().phoneNumber : "",
        userType: self.userFullData().userType ? self.userFullData().userType.enterpriseRoleId : "",
        address: {
          line1: self.userFullData().address.line1 ? self.userFullData().address.line1 : "",
          line2: self.userFullData().address.line2 ? self.userFullData().address.line2 : "",
          line3: self.userFullData().address.line3 ? self.userFullData().address.line3 : "",
          line4: self.userFullData().address.line4 ? self.userFullData().address.line4 : "",
          city: self.userFullData().address.city ? self.userFullData().address.city : "",
          state: self.userFullData().address.state ? self.userFullData().address.state : "",
          country: self.userFullData().address.country ? self.userFullData().address.country : "",
          zipCode: self.userFullData().address.zipCode ? self.userFullData().address.zipCode : ""
        },
        partyId: {},
        version: self.userFullData().version,
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
        partyName: self.userFullData().partyName ? self.userFullData().partyName : "",
        userAccessPointRelationshipList: self.userPreferenceAccessPointRelationship(),
        segmentCode: self.selectedSegmentCode(),
        userGroups: self.selectedChildRole(),
        title: self.userFullData().title ? self.userFullData().title : "",
        organization: self.userFullData().organization ? self.userFullData().organization : "",
        manager: self.userFullData().manager ? self.userFullData().manager : "",
        employeeNumber: self.userFullData().employeeNumber ? self.userFullData().employeeNumber : "",
        deviceList: self.selectedDeviceList().length !== 0 ? self.selectedDeviceList() : "",
        deregisterIOS: self.iOsDevice(),
        deregisterAndroid: self.androidDevice(),
        deregisterPushAndroid: self.androidDeviceForPushNotification(),
        deregisterPushIOS: self.iOsDeviceForPushNotification(),
        applicationRoles: self.selectedChildRole(),
        userGroupDTOs: self.userFullData().userGroupDTOs,
        selectedUserGroupValues: self.selectedUserGroupValues(),
        userGroupValues: self.userGroupValues(),
        bypassFlag: self.bypassFlag(),
        bypassCode: self.bypassCode(),
        hthApiPasswordCodeId: self.hthApiPasswordCodeId(),
        hthApiPasswordPendingCodeId: self.hthApiPasswordPendingCodeId(),
        hthApiPasswordCodeStatus: self.hthApiPasswordCodeStatus(),
        hthApiPasswordCode: self.hthApiPasswordCode()
      };

      self.setEntityRelatedFields(data);
      self.updateUserData(data);
      self.rePopulateEntityList();
      self.byPassCodeVisible(false);
      self.updateReviewFlag(true);
      rootParams.baseModel.closeNotificationMessages();

      $.extend(self.params, {
        data: data,
        accessibleEntityTemplate: self.accessibleEntityArray,
        limitGroupName: self.limitGroupName
      });

      /**
       * Calls validatePinStatus API to display warnings
       */
      if (self.isCorpAdmin === false) {
        UsersUpdateModel.validatePinStatus(data.username, ko.toJSON(self.generateValidatePinPayload())).done(function (data) {
          if (data.status.message.code === "DIGX_CZ_PIN_RESET_WARNING") {
            $("#pinResetWarning").trigger("openModal");
          } else if (data.status.message.code === "DIGX_CZ_SUSPEND_NOTIFICATION") {
            $("#suspendNotification").trigger("openModal");
          }
        });
      }
    };

    /**
     * @function closePinResetWarning
     * This function closes Pin Reset warning modal window.
     */
    self.closePinResetWarning = function () {
      $("#pinResetWarning").trigger("closeModal");
    };

    /**
     * @function closeSuspendNotification
     * This function closes suspend notification warning modal window.
     */
    self.closeSuspendNotification = function () {
      $("#suspendNotification").trigger("closeModal");
      self.backOnReview();
    };

    /**
     * @function sendSuspendEmailAlert
     * This function sends email alert to customer.
     */
    self.sendSuspendEmailAlert = function () {
      UsersUpdateModel.sendSuspendEmailAlert(ko.toJSON(self.generateValidatePinPayload()));
      self.closeSuspendNotification();
    };

    /**
     * @function generateValidatePinPayload
     * This function generates payload for validate pin status and send suspend alert.
     * @returns validationDataPayload
     */
    self.generateValidatePinPayload = function () {
      const validationDataPayload = {},
        validationData = {
          username: self.userFullData().username ? self.userFullData().username : "",
          mobileNumber: self.userFullData().mobileNumber ? self.userFullData().mobileNumber : "",
          emailId: self.userFullData().emailId !== "" ? self.userFullData().emailId.toLowerCase() : "",
          version: self.userFullData().version
        },
        validationExtensionData = {
          cdcNo: self.userExtensionData().cdcNo,
          userID: self.username(),
          mobileNo: self.userExtensionData().mobileNo,
          mobileCode: self.userExtensionData().mobileCode,
          loginHoldStatus: self.newLoginHoldStatus() ? self.newLoginHoldStatus() : self.userExtensionData().loginHoldStatus,
          loginPinstatus: self.newLoginPinStatus() ? self.newLoginPinStatus() : self.userExtensionData().loginPinstatus,
          loginHoldReason: self.newLoginHoldReason() ? self.newLoginHoldReason() : self.userExtensionData().loginHoldReason,
          userExtensionKey: self.username(),
          loginID: self.userExtensionData().loginID,
          loginPinReferenceNo: self.newLoginPinRefNo() ? self.newLoginPinRefNo() : self.userExtensionData().loginPinReferenceNo
        };

      ko.utils.extend(validationDataPayload, validationExtensionData);

      ko.utils.extend(validationDataPayload, {
        userDTO: validationData
      });

      return validationDataPayload;
    };

    self.setUserGroupList = function (data) {
      if (self.userType() === "corporateuser") {
        const updatedUserGroupList = [];

        //deleted elements
        self.userFullData().userGroupDTOs.forEach(function (element) {
          const index = self.selectedUserGroupValues().indexOf(element.id);

          if (index === -1) {
            updatedUserGroupList.push({
              id: element.id,
              action: "D"
            });
          } else {
            updatedUserGroupList.push({
              id: element.id,
              action: "N"
            });
          }

        });

        self.selectedUserGroupValues().forEach(function (element) {
          const index = self.userFullData().userGroupDTOs.map(function (e) {
            return e.id;
          }).indexOf(element);

          if (index === -1) {
            updatedUserGroupList.push({
              id: element,
              action: "A"
            });
          }
        });

        updatedUserGroupList.forEach(function (selectedGroup) {
          self.userGroupValues().forEach(function (userGroup) {
            if (userGroup.value === selectedGroup.id) {
              if (selectedGroup.action === "N") {
                data.userGroupDTOs.push(userGroup.object);
              } else if (selectedGroup.action === "D") {
                if (userGroup.object.users !== null && userGroup.object.users.length > 0) {
                  const pos = userGroup.object.users.map(function (e) {
                    return e.userId;
                  }).indexOf(data.username);

                  if (pos !== -1) {
                    userGroup.object.users.splice(pos, 1);

                  }

                }

                data.userGroupDTOs.push(userGroup.object);
              } else if (selectedGroup.action === "A") {
                if (userGroup.object.users !== null) {
                  userGroup.object.users.push({
                    userId: data.username
                  });
                }

                data.userGroupDTOs.push(userGroup.object);
              }
            }
          });

        });

        self.userFullData().userGroupDTOs = data.userGroupDTOs;
      }
    };

    let relationship_Type;

    self.setEntityRelatedFields = function (data) {
      if (self.userType() === "corporateuser") {
        relationship_Type = "O";
      }

      if (self.userType() === "retailuser") {
        relationship_Type = "I";
      }

      if (self.userType() !== "administrator") {
        data.partyId = {};

        data.partyId = {
          value: self.userFullData().partyId.value,
          displayValue: self.userFullData().partyId.displayValue
        };

        data.userPartyRelationshipDTOs = [];
        data.accessibleEntities = [];

        data.userPartyRelationshipDTOs.push({
          determinantValue: self.userFullData().homeEntity,
          partyId: {
            displayValue: self.userFullData().partyId.displayValue,
            value: self.userFullData().partyId.value
          },
          userId: data.username,
          relationshipType: relationship_Type
        });

        data.accessibleEntities.push({
          entityId: self.userFullData().homeEntity,
          entityName: null,
          partyName: self.userFullData().partyName,
          userPartyRelationship: {
            determinantValue: self.userFullData().homeEntity,
            partyId: {
              displayValue: self.userFullData().partyId.displayValue,
              value: self.userFullData().partyId.value
            },
            userId: data.username,
            relationshipType: relationship_Type
          }
        });

        for (let a = 0; a < self.accessibleEntityArray().length; a++) {
          data.userPartyRelationshipDTOs.push({
            determinantValue: self.accessibleEntityArray()[a].entityId() instanceof Array ? self.accessibleEntityArray()[a].entityId()[0] : self.accessibleEntityArray()[a].entityId(),
            partyId: {
              displayValue: self.accessibleEntityArray()[a].partyInfo.party.displayValue(),
              value: self.accessibleEntityArray()[a].partyInfo.party.value()
            },
            userId: data.username,
            relationshipType: relationship_Type
          });

          data.accessibleEntity.push(self.accessibleEntityArray()[a].entityId() instanceof Array ? self.accessibleEntityArray()[a].entityId()[0] : self.accessibleEntityArray()[a].entityId());

          data.accessibleEntities.push({
            entityId: self.accessibleEntityArray()[a].entityId() instanceof Array ? self.accessibleEntityArray()[a].entityId()[0] : self.accessibleEntityArray()[a].entityId(),
            entityName: self.accessibleEntityArray()[a].entityName(),
            partyName: self.accessibleEntityArray()[a].partyInfo.partyName(),
            userPartyRelationship: {
              determinantValue: self.accessibleEntityArray()[a].entityId() instanceof Array ? self.accessibleEntityArray()[a].entityId()[0] : self.accessibleEntityArray()[a].entityId(),
              partyId: {
                displayValue: self.accessibleEntityArray()[a].partyInfo.party.displayValue(),
                value: self.accessibleEntityArray()[a].partyInfo.party.value()
              },
              userId: data.username,
              relationshipType: relationship_Type
            }
          });
        }

        data.limitPackages = [];

        let tempLimitPackageMappingDTO;

        for (let i = 0; i < self.accessibleEntityArray().length; i++) {
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

        if (self.limitPackageDetails()) {
          const entityLimitPackageMappingDTO = ko.observableArray([]);

          for (let i = 0; i < self.limitPackageDetails().length; i++) {
            if (self.limitPackageDetails()[i].selectedLimitPackage()) {
              entityLimitPackageMappingDTO.push({
                limitPackage: {
                  accessPointGroupType: self.limitPackageDetails()[i].isGroup ? "GROUP" : "SINGLE",
                  accessPointValue: self.limitPackageDetails()[i].accessPoint,
                  key: {
                    id: self.limitPackageDetails()[i].selectedLimitPackage()
                  }
                }
              });
            }
          }

          data.limitPackages.push({
            targetUnit: self.userFullData().homeEntity,
            entityLimitPackageMappingDTO: entityLimitPackageMappingDTO()
          });
        }

        data.accessibleEntity.push(self.userFullData().homeEntity);
        data.homeEntity = self.userFullData().homeEntity;
        data.homeEntity = self.userFullData().homeEntity;

        if (!data.limitPackages.length) {
          data.limitPackages = null;
        }
      }

      if (self.userType() === "administrator") {
        data.accessibleEntity.push(self.userFullData().homeEntity);

        for (let i = 0; i < self.accessibleEntityArray().length; i++) {
          data.accessibleEntity.push(self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId());

          data.accessibleEntities.push({
            entityId: self.accessibleEntityArray()[i].entityId() instanceof Array ? self.accessibleEntityArray()[i].entityId()[0] : self.accessibleEntityArray()[i].entityId(),
            entityName: self.accessibleEntityArray()[i].entityName(),
            partyName: null,
            userPartyRelationship: null
          });
        }

        data.accessibleEntities.push({
          entityId: self.userFullData().homeEntity,
          entityName: null,
          partyName: self.userFullData().partyName,
          userPartyRelationship: null
        });

        data.homeEntity = self.userFullData().homeEntity;
      }
    };

    self.backonEditUser = function () {
      $("#backConfirmationModal").trigger("openModal");
    };

    self.back = function () {
      rootParams.dashboard.loadComponent("user-read", {
        username: self.username(),
        countries: self.countries()
      });
    };

    self.hideModal = function () {
      $("#backConfirmationModal").hide().trigger("closeModal");
    };

    self.backOnReview = function () {
      self.selectedUserLimitPackages.removeAll();

      if (self.limitPackageDetails()) {
        for (let i = 0; i < self.limitPackageDetails().length; i++) {
          if (self.limitPackageDetails()[i].selectedLimitPackage()) {
            self.selectedUserLimitPackages.push({
              key: {
                id: self.limitPackageDetails()[i].selectedLimitPackage()
              },
              accessPointValue: self.limitPackageDetails()[i].accessPoint
            });
          }
        }
      }

      self.byPassCodeVisible(false);
      self.updateReviewFlag(false);
    };

    self.entityList = ko.observableArray([]);
    self.entitiesListLoaded = ko.observable(false);
    self.rePopulateEntityList();

    self.populateAccessPointForPayload = function () {
      self.userPreferenceAccessPointRelationship([]);

      if (!self.selectedAccessPoint()) {
        self.selectedAccessPoint([]);
      }

      ko.utils.arrayForEach(self.selectedExtAccessPoint(), function (item) {
        if (item.accessPointId !== undefined) {
          self.userPreferenceAccessPointRelationship.push({
            accessPointId: item.accessPointId,
            userId: null,
            status: item.status,
            determinantValue: item.determinantValue
          });
        }
      });

      ko.utils.arrayForEach(self.accessPoint(), function (item) {
        if (self.selectedAccessPoint().indexOf(item.value) === -1) {
          self.userPreferenceAccessPointRelationship.push({
            accessPointId: item.value,
            userId: null,
            status: false,
            determinantValue: self.userFullData().homeEntity
          });
        } else {
          self.userPreferenceAccessPointRelationship.push({
            accessPointId: item.value,
            userId: null,
            status: true,
            determinantValue: self.userFullData().homeEntity
          });
        }
      });

      ko.utils.arrayForEach(self.accessibleEntityArray(), function (item) {
        let tempAccessPoint = item.selectedAccessPoints;

        if (!item.selectedAccessPoints) {
          tempAccessPoint = [];
        }

        ko.utils.arrayForEach(tempAccessPoint, function (selectedAccessPointItem) {
          self.userPreferenceAccessPointRelationship.push({
            accessPointId: selectedAccessPointItem,
            userId: null,
            status: true,
            determinantValue: item.entityId()
          });
        });

        ko.utils.arrayForEach(item.accessPointList, function (item1) {
          if (tempAccessPoint.indexOf(item1.value) === -1) {
            self.userPreferenceAccessPointRelationship.push({
              accessPointId: item1.value,
              userId: null,
              status: false,
              determinantValue: item.entityId()
            });
          }
        });
      });
    };

    self.updateUser = function () {
      if (!rootParams.baseModel.showComponentValidationErrors(document.getElementById("tracker"))) {
        return;
      }

      if (self.companyActivityLog()[0] === "true") {
          if ((self.selectedChildRole().length === 1 && (self.selectedChildRole()[0] === "Viewer_TL" || self.selectedChildRole()[0] === "Migrated_SYSADM_TL"))
          || (self.selectedChildRole().length === 2 && ((self.selectedChildRole()[0] === "Viewer_TL" && self.selectedChildRole()[1] === "Migrated_SYSADM_TL")) || (self.selectedChildRole()[0] === "Migrated_SYSADM_TL" && self.selectedChildRole()[1] === "Viewer_TL"))) {
              rootParams.baseModel.showMessages(null, [self.nls.info.noOtherRole], "ERROR");

              return;
          }
      }

      self.populateAccessPointForPayload();

      if (self.selectedChildRole().indexOf(self.userFullData().userType.enterpriseRoleId) !== -1) {
        const index = self.selectedChildRole().indexOf(self.userFullData().userType.enterpriseRoleId);

        self.selectedChildRole().splice(index, 1);
      }

      if (typeof self.userFullData().title === "object") {
        self.userFullData().title = self.userFullData().title[0];
      }

      if (typeof self.userFullData().address.country === "object") {
        self.userFullData().address.country = self.userFullData().address.country[0];
      }

      const data = {
        username: self.userFullData().username ? self.userFullData().username : "",
        firstName: self.userFullData().firstName ? self.userFullData().firstName : "",
        middleName: self.userFullData().middleName ? self.userFullData().middleName : "",
        lastName: self.userFullData().lastName ? self.userFullData().lastName : "",
        dateOfBirth: self.userFullData().dateOfBirth ? self.userFullData().dateOfBirth : "",
        mobileNumber: self.userFullData().mobileNumber ? self.userFullData().mobileNumber : self.isPersonalInfoMandatory() ? "" : "00000000",
        emailId: self.userFullData().emailId !== "" ? self.userFullData().emailId.toLowerCase() : self.isPersonalInfoMandatory() ? "" : "dummy@dummy.dummy",
        employeeType: self.userFullData().employeeType ? self.userFullData().employeeType : "",
        phoneNumber: self.userFullData().phoneNumber ? self.userFullData().phoneNumber : "",
        userType: self.userFullData().userType ? self.userFullData().userType.enterpriseRoleId : "",
        address: {
          line1: self.userFullData().address.line1 ? self.userFullData().address.line1 : "",
          line2: self.userFullData().address.line2 ? self.userFullData().address.line2 : "",
          line3: self.userFullData().address.line3 ? self.userFullData().address.line3 : "",
          line4: self.userFullData().address.line4 ? self.userFullData().address.line4 : "",
          city: self.userFullData().address.city ? self.userFullData().address.city : "",
          state: self.userFullData().address.state ? self.userFullData().address.state : "",
          country: self.userFullData().address.country ? self.userFullData().address.country : "",
          zipCode: self.userFullData().address.zipCode ? self.userFullData().address.zipCode : ""
        },
        partyId: {},
        homeEntity: null,
        version: self.userFullData().version,
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
        partyName: self.userFullData().partyName ? self.userFullData().partyName : "",
        userGroups: [self.userFullData().userType.enterpriseRoleId],
        title: self.userFullData().title ? self.userFullData().title : "",
        organization: self.userFullData().organization ? self.userFullData().organization : "",
        manager: self.userFullData().manager ? self.userFullData().manager : "",
        employeeNumber: self.userFullData().employeeNumber ? self.userFullData().employeeNumber : "",
        deviceDeregistrationListOS: self.selectedDeviceList().length !== 0 ? self.selectedDeviceList() : [],
        pushTokenDegistrationListOS: self.selectedDeviceListForPushNotification().length !== 0 ? self.selectedDeviceListForPushNotification() : [],
        userAccessPointRelationshipList: self.userPreferenceAccessPointRelationship(),
        applicationRoles: self.selectedChildRole(),
        segmentCode: self.selectedSegmentCode(),
        userGroupDTOs: []
      },
        extensionData = {
          signerPinReferenceNo: self.newSignerPinRefNo() ? self.newSignerPinRefNo() : self.userExtensionData().signerPinReferenceNo,
          documentType: self.userExtensionData().documentType,
          cdcNo: self.userExtensionData().cdcNo,
          userID: self.username(),
          mobileNo: self.userExtensionData().mobileNo,
          mobileCode: self.userExtensionData().mobileCode,
          loginHoldStatus: self.newLoginHoldStatus() ? self.newLoginHoldStatus() : self.userExtensionData().loginHoldStatus,
          signerHoldStatus: self.newSignerHoldStatus() ? self.newSignerHoldStatus() : self.userExtensionData().signerHoldStatus,
          isAuthorisedPerson: self.isAuthorizedChecked()[0] === "true",
          companyActivityLog: self.companyActivityLog()[0] === "true",
          loginPinstatus: self.newLoginPinStatus() ? self.newLoginPinStatus() : self.userExtensionData().loginPinstatus,
          loginHoldReason: self.newLoginHoldReason() ? self.newLoginHoldReason() : self.userExtensionData().loginHoldReason,
          userExtensionKey: self.username(),
          signerID: self.newSignerId() ? self.newSignerId() : self.userExtensionData().signerID,
          documentID: self.userExtensionData().documentID,
          documentCountry: self.userExtensionData().documentCountry,
          loginID: self.userExtensionData().loginID,
          loginPinReferenceNo: self.newLoginPinRefNo() ? self.newLoginPinRefNo() : self.userExtensionData().loginPinReferenceNo,
          signerHoldReason: self.newSignerHoldReason() ? self.newSignerHoldReason() : self.userExtensionData().signerHoldReason,
          signerPinstatus: self.newSignerPinStatus() ? self.newSignerPinStatus() : self.userExtensionData().signerPinstatus,
          idDocSubmitted: self.showIdDocCheckbox(),
          migtationStatus: self.userExtensionData().migtationStatus,
          defaultUser: self.userExtensionData().defaultUser,
          bypassFlag: self.bypassFlag() ? "Y" : "N",
          bypassCode: self.bypassCode(),
          version: self.userExtensionData().version,
          userChannelType: self.userExtensionData().userChannelType,
          hthApiPasswordCodeId: self.hthApiPasswordPendingCodeId(),
          dictionaryArray: [{
            nameValuePairDTOArray: [{
              name: "signerDeleteFlag",
              genericName: "signerDeleteFlag",
              value: self.isSignerIdDeleted()
            },
            {
              name: "loginPinReferenceNo",
              genericName: "loginPinReferenceNo",
              value: self.userExtensionData().loginPinReferenceNo
            },
            {
              name: "loginPinstatus",
              genericName: "loginPinstatus",
              value: self.userExtensionData().loginPinstatus
            },
            {
              name: "loginHoldStatus",
              genericName: "loginHoldStatus",
              value: self.userExtensionData().loginHoldStatus
            },
            {
              name: "loginHoldReason",
              genericName: "loginHoldReason",
              value: self.userExtensionData().loginHoldReason
            },
            {
              name: "signerPinReferenceNo",
              genericName: "signerPinReferenceNo",
              value: self.userExtensionData().signerPinReferenceNo
            },
            {
              name: "signerPinstatus",
              genericName: "signerPinstatus",
              value: self.userExtensionData().signerPinstatus
            },
            {
              name: "signerHoldStatus",
              genericName: "signerHoldStatus",
              value: self.userExtensionData().signerHoldStatus
            },
            {
              name: "signerHoldReason",
              genericName: "signerHoldReason",
              value: self.userExtensionData().signerHoldReason
            }
            ]
          }]
        };

      self.setEntityRelatedFields(data);
      self.setUserGroupList(data);
      self.testMethod(data, extensionData);
    };

    /**
     * @function closeuserGroupNotification
     * This function closes UserGroup Reject warning modal window.
     */
    self.closeuserGroupNotification = function () {
      $("#userGroupRejectNotification").trigger("closeModal");
    };

    self.cancel = function () {
      rootParams.dashboard.switchModule(true);
    };

    self.successHandler = function (data, status, jqXhr) {
      self.updateReviewFlag(false);
      self.updateConfirmFlag(true);
      self.updateReviewFlag(false);

      rootParams.dashboard.loadComponent("confirm-screen", {
        jqXHR: jqXhr,
        transactionName: self.nls.info.editTxnName,
        transactionResponse: data
      });
    };

    self.testMethod = function (data, extensionData) {
      if (!isModified) {

        const payload = {};

        ko.utils.extend(payload, extensionData);

        ko.utils.extend(payload, {
          userDTO: data
        });

        const updateSigner = {
          userId: extensionData.signerID,
          signerId: extensionData.signerID,
          hkID: extensionData.documentID,
          hkIDCheckDigit: extensionData.documentID,
          docTypeCode: extensionData.documentType,
          documentCountry: extensionData.documentCountry,
          mpfSignFlag: "N",
          ldsSignFlag: "N",
          scsSignFlag: "N",
          caSignFlag: "N",
          pinReferenceNo: extensionData.signerPinReferenceNo,
          saqSignFlag: "N",
          holdCode: extensionData.signerHoldStatus,
          holdReason: extensionData.signerHoldReason,
          pinStatus: extensionData.signerPinstatus,
          pinChangeFlag: self.newSignerPinRefNo() ? "Y" : "N",
          subOption: "01",
          cdcId: extensionData.cdcNo,
          authPersonFlag: extensionData.isAuthorisedPerson ? "Y" : "N"
          /*TODO: similarly we need to get migration status
          migtationStatus: extensionData.migtationStatus,
          defaultUser: extensionData.defaultUser*/
        },
          updateLoginId = {
            loginId: extensionData.loginID,
            holdCode: extensionData.loginHoldStatus,
            holdReason: extensionData.loginHoldReason,
            pinStatus: extensionData.loginPinstatus,
            pinReferenceNo: extensionData.loginPinReferenceNo,
            subOption: "01",
            pinChangeFlag: self.newLoginPinRefNo() ? "Y" : "N",
            cdcId: extensionData.cdcNo
            /*TODO: similarly we need to get migration status and defaultUser
            migtationStatus: extensionData.migtationStatus,
            defaultUser: extensionData.defaultUser*/
          },
          addLogin = {
            idType: "02",
            /*Check party
            party: self.party,*/
            party: {
              value: data.partyId.value,
              displayValue: data.partyId.displayValue
            },
            ids: [{
              /*details entered from screen */
              id: extensionData.loginID,
              pinRefNo: self.newLoginPinRefNo(),
              pinStatus: self.newLoginPinStatus(),
              holdStatus: self.newLoginHoldStatus(),
              holdReason: self.newLoginHoldReason()
            }],
            subOption: "01",
            idKey: {
              partyId: data.partyId.value,
              id: extensionData.loginID
            }
          };

        self.isFromWHP = function () {

          let isFromWHPFlag = false;

          if (self.userExtensionData().migtationStatus === "N"
            && self.userExtensionData().loginPinstatus === "Inactive"
            && self.userExtensionData().loginHoldStatus === "No Activity Allowed"
            && self.userExtensionData().loginHoldReason === "Requested by Customer") {
            /* For the migrated user, but not yet onboarded */
            isFromWHPFlag = true;
          }

          return isFromWHPFlag;
        };

        self.isUserEligibleForLoginUpdate = function () {

          let isUserEligible = false;

          if ((self.userExtensionData().migtationStatus === "N"
            && (self.userExtensionData().defaultUser === "Y" || self.userExtensionData().defaultUser === "S"))
            && (self.newLoginPinStatus() || self.newLoginHoldStatus() || self.newLoginHoldReason() || self.newLoginHoldReason())) {
            /*In the case of default User*/
            isUserEligible = true;
          } else if (self.userExtensionData().migtationStatus !== "N" && (self.newLoginPinStatus() || self.newLoginHoldStatus() || self.newLoginHoldReason() || self.newLoginHoldReason())) {
            /*In the case of Normal User*/
            isUserEligible = true;
          } else if (self.isFromWHP()) {
            isUserEligible = true;
          }

          return isUserEligible;

        };

        serviceExtension.fetchBMTransactionNo("MT_N_UUS_SIGNER~MT_N_UUS_LOGIN~MT_N_CUS~UM_N_USD_DES01~UM_ID_MC_ALU01").then(function (BMTxnRef) {
          Promise.all([
            self.evaluateSignerAction() === "create" ? UsersUpdateModel.addSigner("01", BMTxnRef.MT_N_CUS, ko.toJSON(updateSigner)) : self.evaluateSignerAction() === "update" ? UsersUpdateModel.updateSigner(BMTxnRef.MT_N_UUS_SIGNER, ko.toJSON(updateSigner)) : "",
            self.isUserEligibleForLoginUpdate() ? extensionData.loginID ? UsersUpdateModel.updateLoginId(BMTxnRef.MT_N_UUS_LOGIN, ko.toJSON(updateLoginId)) : "" : "",
            self.isSignerIdDeleted() ? UsersUpdateModel.deleteSignerId(self.deletedSignerId(), extensionData.cdcNo, BMTxnRef.UM_N_USD_DES01) : "",
            payload.loginID && !self.isCorpAdmin && self.newLoginPinRefNo() !== null && self.newLoginPinStatus() === "Being Reset" && self.userExtensionData().migtationStatus === "N" && self.userExtensionData().defaultUser !== "Y" && self.userExtensionData().defaultUser !== "S" && !self.isFromWHP() ? UsersUpdateModel.addUserIdMaintenance(BMTxnRef.UM_ID_MC_ALU01, ko.toJSON(addLogin)) : ""
          ]).then(function () {

            const BMTxnRefToSent = {
              bmTxnData: [{
                txnRefNo: BMTxnRef.MT_N_UUS_SIGNER,
                txnCode: "MT_N_UUS_SIGNER"
              }, {
                txnRefNo: BMTxnRef.MT_N_UUS_LOGIN,
                txnCode: "MT_N_UUS_LOGIN"
              }, {
                txnRefNo: BMTxnRef.MT_N_CUS,
                txnCode: "MT_N_CUS"
              }, {
                txnRefNo: BMTxnRef.UM_N_USD_DES01,
                txnCode: "UM_N_USD_DES01"
              }, {
                txnRefNo: BMTxnRef.UM_ID_MC_ALU01,
                txnCode: "UM_ID_MC_ALU01"
              }]
            };

            /*
            //Platform.getInstance("authentication").then(function (platform) {
              platform("getCCBPublicKey").then(function (data) {

                //eslint-disable-next-line no-undef
                RSAUtil.encryptPassword(data.publicKey, self.username().split("@")[0], function (username) {
                  payload.userIDEncrypted = username;

                  //eslint-disable-next-line no-undef
                  RSAUtil.encryptPassword(data.publicKey, self.username().split("@")[1], function (cdcId) {
                    payload.cdcNoEncrypted = cdcId;
                    payload.keyIndicator = data.key_indicator;

                    UsersUpdateModel.updateUserExtension(ko.toJSON(payload), self.username(), ko.toJSON(BMTxnRefToSent), self.successHandler);

                  });
                });
              });
            });
            */
            payload.userIDEncrypted = self.username().split("@")[0];
            payload.cdcNoEncrypted = self.username().split("@")[1];

            const userTokenDataDTO = self.userTokenDataDTO() || {};

            ko.utils.extend(payload, { userTokenDataDTO: userTokenDataDTO });

            payload.itokenStatus = !self.isBM() && self.iTokenStatus() === "Inactive" && self.userTokenDataDTO().itokenStatus === "Activated" ? self.iTokenStatus() : self.isBM() && (self.userTokenDataDTO().itokenStatus === "Locked" || self.userTokenDataDTO().itokenStatus === "Activated") && self.iTokenStatus() === "Inactive" ? self.iTokenStatus() : null;

            UsersUpdateModel.updateUserExtension(ko.toJSON(payload), self.username() ? self.username().toUpperCase() : self.username(), ko.toJSON(BMTxnRefToSent), self.successHandler);

          });
        });
      } else {
        UsersUpdateModel.updateUserModify(ko.toJSON(data), self.userFullData().username, rootParams.rootModel.params.transactionId).done(function (data, status, jqXhr) {
          self.updateReviewFlag(false);
          self.updateConfirmFlag(true);
          self.updateReviewFlag(false);

          rootParams.dashboard.loadComponent("confirm-screen", {
            jqXHR: jqXhr,
            transactionName: self.transactionName(),
            transactionResponse: data
          });

        });
      }
    };

    /**
     * @function evaluateSignerAction
     * This function evaluates if Signer update or create API call required.
     * @returns update, create or nothing
     */
    self.evaluateSignerAction = function () {
      if ((self.isAuthorizedChecked()[0] === "true" || self.newSignerPinStatus() || self.newSignerHoldStatus() || self.newSignerHoldReason()) && self.userExtensionData().signerID) {
        return "update";
      } else if (self.newSignerPinStatus() || self.newSignerHoldStatus() || self.newSignerHoldReason() || self.newSignerId()) {
        return "create";
      }

      return "nothing";

    };

    if (!isModified) {
      self.userType = ko.observable(self.userFullData().userType.enterpriseRoleId);
    } else {
      self.userType = ko.observable(rootParams.rootModel.params.data.userType);
    }

    self.refresh = ko.observable(true);
    self.entityList = ko.observableArray([]);
    self.entitiesListLoaded = ko.observable(false);

    self.filterEntityList = function () {
      let entityListTemp = self.entityList();

      if (self.accessibleEntityArray().length !== 0) {
        entityListTemp = $.map(entityListTemp, function (node) {
          let flag = false;

          for (let g = 0; g < self.accessibleEntityArray().length; g++) {
            if ((self.accessibleEntityArray()[g].entityId() instanceof Array ? self.accessibleEntityArray()[g].entityId()[0] : self.accessibleEntityArray()[g].entityId()) === node.entityId) {
              flag = true;
              break;
            }
          }

          if (flag === false) {
            return node;
          }
        });
      }

      self.entityList(entityListTemp);
    };

    self.addAccessibleEntity = function (arg) {
      arg = arg ? arg : [];

      if ((arg.entityId === null || arg.entityId === undefined) && arg.length === 0) {
        self.filterEntityList();
      }

      self.partyInfo = {
        partyFirstName: ko.observable(),
        partyLastName: ko.observable(),
        userType: "CUSTOMER",
        partyName: arg.partyName ? ko.observable(arg.partyName) : ko.observable(),
        partyDetailsFetched: ko.observable(true),
        additionalDetails: ko.observable(),
        userTypeLabel: ko.observable(),
        party: {
          value: arg.value ? ko.observable(arg.value) : ko.observable(),
          displayValue: arg.displayValue ? ko.observable(arg.displayValue) : ko.observable()
        }
      };

      let userType = "";

      if (!isModified) {
        userType = self.userType();
      } else {
        userType = rootParams.rootModel.params.data.userType;
        self.userType = ko.observable(rootParams.rootModel.params.data.userType);
      }

      self.accessibleEntityArray.push({
        entityId: arg.entityId ? ko.observable(arg.entityId) : ko.observable(),
        entityName: arg.entityName ? ko.observable(arg.entityName) : ko.observable(),
        entityList: self.entityList,
        partyInfo: self.partyInfo,
        limitPackage: arg.limitPackage ? ko.observableArray(arg.limitPackage) : ko.observableArray(),
        childRoles: ko.observableArray([]),
        entityChange: arg.entityId ? ko.observable(true) : ko.observable(false),
        entityUserLimitListLoaded: ko.observable(false),
        entityUserLimit: ko.observable(),
        userType: userType,
        limitPackageName: arg.limitPackageName ? ko.observable(arg.limitPackageName) : ko.observable(),
        entitiesListLoaded: self.entitiesListLoaded,
        accessibleEntitySet: arg.entityId ? ko.observable(true) : ko.observable(false),
        accessPointList: self.accessPoint(),
        isLimitPackageAttached: ko.observable(false),
        selectedAccessPoints: arg.selectedAccessPoints
      });
    };

    self.prepareAccessibleEntity = function () {
      const arg = [];

      if (!isModified) {
        this.homeEntity = self.userFullData().homeEntity;

        if (self.userType() !== "administrator") {
          let req_array = self.userFullData().accessibleEntities;

          req_array = $.map(req_array, function (node) {
            node.limitPackage = [];

            if (self.userFullData().limitPackages) {
              for (let g = 0; g < self.userFullData().limitPackages.length; g++) {
                if (self.userFullData().limitPackages[g].targetUnit === node.entityId) {
                  for (let m = 0; m < self.userFullData().limitPackages[g].entityLimitPackageMappingDTO.length; m++) {
                    node.limitPackage.push({
                      limitPackage: {
                        key: {
                          id: self.userFullData().limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.key.id
                        }
                      },
                      accessPointValue: self.userFullData().limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.accessPointValue,
                      accessPointGroupType: self.userFullData().limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.accessPointGroupType
                    });

                    node.limitPackageName = self.userFullData().limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.key.id;
                  }
                }
              }
            }

            return node;
          });

          for (let k = 0; k < req_array.length; k++) {
            if (req_array[k].entityId !== this.homeEntity) {
              arg.entityId = req_array[k].entityId;
              arg.entityName = req_array[k].entityName;
              arg.displayValue = req_array[k].userPartyRelationship.partyId.displayValue;
              arg.value = req_array[k].userPartyRelationship.partyId.value;
              arg.limitPackage = req_array[k].limitPackage;
              arg.limitPackageName = req_array[k].limitPackageName;
              arg.partyName = req_array[k].partyName;
              arg.selectedAccessPoints = req_array[k].selectedAccessPoints;
              self.addAccessibleEntity(arg);
            }
          }
        } else {
          for (let index = 0; index < self.userFullData().accessibleEntities.length; index++) {
            if (self.userFullData().accessibleEntities[index].entityId !== this.homeEntity) {
              arg.entityId = self.userFullData().accessibleEntities[index].entityId;
              arg.entityName = self.userFullData().accessibleEntities[index].entityName;
              arg.selectedAccessPoints = self.userFullData().accessibleEntities[index].selectedAccessPoints;
              self.addAccessibleEntity(arg);
            }
          }
        }
      } else {
        this.homeEntity = rootParams.rootModel.params.data.homeEntity;

        if (rootParams.rootModel.params.data.userType !== "administrator") {
          let req_array = rootParams.rootModel.params.data.accessibleEntities;

          req_array = $.map(req_array, function (node) {
            node.limitPackage = [];

            if (rootParams.rootModel.params.data.limitPackages) {
              for (let g = 0; g < rootParams.rootModel.params.data.limitPackages.length; g++) {
                if (rootParams.rootModel.params.data.limitPackages[g].targetUnit === node.entityId) {
                  for (let m = 0; m < rootParams.rootModel.params.data.limitPackages[g].entityLimitPackageMappingDTO.length; m++) {
                    node.limitPackage.push({
                      limitPackage: {
                        key: {
                          id: rootParams.rootModel.params.data.limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.key.id
                        }
                      },
                      accessPointValue: rootParams.rootModel.params.data.limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.accessPointValue,
                      accessPointGroupType: rootParams.rootModel.params.data.limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.accessPointGroupType
                    });

                    node.limitPackageName = rootParams.rootModel.params.data.limitPackages[g].entityLimitPackageMappingDTO[m].limitPackage.key.id;
                  }
                }
              }
            }

            return node;
          });

          for (let k = 0; k < req_array.length; k++) {
            if (req_array[k].entityId !== this.homeEntity) {
              arg.entityId = req_array[k].entityId;
              arg.entityName = req_array[k].entityName;
              arg.displayValue = req_array[k].userPartyRelationship.partyId.displayValue;
              arg.value = req_array[k].userPartyRelationship.partyId.value;
              arg.limitPackage = req_array[k].limitPackage;
              arg.limitPackageName = req_array[k].limitPackageName;
              arg.partyName = req_array[k].partyName;
              arg.selectedAccessPoints = req_array[k].selectedAccessPoints;
              self.addAccessibleEntity(arg);
            }

          }

          for (let index = 0; index < rootParams.rootModel.params.data.accessibleEntities.length; index++) {
            if (rootParams.rootModel.params.data.accessibleEntities[index].entityId !== this.homeEntity) {
              arg.entityId = rootParams.rootModel.params.data.accessibleEntities[index].entityId;
              arg.entityName = rootParams.rootModel.params.data.accessibleEntities[index].entityName;
              arg.selectedAccessPoints = rootParams.rootModel.params.data.accessibleEntities[index].selectedAccessPoints;
              self.addAccessibleEntity(arg);
            }
          }
        }
      }
    };

    const entityNameList = [];

    for (let c = 0; c < rootParams.dashboard.userData.userProfile.accessibleEntityDTOs.length; c++) {
      entityNameList[rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[c].entityId] = rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[c].entityName;
    }

    self.setEntityList = function () {
      const array = [];

      for (let i = 0; i < rootParams.dashboard.userData.userProfile.accessibleEntityDTOs.length; i++) {
        array.push(rootParams.dashboard.userData.userProfile.accessibleEntityDTOs[i]);
      }

      let index = null;

      for (let j = 0; j < array.length; j++) {
        if (array[j].entityId === this.homeEntity) {
          index = j;
        }
      }

      array.splice(index, 1);
      self.entityList(array.slice(0));
      self.prepareAccessibleEntity();
      self.entitiesListLoaded(true);
    };

    self.setEntityList();

    self.deleteAccessibleEntity = function (index) {
      if (self.accessibleEntityArray()[index].entityId() instanceof Array) {
        self.entityList().push({
          entityId: self.accessibleEntityArray()[index].entityId()[0],
          entityName: entityNameList[self.accessibleEntityArray()[index].entityId()[0]]
        });
      } else if (self.accessibleEntityArray()[index].entityId()) {
        self.filterEntityList();

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

    self.disableEntity = function () {
      if (self.accessibleEntityArray().length === 0) {
        return false;
      }

      return !self.accessibleEntityArray()[self.accessibleEntityArray().length - 1].accessibleEntitySet();
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

    self.bypassFlagChanged = function(event) {
      if (self.isPendingConfirmBypassStatus()) {
        return;
      }

      if (event.detail.value) {
        if (!self.userExtensionData().documentID || !self.userExtensionData().documentType || !self.userExtensionData().documentCountry) {
          rootParams.baseModel.showMessages(null, [self.nls.info.DOCUMENT_ID_EMPTY], "ERROR");

          return;
        }

        if (self.userExtensionData().bypassFlag === "Y" && self.userExtensionData().bypassExpiryTime) {
          self.bypassExpiryTime("");
        }

        self.bypassCode(Math.floor(Math.random() * 1000000).toString().padStart(6, "0"));
        self.byPassCodeSwitchIconVisible(true);
        $("#loginPINResetCodeReminder").trigger("openModal");
      } else {
        self.isPendingConfirmBypassStatus(true);

        setTimeout(() => {
          self.bypassFlag(true);
        }, 0);

        $("#confirmDisableLoginPINResetCodeStatusReminder").trigger("openModal");
      }
    };

    self.getBypassSwitchDisabled = function() {
      return (!self.isCorpAdmin && !self.bypassFlag()) || self.hasDisabledBypassStatus();
    };

    self.confirmDisableLoginPINResetCodeStatus = function() {
      self.bypassCode("");
      self.bypassFlag(false);
      self.hasDisabledBypassStatus(true);

      $("#confirmDisableLoginPINResetCodeStatusReminder").trigger("closeModal");
    };

    self.cancelDisableLoginPINResetCodeStatus = function() {
      setTimeout(() => {
        self.isPendingConfirmBypassStatus(false);
      }, 100);

      $("#confirmDisableLoginPINResetCodeStatusReminder").trigger("closeModal");
    };

    self.closeLoginPINResetCodeReminder = function() {
      $("#loginPINResetCodeReminder").trigger("closeModal");
    };

    self.getBypassFlagSwitchLabel = function() {
      if (self.bypassFlag() && self.bypassExpiryTime()) {
        return rootParams.baseModel.format(self.nls.fieldname.loginPINResetCodeEnabled, {date: rootParams.baseModel.formatDate(self.bypassExpiryTime(), "headerTimeFormat")});
      } else if (self.bypassFlag()) {
        return self.nls.fieldname.enable;
      }

      return self.nls.fieldname.loginPINResetCodeDisabled;
    };

    self.toggleByPassCodeVisible = function() {
      self.byPassCodeVisible(!self.byPassCodeVisible());
    };

    /**
     * BCOH2H-787: Re-generate HTH API Password Code (update page).
     * Creates a PENDING draft; the previous approved Code stays valid until approval.
     */
    self.regenerateHthApiPasswordCode = function () {
      const partyId = self.userFullData().partyId.value,
       userName = self.userFullData().username;

      UsersUpdateModel.generateHthApiPasswordCode(partyId, userName).done(function (data) {
        if (data.status && data.status.result === "SUCCESSFUL") {
          self.hthApiPasswordPendingCodeId(data.codeId);
          self.hthApiPasswordCodeId(data.codeId);
          self.hthApiPasswordCodeVisible(false);
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
     * BCOH2H-787: Toggle HTH API Password Code visibility (eye icon).
     * First reveal calls /reveal (audit logged), subsequent toggles are local.
     */
    self.toggleHthApiPasswordCodeVisible = function () {
      if (!self.hthApiPasswordCodeVisible() && self.hthApiPasswordCodeId()) {
        UsersUpdateModel.revealHthApiPasswordCode(self.hthApiPasswordCodeId()).done(function (data) {
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
     * BCOH2H-787: Close HTH API Password Code reminder modal.
     */
    self.closeHthApiPasswordCodeReminder = function () {
      $("#hthApiPasswordCodeReminder").trigger("closeModal");
    };

    self.dispose = function () {
      androidDeviceSubscription.dispose();
      iosDeviceSubscription.dispose();
      androidDeviceForPushNotificationSubscription.dispose();
      iosDeviceForPushNotificationSubscription.dispose();
      newLoginPinRefNo.dispose();
      newSignerPinRefNo.dispose();
    };

  };
});
