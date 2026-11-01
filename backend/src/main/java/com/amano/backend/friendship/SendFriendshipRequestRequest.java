package com.amano.backend.friendship;

/**
 * Body of POST /api/users/me/friendship-requests (contracts/friendship-requests-api.md).
 * The sender is never part of the body: it always comes from the session.
 */
public record SendFriendshipRequestRequest(Long recipientId) {
}
