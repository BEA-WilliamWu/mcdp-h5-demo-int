-- Read-only verification. Run in the OBDX schema; set :party_id to the test company.
-- Existing BCO user-create/edit task names are intentionally retained.
SELECT T.ID, T.NAME, T.TASK_TYPE, T.MODULE_TYPE, A.ENABLED AS AUDIT_ENABLED,
       R.RESOURCE_NAME
  FROM DIGX_CM_TASK T
  LEFT JOIN DIGX_CM_TASK_ASPECTS A ON A.TASK_ID = T.ID AND A.ASPECT = 'audit'
  LEFT JOIN DIGX_CM_RESOURCE_TASK_REL R ON R.TASK_ID = T.ID
 WHERE T.ID IN ('MT_N_CUS', 'MT_N_UUS',
                'UAT_N_HAP_GEN', 'UAT_N_HAP_REGEN', 'UAT_N_HAP_RVL', 'CM_N_HAP_SETUP', 'CM_N_HAP_RESET',
                'UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY T.ID, R.RESOURCE_NAME;

-- This query intentionally excludes raw request/response and headers:
-- historic records have not been cleaned by 791 and might contain credentials.
SELECT ID, TASK_CODE, USER_ID, PARTY_ID, ACTION, STATUS, REFERENCE_NO,
       START_DATE_TIME
  FROM DIGX_AL_AUDIT_LOGGING
 WHERE PARTY_ID = :party_id
   AND START_DATE_TIME >= SYSDATE - 1
   AND TASK_CODE IN ('MT_N_CUS', 'MT_N_UUS',
                     'UAT_N_HAP_GEN', 'UAT_N_HAP_REGEN', 'UAT_N_HAP_RVL', 'CM_N_HAP_SETUP', 'CM_N_HAP_RESET',
                     'UAT_N_HUA_NEW', 'UAT_N_HUA_EDT', 'UAT_N_HUA_DEL')
 ORDER BY START_DATE_TIME DESC;
