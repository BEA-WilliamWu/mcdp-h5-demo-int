# BCOH2H-849 — CM HTH API Password CRM persistence

**Version:** 2026-10-07
**Scope:** This is the complete design for the corrected 849 implementation. It replaces the earlier design that created `HTH_CRM_EVENT_DETAILS` / `HTH_MTB_EVENT_DETAILS` and added unrelated HTH operations. The source of truth is the current implementation and the corrected scope: **H2H API Password Create** and **H2H API Password Reset/Change** only.

## 1. Purpose and AC disposition

849 stores these HTH password activities in the **existing BCO CRM event model and table** so that a later, separate HTH extract can produce the MTB interface. It does not create an HTH CRM table or directly send an MTB file.

| Story item | Design / current implementation | Acceptance status |
| --- | --- | --- |
| AC1 — persist defined transaction data | `HostToHostApiPassword` builds `CRMInputData` and calls the existing BCO CRM adapter, assembler, ORM and repository. Target: `DIGX_CZ_CRM_EVENT3_DETAILS`. | Code path implemented. Actual completeness and persistence require UAT database verification. |
| AC2 — asynchronous persistence | The current call executes synchronously in the request path, following the prior project decision to use BCO's existing persistence approach for now. | **Deviation from the written AC.** BA/architecture must formally accept synchronous behavior or request a later asynchronous change. Do not describe this AC as completed. |
| Daily CSV extraction / MTB transfer | Separate story 1293 owns the HTH-only daily CSV job. File transfer to MTB is downstream integration. | Outside 849. |

## 2. Activity and trigger rules

| Password operation | CRM activity (`EVENT_ACTV_TYPE_CODE`) | When written | Status |
| --- | --- | --- | --- |
| First successful setup | `HTH_PWD_CRD` | After the credential and Code lifecycle completes, before success notification | `A` (accepted) |
| Successful reset/change | `HTH_PWD_UPD` | After the credential and Code lifecycle completes, before success notification | `A` |
| Setup failure inside `change()` | `HTH_PWD_CRD` | After the failed business interaction closes | `R` (rejected) |
| Reset/change failure inside `change()` | `HTH_PWD_UPD` | After the failed business interaction closes | `R` |

`change()` is reached by the setup/reset service. An idempotent replay of an already successful `requestId` returns the earlier result and **does not write another accepted event**. Access-policy, feature-switch or request-validation failures that occur before the business interaction are not recorded by this 849 hook. User access linkage, approval, BM company management, Code Generate and ordinary BCO activities are not added by 849.

The accepted event is attempted in the active business interaction. The rejected event is attempted after `Interaction.close()` so a rolled-back business transaction does not automatically remove its rejection record. A failure in notification after the accepted attempt can also enter the rejected path; UAT should verify the final persisted status for this case rather than infer it from application logs.

## 3. Persistence sequence and ownership

```text
HTH setup/reset service
  -> validate / decrypt / reserve Code / complete password operation
  -> HTH-only recordCRM(...)
  -> ICRMAsserterCallAdapter.fetchCommonInfo(...)
  -> HTH field overrides on CRMInputData
  -> ICRMAsserterCallAdapter.crmInsertForRaq(...)
  -> CRMAsserter (generates EVENT_ID)
  -> CRMRequestAssembler -> CRMEvent3DomainDTO
  -> CRMLocalRepository -> existing DIGX_CZ_CRM_EVENT3_DETAILS ORM/table
```

The HTH hook lives in `com.ofss.digx.cz.bea.module.hosttohost`. The adapter, `CRMInputData`, assembler, entity, repository, ORM and table are the existing BCO chain and are **not changed by 849**. `recordCRM` reuses `CRMConfiguration` for common values and then overrides HTH-specific fields. The write uses the current DIGX session when one exists; otherwise it opens a DIGX session. This is synchronous, best-effort CRM persistence: CRM failures are logged and do not replace the password operation's response.

| Implementation reference | Responsibility |
| --- | --- |
| `consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java` | HTH trigger, status and field overrides |
| `consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/generic/asserter/impl/CRMAsserter.java` | Existing BCO common field builder and CRM insert entry |
| `consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/domain/common/entity/crm/repository/assembler/CRMRequestAssembler.java` | Existing input-to-entity mapping |
| `consulting/config/orm/eclipselink/mappings/cz/crm/CustomerRelationshipManagement_Event3.orm.xml` | Existing table mapping |
| `consulting/middleware/batchJobs/hth-crm-extract/` | Separate 1293 HTH CSV job; no 849 DB-write logic here |

No 849 `CREATE TABLE`, `ALTER TABLE`, new ORM or new CRM preference records are needed. The one 849 SQL file is a **read-only preflight and verification** script. It checks the activity-code column length and `CRM_SEQUENCE` access, then queries HTH rows. Run it as a user that can see the DIGX CRM table and sequence.

## 4. Stored field mapping

`CRMRequestAssembler` maps `CRMInputData` onto the existing BCO entity. The following values are explicitly set or overridden by the HTH hook; other common fields come from BCO `fetchCommonInfo` when available. Password, Code, hash and request ciphertext are **never stored in CRM**.

