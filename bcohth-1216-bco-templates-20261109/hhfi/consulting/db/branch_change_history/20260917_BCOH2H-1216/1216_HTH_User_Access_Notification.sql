-- BCOH2H-1216. Execute this complete Oracle block in the OBDX configuration schema.
-- Revised 2026-11-09: reuse the installed BCO template IDs, not HTH text copies.
-- Supports an existing 1216 installation and first-time installation. No automatic COMMIT.
-- Disable client auto-commit. Re-runnable; rollback this block on failure. Verify then COMMIT.
-- BCO templates/attributes/source mappings/events/actions/recipient rows are READ ONLY.
-- Keep old HTH1216_* templates and their metadata for already queued notifications.
-- Requires the existing HthUserAccessNotification user/company split; no Java change in this revision.
DECLARE
  c_source CONSTANT VARCHAR2(200) := 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update';
  c_bco_event CONSTANT VARCHAR2(40) := 'USER_ACCOUNT_ACCESS_UPDATE';
  c_event CONSTANT VARCHAR2(40) := 'HTH_USER_ACCOUNT_ACCESS_UPDATE';
  c_company_event CONSTANT VARCHAR2(40) := 'HTH_USER_ACCOUNT_ACCESS_COMPANY';
  c_base CONSTANT VARCHAR2(180) := 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.';
  -- BCO legitimately has both CORPORATE/USER and PARTY/CUSTOMER templates per channel/locale.
  -- Target user + final approver use the user text; the company receives its own email text only.
  CURSOR c_routes IS
    SELECT c_event event_id, 'USER' recipient_group, 'CORPORATE' subscriber_type,
           'USER' subscriber_value, 'EMAIL' dest, CAST(NULL AS VARCHAR2(8)) template_prefix FROM DUAL
    UNION ALL
    SELECT c_event, 'USER', 'CORPORATE', 'USER', 'SMS', NULL FROM DUAL
    UNION ALL
    SELECT c_company_event, 'COMPANY', 'PARTY', 'CUSTOMER', 'EMAIL', 'COMPANY_' FROM DUAL;
  CURSOR c_templates(p_type VARCHAR2, p_value VARCHAR2, p_dest VARCHAR2) IS
    SELECT DISTINCT TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID, UNSECURE_MSG_TMPL_ID
      FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event AND COD_ACTION_ID = 'A'
       AND SUBSCRIBER_TYPE = p_type AND SUBSCRIBER_VALUE = p_value AND TXT_DEST_TYP = p_dest;
  v_count NUMBER;
  v_activity VARCHAR2(200);
  v_act DIGX_EP_ACT_B%ROWTYPE;
  v_event DIGX_PM_EVENT_ALL_B%ROWTYPE;
  v_action DIGX_EP_ACT_EVT_ACN_B%ROWTYPE;

  PROCEDURE bind_template(p_template VARCHAR2, p_destination VARCHAR2, p_suffix VARCHAR2) IS
    v_tmpl DIGX_EP_MSG_TMPL_B%ROWTYPE;
    v_src DIGX_EP_MSG_SRC_B%ROWTYPE;
    v_meta DIGX_MD_SERVICE_ATTR%ROWTYPE;
    v_placeholder VARCHAR2(200);
    v_occurrence NUMBER;
    v_count NUMBER;
  BEGIN
    SELECT * INTO v_tmpl FROM DIGX_EP_MSG_TMPL_B
     WHERE COD_TMPL_ID=p_template AND DETERMINANT_VALUE='OBDX_BU';
    IF NVL(v_tmpl.OBJECT_STATUS,'?')<>'A' OR NVL(v_tmpl.DESTINATION_TYPE,'?')<>p_destination THEN
      RAISE_APPLICATION_ERROR(-20116, '1216: inactive/mismatched BCO template ' || p_template);
    END IF;
    -- Reuse the BCO attribute definitions as well as its exact subject/body.
    FOR part IN 1..2 LOOP
      v_occurrence := 1;
      LOOP
        IF part=1 THEN
          v_placeholder := REGEXP_SUBSTR(v_tmpl.TXT_MSG_TMPL, '#([[:alnum:]_.]+)#', 1, v_occurrence, 'c', 1);
        ELSE
          v_placeholder := REGEXP_SUBSTR(v_tmpl.TXT_SUBJECT_TMPL, '#([[:alnum:]_.]+)#', 1, v_occurrence, 'c', 1);
        END IF;
        EXIT WHEN v_placeholder IS NULL;
        SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_ATTR_B
         WHERE COD_MESS_TMPL_ID=p_template AND COD_ATTR_ID=v_placeholder AND DETERMINANT_VALUE='OBDX_BU';
        IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: missing BCO placeholder metadata: ' || v_placeholder); END IF;
        v_occurrence := v_occurrence+1;
      END LOOP;
    END LOOP;
    FOR m IN (SELECT * FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID=p_template AND DETERMINANT_VALUE='OBDX_BU') LOOP
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_SRC_B s JOIN DIGX_MD_SERVICE_ATTR d
        ON d.COD_SERVICE_ATTR_ID=s.COD_SERVICE_ATTR_ID
       WHERE s.COD_MESS_TMPL_ID=p_template AND s.COD_ATTR_ID=m.COD_ATTR_ID
         AND s.COD_ACT_ID=c_source AND s.DETERMINANT_VALUE='OBDX_BU' AND d.OBJECT_STATUS='A';
      IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: BCO attribute source missing/ambiguous: ' || p_template || '/' || m.COD_ATTR_ID); END IF;
      FOR a IN (SELECT 'submit' operation FROM DUAL UNION ALL SELECT 'edit' FROM DUAL) LOOP
        v_activity := c_base || a.operation;
        SELECT * INTO v_src FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID=p_template
          AND COD_ATTR_ID=m.COD_ATTR_ID AND COD_ACT_ID=c_source AND DETERMINANT_VALUE='OBDX_BU';
        SELECT * INTO v_meta FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID=v_src.COD_SERVICE_ATTR_ID;
        -- Separate namespace: never overwrite metadata used by the old HTH1216_* copies.
        -- UserManagementActivityLogDTO fields/source type are the same as BCO.
        v_meta.COD_SERVICE_ATTR_ID := v_activity || '.1216.bco.' || p_suffix || '.' || m.COD_ATTR_ID;
        v_meta.COD_SERVICE_ID := v_activity;
        v_meta.CREATED_BY := USER; v_meta.CREATION_DATE := SYSDATE;
        v_meta.LAST_UPDATED_BY := USER; v_meta.LAST_UPDATED_DATE := SYSDATE;
        v_meta.OBJECT_VERSION_NUMBER := 1;
        SELECT COUNT(*) INTO v_count FROM DIGX_MD_SERVICE_ATTR
         WHERE COD_SERVICE_ATTR_ID=v_meta.COD_SERVICE_ATTR_ID AND (COD_SERVICE_ID IS NULL OR COD_SERVICE_ID<>v_activity);
        IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20116, '1216: HTH metadata ID belongs to another service'); END IF;
        SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_SRC_B
         WHERE COD_SERVICE_ATTR_ID=v_meta.COD_SERVICE_ATTR_ID
           AND (COD_ACT_ID IS NULL OR COD_ACT_ID<>v_activity
             OR DETERMINANT_VALUE IS NULL OR DETERMINANT_VALUE<>'OBDX_BU'
             OR COD_MESS_TMPL_ID IS NULL OR COD_MESS_TMPL_ID<>p_template);
        IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20116, '1216: HTH metadata ID is used by another template/activity'); END IF;
        SELECT COUNT(*) INTO v_count FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID=v_meta.COD_SERVICE_ATTR_ID;
        IF v_count=0 THEN
          INSERT INTO DIGX_MD_SERVICE_ATTR VALUES v_meta;
        ELSE
          UPDATE DIGX_MD_SERVICE_ATTR SET ROW=v_meta
           WHERE COD_SERVICE_ATTR_ID=v_meta.COD_SERVICE_ATTR_ID AND COD_SERVICE_ID=v_activity;
        END IF;
        -- Only the HTH source mapping for this shared template is replaced.
        -- BCO source rows and old private-template rows remain available.
        DELETE FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID=p_template AND COD_ATTR_ID=m.COD_ATTR_ID
          AND COD_ACT_ID=v_activity AND DETERMINANT_VALUE='OBDX_BU';
        v_src.COD_ACT_ID := v_activity;
        v_src.COD_SERVICE_ATTR_ID := v_meta.COD_SERVICE_ATTR_ID;
        INSERT INTO DIGX_EP_MSG_SRC_B VALUES v_src;
      END LOOP;
    END LOOP;
  END;
