-- Groups-management (tasks.md T008): standalone activity event rows
-- (preservation pattern: only references group_id; persists even when the
-- related member/expense/payment is later modified or deleted). event_type is
-- VARCHAR+CHECK instead of a native ENUM (decision 2026-10-27); values are the
-- module-wide set, with EXPENSE_CREATED/PAYMENT_MADE reserved as hook points
-- (not implemented in this module — spec, Alcance y limitaciones).
CREATE TABLE group_activity (
    id            BIGSERIAL PRIMARY KEY,
    group_id      BIGINT NOT NULL REFERENCES groups (id),
    event_type    VARCHAR(50) NOT NULL CHECK (event_type IN (
                      'MEMBER_ADDED', 'MEMBER_REMOVED', 'MEMBER_LEFT',
                      'PERMISSION_CHANGED', 'PHOTO_CHANGED',
                      'EXPENSE_CREATED', 'PAYMENT_MADE')),
    actor_user_id BIGINT REFERENCES users (id),
    details_json  TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_group_activity_group_id ON group_activity (group_id);
CREATE INDEX idx_group_activity_event_type ON group_activity (event_type);
