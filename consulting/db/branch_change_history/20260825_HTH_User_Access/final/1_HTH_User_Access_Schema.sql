-- BCOH2H-538 / BCOH2H-595: Host to Host User Accounts & Services Access schema.
-- Prerequisites:
--   20260416_HTH_Management/1_HTH_Management_Schema.sql
--   20260803_User_Channel_Type/1_User_Channel_Type_Schema.sql
-- Target owner: HTH_BEA for all new feature tables and indexes.
-- Execute as HTH_BEA, or as a deployment account with direct CREATE/INDEX privileges and direct
-- REFERENCES grants on prerequisite objects. Oracle does not accept a role-based REFERENCES grant
-- for foreign-key creation. HTH_USER_PROFILE and HTH_API_MASTER are existing HTH_BEA objects and
-- must match the qualified foreign-key targets below.
--
-- Package execution order:
--   1. This schema script.
--   2. 2_HTH_User_Access_Permission.sql.
--   3. 3_HTH_User_Access_Process.sql.
--   4. 4_HTH_User_Access_Repository_Adapters.sql.
--   5. 5_HTH_User_Access_Error_Messages.sql.
--   6. 6_HTH_User_Access_Verification.sql.
--
-- This is a one-time DDL script. Do not re-run after the objects have been created.
-- Oracle DDL commits implicitly and cannot be rolled back as one transaction. Before business data
-- exists, back out by dropping HTH_USER_ACCESS_ACCOUNT_API before HTH_USER_ACCESS_ACCOUNT. After
-- effective grant data exists, use an approved migration; never drop these two runtime tables.
-- Maker/checker payloads, pending status, duplicate detection and checker re-entry use the platform
-- DIGX_AP_TRANSACTION transactionSnapshot; no feature-specific request tables are required.

-- Effective Current and Savings or Time Deposit grants by HTH user and account-owning party.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT (
  ID                    VARCHAR2(36 BYTE)  NOT NULL,
  PARTY_ID              VARCHAR2(64 BYTE)  NOT NULL,
  CLOSE_ID              VARCHAR2(255 BYTE) NOT NULL,
  ACCESS_PARTY_ID       VARCHAR2(64 BYTE)  NOT NULL,
  LINKAGE_TYPE          VARCHAR2(16 BYTE)  NOT NULL,
  ACCOUNT_NUMBER        VARCHAR2(64 BYTE)  NOT NULL,
  ACCOUNT_NUMBER_FORMATTED VARCHAR2(64 BYTE) NOT NULL,
  PRODUCT_CODE          VARCHAR2(32 BYTE),
  ACCOUNT_TYPE          VARCHAR2(8 BYTE)   NOT NULL,
  CURRENCY              VARCHAR2(3 BYTE),
  OBJECT_STATUS         VARCHAR2(1 BYTE)   DEFAULT 'A' NOT NULL,
  CREATED_BY            VARCHAR2(255 BYTE) NOT NULL,
  CREATION_DATE         DATE               DEFAULT SYSDATE NOT NULL,
  LAST_UPDATED_BY       VARCHAR2(255 BYTE) NOT NULL,
  LAST_UPDATE_DATE      DATE               DEFAULT SYSDATE NOT NULL,
  CONSTRAINT PK_HTH_UA_ACCOUNT PRIMARY KEY (ID),
  CONSTRAINT UK_HTH_UA_ACCOUNT UNIQUE
    (PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE, ACCOUNT_TYPE, ACCOUNT_NUMBER),
  CONSTRAINT FK_HTH_UAA_USER_PROFILE FOREIGN KEY (PARTY_ID, CLOSE_ID)
    REFERENCES HTH_BEA.HTH_USER_PROFILE (PARTY_ID, CLOSE_ID),
  CONSTRAINT CK_HTH_UAA_LINKAGE CHECK
    (LINKAGE_TYPE IN ('RELATED', 'ASSOCIATED')),
  CONSTRAINT CK_HTH_UAA_TYPE CHECK (ACCOUNT_TYPE IN ('CSA', 'TD')),
  CONSTRAINT CK_HTH_UAA_STATUS CHECK (OBJECT_STATUS IN ('A', 'I'))
);

-- Supports runtime authorization lookup by canonical account number.
CREATE INDEX HTH_BEA.IX_HTH_UAA_ACCOUNT_NO
  ON HTH_BEA.HTH_USER_ACCESS_ACCOUNT (ACCOUNT_NUMBER);

