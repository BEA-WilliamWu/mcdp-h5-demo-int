# BCOH2H-2065 — HTH CHATS Payment Beneficiary Bank Verification

Revision: 17 November 2026. Implementation baseline: `3720297`.

## 1. Scope and authoritative inputs

Correct the confirmed CHATS instruction mapping and validation defects on the latest DSP baseline. Preserve the existing payment flow, error codes, account mappings, fees and database schema. The recent delegation of balance sufficiency checks to IPE remains in place.

Sources in `jira/BCOHTH-2065/`:

- `10 - HTH Local & Overseas Payment Specification for HTH API (v0.8)-v42-20261117_060515.docx`: API field definitions, especially Table 5 rows 5, 22 and 24–27.
- `hth-ipe 参数长度映射_API_RZ.xlsx`: IPE field limits, especially Sheet1 rows 4–5, 37, 40–47 and 91–94.
- `[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`: story acceptance criteria.

Confirmed decisions on 17 November:

1. CHATS `paymentIdentification.endToEndId` must carry the API caller's value instead of HTH's generated internal number.
2. Temporarily reject CHATS creditor names longer than 35 characters in HTH. This is an explicitly agreed compatibility restriction, **not** the v0.8 requirement: v0.8 still allows 140 characters.

This delivery is limited to CHATS and the necessary shared routing/query handling. It does not implement every difference between the latest v0.8 document and existing FPS, TT, DR or On-Us behavior.

## 2. End-to-End ID and transaction reference

The old mapping used the generated batch ID for both `groupHeader.messageId` and `transactions[0].paymentIdentification.endToEndId`. The latter also supplied HTH `txnRef` and database `PAYMENT_ID`. Changing only that assignment would make an optional or reusable customer reference the payment lookup key.

The corrected mapping separates the identifiers:

| Field | CHATS value after this change |
| --- | --- |
| IPE `groupHeader.messageId` | Existing generated HTH batch ID |
| IPE `transactions[0].paymentIdentification.endToEndId` | Customer API value unchanged; missing, null, empty or whitespace-only value becomes `NOTPROVIDED` |
| HTH payment response `txnRef` | Generated HTH batch ID, independent of the customer End-to-End ID |
| HTH payment response `originalEndtoendID` | Original customer input, preserving existing echo behavior; absent input remains absent/null |
| `HTH_PAYMENT_PAYOUT.PAYMENT_ID` | Generated HTH batch ID, used by the existing `txnRef` enquiry |
| `HTH_PAYMENT_PAYOUT.END_TO_END_ID` | Generated HTH batch ID, preserving the existing column meaning |
| `HTH_PAYMENT_PAYOUT.CUSTOMER_TRANSACTION_ID` | Existing API `transactionID`, unchanged |

The customer ID is optional and limited to 35 characters. Supplied nonblank values are neither trimmed nor reformatted. This change does not introduce a customer End-to-End ID uniqueness check or a new persistence column. The existing `transactionID` duplicate check remains separate.

For explicit CHATS requests, the caller ID is present in the initial IPE routing request. After route selection, the mapping is reapplied using the selected type:

- Automatic routing to CHATS uses the customer value/default before submission.
- A request initially marked CHATS but routed to another type resumes that type's existing generated-ID mapping.
- Other payment types retain their current identifier behavior. The v0.8 FPS/TT passthrough requirement remains outside this CHATS correction.

### Enquiry dependency

`GET /payment/v1/transactions/enquiry` still looks up the HTH record by `PAYMENT_ID` and caller `PARTY_ID`. For eligible unfinished CHATS records, HTH now calls the existing IPE result API with `batchId = PAYMENT_ID`, leaving `transactionId` unset. IPE already supports batch queries and HTH submits exactly one transaction per batch.

This works for earlier CHATS records, where the batch and transaction identifiers were identical, and for new CHATS records, where the customer ID differs. An empty, failed, null or multi-transaction batch result is inconclusive and leaves the stored status unchanged. Other payment types retain their transaction-ID enquiry. Refresh eligibility and status mappings are unchanged.

### IPE deployment dependency

The supplied workbook still defines IPE `endToEndId` as 23–26 characters and associates it with `transactionID`; its comment awaits PO confirmation. The checked-in IPE Swagger has the same old length rule. The user's explicit passthrough instruction takes precedence for this implementation, but it does not prove that the deployed IPE validator has changed.

IPE must accept the agreed customer ID range and `NOTPROVIDED`, and confirm propagation to pacs.008 `PmtId/EndToEndId`. Changing HTH cannot remove IPE's deployed validation. The external IPE Swagger is retained as supplied rather than silently rewritten. `instructionId`, which maps to `PmtId/InstrId`, is not repurposed.

## 3. Temporary creditor-name restriction and address validation

The supplied sources allow 140 characters for the canonical creditor name and IPE `creditor.name` / `creditorAccount.accountName`, but only 35 for each legacy `depositAccountName1..4`. The current mapping copies the full name into `depositAccountName1`; SIT supplied a downstream rejection for a 140-character string exceeding 35.

Until IPE defines a lossless mapping, the agreed temporary behavior is:

- Reject a CHATS creditor name longer than 35 characters with existing error `500115` (`BENEFICIARY_ACCOUNT_NAME_INVALID`).
- Validate before IPE routing for an explicit CHATS request, and before submission when automatic routing selects CHATS.
- Preserve accepted names unchanged in all existing name fields. Do not truncate, split or omit them.
- Retain `depositAccountName2 = "6304357992232185"` as previously requested.
- Do not impose this 35-character restriction on other payment types. Their existing rules remain in effect.

