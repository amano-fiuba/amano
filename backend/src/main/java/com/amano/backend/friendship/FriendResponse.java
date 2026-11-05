package com.amano.backend.friendship;

import java.time.OffsetDateTime;

/**
 * One friend of the session user (feature 013, contracts/friends-api.md): the
 * other account of an ACCEPTED request. Only id, names and email (FR-004), plus
 * the acceptance date and the avatar URL (null without a custom avatar → the UI
 * renders the default one). Built directly by the JPQL projection of
 * {@link FriendshipRequestRepository#findFriendsOf(Long)}, so private account
 * data and avatar binaries are never loaded.
 */
public record FriendResponse(Long id, String firstName, String lastName, String email,
        OffsetDateTime friendsSince, String avatarUrl) {

    /** JPQL projection constructor. The binary is revalidated on every read, so no ?v=. */
    public FriendResponse(Long id, String firstName, String lastName, String email,
            OffsetDateTime friendsSince, boolean hasCustomAvatar) {
        this(id, firstName, lastName, email, friendsSince,
                hasCustomAvatar ? "/api/users/" + id + "/avatar" : null);
    }
}
