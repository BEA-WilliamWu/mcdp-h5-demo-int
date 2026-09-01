define([
  "baseService"
], function (BaseService) {
  "use strict";

  const pendingApprovalList = function () {
    const baseService = BaseService.getInstance(),
      hthTaskIds = [
        "UAT_N_HUA_NEW",
        "UAT_N_HUA_EDT",
        "UAT_N_HUA_DEL"
      ],
      isHthUserAccess = function (transaction) {
        return transaction && hthTaskIds.indexOf(transaction.transactionName) > -1;
      },
      isMisclassifiedHthUserAccess = function (transaction) {
        return isHthUserAccess(transaction) && transaction.discriminator === "PARTY_MAINTENANCE";
      },
      getTransactions = function (discriminator, roleType) {
        return baseService.fetchWidget({
          url: "transactions?view={view}&discriminator={discriminator}&roleType={roleType}",
          mockedUrl:"framework/json/design-dashboard/approval/transactions.json"
        }, {
          discriminator: discriminator,
          view: "approval",
          roleType: roleType
        });
      },
      mergeMisclassifiedHthTransactions = function (adminData, partyData) {
        // BCO administrative maintenance is the canonical list. Include only HTH snapshots made
        // before the approval-assembler configuration cache was refreshed; do not mix ordinary
        // corporate PARTY_MAINTENANCE transactions into the administrative table.
        const adminTransactions = adminData.transactionDTOs || [],
          knownTransactionIds = adminTransactions.reduce(function (result, transaction) {
            result[transaction.transactionId] = true;

            return result;
          }, {});

        (partyData.transactionDTOs || []).filter(isMisclassifiedHthUserAccess).forEach(function (transaction) {
          if (!knownTransactionIds[transaction.transactionId]) {
            adminTransactions.push(transaction);
            knownTransactionIds[transaction.transactionId] = true;
          }
        });

        adminData.transactionDTOs = adminTransactions;

        return adminData;
      },
      addMisclassifiedHthCount = function (countData, partyData) {
        // Keep the Administrative Maintenance badge consistent with the merged table during the
        // migration window. Correctly classified snapshots are already present in the base count.
        const hthCount = (partyData.transactionDTOs || []).filter(isMisclassifiedHthUserAccess).length;

        if (!hthCount) {
          return countData;
        }

        countData.countDTOList = countData.countDTOList || [];

        let adminCount = countData.countDTOList.filter(function (count) {
          return count.transactionType === "ADMIN_MAINTENANCE";
        })[0];

        if (!adminCount) {
          adminCount = {
            transactionType: "ADMIN_MAINTENANCE",
            pendingApproval: 0
          };

          countData.countDTOList.push(adminCount);
        }

        adminCount.pendingApproval = (adminCount.pendingApproval || 0) + hthCount;

        return countData;
      };

    return {
      getCountForApproval: function (roleType) {
        const countPromise = baseService.fetchWidget({
          url: "transactions/count?countFor=approval&roleType={roleType}",
          mockedUrl:"framework/json/design-dashboard/approval/count.json"
        }, {
          roleType: roleType
        }),
          fallbackPromise = getTransactions("PARTY_MAINTENANCE", roleType).catch(function () {
            return {
              transactionDTOs: []
            };
          });

        return Promise.all([countPromise, fallbackPromise]).then(function (responses) {
          return addMisclassifiedHthCount(responses[0], responses[1]);
        });
      },
      getTransactionData: function (discriminator, roleType) {
        const primaryPromise = getTransactions(discriminator, roleType);

        if (discriminator !== "ADMIN_MAINTENANCE") {
          return primaryPromise;
        }

        const fallbackPromise = getTransactions("PARTY_MAINTENANCE", roleType).catch(function () {
          return {
            transactionDTOs: []
          };
        });

        return Promise.all([primaryPromise, fallbackPromise]).then(function (responses) {
          return mergeMisclassifiedHthTransactions(responses[0], responses[1]);
        });
      }
    };
  };

  return new pendingApprovalList();
});
