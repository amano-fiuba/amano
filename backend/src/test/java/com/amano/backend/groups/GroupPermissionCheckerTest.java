package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 017 T013: the two V9 casillas that gate group expenses (research.md D8,
 * FR-007). ADMIN is always allowed; MEMBER depends on the casilla; any other
 * role is denied.
 */
class GroupPermissionCheckerTest {

    private final GroupPermissionChecker checker = new GroupPermissionChecker();

    private static GroupPermissions permissions(boolean cannotMakeExpenses, boolean canExpenseForOthers) {
        GroupPermissions permissions = new GroupPermissions();
        permissions.setMembersCannotMakeExpenses(cannotMakeExpenses);
        permissions.setMembersCanGenerateExpensesForOthers(canExpenseForOthers);
        return permissions;
    }

    @Test
    void adminAlwaysCreatesExpenses() {
        assertThat(checker.canCreateExpenses(GroupMember.ROLE_ADMIN, permissions(true, false))).isTrue();
        assertThat(checker.canCreateExpenses(GroupMember.ROLE_ADMIN, null)).isTrue();
    }

    @Test
    void memberCreatesExpensesUnlessTheCasillaForbidsIt() {
        assertThat(checker.canCreateExpenses(GroupMember.ROLE_MEMBER, permissions(false, false))).isTrue();
        assertThat(checker.canCreateExpenses(GroupMember.ROLE_MEMBER, permissions(true, false))).isFalse();
        assertThat(checker.canCreateExpenses(GroupMember.ROLE_MEMBER, null)).isTrue();
    }

    @Test
    void adminAlwaysRegistersExpensesPaidByOthers() {
        assertThat(checker.canExpenseForOthers(GroupMember.ROLE_ADMIN, permissions(false, false))).isTrue();
        assertThat(checker.canExpenseForOthers(GroupMember.ROLE_ADMIN, null)).isTrue();
    }

    @Test
    void memberRegistersExpensesPaidByOthersOnlyWhenTheCasillaAllowsIt() {
        assertThat(checker.canExpenseForOthers(GroupMember.ROLE_MEMBER, permissions(false, true))).isTrue();
        assertThat(checker.canExpenseForOthers(GroupMember.ROLE_MEMBER, permissions(false, false))).isFalse();
        assertThat(checker.canExpenseForOthers(GroupMember.ROLE_MEMBER, null)).isFalse();
    }

    @Test
    void unknownRolesAreDenied() {
        assertThat(checker.canCreateExpenses("GUEST", permissions(false, true))).isFalse();
        assertThat(checker.canExpenseForOthers("GUEST", permissions(false, true))).isFalse();
        assertThat(checker.canCreateExpenses(null, null)).isFalse();
    }

    // 018 T013 (plan.md D2, FR-004): who may register a payment of a debt.
    @Test
    void adminAlwaysRegistersPayments() {
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_ADMIN, 1L, 15L, 7L)).isTrue();
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_ADMIN, 1L, null, 7L)).isTrue();
    }

    @Test
    void theDebtorAndTheCreditorRegisterTheirPayments() {
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, 15L, 15L, 7L)).isTrue();
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, 7L, 15L, 7L)).isTrue();
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, 7L, null, 7L)).isTrue();
    }

    @Test
    void aThirdMemberCannotRegisterPayments() {
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, 20L, 15L, 7L)).isFalse();
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, 20L, null, 7L)).isFalse();
        assertThat(checker.canRegisterPayment(GroupMember.ROLE_MEMBER, null, null, 7L)).isFalse();
    }
}
