-- Read-only Oracle diagnostics for HTH setup/reset notifications.
-- Run in the OBDX configuration schema. No SQL*Plus commands or DML.
-- Query 1 covers the last seven days; adjust the period for older tests.
-- Query 5 uses :PARTY_ID and :LOGIN_USER binds supplied by the SQL client.
-- Do not select MESSAGEBODY, passwords, Code ciphertext or encryption keys.

-- 0. Confirm the configured dispatcher before interpreting MNG audit rows.
-- Preferences.xml maps Dispatchers to category AlertDispatcher.
-- The project implementations are:
-- com.ofss.digx.cz.bea.domain.service.dispatch.EmailDispatcher
-- com.ofss.digx.cz.bea.domain.service.dispatch.SMSDispatcher
-- Other implementations or an outgoing adapter handling email itself may bypass
-- this project's MNG audit. Configuration rows do not prove which class is loaded.
SELECT PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'AlertDispatcher'
 ORDER BY PROP_ID;

-- Check the connection/schema and whether this resolved table has any audit data.
-- Compare the schema with the application's datasource; do not assume HTH_BEA.
SELECT SYS_CONTEXT('USERENV', 'DB_NAME') AS DATABASE_NAME,
       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS CURRENT_SCHEMA_NAME,
       SYSDATE AS DATABASE_TIME
  FROM DUAL;

SELECT OWNER, OBJECT_NAME, OBJECT_TYPE
  FROM ALL_OBJECTS
 WHERE OBJECT_NAME = 'DIGX_CZ_EMAIL_MNG'
   AND OBJECT_TYPE IN ('TABLE', 'VIEW', 'SYNONYM')
 ORDER BY OWNER, OBJECT_TYPE;

SELECT OWNER, SYNONYM_NAME, TABLE_OWNER, TABLE_NAME, DB_LINK
  FROM ALL_SYNONYMS
 WHERE SYNONYM_NAME = 'DIGX_CZ_EMAIL_MNG'
   AND OWNER IN (SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'), 'PUBLIC');

SELECT COUNT(*) AS TOTAL_ROWS, MAX(LAST_UPDATED_DATE) AS LATEST_AUDIT_TIME
  FROM DIGX_CZ_EMAIL_MNG;

-- Inspect recent audit metadata without assuming an event ID or a date window.
SELECT * FROM (
  SELECT REFNUMBER, EVENTID, ACTIVITYID, ALERT_TYPE,
         RESPONSE_STATUS, LAST_UPDATED_DATE
    FROM DIGX_CZ_EMAIL_MNG
   ORDER BY LAST_UPDATED_DATE DESC NULLS LAST
) WHERE ROWNUM <= 30;

-- 1. The project's MNG paths for EMAIL and SMS use DIGX_CZ_EMAIL_MNG.
-- Success: the existing dispatcher classified the MNG response as successful;
--          this is NOT a handset/mailbox delivery receipt.
-- Failed / Exception: inspect the matching dispatcher/provider log.
-- Pending: no final provider result was persisted; it does not prove no send.
-- No row: investigate event generation, configuration, mock mode, contact lookup,
--         template validation and audit-persistence failures before concluding no send.
-- In sendMNGMail, a caught audit-insert failure does not stop the gateway call;
-- an audit-update failure is also caught. Received email can therefore lack a row.
SELECT REFNUMBER, EVENTID, ALERT_TYPE, RESPONSE_STATUS,
       COD_ACT_DATA_ID, LAST_UPDATED_DATE,
       CASE WHEN TRIM(RECIPIENTID) IS NULL THEN 'MISSING'
            WHEN ALERT_TYPE = 'SMS'
              THEN '***' || SUBSTR(TRIM(RECIPIENTID), -4)
            ELSE 'PRESENT' END AS RECIPIENT_CHECK,
       CASE WHEN ALERT_TYPE = 'SMS' THEN LENGTH(TRIM(RECIPIENTID))
            ELSE NULL END AS SMS_DESTINATION_LENGTH
  FROM DIGX_CZ_EMAIL_MNG
 WHERE EVENTID IN ('HTH_API_PASSWORD_SETUP_SUCCESS',
                   'HTH_API_PASSWORD_RESET_SUCCESS')
   AND LAST_UPDATED_DATE >= SYSDATE - 7
 ORDER BY LAST_UPDATED_DATE DESC;

-- Activity parents must exist before any event can reach the dispatchers.
-- Expected: three active CZ activities with MODULE_TYPE=PC.
SELECT COD_ACT_ID, MODULE_TYPE, OBJECT_STATUS, DOMAIN_OBJECT_EXTN
  FROM DIGX_EP_ACT_B
 WHERE COD_ACT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
 );

