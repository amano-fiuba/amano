package com.amano.backend.groups.dto;

/**
 * Response of the removal endpoints (US4, contracts/api-contracts.md):
 * {@code removed} plus a user-facing message with what was done.
 */
public class RemovalResponse {

    private boolean removed;
    private String message;

    public RemovalResponse(boolean removed, String message) {
        this.removed = removed;
        this.message = message;
    }

    public boolean isRemoved() {
        return removed;
    }

    public String getMessage() {
        return message;
    }
}
