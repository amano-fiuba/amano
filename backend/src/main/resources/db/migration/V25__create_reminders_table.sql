-- V25__create_reminders_table.sql
-- 025 T005: tabla única con discriminador para el recordatorio de una deuda
-- (DEBT) y el aviso al pagador de un gasto (PAYER_NOTICE). Comparten la
-- programación, el reclamo y el ciclo de vida.

CREATE TABLE reminders (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT       NOT NULL REFERENCES groups (id),
    kind        VARCHAR(20)  NOT NULL,
    debt_id     BIGINT       NULL REFERENCES debts (id),
    expense_id  BIGINT       NULL REFERENCES group_expenses (id),
    send_date   DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    claimed_at  TIMESTAMPTZ  NULL,
    sent_at     TIMESTAMPTZ  NULL,
    subject     TEXT         NULL,
    body        TEXT         NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reminders_kind
        CHECK (kind IN ('DEBT', 'PAYER_NOTICE')),
    CONSTRAINT ck_reminders_status
        CHECK (status IN ('SCHEDULED', 'SENT', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_reminders_target
        CHECK (
            (kind = 'DEBT' AND debt_id IS NOT NULL AND expense_id IS NULL)
            OR (kind = 'PAYER_NOTICE' AND expense_id IS NOT NULL AND debt_id IS NULL)
        ),
    CONSTRAINT ck_reminders_sent
        CHECK (
            status <> 'SENT'
            OR (sent_at IS NOT NULL AND subject IS NOT NULL AND body IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_reminders_debt
    ON reminders (debt_id)
    WHERE kind = 'DEBT';

CREATE UNIQUE INDEX uq_reminders_expense
    ON reminders (expense_id)
    WHERE kind = 'PAYER_NOTICE';

CREATE INDEX ix_reminders_due
    ON reminders (group_id, status, send_date);
