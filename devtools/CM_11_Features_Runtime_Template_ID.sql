-- Read-only Oracle query: inspect template IDs inside SAVED runtime dispatch data.
-- Run in the schema that owns DIGX_EP_ACN_LOG_B and DIGX_EP_EVT_REC_B.
-- Bind :from_time and :to_time as YYYY-MM-DD HH24:MI:SS.
-- Example: 2026-10-13 14:15:00 to 2026-10-13 14:25:00.
-- Start with a short test window; this query searches BLOB contents.
--
-- Evidence checked against the local framework JAR:
-- MessageTemplateService adds the selected MessageTemplate object (including
-- its ID) as DispatchTemplate; ActionLogFactory passes dispatch data to
-- ActionLog.setDispatchData; StreamHelper uses Java ObjectOutputStream.
-- ActionLogConfig can disable saving this data. A missing BLOB is NOT evidence
-- that no notification was sent.
--
-- This is a search for complete Java serialized string values, not a full Java
-- object decoder. A unique match identifies a template ID present in that
-- runtime payload; multiple matches need the Template Id runtime log or object
-- decoding. Do not replace unresolved results with an arbitrary template.
-- The search dictionary is today's routes, including UNSECURE_MSG_TMPL_ID.
-- A removed/changed historical route can therefore result in NO_MATCH.
-- Exported template IDs are ASCII; TC_STRING + two-byte length prevents a
-- short ID from matching a longer ID with the same prefix.
--
-- FEATURE_REFS lists possible document features, NOT an inferred transaction.
-- Features 3/4/5 and 9/10/11 share events and require the tested operation to
-- distinguish them. Create and phone changes share the welcome SMS event.
-- FRAMEWORK_DEST_ADDR is the address BEFORE custom dispatcher expansion; it
-- is not a substitute for RECIPIENTID in DIGX_CZ_EMAIL_MNG.
-- PROCESS_STATUS is the framework processing result, not delivery confirmation.
-- This query does not join MNG on time/event: that would invent a correlation.

