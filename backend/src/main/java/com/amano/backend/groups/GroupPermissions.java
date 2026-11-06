package com.amano.backend.groups;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Group permission casillas (table {@code group_permissions}, data-model.md):
 * one configuration row per group. Defaults (decision 2026-10-27): all casillas
 * false — no restrictions active on creation; the Administrator turns them on
 * explicitly. Value names mirror the DB columns in V9 verbatim.
 */
@Entity
@Table(name = "group_permissions")
public class GroupPermissions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false, unique = true)
    private Long groupId;

    @Column(name = "members_cannot_make_expenses", nullable = false)
    private boolean membersCannotMakeExpenses;

    @Column(name = "members_cannot_invite", nullable = false)
    private boolean membersCannotInvite;

    @Column(name = "members_can_manage_simulated_profiles", nullable = false)
    private boolean membersCanManageSimulatedProfiles;

    @Column(name = "members_can_generate_expenses_for_others", nullable = false)
    private boolean membersCanGenerateExpensesForOthers;

    @Column(name = "members_can_edit_group_data", nullable = false)
    private boolean membersCanEditGroupData;

    @Column(name = "payments_require_validation", nullable = false)
    private boolean paymentsRequireValidation;

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public boolean isMembersCannotMakeExpenses() {
        return membersCannotMakeExpenses;
    }

    public void setMembersCannotMakeExpenses(boolean membersCannotMakeExpenses) {
        this.membersCannotMakeExpenses = membersCannotMakeExpenses;
    }

    public boolean isMembersCannotInvite() {
        return membersCannotInvite;
    }

    public void setMembersCannotInvite(boolean membersCannotInvite) {
        this.membersCannotInvite = membersCannotInvite;
    }

    public boolean isMembersCanManageSimulatedProfiles() {
        return membersCanManageSimulatedProfiles;
    }

    public void setMembersCanManageSimulatedProfiles(boolean membersCanManageSimulatedProfiles) {
        this.membersCanManageSimulatedProfiles = membersCanManageSimulatedProfiles;
    }

    public boolean isMembersCanGenerateExpensesForOthers() {
        return membersCanGenerateExpensesForOthers;
    }

    public void setMembersCanGenerateExpensesForOthers(boolean membersCanGenerateExpensesForOthers) {
        this.membersCanGenerateExpensesForOthers = membersCanGenerateExpensesForOthers;
    }

    public boolean isPaymentsRequireValidation() {
        return paymentsRequireValidation;
    }

    public void setPaymentsRequireValidation(boolean paymentsRequireValidation) {
        this.paymentsRequireValidation = paymentsRequireValidation;
    }

    public boolean isMembersCanEditGroupData() {
        return membersCanEditGroupData;
    }

    public void setMembersCanEditGroupData(boolean membersCanEditGroupData) {
        this.membersCanEditGroupData = membersCanEditGroupData;
    }
}
