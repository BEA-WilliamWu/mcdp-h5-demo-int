-- BCOH2H-788 / 790 / 1204 post-deployment verification (read-only).
-- Execute after scripts 1-6 with dictionary, HTH_BEA, and OBDX configuration-table read access.
-- Every query documents its expected result. This script performs no DML.

-- Expected: 3 rows.
SELECT TABLE_NAME
  FROM ALL_TABLES
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
     'HTH_API_PASSWORD_CODE',
     'HTH_API_PASSWORD_STATE',
     'HTH_API_PASSWORD_OPERATION'
   )
 ORDER BY TABLE_NAME;

-- Expected: 16 enabled constraints (3 PK, 4 FK, 9 business/object checks).
SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, STATUS
  FROM ALL_CONSTRAINTS
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
     'HTH_API_PASSWORD_CODE',
     'HTH_API_PASSWORD_STATE',
     'HTH_API_PASSWORD_OPERATION'
   )
 ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- Expected: 4 rows, all VALID.
SELECT INDEX_NAME, STATUS
  FROM ALL_INDEXES
 WHERE OWNER = 'HTH_BEA'
   AND INDEX_NAME IN (
     'IX_HTH_API_PWD_CODE_USER',
     'IX_HTH_API_PWD_CODE_REQ',
     'UX_HTH_API_PWD_CODE_LIVE',
     'IX_HTH_API_PWD_OP_USER'
   )
 ORDER BY INDEX_NAME;

-- Expected: 15 rows. ENABLED must be true only after the approved UAM contract is available;
-- SERVICE_URL must be the approved HTTPS URL and contain no CHANGE_ME.
-- APIC secrets are deliberately reused from DSPApi and are not selected here.
SELECT PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ADAPTER_PROP_V
 WHERE CATEGORY_ID = 'HthApiCredentialAdapterConfig'
   AND PROP_ID NOT LIKE '%SECRET%'
 ORDER BY PROP_ID;

-- Expected: 4 resources and 4 resource actions.
SELECT R.ID AS RESOURCE_ID, RA.ACTION_TYPE, RA.ENTITLEMENT_ID
  FROM DIGX_AZ_RESOURCE R
  LEFT JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.RESOURCE_ID = R.ID
 WHERE R.ID IN (
   'hth-api-password',
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status',
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
 )
 ORDER BY R.ID;

-- Expected: 3 rows, each with POLICY_COUNT > 0 and GROUP_COUNT > 0.
SELECT E.ID AS ENTITLEMENT_ID,
       (SELECT COUNT(DISTINCT P.POLICY_ID)
          FROM DIGX_AZ_POLICY_ENT_MAP P
         WHERE P.ENTITLEMENT_ID = E.ID) AS POLICY_COUNT,
       (SELECT COUNT(DISTINCT G.ENT_GROUP_ID)
          FROM DIGX_AZ_ENTGROUP_ENT_MAPPING G
         WHERE G.ENTITLEMENT_ID = E.ID) AS GROUP_COUNT
  FROM DIGX_AZ_ENTITLEMENT E
 WHERE E.ID IN (
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View',
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform',
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform'
 )
 ORDER BY E.ID;

-- Expected: 2 rows, both BCO_CLASSIFICATION=MATCH and audit=Y.
SELECT T.ID, T.NAME, T.TASK_TYPE, T.MODULE_TYPE, A.ASPECT, A.ENABLED,
       CASE WHEN T.TASK_TYPE = S.TASK_TYPE AND T.MODULE_TYPE = S.MODULE_TYPE
            THEN 'MATCH' ELSE 'MISMATCH' END AS BCO_CLASSIFICATION
  FROM DIGX_CM_TASK T
  CROSS JOIN (SELECT TASK_TYPE, MODULE_TYPE FROM DIGX_CM_TASK WHERE ID = 'CM_N_CC') S
  JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID = T.ID
 WHERE T.ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET')
 ORDER BY T.ID, A.ASPECT;

-- Expected: 2 rows, mapping each write service to the corresponding task.
SELECT RESOURCE_NAME, TASK_ID
  FROM DIGX_CM_RESOURCE_TASK_REL
 WHERE TASK_ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET')
 ORDER BY TASK_ID;

-- Expected: 9 rows, each with LOCALE_COUNT=3 (en, zh-hans-cn, zh-hant).
SELECT ERROR_CODE, COUNT(DISTINCT USER_LOCALE) AS LOCALE_COUNT
  FROM DIGX_FW_ERROR_MESSAGES
 WHERE ERROR_CODE BETWEEN 'DIGX_CZ_HTH_API_PASSWORD_001'
                      AND 'DIGX_CZ_HTH_API_PASSWORD_009'
 GROUP BY ERROR_CODE
 ORDER BY ERROR_CODE;

