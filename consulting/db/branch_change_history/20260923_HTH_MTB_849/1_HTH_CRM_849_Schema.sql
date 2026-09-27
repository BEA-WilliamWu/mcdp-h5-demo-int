-- BCOH2H-849: run with the HTH_BEA schema connection, in a dedicated session.
-- DBeaver (Oracle connection): Execute SQL Script (Alt+X); no standalone slash.
-- Execute SQL Statement: select the complete DECLARE ... END; block.
-- One anonymous block, without local routines: avoid premature client-side splitting.
-- Fresh install creates the canonical table. Existing tables are upgraded in place.
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
BEGIN
  SELECT COUNT(*) INTO v_count FROM ALL_TABLES
   WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS';
  IF v_count=0 THEN
    v_stage := 'TABLE_CREATE';
    EXECUTE IMMEDIATE q'~CREATE TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS (
      EVENT_ID VARCHAR2(36 CHAR) NOT NULL,
      EVENT_DTE VARCHAR2(8 CHAR) NOT NULL,
      EVENT_TIME VARCHAR2(6 CHAR) NOT NULL,
      SOURCE_SYSTEM VARCHAR2(16 CHAR) NOT NULL,
      CHANNEL_TYPE VARCHAR2(16 CHAR) NOT NULL,
      TARGET_USER_CHANNEL VARCHAR2(16 CHAR),
      ACTIVITY_KEY VARCHAR2(64 CHAR) NOT NULL,
      EVENT_ACTV_TYPE_CODE VARCHAR2(64 CHAR),
      EVENT_STATUS_CODE VARCHAR2(16 CHAR) NOT NULL,
      PHASE VARCHAR2(32 CHAR) NOT NULL,
      FIN_IND VARCHAR2(1 CHAR) NOT NULL,
      USER_ID VARCHAR2(256 CHAR),
      TARGET_USER_ID VARCHAR2(256 CHAR),
      ACCT_NBR VARCHAR2(64 CHAR),
      RELATIONSHIP_TYPE VARCHAR2(32 CHAR),
      SERVICE_ID VARCHAR2(256 CHAR) NOT NULL,
      TASK_CODE VARCHAR2(100 CHAR),
      SOURCE_TRX_REF_NBR VARCHAR2(128 CHAR),
      SOURCE_ACTION_ID VARCHAR2(128 CHAR),
      REQUEST_ID VARCHAR2(128 CHAR),
      IP_ADDRESS VARCHAR2(64 CHAR),
      ERROR_CODE VARCHAR2(100 CHAR),
      DEDUP_KEY VARCHAR2(64 CHAR),
      CREATED_AT TIMESTAMP(6) NOT NULL,
      CONSTRAINT PK_HTH_MTB_EVENT PRIMARY KEY (EVENT_ID)
    )~';
  END IF;

  v_stage := 'PREFLIGHT';
  FOR spec IN (
    SELECT 'EVENT_ID' column_name, 36 column_length, 'N' column_nullable FROM DUAL
    UNION ALL SELECT 'EVENT_DTE', 8, 'N' FROM DUAL
    UNION ALL SELECT 'EVENT_TIME', 6, 'N' FROM DUAL
    UNION ALL SELECT 'SOURCE_SYSTEM', 16, 'N' FROM DUAL
    UNION ALL SELECT 'CHANNEL_TYPE', 16, 'N' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_CHANNEL', 16, 'Y' FROM DUAL
    UNION ALL SELECT 'ACTIVITY_KEY', 64, 'N' FROM DUAL
    UNION ALL SELECT 'EVENT_ACTV_TYPE_CODE', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'EVENT_STATUS_CODE', 16, 'N' FROM DUAL
    UNION ALL SELECT 'PHASE', 32, 'N' FROM DUAL
    UNION ALL SELECT 'FIN_IND', 1, 'N' FROM DUAL
    UNION ALL SELECT 'USER_ID', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'TARGET_USER_ID', 256, 'Y' FROM DUAL
    UNION ALL SELECT 'ACCT_NBR', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'RELATIONSHIP_TYPE', 32, 'Y' FROM DUAL
    UNION ALL SELECT 'SERVICE_ID', 256, 'N' FROM DUAL
    UNION ALL SELECT 'TASK_CODE', 100, 'Y' FROM DUAL
    UNION ALL SELECT 'SOURCE_TRX_REF_NBR', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'SOURCE_ACTION_ID', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'REQUEST_ID', 128, 'Y' FROM DUAL
    UNION ALL SELECT 'IP_ADDRESS', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'ERROR_CODE', 100, 'Y' FROM DUAL
    UNION ALL SELECT 'DEDUP_KEY', 64, 'Y' FROM DUAL
    UNION ALL SELECT 'CREATED_AT', NULL, 'N' FROM DUAL
  ) LOOP
    v_change := FALSE;
    v_expected(spec.column_name) := TRUE;
    IF spec.column_length IS NULL THEN
      v_definition := 'TIMESTAMP(6)';
    ELSE
      v_definition := 'VARCHAR2(' || TO_CHAR(spec.column_length,'FM99990') || ' CHAR)';
    END IF;
    SELECT COUNT(*) INTO n FROM ALL_TAB_COLS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
       AND COLUMN_NAME=spec.column_name;
    IF n=0 THEN
      IF spec.column_name='EVENT_ID' THEN
        v_issues(v_issues.COUNT+1) := 'EVENT_ID missing; cannot invent historical IDs';
      ELSE
        v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS ADD (' || spec.column_name || ' ' || v_definition || ')';
      END IF;
      CONTINUE;
    END IF;
    SELECT * INTO v_col FROM ALL_TAB_COLS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
       AND COLUMN_NAME=spec.column_name;
    v_actual := spec.column_name || '=' || v_col.DATA_TYPE || ', bytes=' || v_col.DATA_LENGTH
      || ', chars=' || v_col.CHAR_LENGTH || ', semantics=' || v_col.CHAR_USED || ', nullable=' || v_col.NULLABLE;
    IF v_col.VIRTUAL_COLUMN='YES' OR v_col.IDENTITY_COLUMN='YES' THEN
      v_issues(v_issues.COUNT+1) := v_actual || ': generated column cannot receive application values';
      CONTINUE;
    END IF;
    IF spec.column_nullable='Y' AND v_col.NULLABLE='N' THEN
      v_issues(v_issues.COUNT+1) := v_actual || ': application may supply NULL; constraint preserved';
    END IF;
    IF spec.column_length IS NULL THEN
      IF v_col.DATA_TYPE NOT LIKE 'TIMESTAMP(%)' THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': expected plain TIMESTAMP';
        CONTINUE;
      END IF;
      v_change := NVL(v_col.DATA_SCALE,0)<6;
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
         WHERE OWNER='HTH_BEA' AND NAME='HTH_MTB_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
        UNION ALL
        SELECT COLUMN_NAME FROM ALL_SUBPART_KEY_COLUMNS
         WHERE OWNER='HTH_BEA' AND NAME='HTH_MTB_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
      ) WHERE COLUMN_NAME=spec.column_name;
      IF n>0 THEN
        v_issues(v_issues.COUNT+1) := v_actual || ': partition key requires a separate migration';
      ELSE
        v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS MODIFY (' || spec.column_name || ' ' || v_definition || ')';
      END IF;
    END IF;
  END LOOP;

  -- Extra legacy fields stay intact. Do not guess values or relax required constraints.
  FOR c IN (SELECT COLUMN_NAME FROM ALL_TAB_COLS
             WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
               AND USER_GENERATED='YES' AND NULLABLE='N'
               AND VIRTUAL_COLUMN='NO' AND IDENTITY_COLUMN='NO') LOOP
    IF NOT v_expected.EXISTS(c.COLUMN_NAME) THEN
      v_issues(v_issues.COUNT+1) := c.COLUMN_NAME || ': extra NOT NULL column not supplied by 849; requires explicit mapping/default review';
    END IF;
  END LOOP;

  SELECT COUNT(*) INTO v_pk FROM ALL_CONSTRAINTS c
   WHERE c.OWNER='HTH_BEA' AND c.TABLE_NAME='HTH_MTB_EVENT_DETAILS'
     AND c.CONSTRAINT_TYPE='P' AND c.STATUS='ENABLED' AND c.VALIDATED='VALIDATED'
     AND 1=(SELECT COUNT(*) FROM ALL_CONS_COLUMNS x
             WHERE x.OWNER=c.OWNER AND x.CONSTRAINT_NAME=c.CONSTRAINT_NAME)
     AND EXISTS (SELECT 1 FROM ALL_CONS_COLUMNS x
                  WHERE x.OWNER=c.OWNER AND x.CONSTRAINT_NAME=c.CONSTRAINT_NAME
                    AND x.COLUMN_NAME='EVENT_ID');
  IF v_pk<>1 THEN
    SELECT COUNT(*) INTO v_count FROM ALL_CONSTRAINTS
     WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS' AND CONSTRAINT_TYPE='P';
    IF v_count>0 THEN
      v_issues(v_issues.COUNT+1) := 'Existing primary key must be enabled, validated and on EVENT_ID only; constraint preserved';
    ELSE
      SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
         AND COLUMN_NAME='EVENT_ID' AND DATA_TYPE='VARCHAR2';
      IF v_count=1 THEN
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT EVENT_ID FROM HTH_BEA.HTH_MTB_EVENT_DETAILS GROUP BY EVENT_ID HAVING EVENT_ID IS NULL OR COUNT(*)>1)' INTO v_count;
        IF v_count>0 THEN
          v_issues(v_issues.COUNT+1) := 'EVENT_ID has null/duplicate values; cannot add primary key without changing data';
        ELSE
          SELECT COUNT(*) INTO v_count FROM ALL_CONSTRAINTS
           WHERE OWNER='HTH_BEA' AND CONSTRAINT_NAME='PK_HTH_MTB_EVENT';
          IF v_count>0 THEN
            v_issues(v_issues.COUNT+1) := 'PK_HTH_MTB_EVENT name already used; existing constraint preserved';
          ELSE
            v_plan(v_plan.COUNT+1) := 'ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS ADD CONSTRAINT PK_HTH_MTB_EVENT PRIMARY KEY (EVENT_ID)';
          END IF;
        END IF;
      END IF;
    END IF;
  END IF;

  SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
   WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
     AND COLUMN_NAME='DEDUP_KEY' AND DATA_TYPE='VARCHAR2';
  IF v_count=1 THEN
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT DEDUP_KEY FROM HTH_BEA.HTH_MTB_EVENT_DETAILS WHERE DEDUP_KEY IS NOT NULL GROUP BY DEDUP_KEY HAVING COUNT(*)>1)' INTO v_count;
    IF v_count>0 THEN v_issues(v_issues.COUNT+1) := 'DEDUP_KEY has duplicate non-null values; no records deleted'; END IF;
  END IF;

  FOR spec IN (
    SELECT 'UX_HTH_MTB_DEDUP' index_name, 'DEDUP_KEY' column_names, 'UNIQUE' index_uniqueness FROM DUAL
    UNION ALL SELECT 'IX_HTH_MTB_DATE_ACT', 'EVENT_DTE,ACTIVITY_KEY', 'NONUNIQUE' FROM DUAL
    UNION ALL SELECT 'IX_HTH_MTB_SOURCE', 'SOURCE_TRX_REF_NBR', 'NONUNIQUE' FROM DUAL
  ) LOOP
    v_found := FALSE;
    -- The expected name cannot already belong to another object/table.
    SELECT COUNT(*) INTO n FROM ALL_OBJECTS WHERE OWNER='HTH_BEA' AND OBJECT_NAME=spec.index_name
      AND OBJECT_TYPE<>'INDEX';
    IF n>0 THEN v_issues(v_issues.COUNT+1) := spec.index_name || ': name belongs to another object'; END IF;
    FOR i IN (SELECT INDEX_NAME,TABLE_OWNER,TABLE_NAME,UNIQUENESS,STATUS,INDEX_TYPE
                FROM ALL_INDEXES WHERE OWNER='HTH_BEA'
                  AND (INDEX_NAME=spec.index_name OR (TABLE_OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'))) LOOP
      SELECT LISTAGG(COLUMN_NAME,',') WITHIN GROUP (ORDER BY COLUMN_POSITION)
        INTO v_columns FROM ALL_IND_COLUMNS
       WHERE INDEX_OWNER='HTH_BEA' AND INDEX_NAME=i.INDEX_NAME;
      IF i.TABLE_OWNER='HTH_BEA' AND i.TABLE_NAME='HTH_MTB_EVENT_DETAILS'
          AND v_columns=spec.column_names AND i.UNIQUENESS=spec.index_uniqueness AND i.STATUS='VALID'
          AND i.INDEX_TYPE='NORMAL' THEN
        v_found := TRUE;
      ELSIF i.INDEX_NAME=spec.index_name OR v_columns=spec.column_names THEN
        v_issues(v_issues.COUNT+1) := i.INDEX_NAME || ': incompatible index (' || v_columns || ', ' || i.UNIQUENESS || ', ' || i.STATUS || ')';
      END IF;
    END LOOP;
    IF NOT v_found THEN
      v_plan(v_plan.COUNT+1) := 'CREATE ' || CASE WHEN spec.index_uniqueness='UNIQUE' THEN 'UNIQUE ' ELSE '' END
        || 'INDEX HTH_BEA.' || spec.index_name || ' ON HTH_BEA.HTH_MTB_EVENT_DETAILS (' || spec.column_names || ')';
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
