package com.amano.backend.friendship;

/**
 * Lifecycle of a friendship request (data-model.md). Feature 011 only creates and
 * lists PENDING rows; ACCEPTED represents an existing friendship and REJECTED is
 * historical. Accepting/rejecting belongs to a later story.
 */
public enum FriendshipRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
