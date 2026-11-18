# BCOH2H-2126 — Technical Design

Revision: 18 November 2026. DR requested-execution-date correction based on DSP `f70f3dd`.

## Objective and scope

Reuse the existing common payment enquiry API for FPS, CHATS, TT, DR and On-Us. Complete the confirmed request constraint and pending-status refresh gap, align the query's OpenAPI error documentation, and restore executable enquiry regression coverage.

Original delivery baseline: `a97bbb2`, 11 November 2026. The 18 November correction is limited to DR enquiry date mapping and its regression tests. No new endpoint, database column/table, cache or error code is introduced. Unresolved identifier/date definitions and review comments are recorded below rather than implemented as assumed requirements.

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

## DR executionDate correction — 18 November 2026

Confirmed semantics: for DR, the response `executionDate` represents the customer's requested execution date, already stored from the initiation request's `settlementDate` in `HTH_PAYMENT_INTERNAL.EXECUTION_DATE`. It does not assert actual host execution or settlement time.

Previously DR wrote `EXECUTION_DATE`, while the shared INTERNAL enquiry mapper read `PAYMENT_DATE`; DR does not populate that latter column. Change only the DR branch in `PaymentServiceImpl.toPaymentTransaction(PaymentInternal)` to read the existing `EXECUTION_DATE` string.

- Parse the stored ISO date (`yyyy-MM-dd`) and express it at the start of that date in the existing enquiry timezone (`ENQUIRY_ZONE`, currently the JVM default). This preserves the existing `OffsetDateTime`/ISO 8601 response shape. For a Hong Kong deployment, `2026-11-17` becomes `2026-11-17T00:00:00+08:00`.
- Use this source for DR regardless of payment status, including Pending/Processing and final responses. IPE status refresh does not replace the requested date with a last-update timestamp.
- Missing, blank or invalid historical date values remain absent. Invalid values are logged; enquiry continues. Do not substitute `PAYMENT_DATE`, creation time, current time or an inferred execution timestamp.
- On-Us/INTERNAL, FPS, CHATS and TT retain their existing `PAYMENT_DATE` mappings and null behavior.
- No change to database schema, stored records, submission/persistence logic, API models/OpenAPI generation, authorization, status refresh, account validation or quota handling.

Tests exercise the public enquiry entry point and cover all DR statuses, the missing-PAYMENT_DATE case, leap day, missing/invalid requested dates, and other payment types with deliberately different EXECUTION_DATE/PAYMENT_DATE values. Existing payment and enquiry regression suites are retained.

## Open decisions and preserved limitations

| Item | Current evidence and decision |
|---|---|
| `externalReferenceId` | V0.1 says HTH Generated Number, CHAR(35), and asks whether this is msg ID. Code reads `END_TO_END_ID`; the old OpenAPI said Message-Id. Retain the current mapping, correct the misleading assertion, and confirm the intended identifier before changing data. It may be absent for some existing records. |
| `txnRef` / `referenceNumber` | V0.1 says Customer Input Number; the story explicitly requires the reference returned by payment initiation. Retain payment-ID lookup and response. |
| Dates | V0.1 lists CHAR(13) without specifying encoding/timezone. Retain the existing ISO 8601 / OffsetDateTime representation. DR requested-date semantics are confirmed above; no epoch-millisecond encoding or global timezone change is introduced. |
| Missing details | The DR requested-date mapping is corrected above without changing persistence. DR and On-Us remarks sourcing remains outside this correction. No actual execution timestamp is inferred. |
| IPE finality | The API exposes `terminal`; current HTH mappings do not use it. Confirm status/finality consistency and required recovery behaviour before changing final-status polling or schema. |
| Live-query failure | Confirm whether returning the last stored status remains acceptable. AC4 maintenance rejection is already separate from an unexpected IPE query failure. |
| IPE payment types | Java models support five types, but the local IPE query OpenAPI has a narrower transaction-type enum. Confirm the deployed IPE result API for CHATS/TT/DR in SIT. |
| User comments | Proposed creditor/debtor/instructedAmount renaming, ISO status codes, chargeBearer and message metadata require explicit contract approval. Existing JSON names and enums remain. |

These limitations are not represented as completed acceptance evidence.

## Code and document changes

