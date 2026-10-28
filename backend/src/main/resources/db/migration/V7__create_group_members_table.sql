-- Groups-management (tasks.md T005): user-group membership join table.
-- Role is per group (ADMIN/MEMBER) stored in the relation, not on the user
-- (data-model.md). Simulated profiles never get rows here (V8). left_at is the
-- soft baja: the row is preserved so prior expenses and activity entries keep
-- traceability; active membership = left_at IS NULL.
CREATE TABLE group_members (
    user_id   BIGINT NOT NULL REFERENCES users (id),
    group_id  BIGINT NOT NULL REFERENCES groups (id),
    role      VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    left_at   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, group_id)
);

CREATE INDEX idx_group_members_group_id ON group_members (group_id);
CREATE INDEX idx_group_members_user_id ON group_members (user_id);