WITH event_scope (feature_refs, event_id) AS (
    SELECT '1', 'USER_MANAGEMENT_CREATE' FROM dual UNION ALL
    SELECT '2/9/10/11', 'USER_MANAGEMENT_EDIT' FROM dual UNION ALL
    SELECT '1/9/11', 'CORPORATEPLUS_WELCOME_MAIL' FROM dual UNION ALL
    SELECT '3/4/5', 'HTH_USER_ACCOUNT_ACCESS_UPDATE' FROM dual UNION ALL
    SELECT '3/4/5', 'HTH_USER_ACCOUNT_ACCESS_COMPANY' FROM dual UNION ALL
    SELECT '6', 'HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT' FROM dual UNION ALL
    SELECT '6', 'HTH_API_PASSWORD_CODE_APPROVED_COMPANY_EMAIL_EVENT' FROM dual UNION ALL
    SELECT '7', 'HTH_API_PASSWORD_SETUP_SUCCESS' FROM dual UNION ALL
    SELECT '8', 'HTH_API_PASSWORD_RESET_SUCCESS' FROM dual UNION ALL
    SELECT '9/10/11', 'INFO_UPDATE_BY_CORP_ADMIN' FROM dual UNION ALL
    SELECT '9/10', 'USER_EMAIL_ADDRESS_UPDATE' FROM dual UNION ALL
    SELECT '9/11', 'USER_MOBILE_NUMBER_UPDATED_REMINDER' FROM dual UNION ALL
    -- Include extra PIN events so an unexpected PIN notification is not hidden.
    SELECT 'PIN_EXTRA', 'LOGIN_PIN_RESET_ENABLE_REMINDER_CORPORATE_USER' FROM dual UNION ALL
    SELECT 'PIN_EXTRA', 'LOGIN_PIN_RESET_ENABLE_REMINDER_COMPANY' FROM dual UNION ALL
    SELECT 'PIN_EXTRA', 'LOGIN_PIN_RESET_DISABLE_REMINDER_CORPORATE_USER' FROM dual UNION ALL
    SELECT 'PIN_EXTRA', 'LOGIN_PIN_RESET_DISABLE_REMINDER_COMPANY' FROM dual
), runtime_logs AS (
    SELECT /*+ MATERIALIZE */
           s.feature_refs,
           l.COD_ACT_LOG_ID,
           l.COD_ACT_DATA_ID,
           l.COD_ACT_ID,
           l.COD_EVENT_ID,
           l.COD_ACTION_ID,
           l.TXT_DEST_TYP,
           l.TXT_SUBSCRIBER_TYP,
           l.TXT_DEST_ADDR,
           l.FLG_PROCESS_STAT,
           l.DAT_EXECUTION,
           l.TXT_COMMENTS,
           l.DISP_DATA_OBJECT_BLOB,
           DBMS_LOB.GETLENGTH(l.DISP_DATA_OBJECT_BLOB) AS blob_bytes,
           RAWTOHEX(DBMS_LOB.SUBSTR(l.DISP_DATA_OBJECT_BLOB, 4, 1)) AS blob_header
    FROM DIGX_EP_ACN_LOG_B l
    JOIN event_scope s ON s.event_id = l.COD_EVENT_ID
    WHERE l.DAT_EXECUTION >= TO_TIMESTAMP(:from_time, 'YYYY-MM-DD HH24:MI:SS')
      AND l.DAT_EXECUTION <  TO_TIMESTAMP(:to_time,   'YYYY-MM-DD HH24:MI:SS')
      AND l.TXT_DEST_TYP IN ('EMAIL', 'SMS')
), route_ids AS (
    SELECT r.COD_ACT_ID, r.COD_EVENT_ID, r.COD_ACTION_ID,
           r.TXT_DEST_TYP, r.COD_MSG_TMPL_ID AS template_id
    FROM DIGX_EP_EVT_REC_B r
    JOIN event_scope s ON s.event_id = r.COD_EVENT_ID
    WHERE r.COD_MSG_TMPL_ID IS NOT NULL
      AND r.TXT_DEST_TYP IN ('EMAIL', 'SMS')
    UNION
    SELECT r.COD_ACT_ID, r.COD_EVENT_ID, r.COD_ACTION_ID,
           'SMS' AS TXT_DEST_TYP, r.UNSECURE_MSG_TMPL_ID AS template_id
    FROM DIGX_EP_EVT_REC_B r
    JOIN event_scope s ON s.event_id = r.COD_EVENT_ID
    WHERE r.UNSECURE_MSG_TMPL_ID IS NOT NULL
), patterns AS (
    SELECT r.*,
           UTL_RAW.CONCAT(
               HEXTORAW('74' || LPAD(TO_CHAR(LENGTHB(r.template_id), 'FMXXXX'), 4, '0')),
               UTL_RAW.CAST_TO_RAW(r.template_id)
           ) AS id_pattern
    FROM route_ids r
), matched AS (
    SELECT l.feature_refs,
           l.COD_ACT_LOG_ID,
           l.COD_ACT_DATA_ID,
           l.COD_ACT_ID,
           l.COD_EVENT_ID,
           l.COD_ACTION_ID,
           l.TXT_DEST_TYP,
           l.TXT_SUBSCRIBER_TYP,
           l.TXT_DEST_ADDR,
           l.FLG_PROCESS_STAT,
           l.DAT_EXECUTION,
           l.TXT_COMMENTS,
           l.blob_bytes,
           l.blob_header,
           p.template_id,
           COUNT(p.template_id) OVER (PARTITION BY l.COD_ACT_LOG_ID) AS match_count
    FROM runtime_logs l
    LEFT JOIN patterns p
      ON p.COD_ACT_ID = l.COD_ACT_ID
     AND p.COD_EVENT_ID = l.COD_EVENT_ID
     AND p.COD_ACTION_ID = l.COD_ACTION_ID
     AND p.TXT_DEST_TYP = l.TXT_DEST_TYP
     AND CASE
             WHEN l.blob_header = 'ACED0005'
             THEN DBMS_LOB.INSTR(l.DISP_DATA_OBJECT_BLOB, p.id_pattern, 1, 1)
             ELSE 0
         END > 0
)
SELECT feature_refs,
       DAT_EXECUTION AS processed_at,
       COD_ACT_LOG_ID AS action_log_id,
       COD_ACT_DATA_ID AS activity_data_id,
       COD_ACT_ID AS activity_id,
       COD_EVENT_ID AS event_id,
       COD_ACTION_ID AS action_id,
       TXT_DEST_TYP AS channel,
       TXT_SUBSCRIBER_TYP AS framework_subscriber_type,
       TXT_DEST_ADDR AS framework_dest_addr,
       FLG_PROCESS_STAT AS process_status,
       CASE
           WHEN NVL(blob_bytes, 0) = 0 THEN 'NO_SAVED_DISPATCH_DATA'
           WHEN blob_header <> 'ACED0005' THEN 'UNEXPECTED_BLOB_FORMAT'
           WHEN match_count = 1 THEN 'UNIQUE_TEMPLATE_ID_IN_LOG'
           WHEN match_count > 1 THEN 'MULTIPLE_MATCHES_CHECK_LOG'
           ELSE 'NO_MATCH_CHECK_HISTORY_OR_LOG'
       END AS template_evidence,
       CASE WHEN match_count = 1 THEN template_id END AS unique_template_id,
       template_id AS matched_template_id,
       match_count,
       blob_bytes,
       blob_header,
       TXT_COMMENTS AS process_comments
FROM matched
ORDER BY processed_at DESC, action_log_id, matched_template_id;
