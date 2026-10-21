-- V3__password_reset_tokens.sql
-- Data model: specs/006-password-recovery/spec.md (source of truth, ddl-auto=validate)
-- Covers FR-005/FR-006/FR-007 (password_reset_tokens).
-- Feature 006-password-recovery.
-- Sin DELETE (regla del equipo): solo CREATE TABLE.

-- Un solo token vigente por cuenta; se persiste únicamente el hash SHA-256 del
-- token en claro (64 hex chars, mismo criterio que la feature 003, D2). Al
-- eliminar la cuenta los tokens caen en cascada.
CREATE TABLE password_reset_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_expires_at CHECK (expires_at > created_at)
);

-- Invalidación al emitir uno nuevo (FR-007): busca los tokens vigentes de la cuenta.
CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);
