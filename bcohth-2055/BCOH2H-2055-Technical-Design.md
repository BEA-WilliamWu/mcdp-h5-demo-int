# BCOH2H-2055 - HTH CHATS Payment Account Posting

## 1. Objective and agreed scope

Support the HTH payment lifecycle around CHATS account posting through the existing HTH -> IPE -> EBK flow. A payment may have completed account posting while interbank settlement is still pending; a subsequent HKICL rejection must remain visible to HTH.

This delivery implements the confirmed HTH status handling and tests. As agreed, the Bank Charge field mapping, teller/operating-key configuration and host reversal rules remain downstream integration items. HTH does not implement direct EBK posting or reversal calls.

Source: `jira/[#BCOH2H-2055] HTH CHATS Payment Account Posting.pdf`, updated 30 October 2026. This change builds on the BCOH2H-2065 HTH field-mapping implementation.

## 2. Story requirements and ownership

| Requirement | HTH implementation / downstream responsibility |
| --- | --- |
| Reuse existing posting operating keys | IPE/EBK configuration; no new HTH operating key or request field |
| Teller ID `CBSIPE1` | IPE/EBK configuration; verify in AFT |
| No FX; equal account/payment currency and principal amount; rate 1:1 | Retain HTH same-currency validation and unchanged instructed/settlement amounts. IPE/EBK must apply rate 1; the current CHATS contract has no dedicated posting-rate field |
| Existing passbook and statement-reference arrangement | Downstream mapping; verify actual references in AFT |
| Successful EBK posting and CHATS submission -> Completed, pending interbank settlement | Preserve IPE `SUCCESS` -> HTH `SUCCESS` mapping and separately persist `terminal=false` so subsequent outcomes can be queried |
| Insufficient balance -> Rejected, no AFT posting | Retain the HTH principal-balance precheck; persist downstream `FAILED` as Rejected. EBK owns the final balance check, including fees, and no-posting guarantee |
| HKICL rejects invalid value date -> original posting plus reversal entries | Refresh a non-terminal CHATS result even after success. IPE/EBK owns the reversal and AFT records |

The existing public status enum remains unchanged: `SUCCESS`, `PROCESSING` and `REJECTED` serialize as `Success`, `Processing` and `Rejected`. The story's Completed stage maps to the existing success status; no new public settlement status is introduced. Submission acceptance alone is not proof of successful posting.

## 3. Submission and durable pending state

1. Run existing CHATS validation, balance checking and Scameter checks.
2. Keep the smart-routing request, unique transaction identifier, 2065 field mapping and HTH channel identity.
3. Insert a payout row with `STATUS=Processing` before submitting to IPE. `IPE_TERMINAL` is initially null because no downstream finality result exists yet.
4. If the insert fails or affects no row, return the existing service error `600000` and do not submit the payment. An external payment must have a durable local record for subsequent enquiry.
5. Submit once through IPE `/credit-transfer`, retain existing status mapping, and save the response's `terminal` flag for CHATS.
6. On a timeout, null response or inconclusive submission response, retain `Processing` so enquiry can reconcile the result. Do not automatically resubmit.

The submission update applies only to the untouched initial row: matching ID, `Processing` status, and null IPE status/finality. This prevents a slow submission response from overwriting a result already saved by a concurrent enquiry. If the post-submission database update fails, the existing pending row remains available for reconciliation.

## 4. Finality and subsequent enquiry

Both checked-in IPE response models define `terminal` as: the result is final and no further polling is needed. HTH persists this independently from the business status.

| Stored CHATS state | Enquiry behavior |
| --- | --- |
| `IPE_TERMINAL=true` | Return the stored result; do not query IPE |
| `IPE_TERMINAL=false`, including `Success` or `Rejected` | Query IPE for the latest result |
| `IPE_TERMINAL=null` and status is `Processing` or blank | Query IPE; supports pending rows created before this change |
| `IPE_TERMINAL=null` and a completed/rejected legacy status | Retain existing behavior; do not infer finality or poll indefinitely |

