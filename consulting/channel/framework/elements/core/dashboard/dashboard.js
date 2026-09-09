define([
  "ojs/ojcore",
  "knockout",
  "jquery",
  "./model",
  "platform",
  "framework/js/configurations/config",
  "ojL10n!extensions/resources/nls/dashboard",
  "ojs/ojrouter",
  "framework/js/navigation/dashboard-context",
  "framework/js/plugins/navigation",
  "ojs/ojoffcanvas",
  "ojs/ojcontext",
  "extensions/override/extensions",
  "load!framework/js/chatbot/chatbot-components.json",
  "framework/js/chatbot/chatbot-functions",
  "ojs/ojradioset",
  "framework/elements/core/header/loader",
  "framework/elements/core/menu/loader",
  "framework/elements/core/dashboard-container/loader",
  "framework/elements/core/offline-notification/loader",
  "framework/elements/core/docked-menu/loader",
  "framework/elements/core/footer/loader",
  "framework/elements/api/modal-window/loader"
], function (
  oj,
  ko,
  $,
  DashboardModel,
  Platform,
  Configurations,
  locale,
  Router,
  DashboardContext,
  Navigation,
  OffcanvasUtils,
  Context,
  ExtensionsOverride,
  ChatbotComponentMapping,
  ChatBotFunctions
) {
  "use strict";

  require([
    "framework/elements/api/manage-accounts/loader",
    "framework/elements/api/page-section/loader",
    "framework/elements/api/row/loader",
    "ojs/ojarraytabledatasource",
    "ojs/ojpagingtabledatasource"
  ]);

  Router.defaults.baseUrl = window.location.pathname;
  Router.defaults.urlAdapter = new Router.urlParamAdapter();
  Router.defaults.rootInstanceName = "page";

  const router = Router.rootInstance,
    routerData = {},
    componentsWithStates = {
      "manage-accounts": function (params) {
        return params.defaultTab;
      },
      flow: function (params) {
        return params.flowName;
      }
    };

  routerData.home = {
    dashboard: null,
    isDashboard: true
  };

  function changeLoaderState(state) {
    if (["start", "stop"].indexOf(state) === -1) {
      return;
    }

    if (state === "start" && !$("body").hasClass("page-is-changing")) {
      $("body").addClass("page-is-changing");
    } else if (state === "stop" && $("body").hasClass("page-is-changing")) {
      $("body").removeClass("page-is-changing");
    }
  }

  function computeState(state, params) {
    if (componentsWithStates[state]) {
      return "~" + componentsWithStates[state](params);
    }

    return "";
  }

  Platform.getInstance("device").then(function (platform) {
    const link = document.createElement("link");

    link.type = "image/x-icon";
    link.rel = "shortcut icon";
    link.href = platform("getImageBaseURL") + "/favicon.ico";
    document.getElementsByTagName("head")[0].appendChild(link);
  });

  function DashboardComponentModel(context) {
    const self = this,
      genericViewModel = context.properties.rootModel;

    self.oj = oj;
    self.userData = {};
    self.locale = locale;
    self.router = router;
    self.specialCurrency = ko.observable({});

    self.isMarketingBannerPage = ko.computed(function () {
      const currentValue = self.router && self.router.currentValue();

      if (!currentValue || !currentValue.component) {
        return false;
      }

      const componentName = currentValue.component();

      return [
        "fx-rate-dashboard",
        "fx-rate-transaction",
        "generic-money-transfer",
        "adhoc-payments-generic"
      ].includes(
        componentName === "flow"
          ? currentValue.params && currentValue.params.flowName
          : componentName
      );
    });

    let currentModule, headerActionGuestID,
      initialViewPort = context.properties.baseModel.getDeviceSize();
    const appData = {};

    self.isDashboard = ko.observable(true);
    self.isHelpAvailable = ko.observable(false);
    self.modalComponent = ko.observable();
    self.oracleLiveComponent = ko.observable();
    self.fabRequired = ko.observable(true);

    self.componentReset = ko.observable(false);
    self.showHeaderNotification = ko.observable(false);
    self.showPayeeDetailsNotification = ko.observable(false);
    self.showPredesignatedNotification = ko.observable(false);
    //Added for SR2026
    self.showPredesignatedTownNotification = ko.observable(false);
    self.showFavTownNotification = ko.observable(false);
    self.showPayeeDetailsTownNotification = ko.observable(false);
    //Ended

    self.headerName = ko.observable();
    self.headerCaption = ko.observable();

    self.warningsDismissed = false;
    self.isMenuOptionSelected = ko.observable();

    self.pinReminder = ko.observable();
    self.signerPinReminder = ko.observable();
    self.pinReminderNextDisabled = ko.observable(true);
    self.signerPinReminderDisabled = ko.observable(true);
    self.signerPinWarningDays = ko.observable();
    self.showLoginPinReminder = ko.observable(false);
    self.showSignerPinReminder = ko.observable(false);
    self.selfForceChangeSignerPin = ko.observable(false);
    self.showBounceBackReminder = ko.observable(false);
    self.bounceBackReminderType = ko.observable();
    self.callLoginPinCloseHandler = ko.observable(true);
    self.callSignerPinCloseHandler = ko.observable(true);
    self.callHthApiPasswordCloseHandler = ko.observable(true);
    self.hthApiPasswordSetupState = ko.observable();
    self.isMerchantUser = ko.observable(false);
    self.currentUserRole = ko.observable();
    self.canExitCurrentPage = ko.observable(false);
    self.lastRouterChangeFunc = ko.observable();

    context.properties.baseModel.addEvent("canExitCurrentPage", {
      element: window,
      eventName: "canExitCurrentPage",
      eventHandler: function (event) {
          if (event.detail.value) {
            self.canExitCurrentPage(event.detail.value);

            if (self.canExitCurrentPage() && event.detail.componentGo) {
              const fn = self.lastRouterChangeFunc();

              if (typeof fn === "function") {
                fn();
              }
            }
          }
      }
    });

    Router.transitionedToState.add(function (result) {
      if (result.hasChanged) {
        if (result.newState.id === "risk-assessment-result") {
          routerData[result.newState.id].canEnter = routerData[
            result.oldState.id
          ].canEnter = function () {
            if (result.router.direction === "back") {
              return false;
            }
          };
        }
      }
    });

    self.changePinReminderOptions = ko.observableArray([
      {
        label: self.locale.pinChangeReminder.option1,
        value: "keepCurrentPin"
      },{
        label: self.locale.pinChangeReminder.option2,
        value: "remindOnNextLogin"
      },{
        label: self.locale.pinChangeReminder.option3,
        value: "changeNow"
      }]);

      self.changeSignerPinReminderOptions = ko.observableArray([
        {
          label: self.locale.signerPinChangeReminder.option1,
          value: "keepCurrentPin"
        },{
          label: self.locale.signerPinChangeReminder.option2,
          value: "remindOnNextLogin"
        },{
          label: self.locale.signerPinChangeReminder.option3,
          value: "changeNow"
        }]);

    router.currentValue.subscribe(function() {
      self.showHeaderNotification(false);
      self.showPayeeDetailsNotification(false);
      self.showPredesignatedNotification(false);
      //Added for SR2026
      self.showPredesignatedTownNotification(false);
      self.showFavTownNotification(false);
      self.showPayeeDetailsTownNotification(false);
      //Ended
    });

    const headerActions = function (node) {
      const actionSpace = document.getElementById("headingCustomSpace");

      if (!actionSpace) {
        return;
      }

      if (node) {
        headerActionGuestID = self.router.currentState().id;
        actionSpace.appendChild(node);

        return;
      }

      actionSpace.innerHTML = "";
    };

    self.fatcaCheckRequired = ko.observable(false);

    self.rightPanelData = {
      isOpen: ko.observable(),
      componentName: null,
      data: null,
      closeHandler: null,
      header: null
    };

    router.configure(function (stateId) {
      let state;

      context.properties.baseModel.onTFAScreen(false);
      context.properties.baseModel.onInlineTFAScreen(false);

      if (stateId) {
        const data = routerData[stateId];

        if (data) {
          state = {
            value: data,
            canEnter: data.canEnter,
            label: data.label,
            canExit: stateId !== "confirm-screen"
          };
        }
      }

      return state;
    });

    (function (registerElement) {
      registerElement([
        "responsive-img",
        "flow",
        "confirm-screen"
      ]);

      registerElement("error", "core");
      registerElement("banner", "core");
      registerElement("confirm-dialog", "core");
    })(context.properties.baseModel.registerElement);

    context.properties.baseModel.registerComponent("oracle-live", "login");

    context.properties.baseModel.registerComponent(
      "change-password",
      "change-password"
    );

    context.properties.baseModel.registerComponent(
      "change-signer-pin",
      "change-signer-pin"
    );

    context.properties.baseModel.registerComponent(
      "reset-signer-pin",
      "change-signer-pin"
    );

    context.properties.baseModel.registerComponent(
      "compliance-base",
      "compliance"
    );

    context.properties.baseModel.registerComponent(
      "rewards",
      "widgets/dashboard"
    );

    function clearResetEvents() {
      $(document).off();
      $(window).off();
      context.properties.baseModel.processAllEvents("addEventListener");

    }

    function changeRightPanelState(state) {
      if (["close", "open"].indexOf(state) === -1) {
        return;
      }

      return OffcanvasUtils[state]({
        selector: "#endDrawer",
        content: ".main-container",
        displayMode: "overlay",
        edge: "end",
        modality: "modal",
        autoDismiss: "none"
      });
    }

    self.headerName.subscribe(function (value) {
      if (value) {
        self.router.currentState().label = value;

        document.title = context.properties.baseModel.format("{txn_name} - {bankName}", {
          txn_name: value,
          bankName: self.locale.bankName
        });
      } else {
        document.title = self.locale.bankName;
      }
    });

    function resetVM() {
      self.router.currentState().canExit = true;
      changeRightPanelState("close");
      context.properties.baseModel.isDashboardBuilderContext(false);
      DashboardContext.getDashboardContext().helpComponent.componentName(null);
      ko.tasks.runEarly();
      self.headerName(null);
      self.headerCaption(null);
      headerActions(null);
      changeLoaderState("start");
      window.scrollTo(0, 0);

      if (self.router.currentState().id === "risk-assessment-result") {
        self.router.currentState().canExit = function () {
            if (self.canExitCurrentPage()) {
              /** reset the value */
              self.canExitCurrentPage(false);

              return true;
            }

              context.properties.baseModel.dispatchCustomEvent(window, "tryToExitCurrentPage", {
                  value: true
              });

            return false;
          };
      }
    }

    function manageAccountsBusyContextPromise() {
      return Context.getContext(
          document.querySelector("#appShellManageAccounts")
        )
        .getBusyContext()
        .whenReady();
    }

    function componentChange(componentName, params) {
      params = params || {};
      resetVM();

      const newState = componentName + computeState(componentName, params);

      context.properties.baseModel.isOMB(false);
      context.properties.baseModel.selectedData(null);
      context.properties.baseModel.WWSet(false);

      routerData[newState] = {
        component: ko.observable(componentName),
        params: params
      };

      routerData[router.stateId()].previousState = params;
      routerData[router.stateId()].label = router.currentState().label;

      if (ChatbotComponentMapping.chatBotComponents.includes(componentName)) {
        ChatBotFunctions.getInstance().showChatPannel(true);
      } else {
        ChatBotFunctions.getInstance().hideChatPannel();
      }

      if (componentName === "flow") {
        self.headerName(router.currentState().label);
      }

      Promise.all([
        context.properties.baseModel.closeNotificationMessages("error"),
        Navigation.beforeNavigation(
          routerData[newState],
          self.userData,
          context.properties.baseModel.large()
        )
      ]).then(function () {
        router.go(newState).finally(function () {
          self.componentReset(true);
          ko.tasks.runEarly();

          manageAccountsBusyContextPromise().then(function () {
            self.targetLoaded();
          });
        });
      });

      DashboardContext.getDashboardContext().helpComponent.componentName(
        componentName
      );

      self.fabRequired(true);
      self.isDashboard(false);
    }

    self.loadComponent = function (componentName, params) {
      self.lastRouterChangeFunc(self.loadComponent.bind(this, componentName, params));
      clearResetEvents();
      context.properties.baseModel.onTFAScreen(false);
      context.properties.baseModel.onInlineTFAScreen(false);
      context.properties.baseModel.otpExpiryTimer(undefined);
      context.properties.baseModel.emailOtpExpiryTimer(undefined);
      context.properties.baseModel.isOMB(false);
      context.properties.baseModel.selectedData(null);
      context.properties.baseModel.WWSet(false);
      self.componentReset(componentName === "flow");

      if (componentName === "flow") {
        const mappedComponent = ExtensionsOverride.getMappedComponentPath("flows", params.flowName);

        if (mappedComponent) {
          context.properties.baseModel.registerComponent(mappedComponent.component, mappedComponent.module);
          componentName = mappedComponent.component;
        }
      }

      if (componentName === "segment-container" && (params ? params.menuId === "addSubFacility" : false)) {
        ko.utils.extend(params, {
          ifSubFacility: true
        });
      }

      componentChange(componentName, params);
    };

    self.switchModule = function (module) {
      self.lastRouterChangeFunc(self.switchModule.bind(this, module));

      if (typeof module === "boolean" && module) {
        return self.modalComponent("confirm-dialog");
      } else if (!module) {
        module = currentModule.dashboard || "home";
      } else {
        currentModule.dashboard = module;
      }

      context.properties.baseModel.isOMB(false);
      context.properties.baseModel.selectedData(null);
      context.properties.baseModel.WWSet(false);

      if (router.stateId() === module) {
        self.componentReset(false);
        ko.tasks.runEarly();
        self.componentReset(true);
        self.targetLoaded();

        return;
      }

      context.properties.baseModel.onTFAScreen(false);
      context.properties.baseModel.onInlineTFAScreen(false);
      clearResetEvents();

      if (!routerData[module]) {
        routerData[module] = {
          dashboard: module,
          isDashboard: true
        };
      }

      self.componentReset(false);

      Promise.all([
        context.properties.baseModel.closeNotificationMessages(),
        Navigation.beforeNavigation(
          routerData[module],
          self.userData,
          context.properties.baseModel.large()
        )
      ]).then(function () {
        resetVM();
        self.fabRequired(true);
        self.isDashboard(true);
        ko.tasks.runEarly();

        router.go(module).then(function (statusObject) {
          if (statusObject.hasChanged) {
            self.componentReset(true);
          }
        });

      });
    };

    self.hideDetails = function () {
      self.headerName(null);
      context.properties.baseModel.closeNotificationMessages();
      DashboardContext.getDashboardContext().helpComponent.componentName(null);
      context.properties.baseModel.isOMB(false);
      context.properties.baseModel.selectedData(null);
      context.properties.baseModel.WWSet(false);
      ko.tasks.runEarly();
      history.back();
    };

    self.targetLoaded = function () {
      changeLoaderState("stop");
    };

    function getCCAPropName(prop) {
      if (prop) {
        const matchArr = prop.match(/[A-Z]/g);

        if (matchArr) {
          matchArr.forEach(function (char) {
            prop = prop.replace(char, "-" + char.toLowerCase());
          });
        }

        return prop;
      }
    }

    function openRightPanel(componentName, data, header, closeHandler, isCCA) {
      self.rightPanelData.componentName = componentName;
      self.rightPanelData.data = data;
      self.rightPanelData.header = header;
      self.rightPanelData.cca = typeof isCCA === "boolean" ? isCCA : false;

      self.rightPanelData.closeHandler = function () {
        $("body").removeClass("overflow-hidden");

        if (closeHandler && typeof closeHandler === "function") {
          closeHandler();
        }

        changeRightPanelState("close").then(function () {
          document.getElementById("rightPanelBody").innerHTML = "";
          self.rightPanelData.isOpen(false);
        });
      };

      $("body").addClass("overflow-hidden");

      self.rightPanelData.isOpen(true);
      ko.tasks.runEarly();

      if (isCCA && typeof isCCA === "boolean") {
        const element = document.createElement(componentName);

        Object.keys(data).forEach(function (key) {
          element.setAttribute(getCCAPropName(key), "{{" + key + "}}");
        });

        document.getElementById("rightPanelBody").appendChild(element);
        ko.cleanNode(element);
        ko.applyBindings(data, element);
      }

      changeRightPanelState("open");
    }

    function closeRightPanel() {
      if (self.rightPanelData.closeHandler) {
        self.rightPanelData.closeHandler();
      }
    }

    document.addEventListener("openRightPanel", function (event) {
      const params = event.detail;

      if (params.component) {
        openRightPanel(params.component, params.props, params.header, params.closeHandler, params.isCCA);
      }
    });

    document.addEventListener("closeRightPanel", closeRightPanel);

    const onDockedMenuSelect = function () {
        return self.changeMenuState("close");
      },
      resizeHandler = ko.computed(function () {
        if (initialViewPort !== context.properties.baseModel.getDeviceSize()) {
          initialViewPort = context.properties.baseModel.getDeviceSize();
          self.changeMenuState("close");
        }

        return (
          context.properties.baseModel.large() ^
          context.properties.baseModel.medium() ^
          context.properties.baseModel.small()
        );
      }, self);

    self.getMarketingWidgetParams = function() {
      return {
           baseModel: context.properties.baseModel,
           formatter: context.properties.formatter,
           dashboardName: self.router.currentValue().dashboard,
           menuOptionSelect: self.menuOptionSelect,
           filterMenu: self.filterMenu,
           computeContext: context.properties.rootModel.computeContext
        };
    };

    self.getDashboardContext = function () {
      const dashboardContext = {
        showHeaderNotification: self.showHeaderNotification,
        showPayeeDetailsNotification: self.showPayeeDetailsNotification,
        showPredesignatedNotification: self.showPredesignatedNotification,
        //Added for Sr2026
        showPredesignatedTownNotification: self.showPredesignatedTownNotification,
        showFavTownNotification: self.showFavTownNotification,
        showPayeeDetailsTownNotification: self.showPayeeDetailsTownNotification,
        //Ended
        isDashboard: self.isDashboard,
        isHelpAvailable: self.isHelpAvailable,
        headerName: self.headerName,
        headerCaption: self.headerCaption,
        headerActions: headerActions,
        helpComponent: DashboardContext.getDashboardContext().helpComponent,
        loadComponent: self.loadComponent,
        switchModule: self.switchModule,
        hideDetails: self.hideDetails,
        userData: self.userData,
        modalComponent: self.modalComponent,
        resetModalComponent: self.resetModalComponent,
        appData: appData,
        openRightPanel: openRightPanel,
        getTaxonomyDefinition: DashboardModel.getTaxonomyDefinition,
        onDockedMenuSelect: onDockedMenuSelect,
        fabRequired: self.fabRequired,
        rootRouter: router,
        fatcaCheckRequired: self.fatcaCheckRequired,
        showFatcaForm: self.showFatcaForm,
        specialCurrency: self.specialCurrency,
        currentUserRole: self.currentUserRole,
        isMerchantUser:self.isMerchantUser,
        getMarketingWidgetParams: self.getMarketingWidgetParams
      };

      if (self.isMarketingBannerPage()) {
        dashboardContext.marketingBannerParams = {
           baseModel: context.properties.baseModel,
           formatter: context.properties.formatter,
           dashboardName: self.router.currentValue().dashboard,
           menuOptionSelect: self.menuOptionSelect,
           filterMenu: self.filterMenu,
           computeContext: context.properties.rootModel.computeContext
        };
      }

      return Object.seal(dashboardContext);
    };

    self.menuOptionSelect = function (data, menuStateTogglePromise) {
      changeLoaderState("start");
      self.isMenuOptionSelected(true);

      const name = data.class === "flow" ? "flow" : data.name,

        state = name + computeState(name, {
          flowName: name === "flow" ? data.name : null,
          jsonData: data
        });

      if (ChatbotComponentMapping.chatBotComponents.includes(name)) {
        ChatBotFunctions.getInstance().showChatPannel();
      } else {
        ChatBotFunctions.getInstance().hideChatPannel();
      }

      if (routerData[state]) {
        routerData[router.stateId()].previousState = null;

        if (state === router.stateId()) {
          router.currentValue().params = name === "flow" ? {
            flowName: data.name,
            flowStageRootModel: data.stageRootModel
          } : {};
        }
      }

      (menuStateTogglePromise || Promise.resolve()).then(function () {
        if (data.name === "DASHBOARD") {
          return self.switchModule();
        }

        if (data.type && data.type === "MODULE") {
          return self.switchModule(data.name);
        } else if (data.type && data.type === "PAGE") {
          context.properties.baseModel.switchPage(
            data.location.args,
            data.location.isSecure
          );

          return false;
        } else if (data.type && data.type === "FUNCTION") {
          context.properties.baseModel[data.functionName](data.params);
          changeLoaderState("stop");
        } else if (data.type && data.type === "MODAL") {
          context.properties.baseModel.registerComponent(
            data.name,
            data.module
          );

          self.modalComponent(data.name);
        } else if (data.applicationType) {
          self.loadComponent("manage-accounts", {
            applicationType: data.applicationType,
            defaultTab: data.name,
            moduleURL: data.moduleURL,
            jsonData: data
          });
        } else if (data.class === "flow") {
          if (self.isMenuOptionSelected()) {
            if (router.currentValue().params) {
              router.currentValue().params.flowStageRootModel = {};
            }

            self.loadComponent("flow", {
              flowName: data.name,
              flowStageRootModel: data.stageRootModel
            });
          }
        } else {
          if (data.class === "transaction") {
            context.properties.baseModel.registerTransaction(
              data.name,
              data.module
            );
          } else {
            context.properties.baseModel.registerComponent(
              data.name,
              data.module
            );
          }

          self.loadComponent(data.name, {
            type: data.type,
            jsonData: data
          });
        }
      });
    };

    self.changeMenuState = function (state) {
      if (!genericViewModel.menuNavigationAvailable) {
        return;
      }

      if (["close", "open", "toggle"].indexOf(state) === -1) {
        return;
      }

      return OffcanvasUtils[state]({
        selector: "#innerDrawer",
        content: ".main-container",
        displayMode: "push",
        edge: "start",
        modality: "none"
      }).then(function () {
        // Detect IE 11, window.document.documentMode evaluates to true only for IE.
        if (state !== "close" && window.document.documentMode) {
          $("#innerDrawer").css("transform", "none");
        }
      });
    };

    $("#innerDrawer").on("ojclose", function () {
      $("span.hamburger-icon").removeClass("hide");
    });

    $("#innerDrawer").on("ojopen", function () {
      $("span.hamburger-icon").addClass("hide");
    });

    let partyData;

    self.isLogin = ko.observable(false);

    Router.transitionedToState.add(function (result) {
      if (result.hasChanged) {
        if (result.hasChanged && !result.router.parent) {
          if (result.newState.id !== headerActionGuestID) {
            headerActions(null);
          }

          if (result.newState.id !== "home" && result.newState.id !== "login-form" && result.newState.id !== "merchant-login-form") {
            $("body")[0].classList.remove("login");
            self.isLogin(false);

            router.currentState.subscribe(function (stateChanged) {
              if (stateChanged && stateChanged.value.isDashboard !== true) {
                self.componentReset(false);
                ko.tasks.runEarly();
                self.componentReset(true);
              }
            });
          } else if ($("body")[0].classList.value.includes("ANON")) {
            $("body")[0].classList.add("login");
            self.isLogin(true);
          }

          Navigation.afterNavigation();

          if (result.newState.value.isDashboard) {
            self.isDashboard(true);

            const dbContainer = document.querySelector("#dbContainer");

            Context.getContext(dbContainer)
              .getBusyContext()
              .whenReady()
              .then(function () {
                self.targetLoaded();
              });
          }

          if (result.newState.id === "confirm-screen") {
            routerData[result.newState.id].canEnter = routerData[
              result.oldState.id
            ].canEnter = function () {
              if (result.router.direction === "back") {
                return false;
              }
            };
          }
        }
      } else {
        self.componentReset(false);
        ko.tasks.runEarly();
        self.componentReset(true);
      }
    });

    const pinReminder = self.pinReminder.subscribe(function() {
      self.pinReminderNextDisabled(false);
    }),

     signerPinReminder = self.signerPinReminder.subscribe(function() {
      self.signerPinReminderDisabled(false);
    });

    /**
     * @function self.pinReminderNext
     * This function loads component based on selection
     */
    self.pinReminderNext = function() {
      if(self.pinReminder() === "changeNow") {
        self.callSignerPinCloseHandler(false);
        self.callLoginPinCloseHandler(false);
        $("#changePinDialog").trigger("closeModal");
        $("#changeSignerPinDialog").trigger("closeModal");
        DashboardModel.updatePasswordExpiry("LOGIN_REMINDER", true);
        self.loadComponent("change-password", {});
      } else if(self.pinReminder() === "keepCurrentPin") {
        DashboardModel.updatePasswordExpiry("LOGIN", false).then(function() {
          DashboardModel.updatePasswordExpiry("LOGIN_REMINDER", false);
          self.callLoginPinCloseHandler(false);
          $("#changePinDialog").trigger("closeModal");
        });

        self.loadSignerPinReminder();
      } else {
        DashboardModel.updatePasswordExpiry("LOGIN_REMINDER", true).then(function() {
          self.callLoginPinCloseHandler(false);
          $("#changePinDialog").trigger("closeModal");
        });

        self.loadSignerPinReminder();
      }

      // eslint-disable-next-line no-storage/no-browser-storage
      sessionStorage.setItem("loginPinReminderLoaded", "true");
    };

    /**
     * @function pinReminderCancel
     * This function closes the popup and calls updatePasswordExpiry API.
     */
    self.pinReminderCancel = function() {
      if(self.callLoginPinCloseHandler()) {
        DashboardModel.updatePasswordExpiry("LOGIN_REMINDER", true);
        $("#changePinDialog").trigger("closeModal");
        // eslint-disable-next-line no-storage/no-browser-storage
        sessionStorage.setItem("loginPinReminderLoaded", "true");
        self.loadSignerPinReminder();
      }
    };

    /**
     * @function self.pinReminderNext
     * This function loads component based on selection for signer pin.
     */
     self.signerPinReminderNext = function() {
      if(self.signerPinReminder() === "changeNow") {
        self.callLoginPinCloseHandler(false);
        self.callSignerPinCloseHandler(false);
        $("#changeSignerPinDialog").trigger("closeModal");
        $("#changePinDialog").trigger("closeModal");
        DashboardModel.updatePasswordExpiry("SIGNER_REMINDER", true);
        self.loadComponent("change-signer-pin", {});
      } else if(self.signerPinReminder() === "keepCurrentPin") {
        DashboardModel.updatePasswordExpiry("SIGNER", false).then(function() {
          DashboardModel.updatePasswordExpiry("SIGNER_REMINDER", false);
          self.callSignerPinCloseHandler(false);
          $("#changeSignerPinDialog").trigger("closeModal");
        });

        self.loadBounceBackReminder();
      } else {
        DashboardModel.updatePasswordExpiry("SIGNER_REMINDER", true).then(function() {
          self.callSignerPinCloseHandler(false);
          $("#changeSignerPinDialog").trigger("closeModal");
        });

        self.loadBounceBackReminder();
      }

      // eslint-disable-next-line no-storage/no-browser-storage
      sessionStorage.setItem("signerPinReminderLoaded", "true");
    };

    /**
     * @function signerPinReminderCancel
     * This function closes the popup and calls updatePasswordExpiry API.
     */
     self.signerPinReminderCancel = function() {
       if(self.callSignerPinCloseHandler()) {
        DashboardModel.updatePasswordExpiry("SIGNER_REMINDER", true);
        $("#changeSignerPinDialog").trigger("closeModal");
        // eslint-disable-next-line no-storage/no-browser-storage
        sessionStorage.setItem("signerPinReminderLoaded", "true");
        self.loadBounceBackReminder();
       }
    };

    /**
     * @function closeBounceBackReminder
     * This function closes the Bounce Back reminder popup.
     */
    self.closeBounceBackReminder = function() {
      // eslint-disable-next-line no-storage/no-browser-storage
      sessionStorage.setItem("bounceBackReminderLoaded", "true");
      $("#bounceBackDialog").trigger("closeModal");
    };

    self.dismissHthApiPasswordSetup = function() {
      if (self.callHthApiPasswordCloseHandler()) {
        // This is only a per-login dismissal; server state remains NOT_SETUP.
        // eslint-disable-next-line no-storage/no-browser-storage
        sessionStorage.setItem("hthApiPasswordSetupPromptLoaded", "true");
        self.callHthApiPasswordCloseHandler(false);
        $("#hthApiPasswordSetupDialog").trigger("closeModal");
        self.openBounceBackReminder();
      }
    };

    self.openHthApiPasswordSetup = function() {
      // eslint-disable-next-line no-storage/no-browser-storage
      sessionStorage.setItem("hthApiPasswordSetupPromptLoaded", "true");
      self.callHthApiPasswordCloseHandler(false);
      $("#hthApiPasswordSetupDialog").trigger("closeModal");

      context.properties.baseModel.registerComponent(
        "api-password", "host-to-host"
      );

      self.loadComponent("api-password", { mode: "SETUP" });
    };

    self.loadHthApiPasswordSetup = function() {
      // eslint-disable-next-line no-storage/no-browser-storage
      if (sessionStorage.getItem("hthApiPasswordSetupPromptLoaded") === "true") {
        self.openBounceBackReminder();

        return;
      }

      DashboardModel.getHthApiPasswordStatus().then(function(data) {
        const state = data && String(data.setupState || "").toUpperCase();

        self.hthApiPasswordSetupState(state);

        if (state === "REQUIRED" || state === "CODE_REQUIRED") {
          // eslint-disable-next-line no-storage/no-browser-storage
          sessionStorage.setItem("hthApiPasswordSetupPromptLoaded", "true");
          $("#hthApiPasswordSetupDialog").trigger("openModal");
        } else {
          self.openBounceBackReminder();
        }
      }).catch(function() {
        self.openBounceBackReminder();
      });
    };

    /**
     * @function loadBounceBackReminder
     * This function loads bounce back reminder popup.
     */
    self.openBounceBackReminder = function() {
      // eslint-disable-next-line no-storage/no-browser-storage
      if(sessionStorage.getItem("bounceBackReminderLoaded") !== "true" && self.showBounceBackReminder()) {
        $("#bounceBackDialog").trigger("openModal");
      }
    };

    self.loadBounceBackReminder = self.loadHthApiPasswordSetup;

    /**
     * @function updateBounceBackReminder
     * This function calls put api and updates bounce back flag.
     */
    self.updateBounceBackReminder = function() {
      DashboardModel.updateBounceBackFlag("N");
      $("#bounceBackDialog").trigger("closeModal");
    };

    Router.sync().finally(function () {
      Promise.all([
        genericViewModel.userInfoPromise,
        Platform.getInstance("authentication")
      ]).then(function (promiseData) {
        self.headerName(null);

        context.properties.baseModel.showMerchantHeaderFooterChanges(false);

        if(promiseData[0].appData.segment === "CORP" || promiseData[0].appData.segment === "CORPADMIN") {
          let dictionaryArray;

          if(promiseData[0].userData.userProfile !== null && promiseData[0].userData.userProfile !== undefined){
            if(promiseData[0].userData.userProfile.dictionaryArray !== undefined) {
              dictionaryArray = promiseData[0].userData.userProfile.dictionaryArray;

              for(let i=0; i<dictionaryArray[0].nameValuePairDTOArray.length; i++) {
                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "signPwdExpiryWarningDays") {
                  self.signerPinWarningDays(Number(dictionaryArray[0].nameValuePairDTOArray[i].value));
                }

                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "isLoginPinReminder") {
                  self.showLoginPinReminder(dictionaryArray[0].nameValuePairDTOArray[i].value === "true");
                }

                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "SelfForceChangeSignerPin") {
                  self.selfForceChangeSignerPin(dictionaryArray[0].nameValuePairDTOArray[i].value === "Y");
                }

                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "isSignerPinReminder") {
                  self.showSignerPinReminder(dictionaryArray[0].nameValuePairDTOArray[i].value === "true");
                }

                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "Bounce_back_reminder") {
                  self.bounceBackReminderType(dictionaryArray[0].nameValuePairDTOArray[i].value.toLowerCase());
                  self.showBounceBackReminder(dictionaryArray[0].nameValuePairDTOArray[i].value.toLowerCase() !== "n");
                }

                if(dictionaryArray[0].nameValuePairDTOArray[i].name === "isMerchantUser" && dictionaryArray[0].nameValuePairDTOArray[i].value === "Y") {
                  self.isMerchantUser(true);
                  context.properties.baseModel.showMerchantHeaderFooterChanges(true);
                }
              }
            }

            /**
             * @function loadSignerPinReminder
             * This function loads signer reminder popup by checking warning days and flags.
             */
            self.loadSignerPinReminder = function () {
              if(self.signerPinWarningDays() !== null) {
                if((self.signerPinWarningDays() < 90 && self.signerPinWarningDays() > 0) || self.showSignerPinReminder()) {
                  // eslint-disable-next-line no-storage/no-browser-storage
                  if(sessionStorage.getItem("signerPinReminderLoaded") !== "true") {
                    $("#changeSignerPinDialog").trigger("openModal");
                  } else {
                    self.loadBounceBackReminder();
                  }
                } else {
                  self.loadBounceBackReminder();
                }
              }
            };

            // eslint-disable-next-line no-storage/no-browser-storage
            if (self.selfForceChangeSignerPin() && sessionStorage.getItem("signerPinResetPageLoaded") !== "true") {
              setTimeout(() => {
                self.loadComponent("reset-signer-pin", {
                    isMigratedUser: false,
                    isOffshoreEnv: false,
                    genericViewModel: genericViewModel
                });

                appData.toReloadDashboard = true;
                // eslint-disable-next-line no-storage/no-browser-storage
                sessionStorage.setItem("signerPinResetPageLoaded", "true");
              }, 0);
            // eslint-disable-next-line no-storage/no-browser-storage
            } else if(((promiseData[0].userData.userProfile.pwdExpiryWarningDays < 90 && promiseData[0].userData.userProfile.pwdExpiryWarningDays > 0) || self.showLoginPinReminder()) && sessionStorage.getItem("loginPinReminderLoaded") !== "true") {
                $("#changePinDialog").trigger("openModal");
            } else {
              self.loadSignerPinReminder();
            }
          }
        }

        const data = promiseData[0],
          authPlatform = promiseData[1];

        currentModule = data.currentModule;
        $.extend(appData, data.appData);
        $.extend(self.userData, data.userData);
        context.properties.baseModel.dispatchCustomEvent(window, "menuChanged");

        if (!authPlatform("behaviour", "externalAuthenticator")) {
          if (!self.userData.userProfile) {
            if (
              window.location.pathname.match(
                Configurations.authentication.pages.securePage
              )
            ) {
              currentModule.homeComponent = "login-form";
              currentModule.moduleName = "widgets/pre-login";
            }
          }
        }

        let initialState = null;

        if (self.userData.userProfile) {
          genericViewModel.isUserDataSet(true);

          Platform.getInstance("device").then(function (platform) {
            platform("postLogin", self.userData.userProfile);
          });

          authPlatform("getPublicKey").then(function(data) {
            context.properties.baseModel.publicKey(data.publicKeyDTO.publicKey);
            context.properties.baseModel.publicExponent(data.publicKeyDTO.publicExponent);
            context.properties.baseModel.modulus(data.publicKeyDTO.modulus);
          });

          DashboardModel.getDay0Config("SPECIAL_CURRENCIES").then(function(data) {
            if(data.configResponseList && data.configResponseList.length) {
              let currencyList = data.configResponseList[0].propertyValue;

              if(currencyList) {
                currencyList = currencyList.split("#");

                const temp = {};

                currencyList.forEach(e => temp[e.split("~")[0]] = e.split("~")[1].toString());

                self.specialCurrency(temp);

                /**
                 *
                 * This method in extensions.js has been overwritten to accomodate SPECIAL_CURRECNY requirement for Defect UAT-712, 985
                 *
                 */
                ExtensionsOverride.getCurrencyFractionalDigit = function(currency) {
                  return self.specialCurrency()[currency];
                };
              }
            }
          });

          DashboardModel.getDay0preference("API_TIMOUT_MILISECONDS").then(function(data) {
            if(data.listCustomConfigDTO && data.listCustomConfigDTO.length) {
              const apiTimeoutDuration = data.listCustomConfigDTO[0].propertyValue;

              context.properties.baseModel.apiTimeoutDuration(apiTimeoutDuration);

            }
          });
        }

        if (!currentModule.homeComponent) {
          self.isDashboard(true);

          currentModule.dashboard = initialState = "home";
        } else {
          if (currentModule.moduleName) {
            context.properties.baseModel.registerComponent(
              currentModule.homeComponent,
              currentModule.moduleName
            );
          }

          routerData[currentModule.homeComponent] = {
            component: ko.observable(currentModule.homeComponent),
            params: genericViewModel.queryMap && genericViewModel.queryMap.params ?
              JSON.parse(genericViewModel.queryMap.params) : null,
            previousState: null
          };

          if (
            genericViewModel.queryMap &&
            genericViewModel.queryMap.homeComponent ===
            currentModule.homeComponent
          ) {
            genericViewModel.queryMap.homeComponent = null;
            genericViewModel.queryMap.homeModule = null;
            genericViewModel.queryMap.params = null;
          }

          initialState = currentModule.homeComponent;

          self.isDashboard(false);
        }

        Navigation.beforeNavigation(
          routerData[initialState],
          self.userData,
          context.properties.baseModel.large()
        ).then(function () {
          router.go(initialState).then(function () {
            self.componentReset(true);

            manageAccountsBusyContextPromise().then(function () {
              self.targetLoaded();
            });
          });
        });

        if (genericViewModel.menuNavigationAvailable) {
          if (
            self.userData.userProfile &&
            self.userData.userProfile.partyId &&
            self.userData.userProfile.partyId.value
          ) {
            DashboardModel.fetchPartyDetails().then(function (data) {
              if (data.party.fatcaCheckRequired) {
                partyData = data.party;
                self.fatcaCheckRequired(true);
              }
            });
          }
        }

        context.properties.baseModel.enqueueTask(function () {
          self.oracleLiveComponent("oracle-live");
        });
      });
    });

    self.filterMenu = function (menuOptions) {
      return menuOptions.filter(function (element) {
        if (
          !Configurations.system.componentAccessControlEnabled ||
          element.default
        ) {
          return true;
        }

        if (element.submenus) {
          element.submenus = self.filterMenu(element.submenus);
        } else if (element.type && element.type === "MODULE") {
          return context.properties.baseModel
            .getAuthorisedComponentList("DASHBOARD")
            .has(element.name);
        } else {
          return context.properties.baseModel
            .getAuthorisedComponentList()
            .has(element.name);
        }

        if (element.submenus && element.submenus.length) {
          return true;
        }

        return false;
      });
    };

    self.showFatcaForm = function () {
      self.loadComponent("compliance-base", partyData);
    };

    self.resetModalComponent = function () {
      self.modalComponent(null);
    };

    self.sessionExpiredHandler = function () {
      genericViewModel.resetLayout();
    };

    self.backTop = function () {
      $("body,html").animate({
          scrollTop: 0
        },
        1000
      );
    };

    self.dismissWarnings = function () {
      context.properties.baseModel.displayInteraction("fadeOut", "#warning-container", "slow");
      self.warningsDismissed = true;
    };

    //https://stackoverflow.com/questions/18843936/how-to-disable-the-ctrlp-using-javascript-or-jquery
    window.addEventListener("keydown", function (event) {
      if (event.keyCode === 80 && (event.ctrlKey || event.metaKey) && !event.altKey && (!event.shiftKey || window.chrome || window.opera)) {
        event.preventDefault();

        if (event.stopImmediatePropagation) {
          event.stopImmediatePropagation();
        } else {
          event.stopPropagation();
        }
      }
    }, true);

    self.dispose = function () {
      resizeHandler.dispose();
      pinReminder.dispose();
      signerPinReminder.dispose();
    };
  }

  return DashboardComponentModel;
});
