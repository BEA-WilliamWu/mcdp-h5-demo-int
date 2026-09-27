-- Run on the DIGX configuration connection. Read-only; execute each SELECT separately.
-- Missing/blank/N ENABLED keeps collection disabled. Installation preserves this flag.
SELECT CASE WHEN COUNT(*)=0 THEN 'MISSING: defaults to disabled'
            WHEN COUNT(*)>1 THEN 'DUPLICATE: inspect configuration'
            WHEN MAX(UPPER(PROP_VALUE))='Y' THEN 'ENABLED in database: confirm application cache'
            ELSE 'DISABLED: no HTH CRM events will be collected' END AS COLLECTION_GATE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='HTHMtbConfiguration' AND PROP_ID='ENABLED' AND DETERMINANT_VALUE='N';
SELECT PROP_ID, PROP_VALUE, DETERMINANT_VALUE FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='HTHMtbConfiguration' ORDER BY PROP_ID;
-- Existing BCO reference configuration, do not update:
SELECT PROP_ID, PROP_VALUE FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='CRMConfiguration'
AND (PROP_ID LIKE '%UAT_N_RA%' OR PROP_ID LIKE '%LAT_N_CA%' OR PROP_ID LIKE '%LAT_N_UA%' OR PROP_ID LIKE '%LAT_N_DA%')
ORDER BY PROP_ID;

-- HTH cross-module factory; no BCO factory keys changed.
SELECT PREFERENCE_NAME, PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME='AdapterFactories' AND PROP_ID='HTH_MTB_ADAPTER_FACTORY';
-- Expected factory value: com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory
-- Database Y alone does not prove the deployed application sees Y; check HTH_CRM logs.
