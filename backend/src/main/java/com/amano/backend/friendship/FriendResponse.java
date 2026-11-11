package com.amano.backend.friendship;

import java.time.OffsetDateTime;

/**
 * One friend of the session user (feature 013, contracts/friends-api.md): the
 * other account of an ACCEPTED request. Id, names, email (FR-004), acceptance
 * date, the CBU/CVU so the friend can copy it to transfer (null when not
 * informed) and the avatar URL (null without a custom avatar → the UI renders
 * the default one). Built directly by the JPQL projection of
 * {@link FriendshipRequestRepository#findFriendsOf(Long)}, so avatar binaries and
 * the rest of the private account data are never loaded.
 */
public record FriendResponse(Long id, String firstName, String lastName, String email,
        OffsetDateTime friendsSince, String cbuCvu, String avatarUrl) {

    /** JPQL projection constructor. The binary is revalidated on every read, so no ?v=. */
    public FriendResponse(Long id, String firstName, String lastName, String email,
            OffsetDateTime friendsSince, String cbuCvu, boolean hasCustomAvatar) {
        this(id, firstName, lastName, email, friendsSince, cbuCvu,
                hasCustomAvatar ? "/api/users/" + id + "/avatar" : null);
    }
}
