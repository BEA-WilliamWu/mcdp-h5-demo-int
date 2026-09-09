-- Oracle SQL/PLSQL; no SQL*Plus commands or substitution variables.
-- Execute each complete DECLARE/BEGIN ... END; block as one statement (no slash).
-- BCOH2H-1204: First-Time HTH API Password Setup success notification.
-- Execute in the OBDX configuration schema after the Error Messages script.
-- The event is raised only by HostToHostApiPassword.setup after UAM and local finalization succeed.
-- Reset is intentionally excluded (BCOH2H-1205). No password or Password Code is templated.
-- Re-runnable. Dispatch behavior follows the existing BCO Login PIN notification baseline.

DELETE FROM DIGX_EP_EVT_REC_B
 WHERE COD_ACT_ID =
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS';

DELETE FROM DIGX_EP_ACT_EVT_ACN_B
 WHERE COD_ACT_ID =
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS';

DELETE FROM DIGX_EP_ACT_EVT_B
 WHERE COD_ACT_ID =
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS';

DELETE FROM DIGX_EP_MSG_TMPL_B
 WHERE COD_TMPL_ID IN (
   'HTH_API_PWD_SETUP_USER_EMAIL_en',
   'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hans-CN',
   'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hant'
 );

DELETE FROM DIGX_PM_EVENT_ALL_B
 WHERE EVENT_CODE = 'HTH_API_PASSWORD_SETUP_SUCCESS';

INSERT INTO DIGX_PM_EVENT_ALL_B
  (EVENT_CODE, EVENT_DESC, PRODUCT_CLASS, PRICING_EVENT_TYPE, ALERT_EVENT_TYPE,
   FINANCIAL_TYPE, PRICING_DOMAIN_CATEGORY, ACCT_DOMAIN_CATEGORY, ALERTS_FLAG,
   FEES_FLAG, DOCUMENTATION_FLAG, ACCOUNTING_FLAG, ACCT_ENTRY_FLAG, DOMAIN_CODE,
   REWARDS_FLAG, RESTRICT_OFFER_FLAG, TASK_NAME, FEE_RECOGNITION_BRANCH_TYPE,
   DOMAIN_GEN_BANK_LEVEL_FLAG, DOMAIN_SPE_BANK_LEVEL_FLAG, TXN_RES_FLAG)
VALUES
  ('HTH_API_PASSWORD_SETUP_SUCCESS', 'First-time HTH API Password setup succeeded',
   NULL, NULL, NULL, NULL, NULL, NULL, 'Y', 'N', 'N', 'N', 'N', NULL, 'N', 'N',
   NULL, NULL, 'N', 'N', 'N');

INSERT INTO DIGX_EP_ACT_EVT_B
  (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP,
   DOMAIN_OBJECT_EXTN)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
   'HTH_API_PASSWORD_SETUP_SUCCESS', 'First-time HTH API Password setup succeeded',
   'OTHER', 'ONLINE', 'CZ');

INSERT INTO DIGX_EP_ACT_EVT_ACN_B
  (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, FLG_TRANSACTIONAL, ACTION_SOURCE,
   COD_DEC_ID, FLG_CONDITIONAL, COD_ACN_TMPL_ID, ALERT_NAME, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER,
   NUM_RETRY_CNT, FLG_RETRY_ALLOWED, EXPIRY_DATE, ALERT_TYPE,
   ALERT_DISPATCH_TYPE, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
   'HTH_API_PASSWORD_SETUP_SUCCESS', 'A', 'N', NULL, '0', 'N', '1',
   'First-time HTH API Password Setup Success User Email', 'superadmin', SYSDATE,
   'superadmin', SYSDATE, 1, 0, 'N', TO_DATE('31-DEC-2099', 'DD-MON-RRRR'),
   'M', 'I', 'A', 'CZ');

