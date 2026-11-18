# BCOH2H-2065 — HTH CHATS Payment Beneficiary Bank Verification

Revision: 18 November 2026. Latest shared-enquiry follow-up baseline: `7756632`. Retains the CHATS customer-reference, name-segmentation and debtor-address corrections.

## Shared enquiry alignment with BCOH2H-2126

The current shared PaymentServiceImpl and PaymentEnquiryServiceTest also include the approved 2126 date alignment: for FPS, CHATS, TT, DR and On-Us, enquiry executionDate reads the existing EXECUTION_DATE populated from request settlementDate. This represents the customer's requested date, at the start of that day in the existing enquiry timezone. Missing/invalid dates remain absent; PAYMENT_DATE is not a fallback. Submission/database writes, initiatedDate/asOf, account/limit validation and the 2065 CHATS mappings are retained.

Both mcdp story packages carry byte-identical current copies of these two shared files. Details of the date contract are in the updated BCOH2H-2126 Technical Design. No database migration or API-model generation is introduced.

## 1. Scope and authoritative inputs

Correct the confirmed CHATS instruction mapping and validation defects on the latest DSP baseline. Preserve the existing payment flow, error codes, account mappings, fees and database schema. The recent delegation of balance sufficiency checks to IPE remains in place.

Sources in `jira/BCOHTH-2065/`:

- `10 - HTH Local & Overseas Payment Specification for HTH API (v0.8)-v42-20261117_060515.docx`: API field definitions, especially Table 5 rows 5, 22 and 24–27.
- `hth-ipe 参数长度映射_API_RZ.xlsx`: IPE field limits, especially Sheet1 rows 4–5, 37, 40–47 and 91–94.
- `[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`: story acceptance criteria.

Confirmed decisions, updated on 18 November:

1. CHATS `paymentIdentification.endToEndId` carries the API caller's value instead of HTH's generated internal number; the earlier reference separation remains in place.
2. Restore the 140-character CHATS creditor-name limit. Split the name sequentially into `depositAccountName1..4`, at most 35 characters per field. For a name of 35 characters or fewer, populate both Name1 and Name2 with the full name. This replaces the earlier temporary 35-character restriction and fixed Name2 value.
3. Retrieve the debtor address from BCO `DIGX_PI_PARTY_PREFERENCES.ADDRESS1..5` using the payment account owner. Preserve its text in up to four IPE address lines of 70 characters each; leave town/country unset and permit missing addresses.

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

`GET /payment/v1/transaction-inquiry` still looks up the HTH record by `PAYMENT_ID` and caller `PARTY_ID`. For eligible unfinished CHATS records, HTH now calls the existing IPE result API with `batchId = PAYMENT_ID`, leaving `transactionId` unset. IPE already supports batch queries and HTH submits exactly one transaction per batch.

This works for earlier CHATS records, where the batch and transaction identifiers were identical, and for new CHATS records, where the customer ID differs. An empty, failed, null or multi-transaction batch result is inconclusive and leaves the stored status unchanged. Other payment types retain their transaction-ID enquiry. Refresh eligibility and status mappings are unchanged.

### IPE deployment dependency

The supplied workbook still defines IPE `endToEndId` as 23–26 characters and associates it with `transactionID`; its comment awaits PO confirmation. The checked-in IPE Swagger has the same old length rule. The user's explicit passthrough instruction takes precedence for this implementation, but it does not prove that the deployed IPE validator has changed.

IPE must accept the agreed customer ID range and `NOTPROVIDED`, and confirm propagation to pacs.008 `PmtId/EndToEndId`. Changing HTH cannot remove IPE's deployed validation. The external IPE Swagger is retained as supplied rather than silently rewritten. `instructionId`, which maps to `PmtId/InstrId`, is not repurposed.

## 3. Creditor-name segmentation and debtor address

### Creditor name

