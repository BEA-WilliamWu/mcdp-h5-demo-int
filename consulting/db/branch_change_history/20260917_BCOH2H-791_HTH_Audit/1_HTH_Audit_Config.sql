-- BCOH2H-791: repair only the seven existing HTH audit registrations.
-- Execute in the OBDX configuration schema, after the HTH password/user-access scripts.
-- Execute the complete DECLARE ... END; block as one statement. No SQL*Plus commands.
-- Re-runnable. Does not change task classification, approval, 2FA, permissions or BCO tasks.
DECLARE
  V_TASK_COUNT NUMBER;
  V_RESOURCE_COUNT NUMBER;
  V_ASPECT_COUNT NUMBER;
BEGIN
  SAVEPOINT HTH_791_AUDIT_CONFIG;
  SELECT COUNT(*) INTO V_TASK_COUNT FROM DIGX_CM_TASK
   WHERE ID IN ('UAT_N_HAP_GEN', 'UAT_N_HAP_RVL', 'CM_N_HAP_SETUP', 'CM_N_HAP_RESET',
                'UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL');
  IF V_TASK_COUNT <> 7 THEN
    RAISE_APPLICATION_ERROR(-20079,
      'Missing HTH tasks: deploy the HTH password and user-access task scripts first.');
  END IF;

  FOR R IN (
    SELECT 'UAT_N_HAP_GEN' TASK_ID, 'HostToHostApiPassword.generate' SERVICE_NAME FROM DUAL
    UNION ALL SELECT 'UAT_N_HAP_RVL', 'HostToHostApiPassword.reveal' FROM DUAL
    UNION ALL SELECT 'CM_N_HAP_SETUP', 'HostToHostApiPassword.setup' FROM DUAL
    UNION ALL SELECT 'CM_N_HAP_RESET', 'HostToHostApiPassword.reset' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_NEW', 'HostToHostUserAccess.submit' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_EDT', 'HostToHostUserAccess.edit' FROM DUAL
    UNION ALL SELECT 'UAT_N_HUA_DEL', 'HostToHostUserAccess.delete' FROM DUAL
  ) LOOP
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
  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_791_AUDIT_CONFIG;
    RAISE;
END;
