-- BCOH2H-538 / BCOH2H-595: retain both BCO account-number representations and product code.
--
-- Fresh installations already receive these columns from 1_HTH_User_Access_Schema.sql. Run this
-- upgrade only for an existing HTH_USER_ACCESS_ACCOUNT table. Re-running is safe.
--
-- ACCOUNT_NUMBER remains the canonical/internal authorization key. ACCOUNT_NUMBER_FORMATTED is
-- the external/display identifier used by BCO. Existing rows are populated conservatively with
-- their current identifier; the application refreshes the canonical/formatted pair and product
-- code from DIGX_PI_PARTY_ACCOUNTS on the next approved create/edit.

DECLARE
  PROCEDURE ADD_COLUMN_IF_MISSING(
    P_COLUMN_NAME IN VARCHAR2,
    P_DEFINITION  IN VARCHAR2
  ) IS
    L_COUNT NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO L_COUNT
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'HTH_BEA'
       AND TABLE_NAME = 'HTH_USER_ACCESS_ACCOUNT'
       AND COLUMN_NAME = P_COLUMN_NAME;

    IF L_COUNT = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT ADD ('
        || P_COLUMN_NAME || ' ' || P_DEFINITION || ')';
    END IF;
  END ADD_COLUMN_IF_MISSING;
BEGIN
  ADD_COLUMN_IF_MISSING('ACCOUNT_NUMBER_FORMATTED', 'VARCHAR2(64 BYTE)');
  ADD_COLUMN_IF_MISSING('PRODUCT_CODE', 'VARCHAR2(32 BYTE)');
END;
/

-- Backfill only from the BCO account catalogue. Do not derive an internal identifier from digits
-- alone: 14-digit AIO accounts require product metadata. If historical rows representing the same
-- account in two formats collide with the effective-grant unique key, this MERGE stops so those
-- duplicates can be reviewed instead of deleting one implicitly.
MERGE INTO HTH_BEA.HTH_USER_ACCESS_ACCOUNT H
USING (
  SELECT ID, INTERNAL_ACCOUNT_NUMBER, ACCOUNT_NUMBER_FORMATTED, PRODUCT_CODE
    FROM (
      SELECT H0.ID,
             REGEXP_SUBSTR(P.ACCOUNT_NUMBER, '[^~]+', 1, 1)
               AS INTERNAL_ACCOUNT_NUMBER,
             P.ACCOUNT_NUMBER_FORMATTED,
             NVL(P.PRODUCT_CODE,
                 CASE
                   WHEN REGEXP_SUBSTR(P.ACCOUNT_NUMBER, '[^~]+', 1, 3) IS NOT NULL
                   THEN REGEXP_SUBSTR(P.ACCOUNT_NUMBER, '[^~]+', 1, 2)
                 END) AS PRODUCT_CODE,
             ROW_NUMBER() OVER (
               PARTITION BY H0.ID
               ORDER BY CASE WHEN TRIM(P.PARTYID) = H0.ACCESS_PARTY_ID THEN 0 ELSE 1 END,
                        P.LAST_UPDATED_DATE DESC NULLS LAST
             ) AS RN
        FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT H0
        JOIN DIGX_PI_PARTY_ACCOUNTS P
          ON (TRIM(P.PARTYID) = H0.ACCESS_PARTY_ID
              OR TRIM(P.PARENT_PARTY_ID) = H0.ACCESS_PARTY_ID)
         AND (
              REPLACE(REPLACE(REGEXP_SUBSTR(P.ACCOUNT_NUMBER, '[^~]+', 1, 1), '-', ''), ' ', '')
                = REPLACE(REPLACE(REGEXP_SUBSTR(H0.ACCOUNT_NUMBER, '^[0-9]+'), '-', ''), ' ', '')
              OR REPLACE(REPLACE(P.ACCOUNT_NUMBER_FORMATTED, '-', ''), ' ', '')
                = REPLACE(REPLACE(REGEXP_SUBSTR(H0.ACCOUNT_NUMBER, '^[0-9]+'), '-', ''), ' ', '')
             )
       WHERE UPPER(NVL(P.STATUS, 'ACTIVE')) = 'ACTIVE'
    )
   WHERE RN = 1
) P
ON (H.ID = P.ID)
WHEN MATCHED THEN UPDATE SET
  H.ACCOUNT_NUMBER = P.INTERNAL_ACCOUNT_NUMBER,
  H.ACCOUNT_NUMBER_FORMATTED = NVL(P.ACCOUNT_NUMBER_FORMATTED,
                                   P.INTERNAL_ACCOUNT_NUMBER),
  H.PRODUCT_CODE = P.PRODUCT_CODE;

-- Rows not found in the current BCO catalogue remain readable. They are reported by the
-- verification script and refreshed when their context is next approved.
UPDATE HTH_BEA.HTH_USER_ACCESS_ACCOUNT
   SET ACCOUNT_NUMBER_FORMATTED = ACCOUNT_NUMBER
 WHERE ACCOUNT_NUMBER_FORMATTED IS NULL;

ALTER TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT
  MODIFY (ACCOUNT_NUMBER_FORMATTED NOT NULL);

DECLARE
  L_COUNT NUMBER;
BEGIN
  SELECT COUNT(*)
    INTO L_COUNT
    FROM ALL_INDEXES
   WHERE OWNER = 'HTH_BEA'
     AND INDEX_NAME = 'IX_HTH_UAA_ACCOUNT_FMT';

  IF L_COUNT = 0 THEN
    EXECUTE IMMEDIATE
      'CREATE INDEX HTH_BEA.IX_HTH_UAA_ACCOUNT_FMT '
      || 'ON HTH_BEA.HTH_USER_ACCESS_ACCOUNT (ACCOUNT_NUMBER_FORMATTED)';
  END IF;
END;
/

COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_NUMBER IS
  'Canonical/internal unmasked account identifier; must not be logged in plain text.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_NUMBER_FORMATTED IS
  'External/formatted account identifier from the BCO account catalogue; must not be logged in plain text.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.PRODUCT_CODE IS
  'BCO account product code; stored separately from the CSA/TD access account type.';

COMMIT;
