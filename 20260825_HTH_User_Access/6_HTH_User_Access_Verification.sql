-- BCOH2H-538 / BCOH2H-595 post-deployment verification (read-only).
-- Expected result counts are documented above each query.

-- Expected: 6 rows.
SELECT TABLE_NAME
  FROM ALL_TABLES
 WHERE OWNER = 'HTH_BEAUAT'
   AND TABLE_NAME IN (
    'HTH_USER_ACCESS',
    'HTH_USER_ACCESS_ACCOUNT',
    'HTH_USER_ACCESS_ACCOUNT_API',
    'HTH_USER_ACCESS_REQUEST',
    'HTH_USER_ACCESS_REQ_ACCOUNT',
    'HTH_USER_ACCESS_REQ_API'
   )
 ORDER BY TABLE_NAME;

-- Expected: no rows. Invalid foreign keys normally indicate a missing prerequisite.
SELECT OWNER, CONSTRAINT_NAME, TABLE_NAME, STATUS
  FROM ALL_CONSTRAINTS
 WHERE OWNER = 'HTH_BEAUAT'
   AND TABLE_NAME IN (
    'HTH_USER_ACCESS',
    'HTH_USER_ACCESS_ACCOUNT',
    'HTH_USER_ACCESS_ACCOUNT_API',
    'HTH_USER_ACCESS_REQUEST',
    'HTH_USER_ACCESS_REQ_ACCOUNT',
    'HTH_USER_ACCESS_REQ_API'
   )
   AND CONSTRAINT_TYPE = 'R'
   AND STATUS <> 'ENABLED';

-- Expected: 8 entitlements and 8 UAT group mappings.
SELECT E.ID AS ENTITLEMENT_ID, M.ENT_GROUP_ID
  FROM DIGX_AZ_ENTITLEMENT E
  LEFT JOIN DIGX_AZ_ENTGROUP_ENT_MAPPING M
    ON M.ENTITLEMENT_ID = E.ID
 WHERE E.ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY E.ID;

-- Expected: 5 SVC resources and 15 resource-action mappings
-- (7 UI mappings plus 8 service mappings).
SELECT R.ID AS RESOURCE_ID, R.RESOURCE_TYPE, RA.ACTION_TYPE,
       RA.ENTITLEMENT_ID
  FROM DIGX_AZ_RESOURCE R
  JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.RESOURCE_ID = R.ID
 WHERE RA.ENTITLEMENT_ID LIKE
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%'
 ORDER BY R.RESOURCE_TYPE, R.ID, RA.ACTION_TYPE;

-- Expected: 3 tasks, each with approval/audit/blackout aspects.
SELECT T.ID, T.NAME, T.PARENT_ID, A.ASPECT, A.ENABLED
  FROM DIGX_CM_TASK T
  JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID = T.ID
 WHERE T.ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY T.ID, A.ASPECT;

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

-- Expected: 6 base and 6 override Repository Adapter rows.
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
