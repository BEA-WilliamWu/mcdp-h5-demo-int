# BCOH2H-2126 — Technical Design

Revision: 18 November 2026. Unified requested execution date for all five payment types, based on DSP `7756632`.

## Objective and scope

Reuse the common payment enquiry API for FPS, CHATS, TT, DR and On-Us/INTERNAL. The confirmed response meaning is now consistent: `executionDate` is the customer's requested execution date, read from the existing `EXECUTION_DATE` column populated from payment initiation `settlementDate`.

This supersedes the earlier DR-only date correction. The earlier reference constraints, status refresh, company/account authorization and exception handling remain in place. No new endpoint, public JSON field, database schema, migration, backfill, cache, error code or model generation is required.

Sources and decisions:

- `jira/[#BCOH2H-2126] HTH Payment Status Enquiry.pdf`
- `jira/HTH Local & Overseas Payment Enquiry Specification (V0.1)-v22-20261110_234921.docx`
- `jira/99 - Exception Message-v157-20261110_234959.docx`
- User-confirmed decision on 18 November: all five types use existing `EXECUTION_DATE` to represent the requested date; retain database writes and the current response format.

## Existing interface and data flow

`GET /payment/v1/transaction-inquiry?txnRef={reference}`

This is the path in the current Support/BFF `PaymentApi` and OpenAPI definitions. This revision does not rename or reroute the endpoint. Support exposes it beneath `/hth/api`; BFF uses `/hth/api/bff`.

1. Existing BFF maintenance handling runs before forwarding to Support.
2. Support resolves the caller's `partyId`, validates `txnRef`, and searches by `PAYMENT_ID` and `PARTY_ID`.
3. On-Us/DR use `HTH_PAYMENT_INTERNAL`; FPS/CHATS/TT use `HTH_PAYMENT_PAYOUT`.
4. Existing debit-account entitlement checks run before returning details or querying IPE.
5. Eligible Pending/Processing records refresh their status from IPE. CHATS uses the generated HTH batch reference for its IPE batch enquiry; other types retain their existing transaction enquiry.
6. Both entity mappings return `executionDate` from `EXECUTION_DATE`. BFF forwards the existing response field unchanged.

## Date sources and unified mapping

The current payment services already store these values before IPE submission:

| Type | Table | Existing PAYMENT_DATE write | Existing EXECUTION_DATE write | Enquiry executionDate after this revision |
|---|---|---|---|---|
| FPS | HTH_PAYMENT_PAYOUT | `LocalDateTime.now()` | Request `settlementDate` | EXECUTION_DATE |
| CHATS | HTH_PAYMENT_PAYOUT | `LocalDateTime.now()` | Request `settlementDate` | EXECUTION_DATE |
| TT | HTH_PAYMENT_PAYOUT | `LocalDateTime.now()` | Request `settlementDate` | EXECUTION_DATE |
| On-Us / INTERNAL | HTH_PAYMENT_INTERNAL | `new Date()` | Request `settlementDate` | EXECUTION_DATE |
| DR | HTH_PAYMENT_INTERNAL | Not assigned by the Java insertion path | Request `settlementDate` | EXECUTION_DATE |

`PAYMENT_DATE` is a system processing/insertion time in these paths; it is not established as a host-confirmed execution timestamp. Its existing writes remain unchanged.

Conversion rules for every type and status:

- Read the existing `EXECUTION_DATE` string and parse its ISO date (`yyyy-MM-dd`).
- Represent it at the start of that date in the existing enquiry timezone (`ENQUIRY_ZONE`, currently the JVM default), retaining `OffsetDateTime` / ISO 8601 JSON. In a Hong Kong deployment, `2026-11-17` is returned as `2026-11-17T00:00:00+08:00`. Midnight encodes a date; it does not assert an execution time.
- Missing/blank dates remain absent. Invalid historical values are logged and remain absent without failing the enquiry.
- Do not fall back to `PAYMENT_DATE`, creation time, the current time or IPE `lastUpdatedAt`, which would mix different meanings.
- Apply the same rule before/after IPE status refresh and for final Success/Rejected responses.

The other date fields retain their existing sources:

| Response field | Source and meaning |
|---|---|
| `executionDate` | EXECUTION_DATE: customer's requested execution date |
| `initiatedDate` | CREATION_DATE: record creation timestamp |
| `asOf` | LAST_UPDATED_DATE: record/status last-update timestamp |

