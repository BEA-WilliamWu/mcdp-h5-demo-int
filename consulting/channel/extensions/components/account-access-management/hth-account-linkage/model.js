define([
    "jquery",
    "baseService",
    "baseModel",
    "../summary/model"
], function ($, BaseService, BaseModel, AccountAccessModel) {
    "use strict";

    /*
     * HTH access transport wrapper. OBDX can return a successful HTTP status with a failed business
     * status, so both transport and response status are normalized into the same Deferred failure
     * path consumed by the view models. Approval-required is the one exception: older OBDX releases
     * report a successfully staged maker request as HTTP 400 with DIGX_APPROVAL_REQUIRED. That
     * transport response is normalized to the standard HTTP 202 confirmation contract.
     */
    const APPROVAL_REQUIRED_CODE = "DIGX_APPROVAL_REQUIRED",
        APPROVAL_ACCEPTED_STATUS = 202,
        baseService = BaseService.getInstance(),
        baseModel = BaseModel.getInstance(),
        responseBody = function (error) {
            return error && error.responseJSON ? error.responseJSON : error;
        },
        isApprovalRequiredResponse = function (error) {
            const response = responseBody(error) || {},
                status = response.status || {},
                message = response.message || status.message;

            return !!(message && message.code === APPROVAL_REQUIRED_CODE);
        },
        normalizeApprovalRequiredResponse = function (error) {
            const response = Object.assign({}, responseBody(error) || {}),
                status = Object.assign({}, response.status || {}),
                access = response.access || {},
                referenceNumber = response.referenceNumber || status.referenceNumber
                    || access.referenceNumber || status.externalReferenceNumber;

            status.result = status.result || response.result || "SUCCESSFUL";

            status.message = status.message || response.message || {
                code: APPROVAL_REQUIRED_CODE,
                type: "INFO"
            };

            if (status.receiptAvailable === undefined) {
                status.receiptAvailable = false;
            }

            // confirm-screen uses the platform reference as transactionId when the checker
            // selects its quick-Approve action.  Older HTH responses expose that reference on
            // the nested access DTO or as externalReferenceNumber, while BCO exposes the same
            // value as status.referenceNumber.  Publish the canonical BCO shape to the UI.
            if (referenceNumber) {
                response.referenceNumber = referenceNumber;
                status.referenceNumber = referenceNumber;
            }

            response.status = status;
            baseModel.injectProps(response, "getResponseStatus", APPROVAL_ACCEPTED_STATUS);

            return response;
        },
        isFailureResponse = function (data) {
            const result = data && data.status && data.status.result
                ? String(data.status.result).toUpperCase() : "";

            return result === "FAILED" || result === "FAILURE";
        },
        request = function (options) {
            const deferred = $.Deferred(),
                requestOptions = Object.assign({}, options, {
                    version: "cz/v1",
                    success: function (data, status, jqXhr) {
                        if (isFailureResponse(data)) {
                            deferred.reject(data);

                            return;
                        }

                        deferred.resolve(data, status, jqXhr);
                    },
                    error: function (error) {
                        if (isApprovalRequiredResponse(error)) {
                            const normalizedResponse = normalizeApprovalRequiredResponse(error);

                            // Keep the jQuery Deferred callback contract intact: the third argument
                            // is the transport jqXHR, while the first argument is the normalized
                            // approval response consumed by the confirmation screen.
                            deferred.resolve(normalizedResponse, "success", error);

                            return;
                        }

                        deferred.reject(error);
                    }
                }),
                transportPromise = requestOptions.data
                    ? baseService.add(requestOptions) : baseService.fetch(requestOptions);

            // BaseService exposes a native Promise and also invokes the callback handlers above.
            // This wrapper deliberately returns its jQuery Deferred, so consume the native rejection
            // after the callback has routed it; otherwise an expected approval-required HTTP 400 is
            // reported as an unhandled Promise even though the maker request was staged correctly.
            if (transportPromise && typeof transportPromise.catch === "function") {
                transportPromise.catch(function () {
                    return null;
                });
            }

            return deferred;
        },
        readValue = function (value) {
            return value && typeof value === "object"
                ? value.value || value.displayValue || "" : value || "";
        },
        normalizeAccountType = function (value) {
            const accountType = String(value || "").toUpperCase();

            if (accountType === "TRD" || accountType === "TERM_DEPOSIT") {
                return "TD";
            }

            if (accountType === "DEMAND_DEPOSIT") {
                return "CSA";
            }

            return accountType;
        },
        accountKey = function (account) {
            return `${normalizeAccountType(account && account.accountType)}:${String(
                readValue(account && account.accountNumber))}`;
        },
        findBcoPartyAccounts = function (bcoResponse, context) {
            const partyRows = bcoResponse && Array.isArray(bcoResponse.accounts)
                    ? bcoResponse.accounts : [],
                accessPartyId = String(context.accessPartyId || ""),
                exactMatch = partyRows.filter(function (row) {
                    return String(readValue(row && row.party)) === accessPartyId;
                })[0];

            if (exactMatch) {
                return exactMatch;
            }

            // Older AccountAccess responses omit party on the primary company row. For RELATED
            // access that non-linkage row is still unambiguous. ASSOCIATED access must always
            // match accessPartyId so an account from another company can never be displayed.
            if (String(context.linkageType || "").toUpperCase() === "RELATED") {
                return partyRows.filter(function (row) {
                    return ["LINKAGE", "USERLINKAGE"].indexOf(String(
                        (row && row.accessLevel) || "").toUpperCase()) === -1;
                })[0] || (partyRows.length === 1 ? partyRows[0] : null);
            }

            return null;
        },
        mergeBcoAccountCatalogue = function (hthResponse, bcoResponse, context) {
            hthResponse = hthResponse || {};

            const access = hthResponse.access || {},
                hthAccounts = Array.isArray(access.accounts) ? access.accounts
                    : Array.isArray(hthResponse.eligibleAccounts)
                        ? hthResponse.eligibleAccounts : [],
                eligibleApis = Array.isArray(hthResponse.eligibleApis)
                    ? hthResponse.eligibleApis : [],
                existingByKey = {},
                bcoPartyAccounts = findBcoPartyAccounts(bcoResponse, context),
                bcoAccounts = bcoPartyAccounts && Array.isArray(bcoPartyAccounts.accountsList)
                    ? bcoPartyAccounts.accountsList : [],
                seen = {},
                mergedAccounts = [];

            hthAccounts.forEach(function (account) {
                existingByKey[accountKey(account)] = account;
            });

            // AccountAccess is the same catalogue used by BCO. Preserve its row order and display
            // metadata exactly; HTH contributes only the API catalogue and effective selections.
            bcoAccounts.forEach(function (bcoAccount) {
                const accountType = normalizeAccountType(bcoAccount && bcoAccount.accountType),
                    accountNumber = String(readValue(bcoAccount && bcoAccount.accountNumber)),
                    key = `${accountType}:${accountNumber}`;

                if ((accountType !== "CSA" && accountType !== "TD")
                        || !accountNumber || seen[key]) {
                    return;
                }

                seen[key] = true;

                const existing = existingByKey[key] || {},
                    accountNumberObject = bcoAccount.accountNumber,
                    accountNumberDisplay = accountNumberObject
                        && typeof accountNumberObject === "object"
                        ? accountNumberObject.displayValue : "",
                    sourceApis = Array.isArray(existing.apiServices)
                        ? existing.apiServices : eligibleApis;

                mergedAccounts.push(Object.assign({}, existing, {
                    accountNumber: accountNumber,
                    accountNumberDisplay: accountNumberDisplay || accountNumber,
                    maskedAccountNumber: existing.maskedAccountNumber
                        || accountNumberDisplay || "",
                    accountType: accountType,
                    currency: bcoAccount.currencyCode || bcoAccount.currency
                        || existing.currency || "",
                    displayName: bcoAccount.displayName || existing.displayName || "",
                    selected: existing.selected === true,
                    displayOrder: mergedAccounts.length,
                    apiServices: sourceApis.map(function (api) {
                        return Object.assign({}, api, {
                            selected: api.selected === true
                        });
                    })
                }));
            });

            access.accounts = mergedAccounts;
            hthResponse.access = access;
            hthResponse.eligibleAccounts = mergedAccounts;

            return hthResponse;
        },
        readBcoAccounts = function (context) {
            const deferred = $.Deferred(),
                userId = String(context.username || "").trim(),
                params = {
                    partyId: context.partyId,
                    userId: userId
                };

            if (!userId) {
                deferred.reject({
                    message: "The selected user's BCO identifier is missing."
                });

                return deferred;
            }

            // Invoke the BCO model itself instead of maintaining a second copy of its URL or
            // parameter logic. The userId is essential: the party-only call returns the complete
            // corporate catalogue and therefore displays more accounts than BCO's user page.
            AccountAccessModel.readAllUserAccountDetails(params.partyId, params.userId)
                .done(function (data) {
                    if (isFailureResponse(data)) {
                        deferred.reject(data);

                        return;
                    }

                    deferred.resolve(data);
                })
                .fail(function (error) {
                    deferred.reject(error);
                });

            return deferred;
        };

    return {
        read: function (context) {
            const deferred = $.Deferred();

            request({
                url: baseModel.QueryParams.add("hostToHostUserAccess/accounts", {
                    partyId: context.partyId,
                    closeId: context.closeId,
                    accessPartyId: context.accessPartyId,
                    linkageType: context.linkageType
                })
            }).done(function (hthResponse) {
                readBcoAccounts(context).done(function (bcoResponse) {
                    try {
                        deferred.resolve(mergeBcoAccountCatalogue(
                            hthResponse, bcoResponse, context));
                    } catch (error) {
                        deferred.reject(error);
                    }
                }).fail(function (error) {
                    deferred.reject(error);
                });
            }).fail(function (error) {
                deferred.reject(error);
            });

            return deferred;
        },
        save: function (payload, action) {
            return request({
                url: "hostToHostUserAccess/" + (action === "EDIT"
                    ? "edit" : action === "DELETE" ? "delete" : "submit"),
                data: JSON.stringify(payload)
            });
        }
    };
});
