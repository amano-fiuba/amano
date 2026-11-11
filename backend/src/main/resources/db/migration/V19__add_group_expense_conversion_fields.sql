-- V19__add_group_expense_conversion_fields.sql
-- Data model: specs/022-group-debt-management/data-model.md (fuente de verdad, ddl-auto=validate)
-- 022 D8: cotización histórica guardada en el gasto. No hay tasa por defecto ni
-- carga manual: si no hay cotización, el gasto se rechaza con 503 y no se guarda.

ALTER TABLE group_expenses ADD COLUMN converted_amount NUMERIC(11,2) NULL;
ALTER TABLE group_expenses ADD COLUMN exchange_rate NUMERIC(18,8) NULL;
ALTER TABLE group_expenses ADD COLUMN rate_date DATE NULL;
ALTER TABLE group_expenses ADD COLUMN rate_source VARCHAR(40) NULL;

-- Relleno de los gastos cuya moneda ya coincide con la del grupo: tasa 1, sin
-- llamada externa. Los gastos de otra moneda quedan NULL hasta que la feature 022
-- implemente la conversión; no se inventa una tasa ni se usa la moneda original.
UPDATE group_expenses e
SET converted_amount = e.amount,
    exchange_rate = 1,
    rate_date = e.expense_date,
    rate_source = 'SAME_CURRENCY'
FROM groups g
WHERE e.group_id = g.id
  AND e.currency = g.primary_currency;
