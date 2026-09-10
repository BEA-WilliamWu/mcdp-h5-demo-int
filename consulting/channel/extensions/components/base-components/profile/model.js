define(["baseService", "jquery"], function (BaseService, $) {
  "use strict";

  /**
   * This file contains the Tech Agnostic model
   * consisting of all the REST services APIs for the generic component row.
   *
   * @namespace PartyModel~model
   * @class PartyModel
   * @extends BaseService {@link BaseService}
   */
  const PartyModel = function () {
    /**
     * baseService instance through which all the rest calls will be made.
     *
     * @attribute baseService
     * @type {Object} BaseService Instance
     * @private
     */
    const baseService = BaseService.getInstance();
    let params,
      /**
       * This function fires a GET request to fetch the address associated with 'me' party id
       * and delegates control to the successhandler along with response data
       * once the details are successfully fetched
       *
       * @function fetchAddressInfo
       * @memberOf PartyModel
       * @example PartyModel.fetchAddressInfo();
       */
      fetchAddressInfoDeferred;
    const fetchAddressInfo = function (deferred) {
      const options = {
        url: "parties/me/addresses",
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
     * This function fires a GET request to fetch the contact points of specific party id
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchContactInfo
     * @memberOf PartyModel
     * @param {String} partyId  - String indicating the party id whose contact points are to be fetched
     * @example PartyModel.fetchContactInfo(self.userDataFromParent.party.id.value)
     */
    let fetchContactInfoDeferred;
    const fetchContactInfo = function (partyId, deferred) {
      params = {
        partyId: partyId
      };

      const options = {
        url: "parties/{partyId}/contactPoints",
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
     * This function fires a GET request to fetch the country list
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchCountryDeferred
     * @memberOf PartyModel
     * @example PartyModel.fetchCountryDeferred()
     */
    let fetchCountryDeferred;
    const fetchCountry = function(deferred) {
      const options = {
        url: "enumerations/country",
        success: function(data) {
          deferred.resolve(data);
        },
        error: function(data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };
    /**
     * This function fires a GET request to fetch the fields that can be updated
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchProfileConfigDeferred
     * @memberOf PartyModel
     * @example PartyModel.fetchProfileConfigDeferred()
     */
    let fetchProfileConfigDeferred;
    const fetchProfileConfig = function(deferred) {
      const options = {
        url: "profileConfig",
        success: function(data) {
          deferred.resolve(data);
        },
        error: function(data) {
          deferred.reject(data);
        }
      };

      baseService.fetch(options);
    };
    /**
     * This function fires a GET request to fetch the contact points of specific party id
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchContactInfo
     * @memberOf PartyModel
     * @param {String} partyId  - String indicating the party id whose contact points are to be fetched
     * @example PartyModel.fetchContactInfo(self.userDataFromParent.party.id.value)
     */
    let updateUserProfileDeferred;
    const updateUserProfile = function (payload, deferred) {
      const options = {
        url: "me/party",
        data: payload,
        showInModalWindow : true,
        success: function (data) {
          deferred.resolve(data);
        }
      };

      baseService.update(options);
    };
    /**
     * This function fires a GET request to fetch the login time
     * and delegates control to the successhandler along with response data
     * once the details are successfully fetched
     *
     * @function fetchLastLoginTime
     * @memberOf PartyModel
     * @example ProductService.fetchLastLoginTime();
     */
    let fetchPartyDeferred;
    const fetchParty = function (deferred) {
        const options = {
          url: "me/party",
          success: function (data) {
            deferred.resolve(data);
          },
          error: function (data) {
            deferred.reject(data);
          }
        };

        baseService.fetch(options, params);
      },
      downloadPartyDetails = function (userType) {
        const options = {
          url: "me/{userType}?media=text/csv&mediaFormat=csv"
        };

        baseService.downloadFile(options, {
          userType: userType
        });
      },
      logOut = function () {
        const options = {
          url: "session",
          success: function () {
            const form = document.createElement("form");

            form.action = "/logout.";
            document.body.appendChild(form);
            form.submit();
          }
        };

        baseService.remove(options);
      };

    return {
      fetchAddressInfo: function () {
        fetchAddressInfoDeferred = $.Deferred();
        fetchAddressInfo(fetchAddressInfoDeferred);

        return fetchAddressInfoDeferred;
      },
      fetchContactInfo: function (partyId) {
        fetchContactInfoDeferred = $.Deferred();
        fetchContactInfo(partyId, fetchContactInfoDeferred);

        return fetchContactInfoDeferred;
      },
      updateUserProfile: function (payload) {
        updateUserProfileDeferred = $.Deferred();
        updateUserProfile(payload, updateUserProfileDeferred);

        return updateUserProfileDeferred;
      },
      fetchParty: function () {
        fetchPartyDeferred = $.Deferred();
        fetchParty(fetchPartyDeferred);

        return fetchPartyDeferred;
      },
      fetchCountry: function() {
        fetchCountryDeferred = $.Deferred();
        fetchCountry(fetchCountryDeferred);

        return fetchCountryDeferred;
      },
      fetchProfileConfig: function() {
        fetchProfileConfigDeferred = $.Deferred();
        fetchProfileConfig(fetchProfileConfigDeferred);

        return fetchProfileConfigDeferred;
      },
      downloadPartyDetails: function (userType) {
        downloadPartyDetails(userType);
      },
      logOut: function () {
        logOut();
      }
    };
  };

  return new PartyModel();
});
