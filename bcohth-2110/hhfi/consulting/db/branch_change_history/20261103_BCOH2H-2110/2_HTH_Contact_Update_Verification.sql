-- READ ONLY. Run before/after deployment in the OBDX configuration schema.
-- Q1: eighteen rows after deployment: six route/channel combinations x three locales.
SELECT r.COD_EVENT_ID, r.TXT_DEST_TYP, r.LOCALE, r.SUBSCRIBER_TYPE,
       r.SUBSCRIBER_VALUE, r.COD_MSG_TMPL_ID, t.OBJECT_STATUS,
       t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
FROM DIGX_EP_EVT_REC_B r LEFT JOIN DIGX_EP_MSG_TMPL_B t
  ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE = 'OBDX_BU'
WHERE r.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update'
  AND r.COD_EVENT_ID IN ('HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT',
      'HTH_2110_MOBILE_WELCOME','HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE')
ORDER BY r.COD_EVENT_ID, r.TXT_DEST_TYP, r.LOCALE;

-- Q2: five active actions, all unconditional, FLG_TRANSACTIONAL=N (existing async Alert pipeline).
SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, OBJECT_STATUS,
       FLG_TRANSACTIONAL, FLG_CONDITIONAL, EXPIRY_DATE
FROM DIGX_EP_ACT_EVT_ACN_B
WHERE COD_EVENT_ID IN ('HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT',
      'HTH_2110_MOBILE_WELCOME','HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE')
ORDER BY COD_EVENT_ID;

-- Q3: metadata copied from the six exact BCO source templates. Verify getters still match
-- UserProfUpdateActivityLogDTO (email A, SMS A/B/D) or UserManagementActivityLogDTO (email B, SMS C).
SELECT s.COD_MESS_TMPL_ID, s.COD_ATTR_ID, s.COD_ACT_ID, m.*
FROM DIGX_EP_MSG_SRC_B s LEFT JOIN DIGX_MD_SERVICE_ATTR m
  ON m.COD_SERVICE_ATTR_ID = s.COD_SERVICE_ATTR_ID
WHERE SUBSTR(s.COD_MESS_TMPL_ID,1,8) = 'HTH2110_' AND s.DETERMINANT_VALUE = 'OBDX_BU'
ORDER BY s.COD_MESS_TMPL_ID, s.COD_ATTR_ID;

-- Q4: retain the existing list plus the four HTH SMS events in the COUNTRY skip-list.
-- HTH_2110_* must NOT appear in the EMAIL/SMS recipient override lists.
-- Chinese subject/content fragments are reused from BCO and must not be empty.
SELECT PREFERENCE_NAME, PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_CZ_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU'
  AND PROP_ID IN ('SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST',
      'EMAIL_DISPATCHER_ALERT_EVENTID_LIST','SMS_DISPATCHER_ALERT_EVENTID_LIST',
      'INFOUPDATE_EMAILMOB_UPDATE_MAILSUBJ','INFOUPDATE_EMAILMOB_UPDATE_MAILCONTENT')
ORDER BY PROP_ID;

-- Q5: replace both timestamps with a narrow range around one final approval.
-- With four different mailboxes and three valid mobile contacts expect 6 EMAIL + 4 SMS rows.
-- Shared mailboxes reduce rows within one event only; approver/company still get email A AND B.
-- MNG acknowledgement/processing SUCCESS is not proof of inbox/handset delivery.
SELECT REFNUMBER, EVENTID, RECIPIENTID, ALERT_TYPE, RESPONSE_STATUS, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID IN ('HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT',
      'HTH_2110_MOBILE_WELCOME','HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE')
  AND LAST_UPDATED_DATE >= TO_DATE('2026-11-03 00:00:00','YYYY-MM-DD HH24:MI:SS')
  AND LAST_UPDATED_DATE <  TO_DATE('2026-11-04 00:00:00','YYYY-MM-DD HH24:MI:SS')
ORDER BY LAST_UPDATED_DATE DESC, EVENTID, RECIPIENTID;

-- Q6: baseline source content remains unchanged. Export before/after deployment for comparison.
SELECT COD_TMPL_ID, DETERMINANT_VALUE, OBJECT_STATUS, TXT_SUBJECT_TMPL, TXT_MSG_TMPL
FROM DIGX_EP_MSG_TMPL_B
WHERE DETERMINANT_VALUE = 'OBDX_BU'
  AND COD_TMPL_ID IN ('INFO_UPDATE_BY_CORP_ADMIN_EXTERNAL_USER_EMAIL_en',
      'USER_MANAGEMENT_EDIT_CORPORATE_USER_EMAIL_en', 'CorporatePlus Welcome Mail_EXTERNAL_USER_SMS_en',
      'User Email Address update_EXTERNAL_USER_SMS_en','USER_MANAGEMENT_EDIT_CORPORATE_USER_SMS_en',
      'User Mobile Number Updated Reminder_EXTERNAL_USER_SMS_en')
ORDER BY COD_TMPL_ID;
