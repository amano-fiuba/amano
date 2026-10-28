-- V5__expense_recurrence.sql
-- Data model: specs/009-pause-recurring-expense/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-001..FR-009 and FR-015 (existing rows untouched).
-- Feature 009-pause-recurring-expense.
-- Sin DELETE ni UPDATE (regla del equipo): solo ALTER TABLE + CREATE INDEX. Las filas
-- de 007 quedan con las cuatro columnas en NULL, que todas las restricciones admiten.

ALTER TABLE expenses
    ADD COLUMN recurrence_frequency VARCHAR(10) NULL,
    ADD COLUMN recurrence_status    VARCHAR(10) NULL,
    ADD COLUMN recurrence_next_date DATE NULL,
    ADD COLUMN recurrence_source_id BIGINT NULL;

ALTER TABLE expenses
    ADD CONSTRAINT ck_expenses_recurrence_frequency
        CHECK (recurrence_frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    ADD CONSTRAINT ck_expenses_recurrence_status
        CHECK (recurrence_status IN ('ACTIVE', 'PAUSED', 'FINISHED')),
    -- Una serie tiene frecuencia y estado; cualquier otra fila, ninguno de los dos.
    ADD CONSTRAINT ck_expenses_recurrence_series
        CHECK ((recurrence_frequency IS NULL) = (recurrence_status IS NULL)),
    -- Series y repeticiones son siempre RECURRING; un ONE_TIME no recurre (FR-002).
    ADD CONSTRAINT ck_expenses_recurrence_type
        CHECK ((recurrence_frequency IS NULL OR expense_type = 'RECURRING')
           AND (recurrence_source_id IS NULL OR expense_type = 'RECURRING')),
    -- ACTIVE/PAUSED necesitan próxima fecha; FINISHED y las filas sin serie no (FR-009).
    ADD CONSTRAINT ck_expenses_recurrence_next_date
        CHECK (COALESCE(recurrence_status IN ('ACTIVE', 'PAUSED'), false)
               = (recurrence_next_date IS NOT NULL)),
    -- Una repetición nunca es a su vez una serie.
    ADD CONSTRAINT ck_expenses_recurrence_occurrence
        CHECK (recurrence_source_id IS NULL OR recurrence_frequency IS NULL),
    ADD CONSTRAINT fk_expenses_recurrence_source
        FOREIGN KEY (recurrence_source_id) REFERENCES expenses (id) ON DELETE SET NULL,
    -- A lo sumo una repetición por serie y fecha, aunque la generación corra dos veces (FR-006).
    ADD CONSTRAINT uq_expenses_recurrence_occurrence UNIQUE (recurrence_source_id, expense_date);

-- Generación diaria: series activas vencidas (FR-005).
CREATE INDEX ix_expenses_recurrence_due ON expenses (recurrence_next_date)
    WHERE recurrence_status = 'ACTIVE';

-- Pantalla "Gastos recurrentes": series del usuario, más recientes primero (FR-012).
CREATE INDEX ix_expenses_recurrence_owner ON expenses (user_id, created_at DESC)
    WHERE recurrence_frequency IS NOT NULL;
