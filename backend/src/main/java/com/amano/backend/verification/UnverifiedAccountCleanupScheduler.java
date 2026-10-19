package com.amano.backend.verification;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * 3-day cleanup of never-verified accounts (task T055, FR-017/FR-018, SC-007, D6).
 * An hourly {@link Scheduled} job (cadence {@code verification.cron-cleanup}) removes
 * the accounts still unverified after {@code verification.account-expiration} has
 * passed since their LAST token was issued. Deleting the account cascades its tokens
 * (previous links then behave as invalid, FR-018) and frees the unique email address,
 * so a full re-registration is possible (SC-007).
 *
 * <p>The resend limits are NOT removed: they are hash-only rows with no FK to the
 * account (privacy minimality, D4) and persist across the cleanup (data-model).
 * The job never touches verified accounts. ITs shorten the deadline and call the
 * method directly instead of waiting for the cron.
 */
@Component
public class UnverifiedAccountCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountCleanupScheduler.class);

    private final UserRepository userRepository;
    private final VerificationProperties properties;

    public UnverifiedAccountCleanupScheduler(UserRepository userRepository, VerificationProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** Removes the unverified accounts whose most recent token outlived the deadline. */
    @Scheduled(cron = "${verification.cron-cleanup}")
    @Transactional
    public void cleanupExpiredUnverifiedAccounts() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minus(properties.accountExpiration());
        List<User> expirable = userRepository.findExpirableUnverifiedAccounts(now, cutoff);
        if (expirable.isEmpty()) {
            return;
        }
        userRepository.deleteAll(expirable); // tokens cascade by FK (FR-017/FR-018)
        log.info("Cleanup removed {} unverified account(s) whose last token predates {}",
                expirable.size(), cutoff);
    }
}
