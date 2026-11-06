package com.amano.backend.groups.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.amano.backend.groups.GroupPermissions;

/**
 * {@code GET /api/groups/{id}} detail (contracts/api-contracts.md). Since 017
 * (FR-027), {@code expensesCount} is the real number of group expenses of the
 * group ({@code group_expenses}); the shape of the contract is unchanged.
 */
public class GroupDetailResponse {

    private Long id;
    private String name;
    private String description;
    private String photo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean isActiveForUser;
    private long memberCount;
    private String currentUserRole;
    private GroupPermissions permissions;
    private Object lastActivity;
    private long expensesCount;
    private boolean lastMember;
    private boolean lastAdmin;

    /** True when the caller is an ADMIN and the only active admin of the group. */
    @JsonProperty("isLastAdmin")
    public boolean isLastAdmin() {
        return lastAdmin;
    }

    public void setLastAdmin(boolean lastAdmin) {
        this.lastAdmin = lastAdmin;
    }

    /** True when the caller is the only registered member left in the group. */
    @JsonProperty("isLastMember")
    public boolean isLastMember() {
        return lastMember;
    }

    public void setLastMember(boolean lastMember) {
        this.lastMember = lastMember;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonProperty("isActiveForUser")
    public boolean isActiveForUser() {
        return isActiveForUser;
    }

    public void setActiveForUser(boolean activeForUser) {
        this.isActiveForUser = activeForUser;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public String getCurrentUserRole() {
        return currentUserRole;
    }

    public void setCurrentUserRole(String currentUserRole) {
        this.currentUserRole = currentUserRole;
    }

    public GroupPermissions getPermissions() {
        return permissions;
    }

    public void setPermissions(GroupPermissions permissions) {
        this.permissions = permissions;
    }

    public Object getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Object lastActivity) {
        this.lastActivity = lastActivity;
    }

    public long getExpensesCount() {
        return expensesCount;
    }

    public void setExpensesCount(long expensesCount) {
        this.expensesCount = expensesCount;
    }
}
