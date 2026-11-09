# BCOH2H-1334 Tech Design

Parent story: **BCOH2H-1334**  
Existing sub-task: **Tech Design**  
Date: 2026-11-09  
Title: **Mobile only contact update notification**

## Outcome

Reuse the existing BCO events, templates and recipients. Remove only the extra contact-specific SMS copied to the HTH approver. Existing contact-update email notifications and the generic `USER_MANAGEMENT_EDIT` notification remain.

## Current behavior and change

| Event ID | Channel | Recipient after this change |
|---|---|---|
| `INFO_UPDATE_BY_CORP_ADMIN` | Email | Target email, final approver email and company officeEmail |
| `USER_MANAGEMENT_EDIT` | Email / SMS | Existing BCO routes: final approver email/SMS and company officeEmail |
| `CORPORATEPLUS_WELCOME_MAIL` | SMS | Target new mobile; keep the existing UserExtensionData.create activity mapping |
| `USER_MOBILE_NUMBER_UPDATED_REMINDER` | SMS | Target old mobile only; remove the extra approver copy |

The redundant SMS is registered at the end of `UserExtensionData.alertUserProfileUpdate` through `HthProfileApproverNotification.sms`. Removing that additional registration satisfies the recipient adjustment. No replacement event, template, callback or database table is needed.

## Trigger and contact sources

The existing CM final approval executes `UserExtensionData.update`; the contact-success notification is called after the existing business-success check. Preserve that approval and success path. The final approver for explicit HTH contact emails is resolved from the approved transaction's last signer. The generic BCO event keeps the session UserId; in the confirmed normal CM flow this is the final approver.

Old email is captured before persistence; old mobile is captured by the update method. New contacts come from the approved request. Company officeEmail comes from party preferences. A changed managed entity must not replace the captured old email. No background-worker recipient substitution is introduced.

## Template and deduplication

Reuse all installed BCO role/channel/locale template bindings. Do not create HTH copies or modify their text. The generic event can select different corporate-user and company templates; do not merge them. Query Q3 in `consulting/db/branch_change_history/20261109_BCOH2H-1332_1333/2_Verify_Password_Success_Templates.sql` lists the installed IDs and raw content.

Keep the existing per-contact-event email deduplication, including the final approver comparison against already collected addresses. There is no new deduplication across `INFO_UPDATE_BY_CORP_ADMIN` and `USER_MANAGEMENT_EDIT`; both can legitimately reach the same approver/company address. Message counts depend on configured routes and missing/shared contacts. SMS masking and country-code handling remain on the existing BCO path.

## Implementation

| File | Change |
|---|---|
| `HthProfileApproverNotification.java` (SMS module) | Remove the additional approver SMS builder and the mobile/country lookup used only by that builder. Keep final approver email resolution, old-email snapshot, persisted HTH channel resolution and PIN/success gates. |
| `UserExtensionData.java` (SMS module) | Remove the loop that registers the additional approver SMS. Keep the existing BCO notification method and generic event. |
| `Hth851ApproverTest.java` | Execute current notification methods for email-only, mobile-only and combined changes; compare event recipients and business template input with the BCO baseline. |

The two Java files are shared by 865 and 1334 and must be delivered together from the same current checkout. They include the already merged 880/2110 changes; do not overlay older story copies.

No DML is required for this story. No changes to batch, dispatchers, shared DTOs, approval code, frontend, PIN behavior or User Access 1216.

## Verification and deployment

- A new final-approved mobile-only change retains the new mobile welcome and old mobile reminder. The approver retains the generic user-edit SMS and both configured email event types; no approver copy of USER_MOBILE_NUMBER_UPDATED_REMINDER is registered.
- Compare ordinary BCO email-only/mobile-only/combined/no-op events with the pre-change baseline.
- Preserve combined-contact 2110 behavior, old-email snapshot, email deduplication and PIN-change gates.
- Local checks: `verify_hth_851_approver.py` and `verify_hth_851_compile.py` (Java 8 target, repository dependencies).
- Build/deploy the SMS module with both changed Java files. Reuse the existing database configuration.
- For a fresh UAT approval, use Q4 to reconcile event, recipient, channel and time. A dispatch record is not proof of inbox/handset delivery.

## AC coverage and limits

AC1/AC2: retained BCO email routes and target SMS, extra approver contact SMS removed. AC3: existing BCO masking retained. AC4: existing business-success and approval gates retained. AC5: existing configured BCO templates retained, corresponding to reviewed Feature 11.

Local fixtures verify event generation and template inputs, not live Oracle configuration, multi-level approval execution or MNG delivery. No new promise of global exactly-once delivery is introduced.

## Existing Tech Design sub-task content

Summary: **BCOH2H-1334 Tech Design — Mobile only contact update notification**

Attach this document to the existing Tech Design sub-task. Review the retained recipient matrix, removal of the extra approver SMS, BCO impact and UAT checks. No new Jira sub-task is required.