-- Supports runtime authorization when an external/formatted account number is supplied.
CREATE INDEX HTH_BEA.IX_HTH_UAA_ACCOUNT_FMT
  ON HTH_BEA.HTH_USER_ACCESS_ACCOUNT (ACCOUNT_NUMBER_FORMATTED);

-- Supports summary, view/edit, and soft replacement by the complete access context.
CREATE INDEX HTH_BEA.IX_HTH_UAA_CONTEXT
  ON HTH_BEA.HTH_USER_ACCESS_ACCOUNT
    (PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT IS
  'Effective Current and Savings or Time Deposit grants by HTH user and account-owning party.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ID IS
  'Unique identifier of the effective account grant.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.PARTY_ID IS
  'Primary corporate party that owns the HTH user.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.CLOSE_ID IS
  'HTH user CloseID from HTH_USER_PROFILE.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCESS_PARTY_ID IS
  'Party that owns the granted account.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.LINKAGE_TYPE IS
  'Company relationship type: RELATED or ASSOCIATED.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_NUMBER IS
  'Canonical/internal unmasked account identifier; must not be logged in plain text.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_NUMBER_FORMATTED IS
  'External/formatted account identifier from the BCO account catalogue; must not be logged in plain text.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.PRODUCT_CODE IS
  'BCO account product code; stored separately from the CSA/TD access account type.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.ACCOUNT_TYPE IS
  'Granted account type: CSA for Current and Savings or TD for Time Deposit.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.CURRENCY IS
  'Display snapshot only; not used to validate account ownership.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.OBJECT_STATUS IS
  'Record status: A for active or I for inactive.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.CREATED_BY IS
  'User who created the record.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.CREATION_DATE IS
  'Date and time when the record was created.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.LAST_UPDATED_BY IS
  'User who last updated the record.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT.LAST_UPDATE_DATE IS
  'Date and time when the record was last updated.';

-- Effective API grants under each HTH user account.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API (
  ID                         VARCHAR2(36 BYTE)  NOT NULL,
  HTH_USER_ACCESS_ACCOUNT_ID VARCHAR2(36 BYTE)  NOT NULL,
  API_MASTER_ID              VARCHAR2(36 BYTE)  NOT NULL,
  OBJECT_STATUS              VARCHAR2(1 BYTE)   DEFAULT 'A' NOT NULL,
  CREATED_BY                 VARCHAR2(255 BYTE) NOT NULL,
  CREATION_DATE              DATE               DEFAULT SYSDATE NOT NULL,
  LAST_UPDATED_BY            VARCHAR2(255 BYTE) NOT NULL,
  LAST_UPDATE_DATE           DATE               DEFAULT SYSDATE NOT NULL,
  CONSTRAINT PK_HTH_UAA_API PRIMARY KEY (ID),
  CONSTRAINT UK_HTH_UAA_API UNIQUE
    (HTH_USER_ACCESS_ACCOUNT_ID, API_MASTER_ID),
  CONSTRAINT FK_HTH_UAAA_TO_ACCOUNT
    FOREIGN KEY (HTH_USER_ACCESS_ACCOUNT_ID)
    REFERENCES HTH_BEA.HTH_USER_ACCESS_ACCOUNT (ID),
  CONSTRAINT FK_HTH_UAAA_TO_API FOREIGN KEY (API_MASTER_ID)
    REFERENCES HTH_BEA.HTH_API_MASTER (ID),
  CONSTRAINT CK_HTH_UAAA_STATUS CHECK (OBJECT_STATUS IN ('A', 'I'))
);

-- Supports effective-grant lookup when an API master is disabled or reviewed.
CREATE INDEX HTH_BEA.IX_HTH_UAAA_API
  ON HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API (API_MASTER_ID);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API IS
  'Effective HTH API grants by HTH user account.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.ID IS
  'Unique identifier of the effective account API grant.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.HTH_USER_ACCESS_ACCOUNT_ID IS
  'Effective HTH account grant owning this API grant.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.API_MASTER_ID IS
  'References the active API definition in HTH_API_MASTER.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.OBJECT_STATUS IS
  'Record status: A for active or I for inactive.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.CREATED_BY IS
  'User who created the record.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.CREATION_DATE IS
  'Date and time when the record was created.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.LAST_UPDATED_BY IS
  'User who last updated the record.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API.LAST_UPDATE_DATE IS
  'Date and time when the record was last updated.';

COMMIT;
