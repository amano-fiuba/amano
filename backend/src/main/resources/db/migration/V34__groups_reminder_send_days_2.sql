-- V34__groups_reminder_send_days_2.sql
-- 028 FR-017 / clarificación 2026-11-08: la opción "2 días antes" está
-- disponible para todos los grupos, tengan o no gastos recurrentes.

ALTER TABLE groups DROP CONSTRAINT ck_groups_reminder_send_days;

ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_send_days
    CHECK (reminder_send_days IN (7, 5, 3, 2, 1));
