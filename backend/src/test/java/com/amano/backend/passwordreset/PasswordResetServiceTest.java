package com.amano.backend.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.PasswordResetTokenExpiredException;
import com.amano.backend.common.PasswordResetTokenInvalidException;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of PasswordResetService (feature 006, TDD: written BEFORE the
 * implementation). Covers FR-002 (neutral message), FR-003 (normalization),
 * FR-004 (only verified accounts get a mail), FR-005 (only the SHA-256 hash is
 * stored and it matches the mailed token), FR-006 (1-hour expiry) and FR-007
 * (issuing a token invalidates the current ones).
 *
 * <p>US2 (redemption): FR-011 (lookup by hash, used/expired rules), FR-012 (400
 * invalid), FR-013 (410 expired, token removed), FR-014 (policy + confirmation,
 * token kept) and FR-015 (BCrypt hash stored, every reset token removed).
 */
class PasswordResetServiceTest {

    private static final String NEUTRAL_MESSAGE =
            "Si el correo existe, recibirás un enlace para restablecer tu contraseña";

    private static final Duration EXPIRATION = Duration.ofHours(1);

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordResetMailService mailService;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        tokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
        when(tokenRepository.findByUserIdAndUsedAtIsNull(any())).thenReturn(List.of());
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PasswordResetToken.class));

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        mailService = Mockito.mock(PasswordResetMailService.class);

        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "bcrypt:" + invocation.getArgument(0, String.class));

        service = new PasswordResetService(userRepository, tokenRepository, mailService,
                passwordEncoder, new PasswordResetProperties(EXPIRATION));
    }

    private static User verifiedAccount(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setEmailVerifiedAt(OffsetDateTime.now().minusDays(1));
        return user;
    }

    /** Independent SHA-256 implementation: the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** FR-002: the neutral text is exactly the one of the spec, byte by byte. */
    @Test
    void neutralMessageIsTheExactTextOfTheSpec() {
        assertThat(PasswordResetService.NEUTRAL_MESSAGE).isEqualTo(NEUTRAL_MESSAGE);
    }

    /** FR-003/FR-004/FR-005/FR-006: a verified account gets a hashed, 1-hour token and the mail. */
    @Test
    void verifiedAccountGetsAHashedTokenAndTheMail() {
        User ana = verifiedAccount(42L, "ana@example.com");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        service.requestReset("  Ana@Example.COM ");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> clearToken = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq(ana), clearToken.capture());

        PasswordResetToken token = saved.getValue();
        assertThat(token.getUser()).isSameAs(ana);
        // FR-005: 256 bits in Base64URL without padding = 43 chars; only its hash is stored.
        assertThat(clearToken.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(token.getTokenHash()).isEqualTo(sha256Hex(clearToken.getValue()));
        assertThat(token.getUsedAt()).isNull();
        // FR-006: expires one hour from now.
        OffsetDateTime expected = OffsetDateTime.now().plus(EXPIRATION);
        assertThat(token.getExpiresAt()).isBetween(expected.minusSeconds(5), expected);
    }

    /** FR-007: issuing a new token marks the current ones as used. */
    @Test
    void issuingANewTokenInvalidatesTheCurrentOnes() {
        User ana = verifiedAccount(42L, "ana@example.com");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));
        PasswordResetToken previous = new PasswordResetToken();
        previous.setUser(ana);
        previous.setTokenHash("a".repeat(64));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(42L)).thenReturn(List.of(previous));

        service.requestReset("ana@example.com");

        assertThat(previous.getUsedAt()).isNotNull();
        verify(tokenRepository).saveAll(List.of(previous));
    }

    /** Two requests produce two different tokens (SecureRandom, FR-005). */
    @Test
    void everyRequestProducesADifferentToken() {
        User ana = verifiedAccount(42L, "ana@example.com");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        service.requestReset("ana@example.com");
        service.requestReset("ana@example.com");

        ArgumentCaptor<String> clearToken = ArgumentCaptor.forClass(String.class);
        verify(mailService, Mockito.times(2)).sendPasswordResetEmail(eq(ana), clearToken.capture());
        assertThat(clearToken.getAllValues().get(0)).isNotEqualTo(clearToken.getAllValues().get(1));
    }

    /** FR-002/FR-004: an unknown address issues nothing and sends nothing. */
    @Test
    void unknownAddressIssuesNothingAndSendsNothing() {
        String message = service.requestReset("nadie@ejemplo.com");

        assertThat(message).isEqualTo(NEUTRAL_MESSAGE);
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(mailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    /** FR-004: a pending (unverified) account gets the same answer and no mail. */
    @Test
    void pendingAccountIssuesNothingAndSendsNothing() {
        User pending = verifiedAccount(7L, "pendiente@ejemplo.com");
        pending.setEmailVerifiedAt(null);
        when(userRepository.findByEmail("pendiente@ejemplo.com")).thenReturn(Optional.of(pending));

        String message = service.requestReset("pendiente@ejemplo.com");

        assertThat(message).isEqualTo(NEUTRAL_MESSAGE);
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(mailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    /** Edge case: a missing email is handled as an unknown address, never as an error. */
    @Test
    void nullEmailIsTreatedAsUnknown() {
        String message = service.requestReset(null);

        assertThat(message).isEqualTo(NEUTRAL_MESSAGE);
        verify(mailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    /** FR-002: the verified case answers exactly like the unknown one. */
    @Test
    void verifiedAndUnknownAnswerIdentically() {
        User ana = verifiedAccount(42L, "ana@example.com");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThat(service.requestReset("ana@example.com"))
                .isEqualTo(service.requestReset("nadie@ejemplo.com"));
    }

    // ----- US2: redemption -----------------------------------------------------

    private static final String NEW_PASSWORD = "NuevaClave123!";

    /** Stores a token for {@code clearToken} owned by a verified account, with the given expiry. */
    private PasswordResetToken storedToken(String clearToken, OffsetDateTime expiresAt) {
        User ana = verifiedAccount(42L, "ana@example.com");
        ana.setPasswordHash("bcrypt:ViejaClave123!");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(ana);
        token.setTokenHash(sha256Hex(clearToken));
        token.setExpiresAt(expiresAt);
        when(tokenRepository.findByTokenHash(sha256Hex(clearToken))).thenReturn(Optional.of(token));
        return token;
    }

    /** FR-011/FR-015: a valid token stores the BCrypt hash and removes every reset token. */
    @Test
    void validTokenUpdatesThePasswordAndRemovesTheTokens() {
        PasswordResetToken token = storedToken("token-vigente", OffsetDateTime.now().plusMinutes(30));

        String message = service.resetPassword("token-vigente", NEW_PASSWORD, NEW_PASSWORD);

        assertThat(message).isEqualTo(PasswordResetService.RESET_SUCCESS_MESSAGE);
        assertThat(token.getUser().getPasswordHash()).isEqualTo("bcrypt:" + NEW_PASSWORD);
        verify(passwordEncoder).encode(NEW_PASSWORD);
        verify(tokenRepository).deleteByUserId(42L);
    }

    /** FR-015: the success text is exactly the one of the spec. */
    @Test
    void successMessageIsTheExactTextOfTheSpec() {
        assertThat(PasswordResetService.RESET_SUCCESS_MESSAGE)
                .isEqualTo("Tu contraseña fue actualizada. Ya puedes iniciar sesión");
    }

    /** FR-012: an unknown token is invalid and nothing changes. */
    @Test
    void unknownTokenIsInvalid() {
        assertThatThrownBy(() -> service.resetPassword("inventado", NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(PasswordResetTokenInvalidException.class)
                .hasMessage("El enlace para restablecer la contraseña no es válido");
        verify(passwordEncoder, never()).encode(anyString());
        verify(tokenRepository, never()).deleteByUserId(any());
    }

    /** FR-012: a missing token is invalid without throwing anything else. */
    @Test
    void nullTokenIsInvalid() {
        assertThatThrownBy(() -> service.resetPassword(null, NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(PasswordResetTokenInvalidException.class);
    }

    /** FR-007/FR-012: a token superseded by a newer request (used_at set) is invalid. */
    @Test
    void supersededTokenIsInvalid() {
        PasswordResetToken token = storedToken("token-viejo", OffsetDateTime.now().plusMinutes(30));
        token.setUsedAt(OffsetDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.resetPassword("token-viejo", NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(PasswordResetTokenInvalidException.class);
        assertThat(token.getUser().getPasswordHash()).isEqualTo("bcrypt:ViejaClave123!");
    }

    /** FR-013: an expired token answers 410, is deleted and the password stays. */
    @Test
    void expiredTokenIsRemovedAndRejected() {
        PasswordResetToken token = storedToken("token-vencido", OffsetDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> service.resetPassword("token-vencido", NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(PasswordResetTokenExpiredException.class)
                .hasMessage("El enlace para restablecer la contraseña ha expirado");
        verify(tokenRepository).delete(token);
        assertThat(token.getUser().getPasswordHash()).isEqualTo("bcrypt:ViejaClave123!");
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** FR-014: a weak password is rejected per field and the token is kept for a retry. */
    @Test
    void weakPasswordIsRejectedAndTheTokenIsKept() {
        PasswordResetToken token = storedToken("token-vigente", OffsetDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.resetPassword("token-vigente", "corta", "corta"))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("password", "La contraseña debe tener al menos 8 caracteres"));
        assertThat(token.getUsedAt()).isNull();
        verify(tokenRepository, never()).deleteByUserId(any());
        verify(tokenRepository, never()).delete(any(PasswordResetToken.class));
    }

    /** FR-014: the policy of 001 also requires the four character classes. */
    @Test
    void passwordWithoutSymbolIsRejected() {
        storedToken("token-vigente", OffsetDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.resetPassword("token-vigente", "SinSimbolo123", "SinSimbolo123"))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields()).containsEntry("password",
                                "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo"));
    }

    /** FR-014: a mismatching or missing confirmation is rejected on confirmPassword. */
    @Test
    void mismatchingConfirmationIsRejected() {
        storedToken("token-vigente", OffsetDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.resetPassword("token-vigente", NEW_PASSWORD, "OtraClave123!"))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("confirmPassword", "Las contraseñas no coinciden"));
        assertThatThrownBy(() -> service.resetPassword("token-vigente", NEW_PASSWORD, null))
                .isInstanceOf(FieldValidationException.class);
        verify(passwordEncoder, never()).encode(anyString());
    }

    /** FR-014: an empty password reports the required message. */
    @Test
    void emptyPasswordIsRequired() {
        storedToken("token-vigente", OffsetDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.resetPassword("token-vigente", "", ""))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("password", "La contraseña es obligatoria"));
    }
}
