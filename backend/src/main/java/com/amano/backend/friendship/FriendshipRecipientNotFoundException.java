package com.amano.backend.friendship;

/**
 * Recipient unknown or not verified (feature 011, FR-007). One neutral message for
 * both cases so the existence of the account is never disclosed.
 */
public class FriendshipRecipientNotFoundException extends RuntimeException {

    public FriendshipRecipientNotFoundException() {
        super("No se encontró el usuario");
    }
}
