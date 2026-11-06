package com.amano.backend.user;

/**
 * One row of GET /api/users/search (feature 008, D4): ONLY id, first name, last
 * name, email and the caller's friendship relationship with that account.
 * Never CBU/CVU, currencies, password hash or avatar data (FR-007/FR-014).
 * lastName may be null (optional since 001).
 *
 * {@code friendshipStatus}: NONE, PENDING_SENT (the caller sent a request),
 * PENDING_RECEIVED (the other account sent one to the caller) or FRIENDS.
 */
public record UserSearchResult(Long id, String firstName, String lastName, String email,
        String friendshipStatus) {

    public static final String NONE = "NONE";
    public static final String PENDING_SENT = "PENDING_SENT";
    public static final String PENDING_RECEIVED = "PENDING_RECEIVED";
    public static final String FRIENDS = "FRIENDS";

    public static UserSearchResult from(User user, String friendshipStatus) {
        return new UserSearchResult(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), friendshipStatus);
    }
}
