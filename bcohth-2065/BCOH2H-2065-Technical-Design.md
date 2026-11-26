# BCOH2H-2065 — HTH CHATS Payment Beneficiary Bank Verification

Revised 26 November 2026 on DSP sync `8cf58a0`. Section 3 covers the beneficiary mapping for CHATS, TT, DR, FPS and On-us, including the outstanding IPE decisions.

## Shared enquiry alignment with BCOH2H-2126

The current shared PaymentServiceImpl and PaymentEnquiryServiceTest also include the approved 2126 date alignment: for FPS, CHATS, TT, DR and On-Us, enquiry executionDate reads the existing EXECUTION_DATE populated from request settlementDate. This represents the customer's requested date, at the start of that day in the existing enquiry timezone. Missing/invalid dates remain absent; PAYMENT_DATE is not a fallback. Submission/database writes, initiatedDate/asOf, account/limit validation and the 2065 CHATS mappings are retained.

The earlier delivery aligned these two shared files across the 2065 and 2126 packages. The 2065 delivery is refreshed from the current DSP snapshot. Details of the date contract are in the BCOH2H-2126 Technical Design.

## 1. Scope and authoritative inputs

Correct the confirmed CHATS instruction mapping and validation defects on the latest DSP baseline. Preserve the existing payment flow, error codes, account mappings, fees and database schema. The recent delegation of balance sufficiency checks to IPE remains in place.

Sources in `jira/BCOHTH-2065/`:

- `10 - HTH Local & Overseas Payment Specification for HTH API (v0.8)-v42-20261117_060515.docx`: API field definitions, especially Table 5 rows 5, 22 and 24–27.
- `hth-ipe 参数长度映射_API_RZ.xlsx`: IPE field limits, especially Sheet1 rows 4–5, 37, 40–47 and 91–101.
- `[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`: story acceptance criteria.

Confirmed decisions through 26 November

1. CHATS `paymentIdentification.endToEndId` carries the API caller's value instead of HTH's generated internal number; the earlier reference separation remains in place.
2. Accept the full CHATS creditor name up to 140 characters. For the four EBK deposit fields sent through IPE, use one beneficiary name followed by three address slots, as specified in section 3.
3. Retrieve the CHATS debtor address from PE3 PEECHJ7A using the debit account key. The user confirmed taking the first returned contact and omitting the address when a successful response contains no address. This supersedes the BCO party-preferences address lookup.

The 26 November revision supersedes the 20 November cross-channel name segmentation. Only CHATS supplies the four deposit name/address fields.

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

The customer End-to-End ID is optional. On the 25 November sync baseline (`9c25499`), a nonblank CHATS value must contain 1 to 35 ASCII letters, digits or hyphens. HTH passes an accepted value through unchanged. This change does not introduce a customer End-to-End ID uniqueness check or a new persistence column. The existing `transactionID` duplicate check remains separate.

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

## 3. Creditor details and debtor address

### Creditor name

CHATS preserves the full API name in `creditor.name` and `creditorAccount.accountName`, and the supplied address in `creditor.postalAddress`. The BCO CHATS assembler (`CZChatsTfrInitAssembler`) maps the EBK deposit fields to a beneficiary name followed by up to three address lines. HTH sends the following fields under `supplementaryData.chatsRemittance` to IPE.

| IPE field | CHATS source |
| --- | --- |
| `depositAccountName1` | API `creditor.creditorName` |
| `depositAccountName2` | API `creditor.postalAddress.addressLine1` |
| `depositAccountName3` | API `creditor.postalAddress.addressLine2` |
| `depositAccountName4` | Blank. The HTH API accepts two address lines. |

Each deposit field follows the BCO fixed-width rule. Trim leading and trailing whitespace, collapse internal whitespace to one space, take the first 35 characters and right-pad with spaces to 35. A missing line occupies its original slot as 35 spaces. Length limits on the full name and structured address lines remain 140 and 70 respectively.

### Other payment types

