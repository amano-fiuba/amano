-- V35__group_expense_recurrence_anchor.sql
-- 029: anclaje del ciclo actual de la serie para poder reanudar "de 0" sin
-- mutar la fecha histórica del gasto original, y persistencia del origen de
-- cada ocurrencia (job vs reanudación) para el detalle de origen (FR-013).

-- Anclaje del ciclo de recurrencia. Backfill: el inicio del ciclo de las
-- series existentes es la fecha del gasto original, por lo que el
-- comportamiento vigente se conserva.
ALTER TABLE group_expenses
    ADD COLUMN recurrence_anchor_date DATE NULL;

UPDATE group_expenses
SET recurrence_anchor_date = expense_date
WHERE recurrence_frequency IS NOT NULL;

ALTER TABLE group_expenses
    ADD CONSTRAINT ck_group_expenses_recurrence_anchor
        CHECK ((recurrence_frequency IS NULL) = (recurrence_anchor_date IS NULL));

-- Origen de la ocurrencia: JOB para las generadas por el job, RESUME para las
-- creadas al reanudar. Backfill: todas las ocurrencias existentes provienen del job.
ALTER TABLE group_expenses
    ADD COLUMN recurrence_created_via VARCHAR(8) NULL;

UPDATE group_expenses
SET recurrence_created_via = 'JOB'
WHERE recurrence_source_id IS NOT NULL;

ALTER TABLE group_expenses
    ADD CONSTRAINT ck_group_expenses_recurrence_created_via
        CHECK ((recurrence_source_id IS NULL) = (recurrence_created_via IS NULL));
