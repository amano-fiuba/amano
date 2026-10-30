package com.amano.backend.friendship;

import com.amano.backend.user.User;

/**
 * Minimal account representation used by friendship responses (FR-011): only id,
 * first name, last name and email. Never CBU/CVU, currencies, password hash or
 * avatar data.
 */
public record FriendshipUserSummary(Long id, String firstName, String lastName, String email) {

    public static FriendshipUserSummary from(User user) {
        return new FriendshipUserSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail());
    }
}
