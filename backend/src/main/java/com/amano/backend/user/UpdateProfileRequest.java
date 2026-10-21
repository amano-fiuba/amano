package com.amano.backend.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Payload of PUT /api/users/me (feature 005, contracts/users-api.md). The
 * EMAIL IS NOT PART OF THIS CONTRACT (FR-007): the identity of the account is
 * an invariant of the edit; unknown members (any sneaky "email") are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProfileRequest(
        String firstName,
        String lastName,
        List<String> currencies,
        String cbuCvu) {
}
