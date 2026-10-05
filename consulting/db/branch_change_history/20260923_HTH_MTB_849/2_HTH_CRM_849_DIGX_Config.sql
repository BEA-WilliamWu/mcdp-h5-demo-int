-- BCOH2H-849: run with the DIGX configuration schema connection.
-- This is the account used for prior stories' DIGX_FW_CONFIG_ALL_O configuration SQL.
-- Select the entire DECLARE ... END; block and execute as ONE statement (no slash).
-- No HTH table access or cross-schema DDL privileges required.
-- Migrates HTHMtbConfiguration / HTHCrmConfiguration -> HTHCRMConfiguration
-- and HTH_MTB_ADAPTER_FACTORY
-- -> HTH_CRM_ADAPTER_FACTORY, for every determinant. Preserve ENABLED and all mappings.
-- Deploy matching HTH Java + Preferences.xml and restart all application instances.
-- Identical legacy/new keys coalesce; conflicting values or duplicate keys stop safely.
-- Existing factory values are normalized to the HTH CRM class after conflict validation.
-- Register the factory in _B (adapterfactoryconfig), the source read by AdapterFactories.
-- Also migrate only its HTH key in _B adapterfactoryconfigoverride and existing _O
-- AdapterFactories / AdapterFactoriesOverride rows. Do not create optional override rows.
-- First install defaults ENABLED=N. Use a dedicated session: successful execution COMMITs.
DECLARE
  v_stage VARCHAR2(40) := 'START';
  v_config_started BOOLEAN := FALSE;
  v_duplicates NUMBER;
  v_conflicts NUMBER;
  v_invalid_keys NUMBER;
