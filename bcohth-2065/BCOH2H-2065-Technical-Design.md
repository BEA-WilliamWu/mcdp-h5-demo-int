# BCOH2H-2065 - HTH CHATS Payment Beneficiary Bank Verification

## 1. Objective and scope

Ensure that CHATS payment details supplied by HTH to IPE retain the original customer instruction and the bank-owned values required by BCOH2H-2065. Cover normal and maximum-length inputs.

This implementation covers the HTH request mapping and its unit tests. The current repository does not generate, retrieve, verify or transmit pacs.008 messages. IPE or its downstream CHATS gateway owns that boundary. The story's message-verification, transmission and Completed acceptance criteria require downstream integration evidence before the story can be closed.

Source: `jira/[#BCOH2H-2065] HTH CHATS payment beneficiary bank verification.pdf`, updated 30 October 2026.

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
| Debtor account | Preserve the external account number from `debtorAccount.id` | `debtorAccount.identification`; converted internal number remains in `internalAccountNo` and `supplementaryData.debitInternalAccount` |
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
- Preserve the external debtor account in `identification`; retain separate internal-account fields for existing posting/enrichment usage.
- Preserve any trusted debtor postal address already present instead of replacing the entire debtor object.
- Remove the fabricated `depositAccountName2` value. The complete beneficiary address remains in the canonical creditor block.
- Preserve the optional legacy `depositAccountName1` duplicate only for names of at most 35 characters, as required by the IPE schema. Longer names remain complete in `creditor.name` and `creditorAccount.accountName`; no truncated name is submitted. Downstream consumption of these canonical fields is an explicit integration dependency.
- Skip fee-cache processing when the IPE result is absent, so a null response or submission timeout retains the existing `PROCESSING` fallback.

### ChatsPaymentServiceTest

- Freeze the service clock inside the CHATS operating window; production service-hour rules remain unchanged.
- Exercise public-request conversion, actual common routing construction, CHATS route selection and submission of the same request instance.
- Verify serialized BIC, purpose and account fields as well as normal and maximum-length instruction data.

No database migration, new API field or new error code is introduced. Existing CHATS input validation and response-status mapping remain in place. No 2055 posting, fee-persistence or settlement-lifecycle changes are included.

## 5. Validation and failure handling

Reuse the existing CHATS validation: exactly one of BIC/member ID, BIC format, mandatory address/town/country, account and currency rules, charge bearer and End-to-End ID rules. Invalid instructions must fail before IPE submission.

A successful IPE submission is not independently treated as proof that pacs.008 verification or transmission succeeded. Preserve existing result handling; a downstream verification failure must be reported through the agreed IPE failure contract. HTH cannot compare a generated message that is not exposed by that contract.

## 6. Unit-test coverage

- Eight- and eleven-character beneficiary BICs survive JSON serialization unchanged.
- Invalid BICs, missing bank identifiers, and simultaneous BIC/member ID are rejected before submission.
- Member-ID-only requests preserve the member ID without inventing a beneficiary BIC.
- Fixed BEA BIC and `/IFT01/` purpose; customer category purpose cannot override the CHATS purpose, and an omitted category purpose is supported.
- External and internal debtor account numbers remain distinct and correct.
- HKD, CNY, USD and EUR amounts/currencies and the requested settlement date remain unchanged.
- `SHA` and `OUR` retain the IPE one-character mapping.
- Full-length canonical fields: 140-character names, 70-character address lines, 35-character town, 18-character beneficiary account, 35-character customer identifiers and 140-character remittance information.
- Unique routing identifiers remain unchanged, including when the customer End-to-End ID defaults to `NOTPROVIDED`; the customer value is checked in the response echo.
- Trusted debtor address retention and existing persistence behavior.
- Null/failed IPE submission retains `PROCESSING` without a secondary fee-cache exception.

These tests validate the HTH boundary; they do not establish that a generated pacs.008 was transmitted.

## 7. Downstream dependencies and SIT/UAT

The following remain pending integration confirmation; no new IPE or customer-address interface was supplied for this implementation:

1. **End-to-End ID:** agree how the customer ID, including `NOTPROVIDED`, reaches `PmtId/EndToEndId` separately from IPE's unique transaction identifier. The current customer value is echoed in the HTH response but is not separately persisted or sent in an agreed IPE field.
2. **Debtor address:** identify the authoritative customer/account address source and which system retrieves it. HTH must not invent the address or copy the beneficiary address.
3. **Agent mapping:** confirm that the debtor/creditor agent fields feed the story's InstructingAgent/InstructedAgent fields. For member-ID-only input, verify the downstream bank-directory resolution.
4. **Beneficiary data:** confirm that pacs.008 generation uses the complete canonical creditor name/address and supports an absent optional legacy name/address duplicate. This is required for the full-length case.
5. **Charge bearer:** confirm the downstream conversion from `S` to `SHAR` and the appropriate equivalent for `O`.
6. **Verification and completion:** identify the generator/verifier, how mismatches prevent transmission, and how the verified execution result is returned to HTH. A mismatch must not be reported as Completed merely because submission was accepted.

For AC1, capture the customer request, HTH-to-IPE request and generated pacs.008 for one CHATS payment. Compare every story field, verify transmission and obtain the downstream completion evidence. For AC2, repeat with maximum-length inputs and check for truncation, substitution and character changes.

Include an IPE/gateway mismatch or tampering test to establish that a failed verification stops transmission. UAT should review the same field-comparison evidence and business outcome. Unit tests alone cannot close these acceptance criteria.

## 8. Delivery

- Source implementation: `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/payment/ChatsPaymentService.java`
- Tests: `hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/ChatsPaymentServiceTest.java`
- Local Git commit contains only this story's code, tests and this document.
- Copy changed code/tests under `bcohth-2065/source/`, preserving repository-relative paths.
- Copy this document to `bcohth-2065/BCOH2H-2065-Technical-Design.md`.
- Destination root: `/Users/devs/DWksp/mcdp-h5-demo`.

## 9. Verification result - 4 November 2026

Verified with Java 21 and Maven using the existing local dependency cache. Production and test sources compiled successfully. No repository POM or dependency version was changed.

| Test suite | Tests | Passed | Failed / errors |
| --- | ---: | ---: | ---: |
| ChatsPaymentServiceTest | 47 | 47 | 0 |
| PaymentCommonRoutingBuilderTest | 5 | 5 | 0 |
| PaymentRoutingSubmitterTest | 9 | 9 | 0 |
| PaymentServiceImplTest | 51 | 49 | 2 failures, 0 errors |
| Total | 112 | 110 | 2 failures, 0 errors |

The CHATS suite includes 22 added test executions. All 61 CHATS/routing tests pass. The broader Maven run returns a failure status because the following unchanged payment-entry tests fail both before and after this change:

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
