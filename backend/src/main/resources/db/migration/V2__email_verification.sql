-- V2__email_verification.sql
-- Data model: specs/002-email-verification/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-001 (users.email_verified_at), FR-003/FR-007/FR-008/FR-009/FR-021
-- (email_verification_tokens), FR-011/FR-012/FR-013 (email_resend_limits) and
-- FR-017/FR-018 (account expiry removes the account; tokens cascade).
-- Feature 002-email-verification.
-- Sin DELETE (regla del equipo): solo ALTER TABLE + CREATE TABLE.

-- NULL = cuenta "no verificada" (bloqueada, FR-001/FR-004). Sin DEFAULT a propósito:
-- las cuentas existentes quedan no verificadas y el canje de token es la única vía
-- de escribir un valor (data-model.md "users (modificada)").
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL;

-- Acceso de la limpieza @Scheduled horaria: recorre solo las cuentas nunca
-- verificadas (data-model.md "Volumen y escala", FR-017, D6).
CREATE INDEX ix_users_email_verified_at ON users (email_verified_at)
    WHERE email_verified_at IS NULL;

-- Un solo token vigente por cuenta; se persiste únicamente el hash SHA-256 del
-- token en claro (64 hex chars, D2). Al eliminar la cuenta los tokens caen en
-- cascada (FR-017/FR-018).
CREATE TABLE email_verification_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verification_tokens_expires_at CHECK (expires_at > created_at)
);

-- Límite de reenvío por dirección de correo (FR-011): sin FK hacia users porque
-- aplica exista o no la cuenta asociada. Solo se guarda el hash del correo
-- normalizado, sin ningún otro dato del usuario no registrado (D4, privacidad).
CREATE TABLE email_resend_limits (
    email_hash     VARCHAR(64) NOT NULL,
    last_resend_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (email_hash)
);
