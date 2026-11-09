# Notification delivery for BCOH2H-865 1334 1333 and 1332

Date: 2026-11-09. Source base: `fdad029b41eb5bbedcf586f4f9718234545c0ae9` plus the changes in this overlay.

## Package scope

- **865 / 1334:** remove the extra target-specific SMS copied to the HTH final approver. Keep existing contact-update emails and the BCO generic user-edit notification.
- **1333 / 1332:** update existing setup/reset success templates. Keep current password success Java and recipients.
- Four separate English Tech Design Markdown documents are under `hhfi/story/notification/`, one for each existing Jira Tech Design sub-task. No new Jira sub-task was created.
- User Access 1216, batch jobs, common production packages, BCO templates and Code approval 880 are unchanged.

## Apply the code

Copy the two Java files under `hhfi/consulting/middleware/` to the SAME paths in the current source checkout. Build/deploy the SMS module together. This overlay is based on the current merged source, including existing 880/2110 work; do not combine it with older story-specific Java copies.

Use current repository DTO/dependency sources when building. The verification script explicitly compiles the current UserExtensionDataDTO to avoid stale local class files; this delivery does not change that DTO.

## Original templates and database update

| Story | Existing event | Template IDs created by original deployment SQL |
|---|---|---|
| 1333 | `HTH_API_PASSWORD_SETUP_SUCCESS` | `HTH_API_PWD_SETUP_USER_EMAIL_<locale>` / `HTH_API_PWD_SETUP_USER_SMS_<locale>` |
| 1332 | `HTH_API_PASSWORD_RESET_SUCCESS` | `HTH_API_PWD_RESET_USER_EMAIL_<locale>` / `HTH_API_PWD_RESET_USER_SMS_<locale>` |

Locales: `en`, `zh-Hant`, `zh-Hans-CN`. Actual installed IDs can differ. The new update follows the event bindings, including alternate template IDs, rather than relying on these names.

1. In `hhfi/consulting/db/branch_change_history/20261109_BCOH2H-1332_1333/`, run **2_Verify_Password_Success_Templates.sql** first. Export Q1 as the original subject/body backup. Q2 should return no missing/invalid primary bindings. Q3 lists the BCO templates reused by 865/1334.
2. Disable auto-commit. Run **1_Update_Password_Success_Templates.sql** as one complete PL/SQL block in the OBDX configuration schema using UTF-8. In DBeaver, stop the failed script, open this revised file, select from `DECLARE` through the final `END;`, and use **Execute SQL Statement (Ctrl+Enter)**. For SQL*Plus/SQLcl, enter `/` on a separate line after the block. It changes existing setup/reset template content only. It does not create missing configuration or update routes. On missing/conflicting configuration, the whole block stops/rolls back with the actual event/ID in its error.
3. Re-run verification and inspect raw subjects/bodies, then COMMIT. Refresh notification configuration through the existing environment process if needed. A repeat execution of unchanged content does not update rows again.
4. New environments must first install the original setup/reset notification configuration. Apply this delta AFTER old notification/template scripts, as those scripts contain superseded wording.
5. Use Q4 around a fresh test transaction to verify recipients, channels, content and response status. Check actual inbox/handset receipt separately.

**Reviewed text:** v81 Feature 8 uses “Setup Success” / “set up” for reset as well. The SQL preserves that text. The English email hotline is 2211 1056; Chinese is 2211 1321. These are not silently corrected. Email uses the reviewed bilingual body for all three locales; SMS uses the reviewed EN/TC/SC text, excluding historical “Current Situation” text.

## Validation completed

- Production notification-method regression: ordinary BCO and HTH email-only/mobile-only/combined/no-op; final approver email; old/new contacts; existing PIN gates; no extra approver contact SMS.
- Current modified Java classes compile against repository dependencies for Java 8.
- Existing setup/reset notification routing regression passes.
- Existing approval lifecycle regression passes.
- DBeaver parsing fix: the previous local procedure caused the installed Oracle script parser to split the block into two statements, producing an end-of-file error. The revised file has no local procedure and is parsed as one complete statement. All 12 event/channel/locale combinations remain covered.
- SQL raw text matches v81 template cells. Actual selection/update predicates pass local relational fixtures for renamed/alternate IDs, unrelated references, reruns and other business units.

Oracle PL/SQL execution and real approval/MNG delivery still require UAT. Local relational fixtures are not an Oracle database run.

## Checksums and rollback

`SHA256SUMS` covers every file under `hhfi/`; all copies were compared byte-for-byte with source. Test scripts must run in the full source checkout with repository dependencies, not in this partial overlay alone.

Before database COMMIT, ROLLBACK discards the update. After COMMIT, restore only the backed-up template subject/body values for the same IDs and unit. Java rollback uses the prior two files from the source base above, deployed together.