BEGIN
  v_stage := 'CONFIG_VALIDATE';
  SAVEPOINT HTH_849_CONFIG;
  v_config_started := TRUE;
  SELECT COUNT(*) INTO v_invalid_keys
  FROM DIGX_FW_CONFIG_ALL_O
  WHERE (PREFERENCE_NAME IN ('HTHMtbConfiguration', 'HTHCrmConfiguration', 'HTHCRMConfiguration')
      OR (PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
          AND PROP_ID IN ('HTH_MTB_ADAPTER_FACTORY', 'HTH_CRM_ADAPTER_FACTORY')))
    AND (PROP_ID IS NULL OR DETERMINANT_VALUE IS NULL);
  IF v_invalid_keys > 0 THEN
    RAISE_APPLICATION_ERROR(-20849, '849 NULL HTH configuration key/determinant; inspect before rerun');
  END IF;
  SELECT COUNT(*) INTO v_duplicates FROM (
    SELECT PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
    FROM DIGX_FW_CONFIG_ALL_O
    WHERE PREFERENCE_NAME IN ('HTHMtbConfiguration', 'HTHCrmConfiguration', 'HTHCRMConfiguration')
       OR (PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
           AND PROP_ID IN ('HTH_MTB_ADAPTER_FACTORY', 'HTH_CRM_ADAPTER_FACTORY'))
    GROUP BY PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
    HAVING COUNT(*) > 1
  );
  IF v_duplicates > 0 THEN
    RAISE_APPLICATION_ERROR(-20849, '849 duplicate HTH configuration keys; inspect before rerun');
  END IF;
  -- Validate all three group pairs before any DML, including both legacy groups
  -- when the canonical group is absent. DECODE treats two NULL values as equal.
  SELECT COUNT(*) INTO v_conflicts
  FROM DIGX_FW_CONFIG_ALL_O legacy
  JOIN DIGX_FW_CONFIG_ALL_O current_config
    ON DECODE(legacy.DETERMINANT_VALUE, current_config.DETERMINANT_VALUE, 1, 0)=1
   AND ((((legacy.PREFERENCE_NAME='HTHMtbConfiguration'
           AND current_config.PREFERENCE_NAME IN ('HTHCrmConfiguration', 'HTHCRMConfiguration'))
          OR (legacy.PREFERENCE_NAME='HTHCrmConfiguration'
              AND current_config.PREFERENCE_NAME='HTHCRMConfiguration'))
         AND DECODE(legacy.PROP_ID, current_config.PROP_ID, 1, 0)=1)
     OR (legacy.PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
         AND legacy.PROP_ID='HTH_MTB_ADAPTER_FACTORY'
         AND current_config.PREFERENCE_NAME=legacy.PREFERENCE_NAME
         AND current_config.PROP_ID='HTH_CRM_ADAPTER_FACTORY'))
  WHERE DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=0;
  IF v_conflicts > 0 THEN
    RAISE_APPLICATION_ERROR(-20849,
      '849 legacy/new HTH configuration values conflict; compare old/new keys before rerun');
  END IF;

  -- Validate the actual AdapterFactories provider source before changing either table.
  SELECT COUNT(*) INTO v_duplicates FROM (
    SELECT CATEGORY_ID, PROP_ID
    FROM DIGX_FW_CONFIG_ALL_B
    WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
      AND PROP_ID IN ('HTH_MTB_ADAPTER_FACTORY', 'HTH_CRM_ADAPTER_FACTORY')
    GROUP BY CATEGORY_ID, PROP_ID
    HAVING COUNT(*)>1
  );
  IF v_duplicates > 0 THEN
    RAISE_APPLICATION_ERROR(-20849, '849 duplicate HTH base factory keys; inspect before rerun');
  END IF;
  SELECT COUNT(*) INTO v_conflicts
  FROM DIGX_FW_CONFIG_ALL_B legacy
  JOIN DIGX_FW_CONFIG_ALL_B current_config
    ON legacy.CATEGORY_ID=current_config.CATEGORY_ID
   AND legacy.PROP_ID='HTH_MTB_ADAPTER_FACTORY'
   AND current_config.PROP_ID='HTH_CRM_ADAPTER_FACTORY'
  WHERE legacy.CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
    AND DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=0;
  IF v_conflicts > 0 THEN
    RAISE_APPLICATION_ERROR(-20849,
      '849 legacy/new HTH base factory values conflict; compare old/new keys before rerun');
  END IF;

  v_stage := 'CONFIG_RENAME';
  -- Coalesce in priority order: HTHCRMConfiguration, HTHCrmConfiguration, then
  -- HTHMtbConfiguration. Delete matching legacy rows before renaming, so the
  -- unique key remains valid. Preserve CREATED_BY/CREATION_DATE and all values.
  DELETE FROM DIGX_FW_CONFIG_ALL_O legacy
  WHERE legacy.PREFERENCE_NAME='HTHCrmConfiguration'
    AND EXISTS (
      SELECT 1 FROM DIGX_FW_CONFIG_ALL_O current_config
      WHERE current_config.PREFERENCE_NAME='HTHCRMConfiguration'
        AND DECODE(legacy.PROP_ID, current_config.PROP_ID, 1, 0)=1
        AND DECODE(legacy.DETERMINANT_VALUE, current_config.DETERMINANT_VALUE, 1, 0)=1
        AND DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=1
    );
  UPDATE DIGX_FW_CONFIG_ALL_O
  SET PREFERENCE_NAME='HTHCRMConfiguration', LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE PREFERENCE_NAME='HTHCrmConfiguration';

  DELETE FROM DIGX_FW_CONFIG_ALL_O legacy
  WHERE legacy.PREFERENCE_NAME='HTHMtbConfiguration'
    AND EXISTS (
      SELECT 1 FROM DIGX_FW_CONFIG_ALL_O current_config
      WHERE current_config.PREFERENCE_NAME='HTHCRMConfiguration'
        AND DECODE(legacy.PROP_ID, current_config.PROP_ID, 1, 0)=1
        AND DECODE(legacy.DETERMINANT_VALUE, current_config.DETERMINANT_VALUE, 1, 0)=1
        AND DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=1
    );
  UPDATE DIGX_FW_CONFIG_ALL_O
  SET PREFERENCE_NAME='HTHCRMConfiguration', LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE PREFERENCE_NAME='HTHMtbConfiguration';

  DELETE FROM DIGX_FW_CONFIG_ALL_O legacy
  WHERE legacy.PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
    AND legacy.PROP_ID='HTH_MTB_ADAPTER_FACTORY'
    AND EXISTS (
      SELECT 1 FROM DIGX_FW_CONFIG_ALL_O current_config
      WHERE current_config.PREFERENCE_NAME=legacy.PREFERENCE_NAME
        AND current_config.PROP_ID='HTH_CRM_ADAPTER_FACTORY'
        AND DECODE(legacy.DETERMINANT_VALUE, current_config.DETERMINANT_VALUE, 1, 0)=1
        AND DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=1
    );
  UPDATE DIGX_FW_CONFIG_ALL_O
  SET PROP_ID='HTH_CRM_ADAPTER_FACTORY', LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
    AND PROP_ID='HTH_MTB_ADAPTER_FACTORY';
  -- The factory implementation is deployment-owned, unlike operator-owned gate/mappings.
  UPDATE DIGX_FW_CONFIG_ALL_O
  SET PROP_VALUE='com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory',
      LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride')
    AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
    AND (PROP_VALUE<>'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory'
         OR PROP_VALUE IS NULL);

  v_stage := 'BASE_FACTORY_RENAME';
  DELETE FROM DIGX_FW_CONFIG_ALL_B legacy
  WHERE legacy.CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
    AND legacy.PROP_ID='HTH_MTB_ADAPTER_FACTORY'
    AND EXISTS (
      SELECT 1 FROM DIGX_FW_CONFIG_ALL_B current_config
      WHERE current_config.CATEGORY_ID=legacy.CATEGORY_ID
        AND current_config.PROP_ID='HTH_CRM_ADAPTER_FACTORY'
        AND DECODE(legacy.PROP_VALUE, current_config.PROP_VALUE, 1, 0)=1
    );
  UPDATE DIGX_FW_CONFIG_ALL_B
  SET PROP_ID='HTH_CRM_ADAPTER_FACTORY', LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
    AND PROP_ID='HTH_MTB_ADAPTER_FACTORY';
  UPDATE DIGX_FW_CONFIG_ALL_B
  SET PROP_VALUE='com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory',
      LAST_UPDATED_BY='ofssuser', LAST_UPDATED_DATE=SYSDATE
  WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
    AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
    AND (PROP_VALUE<>'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory'
         OR PROP_VALUE IS NULL);

  v_stage := 'BASE_FACTORY_REGISTER';
  MERGE INTO DIGX_FW_CONFIG_ALL_B t
  USING (SELECT 'adapterfactoryconfig' category_id, 'HTH_CRM_ADAPTER_FACTORY' prop_id FROM dual) s
  ON (t.CATEGORY_ID=s.category_id AND t.PROP_ID=s.prop_id)
  WHEN NOT MATCHED THEN INSERT
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE,
     OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE, CATEGORY_DESCRIPTION)
  VALUES
    (s.prop_id, s.category_id, 'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory',
     'N', 'HTH-only CRM collection adapter factory for BCOH2H-849.',
     'HTH CRM adapter factory', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE, NULL, 1, 'N', NULL);

  v_stage := 'CONFIG_REGISTER';
