# BCOH2H-1386 — Technical Design

Status: Implemented for the agreed C/Y rules. Hold/Frozen validation is deferred.

## Objective and scope

Validate the actual debit account state before overseas payment submission. TT and DR share the validation in `OverseasPaymentService`. Existing account-access checks remain separate: an active authorization record does not establish the bank account's status.

## Account status enquiry

Use the existing BG3 `AccountApi.queryAccountInfo()` client:

- Endpoint: `POST /v3z/bgech7e0.v3`
- Request: the debit account's internal 18-digit identifier and debit currency, subject to confirmation of currency handling for AIO accounts.
- Response: `dfhcommarea.qaechcc.bgech7e0.eh7e0Output`.

HTH directly validates the raw response. Keep the existing balance enquiry for funds validation; do not use its mapped status as a fallback if this enquiry fails. Reuse the existing BG3 request and response models.

## Validation rules

Follow the comparisons in `TransactionServiceImpl.mapStatus()`, in this order:

| Field | Condition | Result |
| --- | --- | --- |
| `eh7e0FlgStatusO` | Equals `C`, ignoring case | Closed: reject with `600061` |
| `eh7e0FlgDormantO` | Equals `Y`, ignoring case | Dormant / non-active: reject with `600061` |
| Neither condition matches | Existing mapper falls through to ACTIVE | Continue remaining validation |

Do not trim these values, matching the existing transaction-query comparisons. Both rejection conditions use `FPS_DEBIT_ACCOUNT_STATUS_INVALID` with the existing message, “Invalid account status.”

By agreement, all other state values, including null, blank, padded and unknown values, fall through exactly as in the transaction-query mapper. `eh7e0FlgAccHoldO` is not checked. This implementation does not claim Hold/Frozen coverage.

## Processing and error handling

1. Validate request fields and account access using the existing rules.
2. Query the debit account through `queryAccountInfo`.
3. Validate all required response nodes and enquiry success codes.
4. Apply the C/Y status checks.
5. Continue balance and other payment validations.
6. Create WMG requests, submit to IPE and persist payment data only after validation succeeds.

Use at most one enquiry for this debit-status check per processing attempt. Do not reuse beneficiary data or cache account status across requests.

| Failure | Handling |
| --- | --- |
| Closed or dormant account | Existing `600061` |
| Missing account-access record | Existing `600020` |
| Timeout, failed enquiry or missing response nodes | Stop with existing `GENERAL_SERVICE_EXCEPTION` (`600000`) |

A failed enquiry does not fall back to ACTIVE or become an insufficient-funds error. Require envelope `qaechccErrData=0000`, following the existing OnUs caller, and inner `eh7e0CodReturnO=00`, following the existing queryAccountInfo helper. Other return codes use `600000`; no speculative host account-not-found code mapping is introduced. Response identity echo validation is deferred pending its contract; the request explicitly identifies the debit account and debit currency.

## Open items

| Item | Confirmation needed |
| --- | --- |
| Hold / Frozen | Values of `eh7e0FlgAccHoldO`; whether it covers every debit freeze or additional fields are required |
| Other state values | No pending change: retain the transaction mapper's fallback as agreed |
| Enquiry contract | Confirm any future host account-not-found mapping; currently all unsuccessful enquiries use `600000` |
| Account selection | Currency input and response identity rules, including AIO master/sub-account coverage |

Frozen acceptance remains pending until its detection rule is confirmed. The generic balance API's `isRestricted` flag is not used as a freeze indicator.

## Changes and tests

| Component | Change |
| --- | --- |
| `OverseasPaymentService.java` | Add the shared BG3 enquiry and direct C/Y validation before payment side effects |
| `OverseasPaymentServiceTest.java` | Cover C/c, Y/y, both flags, nonmatching values, response guards and enquiry failures |
| `TtPaymentServiceTest.java` | Verify an authorized but closed/dormant account returns `600061` without WMG, IPE submission or payment persistence |
| `DrPaymentServiceTest.java` | Verify the same shared validation and absence of payment side effects |

Tests also verify request account/currency construction, one enquiry per attempt, fallback for null/blank/padded/unknown values, and that Hold does not cause rejection. No public API, database schema or generic balance mapper was changed.

Java 21 / Maven verification: 11 new test methods passed. The selected Overseas, TT, DR and TT support suites ran 94 tests: 90 passed, 4 failed, 0 errors. The failures are the same previously reproduced WMG baseline failures (three TT WMG invocation tests and one TT support parsing-error test). Maven is configured to ignore test failures; these counts were checked from the test reports.

For SIT/UAT, keep authorization and other payment inputs valid, then demonstrate an active account progressing and closed/dormant accounts being rejected. Capture the host response, payment error and evidence of no downstream payment side effects. Final settlement is not required to verify rejection; Frozen requires a separately confirmed scenario.
