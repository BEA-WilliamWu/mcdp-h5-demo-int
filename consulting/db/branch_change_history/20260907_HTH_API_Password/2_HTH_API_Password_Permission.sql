-- Oracle SQL/PLSQL; no SQL*Plus commands or substitution variables.
-- Execute each complete DECLARE/BEGIN ... END; block as one statement (no slash).
-- BCOH2H-788 / BCOH2H-790: HTH API Password resources and entitlements.
-- Execute in the OBDX configuration schema after 1_HTH_API_Password_Schema.sql.
-- Re-runnable. The feature inherits the policy set of the existing BCO Change Password function;
-- no user-specific or Maker/Checker grants are required.


-- Fail before deleting any existing target rows if this environment uses a different BCO
-- Change Password resource. Copying zero policies/groups would make the feature inaccessible.
DECLARE
  V_POLICY_COUNT NUMBER;
  V_GROUP_COUNT  NUMBER;
BEGIN
  SAVEPOINT HTH_API_PASSWORD_CONFIG;
  SELECT COUNT(DISTINCT PEM.POLICY_ID)
    INTO V_POLICY_COUNT
    FROM DIGX_AZ_POLICY_ENT_MAP PEM
    JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.ENTITLEMENT_ID = PEM.ENTITLEMENT_ID
   WHERE RA.RESOURCE_ID IN (
     'change-password',
     'com.ofss.digx.app.user.service.User.changeCredentials'
   );

  SELECT COUNT(DISTINCT EGM.ENT_GROUP_ID)
    INTO V_GROUP_COUNT
    FROM DIGX_AZ_ENTGROUP_ENT_MAPPING EGM
    JOIN DIGX_AZ_RESOURCE_ACTION RA ON RA.ENTITLEMENT_ID = EGM.ENTITLEMENT_ID
   WHERE RA.RESOURCE_ID IN (
     'change-password',
     'com.ofss.digx.app.user.service.User.changeCredentials'
   );

  IF V_POLICY_COUNT = 0 OR V_GROUP_COUNT = 0 THEN
    RAISE_APPLICATION_ERROR(-20002,
      'BCO Change Password policy/group source was not found; no HTH grants were changed.');
  END IF;


DELETE FROM DIGX_AZ_POLICY_ENT_MAP
 WHERE ENTITLEMENT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');

DELETE FROM DIGX_AZ_ENTGROUP_ENT_MAPPING
 WHERE ENTITLEMENT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');

DELETE FROM DIGX_AZ_RESOURCE_ACTION
 WHERE ENTITLEMENT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');

DELETE FROM DIGX_AZ_ENTITLEMENT
 WHERE ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');

DELETE FROM DIGX_AZ_RESOURCE
 WHERE ID IN (
  'hth-api-password',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset'
 );

INSERT ALL
  INTO DIGX_AZ_RESOURCE
    (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
     LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
  VALUES
    ('hth-api-password', 'hth-api-passwordDisplayName',
     'HTH API Password setup and reset page', 'UCN', 'PRM', NULL,
     'system', SYSDATE, 'system', 'A', SYSDATE, 1)
  INTO DIGX_AZ_RESOURCE
    (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
     LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status',
     'HTH API Password status', 'Read HTH API Password state and public policy',
     'SVC', 'PRM', NULL, 'system', SYSDATE, 'system', 'A', SYSDATE, 1)
  INTO DIGX_AZ_RESOURCE
    (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
     LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
     'Set up HTH API Password', 'Create the current HTH API user credential in UAM',
     'SVC', 'PRM', NULL, 'system', SYSDATE, 'system', 'A', SYSDATE, 1)
  INTO DIGX_AZ_RESOURCE
    (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
     CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
     LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset',
     'Reset HTH API Password', 'Atomically replace the current HTH API user credential in UAM',
     'SVC', 'PRM', NULL, 'system', SYSDATE, 'system', 'A', SYSDATE, 1)
SELECT 1 FROM DUAL;

INSERT ALL
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'View HTH API Password Status',
     'View HTH API Password Status', 'View current-user HTH API Password status',
     'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'Set Up HTH API Password',
     'Set Up HTH API Password', 'Set up the current HTH API user password',
     'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform', 'Reset HTH API Password',
     'Reset HTH API Password', 'Reset the current HTH API user password',
     'system', SYSDATE, 'system', SYSDATE, 'A', 1)
SELECT 1 FROM DUAL;

INSERT ALL
  INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
  VALUES ('hthapipwdui-2026090701', 'VIW', 'hth-api-password', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View')
  INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
  VALUES ('hthapipwdstatus-2026090701', 'PRM',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View')
  INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
  VALUES ('hthapipwdsetup-2026090701', 'PRM',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform')
  INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
  VALUES ('hthapipwdreset-2026090701', 'PRM',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset',
          'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform')
SELECT 1 FROM DUAL;

-- Copy entitlement-group placement from the existing BCO Change Password entitlement(s).
INSERT INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
SELECT DISTINCT SOURCE_GROUP.ENT_GROUP_ID, TARGET_ENT.ENTITLEMENT_ID
  FROM (
    SELECT EGM.ENT_GROUP_ID
      FROM DIGX_AZ_ENTGROUP_ENT_MAPPING EGM
      JOIN DIGX_AZ_RESOURCE_ACTION RA
        ON RA.ENTITLEMENT_ID = EGM.ENTITLEMENT_ID
     WHERE RA.RESOURCE_ID IN (
       'change-password',
       'com.ofss.digx.app.user.service.User.changeCredentials'
     )
  ) SOURCE_GROUP
  CROSS JOIN (
    SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View' ENTITLEMENT_ID FROM DUAL
    UNION ALL SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform' FROM DUAL
    UNION ALL SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform' FROM DUAL
  ) TARGET_ENT;

-- Copy the BCO Change Password policy set. This keeps HTH access aligned when application-role
-- memberships or policy IDs differ between environments.
INSERT INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
SELECT DISTINCT TARGET_ENT.ENTITLEMENT_ID, SOURCE_POLICY.POLICY_ID
  FROM (
    SELECT PEM.POLICY_ID
      FROM DIGX_AZ_POLICY_ENT_MAP PEM
      JOIN DIGX_AZ_RESOURCE_ACTION RA
        ON RA.ENTITLEMENT_ID = PEM.ENTITLEMENT_ID
     WHERE RA.RESOURCE_ID IN (
       'change-password',
       'com.ofss.digx.app.user.service.User.changeCredentials'
     )
  ) SOURCE_POLICY
  CROSS JOIN (
    SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View' ENTITLEMENT_ID FROM DUAL
    UNION ALL SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform' FROM DUAL
    UNION ALL SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform' FROM DUAL
  ) TARGET_ENT;

COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO HTH_API_PASSWORD_CONFIG;
    RAISE;
END;

-- Mandatory pre-deployment validation: both counts must be greater than zero.
-- If either is zero, do not release; confirm the actual BCO Change Password resource ID first.
SELECT COUNT(*) AS HTH_API_PASSWORD_POLICY_COUNT
  FROM DIGX_AZ_POLICY_ENT_MAP
 WHERE ENTITLEMENT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');

SELECT COUNT(*) AS HTH_API_PASSWORD_GROUP_COUNT
  FROM DIGX_AZ_ENTGROUP_ENT_MAPPING
 WHERE ENTITLEMENT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.status_View', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup_Perform', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reset_Perform');