-- Expected: EVENT_COUNT=1, ACTIVITY_COUNT=1, ACTION_COUNT=1,
-- RECIPIENT_COUNT=3, TEMPLATE_COUNT=3.
SELECT
  (SELECT COUNT(*) FROM DIGX_PM_EVENT_ALL_B
    WHERE EVENT_CODE = 'HTH_API_PASSWORD_SETUP_SUCCESS') AS EVENT_COUNT,
  (SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_B
    WHERE COD_ACT_ID =
      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
      AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS') AS ACTIVITY_COUNT,
  (SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_ACN_B
    WHERE COD_ACT_ID =
      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
      AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS') AS ACTION_COUNT,
  (SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B
    WHERE COD_ACT_ID =
      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
      AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS') AS RECIPIENT_COUNT,
  (SELECT COUNT(*) FROM DIGX_EP_MSG_TMPL_B
    WHERE COD_TMPL_ID IN (
      'HTH_API_PWD_SETUP_USER_EMAIL_en',
      'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hans-CN',
      'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hant')) AS TEMPLATE_COUNT
FROM DUAL;

-- Expected: no rows. Only one usable code may exist for one user and purpose.
SELECT PARTY_ID, USER_NAME, PURPOSE, COUNT(*) AS ACTIVE_CODE_COUNT
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE OBJECT_STATUS = 'A'
   AND STATUS = 'ACTIVE'
   AND EXPIRY_TIME > SYSDATE
 GROUP BY PARTY_ID, USER_NAME, PURPOSE
HAVING COUNT(*) > 1;

-- Expected: no rows. Expired codes should be retired by the code-generation/housekeeping feature.
SELECT ID, PARTY_ID, USER_NAME, PURPOSE, STATUS, EXPIRY_TIME
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE OBJECT_STATUS = 'A'
   AND STATUS = 'ACTIVE'
   AND EXPIRY_TIME <= SYSDATE
 ORDER BY EXPIRY_TIME;

-- Expected: no rows. Every operation must still reference its code and HTH user profile.
SELECT O.REQUEST_ID, O.PARTY_ID, O.USER_ID, O.CODE_ID
  FROM HTH_BEA.HTH_API_PASSWORD_OPERATION O
  LEFT JOIN HTH_BEA.HTH_API_PASSWORD_CODE C ON C.ID = O.CODE_ID
  LEFT JOIN HTH_BEA.HTH_USER_PROFILE U
    ON U.PARTY_ID = O.PARTY_ID AND U.CLOSE_ID = O.USER_ID
 WHERE C.ID IS NULL OR U.CLOSE_ID IS NULL;

-- Expected: no rows under normal operation. UNKNOWN requires UAM reconciliation before retry.
SELECT REQUEST_ID, PARTY_ID, USER_ID, OPERATION, STATUS, LAST_UPDATE_DATE
  FROM HTH_BEA.HTH_API_PASSWORD_OPERATION
 WHERE STATUS IN ('IN_PROGRESS', 'UNKNOWN')
 ORDER BY LAST_UPDATE_DATE;

-- Expected after one successful first setup:
--   CODE=USED, OPERATION=SUCCESS, STATE=ACTIVE with the same request/reference.
-- Replace the two bind variables in the deployment tool; never query or print CODE_CIPHER or the code-encryption key.
SELECT S.PARTY_ID, S.USER_ID, S.CREDENTIAL_STATUS, S.CREDENTIAL_VERSION,
       S.SETUP_AT, S.LAST_RESET_AT, S.LAST_REQUEST_ID, S.LAST_REFERENCE_NUMBER,
       O.OPERATION, O.STATUS AS OPERATION_STATUS, C.STATUS AS CODE_STATUS
  FROM HTH_BEA.HTH_API_PASSWORD_STATE S
  JOIN HTH_BEA.HTH_API_PASSWORD_OPERATION O
    ON O.PARTY_ID = S.PARTY_ID
   AND O.USER_ID = S.USER_ID
   AND O.REQUEST_ID = S.LAST_REQUEST_ID
  JOIN HTH_BEA.HTH_API_PASSWORD_CODE C ON C.ID = O.CODE_ID
 WHERE S.PARTY_ID = :PARTY_ID
   AND S.USER_ID = :USER_ID;

-- Approved user creation codes must not be consumed before activation.
SELECT ID, PARTY_ID, USER_NAME, STATUS, PURPOSE
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE (STATUS = 'PENDING' AND (EXPIRY_TIME IS NOT NULL OR USED_TIME IS NOT NULL))
    OR (STATUS = 'USED' AND USED_TIME IS NULL);

-- Key presence only: never select PROP_VALUE for the encryption key.
SELECT COUNT(*) AS CODE_CIPHER_KEY_CONFIG_COUNT
  FROM DIGX_FW_CONFIG_ADAPTER_PROP_V
 WHERE CATEGORY_ID = 'HthApiCredentialAdapterConfig'
   AND PROP_ID = 'HTH_API_PWD_CODE_CIPHER_KEY';
