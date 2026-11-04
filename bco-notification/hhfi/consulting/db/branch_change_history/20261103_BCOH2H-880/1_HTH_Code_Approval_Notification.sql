-- BCOH2H-880. Run as one PL/SQL block in the OBDX configuration schema (UTF-8).
-- Run AFTER older HTH password/template scripts; those scripts contain superseded text.
-- Resolve ALL installed EMAIL template IDs/locales from the two Code approval events.
-- No template-name assumptions, route creation or missing-template inserts.
-- Existing 880 EMAIL-only / explicit-recipient route normalization is retained.
-- Validate all bindings before writing; an error rolls back this whole block.
-- Re-runnable: no new rows, no changes to BCO/setup/reset templates or other units.
-- v81 Feature 6 supplies the same bilingual subject/body for both roles and all locales.
-- Runtime remains approval + 24 hours. The reviewed next-day wording requires separate
-- wording confirmation before customer release; this delta does not change Code expiry.
-- English 2211 1056 and Chinese 2211 1321 are verbatim from the reviewed document.
DECLARE
  c_activity CONSTANT VARCHAR2(200) := 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.activateOnUserApproval';
  v_event DIGX_EP_EVT_REC_B.COD_EVENT_ID%TYPE;
  v_count NUMBER;
  v_destination DIGX_EP_MSG_TMPL_B.DESTINATION_TYPE%TYPE;
  TYPE t_template_ids IS TABLE OF DIGX_EP_MSG_TMPL_B.COD_TMPL_ID%TYPE INDEX BY BINARY_INTEGER;
  v_templates t_template_ids;
  v_template_count PLS_INTEGER := 0;
  CURSOR c_bound_templates IS
    -- Include configured alternate templates as well as the main template.
    -- UNION ensures a template shared by these two events/locales is updated once.
    SELECT COD_MSG_TMPL_ID AS template_id FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_activity AND COD_ACTION_ID='A' AND TXT_DEST_TYP='EMAIL'
       AND COD_EVENT_ID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                            'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
    UNION
    SELECT UNSECURE_MSG_TMPL_ID AS template_id FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_activity AND COD_ACTION_ID='A' AND TXT_DEST_TYP='EMAIL'
       AND COD_EVENT_ID IN ('HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
                            'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT')
       AND TRIM(UNSECURE_MSG_TMPL_ID) IS NOT NULL;
  v_subject VARCHAR2(1000) := q'~BEA Corporate Online : H2H API Password Setup Code Approved
