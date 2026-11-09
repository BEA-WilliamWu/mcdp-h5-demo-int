-- READ ONLY. Run before the update and export Q1 as the original-content backup.
-- Run again afterwards. Q1 shows the ACTUAL installed IDs, not guessed names.

-- Q1: all primary/alternate templates, recipients, locales and full raw content.
WITH routes AS (
  SELECT r.* FROM DIGX_EP_EVT_REC_B r
   WHERE (COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
          AND COD_EVENT_ID='HTH_API_PASSWORD_SETUP_SUCCESS')
      OR (COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
          AND COD_EVENT_ID='HTH_API_PASSWORD_RESET_SUCCESS')
), bindings AS (
  SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, TXT_DEST_TYP, LOCALE,
         SUBSCRIBER_TYPE, SUBSCRIBER_VALUE, 'PRIMARY' BINDING_TYPE,
         COD_MSG_TMPL_ID TEMPLATE_ID FROM routes
  UNION ALL
  SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, TXT_DEST_TYP, LOCALE,
         SUBSCRIBER_TYPE, SUBSCRIBER_VALUE, 'ALTERNATE', UNSECURE_MSG_TMPL_ID
    FROM routes WHERE TRIM(UNSECURE_MSG_TMPL_ID) IS NOT NULL
)
SELECT b.*, t.DETERMINANT_VALUE, t.DESTINATION_TYPE, t.OBJECT_STATUS,
       t.LAST_UPDATED_DATE, t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
  FROM bindings b LEFT JOIN DIGX_EP_MSG_TMPL_B t
    ON t.COD_TMPL_ID=b.TEMPLATE_ID AND t.DETERMINANT_VALUE='OBDX_BU'
 ORDER BY b.COD_EVENT_ID, b.TXT_DEST_TYP, b.LOCALE, b.BINDING_TYPE, b.TEMPLATE_ID;

-- Q2: missing/invalid primary bindings. Expected ZERO rows, not 12 rows.
-- Q1 includes alternate rows too; the update validates those and shared IDs.
WITH operations AS (
  SELECT 'setup' OPERATION, 'HTH_API_PASSWORD_SETUP_SUCCESS' EVENT_ID FROM DUAL
  UNION ALL SELECT 'reset', 'HTH_API_PASSWORD_RESET_SUCCESS' FROM DUAL
), channels AS (
  SELECT 'EMAIL' DEST FROM DUAL UNION ALL SELECT 'SMS' FROM DUAL
), locales AS (
  SELECT 'en' LOCALE FROM DUAL UNION ALL SELECT 'zh-Hant' FROM DUAL
  UNION ALL SELECT 'zh-Hans-CN' FROM DUAL
)
SELECT o.EVENT_ID, c.DEST, l.LOCALE,
       COUNT(r.COD_MSG_TMPL_ID) BINDINGS, COUNT(t.COD_TMPL_ID) VALID_TEMPLATES
  FROM operations o CROSS JOIN channels c CROSS JOIN locales l
  LEFT JOIN DIGX_EP_EVT_REC_B r
    ON r.COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.' || o.OPERATION
   AND r.COD_EVENT_ID=o.EVENT_ID AND r.COD_ACTION_ID='A'
   AND r.TXT_DEST_TYP=c.DEST AND r.LOCALE=l.LOCALE
  LEFT JOIN DIGX_EP_MSG_TMPL_B t
    ON t.COD_TMPL_ID=r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE='OBDX_BU'
   AND t.DESTINATION_TYPE=c.DEST AND t.OBJECT_STATUS='A'
 GROUP BY o.EVENT_ID, c.DEST, l.LOCALE
HAVING COUNT(r.COD_MSG_TMPL_ID)=0 OR COUNT(r.COD_ACT_ID)<>COUNT(r.COD_MSG_TMPL_ID)
    OR COUNT(t.COD_TMPL_ID)<>COUNT(r.COD_MSG_TMPL_ID)
 ORDER BY o.EVENT_ID, c.DEST, l.LOCALE;

-- Q3: original BCO contact-edit templates reused by 865/1334 (NO DML required).
-- Corporate/company variants remain separate. This query shows DB bindings,
-- not proof of delivery or the runtime approver identity.
SELECT r.COD_ACT_ID, r.COD_EVENT_ID, r.COD_ACTION_ID, r.SUBSCRIBER_TYPE,
       r.SUBSCRIBER_VALUE, r.TXT_DEST_TYP, r.LOCALE, r.COD_MSG_TMPL_ID,
       r.UNSECURE_MSG_TMPL_ID, t.OBJECT_STATUS, t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
  FROM DIGX_EP_EVT_REC_B r LEFT JOIN DIGX_EP_MSG_TMPL_B t
    ON t.COD_TMPL_ID=r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE='OBDX_BU'
 WHERE (r.COD_ACT_ID='com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update'
        AND r.COD_EVENT_ID IN ('INFO_UPDATE_BY_CORP_ADMIN','USER_MANAGEMENT_EDIT',
                              'USER_EMAIL_ADDRESS_UPDATE','USER_MOBILE_NUMBER_UPDATED_REMINDER'))
    OR (r.COD_ACT_ID='com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create'
        AND r.COD_EVENT_ID='CORPORATEPLUS_WELCOME_MAIL')
 ORDER BY r.COD_EVENT_ID, r.TXT_DEST_TYP, r.LOCALE, r.SUBSCRIBER_TYPE, r.COD_MSG_TMPL_ID;

-- Q4: new test messages. Change the time window to isolate a fresh transaction.
-- MNG rows/body show a dispatch attempt/result, not confirmed mailbox/handset delivery.
SELECT REFNUMBER, EVENTID, RECIPIENTID, ALERT_TYPE, RESPONSE_STATUS,
       LAST_UPDATED_DATE, SUBJECT, MESSAGEBODY
  FROM DIGX_CZ_EMAIL_MNG
 WHERE EVENTID IN ('HTH_API_PASSWORD_SETUP_SUCCESS','HTH_API_PASSWORD_RESET_SUCCESS',
                   'INFO_UPDATE_BY_CORP_ADMIN','USER_MANAGEMENT_EDIT',
                   'USER_EMAIL_ADDRESS_UPDATE','USER_MOBILE_NUMBER_UPDATED_REMINDER',
                   'CORPORATEPLUS_WELCOME_MAIL')
   AND LAST_UPDATED_DATE >= SYSDATE-1
 ORDER BY LAST_UPDATED_DATE DESC, REFNUMBER DESC;
