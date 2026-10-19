package com.amano.backend.verification;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.amano.backend.user.User;

/**
 * Single-use verification token (table {@code email_verification_tokens},
 * data-model.md). Only the SHA-256 hash of the clear-text token is persisted:
 * the clear value travels exclusively inside the emailed link (D2).
 *
 * <p>One current token per account: issuing a new one marks the previous ones
 * with {@code used_at} in the same transaction (FR-009). Business rules live in
 * {@code VerificationTokenService}; this class is pure persistence mapping.
 */
@Entity
@Table(name = "email_verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Owner of the token; removed with the account (FR-017 cascade, D2). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex (64 chars) of the clear-text token; never the clear value. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /** Start of validity and reference for the 3-day account expiry (FR-003, FR-017). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** {@code created_at + 24 h} (configurable through VerificationProperties). */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** {@code null} = current and unused; set on redemption or invalidation (FR-007, FR-009). */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
