package com.amano.backend.user;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Registered account and its profile data (table {@code users}, data-model.md).
 * Pure persistence mapping: business rules live in the service layers.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Always stored trimmed (Q4); 2-50 chars enforced by service + DB CHECK. */
    @Column(name = "first_name", nullable = false)
    private String firstName;

    /** Optional; stored trimmed (Q4), null when not provided (FR-003). */
    @Column(name = "last_name")
    private String lastName;

    /** Identity of the account, stored normalized (trim + lowercase, FR-004/FR-005). */
    @Column(name = "email", nullable = false)
    private String email;

    /** BCrypt hash; never exposed in DTOs, logs or toString (FR-006/FR-007). */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Optional CBU/CVU, exactly 22 digits after normalization (FR-009, Q3). */
    @Column(name = "cbu")
    private String cbu;

    @Column(name = "terms_accepted_at", nullable = false)
    private OffsetDateTime termsAcceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Instant the verification token was redeemed; {@code null} means the account
     * is not verified yet and is blocked from protected resources (feature 003,
     * FR-001/FR-004, data-model.md). Written only by the token redemption.
     */
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    /** 0..N subset of {@link Currency}; empty means ARS by default at read time (FR-011, Q1). */
    @jakarta.persistence.ElementCollection(fetch = FetchType.LAZY)
    @jakarta.persistence.CollectionTable(
            name = "user_currencies",
            joinColumns = @jakarta.persistence.JoinColumn(name = "user_id"))
    @Column(name = "currency_code", nullable = false, length = 3)
    @Convert(converter = CurrencyConversion.class)
    private Set<Currency> currencies = new HashSet<>();

    /**
     * Association loaded on demand: {@code hasCustomAvatar} is a computed attribute
     * derived from the existence of this row (data-model.md "Reglas derivadas").
     */
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserAvatar avatar;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getCbu() {
        return cbu;
    }

    public void setCbu(String cbu) {
        this.cbu = cbu;
    }

    public OffsetDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(OffsetDateTime termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(OffsetDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public Set<Currency> getCurrencies() {
        return currencies;
    }

    public UserAvatar getAvatar() {
        return avatar;
    }
}
