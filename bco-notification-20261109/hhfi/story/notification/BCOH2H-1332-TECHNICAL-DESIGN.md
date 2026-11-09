# BCOH2H-1332 Tech Design

Parent story: **BCOH2H-1332**  
Existing sub-task: **Tech Design**  
Date: 2026-11-09  
Title: **API password reset completion notification**

## Outcome

Update the existing RESET success email and SMS templates using reviewed Feature 8 of `2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx`. Keep the current Java success notification and recipient flow. This story needs template SQL only; no new Java sender is introduced.

## Existing implementation

`HostToHostApiPassword.reset` completes the credential operation/local finalization, then invokes `notifyResetSuccess` through the existing successful-execution path. The successful-request replay path does not publish a second success event. Failed execution does not reach the success call. Preserve these gates.

| Item | Existing value retained |
|---|---|
| Activity | `com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset` |
| Event | `HTH_API_PASSWORD_RESET_SUCCESS` |
| Email recipient | Target API profile user email |
| SMS recipient | Target API profile user mobile |
| Recipient derivation | Existing explicit `NotificationDetail` entries, `EXTERNAL` routing |
| Approver / company | No additional recipient |
| Original Email IDs | `HTH_API_PWD_RESET_USER_EMAIL_<locale>` |
| Original SMS IDs | `HTH_API_PWD_RESET_USER_SMS_<locale>` |
| Locales | `en`, `zh-Hant`, `zh-Hans-CN` |

The IDs above are created by the original repository deployment scripts. The update resolves the actual installed IDs from `DIGX_EP_EVT_REC_B`, including `UNSECURE_MSG_TMPL_ID`; it does not assume those names are unchanged in UAT.

## Template content

- Email: use Feature 8's **Email Subject** and **Email Message Template** raw cells, preserving HTML and wording. Use the reviewed bilingual email for all three locale bindings.
- SMS: use Feature 8's reviewed EN/TC/SC message for each locale. Exclude the historical `<Current Situation>` text. Keep the existing SMS subject field because no new SMS subject is specified.
- The reviewed success text contains no customer-name or credential placeholders. No new masking/metadata mapping is needed; existing BCO masking is retained for paths that use names.
- Retain existing events, languages, subscriber configuration and object status. No Code approval template or ordinary BCO template is changed.

**Reviewed wording to note:** Feature 8 still uses "Setup Success" / "set up" even for reset. The SQL preserves that reviewed wording; it does not silently replace it with "Reset". The reviewed email's English hotline is 2211 1056 and Chinese hotline is 2211 1321. Both are preserved.

## SQL implementation

The two success stories share one atomic script: `consulting/db/branch_change_history/20261109_BCOH2H-1332_1333/1_Update_Password_Success_Templates.sql`.

1. Read existing template bindings for the exact setup/reset activities and events, action A, Email/SMS and three locales.
2. Validate all required bindings and template rows before any write. Stop on missing rows, wrong channel or IDs used by unrelated events/actions/channels.
3. If the same ID requires different text across routes/locales, stop instead of overwriting one language or event with another.
4. Update only `DIGX_EP_MSG_TMPL_B` subject/body and change metadata in `OBDX_BU`. Keep all routes and SMS subjects. Existing rows in other units are untouched.
5. Compare CLOB subject/body content with `DBMS_LOB.COMPARE`, including the SQL update predicate. Direct SQL `<>` on a CLOB subject fails at compilation; do not truncate the subject to work around this. Roll back the block on error. Re-running unchanged content performs no content update. No automatic commit.

No new tables, template IDs, events, recipient rows, source mappings, batch job or shared-package changes.

## Validation

- `verify_hth_notifications.py`: existing setup/reset target-only routing, missing contacts and notification failure containment; inspect successful-call/replay boundaries.
- `verify_hth_success_template_update.py`: exact reviewed raw cells; actual SQL binding/update predicates with renamed and alternate IDs, unrelated references and rerun fixtures.
- DBeaver parser regression: the previous local procedure was split from the main block. The revised single anonymous block is parsed as one complete statement; its route cursor covers all 12 event/channel/locale combinations.
- CLOB regression: the complete block compiles and runs in a disposable Oracle Free 23.26.3 database with CLOB subject/body columns. All 12 route combinations plus one alternate template update successfully; a second run updates zero rows. Null/empty/changed/long subjects are repaired with matching bodies; SMS subjects, other units and unrelated BCO rows are preserved. The old predicate reproduces the CLOB comparison compile failure (ORA-22848 in this Oracle version; UAT reports ORA-00932).
- No production password Java changes. The Python relational fixtures alone do not validate Oracle types. The disposable Oracle test is separate from UAT and does not establish actual notification delivery.
- UAT: successful reset sends the configured email/SMS only to the target profile; failed reset generates no success notice; repeat-request handling does not generate another success event. Verify all three locales and content.

## Deployment and rollback

1. Run Q1 of `2_Verify_Password_Success_Templates.sql` and export current raw template rows as a backup. Q2 should return zero missing/invalid primary bindings.
2. Existing environments: disable auto-commit and execute the whole new update block. In DBeaver, stop the failed script, open the revised file, select from `DECLARE` through the final `END;`, and use **Execute SQL Statement (Ctrl+Enter)**. Do not execute only the cursor position or a partial selection. For SQL*Plus/SQLcl, enter `/` on a separate line after the block. New environments: first install the original setup/reset notification scripts, then this delta. Re-running the older scripts later can restore old wording, so always apply the delta last.
3. Re-run verification and inspect subject/body, then COMMIT. Follow the existing environment process to refresh notification configuration caches if necessary.
4. Reconcile a new transaction with Q4. Record event, recipient, channel, response status and rendered content; do not infer delivery from a total row count alone.
5. Before commit, use ROLLBACK to abandon the change. After commit, restore only the backed-up template subject/body values for the same IDs/unit through the normal deployment process.

## Existing Tech Design sub-task content

Summary: **BCOH2H-1332 Tech Design — API password reset completion notification**

Attach this document to the existing Tech Design sub-task. Review the existing successful trigger, target-only recipients, template mapping and the reviewed Reset wording note. No new Jira sub-task is required.
