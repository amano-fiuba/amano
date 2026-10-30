-- V6__create_friendship_requests.sql
-- Data model: specs/011-send-friend-request/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-003, FR-005, FR-006, FR-009 and FR-012 (feature 011-send-friend-request).
-- Sin UPDATE ni DELETE: solo CREATE TABLE e índices. Las cuentas (users) no se modifican.

CREATE TABLE friendship_requests (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id    BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    status       VARCHAR(10) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_friendship_requests_sender
        FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friendship_requests_recipient
        FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Una persona no puede enviarse una solicitud a sí misma (FR-004).
    CONSTRAINT ck_friendship_requests_not_self CHECK (sender_id <> recipient_id),
    CONSTRAINT ck_friendship_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    -- PENDING no tiene respuesta; ACCEPTED/REJECTED quedan preparadas para otra historia.
    CONSTRAINT ck_friendship_requests_responded CHECK (
        (status = 'PENDING' AND responded_at IS NULL)
        OR (status IN ('ACCEPTED', 'REJECTED') AND responded_at IS NOT NULL)
    )
);

-- A lo sumo una solicitud pendiente por pareja, sin importar la dirección (FR-005, FR-009).
CREATE UNIQUE INDEX uq_friendship_requests_pending_pair
    ON friendship_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id))
    WHERE status = 'PENDING';

-- A lo sumo una amistad por pareja cuando la aceptación se implemente (FR-006).
CREATE UNIQUE INDEX uq_friendship_requests_accepted_pair
    ON friendship_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id))
    WHERE status = 'ACCEPTED';

-- Bandeja "Solicitudes": pendientes recibidas, más recientes primero (FR-012).
CREATE INDEX ix_friendship_requests_recipient_pending
    ON friendship_requests (recipient_id, created_at DESC, id DESC)
    WHERE status = 'PENDING';
