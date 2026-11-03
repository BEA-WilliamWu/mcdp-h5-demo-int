-- BCOH2H-2110 / reviewed v81 Feature 9. Run this WHOLE block as UTF-8.
-- Backup existing HTH2110_* rows and the SMS country skip-list before deployment.
-- Six private templates x three locales; no BCO template/action/recipient is overwritten.
-- Prerequisite: the six exact BCO *_en templates and their attribute metadata exist.
-- Re-runnable DML only: one COMMIT, rollback this block on any error.
DECLARE
  c_activity CONSTANT VARCHAR2(200) := 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update';
  c_create CONSTANT VARCHAR2(200) := 'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create';
  c_skip CONSTANT VARCHAR2(100) := 'SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST';
  c_default CONSTANT VARCHAR2(500) := 'INWARD_REMITTANCE_ALERT_SUCCESS,TD_MODE1_NOTIFICATION,TD_MODE1_NOTIFICATION_SMS_EN,TD_MODE1_NOTIFICATION_SMS_TC,TD_MODE1_NOTIFICATION_SMS_SC';
  v_count NUMBER;
  v_template VARCHAR2(100);
  v_source VARCHAR2(200);
  v_subject VARCHAR2(4000);
  v_body CLOB;
  v_placeholder VARCHAR2(200);
  v_occurrence NUMBER;
  v_skip DIGX_CZ_FW_CONFIG_ALL_O.PROP_VALUE%TYPE;
  v_original_skip DIGX_CZ_FW_CONFIG_ALL_O.PROP_VALUE%TYPE;
  v_event DIGX_PM_EVENT_ALL_B%ROWTYPE;
  v_action DIGX_EP_ACT_EVT_ACN_B%ROWTYPE;
  v_tmpl DIGX_EP_MSG_TMPL_B%ROWTYPE;
  v_attr DIGX_EP_MSG_ATTR_B%ROWTYPE;
  v_src DIGX_EP_MSG_SRC_B%ROWTYPE;
  v_meta DIGX_MD_SERVICE_ATTR%ROWTYPE;
  CURSOR routes IS
    SELECT 'EMAIL_A' route_id, 'HTH_2110_CONTACT_UPDATE' event_id, 'INFO_UPDATE_BY_CORP_ADMIN' source_event,
           'EMAIL' destination, 'INFO_UPDATE_BY_CORP_ADMIN_EXTERNAL_USER_EMAIL_en' source_template, c_activity source_activity FROM DUAL
    UNION ALL SELECT 'EMAIL_B', 'HTH_2110_USER_EDIT', 'USER_MANAGEMENT_EDIT', 'EMAIL', 'USER_MANAGEMENT_EDIT_CORPORATE_USER_EMAIL_en', c_activity FROM DUAL
    UNION ALL SELECT 'SMS_A', 'HTH_2110_MOBILE_WELCOME', 'CORPORATEPLUS_WELCOME_MAIL', 'SMS', 'CorporatePlus Welcome Mail_EXTERNAL_USER_SMS_en', c_create FROM DUAL
    UNION ALL SELECT 'SMS_B', 'HTH_2110_EMAIL_UPDATED', 'USER_EMAIL_ADDRESS_UPDATE', 'SMS', 'User Email Address update_EXTERNAL_USER_SMS_en', c_activity FROM DUAL
    UNION ALL SELECT 'SMS_C', 'HTH_2110_USER_EDIT', 'USER_MANAGEMENT_EDIT', 'SMS', 'USER_MANAGEMENT_EDIT_CORPORATE_USER_SMS_en', c_activity FROM DUAL
    UNION ALL SELECT 'SMS_D', 'HTH_2110_OLD_MOBILE', 'USER_MOBILE_NUMBER_UPDATED_REMINDER', 'SMS', 'User Mobile Number Updated Reminder_EXTERNAL_USER_SMS_en', c_activity FROM DUAL
    ORDER BY route_id;
