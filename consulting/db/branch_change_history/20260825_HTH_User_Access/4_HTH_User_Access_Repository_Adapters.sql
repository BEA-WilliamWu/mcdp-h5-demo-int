-- BCOH2H-538 / BCOH2H-595: repository adapter registration.
-- Re-runnable: delete feature adapter keys before insert.
-- Execute in the OBDX configuration schema after the application classes are deployed. Base and
-- OBDX_BU override rows must identify the same implementation class. The script commits once at
-- the end; for backout, execute both DELETE blocks without the INSERT ALL statements.
-- The three REQUEST adapter registrations are retained for backward deployment compatibility.
-- The final service uses DIGX_AP_TRANSACTION.transactionSnapshot and does not call those adapters.

DELETE FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'repositoryadapterconfig'
   AND PROP_ID IN (
    'HTH_USER_ACCESS_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER'
   );

INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessAccountRepositoryAdapter',
     'N', 'Persists effective HTH user account grants and performs summary/runtime lookups.',
     'HTH user account grant repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessAccountApiRepositoryAdapter',
     'N', 'Persists effective API grants under an HTH user account grant.',
     'HTH account API grant repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestRepositoryAdapter',
     'N', 'Legacy adapter retained for compatibility; platform transaction snapshot is authoritative.',
     'Legacy HTH access request repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestAccountRepositoryAdapter',
     'N', 'Legacy request-account adapter retained for backward deployment compatibility.',
     'Legacy HTH request account repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestApiRepositoryAdapter',
     'N', 'Legacy request-API adapter retained for backward deployment compatibility.',
     'Legacy HTH request API repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
SELECT 1 FROM DUAL;

DELETE FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'RepositoryAdapterFactories'
   AND PROP_ID IN (
    'HTH_USER_ACCESS_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER',
    'HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER'
   );

INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_USER_ACCESS_ACCOUNT_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessAccountRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_USER_ACCESS_ACCOUNT_API_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessAccountApiRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_USER_ACCESS_REQUEST_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_USER_ACCESS_REQ_ACCOUNT_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestAccountRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_USER_ACCESS_REQ_API_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthUserAccessRequestApiRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
SELECT 1 FROM DUAL;

COMMIT;
