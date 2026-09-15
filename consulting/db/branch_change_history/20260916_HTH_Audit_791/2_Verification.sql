-- Each HTH write/reveal resource must have exactly one mapping and audit=Y.
SELECT T.ID, T.NAME, A.ASPECT, A.ENABLED, R.RESOURCE_NAME
  FROM DIGX_CM_TASK T
  LEFT JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID=T.ID AND A.ASPECT='audit'
  LEFT JOIN DIGX_CM_RESOURCE_TASK_REL R ON R.TASK_ID=T.ID
 WHERE T.ID IN ('UAT_N_HAP_GEN','UAT_N_HAP_RVL','CM_N_HAP_SETUP','CM_N_HAP_RESET',
                'UAT_N_HUA_NEW','UAT_N_HUA_EDT','UAT_N_HUA_DEL')
 ORDER BY T.ID;

-- Retain the existing BCO user maintenance task and approval mappings.
SELECT R.RESOURCE_NAME,R.TASK_ID,A.ENABLED
  FROM DIGX_CM_RESOURCE_TASK_REL R
  LEFT JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID=R.TASK_ID AND A.ASPECT='audit'
 WHERE R.RESOURCE_NAME IN (
   'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.create',
   'com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update');

-- Query only metadata here. Inspect safe details through the authorized Audit Log page.
SELECT TASK_CODE, STATUS, COUNT(*) RECORD_COUNT
  FROM DIGX_AL_AUDIT_LOGGING
 WHERE TASK_CODE IN ('UAT_N_HAP_GEN','UAT_N_HAP_RVL','CM_N_HAP_SETUP','CM_N_HAP_RESET',
                     'UAT_N_HUA_NEW','UAT_N_HUA_EDT','UAT_N_HUA_DEL','MT_N_CUS','MT_N_UUS')
 GROUP BY TASK_CODE,STATUS;
