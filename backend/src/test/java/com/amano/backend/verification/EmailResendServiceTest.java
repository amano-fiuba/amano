package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of the resend limits of EmailResendService (task T042, TDD: written
 * BEFORE the implementation). Covers FR-011 (one resend every 5 minutes per
 * address, existing or not), FR-012/SC-005 (always the same neutral message plus
 * the remaining time) and FR-013 (an active term sends nothing and does not
 * extend itself), plus FR-015/Q2 (re-registration over a pending account
 * respects the term but always re-issues the token). The message is asserted
 * byte-for-byte: the spec forbids re-improvising the text.
 */
class EmailResendServiceTest {

    private static final String NEUTRAL_MESSAGE =
            "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación";

    private static final long COOLDOWN_SECONDS = 300;

    private EmailResendLimitRepository limitRepository;
    private UserRepository userRepository;
    private VerificationTokenRepository tokenRepository;
    private VerificationMailService mailService;
    private EmailResendService service;

    @BeforeEach
    void setUp() {
        limitRepository = Mockito.mock(EmailResendLimitRepository.class);
        when(limitRepository.findById(anyString())).thenReturn(Optional.empty());
        when(limitRepository.save(any(EmailResendLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailResendLimit.class));

        userRepository = Mockito.mock(UserRepository.class);

        tokenRepository = Mockito.mock(VerificationTokenRepository.class);
        when(tokenRepository.findByUserIdAndUsedAtIsNull(any())).thenReturn(List.of());
        when(tokenRepository.save(any(VerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, VerificationToken.class));

        mailService = Mockito.mock(VerificationMailService.class);

        VerificationProperties properties = new VerificationProperties(Duration.ofHours(24),
                Duration.ofSeconds(COOLDOWN_SECONDS), Duration.ofDays(3), "0 0 * * * *");
        VerificationTokenService tokenService = new VerificationTokenService(tokenRepository, properties);
        service = new EmailResendService(limitRepository, userRepository, tokenService, mailService,
                properties);
    }

    private static User account(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    /** Independent SHA-256 implementation (D2/D4): the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-012/SC-005: the neutral text is exactly the one of the spec, byte by byte. */
    @Test
    void neutralMessageIsTheExactTextOfTheSpec() {
        assertThat(EmailResendService.NEUTRAL_MESSAGE).isEqualTo(NEUTRAL_MESSAGE);
    }

    /** FR-009/FR-011/FR-012: no term + pending account → new token, mail and the term starts. */
    @Test
    void freshClickWithAPendingAccountIssuesTokenSendsMailAndStartsTheTerm() {
        User pending = account(42L, "ana.perez@example.com");
        VerificationToken previous = new VerificationToken();
        previous.setUser(pending);
        previous.setTokenHash("a".repeat(64));
        when(userRepository.findByEmail("ana.perez@example.com")).thenReturn(Optional.of(pending));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(42L)).thenReturn(List.of(previous));

        EmailResendService.ResendResult result = service.resend("  Ana.Perez@Example.COM ");

        assertThat(result.message()).isEqualTo(NEUTRAL_MESSAGE);
        // FR-010: the countdown starts at the full term, so the screen can show 5 minutes.
        assertThat(result.remainingSeconds()).isEqualTo(COOLDOWN_SECONDS);
        assertThat(previous.getUsedAt()).isNotNull(); // FR-009: the previous token is invalidated

        ArgumentCaptor<VerificationToken> token = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getTokenHash()).matches("[0-9a-f]{64}"); // only the hash is stored (D2)
        verify(mailService).sendVerificationEmail(eq(pending), anyString());

        ArgumentCaptor<EmailResendLimit> limit = ArgumentCaptor.forClass(EmailResendLimit.class);
        verify(limitRepository).save(limit.capture());
        // D4: the term is keyed by the SHA-256 of the NORMALIZED address, never by the clear one.
        assertThat(limit.getValue().getEmailHash()).isEqualTo(sha256Hex("ana.perez@example.com"));
        assertThat(limit.getValue().getLastResendAt())
                .isBetween(OffsetDateTime.now().minusSeconds(5), OffsetDateTime.now());
    }

    /** FR-011/SC-005: no term + unknown account → the click starts the term but nothing is sent. */
    @Test
    void freshClickWithoutAnAccountStartsTheTermButSendsNothing() {
        when(userRepository.findByEmail("nadie@ejemplo.com")).thenReturn(Optional.empty());

        EmailResendService.ResendResult result = service.resend("nadie@ejemplo.com");

        assertThat(result.message()).isEqualTo(NEUTRAL_MESSAGE);
        assertThat(result.remainingSeconds()).isEqualTo(COOLDOWN_SECONDS);
        verify(limitRepository).save(any(EmailResendLimit.class));
        verify(tokenRepository, never()).save(any(VerificationToken.class));
        verify(mailService, never()).sendVerificationEmail(any(), anyString());
    }

    /** FR-012/SC-005: no term + verified account → identical response, no mail, no token. */
    @Test
    void freshClickOnAVerifiedAccountSendsNothingButStartsTheTerm() {
        User verified = account(7L, "verificada@ejemplo.com");
        verified.setEmailVerifiedAt(OffsetDateTime.now());
        when(userRepository.findByEmail("verificada@ejemplo.com")).thenReturn(Optional.of(verified));

        EmailResendService.ResendResult result = service.resend("verificada@ejemplo.com");

        assertThat(result.message()).isEqualTo(NEUTRAL_MESSAGE);
        verify(limitRepository).save(any(EmailResendLimit.class));
        verify(tokenRepository, never()).save(any(VerificationToken.class));
        verify(mailService, never()).sendVerificationEmail(any(), anyString());
    }

    /**
     * FR-013/SC-004: an active term returns the REAL remaining time, sends nothing
     * and does not extend itself (last_resend_at stays put).
     */
    @Test
    void activeTermReturnsTheRemainingSecondsWithoutSendingOrExtending() {
        User pending = account(42L, "ana@ejemplo.com");
        when(userRepository.findByEmail("ana@ejemplo.com")).thenReturn(Optional.of(pending));
        EmailResendLimit existing = new EmailResendLimit();
        existing.setEmailHash(sha256Hex("ana@ejemplo.com"));
        OffsetDateTime lastClick = OffsetDateTime.now().minusSeconds(173);
        existing.setLastResendAt(lastClick);
        when(limitRepository.findById(sha256Hex("ana@ejemplo.com"))).thenReturn(Optional.of(existing));

        EmailResendService.ResendResult result = service.resend("ana@ejemplo.com");

        assertThat(result.message()).isEqualTo(NEUTRAL_MESSAGE);
        assertThat(result.remainingSeconds()).isIn(126L, 127L); // ceil(300 - 173)
        assertThat(existing.getLastResendAt()).isEqualTo(lastClick); // FR-013: the term is not extended
        verify(limitRepository, never()).save(any(EmailResendLimit.class));
        verify(tokenRepository, never()).save(any(VerificationToken.class));
        verify(mailService, never()).sendVerificationEmail(any(), anyString());
    }

    /** FR-015/Q2: re-registration with no active term re-issues the token, sends and starts the term. */
    @Test
    void pendingReRegistrationWithoutTermIssuesTokenSendsMailAndStartsTheTerm() {
        User pending = account(42L, "ana@ejemplo.com");

        service.reissueForPendingAccount(pending);

        verify(tokenRepository).save(any(VerificationToken.class));
        verify(mailService).sendVerificationEmail(eq(pending), anyString());
        ArgumentCaptor<EmailResendLimit> limit = ArgumentCaptor.forClass(EmailResendLimit.class);
        verify(limitRepository).save(limit.capture());
        assertThat(limit.getValue().getEmailHash()).isEqualTo(sha256Hex("ana@ejemplo.com"));
    }

    /** FR-015/Q2: with an active term the token is re-issued but the mail waits (no send, no extension). */
    @Test
    void pendingReRegistrationWithAnActiveTermIssuesTokenButSendsNothing() {
        User pending = account(42L, "ana@ejemplo.com");
        EmailResendLimit existing = new EmailResendLimit();
        existing.setEmailHash(sha256Hex("ana@ejemplo.com"));
        existing.setLastResendAt(OffsetDateTime.now().minusSeconds(10));
        when(limitRepository.findById(sha256Hex("ana@ejemplo.com"))).thenReturn(Optional.of(existing));

        service.reissueForPendingAccount(pending);

        verify(tokenRepository).save(any(VerificationToken.class)); // T044: a new token is always issued
        verify(limitRepository, never()).save(any(EmailResendLimit.class));
        verify(mailService, never()).sendVerificationEmail(any(), anyString());
    }
}
