# BCOH2H-2110 — CM user contact update: Email + Mobile

Date: 2026-11-03 · Implementation baseline: `4efb66ed`

## 1. Scope and result

For an existing HTH user whose **email and mobile number are both changed**, send the reviewed Feature 9 notifications after final approval and successful business execution. Preserve ordinary BCO, HTH email-only/mobile-only changes, PIN notifications, user creation and API Password Code approval flows.

Sources:
- `[#BCOH2H-2110] CM - User Management - User Contract Point Edit - Update Email + Mobile number - Notification template.pdf`, AC1–AC5.
- `2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx`, Feature 9.
- Current `UserExtensionData`, approval extension, Alert framework SDK and `SMSDispatcher`.

## 2. Exact recipient and template mapping

All routes below use explicit `EXTERNAL / USER` recipients. The template content comes from the named reviewed template; a private database ID prevents changing BCO or other HTH features.

| Route | Event ID | Private template ID | Reviewed template family | Recipients |
|---|---|---|---|---|
| Email A | `HTH_2110_CONTACT_UPDATE` | `HTH2110_EMAIL_A_<locale>` | `INFO_UPDATE_BY_CORP_ADMIN_EXTERNAL_USER_EMAIL` | Target's old email, new email, final approver email, company officeEmail |
| Email B | `HTH_2110_USER_EDIT` | `HTH2110_EMAIL_B_<locale>` | `USER_MANAGEMENT_EDIT_CORPORATE_USER_EMAIL` | Final approver email and company officeEmail |
| SMS A | `HTH_2110_MOBILE_WELCOME` | `HTH2110_SMS_A_<locale>` | `CorporatePlus Welcome Mail_EXTERNAL_USER_SMS` | Target's new mobile: welcome |
| SMS B | `HTH_2110_EMAIL_UPDATED` | `HTH2110_SMS_B_<locale>` | `User Email Address update_EXTERNAL_USER_SMS` | Target's new mobile: email update |
| SMS C | `HTH_2110_USER_EDIT` | `HTH2110_SMS_C_<locale>` | `USER_MANAGEMENT_EDIT_CORPORATE_USER_SMS` | Final approver mobile: generic user information update |
| SMS D | `HTH_2110_OLD_MOBILE` | `HTH2110_SMS_D_<locale>` | `User Mobile Number Updated Reminder_EXTERNAL_USER_SMS` | Target's old mobile: mobile update reminder |

Supported locales: `en`, `zh-hant`, `zh-hans-cn`. Email A/B use the reviewed bilingual English/Traditional Chinese HTML for all three locales. SMS has separate English, Traditional Chinese and Simplified Chinese text. The existing Alert locale-selection mechanism remains in use.

With distinct valid contacts this produces **6 emails and 4 SMS**. The company receives no separate SMS under 2110. The approver receives **SMS C**, not the two target-specific SMS reminders previously added by 851.

Email A and Email B have different subjects and bodies. Both are required for the approver/company, even when sent to the same address. No notification contains a password or Password Code.

## 3. Existing behavior and required change

| Existing implementation | 2110 implementation |
|---|---|
| Combined contact changes call the general BCO notification method. Generic event recipient rules can choose the company variant. | Bypass that method only for the persisted HTH combined-contact case; explicitly select Email B's corporate-user wording for both approver and company. |
| 851 adds the target-specific email/mobile-update SMS to the final approver. | One generic user-edit SMS C for the final approver. |
| Several events reuse the same mutable log/detail object. | Each destination has a new log and detail object; deferred processing cannot observe a later recipient's data. |
| Some deduplication applies only when adding the approver. | Deduplicate normalized email within each event/channel across all four roles. Keep different events separate. |
| SMS country lookup may see the target's updated country code when sending to the old number. | Preserve old and new number/country pairs; use the existing explicit `number~country` dispatcher contract. |
| Notification registration occurs inside user update. | Save a request-local snapshot only after update success; register after the SDK's final business commit. |

## 4. Trigger and data sources

