-- BCOH2H-597/1288: BM HTH Enable, Disable and Edit. Run the complete block in the OBDX configuration schema.
-- Source: 1.Customer Onboarding BM - Notification (Clear)-v12-20260917_192646.docx, #1/#2/#3.
-- Bilingual EN/TC email for every locale; EN/TC/SC SMS. Execute this file as UTF-8.
-- Re-runnable: one commit, rollback to this script's savepoint on any error.
-- Before first execution, export the three HTH event configurations and the country skip-list.
DECLARE
  c_base CONSTANT VARCHAR2(180) := 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostManagement.';
  c_skip CONSTANT VARCHAR2(100) := 'SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST';
  c_default CONSTANT VARCHAR2(500) := 'INWARD_REMITTANCE_ALERT_SUCCESS,TD_MODE1_NOTIFICATION,TD_MODE1_NOTIFICATION_SMS_EN,TD_MODE1_NOTIFICATION_SMS_TC,TD_MODE1_NOTIFICATION_SMS_SC';
  v_count NUMBER;
  v_activity VARCHAR2(200);
  v_event VARCHAR2(50);
  v_description VARCHAR2(100);
  v_template VARCHAR2(100);
  v_subject VARCHAR2(200);
  v_body VARCHAR2(4000);
  v_sms VARCHAR2(1000);
  v_sms_en VARCHAR2(1000);
  v_sms_tc VARCHAR2(1000);
  v_sms_sc VARCHAR2(1000);
  v_skip DIGX_CZ_FW_CONFIG_ALL_O.PROP_VALUE%TYPE;
  v_original_skip DIGX_CZ_FW_CONFIG_ALL_O.PROP_VALUE%TYPE;
