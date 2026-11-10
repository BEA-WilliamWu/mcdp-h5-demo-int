# BCOH2H-2126 — Technical Design

## Objective and scope

Reuse the existing common payment enquiry API for FPS, CHATS, TT, DR and On-Us. Complete the confirmed request constraint and pending-status refresh gap, align the query's OpenAPI error documentation, and restore executable enquiry regression coverage.

Baseline: `a97bbb2`, 11 November 2026. No new endpoint, database column/table, cache or error code is introduced. Unresolved identifier/date definitions and review comments are recorded below rather than implemented as assumed requirements.

Sources:

- `jira/[#BCOH2H-2126] HTH Payment Status Enquiry.pdf`
- `jira/HTH Local & Overseas Payment Enquiry Specification (V0.1)-v22-20261110_234921.docx`
- `jira/99 - Exception Message-v157-20261110_234959.docx`

The story refers to v1.0, while the supplied export is labelled V0.1 and includes a User comments column. Field renaming, ISO status adoption and additional response headers in that column are not treated as approved changes.

## Existing interface and data flow

`GET /payment/v1/transactions/enquiry?txnRef={reference}`

The Support application exposes this beneath `/hth/api`; BFF uses `/hth/api/bff`. APIC routing remains unchanged. Consumers use the reference returned by payment initiation, not a substituted customer transaction ID.

1. BFF's existing API maintenance filter checks the request URI. Its enquiry service also checks IPE maintenance before forwarding to Support.
2. Support resolves the caller's `partyId`, validates `txnRef`, and searches by `PAYMENT_ID` **and** `PARTY_ID`.
3. On-Us/DR records are read from `HTH_PAYMENT_INTERNAL`; FPS/CHATS/TT records are read from `HTH_PAYMENT_PAYOUT`.
4. The existing debit-account entitlement check runs before querying IPE or returning payment details.
5. Eligible unfinished records query the existing IPE result API by payment ID. Existing mappings supply the common response, which BFF forwards.

All 18 response fields and all five payment types already exist in both API models. Payment submission and quota handling are not part of this change.

## Changes

### Reference validation

Retain the existing blank/null check and error `500101`. Add a maximum length of 50 characters, as specified by `CHAR(50)`. An oversized value uses the existing field-prefixed common invalid-parameter error `500000` for `txnRef`, before any payment-table lookup or IPE call. Authentication/caller-resolution precedence is retained. Do not trim or truncate a reference into a different identifier.

Add `maxLength: 50` to the query parameter in both OpenAPI definitions. Other confirmed response string limits are documented, without introducing response truncation or modifying generated Java models. The unresolved external reference and date representations are excluded from new length constraints.

### Status refresh

| Stored HTH status | Query behaviour |
|---|---|
| `Pending` or `Processing`, case-insensitive | Query IPE and apply the existing status mapping. This adds `Pending` to the former Processing-only condition. |
| `Success` or `Rejected` | Return the existing stored final result without polling. |
| Null, blank or unknown | Preserve baseline behaviour pending an agreed recovery rule. |

The existing mapping remains: IPE `SUCCESS` -> `Success`, `FAILED` -> `Rejected`, and `PENDING` / `PROCESSING` / `MANUAL_REVIEW` -> `Processing`.

Changed status/rejection details are persisted using the existing sparse update. An unchanged normalized status/rejection does not cause a new write. IPE errors, unusable responses or absent statuses retain the stored result; this is preserved compatibility behaviour, not a new outage requirement or a guarantee of a live result.

### Exceptions and maintenance

Existing HTH exceptions use HTTP **417** with `ExceptionEntity`. Replace the query's stale OpenAPI 450/460/470/490 descriptions with the current 417 contract. This affects documentation only; the shared runtime exception mapper already returns 417.

| Code | Meaning | Behaviour |
|---|---|---|
| 500000 | Invalid parameter, field-prefixed | Reused for txnRef longer than 50 characters. |
| 500101 | Missing mandatory parameter | Existing null/blank txnRef handling. |
| 500070 | HTH API service not enabled for the company | Existing caller-resolution handling; do not publish the document's `name TBC` placeholder. |
| 500071 | Invalid or expired access token | Existing handling. |
| 600131 | Payment transaction not found for the given transactionId | Existing code and wording, including company-scoped misses. |
| 600210 | Requested API under scheduled maintenance | Existing BFF URI maintenance filter. |
| 600211 | Dependent third-party payment system under maintenance | Existing BFF IPE enquiry guard. |

Maintenance may use the configured window message. No new availability table or service-hours restriction is added. Generic APIC unavailability (HTTP 503) and rate limiting (HTTP 429) remain outside this delivery.

## Open decisions and preserved limitations

