-- BCOH2H-1216. Execute this complete Oracle block in the OBDX configuration schema.
-- Re-runnable; commits once, rolls this script back on failure. No BCO rows are changed.
-- Clone the environment's BCO text and attribute mappings, never invent template content.
-- Deploy together with HthUserAccessNotification: user and company have separate HTH events.
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
    SELECT DISTINCT TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID
      FROM DIGX_EP_EVT_REC_B
     WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event AND COD_ACTION_ID = 'A'
       AND SUBSCRIBER_TYPE = p_type AND SUBSCRIBER_VALUE = p_value AND TXT_DEST_TYP = p_dest;
  v_count NUMBER;
  v_activity VARCHAR2(200);
  v_template VARCHAR2(100);
  v_placeholder VARCHAR2(200);
  v_occurrence NUMBER;
  v_act DIGX_EP_ACT_B%ROWTYPE;
  v_event DIGX_PM_EVENT_ALL_B%ROWTYPE;
  v_action DIGX_EP_ACT_EVT_ACN_B%ROWTYPE;
  v_tmpl DIGX_EP_MSG_TMPL_B%ROWTYPE;
  v_attr DIGX_EP_MSG_ATTR_B%ROWTYPE;
  v_src DIGX_EP_MSG_SRC_B%ROWTYPE;
  v_meta DIGX_MD_SERVICE_ATTR%ROWTYPE;
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
      SELECT LOCALE, COUNT(DISTINCT COD_MSG_TMPL_ID) n
        FROM DIGX_EP_EVT_REC_B WHERE COD_ACT_ID = c_source AND COD_EVENT_ID = c_bco_event
         AND COD_ACTION_ID = 'A' AND SUBSCRIBER_TYPE = route.subscriber_type
         AND SUBSCRIBER_VALUE = route.subscriber_value AND TXT_DEST_TYP = route.dest GROUP BY LOCALE
    ) LOOP
      IF r.n <> 1 OR r.LOCALE IS NULL THEN
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

  -- Only these two activities and the feature-owned events are replaced on rerun.
  DELETE FROM DIGX_EP_EVT_REC_B WHERE COD_EVENT_ID IN (c_event, c_company_event) AND COD_ACT_ID IN (c_base || 'submit', c_base || 'edit');
  DELETE FROM DIGX_EP_ACT_EVT_ACN_B WHERE COD_EVENT_ID IN (c_event, c_company_event) AND COD_ACT_ID IN (c_base || 'submit', c_base || 'edit');
  DELETE FROM DIGX_EP_ACT_EVT_B WHERE COD_EVENT_ID IN (c_event, c_company_event) AND COD_ACT_ID IN (c_base || 'submit', c_base || 'edit');
  -- Refuse to overwrite a feature-owned template if someone attached it to another event.
  SELECT COUNT(*) INTO v_count FROM DIGX_EP_EVT_REC_B WHERE SUBSTR(COD_MSG_TMPL_ID, 1, 8) = 'HTH1216_';
  IF v_count > 0 THEN RAISE_APPLICATION_ERROR(-20116, '1216: HTH1216 templates are referenced by another activity/event'); END IF;
  DELETE FROM DIGX_EP_MSG_SRC_B WHERE SUBSTR(COD_MESS_TMPL_ID, 1, 8) = 'HTH1216_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_EP_MSG_ATTR_B WHERE SUBSTR(COD_MESS_TMPL_ID, 1, 8) = 'HTH1216_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_EP_MSG_TMPL_B WHERE SUBSTR(COD_TMPL_ID, 1, 8) = 'HTH1216_' AND DETERMINANT_VALUE = 'OBDX_BU';
  DELETE FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ID IN (c_base || 'submit', c_base || 'edit')
    AND (INSTR(COD_SERVICE_ATTR_ID, c_base || 'submit.1216.') = 1 OR INSTR(COD_SERVICE_ATTR_ID, c_base || 'edit.1216.') = 1);

  FOR e IN (SELECT c_event event_id FROM DUAL UNION ALL SELECT c_company_event FROM DUAL) LOOP
    SELECT * INTO v_event FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = c_bco_event;
    v_event.EVENT_CODE := e.event_id;
    v_event.EVENT_DESC := 'HTH user accounts and service access notification';
    DELETE FROM DIGX_PM_EVENT_ALL_B WHERE EVENT_CODE = e.event_id;
    INSERT INTO DIGX_PM_EVENT_ALL_B VALUES v_event;
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
      INSERT INTO DIGX_EP_ACT_EVT_B
        (COD_ACT_ID, COD_EVENT_ID, TXT_ACT_EVT_DESC, TXT_EVT_TYP, TXT_ACT_EVT_TYP, DOMAIN_OBJECT_EXTN)
      VALUES (v_activity, e.event_id, 'HTH user accounts and service access notification', 'OTHER', 'ONLINE', 'CZ');
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
    END LOOP;
  END LOOP;

  -- Private copies keep the BCO text exactly as deployed; only HTH metadata bindings are added.
  FOR route IN c_routes LOOP
    FOR r IN c_templates(route.subscriber_type, route.subscriber_value, route.dest) LOOP
      v_template := 'HTH1216_' || route.template_prefix || r.TXT_DEST_TYP || '_' || r.LOCALE;
      SELECT * INTO v_tmpl FROM DIGX_EP_MSG_TMPL_B WHERE COD_TMPL_ID = r.COD_MSG_TMPL_ID AND DETERMINANT_VALUE = 'OBDX_BU';
      IF NVL(v_tmpl.OBJECT_STATUS, '?') <> 'A' OR NVL(v_tmpl.DESTINATION_TYPE, '?') <> r.TXT_DEST_TYP THEN
        RAISE_APPLICATION_ERROR(-20116, '1216: inactive/mismatched BCO template ' || r.COD_MSG_TMPL_ID);
      END IF;
      -- Check both body and subject; every #attribute# must have metadata before publishing.
      FOR part IN 1..2 LOOP
        v_occurrence := 1;
        LOOP
          IF part = 1 THEN
            v_placeholder := REGEXP_SUBSTR(v_tmpl.TXT_MSG_TMPL, '#([[:alnum:]_.]+)#', 1, v_occurrence, 'c', 1);
          ELSE
            v_placeholder := REGEXP_SUBSTR(v_tmpl.TXT_SUBJECT_TMPL, '#([[:alnum:]_.]+)#', 1, v_occurrence, 'c', 1);
          END IF;
          EXIT WHEN v_placeholder IS NULL;
          SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_ATTR_B
           WHERE COD_MESS_TMPL_ID = r.COD_MSG_TMPL_ID AND COD_ATTR_ID = v_placeholder AND DETERMINANT_VALUE = 'OBDX_BU';
          IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: missing BCO placeholder metadata: ' || v_placeholder); END IF;
          v_occurrence := v_occurrence + 1;
        END LOOP;
      END LOOP;
      v_tmpl.COD_TMPL_ID := v_template;
      v_tmpl.CREATED_BY := USER; v_tmpl.CREATION_DATE := SYSDATE;
      v_tmpl.LAST_UPDATED_BY := USER; v_tmpl.LAST_UPDATED_DATE := SYSDATE;
      v_tmpl.OBJECT_VERSION_NUMBER := 1;
      INSERT INTO DIGX_EP_MSG_TMPL_B VALUES v_tmpl;

      FOR m IN (SELECT * FROM DIGX_EP_MSG_ATTR_B WHERE COD_MESS_TMPL_ID = r.COD_MSG_TMPL_ID AND DETERMINANT_VALUE = 'OBDX_BU') LOOP
        v_attr := m; v_attr.COD_MESS_TMPL_ID := v_template;
        INSERT INTO DIGX_EP_MSG_ATTR_B VALUES v_attr;
        SELECT COUNT(*) INTO v_count FROM DIGX_EP_MSG_SRC_B s JOIN DIGX_MD_SERVICE_ATTR d ON d.COD_SERVICE_ATTR_ID = s.COD_SERVICE_ATTR_ID
         WHERE s.COD_MESS_TMPL_ID = r.COD_MSG_TMPL_ID AND s.COD_ATTR_ID = m.COD_ATTR_ID
           AND s.COD_ACT_ID = c_source AND s.DETERMINANT_VALUE = 'OBDX_BU' AND d.OBJECT_STATUS = 'A';
        IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20116, '1216: BCO template attribute source missing/ambiguous: ' || m.COD_ATTR_ID); END IF;
        FOR a IN (SELECT 'submit' operation FROM DUAL UNION ALL SELECT 'edit' FROM DUAL) LOOP
          v_activity := c_base || a.operation;
          SELECT * INTO v_src FROM DIGX_EP_MSG_SRC_B WHERE COD_MESS_TMPL_ID = r.COD_MSG_TMPL_ID
            AND COD_ATTR_ID = m.COD_ATTR_ID AND COD_ACT_ID = c_source AND DETERMINANT_VALUE = 'OBDX_BU';
          SELECT * INTO v_meta FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID = v_src.COD_SERVICE_ATTR_ID;
          -- Same UserManagementActivityLogDTO as BCO; preserve existing field definitions and source type.
          v_meta.COD_SERVICE_ATTR_ID := v_activity || '.1216.' || route.template_prefix || r.TXT_DEST_TYP || '.' || r.LOCALE || '.' || m.COD_ATTR_ID;
          v_meta.COD_SERVICE_ID := v_activity;
          v_meta.CREATED_BY := USER; v_meta.CREATION_DATE := SYSDATE;
          v_meta.LAST_UPDATED_BY := USER; v_meta.LAST_UPDATED_DATE := SYSDATE;
          v_meta.OBJECT_VERSION_NUMBER := 1;
          SELECT COUNT(*) INTO v_count FROM DIGX_MD_SERVICE_ATTR WHERE COD_SERVICE_ATTR_ID = v_meta.COD_SERVICE_ATTR_ID;
          IF v_count = 0 THEN INSERT INTO DIGX_MD_SERVICE_ATTR VALUES v_meta; END IF;
          v_src.COD_MESS_TMPL_ID := v_template; v_src.COD_ACT_ID := v_activity;
          v_src.COD_SERVICE_ATTR_ID := v_meta.COD_SERVICE_ATTR_ID;
          INSERT INTO DIGX_EP_MSG_SRC_B VALUES v_src;
        END LOOP;
      END LOOP;

      FOR a IN (SELECT 'submit' operation FROM DUAL UNION ALL SELECT 'edit' FROM DUAL) LOOP
        INSERT INTO DIGX_EP_EVT_REC_B
          (COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, COD_MSG_TMPL_ID, TXT_DEST_TYP, COD_DEC_ID,
           FLG_CONDITIONAL, SUBSCRIBER_TYPE, RECIPIENT_TYPE, ALERTTYPE, DOMAIN_OBJECT_EXTN,
           LOCALE, GROUPE_NAME, BANKER_TYPE, AMOUNT, UNSECURE_MSG_TMPL_ID, SUBSCRIBER_VALUE)
        VALUES (c_base || a.operation, route.event_id, 'A', v_template, r.TXT_DEST_TYP, '0',
                'N', 'EXTERNAL', NULL, 'M', 'CZ', r.LOCALE, NULL, 'NA', 0, NULL, 'USER');
      END LOOP;
    END LOOP;
  END LOOP;
  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_1216_CONFIG;
    RAISE;
END;
