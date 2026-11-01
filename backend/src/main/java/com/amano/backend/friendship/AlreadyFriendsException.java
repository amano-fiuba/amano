package com.amano.backend.friendship;

/** Both accounts are already friends (feature 011, FR-006): 409. */
public class AlreadyFriendsException extends RuntimeException {

    public AlreadyFriendsException() {
        super("Ya son amigos");
    }
}