1. `UserExtensionData.update` (`MT_N_UUS`) uses the existing persisted `HTH_USER_PROFILE` channel lookup. Client-supplied channel alone does not select 2110.
2. Existing `checkAlerts` compares stored and requested contacts. Its flags mean **unchanged**; 2110 requires both flags to be false.
3. Before writing, copy old email/mobile/country plus the requested new values, target party/user and approval reference. Later changes to managed entities cannot replace the old values.
4. A successful `CZProductUsers.update` result sets the 2110 marker. Validation-only calls do not set it. A failed result does not register a success notification.
5. The SDK invokes `CZTransactionExt.postPerformAction` after service execution and its final commit. The HTH callback consumes the marker, verifies the saved transaction is `APPROVED`, action `APPROVE`, processing `SUCCESS`, service `UserExtensionData.update`, and has the same target party/user and transaction ID.
6. Read the last actual signer from the stored approval record. Do not assume the executing session user is the target or final approver. Maker-only and unresolved approval data do not qualify.
7. Re-read the saved target contacts and require them to match the snapshot before notification registration. Resolve the final approver's email/mobile/country from its user/extension records and company officeEmail/name from existing party preferences.
8. Register each explicit route using the existing Alert pipeline. A missing contact skips that destination; a registration failure is logged without changing an already committed business result.

```mermaid
sequenceDiagram
    participant AP as Final approver
    participant A as Approval service
    participant U as User update
    participant DB as Existing database
    participant N as HTH 2110 helper
    participant E as Existing Alert / MNG pipeline
    AP->>A: Approve user contact change
    A->>U: Execute approved update
    U->>DB: Read persisted HTH identity and old contacts
    U->>U: Copy old/new contacts and country codes
    U->>DB: Save user and extension
    alt Business update succeeds
        U->>U: Store request-local notification snapshot
    else Business update fails
        U-->>A: Failure; no notification snapshot
    end
    U-->>A: Execution result
    A->>DB: Commit transaction and execution outcome
    A->>N: Post-approval callback
    N->>N: Consume snapshot; check action/result/transaction
    N->>DB: Confirm APPROVED + SUCCESS and final signer
    N->>DB: Confirm saved contacts; read approver/company
    alt All execution and ownership gates pass
        N->>E: Email A to old/new target, approver, company
        N->>E: Email B to approver and company
        N->>E: SMS A+B to new target mobile
        N->>E: SMS C to final approver; SMS D to old target mobile
    else Failed, partial, missing snapshot or mismatched transaction
        N->>N: No success notification
    end
```

## 5. Content, masking and deduplication

- SQL contains the exact reviewed subjects, HTML, punctuation, placeholders and literal `\r\n` SMS markers. It does not substitute sample transaction data into templates.
- Reuse existing DTOs and BCO placeholder bindings. Email A describes the target ID; Email B's `compName` comes from **company preferences**, not the target user's name.
- Email A's Chinese subject/content fragments use existing `INFOUPDATE_EMAILMOB_UPDATE_MAILSUBJ` / `...MAILCONTENT` configuration. Deployment verification includes their values.
- Before SMS registration, apply existing `CZCommonUtils.maskName/sepChar` to the identity placeholder. Preserve existing email-address masking for SMS B. No new masking algorithm is introduced. SMS C contains no customer name.
- Email keys are trimmed and compared without case. SMS keys use normalized country + local number. Deduplication applies to **one event and channel in this execution**.
- Different events are not deduplicated: two new-mobile SMS and both approver/company email families remain. There is no new cross-transaction or cross-retry deduplication store.

## 6. Code and BCO impact

| File / configuration | Change and impact |
|---|---|
| SMS module `UserExtensionData.java` | Small HTH-only combined-contact branch; preserve old data before update and defer the route after success. The existing notification method stays unchanged. |
| SMS module `HthContactUpdateNotification.java` | New feature helper: snapshot, six routes, explicit destinations, masking and event-scoped deduplication. |
| Approval module `CZTransactionExt.java` | One call to the HTH-only completion callback. No marker means no additional reads or sends. |
| Approval module `HthContactUpdateApprovalLifecycle.java` | New final-approval/processing/ownership gates and internal bridge to the SMS module; preserves module build order. |
| `20261103_BCOH2H-2110/1_HTH_Contact_Update_Notification.sql` | Five private events, eighteen private templates/routes and copied placeholder bindings. Existing source templates are read only. |
| Existing SMS country skip-list | Append only four `HTH_2110_*` SMS events; retain every existing value/default. No BCO event is added, removed or reconfigured. |

