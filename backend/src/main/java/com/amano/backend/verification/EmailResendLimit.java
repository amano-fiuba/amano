package com.amano.backend.verification;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Resend rate limit of one email address (table {@code email_resend_limits},
 * data-model.md, decision D4). The primary key is the SHA-256 hash of the
 * NORMALIZED address, so the limit applies whether or not an account exists and
 * the clear address of a non-existing account is never retained (privacy
 * minimality, consistent with the 3-day account expiry).
 *
 * <p>Pure persistence mapping: the five-minute rule lives in
 * {@link EmailResendService}.
 */
@Entity
@Table(name = "email_resend_limits")
public class EmailResendLimit {

    /** SHA-256 hex of the normalized address; never the clear address (D4). */
    @Id
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    /** Instant of the last click that started the current five-minute term. */
    @Column(name = "last_resend_at", nullable = false)
    private OffsetDateTime lastResendAt;

    public String getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

    public OffsetDateTime getLastResendAt() {
        return lastResendAt;
    }

    public void setLastResendAt(OffsetDateTime lastResendAt) {
        this.lastResendAt = lastResendAt;
    }
}
