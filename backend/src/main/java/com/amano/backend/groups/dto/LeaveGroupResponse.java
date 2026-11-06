package com.amano.backend.groups.dto;

/**
 * Response of {@code POST /api/groups/{id}/leave} (US5,
 * contracts/api-contracts.md): user-facing message plus whether the group was
 * left (it is, unless rejected; the group itself persists for the others).
 */
public class LeaveGroupResponse {

    private String message;
    private boolean groupLeft;
    private boolean groupDeleted;

    public LeaveGroupResponse(String message, boolean groupLeft, boolean groupDeleted) {
        this.message = message;
        this.groupLeft = groupLeft;
        this.groupDeleted = groupDeleted;
    }

    /** True when the caller was the last member, so the group was deleted. */
    public boolean isGroupDeleted() {
        return groupDeleted;
    }

    public String getMessage() {
        return message;
    }

    public boolean isGroupLeft() {
        return groupLeft;
    }
}
