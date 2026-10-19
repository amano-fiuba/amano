package com.amano.backend.user;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public representation of the account, used by both POST /api/auth/register and
 * GET /api/users/me (contracts/auth-api.md + users-api.md). Never carries the
 * password or its hash (FR-007). {@code null} members are omitted (optional
 * lastName/cbuCvu; avatarUrl only when hasCustomAvatar is true — FR-010/D5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAccountResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        List<String> currencies,
        String cbuCvu,
        boolean hasCustomAvatar,
        String avatarUrl) {

    /** currencies empty → empty list (the client applies the ARS default, FR-011/Q1). */
    public static UserAccountResponse from(User user, boolean hasCustomAvatar) {
        List<String> currencyCodes = new ArrayList<>();
        for (Currency currency : user.getCurrencies()) {
            currencyCodes.add(currency.name());
        }
        String avatarUrl = hasCustomAvatar ? "/api/users/" + user.getId() + "/avatar" : null;
        return new UserAccountResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                currencyCodes,
                user.getCbu(),
                hasCustomAvatar,
                avatarUrl);
    }
}