The canonical IPE `creditor.name` and `creditorAccount.accountName` retain the full API name, up to 140 characters. Only the legacy CHATS remittance fields are segmented:

| Name length | Name1 | Name2 | Name3 | Name4 |
| --- | --- | --- | --- | --- |
| 1–35 | Full name | Full name | Unset | Unset |
| 36–70 | Characters 1–35 | Characters 36–70, as present | Unset | Unset |
| 71–105 | Characters 1–35 | Characters 36–70 | Characters 71–105, as present | Unset |
| 106–140 | Characters 1–35 | Characters 36–70 | Characters 71–105 | Characters 106–140, as present |

More than 140 characters is rejected with existing `500115` (`BENEFICIARY_ACCOUNT_NAME_INVALID`). The explicit-CHATS entry check and selected-CHATS service check both use 140. Existing mandatory-name and character-set validation remains in place. Other payment types retain their existing rules.

### Debtor postal address from BCO

`PartyPreferenceMapper.selectDebtorPostalAddress` joins `DIGX_PI_PARTY_ACCOUNTS a` to `DIGX_PI_PARTY_PREFERENCES p` on `a.PARTYID = p.PARTYID` and matching `DETERMINANT_VALUE`. The account rows are scoped by the current HTH `PARENT_PARTY_ID`, normalized external debit account number, CSA account type and resolved product code. This retrieves the account owner's preferences rather than substituting a parent company's address. Identical results from repeated account/currency rows are deduplicated; conflicting results fail instead of choosing an arbitrary address.

The BCO ORM maps `ADDRESS1..5` to the party preferences address lines. Their creation/update provenance was reviewed separately; this implementation reads the stored values and does not refresh them from the host or introduce an EBK call.

Mapping to `transactions[0].debtor.postalAddress`:

- Ignore blank source lines, strip outer padding and join the remaining lines in source order with one space.
- Split the combined text into `addressLine1..4`, each no longer than 70 Unicode characters, preserving the fifth source line's text.
- Leave `townName` and `country` unset because these are not independent fields in this table. Do not infer them from address text or reuse creditor values.
- If no preferences/address is found, omit the postal-address object and continue. Do not fall back to another company's preferences.
- If the combined address exceeds 280 characters, reject with existing `500000` (`PARAMETER_INVALID_EXCEPTION`) prefixed by `debtor.postalAddress`, rather than truncate it. Database/query errors stop submission.

No client-supplied debtor-address fields, schema migration or generated IPE model changes are needed.

The CHATS creditor address remains optional. Apply the API's bounds as well as IPE's limits, rather than widening API fields to IPE's maximum:

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
| `ChatsPaymentService.java` | Retain customer-ID/reference handling; split the creditor name into four legacy fields and populate debtor postal address from BCO. |
| `PaymentValidationSupport.java` | Restore the CHATS name maximum to 140; retain v0.8 town, address-character and customer-ID checks. |
| `PaymentServiceImpl.java` | Retain CHATS validation, routing identifiers and batch enquiry; share the confirmed five-type requested-execution-date mapping with 2126. |
| `PartyPreferenceMapper.java` / `.xml` | Query all five account-owner address lines using the account/parent/product/determinant relationship. |
| `PartyPostalAddress.java` | Internal five-line query-result DTO; no API contract change. |
| Payment unit tests | Verify name segmentation/duplication and boundaries, debtor-address packing/missing/error cases, as well as existing reference, routing, enquiry and payment validation behavior. |

No database migration, public JSON field addition/renaming or generated-model change is required. Conditional behavior is implemented in service code; shared API types and the supplied external IPE contract are not regenerated or modified in this correction. The earlier temporary 35-character API-name restriction is removed by this revision.

## 5. Remaining differences and SIT checks

The new source documents also differ from current code in BIC applicability/expansion, member-ID and account limits, ignored-field behavior, field naming (`transactionId` / `instructionDate`), new 400xxx/700xxx errors and some currency rules. These are recorded for separate review and are not silently implemented as part of the two confirmed defects.

