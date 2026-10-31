-- US8 (T084 group photo): mirrors the existing user_avatars mechanism (feature 001):
-- the bytes live in a dedicated table validated at upload time with the same
-- AvatarValidator magic-byte gate; groups.photo keeps a URL reference
-- (/api/groups/{id}/photo?v=fingerprint), nullable = no photo → gray default.
CREATE TABLE group_photos (
    group_id     BIGINT PRIMARY KEY REFERENCES groups (id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    data         BYTEA NOT NULL CHECK (octet_length(data) <= 2097152),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