-- 2. Expected: twelve rows with RECIPIENT_COUNT=1 and ACTIVE_TEMPLATE_COUNT=1.
-- Missing setup rows can explain reset working while setup does not.
-- Check the exact locale used at the time of the operation.
WITH events AS (
  SELECT 'HTH_API_PASSWORD_SETUP_SUCCESS' AS event_id,
         'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup' AS activity_id
    FROM DUAL
  UNION ALL
  SELECT 'HTH_API_PASSWORD_RESET_SUCCESS',
         'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
    FROM DUAL
), destinations AS (
  SELECT 'EMAIL' AS destination FROM DUAL
  UNION ALL SELECT 'SMS' FROM DUAL
), locales AS (
  SELECT 'en' AS locale FROM DUAL
  UNION ALL SELECT 'zh-Hans-CN' FROM DUAL
  UNION ALL SELECT 'zh-Hant' FROM DUAL
)
SELECT e.event_id, d.destination, l.locale,
       COUNT(DISTINCT r.ROWID) AS recipient_count,
       COUNT(t.COD_TMPL_ID) AS active_template_count
  FROM events e
 CROSS JOIN destinations d
 CROSS JOIN locales l
  LEFT JOIN DIGX_EP_EVT_REC_B r
    ON r.COD_EVENT_ID = e.event_id AND r.COD_ACT_ID = e.activity_id
   AND r.COD_ACTION_ID = 'A' AND r.TXT_DEST_TYP = d.destination
   AND r.LOCALE = l.locale AND r.SUBSCRIBER_TYPE = 'EXTERNAL'
   AND r.SUBSCRIBER_VALUE = 'USER'
  LEFT JOIN DIGX_EP_MSG_TMPL_B t
    ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID
   AND t.DESTINATION_TYPE = d.destination
   AND t.OBJECT_STATUS = 'A' AND t.DETERMINANT_VALUE = 'OBDX_BU'
 GROUP BY e.event_id, d.destination, l.locale
 ORDER BY e.event_id, d.destination, l.locale;

-- 3. Compare setup/reset event actions and the existing BCO PIN reset action.
-- HTH currently uses active, immediate, nontransactional actions without retry.
SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, OBJECT_STATUS,
       FLG_TRANSACTIONAL, ALERT_DISPATCH_TYPE, FLG_RETRY_ALLOWED,
       NUM_RETRY_CNT, EXPIRY_DATE
  FROM DIGX_EP_ACT_EVT_ACN_B
 WHERE COD_EVENT_ID IN ('HTH_API_PASSWORD_SETUP_SUCCESS',
                        'HTH_API_PASSWORD_RESET_SUCCESS', 'PIN_RESET_SUCCESS')
 ORDER BY COD_EVENT_ID, COD_ACT_ID, COD_ACTION_ID;

SELECT EVENT_CODE, ALERTS_FLAG
  FROM DIGX_PM_EVENT_ALL_B
 WHERE EVENT_CODE IN ('HTH_API_PASSWORD_SETUP_SUCCESS',
                      'HTH_API_PASSWORD_RESET_SUCCESS');

-- 4. isDispatchMocked=true skips the real SMS gateway and returns mock success.
-- Check the active business-unit override as well as the base value.
-- A missing base setting defaults to false in SMSDispatcher.
SELECT CATEGORY_ID, PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'DispatchDetails' AND PROP_ID = 'isDispatchMocked';

SELECT PREFERENCE_NAME, DETERMINANT_VALUE, PROP_ID, PROP_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'DispatchDetails' AND PROP_ID = 'isDispatchMocked';

-- 5. SMSDispatcher reads MOBILE_CODE using the notification's runtime user ID.
-- The HTH service takes the phone number from framework User.getMobileNumber().
-- The extension's MOBILE_NO is shown only as a presence/length cross-check;
-- it is not proof of the framework User mobile value.
SELECT USER_ID, CDC_NO, MOBILE_CODE,
       CASE WHEN TRIM(MOBILE_NO) IS NULL THEN 'MISSING' ELSE 'PRESENT' END AS EXTENSION_MOBILE,
       LENGTH(TRIM(MOBILE_NO)) AS EXTENSION_MOBILE_LENGTH
  FROM DIGX_CZ_UM_EXTENSIONDATA
 WHERE CDC_NO = :PARTY_ID
   AND UPPER(USER_ID) IN (
       UPPER(:LOGIN_USER),
       UPPER(REGEXP_SUBSTR(:LOGIN_USER, '^[^@]+')),
       UPPER(REGEXP_SUBSTR(:LOGIN_USER, '^[^@]+') || '@' || :PARTY_ID)
   );

-- Existing log markers (correlate event, timestamp and REFNUMBER):
--   No user recipient for HTH event
--   No registered contacts for HTH event
--   Unable to publish HTH notification event
--   SMS Template Id:
--   Failed to send SMS due to text length exceeding allowed limit
--   Exception from SMSDispatcher.send
--   MNG SMS before adapter.createAlert
--   MNG SMS after adpater.createAlert,result:
--   AddressException from SMSDispatcher.dispatchMNGSms
-- Avoid sharing full dispatcher payloads: existing shared logs may contain contacts.

-- Optional per-user credential result checks; supply :PARTY_ID and the stored :USER_ID.
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


-- Database credentials: metadata only, never select PASSWORD_HASH or Code ciphertext.
SELECT PARTY_ID, USER_ID, CREDENTIAL_STATUS, CREDENTIAL_VERSION, LAST_REQUEST_ID, UPDATED_AT
  FROM HTH_BEA.HTH_API_PASSWORD_CREDENTIAL
 WHERE PARTY_ID = :PARTY_ID AND USER_ID = :USER_ID;

SELECT REQUEST_ID, OPERATION, STATUS, STORAGE_BACKEND
  FROM HTH_BEA.HTH_API_PASSWORD_OPERATION
 WHERE PARTY_ID = :PARTY_ID AND USER_ID = :USER_ID;
