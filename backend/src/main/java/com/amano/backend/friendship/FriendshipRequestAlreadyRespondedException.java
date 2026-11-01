package com.amano.backend.friendship;

/**
 * The request was already answered, whatever the outcome was (feature 012,
 * FR-008). The message never reveals whether the previous answer was an
 * acceptance or a rejection.
 */
public class FriendshipRequestAlreadyRespondedException extends RuntimeException {

    public FriendshipRequestAlreadyRespondedException() {
        super("Esta solicitud de amistad ya fue respondida");
    }
}