| Payment | BCO outbound rule | HTH submission to IPE |
| --- | --- | --- |
| TT | `CZTelegraphicTfrInitAssembler` writes the name and address lines 1–3 to `BenefNameAddr1–4`, each 35 characters. Slot 2 takes the raw first address line. With a blank second line, the remainder after character 35 of line 1 goes into slot 3 after whitespace normalization. An explicit second line takes precedence. Country travels separately. | Send the caller's full name, address lines and country in the existing structured fields. Keep the complete first line available to IPE. The Smart Routing TT block exposes no four beneficiary slots. |
| DR | `CZChinaTfrInitAssembler` reads the English China account name from `ACCT_NAME_ENG`, fits it to 34 characters and sends 62 spaces for the Chinese name. | Keep the existing HTH English-name source, `creditor.creditorName`. In `supplementaryData.directRemittance`, truncate or right-pad `chinaAccEnglishName` to 34 and send 62 spaces in `chinaAccChineseName`. The full name remains available in the canonical payment fields. |
| FPS | `CZI006Assembler` prefers the I005 customer name and falls back to the beneficiary name. For an account-number payee it combines that name with up to three address lines into I006 `Cdtr/Nm`, using up to 35 characters per component. For proxy payees it sends the name alone. | The current HTH flow accepts account-number payees. Pass the caller's name and available postal address separately as defined by the IPE input contract. A missing address omits the structured address from the submission. HTH does not receive an identified I005 customer-name result in this flow. |
| On-us | `CZFundsTfrInitAssembler` identifies the credit account in TFR01. `CZTFR02Assembler` forwards the account holder name from the TFR01 reply when confirming the transfer. | Send the credit account and existing caller-supplied name. The BG3 long English account name is used for name matching. Its equivalence to the TFR01 confirmation name has not been established. |

TT and DR retain the pre-existing `chatsRemittance.pinCnt = 0` control value. Their four deposit fields are absent. FPS and On-us omit the legacy block entirely.

### Questions for IPE and SIT

| Payment | Evidence needed |
| --- | --- |
| CHATS | Trace one submitted name and two distinct address lines through IPE and EBK into the raw pacs.008. Confirm the first two `Cdtr/PstlAdr/AdrLine` values and handling of a blank fourth slot. |
| TT | Which IPE inputs feed EBK `BenefNameAddr1–4`, and which system implements the 35-character continuation? Compare a long first address line with line 2 absent, then present. The separate WMG workflow does not establish the IPE mapping. |
| DR | Confirm in an EBK trace that IPE passes the 34/62-character fields to the designated China account fields, including a blank Chinese name. Confirm whether designated payees require a host-sourced English name. |
| FPS | Does IPE obtain I005 `CUST_FULLNM` and construct I006 `Cdtr/Nm` from the structured payee fields? If HTH must supply the composite, identify the IPE target and the source of the resolved name. |
| On-us | Does IPE use the TFR01 response directly in TFR02? If HTH must supply the confirmed holder name, identify both the authoritative host response field and the IPE input. |

On baseline `9c25499`, a CHATS creditor name longer than 140 characters returns `500164` (`CREDITORNAME_LENGTH_EXCEPTION`). The explicit-CHATS entry check and selected-CHATS service check both use 140. Existing mandatory-name and character-set validation remains in place. Other payment types retain their existing rules.

### Debtor postal address from PE3 PEECHJ7A

Contract input: `contracts/SC-PE3_PEECHJ7A_Customer_Contact_Information_Retrieval_for_CHATS_TT_FPS_Sorted_by_Address-1.0.0.yaml` and the matching JSON. The integration copy is `src/main/resources/api/pe3/peechj7a.openapi.yaml`. It preserves the source wire fields, scalar types and lengths, and names/reuses the identical nested request/response schemas. Eighteen Java models were generated with OpenAPI Generator 7.17.0 using the existing PE3 JAX-RS/Jackson conventions.

