-- V28__extend_group_activity_event_types_for_reminders.sql
-- 025 T006: agrega los eventos de recordatorio de deuda y aviso al pagador al
-- historial de actividad del grupo. La lista parte del CHECK vigente de V22.

ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;
ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'EXPENSE_EDITED', 'EXPENSE_DELETED',
        'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED',
        'DEBT_REMINDER_SENT', 'PAYER_DUE_NOTICE_SENT'));
