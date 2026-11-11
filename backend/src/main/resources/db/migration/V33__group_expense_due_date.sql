-- V33__group_expense_due_date.sql
-- Calendario de vencimientos de gastos compartidos (028 US3).
-- El vencimiento se persiste en el gasto para que no se recalcule cuando
-- cambie groups.debt_due_days (FR-016 / FR-019).
--
-- Backfill: las ocurrencias generadas de una serie vencen en su propia fecha;
-- el resto de los gastos de grupo usa expense_date + debt_due_days vigente al
-- momento de la migración. Los gastos con amigo y los grupos sin plazo quedan
-- en NULL.

ALTER TABLE group_expenses
    ADD COLUMN due_date DATE NULL;

UPDATE group_expenses e
SET due_date = CASE
        WHEN e.recurrence_source_id IS NOT NULL THEN e.expense_date
        ELSE e.expense_date + g.debt_due_days
    END
FROM groups g
WHERE e.group_id = g.id
  AND g.debt_due_days IS NOT NULL;
