package com.amano.backend.friendship;

/**
 * Body of PUT /api/users/me/friendship-requests/{id} (feature 012). The action
 * stays a raw string so an unknown value reaches the service validation
 * (FR-010) instead of failing at JSON deserialization.
 */
public record RespondFriendshipRequestRequest(String action) {
}
