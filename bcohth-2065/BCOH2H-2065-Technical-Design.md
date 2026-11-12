# BCOH2H-2065 — HTH CHATS Payment Beneficiary Bank Verification

Revision: 12 November 2026. Minimal IPE contract correction on DSP baseline `576d6fc`.

## 1. Scope

Preserve the CHATS instruction fields passed from HTH to IPE and reject confirmed invalid values before submission. This revision addresses address and payer-name validation gaps against the latest checked-in `hth-ms-support/hth-ms-support-svc/src/main/resources/api/ipe/smartRouting.openapi.yaml`.

The story source is `jira/[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`. The validation bounds below come from the IPE interface contract; they are not additional requirements quoted from the story.

HTH constructs the IPE request. It does not generate, retrieve, compare or transmit pacs.008 in this repository. Final message verification and transmission require downstream SIT evidence. This revision is a limited correction, not confirmation of full IPE or pacs.008 compatibility.

No database migration, public API/schema change, generated-model change or new error code is required. Posting, fees, caches and response-status mapping retain their current behavior.

## 2. Validation rules

IPE `PostalAddress` has no required properties. CHATS address fields therefore remain optional; the TT mandatory-address rules must not be applied to CHATS.

| Source / IPE field | Rule in this revision | Existing HTH error |
| --- | --- | --- |
| `creditor.postalAddress.addressLine1` | If supplied, at most 70 characters | `500141` |
| `creditor.postalAddress.addressLine2` | If supplied, at most 70 characters, including all-space values; retain the existing character-set check for nonblank values | `500144` |
| `creditor.postalAddress.townName` | If supplied, at most 70 characters, matching IPE rather than the TT limit of 35 | `500142` |
| `creditor.postalAddress.country` | If supplied, exactly two uppercase letters; no trimming or country-directory lookup | `500143` |
| Party-preference customer name → `debtor.name` | Nonblank and at most 140 characters | `500000`, with the existing parameter-name prefix `debtor.name` |

Lengths count Unicode code points. Values are not trimmed, truncated or split by the new validators. Empty optional address lines/town remain allowed; an empty supplied country is invalid. Omitting the address object or all its properties is supported without fabricating an address.

The existing BIC/member-ID, account, currency, charge-bearer, category-purpose, purpose and common request validations remain in effect. The existing addressLine2 character restriction remains narrower than the IPE schema. This change does not widen shared validators used by TT, FPS, DR or ONUS.

## 3. Processing and code changes

```text
Payment instruction → existing common validation and payer-name lookup
  → explicit CHATS: check address and payer name
  → construct common IPE request → IPE /validation selects payment type
  → selected CHATS: check address and existing CHATS rules
  → existing account/balance/Scameter checks
  → reload and check payer name → enrich CHATS request
  → insert pending payout → IPE /credit-transfer → existing result handling
```

- **`PaymentValidationSupport.java`:** add CHATS-specific optional-address and payer-name checks. Invoke the address check from `validateChatsRules`. Keep the existing TT address rule unchanged.
- **`PaymentServiceImpl.java`:** for an explicitly requested CHATS payment, perform the new checks before IPE routing.
- **`ChatsPaymentService.java`:** validate the actual payer name retrieved for submission, including when automatic routing selects CHATS. Map an absent creditor address safely and still insert the payout record with null address fields.
- **`ChatsPaymentIpeContractTest.java`:** add an active, focused regression suite for these changes. The pre-existing `ChatsPaymentServiceTest.java` in this baseline is entirely commented out; this revision does not reactivate that obsolete suite.

For automatic routing, CHATS-specific checks run after IPE selects CHATS and before submission. This limited revision does not impose CHATS validation on other payment types before routing; IPE may reject an invalid automatic-routing payload before HTH reaches the selected-channel check.

## 4. Preserved mapping and unresolved items