| CRM column / field | HTH value or source |
| --- | --- |
| `EVENT_ID` | Existing `CRMAsserter.generateEventID()` / `CRM_SEQUENCE`; one ID per attempted insert |
| `EVENT_DTE`, `EVENT_TIME` | Operation time in `Asia/Hong_Kong`, `yyyyMMdd` and `HHmmss` |
| `CHNL_ID`, `CHNL_TYPE_CODE` | `ELE-HTH`, `ELE` |
| `EVENT_ACTV_TYPE_CODE` | `HTH_PWD_CRD` for setup; `HTH_PWD_UPD` for reset/change |
| `EVENT_STATUS_CODE` | `A` for accepted; `R` for rejected |
| `RECORD_TYPE`, `FILLER_01` | `50`, `1` |
| `FEE_CHRG_CODE`, `EVENT_COUNTRY_CODE` | `SERV`, `HK` |
| `FIN_IND`, `SELF_SRV_IND` | `N`, `Y` |
| `USER_ID` | Logged-in user, with the `@partyId` suffix removed when present |
| `ACCT_NBR` | HTH party ID; from the resolved identity on success, or session party code on rejection |
| `SCREEN_ID` | `api-password-setup` or `api-password-reset` |
| `ELECT_ADD` | Target user's email when identity and user lookup are available; may be empty on failure or lookup error |
| `IP_ADDRESS` | Existing `FMO_IP_ADDRESS` thread attribute via BCO common builder; insertion is skipped when absent |
| Other existing BCO common fields | BCO `fetchCommonInfo` (for example available request, device or locale attributes); not all are applicable to these password operations |

The 85 columns in `API_MTB_latest(HTH_CRM_MAPPING_BCO).xlsx`, rows 11–95, describe the **file extraction mapping**. They do not mean that all 85 database fields are non-null for a password event. The applicable field list and mandatory/optional rules must be validated against the MTB recipient; do not fill unrelated transaction fields with fabricated values.

## 5. Boundary with 1293 and BCO

849 writes `ELE-HTH` rows to the shared CRM table. The 1293 job independently selects that channel, the two activity codes and one HKT `EVENT_DTE`; it writes a date-specific CSV and does not update `BATCH_PROCESSED_DATE`. The BCO `GenTxnLog2CRM` job's select and both marker updates exclude the HTH channel so HTH rows cannot be consumed by the BCO fixed-width file (its activity field is only seven characters). This filter preserves eligibility of non-HTH BCO rows, including rows with a null channel.

The 1293 file name, schedule, CSV envelope and MTB transfer contract are governed by 1293 and downstream agreement. They are not database changes in 849.

## 6. Failure handling and observability

The HTH hook logs `HTH_CRM_849 stage=BUILD`, `EMAIL_LOOKUP`, `INSERT` and `CLOSE`; it does not log password, Code or encrypted credentials. A missing session identity or IP prevents an insert attempt. Email lookup failure leaves `ELECT_ADD` empty but does not block the attempt. CRM write failure does not fail setup/reset.

**Important implementation limit:** the shared `CRMAsserter.crmInsertData()` catches its own database exceptions and may return without inserting. Therefore `HTH_CRM_849 stage=INSERT` means the adapter call returned, **not** that a committed row exists. Verify the database row and `EVENT_ID` after each test. If guaranteed delivery is required, a separate reliable-persistence change (for example explicit failure propagation or outbox/retry) needs review; it is not part of this synchronous 849 version.

## 7. Deployment and verification

1. Deploy the HTH backend module containing `HostToHostApiPassword` and the existing BCO CRM adapter stack. Confirm the shared table, column length and `CRM_SEQUENCE` with `consulting/db/branch_change_history/20261007_BCOH2H_849/1_HTH_CRM_849_Verify.sql`. No 849 DDL is applied.
2. Complete one real setup and one real reset/change with valid Codes. Query `DIGX_CZ_CRM_EVENT3_DETAILS` and check one `A` row for each expected activity, HTH channel, HKT date/time, user and party. Check that a replay of the successful `requestId` adds no second accepted row.
3. Exercise incorrect/expired Code and credential-operation failure. Where failure occurs inside `change()`, verify the corresponding `R` row. Test failures before the interaction separately; no 849 row is expected there.
4. Test missing IP and forced CRM persistence failure: password response behavior must remain unchanged, while the missing/failed CRM row is visible to support. Check notification-failure behavior separately because it can reach the rejected path after an accepted attempt.
5. Run ordinary BCO CRM regression and confirm non-HTH rows still enter its batch; confirm HTH rows enter the separate 1293 extract only. Compare 1293's extracted count with the HTH source count for that business date.

Example safe query (no password or Code values):

```sql
SELECT EVENT_ID, EVENT_DTE, EVENT_TIME, CHNL_ID,
       EVENT_ACTV_TYPE_CODE, EVENT_STATUS_CODE,
       USER_ID, ACCT_NBR, BATCH_PROCESSED_DATE
FROM DIGX_CZ_CRM_EVENT3_DETAILS
WHERE CHNL_ID = 'ELE-HTH'
  AND EVENT_ACTV_TYPE_CODE IN ('HTH_PWD_CRD', 'HTH_PWD_UPD')
  AND EVENT_DTE = :business_date_yyyymmdd
ORDER BY EVENT_ID DESC;
```

**Verification limit:** local source review and compile/static checks cannot establish real Oracle persistence, commit timing, mailbox delivery or downstream MTB acceptance. Those require SIT/UAT execution and database/file evidence. The written AC2 async requirement and the shared adapter's swallowed insert errors are the two release decisions to resolve explicitly.
