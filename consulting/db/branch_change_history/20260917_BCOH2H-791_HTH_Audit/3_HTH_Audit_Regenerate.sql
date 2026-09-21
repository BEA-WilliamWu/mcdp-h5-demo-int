-- BCOH2H-791: list-visible Re-Generate activity. Run BEFORE deploying the audit helper.
-- Run the entire PL/SQL block as one statement in the OBDX configuration schema.
-- Audit classification only: NO resource mapping, approval, authorization, menu or notification changes.
-- Existing Generate task and historical audit rows remain unchanged. Safe to re-run.
DECLARE
  V_COUNT NUMBER;
  V_TASK DIGX_CM_TASK%ROWTYPE;
BEGIN
  SAVEPOINT HTH_791_REGENERATE;
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
      LAST_UPDATED_DATE = SYSDATE WHERE ID = 'UAT_N_HAP_REGEN';
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
  WHEN NOT MATCHED THEN INSERT (TASK_ID, ASPECT, ENABLED) VALUES (S.TASK_ID, 'audit', 'Y');
  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_791_REGENERATE;
    RAISE;
END;
