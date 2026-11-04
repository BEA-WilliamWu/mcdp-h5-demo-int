# BCOH2H-2055 - HTH CHATS Payment Account Posting

## 1. Minimal delivery scope

Revision: 5 November 2026. This revision replaces the earlier finality-based design.

Retain the existing HTH CHATS submission flow and database schema. Relative to the reviewed BCOH2H-2065 implementation, the only production change is to reuse the existing rejection-reason fallback in `ChatsPaymentService`:

`errorMessage -> statusReason -> existing generic rejection message`

The original IPE error code is preserved. No new public status, error code, posting API or fee mapping is introduced. This is a limited HTH change for initial integration, not evidence that all account-posting acceptance criteria are complete.

## 2. Existing behavior retained

- Validate the account, principal balance and matching debit/payment/credit currencies; preserve the instructed and settlement amounts.
- Submit through the existing IPE `/credit-transfer` interface, using the existing HTH channel identity and the reviewed 2065 field mapping.
- Map IPE `SUCCESS` to `Success`, `FAILED` to `Rejected`, and in-flight results to `Processing`. API acceptance alone does not establish successful EBK posting.
- Save the submission result through the existing payout mapper. Retain the original initial-row state and database-failure behavior.
- Refresh only `Processing` payments through the existing enquiry flow. This revision does not add subsequent refresh of successful payments, background polling or a callback.
- Retain principal-balance error `500181`, which stops processing before insertion/submission. It does not create a queryable Rejected payment record.
- Preserve existing fee-cache handling and fee placeholders. An over-charge result is not treated as the total Bank Charge. The existing null-result fee-cache limitation remains outside this scope.

## 3. Removed from the earlier delivery

Remove the `IPE_TERMINAL` entity mapping, its SQL migration, CHATS-specific finality enquiry, conditional submission updates and related tests. Restore `PaymentPayoutRecord`, `PaymentServiceImpl` and `PaymentServiceSupport` to their pre-2055 implementation. Remove the new insert-failure blocking behavior and retain the existing unused FX constant; this revision does not perform unrelated cleanup.

No database change is required. If the earlier nullable column was already deployed, this application revision does not use it and requires no DROP COLUMN operation.

The tradeoff is explicit: an HKICL rejection received after HTH has saved `Success` will not be picked up by the existing status-based enquiry flow. AC3 requires original and reversal entries in AFT; any additional HTH status-transition requirement must be agreed separately with the IPE status contract.

## 4. Posting requirements and integration verification

BCO source contains fee enquiry followed by `HTCC1`, `HTC02` and, when enabled, `OPY_CHATS`. HTH delegates payment execution to IPE; the IPE implementation and actual host posting were not verified in this delivery.

| Requirement | Verification needed |
| --- | --- |
| Reuse BCO operating keys and posting channel | Compare actual AFT results with the story's examples: withdrawal key `H43`, Bank Charge key `183`, channel `CCB INTERNET`. Do not equate IPE `channel=HTH` with the host posting-channel field. |
| Teller ID | Verify `CBSIPE1` as required by the story. The old BCO screenshot's `CBSEBK1` is not the required HTH value. |
| No FX | Verify rate 1 and equal account/transaction principal amounts in EBK/AFT. Existing HTH same-currency validation is retained. |
| Passbook and statement references | Verify all four reference fields against the existing BCO arrangement; HTH does not construct them directly. |
| Bank Charge | Confirm IPE performs the equivalent of BCO's service-fee enquiry and passes the fee to posting. BCO distinguishes `SvcChrg` from `OvrChargeAmt`; the current HTH request omits the service-fee amount. |
| AC1: successful posting/submission | Confirm what IPE `SUCCESS` means and verify the withdrawal and Bank Charge entries. No assumption is made about `terminal` at this stage. |
| AC2: insufficient balance | Verify downstream rejection with no AFT posting, including fee-insufficient cases. Confirm whether local pre-validation failures also require a persisted Rejected record. |
| AC3: HKICL rejection | Verify original posting and reversal entries in AFT, including principal/fee treatment and duplicate-event handling. HTH status updates do not perform a funds reversal. |

## 5. Code and tests

- `ChatsPaymentService.java`: one production-line change from the reviewed 2065 baseline, selecting `ERROR_MESSAGE_WITH_FALLBACK`.
- `ChatsPaymentServiceTest.java`: cover Processing and Rejected result persistence, original error-message/code precedence, missing rejection-reason fallback and the fee-mapping boundary. Retain existing 2065 mapping and same-currency regression coverage.
- The three restored production files are included in the replacement delivery only to undo the earlier 2055 changes; they add no behavior relative to the pre-2055 baseline.

Java paths are under `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/`; tests are under that module's `src/test/java/com/hkbea/hth/service/`.

## 6. Verification and delivery

Java 21 and offline Maven verification passed all 78 targeted tests on 5 November 2026, using an isolated source snapshot excluding unrelated working-tree changes. No dependency or project POM changes were required.

| Test suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ChatsPaymentServiceTest | 49 | 0 | 0 | 0 |
| PaymentCommonRoutingBuilderTest | 5 | 0 | 0 | 0 |
| PaymentRoutingSubmitterTest | 9 | 0 | 0 | 0 |
| PaymentServiceImplTest (enquiry methods only) | 15 | 0 | 0 | 0 |

The full project regression was not run. SIT/UAT must verify the AFT outcomes in section 4; unit tests do not prove host posting or reversal. No database migration was run.

Synchronize the replacement package to `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2055`, preserving source paths under `source/`. Apply it after the reviewed 2065 package. Keep the target Git repository uncommitted for manual submission.

If the earlier 2055 package was already copied into a project, replace all supplied source files and remove the old `ChatsPaymentEnquiryTest.java` and `bcoh2h_2055_chats_ipe_terminal.sql`. No migration is executed or required by this revision. See `DELIVERY.txt` for the complete replacement/removal paths.
