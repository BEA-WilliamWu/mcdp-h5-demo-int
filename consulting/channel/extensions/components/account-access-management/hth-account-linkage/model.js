define([
    "jquery",
    "baseService",
    "baseModel",
    "../summary/model"
], function ($, BaseService, BaseModel, AccountAccessModel) {
    "use strict";

    /*
     * Keep the HTH request on the same BaseService context while the shared OMB/2FA component is
     * open. BaseService replays that exact context after OTP and adds the OMB transaction header;
     * 412/417 are therefore intermediate authentication challenges, not completed failures.
     *
     * Some deployed OBDX releases return a successfully staged approval as HTTP 400 with
     * DIGX_APPROVAL_REQUIRED. BCO treats that outcome as the normal HTTP 202 confirmation
     * contract, so HTH normalizes only that specific response after any OMB/2FA replay completes.
     * The follow-up lookup resolves only after the asynchronous approval worker has created the
     * checker details required by Pending Approvals.
     */
    const APPROVAL_REQUIRED_CODE = "DIGX_APPROVAL_REQUIRED",
        APPROVAL_ACCEPTED_STATUS = 202,
        APPROVAL_REFERENCE_RETRY_COUNT = 75,
        APPROVAL_REFERENCE_RETRY_DELAY = 400,
        baseService = BaseService.getInstance(),
        baseModel = BaseModel.getInstance(),
        responseBody = function (error) {
            return error && error.responseJSON ? error.responseJSON : error;
        },
        isAuthenticationChallenge = function (error) {
            return !!(error && (error.status === 412 || error.status === 417));
        },
        isApprovalRequiredResponse = function (error) {
            const response = responseBody(error) || {},
                status = response.status || {},
                message = response.message || status.message;

            return !!(message && message.code === APPROVAL_REQUIRED_CODE);
        },
        publishReferenceNumber = function (response, referenceNumber) {
            const normalizedReference = referenceNumber === undefined || referenceNumber === null
                ? "" : String(referenceNumber).trim();

            if (!normalizedReference) {
                return response;
            }

            response.referenceNumber = normalizedReference;
            response.status = response.status || {};
            response.status.referenceNumber = normalizedReference;

            return response;
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

            response.status = status;
            publishReferenceNumber(response, referenceNumber);
            baseModel.injectProps(response, "getResponseStatus", APPROVAL_ACCEPTED_STATUS);

            return response;
        },
        hydrateApprovalReference = function (response, context) {
            const deferred = $.Deferred();

            if (!context || !context.partyId || !context.closeId || !context.accessPartyId
                    || !context.linkageType || !context.username) {
                deferred.resolve(response);

                return deferred;
            }

            let attempts = 0;

            const readPendingReference = function () {
                attempts += 1;

                const transportPromise = baseService.fetch({
                    url: baseModel.QueryParams.add("hostToHostUserAccess/accounts", {
                        partyId: context.partyId,
                        closeId: context.closeId,
                        accessPartyId: context.accessPartyId,
                        linkageType: context.linkageType,
                        username: context.username,
                        approvalReferenceOnly: "true"
                    }),
                    version: "cz/v1",
                    throttle: false,
                    showMessage: false,
                    success: function (data) {
                        const referenceNumber = data && (data.pendingReferenceNumber
                            || data.referenceNumber
                            || (data.status && data.status.referenceNumber));

                        if (referenceNumber) {
                            publishReferenceNumber(response, referenceNumber);
                            deferred.resolve(response);

                            return;
                        }

                        if (attempts < APPROVAL_REFERENCE_RETRY_COUNT) {
                            setTimeout(readPendingReference, APPROVAL_REFERENCE_RETRY_DELAY);
                        } else {
                            deferred.reject();
                        }
                    },
                    error: function () {
                        if (attempts < APPROVAL_REFERENCE_RETRY_COUNT) {
                            setTimeout(readPendingReference, APPROVAL_REFERENCE_RETRY_DELAY);
                        } else {
                            deferred.reject();
                        }
                    }
                });

                if (transportPromise && typeof transportPromise.catch === "function") {
                    transportPromise.catch(function () {
                        return null;
                    });
                }
            };

            readPendingReference();

            return deferred;
        },
        isFailureResponse = function (data) {
            const result = data && data.status && data.status.result
                ? String(data.status.result).toUpperCase() : "";

            return result === "FAILED" || result === "FAILURE";
        },
        request = function (options) {
            const deferred = $.Deferred(),
                approvalContext = options.approvalContext,
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
                        if (isAuthenticationChallenge(error)) {
                            return;
                        }

                        if (isApprovalRequiredResponse(error)) {
                            hydrateApprovalReference(
                                normalizeApprovalRequiredResponse(error), approvalContext)
                                .done(function (normalizedResponse) {
                                    deferred.resolve(normalizedResponse, "success", error);
                                }).fail(function () {
                                    deferred.reject();
                                });

                            return;
                        }

                        deferred.reject(error);
                    }
                });

            // Local lookup metadata is not part of BaseService's request contract.
            delete requestOptions.approvalContext;

            const transportPromise = requestOptions.data
                ? baseService.add(requestOptions) : baseService.fetch(requestOptions);

            // BaseService owns the shared authentication modal and request replay. Callbacks above
            // expose its final result through the existing jQuery contract used by these screens.
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
        normalizeAccountNumber = function (value) {
            return String(value || "").split("~")[0].replace(/[- ]/g, "").trim();
        },
        accountNumberValues = function (account) {
            const accountNumber = account && account.accountNumber,
                candidates = accountNumber && typeof accountNumber === "object"
                    ? [accountNumber.value, accountNumber.displayValue]
                    : [accountNumber, account && account.accountNumberDisplay],
                values = [];

            candidates.forEach(function (candidate) {
                const normalized = normalizeAccountNumber(readValue(candidate));

                if (normalized && values.indexOf(normalized) === -1) {
                    values.push(normalized);
                }
            });

            return values;
        },
        findBcoPartyAccounts = function (bcoResponse, context) {
            const partyRows = bcoResponse && Array.isArray(bcoResponse.accounts)
                    ? bcoResponse.accounts : [],
                accessPartyId = String(context.accessPartyId || ""),
                expectedAccessLevel = String(context.linkageType || "").toUpperCase()
                    === "ASSOCIATED" ? "USERLINKAGE" : "USER",
                exactMatch = partyRows.filter(function (row) {
                    return String(readValue(row && row.party)) === accessPartyId
                        && String(readValue(row && row.accessLevel)).toUpperCase()
                            === expectedAccessLevel;
                })[0];

            if (exactMatch) {
                return exactMatch;
            }

            // Older AccountAccess responses omit party on the primary USER row. That row is still
            // unambiguous for RELATED access. ASSOCIATED access must match both USERLINKAGE and
            // accessPartyId so a party-wide LINKAGE catalogue can never leak into the user screen.
            if (String(context.linkageType || "").toUpperCase() === "RELATED") {
                return partyRows.filter(function (row) {
                    return String(readValue(row && row.accessLevel)).toUpperCase() === "USER";
                })[0] || null;
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
                accountNumberValues(account).forEach(function (accountNumber) {
                    existingByKey[`${normalizeAccountType(account.accountType)}:${accountNumber}`]
                        = account;
                });
            });

            // AccountAccess is the same catalogue used by BCO. Preserve its row order and display
            // metadata exactly; HTH contributes only the API catalogue and effective selections.
            bcoAccounts.forEach(function (bcoAccount) {
                const accountType = normalizeAccountType(bcoAccount && bcoAccount.accountType),
                    bcoAccountNumbers = accountNumberValues(bcoAccount),
                    accountNumber = bcoAccountNumbers[0] || "",
                    key = `${accountType}:${accountNumber}`;

                if ((accountType !== "CSA" && accountType !== "TD")
                        || !accountNumber || seen[key]) {
                    return;
                }

                seen[key] = true;

                const existing = bcoAccountNumbers.map(function (candidate) {
                        return existingByKey[`${accountType}:${candidate}`];
                    }).filter(Boolean)[0] || {},
                    accountNumberObject = bcoAccount.accountNumber,
                    accountNumberDisplay = accountNumberObject
                        && typeof accountNumberObject === "object"
                        ? accountNumberObject.displayValue : "",
                    sourceApis = Array.isArray(existing.apiServices)
                        ? existing.apiServices : eligibleApis;

                mergedAccounts.push(Object.assign({}, existing, {
                    // Keep the backend canonical identifier for persistence while retaining BCO's
                    // display alias and row order. This also preserves existing selections when
                    // AccountAccess returns the 15-digit alias and HTH stores the 18-digit form.
                    accountNumber: existing.accountNumber || accountNumber,
                    accountNumberDisplay: accountNumberDisplay
                        || existing.accountNumberDisplay || accountNumber,
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

            // Invoke the exact BCO user-access query. Product filtering is intentionally done
            // after this shared response so HTH sees the same company/account catalogue and row
            // order as BCO, while exposing only CSA and Time Deposit in its own screens.
            AccountAccessModel.readUserAccountAccess(params.userId, params.partyId)
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
                    linkageType: context.linkageType,
                    username: context.username
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
                data: JSON.stringify(payload),
                showMessage: false,
                approvalContext: {
                    partyId: payload.partyId,
                    closeId: payload.closeId,
                    accessPartyId: payload.accessPartyId,
                    linkageType: payload.linkageType,
                    username: payload.username
                }
            });
        }
    };
});
