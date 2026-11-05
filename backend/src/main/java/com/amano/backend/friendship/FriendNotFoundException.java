package com.amano.backend.friendship;

/**
 * The two accounts are not friends (feature 016, FR-025): one neutral 404 for a
 * missing, foreign or already-deleted friendship, without revealing anything
 * about other relationships.
 */
public class FriendNotFoundException extends RuntimeException {

    public static final String MESSAGE = "No sos amigo de ese usuario";

    public FriendNotFoundException() {
        super(MESSAGE);
    }
}
