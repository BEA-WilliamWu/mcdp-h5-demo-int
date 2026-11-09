-- READ ONLY. Run before/after 1216_HTH_User_Access_Notification.sql.
-- Before changing an existing environment, export Q1 and Q4 as the binding/source backup.
-- Expected: Q2 and Q3 return zero rows after a successful migration.

-- Q1: actual BCO and HTH bindings, both primary and alternate template IDs/raw text.
WITH routes AS (
  SELECT r.* FROM DIGX_EP_EVT_REC_B r
   WHERE (COD_ACT_ID='com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update'
          AND COD_EVENT_ID='USER_ACCOUNT_ACCESS_UPDATE')
      OR (COD_ACT_ID IN (
            'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
            'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit')
          AND COD_EVENT_ID IN ('HTH_USER_ACCOUNT_ACCESS_UPDATE','HTH_USER_ACCOUNT_ACCESS_COMPANY'))
), bindings AS (
  SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, SUBSCRIBER_TYPE, SUBSCRIBER_VALUE,
         TXT_DEST_TYP, LOCALE, 'PRIMARY' BINDING_TYPE, COD_MSG_TMPL_ID TEMPLATE_ID FROM routes
  UNION ALL
  SELECT COD_ACT_ID, COD_EVENT_ID, COD_ACTION_ID, SUBSCRIBER_TYPE, SUBSCRIBER_VALUE,
         TXT_DEST_TYP, LOCALE, 'ALTERNATE', UNSECURE_MSG_TMPL_ID FROM routes
   WHERE TRIM(UNSECURE_MSG_TMPL_ID) IS NOT NULL
)
SELECT b.*, t.DESTINATION_TYPE, t.OBJECT_STATUS, t.TXT_SUBJECT_TMPL, t.TXT_MSG_TMPL
  FROM bindings b LEFT JOIN DIGX_EP_MSG_TMPL_B t
    ON t.COD_TMPL_ID=b.TEMPLATE_ID AND t.DETERMINANT_VALUE='OBDX_BU'
 ORDER BY b.COD_ACT_ID, b.COD_EVENT_ID, b.TXT_DEST_TYP, b.LOCALE, b.BINDING_TYPE;

-- Q2: HTH action-A routes must match the correct BCO recipient role and channel.
-- Do not compare company templates against CORPORATE/USER or introduce company SMS.
WITH bco AS (
  SELECT DISTINCT CASE WHEN SUBSCRIBER_TYPE='PARTY' THEN 'HTH_USER_ACCOUNT_ACCESS_COMPANY'
                       ELSE 'HTH_USER_ACCOUNT_ACCESS_UPDATE' END EVENT_ID,
         TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID, UNSECURE_MSG_TMPL_ID
    FROM DIGX_EP_EVT_REC_B
   WHERE COD_ACT_ID='com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update'
     AND COD_EVENT_ID='USER_ACCOUNT_ACCESS_UPDATE' AND COD_ACTION_ID='A'
     AND ((SUBSCRIBER_TYPE='CORPORATE' AND SUBSCRIBER_VALUE='USER' AND TXT_DEST_TYP IN ('EMAIL','SMS'))
       OR (SUBSCRIBER_TYPE='PARTY' AND SUBSCRIBER_VALUE='CUSTOMER' AND TXT_DEST_TYP='EMAIL'))
), operations AS (
  SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit' ACTIVITY FROM DUAL
  UNION ALL SELECT 'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit' FROM DUAL
)
SELECT a.ACTIVITY, b.EVENT_ID, b.TXT_DEST_TYP, b.LOCALE, b.COD_MSG_TMPL_ID EXPECTED_TEMPLATE,
       b.UNSECURE_MSG_TMPL_ID EXPECTED_ALTERNATE, COUNT(h.COD_ACT_ID) HTH_ROUTE_COUNT
  FROM bco b CROSS JOIN operations a LEFT JOIN DIGX_EP_EVT_REC_B h
    ON h.COD_ACT_ID=a.ACTIVITY AND h.COD_EVENT_ID=b.EVENT_ID AND h.COD_ACTION_ID='A'
   AND h.TXT_DEST_TYP=b.TXT_DEST_TYP AND h.LOCALE=b.LOCALE
 GROUP BY a.ACTIVITY, b.EVENT_ID, b.TXT_DEST_TYP, b.LOCALE, b.COD_MSG_TMPL_ID, b.UNSECURE_MSG_TMPL_ID
HAVING COUNT(h.COD_ACT_ID)<>1 OR SUM(CASE WHEN h.COD_MSG_TMPL_ID=b.COD_MSG_TMPL_ID
       AND NVL(h.UNSECURE_MSG_TMPL_ID,'<NONE>')=NVL(b.UNSECURE_MSG_TMPL_ID,'<NONE>')
       AND h.SUBSCRIBER_TYPE='EXTERNAL' AND h.SUBSCRIBER_VALUE='USER' AND h.FLG_CONDITIONAL='N'
       THEN 1 ELSE 0 END)<>1
 ORDER BY a.ACTIVITY, b.EVENT_ID, b.TXT_DEST_TYP, b.LOCALE;

