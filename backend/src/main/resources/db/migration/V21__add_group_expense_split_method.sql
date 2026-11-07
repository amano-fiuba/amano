-- V21__add_group_expense_split_method.sql
-- Data model: specs/022-group-debt-management/data-model.md (fuente de verdad, ddl-auto=validate)
-- 022 D2/T066: método de reparto del gasto. Los gastos existentes y los payloads
-- de la 017 que omiten splitMethod quedan en EQUAL.

ALTER TABLE group_expenses
    ADD COLUMN split_method VARCHAR(10) NOT NULL DEFAULT 'EQUAL';

ALTER TABLE group_expenses
    ADD CONSTRAINT ck_group_expenses_split_method
    CHECK (split_method IN ('EQUAL', 'CUSTOM', 'PERCENTAGE'));
