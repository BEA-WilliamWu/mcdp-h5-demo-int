define([
    "ojs/ojcore",
    "knockout",
    "ojL10n!resources/nls/audit",
    "ojs/ojtreeview",
    "ojs/ojjsontreedatasource",
    "framework/elements/api/page-section/loader"
], function (oj, ko, resourceBundle) {
    "use strict";

    return function (rootParams) {
        const self = this;
        let request, response, size;

        self.nls = resourceBundle;
        ko.utils.extend(self, rootParams.rootModel);
        rootParams.dashboard.headerName(self.nls.header.auditlogmaintenance);

        if(self.params.auditDetailsDTOList !== undefined) {
            size = self.params.auditDetailsDTOList.length;
        } else {
            size = self.params.auditDTO.auditDetailsDTOList.length;
        }

        for (let i = 0; i < size; i++) {
            if(self.params.auditDetailsDTOList !== undefined) {
                if (self.params.auditDetailsDTOList[i].auditType === "1") {
                    self.url = self.params.resolvedRequestUrl;
                    request = self.params.auditDetailsDTOList[i].request;
                    response = self.params.auditDetailsDTOList[i].response;
                }
            } else if (self.params.auditDTO.auditDetailsDTOList[i].auditType === "1") {
                    self.url = self.params.auditDTO.resolvedRequestUrl;
                    request = self.params.auditDTO.auditDetailsDTOList[i].request;
                    response = self.params.auditDTO.auditDetailsDTOList[i].response;
                }

        }

        const jsonRequestData = [], jsonResponseData = [],
        dataToBePassed = {
            backFlag: true,
            dateRange: rootParams.rootModel.params.dataToPass.dateRange,
            dateFlag: rootParams.rootModel.params.dataToPass.dateFlag,
            dateType: rootParams.rootModel.params.dataToPass.dateType,
            username: rootParams.rootModel.params.dataToPass.username,
            fromDateTime: rootParams.rootModel.params.dataToPass.fromDateTime,
            toDateTime: rootParams.rootModel.params.dataToPass.toDateTime,
            fromDate: rootParams.rootModel.params.dataToPass.fromDate,
            toDate: rootParams.rootModel.params.dataToPass.toDate,
            partyid: rootParams.rootModel.params.dataToPass.partyid,
            action: rootParams.rootModel.params.dataToPass.action,
            status: rootParams.rootModel.params.dataToPass.status,
            moreSearchOptions: rootParams.rootModel.params.dataToPass.moreSearchOptions,
            selectedUserType: rootParams.rootModel.params.dataToPass.selectedUserType,
            selectedActivity: rootParams.rootModel.params.dataToPass.selectedActivity,
            referenceNo: rootParams.rootModel.params.dataToPass.referenceNo,
            loadSearchedAudits: rootParams.rootModel.params.dataToPass.loadSearchedAudits,
            showUserData: rootParams.rootModel.params.dataToPass.showUserData,
            showPartyData: rootParams.rootModel.params.dataToPass.showPartyData,
            mainSearch: rootParams.rootModel.params.dataToPass.mainSearch,
            userType: rootParams.rootModel.params.dataToPass.userType
        };

        function jsonify(object, target) {
            let property;

            if (typeof object === "object") {
                for (property in object) {
                    if (object[property]) {
                        const wrapper = {
                            attr: {
                                id: rootParams.baseModel.incrementIdCount(),
                                title: property
                            }
                        };

                        if (object[property] instanceof Array || object[property] instanceof Object) {
                            wrapper.children = [];
                            jsonify(object[property], wrapper.children);
                        } else {
                            wrapper.attr.value = object[property];
                        }

                        target.push(wrapper);
                    }
                }
            } else {
                target.push({
                    attr: {
                        id: rootParams.baseModel.incrementIdCount(),
                        title: object
                    }
                });
            }
        }

        jsonify(request, jsonRequestData);
        jsonify(response, jsonResponseData);
        this.requestData = new oj.JsonTreeDataSource(jsonRequestData);
        this.responseData = new oj.JsonTreeDataSource(jsonResponseData);

        self.back = function () {
            rootParams.dashboard.loadComponent("audit-log", dataToBePassed, self);
        };
    };
});