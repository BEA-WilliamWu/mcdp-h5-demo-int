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
--   3. The environment-specific Maker and Checker grant scripts.
--   4. 3_HTH_User_Access_Process.sql.
--   5. 4_HTH_User_Access_Repository_Adapters.sql.
--   6. 5_HTH_User_Access_Error_Messages.sql.
--   7. 6_HTH_User_Access_Verification.sql.
--
-- This is a one-time DDL script. Do not re-run after the objects have been created.
-- Oracle DDL commits implicitly and cannot be rolled back as one transaction. Before business data
-- exists, back out by dropping child tables in this order: HTH_USER_ACCESS_REQ_API,
-- HTH_USER_ACCESS_REQ_ACCOUNT, HTH_USER_ACCESS_REQUEST, HTH_USER_ACCESS_ACCOUNT_API,
-- HTH_USER_ACCESS_ACCOUNT. After data exists, use an approved migration; never drop these tables.
--
-- Runtime ownership note:
--   HTH_USER_ACCESS_ACCOUNT and HTH_USER_ACCESS_ACCOUNT_API are the effective grant tables.
--   The three HTH_USER_ACCESS_REQUEST* tables below are retained only for compatibility with
--   environments that executed the original schema. The final application uses the platform
--   DIGX_AP_TRANSACTION transactionSnapshot for maker/checker payload, pending status, duplicate
--   detection, and checker re-entry; it does not write or read the legacy request tables.

