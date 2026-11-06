package com.amano.backend.user;

/**
 * One row of GET /api/users/search (feature 008, D4): ONLY id, first name, last
 * name, email and the relationship with the session user. Never CBU/CVU,
 * currencies, password hash or avatar data (FR-007/FR-014). lastName may be null
 * (optional since 001).
 *
 * <p>relationship: NONE (can be added), FRIENDS, or PENDING (a request was sent
 * or received and is not answered yet).
 */
public record UserSearchResult(Long id, String firstName, String lastName, String email,
        String relationship) {

    public static final String NONE = "NONE";
    public static final String FRIENDS = "FRIENDS";
    public static final String PENDING = "PENDING";

    public static UserSearchResult from(User user, String relationship) {
        return new UserSearchResult(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), relationship);
    }
}