**Deviation from v0.8:** names of 36–140 characters are valid under the API document but are temporarily rejected for CHATS. This restriction must be revisited when IPE confirms the full-length mapping.

The CHATS address remains optional. Apply the API's bounds as well as IPE's limits, rather than widening API fields to IPE's maximum:

| Field | Accepted CHATS input | Existing error |
| --- | --- | --- |
| `addressLine1` | Optional; maximum 70; existing SWIFT-style character set for nonblank values | `500141` |
| `addressLine2` | Optional; maximum 70, including all-space input; existing character-set rule | `500144` |
| `townName` | Optional; maximum **35** and the same character set, per v0.8; replaces the earlier IPE-only maximum of 70 | `500142` |
| `country` | Optional; when supplied, exactly two uppercase letters; no country-directory lookup added | `500143` |
| Trusted debtor name | Existing nonblank/maximum-140 check | `500000` with `debtor.name` prefix |

Optional empty address lines/town remain accepted. An absent address object remains safe during payload construction and payout insertion. No new mandatory CHATS address requirement is introduced.

## 4. Code changes

| File | Change |
| --- | --- |
| `PaymentModelConvert.java` | Separate CHATS customer End-to-End ID from the batch ID; apply the confirmed default; reapply identifiers after routing while retaining other channels' behavior. |
| `ChatsPaymentService.java` | Reapply CHATS customer ID before submission; keep `txnRef`/`PAYMENT_ID` tied to the generated batch; handle a missing identification object in the response echo. |
| `PaymentValidationSupport.java` | Add the temporary CHATS name limit; apply v0.8 town limit and supplied-address character checks; validate customer ID on the CHATS service path. |
| `PaymentServiceImpl.java` | Check explicit CHATS name length before routing; reapply identifiers after selection; use the existing IPE batch query for CHATS while retaining other enquiry paths. |
| Payment unit tests | Verify caller ID/default, distinct HTH reference and persistence, routing transitions, name/address boundaries and old/new CHATS enquiries. |

No database migration, public JSON field addition/renaming or generated-model change is required. Conditional behavior is implemented in service code; shared API types and the supplied external IPE contract are not regenerated or modified in this correction. The temporary API-name restriction is documented above.

## 5. Remaining differences and SIT checks

The new source documents also differ from current code in BIC applicability/expansion, member-ID and account limits, ignored-field behavior, field naming (`transactionId` / `instructionDate`), new 400xxx/700xxx errors and some currency rules. These are recorded for separate review and are not silently implemented as part of the two confirmed defects.

Other 2065 integration dependencies remain:

- Identify and implement the authoritative debtor-address source. HTH currently preserves an existing trusted address but does not retrieve one.
- Confirm outbound debtor-account formatting, agent mapping, member-ID resolution and charge-bearer conversion.
- Confirm the purpose/allocation of the legacy name fields and support for names up to the v0.8 maximum of 140.
- Confirm the remittance array/element length definition and downstream message verification/failure reporting.

SIT should capture the original API request, HTH-to-IPE routing/submission payloads and actual pacs.008 XML. Verify a short customer ID, 35-character ID, omitted ID (`NOTPROVIDED`), repeated customer IDs with distinct HTH `txnRef` values, explicit/automatic CHATS routing, 35/36-character names and 35/36-character towns. Verify enquiry after submission uses the generated reference and obtains the correct single-payment batch result. Include an older CHATS record as a compatibility case.

Real IPE acceptance, database integration, pacs.008 generation and transmission are not established by mocked unit tests.

## 6. Verification and delivery

Verified on 17 November 2026 with Java 21 and Maven using the local dependency cache and a temporary reactor. **282 tests passed; 0 failures, 0 errors, 0 skipped.** No project POM or dependency was changed.

| Test suite | Passed |
| --- | ---: |
| `ChatsPaymentServiceTest` | 44 |
| `PaymentServiceImplTest` | 68 |
| `PaymentEnquiryServiceTest` | 14 |
| `PaymentCommonRoutingBuilderTest` | 5 |
| `PaymentRoutingSubmitterTest` | 9 |
| `FpsPaymentServiceTest` | 31 |
| `TtPaymentServiceTest` | 20 |
| `TtPaymentServiceSupportTest` (nested cases) | 6 |
| `DrPaymentServiceTest` | 17 |
| `OnUsPaymentServiceTest` | 54 |
| `OverseasPaymentServiceTest` | 14 |

The three modified suites add 35 test executions: 17 CHATS service, 16 entry and 2 enquiry. They cover customer ID/defaults, distinct batch/reference persistence, both routing transitions, name/address boundaries, old/new CHATS batch queries and ambiguous/inconclusive results. Route-time assertions run before the shared request object is enriched. Existing balance-delegation tests still pass. External calls and persistence are mocked; no live SIT/UAT was performed.

Reproduction with a temporary reactor containing the parent/common/DSP/support dependencies:

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,PaymentServiceImplTest,PaymentEnquiryServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,FpsPaymentServiceTest,TtPaymentServiceTest,DrPaymentServiceTest,OnUsPaymentServiceTest,OverseasPaymentServiceTest,TtPaymentServiceSupportTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

Commit only this correction and its tests/design to local DSP Git. Sync the changed Java files under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2065/source/`, preserving repository-relative paths, and place this design at the story root. Source documents and unrelated working-tree changes are not included in the code commit.
