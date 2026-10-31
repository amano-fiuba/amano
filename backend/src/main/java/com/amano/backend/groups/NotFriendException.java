package com.amano.backend.groups;

/**
 * The registration target is not a friend of the caller (US1/US3): after the
 * friendship search is restricted to the friendship relation (decision 4),
 * adding a registered member who is outside that relation is a 404 per the
 * contract — it never discloses the account's existence.
 */
public class NotFriendException extends RuntimeException {

    public NotFriendException(String message) {
        super(message);
    }
}