INSERT ALL
  INTO DIGX_EP_MSG_TMPL_B
    (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE,
     OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN,
     DETERMINANT_VALUE)
  VALUES
    ('HTH_API_PWD_SETUP_USER_EMAIL_en', 'EMAIL',
     'HTH API Password Setup Success User Email en', 'CORPORATE - USER',
     '<p>Dear Customer,</p><p>Your HTH API Password has been set up successfully.</p><p>If you did not perform this action, please contact The Bank of East Asia immediately on (852) 2211 1321.</p><p>For your security, the Bank will never ask for your password or HTH API Password Code by email.</p><p>Yours faithfully,<br />The Bank of East Asia, Limited</p><p>This is a system-generated email. Please do not reply.</p>',
     'superadmin', SYSDATE, 'superadmin', SYSDATE, 1, 'A',
     'BEA Corporate Online: HTH API Password Setup Successful', 'CZ', 'OBDX_BU')
  INTO DIGX_EP_MSG_TMPL_B
    (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE,
     OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN,
     DETERMINANT_VALUE)
  VALUES
    ('HTH_API_PWD_SETUP_USER_EMAIL_zh-Hans-CN', 'EMAIL',
     'HTH API Password Setup Success User Email zh-Hans-CN', 'CORPORATE - USER',
     '<p>尊贵的客户：</p><p>您的 HTH API 密码已成功设置。</p><p>若此操作并非由您执行，请立即致电 (852) 2211 1321 联络东亚银行。</p><p>为保障账户安全，本行绝不会通过电邮索取您的密码或 HTH API 密码代码。</p><p>东亚银行有限公司 谨启</p><p>此电邮由系统自动发出，请勿回复。</p>',
     'superadmin', SYSDATE, 'superadmin', SYSDATE, 1, 'A',
     '东亚企业网上银行：HTH API 密码设置成功', 'CZ', 'OBDX_BU')
  INTO DIGX_EP_MSG_TMPL_B
    (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE,
     OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN,
     DETERMINANT_VALUE)
  VALUES
    ('HTH_API_PWD_SETUP_USER_EMAIL_zh-Hant', 'EMAIL',
     'HTH API Password Setup Success User Email zh-Hant', 'CORPORATE - USER',
     '<p>尊貴的客戶：</p><p>您的 HTH API 密碼已成功設定。</p><p>若此操作並非由您執行，請立即致電 (852) 2211 1321 聯絡東亞銀行。</p><p>為保障賬戶安全，本行絕不會透過電郵索取您的密碼或 HTH API 密碼代碼。</p><p>東亞銀行有限公司 謹啟</p><p>此電郵由系統自動發出，請勿回覆。</p>',
     'superadmin', SYSDATE, 'superadmin', SYSDATE, 1, 'A',
     '東亞企業網上銀行：HTH API 密碼設定成功', 'CZ', 'OBDX_BU')
SELECT 1 FROM DUAL;

INSERT ALL
  INTO DIGX_EP_EVT_REC_B
    (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP,
     COD_DEC_ID, FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE,
     DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT,
     UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
     'HTH_API_PASSWORD_SETUP_SUCCESS', 'A', 'HTH_API_PWD_SETUP_USER_EMAIL_en',
     'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'en', NULL, 'NA', 0, NULL,
     'USER')
  INTO DIGX_EP_EVT_REC_B
    (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP,
     COD_DEC_ID, FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE,
     DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT,
     UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
     'HTH_API_PASSWORD_SETUP_SUCCESS', 'A',
     'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hans-CN', 'EMAIL', '0', 'N', 'EXTERNAL',
     NULL, 'M', 'CZ', 'zh-Hans-CN', NULL, 'NA', 0, NULL, 'USER')
  INTO DIGX_EP_EVT_REC_B
    (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP,
     COD_DEC_ID, FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE,
     DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT,
     UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
     'HTH_API_PASSWORD_SETUP_SUCCESS', 'A', 'HTH_API_PWD_SETUP_USER_EMAIL_zh-Hant',
     'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hant', NULL, 'NA', 0,
     NULL, 'USER')
SELECT 1 FROM DUAL;

COMMIT;

SELECT COUNT(*) AS HTH_API_PASSWORD_SETUP_EVENT_COUNT
  FROM DIGX_PM_EVENT_ALL_B
 WHERE EVENT_CODE = 'HTH_API_PASSWORD_SETUP_SUCCESS';

SELECT LOCALE, COD_MSG_TMPL_ID, SUBSCRIBER_VALUE
  FROM DIGX_EP_EVT_REC_B
 WHERE COD_ACT_ID =
       'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup'
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS'
 ORDER BY LOCALE;
