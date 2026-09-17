-- Read-only deployment checks. No recipient addresses or message payloads selected.
SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS CURRENT_SCHEMA FROM DUAL;

WITH operations AS (
 SELECT 'submit' operation, 'SUBMIT' event_suffix, 'ENABLE' template_suffix FROM DUAL
 UNION ALL SELECT 'disable','DISABLE','DISABLE' FROM DUAL
 UNION ALL SELECT 'edit','EDIT','EDIT' FROM DUAL
), locales AS (SELECT 'en' locale FROM DUAL UNION ALL SELECT 'zh-hant' FROM DUAL
 UNION ALL SELECT 'zh-hans-cn' FROM DUAL), channels AS (SELECT 'EMAIL' destination FROM DUAL UNION ALL SELECT 'SMS' FROM DUAL)
SELECT o.operation, c.destination, l.locale, r.COD_MSG_TMPL_ID, r.SUBSCRIBER_TYPE,
       t.OBJECT_STATUS, t.LAST_UPDATED_DATE,
       CASE WHEN c.destination = 'SMS' THEN LENGTH(t.TXT_MSG_TMPL) END AS SMS_CHARACTERS
FROM operations o CROSS JOIN channels c CROSS JOIN locales l
LEFT JOIN DIGX_EP_EVT_REC_B r
 ON r.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.' || o.operation
 AND r.COD_EVENT_ID = 'HTH_API_SERVICE_' || o.event_suffix || '_SUCCESS'
 AND r.COD_ACTION_ID = 'A' AND r.LOCALE = l.locale AND r.TXT_DEST_TYP = c.destination
LEFT JOIN DIGX_EP_MSG_TMPL_B t ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID
 AND t.DESTINATION_TYPE = r.TXT_DEST_TYP AND t.DETERMINANT_VALUE = 'OBDX_BU'
ORDER BY o.operation, c.destination, l.locale;
-- Expect exactly eighteen rows, no null templates, EXTERNAL and active templates.
-- EN SMS lengths: 220/123/133; TC: 80/56/58; SC: 80/64/58.

SELECT COD_EVENT_ID, TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID, SUBSCRIBER_TYPE
FROM DIGX_EP_EVT_REC_B
WHERE COD_EVENT_ID IN ('HTH_API_SERVICE_SUBMIT_SUCCESS',
 'HTH_API_SERVICE_DISABLE_SUCCESS','HTH_API_SERVICE_EDIT_SUCCESS')
ORDER BY COD_EVENT_ID, LOCALE, TXT_DEST_TYP;
-- Supported locales: en, zh-hant, zh-hans-cn. Compare content with DOCX v12.

SELECT PROP_ID, PROP_VALUE FROM DIGX_CZ_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU'
 AND PROP_ID IN ('SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST',
 'SMS_DISPATCHER_ALERT_EVENTID_LIST','EMAIL_DISPATCHER_ALERT_EVENTID_LIST');
-- All three events must be in SKIP_COUNTRY_CODE; none in recipient override lists.

SELECT CATEGORY_ID, PROP_ID, PROP_VALUE FROM DIGX_FW_CONFIG_ALL_B
WHERE (CATEGORY_ID = 'AlertPollerPool' AND PROP_ID = 'SMSLength')
 OR (CATEGORY_ID = 'DispatchDetails' AND PROP_ID IN
 ('isDispatchMocked','sms.pattern.separator','sms.invalid.patterns'));
-- Runtime SMSLength must accommodate 220; mocked dispatch does NOT send to MNG.
-- Review global-setting changes separately because they also affect BCO.

SELECT REFNUMBER, EVENTID, ALERT_TYPE, RESPONSE_STATUS, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID IN ('HTH_API_SERVICE_SUBMIT_SUCCESS',
 'HTH_API_SERVICE_DISABLE_SUCCESS','HTH_API_SERVICE_EDIT_SUCCESS')
 AND LAST_UPDATED_DATE >= SYSDATE - 7
ORDER BY LAST_UPDATED_DATE DESC;
-- Success means MNG accepted the request, not proof of handset/email delivery.
