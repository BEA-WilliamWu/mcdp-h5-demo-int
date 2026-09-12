-- Oracle SQL/PLSQL; no SQL*Plus commands or substitution variables.
-- Execute each complete DECLARE/BEGIN ... END; block as one statement (no slash).
-- BCOH2H-788 / 790 / 1204 post-deployment verification (read-only).
-- Execute after scripts 1-6 with dictionary, HTH_BEA, and OBDX configuration-table read access.
-- Every query documents its expected result. This script performs no DML.

-- Expected: 4 rows.
SELECT TABLE_NAME
  FROM ALL_TABLES
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
     'HTH_API_PASSWORD_CODE',
     'HTH_API_PASSWORD_STATE',
     'HTH_API_PASSWORD_OPERATION',
     'HTH_API_PASSWORD_CREDENTIAL'
   )
 ORDER BY TABLE_NAME;

-- Expected: all constraints ENABLED, including the credential PK, user FK and status check.
SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, STATUS
  FROM ALL_CONSTRAINTS
 WHERE OWNER = 'HTH_BEA'
   AND TABLE_NAME IN (
     'HTH_API_PASSWORD_CODE',
     'HTH_API_PASSWORD_STATE',
     'HTH_API_PASSWORD_OPERATION',
     'HTH_API_PASSWORD_CREDENTIAL'
   )
 ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- Expected: 4 rows, all VALID.
SELECT INDEX_NAME, STATUS
  FROM ALL_INDEXES
 WHERE OWNER = 'HTH_BEA'
   AND INDEX_NAME IN (
     'IX_HTH_API_PWD_CODE_OWN',
     'IX_HTH_API_PWD_CODE_TXN',
     'UX_HTH_API_PWD_CODE_LIVE',
     'IX_HTH_API_PWD_OP_USER'
   )
 ORDER BY INDEX_NAME;

-- ENABLED retains its configured value on rerun; true is required to use the feature.
-- DATABASE needs no UAM URL.
-- For UAM only, SERVICE_URL must be the approved HTTPS URL and contain no CHANGE_ME.
-- APIC secrets are deliberately reused from DSPApi and are not selected here.
SELECT PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ADAPTER_PROP_V
 WHERE CATEGORY_ID = 'HthApiCredentialAdapterConfig'
   AND PROP_ID NOT LIKE '%SECRET%'
   AND PROP_ID <> 'HTH_API_PASSWORD.CODE_CIPHER_KEY'
 ORDER BY PROP_ID;

