-- Oracle PL/SQL. Execute the complete DECLARE ... END; as ONE statement (no slash).
-- Run after the HTH API Password and HTH User Access process scripts.
-- Re-runnable; no role grants, no new audit table, no changes to other task aspects.
DECLARE
  V_COUNT NUMBER;
BEGIN
  SAVEPOINT HTH_AUDIT_791;
  FOR R IN (
    SELECT 'UAT_N_HAP_GEN' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'UAT_N_HAP_RVL' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'CM_N_HAP_SETUP' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'CM_N_HAP_RESET' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'UAT_N_HUA_NEW' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'UAT_N_HUA_EDT' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit' RESOURCE_NAME FROM DUAL
    UNION ALL
    SELECT 'UAT_N_HUA_DEL' TASK_ID, 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete' RESOURCE_NAME FROM DUAL
  ) LOOP
    SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_TASK WHERE ID=R.TASK_ID;
    IF V_COUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20791, 'Missing HTH task ' || R.TASK_ID || '; run its process script first.');
    END IF;
    SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_RESOURCE_TASK_REL
      WHERE RESOURCE_NAME=R.RESOURCE_NAME AND TASK_ID<>R.TASK_ID;
    IF V_COUNT > 0 THEN
      RAISE_APPLICATION_ERROR(-20792, 'Conflicting HTH resource mapping; resolve before deployment.');
    END IF;
    MERGE INTO DIGX_CM_TASK_ASPECTS T
    USING (SELECT R.TASK_ID TASK_ID, 'audit' ASPECT FROM DUAL) S
    ON (T.TASK_ID=S.TASK_ID AND T.ASPECT=S.ASPECT)
    WHEN MATCHED THEN UPDATE SET T.ENABLED='Y'
    WHEN NOT MATCHED THEN INSERT (TASK_ID,ASPECT,ENABLED) VALUES(S.TASK_ID,S.ASPECT,'Y');

    SELECT COUNT(*) INTO V_COUNT FROM DIGX_CM_RESOURCE_TASK_REL
      WHERE RESOURCE_NAME=R.RESOURCE_NAME AND TASK_ID=R.TASK_ID;
    IF V_COUNT=0 THEN
      INSERT INTO DIGX_CM_RESOURCE_TASK_REL
        (ID,RESOURCE_NAME,TASK_ID,CREATED_BY,CREATION_DATE,LAST_UPDATED_BY,LAST_UPDATED_DATE,OBJECT_STATUS,OBJECT_VERSION_NUMBER)
      VALUES ((SELECT NVL(MAX(CASE WHEN REGEXP_LIKE(ID,'^[0-9]+$') THEN TO_NUMBER(ID) END),0)+1
                  FROM DIGX_CM_RESOURCE_TASK_REL),
              R.RESOURCE_NAME,R.TASK_ID,'ofssuser',SYSDATE,'ofssuser',SYSDATE,NULL,1);
    ELSIF V_COUNT<>1 THEN
      RAISE_APPLICATION_ERROR(-20793, 'Duplicate HTH resource mapping; resolve before deployment.');
    END IF;
  END LOOP;
  COMMIT;
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO HTH_AUDIT_791;
  RAISE;
END;
