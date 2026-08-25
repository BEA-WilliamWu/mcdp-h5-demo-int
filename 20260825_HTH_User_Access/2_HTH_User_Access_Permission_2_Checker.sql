-- BCOH2H-538 / BCOH2H-595: checker grants.
-- Replace <BM_APPROVER_USERNAME> before execution.
-- Run 2_HTH_User_Access_Permission.sql first.
-- Perform is intentionally granted with Approve because approval re-entry can execute
-- the original service access check.

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
 )
   AND POLICY_ID IN (
    SELECT PAPM.POLICY_ID
      FROM DIGX_UM_USER_PRINCIPAL UUP
      JOIN DIGX_AZ_POLICY_APP_MAP PAPM
        ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
     WHERE UPPER(UUP.USERNAME) = UPPER('<BM_APPROVER_USERNAME>')
   );

INSERT ALL
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Approve', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Approve', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Approve', POLICY_ID)
SELECT DISTINCT PAPM.POLICY_ID AS POLICY_ID
  FROM DIGX_UM_USER_PRINCIPAL UUP
  JOIN DIGX_AZ_POLICY_APP_MAP PAPM
    ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
 WHERE UPPER(UUP.USERNAME) = UPPER('<BM_APPROVER_USERNAME>');

COMMIT;

-- Verification:
-- SELECT UUP.USERNAME, PAPM.POLICY_ID, PEM.ENTITLEMENT_ID
--   FROM DIGX_UM_USER_PRINCIPAL UUP
--   JOIN DIGX_AZ_POLICY_APP_MAP PAPM ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
--   JOIN DIGX_AZ_POLICY_ENT_MAP PEM ON PEM.POLICY_ID = PAPM.POLICY_ID
--  WHERE UPPER(UUP.USERNAME) = UPPER('<BM_APPROVER_USERNAME>')
--    AND PEM.ENTITLEMENT_ID LIKE
--        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%';
