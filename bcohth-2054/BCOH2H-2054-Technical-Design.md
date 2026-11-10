# BCOH2H-2054 — Technical Design

## Scope and delivery decision

TT payments must share the applicable BCO/EBK parent limits and cross-border sub-limits. EBK ownership and implementation were reported by the team. Based on the current DSP call chain, no additional HTH production change has been identified. HTH will retain its existing TT submission flow without adding another limit deduction or reversal.

**This is a documentation-only DSP delivery. It does not certify end-to-end completion of BCOH2H-2054.** DSP code inspection establishes the existing hand-off and the intended ownership; it cannot verify EBK's internal calculation, consumption, or reversal. Story acceptance requires the integration evidence listed below.

Baseline reviewed: `a97bbb2`, 11 November 2026. Requirement source: `jira/[#BCOH2H-2054] TT - Payment Channel Limit.pdf`.

## Existing HTH flow

1. `PaymentServiceImpl.processLocalPayment` validates the request, resolves the caller and debtor account, and invokes IPE smart routing.
2. Once the routed payment type is known, the common `validateLimit` method performs its FPS limit operation only for FPS. TT does not enter that branch.
3. `TtPaymentService`, through `OverseasPaymentService`, validates the TT request and debtor balance, enriches the accepted routing request, invokes the existing WMG hook, and submits the request to IPE.
4. Existing submission handling maps the IPE result to the HTH payment response and updates the payment record.

Code evidence:

| Code | Relevant behaviour |
|---|---|
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/PaymentServiceImpl.java` | Routing precedes the FPS-only common limit branch; TT dispatches to `TtPaymentService`. |
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/OverseasPaymentService.java` | Documents EBK ownership and contains no TT limit update/reversal call. Sends `partyId` in `hdrCibExtAcNo` and its existing conversion in `hdrCibIntAcNo`. |
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/TtPaymentService.java` | Supplies TT payment details and the existing registered/ad-hoc beneficiary product hint (`CB97` / `0000`). |
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/PaymentRoutingSubmitter.java` | Invokes the existing IPE route and submit APIs. |
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/PaymentResponseSupport.java` | Maps business `FAILED` to `Rejected`; TT uses the existing rejection-message fallback policy. |

The registered/ad-hoc hint must not be assumed to equal the EBK pre-designated/non-designated limit classification. That classification and the actual IPE/WMG-to-EBK execution path require downstream confirmation.

## Responsibility boundary

| Component | Responsibility |
|---|---|
| HTH | Existing request validation, customer/account/payment data, routing/submission, and payment result presentation. |
| EBK and downstream payment flow | Authoritative beneficiary limit classification, shared BCO/H2H quota records, all applicable parent/sub-limit checks and consumption, HKD-equivalent calculation, and applicable release/reversal. Exact invocation and sequencing must be verified in SIT. |
| APIC/HTH consumers | Use the existing payment API and error contract. No new consumer field is introduced for this story. |

No HTH limit ledger, cache, table, migration, API or error code is added. Existing FX and On-Us limit operations are outside this change. HTH will not perform speculative reversals on a timeout or on a status enquiry.

## Acceptance and integration evidence

| AC | Scenario | Evidence required | Current evidence |
|---|---|---|---|
| 1 | Pre-designated HKD TT successfully submitted | Correct applicable parent and cross-border sub-limit consumption; remaining limits agree in BCO and H2H. | Not verified against EBK. |
| 2 | Non-designated USD TT successfully submitted | Correct HKD-equivalent amount and applicable parent/sub-limit consumption; both channels share the same records. | Not verified against EBK. |
| 3 | Pre-designated HKD TT with insufficient limit | Negative HTH result; no cross-border sub-limit consumption. | Not verified against EBK. |
| 4 | Non-designated USD TT with insufficient HKD-equivalent limit | Negative HTH result; no cross-border sub-limit consumption. | Not verified against EBK. |
| 5 | Pre-designated USD TT with sufficient limit but insufficient balance | Rejection and no net cross-border sub-limit consumption. If reserved earlier, downstream restoration must be demonstrated. | HTH has a balance check; EBK timing/restoration is not verified. |
| 6 | Non-designated HKD TT receives SWIFT NACK | Applicable limit restoration/no net consumption, with an agreed asynchronous trigger and owner. | Nice To Have; scope and ownership remain open. |

For AC1/2, capture before/after limits for the same BCO profile and confirm both directions of channel sharing. Include a parent-sufficient/sub-limit-insufficient case to establish that all applicable levels are checked. Record the USD conversion rate source and rounding rule supplied by EBK; do not infer them from DSP's stored amount fields.

For AC3/4/5, capture HTH response, downstream result and the corresponding EBK limit record. Unit tests with mocked IPE responses cannot establish these quota outcomes.

## Closure conditions

- EBK/IPE owners confirm that the HTH TT path actually invokes the delivered quota functionality, including the customer identifiers and beneficiary classification used.
- SIT demonstrates AC1–AC5 with shared BCO/H2H records and confirms which downstream stage constitutes successful submission for quota consumption.
- The business explicitly includes or defers AC6.
- Any observed HTH parameter or response mismatch is assessed as a targeted follow-up; this document does not assert that no future DSP change can be needed.

## Changed files and validation

Only this Technical Design is added for 2054. No production or unit-test code is changed under this story. Validation in this delivery is static DSP call-chain inspection; no EBK, IPE, WMG, SWIFT, SIT or UAT execution has been performed.
