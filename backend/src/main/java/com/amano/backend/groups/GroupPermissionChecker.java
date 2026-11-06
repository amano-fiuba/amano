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

    /**
     * Removing a registered member (DELETE /api/groups/{id}/members/{userId}):
     * ADMIN-only with no casilla — a MEMBER never removes another member.
     */
    public boolean canRemoveMembers(String callerRole, GroupPermissions permissions) {
        return GroupMember.ROLE_ADMIN.equals(callerRole);
    }

    /** US10 role management (assign/revoke admin): ADMIN-only, no casilla —
     * per-group decision, the authorization always resolves here.
     */
    public boolean canManageRoles(String callerRole, GroupPermissions permissions) {
        return GroupMember.ROLE_ADMIN.equals(callerRole);
    }

    /**
     * US6 members list read: both roles (any active member of the group) may
     * view it; there are no casillas gating the plain read.
     */
    public boolean canViewMembers(String callerRole, GroupPermissions permissions) {
        return GroupMember.ROLE_ADMIN.equals(callerRole)
                || GroupMember.ROLE_MEMBER.equals(callerRole);
    }

    /**
     * US8 group photo upload/replace/remove: ADMIN always; MEMBER only when
     * members_can_edit_group_data = true.
     */
    public boolean canManagePhoto(String callerRole, GroupPermissions permissions) {
        return canEditGroupData(callerRole, permissions);
    }

    /**
     * Editing the group data (photo, name, description): ADMIN always; MEMBER
     * only when members_can_edit_group_data = true (positive casilla).
     */
    public boolean canEditGroupData(String callerRole, GroupPermissions permissions) {
        return positiveCasilla(callerRole, permissions != null && permissions.isMembersCanEditGroupData());
    }

    /** Changing the casillas: ADMIN-only, no casilla. */
    public boolean canManagePermissions(String callerRole, GroupPermissions permissions) {
        return GroupMember.ROLE_ADMIN.equals(callerRole);
    }

    private boolean positiveCasilla(String callerRole, boolean granted) {
        return GroupMember.ROLE_ADMIN.equals(callerRole)
                || (GroupMember.ROLE_MEMBER.equals(callerRole) && granted);
    }

    /**
     * US1/US6/US9 plain reads of a group (detail, members, activity): both
     * roles may view; gated here so any direct API call resolves the
     * authorization through this single point.
     */
    public boolean canViewGroup(String callerRole, GroupPermissions permissions) {
        return GroupMember.ROLE_ADMIN.equals(callerRole)
                || GroupMember.ROLE_MEMBER.equals(callerRole);
    }

    /**
     * 017 (research.md D8, FR-007): registering a group expense. ADMIN always;
     * MEMBER only when members_cannot_make_expenses = false.
     */
    public boolean canCreateExpenses(String callerRole, GroupPermissions permissions) {
        return allowedUnless(callerRole, permissions != null && permissions.isMembersCannotMakeExpenses());
    }

    /**
     * 017 (research.md D8, FR-007): registering a group expense whose payer is
     * someone else ("generar gastos en nombre de otras personas"). ADMIN always;
     * MEMBER only when members_can_generate_expenses_for_others = true (positive
     * casilla, false by default).
     */
    public boolean canExpenseForOthers(String callerRole, GroupPermissions permissions) {
        if (GroupMember.ROLE_ADMIN.equals(callerRole)) {
            return true;
        }
        return GroupMember.ROLE_MEMBER.equals(callerRole)
                && permissions != null
                && permissions.isMembersCanGenerateExpensesForOthers();
    }

    /**
     * 018 (plan.md D2, FR-004): registering a payment of a debt. ADMIN always;
     * otherwise only the debtor (when registered) or the creditor of the debt.
     */
    public boolean canRegisterPayment(String callerRole, Long callerUserId, Long debtorUserId,
            Long creditorUserId) {
        if (GroupMember.ROLE_ADMIN.equals(callerRole)) {
            return true;
        }
        return callerUserId != null
                && (callerUserId.equals(debtorUserId) || callerUserId.equals(creditorUserId));
    }

    private boolean allowedUnless(String callerRole, boolean denied) {
        if (GroupMember.ROLE_ADMIN.equals(callerRole)) {
            return true;
        }
        return GroupMember.ROLE_MEMBER.equals(callerRole) && !denied;
    }
}