-- Effective Current and Savings or Time Deposit grants by HTH user and account-owning party.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_ACCOUNT (
  ID                    VARCHAR2(36 BYTE)  NOT NULL,
  PARTY_ID              VARCHAR2(64 BYTE)  NOT NULL,
  CLOSE_ID              VARCHAR2(255 BYTE) NOT NULL,
  ACCESS_PARTY_ID       VARCHAR2(64 BYTE)  NOT NULL,
  LINKAGE_TYPE          VARCHAR2(16 BYTE)  NOT NULL,
  ACCOUNT_NUMBER        VARCHAR2(64 BYTE)  NOT NULL,
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
  'Canonical unmasked account identifier; must not be logged in plain text.';
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

-- Legacy request header retained for schema/deployment compatibility. Runtime approval payloads
-- are stored in DIGX_AP_TRANSACTION.transactionSnapshot instead.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_REQUEST (
  ID                    VARCHAR2(36 BYTE)  NOT NULL,
  TRANSACTION_ID        VARCHAR2(64 BYTE)  NOT NULL,
  REFERENCE_NO          VARCHAR2(64 BYTE)  NOT NULL,
  ACTION_TYPE           VARCHAR2(16 BYTE)  NOT NULL,
  PARTY_ID              VARCHAR2(64 BYTE)  NOT NULL,
  CLOSE_ID              VARCHAR2(255 BYTE) NOT NULL,
  ACCESS_PARTY_ID       VARCHAR2(64 BYTE)  NOT NULL,
  LINKAGE_TYPE          VARCHAR2(16 BYTE)  NOT NULL,
  USER_NAME             VARCHAR2(255 BYTE),
  FULL_NAME             VARCHAR2(255 BYTE),
  ACCESS_PARTY_NAME     VARCHAR2(255 BYTE),
  OBJECT_STATUS         VARCHAR2(1 BYTE)   DEFAULT 'A' NOT NULL,
  CREATED_BY            VARCHAR2(255 BYTE) NOT NULL,
  CREATION_DATE         DATE               DEFAULT SYSDATE NOT NULL,
  LAST_UPDATED_BY       VARCHAR2(255 BYTE) NOT NULL,
  LAST_UPDATE_DATE      DATE               DEFAULT SYSDATE NOT NULL,
  CONSTRAINT PK_HTH_UA_REQUEST PRIMARY KEY (ID),
  CONSTRAINT UK_HTH_UAR_TXN UNIQUE (TRANSACTION_ID),
  CONSTRAINT UK_HTH_UAR_REF UNIQUE (REFERENCE_NO),
  CONSTRAINT CK_HTH_UAR_ACTION CHECK
    (ACTION_TYPE IN ('CREATE', 'EDIT', 'DELETE')),
  CONSTRAINT CK_HTH_UAR_LINKAGE CHECK
    (LINKAGE_TYPE IN ('RELATED', 'ASSOCIATED')),
  CONSTRAINT CK_HTH_UAR_STATUS CHECK
    (OBJECT_STATUS IN ('A', 'I'))
);

-- Legacy compatibility index; the final runtime uses the platform entity identifier and workflow.
CREATE INDEX HTH_BEA.IX_HTH_UAR_CONTEXT
  ON HTH_BEA.HTH_USER_ACCESS_REQUEST
    (PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_REQUEST IS
  'Legacy HTH approval request header retained for compatibility; runtime uses the platform transaction snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.ID IS
  'Unique identifier of the approval request snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.TRANSACTION_ID IS
  'References the OBDX approval transaction identifier.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.REFERENCE_NO IS
  'Business reference displayed in approval and audit screens.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.ACTION_TYPE IS
  'Requested operation: CREATE, EDIT, or DELETE.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.PARTY_ID IS
  'Primary corporate party that owns the HTH user.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.CLOSE_ID IS
  'HTH user CloseID captured when the request was submitted.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.ACCESS_PARTY_ID IS
  'Party owning the accounts captured in the request.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.LINKAGE_TYPE IS
  'Company relationship type: RELATED or ASSOCIATED.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.USER_NAME IS
  'HTH user login name snapshot shown during approval.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.FULL_NAME IS
  'HTH user full name snapshot shown during approval.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.ACCESS_PARTY_NAME IS
  'Account-owning party name snapshot shown during approval.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.OBJECT_STATUS IS
  'Record status: A for active or I for inactive.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.CREATED_BY IS
  'User who created the request snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.CREATION_DATE IS
  'Date and time when the request snapshot was created.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.LAST_UPDATED_BY IS
  'User who last updated the request snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQUEST.LAST_UPDATE_DATE IS
  'Date and time when the request snapshot was last updated.';

-- Legacy request-account snapshot retained for schema/deployment compatibility.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT (
  ID                         VARCHAR2(36 BYTE)  NOT NULL,
  HTH_USER_ACCESS_REQUEST_ID VARCHAR2(36 BYTE)  NOT NULL,
  ACCOUNT_NUMBER             VARCHAR2(64 BYTE)  NOT NULL,
  ACCOUNT_TYPE               VARCHAR2(8 BYTE)   NOT NULL,
  CURRENCY                   VARCHAR2(3 BYTE),
  DISPLAY_ORDER              NUMBER,
  OBJECT_STATUS              VARCHAR2(1 BYTE)   DEFAULT 'A' NOT NULL,
  CREATED_BY                 VARCHAR2(255 BYTE) NOT NULL,
  CREATION_DATE              DATE               DEFAULT SYSDATE NOT NULL,
  LAST_UPDATED_BY            VARCHAR2(255 BYTE) NOT NULL,
  LAST_UPDATE_DATE           DATE               DEFAULT SYSDATE NOT NULL,
  CONSTRAINT PK_HTH_UAR_ACCOUNT PRIMARY KEY (ID),
  CONSTRAINT UK_HTH_UAR_ACCOUNT UNIQUE
    (HTH_USER_ACCESS_REQUEST_ID, ACCOUNT_TYPE, ACCOUNT_NUMBER),
  CONSTRAINT FK_HTH_UARA_TO_REQUEST
    FOREIGN KEY (HTH_USER_ACCESS_REQUEST_ID)
    REFERENCES HTH_BEA.HTH_USER_ACCESS_REQUEST (ID),
  CONSTRAINT CK_HTH_UARA_TYPE CHECK (ACCOUNT_TYPE IN ('CSA', 'TD')),
  CONSTRAINT CK_HTH_UARA_STATUS CHECK (OBJECT_STATUS IN ('A', 'I'))
);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT IS
  'Legacy HTH request account snapshot retained for compatibility; final runtime does not read or write this table.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.ID IS
  'Unique identifier of the request account snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.HTH_USER_ACCESS_REQUEST_ID IS
  'Approval request header owning this account snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.ACCOUNT_NUMBER IS
  'Canonical unmasked account identifier captured at submit time.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.ACCOUNT_TYPE IS
  'Account type captured at submit time: CSA for Current and Savings or TD for Time Deposit.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.CURRENCY IS
  'Account currency snapshot captured at submit time.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.DISPLAY_ORDER IS
  'Original account order submitted by the maker.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.OBJECT_STATUS IS
  'Record status: A for active or I for inactive.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.CREATED_BY IS
  'User who created the account snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.CREATION_DATE IS
  'Date and time when the account snapshot was created.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.LAST_UPDATED_BY IS
  'User who last updated the account snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT.LAST_UPDATE_DATE IS
  'Date and time when the account snapshot was last updated.';

-- Legacy request-API snapshot retained for schema/deployment compatibility.
CREATE TABLE HTH_BEA.HTH_USER_ACCESS_REQ_API (
  ID                         VARCHAR2(36 BYTE)  NOT NULL,
  HTH_USER_ACCESS_REQ_ACC_ID VARCHAR2(36 BYTE)  NOT NULL,
  API_MASTER_ID              VARCHAR2(36 BYTE)  NOT NULL,
  API_CODE                   VARCHAR2(64 BYTE)  NOT NULL,
  API_NAME                   VARCHAR2(255 BYTE) NOT NULL,
  DISPLAY_ORDER              NUMBER,
  OBJECT_STATUS              VARCHAR2(1 BYTE)   DEFAULT 'A' NOT NULL,
  CREATED_BY                 VARCHAR2(255 BYTE) NOT NULL,
  CREATION_DATE              DATE               DEFAULT SYSDATE NOT NULL,
  LAST_UPDATED_BY            VARCHAR2(255 BYTE) NOT NULL,
  LAST_UPDATE_DATE           DATE               DEFAULT SYSDATE NOT NULL,
  CONSTRAINT PK_HTH_UAR_API PRIMARY KEY (ID),
  CONSTRAINT UK_HTH_UAR_API UNIQUE
    (HTH_USER_ACCESS_REQ_ACC_ID, API_MASTER_ID),
  CONSTRAINT FK_HTH_UARA_TO_ACCOUNT
    FOREIGN KEY (HTH_USER_ACCESS_REQ_ACC_ID)
    REFERENCES HTH_BEA.HTH_USER_ACCESS_REQ_ACCOUNT (ID),
  CONSTRAINT FK_HTH_UARA_TO_API FOREIGN KEY (API_MASTER_ID)
    REFERENCES HTH_BEA.HTH_API_MASTER (ID),
  CONSTRAINT CK_HTH_UARAPI_STATUS CHECK (OBJECT_STATUS IN ('A', 'I'))
);

-- Legacy compatibility index; final approval revalidation uses the platform transaction snapshot.
CREATE INDEX HTH_BEA.IX_HTH_UARAPI_API
  ON HTH_BEA.HTH_USER_ACCESS_REQ_API (API_MASTER_ID);

COMMENT ON TABLE HTH_BEA.HTH_USER_ACCESS_REQ_API IS
  'Legacy HTH request API snapshot retained for compatibility; final runtime does not read or write this table.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.ID IS
  'Unique identifier of the request API snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.HTH_USER_ACCESS_REQ_ACC_ID IS
  'Request account snapshot owning this API snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.API_MASTER_ID IS
  'API master identifier captured for validation and activation.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.API_CODE IS
  'Copied at submit time for historical review.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.API_NAME IS
  'Copied at submit time so later master renames do not change history.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.DISPLAY_ORDER IS
  'Original API order submitted by the maker.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.OBJECT_STATUS IS
  'Record status: A for active or I for inactive.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.CREATED_BY IS
  'User who created the API snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.CREATION_DATE IS
  'Date and time when the API snapshot was created.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.LAST_UPDATED_BY IS
  'User who last updated the API snapshot.';
COMMENT ON COLUMN HTH_BEA.HTH_USER_ACCESS_REQ_API.LAST_UPDATE_DATE IS
  'Date and time when the API snapshot was last updated.';

COMMIT;