東亞企業網上銀行：銀企直連密碼設定碼已獲批准~';
  v_body CLOB := TO_CLOB(q'~<p>Dear Valued Customer,</p><p> </p>
<p>Thank you for choosing BEA. You can now set up your BEA H2H API password by following the steps below:</p><p> </p>
<p> 1. Visit the Bank's website.</p><p> </p>
<p> 2. Select "Hong Kong - BEA Corporate Online".</p><p> </p>
<p> 3. Log in to BEA Corporate Online using your User ID and Password.</p><p> </p>
<p> 4. Click "Set Up H2H API Password" from the pop-up reminder, or click "Profile" at the top right corner and select "H2H API Password".</p><p> </p>
<p> 5. Enter "New Password", "Confirm New Password" and the 6-digit "H2H API Password Setup Code" provided by an Authorised Signer or System Administrator for verification.</p><p> </p>
<p> Please note that the H2H API Password Setup Code is valid until 23:59:59 (UTC+8) on the following day.</p><p> </p>
<p> For enquiries, please call our Merchant Services Hotline on (852) 2211 1056 during office hours.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p> Yours faithfully,</p><p> </p>
<p> The Bank of East Asia, Limited</p><p> </p>
<p> If there are any discrepancies between the English and Chinese versions of this email, the English version shall apply and prevail.
This is a system-generated email – please do not  reply to this email or via any hyperlinks in the message.</p><p> </p>
<p>=======================================================================</p><p> </p>
<p> 尊貴的客戶：</p><p> </p>
<p> 感謝選用東亞銀行。閣下現可按照以下步驟設定東亞銀行H2H API密碼：</p><p> </p>
<p> 1. 瀏覽本行網站。</p><p> </p>
<p> 2. 選擇「香港 - 東亞企業網上銀行」。</p><p> </p>
<p> 3. 輸入用戶識別碼及密碼登入東亞企業網上銀行。</p><p> </p>
<p> 4. 於彈窗提示中按「設置H2H API密碼」，或按右上角的「簡介」後點擊「H2H API密碼」。</p><p> </p>
<p> 5. 輸入「新密碼」、「確認新密碼」及由獲授權人士或系統管理員提供的 6 位數字「H2H API密碼設定碼」以完成驗證。</p><p> </p>
<p>請注意，H2H API密碼設定碼將於翌日 23:59:59（UTC+8）失效。</p><p> </p>
<p>如有垂詢，請致電本行：(852) 2211 1321。</p><p> </p>
<p>感謝使用本行服務。</p><p> </p>
<p>=======================================================================</p><p> </p>
<p>謹啓</p><p> </p>
<p>東亞銀行有限公司</p><p> </p>
<p>中英文本文義如有歧異，概以英文本為準。此乃電腦系統發出之電郵，請勿回覆此電郵和電郵內的超連結。</p>~');
BEGIN
  SAVEPOINT HTH_880_CONFIG;
  -- Keep the existing background action. Do not guess a missing event or route.
  FOR role IN (SELECT 'USER' name FROM DUAL UNION ALL SELECT 'COMPANY' FROM DUAL) LOOP
    v_event := 'HTH_API_PASSWORD_CODE_APPROVED_' || role.name || '_EMAIL_EVENT';
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B
     WHERE COD_ACT_ID=c_activity AND COD_EVENT_ID=v_event AND COD_ACTION_ID='A'
       AND OBJECT_STATUS='A' AND FLG_TRANSACTIONAL='N';
    IF v_count <> 1 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: expected one active non-transactional Code approval action: ' || role.name);
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_activity AND COD_EVENT_ID=v_event AND COD_ACTION_ID='A'
       AND TXT_DEST_TYP='EMAIL';
    IF v_count = 0 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: no EMAIL route for event: ' || v_event);
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID=c_activity AND COD_EVENT_ID=v_event AND COD_ACTION_ID='A'
       AND TXT_DEST_TYP='EMAIL' AND TRIM(COD_MSG_TMPL_ID) IS NULL;
    IF v_count <> 0 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: EMAIL route has empty template ID: ' || v_event);
    END IF;
  END LOOP;

  -- Resolve and validate every distinct bound ID before any template/route changes.
  FOR bound IN c_bound_templates LOOP
    SELECT COUNT(*), MIN(DESTINATION_TYPE) INTO v_count, v_destination
      FROM DIGX_EP_MSG_TMPL_B
     WHERE COD_TMPL_ID=bound.template_id AND DETERMINANT_VALUE='OBDX_BU';
    IF v_count <> 1 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: bound template rows=' || v_count
        || ' in OBDX_BU: ' || bound.template_id || '; check event binding; no changes applied');
    END IF;
    IF v_destination IS NULL OR v_destination <> 'EMAIL' THEN
      RAISE_APPLICATION_ERROR(-20880, '880: bound template is not EMAIL: ' || bound.template_id);
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
     WHERE (COD_MSG_TMPL_ID=bound.template_id OR UNSECURE_MSG_TMPL_ID=bound.template_id)
       AND (COD_ACT_ID IS NULL OR COD_ACT_ID <> c_activity
         OR COD_ACTION_ID IS NULL OR COD_ACTION_ID <> 'A'
         OR TXT_DEST_TYP IS NULL OR TXT_DEST_TYP <> 'EMAIL'
         OR COD_EVENT_ID IS NULL OR COD_EVENT_ID NOT IN (
           'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT',
           'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT'));
    IF v_count <> 0 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: template is also referenced outside these Code EMAIL routes: '
        || bound.template_id || '; no changes applied');
    END IF;
    v_template_count := v_template_count + 1;
    v_templates(v_template_count) := bound.template_id;
  END LOOP;

  FOR i IN 1..v_template_count LOOP
    UPDATE DIGX_EP_MSG_TMPL_B SET TXT_SUBJECT_TMPL=v_subject, TXT_MSG_TMPL=v_body,
      OBJECT_STATUS='A', LAST_UPDATED_BY=USER, LAST_UPDATED_DATE=SYSDATE,
      OBJECT_VERSION_NUMBER=NVL(OBJECT_VERSION_NUMBER,0)+1
     WHERE COD_TMPL_ID=v_templates(i) AND DETERMINANT_VALUE='OBDX_BU'
       AND DESTINATION_TYPE='EMAIL';
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20880, '880: template changed during update: ' || v_templates(i));
    END IF;
  END LOOP;

  -- Retain the existing 880 routing change; preserve every installed template ID/locale.
  FOR role IN (SELECT 'USER' name FROM DUAL UNION ALL SELECT 'COMPANY' FROM DUAL) LOOP
    v_event := 'HTH_API_PASSWORD_CODE_APPROVED_' || role.name || '_EMAIL_EVENT';
    DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID=c_activity AND COD_EVENT_ID=v_event
      AND COD_ACTION_ID='A' AND (TXT_DEST_TYP IS NULL OR TXT_DEST_TYP <> 'EMAIL');
    UPDATE DIGX_EP_EVT_REC_B SET SUBSCRIBER_TYPE='EXTERNAL', SUBSCRIBER_VALUE='USER', RECIPIENT_TYPE=NULL,
      FLG_CONDITIONAL='N', COD_DEC_ID='0'
     WHERE COD_ACT_ID=c_activity AND COD_EVENT_ID=v_event AND COD_ACTION_ID='A'
       AND TXT_DEST_TYP='EMAIL';
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('880: updated ' || v_template_count
    || ' distinct bound EMAIL templates in OBDX_BU. Verify and COMMIT.');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO HTH_880_CONFIG;
  RAISE;
END;