| Item | Current evidence and decision |
|---|---|
| `externalReferenceId` | V0.1 says HTH Generated Number, CHAR(35), and asks whether this is msg ID. Code reads `END_TO_END_ID`; the old OpenAPI said Message-Id. Retain the current mapping, correct the misleading assertion, and confirm the intended identifier before changing data. It may be absent for some existing records. |
| `txnRef` / `referenceNumber` | V0.1 says Customer Input Number; the story explicitly requires the reference returned by payment initiation. Retain payment-ID lookup and response. |
| Dates | V0.1 lists CHAR(13) for `asOf`, `executionDate`, `initiatedDate`, without specifying encoding/timezone. Retain ISO 8601 / OffsetDateTime pending confirmation; do not assume epoch milliseconds. |
| Missing details | DR does not populate the payment-date/remarks fields used by enquiry; On-Us does not populate remarks in the inspected insertion path. Establish the required source/semantics before altering payment persistence. No synthetic execution date is added. |
| IPE finality | The API exposes `terminal`; current HTH mappings do not use it. Confirm status/finality consistency and required recovery behaviour before changing final-status polling or schema. |
| Live-query failure | Confirm whether returning the last stored status remains acceptable. AC4 maintenance rejection is already separate from an unexpected IPE query failure. |
| IPE payment types | Java models support five types, but the local IPE query OpenAPI has a narrower transaction-type enum. Confirm the deployed IPE result API for CHATS/TT/DR in SIT. |
| User comments | Proposed creditor/debtor/instructedAmount renaming, ISO status codes, chargeBearer and message metadata require explicit contract approval. Existing JSON names and enums remain. |

These limitations are not represented as completed acceptance evidence.

## Code and document changes

| Path | Change |
|---|---|
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/PaymentServiceImpl.java` | txnRef maximum-length guard; Pending/Processing refresh eligibility; correct lookup comments. |
| `hth-ms-support/hth-ms-support-api/src/main/resources/api/hth-support-local-payment.0.0.openapi.yaml` | Query limits, status refresh description, HTTP 417 and response-field documentation. |
| `hth-ms-bff/hth-ms-bff-api/src/main/resources/api/hth-bff-local-payment.0.0.openapi.yaml` | Same contract alignment plus existing BFF maintenance errors. |
| `hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/PaymentEnquiryServiceTest.java` | New independent query regression tests; leave the disabled broad payment test untouched. |
| `hth-ms-bff/hth-ms-bff-svc/src/test/java/com/hkbea/hth/bff/service/PaymentEnquiryBffTest.java` | All five types and 18 response fields, IPE maintenance and error pass-through. |
| `hth-ms-bff/hth-ms-bff-svc/src/test/java/com/hkbea/hth/bff/maintenance/filter/MaintenanceWindowFilterTest.java` | Add the exact query path for HTH maintenance and availability cases. |

## Verification and acceptance

Unit tests cover blank/oversized/50-character references, company-scoped SQL predicates, account authorization, all five payment types, Pending/Processing refresh, final states without polling, rejection propagation, inconclusive IPE responses, unchanged-state writes and BFF mapping/maintenance. Existing maintenance and common exception tests are included in the targeted run.

SIT must exercise payment-initiation-to-enquiry for all five types, confirm deployed IPE support and state progression, verify the APIC-to-BFF route and maintenance URI matching, and inspect HTTP 417/no-record responses. UAT should confirm agreed identifiers, date representations, payment details and the cross-type enquiry experience.

Verification completed on 11 November 2026 with Java 21 and Maven 3.10.0: **69 targeted tests passed, with 0 failures, 0 errors and 0 skipped tests**.

| Test suite | Tests passed |
|---|---:|
| PaymentEnquiryServiceTest | 40 |
| PaymentEnquiryBffTest | 3 |
| MaintenanceWindowFilterTest | 5 |
| MaintenanceWindowServiceTest | 14 |
| ThirdPartyWindowServiceTest | 2 |
| PaymentServiceImplThirdPartyTest | 3 |
| ExceptionResponseBuilderTest | 2 |

The run compiled the required Common, DSP Support, HTH Support and BFF modules through a temporary local reactor. The existing proprietary `esenapi.jar` from the local HTH Application checkout was installed into the local Maven repository; no project dependency or build file was changed. Test-failure ignoring was explicitly disabled.

Both OpenAPI YAML files parsed successfully. Checks confirmed the 50-character query constraint, HTTP 200/417 responses, identical 18-field BFF/Support response schemas, and no changes to other endpoints or component schemas. Diff whitespace checks passed with the existing CRLF convention respected.

This was a targeted unit/regression run, not the full repository test suite. No live IPE/EBK integration, SIT or UAT was performed. The open decisions above remain acceptance prerequisites where applicable.
