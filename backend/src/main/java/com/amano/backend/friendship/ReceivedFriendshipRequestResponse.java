package com.amano.backend.friendship;

import java.time.OffsetDateTime;

import com.amano.backend.user.User;

/** One pending request in the recipient's inbox (contracts/friendship-requests-api.md). */
public record ReceivedFriendshipRequestResponse(Long id, FriendshipRequestStatus status,
        OffsetDateTime createdAt, FriendshipUserSummary sender) {

    public static ReceivedFriendshipRequestResponse from(FriendshipRequest request, User sender) {
        return new ReceivedFriendshipRequestResponse(request.getId(), request.getStatus(),
                request.getCreatedAt(), FriendshipUserSummary.from(sender));
    }
}
