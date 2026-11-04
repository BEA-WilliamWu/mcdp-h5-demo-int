# BCOH2H-2065 - HTH CHATS Payment Beneficiary Bank Verification

## 1. Objective and scope

Ensure that CHATS payment details supplied by HTH to IPE retain the original customer instruction and the bank-owned values required by BCOH2H-2065. Cover normal and maximum-length inputs.

This implementation covers the HTH request mapping and its unit tests. The current repository does not generate, retrieve, verify or transmit pacs.008 messages. IPE or its downstream CHATS gateway owns that boundary. The story's message-verification, transmission and Completed acceptance criteria require downstream integration evidence before the story can be closed.

Source: `jira/[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`, updated 30 October 2026.

### Scope correction after review - 4 November 2026

The story requires payment-instruction fields to match the generated message (AC1), including full-length inputs (AC2). It does not prescribe changes to the legacy IPE account-number mapping, conditional omission of legacy name fields, or fee-cache error handling. These must not be presented as explicit requirements.

| Reviewed change | Requirement evidence | Decision |
| --- | --- | --- |
| External account in IPE `debtorAccount.identification` | Page 1 maps API `debtorAccount.Id` to the outbound DebtorAccount identification; AC1 requires account consistency. It does not define which IPE field the generator consumes or its formatting | Restore the pre-2065 internal-number mapping pending IPE confirmation |
| Omit `depositAccountName1` for names longer than 35 | AC2 on page 2 requires full-length inputs. The 35-character limit comes from the IPE schema, not the story; omission was an implementation assumption | Restore unconditional legacy assignment; resolve the schema/full-length conflict with IPE before changing it |
| Remove `depositAccountName2` | No explicit removal requirement; the existing field's downstream purpose is unconfirmed | Preserve the existing `6304357992232185` value as requested |
| Skip fee caching for null IPE results | No requirement in this story | Revert the guard and remove its added test; track any cache defect separately |

The explicit fixed debtor-bank BIC and proprietary purpose, existing-address retention, and relevant instruction-field tests remain. The restored mappings are compatibility behavior, not confirmation that the final message satisfies AC1/AC2.

## 2. Existing flow

```text
PaymentInstructionRequest
  -> PaymentInstructionConvert / common validation
  -> PaymentCommonRoutingBuilder
  -> IPE POST /validation
  -> ChatsPaymentService: validate and enrich the same request
  -> IPE POST /credit-transfer
  -> downstream CHATS message generation, verification and transmission
```

The smart-routing request and submission share the same batch and unique IPE transaction identifiers. CHATS enrichment runs after route selection, including when smart routing selects CHATS for an initially unspecified payment method.

## 3. HTH field mapping

The IPE paths below are relative to `transactions[0]` unless stated otherwise. The final pacs.008 mapping must be verified with the downstream owner.

| Story field | HTH source / rule | IPE request mapping |
| --- | --- | --- |
| Debtor / instructing bank | Fixed `BEASHKHHXXX` | `debtorAgent.financialInstitution.bicfi` |
| Beneficiary / instructed bank | Preserve `creditorAgent.financialInstitutionId.bicfi` exactly | `creditorAgent.financialInstitution.bicfi` |
| Clearing member identifier | Preserve the existing member-ID alternative; do not fabricate a BIC | `creditorAgent.financialInstitution.memberId` and existing `bankCode` mapping |
| Settlement amount and currency | Preserve `instructedAmount.amount` and `.currency`; retain existing same-currency validation | `amount.instructedAmount` and `amount.settlementAmount` |
| Settlement date | Preserve `settlementDate` | Batch-level `paymentInformation.requestedExecutionDate` |
| Debtor name | Existing trusted party-preference lookup | `debtor.name` |
| Debtor address | Retain an existing trusted address if already populated; current HTH flow has no source to populate one | `debtor.postalAddress`; see dependencies |
| Debtor account | Preserve pre-2065 behavior pending confirmation of the generator's account source and formatting | Converted internal number remains in `debtorAccount.identification`, `internalAccountNo` and `supplementaryData.debitInternalAccount`; final consistency with API `debtorAccount.id` is unverified |
| Beneficiary name and address | Preserve name, both address lines, town and country without truncation | `creditor.name`, `creditor.postalAddress`; account name/country remain in `creditorAccount` |
| Beneficiary account | Preserve `creditorAccount.id` | `creditorAccount.identification` |
| Customer End-to-End ID | Retain current validation/defaulting to `NOTPROVIDED` and echo in the payment response | No confirmed independent outbound field in the current IPE contract; see dependencies |
| Charge bearer | Retain `SHA -> S`, `OUR -> O` | `charges.chargeBearer`; IPE contract is a one-character OVR indicator, not the pacs.008 code |
| Payment reference | Existing lossless split into at most four 35-character segments | `remittanceInformation.unstructured` |
| Proprietary purpose | Fixed `/IFT01/`, independent of customer category purpose | `purpose.proprietary`; leave `purpose.code` unset |

