-- Existing background actions must have FLG_TRANSACTIONAL=N. Do not switch to Y.
SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, FLG_TRANSACTIONAL,
       OBJECT_STATUS, NUM_RETRY_CNT, FLG_RETRY_ALLOWED, EXPIRY_DATE
FROM DIGX_EP_ACT_EVT_ACN_B
WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
  AND COD_EVENT_ID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                       'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
ORDER BY COD_EVENT_ID, COD_ACTION_ID;

-- Read-only. Six EMAIL rows; no SMS rows; bodies/subjects identical across both roles and locales.
-- The installed ID may be HTH_API_PWD_CODE_* or HTH API Password Code Approved_*.
SELECT r.COD_EVENT_ID, r.LOCALE, r.TXT_DEST_TYP, r.SUBSCRIBER_TYPE, r.SUBSCRIBER_VALUE,
       r.COD_MSG_TMPL_ID, t.DETERMINANT_VALUE, t.DESTINATION_TYPE,
       t.OBJECT_STATUS, t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
FROM DIGX_EP_EVT_REC_B r LEFT JOIN DIGX_EP_MSG_TMPL_B t
  ON t.COD_TMPL_ID=r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE='OBDX_BU'
WHERE r.COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval'
  AND r.COD_EVENT_ID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                        'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
ORDER BY r.COD_EVENT_ID, r.TXT_DEST_TYP, r.LOCALE;

-- If preflight fails, list both historical ID families across units/channels.
-- An existing template in another unit/channel does not satisfy OBDX_BU / EMAIL.
-- This query does not change or recreate any configuration.
SELECT COD_TMPL_ID, DETERMINANT_VALUE, DESTINATION_TYPE, OBJECT_STATUS,
       TXT_SUBJECT_TMPL
FROM DIGX_EP_MSG_TMPL_B
WHERE INSTR(COD_TMPL_ID, 'HTH_API_PWD_CODE_') = 1
   OR INSTR(COD_TMPL_ID, 'HTH API Password Code Approved_') = 1
ORDER BY COD_TMPL_ID, DETERMINANT_VALUE, DESTINATION_TYPE;

-- Do not select CODE_CIPHER. New approvals expire 24 hours after activation.
-- LAST_UPDATE_DATE can change after approval (attempt/use), so compare it only for a newly
-- activated, untouched ACTIVE Code. Existing historical expiry is not rewritten.
SELECT ID, PARTY_ID, USER_NAME, PURPOSE, STATUS, TRANSACTION_ID, EXPIRY_TIME,
       LAST_UPDATE_DATE
FROM HTH_BEA.HTH_API_PASSWORD_CODE
WHERE LAST_UPDATE_DATE >= SYSDATE - 1
ORDER BY LAST_UPDATE_DATE DESC;

-- Set the time range to the test transaction. SUCCESS is processing status, not proof of inbox delivery.
SELECT REFNUMBER, EVENTID, RECIPIENTID, ALERT_TYPE, RESPONSE_STATUS, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                  'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
  AND LAST_UPDATED_DATE >= SYSDATE - 1
ORDER BY LAST_UPDATED_DATE DESC;
