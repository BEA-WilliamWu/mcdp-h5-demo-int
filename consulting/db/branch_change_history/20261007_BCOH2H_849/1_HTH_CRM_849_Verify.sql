-- BCOH2H-849 uses the existing DIGX CRM event table. Run as the DIGX schema owner.
-- Read-only preflight; this story does not create or alter HTH/BCO tables.
DECLARE
  v_column_length NUMBER;
  v_sequence_count NUMBER;
BEGIN
  SELECT CASE WHEN CHAR_USED = 'C' THEN CHAR_LENGTH ELSE DATA_LENGTH END
    INTO v_column_length
    FROM USER_TAB_COLUMNS
   WHERE TABLE_NAME = 'DIGX_CZ_CRM_EVENT3_DETAILS'
     AND COLUMN_NAME = 'EVENT_ACTV_TYPE_CODE';
  IF v_column_length < LENGTH('HTH_PWD_CRD') THEN
    RAISE_APPLICATION_ERROR(-20849,
      '849: EVENT_ACTV_TYPE_CODE is too short for the approved HTH activity codes');
  END IF;
  SELECT COUNT(*) INTO v_sequence_count
    FROM ALL_SEQUENCES
   WHERE SEQUENCE_NAME = 'CRM_SEQUENCE';
  IF v_sequence_count = 0 THEN
    RAISE_APPLICATION_ERROR(-20849, '849: CRM_SEQUENCE is not accessible');
  END IF;
END;
/

-- After a real setup/reset, inspect the new rows (never query password or Code values).
SELECT EVENT_ID, EVENT_DTE, EVENT_TIME, CHNL_ID, EVENT_ACTV_TYPE_CODE,
       EVENT_STATUS_CODE, USER_ID, ACCT_NBR, BATCH_PROCESSED_DATE
  FROM DIGX_CZ_CRM_EVENT3_DETAILS
 WHERE CHNL_ID = 'ELE-HTH'
   AND EVENT_ACTV_TYPE_CODE IN ('HTH_PWD_CRD', 'HTH_PWD_UPD')
 ORDER BY EVENT_ID DESC
 FETCH FIRST 20 ROWS ONLY;
