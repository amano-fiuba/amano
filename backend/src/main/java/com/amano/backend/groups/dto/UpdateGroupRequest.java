package com.amano.backend.groups.dto;

/**
 * Payload of {@code PUT /api/groups/{id}} (contracts/api-contracts.md): partial
 * update of name, description and the permission casillas. Unspecified fields
 * and casillas keep their previous value; business validation lives in
 * {@code GroupService}.
 */
public class UpdateGroupRequest {

    /** Optional name override; when present it stays required and non-empty. */
    private String name;

    private String description;

    private PermissionsInput permissions;

    /**
     * 022 T078: days until a pending debt becomes overdue. {@code null} means
     * "never overdue", so whether the key was sent is tracked apart from its value.
     */
    private Integer debtDueDays;

    private boolean debtDueDaysPresent;

    public Integer getDebtDueDays() {
        return debtDueDays;
    }

    public void setDebtDueDays(Integer debtDueDays) {
        this.debtDueDays = debtDueDays;
        this.debtDueDaysPresent = true;
    }

    /** True when the payload carried {@code debtDueDays} (even as {@code null}). */
    public boolean isDebtDueDaysPresent() {
        return debtDueDaysPresent;
    }

    public static class PermissionsInput {

        private Boolean membersCannotMakeExpenses;
        private Boolean membersCannotInvite;
        private Boolean membersCanManageSimulatedProfiles;
        private Boolean membersCanGenerateExpensesForOthers;
        private Boolean paymentsRequireValidation;
        private Boolean membersCanEditGroupData;

        public Boolean getMembersCanEditGroupData() {
            return membersCanEditGroupData;
        }

        public void setMembersCanEditGroupData(Boolean membersCanEditGroupData) {
            this.membersCanEditGroupData = membersCanEditGroupData;
        }

        public Boolean getMembersCannotMakeExpenses() {
            return membersCannotMakeExpenses;
        }

        public void setMembersCannotMakeExpenses(Boolean membersCannotMakeExpenses) {
            this.membersCannotMakeExpenses = membersCannotMakeExpenses;
        }

        public Boolean getMembersCannotInvite() {
            return membersCannotInvite;
        }

        public void setMembersCannotInvite(Boolean membersCannotInvite) {
            this.membersCannotInvite = membersCannotInvite;
        }

        public Boolean getMembersCanManageSimulatedProfiles() {
            return membersCanManageSimulatedProfiles;
        }

        public void setMembersCanManageSimulatedProfiles(Boolean membersCanManageSimulatedProfiles) {
            this.membersCanManageSimulatedProfiles = membersCanManageSimulatedProfiles;
        }

        public Boolean getMembersCanGenerateExpensesForOthers() {
            return membersCanGenerateExpensesForOthers;
        }

        public void setMembersCanGenerateExpensesForOthers(Boolean membersCanGenerateExpensesForOthers) {
            this.membersCanGenerateExpensesForOthers = membersCanGenerateExpensesForOthers;
        }

        public Boolean getPaymentsRequireValidation() {
            return paymentsRequireValidation;
        }

        public void setPaymentsRequireValidation(Boolean paymentsRequireValidation) {
            this.paymentsRequireValidation = paymentsRequireValidation;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PermissionsInput getPermissions() {
        return permissions;
    }

    public void setPermissions(PermissionsInput permissions) {
        this.permissions = permissions;
    }
}
