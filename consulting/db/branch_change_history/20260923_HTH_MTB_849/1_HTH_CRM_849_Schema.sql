-- BCOH2H-849: run with the HTH_BEA schema connection, in a dedicated session.
-- DBeaver (Oracle connection): Execute SQL Script (Alt+X); no standalone slash.
-- Execute SQL Statement: select the complete DECLARE ... END; block.
-- One anonymous block, without local routines: avoid premature client-side splitting.
-- Uses only HTH_BEA.HTH_CRM_EVENT_DETAILS, with CRM-prefixed constraint/index names.
-- Never rename, migrate, copy from or modify HTH_MTB_EVENT_DETAILS / HTH_MTB_API_CONFIG.
-- BCO baseline: 99 ORM columns plus 14 HTH fields; 113 columns in total.
-- Fresh install creates the canonical CRM table. Existing CRM tables are upgraded in place.
-- Preserve all rows/extra columns/constraints. Never shrink, drop, truncate or backfill data.
-- Missing columns on an existing table are nullable: historical values are unknown.
-- Existing BYTE columns are expanded to CHAR semantics without reducing their byte capacity.
-- Preflight detects unsupported definitions before executing the ALTER plan.
-- DDL commits implicitly. Runtime DDL failures may leave completed steps; rerun is supported.
DECLARE
  v_stage VARCHAR2(128) := 'PREFLIGHT';
  v_count NUMBER;
  v_pk NUMBER;
  v_errors VARCHAR2(1800);
  TYPE text_list IS TABLE OF VARCHAR2(1000) INDEX BY PLS_INTEGER;
  v_plan text_list;
  v_issues text_list;
  TYPE name_set IS TABLE OF BOOLEAN INDEX BY VARCHAR2(128);
  v_expected name_set;
  v_col ALL_TAB_COLS%ROWTYPE;
  n NUMBER;
  v_definition VARCHAR2(100);
  v_change BOOLEAN;
  v_actual VARCHAR2(300);
  v_found BOOLEAN;
  v_columns VARCHAR2(1000);
  v_comment VARCHAR2(4000);
