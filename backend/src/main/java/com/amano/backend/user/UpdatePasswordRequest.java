package com.amano.backend.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload of PUT /api/users/me/password (feature 005, contracts/users-api.md).
 * The current password is the authorization proof: the credit is never changed
 * without its 1:1 verification against the stored hash (FR-013/FR-014).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePasswordRequest(
        String currentPassword,
        String newPassword,
        String confirmPassword) {
}
