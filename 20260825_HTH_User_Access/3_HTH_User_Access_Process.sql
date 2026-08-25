-- BCOH2H-595: maker/checker process configuration for HTH User Access.
-- Run after 2_HTH_User_Access_Permission.sql.
-- Re-runnable: feature-owned tasks, resource relations, and assembler rows are replaced.

-- 1. Remove feature-owned approval assembler and task configuration.
DELETE FROM DIGX_FW_CONFIG_ALL_O
 WHERE PROP_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete'
 )
   AND PREFERENCE_NAME = 'ApprovalAssemblers';

DELETE FROM DIGX_FW_CONFIG_ALL_B
 WHERE PROP_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete'
 )
   AND CATEGORY_ID = 'approval_assembler';

DELETE FROM DIGX_CM_RESOURCE_TASK_REL
 WHERE TASK_ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
    OR RESOURCE_NAME IN (
     'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
     'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
     'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete'
    );

DELETE FROM DIGX_CM_TASK_ASPECTS
 WHERE TASK_ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL');

DELETE FROM DIGX_CM_TASK
 WHERE ID IN ('UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL');

-- 2. Register tasks under the existing User Account Access parent task UAT.
INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('UAT_N_HUA_NEW', 'HTH User Access - Create', 'UAT', 'Y', 'MAINTENANCE',
   'BO', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('UAT_N_HUA_EDT', 'HTH User Access - Edit', 'UAT', 'Y', 'MAINTENANCE',
   'BO', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_TASK
  (ID, NAME, PARENT_ID, EXECUTABLE, TASK_TYPE, MODULE_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE, OBJECT_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('UAT_N_HUA_DEL', 'HTH User Access - Delete', 'UAT', 'Y', 'MAINTENANCE',
   'BO', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

-- 3. Enable approval, audit, and blackout aspects for all write operations.
INSERT ALL
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_NEW', 'approval', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_NEW', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_NEW', 'blackout', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_EDT', 'approval', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_EDT', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_EDT', 'blackout', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_DEL', 'approval', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_DEL', 'audit', 'Y')
  INTO DIGX_CM_TASK_ASPECTS (TASK_ID, ASPECT, ENABLED)
  VALUES ('UAT_N_HUA_DEL', 'blackout', 'Y')
SELECT 1 FROM DUAL;

-- 4. Link each write resource to its workflow task.
INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT A.ID FROM
      (SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 AS ID
         FROM DIGX_CM_RESOURCE_TASK_REL) A),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
   'UAT_N_HUA_NEW', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT A.ID FROM
      (SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 AS ID
         FROM DIGX_CM_RESOURCE_TASK_REL) A),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
   'UAT_N_HUA_EDT', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

INSERT INTO DIGX_CM_RESOURCE_TASK_REL
  (ID, RESOURCE_NAME, TASK_ID, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
   LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER)
VALUES
  ((SELECT A.ID FROM
      (SELECT NVL(MAX(TO_NUMBER(ID)), 0) + 1 AS ID
         FROM DIGX_CM_RESOURCE_TASK_REL) A),
   'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
   'UAT_N_HUA_DEL', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1);

-- 5. Register the approval assembler for Create, Edit, and Delete re-entry.
INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
     'approval_assembler',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     'N', 'assembler class for HTH User Access submit approval',
     'assembler class for HTH User Access submit approval', 'system', SYSDATE,
     'system', SYSDATE, 'A', 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
     'approval_assembler',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     'N', 'assembler class for HTH User Access edit approval',
     'assembler class for HTH User Access edit approval', 'system', SYSDATE,
     'system', SYSDATE, 'A', 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
     'approval_assembler',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     'N', 'assembler class for HTH User Access delete approval',
     'assembler class for HTH User Access delete approval', 'system', SYSDATE,
     'system', SYSDATE, 'A', 1, 'N', NULL)
SELECT 1 FROM DUAL;

INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
     'ApprovalAssemblers',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     '01', 'system', SYSDATE, 'system', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
     'ApprovalAssemblers',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     '01', 'system', SYSDATE, 'system', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
     'ApprovalAssemblers',
     'com.ofss.digx.cz.bea.app.hosttohost.assembler.SubmitHostToHostUserAccessApprovalAssembler',
     '01', 'system', SYSDATE, 'system', SYSDATE)
SELECT 1 FROM DUAL;

-- 6. Ensure transaction-list conversion handles the three new task codes.
MERGE INTO DIGX_FW_CONFIG_ALL_O T
USING (
  SELECT 'TAB_CHANGE_TASK_CODES' PROP_ID,
         'DayOneConfig' PREFERENCE_NAME,
         'OBDX_BU' DETERMINANT_VALUE
    FROM DUAL
) S
ON (T.PROP_ID = S.PROP_ID
    AND T.PREFERENCE_NAME = S.PREFERENCE_NAME
    AND T.DETERMINANT_VALUE = S.DETERMINANT_VALUE)
WHEN MATCHED THEN UPDATE SET
  T.PROP_VALUE =
    CASE
      WHEN T.PROP_VALUE IS NULL OR TRIM(T.PROP_VALUE) IS NULL
        THEN 'UAT_N_HUA_NEW,UAT_N_HUA_EDT,UAT_N_HUA_DEL'
      ELSE T.PROP_VALUE
        || CASE WHEN INSTR(T.PROP_VALUE, 'UAT_N_HUA_NEW') = 0 THEN ',UAT_N_HUA_NEW' ELSE '' END
        || CASE WHEN INSTR(T.PROP_VALUE, 'UAT_N_HUA_EDT') = 0 THEN ',UAT_N_HUA_EDT' ELSE '' END
        || CASE WHEN INSTR(T.PROP_VALUE, 'UAT_N_HUA_DEL') = 0 THEN ',UAT_N_HUA_DEL' ELSE '' END
    END,
  T.LAST_UPDATED_BY = 'system',
  T.LAST_UPDATED_DATE = SYSDATE
WHEN NOT MATCHED THEN INSERT
  (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
VALUES
  (S.PROP_ID, S.PREFERENCE_NAME,
   'UAT_N_HUA_NEW,UAT_N_HUA_EDT,UAT_N_HUA_DEL', S.DETERMINANT_VALUE,
   'system', SYSDATE, 'system', SYSDATE);

COMMIT;