BEGIN
  SAVEPOINT HTH_1216_CONFIG;

  -- Require an unambiguous, usable BCO source before making any changes.
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = c_source AND OBJECT_STATUS = 'A';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: BCO UserAccountAccess.update activity missing/inactive'); END IF;
  SELECT COUNT(*) INTO v_count FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = c_bco_event;
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: BCO USER_ACCOUNT_ACCESS_UPDATE event missing'); END IF;
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B
   WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event AND COD_ACTION_ID = 'A' AND OBJECT_STATUS = 'A'
     AND (EXPIRY_DATE IS NULL OR EXPIRY_DATE > SYSDATE);
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: BCO account access Alert action missing/inactive'); END IF;

  FOR route IN c_routes LOOP
    -- These are unconditional notifications; do not silently discard a BCO decision rule.
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event AND COD_ACTION_ID = 'A'
       AND SUBSCRIBER_TYPE = route.subscriber_type AND SUBSCRIBER_VALUE = route.subscriber_value
       AND TXT_DEST_TYP = route.dest AND NVL(FLG_CONDITIONAL, '?') <> 'N';
    IF v_count > 0 THEN
      RAISE_APPLICATION_ERROR(-20116, '1216: conditional BCO source requires review: ' || route.recipient_group || '/' || route.dest);
    END IF;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B r JOIN DIGX_EP_MSG_TMPL_B t
      ON t.COD_TMPL_ID = r.COD_MSG_TMPL_ID AND t.DETERMINANT_VALUE = 'OBDX_BU'
     WHERE r.COD_ACT_ID = c_source AND r.COD_EVENT_ID = c_bco_event AND r.COD_ACTION_ID = 'A'
       AND r.SUBSCRIBER_TYPE = route.subscriber_type AND r.SUBSCRIBER_VALUE = route.subscriber_value
       AND r.TXT_DEST_TYP = route.dest AND r.LOCALE = 'en'
       AND t.OBJECT_STATUS = 'A' AND t.DESTINATION_TYPE = route.dest;
    IF v_count = 0 THEN
      RAISE_APPLICATION_ERROR(-20116, '1216: missing BCO en ' || route.recipient_group || '/' || route.dest || ' template');
    END IF;
    FOR r IN (
      SELECT LOCALE, COUNT(DISTINCT COD_MSG_TMPL_ID) n,
             COUNT(DISTINCT NVL(UNSECURE_MSG_TMPL_ID, '<NONE>')) n_alt,
             COUNT(*) total_rows, COUNT(COD_MSG_TMPL_ID) primary_rows
        FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event
         AND COD_ACTION_ID = 'A' AND SUBSCRIBER_TYPE = route.subscriber_type
         AND SUBSCRIBER_VALUE = route.subscriber_value AND TXT_DEST_TYP = route.dest GROUP BY LOCALE
    ) LOOP
      IF r.n <> 1 OR r.n_alt <> 1 OR r.total_rows <> r.primary_rows OR r.LOCALE IS NULL THEN
        RAISE_APPLICATION_ERROR(-20116, '1216: ambiguous BCO template for ' || route.recipient_group || '/' || route.dest || '/' || r.LOCALE);
      END IF;
    END LOOP;
  END LOOP;

  -- Explicit recipients must not be replaced by dispatcher lookups. Do not alter these BCO lists.
  SELECT COUNT(*) INTO v_count FROM DIGX_CZ_FW_CONFIG_ALL_O
   WHERE PROP_ID IN ('EMAIL_DISPATCHER_ALERT_EVENTID_LIST', 'SMS_DISPATCHER_ALERT_EVENTID_LIST',
                     'SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST')
     AND (INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',' || c_event || ',') > 0
       OR INSTR(',' || REPLACE(UPPER(PROP_VALUE), ' ', '') || ',', ',' || c_company_event || ',') > 0);
  IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20116, '1216: HTH event must not be in dispatcher recipient/country override lists'); END IF;
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_B WHERE COD_EVENT_ID IN (c_event, c_company_event)
   AND COD_ACT_ID NOT IN (c_base || 'submit', c_base || 'edit');
  IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20116, '1216: HTH event is already attached to an unrelated activity'); END IF;

  -- Only the HTH action-A recipient bindings are replaced. Preserve BCO and other actions.
  -- Existing HTH event/action configuration is retained; initialize only missing rows.
  DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID IN (c_event, c_company_event)
    AND COD_ACT_ID IN (c_base || 'submit', c_base || 'edit') AND COD_ACTION_ID='A';
  -- Do not delete old HTH1216_* templates, attributes, source mappings or service metadata.

  FOR e IN (SELECT c_event event_id FROM DUAL UNION ALL SELECT c_company_event FROM DUAL) LOOP
    SELECT COUNT(*) INTO v_count FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE=e.event_id;
    IF v_count=0 THEN
      SELECT * INTO v_event FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE=c_bco_event;
      v_event.EVENT_CODE := e.event_id;
      v_event.EVENT_DESC := 'HTH user accounts and service access notification';
      INSERT INTO DIGX_PM_EVENT_ALL_B VALUES v_event;
    END IF;
  END LOOP;

  FOR a IN (SELECT 'submit' operation FROM DUAL UNION ALL SELECT 'edit' FROM DUAL) LOOP
    v_activity := c_base || a.operation;
    SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = v_activity;
    IF v_count = 0 THEN
      SELECT * INTO v_act FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = c_source;
      v_act.COD_ACT_ID := v_activity;
      v_act.TXT_ACT_NAME := 'HostToHostUserAccess.' || a.operation;
      v_act.TXT_ACT_DESC := 'HTH user access ' || a.operation;
      v_act.CREATED_BY := USER; v_act.CREATION_DATE := SYSDATE;
      v_act.LAST_UPDATED_BY := USER; v_act.LAST_UPDATED_DATE := SYSDATE;
      v_act.OBJECT_VERSION_NUMBER := 1;
      INSERT INTO DIGX_EP_ACT_B VALUES v_act;
    ELSE
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_B WHERE COD_ACT_ID = v_activity AND OBJECT_STATUS = 'A';
      IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: existing HTH activity is inactive'); END IF;
    END IF;
    FOR e IN (SELECT c_event event_id FROM DUAL UNION ALL SELECT c_company_event FROM DUAL) LOOP
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_B
       WHERE COD_ACT_ID=v_activity AND COD_EVENT_ID=e.event_id;
      IF v_count=0 THEN
        INSERT INTO DIGX_EP_ACT_EVT_B
          (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP, DOMAIN_OBJECT_EXTN)
        VALUES (v_activity, e.event_id, 'HTH user accounts and service access notification', 'OTHER', 'ONLINE', 'CZ');
      END IF;
      SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B
       WHERE COD_ACT_ID=v_activity AND COD_EVENT_ID=e.event_id AND COD_ACTION_ID='A';
      IF v_count=0 THEN
        SELECT * INTO v_action FROM DIGX_EP_ACT_EVT_ACN_B
         WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event AND COD_ACTION_ID = 'A';
        v_action.COD_ACT_ID := v_activity; v_action.COD_EVENT_ID := e.event_id;
        v_action.ALERT_NAME := 'HTH user access notification';
        v_action.FLG_CONDITIONAL := 'N'; v_action.COD_DEC_ID := '0';
        -- N uses the existing asynchronous Alert pipeline, which reads the persisted ActivityData.
        -- Y executes dispatch in the caller and could deliver a success message before its commit.
        v_action.ALERT_TYPE := 'M'; v_action.FLG_TRANSACTIONAL := 'N';
        v_action.OBJECT_STATUS := 'A'; v_action.DOMAIN_OBJECT_EXTN := 'CZ';
        v_action.CREATED_BY := USER; v_action.CREATION_DATE := SYSDATE;
        v_action.LAST_UPDATED_BY := USER; v_action.LAST_UPDATED_DATE := SYSDATE;
        v_action.OBJECT_VERSION_NUMBER := 1;
        INSERT INTO DIGX_EP_ACT_EVT_ACN_B VALUES v_action;
      ELSE
        SELECT COUNT(*) INTO v_count FROM DIGX_EP_ACT_EVT_ACN_B
         WHERE COD_ACT_ID=v_activity AND COD_EVENT_ID=e.event_id AND COD_ACTION_ID='A'
           AND OBJECT_STATUS='A' AND FLG_TRANSACTIONAL='N' AND FLG_CONDITIONAL='N'
           AND (EXPIRY_DATE IS NULL OR EXPIRY_DATE>SYSDATE);
        IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: existing HTH action inactive/conditional/not asynchronous'); END IF;
      END IF;
    END LOOP;
  END LOOP;

  -- Bind the existing BCO primary/alternate IDs; never copy or modify template text.
  FOR route IN c_routes LOOP
    FOR r IN c_templates(route.subscriber_type, route.subscriber_value, route.dest) LOOP
      bind_template(r.COD_MSG_TMPL_ID, r.TXT_DEST_TYP,
        route.template_prefix || r.TXT_DEST_TYP || '.' || r.LOCALE || '.PRIMARY');
      IF TRIM(r.UNSECURE_MSG_TMPL_ID) IS NOT NULL AND r.UNSECURE_MSG_TMPL_ID<>r.COD_MSG_TMPL_ID THEN
        bind_template(r.UNSECURE_MSG_TMPL_ID, r.TXT_DEST_TYP,
          route.template_prefix || r.TXT_DEST_TYP || '.' || r.LOCALE || '.ALTERNATE');
      END IF;

      FOR a IN (SELECT 'submit' operation FROM DUAL UNION ALL SELECT 'edit' FROM DUAL) LOOP
        INSERT INTO DIGX_EP_EVT_REC_B
          (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID,
           FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN,
           LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
        VALUES (c_base || a.operation, route.event_id, 'A', r.COD_MSG_TMPL_ID, r.TXT_DEST_TYP, '0',
                'N', 'EXTERNAL', NULL, 'M', 'CZ', r.LOCALE, NULL, 'NA', 0, r.UNSECURE_MSG_TMPL_ID, 'USER');
      END LOOP;
    END LOOP;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('1216: HTH bindings now reference BCO templates. BCO rows and old HTH templates preserved. Verify then COMMIT.');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_1216_CONFIG;
    RAISE;
END;
