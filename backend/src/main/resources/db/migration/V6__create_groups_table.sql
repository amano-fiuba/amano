-- Groups-management (specs/010-groups-management, tasks.md T004): table groups.
-- Name: required and non-empty only (FR-001, no uniqueness nor length limit —
-- decision 2026-10-27, column kept at VARCHAR(255)). Business validation lives
-- in the service layer (constitution VIII).
CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(200),
    photo       VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groups_name ON groups (name);
