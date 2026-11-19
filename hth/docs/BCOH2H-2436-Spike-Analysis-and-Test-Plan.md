# BCOH2H-2436 — Spike Analysis and Performance Test Plan

**Subject:** Account Transaction History Inquiry API for High-Volume CASA Accounts  
**Status:** Draft for review; tests not executed  
**Prepared:** 19 November 2026  
**Owner / reviewer:** [Complete before execution]

## 1. Purpose and expected outcome

Assess whether the existing Account Transaction History Inquiry API can retrieve transaction history for high-volume current and savings accounts within agreed response-time and resource limits. Establish a reproducible baseline, identify bottlenecks, and recommend follow-up work before production rollout.

The assessment must measure both individual page requests and complete retrieval journeys. Fast responses are not evidence of acceptable performance if pagination omits transactions or stops early.

This spike produces an analysis and test report. Implementation changes and optimization are separate follow-up activities. Static code observations below are not measured performance results or confirmed production defects.

## 2. Scope and baseline

### Source and code baseline

- Requirement: Jira BCOH2H-2436, “Assess Performance of Account Transaction History Inquiry API for High-Volume CASA Accounts”; PDF export generated 18 November 2026.
- Reviewed code: main project checkout `hth-dsp`, commit `be7c0a04edb7c9f9e53159080928e832dd21641b`.
- Main entry point: `POST /account/v1/transaction-inquiry`, operation `inquireTransactions`.
- Primary implementation: `TransactionServiceImpl` under `com.hkbea.hth.service.payment`.
- Current maximum and default page size for this endpoint: **50**, confirmed in the API schema and runtime validation.
- An older `/account/v1/transactions/enquiry` endpoint also exists. Do not mix its results with the selected endpoint.

Record the actual deployed commit and any differences from this reviewed baseline before testing. The analysis does not describe the older worktree implementation as the deployed version.

### Included

Single-day and multi-day enquiries, maximum page size, complete five-day pagination, five-user and ten-user concurrency, BG3 integration latency, database behavior, application resources, and result correctness. Include composite/multi-account and multi-currency behavior where supported by the prepared test account.

### Clarifications required

| Topic | Requirement ambiguity | Proposed handling |
| --- | --- | --- |
| Recent days | Objectives specify two calendar days; preparation specifies two business days | Agree exact dates and their relationship to the host business date. Keep weekend/holiday cases separate. |
| Five-day data | Preparation requires two days, but S4 requires five days | Prepare and reconcile all five dates; document daily volume, including zero-transaction days. |
| Performance acceptance | No numeric latency, throughput or resource thresholds are stated | Agree targets before execution. Without targets, report baseline and risks, not “performance passed.” |
| Concurrent users | Duration, pacing and account distribution are unspecified | Use the proposed workload below, subject to environment-owner agreement. |
| Endpoint and environment | Deployment version and public route are unspecified | Freeze endpoint, deployment, gateway route and environment in the run record. |

## 3. Current processing model

The reviewed implementation resolves accessible accounts, optionally filters them using BG3 account information, and processes matching accounts sequentially. Recent transaction paths call BGNC401 for transaction lists and BGNC400i once per returned transaction for details. Historical paths query `DIGX_CZ_BATCH_ACCT_HIST_CASA`. Mixed paths supplement recent transactions with database records.

Results are consolidated, sorted and limited to the requested page size. Cursor source is tracked using Spring Cache; the deployed cache provider and TTL must be recorded.

Logical flow:

```text
Client / gateway / BFF
  -> Support API and account resolution
  -> H7E0 account information
  -> BG3 list (BGNC401) and sequential details (BGNC400i)
     and/or historical database query
  -> Consolidation, sorting and pagination
  -> Response
```

Date routing in the implementation distinguishes today, yesterday and older ranges. The API description is simpler and does not fully describe those branches. Verify actual requested dates and BG3/DB date parameters in traces, rather than assuming every two-day request uses the same backend path.

## 4. Current code risks and verification priorities

Reference IDs map to Section 11.

