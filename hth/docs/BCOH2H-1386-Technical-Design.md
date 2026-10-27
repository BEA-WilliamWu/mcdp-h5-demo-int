# BCOH2H-1386 — Technical Design

## Overview

BCOH2H-1386 strengthens debit account status validation for overseas Telegraphic Transfer (TT) payments. The debit account must exist in the existing account access lookup and have an active status before payment submission can proceed. Invalid or missing status values must produce a business error instead of a technical exception.

The implementation enhances an existing validation. Account existence checks and rejection of non-active statuses are already present. The functional correction makes the status comparison null-safe; additional tests establish the expected behavior and verify that rejected requests do not trigger payment side effects.

## Scope and agreed decisions

| Item | Decision |
| --- | --- |
| Business acceptance scope | Overseas TT debit account validation |
| Shared implementation | TT and DR use `OverseasPaymentService`; DR is included in regression coverage |
| Status source | Continue using `HTH_BEA.HTH_USER_ACCESS_ACCOUNT.OBJECT_STATUS` |
| Multiple records | Continue using the first returned record |
| Active values | Accept `A` and `ACTIVE`, ignoring case |
| Whitespace | Do not trim; padded values such as ` A ` remain invalid |
| Error contract | Reuse existing error codes, messages and exception handling |

The change does not introduce a real-time core banking status enquiry, change the account query, define new BCO status codes, or change FPS, CHATS or ONUS validation. The decision is based on the existing stored `OBJECT_STATUS` value; it does not independently verify the account's current status in the core banking system.

## Existing behavior and defect

`OverseasPaymentService.validateFromAccount()` validates the debit account number, currency and type, then calls:

```java
hthUserAccessAccountMapper.selectFromAccountInfo(
        partyId, closeId, fromAccount.getAccountNumber());
```

The query matches `PARTY_ID`, `CLOSE_ID`, and the formatted account number with hyphens removed. It returns account access records including `OBJECT_STATUS`.

A null or empty result, or a null first record, produces error `600020`. The existing status check accepts `A` or `ACTIVE` and rejects other values. However, it calls `equalsIgnoreCase()` on the returned status directly. A null status therefore causes a `NullPointerException` instead of error `600061`.

The query has no explicit ordering. This change preserves first-record behavior rather than introducing a selection rule by product or currency. Where multiple records have conflicting statuses, the result depends on the first record returned by the database.

## Validation rules

| Account lookup or status | Expected result |
| --- | --- |
| Null result, empty result, or null first record | Reject with `600020` |
| `A`, `a`, `ACTIVE`, `active`, or other case variants | Continue validation |
| Null, empty, or whitespace-only status | Reject with `600061` |
| Any non-active or unknown value | Reject with `600061` |
| Padded active value, for example ` A ` or ` ACTIVE ` | Reject with `600061` |
| First record active, later record inactive | Continue validation |
| First record inactive, later record active | Reject with `600061` |

Values such as `FROZEN`, `INACTIVE`, `CLOSED`, `DORMANT`, and `HOLD` are rejected because they are outside the active allowlist. These examples are test inputs, not a new authoritative mapping of BCO status codes.

## Implementation

Update the status comparison in `OverseasPaymentService.validateFromAccount()`:

```java
String status = first.getObjectStatus();
boolean active = "A".equalsIgnoreCase(status)
        || AccountStatus.ACTIVE.value().equalsIgnoreCase(status);
if (!active) {
    throw new ServiceException(
            HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
}
```

Both comparisons start from non-null active constants. A null status evaluates to false and follows the existing business-error path. The account-not-found branch remains unchanged.

No additional service, database query, configuration property or dependency is required.

## Processing sequence and failure handling

The existing payment sequence is retained:

1. BFF checks applicable service windows and maps the request.
2. Support `PaymentServiceImpl` performs common validation.
3. IPE smart routing selects the payment type.
4. The TT or DR service performs overseas validation, including debit account status validation.
5. Once validation succeeds, processing continues to WMG creation for TT, IPE payment submission, and payment persistence.

An account validation failure propagates as a `ServiceException` before WMG creation, IPE `submit`, or payment record persistence. It does not enter the submit-stage exception handler and is not converted into a `PROCESSING` payment response.