Use the existing read-only `/result/query` interface and unique HTH/IPE payment identifier. Require the returned transaction ID to match before applying a CHATS result. Inconclusive or failed enquiries retain the stored state.

Persist status, finality, IPE status/reason/reference and rejection details when any changes. A finality-only transition, such as `SUCCESS/false -> SUCCESS/true`, must also be saved. A missing `terminal` in a later response does not erase a previously known flag.

For rejected results, prefer `errorMessage`, then `statusReason`, then the existing generic rejection text. A later non-failed result clears an obsolete rejection message.

Enquiry updates compare the row ID, previous status, finality and last-update timestamp. If another update has changed the row, skip the stale update and reload the current database result. Repeated identical enquiries do not write again.

Refresh runs when the transaction is enquired; this delivery introduces no background polling or callback endpoint. FPS, TT, DR and internal-payment refresh behavior remains unchanged.

## 5. Balance, fees and reversal boundaries

- The HTH precheck covers the principal amount. Insufficient principal balance retains error `500181` and stops before payout insertion or IPE submission. Changing this validation error into a persisted Rejected payment requires a separate API/business decision.
- EBK must check the final required amount, including applicable charges. If IPE returns `FAILED`, HTH returns and persists Rejected with the available reason.
- `overChargeAmount` / `overChargeCcy` are documented as over-charge fields. They are not assumed to be the total AFT Bank Charge and are not copied into `SVC_AMOUNT` / `SVC_CUR` in this delivery. The existing fee-cache behavior and initial fee placeholder remain unchanged; the placeholder is not evidence that the actual fee is zero.
- HTH does not trigger principal/fee reversal or reuse transfer-limit reversal as a ledger reversal. Updating a payment status cannot itself create duplicate ledger entries.
- Remove the unused `85.51068523` CHATS exchange-rate constant. It was not sent by the current routing flow. No speculative FX or posting-rate field is added.

## 6. Database migration and deployment

Add nullable `HTH_BEA.HTH_PAYMENT_PAYOUT.IPE_TERMINAL NUMBER(1)` with allowed values 0/1:

- `0`: IPE explicitly reports non-terminal.
- `1`: IPE explicitly reports terminal.
- `NULL`: unknown or legacy row.

Script: `hth-ms-support/hth-ms-support-svc/src/main/resources/sql/bcoh2h_2055_chats_ipe_terminal.sql`.

**Apply this additive SQL before deploying the application.** The entity's new column is read by payout queries, so deploying application code without the column will break those queries. The script is supplied for deployment and is not executed by this local task. It makes no bulk status/finality backfill.

For rollback, deploy the previous application first and retain the nullable column. Existing successful rows with unknown finality require a targeted operational reconciliation based on IPE evidence; do not mark all historical successes final or non-final automatically.

## 7. Code changes

| File | Change |
| --- | --- |
| `service/payment/ChatsPaymentService.java` | Durable Processing insert; stop before submission on insert failure; rejection-reason fallback; remove unused FX constant |
| `service/payment/PaymentServiceSupport.java` | CHATS finality persistence and conditional submission update; retain other networks' update path |
| `entity/PaymentPayoutRecord.java` | Map nullable IPE finality |
| `service/PaymentServiceImpl.java` | CHATS-specific finality-aware enquiry, matching result ID, metadata updates and stale-write protection |
| `resources/sql/bcoh2h_2055_chats_ipe_terminal.sql` | Add the finality column and constraint |
| `ChatsPaymentServiceTest.java` | Submission ordering, insert failure, acceptance, success/finality, insufficient-balance rejection, reason fallback and fee-boundary tests |
| `ChatsPaymentEnquiryTest.java` | Subsequent rejection, finality-only updates, query failures, legacy behavior, concurrency protection and FPS isolation |

Java source paths above are relative to `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/`. The SQL path is relative to `src/main/`; tests are under `src/test/java/com/hkbea/hth/service/` in the same module.

