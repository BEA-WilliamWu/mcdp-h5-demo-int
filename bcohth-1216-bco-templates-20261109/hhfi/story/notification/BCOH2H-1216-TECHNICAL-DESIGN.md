# BCOH2H-1216 — User Accounts & Service Access Notification

Tech Design sub-task · Updated 2026-11-09

Base: current source at `fdad029b41eb5bbedcf586f4f9718234545c0ae9`, including the pending 865 / 1334 / 1333 / 1332 delivery. This change preserves that delivery.

## Change in this revision

**HTH references the existing BCO template IDs directly. It no longer creates separate `HTH1216_*` copies of BCO email/SMS text.**

This is a configuration change. The current HTH Java, approval checks, recipients, deduplication and asynchronous delivery remain unchanged. Ordinary BCO retains its existing event, recipients, template content and source mappings.

| Item | Previous 1216 implementation | This revision |
| --- | --- | --- |
| Email/SMS templates | Copy BCO text into private `HTH1216_*` templates | Reference the actual BCO primary and alternate template IDs |
| Template selection | BCO recipient category + channel + language | Same selection; do not assume an ID from an Excel file |
| Placeholder values | HTH activity mappings for private templates | HTH activity mappings for the shared BCO templates |
| Java and recipients | Existing 1216 implementation | No change |
| Old HTH template rows | Used by existing bindings | Retained; current action-A routes switch to BCO IDs |

## Trigger and recipients retained

Link (`HostToHostUserAccess.submit`) and Edit (`HostToHostUserAccess.edit`) register notifications after final approval and successful access persistence. Maker submission, validation, intermediate approval, rejection, failed persistence and independent Delete do not enter this notification flow.

| Recipient | Email | SMS | Template category to reuse |
| --- | --- | --- | --- |
| Target HTH user | Target user's email | Target user's mobile and country code | BCO `CORPORATE / USER` |
| Final approver | Last approver's email | Last approver's mobile and country code | BCO `CORPORATE / USER` |
| Company | Target user's company `officeEmailId` | None | BCO `PARTY / CUSTOMER`, EMAIL only |

The final approver comes from the approved transaction's final `signedBy` entry, after checking the service ID; it is not inferred from the session user. Company contact comes from the target user's `partyId`, even when access is linked to another company's account.

Deduplication remains per notification preparation: target user, then final approver, then company. Email comparison ignores surrounding spaces and case; SMS comparison uses normalized country code and mobile number. If company email matches a preceding recipient, only that preceding user-template email is registered. There is no new cross-retry deduplication store.

With distinct, complete contacts, the existing flow registers **three emails and two SMS messages**. This revision does not remove the final approver from 1216; it changes templates only.

## Template and event mapping

Read the installed BCO configuration from activity `com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update`, event `USER_ACCOUNT_ACCESS_UPDATE`, action `A`, template unit `OBDX_BU`.

| HTH event | Actual recipient | BCO source selector | Channels |
| --- | --- | --- | --- |
| `HTH_USER_ACCOUNT_ACCESS_UPDATE` | Target user and final approver | `SUBSCRIBER_TYPE=CORPORATE`, `SUBSCRIBER_VALUE=USER` | EMAIL, SMS |
| `HTH_USER_ACCOUNT_ACCESS_COMPANY` | Company | `SUBSCRIBER_TYPE=PARTY`, `SUBSCRIBER_VALUE=CUSTOMER` | EMAIL |

For each configured locale, bind the source `COD_MSG_TMPL_ID` and `UNSECURE_MSG_TMPL_ID`. English is required; other locales follow the installed BCO routes. User/company templates are selected separately, even when both exist for the same channel/language. Conflicting IDs within the same category/channel/language stop the script; it never selects an arbitrary first row.

The HTH events and explicit `EXTERNAL / USER` routes remain. Sharing template IDs does not mean registering the original BCO event or allowing BCO subscriber lookup to replace HTH addresses.

The existing `UserManagementActivityLogDTO` supplies the target user name, company name and date. The framework's `fetchMessageDataSource` query selects by **template ID and activity ID**. A shared template can therefore keep its BCO mapping and have separate HTH `submit` / `edit` mappings. BCO continues to resolve its original activity's values.

## Database changes and BCO isolation

