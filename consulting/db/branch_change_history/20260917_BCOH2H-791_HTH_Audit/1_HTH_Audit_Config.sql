-- BCOH2H-791 FINAL: existing seven HTH audit tasks + Re-Generate audit category.
-- Replaces the old 1_HTH_Audit_Config.sql + 3_HTH_Audit_Regenerate.sql combination.
-- Execute this WHOLE DECLARE ... END; block as ONE statement in the OBDX schema.
-- Use a dedicated session with no unrelated uncommitted changes (this block COMMITs).
-- Prerequisite: existing HTH password/user-access task scripts, before backend deployment.
-- Repeat execution inserts no duplicate rows; already-correct rows are not updated.
-- Only audit aspects, missing expected HTH resource mappings and the Re-Generate task are touched.
-- No historical audit rows, BCO tasks, business approval/authorization, menus or notifications change.
-- Short NOWAIT locks protect concurrent configuration edits. If busy, retry after they complete.
-- Any failure rolls back ALL changes made by this block; no partial configuration is committed.
DECLARE
  V_TASK_COUNT NUMBER;
  V_RESOURCE_COUNT NUMBER;
  V_ASPECT_COUNT NUMBER;
  V_COUNT NUMBER;
  V_TASK DIGX_CM_TASK%ROWTYPE;