Other 2065 integration dependencies remain:

- Validate real BCO account-owner/preference data and address freshness in SIT, including linked companies with no preferences row; the implementation now reads the stored BCO address.
- Confirm outbound debtor-account formatting, agent mapping, member-ID resolution and charge-bearer conversion.
- Confirm deployed IPE accepts the agreed four-part legacy-name mapping, including duplicated Name1/Name2 for short names, and propagates debtor address into pacs.008.
- Confirm the remittance array/element length definition and downstream message verification/failure reporting.

SIT should capture the original API request, HTH-to-IPE routing/submission payloads and actual pacs.008 XML. Verify a short customer ID, 35-character ID, omitted ID (`NOTPROVIDED`), repeated customer IDs with distinct HTH `txnRef` values, explicit/automatic CHATS routing, 35/36-character names and 35/36-character towns. Verify enquiry after submission uses the generated reference and obtains the correct single-payment batch result. Include an older CHATS record as a compatibility case.

SIT should additionally submit creditor names of 35, 36, 70, 71, 105, 106 and 140 characters and inspect all four outbound fields. Compare debtor address with the payment account owner's BCO preferences and inspect the raw pacs.008 `Dbtr/PstlAdr`. Cover missing preferences and all-blank addresses.

Real IPE acceptance, database integration, pacs.008 generation and transmission are not established by mocked unit tests.

## 6. Verification and delivery

Verified on 18 November 2026 with Java 21 and Maven using the local dependency cache and a temporary reactor. **363 tests passed; 0 failures, 0 errors, 0 skipped (350 Support and 13 BFF).** No project POM or dependency was changed.

| Test suite | Passed |
| --- | ---: |
| `ChatsPaymentServiceTest` | 55 |
| `PaymentServiceImplTest` | 59 |
| `PaymentEnquiryServiceTest` | 69 |
| `PaymentCommonRoutingBuilderTest` | 5 |
| `PaymentRoutingSubmitterTest` | 9 |
| `FpsPaymentServiceTest` | 31 |
| `TtPaymentServiceTest` | 20 |
| `DrPaymentServiceTest` | 17 |
| `OnUsPaymentServiceTest` | 54 |
| `OverseasPaymentServiceTest` | 14 |
| `AccountServiceImplGenericEnquiryTest` | 17 |
| BFF `PaymentServiceImplCoverageTest` | 13 |

The new tests cover creditor-name segment boundaries and short-name duplication, full canonical names, all five BCO address lines, packing at 70/71/280 characters, missing/blank lines, overflow and query failure. Existing balance-delegation and other payment tests still pass. MyBatis XML parsing and its DTO/parameter bindings were checked; the actual query was also exercised against in-memory SQLite fixtures for account-owner, parent, determinant, product and account-type isolation and duplicate rows. These fixture checks do not replace Oracle/SIT integration. External calls and persistence are mocked; no live SIT/UAT was performed.

Current clean-build test log: `/tmp/hth-2126-all-execution-dates-clean.log`. The earlier BCO mapper parsing/SQL fixture checks remain applicable to the unchanged mapper. The current run also covers all five requested-date mappings and initiatedDate/asOf preservation.

Reproduction with a temporary reactor containing the parent/common/DSP/support dependencies:

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,PaymentServiceImplTest,PaymentEnquiryServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,FpsPaymentServiceTest,TtPaymentServiceTest,DrPaymentServiceTest,OnUsPaymentServiceTest,OverseasPaymentServiceTest,AccountServiceImplGenericEnquiryTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

Commit only this correction and its tests/design to local DSP Git. Sync the changed Java/mapper XML files and refresh existing story-delivery sources from the same current commit under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2065/source/`, preserving repository-relative paths, and place this design at the story root. Source documents and unrelated working-tree changes are not included in the code commit.
