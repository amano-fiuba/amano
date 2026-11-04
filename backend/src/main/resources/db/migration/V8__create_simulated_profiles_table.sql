-- Groups-management (tasks.md T006): simulated profiles (invitados sin cuenta).
-- No FK to users by design; members of the group implicitly via group_id
-- (counted together with active group_members rows). Unique (group_id, email).
CREATE TABLE simulated_profiles (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL REFERENCES groups (id),
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(191) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_simulated_profiles_group_email
    ON simulated_profiles (group_id, email);