Do not change the unique IPE `paymentIdentification.endToEndId` to the customer value or `NOTPROVIDED`. The checked-in IPE schema defines it as a 23-26-character unique transaction identifier. Its `instructionId` maps to `PmtId/InstrId`, so it is not an agreed substitute for the customer's End-to-End ID.

## 4. Code changes

### ChatsPaymentService

- Set the bank-owned debtor BIC and proprietary purpose during CHATS enrichment.
- Preserve any trusted debtor postal address already present instead of replacing the entire debtor object.
- Leave the legacy account-number mapping, unconditional `depositAccountName1` assignment and fixed `depositAccountName2` value unchanged after review. Keep full creditor name/address fields, but do not claim downstream full-length acceptance until the legacy-field contract is confirmed.
- Leave fee-cache processing unchanged; its null-response handling is outside this story.

### ChatsPaymentServiceTest

- Freeze the service clock inside the CHATS operating window; production service-hour rules remain unchanged.
- Exercise public-request conversion, actual common routing construction, CHATS route selection and submission of the same request instance.
- Verify serialized BIC, purpose and account fields as well as normal and maximum-length instruction data.

No database migration, new API field or new error code is introduced. Existing CHATS input validation and response-status mapping remain in place. No 2055 posting, fee-persistence or settlement-lifecycle changes are included.

## 5. Validation and failure handling

Reuse the existing CHATS validation: exactly one of BIC/member ID, BIC format, mandatory address/town/country, account and currency rules, charge bearer and End-to-End ID rules. Invalid instructions must fail before IPE submission.

A successful IPE submission is not independently treated as proof that pacs.008 verification or transmission succeeded. Preserve existing result handling; a downstream verification failure must be reported through the agreed IPE failure contract. HTH cannot compare a generated message that is not exposed by that contract.

Known pre-existing limitation retained by scope decision: the fee-cache path can dereference a null IPE result when a cache is present (or while logging a cache error). The submission mapper's Processing fallback does not guarantee an exception-free API response in that configuration. This requires a separate fix; it is not addressed or certified by this story's tests.

## 6. Unit-test coverage

- Eight- and eleven-character beneficiary BICs survive JSON serialization unchanged.
- Invalid BICs, missing bank identifiers, and simultaneous BIC/member ID are rejected before submission.
- Member-ID-only requests preserve the member ID without inventing a beneficiary BIC.
- Fixed BEA BIC and `/IFT01/` purpose; customer category purpose cannot override the CHATS purpose, and an omitted category purpose is supported.
- Existing internal account conversion remains intact; the external-to-outbound account mapping is a pending integration item, not a completed assertion.
- HKD, CNY, USD and EUR amounts/currencies and the requested settlement date remain unchanged.
- `SHA` and `OUR` retain the IPE one-character mapping.
- Full-length canonical fields: 140-character names, 70-character address lines, 35-character town, 18-character beneficiary account, 35-character customer identifiers and 140-character remittance information.
- Unique routing identifiers remain unchanged, including when the customer End-to-End ID defaults to `NOTPROVIDED`; the customer value is checked in the response echo.
- Trusted debtor address retention and existing persistence behavior.

These tests validate HTH field construction, not real IPE acceptance or transmission. The full-length test checks canonical Java fields and does not validate the legacy `depositAccountName1` against the IPE schema; its existing 35-character contract conflict remains unresolved. The unrelated null-cache regression test and the speculative 35/36-character omission tests have been removed.

## 7. Downstream dependencies and SIT/UAT

The following remain pending integration confirmation; no new IPE or customer-address interface was supplied for this implementation:

1. **End-to-End ID:** agree how the customer ID, including `NOTPROVIDED`, reaches `PmtId/EndToEndId` separately from IPE's unique transaction identifier. The current customer value is echoed in the HTH response but is not separately persisted or sent in an agreed IPE field.
2. **Debtor address:** identify the authoritative customer/account address source and which system retrieves it. HTH must not invent the address or copy the beneficiary address.
3. **Agent mapping:** confirm that the debtor/creditor agent fields feed the story's InstructingAgent/InstructedAgent fields. For member-ID-only input, verify the downstream bank-directory resolution.
4. **Beneficiary data:** confirm which name/address fields pacs.008 generation consumes and the purpose of `depositAccountName1/2`. Agree how full-length names are supported despite the legacy 35-character limit. Do not omit, truncate, split or repurpose legacy values until that behavior is confirmed.
5. **Charge bearer:** confirm the downstream conversion from `S` to `SHAR` and the appropriate equivalent for `O`.
6. **Verification and completion:** identify the generator/verifier, how mismatches prevent transmission, and how the verified execution result is returned to HTH. A mismatch must not be reported as Completed merely because submission was accepted.
7. **Debtor account:** confirm which IPE field becomes outbound DebtorAccount identification, whether an external or internal number is expected, and any formatting/conversion rules. The account-consistency criterion remains unverified while the existing mapping is retained.

