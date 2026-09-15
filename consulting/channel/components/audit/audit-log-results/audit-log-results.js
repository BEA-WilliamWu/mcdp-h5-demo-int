define([
    "ojs/ojcore",
    "knockout",
    "ojL10n!resources/nls/audit",
    "ojL10n!extensions/resources/nls/hth-audit",
    "ojs/ojtreeview",
    "ojs/ojjsontreedatasource",
    "framework/elements/api/page-section/loader"
], function (oj, ko, resourceBundle, hthBundle) {
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

        self.hthNls = hthBundle;
        self.hthAuditRows = [];

        const auditRecord = self.params.auditDTO || self.params,
            detailList = auditRecord.auditDetailsDTOList || [],
            summaryFields = ["operation", "actorUserId", "targetUserId", "partyId", "targetUnit", "oldUserChannelType",
                "newUserChannelType", "approvalReference", "referenceNumber", "occurredAt", "businessOutcome",
                "errorCode", "codeId", "previousCodeId", "purpose", "codeStatus", "requestId", "idempotentReplay",
                "accessPartyId", "linkageType", "effectiveChange"],
            addAuditRow = function (field, value) {
                if (value !== undefined && value !== null && typeof value !== "object") {
                    self.hthAuditRows.push({
                        label: hthBundle[field],
                        value: String(value)
                    });
                }
            };

        detailList.forEach(function (detail) {
            const summary = detail.request && detail.request.hthOnboarding;

            if (detail.operationName === "HTH_ONBOARDING_791" && summary && summary.schemaVersion === "1") {
                summaryFields.forEach(function (field) {
                    addAuditRow(field, summary[field]);
                });

                (summary.accessChanges || []).forEach(function (change) {
                    ["change", "accountType", "maskedAccountNumber", "apiMasterId"].forEach(function (field) {
                        addAuditRow(field, change[field]);
                    });
                });
            }
        });

        self.downloadHthAudit = function () {
            const csvCell = function (value) {
                    const text = String(value),
                        safeText = /^[\s]*[=+@-]/.test(text) ? "'" + text : text;

                    return "\"" + safeText.replace(/"/g, "\"\"") + "\"";
                },
                rows = [[hthBundle.field, hthBundle.value],
                    [hthBundle.auditId, auditRecord.id || ""],
                    [hthBundle.referenceNumber, auditRecord.referenceNo || ""],
                    [hthBundle.businessOutcome, auditRecord.status || ""]],
                link = document.createElement("a");

            self.hthAuditRows.forEach(function (row) {
                rows.push([row.label, row.value]);
            });

            const content = "\uFEFF" + rows.map(function (row) {
                    return row.map(csvCell).join(",");
                }).join("\r\n"),
                url = URL.createObjectURL(new Blob([content], {type: "text/csv;charset=utf-8"}));

            link.href = url;
            link.download = "HTH-Audit.csv";
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            setTimeout(function () {
                URL.revokeObjectURL(url);
            }, 0);
        };

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