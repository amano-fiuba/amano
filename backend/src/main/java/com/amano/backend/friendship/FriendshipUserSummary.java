package com.amano.backend.friendship;

import com.amano.backend.user.User;

/**
 * Minimal account representation used by friendship responses (FR-011): id,
 * first name, last name, email and the avatar URL when the inbox shows it. Never
 * CBU/CVU, currencies, password hash or avatar binaries.
 */
public record FriendshipUserSummary(Long id, String firstName, String lastName, String email,
        String avatarUrl) {

    public static FriendshipUserSummary from(User user) {
        return from(user, false);
    }

    /** The binary is revalidated on every read, so the URL needs no ?v=. */
    public static FriendshipUserSummary from(User user, boolean hasCustomAvatar) {
        return new FriendshipUserSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), hasCustomAvatar ? "/api/users/" + user.getId() + "/avatar" : null);
    }
}
