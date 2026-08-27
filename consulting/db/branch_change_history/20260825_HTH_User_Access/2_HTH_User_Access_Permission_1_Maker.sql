-- BCOH2H-538 / BCOH2H-595: maker grants.
-- Replace <BM_MAKER_USERNAME> before execution.
-- Run 2_HTH_User_Access_Permission.sql first.
-- Execute in the OBDX configuration schema. The script replaces grants for this feature only and
-- commits at the end. Backout is the DELETE statement below without the following INSERT ALL.

DELETE FROM DIGX_AZ_POLICY_ENT_MAP
 WHERE ENTITLEMENT_ID IN (
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform',
  'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform'
 )
   AND POLICY_ID IN (
    SELECT PAPM.POLICY_ID
      FROM DIGX_UM_USER_PRINCIPAL UUP
      JOIN DIGX_AZ_POLICY_APP_MAP PAPM
        ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
     WHERE UPPER(UUP.USERNAME) = UPPER('<BM_MAKER_USERNAME>')
   );

INSERT ALL
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.search_View', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.accounts_View', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit_Perform', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit_Perform', POLICY_ID)
  INTO DIGX_AZ_POLICY_ENT_MAP (ENTITLEMENT_ID, POLICY_ID)
  VALUES ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.delete_Perform', POLICY_ID)
SELECT DISTINCT PAPM.POLICY_ID AS POLICY_ID
  FROM DIGX_UM_USER_PRINCIPAL UUP
  JOIN DIGX_AZ_POLICY_APP_MAP PAPM
    ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
 WHERE UPPER(UUP.USERNAME) = UPPER('<BM_MAKER_USERNAME>');

COMMIT;

-- Verification:
-- SELECT UUP.USERNAME, PAPM.POLICY_ID, PEM.ENTITLEMENT_ID
--   FROM DIGX_UM_USER_PRINCIPAL UUP
--   JOIN DIGX_AZ_POLICY_APP_MAP PAPM ON PAPM.APPLICATION_ROLE_ID = UUP.PRINCIPAL
--   JOIN DIGX_AZ_POLICY_ENT_MAP PEM ON PEM.POLICY_ID = PAPM.POLICY_ID
--  WHERE UPPER(UUP.USERNAME) = UPPER('<BM_MAKER_USERNAME>')
--    AND PEM.ENTITLEMENT_ID LIKE
--        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.%';