| ID | Observation from code | Potential impact | Verification |
| --- | --- | --- | --- |
| R1 | BGNC400i detail calls execute sequentially for each accumulated transaction [C1] | A 50-record page can require 50 detail calls in addition to list and account calls. Downstream latency accumulates. | Count calls and measure individual and aggregate detail time per API request. |
| R2 | Outer pagination derives `hasMoreData` from `allTransactions.size() > pageSize`, rather than preserving each source's continuation state [C1] | A single account returning exactly 50 records can produce `hasMoreData=false` despite more backend records. Complete-retrieval tests could stop prematurely. | Reproduce with one matching account and a known count above 50. Compare inner continuation evidence with public response. |
| R3 | Each matched account is processed sequentially, then results are merged and truncated; a single output cursor is derived from the final transaction [C1] | Composite accounts can amplify downstream work. Cross-account cursor progression may omit or repeat records. | Trace calls per internal account and reconcile transaction identity across all pages. |
| R4 | Account information can be queried during filtering and queried again during retrieval [C1] | Avoidable H7E0 calls, multiplied by account count and page requests | Compare actual call count with distinct accounts and filtering conditions. |
| R5 | The DB supplementation helper performs another page query to determine whether more records exist [C1, C2] | Additional SQL work per page on this path | Record statement counts and timings by branch; assess a look-ahead approach only after measurement. |
| R6 | Historical SQL filters by account/date/optional currency, joins descriptions, sorts and limits rows [C2] | Scan, sort or join cost may grow with stored volume; actual impact depends on indexes and plan | Obtain actual execution plans, row counts, logical reads and timings. Do not infer a missing index from SQL alone. |
| R7 | SQL orders by date, reference and time, while continuation filters only on transaction reference [C2] | Correctness depends on reference ordering and uniqueness across dates/accounts | Confirm host identifier semantics; test repeated and non-monotonic references across days. |
| R8 | Unknown cursor source attempts BG3 before a DB fallback; the cache key does not include the date range [C1, C3] | Extra calls on cache misses; possible interference between overlapping enquiries using the same account/cursor | Test warm/cold cursor metadata, parallel ranges and multi-instance requests in a controlled environment. |
| R9 | BG3 and DB date-boundary logic differs from the simplified API description [C1, C4] | Missing, duplicated or out-of-range records around today/yesterday and batch availability boundaries | Reconcile exact dates against host business date and database load watermark. |

R2 and other data-integrity risks must be checked before interpreting full-journey performance. A failed integrity check does not prevent collecting diagnostic timings, but that run cannot demonstrate successful complete retrieval.

For a single-account recent page, a useful diagnostic model is:

```text
Page time approximately equals:
  gateway/BFF/application overhead
  + account-information time
  + sum of transaction-list call times
  + sum of sequential transaction-detail call times
  + any DB/cache time
  + mapping and serialization time
```

This is an explanatory model, not a benchmark. For illustration only, 50 serial detail calls averaging 100 ms contribute about five seconds before other work.

## 5. Test preparation and measurements

### Data and environment

1. Select an authorized CASA/composite account with **more than 100 transactions on each of the agreed recent two dates**. Record daily counts and a separate five-day reference set.
2. Confirm access rights and a successful small inquiry through the intended external route.
3. Establish an independent expected result set from approved host/DB evidence. Do not use the API under test as its own source of expected totals. Account for batch-load lag and transactions still arriving today.
4. Prefer a stable window or controlled account with no new transactions during a traversal. If this is impossible, define a reconciliation cutoff and document the API's snapshot limitations.
5. Record internal account count, currencies and product types. Keep masked account identifiers in the report.
6. Record deployed versions, host business date, DB watermark, replica count, CPU/memory allocation, JVM, cache configuration, connection pools, timeouts and retry policies.
7. Synchronize clocks across client and observability systems. Enable existing metrics/traces at an agreed level; avoid verbose payload logging that distorts latency or exposes customer data.

### Metrics and definitions

