-- V28__group_expense_classification.sql
-- Feature 027 (formas de registrar un gasto), ver specs/027-expense-entry-modes/data-model.md.
-- Va aparte de V27 porque V27 ya estaba aplicada (US2); una migración aplicada no se edita.
--
-- US3 (FR-020): el gasto de grupo guarda categoría, tipo de gasto y medio
-- de pago con los catálogos de 007 (mismos valores y tamaños que V4). Nullable: los
-- gastos anteriores a 027 no los tienen y no se inventan; el servicio los exige en
-- las altas nuevas.
ALTER TABLE group_expenses ADD COLUMN category VARCHAR(20) NULL;
ALTER TABLE group_expenses ADD COLUMN expense_type VARCHAR(20) NULL;
ALTER TABLE group_expenses ADD COLUMN payment_method VARCHAR(20) NULL;

ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_category CHECK (category IS NULL OR category IN
    ('FOOD', 'TRANSPORT', 'HOUSING', 'SERVICES', 'HEALTH', 'EDUCATION', 'LEISURE', 'SHOPPING', 'PETS', 'OTHER'));
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_type CHECK (expense_type IS NULL OR expense_type IN
    ('RECURRING', 'ONE_TIME'));
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_payment_method CHECK (payment_method IS NULL OR
    payment_method IN ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'TRANSFER', 'VIRTUAL_WALLET', 'OTHER'));