BEGIN
  SAVEPOINT HTH_791_AUDIT_CONFIG;
  -- Serialize configuration edits and MAX(ID)+1 allocation; fail immediately if busy.
  -- DML locks are released by the final COMMIT or failure rollback.
  LOCK TABLE DIGX_CM_TASK IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE DIGX_CM_TASK_ASPECTS IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE DIGX_CM_RESOURCE_TASK_REL IN SHARE ROW EXCLUSIVE MODE NOWAIT;

  FOR R IN (
    SELECT 'UAT_N_HAP_GEN' TASK_ID, 'HostToHostApiPassword.generate' SERVICE_NAME FROM DUAL
    UNION ALL SELECT 'UAT_N_HAP_RVL', 'HostToHostApiPassword.reveal' FROM DUAL
    UNION ALL SELECT 'CM_N_HAP_SETUP', 'HostToHostApiPassword.setup' FROM DUAL
    UNION ALL SELECT 'CM_N_HAP_RESET', 'HostToHostApiPassword.reset' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_NEW', 'HostToHostUserAccess.submit' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_EDT', 'HostToHostUserAccess.edit' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_DEL', 'HostToHostUserAccess.delete' FROM DUAL
  ) LOOP
    SELECT COUNT(*) INTO V_TASK_COUNT FROM DIGX_CM_TASK WHERE ID = R.TASK_ID;
    IF V_TASK_COUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20079,
        '791: expected exactly one prerequisite task: ' || R.TASK_ID);
    END IF;
    SELECT COUNT(*) INTO V_RESOURCE_COUNT FROM DIGX_CM_RESOURCE_TASK_REL
     WHERE RESOURCE_NAME = 'com.ofss.digx.cz.bea.app.hosttohost.service.' || R.SERVICE_NAME
       AND (TASK_ID <> R.TASK_ID OR TASK_ID IS NULL);
    IF V_RESOURCE_COUNT <> 0 THEN
      RAISE_APPLICATION_ERROR(-20080,
        'Conflicting resource/task mapping: ' || R.SERVICE_NAME || '. No configuration changed.');
    END IF;

    SELECT COUNT(*) INTO V_RESOURCE_COUNT FROM DIGX_CM_RESOURCE_TASK_REL
     WHERE RESOURCE_NAME = 'com.ofss.digx.cz.bea.app.hosttohost.service.' || R.SERVICE_NAME;
    IF V_RESOURCE_COUNT > 1 THEN
      RAISE_APPLICATION_ERROR(-20081,
        'Duplicate HTH resource mapping: ' || R.SERVICE_NAME || '. Review before retrying.');
    END IF;

    SELECT COUNT(*) INTO V_ASPECT_COUNT FROM DIGX_CM_TASK_ASPECTS
     WHERE TASK_ID = R.TASK_ID AND ASPECT = 'audit';
    IF V_ASPECT_COUNT > 1 THEN
      RAISE_APPLICATION_ERROR(-20082,
        'Duplicate HTH audit aspect: ' || R.TASK_ID || '. Review before retrying.');
    END IF;

    MERGE INTO DIGX_CM_TASK_ASPECTS T
    USING (SELECT R.TASK_ID TASK_ID FROM DUAL) S
       ON (T.TASK_ID = S.TASK_ID AND T.ASPECT = 'audit')
    WHEN MATCHED THEN UPDATE SET T.ENABLED = 'Y'
      WHERE T.ENABLED IS NULL OR T.ENABLED <> 'Y'
    WHEN NOT MATCHED THEN INSERT (TASK_ID, ASPECT, ENABLED)
      VALUES (S.TASK_ID, 'audit', 'Y');

    IF V_RESOURCE_COUNT = 0 THEN
      INSERT INTO DIGX_CM_RESOURCE_TASK_REL
        (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
         LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
      VALUES
        ((SELECT NVL(MAX(CASE WHEN REGEXP_LIKE(ID, '^[0-9]+$') THEN TO_NUMBER(ID) END), 0) + 1
            FROM DIGX_CM_RESOURCE_TASK_REL),
         'com.ofss.digx.cz.bea.app.hosttohost.service.' || R.SERVICE_NAME,
         R.TASK_ID, 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);
    END IF;
  END LOOP;

  -- Additional audit-only category; business Generate mapping remains unchanged.
  SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_TASK WHERE ID = 'UAT_N_HAP_GEN';
  IF V_COUNT <> 1 THEN
    RAISE_APPLICATION_ERROR(-20083, '791: expected exactly one existing HTH Generate task');
  END IF;
  SELECT * INTO V_TASK FROM DIGX_CM_TASK WHERE ID = 'UAT_N_HAP_GEN';
  SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_RESOURCE_TASK_REL WHERE TASK_ID = 'UAT_N_HAP_REGEN';
  IF V_COUNT <> 0 THEN
    RAISE_APPLICATION_ERROR(-20084, '791: Re-Generate audit identifier must not map to a business service');
  END IF;
  SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_TASK WHERE ID = 'UAT_N_HAP_REGEN';
  IF V_COUNT = 0 THEN
    -- Inherit the existing task category so resourceTasks?aspects=audit can list it normally.
    V_TASK.ID := 'UAT_N_HAP_REGEN';
    V_TASK.NAME := 'HTH API Password Code - Re-Generate';
    V_TASK.CREATION_DATE := SYSDATE;
    V_TASK.LAST_UPDATED_DATE := SYSDATE;
    INSERT INTO DIGX_CM_TASK VALUES V_TASK;
  ELSIF V_COUNT = 1 THEN
    UPDATE DIGX_CM_TASK SET NAME = 'HTH API Password Code - Re-Generate',
      LAST_UPDATED_DATE = SYSDATE WHERE ID = 'UAT_N_HAP_REGEN'
      AND (NAME IS NULL OR NAME <> 'HTH API Password Code - Re-Generate');
  ELSE
    RAISE_APPLICATION_ERROR(-20085, '791: duplicate Re-Generate task');
  END IF;
  SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_TASK_ASPECTS
   WHERE TASK_ID = 'UAT_N_HAP_REGEN' AND ASPECT = 'audit';
  IF V_COUNT > 1 THEN
    RAISE_APPLICATION_ERROR(-20086, '791: duplicate Re-Generate audit aspect');
  END IF;
  MERGE INTO DIGX_CM_TASK_ASPECTS T
  USING (SELECT 'UAT_N_HAP_REGEN' TASK_ID FROM DUAL) S
     ON (T.TASK_ID = S.TASK_ID AND T.ASPECT = 'audit')
  WHEN MATCHED THEN UPDATE SET T.ENABLED = 'Y'
    WHERE T.ENABLED IS NULL OR T.ENABLED <> 'Y'
  WHEN NOT MATCHED THEN INSERT (TASK_ID, ASPECT, ENABLED) VALUES (S.TASK_ID, 'audit', 'Y');
  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_791_AUDIT_CONFIG;
    RAISE;
END;
