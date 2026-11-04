package com.amano.backend.friendship;

import com.amano.backend.common.FieldValidationException;
import java.util.Map;

/**
 * Response the recipient gives to a pending request (feature 012, FR-010).
 * Compared exactly against the raw string in the request body: any other value,
 * empty or missing, is a field validation error without touching the database.
 */
public enum FriendshipRequestAction {
    ACCEPT(FriendshipRequestStatus.ACCEPTED),
    REJECT(FriendshipRequestStatus.REJECTED);

    /** Exact message of the contract for an invalid action (properties.fields.action). */
    public static final String INVALID_ACTION_MESSAGE = "La acción no es válida";

    private final FriendshipRequestStatus targetStatus;

    FriendshipRequestAction(FriendshipRequestStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public FriendshipRequestStatus targetStatus() {
        return targetStatus;
    }

    /** Parses the body value; unknown, empty or missing values produce 400 (FR-010). */
    public static FriendshipRequestAction fromValue(String value) {
        for (FriendshipRequestAction action : values()) {
            if (action.name().equals(value)) {
                return action;
            }
        }
        throw new FieldValidationException(
                Map.of("action", INVALID_ACTION_MESSAGE));
    }
}
