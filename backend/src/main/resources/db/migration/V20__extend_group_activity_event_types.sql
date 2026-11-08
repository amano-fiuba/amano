-- V20__extend_group_activity_event_types.sql
-- Data model: specs/022-group-debt-management/data-model.md (fuente de verdad, ddl-auto=validate)
-- 022 US8 (T046-T049): agrega los eventos de liquidación y de transiciones de estado
-- de deuda y pago al historial de actividad del grupo (Q7).

ALTER TABLE group_activity DROP CONSTRAINT group_activity_event_type_check;
ALTER TABLE group_activity ADD CONSTRAINT group_activity_event_type_check
    CHECK (event_type IN (
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
        'PERMISSION_CHANGED', 'PHOTO_CHANGED',
        'EXPENSE_CREATED', 'PAYMENT_MADE', 'PAYMENT_REGISTERED',
        'PAYMENT_CONFIRMED', 'PAYMENT_REJECTED',
        'DEBT_OVERDUE', 'LIQUIDATION_PROPOSED'));
