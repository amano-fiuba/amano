package com.amano.backend.friendship;

/**
 * Unknown request, request not directed to the session user or request already
 * removed by the account cascade (feature 012, FR-007/FR-009). One neutral
 * message for every case so existence, ownership and previous state are never
 * disclosed.
 */
public class FriendshipRequestNotFoundException extends RuntimeException {

    public FriendshipRequestNotFoundException() {
        super("No se encontró la solicitud de amistad");
    }
}
