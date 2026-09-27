-- BCOH2H-849: run with the DIGX configuration schema connection.
-- This is the account used for prior stories' DIGX_FW_CONFIG_ALL_O configuration SQL.
-- Select the entire DECLARE ... END; block and execute as ONE statement (no slash).
-- No HTH table access or cross-schema DDL privileges required.
-- Re-runnable: preserve ENABLED and activity mappings; update only the HTH factory value.
-- First install defaults ENABLED=N. Use a dedicated session: successful execution COMMITs.
DECLARE
  v_stage VARCHAR2(40) := 'START';
  v_config_started BOOLEAN := FALSE;
  v_duplicates NUMBER;
BEGIN
  v_stage := 'CONFIG_VALIDATE';
  SAVEPOINT HTH_849_CONFIG;
  v_config_started := TRUE;
  SELECT COUNT(*) INTO v_duplicates FROM (
    SELECT PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
    FROM DIGX_FW_CONFIG_ALL_O
    WHERE DETERMINANT_VALUE='N'
      AND ((PREFERENCE_NAME='AdapterFactories' AND PROP_ID='HTH_MTB_ADAPTER_FACTORY')
        OR (PREFERENCE_NAME='HTHMtbConfiguration' AND PROP_ID='ENABLED'))
    GROUP BY PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
    HAVING COUNT(*) > 1
  );
  IF v_duplicates > 0 THEN
    RAISE_APPLICATION_ERROR(-20849, '849 duplicate HTH configuration keys; inspect before rerun');
  END IF;
  v_stage := 'CONFIG_REGISTER';
-- Cross-module Adapter; only this HTH factory key is inserted/updated.
MERGE INTO DIGX_FW_CONFIG_ALL_O t
USING (SELECT 'AdapterFactories' preference_name, 'HTH_MTB_ADAPTER_FACTORY' prop_id,
 'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory' prop_value, 'N' determinant_value FROM dual) s
ON (t.PREFERENCE_NAME=s.preference_name AND t.PROP_ID=s.prop_id AND t.DETERMINANT_VALUE=s.determinant_value)
WHEN MATCHED THEN UPDATE SET t.PROP_VALUE=s.prop_value, t.LAST_UPDATED_BY='ofssuser', t.LAST_UPDATED_DATE=SYSDATE
 WHERE t.PROP_VALUE<>s.prop_value OR t.PROP_VALUE IS NULL
WHEN NOT MATCHED THEN INSERT (PREFERENCE_NAME,PROP_ID,PROP_VALUE,DETERMINANT_VALUE,CREATED_BY,CREATION_DATE,LAST_UPDATED_BY,LAST_UPDATED_DATE)
VALUES (s.preference_name,s.prop_id,s.prop_value,s.determinant_value,'ofssuser',SYSDATE,'ofssuser',SYSDATE);
-- Preserve existing enabled flag and all operator mappings on re-run. No invented external codes.
MERGE INTO DIGX_FW_CONFIG_ALL_O t
USING (SELECT 'HTHMtbConfiguration' preference_name, 'ENABLED' prop_id, 'N' prop_value, 'N' determinant_value FROM dual) s
ON (t.PREFERENCE_NAME=s.preference_name AND t.PROP_ID=s.prop_id AND t.DETERMINANT_VALUE=s.determinant_value)
WHEN NOT MATCHED THEN INSERT (PREFERENCE_NAME,PROP_ID,PROP_VALUE,DETERMINANT_VALUE,CREATED_BY,CREATION_DATE,LAST_UPDATED_BY,LAST_UPDATED_DATE)
VALUES (s.preference_name,s.prop_id,s.prop_value,s.determinant_value,'ofssuser',SYSDATE,'ofssuser',SYSDATE);
COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    IF v_config_started THEN
      ROLLBACK TO HTH_849_CONFIG;
    END IF;
    RAISE_APPLICATION_ERROR(-20849,
      '849 stage=' || v_stage || ': ' || SUBSTR(SQLERRM, 1, 400), TRUE);
END;
-- Enable explicitly only after runtime transaction and permission tests:
-- UPDATE DIGX_FW_CONFIG_ALL_O SET PROP_VALUE='Y' WHERE PREFERENCE_NAME='HTHMtbConfiguration' AND PROP_ID='ENABLED' AND DETERMINANT_VALUE='N';
