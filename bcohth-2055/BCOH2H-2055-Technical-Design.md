# BCOH2H-2055 - HTH CHATS Payment Account Posting

## 1. Scope and baseline

Revision: 5 November 2026. Baseline: synchronized source commit `a10fb5d` (`sync`). This delivery supersedes the earlier 2055 packages.

Apply the agreed minimum change without modifying the database: reuse the existing rejection-reason fallback in `ChatsPaymentService`:

`errorMessage -> statusReason -> existing generic rejection message`

Preserve the original IPE error code. The production diff is one line. This limited HTH delivery supports initial integration; it does not establish that all account-posting acceptance criteria are complete.

## 2. Behavior

- Continue submitting CHATS payments through IPE `/credit-transfer` with the synchronized request mapping and HTH channel identity.
- Retain IPE `SUCCESS -> Success`, `FAILED -> Rejected`, and in-flight results -> `Processing`. Submission acceptance alone is not proof of successful EBK posting.
- For a rejected submission, preserve a nonblank `errorMessage`. Otherwise use a nonblank `statusReason`; if both are missing or blank, use `Rejected by payment engine (no detail returned)`. Return and persist the selected reason through the existing mapper.
- Preserve the synchronized validation, limits, account handling, initial payout state, database-failure behavior and enquiry implementation. Enquiry still refreshes only Processing payments.
- Retain same-currency checks and instructed/settlement amounts. Principal-balance pre-validation still returns `500181` before insertion/submission; it does not create a queryable Rejected payment record.
- Retain the new baseline's `CacheHelperUtils.saveMtbInfo` calls and `MtbInfoDto` fee fields. Do not restore the old CacheManager/string writer or map over-charge amounts to the total Bank Charge.

No `IPE_TERMINAL` field, SQL, status-transition extension, new public error/status, direct posting API or reversal call is added. `PaymentPayoutRecord`, `PaymentServiceImpl` and `PaymentServiceSupport` remain exactly as synchronized; the old restoration copies are not part of this package.

A later HKICL rejection after a saved Success is not picked up by the current enquiry flow. AC3 requires AFT original and reversal entries; any additional HTH transition must be agreed with the IPE status contract.

## 3. Posting requirements pending integration

BCO source contains service-fee enquiry followed by `HTCC1`, `HTC02` and, when enabled, `OPY_CHATS`. HTH delegates execution to IPE. Actual IPE/EBK posting and AFT results have not been verified by this delivery.

| Requirement | Verification needed |
| --- | --- |
| Operating keys and posting channel | Compare AFT results with the story examples: withdrawal `H43`, Bank Charge `183`, channel `CCB INTERNET`. IPE `channel=HTH` is not itself the host posting-channel field. |
| Teller ID | Verify `CBSIPE1` as specified in the story, rather than the old BCO screenshot's `CBSEBK1`. |
| No FX | Verify rate 1 and equal account/transaction principal amounts in EBK/AFT. Existing HTH same-currency controls remain. |
| Transaction references | Verify Passbook Transaction Reference and Statement Transaction References 1/2/3 against the existing BCO arrangement. |
| Bank Charge | Confirm who performs the equivalent of BCO's service-fee enquiry. BCO distinguishes `SvcChrg` from `OvrChargeAmt`; HTH currently omits the service-fee amount in the request. |
| AC1 | Confirm IPE SUCCESS corresponds to successful EBK posting and CHATS submission; verify withdrawal and Bank Charge entries in AFT. |
| AC2 | Verify insufficient principal and insufficient funds including charges: rejection with no AFT posting. Decide separately whether local pre-validation failures need a persisted Rejected record. |
| AC3 | Verify original entries and principal/fee reversal in AFT after HKICL invalid-value-date rejection. Updating HTH status does not perform a funds reversal. |

## 4. Existing cache integration caveat

The earlier cache writer passed fee information to CRM event enrichment using the message ID. In the synchronized code, payment services now write `MtbInfoDto` through `CacheHelperUtils`, while `PaymentCrmEventAdapter.getPaymentFee` still reads the old `OPCODE_MAPPING` cache as a colon-separated string. These paths are not shown to be connected by the current implementation; CRM fee population needs separate follow-up. Caching is not an additional requirement of this 2055 change.

The existing null-IPE-result path can also fail when the real cache helper invokes a callback that dereferences the null result. This delivery preserves the baseline behavior. The new tests check the nonnull-result cache callbacks, not Redis/CRM integration.

## 5. Changed files and tests

| File | Change |
| --- | --- |
| `service/payment/ChatsPaymentService.java` | Select the existing `ERROR_MESSAGE_WITH_FALLBACK` policy. |
| `src/test/java/com/hkbea/hth/service/ChatsPaymentServiceTest.java` | Add result/persistence regression tests and null, empty, space and tab rejection-reason cases; preserve original error precedence and the synchronized fee-cache callback behavior. |
| `docs/BCOH2H-2055-Technical-Design.md` | Document the new baseline, minimum scope, integration boundaries and current verification. |

Production Java paths are relative to `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/`; the test path is relative to the same module. Test error codes prefixed `TEST-` are synthetic fixtures, not newly defined production errors.

## 6. Verification and delivery

Java 21 and offline Maven verification passed all 65 targeted tests on 5 November 2026, using a fresh export of `a10fb5d` with only this delivery's two Java files overlaid. No dependency or project POM changes were required.

| Test suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ChatsPaymentServiceTest | 36 | 0 | 0 | 0 |
| PaymentCommonRoutingBuilderTest | 5 | 0 | 0 | 0 |
| PaymentRoutingSubmitterTest | 9 | 0 | 0 | 0 |
| PaymentServiceImplTest (enquiry methods only) | 15 | 0 | 0 | 0 |

The test selection was `ChatsPaymentServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,PaymentServiceImplTest#enquire_*`, with test failures enforced. Full regression, Redis/CRM integration, SIT/UAT and host posting/reversal were not run.

Deliver only the two changed Java files under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2055/source/`, preserving repository-relative paths. Put this design and `DELIVERY.txt` at the package root. Apply this package to baseline `a10fb5d` or its compatible successor; do not overlay older 2055/2065 source files onto the synchronized code.

No database migration is required or executed. No entity, shared-support or enquiry restoration file is supplied. If an earlier nullable IPE_TERMINAL column exists, this revision does not require dropping it. Commit the source changes locally; leave the mcdp repository uncommitted for manual submission.