| Path | Change |
|---|---|
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/PaymentServiceImpl.java` | txnRef maximum-length guard; Pending/Processing refresh eligibility; correct lookup comments; DR reads the stored requested execution date. |
| `hth-ms-support/hth-ms-support-api/src/main/resources/api/hth-support-local-payment.0.0.openapi.yaml` | Query limits, status refresh description, HTTP 417 and response-field documentation. |
| `hth-ms-bff/hth-ms-bff-api/src/main/resources/api/hth-bff-local-payment.0.0.openapi.yaml` | Same contract alignment plus existing BFF maintenance errors. |
| `hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/PaymentEnquiryServiceTest.java` | Public-entry enquiry regression tests, including DR requested-date and other payment-type date-source preservation cases. |
| `hth-ms-bff/hth-ms-bff-svc/src/test/java/com/hkbea/hth/bff/service/PaymentEnquiryBffTest.java` | All five types and 18 response fields, IPE maintenance and error pass-through. |
| `hth-ms-bff/hth-ms-bff-svc/src/test/java/com/hkbea/hth/bff/maintenance/filter/MaintenanceWindowFilterTest.java` | Add the exact query path for HTH maintenance and availability cases. |

## Verification and acceptance

Unit tests cover blank/oversized/50-character references, company-scoped SQL predicates, account authorization, all five payment types, Pending/Processing refresh, final states without polling, rejection propagation, inconclusive IPE responses, unchanged-state writes and BFF mapping/maintenance. Existing maintenance and common exception tests are included in the targeted run.

SIT must exercise payment-initiation-to-enquiry for all five types, confirm deployed IPE support and state progression, verify the APIC-to-BFF route and maintenance URI matching, and inspect HTTP 417/no-record responses. UAT should confirm agreed identifiers, date representations, payment details and the cross-type enquiry experience.

Original delivery verification completed on 11 November 2026 with Java 21 and Maven 3.10.0: **69 targeted tests passed, with 0 failures, 0 errors and 0 skipped tests**.

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

## Verification for the 18 November correction

Java 21 / offline Maven targeted Support reactor: **310 tests passed; 0 failures, 0 errors, 0 skipped**. `PaymentEnquiryServiceTest` has 29 test executions, including 15 new requested-date/regression cases. The new cases first reproduced 11 failures against the previous implementation; they pass after the DR-only mapping correction. Other payment-type regression cases also pass.

Support test log: `/tmp/hth-2126-dr-execution-date-after.log`. The temporary verification reactor and local dependency cache are used without changing project POMs or dependencies. Live database/SIT validation is not claimed.

SIT should query a DR record whose `EXECUTION_DATE` is populated and `PAYMENT_DATE` is null, compare the returned calendar date with the submitted `settlementDate`, and confirm the other four payment types still return their prior dates. Existing DR records benefit from the query mapping without a database migration or backfill.

### Verification of the 18 November DR correction

Java 21 and offline Maven temporary reactors passed with test-failure ignoring disabled:

| Target | Tests | Failures / errors / skipped |
|---|---:|---|
| Support payment/enquiry regression, 11 suites | 310 | 0 / 0 / 0 |
| BFF `PaymentServiceImplCoverageTest`, including enquiry field forwarding | 13 | 0 / 0 / 0 |
| Total | **323** | **0 / 0 / 0** |

`PaymentEnquiryServiceTest` now has 29 executions, including 15 new cases covering this correction. The new tests reproduced the previous behavior before the production change and passed afterward. All four other payment types retain their payment-date and missing-date behavior. Existing CHATS name/address and customer-reference tests also pass.

Logs: `/tmp/hth-2126-dr-execution-date-after.log` and `/tmp/hth-2126-dr-date-bff.log`. Project POMs/dependencies were not modified. No live database access, data migration or SIT/UAT was performed.

Delivery: commit only `PaymentServiceImpl.java`, `PaymentEnquiryServiceTest.java` and this design to local DSP Git. Copy the two complete Java files under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2126/source/`, retaining repository-relative paths, and put the design at the story root. The shared service is based on current DSP `f70f3dd` and retains the 2065 corrections; apply this newer service file after older story snapshots when combining deliveries. Leave mcdp Git submission to the user.
