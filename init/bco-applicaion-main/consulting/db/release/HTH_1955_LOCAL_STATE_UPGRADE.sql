-- Existing UAT only: run ONCE before deploying CM. No stored procedures; no password/hash columns.
-- Fresh production uses HTH_FIRST_DEPLOY.sql instead. Oracle DDL commits; take a backup first.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET VERIFY OFF
ALTER TABLE HTH_BEA.HTH_USER_PROFILE ADD (
  API_PASSWORD_STATE VARCHAR2(16 BYTE) DEFAULT 'UNKNOWN' NOT NULL,
  API_PASSWORD_CLIENT_ID VARCHAR2(64 BYTE),
  API_PASSWORD_REQUEST_ID VARCHAR2(36 BYTE),
  API_PASSWORD_UPDATED_AT TIMESTAMP,
  CONSTRAINT CK_HTH_USER_PWD_STATE CHECK (API_PASSWORD_STATE IN ('NOT_SETUP','ACTIVE','IN_PROGRESS','UNKNOWN'))
);
-- Existing rows stay UNKNOWN; only newly created profiles default to NOT_SETUP.
ALTER TABLE HTH_BEA.HTH_USER_PROFILE MODIFY (API_PASSWORD_STATE DEFAULT 'NOT_SETUP');
-- Reconcile existing profiles against DSP before enabling setup/reset for them.
-- Verified active DSP credential: set ACTIVE and API_PASSWORD_CLIENT_ID to its actual business clientId.
-- Verified no DSP credential: set NOT_SETUP and bind the current business clientId.
-- Do not infer status from USED Code history or blindly mark all existing profiles NOT_SETUP.
SELECT PARTY_ID, CLOSE_ID, API_PASSWORD_STATE, API_PASSWORD_CLIENT_ID, API_PASSWORD_UPDATED_AT
FROM HTH_BEA.HTH_USER_PROFILE ORDER BY PARTY_ID, CLOSE_ID;