| Metric | Definition |
| --- | --- |
| Page response time | Client elapsed time from request start until the complete response body is received |
| Full-journey time | Elapsed time from first-page request until the valid final page is received; excludes configured pauses between separate journeys |
| Latency distribution | Sample count, median/P50, P95, P99 where sample size is sufficient, and maximum; retain raw timings |
| Throughput | Completed page requests/second and completed full journeys/minute, reported separately |
| Error rate | Failed requests divided by attempted requests; classify transport errors, timeouts, HTTP errors and business-response errors |
| Data integrity | Expected/actual unique count, duplicates, missing and unexpected records, ordering, date/currency scope and cursor progress |
| Integration cost | H7E0/401/400i calls per page, latency, errors, retries and pool wait time where available |
| DB cost | Statement count, elapsed time, returned/examined rows, logical reads, waits and execution plan |
| Application resources | CPU, heap, GC pause/time, active/queued requests, thread/connection pool utilization |

A successful HTTP response with an error business code is not a successful enquiry. Do not exclude failed or timed-out requests from reliability statistics. Report censored timeout observations separately from completed-response percentiles.

### Proposed execution defaults

These are suggested run settings, not Jira acceptance criteria:

- Use one consistent load tool, such as JMeter or k6, and save its version and script revision.
- S1–S3: five warm-up journeys followed by at least 30 measured journeys per variant; increase samples where needed for meaningful percentile estimates.
- S4: five measured full traversals after warm-up; report each traversal individually rather than a misleading small-sample P99.
- S5–S6: 60-second ramp-up, two-minute warm-up, ten-minute measured steady state, then drain outstanding requests. Repeat three times when environment capacity permits.
- Do not clear shared caches. Separate natural first-run observations from warm runs; arrange isolated cache-miss tests with the environment owner.
- Execute outside disruptive batch/deployment windows or explicitly label those windows as a separate variant.
- Stop load generation if agreed error, resource or downstream-protection limits are reached. Record the stopping condition and incomplete workload.

## 6. Common pagination procedure

Use this procedure for every complete-retrieval journey:

1. Send the first request without a cursor. Keep account, date range, type, currency and page size fixed throughout that journey.
2. Record request/correlation ID, elapsed time, response size, transaction count, `hasMoreData` and `nextCursor`.
3. If `hasMoreData=true`, require a usable next cursor and pass it exactly as returned. Do not invent cursors to bypass an application defect.
4. Detect repeated cursors, repeated pages, empty pages claiming continuation, invalid cursor format and lack of progress. Abort and classify the journey if the agreed page/time guard is exceeded; do not count it as complete.
5. On `hasMoreData=false`, reconcile against the independent expected set. A final-page flag alone does not establish completeness.
6. Use an approved transaction identity including internal account and currency where needed; do not assume transaction reference alone is globally unique. Check duplicates and ordering without silently deduplicating the API output.

## 7. S1–S6 execution steps

### S1 — One-day history above 100 transactions

**Preparation:** A verified date with more than 100 records. Use page size 50 and one user. Run a recent/BG3-backed and an older/DB-backed date as separate variants where data permits.

1. Capture the date, expected count and expected backend path.
2. Execute the common pagination procedure through the public endpoint.
3. Repeat using the agreed warm-up and measured-run settings.
4. Compare first-page, continuation-page and complete-journey timing.
5. Correlate each page with H7E0, BGNC401, BGNC400i and SQL calls.

**Checks:** At least three pages are needed for more than 100 records at page size 50. If fewer pages are returned, investigate completeness before reporting success. Verify all records belong to the requested date.

### S2 — Latest two-day history above 200 combined transactions

**Preparation:** Two agreed dates, each above 100 transactions, with reference counts by date/account/currency.

1. Fix the calendar/business-day interpretation and host business date in the run record.
2. Request the two-day range and traverse all pages at page size 50.
3. Record date distribution per page and backend route transitions.
4. Check duplicates, omissions and ordering across the date boundary.
5. Repeat and compare with S1, separating increased page count from increased per-page latency.

**Checks:** More than 200 records require at least five pages. Do not assume the range necessarily reaches DB: verify actual route selection and batch availability.

### S3 — Maximum supported page size

**Preparation:** A range with more than 50 records. Maximum for the selected endpoint is 50.

1. Measure otherwise identical requests using page sizes 10, 25 and 50; the smaller sizes are comparison variants.
2. Measure both the first page and complete retrieval for each size. Compare against the same expected result set.
3. Record response bytes, detail-call count, page latency and total journey time.
4. Check that no page exceeds the requested size and continuation remains valid.
5. Optionally submit page size 51 once as a contract check, outside performance statistics; expect validation rejection.

