define([
  "baseService"
], function (BaseService) {
  "use strict";

  const pendingApprovalList = function () {
    const baseService = BaseService.getInstance();

    return {
      getCountForApproval: function (roleType) {
        return baseService.fetchWidget({
          url: "transactions/count?countFor=approval&roleType={roleType}",
          mockedUrl:"framework/json/design-dashboard/approval/count.json"
        }, {
          roleType: roleType
        });
      },
      getTransactionData: function (discriminator, roleType) {
        return baseService.fetchWidget({
          url: "transactions?view={view}&discriminator={discriminator}&roleType={roleType}",
          mockedUrl:"framework/json/design-dashboard/approval/transactions.json"
        }, {
          discriminator: discriminator,
          view: "approval",
          roleType: roleType
        });
      }
    };
  };

  return new pendingApprovalList();
});
