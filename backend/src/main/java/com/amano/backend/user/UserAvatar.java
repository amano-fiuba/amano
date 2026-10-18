package com.amano.backend.user;

import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Binary custom avatar, one-to-one with {@code users} (table {@code user_avatars}).
 * The row only exists when the user uploaded a file (D5: no default row in the DB).
 */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    /** Primary key and FK to {@code users.id} at the same time (ON DELETE CASCADE). */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long id;

    /** Read-only association for the mapped column; the writable side is {@link #id}. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /** Either 'image/jpeg' or 'image/png'; real MIME verified with magic bytes in the service. */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
