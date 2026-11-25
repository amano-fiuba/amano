-- V39__create_notifications.sql
-- Feature 035-in-app-notifications (specs/035-in-app-notifications/data-model.md).
-- Bandeja personal de avisos y preferencias por usuario. Tablas nuevas y vacías:
-- no hace falta backfill. Sin DELETE (regla del equipo): solo CREATE TABLE.
-- Los identificadores de destino (grupo, deuda, gasto, solicitud) son columnas
-- planas sin FOREIGN KEY para que el aviso sobreviva a la eliminación del
-- recurso al que apuntaba (FR-019, SC-005).

CREATE TABLE notifications (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    type                VARCHAR(40) NOT NULL,
    title               VARCHAR(150) NOT NULL,
    message             TEXT NOT NULL,
    target_kind         VARCHAR(20) NOT NULL DEFAULT 'NONE',
    group_id            BIGINT NULL,
    debt_id             BIGINT NULL,
    expense_id          BIGINT NULL,
    friend_request_id   BIGINT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at             TIMESTAMPTZ NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'REMINDER', 'DEBT_WHISTLE', 'DEBT_OVERDUE', 'PAYER_DUE_NOTICE',
        'GROUP_EXPENSE', 'PAYMENT_RECORDED', 'FRIEND_REQUEST', 'FRIEND_ACTIVITY')),
    CONSTRAINT ck_notifications_target_kind CHECK (target_kind IN
        ('GROUP', 'DEBT', 'FRIEND_REQUEST', 'NONE'))
);

CREATE INDEX ix_notifications_user_created
    ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_user_unread
    ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE user_notification_preferences (
    user_id                  BIGINT PRIMARY KEY,
    in_app                   BOOLEAN NOT NULL DEFAULT TRUE,
    email                    BOOLEAN NOT NULL DEFAULT TRUE,
    reminders_whistles       BOOLEAN NOT NULL DEFAULT TRUE,
    debt_due_collections     BOOLEAN NOT NULL DEFAULT TRUE,
    group_activity           BOOLEAN NOT NULL DEFAULT TRUE,
    friend_activity          BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
