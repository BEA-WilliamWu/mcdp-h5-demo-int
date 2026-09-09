-- BCOH2H-787: repository adapter registration for the HTH API Password Code table.
-- Template: 20260825_HTH_User_Access/4_HTH_User_Access_Repository_Adapters.sql.
-- Base and OBDX_BU override rows must identify the same implementation class.
-- Re-runnable: delete feature adapter keys before insert; single commit at the end.

DELETE FROM DIGX_FW_CONFIG_ALL_B
 WHERE CATEGORY_ID = 'repositoryadapterconfig'
   AND PROP_ID = 'HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER';

INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_B
    (PROP_ID, CATEGORY_ID, PROP_VALUE, FACTORY_SHIPPED_FLAG, PROP_COMMENTS,
     SUMMARY_TEXT, CREATED_BY, CREATION_DATE, LAST_UPDATED_BY,
     LAST_UPDATED_DATE, OBJECT_STATUS, OBJECT_VERSION_NUMBER, EDITABLE,
     CATEGORY_DESCRIPTION)
  VALUES
    ('HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER', 'repositoryadapterconfig',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthApiPasswordCodeRepositoryAdapter',
     'N', 'Persists one-time HTH API password setup codes and their approval-driven lifecycle.',
     'HTH API password code repository adapter', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE,
     NULL, 1, 'N', NULL)
SELECT 1 FROM DUAL;

DELETE FROM DIGX_FW_CONFIG_ALL_O
 WHERE PREFERENCE_NAME = 'RepositoryAdapterFactories'
   AND PROP_ID = 'HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER';

INSERT ALL
  INTO DIGX_FW_CONFIG_ALL_O
    (PROP_ID, PREFERENCE_NAME, PROP_VALUE, DETERMINANT_VALUE, CREATED_BY,
     CREATION_DATE, LAST_UPDATED_BY, LAST_UPDATED_DATE)
  VALUES
    ('HTH_API_PASSWORD_CODE_LOCAL_REPOSITORY_ADAPTER', 'RepositoryAdapterFactories',
     'com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthApiPasswordCodeRepositoryAdapter',
     'OBDX_BU', 'ofssuser', SYSDATE, 'ofssuser', SYSDATE)
SELECT 1 FROM DUAL;

COMMIT;
