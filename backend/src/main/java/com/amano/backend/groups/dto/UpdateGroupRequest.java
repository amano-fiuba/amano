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

    public static class PermissionsInput {

        private Boolean membersCannotMakeExpenses;
        private Boolean membersCannotInvite;
        private Boolean membersCannotEditSettings;
        private Boolean membersCanManageSimulatedProfiles;
        private Boolean membersCanGenerateExpensesForOthers;
        private Boolean paymentsRequireValidation;

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

        public Boolean getMembersCannotEditSettings() {
            return membersCannotEditSettings;
        }

        public void setMembersCannotEditSettings(Boolean membersCannotEditSettings) {
            this.membersCannotEditSettings = membersCannotEditSettings;
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
