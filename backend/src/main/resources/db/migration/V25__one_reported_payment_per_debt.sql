-- V25__one_reported_payment_per_debt.sql
-- 022 (T079): partial payments are allowed, so a debt can receive several CONFIRMED payments.
-- V18's uq_payments_active_debt allowed a single non-legacy REPORTED *or* CONFIRMED payment per
-- debt and would reject the second partial payment. The only thing the database has to
-- guarantee now is that at most one payment waits for an administrator's decision per debt
-- (plan.md D6). The `legacy` flag is just an origin marker and takes no part in the index.
-- No row is touched: legacy rows are all CONFIRMED (outside the new index) and the old index
-- already allowed at most one REPORTED payment per debt, so the new index cannot fail on data.
DROP INDEX uq_payments_active_debt;

CREATE UNIQUE INDEX uq_payments_reported_debt ON payments (debt_id)
    WHERE status = 'REPORTED';