| Item | Current behavior / pending confirmation |
| --- | --- |
| Debtor bank and purpose | Retain `BEASHKHHXXX` and `/IFT01/`. These are bank-owned values. |
| Debtor name | Retrieved by `PartyPreferenceMapper.selectCustomerNameByPartyId`; it is not a fixed sample name. |
| Debtor address | Preserve a trusted address already present in the routing request. No address retrieval is added; the source and responsible system remain unconfirmed. |
| Creditor name | Retain the full name in `creditor.name`, `creditorAccount.accountName` and legacy `depositAccountName1`. IPE allows 140 characters in the first two fields but only 35 in `depositAccountName1`. Names of 36–140 characters can still violate that legacy-field contract. Agree the mapping with IPE before truncating, splitting, omitting or rejecting them. |
| `depositAccountName2` | Preserve `6304357992232185` as previously requested. Its downstream meaning still needs confirmation. |
| Creditor account / member ID | Retain the existing effective account limit of 18 and member-ID limit of 5. IPE's wider limits alone do not establish that HTH business validation should be widened. |
| Debtor account | Preserve internal-number mapping. Confirm which IPE field becomes the outbound external account identification and any conversion rules. |
| Customer End-to-End ID | Preserve current common validation and response echo. This baseline does not default a missing customer ID to `NOTPROVIDED`. The separate IPE internal identifier remains unchanged; the downstream customer-ID mapping is unconfirmed. |
| Remittance | Retain the existing maximum total of 140 characters split into four 35-character segments. IPE declares `maxLength` on the array rather than its items; clarify/correct the contract before changing this behavior. |
| Agent and charge mapping | Confirm the outbound InstructingAgent/InstructedAgent mapping, member-ID resolution and charge-bearer conversion with IPE. |
| Verification result | Agree how a message mismatch stops transmission and how that outcome reaches HTH. Submission success alone does not prove message verification or delivery. |

The unresolved mappings above remain integration dependencies, not completed acceptance criteria. No unrelated null-result fee-cache fix is included.

## 5. Verification

Executed with Java 21 and Maven using the local dependency cache and a temporary reactor; no project POM or dependency version was changed.

| Suite | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| `ChatsPaymentIpeContractTest` | 41 | 0 / 0 / 0 |
| `PaymentCommonRoutingBuilderTest` | 5 | 0 / 0 / 0 |
| `PaymentRoutingSubmitterTest` | 9 | 0 / 0 / 0 |
| `PaymentEnquiryServiceTest` | 40 | 0 / 0 / 0 |
| **Total** | **95** | **0 / 0 / 0** |

The new suite covers 70/71-character address boundaries, all-space addressLine2, country format, missing/blank/140/141-character payer names, Unicode character counts, explicit and automatic CHATS routing, a changed payer name between lookups, null-address submission and persistence, and unchanged legacy name mapping. Serialized boundary fields are checked against the checked-in IPE schema. This is not a complete schema-validation test.

Downstream APIs, database mappers and unrelated entry checks are mocked; request construction, route selection and CHATS processing are real. The suite does not certify token/account authorization, actual database constraints, real IPE acceptance or pacs.008 output. The retained 140/35-character name conflict is explicitly tested as legacy compatibility, not as IPE compliance.

Reproduction (the temporary reactor includes the required parent/common/DSP/support modules):

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentIpeContractTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,PaymentEnquiryServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

For SIT, test the accepted boundaries and one-over-limit values through the payment API. Capture the actual HTH-to-IPE request and original pacs.008 XML, not only the message-viewer page. Compare all story fields and obtain downstream transmission/completion evidence. Include absent address, full-length names and customer End-to-End ID cases to resolve the dependencies above. SIT/UAT and real IPE calls were not executed locally.

## 6. Delivery

Commit the three production files, the new test and this design to local DSP Git. Copy the changed Java files under `/Users/devs/DWksp/mcdp-h5-demo-int/bcohth-2065/source/`, preserving repository-relative paths, and place this design at the story directory root. No remote push or target-repository commit is performed.

This document describes the current baseline and correction. Historical test counts from earlier revisions are superseded by the verified results above.
