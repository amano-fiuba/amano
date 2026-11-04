package com.amano.backend.groups.dto;

/**
 * Response of {@code POST /api/groups/{id}/leave} (US5,
 * contracts/api-contracts.md): user-facing message plus whether the group was
 * left (it is, unless rejected; the group itself persists for the others).
 */
public class LeaveGroupResponse {

    private String message;
    private boolean groupLeft;

    public LeaveGroupResponse(String message, boolean groupLeft) {
        this.message = message;
        this.groupLeft = groupLeft;
    }

    public String getMessage() {
        return message;
    }

    public boolean isGroupLeft() {
        return groupLeft;
    }
}
