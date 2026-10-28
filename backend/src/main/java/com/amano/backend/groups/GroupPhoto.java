package com.amano.backend.groups;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Binary group photo, one-to-one with {@code groups} (table
 * {@code group_photos}, V11): mirrors the existing {@code user_avatars}
 * mechanism (feature 001) — the format/size gate lives in the shared
 * {@code AvatarValidator}; {@code groups.photo} holds the URL reference.
 */
@Entity
@Table(name = "group_photos")
public class GroupPhoto {

    /** Primary key and FK to {@code groups.id} at the same time (ON DELETE CASCADE). */
    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** Read-only association for the mapped column; the writable side is {@link #groupId}. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private Group group;

    /** Either 'image/jpeg' or 'image/png'; real MIME verified with magic bytes in the service. */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "data", nullable = false)
    private byte[] data;

    /** NOW() on INSERT and on UPDATE (US8 replaces keep the timestamp current). */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
