-- V37__create_debt_whistles.sql
-- 030 (Whistle): registro de cada aviso manual que el acreedor le manda al
-- deudor de una deuda de grupo. Es la base del límite de 1 silbido por par
-- (grupo, acreedor, deudor) cada 24 h (FR-007/FR-008). Tabla nueva y vacía:
-- no hace falta backfill (NFR-004, sin UPDATE ni DELETE de datos).

CREATE TABLE debt_whistles (
    id               BIGSERIAL PRIMARY KEY,
    group_id         BIGINT       NOT NULL REFERENCES groups (id),
    debt_id          BIGINT       NOT NULL REFERENCES debts (id),
    creditor_user_id BIGINT       NOT NULL REFERENCES users (id),
    debtor_user_id   BIGINT       NOT NULL REFERENCES users (id),
    status           VARCHAR(10)  NOT NULL DEFAULT 'SENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ  NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_debt_whistles_status
        CHECK (status IN ('SENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_debt_whistles_sent
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);

-- Consulta del límite: último intento SENDING/SENT del par en la ventana.
CREATE INDEX ix_debt_whistles_pair
    ON debt_whistles (group_id, creditor_user_id, debtor_user_id, created_at DESC);

-- 030 FR-011: evento DEBT_WHISTLED en el historial (patrón de V36).
ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;
ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'EXPENSE_EDITED', 'EXPENSE_DELETED',
        'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED',
        'DEBT_REMINDER_SENT', 'PAYER_DUE_NOTICE_SENT',
        'RECURRENCE_PAUSED', 'RECURRENCE_RESUMED', 'RECURRENCE_FINISHED',
        'DEBT_WHISTLED'));
