# BCOH2H-880 — HTH API Password Code Generation Notification

## 1. Scope

After CM User Create / Edit completes final approval and successfully activates a new API Password Code, notify the target API user and company by email. This covers SETUP and RESET Codes. Code generation sends no SMS. Independent User Create/Edit and password setup/reset notifications retain their own rules.

Sources: BCOH2H-880 story PDF and Feature 6 of `2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx` in this directory.

**Revision agreed on 4 November 2026:** retain the original approval + 24-hour expiry; remove the new `HTH_880_CODE_ACTIVATED` thread marker, APPROVE callback and calendar-expiry utility. Use the existing BCO activity/event registration flow. No new batch, queue, shared dispatcher or database table.

## 2. Trigger and processing

```mermaid
sequenceDiagram
    participant CM as CM final approval
    participant User as User Create / Edit
    participant Code as HTH Code service
    participant DB as Existing database transaction
    participant Alert as BCO notification framework
    CM->>User: Execute approved business operation
    alt User operation succeeds
        User->>Code: Activate the submitted Code (owner checked)
        Code->>DB: Lock Code; change PENDING to ACTIVE
        Code->>DB: Set expiry to database time + 24 hours
        Code->>DB: Refresh entity after native SQL
        Code->>Alert: Register target/company email events
        Alert->>DB: Save activity through existing framework
        alt Approval transaction commits
            CM->>DB: Commit
            Alert->>DB: Read saved activity for dispatch
            Alert->>Alert: Process configured email actions
        else Approval transaction rolls back
            CM->>DB: Roll back activation and registration
        end
    else User operation fails or Code already activated
        Note over Code,Alert: No new Code approval event
    end
```

- The existing approval framework invokes the business service at final approval. `UserExtensionData.create/update` retains its HTH activation call. Update activation remains inside the successful user-update branch.
- Only a new PENDING → ACTIVE database transition registers the Code events. Re-entry for ACTIVE / USED / IN_PROGRESS / UNKNOWN does not reactivate the Code or register another event.
- Registration is inside the existing activation transaction, before its lease is closed. The refreshed Code entity supplies the database expiry; no stale PENDING state or application-clock estimate is used.
- `HthApiPasswordApprovalLifecycle` returns to its previous **rejection cleanup only** implementation. The separate 2110 contact-notification implementation is not changed by this revision.

### Existing framework boundary

The repository SDK saves activity data through `ActivityDataRepository.insert`. For the existing Code actions, `FLG_TRANSACTIONAL='N'` selects the framework's background dispatch path, which loads the saved activity before executing actions. Keep this existing configuration; changing it to transactional execution can send an external message before business commit.

No HTH-specific in-memory handoff remains between approval commit and notification registration. Code state and expiry remain in the database. This does **not** introduce a new durable outbox or guarantee exactly-once delivery: registration errors, framework queue/poller recovery, database/JTA enlistment and server-restart behavior remain those of the existing BCO notification framework. Verify these boundaries on WebLogic/UAT; local transaction tests do not prove the deployed queue behavior. Existing per-recipient error isolation is retained.

## 3. Recipients, templates and deduplication

| Recipient | Address source | Event ID | Template ID |
|---|---|---|---|
| Target API user | Existing owner-checked OBDX user lookup for Code party/user | `HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT` | `HTH_API_PWD_CODE_USER_EMAIL_<locale>` |
| Company | Existing party preferences `officeEmail` | `HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT` | `HTH_API_PWD_CODE_COMPANY_EMAIL_<locale>` |

Activity: `com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval`.

Both routes use EXTERNAL / USER with explicit email addresses. The target user is processed first, then company. Trim addresses and compare case-insensitively across these two recipients; a shared mailbox uses the target-user route. No maker or approver is added solely because of that role. This deduplication does not span separate User Create/Edit events or framework retries. Missing email does not trigger Code SMS fallback.

The six existing templates (two recipient roles × en / zh-Hant / zh-Hans-CN) use the exact reviewed bilingual subject and raw HTML. No new translation or secret is added.

## 4. Expiry and template discrepancy

**Runtime rule remains the original rule: approval time + 24 hours**, calculated by Oracle with `SYSTIMESTAMP + NUMTODSINTERVAL(24, 'HOUR')`. Both SETUP and RESET use it. Example: approval on 4 November at 10:00 HKT → expiry on 5 November at 10:00 HKT. Restore the response's original fixed `expiryHours=24` contract. Existing historical rows are not rewritten.

