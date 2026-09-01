define([
  "jquery",
  "baseService"
], function($, BaseService) {
  "use strict";

  const ActivityLogModel = function() {
    const baseService = BaseService.getInstance();
    let getTransactionListDeferred;
    const getTransactionList = function(deferred, view, fromDate, toDate,roleType) {
      const url = "transactions?view={view}&discriminator={discriminator}&roleType={roleType}&fromDate={fromDate}&toDate={toDate}",
        options = {
          url: url,
          success: function(data) {
            deferred.resolve(data);
          }
        },
        params = {
          discriminator: "ADMIN_MAINTENANCE",
          view: view,
          fromDate: fromDate,
          toDate: toDate,
          roleType: roleType
        };

      baseService.fetch(options, params);
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
