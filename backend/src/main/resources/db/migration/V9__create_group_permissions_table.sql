-- Groups-management (tasks.md T007): one permissions configuration per group.
-- Defaults (decision 2026-10-27): spec.md does not define casilla defaults
-- (FR-005 lists them without values) → ALL casillas default to false; the
-- Administrator activates restrictions explicitly from the settings UI.
CREATE TABLE group_permissions (
    id                                     BIGSERIAL PRIMARY KEY,
    group_id                               BIGINT NOT NULL UNIQUE REFERENCES groups (id),
    members_cannot_make_expenses           BOOLEAN NOT NULL DEFAULT FALSE,
    members_cannot_invite                  BOOLEAN NOT NULL DEFAULT FALSE,
    members_cannot_edit_settings           BOOLEAN NOT NULL DEFAULT FALSE,
    members_can_manage_simulated_profiles  BOOLEAN NOT NULL DEFAULT FALSE,
    members_can_generate_expenses_for_others BOOLEAN NOT NULL DEFAULT FALSE,
    payments_require_validation            BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_group_permissions_group_id ON group_permissions (group_id);
