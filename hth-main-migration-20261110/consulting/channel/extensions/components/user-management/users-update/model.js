define([
  "jquery",
  "baseService"
], function ($, BaseService) {
  "use strict";

  /**
   * Model for UsersUpdateModel section.
   *
   * @namespace UsersUpdateModel code~UsersUpdateModel
   * @class
   */
  const UsersUpdateModel = function () {
    /**
     * baseService instance through which all the rest calls will be made.
     *
     * @attribute baseService
     * @private
     */
    const baseService = BaseService.getInstance();
    let
      /* variable to make sure that in case there is no change
       * in model no additional fetch requests are fired.*/
      /**
       * This function fires a POST request to update user details
       * and delegates control to the successhandler along with response data
       * once the details are successfully fetched
       *
       * @function updateUser
       * @memberOf UsersUpdateModel
       * @param {String} payload  -  indicates the form details to be updated filled by admin
       * @example UsersUpdateModel.updateUser(data);
       */
      updateUserDeferred;
    const updateUser = function (payload, id, deferred) {
      const params = {
        userId: id
      },
        options = {
          url: "users/{userId}",
          data: payload,
          success: function (payload, status, jqXhr) {
            deferred.resolve(payload, status, jqXhr);
          },
          error: function (data, status, jqXhr) {
            deferred.reject(data, status, jqXhr);
          }
        };

      baseService.update(options, params);
    };
    let updateUserModifiedDeferred;
    const updateUserModify = function (payload, id, transactionId, deferred) {
      const params = {
        userId: id
      },
        options = {
          url: "users/{userId}",
          data: payload,
          headers: {
            "X-Transaction-ID": transactionId + "#1"
          },
          success: function (payload, status, jqXhr) {
            deferred.resolve(payload, status, jqXhr);
          },
          error: function (data, status, jqXhr) {
            deferred.reject(data, status, jqXhr);
          }
        };

      baseService.update(options, params);
    };
    let fetchAccessDeferred;
    const fetchAccess = function (searchParams, deferred) {
      const options = {
        url: "accessPoints?accessType={accessType}&accessPointStatus=Y",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options, searchParams);
    };

    let fetchUserSegmentsDeferred;
    const fetchUserSegments = function (searchParams, deferred) {
      const options = {
        url: "segments?enterpriseRole={selectedUser}&status=ENABLED",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options, searchParams);
    };

    let fetchUserLimitOptionsDeferred;
    const fetchUserLimitOptions = function (deferred, businessEntity, assignableEntitiesData) {
      const params = {
        assignableEntitiesData: assignableEntitiesData
      },
        options = {
          url: "limitPackages?assignableEntities={assignableEntitiesData}",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      if (businessEntity) {
        options.headers = {
          "X-Target-Unit": businessEntity
        };
      }

      baseService.fetch(options, params);
    };
    /**
     * This function read the entities
     * @params {deferred} - object to trach completion of network call
     * @function fetchEntitites
     * @memberOf ExclusionModel
     **/
    let fetchEntititesDeferred;
    const fetchEntitites = function (deferred) {
      const options = {
        url: "entities",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options);
    };

    let fetchCountryDeferred;
    const fetchCountry = function (deferred) {
      const options = {
        url: "enumerations/country",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };
    let fetchChildRoleDeferred;
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
    },
      updateUserExtension = function (payload, id, BMTransactionRefNo, successHandler) {
        const params = {
          userExtensionKey: id
        },
          options = {
            url: "userExtensionData/{userExtensionKey}",
            version: "cz/v1",
            data: payload,
            headers: {
              "MULTI_BM-TXN-REFERENCES": BMTransactionRefNo
            },
            success: function (payload, status, jqXhr) {
              successHandler(payload, status, jqXhr);
            }
          };

        baseService.update(options, params);
      };
    let fetchCountryCodesDeferred;
    const fetchCountryCodes = function (deferred) {
      const options = {
        url: "enumerations/countrycodes",
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
    let fetchUserIdMaintenanceDeferred;
    const fetchUserIdMaintenance = function (partyId, deferred) {
      const params = {
        partyId: partyId
      },
        options = {
          url: "useridmaintenance/search?partyId={partyId}",
          version: "cz/v1",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    };
    let updateSignerDeferred;
    const updateSigner = function (BMTransactionRefNo, payload, deferred) {
      const options = {
        url: "hostuserdetails/updateSigner/01",
        version: "cz/v1",
        data: payload,
        headers: {
          "BM-TXN-REFERENCE": BMTransactionRefNo
        },
        success: function (data, status, jqXhr) {
          deferred.resolve(data, status, jqXhr);
        },
        error: function (data, status, jqXhr) {
          deferred.reject(data, status, jqXhr);
        }
      };

      baseService.add(options);
    };
    let updateLoginIdDeferred;
    const updateLoginId = function (BMTransactionRefNo, payload, deferred) {
      const options = {
        url: "hostuserdetails/updateLoginId/01",
        version: "cz/v1",
        data: payload,
        headers: {
          "BM-TXN-REFERENCE": BMTransactionRefNo
        },
        success: function (data, status, jqXhr) {
          deferred.resolve(data, status, jqXhr);
        },
        error: function (data, status, jqXhr) {
          deferred.reject(data, status, jqXhr);
        }
      };

      baseService.add(options);
    };
    let fetchDayZeroUserIdsDeferred;
    const fetchDayZeroUserIds = function (userType, partyId, deferred) {
      const params = {
        userType: userType,
        partyId: partyId
      },
        options = {
          url: "useridmaintenance/getids?idType={userType}&partyId={partyId}&moduleType=UM&txnType=UM",
          version: "cz/v1",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
    };
    let validateCustDetailsDeferred;
    const validateCustDetails = function (documentType, documentCountry, documentId, deferred) {
      const params = {
        documentType: documentType,
        documentCountry: documentCountry,
        documentId: documentId
      },
        options = {
          url: "userExtensionData/fetchCustInfo?documentType={documentType}&documentCountry={documentCountry}&documentId={documentId}",
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
    };
    let checkApprovalDeferred;
    const checkApproval = function (cdcId, userGroupIds, userId, deferred) {
      const params = {
        cdcId: cdcId,
        userGroupIds: userGroupIds,
        userId: userId
      },
        options = {
          url: "userGroups/checkTxn?cdcId={cdcId}&userGroupIds={userGroupIds}&method=UPDATE&userId={userId}",
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
    };
    let validatePinStatusDeferred;
    const validatePinStatus = function (id, payload, deferred) {
      const params = {
        userExtensionKey: id
      },
        options = {
          url: "userExtensionData/pinStatus/{userExtensionKey}",
          version: "cz/v1",
          data: payload,
          showMessage: false,
          success: function (payload, status, jqXhr) {
            deferred.resolve(payload, status, jqXhr);
          },
          error: function (data, status, jqXhr) {
            deferred.reject(data, status, jqXhr);
          }
        };

      baseService.update(options, params);
    };

    /**
     * BCOH2H-787: Generate HTH API Password Code (update page).
     * Returns plain-text code only in this response.
     */
    let generateHthApiPasswordCodeDeferred;
    const generateHthApiPasswordCode = function (partyId, userName, deferred) {
      const options = {
        url: "hostToHostApiPassword/generate",
        data: JSON.stringify({ partyId: partyId,
userName: userName }),
        version: "cz/v1",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.add(options);
    };

    /**
     * BCOH2H-787: Get masked HTH API Password Code info.
     * Never returns plain-text code.
     */
    let getHthApiPasswordCodeMaskedDeferred;
    const getHthApiPasswordCodeMasked = function (partyId, userName, deferred) {
      const params = {
        partyId: partyId,
        userName: userName
      },
      options = {
        url: "hostToHostApiPassword/masked?partyId={partyId}&userName={userName}",
        version: "cz/v1",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options, params);
    };

    /**
     * BCOH2H-787: Reveal HTH API Password Code (audit logged).
     */
    let revealHthApiPasswordCodeDeferred;
    const revealHthApiPasswordCode = function (codeId, deferred) {
      const options = {
        url: "hostToHostApiPassword/reveal",
        data: JSON.stringify({ codeId: codeId }),
        version: "cz/v1",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.add(options);
    };

    let sendSuspendEmailAlertDeferred;
    const sendSuspendEmailAlert = function (payload, deferred) {
      const options = {
        url: "userExtensionData/sendSuspendEmailAlert",
        version: "cz/v1",
        data: payload,
        showMessage: false,
        success: function (payload, status, jqXhr) {
          deferred.resolve(payload, status, jqXhr);
        },
        error: function (data, status, jqXhr) {
          deferred.reject(data, status, jqXhr);
        }
      };

      baseService.update(options);
    };
    let addSignerDeferred;
    const addSigner = function (subOption, BMTransactionRefNo, payload, deferred) {
      const params = {
        subOption: subOption
      }, options = {
        url: "hostuserdetails/addSigner/{subOption}",
        headers: {
          "BM-TXN-REFERENCE": BMTransactionRefNo
        },
        version: "cz/v1",
        data: payload,
        success: function (data, status, jqXhr) {
          deferred.resolve(data, status, jqXhr);
        },
        error: function (data, status, jqXhr) {
          deferred.reject(data, status, jqXhr);
        }
      };

      baseService.add(options, params);
    };
    let deleteSignerIdDeferred;
    const deleteSignerId = function (signerId, cdcdId, BMTransactionRefNo, deferred) {
      const params = {
        signerId: signerId,
        cdcdId: cdcdId
      },
        options = {
          url: "hostuserdetails/deleteSignerId/01/{signerId}?cdcId={cdcdId}",
          version: "cz/v1",
          headers: {
            "BM-TXN-REFERENCE": BMTransactionRefNo
          },
          success: function (data, status, jqXHR) {
            deferred.resolve(data, status, jqXHR);
          },
          error: function (data, status, jqXHR) {
            deferred.reject(data, status, jqXHR);
          }
        };

      baseService.remove(options, params);
    };

    let addUserIdMaintenanceDeferred;
    const addUserIdMaintenance = function (BMTransactionRefNo, payload, deferred) {
      const params = {
        payload: payload
      },
        options = {
          url: "useridmaintenance/addUserIdMaintenance",
          data: payload,
          version: "cz/v1",
          headers: {
            "BM-TXN-REFERENCE": BMTransactionRefNo
          },
          success: function (data, status, jqXhr) {
            deferred.resolve(data, status, jqXhr);
          },
          error: function (data, status, jqXhr) {
            deferred.reject(data, status, jqXhr);
          }
        };

      baseService.add(options, params);
    };

    return {
      updateUser: function (payload, id) {
        updateUserDeferred = $.Deferred();
        updateUser(payload, id, updateUserDeferred);

        return updateUserDeferred;
      },
      updateUserModify: function (payload, id, trasnactionId) {
        updateUserModifiedDeferred = $.Deferred();
        updateUserModify(payload, id, trasnactionId, updateUserModifiedDeferred);

        return updateUserModifiedDeferred;
      },
      fetchUserLimitOptions: function (businessEntity, assignableEntitiesData) {
        fetchUserLimitOptionsDeferred = $.Deferred();
        fetchUserLimitOptions(fetchUserLimitOptionsDeferred, businessEntity, assignableEntitiesData);

        return fetchUserLimitOptionsDeferred;
      },
      fetchEntitites: function () {
        fetchEntititesDeferred = $.Deferred();
        fetchEntitites(fetchEntititesDeferred);

        return fetchEntititesDeferred;
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
      fetchCountry: function () {
        fetchCountryDeferred = $.Deferred();
        fetchCountry(fetchCountryDeferred);

        return fetchCountryDeferred;
      },
      fetchChildRole: function (enterpriseRoleId) {
        fetchChildRoleDeferred = $.Deferred();
        fetchChildRole(enterpriseRoleId, fetchChildRoleDeferred);

        return fetchChildRoleDeferred;
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
      updateUserExtension: function (payload, id, BMTransactionRefNo, successHandler) {
        updateUserExtension(payload, id, BMTransactionRefNo, successHandler);
      },
      fetchCountryCodes: function () {
        fetchCountryCodesDeferred = $.Deferred();
        fetchCountryCodes(fetchCountryCodesDeferred);

        return fetchCountryCodesDeferred;
      },
      fetchUserIdMaintenance: function (partyId) {
        fetchUserIdMaintenanceDeferred = $.Deferred();
        fetchUserIdMaintenance(partyId, fetchUserIdMaintenanceDeferred);

        return fetchUserIdMaintenanceDeferred;
      },
      updateSigner: function (BMTransactionRefNo, payload) {
        updateSignerDeferred = $.Deferred();
        updateSigner(BMTransactionRefNo, payload, updateSignerDeferred);

        return updateSignerDeferred;
      },
      updateLoginId: function (BMTransactionRefNo, payload) {
        updateLoginIdDeferred = $.Deferred();
        updateLoginId(BMTransactionRefNo, payload, updateLoginIdDeferred);

        return updateLoginIdDeferred;
      },
      fetchDayZeroUserIds: function (userType, partyId) {
        fetchDayZeroUserIdsDeferred = $.Deferred();
        fetchDayZeroUserIds(userType, partyId, fetchDayZeroUserIdsDeferred);

        return fetchDayZeroUserIdsDeferred;
      },
      checkApproval: function (cdcId, userGroupIds, userId) {
        checkApprovalDeferred = $.Deferred();
        checkApproval(cdcId, userGroupIds, userId, checkApprovalDeferred);

        return checkApprovalDeferred;
      },
      validateCustDetails: function (documentType, documentCountry, documentId) {
        validateCustDetailsDeferred = $.Deferred();
        validateCustDetails(documentType, documentCountry, documentId, validateCustDetailsDeferred);

        return validateCustDetailsDeferred;
      },
      validatePinStatus: function (id, payload) {
        validatePinStatusDeferred = $.Deferred();
        validatePinStatus(id, payload, validatePinStatusDeferred);

        return validatePinStatusDeferred;
      },
      sendSuspendEmailAlert: function (payload) {
        sendSuspendEmailAlertDeferred = $.Deferred();
        sendSuspendEmailAlert(payload, sendSuspendEmailAlertDeferred);

        return sendSuspendEmailAlertDeferred;
      },
      addSigner: function (subOption, BMTransactionRefNo, payload) {
        addSignerDeferred = $.Deferred();
        addSigner(subOption, BMTransactionRefNo, payload, addSignerDeferred);

        return addSignerDeferred;
      },
      deleteSignerId: function (signerId, cdcdId, BMTransactionRefNo) {
        deleteSignerIdDeferred = $.Deferred();
        deleteSignerId(signerId, cdcdId, BMTransactionRefNo, deleteSignerIdDeferred);

        return deleteSignerIdDeferred;
      },
      addUserIdMaintenance: function (BMTransactionRefNo, payload) {
        addUserIdMaintenanceDeferred = $.Deferred();
        addUserIdMaintenance(BMTransactionRefNo, payload, addUserIdMaintenanceDeferred);

        return addUserIdMaintenanceDeferred;
      },
      generateHthApiPasswordCode: function (partyId, userName) {
        generateHthApiPasswordCodeDeferred = $.Deferred();
        generateHthApiPasswordCode(partyId, userName, generateHthApiPasswordCodeDeferred);

        return generateHthApiPasswordCodeDeferred;
      },
      getHthApiPasswordCodeMasked: function (partyId, userName) {
        getHthApiPasswordCodeMaskedDeferred = $.Deferred();
        getHthApiPasswordCodeMasked(partyId, userName, getHthApiPasswordCodeMaskedDeferred);

        return getHthApiPasswordCodeMaskedDeferred;
      },
      revealHthApiPasswordCode: function (codeId) {
        revealHthApiPasswordCodeDeferred = $.Deferred();
        revealHthApiPasswordCode(codeId, revealHthApiPasswordCodeDeferred);

        return revealHthApiPasswordCodeDeferred;
      }
    };
  };

  return new UsersUpdateModel();
});
