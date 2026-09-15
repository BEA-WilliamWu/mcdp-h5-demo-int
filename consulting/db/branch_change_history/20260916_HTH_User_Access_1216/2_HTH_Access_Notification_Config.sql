-- BCOH2H-1216. Execute the whole block, then COMMIT. Default OFF; reruns preserve flag.
-- Dedicated HTH events: BCO activity/templates/dispatch lists are not overwritten.
-- Matrix #4/#5 working EN+TC / EN+SC wording; confirm final copy before enabling.
BEGIN
SAVEPOINT HTH_ACCESS_CONFIG;

INSERT INTO DIGX_FW_CONFIG_ALL_B
 (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS, SUMMARY_TEXT,
  CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE, CATEGORY_DESCRIPTION)
SELECT 'HTH_USER_ACCESS_NOTIFICATION_ENABLED', 'HthUserAccessNotification', 'false', 'N', 'BCOH2H-1216 access notification',
 'HTH User Access Notification', USER, SYSDATE, USER, SYSDATE, 'A', 1, 'N', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_FW_CONFIG_ALL_B WHERE CATEGORY_ID = 'HthUserAccessNotification' AND PROP_ID = 'HTH_USER_ACCESS_NOTIFICATION_ENABLED');

INSERT INTO DIGX_FW_CONFIG_ALL_B
 (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS, SUMMARY_TEXT,
  CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE, CATEGORY_DESCRIPTION)
SELECT 'dispatchDataSource', 'HthUserAccessNotification', 'NONXA', 'N', 'BCOH2H-1216 access notification',
 'HTH User Access Notification', USER, SYSDATE, USER, SYSDATE, 'A', 1, 'N', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_FW_CONFIG_ALL_B WHERE CATEGORY_ID = 'HthUserAccessNotification' AND PROP_ID = 'dispatchDataSource');


INSERT INTO DIGX_MD_GEN_ATTR_LEGACY_B
 (COD_CONSTRAINT_ATTR_ID, TXT_CONSTRAINT_ATTR_NAME, DATA_TYPE, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS)
SELECT 'userNameId', 'userNameId', 'java.lang.String', USER, SYSDATE, USER, SYSDATE, 1, 'A' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_GEN_ATTR_LEGACY_B WHERE COD_CONSTRAINT_ATTR_ID='userNameId');

INSERT INTO DIGX_MD_GEN_ATTR_LEGACY_B
 (COD_CONSTRAINT_ATTR_ID, TXT_CONSTRAINT_ATTR_NAME, DATA_TYPE, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS)
SELECT 'userSysDate', 'userSysDate', 'java.lang.String', USER, SYSDATE, USER, SYSDATE, 1, 'A' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_GEN_ATTR_LEGACY_B WHERE COD_CONSTRAINT_ATTR_ID='userSysDate');

INSERT INTO DIGX_MD_GEN_ATTR_LEGACY_B
 (COD_CONSTRAINT_ATTR_ID, TXT_CONSTRAINT_ATTR_NAME, DATA_TYPE, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS)
SELECT 'compName', 'compName', 'java.lang.String', USER, SYSDATE, USER, SYSDATE, 1, 'A' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_GEN_ATTR_LEGACY_B WHERE COD_CONSTRAINT_ATTR_ID='compName');

INSERT INTO DIGX_EP_ACT_B
 (COD_ACT_ID, TXT_ACT_NAME, TXT_ACT_DESC, MODULE_TYPE, FLG_IP_REQD, FLG_OP_REQD,
  FLG_LOG_REQD, TXT_LOG_CLASS, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
  LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HostToHostUserAccess.submit', 'HTH User Account and Service Access', 'PC', NULL, NULL, NULL, NULL,
 USER, SYSDATE, USER, SYSDATE, 1, 'A', 'CZ' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit');


UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='userNameId', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserNameId', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO', 'INDIRECT', 'DTO', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserNameId' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO');

UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='userSysDate', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserSysDate', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO', 'INDIRECT', 'DTO', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserSysDate' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO');

UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='compName', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.CompName', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO', 'INDIRECT', 'DTO', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.CompName' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO');

-- HTH_USER_ACCESS_LINKED
DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID IN ('HTH_1216_LINK_EMAIL_en', 'HTH_1216_LINK_EMAIL_zh-Hant', 'HTH_1216_LINK_EMAIL_zh-Hans-CN', 'HTH_1216_LINK_SMS_en', 'HTH_1216_LINK_SMS_zh-Hant', 'HTH_1216_LINK_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID IN ('HTH_1216_LINK_EMAIL_en', 'HTH_1216_LINK_EMAIL_zh-Hant', 'HTH_1216_LINK_EMAIL_zh-Hans-CN', 'HTH_1216_LINK_SMS_en', 'HTH_1216_LINK_SMS_zh-Hant', 'HTH_1216_LINK_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit' AND COD_EVENT_ID='HTH_USER_ACCESS_LINKED';
DELETE FROM DIGX_EP_ACT_EVT_ACN_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit' AND COD_EVENT_ID='HTH_USER_ACCESS_LINKED';
DELETE FROM DIGX_EP_ACT_EVT_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit' AND COD_EVENT_ID='HTH_USER_ACCESS_LINKED';
DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID IN ('HTH_1216_LINK_EMAIL_en', 'HTH_1216_LINK_EMAIL_zh-Hant', 'HTH_1216_LINK_EMAIL_zh-Hans-CN', 'HTH_1216_LINK_SMS_en', 'HTH_1216_LINK_SMS_zh-Hant', 'HTH_1216_LINK_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE='HTH_USER_ACCESS_LINKED';
INSERT INTO DIGX_PM_EVENT_ALL_B
 (EVENT_CODE, EVENT_DESC, PRODUCT_CLASS, PRICING_EVENT_TYPE, ALERT_EVENT_TYPE, FINANCIAL_TYPE, PRICING_DOMAIN_CATEGORY,
 ACCT_DOMAIN_CATEGORY, ALERTS_FLAG, FEES_FLAG, DOCUMENTATION_FLAG, ACCOUNTING_FLAG, ACCT_ENTRY_FLAG, DOMAIN_CODE,
 REWARDS_FLAG, RESTRICT_OFFER_FLAG, TASK_NAME, FEE_RECOGNITION_BRANCH_TYPE, DOMAIN_GEN_BANK_LEVEL_FLAG, DOMAIN_SPE_BANK_LEVEL_FLAG, TXN_RES_FLAG)
VALUES ('HTH_USER_ACCESS_LINKED', 'HTH User Access LINK', NULL, NULL, NULL, NULL, NULL, NULL, 'Y','N','N','N','N',NULL,'N','N',NULL,NULL,'N','N','N');
INSERT INTO DIGX_EP_ACT_EVT_B (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP, DOMAIN_OBJECT_EXTN)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'HTH User Access LINK', 'OTHER', 'ONLINE', 'CZ');
INSERT INTO DIGX_EP_ACT_EVT_ACN_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, FLG_TRANSACTIONAL, ACTION_SOURCE, COD_DEC_ID, FLG_CONDITIONAL,
 COD_ACN_TMPL_ID, ALERT_NAME, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER,
 NUM_RETRY_CNT, FLG_RETRY_ALLOWED, EXPIRY_DATE, ALERT_TYPE, ALERT_DISPATCH_TYPE, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'N', NULL, '0', 'N', '1', 'HTH User Access LINK', USER, SYSDATE,
 USER, SYSDATE, 1, 0, 'N', DATE '2099-12-31', 'M', 'I', 'A', 'CZ');


INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'EMAIL', 'HTH 1216 LINK EMAIL en', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系統日期／時間：#userSysDate#</p><p>親愛的客戶：</p><p>請注意，貴公司已透過東亞企業網上銀行更新用戶 #userNameId# 的賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。</p><p>公司名稱：#compName#</p><p>感謝使用本行服務。</p><p>東亞銀行有限公司</p><p>此乃系統自動發出的電郵。如有查詢，請於服務時間內致電 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_EMAIL_en', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'en', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_en', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'EMAIL', 'HTH 1216 LINK EMAIL zh-Hant', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系統日期／時間：#userSysDate#</p><p>親愛的客戶：</p><p>請注意，貴公司已透過東亞企業網上銀行更新用戶 #userNameId# 的賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。</p><p>公司名稱：#compName#</p><p>感謝使用本行服務。</p><p>東亞銀行有限公司</p><p>此乃系統自動發出的電郵。如有查詢，請於服務時間內致電 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_EMAIL_zh-Hant', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hant', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hant', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'EMAIL', 'HTH 1216 LINK EMAIL zh-Hans-CN', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系统日期／时间：#userSysDate#</p><p>亲爱的客户：</p><p>请注意，贵公司已透过东亚企业网上银行更新用户 #userNameId# 的账户及服务权限资料。详情请登入您的账户或致电 (852) 2211 1321。</p><p>公司名称：#compName#</p><p>感谢使用本行服务。</p><p>东亚银行有限公司</p><p>此乃系统自动发出的电邮。如有查询，请于服务时间内致电 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_EMAIL_zh-Hans-CN', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hans-CN', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_EMAIL_zh-Hans-CN', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_SMS_en', 'SMS', 'HTH 1216 LINK SMS en', 'BCOH2H-1216', 'Your company has updated a user account and services access via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_SMS_en', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'en', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_SMS_zh-Hant', 'SMS', 'HTH 1216 LINK SMS zh-Hant', 'BCOH2H-1216', '貴公司已更新用戶賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_SMS_zh-Hant', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hant', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_LINK_SMS_zh-Hans-CN', 'SMS', 'HTH 1216 LINK SMS zh-Hans-CN', 'BCOH2H-1216', '贵公司已更新用户账户及服务权限资料。详情请登入您的账户或致电 (852) 2211 1321。', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit', 'HTH_USER_ACCESS_LINKED', 'A', 'HTH_1216_LINK_SMS_zh-Hans-CN', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hans-CN', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_ACT_B
 (COD_ACT_ID, TXT_ACT_NAME, TXT_ACT_DESC, MODULE_TYPE, FLG_IP_REQD, FLG_OP_REQD,
  FLG_LOG_REQD, TXT_LOG_CLASS, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
  LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HostToHostUserAccess.edit', 'HTH User Account and Service Access', 'PC', NULL, NULL, NULL, NULL,
 USER, SYSDATE, USER, SYSDATE, 1, 'A', 'CZ' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit');


UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='userNameId', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserNameId', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO', 'INDIRECT', 'DTO', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserNameId' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO');

UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='userSysDate', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserSysDate', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO', 'INDIRECT', 'DTO', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.UserSysDate' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO');

UPDATE DIGX_MD_SERVICE_ATTR SET TYP_DATA_AVAIL='INDIRECT', TYP_DATA_SRC='DTO', COD_ATTR_ID='compName', COD_SERVICE_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
 PARAMETER_NAME=NULL, REF_ENT_DEFN_ID=NULL, KEY_SERVICE_ATTR_ID=NULL, REF_FIELD_DEFN_ID = 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.CompName', OBJECT_STATUS='A'
WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO';
INSERT INTO DIGX_MD_SERVICE_ATTR
 (COD_SERVICE_ATTR_ID, TYP_DATA_AVAIL, TYP_DATA_SRC, COD_ATTR_ID, COD_SERVICE_ID, PARAMETER_NAME, REF_ENT_DEFN_ID,
 KEY_SERVICE_ATTR_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, REF_FIELD_DEFN_ID)
SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO', 'INDIRECT', 'DTO', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO.CompName' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO');

