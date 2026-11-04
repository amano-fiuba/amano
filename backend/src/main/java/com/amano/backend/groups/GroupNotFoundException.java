package com.amano.backend.groups;

/**
 * The caller does not see / does not belong to a group (US1 detail, one
 * neutral 404 — it never discloses existence to non-members).
 */
public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException(String message) {
        super(message);
    }
}
