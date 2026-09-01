-- BCOH2H-538 / BCOH2H-595: base permission setup for HTH User Accounts & Services Access.
-- Grants are derived from the equivalent BCO User/Linked User Account Access policies below;
-- environment-specific maker/checker grant scripts are optional overrides only.
-- Re-runnable: only mappings and resources owned by this feature are replaced.
-- Existing shared User Account Access UI resources are reused and are not deleted.
-- Execute in the OBDX configuration schema, or through deployment synonyms with direct DML grants.
-- All changes commit once at the end. If execution fails before COMMIT, roll back the transaction.
-- To back out after commit, execute section 1 only in the documented dependency order, then remove
-- the feature-owned UI/SVC resources; do not delete the shared User Account Access UI resources.

-- 1. Remove dependent mappings before replacing entitlements and owned resources.
DELETE FROM DIGX_AZ_POLICY_ENT_MAP
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
 );

DELETE FROM DIGX_AZ_ENTGROUP_ENT_MAPPING
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
 );

DELETE FROM DIGX_AZ_RESOURCE_ACTION
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
 );

DELETE FROM DIGX_AZ_ENTITLEMENT
 WHERE ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
 );

DELETE FROM DIGX_AZ_RESOURCE
 WHERE ID IN (
  'hth-account-linkage',
  'hth-api-service-mapping',
  'review-hth-user-access',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete'
 );

-- 2. Create the three UI resources owned by the HTH extension.
INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
   CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
   LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
VALUES
  ('hth-account-linkage', 'hth-account-linkageDisplayName',
   'hth-account-linkageDescription', 'UCN', 'PRM', NULL,
   'system', SYSDATE, 'system', 'A', SYSDATE, 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
   CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
   LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
VALUES
  ('hth-api-service-mapping', 'hth-api-service-mappingDisplayName',
   'hth-api-service-mappingDescription', 'UCN', 'PRM', NULL,
   'system', SYSDATE, 'system', 'A', SYSDATE, 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, IS_DEFAULT,
   CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, ENTITY_STATUS,
   LAST_UPDATE_DATE, OBJECT_VERSION_NUMBER)
VALUES
  ('review-hth-user-access', 'review-hth-user-accessDisplayName',
   'review-hth-user-accessDescription', 'UCN', 'PRM', NULL,
   'system', SYSDATE, 'system', 'A', SYSDATE, 1);

-- 3. Create backend service resources. Resource IDs must match checkAccessPolicy/@Task names.
INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search',
   'HTH User Access search', 'HTH User Access search service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts',
   'HTH User Access accounts', 'HTH User Access eligible accounts service',
   'SVC', 'PRM', 'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
   'HTH User Access submit', 'HTH User Access submit service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
   'HTH User Access edit', 'HTH User Access edit service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

INSERT INTO DIGX_AZ_RESOURCE
  (ID, DISPLAY_NAME, DESCRIPTION, RESOURCE_TYPE, ACTION_TYPE, CREATED_BY,
   CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS,
   OBJECT_VERSION_NUMBER)
VALUES
  ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
   'HTH User Access delete', 'HTH User Access delete service', 'SVC', 'PRM',
   'system', SYSDATE, 'system', SYSDATE, 'A', 1);

-- 4. Create read, perform, and approve entitlements.
INSERT ALL
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
     'HTH User Access search view', 'HTH User Access search view',
     'View HTH user access summary', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
     'HTH User Access accounts view', 'HTH User Access accounts view',
     'View eligible HTH accounts and APIs', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
     'HTH User Access submit perform', 'HTH User Access submit perform',
     'Submit new HTH user access', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve',
     'HTH User Access submit approve', 'HTH User Access submit approve',
     'Approve new HTH user access', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
     'HTH User Access edit perform', 'HTH User Access edit perform',
     'Submit an HTH user access amendment', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve',
     'HTH User Access edit approve', 'HTH User Access edit approve',
     'Approve an HTH user access amendment', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform',
     'HTH User Access delete perform', 'HTH User Access delete perform',
     'Submit HTH user access deletion', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
  INTO DIGX_AZ_ENTITLEMENT
    (ID, NAME, DISPLAY_NAME, DESCRIPTION, CREATED_BY, CREATION_DATE,
     LAST_UPDATED_BY, LAST_UPDATE_DATE, ENTITY_STATUS, OBJECT_VERSION_NUMBER)
  VALUES
    ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve',
     'HTH User Access delete approve', 'HTH User Access delete approve',
     'Approve HTH user access deletion', 'system', SYSDATE, 'system', SYSDATE, 'A', 1)
SELECT 1 FROM DUAL;

-- 5. Map shared and feature-owned UI resources to HTH view permission.
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-base-2026082501', 'VIW', 'user-access-management-base',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-valid-2026082501', 'VIW', 'validation',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-list-2026082501', 'VIW', 'user-list-details',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-summary-2026082501', 'VIW', 'summary',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-map-2026082501', 'VIW', 'mapping-modules',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-link-2026082501', 'VIW', 'hth-account-linkage',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-api-2026082501', 'VIW', 'hth-api-service-mapping',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuaui-review-2026082501', 'VIW', 'review-hth-user-access',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');

-- 6. Map backend resources. PRM is the maker/read action and APR is checker approval.
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-search-2026082501', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-accts-2026082501', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-submit-2026082501', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-submit-apr-20260825', 'APR',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-edit-2026082501', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-edit-apr-2026082501', 'APR',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-delete-2026082501', 'PRM',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform');
INSERT INTO DIGX_AZ_RESOURCE_ACTION (ID, ACTION_TYPE, RESOURCE_ID, ENTITLEMENT_ID)
VALUES ('hthuasvc-delete-apr-20260825', 'APR',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete',
        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve');

-- 7. Keep this feature in the existing User Account Access entitlement group.
INSERT ALL
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform')
  INTO DIGX_AZ_ENTGROUP_ENT_MAPPING (ENT_GROUP_ID, ENTITLEMENT_ID)
  VALUES ('UAT', 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve')
SELECT 1 FROM DUAL;

-- 8. Grant HTH User Access to the same policies as the corresponding BCO User Account Access
-- operations. This keeps HTH authorization aligned with BCO without environment-specific
-- usernames or application-role IDs. Section 1 removes the old HTH policy mappings, so this
-- step is required on every re-run of the permission script.
INSERT INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
SELECT DISTINCT M.TARGET_ENTITLEMENT_ID, P.POLICY_ID
  FROM (
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.read_Perform'
             AS SOURCE_ENTITLEMENT_ID,
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View'
             AS TARGET_ENTITLEMENT_ID
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.read_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.create_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.create_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.delete_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.delete_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.read_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.read_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.create_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.create_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.update_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.update_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.delete_Perform',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform'
      FROM DUAL
    UNION ALL
    SELECT 'com.ofss.digx.app.access.service.account.linkedParty.user.LinkedUserAccountAccess.delete_Approve',
           'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve'
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
