package com.amano.backend.verification;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Resend flow of the verification email (task T048, FR-011..FR-013, D4).
 *
 * <p>One resend every {@code verification.resend-cooldown} (5 min) PER ADDRESS,
 * existing or not: the term lives in {@code email_resend_limits}, keyed by the
 * SHA-256 hash of the NORMALIZED address (D4), which is why a click for an
 * unknown address still starts the clock and why nothing about the account
 * leaks. Every response is the same neutral message plus {@code remainingSeconds}
 * (SC-005): the controller is forbidden from answering 400/404/429.
 *
 * <p>Internal effects never reflected in the response:
 * <ul>
 *   <li>no active term + PENDING account → a fresh token (voids the previous
 *       one) and a new mail; the click starts the term;</li>
 *   <li>no active term + unknown or VERIFIED account → no mail, but the click
 *       still starts the term (FR-011);</li>
 *   <li>active term → no mail and the term is not extended (FR-013), only the
 *       real remaining time is returned.</li>
 * </ul>
 *
 * <p>A re-registration over a PENDING account (FR-015/Q2) always re-issues the
 * token but only sends when no term is in force; the FIRST registration never
 * passes through here, so its mail always goes out at once (FR-011/Q3).
 */
@Service
public class EmailResendService {

    /** FR-012/SC-005: the very same neutral answer in every case, byte by byte. */
    public static final String NEUTRAL_MESSAGE =
            "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación";

    private final EmailResendLimitRepository resendLimitRepository;
    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final VerificationMailService mailService;
    private final VerificationProperties properties;

    public EmailResendService(EmailResendLimitRepository resendLimitRepository,
            UserRepository userRepository, VerificationTokenService tokenService,
            VerificationMailService mailService, VerificationProperties properties) {
        this.resendLimitRepository = resendLimitRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.properties = properties;
    }

    /** Body of the resend endpoint: the neutral message plus the remaining wait (FR-012). */
    public record ResendResult(String message, long remainingSeconds) {
    }

    /**
     * Handles one resend click. Always answers {@code 200} with the neutral
     * message; {@code remainingSeconds} is the full term when the click starts it
     * (so the screen can show its 5-minute countdown, FR-010) and the real
     * remainder while a term is in force (FR-013).
     */
    @Transactional
    public ResendResult resend(String rawEmail) {
        String email = normalize(rawEmail);
        OffsetDateTime now = OffsetDateTime.now();
        String emailHash = tokenService.hash(email);

        Optional<EmailResendLimit> existing = resendLimitRepository.findById(emailHash);
        if (existing.filter(limit -> termInForce(limit, now)).isPresent()) {
            return new ResendResult(NEUTRAL_MESSAGE, remainingSeconds(existing.get(), now));
        }

        // FR-011: the click starts the term, with or without an account.
        recordClick(emailHash, now, existing.orElse(null));

        // FR-012/D4: only a PENDING account gets a new token and a new mail; the
        // outcome never shows up in the response (SC-005).
        userRepository.findByEmail(email)
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(user -> mailService.sendVerificationEmail(user, tokenService.issueFor(user)));

        return new ResendResult(NEUTRAL_MESSAGE, properties.resendCooldown().toSeconds());
    }

    /**
     * FR-015/Q2: re-registration over a PENDING account. The token is ALWAYS
     * re-issued (FR-009: the previous one is voided), but the mail only goes out
     * when no term is in force; a send starts the term, exactly like a resend
     * click, so repeated re-registrations cannot turn into mail spam (SC-004).
     */
    @Transactional
    public void reissueForPendingAccount(User user) {
        String clearToken = tokenService.issueFor(user);
        OffsetDateTime now = OffsetDateTime.now();
        String emailHash = tokenService.hash(user.getEmail());

        Optional<EmailResendLimit> existing = resendLimitRepository.findById(emailHash);
        if (existing.filter(limit -> termInForce(limit, now)).isPresent()) {
            return; // Q2: token replaced, no mail while the term is in force
        }

        recordClick(emailHash, now, existing.orElse(null));
        mailService.sendVerificationEmail(user, clearToken);
    }

    private void recordClick(String emailHash, OffsetDateTime now, EmailResendLimit existing) {
        EmailResendLimit limit = existing == null ? new EmailResendLimit() : existing;
        limit.setEmailHash(emailHash);
        limit.setLastResendAt(now);
        resendLimitRepository.save(limit);
    }

    private boolean termInForce(EmailResendLimit limit, OffsetDateTime now) {
        return deadline(limit).isAfter(now);
    }

    /** Ceil, so a request made while the term is in force never returns 0. */
    private long remainingSeconds(EmailResendLimit limit, OffsetDateTime now) {
        long millis = Duration.between(now, deadline(limit)).toMillis();
        return (millis + 999) / 1000;
    }

    private OffsetDateTime deadline(EmailResendLimit limit) {
        return limit.getLastResendAt().plus(properties.resendCooldown());
    }

    /** Address normalization of 001: trim + lowercase before hashing (FR-004/D4). */
    private static String normalize(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
