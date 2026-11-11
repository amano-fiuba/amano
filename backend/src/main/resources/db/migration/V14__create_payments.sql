-- V14__create_payments.sql
-- Data model: specs/018-register-payment/data-model.md (fuente de verdad, ddl-auto=validate)
-- Solo CREATE TABLE / CREATE INDEX (regla del equipo): sin modificar filas existentes.

CREATE TABLE payments (
    id                         BIGSERIAL PRIMARY KEY,
    group_id                   BIGINT        NOT NULL REFERENCES groups (id),
    debt_id                    BIGINT        NOT NULL REFERENCES debts (id),
    payer_type                 VARCHAR(10)   NOT NULL,
    payer_user_id              BIGINT        NULL REFERENCES users (id),
    payer_simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    payer_name                 VARCHAR(255)  NOT NULL,
    receiver_user_id           BIGINT        NOT NULL REFERENCES users (id),
    receiver_name              VARCHAR(255)  NOT NULL,
    registered_by_user_id      BIGINT        NOT NULL REFERENCES users (id),
    amount                     NUMERIC(11,2) NOT NULL,
    currency                   VARCHAR(3)    NOT NULL,
    payment_date               DATE          NOT NULL,
    idempotency_key            UUID          NOT NULL,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_payer_type CHECK (payer_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_payments_payer CHECK (
        (payer_type = 'REGISTERED' AND payer_user_id IS NOT NULL AND payer_simulated_profile_id IS NULL)
        OR (payer_type = 'SIMULATED' AND payer_user_id IS NULL)),
    CONSTRAINT ck_payments_not_self CHECK (payer_user_id IS NULL OR payer_user_id <> receiver_user_id),
    CONSTRAINT ck_payments_amount   CHECK (amount >= 0.01),
    CONSTRAINT ck_payments_currency CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT uq_payments_registered_by_idempotency_key UNIQUE (registered_by_user_id, idempotency_key)
);

CREATE INDEX ix_payments_debt  ON payments (debt_id, created_at);
CREATE INDEX ix_payments_group ON payments (group_id, created_at DESC);
