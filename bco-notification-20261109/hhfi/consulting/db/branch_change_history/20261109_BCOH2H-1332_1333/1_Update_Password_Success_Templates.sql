-- BCOH2H-1333 / BCOH2H-1332: update existing SETUP/RESET success templates only.
-- DBeaver: stop the failed script, open this whole file, disable auto-commit.
-- Select from DECLARE through the final END; and use Execute SQL Statement (Ctrl+Enter).
-- SQL*Plus/SQLcl: enter / on a separate line after this block to execute it.
-- One anonymous block, no local procedure: avoid client splitting inside declarations.
-- Use the OBDX configuration schema (UTF-8).
-- Run AFTER the original 6_HTH_API_Password_Notification.sql and
-- 8_HTH_API_Password_Reset_Notification.sql when initializing an environment.
-- Existing environments: run 2_Verify_Password_Success_Templates.sql first and export
-- the current subjects/bodies as a backup; run this block, verify, then COMMIT.
-- Resolve the installed IDs by activity/event/channel/locale, including alternate IDs.
-- Do not create IDs, change recipients/actions, update Code approval or BCO templates.
-- Missing routes/rows and templates shared outside these events stop the whole block.
-- Rerunnable: only changed content is updated. No automatic COMMIT.
-- v81 Feature 8 still says "Setup Success" / "set up" for RESET: retained verbatim.
-- English hotline 2211 1056 and Chinese 2211 1321 are also verbatim.
DECLARE
  c_base CONSTANT VARCHAR2(200) := 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.';
  TYPE t_change IS RECORD (
    template_id DIGX_EP_MSG_TMPL_B.COD_TMPL_ID%TYPE,
    destination DIGX_EP_MSG_TMPL_B.DESTINATION_TYPE%TYPE,
    subject DIGX_EP_MSG_TMPL_B.TXT_SUBJECT_TMPL%TYPE,
    body CLOB);
  TYPE t_changes IS TABLE OF t_change INDEX BY BINARY_INTEGER;
  v_changes t_changes;
  v_size PLS_INTEGER := 0;
  v_updated PLS_INTEGER := 0;
  c_setup_subject CONSTANT VARCHAR2(1000) := q'~BEA Corporate Online : H2H API Password Setup Success