| Table | Scope of this revision |
| --- | --- |
| `DIGX_EP_EVT_REC_B` | Replace action-A bindings for the two HTH events under HTH `submit` / `edit` only; reference BCO template IDs |
| `DIGX_EP_MSG_SRC_B` | Add/replace shared-template mappings for HTH activities in `OBDX_BU`; preserve BCO and old private-template mappings |
| `DIGX_MD_SERVICE_ATTR` | Add/update HTH-owned `.1216.bco.` metadata using BCO's existing field definitions and source types |
| `DIGX_EP_MSG_TMPL_B`, `DIGX_EP_MSG_ATTR_B` | Read only; no template text or attribute changes |
| HTH activity/event/action tables | Preserve existing configuration; initialize missing 1216 records for a first installation |

Existing HTH actions must remain active, unconditional and asynchronous (`FLG_TRANSACTIONAL=N`). The script stops on incompatible configuration rather than overwriting it. It also rejects HTH events in dispatcher recipient/country override lists.

There is no change to BCO recipient rows, source mappings, events or actions; no Java, batch, scheduler, dispatcher, DTO, common-package, frontend or business-table change.

Old `HTH1216_*` templates and metadata are retained so this migration does not remove dependencies that previously queued notifications may still use. Repeating the script with the same source configuration does not add duplicate routes or source mappings.

Later edits to shared BCO subject/body text also apply to HTH after normal cache refresh. New placeholders or changed template IDs require HTH mapping verification. A later template-ID change that collides with an existing HTH metadata reference causes a safe stop and needs a targeted mapping review.

## Files and code evidence

| File | Purpose |
| --- | --- |
| [1216_HTH_User_Access_Notification.sql](../../consulting/db/branch_change_history/20260917_BCOH2H-1216/1216_HTH_User_Access_Notification.sql) | Updated installation/migration block; replaces private-template creation with direct BCO bindings |
| [1216_Verify_BCO_Template_Reuse.sql](../../consulting/db/branch_change_history/20260917_BCOH2H-1216/1216_Verify_BCO_Template_Reuse.sql) | Read-only inventory, backup and binding/source verification |
| [verify_hth_1216_sql.py](../../devtools/backend-compile/tests/verify_hth_1216_sql.py) | Local source-selection, isolation, alternate-template and rerun regression |

Unchanged evidence: [HthUserAccessNotification.java](../../consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HthUserAccessNotification.java), [HostToHostUserAccess.java](../../consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostUserAccess.java), and [QueriesForEventGeneration.orm.xml](../../consulting/config_core/orm/eclipselink/mappings/alert/eventGeneration/QueriesForEventGeneration.orm.xml).

## Deployment and verification

1. Use the OBDX configuration schema, **disable client auto-commit**, and use a connection with no unrelated pending work. Export Q1 and both Q4 result sets from the verification file before changing anything.
2. Run the complete revised `DECLARE ... END;` block. In SQL*Plus/SQLcl, use `/` on a separate line after the block. It has no automatic COMMIT and rolls back its changes on error. Apply the current file, not an older script that recreates private templates.
3. In the same connection, run verification again: Q1 lists actual BCO/HTH IDs and raw text; **Q2 and Q3 must return zero rows**. With three source locales, the normal HTH action-A route count is 18 (two activities × three role/channel combinations × three locales).
4. COMMIT after checking; refresh notification configuration through the environment's normal process. If current 1216 Java is already deployed, this revision needs **no new JAR**. A first-time 1216 installation still requires the existing HTH notification implementation.
5. Complete one Link and one Edit approval with distinct contacts. Verify three emails/two SMS, correct user/company template selection, actual placeholder rendering, alert/MNG result and inbox/handset delivery. Recheck ordinary BCO access notifications.

Before COMMIT, use ROLLBACK to discard the migration. After COMMIT, restore only the backed-up HTH action-A recipient bindings to their old template IDs and refresh configuration. The old templates/mappings are retained. Do not delete or restore shared BCO templates as part of this rollback.

## Validation evidence and limits

- Local SQL fixtures execute the actual source selectors, scoped cleanup, recipient inserts and verification queries. They cover three locales, user/company separation, alternate IDs, conflicting/missing sources, repeat execution, and preservation of BCO/shared/legacy rows.
- `verify_hth_1216.py` passes against the current helper and service methods: final-approval gates, save ordering, unchanged recipients, cross-role deduplication, event selection, DTO serialization, SDK recipient resolution and SMS dispatcher routing.
- Local fixtures do not execute the Oracle PL/SQL record-copy block and do not contact MNG. Full Oracle execution, live approval/transaction behavior, template rendering and actual delivery remain UAT checks.

This document is the deliverable for the existing Tech Design sub-task; no new Jira sub-task is needed.