-- Q3: every attribute used by a bound BCO template needs the matching HTH DTO source.
-- Check alternate templates too. Any row here is missing, inactive or inconsistent.
WITH bindings AS (
  SELECT COD_ACT_ID, COD_MSG_TMPL_ID TEMPLATE_ID FROM DIGX_EP_EVT_REC_B
   WHERE COD_EVENT_ID IN ('HTH_USER_ACCOUNT_ACCESS_UPDATE','HTH_USER_ACCOUNT_ACCESS_COMPANY')
     AND COD_ACTION_ID='A'
     AND COD_ACT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
                        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit')
  UNION
  SELECT COD_ACT_ID, UNSECURE_MSG_TMPL_ID FROM DIGX_EP_EVT_REC_B
   WHERE COD_EVENT_ID IN ('HTH_USER_ACCOUNT_ACCESS_UPDATE','HTH_USER_ACCOUNT_ACCESS_COMPANY')
     AND COD_ACTION_ID='A' AND TRIM(UNSECURE_MSG_TMPL_ID) IS NOT NULL
     AND COD_ACT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
                        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit')
)
SELECT b.COD_ACT_ID, b.TEMPLATE_ID, m.COD_ATTR_ID,
       h.COD_SERVICE_ATTR_ID HTH_SOURCE, d.REF_FIELD_DEFN_ID HTH_FIELD,
       sd.REF_FIELD_DEFN_ID BCO_FIELD
  FROM bindings b JOIN DIGX_EP_MSG_ATTR_B m
    ON m.COD_MESS_TMPL_ID=b.TEMPLATE_ID AND m.DETERMINANT_VALUE='OBDX_BU'
  LEFT JOIN DIGX_EP_MSG_SRC_B h
    ON h.COD_MESS_TMPL_ID=b.TEMPLATE_ID AND h.COD_ATTR_ID=m.COD_ATTR_ID
   AND h.COD_ACT_ID=b.COD_ACT_ID AND h.DETERMINANT_VALUE='OBDX_BU'
  LEFT JOIN DIGX_MD_SERVICE_ATTR d ON d.COD_SERVICE_ATTR_ID=h.COD_SERVICE_ATTR_ID
  LEFT JOIN DIGX_EP_MSG_SRC_B s
    ON s.COD_MESS_TMPL_ID=b.TEMPLATE_ID AND s.COD_ATTR_ID=m.COD_ATTR_ID
   AND s.COD_ACT_ID='com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update'
   AND s.DETERMINANT_VALUE='OBDX_BU'
  LEFT JOIN DIGX_MD_SERVICE_ATTR sd ON sd.COD_SERVICE_ATTR_ID=s.COD_SERVICE_ATTR_ID
 WHERE h.COD_SERVICE_ATTR_ID IS NULL OR s.COD_SERVICE_ATTR_ID IS NULL
    OR NVL(d.OBJECT_STATUS,'?')<>'A' OR NVL(sd.OBJECT_STATUS,'?')<>'A'
    OR NVL(d.COD_SERVICE_ID,'?')<>b.COD_ACT_ID
    OR NVL(d.REF_FIELD_DEFN_ID,'?')<>NVL(sd.REF_FIELD_DEFN_ID,'?')
    OR NVL(d.TYP_DATA_SRC,'?')<>NVL(sd.TYP_DATA_SRC,'?')
 ORDER BY b.COD_ACT_ID, b.TEMPLATE_ID, m.COD_ATTR_ID;

-- Q4: export HTH recipient rows/source metadata before migration for rollback.
SELECT r.* FROM DIGX_EP_EVT_REC_B r
 WHERE COD_EVENT_ID IN ('HTH_USER_ACCOUNT_ACCESS_UPDATE','HTH_USER_ACCOUNT_ACCESS_COMPANY')
   AND COD_ACT_ID IN ('com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
                      'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit')
   AND COD_ACTION_ID='A';

SELECT s.*, d.COD_SERVICE_ID, d.REF_FIELD_DEFN_ID, d.TYP_DATA_SRC, d.OBJECT_STATUS
  FROM DIGX_EP_MSG_SRC_B s JOIN DIGX_MD_SERVICE_ATTR d ON d.COD_SERVICE_ATTR_ID=s.COD_SERVICE_ATTR_ID
 WHERE s.COD_ACT_ID IN ('com.ofss.digx.app.access.service.account.party.user.UserAccountAccess.update',
                        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.submit',
                        'com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.edit')
   AND s.DETERMINANT_VALUE='OBDX_BU'
 ORDER BY s.COD_ACT_ID, s.COD_MESS_TMPL_ID, s.COD_ATTR_ID;
