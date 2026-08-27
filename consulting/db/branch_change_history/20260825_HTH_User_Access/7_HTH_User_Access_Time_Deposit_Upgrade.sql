-- BCOH2H-538 / BCOH2H-595: add Time Deposit support to an existing HTH User Access schema.
--
-- Run this script when 1_HTH_User_Access_Schema.sql was executed before the current TD constraints
-- and composite account business keys were finalized. A fresh installation using the current
-- schema script already contains the same constraints and comments and does not require this
-- upgrade.
--
-- The upgrade is data-preserving: it expands the two account-type check constraints and changes
-- each account business key from Account Number to Account Type + Account Number. This is required
-- because BCO can return the same Account Number in both CSA and TD tabs. Existing grants and
-- approval snapshots are not updated or deleted. Re-running is safe because each known constraint
-- is dropped before it is recreated.
-- Oracle DDL commits implicitly; execute during an approved deployment window as HTH_BEA or as a
-- deployment account with ALTER privileges on the feature tables.

DECLARE
  PROCEDURE DROP_CONSTRAINT_IF_PRESENT(
    P_TABLE_NAME      IN VARCHAR2,
    P_CONSTRAINT_NAME IN VARCHAR2
  ) IS
    L_COUNT NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO L_COUNT
      FROM ALL_CONSTRAINTS
     WHERE OWNER = 'HTH_BEA'
       AND TABLE_NAME = P_TABLE_NAME
       AND CONSTRAINT_NAME = P_CONSTRAINT_NAME;

    IF L_COUNT > 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE HTH_BEA.' || P_TABLE_NAME
        || ' DROP CONSTRAINT ' || P_CONSTRAINT_NAME;
    END IF;
  END DROP_CONSTRAINT_IF_PRESENT;
BEGIN
  DROP_CONSTRAINT_IF_PRESENT('HTH_USER_ACCESS_ACCOUNT', 'CK_HTH_UAA_TYPE');
  DROP_CONSTRAINT_IF_PRESENT('HTH_USER_ACCESS_REQ_ACCOUNT', 'CK_HTH_UARA_TYPE');
  DROP_CONSTRAINT_IF_PRESENT('HTH_USER_ACCESS_ACCOUNT', 'UK_HTH_UA_ACCOUNT');
  DROP_CONSTRAINT_IF_PRESENT('HTH_USER_ACCESS_REQ_ACCOUNT', 'UK_HTH_UAR_ACCOUNT');
END;
/

ALTER TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT
  ADD CONSTRAINT CK_HTH_UAA_TYPE CHECK (ACCOUNT_TYPE IN ('CSA', 'TD'));

ALTER TABLE HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT
  ADD CONSTRAINT CK_HTH_UARA_TYPE CHECK (ACCOUNT_TYPE IN ('CSA', 'TD'));

ALTER TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT
  ADD CONSTRAINT UK_HTH_UA_ACCOUNT UNIQUE
    (PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE, ACCOUNT_TYPE, ACCOUNT_NUMBER);

ALTER TABLE HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT
  ADD CONSTRAINT UK_HTH_UAR_ACCOUNT UNIQUE
    (HTH_USER_ACCESS_REQUEST_ID, ACCOUNT_TYPE, ACCOUNT_NUMBER);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT IS
  'Effective Current and Savings or Time Deposit grants by HTH user and account-owning party.';

COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_TYPE IS
  'Granted account type: CSA for Current and Savings or TD for Time Deposit.';

COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.ACCOUNT_TYPE IS
  'Account type captured at submit time: CSA for Current and Savings or TD for Time Deposit.';
