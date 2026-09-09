-- BCOH2H-787: task/aspect configuration for the HTH API Password Code helper operations.
-- The business flow itself is unchanged: the code rides inside the original user-maintenance
-- maker/checker transactions (MT_N_CUS / MT_N_UUS) and becomes usable only when that flow is
-- approved. The two tasks below are audit-only (no approval aspect) so generate/reveal execute
-- immediately while still landing in the audit trail.
-- Re-runnable: feature-owned rows are replaced; single commit at the end.

-- 1. Remove feature-owned configuration before re-inserting.
DELETE FROM DIGX_CM_RESOURCE_TASK_REL
 WHERE TASK_ID IN ('UAT_N_HAP_GEN', 'UAT_N_HAP_RVL')
    OR RESOURCE_NAME IN (
     'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate',
     'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal'
    );

DELETE FROM DIGX_CM_TASK_ASPECTS
 WHERE TASK_ID IN ('UAT_N_HAP_GEN', 'UAT_N_HAP_RVL');

DELETE FROM DIGX_CM_TASK
 WHERE ID IN ('UAT_N_HAP_GEN', 'UAT_N_HAP_RVL');

-- 2. Register tasks under the existing User Account Access parent task UAT (same parent the
--    HTH flows are configured under).
INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('UAT_N_HAP_GEN', 'HTH API Password - Generate', 'UAT', 'Y', 'MAINTENANCE',
   'BO', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('UAT_N_HAP_RVL', 'HTH API Password - Reveal', 'UAT', 'Y', 'MAINTENANCE',
   'BO', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

-- 3. Audit + blackout only; activation follows the original user-maintenance approval.
INSERT ALL
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HAP_GEN', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HAP_GEN', 'blackout', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HAP_RVL', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HAP_RVL', 'blackout', 'Y')
SELECT 1 FROM DUAL;

-- 4. Link the write resources to their audit tasks.
INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT A.ID FROM
      (SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 AS ID
         FROM DIGX_CM_RESOURCE_TASK_REL) A),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate',
   'UAT_N_HAP_GEN', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT A.ID FROM
      (SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 AS ID
         FROM DIGX_CM_RESOURCE_TASK_REL) A),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal',
   'UAT_N_HAP_RVL', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

COMMIT;
