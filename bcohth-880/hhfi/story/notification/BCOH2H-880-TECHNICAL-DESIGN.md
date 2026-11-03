# BCOH2H-880 — HTH API Password Code Generation Notification

## 1. Scope and AC

Creation or regeneration of an API Password Code from CM User Create / Edit sends email only after successful final approval. This covers SETUP and RESET Codes. It does not change successful password setup/reset notifications (1204/1205), 851 contact notifications, 1216 access notifications, BM notifications, batch or shared dispatchers.

| AC | Implementation |
|---|---|
| AC1 | After successful final approval commits, send email to the target API Profile User and company officeEmail. No maker/intermediate/final approver recipient solely because of that role. |
| AC2 | No SMS event/destination for Code generation. Existing User Create/Edit SMS for their separate business events remain unchanged. |
| AC3 | No success Code email for failure, rejection, pending/intermediate approval or transaction rollback. |
| AC4 | Use Feature 6 of `2.Customer Onboarding CM - Notification V1.0-v81-20261103_105653.docx` as the exact subject/HTML source. |

Sources: the BCOH2H-880 PDF (updated 2 Nov 2026) and the reviewed v81 notification DOCX in this directory.

## 2. Trigger and transaction boundary

```mermaid
sequenceDiagram
    participant CM as CM approval
    participant User as User Create / Edit
    participant HTH as HTH Code service
    participant Hook as Existing HTH postPerformAction extension
    participant Alert as Existing Alert / MNG
    CM->>User: Execute final approved transaction
    User->>HTH: Activate the submitted PENDING Code
    HTH->>HTH: Join approval transaction; set next-day expiry
    HTH->>HTH: Record request-local Code ID + transaction ID
    alt Successful commit and saved APPROVED result
        CM->>Hook: postPerformAction (SDK calls after commit)
        Hook->>Hook: Consume marker; verify server snapshot and response
        Hook->>HTH: Verify committed ACTIVE Code, owner and transaction
        HTH->>Alert: User email + company email (deduplicated)
    else Failure / rollback / intermediate approval
        Note over HTH,Alert: No Code success email
    end
```

The existing `HthApiPasswordApprovalLifecycle` is extended; no new common approval hook is added. It already runs from `CZTransactionExt.postPerformAction`. The SDK bytecode is checked by the regression test to establish that the callback follows commit. In User Edit, activation is moved after the existing successful user-update check. Activation itself no longer sends email.

Only a new PENDING → ACTIVE transition sets the marker. The callback consumes it before validation, matches Code ID and transaction ID to the server-owned snapshot, checks successful response, saved final APPROVED status and business processing SUCCESS, then refreshes the Code entity from the database and verifies its committed owner/state. The refresh is necessary because native activation SQL does not update a cached PENDING entity. Replayed activation does not set another marker. BCO users and unrelated tasks remain on their existing paths.

This is a request-local post-commit callback, not a durable outbox. A process failure between commit and notification registration can lose the notification; this story adds no recovery batch or new retry mechanism. Notification delivery failure must not turn a committed approval into a failed transaction. Oracle/WebLogic integration and one-man-band approval require UAT verification.

## 3. Recipients and deduplication

| Destination | Source | Event | Template ID |
|---|---|---|---|
| Target API user email | Existing owner-checked OBDX user lookup for the Code party/user | `HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT` | `HTH_API_PWD_CODE_USER_EMAIL_<locale>` |
| Company officeEmail | Existing party preferences adapter | `HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT` | `HTH_API_PWD_CODE_COMPANY_EMAIL_<locale>` |

Keep the existing activity `com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval`. Both routes use EXTERNAL / USER with explicit email destinations. Remove signedBy recipient traversal. Trim addresses and compare case-insensitively across the target/company recipients in this one Code notification. Target user takes precedence for a shared mailbox. This does not deduplicate independent User Create/Edit events. Missing email has no SMS fallback; each recipient failure is isolated.

## 4. Template and expiry

The six existing templates (two roles × en / zh-Hant / zh-Hans-CN) receive the same reviewed English + Traditional Chinese subject/body. The DOCX supplies one bilingual body; no new translation is invented. HTML, wording and line breaks are retained. The template contains no actual Code, password or secret. Existing unused placeholder metadata is left intact; no new placeholder is required.

To match the approved wording, a newly approved Code expires at **23:59:59 on the following Hong Kong calendar day**, for SETUP and RESET. Example: approval on 3 November at 10:00 HKT → expiry 4 November 23:59:59 HKT. Calculate once using `Asia/Hong_Kong` and bind that timestamp to the existing EXPIRY_TIME field. Do not backfill historical ACTIVE/USED/EXPIRED rows. The response's optional expiryHours becomes rounded-up remaining hours for a Code with an actual expiry, rather than a fixed 24-hour claim.

The reviewed wording is implemented verbatim, including English hotline 2211 1056 versus Chinese 2211 1321, setup-only wording for this shared Code notification and existing H2H/HTH UI-label differences. These are document consistency observations, not silent changes to the agreed text or to BCO screens.

