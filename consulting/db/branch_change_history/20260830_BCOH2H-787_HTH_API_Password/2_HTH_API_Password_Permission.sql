-- BCOH2H-787: base permission setup for HTH API Password Code feature.
-- Follows the pattern of 20260825_HTH_User_Access/2_HTH_User_Access_Permission.sql.
-- Re-runnable: only mappings and resources owned by this feature are replaced.
-- Execute in the OBDX configuration schema after 3_HTH_API_Password_Process.sql.

-- 1. Remove dependent mappings before replacing entitlements and owned resources.
DELETE FROM DIGX_AZ_POLICY_ENT_MAP
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform'
 );

DELETE FROM DIGX_AZ_ENTGROUP_ENT_MAPPING
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform'
 );

DELETE FROM DIGX_AZ_RESOURCE_ACTION
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform'
 );

DELETE FROM DIGX_AZ_ENTITLEMENT
 WHERE ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform'
 );

DELETE FROM DIGX_AZ_RESOURCE
 WHERE ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal'
 );

-- 2. Create backend service resources. Resource IDs must match checkAccessPolicy/@Task names.
INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate',
   'HTH API Password generate', 'HTH API Password generate service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked',
   'HTH API Password masked', 'HTH API Password masked view service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal',
   'HTH API Password reveal', 'HTH API Password reveal service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

-- 3. Create entitlements.
INSERT ALL
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform',
     'HTH API Password generate perform', 'HTH API Password generate perform',
     'Generate or regenerate a one-time HTH API password setup code', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View',
     'HTH API Password masked view', 'HTH API Password masked view',
     'View masked HTH API password code lifecycle status', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform',
     'HTH API Password reveal perform', 'HTH API Password reveal perform',
     'Reveal plaintext HTH API password code (audit logged)', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
SELECT 1 FROM DUAL;

-- 4. Map backend resources to entitlements. PRM = maker/read action.
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthapsvc-gen-2026083001', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthapsvc-mask-2026083001', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthapsvc-reveal-2026083001', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform');

-- 5. Add to the UAT entitlement group.
INSERT ALL
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform')
SELECT 1 FROM DUAL;

-- 6. Grant by copying policy mappings from existing BCO User Account Access entitlements.
-- This keeps HTH authorization aligned with BCO without environment-specific usernames.
INSERT INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
SELECT DISTINCT M.TARGET_ENTITLEMENT_ID, P.POLICY_ID
  FROM (
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.create_Perform'
             AS SOURCE_ENTITLEMENT_ID,
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.generate_Perform'
             AS TARGET_ENTITLEMENT_ID
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.read_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.masked_View'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.read_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.reveal_Perform'
      FROM DUAL
  ) M
  JOIN DIGX_AZ_POLICY_ENT_MAP P
    ON P.ENTITLEMENT_ID = M.SOURCE_ENTITLEMENT_ID
 WHERE NOT EXISTS (
       SELECT 1
         FROM DIGX_AZ_POLICY_ENT_MAP H
        WHERE H.ENTITLEMENT_ID = M.TARGET_ENTITLEMENT_ID
          AND H.POLICY_ID = P.POLICY_ID
 );

COMMIT;

-- Verification:
-- SELECT PEM.ENTITLEMENT_ID, PEM.POLICY_ID
--   FROM DIGX_AZ_POLICY_ENT_MAP PEM
--  WHERE PEM.ENTITLEMENT_ID LIKE
--        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.%';
