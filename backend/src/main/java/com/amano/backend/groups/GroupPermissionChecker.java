package com.amano.backend.groups;

import org.springframework.stereotype.Component;

/**
 * Single authorization decision point (tasks.md T022): combines the caller's
 * role with the group's casillas. ADMIN is always allowed; MEMBER only when
 * the matching casilla permits it. All group actions consult this checker
 * instead of duplicating role/checkbox logic; server-side enforcement means
 * permissions hold even when endpoints are called directly.
 */
@Component
public class GroupPermissionChecker {

    /**
     * Settings editing (PUT /api/groups/{id}): allowed for ADMIN always and
     * for MEMBER only when members_cannot_edit_settings = false.
     */
    public boolean canEditSettings(String callerRole, GroupPermissions permissions) {
        return allowedUnless(callerRole, permissions != null && permissions.isMembersCannotEditSettings());
    }

    /**
     * Adding members (POST /api/groups/{id}/members): ADMIN always; MEMBER
     * only when members_cannot_invite = false.
     */
    public boolean canAddMembers(String callerRole, GroupPermissions permissions) {
        return allowedUnless(callerRole, permissions != null && permissions.isMembersCannotInvite());
    }

    /**
     * Removing a simulated profile: ADMIN always; MEMBER only when
     * members_can_manage_simulated_profiles = true (positive casilla).
     */
    public boolean canRemoveSimulatedProfiles(String callerRole, GroupPermissions permissions) {
        if (GroupMember.ROLE_ADMIN.equals(callerRole)) {
            return true;
        }
        return GroupMember.ROLE_MEMBER.equals(callerRole)
                && permissions != null
                && permissions.isMembersCanManageSimulatedProfiles();
    }

    private boolean allowedUnless(String callerRole, boolean denied) {
        if (GroupMember.ROLE_ADMIN.equals(callerRole)) {
            return true;
        }
        return GroupMember.ROLE_MEMBER.equals(callerRole) && !denied;
    }
}
