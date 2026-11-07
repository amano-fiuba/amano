-- V17__extend_groups_for_debt_management.sql
-- Data model: specs/022-group-debt-management/data-model.md (fuente de verdad, ddl-auto=validate)
-- 022 D3/D7: moneda principal y umbral de vencimiento por grupo.

-- Grupos existentes: no hay otra fuente de la que inferir la moneda, así que se
-- adopta ARS de forma explícita (decisión de la feature 022).
ALTER TABLE groups ADD COLUMN primary_currency VARCHAR(3) NOT NULL DEFAULT 'ARS';

-- NULL = la deuda nunca vence. La columna se agrega una sola vez (esta migración).
ALTER TABLE groups ADD COLUMN debt_due_days INTEGER NULL;

ALTER TABLE groups ADD CONSTRAINT ck_groups_primary_currency
    CHECK (primary_currency IN ('ARS', 'USD', 'EUR'));

ALTER TABLE groups ADD CONSTRAINT ck_groups_debt_due_days
    CHECK (debt_due_days IS NULL OR debt_due_days >= 0);
