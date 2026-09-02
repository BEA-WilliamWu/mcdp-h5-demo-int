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
     */
    const baseService = BaseService.getInstance(),
        baseModel = BaseModel.getInstance(),
        isAuthenticationChallenge = function (error) {
            return !!(error && (error.status === 412 || error.status === 417));
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
                        if (isAuthenticationChallenge(error)) {
                            return;
                        }

                        deferred.reject(error);
                    }
                }),
                transportPromise = requestOptions.data
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
                data: JSON.stringify(payload)
            });
        }
    };
});
