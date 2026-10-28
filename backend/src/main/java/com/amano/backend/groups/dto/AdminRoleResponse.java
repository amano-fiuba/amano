package com.amano.backend.groups.dto;

/**
 * Response of the role endpoints (US10, contracts/api-contracts.md):
 * {@code targetUserId}, {@code newRole}, {@code previousRole} and
 * {@code success}.
 */
public class AdminRoleResponse {

    private long targetUserId;
    private String newRole;
    private String previousRole;
    private boolean success;

    public AdminRoleResponse(long targetUserId, String newRole, String previousRole,
            boolean success) {
        this.targetUserId = targetUserId;
        this.newRole = newRole;
        this.previousRole = previousRole;
        this.success = success;
    }

    public long getTargetUserId() {
        return targetUserId;
    }

    public String getNewRole() {
        return newRole;
    }

    public String getPreviousRole() {
        return previousRole;
    }

    public boolean isSuccess() {
        return success;
    }
}