## Existing enquiry behavior retained

- Null/blank `txnRef`: existing `500101`; more than 50 characters: existing `500000` prefixed by `txnRef`. No trimming or truncation.
- Missing company-scoped transaction: existing `600131`. Existing caller/authentication and debit-account permission checks remain.
- Pending/Processing refresh is case-insensitive. IPE SUCCESS maps to Success, FAILED to Rejected, and PENDING/PROCESSING/MANUAL_REVIEW to Processing. Final records are not polled.
- Changed status/rejection details use existing sparse updates. Inconclusive IPE results retain the stored state. Date-source unification does not change this behavior or infer execution from the status.
- Runtime exceptions retain HTTP 417 / `ExceptionEntity`, including the existing maintenance behavior. No new error codes or service windows are introduced.
- Existing CHATS customer End-to-End ID, generated HTH reference, batch enquiry, creditor-name segmentation and BCO debtor address handling are retained.

## Implementation

| File | Change |
|---|---|
| `PaymentServiceImpl.java` | Both INTERNAL and PAYOUT enquiry mappings call the existing requested-date parser with `entity.getExecutionDate()`. Remove the DR-only branch; make its comment/log generic. |
| `PaymentEnquiryServiceTest.java` | Expand requested-date regression cases to all five types, all four statuses, missing/invalid dates, leap day and deliberately different payment/update timestamps. Assert initiatedDate/asOf and refresh behavior remain correct. |
| `BCOH2H-2126-Technical-Design.md` | Record the unified confirmed semantics and current verification. |
| `BCOH2H-2065-Technical-Design.md` | Note the shared enquiry alignment so both story packages describe their current shared service. |

No persistence code, entity/mapper SQL, database configuration, public model, OpenAPI YAML, POM or dependency changes are needed. The current API already represents executionDate as a date-time; no code regeneration is required.

## Remaining contract decisions

The supplied V0.1 export says CHAR(13) for dates without specifying encoding/timezone; this revision retains the existing ISO representation and timezone. Requested-date semantics are explicitly confirmed, while epoch encoding, a global timezone change or actual-execution timestamps are outside this revision.

`externalReferenceId`, proposed response-field renaming, ISO status codes, IPE terminal/finality policy and missing remarks sourcing remain separate decisions. Existing mappings are retained. Current IPE result contracts expose `lastUpdatedAt`, not an explicitly defined actual execution timestamp.

## Verification and acceptance

Java 21 / offline Maven temporary reactor, using `clean test` with test-failure ignoring disabled:

| Target | Tests | Failures / errors / skipped |
|---|---:|---|
| Support payment/enquiry regression, 11 suites | 350 | 0 / 0 / 0 |
| BFF PaymentServiceImplCoverageTest | 13 | 0 / 0 / 0 |
| Total | **363** | **0 / 0 / 0** |

`PaymentEnquiryServiceTest` has 69 executions: 20 combinations of payment type and status, 5 leap-day/no-payment-date cases, 30 missing/invalid-date cases and 14 existing enquiry cases. This is 40 more executions than the preceding DR-only version. The expanded cases reproduced the mixed-source behavior before the production change and pass with the unified mapping.

Existing CHATS name/address/reference tests, other payment submission tests and BFF response mapping also pass. Clean builds exclude stale test classes after source synchronization. No project dependency/POM was changed. Test log: `/tmp/hth-2126-all-execution-dates-clean.log`.

SIT should compare executionDate with the submitted settlementDate for every type, including records with different PAYMENT_DATE values, final/rejected states and status refresh. Verify missing/invalid historical dates remain absent and initiatedDate/asOf retain their previous meanings. Existing valid EXECUTION_DATE values work without a migration or backfill. No live database/IPE/SIT/UAT run is claimed by these unit tests.

## Delivery

Commit the two Java files and both updated designs to local DSP Git. Synchronize the complete Java files and the 2126 design to `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2126/`, keeping repository-relative Java paths under `source/`.

Refresh the same two shared files in `bcohth-2065/source/` from the same DSP commit, update its 2065 design, and regenerate each directory's DELIVERY.txt and SHA256SUMS. The shared files must be byte-identical between packages, so copying the current deliveries in either order preserves this correction. mcdp Git submission remains manual.
