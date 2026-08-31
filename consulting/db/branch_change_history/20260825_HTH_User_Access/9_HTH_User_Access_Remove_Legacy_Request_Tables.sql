-- BCOH2H-538 / BCOH2H-595: remove unused HTH User Access request snapshot storage.
--
-- The platform DIGX_AP_TRANSACTION.transactionSnapshot is the sole maker/checker request store.
-- These three legacy tables were never part of the final runtime path. This migration removes them
-- from environments that executed an earlier version of 1_HTH_User_Access_Schema.sql.
--
-- Execute with direct DROP privileges on HTH_BEA objects and DELETE privileges on the OBDX
-- configuration tables. Oracle DDL commits implicitly. The child-to-parent drop order is required.
-- Re-running this script is safe.

DELETE FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'RepositoryAdapterFactories'
   AND PROP_ID IN (
    'HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER'
   );

DELETE FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'repositoryadapterconfig'
   AND PROP_ID IN (
    'HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER'
   );

COMMIT;

DECLARE
  PROCEDURE DROP_TABLE_IF_PRESENT(P_TABLE_NAME IN VARCHAR2) IS
    L_COUNT NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO L_COUNT
      FROM ALL_TABLES
     WHERE OWNER = 'HTH_BEA'
       AND TABLE_NAME = P_TABLE_NAME;

    IF L_COUNT > 0 THEN
      EXECUTE IMMEDIATE 'DROP TABLE HTH_BEA.' || P_TABLE_NAME
        || ' CASCADE CONSTRAINTS PURGE';
    END IF;
  END DROP_TABLE_IF_PRESENT;
BEGIN
  DROP_TABLE_IF_PRESENT('HTH_USER_ACCESS_REQ_API');
  DROP_TABLE_IF_PRESENT('HTH_USER_ACCESS_REQ_ACCOUNT');
  DROP_TABLE_IF_PRESENT('HTH_USER_ACCESS_REQUEST');
END;
/
