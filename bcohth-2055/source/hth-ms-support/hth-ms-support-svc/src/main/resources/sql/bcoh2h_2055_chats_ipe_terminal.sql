-- BCOH2H-2055: deploy this additive migration BEFORE the application update.
-- Run with privileges to alter HTH_BEA.HTH_PAYMENT_PAYOUT.
-- NULL = terminal state not supplied/legacy row; 0 = non-terminal; 1 = terminal.
-- No existing rows are backfilled: SUCCESS alone does not establish finality.
-- Re-running skips only an already-existing column/constraint.

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE HTH_BEA.HTH_PAYMENT_PAYOUT ADD (IPE_TERMINAL NUMBER(1))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN -- ORA-01430: column already exists
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE HTH_BEA.HTH_PAYMENT_PAYOUT ADD CONSTRAINT CK_HTH_PAYOUT_IPE_TERMINAL CHECK (IPE_TERMINAL IN (0, 1))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -2264 THEN -- ORA-02264: constraint name already used
      RAISE;
    END IF;
END;
/

COMMENT ON COLUMN HTH_BEA.HTH_PAYMENT_PAYOUT.IPE_TERMINAL IS
  'IPE result finality: 0=non-terminal, 1=terminal, NULL=unknown/legacy';

-- Application rollback: deploy the previous application first and retain this
-- nullable column. Do not drop it while any updated application instance runs.