No schema changes, new tables, scheduler, batch, shared DTO/common package, dispatcher source, REST API or frontend changes. Existing bounce-back handling is unchanged. Prior 880/881 functionality stays in the source baseline.

## 7. Deployment and verification

1. Export the private configuration rows, if already present, and the country skip-list as a backup.
2. Execute the **whole** `1_HTH_Contact_Update_Notification.sql` block with UTF-8. It checks source templates/bindings, replaces only feature-owned configuration and commits once. On failure it rolls back this block. Execute after older notification scripts.
3. Run `2_HTH_Contact_Update_Verification.sql`: expect eighteen template routes and five active actions; confirm four events in the country skip-list, and none in recipient-override lists.
4. Build/deploy both SMS and approval modules from the same revision. Reload the environment's existing notification/configuration caches or restart the affected application according to its normal deployment procedure.
5. Execute a new final approval; check `HTH_2110 stage=DEFERRED`, then `REGISTERED`. Diagnose `APPROVAL_CALLBACK_FAILED`, `COMMITTED_CONTACT_MISMATCH`, `CONTACT_SKIPPED`, `POST_COMMIT_FAILED` and `REGISTER_FAILED` as distinct stages. Logs omit addresses, phone numbers and message content.
6. Use verification Q5 with a narrow time range to reconcile the six-email/four-SMS matrix. A framework/MNG success acknowledgement does not establish actual inbox or handset delivery.

Rollback: restore the previous SMS/approval binaries together, restore the backed-up feature configuration/skip-list, and reload caches. Do not remove other HTH/BCO configuration. This change does not migrate customer data.

## 8. Validation and remaining environment checks

Local tests:
- Compile both complete modified shared classes and new helpers against repository SDKs, Java 8 target.
- Execute production recipient builder and approval gates with SDK DTOs; contact/database/network/clock configuration use fixtures.
- Check all six routes, ten notices, missing contacts, distinct/shared mailbox/phone cases, preservation of old values after managed-object mutation, different country codes, and application of the existing masking function.
- Verify real SDK EXTERNAL recipient derivation keeps the explicit destination and delivery identity.
- Verify no success notifications for failed/partial/unrelated approval, mismatched owner or contacts, missing marker, replay callback, ordinary BCO or single-field updates.
- Compare raw SQL subjects/HTML and all twelve SMS language bodies with v81 Feature 9; compare the existing BCO notification method with the baseline.
- Re-run existing 851 recipient/PIN-gate and 880 approval regressions.

SIT/UAT still required: execute/re-execute Oracle SQL; verify actual placeholder values and locale selection, real final approval (including the environment's OMB route), actual MNG email/SMS delivery, and ordinary BCO/email-only/mobile-only regression. Local tests do not claim these environment checks passed.

The completion marker is request-local and consumed once. This release adds no durable notification outbox or automatic recovery for a process crash between business commit and event registration; registration failures are diagnostic/manual follow-up cases. A custom background execution path that does not return through the inspected SDK completion hook needs explicit environment verification before release.

## 9. Source control and mcdp delivery

Commit/push the source repository, then copy this change's committed files into:

```text
mcdp-h5-demo-int/
  bcohth-2110/
    README.md
    SHA256SUMS
    hhfi/
      consulting/middleware/projects/...
      consulting/db/branch_change_history/20261103_BCOH2H-2110/...
      story/notification/BCOH2H-2110-TECHNICAL-DESIGN.md
      devtools/backend-compile/tests/...
```

The mcdp root `consulting` directory is not a sync target. The overlay is based on the stated source revision, not a standalone application checkout.
