package com.amano.backend.groups.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Response of {@code GET /api/groups/{id}/activity} (contracts
 * /api-contracts.md). Entries are standalone history rows: actor info is
 * resolved at read time, but the row itself persists even when the related
 * member/expense/payment is later modified or deleted. Actor info of the write
 * side (who performed the action) is snapshot-free here: the actorUserId
 * references users, and the name resolution degrades to null context when the
 * display name is missing.
 */
public class ActivityHistoryResponse {

    private List<Entry> content = new ArrayList<>();
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public static class Entry {

        private Long id;
        private String eventType;
        private Actor actor;
        private String details;
        private OffsetDateTime createdAt;

        public Entry(Long id, String eventType, Actor actor, String details, OffsetDateTime createdAt) {
            this.id = id;
            this.eventType = eventType;
            this.actor = actor;
            this.details = details;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getEventType() {
            return eventType;
        }

        public Actor getActor() {
            return actor;
        }

        public String getDetails() {
            return details;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public static class Actor {

        private Long id;
        private String firstName;
        private String lastName;

        public Actor(Long id, String firstName, String lastName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
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
    }

    public List<Entry> getContent() {
        return content;
    }

    public void setContent(List<Entry> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
