-- Run on the DIGX configuration connection. Read-only; execute each SELECT separately.
-- Missing/blank/N ENABLED keeps collection disabled. Installation preserves this flag.
-- SQL 2 renames legacy MTB configuration and factory keys; it does not enable collection.
-- Deploy matching Java + Preferences.xml; database Y alone does not prove runtime loading.
SELECT SYS_CONTEXT('USERENV','DB_NAME') AS DATABASE_NAME,
       SYS_CONTEXT('USERENV','SERVICE_NAME') AS SERVICE_NAME,
       SYS_CONTEXT('USERENV','SESSION_USER') AS LOGIN_USER
FROM DUAL;
SELECT CASE WHEN COUNT(*)=0 THEN 'MISSING: defaults to disabled'
            WHEN COUNT(*)>1 THEN 'DUPLICATE: inspect configuration'
            WHEN MAX(UPPER(PROP_VALUE))='Y' THEN 'ENABLED in database: confirm application cache'
            ELSE 'DISABLED: no HTH CRM events will be collected' END AS COLLECTION_GATE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='HTHCrmConfiguration' AND PROP_ID='ENABLED' AND DETERMINANT_VALUE='N';
SELECT PROP_ID, PROP_VALUE, DETERMINANT_VALUE FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='HTHCrmConfiguration' ORDER BY DETERMINANT_VALUE, PROP_ID;
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: NULL HTH configuration key/determinant' END AS KEY_CHECK
FROM DIGX_FW_CONFIG_ALL_O
WHERE (PREFERENCE_NAME='HTHCrmConfiguration'
    OR (PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride') AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'))
  AND (PROP_ID IS NULL OR DETERMINANT_VALUE IS NULL);
-- Existing BCO reference configuration, do not update:
SELECT PROP_ID, PROP_VALUE FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='CRMConfiguration'
AND (PROP_ID LIKE '%UAT_N_RA%' OR PROP_ID LIKE '%LAT_N_CA%' OR PROP_ID LIKE '%LAT_N_UA%' OR PROP_ID LIKE '%LAT_N_DA%')
ORDER BY PROP_ID;

-- Actual AdapterFactories provider source; this base row is mandatory.
SELECT CASE WHEN COUNT(*)=0 THEN 'FAIL: HTH base factory missing'
            WHEN COUNT(*)>1 THEN 'FAIL: duplicate HTH base factory'
            WHEN MAX(PROP_VALUE)='com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory'
              THEN 'PASS' ELSE 'FAIL: unexpected HTH base factory class' END AS BASE_FACTORY_CHECK
FROM DIGX_FW_CONFIG_ALL_B
WHERE CATEGORY_ID='adapterfactoryconfig' AND PROP_ID='HTH_CRM_ADAPTER_FACTORY';
SELECT CATEGORY_ID, PROP_ID, PROP_VALUE
FROM DIGX_FW_CONFIG_ALL_B
WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
  AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
ORDER BY CATEGORY_ID;
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: unexpected HTH base factory class' END AS ALL_BASE_FACTORY_CHECK
FROM DIGX_FW_CONFIG_ALL_B
WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
  AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
  AND (PROP_VALUE<>'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory' OR PROP_VALUE IS NULL);

-- HTH override rows; a PASS here alone does not replace the required _B base row.
SELECT CASE WHEN COUNT(*)=0 THEN 'FAIL: HTH factory missing'
            WHEN COUNT(*)>1 THEN 'FAIL: duplicate HTH factory'
            WHEN MAX(PROP_VALUE)='com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory'
              THEN 'PASS' ELSE 'FAIL: unexpected HTH factory class' END AS FACTORY_CHECK
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='AdapterFactories' AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
  AND DETERMINANT_VALUE='N';
SELECT PREFERENCE_NAME, PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride') AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
ORDER BY PREFERENCE_NAME, DETERMINANT_VALUE;
-- Must PASS after SQL 2; zero legacy keys should remain in any determinant.
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: legacy HTH configuration remains' END AS RENAME_CHECK
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='HTHMtbConfiguration'
   OR (PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride') AND PROP_ID='HTH_MTB_ADAPTER_FACTORY');
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: legacy HTH base factory remains' END AS BASE_RENAME_CHECK
FROM DIGX_FW_CONFIG_ALL_B
WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
  AND PROP_ID='HTH_MTB_ADAPTER_FACTORY';
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: duplicate HTH configuration keys' END AS DUPLICATE_CHECK
FROM (
  SELECT PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
  FROM DIGX_FW_CONFIG_ALL_O
  WHERE PREFERENCE_NAME='HTHCrmConfiguration'
     OR (PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride') AND PROP_ID='HTH_CRM_ADAPTER_FACTORY')
  GROUP BY PREFERENCE_NAME, PROP_ID, DETERMINANT_VALUE
  HAVING COUNT(*)>1
);
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: duplicate HTH base factory keys' END AS BASE_DUPLICATE_CHECK
FROM (
  SELECT CATEGORY_ID, PROP_ID
  FROM DIGX_FW_CONFIG_ALL_B
  WHERE CATEGORY_ID IN ('adapterfactoryconfig', 'adapterfactoryconfigoverride')
    AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
  GROUP BY CATEGORY_ID, PROP_ID
  HAVING COUNT(*)>1
);
-- Must PASS for every determinant in both factory provider layers, including optional
-- AdapterFactoriesOverride enterprise overrides, not only the default N row.
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL: unexpected HTH factory class' END AS ALL_FACTORY_CHECK
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME IN ('AdapterFactories', 'AdapterFactoriesOverride') AND PROP_ID='HTH_CRM_ADAPTER_FACTORY'
  AND (PROP_VALUE<>'com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory' OR PROP_VALUE IS NULL);
-- Expected factory value: com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory
-- Database Y alone does not prove the deployed application sees Y; check HTH_CRM logs.
