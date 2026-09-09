-- Oracle SQL/PLSQL; no SQL*Plus commands or substitution variables.
-- Execute each complete DECLARE/BEGIN ... END; block as one statement (no slash).
-- BCOH2H-788 / BCOH2H-790: self-service task and audit configuration.
-- Execute in the OBDX configuration schema before 3_HTH_API_Password_Permission.sql.
-- These are immediate current-user security operations: audit is enabled, approval and 2FA are
-- deliberately not configured. The HTH API Password Code is the story-defined authorization code.
-- Re-runnable.

DECLARE
  V_SOURCE_TASK_COUNT NUMBER;
BEGIN
  SAVEPOINT HTH_API_PASSWORD_CONFIG;
  SELECT COUNT(*) INTO V_SOURCE_TASK_COUNT
    FROM DIGX_CM_TASK
   WHERE ID = 'CM_N_CC';

  IF V_SOURCE_TASK_COUNT <> 1 THEN
    RAISE_APPLICATION_ERROR(-20003,
      'Source BCO Change Credentials task CM_N_CC was not found; no HTH tasks were changed.');
  END IF;


DELETE FROM DIGX_CM_RESOURCE_TASK_REL
 WHERE TASK_ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET')
    OR RESOURCE_NAME IN (
      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
    );

DELETE FROM DIGX_CM_TASK_ASPECTS
 WHERE TASK_ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET');

DELETE FROM DIGX_CM_TASK
 WHERE ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET');

-- Copy stable BCO Change Credentials classification instead of hard-coding an environment-specific
-- MODULE_TYPE value (for example BO vs BACK_OFFICE). Only AUDIT is registered below; approval/2FA
-- aspects are intentionally not copied because the story-defined Password Code authorizes this
-- immediate self-service operation.
INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
SELECT P.TARGET_TASK_ID, P.TARGET_TASK_NAME, S.PARENT_ID, S.EXECUTABLE,
       S.TASK_TYPE, S.MODULE_TYPE, 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
       S.OBJECT_STATUS, S.OBJECT_VERSION_NUMBER
  FROM DIGX_CM_TASK S
  CROSS JOIN (
    SELECT 'CM_N_HAP_SETUP' TARGET_TASK_ID,
           'Set Up HTH API Password' TARGET_TASK_NAME FROM DUAL
    UNION ALL
    SELECT 'CM_N_HAP_RESET', 'Reset HTH API Password' FROM DUAL
  ) P
 WHERE S.ID = 'CM_N_CC';

INSERT ALL
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('CM_N_HAP_SETUP', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('CM_N_HAP_RESET', 'audit', 'Y')
SELECT 1 FROM DUAL;

INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 FROM DIGX_CM_RESOURCE_TASK_REL),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
   'CM_N_HAP_SETUP', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 FROM DIGX_CM_RESOURCE_TASK_REL),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset',
   'CM_N_HAP_RESET', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_API_PASSWORD_CONFIG;
    RAISE;
END;

SELECT COUNT(*) AS SOURCE_CHANGE_CREDENTIAL_TASK
  FROM DIGX_CM_TASK WHERE ID = 'CM_N_CC';

SELECT T.ID, T.NAME, T.TASK_TYPE, T.MODULE_TYPE, A.ASPECT, A.ENABLED,
       CASE WHEN T.TASK_TYPE = S.TASK_TYPE AND T.MODULE_TYPE = S.MODULE_TYPE
            THEN 'MATCH' ELSE 'MISMATCH' END AS BCO_CLASSIFICATION
  FROM DIGX_CM_TASK T
  CROSS JOIN (SELECT TASK_TYPE, MODULE_TYPE FROM DIGX_CM_TASK WHERE ID = 'CM_N_CC') S
  JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID = T.ID
 WHERE T.ID IN ('CM_N_HAP_SETUP', 'CM_N_HAP_RESET')
 ORDER BY T.ID, A.ASPECT;