**The reviewed v81 template still says “23:59:59 (UTC+8) on the following day”. This conflicts with the retained 24-hour rule.** The template SQL preserves reviewed content; this revision does not silently rewrite approved wording. Confirm corrected expiry wording before releasing this template to customers. Do not treat the current template as evidence of a changed runtime expiry requirement. The story AC does not itself specify a new expiry duration.

The reviewed hotline and setup-only wording also remain verbatim; this revision is confined to the requested implementation simplification.

## 5. Code changes and BCO impact

| File / area | This revision | Impact |
|---|---|---|
| `HostToHostApiPassword` | Remove marker and public post-commit bridge; register through the existing framework inside activation; refresh native-SQL state | HTH Code only; retain recipient and dedup changes |
| `HthApiPasswordApprovalLifecycle` | Remove 880 APPROVE handling/reflection; restore earlier rejection hook | No new common approval behavior |
| Code repository / interface / local adapter | Restore original integer expiry-hours parameter and Oracle interval SQL | HTH Code table only; earlier expiry implementation restored |
| `HthApiPasswordCodeExpiry` | Delete the new next-calendar-day utility | Removes the unneeded rule change |
| `UserExtensionData.update` | Retain the prior success gate; update obsolete comment | Ordinary BCO notification logic is unchanged by this revision |
| 880 SQL | Keep reviewed templates and recipient routes; add configuration check / verification query | Only the two existing Code approval events |

No batch or shared notification package changes. The existing BCO framework may internally use thread context; this revision removes only the custom 880 notification handoff.

## 6. Deployment

1. Build/deploy hosttohost, sms and approval modules together. The repository interface and implementation must match. Remove the deleted expiry utility from clean build outputs.
2. Run `consulting/db/branch_change_history/20261103_BCOH2H-880/1_HTH_Code_Approval_Notification.sql` as one block, after the existing HTH baseline scripts; COMMIT after success. It checks the two existing non-transactional actions and six templates/routes before updating. Re-execution adds no duplicate rows.
3. Run `2_HTH_Code_Approval_Verification.sql`; refresh configuration caches using the existing deployment procedure. Resolve the template discrepancy in section 4 before customer release.
4. Delivery is isolated under `mcdp/bcohth-880/hhfi/consulting/...`, with design/tests at their matching relative paths below `hhfi`. Do not copy into mcdp's root `consulting`. Apply against the matching full source baseline, not as a standalone project.

## 7. Verification and limits

| Check | What it establishes |
|---|---|
| Java 8 target compilation | Current production classes compile against repository SDK/dependencies |
| Existing rejection-hook tests | Reject cleanup, server snapshot ownership and ordinary BCO exclusion remain unchanged |
| Notification routing tests | Target/company-only Code emails; normalized dedup; missing contacts; failure isolation; existing setup/reset routes retained |
| EclipseLink/H2 transaction tests | Production activation methods and SQL, with equivalent H2 interval syntax; cached refresh, 24-hour expiry, commit/rollback and replay. Notification registration is a test-table fixture in the same real ORM transaction; MNG and the SDK event processor are not executed |
| DOCX/SQL contract checks | Exact reviewed subject/raw HTML, scoped six-template/two-event delta, no Code SMS, no custom 880 marker/bridge |

All five local checks listed above passed on 4 November 2026.

UAT must verify Create and Edit/regeneration with final and intermediate approvals, rollback after activation, registration failures, replay, shared mailbox, both recipient roles/all locales, no Code SMS, and unchanged independent BCO notifications. Check framework recovery on server restart separately. Oracle execution and actual MNG delivery are not local test results.

Run with a Java installation supporting `--release 8` and `H2_JAR` pointing to H2 1.4.200:

```sh
python3 devtools/backend-compile/tests/verify_hth_audit_compile.py
python3 devtools/backend-compile/tests/verify_hth_code_approval.py
python3 devtools/backend-compile/tests/verify_hth_notifications.py
python3 devtools/backend-compile/tests/verify_hth_password_transactions.py
python3 devtools/backend-compile/tests/verify_hth_880.py
```

For investigation, use the two Code event IDs, Code `TRANSACTION_ID` and the verification SQL. Existing failure logs include `Unable to publish HTH notification event` and `Unable to resolve HTH notification`; the removed `HTH_880 stage=POST_COMMIT_NOTIFICATION_FAILED` / `NOTIFY_SKIPPED` messages no longer apply.