BEGIN
  SAVEPOINT HTH_1288_CONFIG;
  -- Never let a shared dispatcher replace our explicit company address with the BM user.
  SELECT COUNT(*) INTO v_count FROM DIGX_CZ_FW_CONFIG_ALL_O
   WHERE PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU'
     AND PROP_ID IN ('EMAIL_DISPATCHER_ALERT_EVENTID_LIST', 'SMS_DISPATCHER_ALERT_EVENTID_LIST')
     AND (INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',HTH_API_SERVICE_SUBMIT_SUCCESS,') > 0
       OR INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',HTH_API_SERVICE_DISABLE_SUCCESS,') > 0
       OR INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',HTH_API_SERVICE_EDIT_SUCCESS,') > 0);
  IF v_count > 0 THEN
    RAISE_APPLICATION_ERROR(-20128, '1288: HTH event is in a dispatcher recipient override list; resolve before deployment');
  END IF;

  FOR a IN (SELECT 'submit' operation, 'ENABLE' suffix, 'SUBMIT' event_suffix FROM DUAL
            UNION ALL SELECT 'disable', 'DISABLE', 'DISABLE' FROM DUAL
            UNION ALL SELECT 'edit', 'EDIT', 'EDIT' FROM DUAL) LOOP
    v_activity := c_base || a.operation;
    v_event := 'HTH_API_SERVICE_' || a.event_suffix || '_SUCCESS';
    v_description := 'HTH company ' || a.operation || ' notification';
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_B
     WHERE COD_EVENT_ID = v_event AND COD_ACT_ID <> v_activity;
    IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20128, '1288: event attached to another activity: ' || v_event); END IF;

    -- Activity parent may be used by audit/other actions: preserve existing settings.
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = v_activity;
    IF v_count = 0 THEN
      INSERT INTO DIGX_EP_ACT_B
        (COD_ACT_ID, TXT_ACT_NAME, TXT_ACT_DESC, MODULE_TYPE, FLG_IP_REQD, FLG_OP_REQD,
         FLG_LOG_REQD, TXT_LOG_CLASS, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
         LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
      VALUES (v_activity, 'HostToHostManagement.' || a.operation, v_description, 'BO',
              NULL, NULL, NULL, NULL, USER, SYSDATE, USER, SYSDATE, 1, 'A', 'CZ');
    ELSE
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = v_activity AND OBJECT_STATUS = 'A';
      IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20128, '1288: existing HTH activity inactive/ambiguous'); END IF;
    END IF;

    UPDATE DIGX_PM_EVENT_ALL_B SET ALERTS_FLAG = 'Y', EVENT_DESC = v_description WHERE EVENT_CODE = v_event;
    IF SQL%ROWCOUNT = 0 THEN
      INSERT INTO DIGX_PM_EVENT_ALL_B
        (EVENT_CODE, EVENT_DESC, PRODUCT_CLASS, PRICING_EVENT_TYPE, ALERT_EVENT_TYPE,
         FINANCIAL_TYPE, PRICING_DOMAIN_CATEGORY, ACCT_DOMAIN_CATEGORY, ALERTS_FLAG,
         FEES_FLAG, DOCUMENTATION_FLAG, ACCOUNTING_FLAG, ACCT_ENTRY_FLAG, DOMAIN_CODE,
         REWARDS_FLAG, RESTRICT_OFFER_FLAG, TASK_NAME, FEE_RECOGNITION_BRANCH_TYPE,
         DOMAIN_GEN_BANK_LEVEL_FLAG, DOMAIN_SPE_BANK_LEVEL_FLAG, TXN_RES_FLAG)
      VALUES (v_event, v_description, NULL, NULL, NULL, NULL, NULL, NULL,
              'Y', 'N', 'N', 'N', 'N', NULL, 'N', 'N', NULL, NULL, 'N', 'N', 'N');
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_B WHERE COD_ACT_ID = v_activity AND COD_EVENT_ID = v_event;
    IF v_count = 0 THEN
      INSERT INTO DIGX_EP_ACT_EVT_B
        (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP, DOMAIN_OBJECT_EXTN)
      VALUES (v_activity, v_event, v_description, 'OTHER', 'ONLINE', 'CZ');
    ELSIF v_count <> 1 THEN
      RAISE_APPLICATION_ERROR(-20128, '1288: duplicate activity-event association');
    ELSE
      UPDATE DIGX_EP_ACT_EVT_B SET TXT_ACT_EVT_DESC = v_description, TXT_EVT_TYP = 'OTHER',
             TXT_ACT_EVT_TYP = 'ONLINE', DOMAIN_OBJECT_EXTN = 'CZ'
       WHERE COD_ACT_ID = v_activity AND COD_EVENT_ID = v_event;
    END IF;

    -- Replace only these three HTH events and the supported language routes.
    DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID = v_activity AND COD_EVENT_ID = v_event
      AND COD_ACTION_ID = 'A' AND LOCALE IN ('en', 'zh-hant', 'zh-hans-cn') AND TXT_DEST_TYP IN ('EMAIL', 'SMS');
    UPDATE DIGX_EP_ACT_EVT_ACN_B
       SET FLG_TRANSACTIONAL = 'N', ACTION_SOURCE = NULL, COD_DEC_ID = '0', FLG_CONDITIONAL = 'N',
           COD_ACN_TMPL_ID = '1', ALERT_NAME = v_description, LAST_UPDATED_BY = USER, LAST_UPDATED_DATE = SYSDATE,
           OBJECT_VERSION_NUMBER = NVL(OBJECT_VERSION_NUMBER, 0) + 1, NUM_RETRY_CNT = 0, FLG_RETRY_ALLOWED = 'N',
           EXPIRY_DATE = DATE '2099-12-31', ALERT_TYPE = 'M', ALERT_DISPATCH_TYPE = 'I', OBJECT_STATUS = 'A', DOMAIN_OBJECT_EXTN = 'CZ'
     WHERE COD_ACT_ID = v_activity AND COD_EVENT_ID = v_event AND COD_ACTION_ID = 'A';
    IF SQL%ROWCOUNT > 1 THEN RAISE_APPLICATION_ERROR(-20128, '1288: duplicate Alert action'); END IF;
    IF SQL%ROWCOUNT = 0 THEN
      INSERT INTO DIGX_EP_ACT_EVT_ACN_B
      (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, FLG_TRANSACTIONAL, ACTION_SOURCE,
       COD_DEC_ID, FLG_CONDITIONAL, COD_ACN_TMPL_ID, ALERT_NAME, CREATED_BY,
       CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER,
       NUM_RETRY_CNT, FLG_RETRY_ALLOWED, EXPIRY_DATE, ALERT_TYPE,
       ALERT_DISPATCH_TYPE, OBJECT_STATUS, DOMAIN_OBJECT_EXTN)
    VALUES (v_activity, v_event, 'A', 'N', NULL, '0', 'N', '1', v_description,
            USER, SYSDATE, USER, SYSDATE, 1, 0, 'N', DATE '2099-12-31', 'M', 'I', 'A', 'CZ');
    END IF;
    -- FLG_TRANSACTIONAL=N uses the existing async Alert pipeline after persisted activity.
    -- Static content needs no new common DTO or message attribute/source mappings.
    IF a.operation = 'submit' THEN
      v_subject := q'[BEA Corporate Online: H2H API Service Activated 東亞企業網上銀行：H2H API服務已啟用]';
      v_body := q'[<p>Dear Valued Customer,</p><p>Please be informed that H2H API service has been activated for your company.</p><p>Please complete the remaining setup and configuration activities in accordance with the Bank's setup guidance before using the H2H API service.</p><p>For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p>Yours faithfully,</p><p>The Bank of East Asia, Limited</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>This is a system-generated email. Please do not reply to this email or via any hyperlinks in the message.</p><p>尊貴的客戶：</p><p>現特函通知，貴公司的H2H API服務已啟用。</p><p>請根據本行提供的設定指引完成所需設定及配置程序，方可使用H2H API服務。</p><p>如有查詢，請於辦公時間內致電本行商戶服務熱線：(852) 2211 1056。</p><p>謹啟</p><p>東亞銀行有限公司</p><p>中英文本文義如有歧異，概以英文本為準。</p><p>此乃電腦系統發出之電郵，請勿回覆此電郵或電郵內的超連結。</p>]';
      v_sms_en := q'[Your company's BEA H2H API service has been activated. Please complete the required setup and configuration in accordance with the Bank's setup guidance. For details, please log into your account or call (852) 2211 1321.]';
      v_sms_tc := q'[貴公司的東亞銀行H2H API服務已啟用。請根據本行提供的設定指引完成所需設定及配置程序。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。]';
      v_sms_sc := q'[贵公司的东亚银行H2H API服务已启用。请根据本行提供的设置指引完成所需设置及配置程序。如欲了解更多详情，请登录你的账户或致电(852) 2211 1321。]';
    ELSIF a.operation = 'disable' THEN
      v_subject := q'[BEA Corporate Online: H2H API Service Deactivated 東亞企業網上銀行：H2H API服務已停用]';
      v_body := q'[<p>Dear Valued Customer,</p><p>Please be informed that H2H API service has been deactivated for your company.</p><p>Your company will no longer be able to access or use H2H API services. Should your company wish to resume H2H API services in the future, please contact your Relationship Manager or the Bank for assistance.</p><p>For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p>Yours faithfully,</p><p>The Bank of East Asia, Limited</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>This is a system-generated email. Please do not reply to this email or via any hyperlinks in the message.</p><p>尊貴的客戶：</p><p>現特函通知，貴公司的H2H API服務已停用。</p><p>貴公司將不能再使用H2H API服務。如貴公司日後需要重新啟用H2H API服務，請聯絡客戶經理或本行辦理相關手續。</p><p>如有查詢，請於辦公時間內致電本行商戶服務熱線：(852) 2211 1056。</p><p>謹啟</p><p>東亞銀行有限公司</p><p>中英文本文義如有歧異，概以英文本為準。</p><p>此乃電腦系統發出之電郵，請勿回覆此電郵或電郵內的超連結。</p>]';
      v_sms_en := q'[Your company's BEA H2H API service has been deactivated. For details, please log into your account or call (852) 2211 1321.]';
      v_sms_tc := q'[貴公司的東亞銀行H2H API服務已停用。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。]';
      v_sms_sc := q'[贵公司的东亚银行H2H API服务已停用。详情请参阅电邮。如欲了解更多详情，请登录你的账户或致电(852) 2211 1321。]';
    ELSIF a.operation = 'edit' THEN
      v_subject := q'[BEA Corporate Online: H2H API Service Configuration Updated 東亞企業網上銀行：H2H API服務設定已更新]';
      v_body := q'[<p>Dear Valued Customer,</p><p>Please be informed that H2H API service configuration for your company has been updated.</p><p>For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p>Yours faithfully,</p><p>The Bank of East Asia, Limited</p><p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p>This is a system-generated email. Please do not reply to this email or via any hyperlinks in the message.</p><p>尊貴的客戶：</p><p>現特函通知，貴公司的H2H API服務設定已更新。</p><p>如有查詢，請於辦公時間內致電本行商戶服務熱線：(852) 2211 1056。</p><p>謹啟</p><p>東亞銀行有限公司</p><p>中英文本文義如有歧異，概以英文本為準。</p><p>此乃電腦系統發出之電郵，請勿回覆此電郵或電郵內的超連結。</p>]';
      v_sms_en := q'[Your company's BEA H2H API service configuration has been updated. For details, please log into your account or call (852) 2211 1321.]';
      v_sms_tc := q'[貴公司的東亞銀行H2H API服務設定已更新。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。]';
      v_sms_sc := q'[贵公司的东亚银行H2H API服务设置已更新。如欲了解更多详情，请登录你的账户或致电(852) 2211 1321。]';
    END IF;
    FOR l IN (SELECT 'en' locale FROM DUAL UNION ALL SELECT 'zh-hant' FROM DUAL
              UNION ALL SELECT 'zh-hans-cn' FROM DUAL) LOOP
      v_sms := CASE l.locale WHEN 'zh-hant' THEN v_sms_tc WHEN 'zh-hans-cn' THEN v_sms_sc ELSE v_sms_en END;
      FOR d IN (SELECT 'EMAIL' destination FROM DUAL UNION ALL SELECT 'SMS' FROM DUAL) LOOP
        v_template := 'HTH1288_' || a.suffix || '_' || d.destination || '_' || l.locale;
        SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B WHERE COD_MSG_TMPL_ID = v_template;
        IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20128, '1288: template referenced outside this event: ' || v_template); END IF;
        DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID = v_template AND DETERMINANT_VALUE = 'OBDX_BU';
        DELETE FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID = v_template AND DETERMINANT_VALUE = 'OBDX_BU';
        UPDATE DIGX_EP_MSG_TMPL_B
           SET DESTINATION_TYPE = d.destination, MSG_TMPL_NAME = v_description, MSG_TMPL_DESC = v_description,
               TXT_MSG_TMPL = CASE WHEN d.destination = 'EMAIL' THEN v_body ELSE v_sms END,
               TXT_SUBJECT_TMPL = CASE WHEN d.destination = 'EMAIL' THEN v_subject ELSE NULL END,
               OBJECT_STATUS = 'A', DOMAIN_OBJECT_EXTN = 'CZ', LAST_UPDATED_BY = USER,
               LAST_UPDATED_DATE = SYSDATE, OBJECT_VERSION_NUMBER = NVL(OBJECT_VERSION_NUMBER, 0) + 1
         WHERE COD_TMPL_ID = v_template AND DETERMINANT_VALUE = 'OBDX_BU';
        IF SQL%ROWCOUNT > 1 THEN RAISE_APPLICATION_ERROR(-20128, '1288: duplicate message template: ' || v_template); END IF;
        IF SQL%ROWCOUNT = 0 THEN
          INSERT INTO DIGX_EP_MSG_TMPL_B
            (COD_TMPL_ID, DESTINATION_TYPE, MSG_TMPL_NAME, MSG_TMPL_DESC, TXT_MSG_TMPL,
             CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_VERSION_NUMBER,
             OBJECT_STATUS, TXT_SUBJECT_TMPL, DOMAIN_OBJECT_EXTN, DETERMINANT_VALUE)
          VALUES (v_template, d.destination, v_description, v_description,
                  CASE WHEN d.destination = 'EMAIL' THEN v_body ELSE v_sms END,
                  USER, SYSDATE, USER, SYSDATE, 1, 'A',
                  CASE WHEN d.destination = 'EMAIL' THEN v_subject ELSE NULL END, 'CZ', 'OBDX_BU');
        END IF;
        INSERT INTO DIGX_EP_EVT_REC_B
          (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID,
           FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN,
           LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
        VALUES (v_activity, v_event, 'A', v_template, d.destination, '0',
                'N', 'EXTERNAL', NULL, 'M', 'CZ', l.locale, NULL, 'NA', 0, NULL, 'USER');
      END LOOP;
    END LOOP;
  END LOOP;

  -- Only append HTH's three events; retain the environment's full list or the dispatcher's default.
  SELECT COUNT(*) INTO v_count FROM DIGX_CZ_FW_CONFIG_ALL_O
   WHERE PROP_ID = c_skip AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU';
  IF v_count > 1 THEN RAISE_APPLICATION_ERROR(-20128, '1288: ambiguous SMS country skip-list'); END IF;
  IF v_count = 1 THEN
    SELECT PROP_VALUE INTO v_original_skip FROM DIGX_CZ_FW_CONFIG_ALL_O
     WHERE PROP_ID = c_skip AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU' FOR UPDATE;
  END IF;
  v_skip := NVL(v_original_skip, c_default);
  FOR e IN (SELECT 'HTH_API_SERVICE_SUBMIT_SUCCESS' event_id FROM DUAL
            UNION ALL SELECT 'HTH_API_SERVICE_DISABLE_SUCCESS' FROM DUAL
            UNION ALL SELECT 'HTH_API_SERVICE_EDIT_SUCCESS' FROM DUAL) LOOP
    IF INSTR(',' || REPLACE(UPPER(v_skip), ' ', '') || ',', ',' || e.event_id || ',') = 0 THEN
      v_skip := v_skip || ',' || e.event_id;
    END IF;
  END LOOP;
  IF v_count = 0 THEN
    INSERT INTO DIGX_CZ_FW_CONFIG_ALL_O
      (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
    VALUES (c_skip, 'DayOneConfig', v_skip, 'OBDX_BU', USER, SYSDATE, USER, SYSDATE);
  ELSIF v_original_skip IS NULL OR v_original_skip <> v_skip THEN
    UPDATE DIGX_CZ_FW_CONFIG_ALL_O SET PROP_VALUE = v_skip, LAST_UPDATED_BY = USER, LAST_UPDATED_DATE = SYSDATE
     WHERE PROP_ID = c_skip AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU';
  END IF;
  -- Eighteen supported language routes must point to active templates before committing.
  SELECT COUNT(*) INTO v_count
    FROM DIGX_EP_EVT_REC_B r JOIN DIGX_EP_MSG_TMPL_B t
      ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE = 'OBDX_BU' AND t.DESTINATION_TYPE = r.TXT_DEST_TYP
   WHERE r.COD_EVENT_ID IN ('HTH_API_SERVICE_SUBMIT_SUCCESS', 'HTH_API_SERVICE_DISABLE_SUCCESS', 'HTH_API_SERVICE_EDIT_SUCCESS')
     AND r.COD_ACT_ID IN (c_base || 'submit', c_base || 'disable', c_base || 'edit')
     AND r.COD_ACTION_ID = 'A' AND r.LOCALE IN ('en', 'zh-hant', 'zh-hans-cn') AND r.SUBSCRIBER_TYPE = 'EXTERNAL'
     AND r.TXT_DEST_TYP IN ('EMAIL', 'SMS') AND t.OBJECT_STATUS = 'A';
  IF v_count <> 18 THEN RAISE_APPLICATION_ERROR(-20128, '1288: expected eighteen company notification routes'); END IF;
  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_1288_CONFIG;
    RAISE;
END;