東亞企業網上銀行：銀企直連密碼設定成功~';
  c_setup_body CONSTANT CLOB := TO_CLOB(q'~<p>Dear Valued Customer,</p><p> </p>
<p>Thank you for choosing BEA. Please be informed that you have successfully set up your H2H API Password via H2H API Password Setup Code.</p><p> </p> 
<p>You may now access H2H API services using this password. Please keep your password secure and only share it with authorized personnel who require access.</p><p> </p>
<p>For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>Yours faithfully,</p><p> </p>
<p>The Bank of East Asia, Limited</p><p> </p>
<p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p> </p> 
<p>This is a system-generated email – please do not reply to this email or via any hyperlinks in the message.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>尊貴的客戶：</p><p> </p>
<p>現特函通知，您已透過API密碼設定碼成功設定東亞銀行H2H API密碼。您現可使用該密碼使用H2H API服務。請妥善保管您的密碼，並僅與獲授權使用有關服務的人員分享。</p><p> </p>
<p>如有垂詢，請致電本行：(852) 2211 1321。</p><p> </p>
<p>感謝使用本行服務。</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>謹啓</p><p> </p>
<p>東亞銀行有限公司</p><p> </p>
<p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>~');
  c_reset_subject CONSTANT VARCHAR2(1000) := q'~BEA Corporate Online : H2H API Password Setup Success
東亞企業網上銀行：銀企直連密碼設定成功~';
  c_reset_body CONSTANT CLOB := TO_CLOB(q'~<p>Dear Valued Customer,</p><p> </p>
<p>Thank you for choosing BEA. Please be informed that you have successfully set up your H2H API Password via H2H API Password Setup Code.</p><p> </p> 
<p>You may now access H2H API services using this password. Please keep your password secure and only share it with authorized personnel who require access.</p><p> </p>
<p>For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>Yours faithfully,</p><p> </p>
<p>The Bank of East Asia, Limited</p><p> </p>
<p>If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.</p><p> </p> 
<p>This is a system-generated email – please do not reply to this email or via any hyperlinks in the message.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>尊貴的客戶：</p><p> </p>
<p>現特函通知，您已透過H2H API密碼設定碼成功設定東亞銀行H2H API密碼。您現可使用該密碼使用H2H API服務。請妥善保管您的密碼，並僅與獲授權使用有關服務的人員分享。</p><p> </p>
<p>如有垂詢，請致電本行：(852) 2211 1321。</p><p> </p>
<p>感謝使用本行服務。</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>謹啓</p><p> </p>
<p>東亞銀行有限公司</p><p> </p>
<p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>~');
  c_sms_en CONSTANT VARCHAR2(1000) := q'~Your company has successfully set up a BEA H2H API password. For details, please log into your account or call (852) 2211 1321.~';
  c_sms_tc CONSTANT VARCHAR2(1000) := q'~貴公司已成功設定東亞銀行H2H API密碼。如欲了解更多詳情，請登入你的賬戶或致電(852) 2211 1321。~';
  c_sms_sc CONSTANT VARCHAR2(1000) := q'~贵公司已成功设置东亚银行H2H API密码。如欲了解更多详情，请登陆你的账户或致电(852) 2211 1321。~';

  p_operation VARCHAR2(10);
  p_event VARCHAR2(100);
  p_destination VARCHAR2(10);
  p_locale VARCHAR2(20);
  p_subject DIGX_EP_MSG_TMPL_B.TXT_SUBJECT_TMPL%TYPE;
  p_body CLOB;
  v_count NUMBER;
  v_destination DIGX_EP_MSG_TMPL_B.DESTINATION_TYPE%TYPE;
  v_seen BOOLEAN;
  CURSOR c_bindings IS
    SELECT COD_MSG_TMPL_ID AS template_id FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_base || p_operation AND COD_EVENT_ID=p_event
       AND COD_ACTION_ID='A' AND TXT_DEST_TYP=p_destination AND LOCALE=p_locale
    UNION
    SELECT UNSECURE_MSG_TMPL_ID AS template_id FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_base || p_operation AND COD_EVENT_ID=p_event
       AND COD_ACTION_ID='A' AND TXT_DEST_TYP=p_destination AND LOCALE=p_locale
       AND TRIM(UNSECURE_MSG_TMPL_ID) IS NOT NULL;
  CURSOR c_routes IS
    SELECT op.operation, op.event_id, channel.destination, lang.locale
      FROM (SELECT 'setup' operation, 'HTH_API_PASSWORD_SETUP_SUCCESS' event_id FROM DUAL
            UNION ALL SELECT 'reset', 'HTH_API_PASSWORD_RESET_SUCCESS' FROM DUAL) op
      CROSS JOIN (SELECT 'EMAIL' destination FROM DUAL UNION ALL SELECT 'SMS' FROM DUAL) channel
      CROSS JOIN (SELECT 'en' locale FROM DUAL UNION ALL SELECT 'zh-Hant' FROM DUAL
                  UNION ALL SELECT 'zh-Hans-CN' FROM DUAL) lang;
BEGIN
  SAVEPOINT HTH_PASSWORD_SUCCESS_TMPL;
  -- Collect all 12 route variants and validate every change BEFORE writing.
  FOR route IN c_routes LOOP
    p_operation := route.operation;
    p_event := route.event_id;
    p_destination := route.destination;
    p_locale := route.locale;
    IF p_destination='EMAIL' THEN
      p_subject := CASE p_operation WHEN 'setup' THEN c_setup_subject ELSE c_reset_subject END;
      p_body := CASE p_operation WHEN 'setup' THEN c_setup_body ELSE c_reset_body END;
    ELSE
      p_subject := NULL;
      p_body := TO_CLOB(CASE p_locale WHEN 'en' THEN c_sms_en WHEN 'zh-Hant' THEN c_sms_tc ELSE c_sms_sc END);
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_base || p_operation AND COD_EVENT_ID=p_event
       AND COD_ACTION_ID='A' AND TXT_DEST_TYP=p_destination AND LOCALE=p_locale;
    IF v_count=0 THEN
      RAISE_APPLICATION_ERROR(-20333, 'Missing binding: ' || p_event || '/' || p_destination || '/' || p_locale);
    END IF;
    FOR r IN c_bindings LOOP
      SELECT COUNT(*), MIN(DESTINATION_TYPE) INTO v_count, v_destination
        FROM DIGX_EP_MSG_TMPL_B
       WHERE COD_TMPL_ID=r.template_id AND DETERMINANT_VALUE='OBDX_BU';
      IF v_count<>1 OR v_destination IS NULL OR v_destination<>p_destination THEN
        RAISE_APPLICATION_ERROR(-20333, 'Bound template missing/ambiguous/wrong channel: '
          || NVL(r.template_id, '<NULL>') || '; rows=' || v_count || '; inspect verification SQL');
      END IF;
      -- Do not change a template used by BCO, Code approval, another action or channel.
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
       WHERE (COD_MSG_TMPL_ID=r.template_id OR UNSECURE_MSG_TMPL_ID=r.template_id)
         AND (COD_ACT_ID IS NULL OR COD_EVENT_ID IS NULL
           OR NOT ((COD_ACT_ID=c_base || 'setup' AND COD_EVENT_ID='HTH_API_PASSWORD_SETUP_SUCCESS')
                OR (COD_ACT_ID=c_base || 'reset' AND COD_EVENT_ID='HTH_API_PASSWORD_RESET_SUCCESS'))
           OR COD_ACTION_ID IS NULL OR COD_ACTION_ID<>'A'
           OR TXT_DEST_TYP IS NULL OR TXT_DEST_TYP<>p_destination
           OR LOCALE IS NULL OR LOCALE NOT IN ('en','zh-Hant','zh-Hans-CN'));
      IF v_count<>0 THEN
        RAISE_APPLICATION_ERROR(-20333, 'Template also bound outside requested success routes: ' || r.template_id);
      END IF;
      v_seen := FALSE;
      FOR i IN 1..v_size LOOP
        IF v_changes(i).template_id=r.template_id THEN
          v_seen := TRUE;
          IF v_changes(i).destination<>p_destination
             OR (p_destination='EMAIL' AND v_changes(i).subject<>p_subject)
             OR DBMS_LOB.COMPARE(v_changes(i).body,p_body)<>0 THEN
            RAISE_APPLICATION_ERROR(-20333, 'Shared template requires different content: ' || r.template_id);
          END IF;
        END IF;
      END LOOP;
      IF NOT v_seen THEN
        v_size := v_size+1;
        v_changes(v_size).template_id := r.template_id;
        v_changes(v_size).destination := p_destination;
        v_changes(v_size).subject := p_subject;
        v_changes(v_size).body := p_body;
      END IF;
    END LOOP;
  END LOOP;
  FOR i IN 1..v_size LOOP
    -- SMS has no reviewed subject. Keep its existing subject/metadata unchanged.
    UPDATE DIGX_EP_MSG_TMPL_B
       SET TXT_MSG_TMPL=v_changes(i).body,
           TXT_SUBJECT_TMPL=CASE WHEN v_changes(i).destination='EMAIL'
             THEN v_changes(i).subject ELSE TXT_SUBJECT_TMPL END,
           LAST_UPDATED_BY=USER, LAST_UPDATED_DATE=SYSDATE,
           OBJECT_VERSION_NUMBER=NVL(OBJECT_VERSION_NUMBER,0)+1
     WHERE COD_TMPL_ID=v_changes(i).template_id AND DETERMINANT_VALUE='OBDX_BU'
       AND DESTINATION_TYPE=v_changes(i).destination
       AND (TXT_MSG_TMPL IS NULL OR DBMS_LOB.COMPARE(TXT_MSG_TMPL,v_changes(i).body)<>0
         OR (v_changes(i).destination='EMAIL'
           AND (TXT_SUBJECT_TMPL IS NULL OR TXT_SUBJECT_TMPL<>v_changes(i).subject)));
    v_updated := v_updated+SQL%ROWCOUNT;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('1333/1332: bound templates=' || v_size || ', content updated=' || v_updated
    || '. No recipient/event changes. Verify then COMMIT.');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO HTH_PASSWORD_SUCCESS_TMPL;
  RAISE;
END;