The On-us customer enquiry (PEECHI11) and the shared debtor address enquiry (PEECHJ7A) share the same `Pe3Client`, environment-specific `pe3.base-url`, APIC credentials and z/OS timeout and error handling. Both use HTTP POST. In SIT, HTH calls the existing `/hkbea/sit-sf/hth/v1z/peechi11.v1` endpoint for the On-us customer check and `/hkbea/sit-sf/hth/v1z/peechj7a.v1` for the address enquiry.

CHATS, TT, DR, FPS and On-us each call `DebtorAddressService.lookup` once with the internal debit account during preparation of the selected payment for IPE submission. TT and DR share the overseas payment implementation. FPS and On-us retrieve the address before reserving the transfer limit. Each flow writes the result to `transactions[0].debtor.postalAddress`.

Request mapping follows the existing PE3 account-key convention. The validated 18-digit internal debit account is split into entity (4), branch (4) and account (10), supplied under `ehj7aInput.ehj7aContractI.ehj7aKeyAccI`. `ehj7aCodEntityI` matches the entity. The common header uses channel `IN`, application `PE`, collection `PE9CHJ7A` (following the existing `PE9CHI11` convention), current operation date and blank optional common-header fields. The subsequently supplied PE9CHJ7A request form confirms collection `PE9CHJ7A` and supports the Alnova account-key query.

Response and IPE mapping:

- Use the PE3 address only when the response is well formed, `ehj7aAnswer.ehj7aCodReturn = 00` and the common-header error field is blank or `0000`.
- Select the first `ehj7aContactO` item in returned order, as confirmed by the user. Read its `ehj7aAddressO.ehj7aExtAdrLin1O..4O`. No primary-address or language filtering is applied.
- Map `ehj7aExtAdrLin1O..4O` directly to IPE `debtor.postalAddress.addressLine1..4`, as requested on 25 November. Strip outer padding and omit blank fields while preserving each line position. If any mapped line exceeds the IPE limit of 70 Unicode characters, log a warning and omit the entire debtor postal address.
- The fifth line `ehj7aExtAdrLin5O` is currently unmapped, pending the user's log review and mapping confirmation.
- Populate `country` according to the trimmed, uppercased `ehj7aTypCntaadrO`. For a two-character contact type ending in `H`, `C` or `I`, read `ehj7aCodCountryAdrO`. For non-structured types `40`, `50`, `60`, `70`, `80`, `90`, `00`, `ZZ`, `Z0`, `Z1`, read `ehj7aCodCtryValO` instead. Trim and uppercase the selected code, require membership in the existing Java ISO alpha-2 country list, and omit/log unrecognized nonblank codes. Do not convert three-letter codes or fall back to the other country field. Unrecognized/missing contact types leave country absent.
- Populate `townName` from `ehj7aDesDistrictO` for contact types ending in `H`; use `ehj7aOvrCtyDesO` for those ending in `C` or `I`. If that value is blank, or the type has no designated town field, use `ehj7aDesCityO`. Strip outer padding; all-blank candidates leave town absent. The user accepted this generic-city fallback as an implementation decision on 25 November.
- Province, area and other district descriptions are not appended to the address lines. All mapped values come from the selected first contact.
- A successful response with no contact/address omits the postal-address object and allows payment to continue. If the first four lines, mapped country and mapped town are all empty, omit the object. Country-only or town-only results retain an address object.
- For all five payment types, a timeout, client exception, malformed reply, host error or address mapping failure logs a WARN with the message ID. HTH omits `debtor.postalAddress` and continues normal payment processing, including limit checks and IPE submission. The IPE response determines the submission outcome.

The BCO `selectDebtorPostalAddress` query and its unused `PartyPostalAddress` DTO are removed. Customer-name and other existing party-preference queries remain in use.