IPE `route` may already have been called by the common entry point before this validation. The rejection guarantee concerns payment submission and subsequent payment side effects, not the absence of all downstream calls. Existing audit and event logging is unaffected.

## Error contract

| Code | Existing constant | Message |
| --- | --- | --- |
| `600020` | `ACCOUNT_NOT_FOUND_EXCEPTION` | The specified account does not exist or is not accessible. |
| `600061` | `FPS_DEBIT_ACCOUNT_STATUS_INVALID` | Invalid account status. |

The existing constant with the `FPS` prefix is reused for compatibility. Exception mapping, response schema and HTTP handling remain unchanged.

## Affected files

All paths below are relative to `hth-ms-support/hth-ms-support-svc/`.

| File | Change |
| --- | --- |
| `src/main/java/com/hkbea/hth/service/OverseasPaymentService.java` | Null-safe active status comparison |
| `src/test/java/com/hkbea/hth/service/OverseasPaymentServiceTest.java` | Status matrix, missing account results, and first-record selection tests |
| `src/test/java/com/hkbea/hth/service/TtPaymentServiceTest.java` | TT rejection tests and assertions that IPE submission, WMG invocation and payment persistence do not occur |
| `src/test/java/com/hkbea/hth/service/DrPaymentServiceTest.java` | DR regression tests and assertions that IPE submission and payment persistence do not occur |

No API specification, generated model, Mapper SQL, database schema or BFF changes are required.

## Unit test results

Nine test methods were added, with several methods exercising multiple status values. Existing success-path tests cover normal payment processing for active accounts.

The following suites were executed using Java 21 and Maven 3.10.0 on 26 October 2026:

- `OverseasPaymentServiceTest`
- `TtPaymentServiceTest`
- `DrPaymentServiceTest`
- `PaymentServiceImplTest`
- `PaymentRoutingSubmitterTest`

Result: **151 tests run, 0 failures, 0 errors, 0 skipped**. This is the result for the selected suites, not a full system or end-to-end test run. OpenAPI generation was disabled with `-Dcodegen.skip=true`, and test failures were not ignored.

## SIT and UAT approach

SIT and UAT should verify this story's account-validation acceptance criteria. A complete payment settlement journey is not required to demonstrate rejection of an invalid debit account.

### SIT

Use controlled SIT account records and an otherwise valid TT payment request. Ensure the customer profile, API entitlement and other prerequisites allow the request to reach account validation.

| Scenario | Expected evidence |
| --- | --- |
| Active account | Request passes account validation and reaches subsequent payment processing |
| Non-active account | API returns `600061` and the specified message |
| Missing or inaccessible account | API returns `600020` and the specified message |
| Null or blank status | API returns `600061`, without a technical exception |
| Padded active value | API returns `600061` |
| DR regression | Active processing remains valid; invalid status prevents submission |

For rejected requests, inspect downstream call records and payment persistence to confirm that no IPE `submit`, TT WMG creation, or payment insert/update occurred. Capture the request correlation identifier, prepared account state, response, and relevant logs or database evidence.

Use a downstream test stub where the payment engine is unavailable, and record which integrations were simulated. Unit tests cover the first-record selection rule deterministically; SIT must not assume a stable ordering for an unordered database query.

### UAT

Business users validate TT requests with agreed active and non-active test accounts. AC1 is demonstrated by an active account passing status validation and a non-active account being blocked. AC2 is demonstrated by the agreed inactive-account error code and message.

Keep all other request conditions valid and retain evidence that the active request progressed past account validation. Receiving an unrelated error alone does not prove that AC1 passed. Any use of simulated downstream systems should be agreed with the business and recorded as an acceptance boundary. Final settlement remains part of separate payment end-to-end coverage.

## Deployment and rollback

Deploy the updated `hth-ms-support-svc` artifact through the existing release process. No database migration or configuration change is needed, and the change adds no database or network calls.

Rollback consists of restoring the previous service artifact. This also restores the previous null-status behavior, including the possibility of a technical exception, so rollback should be assessed against that known defect.
