-- BCOH2H-538 / BCOH2H-595 post-deployment verification (read-only).
-- Expected result counts are documented above each query.
-- Execute after all feature scripts using an account with dictionary and OBDX configuration-table
-- read access. This file performs no DML and requires no commit or rollback.
-- For an existing installation created by an older version of the schema script, execute
-- 7_HTH_User_Access_Time_Deposit_Upgrade.sql before running these checks.

-- Expected: 2 rows.
SELECT TABLE_NAME
  FROM ALL_TABLES
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
    'HTH_USER_ACCESS_ACCOUNT',
    'HTH_USER_ACCESS_ACCOUNT_API'
   )
 ORDER BY TABLE_NAME;

-- Expected: no rows. Approval state belongs to DIGX_AP_TRANSACTION.transactionSnapshot.
SELECT TABLE_NAME
  FROM ALL_TABLES
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
    'HTH_USER_ACCESS_REQUEST',
    'HTH_USER_ACCESS_REQ_ACCOUNT',
    'HTH_USER_ACCESS_REQ_API'
   )
 ORDER BY TABLE_NAME;

-- Expected: no rows. Invalid foreign keys normally indicate a missing prerequisite.
SELECT OWNER, CONSTRAINT_NAME, TABLE_NAME, STATUS
  FROM ALL_CONSTRAINTS
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
    'HTH_USER_ACCESS_ACCOUNT',
    'HTH_USER_ACCESS_ACCOUNT_API'
   )
   AND CONSTRAINT_TYPE = 'R'
   AND STATUS <> 'ENABLED';

-- Expected: 1 enabled check constraint; SEARCH_CONDITION_VC contains both CSA and TD.
SELECT TABLE_NAME, CONSTRAINT_NAME, STATUS, SEARCH_CONDITION_VC
  FROM ALL_CONSTRAINTS
 WHERE OWNER = 'HTH_BEA'
   AND CONSTRAINT_NAME = 'CK_HTH_UAA_TYPE'
 ORDER BY TABLE_NAME;

-- Expected: 6 rows. ACCOUNT_TYPE must precede ACCOUNT_NUMBER in the effective business key.
SELECT C.TABLE_NAME, C.CONSTRAINT_NAME, CC.POSITION, CC.COLUMN_NAME
  FROM ALL_CONSTRAINTS C
  JOIN ALL_CONS_COLUMNS CC
    ON CC.OWNER = C.OWNER
   AND CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME
   AND CC.TABLE_NAME = C.TABLE_NAME
 WHERE C.OWNER = 'HTH_BEA'
   AND C.CONSTRAINT_NAME = 'UK_HTH_UA_ACCOUNT'
 ORDER BY C.TABLE_NAME, C.CONSTRAINT_NAME, CC.POSITION;

-- Expected: 3 rows. Existing installations require
-- 10_HTH_User_Access_Account_Metadata_Upgrade.sql before this check.
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME = 'HTH_USER_ACCESS_ACCOUNT'
   AND COLUMN_NAME IN ('ACCOUNT_NUMBER', 'ACCOUNT_NUMBER_FORMATTED', 'PRODUCT_CODE')
 ORDER BY COLUMN_ID;

-- Expected: 2 rows, one index for each accepted runtime account-number representation.
SELECT INDEX_NAME, STATUS
  FROM ALL_INDEXES
 WHERE OWNER = 'HTH_BEA'
   AND INDEX_NAME IN ('IX_HTH_UAA_ACCOUNT_NO', 'IX_HTH_UAA_ACCOUNT_FMT')
 ORDER BY INDEX_NAME;

-- Expected after existing grants have been refreshed: no rows. The masked projection makes this
-- safe to include in deployment evidence without exposing account identifiers.
SELECT ID, PARTY_ID, CLOSE_ID,
       SUBSTR(ACCOUNT_NUMBER, 1, 4) || '***' || SUBSTR(ACCOUNT_NUMBER, -4)
         AS MASKED_ACCOUNT_NUMBER
  FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT
 WHERE OBJECT_STATUS = 'A'
   AND (ACCOUNT_NUMBER_FORMATTED IS NULL OR PRODUCT_CODE IS NULL)
 ORDER BY LAST_UPDATE_DATE DESC;

-- Expected: 8 entitlements and 8 UAT group mappings.
SELECT E.ID AS ENTITLEMENT_ID, M.ENT_GROUP_ID
  FROM DIGX_AZ_ENTITLEMENT E
  LEFT JOIN DIGX_AZ_ENTGROUP_ENT_MAPPING M
    ON M.ENTITLEMENT_ID = E.ID
 WHERE E.ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY E.ID;

-- Expected: 5 SVC resources and 16 resource-action mappings
-- (8 UI mappings plus 8 service mappings).
SELECT R.ID AS RESOURCE_ID, R.RESOURCE_TYPE, RA.ACTION_TYPE,
       RA.ENTITLEMENT_ID
  FROM DIGX_AZ_RESOURCE R
  JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.RESOURCE_ID = R.ID
 WHERE RA.ENTITLEMENT_ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY R.RESOURCE_TYPE, R.ID, RA.ACTION_TYPE;