Diagnostics use `messageId` for correlation. INFO records the successful enquiry return code and contact count. WARN identifies the reason for omitting an address after a failed enquiry. DEBUG for `com.hkbea.hth.service.payment.DebtorAddressService` records the PE3 field names used to populate country and townName. An omitted field is logged as `none`.

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
| `DebtorAddressService.java` | Query PEECHJ7A and map the first contact to an IPE debtor postal address for all five payment types. |
| `ChatsPaymentService.java` | Map the CHATS beneficiary name and address into the four EBK deposit fields. Apply the shared debtor address lookup and CHATS remittance flags. |
| `PaymentRoutingPayload.java` | Build the four CHATS deposit fields and format fixed-width EBK names. |
| `OverseasPaymentService.java` | Keep the TT/DR PIN control and shared debtor-address enquiry. Omit CHATS beneficiary slots. |
| `FpsPaymentService.java`, `OnUsPaymentService.java` | Remove the name-only CHATS block. FPS accepts an absent creditor postal address. |
| `DrPaymentService.java` | Format the China account names in the dedicated IPE direct-remittance block. |
| `PaymentValidationSupport.java` | Restore the CHATS name maximum to 140; retain v0.8 town, address-character and customer-ID checks. |
| `PaymentServiceImpl.java` | Retain CHATS validation, routing identifiers and batch enquiry; share the confirmed five-type requested-execution-date mapping with 2126. |
| `Pe3Api.java`, `Peechj7a*.java`, `api/pe3/peechj7a.openapi.yaml` | Add the PE3 contact enquiry and generated wire models using the existing PE3 client. |
| `PartyPreferenceMapper.java` / `.xml`, `PartyPostalAddress.java` | Remove the obsolete BCO address query and DTO. |
| Payment unit tests | Verify the distinct submission fields for all five channels, name and address boundaries, debtor-address packing/missing/error cases, as well as existing reference, routing, enquiry and payment validation behavior. |

No database migration, public JSON field addition/renaming or generated-model change is required. Conditional behavior is implemented in service code; shared API types and the supplied external IPE contract are not regenerated or modified in this correction. The earlier temporary 35-character API-name restriction is removed by this revision.

## 5. Remaining differences and SIT checks

The new source documents also differ from current code in BIC applicability/expansion, member-ID and account limits, ignored-field behavior, field naming (`transactionId` / `instructionDate`), new 400xxx/700xxx errors and some currency rules. These are recorded for separate review and are not silently implemented as part of the two confirmed defects.

Other 2065 integration dependencies remain:

- To confirm the new PE3 endpoint in SIT, submit one CHATS payment. Find the INFO entry `PEECHJ7A enquiry completed` with the same `messageId` and verify `returnCode=00`. A successful enquiry with `contactCount=0` also passes this endpoint check.
- Confirm outbound debtor-account formatting, agent mapping, member-ID resolution and charge-bearer conversion.
- Confirm IPE forwards the four CHATS deposit fields to EBK in order. Verify the resulting creditor address in the raw pacs.008 and the PE3 debtor address separately.
- Confirm the remittance array/element length definition and downstream message verification/failure reporting.

SIT should capture the original API request, HTH-to-IPE routing/submission payloads and actual pacs.008 XML. Verify a short customer ID, 35-character ID, omitted ID (`NOTPROVIDED`), repeated customer IDs with distinct HTH `txnRef` values, explicit/automatic CHATS routing, 35/36-character names and 35/36-character towns. Verify enquiry after submission uses the generated reference and obtains the correct single-payment batch result. Include an older CHATS record as a compatibility case.

For CHATS SIT, use a distinct beneficiary name and two recognisable address lines. Check the four IPE/EBK fields, then compare the first two `Cdtr/PstlAdr/AdrLine` values in the raw pacs.008 with the corresponding address lines. Include a name or address longer than 35 characters and an omitted address case. For an account with a PE3 address, verify `Dbtr/PstlAdr` separately.

Real IPE acceptance, database integration, pacs.008 generation and transmission are not established by mocked unit tests.

## 6. Verification and delivery

