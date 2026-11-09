# BCOH2H-1216 — Reuse BCO User Access templates

Date: 2026-11-09. Source base: `fdad029b41eb5bbedcf586f4f9718234545c0ae9` plus the current pending four-story delivery and this change.

## Scope

HTH User Accounts & Service Access notifications now directly reference the installed BCO templates, including configured alternate template IDs. The SQL reads the real BCO event bindings by recipient category, channel and locale. It does not rely on manually supplied template names.

| HTH recipient | BCO template category | Channel |
| --- | --- | --- |
| Target user and final approver | CORPORATE / USER | EMAIL and SMS |
| Company | PARTY / CUSTOMER | EMAIL only |

Existing HTH events, approval timing, recipients, deduplication and asynchronous delivery remain. The final approver is still included in 1216. With complete distinct contacts, the expected existing flow is three emails and two SMS messages.

BCO template text, attributes, recipient rows, event/action configuration and BCO source mappings are not changed. HTH activity-specific source mappings are added for the shared templates. Old HTH1216 templates and metadata remain available for pending notifications and rollback.

No Java file is changed or copied. If the current 1216 Java is already deployed, no new JAR is needed for this revision. First-time 1216 installations still need that existing implementation.

## Apply

SQL directory: `hhfi/consulting/db/branch_change_history/20260917_BCOH2H-1216/`.

1. Use the OBDX configuration schema. Disable client auto-commit and use a connection with no unrelated pending changes.
2. Run `1216_Verify_BCO_Template_Reuse.sql` first. Export Q1 and both Q4 result sets as the original configuration backup.
3. Run the revised `1216_HTH_User_Access_Notification.sql` as one complete PL/SQL block. It supports migration and first installation, has no automatic COMMIT, and rolls back its own changes on error. In SQL*Plus/SQLcl, add `/` on a separate line after the block.
4. In the same connection, re-run verification. Inspect Q1 IDs/raw content; Q2 and Q3 must return zero rows. With three configured locales the normal HTH route count is 18. Then COMMIT and refresh notification configuration through the existing environment process.
5. Do not run an older 1216 script afterwards: it recreates private templates. The file in this package replaces the old canonical script.
6. Complete a Link and an Edit final approval. Check user/company template selection, placeholders, recipients, alert/MNG records and actual inbox/handset receipt; regress ordinary BCO access notifications.

Missing, inactive, conditional or conflicting BCO configuration causes an error rather than an arbitrary template selection. The existing HTH action must remain active, unconditional and asynchronous. Later changes to shared BCO text also apply to HTH; changes to template IDs or placeholders need mapping review.

## Validation

- `verify_hth_1216_sql.py`: actual SQL selectors, scoped deletes, recipient inserts and verification queries pass local relational fixtures. Includes role/language/channel separation, alternate IDs, rejected ambiguities, reruns and BCO/legacy preservation.
- `verify_hth_1216.py`: existing Java notification regression passes, including approval gates, save ordering, recipient deduplication, event selection, serialization, SDK recipient resolution and SMS routing.
- Oracle PL/SQL execution, live rendering/transactions and delivery still require UAT; the fixture is not an Oracle execution.

The test file must be run from the full source checkout, not from this partial package alone.

## Package and rollback

Four changed/new source files are mirrored under `hhfi/`, with their original repository-relative paths. `SHA256SUMS` covers all four; each copy was compared byte-for-byte with source.

The earlier `bco-notification-20261109/` delivery is separate and unchanged. All 12 of its source and packaged files were checked against its manifest. This directory does not replace that package or write to mcdp's original consulting tree.

Before COMMIT, use ROLLBACK. After COMMIT, restore the backed-up HTH action-A recipient bindings and refresh configuration; old private templates/mappings were retained. Do not delete or restore BCO shared templates for this rollback.

Tech Design: `hhfi/story/notification/BCOH2H-1216-TECHNICAL-DESIGN.md`, for the existing Tech Design sub-task. No new Jira task is created. Source changes and this package are committed separately from the preceding four-story delivery.