-- Expected: 3 tasks, each with approval/audit/blackout/2fa aspects (12 rows total).
SELECT T.ID, T.NAME, T.PARENT_ID, A.ASPECT, A.ENABLED
  FROM DIGX_CM_TASK T
  JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID = T.ID
 WHERE T.ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY T.ID, A.ASPECT;

-- Expected: HTH mappings mirror the BCO Create/Edit/Delete mapping counts and authentication
-- parameters. AUTH_TYPE_ID should include the site's standard Signer OTP / iToken challenge.
SELECT M.TASK_ID, M.ENTITY_TYPE, M.ENTITY_VALUE, M.DETERMINANT_VALUE,
       P.LEVEL_NO, P.AUTH_TYPE_ID
  FROM DIGX_AU_MAPPING M
  JOIN DIGX_AU_MAPPING_PARAM P ON P.MAP_ID = M.ID
 WHERE M.TASK_ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY M.TASK_ID, M.DETERMINANT_VALUE, P.LEVEL_NO, P.AUTH_TYPE_ID;

-- Expected: one row whose PROP_VALUE contains all three HTH task codes separated by '~'.
SELECT PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
 WHERE PROP_ID = 'CRM_ALLOWED_TASK_CODES'
   AND PREFERENCE_NAME = 'CRMConfiguration'
   AND DETERMINANT_VALUE = 'N';

-- Expected: 3 rows with submit/edit/delete resource names.
SELECT RESOURCE_NAME, TASK_ID
  FROM DIGX_CM_RESOURCE_TASK_REL
 WHERE TASK_ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY TASK_ID;

-- Expected: 3 base and 3 override Approval Assembler rows.
SELECT PROP_ID, CATEGORY_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'approval_assembler'
   AND PROP_ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY PROP_ID;

SELECT PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'ApprovalAssemblers'
   AND PROP_ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY PROP_ID;

-- Expected: 2 base and 2 override Repository Adapter rows.
SELECT PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'repositoryadapterconfig'
   AND PROP_ID LIKE 'HTH_USER_ACCESS%LOCAL_REPOSITORY_ADAPTER'
 ORDER BY PROP_ID;

SELECT PROP_ID, PROP_VALUE, DETERMINANT_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'RepositoryAdapterFactories'
   AND PROP_ID LIKE 'HTH_USER_ACCESS%LOCAL_REPOSITORY_ADAPTER'
 ORDER BY PROP_ID;

-- Expected: 12 error codes, each with 3 locales (36 rows total).
SELECT ERROR_CODE, COUNT(*) AS LOCALE_COUNT
  FROM DIGX_FW_ERROR_MESSAGES
 WHERE ERROR_CODE LIKE 'DIGX_CZ_HTH_UA_%'
 GROUP BY ERROR_CODE
 ORDER BY ERROR_CODE;

-- Expected: one row whose PROP_VALUE contains all three HTH User Access task codes.
SELECT PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
 WHERE PROP_ID = 'TAB_CHANGE_TASK_CODES'
   AND PREFERENCE_NAME = 'DayOneConfig'
   AND DETERMINANT_VALUE = 'OBDX_BU';

-- Expected: no rows. A PARTY_MAINTENANCE HTH row is invisible to the BCO Administrative lists.
SELECT TXN_ID, TXN_NAME, DISCRIMINATOR, APPR_STATUS,
       PROCESSING_CURRENT_STEP, PROCESSING_STATUS, CREATED_BY, CREATION_DATE
  FROM DIGX_AP_TRANSACTION
 WHERE TXN_NAME IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
   AND NVL(DISCRIMINATOR, 'UNKNOWN') <> 'ADMIN_MAINTENANCE'
 ORDER BY CREATION_DATE DESC;

-- Diagnostic for issue 3. A newly submitted approval must be present here immediately after the
-- HTTP 400 / DIGX_APPROVAL_REQUIRED response; creation is synchronous, not a queue-fed insert.
-- Pending Approvals additionally requires a checker row and a workflow snapshot whose SEQUENCE_NO
-- equals APPR_STEP_NO. Activity Log only needs the transaction row and the correct discriminator.
SELECT T.TXN_ID, T.TXN_NAME, T.DISCRIMINATOR, T.APPR_STATUS, T.APPR_STEP_NO,
       T.PROCESSING_CURRENT_STEP, T.PROCESSING_STATUS, T.PARTY_ID,
       T.DETERMINANT_VALUE, T.CREATED_BY, T.CREATION_DATE,
       C.USER_NAME AS CHECKER_USER, C.SNAPSHOT_ID, C.MAX_APPROVAL_DATE,
       W.SEQUENCE_NO AS WORKFLOW_SEQUENCE
  FROM DIGX_AP_TRANSACTION T
  LEFT JOIN DIGX_AP_CHECKER_DETAILS C ON C.TXN_ID = T.TXN_ID
  LEFT JOIN DIGX_AP_TXN_WORKFLOW_SNAPSHOT W
    ON W.ID = C.SNAPSHOT_ID
   AND W.TXN_ID = T.TXN_ID
 WHERE T.TXN_NAME IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
   AND T.CREATION_DATE >= SYSDATE - 1
 ORDER BY T.CREATION_DATE DESC, C.USER_NAME;
