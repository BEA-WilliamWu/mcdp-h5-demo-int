# BCOH2H-2055 — HTH CHATS Payment Account Posting

Revision: 17 November 2026 — all-payment-type extension. Baseline: `ded39e6` (CHATS change), based on synchronized DSP commit `c99804c`.

## 1. Scope and confirmed responsibility

IPE owns the debtor-account funds-availability check for **CHATS, FPS, On-Us, TT and DR**, as explicitly confirmed for this extension. HTH must not query the debtor's balance or compare it with the payment amount before submission. This supersedes the earlier design that retained the local principal-balance pre-check.

CHATS was updated in `ded39e6`; this revision applies the same rule to FPS, On-Us, TT and DR and removes the now-unused shared payment balance-check helpers. Retain all other validation, routing, request mapping, persistence and response handling. No database migration, API field, error code, cache or direct host-posting call is introduced. The standalone account-balance enquiry API remains available and unchanged.

For CHATS, TT and DR, the existing rejection-reason fallback remains unchanged: `errorMessage -> statusReason -> existing generic rejection message`. FPS and On-Us retain their existing error-message-only policy. Preserve the IPE error code in every payment response.

## 2. Processing

```text
Existing common validation and IPE route selection
  -> existing payment-type account / amount / currency / other checks
  -> existing Scameter / WMG / limit handling where applicable and enrichment
  -> insert initial HTH_PAYMENT_PAYOUT or HTH_PAYMENT_INTERNAL record
  -> submit to IPE /credit-transfer (IPE checks funds and handles posting)
  -> map the IPE result, update the payment record and return the response
```

- Remove the remaining FPS, On-Us, TT and DR calls to `validateSufficientBalance`. Remove unused local wrappers, `hasEnoughBalance` helpers and the HTH `AccountApi` dependency from payment services. Keep the separate BG3 `AccountApi` dependency used for account existence/status checks.
- Retain debtor-account existence/status checks, account authorization, payment limits, amount validity, currency consistency, service-window and beneficiary checks. Removing the balance check does not bypass these controls.
- A valid request for any of these five payment types proceeds to IPE without HTH calling `enquireGenericBalance`. This path no longer raises local error `500181` on the basis of the debtor balance.
- When IPE returns a valid `FAILED` payment result for insufficient funds, HTH returns `Rejected` with the existing IPE error code/reason mapping. The existing payment-record update saves `STATUS=Rejected`, `IPE_STATUS=FAILED` and the rejection reason.
- Retain `SUCCESS -> Success` and in-flight results -> `Processing`. Transport errors, malformed results, cache processing and database-failure handling retain their existing behavior; this revision does not invent an insufficient-funds outcome when IPE provides none.
- The initial payment row still has a null status until the submission result is applied. A successfully persisted IPE rejection can be returned by the existing enquiry API. Persistence remains subject to the baseline's database-error handling.

HTH does not reserve or debit funds. A local balance snapshot is no longer used as a gate; the actual funds decision is delegated to IPE.

## 3. Code changes

Paths are relative to `hth-ms-support/hth-ms-support-svc`.

| File | Change |
| --- | --- |
| `src/main/java/com/hkbea/hth/service/payment/FpsPaymentService.java` | Remove the balance pre-check, wrapper and unused HTH `AccountApi` dependency. |
| `src/main/java/com/hkbea/hth/service/payment/OnUsPaymentService.java` | Remove the pre-check, wrappers and unused HTH `AccountApi` dependency; retain BG3 account queries and transfer limits. |
| `src/main/java/com/hkbea/hth/service/payment/TtPaymentService.java` | Remove the balance pre-check; retain existing TT validation and WMG handling. |
| `src/main/java/com/hkbea/hth/service/payment/DrPaymentService.java` | Remove the balance pre-check; retain existing DR validation. |
| `src/main/java/com/hkbea/hth/service/payment/OverseasPaymentService.java` | Remove balance wrappers and the unused HTH `AccountApi` dependency; retain BG3 account-status validation. |
| `src/main/java/com/hkbea/hth/service/payment/PaymentServiceSupport.java` | Remove the unused balance-query/comparison helpers and their imports. Other shared checks and persistence helpers are unchanged. |
| `src/test/java/com/hkbea/hth/service/{Fps,OnUs,Tt,Dr}PaymentServiceTest.java` | Replace local-insufficient-balance expectations with no-balance-query submission and IPE rejection/persistence coverage; retain other validations and limit behavior. |
| `src/test/java/com/hkbea/hth/service/OverseasPaymentServiceTest.java` | Remove tests and fixtures for the deleted balance helpers; retain all other shared-validation tests. |
| `docs/BCOH2H-2055-Technical-Design.md` (repository root) | Record the explicitly expanded scope, current behavior and verification. |

