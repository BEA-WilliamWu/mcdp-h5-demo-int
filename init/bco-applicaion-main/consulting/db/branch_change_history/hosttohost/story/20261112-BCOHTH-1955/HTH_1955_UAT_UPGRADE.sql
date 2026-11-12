-- Historical Code-reservation column upgrade. Existing UAT only; run ONCE if the column is absent.
-- hth_onboarding.sql already includes it. This script does not depend on any retired password table.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET VERIFY OFF
ALTER TABLE HTH_BEA.HTH_API_PASSWORD_CODE ADD (REMOTE_CLIENT_ID VARCHAR2(64 BYTE));
-- Historical request rows with no confirmed DSP client binding cannot be replayed as successes.
-- Reconcile any IN_PROGRESS/UNKNOWN reservations with DSP before reusing affected users.
-- The new local status columns are in HTH_1955_LOCAL_STATE_UPGRADE.sql; apply that once as well.
-- Transport reuses the original BM DSPApiAdapterImplConfig / DSP.SERVICE_URL, APIC_CLIENT_ID, APIC_CLIENT_SECRET, CHANNEL.
