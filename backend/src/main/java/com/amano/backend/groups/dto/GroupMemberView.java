package com.amano.backend.groups.dto;

/**
 * One row of the members list (US6, T078). Exactly one of
 * {@code userId}/{@code simulatedProfileId} is set; {@code avatar} is the
 * avatar URL of the registered user (reusing the existing user-avatar
 * mechanism) or NULL → the UI renders the gray default avatar;
 * {@code role} is the per-group role (simulated profiles are always MEMBER);
 * {@code cbuCvu} is the registered member's 22-digit CBU/CVU so group members
 * can copy it to make a transfer (NULL when not informed or simulated).
 */
public record GroupMemberView(
        Long userId,
        Long simulatedProfileId,
        String name,
        String avatar,
        String role,
        String cbuCvu) {
}
