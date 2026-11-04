package com.amano.backend.friendship;

import java.time.OffsetDateTime;

/**
 * One friend of the session user (feature 013, contracts/friends-api.md): the
 * other account of an ACCEPTED request. Only id, names and email (FR-004), plus
 * the acceptance date. Built directly by the JPQL projection of
 * {@link FriendshipRequestRepository#findFriendsOf(Long)}, so private account
 * data is never loaded.
 */
public record FriendResponse(Long id, String firstName, String lastName, String email,
        OffsetDateTime friendsSince) {
}
