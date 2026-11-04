package com.amano.backend.friendship;

/**
 * A pending request already exists between the pair, in either direction
 * (feature 011, FR-005/FR-009): 409. Also used when the unique index wins a
 * concurrent race.
 */
public class PendingFriendshipRequestExistsException extends RuntimeException {

    public PendingFriendshipRequestExistsException() {
        super("Ya existe una solicitud de amistad pendiente entre ambos usuarios");
    }
}
