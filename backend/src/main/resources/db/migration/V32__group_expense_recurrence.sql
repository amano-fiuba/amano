-- V32__group_expense_recurrence.sql
-- Recurrencia de gastos compartidos: misma semántica que V5 para los gastos
-- personales (009), pero en group_expenses. Una serie guarda frecuencia, estado
-- y próxima fecha; cada repetición apunta a su serie con recurrence_source_id.
-- Las filas existentes quedan con las cuatro columnas en NULL.

ALTER TABLE group_expenses
    ADD COLUMN recurrence_frequency VARCHAR(10) NULL,
    ADD COLUMN recurrence_status    VARCHAR(10) NULL,
    ADD COLUMN recurrence_next_date DATE NULL,
    ADD COLUMN recurrence_source_id BIGINT NULL;

ALTER TABLE group_expenses
    ADD CONSTRAINT ck_group_expenses_recurrence_frequency
        CHECK (recurrence_frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    ADD CONSTRAINT ck_group_expenses_recurrence_status
        CHECK (recurrence_status IN ('ACTIVE', 'PAUSED', 'FINISHED')),
    ADD CONSTRAINT ck_group_expenses_recurrence_series
        CHECK ((recurrence_frequency IS NULL) = (recurrence_status IS NULL)),
    ADD CONSTRAINT ck_group_expenses_recurrence_type
        CHECK ((recurrence_frequency IS NULL OR expense_type = 'RECURRING')
           AND (recurrence_source_id IS NULL OR expense_type = 'RECURRING')),
    ADD CONSTRAINT ck_group_expenses_recurrence_next_date
        CHECK (COALESCE(recurrence_status IN ('ACTIVE', 'PAUSED'), false)
               = (recurrence_next_date IS NOT NULL)),
    ADD CONSTRAINT ck_group_expenses_recurrence_occurrence
        CHECK (recurrence_source_id IS NULL OR recurrence_frequency IS NULL),
    ADD CONSTRAINT fk_group_expenses_recurrence_source
        FOREIGN KEY (recurrence_source_id) REFERENCES group_expenses (id) ON DELETE SET NULL,
    ADD CONSTRAINT uq_group_expenses_recurrence_occurrence
        UNIQUE (recurrence_source_id, expense_date);

CREATE INDEX ix_group_expenses_recurrence_due ON group_expenses (recurrence_next_date)
    WHERE recurrence_status = 'ACTIVE';