-- HTH_USER_ACCESS_UPDATED
DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID IN ('HTH_1216_UPDATE_EMAIL_en', 'HTH_1216_UPDATE_EMAIL_zh-Hant', 'HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'HTH_1216_UPDATE_SMS_en', 'HTH_1216_UPDATE_SMS_zh-Hant', 'HTH_1216_UPDATE_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID IN ('HTH_1216_UPDATE_EMAIL_en', 'HTH_1216_UPDATE_EMAIL_zh-Hant', 'HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'HTH_1216_UPDATE_SMS_en', 'HTH_1216_UPDATE_SMS_zh-Hant', 'HTH_1216_UPDATE_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit' AND COD_EVENT_ID='HTH_USER_ACCESS_UPDATED';
DELETE FROM DIGX_EP_ACT_EVT_ACN_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit' AND COD_EVENT_ID='HTH_USER_ACCESS_UPDATED';
DELETE FROM DIGX_EP_ACT_EVT_B WHERE COD_ACT_ID='com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit' AND COD_EVENT_ID='HTH_USER_ACCESS_UPDATED';
DELETE FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID IN ('HTH_1216_UPDATE_EMAIL_en', 'HTH_1216_UPDATE_EMAIL_zh-Hant', 'HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'HTH_1216_UPDATE_SMS_en', 'HTH_1216_UPDATE_SMS_zh-Hant', 'HTH_1216_UPDATE_SMS_zh-Hans-CN') AND DETERMINANT_VALUE='OBDX_BU';
DELETE FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE='HTH_USER_ACCESS_UPDATED';
INSERT INTO DIGX_PM_EVENT_ALL_B
 (EVENT_CODE, EVENT_DESC, PRODUCT_CLASS, PRICING_EVENT_TYPE, ALERT_EVENT_TYPE, FINANCIAL_TYPE, PRICING_DOMAIN_CATEGORY,
 ACCT_DOMAIN_CATEGORY, ALERTS_FLAG, FEES_FLAG, DOCUMENTATION_FLAG, ACCOUNTING_FLAG, ACCT_ENTRY_FLAG, DOMAIN_CODE,
 REWARDS_FLAG, RESTRICT_OFFER_FLAG, TASK_NAME, FEE_RECOGNITION_BRANCH_TYPE, DOMAIN_GEN_BANK_LEVEL_FLAG, DOMAIN_SPE_BANK_LEVEL_FLAG, TXN_RES_FLAG)
VALUES ('HTH_USER_ACCESS_UPDATED', 'HTH User Access UPDATE', NULL, NULL, NULL, NULL, NULL, NULL, 'Y','N','N','N','N',NULL,'N','N',NULL,NULL,'N','N','N');
INSERT INTO DIGX_EP_ACT_EVT_B (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP, DOMAIN_OBJECT_EXTN)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'HTH User Access UPDATE', 'OTHER', 'ONLINE', 'CZ');
INSERT INTO DIGX_EP_ACT_EVT_ACN_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, FLG_TRANSACTIONAL, ACTION_SOURCE, COD_DEC_ID, FLG_CONDITIONAL,
 COD_ACN_TMPL_ID, ALERT_NAME, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER,
 NUM_RETRY_CNT, FLG_RETRY_ALLOWED, EXPIRY_DATE, ALERT_TYPE, ALERT_DISPATCH_TYPE, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'N', NULL, '0', 'N', '1', 'HTH User Access UPDATE', USER, SYSDATE,
 USER, SYSDATE, 1, 0, 'N', DATE '2099-12-31', 'M', 'I', 'A', 'CZ');


INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'EMAIL', 'HTH 1216 UPDATE EMAIL en', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系統日期／時間：#userSysDate#</p><p>親愛的客戶：</p><p>請注意，貴公司已透過東亞企業網上銀行更新用戶 #userNameId# 的賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。</p><p>公司名稱：#compName#</p><p>感謝使用本行服務。</p><p>東亞銀行有限公司</p><p>此乃系統自動發出的電郵。如有查詢，請於服務時間內致電 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_EMAIL_en', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'en', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_en', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'EMAIL', 'HTH 1216 UPDATE EMAIL zh-Hant', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系統日期／時間：#userSysDate#</p><p>親愛的客戶：</p><p>請注意，貴公司已透過東亞企業網上銀行更新用戶 #userNameId# 的賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。</p><p>公司名稱：#compName#</p><p>感謝使用本行服務。</p><p>東亞銀行有限公司</p><p>此乃系統自動發出的電郵。如有查詢，請於服務時間內致電 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_EMAIL_zh-Hant', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hant', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hant', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'EMAIL', 'HTH 1216 UPDATE EMAIL zh-Hans-CN', 'BCOH2H-1216', '<p>System Date/Time: #userSysDate#</p><p>Dear Customer,</p><p>Please note that your company has updated the user account and service access information for #userNameId# via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.</p><p>Company Name: #compName#</p><p>Thank you for using our service.</p><p>THE BANK OF EAST ASIA, LIMITED</p><p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>(The Chinese version of this message is provided below.)</p><p>系统日期／时间：#userSysDate#</p><p>亲爱的客户：</p><p>请注意，贵公司已透过东亚企业网上银行更新用户 #userNameId# 的账户及服务权限资料。详情请登入您的账户或致电 (852) 2211 1321。</p><p>公司名称：#compName#</p><p>感谢使用本行服务。</p><p>东亚银行有限公司</p><p>此乃系统自动发出的电邮。如有查询，请于服务时间内致电 (852) 2211 1321。</p>', USER, SYSDATE, USER, SYSDATE, 1, 'A', 'BEA Corporate Online - User Account and Service Access Update(s)', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'EMAIL', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hans-CN', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'userNameId', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'userNameId', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userNameId.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'userSysDate', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'userSysDate', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.userSysDate.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_ATTR_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, ATTR_MASK, DATA_ATTR_ORDER, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'compName', 'D', NULL, 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_SRC_B
 (COD_MESS_TMPL_ID, COD_ATTR_ID, COD_ACT_ID, COD_SERVICE_ATTR_ID, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_EMAIL_zh-Hans-CN', 'compName', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit.compName.DTO', 'OBDX_BU');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_SMS_en', 'SMS', 'HTH 1216 UPDATE SMS en', 'BCOH2H-1216', 'Your company has updated a user account and services access via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_SMS_en', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'en', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_SMS_zh-Hant', 'SMS', 'HTH 1216 UPDATE SMS zh-Hant', 'BCOH2H-1216', '貴公司已更新用戶賬戶及服務權限資料。詳情請登入您的賬戶或致電 (852) 2211 1321。', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_SMS_zh-Hant', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hant', NULL, 'NA', 0, NULL, 'USER');

INSERT INTO DIGX_EP_MSG_TMPL_B
 (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL, CREATED_BY, CREATION_DATE,
 LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
VALUES ('HTH_1216_UPDATE_SMS_zh-Hans-CN', 'SMS', 'HTH 1216 UPDATE SMS zh-Hans-CN', 'BCOH2H-1216', '贵公司已更新用户账户及服务权限资料。详情请登入您的账户或致电 (852) 2211 1321。', USER, SYSDATE, USER, SYSDATE, 1, 'A', '', 'CZ', 'OBDX_BU');

INSERT INTO DIGX_EP_EVT_REC_B
 (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID, FLG_CONDITIONAL,
 SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN, LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit', 'HTH_USER_ACCESS_UPDATED', 'A', 'HTH_1216_UPDATE_SMS_zh-Hans-CN', 'SMS', '0', 'N', 'EXTERNAL', NULL, 'M', 'CZ', 'zh-Hans-CN', NULL, 'NA', 0, NULL, 'USER');

EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO HTH_ACCESS_CONFIG;
  RAISE;
END;