For AC1, capture the customer request, HTH-to-IPE request and generated pacs.008 for one CHATS payment. Compare every story field, verify transmission and obtain the downstream completion evidence. For AC2, repeat with maximum-length inputs and check for truncation, substitution and character changes.

Include an IPE/gateway mismatch or tampering test to establish that a failed verification stops transmission. UAT should review the same field-comparison evidence and business outcome. Unit tests alone cannot close these acceptance criteria.

## 8. Delivery

- Source implementation: `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/ChatsPaymentService.java`
- Tests: `hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/ChatsPaymentServiceTest.java`
- Local Git commit contains only this story's code, tests and this document.
- Copy changed code/tests under `bcohth-2065/source/`, preserving repository-relative paths.
- Copy this document to `bcohth-2065/BCOH2H-2065-Technical-Design.md`.
- Destination root: `/Users/devs/DWksp/mcdp-h5-demo-int`.

## 9. Original verification result - before the scope correction

Verified with Java 21 and Maven using the existing local dependency cache. Production and test sources compiled successfully. No repository POM or dependency version was changed.

| Test suite | Tests | Passed | Failed / errors |
| --- | ---: | ---: | ---: |
| ChatsPaymentServiceTest | 47 | 47 | 0 |
| PaymentCommonRoutingBuilderTest | 5 | 5 | 0 |
| PaymentRoutingSubmitterTest | 9 | 9 | 0 |
| PaymentServiceImplTest | 51 | 49 | 2 failures, 0 errors |
| Total | 112 | 110 | 2 failures, 0 errors |

These are historical results for commit `946d019`, before the review corrections above; they must not be used as the revised delivery's test counts. That CHATS suite included 22 added test executions. All 61 CHATS/routing tests passed. The broader Maven run returned a failure status because the following unchanged payment-entry tests failed both before and after that change:

- `malformedDebtorAccountId_throws`: expects error `500160`, receives `500186`.
- `unknownDebtorAccount_throws`: expects `NoSuchElementException`, receives `ParameterException`.

The pre-change CHATS suite also failed outside operating hours; the fixed test clock removes this time dependency without changing production behavior. No tests are skipped or configured to ignore failures.

Reproduction: use a temporary Maven aggregator containing `hth-parent`, `hth-common`, `dsp-ms-support` and `hth-ms-support`, then run:

```sh
mvn -o -f /path/to/verification-pom.xml -pl :hth-ms-support-svc -am \
  -Dtest=ChatsPaymentServiceTest,PaymentCommonRoutingBuilderTest,PaymentRoutingSubmitterTest,PaymentServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -Dexec.skip=true test
```

SIT/UAT, generated-message verification and actual transmission were not executed locally.

## 10. Scope-review verification - 4 November 2026

The revised 2065-only sources were compiled and tested in an isolated export of commit `946d019` with this scope correction, without the 2055 changes:

| Test suite | Tests | Passed | Failures / errors / skipped |
| --- | ---: | ---: | --- |
| ChatsPaymentServiceTest | 44 | 44 | 0 / 0 / 0 |
| PaymentCommonRoutingBuilderTest | 5 | 5 | 0 / 0 / 0 |
| PaymentRoutingSubmitterTest | 9 | 9 | 0 / 0 / 0 |
| Total | 58 | 58 | 0 / 0 / 0 |

The current source checkout, which also contains 2055, passed 92 related tests: 54 CHATS submission, 24 CHATS enquiry, 5 common-routing and 9 routing-submitter tests. Both targeted Maven builds succeeded. The unrelated broader suites were not rerun for this scope correction.

Three executions tied to withdrawn changes were removed (the 35/36-character omission cases and the null-result fee-cache case). The revised 2065 suite therefore contains 19 added executions relative to the original baseline. Passing mapping tests does not resolve the account-source, legacy-name-length or fee-cache limitations above.

The refreshed `bcohth-2065/source` package contains the separately tested 2065-only revision. The `bcohth-2055/source` package contains the combined revision so its shared files do not reintroduce the withdrawn changes. Apply 2065 before 2055 when using both packages.
