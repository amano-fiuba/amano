-- V18__extend_debts_and_payments_for_debt_management.sql
-- Data model: specs/022-group-debt-management/data-model.md (fuente de verdad, ddl-auto=validate)
-- 022 D3/D6: seis estados de deuda y extensión de la tabla payments existente.

-- --- debts: nuevos estados -------------------------------------------------
-- PARTIALLY_PAID se conserva para poder leer las deudas de la 018; la 022 nunca
-- lo produce. Se elimina y recrea el CHECK de estados.
ALTER TABLE debts DROP CONSTRAINT ck_debts_status;
ALTER TABLE debts ADD CONSTRAINT ck_debts_status
    CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAYMENT_REPORTED', 'PAID', 'VERIFIED', 'OVERDUE'));

-- La restricción vieja solo conocía PENDING/PARTIALLY_PAID/PAID y rechazaría los
-- estados nuevos. Se recrea admitiendo PAYMENT_REPORTED y VERIFIED sin romper las
-- filas PARTIALLY_PAID existentes.
ALTER TABLE debts DROP CONSTRAINT ck_debts_status_matches_paid;
ALTER TABLE debts ADD CONSTRAINT ck_debts_status_matches_paid CHECK (
    (status = 'PENDING'             AND paid_amount = 0)
    OR (status = 'PARTIALLY_PAID'   AND paid_amount > 0 AND paid_amount < amount)
    OR (status = 'PAYMENT_REPORTED' AND paid_amount >= 0 AND paid_amount <= amount)
    OR (status = 'PAID'             AND paid_amount = amount)
    OR (status = 'VERIFIED'         AND paid_amount = amount)
    OR (status = 'OVERDUE'          AND paid_amount = 0));

-- Los índices de deudas abiertas deben excluir también VERIFIED (saldada).
DROP INDEX ix_debts_open_debtor_user;
DROP INDEX ix_debts_open_debtor_profile;
DROP INDEX ix_debts_open_creditor;

CREATE INDEX ix_debts_open_debtor_user
    ON debts (group_id, debtor_user_id)
    WHERE status NOT IN ('PAID', 'VERIFIED');
CREATE INDEX ix_debts_open_debtor_profile
    ON debts (group_id, debtor_simulated_profile_id)
    WHERE status NOT IN ('PAID', 'VERIFIED');
CREATE INDEX ix_debts_open_creditor
    ON debts (group_id, creditor_user_id)
    WHERE status NOT IN ('PAID', 'VERIFIED');

-- --- payments: extensión de la tabla existente (V14) -----------------------
-- No se crea otra tabla ni otra entidad: la 018 sigue usando esta misma.
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE payments ADD COLUMN decided_by_user_id BIGINT NULL REFERENCES users (id);
ALTER TABLE payments ADD COLUMN decided_at TIMESTAMPTZ NULL;

-- Los pagos existentes quedan CONFIRMED y marcados como legacy. El DEFAULT TRUE
-- solo aplica a filas escritas fuera del flujo nuevo; después se deja en FALSE.
ALTER TABLE payments ADD COLUMN legacy BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE payments SET status = 'CONFIRMED', legacy = TRUE;
ALTER TABLE payments ALTER COLUMN legacy SET DEFAULT FALSE;

ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING', 'REPORTED', 'CONFIRMED', 'REJECTED'));

ALTER TABLE payments ADD CONSTRAINT ck_payments_decision
    CHECK ((decided_by_user_id IS NULL AND decided_at IS NULL)
        OR (decided_by_user_id IS NOT NULL AND decided_at IS NOT NULL));

-- Un solo pago activo por deuda. Los legacy (incluidos los pagos parciales de la
-- 018) quedan fuera del índice parcial, por eso pueden coexistir varios.
CREATE UNIQUE INDEX uq_payments_active_debt ON payments (debt_id)
    WHERE legacy = FALSE AND status IN ('REPORTED', 'CONFIRMED');
