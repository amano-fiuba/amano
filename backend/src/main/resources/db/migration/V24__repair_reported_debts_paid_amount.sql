-- V24__repair_reported_debts_paid_amount.sql
-- 022 (T056): a debt in PAYMENT_REPORTED is NOT settled until an administrator confirms
-- the payment (plan.md D4), so it must keep contributing its outstanding amount to the
-- balances. Until now reporting a payment also set paid_amount = amount, which left
-- outstanding = 0 and dropped the debt from the balances. Reporting no longer touches
-- paid_amount (Debt.applyFullPayment); this repairs the rows written by the old behavior:
-- the paid amount goes back to what it was before the reported payment (0, or the legacy
-- 018 partial amount). Idempotent: it only touches reported debts that still look settled.
UPDATE debts d
SET paid_amount = d.amount - p.amount
FROM payments p
WHERE p.debt_id = d.id
  AND p.legacy = FALSE
  AND p.status = 'REPORTED'
  AND d.status = 'PAYMENT_REPORTED'
  AND d.paid_amount = d.amount;
