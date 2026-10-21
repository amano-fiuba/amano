-- V4__create_expenses.sql
-- Data model: specs/007-register-expense/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-003, FR-006, FR-009, FR-010..FR-012, FR-014, FR-023, FR-025, FR-027.
-- Feature 007-register-expense.
-- Sin DELETE (regla del equipo): solo CREATE TABLE.

CREATE TABLE expenses (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    amount          NUMERIC(11,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    expense_date    DATE NOT NULL,
    category        VARCHAR(20) NOT NULL,
    expense_type    VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    description     VARCHAR(200) NULL,
    idempotency_key UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_expenses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_expenses_amount CHECK (amount >= 0.01),
    CONSTRAINT ck_expenses_currency CHECK (currency IN ('ARS', 'USD', 'EUR')),
    CONSTRAINT ck_expenses_category CHECK (category IN
        ('FOOD','TRANSPORT','HOUSING','SERVICES','HEALTH','EDUCATION','LEISURE','SHOPPING','PETS','OTHER')),
    CONSTRAINT ck_expenses_type CHECK (expense_type IN ('RECURRING', 'ONE_TIME')),
    CONSTRAINT ck_expenses_payment_method CHECK (payment_method IN
        ('CASH','DEBIT_CARD','CREDIT_CARD','TRANSFER','VIRTUAL_WALLET','OTHER')),
    CONSTRAINT ck_expenses_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_expenses_user_idempotency_key UNIQUE (user_id, idempotency_key)
);
