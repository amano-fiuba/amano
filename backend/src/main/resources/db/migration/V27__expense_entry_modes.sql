-- V27__expense_entry_modes.sql
-- Feature 027 (formas de registrar un gasto), ver specs/027-expense-entry-modes/data-model.md.
--
-- Parte 1 (US2, FR-014): gastos, deudas, pagos y preferencias aceptan las 32 monedas
-- del BCRA vigentes y con cotización (research.md R1), las mismas constantes que el
-- enum Currency. La moneda principal del grupo (ck_groups_primary_currency) no cambia.

ALTER TABLE expenses DROP CONSTRAINT ck_expenses_currency;
ALTER TABLE expenses ADD CONSTRAINT ck_expenses_currency CHECK (currency IN (
    'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
    'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
    'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR'));

ALTER TABLE group_expenses DROP CONSTRAINT ck_group_expenses_currency;
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_currency CHECK (currency IN (
    'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
    'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
    'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR'));

ALTER TABLE debts DROP CONSTRAINT ck_debts_currency;
ALTER TABLE debts ADD CONSTRAINT ck_debts_currency CHECK (currency IN (
    'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
    'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
    'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR'));

ALTER TABLE payments DROP CONSTRAINT ck_payments_currency;
ALTER TABLE payments ADD CONSTRAINT ck_payments_currency CHECK (currency IN (
    'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
    'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
    'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR'));

ALTER TABLE user_currencies DROP CONSTRAINT ck_user_currencies_code;
ALTER TABLE user_currencies ADD CONSTRAINT ck_user_currencies_code CHECK (currency_code IN (
    'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
    'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
    'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR'));
