package com.amano.backend.groups.dto;

import java.time.OffsetDateTime;

/**
 * {@code POST /api/groups} response (201): the created group, its creator and
 * the membership counters of the creation shape.
 */
public class GroupResponse {

    private Long id;
    private String name;
    private String description;
    private String photo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Creator creator;
    private int membersCount;
    private String creatorRole;
    private String primaryCurrency;

    public String getPrimaryCurrency() { return primaryCurrency; }
    public void setPrimaryCurrency(String primaryCurrency) { this.primaryCurrency = primaryCurrency; }

    public static class Creator {

        private Long id;
        private String firstName;
        private String lastName;
        private String email;

        public Creator(Long id, String firstName, String lastName, String email) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getEmail() {
            return email;
        }
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

    public Creator getCreator() {
        return creator;
    }

    public void setCreator(Creator creator) {
        this.creator = creator;
    }

    public int getMembersCount() {
        return membersCount;
    }

    public void setMembersCount(int membersCount) {
        this.membersCount = membersCount;
    }

    public String getCreatorRole() {
        return creatorRole;
    }

    public void setCreatorRole(String creatorRole) {
        this.creatorRole = creatorRole;
    }
}