## 5. Change and BCO impact

| Area | Change | BCO impact |
|---|---|---|
| HostToHostApiPassword | Defer email; validate committed Code; target/company recipients only | HTH service only |
| HthApiPasswordApprovalLifecycle | Add APPROVE handling to existing HTH-only extension | Fail-closed gates for HTH user create/edit only; rejection cleanup retained |
| UserExtensionData.update | Move existing HTH activation into successful-update block | No change to BCO notification templates/recipients or general success condition |
| HTH Code repository + adapter + interface | Bind explicit expiry timestamp | HTH Code table only |
| HthApiPasswordCodeExpiry | Hong Kong calendar-day expiry calculation | New HTH utility |
| 880 configuration delta | Six email templates and their two event routes; remove non-email routes for those events | No BCO/setup/reset/batch configuration changed |

## 6. Deployment and synchronization

1. Build/deploy the changed hosttohost, sms and approval modules together using the normal build; repository interface and implementation must be deployed together.
2. Run `consulting/db/branch_change_history/20261103_BCOH2H-880/1_HTH_Code_Approval_Notification.sql` as one block in the OBDX configuration schema, then COMMIT. It validates the six existing templates/routes and active actions before changing anything. On error it rolls back this block to its savepoint. Re-execution does not add duplicate rows.
3. Run the accompanying read-only verification SQL. Use the existing deployment procedure to refresh notification configuration/template caches.
4. Run this delta **after** older HTH password template scripts. Re-running old scripts afterwards restores superseded content.
5. mcdp delivery directory: `bcohth-880/hhfi/consulting/...`; copy only changed consulting files with identical relative paths. Keep this design and regression tools at their original relative paths under `bcohth-880/hhfi/story/notification` and `bcohth-880/hhfi/devtools/backend-compile/tests`. Do not copy into mcdp's root `consulting`.

## 7. Verification

Automated tests cover target/company-only emails, shared mailbox dedup, missing contacts, no SMS fallback, isolation of delivery failures, setup/reset notification preservation, final approval gates, error-type response despite SUCCESSFUL result, intermediate approval, rollback/no marker, replay, owner binding, rejection regression and ordinary BCO exclusion. The real EclipseLink/H2 test also exercises a cached PENDING entity through native activation, commit and notification, plus rollback and rejected/used Code cases. Expiry tests cover Hong Kong midnight, UTC date boundaries, month/year transitions and leap day. SQL tests compare the exact subject/body with the reviewed DOCX and restrict updates to the six Code templates/two events.

Before UAT sign-off, execute real Create and Edit/regeneration approvals with multiple approvers; verify only user/company receive the Code emails, no Code SMS, no email on failure/rejection/partial approval, correct expiry, both email roles and all three locales, shared mailbox once, and unchanged independent BCO User Create/Edit notifications. Local compile/tests do not prove MNG inbox delivery or Oracle/WebLogic deployment behavior.

## 8. Diagnostic evidence

Use the verification SQL and existing Code event IDs to correlate the approval test. New failure diagnostics use `HTH_880 stage=POST_COMMIT_NOTIFICATION_FAILED` or `HTH_880 stage=NOTIFY_SKIPPED`; they contain stage/reason/exception type only, not Code/password/email content. Check approval transaction status, Code TRANSACTION_ID and newly assigned EXPIRY_TIME before inspecting dispatch history.

### Local verification results

- Java 8 API compilation of changed production classes and current module dependencies: passed (repository SDK/JARs; not a server deployment build).
- `verify_hth_code_approval.py`: passed; SDK bytecode confirms business execution → commit → postPerformAction → session close; actual SDK DTO tests exercise all final-approval/failure gates and rejection regression.
- `verify_hth_notifications.py`: passed; target/company-only Code email, normalized-mailbox dedup, independent role failures, missing contacts/no SMS fallback; existing password setup/reset channels preserved.
- `verify_hth_password_transactions.py`: passed with EclipseLink/OBDX ORM and H2 1.4.200; real transaction rollback, native activation, cached-entity refresh, owner binding, SETUP/RESET expiry persistence and existing password lifecycle regression. Infrastructure bootstrap, DSP and actual delivery are fixtures.
- `verify_hth_880.py`: passed; 21 Hong Kong expiry boundary cases under three JVM timezones; raw subject/HTML equal the reviewed DOCX.
- Oracle PL/SQL execution/re-execution and real MNG delivery have **not** been run locally. These remain deployment checks, not claims of passing UAT.

Run from the source repository with `JAVA_HOME` pointing to a JDK supporting `--release 8`, and `H2_JAR` pointing to H2 1.4.200:

```sh
python3 devtools/backend-compile/tests/verify_hth_audit_compile.py
python3 devtools/backend-compile/tests/verify_hth_code_approval.py
python3 devtools/backend-compile/tests/verify_hth_notifications.py
python3 devtools/backend-compile/tests/verify_hth_password_transactions.py
python3 devtools/backend-compile/tests/verify_hth_880.py
```
