-- V13__create_group_expenses_and_debts.sql
-- Data model: specs/017-debt-states/data-model.md (fuente de verdad, ddl-auto=validate)
-- Solo CREATE TABLE / CREATE INDEX (regla del equipo): sin UPDATE ni DELETE.

CREATE TABLE group_expenses (
    id                 BIGSERIAL PRIMARY KEY,
    group_id           BIGINT        NOT NULL REFERENCES groups (id),
    created_by_user_id BIGINT        NOT NULL REFERENCES users (id),
    payer_user_id      BIGINT        NOT NULL REFERENCES users (id),
    amount             NUMERIC(11,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    expense_date       DATE          NOT NULL,
    description        VARCHAR(200)  NULL,
    idempotency_key    UUID          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_group_expenses_amount      CHECK (amount >= 0.01),
    CONSTRAINT ck_group_expenses_currency    CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_group_expenses_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_group_expenses_creator_idempotency_key UNIQUE (created_by_user_id, idempotency_key)
);

CREATE TABLE group_expense_shares (
    id                   BIGSERIAL PRIMARY KEY,
    expense_id           BIGINT        NOT NULL REFERENCES group_expenses (id),
    position             SMALLINT      NOT NULL,
    participant_type     VARCHAR(10)   NOT NULL,
    user_id              BIGINT        NULL REFERENCES users (id),
    simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    participant_name     VARCHAR(255)  NOT NULL,
    amount               NUMERIC(11,2) NOT NULL,
    CONSTRAINT ck_group_expense_shares_position CHECK (position >= 0),
    CONSTRAINT ck_group_expense_shares_type     CHECK (participant_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_group_expense_shares_participant CHECK (
        (participant_type = 'REGISTERED' AND user_id IS NOT NULL AND simulated_profile_id IS NULL)
        OR (participant_type = 'SIMULATED' AND user_id IS NULL)),
    CONSTRAINT ck_group_expense_shares_amount   CHECK (amount >= 0.01),
    CONSTRAINT uq_group_expense_shares_position UNIQUE (expense_id, position),
    CONSTRAINT uq_group_expense_shares_user     UNIQUE (expense_id, user_id),
    CONSTRAINT uq_group_expense_shares_profile  UNIQUE (expense_id, simulated_profile_id)
);

CREATE TABLE debts (
    id                          BIGSERIAL PRIMARY KEY,
    group_id                    BIGINT        NOT NULL REFERENCES groups (id),
    expense_id                  BIGINT        NOT NULL REFERENCES group_expenses (id),
    share_id                    BIGINT        NOT NULL REFERENCES group_expense_shares (id),
    debtor_type                 VARCHAR(10)   NOT NULL,
    debtor_user_id              BIGINT        NULL REFERENCES users (id),
    debtor_simulated_profile_id BIGINT        NULL REFERENCES simulated_profiles (id) ON DELETE SET NULL,
    debtor_name                 VARCHAR(255)  NOT NULL,
    creditor_user_id            BIGINT        NOT NULL REFERENCES users (id),
    currency                    VARCHAR(3)    NOT NULL,
    amount                      NUMERIC(11,2) NOT NULL,
    paid_amount                 NUMERIC(11,2) NOT NULL DEFAULT 0,
    status                      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_debts_share       UNIQUE (share_id),
    CONSTRAINT ck_debts_debtor_type CHECK (debtor_type IN ('REGISTERED', 'SIMULATED')),
    CONSTRAINT ck_debts_debtor CHECK (
        (debtor_type = 'REGISTERED' AND debtor_user_id IS NOT NULL AND debtor_simulated_profile_id IS NULL)
        OR (debtor_type = 'SIMULATED' AND debtor_user_id IS NULL)),
    CONSTRAINT ck_debts_not_self    CHECK (debtor_user_id IS NULL OR debtor_user_id <> creditor_user_id),
    CONSTRAINT ck_debts_currency    CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_debts_amount      CHECK (amount >= 0.01),
    CONSTRAINT ck_debts_paid_range  CHECK (paid_amount >= 0 AND paid_amount <= amount),
    CONSTRAINT ck_debts_status      CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID')),
    CONSTRAINT ck_debts_status_matches_paid CHECK (
        (status = 'PENDING'           AND paid_amount = 0)
        OR (status = 'PARTIALLY_PAID' AND paid_amount > 0 AND paid_amount < amount)
        OR (status = 'PAID'           AND paid_amount = amount))
);

CREATE INDEX ix_group_expenses_group         ON group_expenses (group_id, created_at DESC);
CREATE INDEX ix_group_expense_shares_expense ON group_expense_shares (expense_id);
CREATE INDEX ix_debts_expense                ON debts (expense_id);
CREATE INDEX ix_debts_open_debtor_user       ON debts (group_id, debtor_user_id)              WHERE status <> 'PAID';
CREATE INDEX ix_debts_open_debtor_profile    ON debts (group_id, debtor_simulated_profile_id) WHERE status <> 'PAID';
CREATE INDEX ix_debts_open_creditor          ON debts (group_id, creditor_user_id)            WHERE status <> 'PAID';
