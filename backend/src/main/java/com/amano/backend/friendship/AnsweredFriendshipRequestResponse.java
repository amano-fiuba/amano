package com.amano.backend.friendship;

import java.time.OffsetDateTime;

import com.amano.backend.user.User;

/**
 * Finished request returned by the respond endpoint (feature 012): final status,
 * answer date and the summary of the sender, mirroring the inbox item
 * (contracts/friendship-requests-api.md §200).
 */
public record AnsweredFriendshipRequestResponse(Long id, FriendshipRequestStatus status,
        OffsetDateTime createdAt, OffsetDateTime respondedAt, FriendshipUserSummary sender) {

    public static AnsweredFriendshipRequestResponse from(FriendshipRequest request, User sender) {
        return new AnsweredFriendshipRequestResponse(request.getId(), request.getStatus(),
                request.getCreatedAt(), request.getRespondedAt(), FriendshipUserSummary.from(sender));
    }
}