BEGIN
  SELECT COUNT(*) INTO v_count FROM ALL_TABLES
   WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS';
  IF v_count=0 THEN
    v_stage := 'TABLE_CREATE';
    EXECUTE IMMEDIATE q'~CREATE TABLE HTH_BEA.HTH_CRM_EVENT_DETAILS (
      EVENT_ID VARCHAR2(36 CHAR) NOT NULL,
      RECORD_TYPE VARCHAR2(16 CHAR),
      FILLER_01 VARCHAR2(64 CHAR),
      EVENT_DTE VARCHAR2(8 CHAR) NOT NULL,
      EVENT_TIME VARCHAR2(6 CHAR) NOT NULL,
      CHNL_ID VARCHAR2(64 CHAR),
      CHNL_TYPE_CODE VARCHAR2(32 CHAR),
      EVENT_STATUS_CODE VARCHAR2(16 CHAR) NOT NULL,
      CR_DR_IND VARCHAR2(1 CHAR),
      FEE_CHRG_CODE VARCHAR2(32 CHAR),
      EVENT_ACTV_TYPE_CODE VARCHAR2(64 CHAR),
      FIN_IND VARCHAR2(1 CHAR) NOT NULL,
      SELF_SRV_IND VARCHAR2(1 CHAR),
      USER_ID VARCHAR2(256 CHAR),
      EVENT_COUNTRY_CODE VARCHAR2(8 CHAR),
      ACCT_NBR VARCHAR2(64 CHAR),
      PHONE_NBR VARCHAR2(20 CHAR),
      PHONE_NBR_ACCT_NBR VARCHAR2(20 CHAR),
      PHONE_NBR_REQ_RESULT VARCHAR2(16 CHAR),
      PHONE_DEFAULT_IND VARCHAR2(1 CHAR),
      ELECT_ADD VARCHAR2(50 CHAR),
      ELECT_ADD_ACCT_NBR VARCHAR2(20 CHAR),
      ELECT_ADD_REQ_RESULT VARCHAR2(16 CHAR),
      ELECT_ADD_DEFAULT_IND VARCHAR2(1 CHAR),
      FPS_ID VARCHAR2(20 CHAR),
      FPS_ACCT_NBR VARCHAR2(20 CHAR),
      FPS_REQ_RESULT VARCHAR2(16 CHAR),
      DEBIT_ACCT_NBR VARCHAR2(20 CHAR),
      EVENT_CCY_CODE VARCHAR2(3 CHAR),
      EVENT_AMT NUMBER,
      FEE_CHRG_AMT NUMBER,
      FEE_CCY_CODE VARCHAR2(3 CHAR),
      TRF_DTE VARCHAR2(8 CHAR),
      TRF_FREQ VARCHAR2(32 CHAR),
      PROXY_ID_TYPE VARCHAR2(32 CHAR),
      PROXY_ID VARCHAR2(34 CHAR),
      PAYEE_NAME VARCHAR2(50 CHAR),
      PAYEE_BANK_CODE VARCHAR2(3 CHAR),
      EVENT_REM VARCHAR2(40 CHAR),
      REF_NBR VARCHAR2(9 CHAR),
      FROM_DTE VARCHAR2(8 CHAR),
      TO_DTE VARCHAR2(8 CHAR),
      MANDATE_ID VARCHAR2(128 CHAR),
      EDDA_MAINT_ACTION VARCHAR2(32 CHAR),
      SOURCE_TRX_REF_NBR VARCHAR2(128 CHAR),
      PAY_CAT_PURP_CODE VARCHAR2(6 CHAR),
      PAY_PURP_CODE VARCHAR2(6 CHAR),
      DEVICE_ID VARCHAR2(256 CHAR),
      MOBILE_BRAND VARCHAR2(128 CHAR),
      PLATFORM_CODE VARCHAR2(64 CHAR),
      DEVICE_MODEL VARCHAR2(128 CHAR),
      DEVICE_OS_VERSION VARCHAR2(128 CHAR),
      IP_ADDRESS VARCHAR2(64 CHAR),
      FILLER_02 VARCHAR2(3 CHAR),
      EVENT_AMT_HKE NUMBER,
      EVENT_EX_RATE NUMBER,
      MRCH_ID VARCHAR2(128 CHAR),
      ACCT_CCY_CODE VARCHAR2(3 CHAR),
      ACCT_AMT NUMBER,
      SCREEN_ID VARCHAR2(64 CHAR),
      OMB_FLAG VARCHAR2(1 CHAR),
      MULTI_APP_REJ_TXN_CNT VARCHAR2(32 CHAR),
      COUPON_CODE VARCHAR2(64 CHAR),
      ACTL_INT_RATE VARCHAR2(64 CHAR),
      TT_AUTO_ROUTE VARCHAR2(1 CHAR),
      SUSPICIOUS_ACTIVITY VARCHAR2(128 CHAR),
      SUSPICIOUS_IND VARCHAR2(1 CHAR),
      WITH_MRCH VARCHAR2(1 CHAR),
      CMPY_NAME VARCHAR2(256 CHAR),
      DOC_ID VARCHAR2(128 CHAR),
      DOC_TYPE VARCHAR2(32 CHAR),
      DOC_COUNTRY_CODE VARCHAR2(8 CHAR),
      MRCH_USER_ID VARCHAR2(256 CHAR),
      MRCH_USER_NAME VARCHAR2(256 CHAR),
      DOC_CMPY_NAME VARCHAR2(256 CHAR),
      DOC_ACCT_NBR VARCHAR2(64 CHAR),
      SLE_MRCH_NAME VARCHAR2(256 CHAR),
      FORMAT VARCHAR2(32 CHAR),
      TXN_COUNT VARCHAR2(32 CHAR),
      ADV_FREQ_TYPE VARCHAR2(32 CHAR),
      ADV_TYPE VARCHAR2(32 CHAR),
      SLE_MRCH_ID VARCHAR2(128 CHAR),
      AR_TOKEN VARCHAR2(256 CHAR),
      TREASURY_REF VARCHAR2(128 CHAR),
      LM_SWEEP_INSTRUCTION_TYPE VARCHAR2(64 CHAR),
      LM_FREQUENCY_EXECUTIONDAY VARCHAR2(64 CHAR),
      LM_EFFECTIVEDATE VARCHAR2(32 CHAR),
      LM_RULE_SETUPDATE VARCHAR2(32 CHAR),
      LM_SWEEPING_AMOUNT_THRESHOLD VARCHAR2(128 CHAR),
      LM_FPXTXN_NBR VARCHAR2(128 CHAR),
      LM_INSTRUCTION_NBR VARCHAR2(128 CHAR),
      USER_ROLE VARCHAR2(32 CHAR),
      TOKEN_ID VARCHAR2(256 CHAR),
      ERR_CODE VARCHAR2(20 CHAR),
      BIO_TYPE VARCHAR2(32 CHAR),
      REG_METHOD VARCHAR2(64 CHAR),
      AUTH_METHOD VARCHAR2(64 CHAR),
      LANG VARCHAR2(2 CHAR),
      APP_VERSION VARCHAR2(64 CHAR),
      SOURCE_SYSTEM VARCHAR2(16 CHAR) NOT NULL,
      CHANNEL_TYPE VARCHAR2(16 CHAR) NOT NULL,
      TARGET_USER_CHANNEL VARCHAR2(16 CHAR),
      ACTIVITY_KEY VARCHAR2(64 CHAR) NOT NULL,
      PHASE VARCHAR2(32 CHAR) NOT NULL,
      TARGET_USER_ID VARCHAR2(256 CHAR),
      RELATIONSHIP_TYPE VARCHAR2(32 CHAR),
      SERVICE_ID VARCHAR2(256 CHAR) NOT NULL,
      TASK_CODE VARCHAR2(100 CHAR),
      SOURCE_ACTION_ID VARCHAR2(128 CHAR),
      REQUEST_ID VARCHAR2(128 CHAR),
      ERROR_CODE VARCHAR2(100 CHAR),
      DEDUP_KEY VARCHAR2(64 CHAR),
      CREATED_AT TIMESTAMP(6) NOT NULL,
      CONSTRAINT PK_HTH_CRM_EVENT PRIMARY KEY (EVENT_ID)
    )~';
  END IF;

  v_stage := 'PREFLIGHT';
  FOR spec IN (
    SELECT 'EVENT_ID' column_name, 'VARCHAR2' column_type, 36 column_length, 'N' column_nullable FROM DUAL
    UNION ALL SELECT 'RECORD_TYPE', 'VARCHAR2', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'FILLER_01', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_DTE', 'VARCHAR2', 8, 'N' FROM DUAL
    UNION ALL SELECT 'EVENT_TIME', 'VARCHAR2', 6, 'N' FROM DUAL
    UNION ALL SELECT 'CHNL_ID', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'CHNL_TYPE_CODE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_STATUS_CODE', 'VARCHAR2', 16, 'N' FROM DUAL
    UNION ALL SELECT 'CR_DR_IND', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'FEE_CHRG_CODE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_ACTV_TYPE_CODE', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'FIN_IND', 'VARCHAR2', 1, 'N' FROM DUAL
    UNION ALL SELECT 'SELF_SRV_IND', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'USER_ID', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_COUNTRY_CODE', 'VARCHAR2', 8, 'Y' FROM DUAL
    UNION ALL SELECT 'ACCT_NBR', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR_ACCT_NBR', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR_REQ_RESULT', 'VARCHAR2', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'PHONE_DEFAULT_IND', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD', 'VARCHAR2', 50, 'Y' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_ACCT_NBR', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_REQ_RESULT', 'VARCHAR2', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_DEFAULT_IND', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'FPS_ID', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'FPS_ACCT_NBR', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'FPS_REQ_RESULT', 'VARCHAR2', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'DEBIT_ACCT_NBR', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_CCY_CODE', 'VARCHAR2', 3, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_AMT', 'NUMBER', NULL, 'Y' FROM DUAL
    UNION ALL SELECT 'FEE_CHRG_AMT', 'NUMBER', NULL, 'Y' FROM DUAL
    UNION ALL SELECT 'FEE_CCY_CODE', 'VARCHAR2', 3, 'Y' FROM DUAL
    UNION ALL SELECT 'TRF_DTE', 'VARCHAR2', 8, 'Y' FROM DUAL
    UNION ALL SELECT 'TRF_FREQ', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'PROXY_ID_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'PROXY_ID', 'VARCHAR2', 34, 'Y' FROM DUAL
    UNION ALL SELECT 'PAYEE_NAME', 'VARCHAR2', 50, 'Y' FROM DUAL
    UNION ALL SELECT 'PAYEE_BANK_CODE', 'VARCHAR2', 3, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_REM', 'VARCHAR2', 40, 'Y' FROM DUAL
    UNION ALL SELECT 'REF_NBR', 'VARCHAR2', 9, 'Y' FROM DUAL
    UNION ALL SELECT 'FROM_DTE', 'VARCHAR2', 8, 'Y' FROM DUAL
    UNION ALL SELECT 'TO_DTE', 'VARCHAR2', 8, 'Y' FROM DUAL
    UNION ALL SELECT 'MANDATE_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'EDDA_MAINT_ACTION', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'SOURCE_TRX_REF_NBR', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'PAY_CAT_PURP_CODE', 'VARCHAR2', 6, 'Y' FROM DUAL
    UNION ALL SELECT 'PAY_PURP_CODE', 'VARCHAR2', 6, 'Y' FROM DUAL
    UNION ALL SELECT 'DEVICE_ID', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'MOBILE_BRAND', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'PLATFORM_CODE', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'DEVICE_MODEL', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'DEVICE_OS_VERSION', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'IP_ADDRESS', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'FILLER_02', 'VARCHAR2', 3, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_AMT_HKE', 'NUMBER', NULL, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_EX_RATE', 'NUMBER', NULL, 'Y' FROM DUAL
    UNION ALL SELECT 'MRCH_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'ACCT_CCY_CODE', 'VARCHAR2', 3, 'Y' FROM DUAL
    UNION ALL SELECT 'ACCT_AMT', 'NUMBER', NULL, 'Y' FROM DUAL
    UNION ALL SELECT 'SCREEN_ID', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'OMB_FLAG', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'MULTI_APP_REJ_TXN_CNT', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'COUPON_CODE', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'ACTL_INT_RATE', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'TT_AUTO_ROUTE', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'SUSPICIOUS_ACTIVITY', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'SUSPICIOUS_IND', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'WITH_MRCH', 'VARCHAR2', 1, 'Y' FROM DUAL
    UNION ALL SELECT 'CMPY_NAME', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'DOC_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'DOC_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'DOC_COUNTRY_CODE', 'VARCHAR2', 8, 'Y' FROM DUAL
    UNION ALL SELECT 'MRCH_USER_ID', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'MRCH_USER_NAME', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'DOC_CMPY_NAME', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'DOC_ACCT_NBR', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'SLE_MRCH_NAME', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'FORMAT', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'TXN_COUNT', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'ADV_FREQ_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'ADV_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'SLE_MRCH_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'AR_TOKEN', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'TREASURY_REF', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_SWEEP_INSTRUCTION_TYPE', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_FREQUENCY_EXECUTIONDAY', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_EFFECTIVEDATE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_RULE_SETUPDATE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_SWEEPING_AMOUNT_THRESHOLD', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_FPXTXN_NBR', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'LM_INSTRUCTION_NBR', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'USER_ROLE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'TOKEN_ID', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'ERR_CODE', 'VARCHAR2', 20, 'Y' FROM DUAL
    UNION ALL SELECT 'BIO_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'REG_METHOD', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'AUTH_METHOD', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'LANG', 'VARCHAR2', 2, 'Y' FROM DUAL
    UNION ALL SELECT 'APP_VERSION', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'SOURCE_SYSTEM', 'VARCHAR2', 16, 'N' FROM DUAL
    UNION ALL SELECT 'CHANNEL_TYPE', 'VARCHAR2', 16, 'N' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_CHANNEL', 'VARCHAR2', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'ACTIVITY_KEY', 'VARCHAR2', 64, 'N' FROM DUAL
    UNION ALL SELECT 'PHASE', 'VARCHAR2', 32, 'N' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_ID', 'VARCHAR2', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'RELATIONSHIP_TYPE', 'VARCHAR2', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'SERVICE_ID', 'VARCHAR2', 256, 'N' FROM DUAL
    UNION ALL SELECT 'TASK_CODE', 'VARCHAR2', 100, 'Y' FROM DUAL
    UNION ALL SELECT 'SOURCE_ACTION_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'REQUEST_ID', 'VARCHAR2', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'ERROR_CODE', 'VARCHAR2', 100, 'Y' FROM DUAL
    UNION ALL SELECT 'DEDUP_KEY', 'VARCHAR2', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'CREATED_AT', 'TIMESTAMP', NULL, 'N' FROM DUAL
  ) LOOP
    v_change := FALSE;
    v_expected(spec.column_name) := TRUE;
    IF spec.column_type='TIMESTAMP' THEN
      v_definition := 'TIMESTAMP(6)';
    ELSIF spec.column_type='NUMBER' THEN
      v_definition := 'NUMBER';
    ELSE
      v_definition := 'VARCHAR2(' || TO_CHAR(spec.column_length,'FM99990') || ' CHAR)';
    END IF;
    SELECT COUNT(*) INTO n FROM ALL_TAB_COLS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'
       AND COLUMN_NAME=spec.column_name;
    IF n=0 THEN
      IF spec.column_name='EVENT_ID' THEN
        v_issues(v_issues.COUNT+1) := 'EVENT_ID missing; cannot invent historical IDs';
      ELSE
        v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_CRM_EVENT_DETAILS ADD (' || spec.column_name || ' ' || v_definition || ')';
      END IF;
      CONTINUE;
    END IF;
    SELECT * INTO v_col FROM ALL_TAB_COLS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'
       AND COLUMN_NAME=spec.column_name;
    v_actual := spec.column_name || '=' || v_col.DATA_TYPE || ', bytes=' || v_col.DATA_LENGTH
      || ', chars=' || v_col.CHAR_LENGTH || ', semantics=' || v_col.CHAR_USED || ', nullable=' || v_col.NULLABLE
      || ', precision=' || v_col.DATA_PRECISION || ', scale=' || v_col.DATA_SCALE;
    IF v_col.VIRTUAL_COLUMN='YES' OR v_col.IDENTITY_COLUMN='YES' THEN
      v_issues(v_issues.COUNT+1) := v_actual || ': generated column cannot receive application values';
      CONTINUE;
    END IF;
    IF spec.column_nullable='Y' AND v_col.NULLABLE='N' THEN
      v_issues(v_issues.COUNT+1) := v_actual || ': application may supply NULL; constraint preserved';
    END IF;
    IF spec.column_type='TIMESTAMP' THEN
      IF v_col.DATA_TYPE NOT LIKE 'TIMESTAMP(%)' THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': expected plain TIMESTAMP';
        CONTINUE;
      END IF;
      v_change := NVL(v_col.DATA_SCALE,0)<6;
    ELSIF spec.column_type='NUMBER' THEN
      -- BigDecimal fields must not be silently rounded or truncated by a narrow definition.
      IF v_col.DATA_TYPE<>'NUMBER' OR v_col.DATA_PRECISION IS NOT NULL OR v_col.DATA_SCALE IS NOT NULL THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': expected unrestricted NUMBER; existing data and definition preserved';
      END IF;
    ELSE
      IF v_col.DATA_TYPE<>'VARCHAR2' OR v_col.CHAR_USED NOT IN ('B','C') THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': expected VARCHAR2';
        CONTINUE;
      END IF;
      IF v_col.CHAR_USED='B' THEN
        -- One existing character consumes at least one byte; this never reduces capacity.
        v_definition := 'VARCHAR2(' || TO_CHAR(GREATEST(spec.column_length,v_col.DATA_LENGTH),'FM99990') || ' CHAR)';
        v_change := TRUE;
      ELSE
        v_change := v_col.CHAR_LENGTH<spec.column_length;
      END IF;
    END IF;
    IF v_change THEN
      SELECT COUNT(*) INTO n FROM (
        SELECT COLUMN_NAME FROM ALL_PART_KEY_COLUMNS
         WHERE OWNER='HTH_BEA' AND NAME='HTH_CRM_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
        UNION ALL
        SELECT COLUMN_NAME FROM ALL_SUBPART_KEY_COLUMNS
         WHERE OWNER='HTH_BEA' AND NAME='HTH_CRM_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
      ) WHERE COLUMN_NAME=spec.column_name;
      IF n>0 THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': partition key requires a separate migration';
      ELSE
        v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_CRM_EVENT_DETAILS MODIFY (' || spec.column_name || ' ' || v_definition || ')';
      END IF;
    END IF;
  END LOOP;

  -- Extra legacy fields stay intact. Do not guess values or relax required constraints.
  FOR c IN (SELECT COLUMN_NAME FROM ALL_TAB_COLS
             WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'
               AND USER_GENERATED='YES' AND NULLABLE='N'
               AND VIRTUAL_COLUMN='NO' AND IDENTITY_COLUMN='NO') LOOP
    IF NOT v_expected.EXISTS(c.COLUMN_NAME) THEN
      v_issues(v_issues.COUNT+1) := c.COLUMN_NAME || ': extra NOT NULL column not supplied by 849; requires explicit mapping/default review';
    END IF;
  END LOOP;

  SELECT COUNT(*) INTO v_pk FROM ALL_CONSTRAINTS c
   WHERE c.OWNER='HTH_BEA' AND c.TABLE_NAME='HTH_CRM_EVENT_DETAILS'
     AND c.CONSTRAINT_TYPE='P' AND c.STATUS='ENABLED' AND c.VALIDATED='VALIDATED'
     AND 1=(SELECT COUNT(*) FROM ALL_CONS_COLUMNS x
             WHERE x.OWNER=c.OWNER AND x.CONSTRAINT_NAME=c.CONSTRAINT_NAME)
     AND EXISTS (SELECT 1 FROM ALL_CONS_COLUMNS x
                  WHERE x.OWNER=c.OWNER AND x.CONSTRAINT_NAME=c.CONSTRAINT_NAME
                    AND x.COLUMN_NAME='EVENT_ID');
  IF v_pk<>1 THEN
    SELECT COUNT(*) INTO v_count FROM ALL_CONSTRAINTS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS' AND CONSTRAINT_TYPE='P';
    IF v_count>0 THEN
      v_issues(v_issues.COUNT+1) := 'Existing primary key must be enabled, validated and on EVENT_ID only; constraint preserved';
    ELSE
      SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'
         AND COLUMN_NAME='EVENT_ID' AND DATA_TYPE='VARCHAR2';
      IF v_count=1 THEN
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT EVENT_ID FROM HTH_BEA.HTH_CRM_EVENT_DETAILS GROUP BY EVENT_ID HAVING EVENT_ID IS NULL OR COUNT(*)>1)' INTO v_count;
        IF v_count>0 THEN
          v_issues(v_issues.COUNT+1) := 'EVENT_ID has null/duplicate values; cannot add primary key without changing data';
        ELSE
          SELECT COUNT(*) INTO v_count FROM ALL_CONSTRAINTS
           WHERE OWNER='HTH_BEA' AND CONSTRAINT_NAME='PK_HTH_CRM_EVENT';
          IF v_count>0 THEN
            v_issues(v_issues.COUNT+1) := 'PK_HTH_CRM_EVENT name already used; existing constraint preserved';
          ELSE
            v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_CRM_EVENT_DETAILS ADD CONSTRAINT PK_HTH_CRM_EVENT PRIMARY KEY (EVENT_ID)';
          END IF;
        END IF;
      END IF;
    END IF;
  END IF;

  SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
   WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'
     AND COLUMN_NAME='DEDUP_KEY' AND DATA_TYPE='VARCHAR2';
  IF v_count=1 THEN
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT DEDUP_KEY FROM HTH_BEA.HTH_CRM_EVENT_DETAILS WHERE DEDUP_KEY IS NOT NULL GROUP BY DEDUP_KEY HAVING COUNT(*)>1)' INTO v_count;
    IF v_count>0 THEN v_issues(v_issues.COUNT+1) := 'DEDUP_KEY has duplicate non-null values; no records deleted'; END IF;
  END IF;

  FOR spec IN (
    SELECT 'UX_HTH_CRM_DEDUP' index_name, 'DEDUP_KEY' column_names, 'UNIQUE' index_uniqueness FROM DUAL
    UNION ALL SELECT 'IX_HTH_CRM_DATE_ACT', 'EVENT_DTE,ACTIVITY_KEY', 'NONUNIQUE' FROM DUAL
    UNION ALL SELECT 'IX_HTH_CRM_SOURCE', 'SOURCE_TRX_REF_NBR', 'NONUNIQUE' FROM DUAL
  ) LOOP
    v_found := FALSE;
    -- The expected name cannot already belong to another object/table.
    SELECT COUNT(*) INTO n FROM ALL_OBJECTS WHERE OWNER='HTH_BEA' AND OBJECT_NAME=spec.index_name
      AND OBJECT_TYPE<>'INDEX';
    IF n>0 THEN v_issues(v_issues.COUNT+1) := spec.index_name || ': name belongs to another object'; END IF;
    FOR i IN (SELECT INDEX_NAME,TABLE_OWNER,TABLE_NAME,UNIQUENESS,STATUS,INDEX_TYPE
                FROM ALL_INDEXES WHERE OWNER='HTH_BEA'
                  AND (INDEX_NAME=spec.index_name OR (TABLE_OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS'))) LOOP
      SELECT LISTAGG(COLUMN_NAME,',') WITHIN GROUP (ORDER BY COLUMN_POSITION)
        INTO v_columns FROM ALL_IND_COLUMNS
       WHERE INDEX_OWNER='HTH_BEA' AND INDEX_NAME=i.INDEX_NAME;
      IF i.TABLE_OWNER='HTH_BEA' AND i.TABLE_NAME='HTH_CRM_EVENT_DETAILS'
          AND v_columns=spec.column_names AND i.UNIQUENESS=spec.index_uniqueness AND i.STATUS='VALID'
          AND i.INDEX_TYPE='NORMAL' THEN
        v_found := TRUE;
      ELSIF i.INDEX_NAME=spec.index_name OR v_columns=spec.column_names THEN
        v_issues(v_issues.COUNT+1) := i.INDEX_NAME || ': incompatible index (' || v_columns || ', ' || i.UNIQUENESS || ', ' || i.STATUS || ')';
      END IF;
    END LOOP;
    IF NOT v_found THEN
      v_plan(v_plan.COUNT+1) := 'CREATE ' || CASE WHEN spec.index_uniqueness='UNIQUE' THEN 'UNIQUE ' ELSE '' END
        || 'INDEX HTH_BEA.' || spec.index_name || ' ON HTH_BEA.HTH_CRM_EVENT_DETAILS (' || spec.column_names || ')';
    END IF;
  END LOOP;
  FOR j IN 1..v_issues.COUNT LOOP
    DBMS_OUTPUT.PUT_LINE('849 preflight: ' || v_issues(j));
    IF NVL(LENGTH(v_errors),0) + LENGTH(v_issues(j)) + 2 <= 1700 THEN
      v_errors := v_errors || v_issues(j) || '; ';
    ELSE
      v_errors := SUBSTR(v_errors,1,1650) || ' [more: see DBMS Output]';
    END IF;
  END LOOP;
  IF v_errors IS NOT NULL THEN
    RAISE_APPLICATION_ERROR(-20849,'849 preflight; no migration DDL executed: ' || v_errors);
  END IF;

  -- English database documentation for the table and the 113 columns owned by this release.
  -- Queue only changed comments, after the column/index plan; preserve unknown legacy comments.
  FOR spec IN (
    SELECT CAST(NULL AS VARCHAR2(128)) column_name,
      'HTH CM/BM onboarding operation records for CRM/MTB; populated by application events, with no deployment seed data.' description FROM DUAL
    UNION ALL SELECT 'EVENT_ID', 'Unique event identifier generated by the HTH CRM collector.' FROM DUAL
    UNION ALL SELECT 'RECORD_TYPE', 'CRM record type from the existing BCO CRM configuration; null when not configured.' FROM DUAL
    UNION ALL SELECT 'FILLER_01', 'CRM filler value from the existing BCO CRM configuration; null when not configured.' FROM DUAL
    UNION ALL SELECT 'EVENT_DTE', 'Event date in Asia/Hong_Kong time, formatted as yyyyMMdd.' FROM DUAL
    UNION ALL SELECT 'EVENT_TIME', 'Event time in Asia/Hong_Kong time, formatted as HHmmss.' FROM DUAL
    UNION ALL SELECT 'CHNL_ID', 'BCO CRM channel identifier from Login-Channel and CRM configuration; distinct from the CM/BM portal.' FROM DUAL
    UNION ALL SELECT 'CHNL_TYPE_CODE', 'BCO CRM channel type code from the existing CRM configuration; null when not configured.' FROM DUAL
    UNION ALL SELECT 'EVENT_STATUS_CODE', 'Operation-stage result: A for success or submission accepted for approval; R for failure or business rollback.' FROM DUAL
    UNION ALL SELECT 'CR_DR_IND', 'Credit or debit indicator from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FEE_CHRG_CODE', 'Fee charge classification from the existing BCO CRM configuration; no fee is generated by this record.' FROM DUAL
    UNION ALL SELECT 'EVENT_ACTV_TYPE_CODE', 'External CRM activity code from ACTIVITY_<ACTIVITY_KEY> configuration; null when not configured.' FROM DUAL
    UNION ALL SELECT 'FIN_IND', 'Financial indicator; N for the non-financial CM/BM onboarding operations in this release.' FROM DUAL
    UNION ALL SELECT 'SELF_SRV_IND', 'BCO self-service indicator derived from the platform isAdmin context; null when unknown.' FROM DUAL
    UNION ALL SELECT 'USER_ID', 'User who performed this operation or approval action.' FROM DUAL
    UNION ALL SELECT 'EVENT_COUNTRY_CODE', 'CRM event country code from the existing BCO CRM configuration; null when not configured.' FROM DUAL
    UNION ALL SELECT 'ACCT_NBR', 'Corporate party identifier for the maintenance operation; not an individual bank account number.' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR', 'Phone proxy number from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR_ACCT_NBR', 'Account linked to the phone proxy from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PHONE_NBR_REQ_RESULT', 'Phone proxy request result from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PHONE_DEFAULT_IND', 'Default phone proxy indicator from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD', 'Electronic-address proxy from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_ACCT_NBR', 'Account linked to the electronic-address proxy from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_REQ_RESULT', 'Electronic-address proxy request result from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ELECT_ADD_DEFAULT_IND', 'Default electronic-address proxy indicator from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FPS_ID', 'FPS proxy identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FPS_ACCT_NBR', 'Account linked to the FPS proxy from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FPS_REQ_RESULT', 'FPS proxy request result from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DEBIT_ACCT_NBR', 'Debit account number from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EVENT_CCY_CODE', 'Event currency code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EVENT_AMT', 'Event amount from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FEE_CHRG_AMT', 'Fee charged amount from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FEE_CCY_CODE', 'Fee currency code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'TRF_DTE', 'Transfer date from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'TRF_FREQ', 'Transfer frequency from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PROXY_ID_TYPE', 'Payment proxy identifier type from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PROXY_ID', 'Payment proxy identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PAYEE_NAME', 'Payee name from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PAYEE_BANK_CODE', 'Payee bank code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EVENT_REM', 'BCO-style event reference remark, limited to 40 characters; the full HTH reference remains in SOURCE_TRX_REF_NBR.' FROM DUAL
    UNION ALL SELECT 'REF_NBR', 'BCO numeric-format reference number from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FROM_DTE', 'Period start date from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'TO_DTE', 'Period end date from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'MANDATE_ID', 'Direct-debit mandate identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EDDA_MAINT_ACTION', 'Electronic direct-debit maintenance action from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'SOURCE_TRX_REF_NBR', 'Approval transaction reference or business response reference used to correlate the event.' FROM DUAL
    UNION ALL SELECT 'PAY_CAT_PURP_CODE', 'Payment category purpose code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'PAY_PURP_CODE', 'Payment purpose code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DEVICE_ID', 'Device identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'MOBILE_BRAND', 'Operating-system label parsed from the user agent, following the BCO field convention; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'PLATFORM_CODE', 'Client platform code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DEVICE_MODEL', 'Browser brand parsed from the user agent, following the BCO field convention; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'DEVICE_OS_VERSION', 'Browser version parsed from the user agent, following the BCO field convention; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'IP_ADDRESS', 'Client IP address supplied by the platform operation context, when available.' FROM DUAL
    UNION ALL SELECT 'FILLER_02', 'Secondary CRM filler from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EVENT_AMT_HKE', 'Event amount in Hong Kong dollar equivalent from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'EVENT_EX_RATE', 'Event exchange rate from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'MRCH_ID', 'Merchant identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ACCT_CCY_CODE', 'Account currency code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ACCT_AMT', 'Account-currency amount from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'SCREEN_ID', 'BCO screen identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'OMB_FLAG', 'One-Man-Bank approval indicator: Y for affirmative CM user/access context; null when unknown or not applicable.' FROM DUAL
    UNION ALL SELECT 'MULTI_APP_REJ_TXN_CNT', 'Count of transactions in a multiple-approval or rejection action from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'COUPON_CODE', 'Coupon code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ACTL_INT_RATE', 'Actual interest rate from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'TT_AUTO_ROUTE', 'BCO CRM automatic-routing default N for these non-financial HTH maintenance events.' FROM DUAL
    UNION ALL SELECT 'SUSPICIOUS_ACTIVITY', 'Suspicious activity classification from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'SUSPICIOUS_IND', 'Suspicious activity indicator from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'WITH_MRCH', 'Merchant-presence indicator from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'CMPY_NAME', 'Merchant company name from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DOC_ID', 'Document identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DOC_TYPE', 'Document type from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DOC_COUNTRY_CODE', 'Document issuing country code from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'MRCH_USER_ID', 'Merchant user identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'MRCH_USER_NAME', 'Merchant user name from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DOC_CMPY_NAME', 'Company name on a document from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'DOC_ACCT_NBR', 'Account number on a document from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'SLE_MRCH_NAME', 'SLE merchant name from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'FORMAT', 'Advice or document format from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'TXN_COUNT', 'Transaction count from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ADV_FREQ_TYPE', 'Advice frequency type from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'ADV_TYPE', 'Advice type from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'SLE_MRCH_ID', 'SLE merchant identifier from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'AR_TOKEN', 'AR token from the BCO model; reserved and always null in HTH because token values are not collected.' FROM DUAL
    UNION ALL SELECT 'TREASURY_REF', 'Treasury reference from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_SWEEP_INSTRUCTION_TYPE', 'Liquidity-management sweep instruction type from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_FREQUENCY_EXECUTIONDAY', 'Liquidity-management frequency or execution day from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_EFFECTIVEDATE', 'Liquidity-management effective date from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_RULE_SETUPDATE', 'Liquidity-management rule setup date from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_SWEEPING_AMOUNT_THRESHOLD', 'Liquidity-management sweep amount or threshold from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_FPXTXN_NBR', 'Liquidity-management FPX transaction number from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'LM_INSTRUCTION_NBR', 'Liquidity-management instruction number from the BCO model; reserved and null for current HTH CM/BM onboarding events.' FROM DUAL
    UNION ALL SELECT 'USER_ROLE', 'Platform user role from the approved request metadata field; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'TOKEN_ID', 'Authentication token identifier from the BCO model; reserved and always null in HTH because token values are not collected.' FROM DUAL
    UNION ALL SELECT 'ERR_CODE', 'BCO-model error code: first approval-processing code or TFA context code, keeping the last 20 characters; distinct from HTH ERROR_CODE.' FROM DUAL
    UNION ALL SELECT 'BIO_TYPE', 'Biometric authentication type from the approved platform metadata field; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'REG_METHOD', 'Registration method from the approved platform metadata field; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'AUTH_METHOD', 'Authentication method from the approved platform metadata field; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'LANG', 'BCO CRM language code: en, sc or tc, derived from the active locale.' FROM DUAL
    UNION ALL SELECT 'APP_VERSION', 'Client application version from the approved platform metadata field; null when unavailable.' FROM DUAL
    UNION ALL SELECT 'SOURCE_SYSTEM', 'Source system identifier; current HTH collector writes HTH.' FROM DUAL
    UNION ALL SELECT 'CHANNEL_TYPE', 'Originating portal: CM for customer management, BM for bank management.' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_CHANNEL', 'Target user channel HTH; null for company-level operations.' FROM DUAL
    UNION ALL SELECT 'ACTIVITY_KEY', 'Internal HTH operation, such as ACCESS_CREATE, ACCESS_EDIT or PASSWORD_SETUP.' FROM DUAL
    UNION ALL SELECT 'PHASE', 'Recorded stage: SUBMIT, APPLY, GENERATE, EXECUTE, APPROVAL_APPROVE, APPROVAL_REJECT or APPROVAL_ACTION.' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_ID', 'User affected by the operation; null for company-level operations.' FROM DUAL
    UNION ALL SELECT 'RELATIONSHIP_TYPE', 'Account/service access relationship type supplied by the operation, such as Related or Associated.' FROM DUAL
    UNION ALL SELECT 'SERVICE_ID', 'Application service identifier that produced the HTH CRM event.' FROM DUAL
    UNION ALL SELECT 'TASK_CODE', 'Platform task identifier captured from the operation context when available.' FROM DUAL
    UNION ALL SELECT 'SOURCE_ACTION_ID', 'Stable identifier for a specific action, when supplied; distinct from the transaction reference.' FROM DUAL
    UNION ALL SELECT 'REQUEST_ID', 'Request identifier supplied by the operation, when available.' FROM DUAL
    UNION ALL SELECT 'ERROR_CODE', 'Sanitized application error code; BUSINESS_ROLLBACK denotes a rolled-back business transaction.' FROM DUAL
    UNION ALL SELECT 'DEDUP_KEY', 'SHA-256 event deduplication key when a reliable action identifier is available; otherwise null.' FROM DUAL
    UNION ALL SELECT 'CREATED_AT', 'Record write time in Asia/Hong_Kong time; null for legacy rows without this metadata.' FROM DUAL
  ) LOOP
    IF spec.column_name IS NULL THEN
      SELECT MAX(COMMENTS) INTO v_comment FROM ALL_TAB_COMMENTS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS';
    ELSE
      SELECT MAX(COMMENTS) INTO v_comment FROM ALL_COL_COMMENTS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_CRM_EVENT_DETAILS' AND COLUMN_NAME=spec.column_name;
    END IF;
    IF v_comment IS NULL OR v_comment<>spec.description THEN
      IF spec.column_name IS NULL THEN
        v_plan(v_plan.COUNT+1) := 'COMMENT ON TABLE HTH_BEA.HTH_CRM_EVENT_DETAILS IS '
          || CHR(39) || REPLACE(spec.description,CHR(39),CHR(39)||CHR(39)) || CHR(39);
      ELSE
        v_plan(v_plan.COUNT+1) := 'COMMENT ON COLUMN HTH_BEA.HTH_CRM_EVENT_DETAILS.' || spec.column_name || ' IS '
          || CHR(39) || REPLACE(spec.description,CHR(39),CHR(39)||CHR(39)) || CHR(39);
      END IF;
    END IF;
  END LOOP;

  -- All known blockers checked first. Each statement is idempotent on the next run.
  FOR j IN 1..v_plan.COUNT LOOP
    v_stage := 'APPLY ' || SUBSTR(v_plan(j),1,100);
    EXECUTE IMMEDIATE v_plan(j);
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('849 schema ready; DDL statements applied=' || v_plan.COUNT);
EXCEPTION
  WHEN OTHERS THEN
    RAISE_APPLICATION_ERROR(-20849,
      '849 schema stage=' || v_stage || ': ' || SUBSTR(SQLERRM,1,1700), TRUE);
END;