**Checks:** Distinguish a slower large page from a faster total retrieval with fewer page requests. A maximum-size request must not silently return partial or incomplete results.

### S4 — Retrieve all pages for a five-day range

**Preparation:** Known counts for five dates, preferably spanning recent host data and historical DB data. Record zero-volume dates and load watermarks explicitly.

1. Capture the expected five-day total and transaction identity set.
2. Traverse using page size 50 without changing filters or cursors manually.
3. Save page-by-page timings, counts, boundary records, continuation flags and source transitions.
4. Reconcile the complete set, including the BG3-to-DB boundary and composite-account merge.
5. Repeat five measured traversals. Run controlled cursor-cache-miss/multi-instance variants separately if supported.

**Checks:** Expected minimum data pages are `ceil(expectedCount / pageSize)`; the contract may require an additional empty termination page, which must be documented. Verify no gaps, duplicates, invalid cursor or endless loop. An early-stop defect makes complete-journey performance inconclusive.

### S5 — Five concurrent users

**Preparation:** Five authorized virtual users with independent pagination state. Primary workload: repeat the same agreed S2 complete-retrieval journey, with page size 50 and a one-second pause between journeys, not between pages.

1. Use the same high-volume account for the primary shared-account contention test. Record whether identities/tokens are shared or distinct and any applicable throttling.
2. Give each virtual user its own cursor and request variables. Each user issues one request at a time and waits for its response before the next page.
3. Apply the proposed ramp-up, warm-up, steady-state and drain periods.
4. Capture page and journey latency, achieved throughput, errors and backend/resource metrics.
5. Validate completeness per journey and compare against the single-user baseline.
6. If equivalent accounts are available, repeat with separate accounts as a distinct distribution variant.

**Checks:** Five users do not guarantee five in-flight requests continuously. Record achieved concurrency and throughput. Include all failures; do not hide them through load-tool retries.

### S6 — Ten concurrent users

**Preparation:** Same dataset, workload, pacing, deployment and metrics as S5; increase only virtual-user count to ten.

1. Confirm the environment has recovered from S5 and background workload is comparable.
2. Execute the same workload with ten independent virtual-user sessions.
3. Capture the same timing, integrity and resource metrics.
4. Compare P95, full-journey throughput, error rates, pool waits and BG3 latency with S5.
5. Identify saturation or disproportionate latency growth. Repeat the separate-account variant if it was used for S5.

**Checks:** Do not expect throughput to double automatically. Record whether higher load is constrained by BG3, DB, application pools, gateway throttling or the load generator itself.

## 8. Results recording templates

All result cells remain **Not run / TBD** until populated with measured evidence.

### Run manifest

| Field | Value |
| --- | --- |
| Run ID, scenario and variant | TBD |
| Executor and execution time/timezone | TBD |
| Environment, endpoint and deployed commit | TBD |
| Load-tool version and script revision | TBD |
| Host business date and DB load watermark | TBD |
| Masked account, product, currencies, internal-account count | TBD |
| Date range and independently verified daily counts | TBD |
| Page size, users, pacing and account distribution | TBD |
| Ramp-up / warm-up / measured duration / repetitions | TBD |
| Cache condition, replica count and routing | TBD |
| JVM, CPU/memory, pools, timeouts and retries | TBD |
| Background load, deployment or batch activity | TBD |
| Evidence location and agreed acceptance thresholds | TBD |

### Scenario summary

| Scenario | Variant / run IDs | Measured pages / journeys | Page P50 / P95 / max (ms) | Journey P50 / max (s) | Requests/s | Errors / attempts | Integrity | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |
| S2 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |
| S3 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |
| S4 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |
| S5 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |
| S6 | TBD | TBD | TBD | TBD | TBD | TBD | Not run | Not run |

Add P99 only with sample count and a suitable sample population. Keep different routes, page sizes and cache conditions in separate rows.

### Page and correctness evidence

| Run / user / journey / page | Correlation ID | Cursor in / out | Records | More? | Elapsed ms | HTTP / business code | Backend source | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

