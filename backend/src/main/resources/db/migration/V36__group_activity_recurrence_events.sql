-- V36__group_activity_recurrence_events.sql
-- 029 FR-018: agrega al historial de actividad los eventos de cambio de estado
-- de una serie recurrente de grupo (pausa, reanudación y finalización).

ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;
ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'EXPENSE_EDITED', 'EXPENSE_DELETED',
        'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED',
        'DEBT_REMINDER_SENT', 'PAYER_DUE_NOTICE_SENT',
        'RECURRENCE_PAUSED', 'RECURRENCE_RESUMED', 'RECURRENCE_FINISHED'));