BEGIN
  SAVEPOINT HTH_2110_CONFIG;
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = c_activity AND OBJECT_STATUS = 'A';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110, '2110: missing/ambiguous user update activity'); END IF;
  FOR r IN routes LOOP
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_TMPL_B
     WHERE COD_TMPL_ID = r.source_template AND DETERMINANT_VALUE = 'OBDX_BU'
       AND DESTINATION_TYPE = r.destination AND OBJECT_STATUS = 'A';
    IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110, '2110: missing/ambiguous source ' || r.source_template); END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = r.source_event;
    IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110, '2110: missing/ambiguous source event ' || r.source_event); END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B
     WHERE COD_ACT_ID = r.source_activity AND COD_EVENT_ID = r.source_event AND COD_ACTION_ID = 'A' AND OBJECT_STATUS = 'A';
    IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110, '2110: missing/ambiguous source action ' || r.source_event); END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_CZ_FW_CONFIG_ALL_O
     WHERE PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU'
       AND PROP_ID IN ('EMAIL_DISPATCHER_ALERT_EVENTID_LIST','SMS_DISPATCHER_ALERT_EVENTID_LIST')
       AND INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',' || r.event_id || ',') > 0;
    IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20110, '2110: private event in recipient override list ' || r.event_id); END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_B WHERE COD_EVENT_ID = r.event_id AND COD_ACT_ID <> c_activity;
    IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20110, '2110: private event attached elsewhere'); END IF;
  END LOOP;
  DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID = c_activity
    AND COD_EVENT_ID IN ('HTH_2110_CONTACT_UPDATE','HTH_2110_USER_EDIT','HTH_2110_MOBILE_WELCOME','HTH_2110_EMAIL_UPDATED','HTH_2110_OLD_MOBILE');
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B WHERE SUBSTR(COD_MSG_TMPL_ID,1,8) = 'HTH2110_';
  IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20110, '2110: private template referenced by another route'); END IF;
  DELETE FROM DIGX_EP_MSG_SRC_B WHERE SUBSTR(COD_MESS_TMPL_ID,1,8) = 'HTH2110_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_EP_MSG_ATTR_B WHERE SUBSTR(COD_MESS_TMPL_ID,1,8) = 'HTH2110_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_EP_MSG_TMPL_B WHERE SUBSTR(COD_TMPL_ID,1,8) = 'HTH2110_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ID = c_activity AND INSTR(COD_SERVICE_ATTR_ID,c_activity || '.2110.') = 1;
  FOR r IN routes LOOP
    -- EMAIL_B and SMS_C intentionally share one event but have explicit per-notice channels.
    IF r.route_id <> 'SMS_C' THEN
      SELECT * INTO v_event FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = r.source_event;
      v_event.EVENT_CODE := r.event_id; v_event.EVENT_DESC := 'HTH combined contact update'; v_event.ALERTS_FLAG := 'Y';
      DELETE FROM DIGX_EP_ACT_EVT_ACN_B WHERE COD_ACT_ID = c_activity AND COD_EVENT_ID = r.event_id;
      DELETE FROM DIGX_EP_ACT_EVT_B WHERE COD_ACT_ID = c_activity AND COD_EVENT_ID = r.event_id;
      DELETE FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = r.event_id;
      INSERT INTO DIGX_PM_EVENT_ALL_B VALUES v_event;
      INSERT INTO DIGX_EP_ACT_EVT_B (COD_ACT_ID,COD_EVENT_ID,TXT_ACT_EVT_DESC,TXT_EVT_TYP,TXT_ACT_EVT_TYP,DOMAIN_OBJECT_EXTN)
      VALUES (c_activity,r.event_id,'HTH combined contact update','OTHER','ONLINE','CZ');
      SELECT * INTO v_action FROM DIGX_EP_ACT_EVT_ACN_B
       WHERE COD_ACT_ID = r.source_activity AND COD_EVENT_ID = r.source_event AND COD_ACTION_ID = 'A';
      v_action.COD_ACT_ID := c_activity; v_action.COD_EVENT_ID := r.event_id;
      v_action.FLG_TRANSACTIONAL := 'N'; v_action.ACTION_SOURCE := NULL;
      v_action.COD_DEC_ID := '0'; v_action.FLG_CONDITIONAL := 'N';
      v_action.ALERT_NAME := 'HTH combined contact update';
      v_action.ALERT_TYPE := 'M'; v_action.ALERT_DISPATCH_TYPE := 'I';
      v_action.OBJECT_STATUS := 'A'; v_action.EXPIRY_DATE := DATE '2099-12-31';
      v_action.NUM_RETRY_CNT := 0; v_action.FLG_RETRY_ALLOWED := 'N';
      v_action.CREATED_BY := USER; v_action.CREATION_DATE := SYSDATE;
      v_action.LAST_UPDATED_BY := USER; v_action.LAST_UPDATED_DATE := SYSDATE;
      v_action.OBJECT_VERSION_NUMBER := 1;
      INSERT INTO DIGX_EP_ACT_EVT_ACN_B VALUES v_action;
    END IF;
    FOR l IN (SELECT 'en' locale FROM DUAL UNION ALL SELECT 'zh-hant' FROM DUAL UNION ALL SELECT 'zh-hans-cn' FROM DUAL) LOOP
      v_template := 'HTH2110_' || r.route_id || '_' || l.locale;
      v_subject := NULL;
      IF r.route_id = 'EMAIL_A' THEN
        v_subject := q'~BEA Corporate Online: Confirmation of User's #engMailSubj# 東亞企業網上銀行：確認使用者#zhMailSubj#~';
        v_body := TO_CLOB(q'~<p>Dear Valued Customer,</p><p> </p>
<p>Please be informed that #engMailContent# of #profileUser# of your company has updated.</p><p> </p>
<p>For enquiries, please call us on (852) 2211 1321.<br />Thank you.</p><p> </p>
<p>Yours faithfully,<br />The Bank of East Asia, Limited</p><p> </p>
<p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail. 
This is a system-generated email – please do not reply to this email or via any hyperlinks in the message.</p><p> </p>
<p>(The Chinese version of this message is provided below.)</p><p> </p><p>尊貴的客戶：</p><p> </p>
<p>現特函通知，貴公司已更新#profileUser##zhMailContent#</p><p> </p>
<p>如有垂詢，請致電本行：(852) 2211 1321。<br />感謝使用本行服務。</p><p> </p>
<p>謹啓<br />東亞銀行有限公司</p><p> </p>
<p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>~');
      ELSIF r.route_id = 'EMAIL_B' THEN
        v_subject := q'~BEA Corporate Online – User Information Update 東亞企業網上銀行–用戶資料更新~';
        v_body := TO_CLOB(q'~<p>System Date/Time: #userSysDate#</p><p> </p>
<p>Dear Customer,</p><p> </p>
<p>Please note that your company has updated user information for #userNameId# via BEA Corporate Online. 
For details, please log into your account or call (852) 2211 1321.</p><p> </p>
<p>Company Name: #compName#</p><p> </p><p>Thank you for using our service.</p><p> </p>
<p>THE BANK OF EAST ASIA, LIMITED</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>This is a system-generated email. For enquiries, please call our hotline on (852) 2211 1321 during service hours.</p><p> </p>
<p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p> </p>
<p>(The Chinese version of this message is provided below.)</p><p> </p><p>系統日期／時間： #userSysDate#</p><p> </p>
<p>親愛的客戶：</p><p> </p>
<p>請注意，貴公司已在東亞企業網上銀行就#userNameId#更新用戶資料。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。</p><p> </p>
<p>公司名稱： #compName#</p>~') ||
      TO_CLOB(q'~<p> </p><p>感謝使用本行服務。</p><p> </p><p>東亞銀行有限公司</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>此乃電腦系統發出之電郵。如欲查詢，請於服務時間內致電本行熱線 (852) 2211 1321。</p><p> </p>
<p>中英文本文義如有歧異，概以英文本為準。</p>~');
      ELSIF r.route_id = 'SMS_A' THEN
        IF l.locale = 'en' THEN
          v_body := TO_CLOB(q'~Identity: #profileUser#\r\nThank you for registering your mobile no. We have activated mobile phone-related services for this no.~');
        ELSIF l.locale = 'zh-hant' THEN
          v_body := TO_CLOB(q'~確認訊息：#profileUser#\r\n多謝閣下登記流動電話號碼，本行已為此號碼啓動流動電話相關服務。~');
        ELSIF l.locale = 'zh-hans-cn' THEN
          v_body := TO_CLOB(q'~确认讯息：#profileUser#\r\n感谢您登记移动电话号码，本行已为此号码启动移动电话相关服务。~');
        END IF;
      ELSIF r.route_id = 'SMS_B' THEN
        IF l.locale = 'en' THEN
          v_body := TO_CLOB(q'~Identity: #profileUser#.\r\nUpdated Email Address: #emailId#.\r\nThank you for updating your email address.~');
        ELSIF l.locale = 'zh-hant' THEN
          v_body := TO_CLOB(q'~確認訊息：#profileUser#.\r\n更新電郵地址：#emailId#.\r\n多謝閣下更新電郵地址。~');
        ELSIF l.locale = 'zh-hans-cn' THEN
          v_body := TO_CLOB(q'~确认讯息：#profileUser#.\r\n更新电邮地址：#emailId#.\r\n感谢您更新电邮地址。~');
        END IF;
      ELSIF r.route_id = 'SMS_C' THEN
        IF l.locale = 'en' THEN
          v_body := TO_CLOB(q'~Your company has updated user information via BEA Corporate Online. For details, please log into your account or call (852) 2211 1321.~');
        ELSIF l.locale = 'zh-hant' THEN
          v_body := TO_CLOB(q'~貴公司已在東亞企業網上銀行更新用戶資料。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。~');
        ELSIF l.locale = 'zh-hans-cn' THEN
          v_body := TO_CLOB(q'~贵公司已在东亚企业网上银行更新用户数据。如欲了解更多详情，请登陆你的账户或致电(852) 2211 1321。~');
        END IF;
      ELSIF r.route_id = 'SMS_D' THEN
        IF l.locale = 'en' THEN
          v_body := TO_CLOB(q'~Identity: #profileUser#.\r\nYour mobile no. has been updated. Please use the new mobile no. for mobile phone-related services.~');
        ELSIF l.locale = 'zh-hant' THEN
          v_body := TO_CLOB(q'~確認訊息：#profileUser#\r\n閣下已更新流動電話號碼。請以新流動電話號碼使用相關服務。~');
        ELSIF l.locale = 'zh-hans-cn' THEN
          v_body := TO_CLOB(q'~确认讯息：#profileUser#\r\n您已更新移动电话号码。请以新移动电话号码使用相关服务。~');
        END IF;
      END IF;
      SELECT * INTO v_tmpl FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID = r.source_template AND DETERMINANT_VALUE = 'OBDX_BU';
      v_tmpl.COD_TMPL_ID := v_template; v_tmpl.TXT_SUBJECT_TMPL := v_subject; v_tmpl.TXT_MSG_TMPL := v_body;
      v_tmpl.CREATED_BY := USER; v_tmpl.CREATION_DATE := SYSDATE;
      v_tmpl.LAST_UPDATED_BY := USER; v_tmpl.LAST_UPDATED_DATE := SYSDATE; v_tmpl.OBJECT_VERSION_NUMBER := 1;
      INSERT INTO DIGX_EP_MSG_TMPL_B VALUES v_tmpl;
      -- Every placeholder in the reviewed body/subject requires exactly one active source mapping.
      FOR part IN 1..2 LOOP
        v_occurrence := 1;
        LOOP
          IF part = 1 THEN v_placeholder := REGEXP_SUBSTR(v_body, '#([[:alnum:]_.]+)#',1,v_occurrence,'c',1);
          ELSE v_placeholder := REGEXP_SUBSTR(v_subject, '#([[:alnum:]_.]+)#',1,v_occurrence,'c',1); END IF;
          EXIT WHEN v_placeholder IS NULL;
          SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_ATTR_B
           WHERE COD_MESS_TMPL_ID = r.source_template AND COD_ATTR_ID = v_placeholder AND DETERMINANT_VALUE = 'OBDX_BU';
          IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110,'2110: missing attribute ' || r.source_template || '/' || v_placeholder); END IF;
          SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_SRC_B s JOIN DIGX_MD_SERVICE_ATTR m ON m.COD_SERVICE_ATTR_ID = s.COD_SERVICE_ATTR_ID
           WHERE s.COD_MESS_TMPL_ID = r.source_template AND s.COD_ATTR_ID = v_placeholder
             AND s.COD_ACT_ID = r.source_activity AND s.DETERMINANT_VALUE = 'OBDX_BU' AND m.OBJECT_STATUS = 'A';
          IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20110,'2110: missing/ambiguous source mapping ' || v_placeholder); END IF;
          v_occurrence := v_occurrence + 1;
        END LOOP;
      END LOOP;
      FOR m IN (SELECT * FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID = r.source_template AND DETERMINANT_VALUE = 'OBDX_BU') LOOP
        v_attr := m; v_attr.COD_MESS_TMPL_ID := v_template;
        INSERT INTO DIGX_EP_MSG_ATTR_B VALUES v_attr;
        SELECT * INTO v_src FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID = r.source_template
          AND COD_ATTR_ID = m.COD_ATTR_ID AND COD_ACT_ID = r.source_activity AND DETERMINANT_VALUE = 'OBDX_BU';
        SELECT * INTO v_meta FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID = v_src.COD_SERVICE_ATTR_ID AND OBJECT_STATUS = 'A';
        v_meta.COD_SERVICE_ATTR_ID := c_activity || '.2110.' || r.route_id || '.' || l.locale || '.' || m.COD_ATTR_ID;
        v_meta.COD_SERVICE_ID := c_activity;
        v_meta.CREATED_BY := USER; v_meta.CREATION_DATE := SYSDATE;
        v_meta.LAST_UPDATED_BY := USER; v_meta.LAST_UPDATED_DATE := SYSDATE; v_meta.OBJECT_VERSION_NUMBER := 1;
        INSERT INTO DIGX_MD_SERVICE_ATTR VALUES v_meta;
        v_src.COD_MESS_TMPL_ID := v_template; v_src.COD_ACT_ID := c_activity; v_src.COD_SERVICE_ATTR_ID := v_meta.COD_SERVICE_ATTR_ID;
        INSERT INTO DIGX_EP_MSG_SRC_B VALUES v_src;
      END LOOP;
      INSERT INTO DIGX_EP_EVT_REC_B
        (COD_ACT_ID,COD_EVENT_ID,COD_ACTION_ID,COD_MSG_TMPL_ID,TXT_DEST_TYP,COD_DEC_ID,FLG_CONDITIONAL,
         SUBSCRIBER_TYPE,RECIPIENT_TYPE,ALERTTYPE,DOMAIN_OBJECT_EXTN,LOCALE,GROUPE_NAME,BANKER_TYPE,AMOUNT,UNSECURE_MSG_TMPL_ID,SUBSCRIBER_VALUE)
      VALUES (c_activity,r.event_id,'A',v_template,r.destination,'0','N','EXTERNAL',NULL,'M','CZ',l.locale,NULL,'NA',0,NULL,'USER');
    END LOOP;
  END LOOP;
  -- SMS snapshots carry number~country. Only private events bypass the live country lookup.
  SELECT COUNT(*) INTO v_count FROM DIGX_CZ_FW_CONFIG_ALL_O
   WHERE PROP_ID = c_skip AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU';
  IF v_count > 1 THEN RAISE_APPLICATION_ERROR(-20110,'2110: ambiguous SMS country skip-list'); END IF;
  IF v_count = 1 THEN
    SELECT PROP_VALUE INTO v_original_skip FROM DIGX_CZ_FW_CONFIG_ALL_O
     WHERE PROP_ID = c_skip AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU' FOR UPDATE;
  END IF;
  v_skip := NVL(v_original_skip,c_default);
  FOR e IN (SELECT 'HTH_2110_MOBILE_WELCOME' event_id FROM DUAL UNION ALL SELECT 'HTH_2110_EMAIL_UPDATED' FROM DUAL
            UNION ALL SELECT 'HTH_2110_USER_EDIT' FROM DUAL UNION ALL SELECT 'HTH_2110_OLD_MOBILE' FROM DUAL) LOOP
    IF INSTR(',' || REPLACE(UPPER(v_skip),' ','') || ',', ',' || e.event_id || ',') = 0 THEN
      v_skip := v_skip || ',' || e.event_id;
    END IF;
  END LOOP;
  IF v_count = 0 THEN
    INSERT INTO DIGX_CZ_FW_CONFIG_ALL_O
     (PROP_ID,PREFERENCE_NAME,PROP_VALUE,DETERMINANT_VALUE,CREATED_BY,CREATION_DATE,LAST_UPDATED_BY,LAST_UPDATED_DATE)
    VALUES (c_skip,'DayOneConfig',v_skip,'OBDX_BU',USER,SYSDATE,USER,SYSDATE);
  ELSIF v_original_skip IS NULL OR v_original_skip <> v_skip THEN
    UPDATE DIGX_CZ_FW_CONFIG_ALL_O SET PROP_VALUE=v_skip,LAST_UPDATED_BY=USER,LAST_UPDATED_DATE=SYSDATE
     WHERE PROP_ID=c_skip AND PREFERENCE_NAME='DayOneConfig' AND DETERMINANT_VALUE='OBDX_BU';
  END IF;
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B r JOIN DIGX_EP_MSG_TMPL_B t ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID
   WHERE r.COD_ACT_ID = c_activity AND SUBSTR(r.COD_MSG_TMPL_ID,1,8)='HTH2110_'
     AND r.SUBSCRIBER_TYPE='EXTERNAL' AND r.SUBSCRIBER_VALUE='USER' AND t.DETERMINANT_VALUE='OBDX_BU' AND t.OBJECT_STATUS='A';
  IF v_count <> 18 THEN RAISE_APPLICATION_ERROR(-20110,'2110: expected 18 locale/channel routes'); END IF;
  COMMIT;
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO HTH_2110_CONFIG;
  RAISE;
END;