The 26 November Java 21 offline Maven clean payment regression passed 288 tests with zero failures, errors or skips, including the five payment submission suites and the PE3 contract test. Coverage includes CHATS slot order and whitespace, TT preservation of a long first line, DR short/34/35/140-character English names and the blank Chinese field, FPS submissions with and without an address, and On-us submission with the caller name. The tests inspect the HTH submission to a mocked IPE. Live IPE, EBK and payment-network output require the SIT checks in section 3.

Payment service tests simulate a PE3 client failure for each of the five types and verify submission to IPE with the debtor address omitted. CHATS tests also cover malformed replies, header and return-code errors, oversized address lines, omission from serialized JSON and an IPE rejection after a PE3 failure. PE3 and IPE are mocked.

The 25 November PE3 replacement first passed 45 targeted tests (44 CHATS service cases and one Feign wire-contract test). Coverage includes account-key request/header mapping, exact endpoint and JSON property names, response decoding, first-contact selection, empty addresses and failures before IPE submission. The subsequent direct-line correction tests one-to-one mapping, preserved empty-line positions, fifth-line-only input and per-line bounds. It passed 46 targeted executions (45 CHATS service cases plus one wire-contract case), with 0 failures, errors or skipped tests. Log: `/tmp/hth-pe3-address-20261125/direct-lines-test.log`. The verification reactor and generator run from `/tmp/hth-pe3-address-20261125/`; project POMs and dependencies are unchanged. Before the direct-line correction, the full clean payment regression passed 276 tests with 0 failures, 0 errors and 0 skipped. Log: `/tmp/hth-pe3-address-20261125/regression.log`. Request/response schemas were expanded and compared with the supplied contract, including every field and bound.

The country/town follow-up uses the supplied PE9CHJ7A request form (provider sheet rows 73, 97-111) for contact-type applicability and country-code examples. Targeted tests cover structured HK/China/Macau/international addresses, every listed non-structured type, generic-city fallback, ISO code normalization/omission, metadata-only addresses, unknown contact types and first-contact isolation. Final verification passed 80 targeted tests (79 CHATS cases plus one PE3 wire-contract case), with 0 failures, errors or skipped tests. Log: `/tmp/hth-pe3-address-20261125/country-town-final.log`. PE3 and IPE calls are mocked.

### Previous verification on 20 November

The 20 November extension passed a Java 21 offline Maven **clean test** run: **305 tests, 0 failures, 0 errors, 0 skipped**. Four parameterized tests add 48 executions through the TT, DR, FPS and On-Us service entry points, covering name lengths 1, 34, 35, 36, 69, 70, 71, 104, 105, 106, 139 and 140. All 48 failed against the prior production mapping and pass after this extension. Assertions check the outbound IPE request, full canonical names and short-name duplication. Existing CHATS tests pass through the shared builder.

The run includes all five payment-service suites, overseas validation, the common routing builder, routing submitter, payment entry service and TT support. Log: `/tmp/hth-all-payment-names-20261120/test.log`. IPE calls and persistence are mocked; deployed IPE acceptance remains to be verified in SIT.

### Previous verification on 18 November

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

Current clean-build test log: `/tmp/hth-2126-all-execution-dates-clean.log`. The earlier BCO address-query fixtures describe the superseded implementation; the 25 November PE3 replacement removes that query. The current run also covers all five requested-date mappings and initiatedDate/asOf preservation.

Reproduction with a temporary reactor containing the parent/common/DSP/support dependencies:

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,PaymentServiceImplTest,PaymentEnquiryServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,FpsPaymentServiceTest,TtPaymentServiceTest,DrPaymentServiceTest,OnUsPaymentServiceTest,OverseasPaymentServiceTest,AccountServiceImplGenericEnquiryTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

Commit only this correction and its tests/design to local DSP Git. Sync the changed Java/mapper XML files and refresh existing story-delivery sources from the same current commit under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2065/source/`, preserving repository-relative paths, and place this design at the story root. Source documents and unrelated working-tree changes are not included in the code commit.
