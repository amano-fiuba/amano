package com.amano.backend.friendship;

import java.time.OffsetDateTime;

import com.amano.backend.user.User;

/** Response of a successful send (201, contracts/friendship-requests-api.md). */
public record FriendshipRequestResponse(Long id, FriendshipRequestStatus status,
        OffsetDateTime createdAt, FriendshipUserSummary recipient) {

    public static FriendshipRequestResponse from(FriendshipRequest request, User recipient) {
        return new FriendshipRequestResponse(request.getId(), request.getStatus(),
                request.getCreatedAt(), FriendshipUserSummary.from(recipient));
    }
}
