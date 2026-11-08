-- V26__add_group_reminder_settings.sql
-- 025 T004: configuración de recordatorios por grupo. El valor 5 es el
-- predeterminado y también una elección explícita; el mensaje nulo usa el
-- texto predeterminado.

ALTER TABLE groups
    ADD COLUMN reminder_send_days INTEGER NOT NULL DEFAULT 5;

ALTER TABLE groups
    ADD COLUMN reminder_message VARCHAR(500) NULL;

ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_send_days
    CHECK (reminder_send_days IN (7, 5, 3, 1));

ALTER TABLE groups
    ADD CONSTRAINT ck_groups_reminder_message
    CHECK (reminder_message IS NULL OR char_length(reminder_message) <= 500);