## 8. SIT/UAT and open integration items

1. Confirm IPE/EBK reuse of operating keys, `CBSIPE1`, channel-to-posting configuration and reference formatting. Keep the HTH API channel identity unchanged.
2. Confirm the Bank Charge amount/currency fields and whether fee enquiry is required after asynchronous posting. Do not infer the amount from the story's sample charge.
3. Verify same-currency rate 1 and equal principal amounts in EBK/AFT for each supported CHATS currency.
4. Verify that IPE reports `SUCCESS, terminal=false` at the story's posting-success/pending-settlement stage and supplies a final result after settlement or rejection. A premature `terminal=true` would prevent subsequent enquiry by design.
5. Confirm reversal ownership, principal/fee treatment, duplicate-event handling and whether a rejected payment remains non-terminal while reversal is outstanding.
6. Decide whether an HTH pre-validation balance failure also requires a persisted Rejected record; the current API error behavior is retained.

SIT evidence must cover:

- **AC1:** successful EBK posting and CHATS submission; AFT withdrawal and Bank Charge entries, configured teller/references, and the appropriate success/finality state.
- **AC2:** insufficient principal, plus principal-sufficient/fee-insufficient cases; rejection and no AFT posting.
- **AC3:** subsequent HKICL invalid-value-date rejection; original posting plus reversal entries, and HTH enquiry reflects the downstream rejection without resubmitting the payment.

UAT should review these business outcomes and AFT evidence. Unit tests validate the HTH boundary, not actual posting, settlement or reversal. Oracle migration execution and SIT/UAT remain outstanding.

## 9. Delivery

Commit this story's code, tests, SQL and design to local Git. Synchronize changed code/tests/SQL beneath `/Users/devs/DWksp/mcdp-h5-demo/bcohth-2055/source/`, preserving repository-relative paths. Place this document at `bcohth-2055/BCOH2H-2055-Technical-Design.md`. Leave the 2065 delivery directory unchanged.

## 10. Local verification - 4 November 2026

Java 21/Maven compilation succeeded. No dependency or POM changes were required. The test run used the existing local dependency cache and a temporary reactor containing `hth-parent`, `hth-common`, `dsp-ms-support` and `hth-ms-support`.

| Test suite | Tests | Passed | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| ChatsPaymentServiceTest | 57 | 57 | 0 | 0 |
| ChatsPaymentEnquiryTest | 24 | 24 | 0 | 0 |
| PaymentCommonRoutingBuilderTest | 5 | 5 | 0 | 0 |
| PaymentRoutingSubmitterTest | 9 | 9 | 0 | 0 |
| PaymentServiceImplTest | 51 | 49 | 2 | 0 |
| FpsPaymentServiceTest | 30 | 23 | 2 | 5 |
| Total | 176 | 167 | 4 | 5 |

All 95 CHATS/routing tests pass, including 34 added test executions for this story. No tests were skipped and test failures were not ignored.

The 9 unsuccessful broader-regression cases were reproduced in an isolated source snapshot of pre-change commit `946d019`:

- Payment entry: `malformedDebtorAccountId_throws` expects `500160` but receives `500186`; `unknownDebtorAccount_throws` expects `NoSuchElementException` but receives `ParameterException`.
- FPS: `limitDeductionFails_throws`, `pendingInsertException_swallowed`, `fpsTxRef_truncated`, `ipeException_returnsProcessingAndPersists`, `rejectedPayment_releasesLimit`, `success_persistsPendingThenUpdates` and `success_unregisteredPayee_adhocProxy`. These hit the existing missing-creditor-address null dereference. They are not changed by this story.

The complete regression command therefore returns a failing Maven exit status; it must not be reported as a fully green build.

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,ChatsPaymentEnquiryTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,PaymentServiceImplTest,FpsPaymentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

Database behavior is covered at the mapper/update-condition boundary. The Oracle DDL was not executed against a database; actual schema migration, AFT entries and downstream settlement/reversal require SIT.
