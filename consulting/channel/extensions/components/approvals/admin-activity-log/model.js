define([
  "jquery",
  "baseService"
], function($, BaseService) {
  "use strict";

  const ActivityLogModel = function() {
    const baseService = BaseService.getInstance();
    let getTransactionListDeferred;
    const hthTaskIds = [
        "UAT_N_HUA_NEW",
        "UAT_N_HUA_EDT",
        "UAT_N_HUA_DEL"
      ],
      fetchTransactions = function(view, discriminator, fromDate, toDate, roleType) {
        return baseService.fetchWidget({
          url: "transactions?view={view}&discriminator={discriminator}&roleType={roleType}&fromDate={fromDate}&toDate={toDate}"
        }, {
          discriminator: discriminator,
          view: view,
          fromDate: fromDate,
          toDate: toDate,
          roleType: roleType
        });
      },
      mergeMisclassifiedHthTransactions = function(adminData, partyData) {
        // Historical HTH snapshots may have been created with the platform default discriminator
        // while ApprovalAssemblers was still cached. BCO Administrative Activity Log remains the
        // destination, and only those HTH task codes are recovered from PARTY_MAINTENANCE.
        const adminTransactions = adminData.transactionDTOs || [],
          knownTransactionIds = adminTransactions.reduce(function(result, transaction) {
            result[transaction.transactionId] = true;

            return result;
          }, {});

        (partyData.transactionDTOs || []).forEach(function(transaction) {
          if (hthTaskIds.indexOf(transaction.transactionName) > -1 &&
              transaction.discriminator === "PARTY_MAINTENANCE" &&
              !knownTransactionIds[transaction.transactionId]) {
            adminTransactions.push(transaction);
            knownTransactionIds[transaction.transactionId] = true;
          }
        });

        adminData.transactionDTOs = adminTransactions;

        return adminData;
      },
      getTransactionList = function(deferred, view, fromDate, toDate,roleType) {
        const adminPromise = fetchTransactions(view, "ADMIN_MAINTENANCE", fromDate, toDate, roleType),
          partyPromise = fetchTransactions(view, "PARTY_MAINTENANCE", fromDate, toDate, roleType).catch(function() {
            return {
              transactionDTOs: []
            };
          });

        Promise.all([adminPromise, partyPromise]).then(function(responses) {
          deferred.resolve(mergeMisclassifiedHthTransactions(responses[0], responses[1]));
        }).catch(function(error) {
          deferred.reject(error);
        });
      };

    return {
      getTransactionList: function(view, fromDate, toDate,roleType) {
        getTransactionListDeferred = $.Deferred();
        getTransactionList(getTransactionListDeferred, view, fromDate, toDate,roleType);

        return getTransactionListDeferred;
      }
    };
  };

  return new ActivityLogModel();
});
