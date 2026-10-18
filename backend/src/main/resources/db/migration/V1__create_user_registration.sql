-- V1__create_user_registration.sql
-- Data model: specs/001-user-registration/data-model.md (source of truth, ddl-auto=validate)
-- Covers FR-002..FR-006, FR-009..FR-012 (feature 001-user-registration)

CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name        VARCHAR(50) NOT NULL,
    last_name         VARCHAR(50) NULL,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    cbu               VARCHAR(22) NULL,
    terms_accepted_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_first_name CHECK (char_length(first_name) BETWEEN 2 AND 50),
    CONSTRAINT ck_users_last_name CHECK (last_name IS NULL OR char_length(last_name) BETWEEN 2 AND 50),
    CONSTRAINT ck_users_cbu CHECK (cbu IS NULL OR cbu ~ '^[0-9]{22}$')
);

-- Unique identity of an account is the normalized email (trim + lowercase, FR-004/FR-005).
-- Applied by the service layer before insert; the index is the last resort against
-- concurrent registration races (SC-004, 409 via DataIntegrityViolationException).
CREATE UNIQUE INDEX uq_users_email ON users (email);

-- Row exists only when the user uploaded a custom avatar (D5: no default row).
CREATE TABLE user_avatars (
    user_id      BIGINT NOT NULL PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    data         BYTEA NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_avatars_data CHECK (octet_length(data) <= 2097152),
    CONSTRAINT ck_user_avatars_content_type CHECK (content_type IN ('image/jpeg', 'image/png'))
);

-- Preferred currencies: subset of {ARS, USD, EUR}; no persisted default —
-- 0 rows means ARS at read/serve time (FR-011, Q1).
CREATE TABLE user_currencies (
    user_id       BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    PRIMARY KEY (user_id, currency_code),
    CONSTRAINT fk_user_currencies_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_currencies_code CHECK (currency_code IN ('ARS', 'USD', 'EUR'))
);
