package com.amano.backend.verification;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link EmailResendLimit} (data-model.md, feature 003). The
 * identity is the SHA-256 hash of the normalized email address (D4), so lookups
 * are by {@code findById(emailHash)}; no query is declared here.
 */
public interface EmailResendLimitRepository extends JpaRepository<EmailResendLimit, String> {
}
