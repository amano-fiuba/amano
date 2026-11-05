package com.amano.backend.friendship;

/**
 * Answer of the deletion-eligibility endpoint (feature 016, FR-022): whether the
 * session user can delete the friendship and, when not, the reason to show.
 */
public record DeletionEligibilityResponse(boolean canDelete, String reason) {

    public static DeletionEligibilityResponse allowed() {
        return new DeletionEligibilityResponse(true, null);
    }

    public static DeletionEligibilityResponse blocked(String reason) {
        return new DeletionEligibilityResponse(false, reason);
    }
}
