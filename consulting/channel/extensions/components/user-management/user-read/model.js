define([
  "jquery",
  "baseService"
], function ($, BaseService) {
  "use strict";

  /**
   * Model for UsersCreateModel section.
   *
   * @namespace UsersCreateModel code~UsersCreateModel
   * @class
   */
  const UserReadModel = function () {
    /**
     * baseService instance through which all the rest calls will be made.
     *
     * @attribute baseService
     * @private
     */
    const baseService = BaseService.getInstance();
    let
      fetchChildRoleDeferred;
    const fetchChildRole = function (enterpriseRoleId, deferred) {
      const params = { enterpriseRoleId: enterpriseRoleId },
        options = {
          url: "applicationRoles?filterSegmentedRole=true&accessPointType=INT&enterpriseRole={enterpriseRoleId}",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    };
    let fetchAccessDeferred;
    const fetchAccess = function (searchParams, deferred) {
      const options = {
        url: "accessPoints?accessType=All",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options, searchParams);
    };
    let getEnterpriseRolesDeferred;
    const getEnterpriseRoles = function (deferred) {
      const options = {
        url: "enterpriseRoles?isLocal=true",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };
    let resetPasswordDeferred;
    const resetPassword = function (username, deferred) {
      const params = {
        userId: username
      },
        options = {
          url: "users/{userId}/resetCredentials",
          success: function (data, status, jqXHR) {
            deferred.resolve(data, status, jqXHR);
          },
          error: function (data, status, jqXHR) {
            deferred.reject(data, status, jqXHR);
          }
        };

      baseService.update(options, params);
    };
    let readUserDeferred;
    const readUser = function (id, deferred) {
      const params = {
        userId: id
      },
        options = {
          url: "users/{userId}",
          showMessage: false,
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    },
      downloadUserDetails = function (id) {
        const params = {
          userId: id
        },
          options = {
            url: "users/{userId}/?media=text/csv&mediaFormat=csv"
          };

        baseService.downloadFile(options, params);
      };
    let fetchDeviceCountDeferred;
    const fetchDeviceCount = function (username, deferred) {
      const params = {
        userId: username
      },
        options = {
          url: "mobileClient/registeredDevices/{userId}",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    };
    let fetchDeviceCountForPushNotificationDeferred;
    const fetchDeviceCountForPushNotification = function (username, deferred) {
      const params = {
        userId: username
      },
        options = {
          url: "mobileClient/registeredPushToken/{userId}",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    };
    let fetchUserSegmentsDeferred;
    const fetchUserSegments = function (searchParams, deferred) {
      const options = {
        url: "segments?enterpriseRole={selectedUser}",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options, searchParams);
    };
    let readUserExtensionDeferred;
    const readUserExtension = function (id, deferred) {
      const params = {
        userExtensionKey: id
      },
        options = {
          url: "userExtensionData/{userExtensionKey}",
          version: "cz/v1",
          showMessage: false,
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    },
    checkTFA = function (successHandler) {
      const options = {
          url: "transactionTFA/checkTFA",
          version: "cz/v1",
          showMessage: false,
          throttle: false,
          success: function (data, status, jqXHR) {
            successHandler(data, status, jqXHR);
          }
        };

      baseService.fetch(options);
    };
    let getEnbleItokenManagementDeferred;
    const getEnbleItokenManagement = function (deferred) {
      const options = {
        url: "customconfigurations/listwithsearchtext?summaryText=ENABLE_ITOKEN_MANAGEMENT",
        version: "cz/v1",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };

    return {
      fetchChildRole: function (enterpriseRoleId) {
        fetchChildRoleDeferred = $.Deferred();
        fetchChildRole(enterpriseRoleId, fetchChildRoleDeferred);

        return fetchChildRoleDeferred;
      },
      resetPassword: function (username) {
        resetPasswordDeferred = $.Deferred();
        resetPassword(username, resetPasswordDeferred);

        return resetPasswordDeferred;
      },
      readUser: function (Parameters) {
        readUserDeferred = $.Deferred();
        readUser(Parameters, readUserDeferred);

        return readUserDeferred;
      },
      downloadUserDetails: function (Parameters) {
        downloadUserDetails(Parameters);
      },
      fetchDeviceCount: function (Parameters) {
        fetchDeviceCountDeferred = $.Deferred();
        fetchDeviceCount(Parameters, fetchDeviceCountDeferred);

        return fetchDeviceCountDeferred;
      },
      fetchDeviceCountForPushNotification: function (Parameters) {
        fetchDeviceCountForPushNotificationDeferred = $.Deferred();
        fetchDeviceCountForPushNotification(Parameters, fetchDeviceCountForPushNotificationDeferred);

        return fetchDeviceCountForPushNotificationDeferred;
      },
      getEnterpriseRoles: function () {
        getEnterpriseRolesDeferred = $.Deferred();
        getEnterpriseRoles(getEnterpriseRolesDeferred);

        return getEnterpriseRolesDeferred;
      },
      fetchAccess: function (searchParams) {
        fetchAccessDeferred = $.Deferred();
        fetchAccess(searchParams, fetchAccessDeferred);

        return fetchAccessDeferred;
      },
      fetchUserSegments: function (searchParams) {
        fetchUserSegmentsDeferred = $.Deferred();
        fetchUserSegments(searchParams, fetchUserSegmentsDeferred);

        return fetchUserSegmentsDeferred;
      },
      /**
       * ListAccessPointGroup - fetches the AccessPointGroup List.
       *
       * @returns {Promise}  Returns the promise object.
       */
      listAccessPointGroup: function () {
        const options = {
          url: "accessPointGroups"
        };

        return baseService.fetch(options);
      },
      fetchUserGroupList: function (partyId) {
        return baseService.fetch({
          url: "userGroups?partyId={partyId}"
        }, {
          partyId: partyId
        });
      },
      readUserExtension: function (id) {
        readUserExtensionDeferred = $.Deferred();
        readUserExtension(id, readUserExtensionDeferred);

        return readUserExtensionDeferred;
      },
      checkTFA: function (successHandler) {
        checkTFA(successHandler);
      },
      getEnbleItokenManagement: function () {
        getEnbleItokenManagementDeferred = $.Deferred();
        getEnbleItokenManagement(getEnbleItokenManagementDeferred);

        return getEnbleItokenManagementDeferred;
      },
      /**
       * BCOH2H-787: Reveal HTH API Password Code (audit logged).
       *
       * @param {String} codeId - code ID to reveal
       * @returns {Promise} Returns the promise object.
       */
      revealHthApiPasswordCode: function (codeId) {
        const revealDeferred = $.Deferred(),
          options = {
            url: "hostToHostApiPassword/reveal",
            version: "cz/v1",
            data: JSON.stringify({ codeId: codeId }),
            contentType: "application/json",
            success: function (data) {
              revealDeferred.resolve(data);
            },
            error: function (data) {
              revealDeferred.reject(data);
            }
          };

        baseService.add(options);

        return revealDeferred;
      },
      /**
       * BCOH2H-787: Get masked HTH API Password Code info.
       * Never returns plain-text code.
       *
       * @param {String} partyId
       * @param {String} userName
       * @returns {Promise} Returns the promise object.
       */
      getHthApiPasswordCodeMasked: function (partyId, userName, codeId) {
        const maskedDeferred = $.Deferred(),
          options = {
            url: "hostToHostApiPassword/masked?partyId={partyId}&userName={userName}"
              + (codeId ? "&codeId={codeId}" : ""),
            version: "cz/v1",
            success: function (data) {
              maskedDeferred.resolve(data);
            },
            error: function (data) {
              maskedDeferred.reject(data);
            }
          };

        baseService.fetch(options, {
          partyId: partyId,
          userName: userName,
          codeId: codeId
        });

        return maskedDeferred;
      }
    };
  };

  return new UserReadModel();
});