-- Cross-module Adapter; only this HTH factory key is inserted/updated.
MERGE INTO DIGX_FW_CONFIG_ALL_O t
USING (SELECT 'AdapterFactories' preference_name, 'HTH_CRM_ADAPTER_FACTORY' prop_id,
 'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory' prop_value, 'N' determinant_value FROM dual) s
ON (t.PREFERENCE_NAME=s.preference_name AND t.PROP_ID=s.prop_id AND t.DETERMINANT_VALUE=s.determinant_value)
WHEN MATCHED THEN UPDATE SET t.PROP_VALUE=s.prop_value, t.LAST_UPDATED_BY='ofssuser', t.LAST_UPDATED_DATE=SYSDATE
 WHERE t.PROP_VALUE<>s.prop_value OR t.PROP_VALUE IS NULL
WHEN NOT MATCHED THEN INSERT (PREFERENCE_NAME,PROP_ID,PROP_VALUE,DETERMINANT_VALUE,CREATED_BY,CREATION_DATE,LAST_UPDATED_BY,LAST_UPDATED_DATE)
VALUES (s.preference_name,s.prop_id,s.prop_value,s.determinant_value,'ofssuser',SYSDATE,'ofssuser',SYSDATE);
-- Preserve existing enabled flag and all operator mappings on re-run. No invented external codes.
MERGE INTO DIGX_FW_CONFIG_ALL_O t
USING (SELECT 'HTHCRMConfiguration' preference_name, 'ENABLED' prop_id, 'N' prop_value, 'N' determinant_value FROM dual) s
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
-- UPDATE DIGX_FW_CONFIG_ALL_O SET PROP_VALUE='Y' WHERE PREFERENCE_NAME='HTHCRMConfiguration' AND PROP_ID='ENABLED' AND DETERMINANT_VALUE='N';
