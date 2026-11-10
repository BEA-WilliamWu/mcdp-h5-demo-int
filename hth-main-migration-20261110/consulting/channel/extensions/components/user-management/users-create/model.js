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
  const UsersCreateModel = function () {
    /**
     * baseService instance through which all the rest calls will be made.
     *
     * @attribute baseService
     * @private
     */
    const baseService = BaseService.getInstance();
    let params;
    const model = function () {
      this.addressData = {
        postalAddress: {
          line1: "",
          line2: "",
          line3: "",
          line4: "",
          line5: "",
          line6: "",
          line7: "",
          line8: "",
          line9: "",
          line10: "",
          line11: "",
          line12: "",
          city: "",
          state: "",
          country: "",
          zipCode: "",
          branch: "",
          branchName: ""
        }
      };

      this.partyDetails = {
        partyId: null,
        partyName: null,
        partyDetailsFetched: false,
        partyFirstName: null,
        partyLastName: null,
        party: {
          value: null,
          displayValue: null
        }
      };
    };
    let modelInitialized = false,
      /* variable to make sure that in case there is no change
       * in model no additional fetch requests are fired.*/
      /**
       * This function fires a GET request to party details of specific party ID
       * and delegates control to the successhandler along with response data
       * once the details are successfully fetched
       *
       * @function fetchPartyDetails
       * @memberOf UsersCreateModel
       * @param {String} partyId      - String indicating the party id whose  details are to be fetched
       * @example UsersCreateModel.fetchPartyDetails(data.partyId());
       */
      fetchPartyDetailsDeferred;
    const fetchPartyDetails = function (partyId, deferred) {
      const options = {
        url: "administration/parties/{partyId}",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options, partyId);
    };
    let checkIfUserExistsDeferred;
    const checkIfUserExists = function (username, deferred) {
      const params = { username: username },
        options = {
          url: "users/{username}/exists",
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
     * This function fires a GET request to fetch the user groups options
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchUserGroupOptions
     * @memberOf UsersCreateModel
     * @example UsersCreateModel.fetchUserGroupOptions();
     */
    let fetchUserGroupOptionsDeferred;
    const fetchUserGroupOptions = function (deferred) {
      const options = {
        url: "enterpriseRoles?isLocal=true",
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
     * This function fires a POST request to create user
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function createUser
     * @memberOf UsersCreateModel
     * @param {String} payload  -  indicates the form details filled by admin
     * @example UsersCreateModel.createUser(data);
     */
    let createUserDeferred;
    const createUser = function (payload, deferred) {
      const options = {
        url: "users",
        data: payload,
        success: function (data, status, jqXhr) {
          deferred.resolve(data, status, jqXhr);
        },
        error: function (data, status, jqXhr) {
          deferred.reject(data, status, jqXhr);
        }
      };

      baseService.add(options);
    };
    /**
     * This function fires a POST request to update user details
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function updateUser
     * @memberOf UsersCreateModel
     * @param {String} payload  -  indicates the form details to be updated filled by admin
     * @example UsersCreateModel.createUser(data);
     */
    let updateUserDeferred;
    const updateUser = function (payload, id, deferred) {
      const options = {
        url: "users/" + id,
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
    /**
     * This function fires a GET request to get the list of user types
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchUserType
     * @memberOf UsersCreateModel
     * @example UsersCreateModel.fetchUserType(data);
     */
    let fetchUserTypeDeferred;
    const fetchUserType = function (deferred) {
      const options = {
        url: "enumerations/userType",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
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
    let fetchCityDeferred;
    const fetchCity = function (selectedCountry, deferred) {
      const params = {
        countryID: selectedCountry
      },
        options = {
          url: "locations/country/{countryID}/city",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

      baseService.fetch(options, params);
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
    let showPartyDetailsDeferred;
    const showPartyDetails = function (deferred) {
      const options = {
        url: "me",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };
    let fetchPartyDetailsByNameDeferred;
    const fetchPartyDetailsByName = function (deferred) {
      const options = {
        url: "me/party",
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
      const options = {
        url: "applicationRoles?filterSegmentedRole=true&accessPointType=INT&enterpriseRole=" + enterpriseRoleId,
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
     * This function read the role preferences corresponding to the roleId
     * @params {deferred} - object to trach completion of Batch call
     * @function fetchRolePreference
     * @memberOf ExclusionModel
     **/
    let fetchRolePreferenceDeferred;
    const fetchRolePreference = function (roleId, deferred) {
      const options = {
        url: "rolePreferences/" + roleId + "/preferences",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options);
    };
    /**
     * This function read the party preferences
     * @params {deferred} - object to trach completion of network call
     * @function fetchPartyPreferences
     * @memberOf ExclusionModel
     **/
    let fetchPartyPreferencesDeferred;
    const fetchPartyPreferences = function (partyId, deferred) {
      const options = {
        url: "parties/" + partyId + "/preferences",
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.fetch(options);
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

    let fetchMeWithPartyDeferred;
    const fetchMeWithParty = function (deferred) {
      const options = {
        url: "me/party",
        success: function (data) {
          deferred.resolve(data);
        },
        error: function (data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    },
    /**
     * This function fires a POST request to create user extension
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function createUserExtension
     * @memberOf UsersCreateModel
     * @param {String} payload  -  indicates the form details filled by admin
     * @example UsersCreateModel.createUserExtension(data);
     */
      createUserExtension = function (BMTransactionRefNo, payload, successHandler) {
       const options = {
         url: "userExtensionData",
         version: "cz/v1",
         data: payload,
         headers: {
          "MULTI_BM-TXN-REFERENCES": BMTransactionRefNo
         },
         success: function (data, status, jqXhr) {
          successHandler(data, status, jqXhr);
         }
       };

       baseService.add(options);
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

     let addSignerDeferred;
     const addSigner = function (subOption, BMTransactionRefNo, payload, deferred) {
       const params = {
        subOption : subOption
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

     let fetchDayZeroUserIdsDeferred;
     const fetchDayZeroUserIds = function (userType, partyId, moduleType, deferred) {
       const params = {
        userType: userType,
        partyId: partyId,
        moduleType:moduleType
       },
       options = {
         url: "useridmaintenance/getids?idType={userType}&partyId={partyId}&moduleType={moduleType}&txnType=UM",
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

    /**
     * This function fires a POST request to generate HTH API Password Code.
     * The plain-text code is returned only in this response.
     *
     * @function generateHthApiPasswordCode
     * @memberOf UsersCreateModel
     * @param {String} partyId  - party ID (cdcNo)
     * @param {String} userName - user name (username@partyId)
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
     * This function fires a GET request to get masked HTH API Password Code info.
     * Never returns plain-text code.
     *
     * @function getHthApiPasswordCodeMasked
     * @memberOf UsersCreateModel
     * @param {String} partyId  - party ID (cdcNo)
     * @param {String} userName - user name (username@partyId)
     */
    let getHthApiPasswordCodeMaskedDeferred;
    const getHthApiPasswordCodeMasked = function (partyId, userName, deferred) {
      const params = {
        partyId: partyId,
        userName: userName
      },
      options = {
        url: "hostToHostApiPassword/masked",
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
     * This function fires a POST request to reveal HTH API Password Code (audit logged).
     *
     * @function revealHthApiPasswordCode
     * @memberOf UsersCreateModel
     * @param {String} codeId - code ID to reveal
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

    let checkApprovalDeferred;
    const checkApproval = function (cdcId, userGroupIds, userId, deferred) {
      const params = {
       cdcId: cdcId,
       userGroupIds: userGroupIds,
       userId: userId
      },
      options = {
        url: "userGroups/checkTxn?cdcId={cdcId}&userGroupIds={userGroupIds}&method=CREATE&userId={userId}",
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

     let addUserIdMaintenanceDeferred;
     const addUserIdMaintenance = function (BMTransactionRefNo, payload, deferred) {
       const params = {
         payload: payload
         },
         options = {
           url: "useridmaintenance/addUserIdMaintenance",
           data: payload,
           version:"cz/v1",
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
      init: function () {
        modelInitialized = true;

        return modelInitialized;
      },
      getNewModel: function () {
        return new model();
      },
      fetchPartyDetails: function (partyId) {
        fetchPartyDetailsDeferred = $.Deferred();
        fetchPartyDetails(partyId, fetchPartyDetailsDeferred);

        return fetchPartyDetailsDeferred;
      },
      fetchUserGroupOptions: function () {
        fetchUserGroupOptionsDeferred = $.Deferred();
        fetchUserGroupOptions(fetchUserGroupOptionsDeferred);

        return fetchUserGroupOptionsDeferred;
      },
      createUser: function (payload) {
        createUserDeferred = $.Deferred();
        createUser(payload, createUserDeferred);

        return createUserDeferred;
      },
      updateUser: function (payload, id) {
        updateUserDeferred = $.Deferred();
        updateUser(payload, id, updateUserDeferred);

        return updateUserDeferred;
      },
      fetchUserType: function () {
        fetchUserTypeDeferred = $.Deferred();
        fetchUserType(fetchUserTypeDeferred);

        return fetchUserTypeDeferred;
      },
      fetchCountry: function () {
        fetchCountryDeferred = $.Deferred();
        fetchCountry(fetchCountryDeferred);

        return fetchCountryDeferred;
      },
      fetchCity: function (countryID) {
        fetchCityDeferred = $.Deferred();
        fetchCity(countryID, fetchCityDeferred);

        return fetchCityDeferred;
      },
      fetchUserLimitOptions: function (businessEntity, assignableEntitiesData) {
        fetchUserLimitOptionsDeferred = $.Deferred();
        fetchUserLimitOptions(fetchUserLimitOptionsDeferred, businessEntity, assignableEntitiesData);

        return fetchUserLimitOptionsDeferred;
      },
      fetchUserSegments: function (searchParams) {
        fetchUserSegmentsDeferred = $.Deferred();
        fetchUserSegments(searchParams, fetchUserSegmentsDeferred);

        return fetchUserSegmentsDeferred;
      },
      showPartyDetails: function () {
        showPartyDetailsDeferred = $.Deferred();
        showPartyDetails(showPartyDetailsDeferred);

        return showPartyDetailsDeferred;
      },
      fetchPartyDetailsByName: function () {
        fetchPartyDetailsByNameDeferred = $.Deferred();
        fetchPartyDetailsByName(fetchPartyDetailsByNameDeferred);

        return fetchPartyDetailsByNameDeferred;
      },
      fetchChildRole: function (enterpriseRoleId) {
        fetchChildRoleDeferred = $.Deferred();
        fetchChildRole(enterpriseRoleId, fetchChildRoleDeferred);

        return fetchChildRoleDeferred;
      },
      fetchRolePreference: function (roleId) {
        fetchRolePreferenceDeferred = $.Deferred();
        fetchRolePreference(roleId, fetchRolePreferenceDeferred);

        return fetchRolePreferenceDeferred;
      },
      fetchPartyPreferences: function (partyId) {
        fetchPartyPreferencesDeferred = $.Deferred();
        fetchPartyPreferences(partyId, fetchPartyPreferencesDeferred);

        return fetchPartyPreferencesDeferred;
      },
      checkIfUserExists: function (username) {
        checkIfUserExistsDeferred = $.Deferred();
        checkIfUserExists(username, checkIfUserExistsDeferred);

        return checkIfUserExistsDeferred;
      },
      fetchEntitites: function () {
        fetchEntititesDeferred = $.Deferred();
        fetchEntitites(fetchEntititesDeferred);

        return fetchEntititesDeferred;
      },
      fetchMeWithParty: function () {
        fetchMeWithPartyDeferred = $.Deferred();
        fetchMeWithParty(fetchMeWithPartyDeferred);

        return fetchMeWithPartyDeferred;
      },
      fetchAccess: function (searchParams) {
        fetchAccessDeferred = $.Deferred();
        fetchAccess(searchParams, fetchAccessDeferred);

        return fetchAccessDeferred;
      },
      fetchUserGroupList: function (partyId) {
        return baseService.fetch({
          url: "userGroups?partyId={partyId}"
        }, {
          partyId: partyId
        });
      },
      fetchDayOneConfig: function (configId) {
        return baseService.fetch({
          url: "configurations/base/dayoneconfig/properties/{configId}"
        }, {
          configId: configId
        });
      },
      createUserExtension: function (BMTransactionRefNo, payload, successHandler) {
        createUserExtension(BMTransactionRefNo, payload, successHandler);
      },
      fetchUserIdMaintenance: function (partyId) {
        fetchUserIdMaintenanceDeferred = $.Deferred();
        fetchUserIdMaintenance(partyId, fetchUserIdMaintenanceDeferred);

        return fetchUserIdMaintenanceDeferred;
      },
      fetchCountryCodes: function () {
        fetchCountryCodesDeferred = $.Deferred();
        fetchCountryCodes(fetchCountryCodesDeferred);

        return fetchCountryCodesDeferred;
      },
      addSigner: function (subOption, BMTransactionRefNo, payload) {
        addSignerDeferred = $.Deferred();
        addSigner(subOption, BMTransactionRefNo, payload, addSignerDeferred);

        return addSignerDeferred;
      },
      fetchDayZeroUserIds: function (userType, partyId, moduleType) {
        fetchDayZeroUserIdsDeferred = $.Deferred();
        fetchDayZeroUserIds(userType, partyId, moduleType, fetchDayZeroUserIdsDeferred);

        return fetchDayZeroUserIdsDeferred;
      },
      validateCustDetails: function (documentType, documentCountry, documentId) {
        validateCustDetailsDeferred = $.Deferred();
        validateCustDetails(documentType, documentCountry, documentId, validateCustDetailsDeferred);

        return validateCustDetailsDeferred;
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
      },
      checkApproval: function (cdcId,userGroupIds,userId){
        checkApprovalDeferred = $.Deferred();
        checkApproval(cdcId,userGroupIds,userId,checkApprovalDeferred);

        return checkApprovalDeferred;
      },
      addUserIdMaintenance: function (BMTransactionRefNo, payload) {
        addUserIdMaintenanceDeferred = $.Deferred();
        addUserIdMaintenance(BMTransactionRefNo, payload, addUserIdMaintenanceDeferred);

        return addUserIdMaintenanceDeferred;
      }
    };
  };

  return new UsersCreateModel();
});
