-- BCOH2H-849: run with the HTH_BEA schema connection, in a dedicated session.
-- Execute the WHOLE DECLARE ... END; block as one statement, no standalone slash.
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
  TYPE ddl_list IS TABLE OF VARCHAR2(1000) INDEX BY PLS_INTEGER;
  v_plan ddl_list;
  TYPE name_set IS TABLE OF BOOLEAN INDEX BY VARCHAR2(128);
  v_expected name_set;

  PROCEDURE problem(p_message VARCHAR2) IS
  BEGIN
    DBMS_OUTPUT.PUT_LINE('849 preflight: ' || p_message);
    IF NVL(LENGTH(v_errors),0) + LENGTH(p_message) + 2 <= 1700 THEN
      v_errors := v_errors || p_message || '; ';
    ELSE
      v_errors := SUBSTR(v_errors,1,1650) || ' [more: see DBMS Output]';
    END IF;
  END;

  PROCEDURE plan(p_ddl VARCHAR2) IS
  BEGIN
    v_plan(v_plan.COUNT+1) := p_ddl;
  END;

  PROCEDURE check_column(p_name VARCHAR2, p_length NUMBER, p_nullable CHAR) IS
    c ALL_TAB_COLS%ROWTYPE;
    n NUMBER;
    v_definition VARCHAR2(100);
    v_change BOOLEAN := FALSE;
    v_actual VARCHAR2(300);
  BEGIN
    v_expected(p_name) := TRUE;
    IF p_length IS NULL THEN
      v_definition := 'TIMESTAMP(6)';
    ELSE
      v_definition := 'VARCHAR2(' || TO_CHAR(p_length,'FM99990') || ' CHAR)';
    END IF;
    BEGIN
      SELECT * INTO c FROM ALL_TAB_COLS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
         AND COLUMN_NAME=p_name;
    EXCEPTION WHEN NO_DATA_FOUND THEN
      IF p_name='EVENT_ID' THEN
        problem('EVENT_ID missing; cannot invent historical IDs');
      ELSE
        plan('ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS ADD (' || p_name || ' ' || v_definition || ')');
      END IF;
      RETURN;
    END;
    v_actual := p_name || '=' || c.DATA_TYPE || ', bytes=' || c.DATA_LENGTH
      || ', chars=' || c.CHAR_LENGTH || ', semantics=' || c.CHAR_USED || ', nullable=' || c.NULLABLE;
    IF c.VIRTUAL_COLUMN='YES' OR c.IDENTITY_COLUMN='YES' THEN
      problem(v_actual || ': generated column cannot receive application values');
      RETURN;
    END IF;
    IF p_nullable='Y' AND c.NULLABLE='N' THEN
      problem(v_actual || ': application may supply NULL; constraint preserved');
    END IF;
    IF p_length IS NULL THEN
      IF c.DATA_TYPE NOT LIKE 'TIMESTAMP(%)' THEN
        problem(v_actual || ': expected plain TIMESTAMP');
        RETURN;
      END IF;
      v_change := NVL(c.DATA_SCALE,0)<6;
    ELSE
      IF c.DATA_TYPE<>'VARCHAR2' OR c.CHAR_USED NOT IN ('B','C') THEN
        problem(v_actual || ': expected VARCHAR2');
        RETURN;
      END IF;
      IF c.CHAR_USED='B' THEN
        -- One existing character consumes at least one byte; this never reduces capacity.
        v_definition := 'VARCHAR2(' || TO_CHAR(GREATEST(p_length,c.DATA_LENGTH),'FM99990') || ' CHAR)';
        v_change := TRUE;
      ELSE
        v_change := c.CHAR_LENGTH<p_length;
      END IF;
    END IF;
    IF v_change THEN
      SELECT COUNT(*) INTO n FROM (
        SELECT COLUMN_NAME FROM ALL_PART_KEY_COLUMNS
         WHERE OWNER='HTH_BEA' AND NAME='HTH_MTB_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
        UNION ALL
        SELECT COLUMN_NAME FROM ALL_SUBPART_KEY_COLUMNS
         WHERE OWNER='HTH_BEA' AND NAME='HTH_MTB_EVENT_DETAILS' AND OBJECT_TYPE='TABLE'
      ) WHERE COLUMN_NAME=p_name;
      IF n>0 THEN
        problem(v_actual || ': partition key requires a separate migration');
      ELSE
        plan('ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS MODIFY (' || p_name || ' ' || v_definition || ')');
      END IF;
    END IF;
  END;

  PROCEDURE check_index(p_name VARCHAR2, p_columns VARCHAR2, p_unique VARCHAR2) IS
    v_found BOOLEAN := FALSE;
    n NUMBER;
    v_columns VARCHAR2(1000);
  BEGIN
    -- The expected name cannot already belong to another object/table.
    SELECT COUNT(*) INTO n FROM ALL_OBJECTS WHERE OWNER='HTH_BEA' AND OBJECT_NAME=p_name
      AND OBJECT_TYPE<>'INDEX';
    IF n>0 THEN problem(p_name || ': name belongs to another object'); END IF;
    FOR i IN (SELECT INDEX_NAME,TABLE_OWNER,TABLE_NAME,UNIQUENESS,STATUS,INDEX_TYPE
                FROM ALL_INDEXES WHERE OWNER='HTH_BEA'
                  AND (INDEX_NAME=p_name OR (TABLE_OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'))) LOOP
      SELECT LISTAGG(COLUMN_NAME,',') WITHIN GROUP (ORDER BY COLUMN_POSITION)
        INTO v_columns FROM ALL_IND_COLUMNS
       WHERE INDEX_OWNER='HTH_BEA' AND INDEX_NAME=i.INDEX_NAME;
      IF i.TABLE_OWNER='HTH_BEA' AND i.TABLE_NAME='HTH_MTB_EVENT_DETAILS'
          AND v_columns=p_columns AND i.UNIQUENESS=p_unique AND i.STATUS='VALID'
          AND i.INDEX_TYPE='NORMAL' THEN
        v_found := TRUE;
      ELSIF i.INDEX_NAME=p_name OR v_columns=p_columns THEN
        problem(i.INDEX_NAME || ': incompatible index (' || v_columns || ', ' || i.UNIQUENESS || ', ' || i.STATUS || ')');
      END IF;
    END LOOP;
    IF NOT v_found THEN
      plan('CREATE ' || CASE WHEN p_unique='UNIQUE' THEN 'UNIQUE ' ELSE '' END
        || 'INDEX HTH_BEA.' || p_name || ' ON HTH_BEA.HTH_MTB_EVENT_DETAILS (' || p_columns || ')');
    END IF;
  END;
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
  check_column('EVENT_ID',36,'N');
  check_column('EVENT_DTE',8,'N');
  check_column('EVENT_TIME',6,'N');
  check_column('SOURCE_SYSTEM',16,'N');
  check_column('CHANNEL_TYPE',16,'N');
  check_column('TARGET_USER_CHANNEL',16,'Y');
  check_column('ACTIVITY_KEY',64,'N');
  check_column('EVENT_ACTV_TYPE_CODE',64,'Y');
  check_column('EVENT_STATUS_CODE',16,'N');
  check_column('PHASE',32,'N');
  check_column('FIN_IND',1,'N');
  check_column('USER_ID',256,'Y');
  check_column('TARGET_USER_ID',256,'Y');
  check_column('ACCT_NBR',64,'Y');
  check_column('RELATIONSHIP_TYPE',32,'Y');
  check_column('SERVICE_ID',256,'N');
  check_column('TASK_CODE',100,'Y');
  check_column('SOURCE_TRX_REF_NBR',128,'Y');
  check_column('SOURCE_ACTION_ID',128,'Y');
  check_column('REQUEST_ID',128,'Y');
  check_column('IP_ADDRESS',64,'Y');
  check_column('ERROR_CODE',100,'Y');
  check_column('DEDUP_KEY',64,'Y');
  check_column('CREATED_AT',NULL,'N');

  -- Extra legacy fields stay intact. Do not guess values or relax required constraints.
  FOR c IN (SELECT COLUMN_NAME FROM ALL_TAB_COLS
             WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
               AND USER_GENERATED='YES' AND NULLABLE='N'
               AND VIRTUAL_COLUMN='NO' AND IDENTITY_COLUMN='NO') LOOP
    IF NOT v_expected.EXISTS(c.COLUMN_NAME) THEN
      problem(c.COLUMN_NAME || ': extra NOT NULL column not supplied by 849; requires explicit mapping/default review');
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
      problem('Existing primary key must be enabled, validated and on EVENT_ID only; constraint preserved');
    ELSE
      SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
       WHERE OWNER='HTH_BEA' AND TABLE_NAME='HTH_MTB_EVENT_DETAILS'
         AND COLUMN_NAME='EVENT_ID' AND DATA_TYPE='VARCHAR2';
      IF v_count=1 THEN
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM (SELECT EVENT_ID FROM HTH_BEA.HTH_MTB_EVENT_DETAILS GROUP BY EVENT_ID HAVING EVENT_ID IS NULL OR COUNT(*)>1)' INTO v_count;
        IF v_count>0 THEN
          problem('EVENT_ID has null/duplicate values; cannot add primary key without changing data');
        ELSE
          SELECT COUNT(*) INTO v_count FROM ALL_CONSTRAINTS
           WHERE OWNER='HTH_BEA' AND CONSTRAINT_NAME='PK_HTH_MTB_EVENT';
          IF v_count>0 THEN
            problem('PK_HTH_MTB_EVENT name already used; existing constraint preserved');
          ELSE
            plan('ALTER TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS ADD CONSTRAINT PK_HTH_MTB_EVENT PRIMARY KEY (EVENT_ID)');
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
    IF v_count>0 THEN problem('DEDUP_KEY has duplicate non-null values; no records deleted'); END IF;
  END IF;

  check_index('UX_HTH_MTB_DEDUP','DEDUP_KEY','UNIQUE');
  check_index('IX_HTH_MTB_DATE_ACT','EVENT_DTE,ACTIVITY_KEY','NONUNIQUE');
  check_index('IX_HTH_MTB_SOURCE','SOURCE_TRX_REF_NBR','NONUNIQUE');
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
