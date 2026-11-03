-- BCOH2H-2110 MINIMAL VERSION: READ ONLY. No template/configuration DML required.
-- Q1: active BCO route/template configuration; do not assume one template per event.
-- CORPORATE/USER and PARTY/CUSTOMER can select different templates for the generic event.
-- Explicit contact notifications use the existing EXTERNAL route.
SELECT r.COD_ACT_ID, r.COD_EVENT_ID, r.COD_ACTION_ID, r.TXT_DEST_TYP,
       r.LOCALE, r.SUBSCRIBER_TYPE, r.SUBSCRIBER_VALUE, r.RECIPIENT_TYPE,
       r.COD_MSG_TMPL_ID, t.OBJECT_STATUS, t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
FROM DIGX_EP_EVT_REC_B r LEFT JOIN DIGX_EP_MSG_TMPL_B t
  ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE = 'OBDX_BU'
WHERE (r.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update'
       AND r.COD_EVENT_ID IN ('INFO_UPDATE_BY_CORP_ADMIN','USER_MANAGEMENT_EDIT',
           'USER_MOBILE_NUMBER_UPDATED_REMINDER','USER_EMAIL_ADDRESS_UPDATE'))
   OR (r.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create'
       AND r.COD_EVENT_ID = 'CORPORATEPLUS_WELCOME_MAIL')
ORDER BY r.COD_ACT_ID, r.COD_EVENT_ID, r.TXT_DEST_TYP, r.LOCALE, r.SUBSCRIBER_TYPE, r.COD_MSG_TMPL_ID;

-- Q2: action enablement, expiry and channel conditions control actual dispatch.
SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, OBJECT_STATUS,
       FLG_TRANSACTIONAL, FLG_CONDITIONAL, EXPIRY_DATE
FROM DIGX_EP_ACT_EVT_ACN_B
WHERE (COD_ACT_ID = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update'
       AND COD_EVENT_ID IN ('INFO_UPDATE_BY_CORP_ADMIN','USER_MANAGEMENT_EDIT',
           'USER_MOBILE_NUMBER_UPDATED_REMINDER','USER_EMAIL_ADDRESS_UPDATE'))
   OR (COD_ACT_ID = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create'
       AND COD_EVENT_ID = 'CORPORATEPLUS_WELCOME_MAIL')
ORDER BY COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID;

-- Q3: existing dispatcher lists can override explicit recipients/country codes.
-- This release does not alter them. Check especially when old/new numbers use different countries.
SELECT PREFERENCE_NAME, PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_CZ_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU'
  AND PROP_ID IN ('SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST',
      'EMAIL_DISPATCHER_ALERT_EVENTID_LIST','SMS_DISPATCHER_ALERT_EVENTID_LIST',
      'INFOUPDATE_EMAILMOB_UPDATE_MAILSUBJ','INFOUPDATE_EMAILMOB_UPDATE_MAILCONTENT')
ORDER BY PROP_ID;

-- Q4: replace timestamps with a NARROW interval around one new final approval.
-- Shared BCO events are used by other transactions too; reconcile RECIPIENTID and REFNUMBER.
-- Target old mobile: USER_MOBILE_NUMBER_UPDATED_REMINDER.
-- Target new mobile: CORPORATEPLUS_WELCOME_MAIL and USER_EMAIL_ADDRESS_UPDATE.
-- Final approver must NOT get additional copies of these two contact-change reminders.
-- Generic USER_MANAGEMENT_EDIT email/SMS still follows the existing database configuration.
-- Include private IDs to detect old deployments/queued messages; no new code emits them.
-- A dispatch acknowledgement does not prove inbox/handset delivery.
SELECT REFNUMBER, EVENTID, RECIPIENTID, ALERT_TYPE, RESPONSE_STATUS, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID IN ('INFO_UPDATE_BY_CORP_ADMIN','USER_MANAGEMENT_EDIT',
      'CORPORATEPLUS_WELCOME_MAIL','USER_MOBILE_NUMBER_UPDATED_REMINDER','USER_EMAIL_ADDRESS_UPDATE',
      'HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT','HTH_2110_MOBILE_WELCOME',
      'HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE')
  AND LAST_UPDATED_DATE >= TO_DATE('2026-11-04 00:00:00','YYYY-MM-DD HH24:MI:SS')
  AND LAST_UPDATED_DATE <  TO_DATE('2026-11-05 00:00:00','YYYY-MM-DD HH24:MI:SS')
ORDER BY LAST_UPDATED_DATE DESC, EVENTID, RECIPIENTID;

-- Q5: optional inventory of superseded private mappings if old SQL was already executed.
-- They may remain unused. Their presence alone is not evidence of message dispatch.
SELECT COD_ACT_ID, COD_EVENT_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, LOCALE
FROM DIGX_EP_EVT_REC_B
WHERE COD_EVENT_ID IN ('HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT',
      'HTH_2110_MOBILE_WELCOME','HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE')
ORDER BY COD_EVENT_ID, TXT_DEST_TYP, LOCALE;
