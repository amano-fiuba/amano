package com.amano.backend.user;

/**
 * One row of GET /api/users/search (feature 008, D4): ONLY id, first name, last
 * name and email. Never CBU/CVU, currencies, password hash or avatar data
 * (FR-007/FR-014). lastName may be null (optional since 001).
 */
public record UserSearchResult(Long id, String firstName, String lastName, String email) {

    public static UserSearchResult from(User user) {
        return new UserSearchResult(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }
}