The prior CHATS production/test changes are included in the cumulative delivery but are not changed again in this revision. Account-enquiry APIs, entities, OpenAPI definitions, generated models and error-code definitions are unchanged. Existing account/status tests remain active, and the production service-window behavior is unchanged. `500181` remains defined for compatibility, but none of the five payment flows uses it for local balance comparison.

## 4. Integration verification still required

Balance-check ownership is confirmed. The exact deployed IPE error code, message and outcome must be verified in SIT; `TEST-IPE-INSUFFICIENT-FUNDS` in unit tests is a synthetic fixture, not a new production error code.

| Requirement | SIT / host evidence |
| --- | --- |
| AC1: successful posting | Confirm IPE SUCCESS corresponds to successful EBK posting and CHATS submission; verify principal withdrawal and Bank Charge entries in AFT. |
| AC2: insufficient funds | Test insufficient principal and insufficient funds including charges. Verify IPE rejects the payment, HTH returns/stores the rejection, and there is no AFT posting. Confirm the actual IPE error contract. |
| Operating keys / channel / teller | Verify withdrawal `H43`, Bank Charge `183`, channel `CCB INTERNET` and teller `CBSIPE1`. IPE `channel=HTH` is not itself the host posting-channel field. |
| No FX | Verify rate 1 and equal account/transaction principal amounts in EBK/AFT. Existing HTH same-currency controls remain. |
| References and charges | Verify the BCO-compatible passbook/statement references and fee calculation. Do not assume `overChargeAmount` equals the total Bank Charge. |
| AC3: reversal | Verify original and principal/fee reversal entries in AFT after HKICL invalid-value-date rejection. Updating an HTH status does not reverse funds. |
| Finality | A later rejection after saved Success is not refreshed by the existing enquiry path. Agree any additional transition with the IPE status contract separately. |

Existing CHATS field-mapping dependencies documented under 2065 remain outside this change. This delivery does not establish completion of all posting or reversal acceptance criteria.

## 5. Verification

The baseline `ded39e6` passed 195 targeted tests before this extension. The revised build succeeded with **194 tests passed, 0 failures, 0 errors and 0 skipped tests**, using Java 21 and offline Maven with a temporary reactor. No project POM or dependency version was changed.

| Suite | Tests passed |
| --- | ---: |
| `ChatsPaymentServiceTest` | 27 |
| `FpsPaymentServiceTest` | 31 |
| `OnUsPaymentServiceTest` | 54 |
| `TtPaymentServiceTest` | 20 |
| `DrPaymentServiceTest` | 17 |
| `OverseasPaymentServiceTest` | 14 |
| `PaymentCommonRoutingBuilderTest` | 5 |
| `PaymentRoutingSubmitterTest` | 9 |
| `AccountServiceImplGenericEnquiryTest` | 17 |

Four obsolete per-type local-balance rejection cases are replaced by eight tests. Five direct tests of the removed balance helpers are deleted. This explains the total changing from 195 to 194 without skipping any tests.

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,FpsPaymentServiceTest,OnUsPaymentServiceTest,TtPaymentServiceTest,DrPaymentServiceTest,OverseasPaymentServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,AccountServiceImplGenericEnquiryTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

New cases verify:

- Each remaining payment type reaches IPE even when the mocked local balance API would fail if called; the balance API receives no calls.
- IPE insufficient-funds failures return `Rejected`, preserve the IPE error code/message and update the correct payment table after submission.
- Existing FPS limit-release and On-Us limit-reversal handling still runs after IPE rejection. These are limit operations, not funds reversals.
- TT/DR still execute BG3 account-info checks. Existing suites continue covering account existence/status, currencies, amounts, beneficiary rules and other validation failures.

The CHATS regression suite and independent generic balance-enquiry suite also pass. A source check finds no local balance-query/comparison calls remaining in the payment-service package. Downstream services and database mappers are mocked; actual IPE balance enforcement, database integration, SIT/UAT and host posting/reversal are not executed locally. Repeat insufficient-funds SIT for all five payment types using the actual deployed IPE contract.

## 6. Delivery

Commit the changed code, tests and this design to local DSP Git. Deliver the cumulative CHATS plus FPS/On-Us/TT/DR revision, including the changed shared helpers and tests, under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2055/source/`, preserving repository-relative paths. Place this design and delivery metadata at the story directory root. Leave the mcdp repository uncommitted for manual submission.

These are complete files from the stated synchronized baseline plus this correction. Do not overlay older isolated 2055/2065 source copies onto them. Historical test counts from previous deliveries do not describe this revision.
