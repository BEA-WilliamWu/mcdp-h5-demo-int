-- READ ONLY. Run in the BCO database account that owns the DIGX_* objects.
-- Replace the party/user values below. Do not select CODE_CIPHER or passwords.
-- This file diagnoses a missing email; it does not repair data or resend emails.

-- 1. Approval/code state. ACTIVE alone does NOT prove that an email was sent.
SELECT ID, TRANSACTION_ID, STATUS, PURPOSE, OBJECT_STATUS,
       EXPIRY_TIME, CREATION_DATE, LAST_UPDATE_DATE
FROM HTH_BEA.HTH_API_PASSWORD_CODE
WHERE PARTY_ID = '<PARTY_ID>'
  AND UPPER(USER_NAME) IN (UPPER('<USER_ID>'), UPPER('<USER_ID>@<PARTY_ID>'))
ORDER BY CREATION_DATE DESC, ID DESC;

-- 2. Recent BCO creation and HTH Code approval email attempts.
-- Match the creation/approval time; counts alone cannot identify the target user.
-- Do not include recipient addresses when sharing these diagnostic results.
SELECT REFNUMBER, EVENTID, ALERT_TYPE, RESPONSE_STATUS, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID IN ('USER_MANAGEMENT_CREATE',
                  'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                  'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
  AND LAST_UPDATED_DATE >= SYSDATE - 1
ORDER BY LAST_UPDATED_DATE DESC;

-- 3. Both HTH events should have an activity, event, binding and active action.
-- LEFT JOIN makes missing configuration visible instead of hiding it.
WITH EXPECTED AS (
  SELECT 'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT' EVENT_ID FROM DUAL
  UNION ALL
  SELECT 'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT' FROM DUAL
)
SELECT E.EVENT_ID,
       CASE WHEN A.COD_ACT_ID IS NULL THEN 'MISSING' ELSE 'PRESENT' END ACTIVITY,
       P.ALERTS_FLAG,
       CASE WHEN B.COD_EVENT_ID IS NULL THEN 'MISSING' ELSE 'PRESENT' END EVENT_BINDING,
       X.COD_ACTION_ID, X.OBJECT_STATUS, X.FLG_TRANSACTIONAL, X.EXPIRY_DATE
FROM EXPECTED E
LEFT JOIN DIGX_EP_ACT_B A
  ON A.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
LEFT JOIN DIGX_PM_EVENT_ALL_B P ON P.EVENT_CODE = E.EVENT_ID
LEFT JOIN DIGX_EP_ACT_EVT_B B ON B.COD_EVENT_ID = E.EVENT_ID
 AND B.COD_ACT_ID = A.COD_ACT_ID
LEFT JOIN DIGX_EP_ACT_EVT_ACN_B X ON X.COD_EVENT_ID = E.EVENT_ID
 AND X.COD_ACT_ID = A.COD_ACT_ID AND X.COD_ACTION_ID = 'A'
ORDER BY E.EVENT_ID;

-- 4. Check installed locales and route-bound templates, including older template IDs.
-- No rows = missing routes. NULL TEMPLATE_STATUS = a dangling template binding.
SELECT R.COD_EVENT_ID, R.LOCALE, R.SUBSCRIBER_TYPE, R.SUBSCRIBER_VALUE,
       R.TXT_DEST_TYP, R.COD_MSG_TMPL_ID, T.OBJECT_STATUS TEMPLATE_STATUS,
       T.DESTINATION_TYPE TEMPLATE_DESTINATION, T.DETERMINANT_VALUE
FROM DIGX_EP_EVT_REC_B R
LEFT JOIN DIGX_EP_MSG_TMPL_B T ON T.COD_TMPL_ID = R.COD_MSG_TMPL_ID
 AND T.DETERMINANT_VALUE = 'OBDX_BU'
WHERE R.COD_ACT_ID = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
  AND R.COD_EVENT_ID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                         'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
  AND R.COD_ACTION_ID = 'A'
ORDER BY R.COD_EVENT_ID, R.LOCALE, R.TXT_DEST_TYP;
