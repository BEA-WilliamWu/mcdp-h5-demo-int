define([
  "knockout",
  "ojL10n!resources/nls/side-menu",
  "extensions/components/host-to-host/api-password/user-context",
  "extensions/components/host-to-host/api-password/model",
  "ojL10n!extensions/resources/nls/hth-api-password",
  "ojs/ojarraydataprovider",
  "ojs/ojnavigationlist",
  "ojs/ojbutton"
], function (ko, ResourceBundle, HthUserContext, HthPasswordModel, HthPasswordResource, ArrayDataProvider) {
  "use strict";

  return function (rootParams) {
    const self = this;

    ko.utils.extend(self, rootParams.rootModel);
    self.resource = ResourceBundle;

    let disposed = false,
      initialized = false;

    self.selectedSideMenuItem = ko.observable();
    self.menuSelectionForSideMenu = ko.observable();
    rootParams.dashboard.headerName(self.resource.header);
    rootParams.baseModel.registerComponent("profile", "base-components");

    if (rootParams.dashboard.userData.userProfile.roles.indexOf("Merchant_TL") !== -1) {
      self.sideMenuList = [{
        id: "MyProfile",
        component: "profile",
        module: "base-components"
      }, {
        id: "securityAndLogin",
        component: "security-menu",
        module: "security"
      }];
    }
    else {
      self.sideMenuList = [{
        id: "MyProfile",
        component: "profile",
        module: "base-components"
      }, {
        id: "alerts",
        component: "alerts-list",
        module: "alerts"
      }, {
        id: "securityAndLogin",
        component: "security-menu",
        module: "security"
      }];
    }

    self.sideMenuList = ko.observableArray(self.sideMenuList);
    self.sideMenuDataProvider = new ArrayDataProvider(self.sideMenuList, { keyAttributes: "id" });

    /** Show the Profile entry for an existing credential; validate its Code on submission. */
    if (HthUserContext.isHthUser(rootParams.dashboard.userData.userProfile)) {
      Promise.resolve().then(function () {
        return HthPasswordModel.status();
      }).then(function (data) {
        if (!disposed && data && String(data.setupState).toUpperCase() === "ACTIVE") {
          self.sideMenuList.push({
            id: "hthApiPassword",
            component: "api-password",
            module: "host-to-host",
            label: HthPasswordResource.menuLabel,
            data: { mode: "RESET" }
          });
        }
      }).catch(function () {
        return null;
      });
    }

    self.getRootContext = function ($root) {
      if (initialized) {
        return;
      }

      initialized = true;

      if (rootParams.rootModel.params && rootParams.rootModel.params.switchToForgotSignerPIN) {
        self.selectedSideMenuItem("securityAndLogin");
      } else if (!rootParams.baseModel.small()) {
        if ($root.queryMap && rootParams.rootModel.previousState && rootParams.rootModel.previousState.flowName && rootParams.rootModel.previousState.flowName==="edit-user-security-question") {
            self.selectedSideMenuItem("securityAndLogin");
          }
          else {
          self.selectedSideMenuItem("MyProfile");
        }
      }
    };

    const selectionSubscription = self.selectedSideMenuItem.subscribe(function (newValue) {
      if (newValue) {
        let selectedItem = {};

        selectedItem = self.sideMenuList().find(function (item) {
          return item.id === newValue;
        });

        if (!selectedItem) {
          return;
        }

        rootParams.baseModel.registerComponent(selectedItem.component, selectedItem.module);

        if (!rootParams.baseModel.small()) {
          self.showDetailParams = selectedItem.data || {};

          if (selectedItem.id === "hthApiPassword") {
            rootParams.dashboard.headerName(HthPasswordResource.profileHeader);

            self.showDetailParams = Object.assign({}, selectedItem.data, {
              embedded: true,
              onCancel: function () { self.selectedSideMenuItem("MyProfile"); }
            });
          }

          self.menuSelectionForSideMenu(selectedItem.component);
        } else {
          rootParams.dashboard.loadComponent(selectedItem.component, selectedItem.data || {});
        }
      }
    });

    self.dispose = function () {
      disposed = true;
      selectionSubscription.dispose();
    };
  };
});