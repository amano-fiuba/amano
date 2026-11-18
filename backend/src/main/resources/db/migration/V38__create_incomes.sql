-- V38__create_incomes.sql
-- Feature 032-income-management (specs/032-income-management/data-model.md).
-- Ingresos personales inmutables: la aplicación solo inserta y consulta.
-- Sin DELETE (regla del equipo): solo CREATE TABLE.

CREATE TABLE incomes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    amount          NUMERIC(11,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    income_date     DATE NOT NULL,
    category        VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    description     VARCHAR(200) NULL,
    idempotency_key UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_incomes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_incomes_amount CHECK (amount >= 0.01),
    CONSTRAINT ck_incomes_currency CHECK (currency IN (
        'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
        'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
        'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR')),
    CONSTRAINT ck_incomes_category CHECK (category IN
        ('SALARY','FREELANCE','SALE','REFUND','OTHER')),
    CONSTRAINT ck_incomes_payment_method CHECK (payment_method IN
        ('CASH','TRANSFER','VIRTUAL_WALLET','OTHER')),
    CONSTRAINT ck_incomes_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_incomes_user_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_incomes_user_date ON incomes (user_id, income_date DESC, id DESC);