-- Expected: 4 resources and 4 resource actions.
SELECT R.ID AS RESOURCE_ID, RA.ACTION_TYPE, RA.ENTITLEMENT_ID
  FROM DIGX_AZ_RESOURCE R
  LEFT JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.RESOURCE_ID = R.ID
 WHERE R.ID IN (
   'api-password',
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

-- Expected: ten rows, each with ROW_COUNT=3, LOCALE_COUNT=3 and CONFIG_STATUS=OK.
WITH expected AS (
  SELECT 'DIGX_CZ_HTH_API_PASSWORD_' || TO_CHAR(LEVEL, 'FM000') AS error_code
    FROM DUAL CONNECT BY LEVEL <= 10
)
SELECT e.error_code, COUNT(m.ERROR_CODE) AS row_count,
       COUNT(DISTINCT m.USER_LOCALE) AS locale_count,
       CASE WHEN COUNT(m.ERROR_CODE) = 3 AND
         COUNT(DISTINCT CASE WHEN m.USER_LOCALE IN ('en','zh-hans-cn','zh-hant')
           AND m.OBJECT_STATUS_FLAG = 'A' THEN m.USER_LOCALE END) = 3
         THEN 'OK' ELSE 'MISSING_OR_INVALID' END AS config_status
  FROM expected e LEFT JOIN DIGX_FW_ERROR_MESSAGES m ON m.ERROR_CODE = e.error_code
 GROUP BY e.error_code ORDER BY e.error_code;

-- Expected: four rows, CONFIG_STATUS=OK; three distinct Activity parents cover four events.
-- ACTIVITY_COUNT checks DIGX_EP_ACT_B, not just the event-to-activity mapping.
WITH expected AS (
SELECT 'HTH_API_PASSWORD_SETUP_SUCCESS' AS event_id,
 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup' AS activity_id,
 6 AS expected_recipients FROM DUAL
UNION ALL
SELECT 'HTH_API_PASSWORD_RESET_SUCCESS',
 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset', 6 FROM DUAL
UNION ALL
SELECT 'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval', 3 FROM DUAL
UNION ALL
SELECT 'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT',
 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval', 3 FROM DUAL
), checks AS (
SELECT e.*,
 (SELECT COUNT(*) FROM DIGX_EP_ACT_B b WHERE b.COD_ACT_ID=e.activity_id
  AND b.MODULE_TYPE='PC' AND b.OBJECT_STATUS='A' AND b.DOMAIN_OBJECT_EXTN='CZ') AS activity_count,
 (SELECT COUNT(*) FROM DIGX_PM_EVENT_ALL_B b WHERE b.EVENT_CODE=e.event_id
  AND b.ALERTS_FLAG='Y') AS event_count,
 (SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_B b WHERE b.COD_ACT_ID=e.activity_id
  AND b.COD_EVENT_ID=e.event_id AND b.DOMAIN_OBJECT_EXTN='CZ') AS activity_event_count,
 (SELECT COUNT(*) FROM DIGX_EP_ACT_EVT_ACN_B b WHERE b.COD_ACT_ID=e.activity_id
  AND b.COD_EVENT_ID=e.event_id AND b.COD_ACTION_ID='A' AND b.OBJECT_STATUS='A'
  AND b.DOMAIN_OBJECT_EXTN='CZ' AND b.EXPIRY_DATE > SYSDATE
  AND b.ALERT_DISPATCH_TYPE='I') AS action_count,
 (SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B b WHERE b.COD_ACT_ID=e.activity_id
  AND b.COD_EVENT_ID=e.event_id AND b.COD_ACTION_ID='A') AS recipient_count,
 (SELECT COUNT(*) FROM DIGX_EP_EVT_REC_B b JOIN DIGX_EP_MSG_TMPL_B t
  ON t.COD_TMPL_ID=b.COD_MSG_TMPL_ID AND t.DESTINATION_TYPE=b.TXT_DEST_TYP
  WHERE b.COD_ACT_ID=e.activity_id AND b.COD_EVENT_ID=e.event_id AND b.COD_ACTION_ID='A'
  AND b.DOMAIN_OBJECT_EXTN='CZ' AND b.SUBSCRIBER_TYPE='EXTERNAL' AND b.SUBSCRIBER_VALUE='USER'
  AND t.OBJECT_STATUS='A' AND t.DOMAIN_OBJECT_EXTN='CZ'
  AND t.DETERMINANT_VALUE='OBDX_BU') AS matching_template_count,
 (SELECT COUNT(DISTINCT b.TXT_DEST_TYP || ':' || b.LOCALE)
  FROM DIGX_EP_EVT_REC_B b WHERE b.COD_ACT_ID=e.activity_id AND b.COD_EVENT_ID=e.event_id
  AND b.COD_ACTION_ID='A' AND b.LOCALE IN ('en','zh-Hans-CN','zh-Hant')
  AND (b.TXT_DEST_TYP='EMAIL' OR (e.expected_recipients=6 AND b.TXT_DEST_TYP='SMS'))) AS channel_locale_count
FROM expected e
)
SELECT c.*, CASE WHEN activity_count=1 AND event_count=1 AND activity_event_count=1
  AND action_count=1 AND recipient_count=expected_recipients
  AND matching_template_count=expected_recipients AND channel_locale_count=expected_recipients
  THEN 'OK' ELSE 'MISSING_OR_INVALID' END AS config_status
FROM checks c ORDER BY event_id;

-- Expected: no rows. Only one usable code may exist for one user and purpose.
SELECT PARTY_ID,
       CASE WHEN SUBSTR(USER_NAME, -LENGTH(PARTY_ID)-1) = '@' || PARTY_ID
         THEN SUBSTR(USER_NAME, 1, LENGTH(USER_NAME)-LENGTH(PARTY_ID)-1) ELSE USER_NAME END AS USER_NAME,
       PURPOSE, COUNT(*) AS ACTIVE_CODE_COUNT
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE OBJECT_STATUS = 'A'
   AND STATUS = 'ACTIVE'
   AND EXPIRY_TIME > SYSTIMESTAMP
 GROUP BY PARTY_ID,
       CASE WHEN SUBSTR(USER_NAME, -LENGTH(PARTY_ID)-1) = '@' || PARTY_ID
         THEN SUBSTR(USER_NAME, 1, LENGTH(USER_NAME)-LENGTH(PARTY_ID)-1) ELSE USER_NAME END, PURPOSE
HAVING COUNT(*) > 1;

-- Informational: ACTIVE rows past expiry are unusable even before lifecycle retirement.
-- Do not extend their expiry or reset their status as part of deployment.
SELECT ID, PARTY_ID, USER_NAME, PURPOSE, STATUS, EXPIRY_TIME
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE OBJECT_STATUS = 'A'
   AND STATUS = 'ACTIVE'
   AND EXPIRY_TIME <= SYSTIMESTAMP
 ORDER BY EXPIRY_TIME;

-- Expected: no rows. Every operation must still reference its code and HTH user profile.
SELECT O.REQUEST_ID, O.PARTY_ID, O.USER_ID, O.CODE_ID
  FROM HTH_BEA.HTH_API_PASSWORD_OPERATION O
  LEFT JOIN HTH_BEA.HTH_API_PASSWORD_CODE C ON C.ID = O.CODE_ID
  LEFT JOIN HTH_BEA.HTH_USER_PROFILE U
    ON U.PARTY_ID = O.PARTY_ID AND U.CLOSE_ID = O.USER_ID
 WHERE C.ID IS NULL OR U.CLOSE_ID IS NULL;

-- Expected: no rows after completed operations. UNKNOWN requires checking the selected
-- storage backend and commit outcome before retry.
SELECT REQUEST_ID, PARTY_ID, USER_ID, OPERATION, STATUS, LAST_UPDATE_DATE
  FROM HTH_BEA.HTH_API_PASSWORD_OPERATION
 WHERE STATUS IN ('IN_PROGRESS', 'UNKNOWN')
 ORDER BY LAST_UPDATE_DATE;

-- Approved user creation codes must not be consumed before activation.
SELECT ID, PARTY_ID, USER_NAME, STATUS, PURPOSE
  FROM HTH_BEA.HTH_API_PASSWORD_CODE
 WHERE (STATUS = 'PENDING' AND (EXPIRY_TIME IS NOT NULL OR USED_TIME IS NOT NULL))
    OR (STATUS = 'USED' AND USED_TIME IS NULL);

-- Key presence only: never select PROP_VALUE for the encryption key.
SELECT COUNT(*) AS CODE_CIPHER_KEY_CONFIG_COUNT
  FROM DIGX_FW_CONFIG_ADAPTER_PROP_V
 WHERE CATEGORY_ID = 'HthApiCredentialAdapterConfig'
   AND PROP_ID = 'HTH_API_PASSWORD.CODE_CIPHER_KEY';

-- Selected credential store. Missing property means DATABASE in Java; empty/invalid is rejected.
SELECT CASE WHEN COUNT(*) = 0 THEN 'DATABASE'
            WHEN COUNT(*) = 1 AND UPPER(TRIM(MAX(PROP_VALUE))) IN ('DATABASE', 'UAM')
              THEN UPPER(TRIM(MAX(PROP_VALUE)))
            ELSE 'INVALID' END AS STORAGE_BACKEND
  FROM DIGX_FW_CONFIG_ADAPTER_PROP_V
 WHERE CATEGORY_ID = 'HthApiCredentialAdapterConfig'
   AND PROP_ID = 'HTH_API_PASSWORD.STORAGE_BACKEND';

-- Each repository requires matching base and OBDX_BU registrations.
SELECT PROP_ID, PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'repositoryadapterconfig'
   AND PROP_ID IN ('HTH_API_PASSWORD_CREDENTIAL_LOCAL_REPOSITORY_ADAPTER',
       'HTH_API_PASSWORD_STATE_LOCAL_REPOSITORY_ADAPTER',
       'HTH_API_PASSWORD_OPERATION_LOCAL_REPOSITORY_ADAPTER');
SELECT PROP_ID, PROP_VALUE, DETERMINANT_VALUE FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'RepositoryAdapterFactories' AND DETERMINANT_VALUE = 'OBDX_BU'
   AND PROP_ID IN ('HTH_API_PASSWORD_CREDENTIAL_LOCAL_REPOSITORY_ADAPTER',
       'HTH_API_PASSWORD_STATE_LOCAL_REPOSITORY_ADAPTER',
       'HTH_API_PASSWORD_OPERATION_LOCAL_REPOSITORY_ADAPTER');