| Journey | Expected unique | Returned total | Returned unique | Missing | Duplicate | Unexpected / out-of-range | Cursor / ordering issues | Complete? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

### Backend and resource breakdown

| Run / request cohort | H7E0 calls / time | 401 calls / time | 400i calls / time | SQL calls / time | Cache misses | Retries / timeouts | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

| Run | CPU avg / peak | Heap peak / limit | GC pause / time | Threads / queues | HTTP / DB pool waits | DB reads / plan | Gateway throttles |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Specify units and whether backend time is per call, summed per request, or a percentile. Do not add aggregate percentiles together to claim an end-to-end percentile.

### Findings and follow-up actions

| Finding ID | Scenario / evidence | Observed behavior and impact | Cause confidence | Proposed action | Priority / owner | Follow-up Jira | Retest |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TBD | TBD | TBD | Hypothesis / reproduced / confirmed | TBD | TBD | TBD | TBD |

### Acceptance and decision record

| Criterion | Agreed threshold | Observed result | Verdict | Owner |
| --- | --- | --- | --- | --- |
| Complete and correct retrieval | No unexplained missing, duplicate or out-of-range records | TBD | Not run | TBD |
| Per-page P95 / maximum latency | TBD | TBD | Not assessed | TBD |
| Complete-journey latency | TBD | TBD | Not assessed | TBD |
| Error / timeout rate | TBD | TBD | Not assessed | TBD |
| Throughput at 5 / 10 users | TBD | TBD | Not assessed | TBD |
| Resource and downstream limits | TBD | TBD | Not assessed | TBD |

Use Pass, Fail, Inconclusive or Not run. Missing targets or incomplete retrieval must not be reported as a performance pass.

## 9. Optimization opportunities to evaluate after measurement

1. Correct confirmed pagination/date-boundary defects before drawing conclusions about full retrieval.
2. Quantify serial detail-call cost. Check whether BG3 supports a suitable batch/list response before considering bounded parallel calls; respect downstream capacity and ordering/error behavior.
3. Reuse account-information results within a request where semantics permit.
4. Avoid fetching details for records that will be discarded during composite-account pagination, subject to a correct global cursor design.
5. Replace redundant next-page DB probes with a measured look-ahead strategy if compatible with the API contract.
6. Tune SQL/indexes only with actual plans and representative data; consider write and batch-load costs.
7. Evaluate cursor metadata and cache-miss behavior, including whether a cursor should encode enough source/account state to resume deterministically.

Each recommendation must include supporting measurements, expected benefit, implementation risk and a retest plan. No optimization is included in this document-only change.

## 10. Spike completion checklist

- Requirement ambiguities and performance targets resolved or explicitly recorded as limitations.
- Prepared data independently reconciled for two-day and five-day ranges.
- S1–S6 executed, or blocked cases documented with evidence and ownership.
- Complete-retrieval correctness assessed before performance conclusions.
- Per-page, full-journey, integration, SQL and resource results attached.
- Bottlenecks distinguished from hypotheses, with prioritized follow-up issues.
- Business, QA and relevant integration/DB owners review the conclusion and release risks.

## 11. Code evidence references

Paths are relative to repository root at the reviewed commit; line numbers are review aids and may move after changes. Use these repository paths when attaching source links in Confluence.

| ID | Source | Relevant locations |
| --- | --- | --- |
| C1 | `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/TransactionServiceImpl.java` | Account filtering around 686–737; per-account retrieval and consolidation 775–884; source supplementation 913–950; list/detail calls 971–1053; DB look-ahead 1060–1098; page-size validation 1171–1175 |
| C2 | `hth-ms-support/hth-ms-support-svc/src/main/resources/mapper/BatchTxnHistoryMapper.xml` | `selectBatchTxnHistoryByCursor`, lines 75–111 |
| C3 | `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/config/OpcodeMapping.java` | Cursor-source cache methods, lines 111–119 |
| C4 | `hth-ms-support/hth-ms-support-api/src/main/resources/api/hth-support-account-1.0.0.openapi.yaml` | Endpoint around 656–674; request cursor/page-size schema around 1455–1472 |
| C5 | `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/AccountServiceImpl.java` | New inquiry delegates to transaction service around 700; older enquiry marked deprecated around 686 |
