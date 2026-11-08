-- V22__extend_group_activity_event_types_for_expense_edit_delete.sql
-- 022 Etapa 1B (T020/T021): agrega los eventos EXPENSE_EDITED y EXPENSE_DELETED
-- al historial de actividad del grupo.

ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;
ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'EXPENSE_EDITED', 'EXPENSE_DELETED',
        'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED'));
